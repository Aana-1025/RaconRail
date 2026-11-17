-- Verified issuer/subject -> locked ACTIVE account bootstrap only.
-- Child relations remain workspace-only. Both settings are transaction-local.
CREATE POLICY account_workspace_lookup ON rr.workspace FOR SELECT TO rr_control
USING (account_id = nullif(current_setting('rr.account_id', true), '')::uuid);
