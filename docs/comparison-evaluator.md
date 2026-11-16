# Qualified comparison evaluator

The pure evaluator compares frozen, validated sandbox projections using stripe-card-order-v1 / rr-currency-v1 / stripe-card-order-normalizer-v1. It has no Spring, JDBC, network or live-clock dependency. This foundation is not wired to an upload endpoint, worker, persistence layer or result screen.

## Input boundary

EvidenceComparison validates the full pair against trusted case namespaces, then constructs immutable facts, explicit presence information, config, digests, versions, input identity and reference time. The core receives these trusted frozen DTOs; directly constructing DTOs is not a substitute for evidence validation or authorization. Unsupported versions fail rather than silently falling back. New evidence/config creates another frozen input/run context; results retain their original context.

## Independent outcomes

Every order uses its explicit case-sensitive PaymentIntent reference. Multiple orders claiming a payment are ambiguous. Missing references/targets are unassessed, never guessed from amounts or times. Provider eligibility requires the latest Charge to corroborate success, full automatic/automatic_async captured card collection and the declared original-flow scope. Missing facts and conflicting provider assertions cannot become application discrepancies. Known unsupported states, methods, reversals and coherent partial capture are marked unsupported. An isolated contradictory captured amount is evidence conflict; matching received/captured partial amounts establish an unsupported partial flow.

State, currency and amount have separate classifications/counts. Timing uncertainty cannot hide an otherwise eligible amount comparison. Unsupported monetary currency cannot hide a justified state discrepancy. A stable application expectation revision is required for monetary comparisons. USD/INR/JPY are the reviewed set; different currencies are never numerically compared or converted. Original amounts use arbitrary-precision integer minor units, never floats, tolerance or netted attempts.

The conservative elapsed bound subtracts application uncertainty and both the PI and latest Charge upper observation bounds. Awaiting-payment discrepancy uses elapsed >= declared grace, including equality. Paid consistency needs known nonnegative ordering but no grace. Missing grace never gets a default. Unrepresentable derived instants make timing unknown. Freshness/reference limitations are explicit qualifiers and do not establish current source correctness.

## Counts, provenance and repeatability

All input PaymentIntents, Charges and orders are counted independently of findings. Prior attempts associated with an observed PI do not become additional payments. Orders, unmatched PaymentIntents and orphan Charges receive qualified check units; eligible counts equal consistent plus discrepancy. Empty input has zero eligible checks. There is no global-clean flag or root-cause/repair conclusion.

Findings contain bounded source references with accurate original record indices. Business conclusions sort by exact UTF-8 subject keys and rule. Semantic digests exclude record-index provenance and operational run IDs, so equivalent record permutations preserve conclusions while raw digests/locators remain different. The result retains the frozen input/reference for attribution.

At most20000 input records can yield60000 check findings. Canonical encoding therefore allows up to60000 SemanticFinding list entries while retaining its64MiB byte limit; other list and artifact limits are unchanged. This corrects an earlier generic10000-item codec assumption. It is not a throughput or worker-memory benchmark.

## Verification

`./ops/rr.ps1 verify -Suite domain -Task RR-05` runs the codec and pure evaluator tests. The tests materialize24 approved synthetic decision-table scenarios plus targeted time/money/version/permutation/count cases. Deliberate local mutants exercise inclusive grace, ambiguous claims, currency and amount comparisons; mutation evidence is a local verification artifact. Hosted CI, live capture validation, worker cancellation/fencing, persistence and complete financial product readiness remain separate verification work.
