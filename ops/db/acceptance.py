"""Synthetic SQL cases, executed only by the disposable PG test runner."""
W='00000000-0000-0000-0000-000000000001';W2='00000000-0000-0000-0000-000000000002'
C='00000000-0000-0000-0000-000000000003';I='00000000-0000-0000-0000-000000000004'
R='00000000-0000-0000-0000-000000000005';T='00000000-0000-0000-0000-000000000006'
A='00000000-0000-0000-0000-000000000007';Q='00000000-0000-0000-0000-000000000008'
D="decode(repeat('00',32),'hex')"
def eq(query,value,**kwargs):assert sql(query,**kwargs)==value,query
sql(f"""
INSERT INTO rr.account(account_id,state,generation) VALUES('{W}','ACTIVE',1),('{W2}','ACTIVE',1);
INSERT INTO rr.workspace(workspace_id,account_id) VALUES('{W}','{W}'),('{W2}','{W2}');
INSERT INTO rr.case_record(workspace_id,case_id,state,generation,provider_namespace,app_namespace) VALUES('{W}','{C}','ACTIVE',1,'{{}}','{{}}'),('{W2}','{C}','ACTIVE',1,'{{}}','{{}}');
INSERT INTO rr.intake(workspace_id,case_id,intake_id,state,first_received_at,expires_at,temporary_handle,received_bytes,slot_held) VALUES('{W}','{C}','{T}','ACCEPTED',now(),now()+interval '1 hour','{T}',2,false);
BEGIN;
INSERT INTO rr.input_version(workspace_id,case_id,input_version_id,ordinal,intake_id,normalizer_version,provider_account,provider_sandbox,app_system,app_environment,context,context_digest,validation_system_ms) VALUES('{W}','{C}','{I}',1,'{T}','v1','sandbox','test','app','test','{{}}',{D},0);
INSERT INTO rr.artifact(workspace_id,case_id,input_version_id,role,raw_bytes,sha256,logical_records) VALUES('{W}','{C}','{I}','provider','x',{D},1),('{W}','{C}','{I}','application','x',{D},1);
INSERT INTO rr.provider_charge(workspace_id,case_id,input_version_id,charge_id,source_ordinal,payment_intent_id,presence) VALUES('{W}','{C}','{I}','ch',1,'pi_missing','{{}}');
INSERT INTO rr.application_order(workspace_id,case_id,input_version_id,order_id,source_ordinal,payment_reference,presence) VALUES('{W}','{C}','{I}','order1',1,'pi_missing','{{}}'),('{W}','{C}','{I}','order2',2,'pi_missing','{{}}');
COMMIT;
INSERT INTO rr.quota_allocation(workspace_id,case_id,allocation_id,resource_kind,resource_id,codec_version,state,reserved_bytes,charged_bytes) VALUES('{W}','{C}','{Q}','result','{R}','v1','RESERVED',67108864,0);
INSERT INTO rr.analysis_run(workspace_id,case_id,run_id,input_id,rule_version,currency_version,normalizer_version,config,config_digest,reference_at,requested_at,deadline_at,operational_phase,result_reservation_id,validation_system_ms) VALUES('{W}','{C}','{R}','{I}','v1','v1','v1','{{}}',{D},now(),now(),now()+interval '1 minute','RUNNING','{Q}',0);
INSERT INTO rr.runtime_incarnation(runtime_id,state) VALUES('{A}','READY');
INSERT INTO rr.run_attempt(workspace_id,case_id,run_id,attempt_no,attempt_token,runtime_id,state,started_at,output_bytes) VALUES('{W}','{C}','{R}',1,'{A}','{A}','RUNNING',now(),0);
INSERT INTO rr.output_manifest(workspace_id,case_id,run_id,attempt_no,sealed,input_count,assessed_count,unassessed_count,finding_count,encoded_bytes) VALUES('{W}','{C}','{R}',1,false,1,1,0,0,0);
""")
check('missing RLS context denies workspace rows',lambda:eq('SELECT count(*) FROM rr.workspace;','0',role='rr_control'))
check('workspace context isolates reads',lambda:eq('SELECT count(*) FROM rr.workspace;','1',role='rr_control',workspace=W))
check('transaction-local context clears on connection reuse',lambda:eq(f"BEGIN; SET LOCAL rr.workspace_id='{W}'; SELECT count(*) FROM rr.workspace; COMMIT; SELECT count(*) FROM rr.workspace;",'1\n0',role='rr_control'))
check('malformed context fails closed',lambda:sql("SET rr.workspace_id='malformed'; SELECT * FROM rr.workspace;",role='rr_control',expected='22P02'))
check('foreign workspace insert denied',lambda:sql(f"INSERT INTO rr.case_record(workspace_id,case_id,state,generation,provider_namespace,app_namespace) VALUES('{W2}','{T}','ACTIVE',1,'{{}}','{{}}');",role='rr_control',workspace=W,expected='42501'))
for role in ['rr_control','rr_worker','rr_maintenance']:
 check(role+' cannot create tables',lambda role=role:sql('CREATE TABLE rr.forbidden(id int);',role=role,expected='42501'))
 check(role+' cannot switch to migration owner',lambda role=role:sql('SET ROLE rr_owner;',role=role,expected='42501'))
 check(role+' cannot change migration history',lambda role=role:sql('DELETE FROM rr_migrations.flyway_schema_history;',role=role,expected='42501'))
