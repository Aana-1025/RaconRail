-- Runtime grants, workspace RLS and seal-dependent immutability.

REVOKE ALL ON ALL TABLES IN SCHEMA rr,rr_internal FROM PUBLIC; REVOKE ALL ON ALL FUNCTIONS IN SCHEMA rr FROM PUBLIC;

GRANT USAGE ON SCHEMA rr,rr_internal TO rr_control,rr_worker,rr_maintenance; REVOKE USAGE ON DOMAIN rr.opaque_id,rr.code,rr.source_status,rr.minor_amount,rr.digest,rr.object FROM PUBLIC; GRANT USAGE ON DOMAIN rr.opaque_id,rr.code,rr.source_status,rr.minor_amount,rr.digest,rr.object TO rr_control,rr_worker,rr_maintenance;

ALTER TABLE rr.workspace ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.workspace FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.workspace TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.workspace TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.case_record ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.case_record FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.case_record TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.case_record TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.intake ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.intake FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.intake TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.intake TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.input_version ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.input_version FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.input_version TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.input_version TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.artifact ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.artifact FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.artifact TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.artifact TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.provider_payment ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.provider_payment FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.provider_payment TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.provider_payment TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.provider_charge ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.provider_charge FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.provider_charge TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.provider_charge TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.application_order ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.application_order FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.application_order TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.application_order TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.quota_allocation ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.quota_allocation FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.quota_allocation TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.quota_allocation TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.analysis_run ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.analysis_run FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.analysis_run TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.analysis_run TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.run_attempt ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.run_attempt FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.run_attempt TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.run_attempt TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.output_manifest ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.output_manifest FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.output_manifest TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.output_manifest TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.finding ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.finding FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.finding TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.finding TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.publication ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.publication FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.publication TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.publication TO rr_maintenance USING(true) WITH CHECK(true);

ALTER TABLE rr.case_execution_slot ENABLE ROW LEVEL SECURITY; ALTER TABLE rr.case_execution_slot FORCE ROW LEVEL SECURITY;

CREATE POLICY workspace_scope ON rr.case_execution_slot TO rr_control,rr_worker USING(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid) WITH CHECK(workspace_id=nullif(current_setting('rr.workspace_id',true),'')::uuid);

CREATE POLICY cleanup_scope ON rr.case_execution_slot TO rr_maintenance USING(true) WITH CHECK(true);

CREATE POLICY published_only ON rr.finding AS RESTRICTIVE FOR SELECT TO rr_control USING(EXISTS(SELECT 1 FROM rr.publication p WHERE p.workspace_id=finding.workspace_id AND p.case_id=finding.case_id AND p.run_id=finding.run_id AND p.attempt_no=finding.attempt_no));

CREATE POLICY published_only ON rr.output_manifest AS RESTRICTIVE FOR SELECT TO rr_control USING(EXISTS(SELECT 1 FROM rr.publication p WHERE p.workspace_id=output_manifest.workspace_id AND p.case_id=output_manifest.case_id AND p.run_id=output_manifest.run_id AND p.attempt_no=output_manifest.attempt_no));

GRANT SELECT,INSERT ON rr.account,rr.identity_binding,rr.workspace,rr.case_record,rr.service_admission,rr.intake,rr.input_version,rr.artifact,rr.provider_payment,rr.provider_charge,rr.application_order,rr.command_effect,rr.quota_allocation,rr.analysis_run TO rr_control;

GRANT SELECT ON rr.publication,rr.output_manifest,rr.finding,rr.case_execution_slot TO rr_control;

GRANT SELECT ON rr.service_admission,rr.workspace,rr.case_record,rr.input_version,rr.artifact,rr.provider_payment,rr.provider_charge,rr.application_order,rr.analysis_run,rr.quota_allocation,rr.runtime_incarnation,rr.run_attempt,rr.output_manifest,rr.finding,rr.publication,rr.case_execution_slot TO rr_worker;

GRANT INSERT ON rr.run_attempt,rr.output_manifest,rr.finding,rr.publication,rr.case_execution_slot TO rr_worker;

GRANT UPDATE(state,generation,closed_at,updated_at,row_version) ON rr.account TO rr_control;

GRANT UPDATE(used_bytes,reserved_bytes,nonterminal_runs,updated_at,row_version) ON rr.workspace TO rr_control,rr_worker;

GRANT UPDATE(state,generation,title,purpose_context,removed_at,updated_at,row_version) ON rr.case_record TO rr_control;

GRANT UPDATE(queued_runs,nonterminal_runs,staged_pairs,staged_bytes,updated_at,row_version) ON rr.service_admission TO rr_control,rr_worker;

GRANT UPDATE(state,received_bytes,slot_held,last_error_code,updated_at,row_version) ON rr.intake TO rr_control;

GRANT UPDATE(state,reserved_bytes,charged_bytes,updated_at,row_version) ON rr.quota_allocation TO rr_control,rr_worker;

GRANT UPDATE(operational_phase,terminal_at,updated_at,row_version) ON rr.analysis_run TO rr_control,rr_worker;

GRANT UPDATE(state,stopped_at,output_bytes,failure_code,updated_at,row_version) ON rr.run_attempt TO rr_worker;

