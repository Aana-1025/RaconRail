package dev.reconrail.lifecycle;

import java.time.Duration;
import java.util.Objects;

/** Orders local drain, journal confirmation and the database t0 fence without overlapping them. */
public final class DenialCoordinator {
    public enum Status { FENCED, DRAIN_PENDING, JOURNAL_UNKNOWN, FENCE_PENDING, FENCE_FAILED }
    public record Outcome(Status status, IntentJournal.AppendReceipt receipt, Exception failure) {
        public Outcome {
            Objects.requireNonNull(status, "status");
            if ((status == Status.JOURNAL_UNKNOWN || status == Status.FENCE_FAILED) != (failure != null)) {
                throw new IllegalArgumentException("OUTCOME_FAILURE_MISMATCH");
            }
        }
    }
    @FunctionalInterface public interface FenceAction {
        void commit(IntentJournal.AppendReceipt receipt) throws Exception;
    }

    private final IoGate gate;
    private final IntentJournal journal;

    public DenialCoordinator(IoGate gate, IntentJournal journal) {
        this.gate = Objects.requireNonNull(gate, "gate");
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    public Outcome deny(JournalIntent intent, FenceAction fence) throws InterruptedException {
        Objects.requireNonNull(intent, "intent"); Objects.requireNonNull(fence, "fence");
        if (gate.prepareDeny(intent.scope()) == IoGate.DrainResult.PENDING) {
            return new Outcome(Status.DRAIN_PENDING, null, null);
        }
        final IntentJournal.AppendReceipt receipt;
        try {
            // Deliberately outside the local write lock and any database transaction.
            receipt = journal.putIntent(intent);
        } catch (Exception failure) {
            return new Outcome(Status.JOURNAL_UNKNOWN, null, failure);
        }
        try {
            boolean committed = gate.fence(intent.scope(), Duration.ofSeconds(2), () -> fence.commit(receipt));
            return new Outcome(committed ? Status.FENCED : Status.FENCE_PENDING, receipt, null);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (Exception failure) {
            return new Outcome(Status.FENCE_FAILED, receipt, failure);
        }
    }
}
