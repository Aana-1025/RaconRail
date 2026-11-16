-- Normalized source membership freezes with its accepted input transaction.
-- Full transaction identity avoids a 32-bit wrap collision; xmin also checks row version.
ALTER TABLE rr.input_version ADD COLUMN accepted_transaction xid8 NOT NULL DEFAULT pg_current_xact_id();
CREATE FUNCTION rr.guard_source_membership() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,rr AS $$
DECLARE input_xid xid; accepted_xid xid8;
BEGIN
 SELECT xmin,accepted_transaction INTO input_xid,accepted_xid FROM rr.input_version WHERE workspace_id=NEW.workspace_id AND case_id=NEW.case_id AND input_version_id=NEW.input_version_id;
 IF FOUND AND (input_xid<>pg_current_xact_id()::xid OR accepted_xid<>pg_current_xact_id()) THEN
  RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='accepted source membership is immutable';
 END IF;
 RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION rr.guard_source_membership() FROM PUBLIC;
CREATE TRIGGER payment_membership BEFORE INSERT ON rr.provider_payment FOR EACH ROW EXECUTE FUNCTION rr.guard_source_membership();
CREATE TRIGGER charge_membership BEFORE INSERT ON rr.provider_charge FOR EACH ROW EXECUTE FUNCTION rr.guard_source_membership();
CREATE TRIGGER order_membership BEFORE INSERT ON rr.application_order FOR EACH ROW EXECUTE FUNCTION rr.guard_source_membership();