GRANT UPDATE(sealed,input_count,assessed_count,unassessed_count,finding_count,encoded_bytes,digest,updated_at,row_version) ON rr.output_manifest TO rr_worker;

GRANT UPDATE(owner_run_id,owner_attempt_no,runtime_id,token,held_since,updated_at,row_version) ON rr.case_execution_slot TO rr_worker;

GRANT SELECT,DELETE ON rr.account,rr.identity_binding,rr.service_admission,rr.runtime_incarnation,rr.host_budget,rr.deletion_operation,rr.suppression,rr.recovery_cursor,rr.command_effect,rr.workspace,rr.case_record,rr.intake,rr.input_version,rr.artifact,rr.provider_payment,rr.provider_charge,rr.application_order,rr.quota_allocation,rr.analysis_run,rr.run_attempt,rr.output_manifest,rr.finding,rr.publication,rr.case_execution_slot TO rr_maintenance;

GRANT UPDATE(state,generation,closed_at,updated_at,row_version) ON rr.account TO rr_maintenance;

GRANT UPDATE(state,generation,title,purpose_context,removed_at,updated_at,row_version) ON rr.case_record TO rr_maintenance;

GRANT UPDATE(used_bytes,reserved_bytes,nonterminal_runs,updated_at,row_version) ON rr.workspace TO rr_maintenance;

GRANT UPDATE(state,exit_evidence_digest,recovery_barrier,updated_at,row_version) ON rr.runtime_incarnation TO rr_maintenance;

GRANT UPDATE(queued_runs,nonterminal_runs,staged_pairs,staged_bytes,updated_at,row_version) ON rr.service_admission TO rr_maintenance;

GRANT UPDATE(state,reserved_bytes,charged_bytes,updated_at,row_version) ON rr.quota_allocation TO rr_maintenance;

GRANT UPDATE(owner_run_id,owner_attempt_no,runtime_id,token,held_since,updated_at,row_version) ON rr.case_execution_slot TO rr_maintenance;

GRANT UPDATE(reserved_bytes,observed_free_bytes,probe_at,updated_at,row_version) ON rr.host_budget TO rr_maintenance;

GRANT UPDATE(journal_identity,applied_sequence,checkpoint_digest,service_clock_high_water,runtime_id,updated_at,row_version) ON rr.recovery_cursor TO rr_maintenance;

GRANT UPDATE(phase,journal_sequence,fenced_commit_known,active_removed_at,retired_at,safe_error_code,updated_at,row_version) ON rr.deletion_operation TO rr_maintenance;

GRANT UPDATE(retain_until,payload_retirement_verified,retention_breach,updated_at,row_version) ON rr.suppression TO rr_maintenance;

GRANT INSERT ON rr.runtime_incarnation,rr.host_budget,rr.recovery_cursor,rr.deletion_operation,rr.suppression,rr.service_admission TO rr_maintenance;

GRANT SELECT,INSERT,UPDATE,DELETE ON rr_internal.spring_session,rr_internal.spring_session_attributes TO rr_control; GRANT DELETE ON rr_internal.spring_session,rr_internal.spring_session_attributes TO rr_maintenance; GRANT SELECT,INSERT,UPDATE,DELETE ON rr_internal.scheduled_tasks TO rr_maintenance;

CREATE FUNCTION rr.guard_manifest() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,rr AS $$
DECLARE actual_count bigint; actual_bytes numeric;
BEGIN
 IF TG_OP='UPDATE' AND OLD.sealed THEN RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='sealed output is immutable'; END IF;
 IF NEW.sealed THEN
  SELECT count(*),coalesce(sum(encoded_bytes),0) INTO actual_count,actual_bytes FROM rr.finding WHERE workspace_id=NEW.workspace_id AND case_id=NEW.case_id AND run_id=NEW.run_id AND attempt_no=NEW.attempt_no;
  IF NEW.input_count::numeric<>NEW.assessed_count::numeric+NEW.unassessed_count::numeric OR NEW.finding_count<>actual_count OR NEW.encoded_bytes<actual_bytes THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='incomplete output seal'; END IF;
 END IF; RETURN NEW;
END $$;
CREATE TRIGGER manifest_seal BEFORE INSERT OR UPDATE ON rr.output_manifest FOR EACH ROW EXECUTE FUNCTION rr.guard_manifest();
CREATE FUNCTION rr.guard_finding() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,rr AS $$
DECLARE is_sealed boolean;
BEGIN
 SELECT sealed INTO is_sealed FROM rr.output_manifest WHERE workspace_id=NEW.workspace_id AND case_id=NEW.case_id AND run_id=NEW.run_id AND attempt_no=NEW.attempt_no FOR UPDATE;
 IF is_sealed THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='sealed output is immutable'; END IF; RETURN NEW;
END $$;
CREATE TRIGGER finding_seal BEFORE INSERT ON rr.finding FOR EACH ROW EXECUTE FUNCTION rr.guard_finding();
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA rr FROM PUBLIC;


GRANT SELECT(account_id,state,generation) ON rr.account TO rr_worker;
