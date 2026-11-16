-- D-022 identity, control and lifecycle foundation. Role bootstrap is offline.

CREATE SCHEMA rr AUTHORIZATION rr_owner; CREATE SCHEMA rr_internal AUTHORIZATION rr_owner;

REVOKE ALL ON SCHEMA rr,rr_internal FROM PUBLIC;

CREATE DOMAIN rr.opaque_id AS text COLLATE "C" CHECK(octet_length(VALUE) BETWEEN 1 AND 255);

CREATE DOMAIN rr.code AS text COLLATE "C" CHECK(octet_length(VALUE) BETWEEN 1 AND 128);

CREATE DOMAIN rr.source_status AS text COLLATE "C" CHECK(octet_length(VALUE) BETWEEN 1 AND 64);

CREATE DOMAIN rr.minor_amount AS numeric CHECK(VALUE NOT IN ('NaN'::numeric,'Infinity'::numeric,'-Infinity'::numeric) AND scale(VALUE)=0 AND abs(VALUE)<1e38::numeric);

CREATE DOMAIN rr.digest AS bytea CHECK(octet_length(VALUE)=32);

CREATE DOMAIN rr.object AS jsonb CHECK(jsonb_typeof(VALUE)='object');

CREATE TABLE rr.account (account_id uuid PRIMARY KEY, state text NOT NULL CHECK(state IN ('ACTIVE','DENY_PENDING','CLOSING','CLOSED')), generation bigint NOT NULL CHECK(generation>0), closed_at timestamptz(6), created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, row_version bigint NOT NULL DEFAULT 1 CHECK(row_version>0));

CREATE TABLE rr.identity_binding (issuer text COLLATE "C" NOT NULL CHECK(octet_length(issuer) BETWEEN 1 AND 1024), subject text COLLATE "C" NOT NULL CHECK(octet_length(subject) BETWEEN 1 AND 1024), account_id uuid NOT NULL REFERENCES rr.account, PRIMARY KEY(issuer,subject), created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP);

CREATE INDEX identity_account ON rr.identity_binding(account_id);

CREATE TABLE rr.workspace (workspace_id uuid NOT NULL, account_id uuid NOT NULL UNIQUE REFERENCES rr.account, used_bytes bigint NOT NULL DEFAULT 0 CHECK(used_bytes>=0), reserved_bytes bigint NOT NULL DEFAULT 0 CHECK(reserved_bytes>=0), nonterminal_runs bigint NOT NULL DEFAULT 0 CHECK(nonterminal_runs BETWEEN 0 AND 2), PRIMARY KEY(workspace_id), CHECK(used_bytes<=524288000-reserved_bytes), created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, row_version bigint NOT NULL DEFAULT 1 CHECK(row_version>0));

CREATE TABLE rr.case_record (workspace_id uuid NOT NULL, case_id uuid NOT NULL, state text NOT NULL CHECK(state IN ('ACTIVE','ARCHIVED','DENY_PENDING','DELETING','REMOVED')), generation bigint NOT NULL CHECK(generation>0), title text CHECK(octet_length(title)<=1024), purpose_context rr.object, provider_namespace rr.object NOT NULL, app_namespace rr.object NOT NULL, removed_at timestamptz(6), PRIMARY KEY(workspace_id,case_id), FOREIGN KEY(workspace_id) REFERENCES rr.workspace, created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, row_version bigint NOT NULL DEFAULT 1 CHECK(row_version>0));

CREATE INDEX case_list ON rr.case_record(workspace_id,state,created_at DESC,case_id DESC);

CREATE TABLE rr.service_admission (singleton smallint PRIMARY KEY CHECK(singleton=1), queued_runs bigint NOT NULL CHECK(queued_runs BETWEEN 0 AND 4), nonterminal_runs bigint NOT NULL CHECK(nonterminal_runs BETWEEN 0 AND 6), staged_pairs bigint NOT NULL CHECK(staged_pairs BETWEEN 0 AND 2), staged_bytes bigint NOT NULL CHECK(staged_bytes BETWEEN 0 AND 41943040), created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, row_version bigint NOT NULL DEFAULT 1 CHECK(row_version>0));

CREATE TABLE rr.runtime_incarnation (runtime_id uuid PRIMARY KEY, state text NOT NULL CHECK(state IN ('STARTING','READY','DRAINING','EXITED','LOCKED_RECOVERY')), predecessor_id uuid REFERENCES rr.runtime_incarnation, exit_evidence_digest rr.digest, recovery_barrier bigint CHECK(recovery_barrier>=0), created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, row_version bigint NOT NULL DEFAULT 1 CHECK(row_version>0));

