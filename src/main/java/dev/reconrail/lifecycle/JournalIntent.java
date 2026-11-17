package dev.reconrail.lifecycle;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/** Minimal opaque deletion intent. It deliberately contains no user or evidence payload. */
public record JournalIntent(
        UUID commandId,
        AuthorityScope scope,
        Instant requestedAt,
        Instant deadlineBase,
        byte[] fingerprint) {
    public JournalIntent {
        Objects.requireNonNull(commandId, "commandId");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(deadlineBase, "deadlineBase");
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (fingerprint.length != 32) throw new IllegalArgumentException("FINGERPRINT_LENGTH");
        if (deadlineBase.isAfter(requestedAt)) throw new IllegalArgumentException("DEADLINE_BASE_AFTER_REQUEST");
        fingerprint = fingerprint.clone();
    }

    @Override public byte[] fingerprint() { return fingerprint.clone(); }

    boolean sameBody(JournalIntent other) {
        return commandId.equals(other.commandId)
                && scope.equals(other.scope)
                && requestedAt.equals(other.requestedAt)
                && deadlineBase.equals(other.deadlineBase)
                && Arrays.equals(fingerprint, other.fingerprint);
    }
}
