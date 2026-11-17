package dev.reconrail.lifecycle;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Process-local admission and drain barrier. The database remains the durable authority.
 * Stripe collisions share scheduling only; exact account/resource states are never shared.
 */
public final class IoGate {
    public static final int STRIPES = 64;
    public static final Duration ORDINARY_WAIT = Duration.ofMillis(250);
    public static final Duration LIFECYCLE_WAIT = Duration.ofSeconds(2);

    public enum DrainResult { DRAINED, PENDING }
    private enum State { ACTIVE, DRAINING, DENY_PENDING, FENCED }

    @FunctionalInterface
    public interface CheckedAction { void run() throws Exception; }

    private record AccountKey(UUID id, long generation) { }

    private final ReentrantReadWriteLock[] stripes = new ReentrantReadWriteLock[STRIPES];
    private final ConcurrentHashMap<AccountKey, AtomicReference<State>> accounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<AuthorityScope, AtomicReference<State>> resources = new ConcurrentHashMap<>();

    public IoGate() {
        for (int i = 0; i < stripes.length; i++) stripes[i] = new ReentrantReadWriteLock(true);
    }

    public void withShared(AuthorityScope scope, CheckedAction action) throws Exception {
        withShared(scope, ORDINARY_WAIT, action);
    }

    public void withShared(AuthorityScope scope, Duration wait, CheckedAction action) throws Exception {
        requirePositive(wait);
        var stripe = stripe(scope.accountId());
        var read = stripe.readLock();
        if (!read.tryLock(wait.toNanos(), TimeUnit.NANOSECONDS)) {
            throw new AdmissionTimeoutException();
        }
        try {
            requireActive(accountState(scope));
            if (scope.kind() == AuthorityScope.Kind.CASE) requireActive(resourceState(scope));
            action.run();
        } finally {
            read.unlock();
        }
    }

    /**
     * Closes admission before waiting, so timeout or interruption cannot silently reopen work.
     */
    public DrainResult prepareDeny(AuthorityScope scope) throws InterruptedException {
        return prepareDeny(scope, LIFECYCLE_WAIT);
    }

    public DrainResult prepareDeny(AuthorityScope scope, Duration wait) throws InterruptedException {
        requirePositive(wait);
        var stripe = stripe(scope.accountId());
        if (stripe.getReadHoldCount() != 0) throw new IllegalStateException("READ_TO_WRITE_UPGRADE_FORBIDDEN");
        var state = denialState(scope);
        state.compareAndSet(State.ACTIVE, State.DRAINING);
        if (state.get() == State.ACTIVE) throw new IllegalStateException("DENIAL_STATE_RACE");
        var write = stripe.writeLock();
        if (!write.tryLock(wait.toNanos(), TimeUnit.NANOSECONDS)) return DrainResult.PENDING;
        try {
            state.compareAndSet(State.DRAINING, State.DENY_PENDING);
            return DrainResult.DRAINED;
        } finally {
            write.unlock();
        }
    }

    /** Runs only the database fence callback under the exclusive local stripe. */
    public boolean fence(AuthorityScope scope, Duration wait, CheckedAction action) throws Exception {
        requirePositive(wait);
        var stripe = stripe(scope.accountId());
        if (stripe.getReadHoldCount() != 0) throw new IllegalStateException("READ_TO_WRITE_UPGRADE_FORBIDDEN");
        var state = denialState(scope);
        if (state.get() == State.ACTIVE || state.get() == State.DRAINING) {
            throw new IllegalStateException("DENIAL_NOT_DRAINED");
        }
        var write = stripe.writeLock();
        if (!write.tryLock(wait.toNanos(), TimeUnit.NANOSECONDS)) return false;
        try {
            action.run();
            state.set(State.FENCED);
            return true;
        } finally {
            write.unlock();
        }
    }

    boolean locallyDenied(AuthorityScope scope) {
        return accountState(scope).get() != State.ACTIVE
                || (scope.kind() == AuthorityScope.Kind.CASE && resourceState(scope).get() != State.ACTIVE);
    }

    int stripeIndex(UUID accountId) {
        return Math.floorMod(accountId.hashCode(), STRIPES);
    }

    private ReentrantReadWriteLock stripe(UUID accountId) { return stripes[stripeIndex(accountId)]; }
    private AccountKey accountKey(AuthorityScope scope) { return new AccountKey(scope.accountId(), scope.accountGeneration()); }
    private AtomicReference<State> accountState(AuthorityScope scope) {
        return accounts.computeIfAbsent(accountKey(scope), ignored -> new AtomicReference<>(State.ACTIVE));
    }
    private AtomicReference<State> resourceState(AuthorityScope scope) {
        return resources.computeIfAbsent(scope, ignored -> new AtomicReference<>(State.ACTIVE));
    }
    private AtomicReference<State> denialState(AuthorityScope scope) {
        return scope.kind() == AuthorityScope.Kind.ACCOUNT ? accountState(scope) : resourceState(scope);
    }
    private static void requireActive(AtomicReference<State> state) {
        if (state.get() != State.ACTIVE) throw new AuthorityDeniedException();
    }
    private static void requirePositive(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("POSITIVE_WAIT_REQUIRED");
        }
    }

    public static final class AuthorityDeniedException extends RuntimeException {
        public AuthorityDeniedException() { super("AUTHORITY_DENIED", null, false, false); }
    }
    public static final class AdmissionTimeoutException extends RuntimeException {
        public AdmissionTimeoutException() { super("ADMISSION_TIMEOUT", null, false, false); }
    }
}