check('runtime cannot alter RLS',lambda:sql('ALTER TABLE rr.workspace DISABLE ROW LEVEL SECURITY;',role='rr_control',expected='42501'))
check('runtime cannot truncate',lambda:sql('TRUNCATE rr.workspace CASCADE;',role='rr_control',expected='42501'))
for value in ['1.5','NaN','Infinity','-Infinity','100000000000000000000000000000000000000']:
 check('invalid exact money '+value,lambda value=value:sql("SELECT '"+value+"'::rr.minor_amount;",role='rr_control',expected='23514'))
check('38-digit integer round trips exactly',lambda:eq("SELECT '99999999999999999999999999999999999999'::rr.minor_amount::text;",'99999999999999999999999999999999999999',role='rr_control'))
check('negative original amount preserved',lambda:eq("SELECT '-1'::rr.minor_amount::text;",'-1',role='rr_control'))
check('cross-lineage source FK denied',lambda:sql(f"INSERT INTO rr.provider_payment(workspace_id,case_id,input_version_id,payment_id,source_ordinal,presence) VALUES('{W2}','{C}','{I}','pi',1,'{{}}');",expected='23503'))
check('incomplete accepted pair cannot commit',lambda:sql(f"BEGIN; INSERT INTO rr.intake(workspace_id,case_id,intake_id,state,first_received_at,expires_at,temporary_handle,received_bytes,slot_held) VALUES('{W}','{C}','{A}','ACCEPTED',now(),now(),'{A}',0,false); INSERT INTO rr.input_version(workspace_id,case_id,input_version_id,ordinal,intake_id,normalizer_version,provider_account,provider_sandbox,app_system,app_environment,context,context_digest,validation_system_ms) VALUES('{W}','{C}','{A}',2,'{A}','v1','sandbox','test','app','test','{{}}',{D},0); COMMIT;",expected='23503'))
check('accepted evidence cannot update',lambda:sql(f"UPDATE rr.artifact SET raw_bytes='changed' WHERE workspace_id='{W}';",role='rr_control',workspace=W,expected='42501'))
check('foreign external reference remains evidence',lambda:eq("SELECT payment_intent_id FROM rr.provider_charge;",'pi_missing',role='rr_control',workspace=W))
check('accepted source membership cannot grow',lambda:sql(f"INSERT INTO rr.application_order(workspace_id,case_id,input_version_id,order_id,source_ordinal,presence) VALUES('{W}','{C}','{I}','late',3,'{{}}');",role='rr_control',workspace=W,expected='23514'))
check('full transaction marker rejects equal low-32-bit identity',lambda:sql(f"BEGIN; UPDATE rr.input_version SET accepted_transaction=((pg_current_xact_id()::text::numeric+4294967296)::text)::xid8; SET LOCAL ROLE rr_control; SET LOCAL rr.workspace_id='{W}'; INSERT INTO rr.application_order(workspace_id,case_id,input_version_id,order_id,source_ordinal,presence) VALUES('{W}','{C}','{I}','wrap-probe',3,'{{}}'); COMMIT;",expected='23514'))
check('ambiguous application bindings persist',lambda:eq('SELECT count(*) FROM rr.application_order;','2',role='rr_control',workspace=W))
check('single nonterminal run enforced',lambda:sql(f"INSERT INTO rr.analysis_run(workspace_id,case_id,run_id,input_id,rule_version,currency_version,normalizer_version,config,config_digest,reference_at,requested_at,deadline_at,operational_phase,result_reservation_id,validation_system_ms) SELECT workspace_id,case_id,'{A}',input_id,rule_version,currency_version,normalizer_version,config,config_digest,reference_at,requested_at,deadline_at,operational_phase,result_reservation_id,validation_system_ms FROM rr.analysis_run;",expected='23505'))
check('unsealed output cannot publish',lambda:sql(f"INSERT INTO rr.publication(workspace_id,case_id,run_id,attempt_no,published_at,semantic_digest) VALUES('{W}','{C}','{R}',1,now(),{D});",role='rr_worker',workspace=W,expected='23503'))
new_finding=f"INSERT INTO rr.finding(workspace_id,case_id,run_id,attempt_no,finding_id,rule_id,classification,subject_kind,subject_id,source_refs,explanation_code,safe_values,encoded_bytes) VALUES('{W}','{C}','{R}',1,'{A}','rule','DIFFERENCE','order','order1','{{}}','difference','{{}}',10);"
check('worker can write private finding',lambda:sql(new_finding,role='rr_worker',workspace=W))
check('control cannot read private batches',lambda:eq('SELECT count(*) FROM rr.finding;','0',role='rr_control',workspace=W))
check('incomplete seal rejected',lambda:sql('UPDATE rr.output_manifest SET sealed=true,digest='+D+';',role='rr_worker',workspace=W,expected='23514'))
check('complete output can seal',lambda:sql('UPDATE rr.output_manifest SET sealed=true,finding_count=1,encoded_bytes=20,digest='+D+';',role='rr_worker',workspace=W))
check('sealed manifest cannot change',lambda:sql('UPDATE rr.output_manifest SET encoded_bytes=21;',role='rr_worker',workspace=W,expected='23514'))
check('finding insertion after seal rejected',lambda:sql(new_finding.replace("'"+A+"'","'"+T+"'"),role='rr_worker',workspace=W,expected='23514'))
check('sealed output can publish',lambda:sql(f"INSERT INTO rr.publication(workspace_id,case_id,run_id,attempt_no,published_at,semantic_digest) VALUES('{W}','{C}','{R}',1,now(),{D});",role='rr_worker',workspace=W))
check('published findings visible to control',lambda:eq('SELECT count(*) FROM rr.finding;','1',role='rr_control',workspace=W))
check('second publication rejected',lambda:sql(f"INSERT INTO rr.publication(workspace_id,case_id,run_id,attempt_no,published_at,semantic_digest) VALUES('{W}','{C}','{R}',1,now(),{D});",role='rr_worker',workspace=W,expected='23505'))
check('all tenant relations FORCE RLS',lambda:eq("SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='rr' AND c.relrowsecurity AND c.relforcerowsecurity;",'15'))

