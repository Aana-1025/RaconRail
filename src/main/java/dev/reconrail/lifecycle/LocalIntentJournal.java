package dev.reconrail.lifecycle;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import static java.nio.file.StandardOpenOption.*;

/**
 * Single-writer, forced local append journal. Its barrier is deliberately LOCAL_ONLY: neither
 * HMAC nor same-disk fsync proves independent freshness or off-host disaster survival.
 */
public final class LocalIntentJournal implements IntentJournal, AutoCloseable {
    private static final byte[] MAGIC = "RRJNL001".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER_BYTES = MAGIC.length + Integer.BYTES + 16;
    private static final int FORMAT_VERSION = 1;
    private static final int DIGEST_BYTES = 32;
    private static final int MAX_FRAME_BYTES = 16 * 1024;
    private static final byte[] EMPTY_DIGEST = new byte[DIGEST_BYTES];

    private record Stored(JournalIntent intent, byte[] body, AppendReceipt receipt) { }

    private final FileChannel channel;
    private final FileLock writerLock;
    private final Map<String, SecretKey> keys;
    private final String currentKeyId;
    private final LinkedHashMap<UUID, Stored> byCommand = new LinkedHashMap<>();
    private UUID journalId;
    private long lastSequence;
    private byte[] lastDigest = EMPTY_DIGEST.clone();
    private boolean appendUncertain;

    public LocalIntentJournal(Path file, Map<String, SecretKey> keys, String currentKeyId) throws IOException {
        Objects.requireNonNull(file, "file");
        this.keys = Map.copyOf(Objects.requireNonNull(keys, "keys"));
        this.currentKeyId = requireText(currentKeyId, 64, "KEY_ID_REQUIRED");
        if (!this.keys.containsKey(this.currentKeyId)) throw new IllegalArgumentException("CURRENT_KEY_MISSING");
        this.channel = FileChannel.open(file, CREATE, READ, WRITE);
        FileLock acquired = null;
        try {
            try { acquired = channel.tryLock(); }
            catch (OverlappingFileLockException failure) {
                throw new IOException("JOURNAL_WRITER_ALREADY_ACTIVE", failure);
            }
            if (acquired == null) throw new IOException("JOURNAL_WRITER_ALREADY_ACTIVE");
            this.writerLock = acquired;
            if (channel.size() == 0) initialize(); else replay();
        } catch (IOException | RuntimeException failure) {
            if (acquired != null) acquired.release();
            channel.close();
            throw failure;
        }
    }

    @Override public synchronized AppendReceipt putIntent(JournalIntent intent) throws IOException {
        Objects.requireNonNull(intent, "intent");
        if (appendUncertain) throw new IOException("JOURNAL_REOPEN_REQUIRED");
        byte[] body = encodeIntent(intent);
        Stored existing = byCommand.get(intent.commandId());
        if (existing != null) {
            if (!MessageDigest.isEqual(existing.body, body)) throw new IOException("JOURNAL_BODY_CONFLICT");
            return existing.receipt;
        }
        long sequence = Math.addExact(lastSequence, 1);
        byte[] payload = encodePayload(sequence, currentKeyId, lastDigest, body);
        byte[] mac = hmac(keys.get(currentKeyId), payload);
        byte[] digest = sha256(payload, mac);
        int frameLength = payload.length + mac.length + digest.length;
        if (frameLength > MAX_FRAME_BYTES) throw new IOException("JOURNAL_FRAME_TOO_LARGE");
        ByteBuffer frame = ByteBuffer.allocate(Integer.BYTES + frameLength)
                .putInt(frameLength).put(payload).put(mac).put(digest).flip();
        try {
            channel.position(channel.size());
            writeFully(frame);
            channel.force(true);
        } catch (IOException | RuntimeException failure) {
            appendUncertain = true;
            if (failure instanceof IOException io) throw io;
            throw new IOException("JOURNAL_APPEND_UNCERTAIN", failure);
        }
        var receipt = new AppendReceipt(journalId, sequence, digest);
        byCommand.put(intent.commandId(), new Stored(intent, body, receipt));
        lastSequence = sequence;
        lastDigest = digest;
        return receipt;
    }

    @Override public synchronized Optional<JournalIntent> getIntent(UUID commandId) {
        Stored stored = byCommand.get(Objects.requireNonNull(commandId, "commandId"));
        return stored == null ? Optional.empty() : Optional.of(stored.intent);
    }

