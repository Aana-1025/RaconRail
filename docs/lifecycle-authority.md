# Lifecycle authority and response gating

ReconRail treats account and case generations as part of authority identity. A request carrying an old generation cannot regain access merely because the same UUID exists. Case authority also includes the trusted workspace ID used for PostgreSQL row-level security.

## Local I/O gate

`IoGate` uses 64 fair account-hash read/write stripes. Hash collisions share scheduling, never authority state. Ordinary work acquires a timed, interruptible shared permit and checks the exact account and resource generation. Lifecycle denial marks the exact scope as draining before waiting for an exclusive permit, so a timeout or interruption leaves new admission closed. Existing work is never force-unlocked, and a shared permit cannot be upgraded to an exclusive permit.

The local gate is coordination, not durable truth. Durable state is rechecked through a database authority probe. Account denial applies to every resource in that account generation; case denial applies only to that case generation.

## Response boundary

`GatedServletOutputStream` splits writes into chunks of at most 64 KiB. On the actual writing thread, each chunk acquires a fresh shared permit, completes the database generation/state probe, and writes while the permit remains held. Explicit flush and close operations are gated because they can cause buffered data to reach the socket.

The one-second I/O budget can detect a blocking write that returned late, but Java blocking Servlet I/O cannot preempt that call or recall bytes already accepted by the container or peer. Later chunks are denied. Callers needing preemptible writes must use a separately verified asynchronous adapter.

## Denial order

The local protocol is:

1. Close exact-scope admission and drain existing bounded I/O.
2. Append the immutable intent outside local locks and database transactions.
3. Reacquire the exclusive local permit and execute the database fence callback.
4. Treat the committed database fence as t0.

An ambiguous journal acknowledgement stays denied. Retry uses the same command and canonical body; a different body is a conflict. A drain or fence timeout returns pending and never reopens the scope.

## Local intent journal

`LocalIntentJournal` is a single-writer, length-framed append file with monotonically increasing sequence numbers, previous-record digest chaining, HMAC-SHA-256 authentication, and `FileChannel.force(true)` after every append. Startup replays the complete file and fails closed on truncation, invalid framing, gaps, missing retained keys, MAC/digest/chain failure, or conflicting command reuse.

The resulting barrier is explicitly `LOCAL_ONLY`. A local force, hash, or HMAC does not prove external freshness, directory-entry durability, off-host survival, or disaster recovery.

## Runtime recovery boundary

`RecoveryAdmission` begins locked. It can become ready only after a trusted prior-exit verifier accepts the exact predecessor and a distinct independent-barrier port returns the same journal identity, sequence and head digest as the fully replayed local journal. The local journal cannot implement that independent port, and there is no force-ready API.

Concrete supervisor evidence, hosted independent journal/barrier storage, worker/session reconciliation, lifecycle cleanup, and complete account/case closure remain later integration work. This module does not claim those boundaries are complete.
