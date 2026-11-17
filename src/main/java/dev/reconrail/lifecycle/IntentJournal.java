package dev.reconrail.lifecycle;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public interface IntentJournal {
    AppendReceipt putIntent(JournalIntent intent) throws IOException;
    Optional<JournalIntent> getIntent(UUID commandId);
    LocalBarrier readLocalBarrier();

    record AppendReceipt(UUID journalId, long sequence, byte[] recordDigest) {
        public AppendReceipt {
            if (journalId == null || sequence < 1 || recordDigest == null || recordDigest.length != 32) {
                throw new IllegalArgumentException("INVALID_APPEND_RECEIPT");
            }
            recordDigest = recordDigest.clone();
        }
        @Override public byte[] recordDigest() { return recordDigest.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof AppendReceipt that && journalId.equals(that.journalId)
                    && sequence == that.sequence && Arrays.equals(recordDigest, that.recordDigest);
        }
        @Override public int hashCode() { return 31 * Objects.hash(journalId, sequence) + Arrays.hashCode(recordDigest); }
    }

    enum EvidenceClass { LOCAL_ONLY, INDEPENDENT }

    record LocalBarrier(UUID journalId, long sequence, byte[] recordDigest, EvidenceClass evidenceClass) {
        public LocalBarrier {
            if (journalId == null || sequence < 0 || recordDigest == null || recordDigest.length != 32
                    || evidenceClass == null) throw new IllegalArgumentException("INVALID_BARRIER");
            recordDigest = recordDigest.clone();
        }
        @Override public byte[] recordDigest() { return recordDigest.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof LocalBarrier that && journalId.equals(that.journalId)
                    && sequence == that.sequence && evidenceClass == that.evidenceClass
                    && Arrays.equals(recordDigest, that.recordDigest);
        }
        @Override public int hashCode() {
            return 31 * Objects.hash(journalId, sequence, evidenceClass) + Arrays.hashCode(recordDigest);
        }
    }
}
