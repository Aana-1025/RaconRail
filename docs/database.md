# Database foundation

Versioned SQL migrations define 24 application relations in rr and three vendor framework relations in rr_internal. An offline Flyway12.4.0 Maven profile applies them; application startup does not connect as a migration owner or initialize framework tables. Never edit an applied script in a persistent environment: roll forward with a new migration.

## Role boundary

ops/db/bootstrap.sql is a one-time offline administrator script for an explicitly selected database named reconrail. It creates non-login owner/control/worker/maintenance roles and the migration-history namespace. It does not provision passwords or application logins. Actual environment provisioning and recovery remain separate work.

The migration login must be an offline member of rr_owner; the Maven profile switches to rr_owner. Supply RR_DB_URL, RR_DB_USER and RR_DB_PASSWORD through private environment injection, then invoke the wrapper with `-Pdatabase flyway:migrate` or `flyway:validate`. Never provide owner credentials to the web application, put them in Git or print them. The disposable test harness creates its own temporary migration identity. The migration process uses UTC to avoid platform-specific timezone aliases.

Runtime roles cannot create/alter/truncate tables, change migration history, bypass RLS or assume the owner role. Column grants restrict updates. Maintenance is a trusted cleanup role, not an endpoint-selected authority; its explicit cross-workspace policies require lifecycle fencing in the future service layer.

## Integrity

Composite foreign keys preserve workspace/case/input/run lineage. Complete inputs require both provider and application artifacts through deferred foreign keys. Normalized source rows must be inserted in the same transaction as their input: a trigger compares both the input row xmin and a full-width accepted_transaction marker to the current transaction ID, so later source appends fail. The full-width marker prevents a 32-bit transaction-ID wrap collision. These markers enforce transaction membership, not business identity; source rows still need full scoped keys. This added internal field and its eight-byte logical-accounting treatment need incorporation into the input codec before ingestion is wired. Logical restore must not reopen ingestion without migration/restore verification. Money uses unconstrained numeric with integral, finite and 38-digit bounds, avoiding scale coercion. External payment references remain observations: missing targets and ambiguous order bindings can be retained.

Fifteen tenant relations enable and force row-level security. Each ordinary transaction sets rr.workspace_id with transaction-local scope from trusted authorization. Missing context denies access; malformed context errors. Account/identity/lifecycle records without workspace scope still need scoped repositories before application use. RLS is not protection against an application that arbitrarily changes its trusted context.

Published results require a sealed manifest. Control reads exclude private findings/manifests. Seal-dependent triggers serialize finding writes against manifest sealing, reject later mutations and check finding totals/count arithmetic. The service layer must still validate canonical bytes/digests, source counts, current tokens, quotas, account generation and complete business transitions. Database totals alone cannot prove financial correctness.

## Verification and limits

The disposable suite exercises fresh migration, upgrade from V1, rollback of deliberately failed DDL, checksum mismatch detection, restricted-role SQL, context reset, complete pairs, exact money, active-run contention, publication/seals and child-first cleanup. These are real PostgreSQL checks, not H2 or mocks. Tests remove only their owned synthetic container.

Application authentication/JDBC repositories, actual session serialization, scheduler transaction enlistment, host/process death proof, restore/suppression replay, canonical accounting and representative query/performance measurements remain future work. A test clearing a synthetic execution slot is not evidence that a real worker exited. Hosted CI remains unverified.
