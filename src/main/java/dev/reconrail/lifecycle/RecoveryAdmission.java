package dev.reconrail.lifecycle;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.UUID;

/**
 * Opens a runtime only from exact predecessor-exit evidence and an independently authoritative
 * barrier matching the fully replayed local head. There is intentionally no force-ready method.
 */
public final class RecoveryAdmission {
    public enum State { LOCKED_RECOVERY, READY, DRAINING }

    public record IndependentBarrier(UUID journalId, long sequence, byte[] digest) {
        public IndependentBarrier {
            if (journalId == null || sequence < 0 || digest == null || digest.length != 32) {
                throw new IllegalArgumentException("INVALID_BARRIER_EVIDENCE");
            }
            digest = digest.clone();
        }
        @Override public byte[] digest() { return digest.clone(); }
    }

    /** Trusted adapter must validate supervisor/host evidence and throw when it cannot. */
    @FunctionalInterface public interface PriorExitVerifier { void requireVerifiedExit(UUID predecessorId) throws IOException; }
    /** This port is intentionally distinct from the local journal's LOCAL_ONLY barrier API. */
    @FunctionalInterface public interface IndependentBarrierReader {
        IndependentBarrier readIndependentBarrier(UUID journalId) throws IOException;
    }

    private State state = State.LOCKED_RECOVERY;

    public synchronized void openEpoch(UUID predecessorId, IntentJournal.LocalBarrier replayed,
            PriorExitVerifier exitVerifier, IndependentBarrierReader barrierReader) throws IOException {
        Objects.requireNonNull(predecessorId, "predecessorId"); Objects.requireNonNull(replayed, "replayed");
        Objects.requireNonNull(exitVerifier, "exitVerifier"); Objects.requireNonNull(barrierReader, "barrierReader");
        if (state != State.LOCKED_RECOVERY) throw new IllegalStateException("RECOVERY_NOT_LOCKED");
        exitVerifier.requireVerifiedExit(predecessorId);
        IndependentBarrier barrier = barrierReader.readIndependentBarrier(replayed.journalId());
        if (barrier == null || !replayed.journalId().equals(barrier.journalId())
                || replayed.sequence() != barrier.sequence()
                || !MessageDigest.isEqual(replayed.recordDigest(), barrier.digest())) {
            throw new IOException("INDEPENDENT_BARRIER_UNPROVEN");
        }
        state = State.READY;
    }

    public synchronized void stopAdmission() {
        if (state == State.READY) state = State.DRAINING;
    }

    public synchronized State state() { return state; }
}
