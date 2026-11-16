"""Disposable PostgreSQL/Flyway verification. No existing database is touched."""
import hashlib,json,os,secrets,shutil,subprocess,tempfile,time,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
IMAGE='postgres@sha256:2ea8630df176ad090ec92136dee850397c5af8781e4bcf2e4162593691c61bbc'

def main():
    stamp=time.strftime('%Y%m%dT%H%M%SZ',time.gmtime())
    evidence=ROOT/'harness/evidence/pg'/stamp;evidence.mkdir(parents=True,exist_ok=False)
    checks=[];commands=[];container=None
    def call(argv,*,input=None,env=None,ok=True):
        result=subprocess.run(argv,input=input,text=True,capture_output=True,cwd=ROOT,env=env,timeout=240)
        commands.append({'argv':argv,'exit_code':result.returncode})
        if ok and result.returncode:raise RuntimeError(result.stderr[-1200:] or result.stdout[-1200:])
        return result
    def sql(query,*,role=None,workspace=None,expected=None):
        prefix=('SET SESSION AUTHORIZATION '+role+'; ' if role else '')
        if workspace:prefix+="BEGIN; SET LOCAL rr.workspace_id='"+workspace+"'; "
        q=prefix+query+(' COMMIT;' if workspace else '')
        result=call(['docker','exec','-i',container,'psql','-X','-q','-At','-v','ON_ERROR_STOP=1','-v','VERBOSITY=sqlstate','-U','postgres','-d','reconrail'],input=q,ok=False)
        if expected:
            assert result.returncode and expected in result.stderr,('SQL execution failed',result.stderr)
        else:assert result.returncode==0,('SQL execution failed',result.stderr)
        return result.stdout.strip()
    def check(name,action):
        action();checks.append({'name':name,'status':'PASS'});print('PASS '+name,flush=True)
    try:
        image=call(['docker','image','inspect',IMAGE,'--format','{{.Id}}']).stdout.strip()
        name='rr-pg-'+uuid.uuid4().hex[:12]
        (ROOT/'tmp').mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(prefix='rr-pg-',dir=ROOT/'tmp') as directory:
            temp=Path(directory);password=secrets.token_urlsafe(32)
            envfile=temp/'postgres.env';envfile.write_text('POSTGRES_PASSWORD='+password+'\nPOSTGRES_DB=reconrail\n')
            container=call(['docker','run','--detach','--rm','--name',name,'--label','dev.reconrail.test='+name,'--memory','512m','--cpus','2','--publish','127.0.0.1::5432','--tmpfs','/var/lib/postgresql','--env-file',str(envfile),image]).stdout.strip()
            for n in range(60):
                ready=call(['docker','exec',container,'pg_isready','-h','127.0.0.1','-U','postgres','-d','reconrail'],ok=False)
                if ready.returncode==0:break
                time.sleep(1)
            else:raise RuntimeError('PostgreSQL readiness timeout')
            version=sql('SHOW server_version;');assert version.startswith('18.7'),version
            sql((ROOT/'ops/db/bootstrap.sql').read_text())
            sql("CREATE ROLE rr_migrator LOGIN PASSWORD '"+password+"' IN ROLE rr_owner;")
            port=call(['docker','port',container,'5432/tcp']).stdout.strip().split(':')[-1]
            env=os.environ.copy();env['MAVEN_OPTS']=env.get('MAVEN_OPTS','')+' -Duser.timezone=UTC';local=ROOT/'harness/local-toolchain.json'
            if os.name=='nt' and local.exists():
                tool=json.loads(local.read_text())['tools'];env['JAVA_HOME']=str(ROOT/tool['java']['home'])
                env['PATH']=str(Path(env['JAVA_HOME'])/'bin')+os.pathsep+env['PATH']
            env.update(RR_DB_URL='jdbc:postgresql://127.0.0.1:'+port+'/reconrail',RR_DB_USER='rr_migrator',RR_DB_PASSWORD=password,MAVEN_USER_HOME=str(ROOT/'.local-tools/maven-user'))
            wrapper=[str(ROOT/'mvnw.cmd')] if os.name=='nt' else ['sh',str(ROOT/'mvnw')]
            def flyway(goal,extra=(),expected=0):
                args=wrapper+['--batch-mode','--settings','.mvn/settings.xml','-Dmaven.repo.local='+str(ROOT/'.local-tools/m2/repository'),'-Pdatabase','flyway:'+goal,*extra]
                result=call(args,env=env,ok=False);log=evidence/('flyway-'+str(len(list(evidence.glob('flyway-*'))))+'.log')
                log.write_text((result.stdout+result.stderr).replace(password,'[REDACTED]'),encoding='utf-8')
                assert (result.returncode==0)==(expected==0),str(log)
                return result
            check('predecessor V1 migration',lambda:flyway('migrate',['-Dflyway.target=1']))
            check('upgrade V1 to complete schema',lambda:flyway('migrate'))
            check('migration checksums validate',lambda:flyway('validate'))
            assert sql("SELECT count(*) FROM pg_tables WHERE schemaname='rr';")=='24'
            checks.append({'name':'24 application tables present','status':'PASS'})
            # SQL acceptance fixtures run after real Flyway upgrade.
            scope={'sql':sql,'check':check,'ROOT':ROOT,'container':container,'call':call}
            exec((ROOT/'ops/db/acceptance.py').read_text(),scope)
            broken=temp/'broken';broken.mkdir();(broken/'V999__failure_probe.sql').write_text('CREATE TABLE rr.rollback_probe(id int); SELECT 1/0;')
            locations='-Dflyway.locations=filesystem:src/main/resources/db/migration,filesystem:'+str(broken)
            check('failed Flyway DDL rolls back',lambda:flyway('migrate',[locations],expected=1))
            assert sql("SELECT to_regclass('rr.rollback_probe') IS NULL;")=='t'
            assert sql("SELECT count(*) FROM rr_migrations.flyway_schema_history WHERE version='999';")=='0'
            altered=temp/'altered';shutil.copytree(ROOT/'src/main/resources/db/migration',altered)
            with (altered/'V1__identity_and_control.sql').open('a') as f:f.write('\n-- checksum mismatch probe\n')
            check('changed applied migration rejected',lambda:flyway('validate',['-Dflyway.locations=filesystem:'+str(altered)],expected=1))
            check('original migration checksums intact',lambda:flyway('validate'))
            # A separate empty database proves a direct fresh installation.
            sql('CREATE DATABASE reconrail_fresh OWNER rr_owner;')
            env['RR_DB_URL']=env['RR_DB_URL'].replace('/reconrail','/reconrail_fresh')
            call(['docker','exec','-i',container,'psql','-X','-v','ON_ERROR_STOP=1','-U','postgres','-d','reconrail_fresh'],input='CREATE SCHEMA rr_migrations AUTHORIZATION rr_owner;')
            check('fresh complete migration',lambda:flyway('migrate'))
        status='PASS'
    except Exception as error:
        status='FAIL';checks.append({'name':'verification failure','status':'FAIL','reason':str(error)});raise
    finally:
        cleanup='NOT_STARTED'
        if container:
            # Only the exact container created by this invocation is removed.
            result=call(['docker','rm','--force',container],ok=False);cleanup='REMOVED' if result.returncode==0 else 'FAILED'
        record={'status':locals().get('status','FAIL'),'checks':checks,'commands':commands,'image':locals().get('image'),'postgres_version':locals().get('version'),'cleanup':cleanup,'migration_sha256':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in (ROOT/'src/main/resources/db/migration').glob('*.sql')}}
        (evidence/'manifest.json').write_text(json.dumps(record,indent=2)+'\n');print(str(evidence),flush=True)
        if cleanup=='FAILED':raise RuntimeError('Owned test container cleanup failed')

if __name__=='__main__':main()
