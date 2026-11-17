package dev.reconrail.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalIntentJournalTest {
    @TempDir Path temp;

    @Test void forcedAppendReplaysAndSameBodyIsExactlyIdempotent() throws Exception {
        Path file = temp.resolve("intent.rrj"); var intent = intent(UUID.randomUUID(), (byte) 1);
        IntentJournal.AppendReceipt first;
        try (var journal = journal(file, (byte) 9)) {
            first = journal.putIntent(intent);
            assertEquals(first, journal.putIntent(intent));
            assertEquals(IntentJournal.EvidenceClass.LOCAL_ONLY, journal.readLocalBarrier().evidenceClass());
        }
        try (var restarted = journal(file, (byte) 9)) {
            assertTrue(restarted.getIntent(intent.commandId()).orElseThrow().sameBody(intent));
            assertEquals(first, restarted.putIntent(intent));
            var conflict = new JournalIntent(intent.commandId(), intent.scope(), intent.requestedAt(),
                    intent.deadlineBase(), filled((byte) 2));
            assertEquals("JOURNAL_BODY_CONFLICT", assertThrows(IOException.class,
                    () -> restarted.putIntent(conflict)).getMessage());
        }
    }

    @Test void truncationCorruptionAndMissingKeyAllFailClosedOnRestart() throws Exception {
        for (String mode : List.of("truncate", "corrupt", "wrong-key", "missing-key")) {
            Path file = temp.resolve(mode + ".rrj");
            try (var journal = journal(file, (byte) 4)) { journal.putIntent(intent(UUID.randomUUID(), (byte) 3)); }
            if (mode.equals("truncate")) {
                try (var channel = FileChannel.open(file, StandardOpenOption.WRITE)) { channel.truncate(Files.size(file) - 1); }
            } else if (mode.equals("corrupt")) {
                byte[] bytes = Files.readAllBytes(file); bytes[bytes.length - 10] ^= 1; Files.write(file, bytes);
            }
            assertThrows(IOException.class, () -> {
                try (var ignored = mode.equals("missing-key")
                        ? journal(file, "replacement-key", (byte) 4)
                        : journal(file, mode.equals("wrong-key") ? (byte) 5 : (byte) 4)) { }
            }, mode);
        }
    }

    @Test void authenticatedButSkippedSequenceIsRejected() throws Exception {
        Path file = temp.resolve("gap.rrj"); byte keyFill = 12;
        try (var journal = journal(file, keyFill)) {
            journal.putIntent(intent(UUID.randomUUID(), (byte) 1));
            journal.putIntent(intent(UUID.randomUUID(), (byte) 2));
        }
        byte[] bytes = Files.readAllBytes(file); ByteBuffer all = ByteBuffer.wrap(bytes);
        all.position(28); int firstLength = all.getInt(); all.position(28 + 4 + firstLength);
        int secondLength = all.getInt(); int payloadStart = all.position(); int payloadLength = secondLength - 64;
        all.putLong(payloadStart, 3L);
        byte[] payload = Arrays.copyOfRange(bytes, payloadStart, payloadStart + payloadLength);
        var key = new SecretKeySpec(filled(keyFill), "HmacSHA256");
        Mac hmac = Mac.getInstance("HmacSHA256"); hmac.init(key); byte[] mac = hmac.doFinal(payload);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(concat(payload, mac));
        System.arraycopy(mac, 0, bytes, payloadStart + payloadLength, 32);
        System.arraycopy(digest, 0, bytes, payloadStart + payloadLength + 32, 32);
        Files.write(file, bytes);
        assertEquals("JOURNAL_SEQUENCE_GAP", assertThrows(IOException.class,
                () -> { try (var ignored = journal(file, keyFill)) { } }).getMessage());
    }

    @Test void ambiguousAcknowledgementRetriesSameImmutableCommandWithoutReopening() throws Exception {
        Path file = temp.resolve("ambiguous.rrj"); var gate = new IoGate(); var intent = intent(UUID.randomUUID(), (byte) 7);
        try (var durable = journal(file, (byte) 8)) {
            var ambiguous = new IntentJournal() {
                boolean first = true;
                @Override public AppendReceipt putIntent(JournalIntent value) throws IOException {
                    AppendReceipt receipt = durable.putIntent(value);
                    if (first) { first = false; throw new IOException("ACK_LOST"); }
                    return receipt;
                }
                @Override public Optional<JournalIntent> getIntent(UUID id) { return durable.getIntent(id); }
                @Override public LocalBarrier readLocalBarrier() { return durable.readLocalBarrier(); }
            };
            var coordinator = new DenialCoordinator(gate, ambiguous);
            var first = coordinator.deny(intent, receipt -> fail("fence must not run after ambiguous acknowledgement"));
            assertEquals(DenialCoordinator.Status.JOURNAL_UNKNOWN, first.status());
            assertTrue(gate.locallyDenied(intent.scope()));
            var second = coordinator.deny(intent, receipt -> assertEquals(1, receipt.sequence()));
            assertEquals(DenialCoordinator.Status.FENCED, second.status());
            assertEquals(1, durable.readLocalBarrier().sequence());
        }
    }

    @Test void secondWriterCannotOpenTheSameJournal() throws Exception {
        Path file = temp.resolve("single-writer.rrj");
        try (var first = journal(file, (byte) 6)) {
            assertEquals("JOURNAL_WRITER_ALREADY_ACTIVE", assertThrows(IOException.class,
                    () -> { try (var ignored = journal(file, (byte) 6)) { } }).getMessage());
        }
    }

    private static LocalIntentJournal journal(Path file, byte fill) throws IOException {
        return journal(file, "local-test-key", fill);
    }
    private static LocalIntentJournal journal(Path file, String keyId, byte fill) throws IOException {
        byte[] key = filled(fill); return new LocalIntentJournal(file,
                Map.of(keyId, new SecretKeySpec(key, "HmacSHA256")), keyId);
    }
    private static JournalIntent intent(UUID command, byte fill) {
        Instant now = Instant.parse("2026-11-17T10:15:30.123456Z");
        return new JournalIntent(command, AuthorityScope.account(
                UUID.fromString("00000000-0000-0000-0000-000000000777"), 4), now, now.minusSeconds(1), filled(fill));
    }
    private static byte[] filled(byte value) { byte[] bytes = new byte[32]; Arrays.fill(bytes, value); return bytes; }
    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length); return result;
    }
}