check('runtime identities have no superuser or RLS bypass',lambda:eq("SELECT bool_and(NOT rolsuper AND NOT rolbypassrls AND NOT rolcreaterole AND NOT rolcreatedb) FROM pg_roles WHERE rolname IN ('rr_control','rr_worker','rr_maintenance');",'t'))
check('vendor framework tables exist',lambda:eq("SELECT count(*) FROM pg_tables WHERE schemaname='rr_internal';",'3'))
check('control cannot insert private findings',lambda:sql(new_finding,role='rr_control',workspace=W,expected='42501'))
# Real concurrent unique-index contention, not a sequential duplicate assertion.
from concurrent.futures import ThreadPoolExecutor
sql(f"UPDATE rr.analysis_run SET operational_phase='PUBLISHED',terminal_at=now() WHERE run_id='{R}';")
def contender(run_id):
 query=f"SET SESSION AUTHORIZATION rr_control; BEGIN; SET LOCAL rr.workspace_id='{W}'; INSERT INTO rr.analysis_run(workspace_id,case_id,run_id,input_id,rule_version,currency_version,normalizer_version,config,config_digest,reference_at,requested_at,deadline_at,operational_phase,result_reservation_id,validation_system_ms) SELECT workspace_id,case_id,'{run_id}',input_id,rule_version,currency_version,normalizer_version,config,config_digest,reference_at,requested_at,deadline_at,'QUEUED',result_reservation_id,validation_system_ms FROM rr.analysis_run WHERE run_id='{R}'; SELECT pg_sleep(1); COMMIT;"
 return call(['docker','exec','-i',container,'psql','-X','-q','-At','-v','ON_ERROR_STOP=1','-v','VERBOSITY=sqlstate','-U','postgres','-d','reconrail'],input=query,ok=False)
def concurrent_admission():
 with ThreadPoolExecutor(max_workers=2) as executor:results=list(executor.map(contender,[A,T]))
 assert sorted(p.returncode for p in results)==[0,3]
 assert any('23505' in p.stderr for p in results)
check('concurrent active-run contenders produce one winner',concurrent_admission)
sql(f"INSERT INTO rr.case_execution_slot(workspace_id,case_id,owner_run_id,owner_attempt_no,runtime_id,token,held_since) VALUES('{W}','{C}','{R}',1,'{A}','{A}',now());",role='rr_worker',workspace=W)
sql('DELETE FROM rr.publication; DELETE FROM rr.finding; DELETE FROM rr.output_manifest;',role='rr_maintenance')
check('held execution slot blocks attempt removal',lambda:sql('DELETE FROM rr.run_attempt;',role='rr_maintenance',expected='23503'))
sql('UPDATE rr.case_execution_slot SET owner_run_id=NULL,owner_attempt_no=NULL,runtime_id=NULL,token=NULL,held_since=NULL;',role='rr_maintenance')
check('child-first execution cleanup succeeds after explicit release',lambda:sql('DELETE FROM rr.run_attempt; DELETE FROM rr.case_execution_slot; DELETE FROM rr.analysis_run; DELETE FROM rr.quota_allocation;',role='rr_maintenance'))
check('deferred pair removed together in bounded transaction',lambda:sql('BEGIN; DELETE FROM rr.provider_payment; DELETE FROM rr.provider_charge; DELETE FROM rr.application_order; DELETE FROM rr.artifact; DELETE FROM rr.input_version; COMMIT;',role='rr_maintenance'))
check('payload parents removable after child cleanup',lambda:sql('DELETE FROM rr.intake; DELETE FROM rr.case_record; DELETE FROM rr.workspace; DELETE FROM rr.account;',role='rr_maintenance'))
