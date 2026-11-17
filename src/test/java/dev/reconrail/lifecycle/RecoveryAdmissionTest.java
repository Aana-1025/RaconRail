package dev.reconrail.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecoveryAdmissionTest {
    private static final UUID PREDECESSOR = UUID.fromString("00000000-0000-0000-0000-000000000701");
    private static final UUID JOURNAL = UUID.fromString("00000000-0000-0000-0000-000000000702");

    @Test void sameDiskBarrierAndUnverifiedExitCannotOpenRecovery() {
        var admission = new RecoveryAdmission(); var local = localBarrier();
        IOException exitFailure = assertThrows(IOException.class, () -> admission.openEpoch(PREDECESSOR, local,
                id -> { throw new IOException("PRIOR_EXIT_UNPROVEN"); },
                id -> independentBarrier()));
        assertEquals("PRIOR_EXIT_UNPROVEN", exitFailure.getMessage());
        assertEquals(RecoveryAdmission.State.LOCKED_RECOVERY, admission.state());

        IOException barrierFailure = assertThrows(IOException.class, () -> admission.openEpoch(PREDECESSOR, local,
                id -> {}, id -> { throw new IOException("INDEPENDENT_BARRIER_UNAVAILABLE"); }));
        assertEquals("INDEPENDENT_BARRIER_UNAVAILABLE", barrierFailure.getMessage());
        assertEquals(RecoveryAdmission.State.LOCKED_RECOVERY, admission.state());
    }

    @Test void exactIndependentEvidenceOpensOnceAndCanOnlyDrainAfterward() throws Exception {
        var admission = new RecoveryAdmission();
        admission.openEpoch(PREDECESSOR, localBarrier(), id -> {}, id -> independentBarrier());
        assertEquals(RecoveryAdmission.State.READY, admission.state());
        assertThrows(IllegalStateException.class, () -> admission.openEpoch(
                PREDECESSOR, localBarrier(), id -> {}, id -> independentBarrier()));
        admission.stopAdmission(); assertEquals(RecoveryAdmission.State.DRAINING, admission.state());
    }

    @Test void staleOrDifferentIndependentHeadCannotAuthorizeReplay() {
        var local = localBarrier(); var admission = new RecoveryAdmission();
        assertThrows(IOException.class, () -> admission.openEpoch(PREDECESSOR, local, id -> {},
                id -> new RecoveryAdmission.IndependentBarrier(id, local.sequence() + 1, local.recordDigest())));
        assertThrows(IOException.class, () -> admission.openEpoch(PREDECESSOR, local, id -> {},
                id -> new RecoveryAdmission.IndependentBarrier(id, local.sequence(), digest((byte) 9))));
    }

    private static RecoveryAdmission.IndependentBarrier independentBarrier() {
        return new RecoveryAdmission.IndependentBarrier(JOURNAL, 8, digest((byte) 2));
    }
    private static IntentJournal.LocalBarrier localBarrier() {
        return new IntentJournal.LocalBarrier(JOURNAL, 8, digest((byte) 2), IntentJournal.EvidenceClass.LOCAL_ONLY);
    }
    private static byte[] digest(byte value) { byte[] bytes = new byte[32]; Arrays.fill(bytes, value); return bytes; }
}
