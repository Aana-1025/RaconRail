# Evidence and codec foundation

The pure Java evidence/codec package validates projected sandbox evidence bytes. It provides no upload endpoint, persistence, capture adapter or financial evaluator. A complete pair is returned only after both artifacts and their shared capture/namespaces match the trusted case namespaces. Independently parsing one artifact is not pair acceptance.

## Strict evidence boundary

Input is UTF-8, at most10MiB per artifact, without a BOM, invalid Unicode, NUL, duplicate JSON members or trailing documents. Records are bounded to10000 per file, nesting to16 and decoded scalars to64KiB UTF-8. Every record is validated; duplicate scoped kind/ID identities and unknown fields reject the pair. Parser errors expose only INVALID_EVIDENCE, without rejected values or parser causes. Input arrays are copied before parsing/hashing. Returned JSON trees are immutable and preserve absent versus explicit null.

Money is a canonical signed decimal string with at most38 digits; no float, exponent, coercion, -0 or leading zero. Observation intervals, bounded durations and strict Gregorian UTC microsecond instants are checked. Missing observations and unknown source labels remain evidence, not defaulted successes. Record locators use artifact role, zero-based record index and an allowlisted field pointer. Hashes identify bytes; they do not authenticate the exporter.

The pinned projection profile is an input contract, not proof of a live provider capture. HTTP filename/body bounds, cancellation, streaming transport and pre-staging authentication are future adapter responsibilities. The current parser accepts an already bounded byte array and creates an in-memory immutable model; it does not stream records to persistence.

## Canonical bytes and accounting

CanonicalCodec implements the33 fixed layouts with domain/version framing, explicit presence/type tags and schema-ordered fields. Java and TypeScript implementations are checked against the existing six vectors. Trusted callers select the domain/layout and prepare semantic ordering; this is not permission to accept arbitrary caller-provided fingerprints or unsorted financial results. Raw evidence whitespace intentionally changes its original-byte digest.

AccountingCodec separately implements24 row schemas. Logical charges include raw bytes once, structured typed bytes and128 bytes per row. JSON objects sort keys by UTF-8 bytes, exact integers remain arbitrary precision and floating values are rejected. Excluded accounting fields stay excluded. This is not PostgreSQL physical disk usage.

The input row's approved accepted_transaction marker is unsigned64-bit:8 value bytes plus1 normal presence byte. The accounting manifest and packaged resource include it. No previously persisted application accounting rows exist in this foundation; future ingestion must obtain and charge the actual stored marker within its accepted-input transaction. Any later incompatible persisted-codec change requires explicit version/migration/reconciliation.

Frontend exactMoney formats USD/INR with2 decimal digits and JPY with0 without converting money to Number; unsupported currencies retain labelled minor units. Canonical TypeScript timestamps use exact BigInt microseconds after strict calendar validation. These helpers are not yet connected to real financial result screens.

## Verification

`./ops/rr.ps1 verify -Suite domain -Task RR-04` runs the RR-04 codec core scope. The general domain evaluator and all suites remain blocked. `build` additionally checks the shell and packages static assets. Hosted CI and actual provider capture remain unverified.