CREATE INDEX runtime_predecessor ON rr.runtime_incarnation(predecessor_id);

CREATE TABLE rr.host_budget (storage_kind text PRIMARY KEY CHECK(storage_kind IN ('staging','database','journal')), capacity_bytes bigint NOT NULL CHECK(capacity_bytes>0), reservation_limit_bytes bigint NOT NULL CHECK(reservation_limit_bytes>0), free_space_floor_bytes bigint NOT NULL CHECK(free_space_floor_bytes>0), reserved_bytes bigint NOT NULL CHECK(reserved_bytes BETWEEN 0 AND reservation_limit_bytes), observed_free_bytes bigint NOT NULL CHECK(observed_free_bytes>=0), probe_at timestamptz(6) NOT NULL, policy_version rr.code NOT NULL, CHECK(reservation_limit_bytes<=capacity_bytes), CHECK(free_space_floor_bytes<=capacity_bytes), created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, row_version bigint NOT NULL DEFAULT 1 CHECK(row_version>0));

CREATE TABLE rr.deletion_operation (operation_id uuid PRIMARY KEY, account_id uuid NOT NULL, account_generation bigint NOT NULL CHECK(account_generation>0), workspace_id uuid, case_id uuid, scope_kind text NOT NULL CHECK(scope_kind IN ('ACCOUNT','CASE')), resource_id uuid NOT NULL, resource_generation bigint NOT NULL CHECK(resource_generation>0), command_key rr.code NOT NULL, fingerprint_version rr.code NOT NULL, fingerprint rr.digest NOT NULL, receipt_sha256 rr.digest, phase text NOT NULL CHECK(phase IN ('PREPARING','JOURNALED','FENCED','CLEANING','ACTIVE_DATA_REMOVED','RETIREMENT_PENDING','RETIRED')), journal_sequence bigint CHECK(journal_sequence>=0), deadline_base timestamptz(6) NOT NULL, fenced_commit_known boolean NOT NULL, active_removed_at timestamptz(6), retired_at timestamptz(6), safe_error_code rr.code, UNIQUE(account_id,account_generation,scope_kind,command_key), UNIQUE(operation_id,account_id,account_generation,scope_kind,resource_id,resource_generation), CHECK((scope_kind='ACCOUNT' AND workspace_id IS NULL AND case_id IS NULL AND resource_id=account_id) OR (scope_kind='CASE' AND workspace_id IS NOT NULL AND case_id IS NOT NULL AND resource_id=case_id)), created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, row_version bigint NOT NULL DEFAULT 1 CHECK(row_version>0));

CREATE TABLE rr.suppression (scope_kind text NOT NULL, account_id uuid NOT NULL, account_generation bigint NOT NULL CHECK(account_generation>0), resource_id uuid NOT NULL, resource_generation bigint NOT NULL CHECK(resource_generation>0), operation_id uuid NOT NULL, deny_from timestamptz(6) NOT NULL, retain_until timestamptz(6) NOT NULL, payload_retirement_verified boolean NOT NULL, retention_breach boolean NOT NULL, PRIMARY KEY(scope_kind,account_id,account_generation,resource_id,resource_generation), FOREIGN KEY(operation_id,account_id,account_generation,scope_kind,resource_id,resource_generation) REFERENCES rr.deletion_operation(operation_id,account_id,account_generation,scope_kind,resource_id,resource_generation), CHECK(retain_until>=deny_from), created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, row_version bigint NOT NULL DEFAULT 1 CHECK(row_version>0));

CREATE INDEX suppression_operation ON rr.suppression(operation_id,account_id,account_generation,scope_kind,resource_id,resource_generation); CREATE INDEX suppression_expiry ON rr.suppression(retain_until,operation_id); CREATE INDEX deletion_sweep ON rr.deletion_operation(phase,deadline_base,operation_id);

CREATE TABLE rr.recovery_cursor (singleton smallint PRIMARY KEY CHECK(singleton=1), journal_identity uuid NOT NULL, applied_sequence bigint NOT NULL CHECK(applied_sequence>=0), checkpoint_digest rr.digest NOT NULL, service_clock_high_water timestamptz(6) NOT NULL, runtime_id uuid NOT NULL REFERENCES rr.runtime_incarnation, created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP, row_version bigint NOT NULL DEFAULT 1 CHECK(row_version>0));

CREATE INDEX recovery_runtime ON rr.recovery_cursor(runtime_id);
