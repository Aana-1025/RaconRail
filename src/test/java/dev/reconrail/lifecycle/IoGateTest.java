package dev.reconrail.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class IoGateTest {
    private static AuthorityScope account(long generation) {
        return AuthorityScope.account(UUID.fromString("00000000-0000-0000-0000-000000000001"), generation);
    }
    private static AuthorityScope caseScope(UUID id) {
        return AuthorityScope.caseScope(account(1).accountId(), 1,
                UUID.fromString("00000000-0000-0000-0000-000000000099"), id, 3);
    }

    @Test void accountDenialClosesAllResourcesButNotANewGeneration() throws Exception {
        var gate = new IoGate(); var oldCase = caseScope(UUID.randomUUID());
        assertEquals(IoGate.DrainResult.DRAINED, gate.prepareDeny(account(1)));
        assertThrows(IoGate.AuthorityDeniedException.class, () -> gate.withShared(oldCase, () -> {}));
        assertDoesNotThrow(() -> gate.withShared(account(2), () -> {}));
    }

    @Test void caseDenialDoesNotShareAuthorityWithAnotherCaseOrHashCollision() throws Exception {
        var gate = new IoGate();
        var denied = caseScope(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        var sibling = caseScope(UUID.fromString("00000000-0000-0000-0000-000000000011"));
        assertEquals(IoGate.DrainResult.DRAINED, gate.prepareDeny(denied));
        assertThrows(IoGate.AuthorityDeniedException.class, () -> gate.withShared(denied, () -> {}));
        assertDoesNotThrow(() -> gate.withShared(sibling, () -> {}));

        var collisionA = AuthorityScope.account(new UUID(0, 0), 1);
        var collisionB = AuthorityScope.account(new UUID(1, 1), 1);
        assertEquals(gate.stripeIndex(collisionA.accountId()), gate.stripeIndex(collisionB.accountId()));
        assertEquals(IoGate.DrainResult.DRAINED, gate.prepareDeny(collisionA));
        assertDoesNotThrow(() -> gate.withShared(collisionB, () -> {}));
    }

    @Test void timedOutDrainStaysClosedAndNeverForceUnlocks() throws Exception {
        var gate = new IoGate(); var scope = account(1);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> holder = executor.submit(() -> {
                try { gate.withShared(scope, () -> { entered.countDown(); release.await(); }); }
                catch (Exception failure) { throw new CompletionException(failure); }
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(IoGate.DrainResult.PENDING, gate.prepareDeny(scope, Duration.ofMillis(20)));
            release.countDown(); holder.get(2, TimeUnit.SECONDS);
            assertThrows(IoGate.AuthorityDeniedException.class, () -> gate.withShared(scope, () -> {}));
            assertEquals(IoGate.DrainResult.DRAINED, gate.prepareDeny(scope));
        }
    }

    @Test void interruptedDrainRemainsClosed() throws Exception {
        var gate = new IoGate(); var scope = account(1);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> holder = executor.submit(() -> {
                try { gate.withShared(scope, () -> { entered.countDown(); release.await(); }); }
                catch (Exception failure) { throw new CompletionException(failure); }
            });
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            Thread drainer = Thread.ofVirtual().start(() -> {
                try { gate.prepareDeny(scope, Duration.ofSeconds(5)); }
                catch (InterruptedException expected) { interrupted.set(true); }
            });
            Thread.sleep(30); drainer.interrupt(); drainer.join(2000);
            assertTrue(interrupted.get()); release.countDown(); holder.get(2, TimeUnit.SECONDS);
            assertThrows(IoGate.AuthorityDeniedException.class, () -> gate.withShared(scope, () -> {}));
        }
    }

    @Test void readToWriteUpgradeIsRejectedInsteadOfDeadlocking() {
        var gate = new IoGate(); var scope = account(1);
        assertThrows(IllegalStateException.class, () -> gate.withShared(scope, () -> gate.prepareDeny(scope)));
    }
}