    @Override public synchronized LocalBarrier readLocalBarrier() {
        return new LocalBarrier(journalId, lastSequence, lastDigest, EvidenceClass.LOCAL_ONLY);
    }

    private void initialize() throws IOException {
        journalId = UUID.randomUUID();
        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).put(MAGIC).putInt(FORMAT_VERSION)
                .putLong(journalId.getMostSignificantBits()).putLong(journalId.getLeastSignificantBits()).flip();
        writeFully(header);
        channel.force(true);
    }

    private void replay() throws IOException {
        if (channel.size() < HEADER_BYTES) throw new IOException("JOURNAL_TRUNCATED_HEADER");
        channel.position(0);
        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
        readFully(header);
        header.flip();
        byte[] magic = new byte[MAGIC.length]; header.get(magic);
        if (!Arrays.equals(magic, MAGIC) || header.getInt() != FORMAT_VERSION) throw new IOException("JOURNAL_HEADER_INVALID");
        journalId = new UUID(header.getLong(), header.getLong());
        while (channel.position() < channel.size()) replayFrame();
    }

    private void replayFrame() throws IOException {
        ByteBuffer length = ByteBuffer.allocate(Integer.BYTES);
        readFully(length); length.flip();
        int frameLength = length.getInt();
        if (frameLength < 8 + 4 + 1 + DIGEST_BYTES + 4 + 1 + DIGEST_BYTES * 2
                || frameLength > MAX_FRAME_BYTES) throw new IOException("JOURNAL_FRAME_LENGTH_INVALID");
        ByteBuffer frameBuffer = ByteBuffer.allocate(frameLength);
        readFully(frameBuffer); frameBuffer.flip();
        byte[] frame = new byte[frameLength]; frameBuffer.get(frame);
        int payloadLength = frameLength - DIGEST_BYTES * 2;
        byte[] payload = Arrays.copyOfRange(frame, 0, payloadLength);
        byte[] mac = Arrays.copyOfRange(frame, payloadLength, payloadLength + DIGEST_BYTES);
        byte[] digest = Arrays.copyOfRange(frame, payloadLength + DIGEST_BYTES, frameLength);
        Payload decoded = decodePayload(payload);
        SecretKey key = keys.get(decoded.keyId);
        if (key == null) throw new IOException("JOURNAL_KEY_UNAVAILABLE");
        if (!MessageDigest.isEqual(mac, hmac(key, payload))) throw new IOException("JOURNAL_MAC_INVALID");
        if (!MessageDigest.isEqual(digest, sha256(payload, mac))) throw new IOException("JOURNAL_DIGEST_INVALID");
        if (decoded.sequence != lastSequence + 1) throw new IOException("JOURNAL_SEQUENCE_GAP");
        if (!MessageDigest.isEqual(decoded.previousDigest, lastDigest)) throw new IOException("JOURNAL_CHAIN_INVALID");
        JournalIntent intent = decodeIntent(decoded.intentBody);
        Stored prior = byCommand.get(intent.commandId());
        if (prior != null) throw new IOException(MessageDigest.isEqual(prior.body, decoded.intentBody)
                ? "JOURNAL_DUPLICATE_SEQUENCE" : "JOURNAL_BODY_CONFLICT");
        var receipt = new AppendReceipt(journalId, decoded.sequence, digest);
        byCommand.put(intent.commandId(), new Stored(intent, decoded.intentBody, receipt));
        lastSequence = decoded.sequence;
        lastDigest = digest;
    }

    private record Payload(long sequence, String keyId, byte[] previousDigest, byte[] intentBody) { }

    private static byte[] encodePayload(long sequence, String keyId, byte[] previous, byte[] body) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeLong(sequence); writeText(out, keyId); out.write(previous); out.writeInt(body.length); out.write(body);
        }
        return bytes.toByteArray();
    }

    private static Payload decodePayload(byte[] payload) throws IOException {
        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            long sequence = in.readLong();
            String keyId = readText(in, 64);
            byte[] previous = in.readNBytes(DIGEST_BYTES);
            if (previous.length != DIGEST_BYTES) throw new IOException("JOURNAL_PAYLOAD_TRUNCATED");
            int bodyLength = in.readInt();
            if (bodyLength < 1 || bodyLength > MAX_FRAME_BYTES || bodyLength > in.available()) {
                throw new IOException("JOURNAL_BODY_LENGTH_INVALID");
            }
            byte[] body = in.readNBytes(bodyLength);
            if (in.available() != 0) throw new IOException("JOURNAL_PAYLOAD_TRAILING_BYTES");
            return new Payload(sequence, keyId, previous, body);
        } catch (EOFException failure) { throw new IOException("JOURNAL_PAYLOAD_TRUNCATED", failure); }
    }

    private static byte[] encodeIntent(JournalIntent intent) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(1); writeUuid(out, intent.commandId()); writeUuid(out, intent.scope().accountId());
            out.writeLong(intent.scope().accountGeneration()); out.writeByte(intent.scope().kind().ordinal());
            if (intent.scope().workspaceId() == null) out.writeBoolean(false);
            else { out.writeBoolean(true); writeUuid(out, intent.scope().workspaceId()); }
            writeUuid(out, intent.scope().resourceId()); out.writeLong(intent.scope().resourceGeneration());
            writeInstant(out, intent.requestedAt()); writeInstant(out, intent.deadlineBase());
            out.write(intent.fingerprint());
        }
        return bytes.toByteArray();
    }

    private static JournalIntent decodeIntent(byte[] body) throws IOException {
        try (var in = new DataInputStream(new ByteArrayInputStream(body))) {
            if (in.readInt() != 1) throw new IOException("JOURNAL_INTENT_VERSION_INVALID");
            UUID command = readUuid(in), account = readUuid(in); long accountGeneration = in.readLong();
            int kindOrdinal = in.readUnsignedByte();
            if (kindOrdinal >= AuthorityScope.Kind.values().length) throw new IOException("JOURNAL_SCOPE_INVALID");
            UUID workspace = in.readBoolean() ? readUuid(in) : null;
            UUID resource = readUuid(in); long resourceGeneration = in.readLong();
            Instant requested = readInstant(in), deadline = readInstant(in);
            byte[] fingerprint = in.readNBytes(DIGEST_BYTES);
            if (fingerprint.length != DIGEST_BYTES || in.available() != 0) throw new IOException("JOURNAL_INTENT_INVALID");
            var scope = new AuthorityScope(account, accountGeneration, AuthorityScope.Kind.values()[kindOrdinal], workspace, resource, resourceGeneration);
            return new JournalIntent(command, scope, requested, deadline, fingerprint);
        } catch (EOFException | IllegalArgumentException failure) { throw new IOException("JOURNAL_INTENT_INVALID", failure); }
    }

    private static void writeUuid(DataOutputStream out, UUID value) throws IOException {
        out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits());
    }
    private static UUID readUuid(DataInputStream in) throws IOException { return new UUID(in.readLong(), in.readLong()); }
    private static void writeInstant(DataOutputStream out, Instant value) throws IOException {
        out.writeLong(value.getEpochSecond()); out.writeInt(value.getNano());
    }
    private static Instant readInstant(DataInputStream in) throws IOException {
        try { return Instant.ofEpochSecond(in.readLong(), in.readInt()); }
        catch (RuntimeException failure) { throw new IOException("JOURNAL_INSTANT_INVALID", failure); }
    }
    private static void writeText(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); out.writeInt(bytes.length); out.write(bytes);
    }
    private static String readText(DataInputStream in, int max) throws IOException {
        int length = in.readInt(); if (length < 1 || length > max || length > in.available()) throw new IOException("JOURNAL_TEXT_INVALID");
        return requireText(new String(in.readNBytes(length), StandardCharsets.UTF_8), max, "JOURNAL_TEXT_INVALID");
    }
    private static String requireText(String value, int max, String code) {
        if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > max) throw new IllegalArgumentException(code);
        return value;
    }
    private static byte[] hmac(SecretKey key, byte[] payload) throws IOException {
        try { Mac hmac = Mac.getInstance("HmacSHA256"); hmac.init(key); return hmac.doFinal(payload); }
        catch (GeneralSecurityException failure) { throw new IOException("JOURNAL_MAC_UNAVAILABLE", failure); }
    }
    private static byte[] sha256(byte[] first, byte[] second) throws IOException {
        try { MessageDigest digest = MessageDigest.getInstance("SHA-256"); digest.update(first); return digest.digest(second); }
        catch (GeneralSecurityException failure) { throw new IOException("JOURNAL_DIGEST_UNAVAILABLE", failure); }
    }
    private void writeFully(ByteBuffer buffer) throws IOException { while (buffer.hasRemaining()) channel.write(buffer); }
    private void readFully(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) if (channel.read(buffer) < 0) throw new IOException("JOURNAL_TRUNCATED_RECORD");
    }
    @Override public synchronized void close() throws IOException { writerLock.release(); channel.close(); }
}
