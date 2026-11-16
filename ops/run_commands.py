"""Exact manifest-driven verification with fail-closed unavailable suites and raw exit records."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parent.parent
def stamp(): return dt.datetime.now(dt.timezone.utc).isoformat()
def execute(suite, task, *, root=ROOT, run=subprocess.run):
    manifest=json.loads((root/'ops/commands.json').read_text(encoding='utf-8'))
    registry=json.loads((root/'ops/tasks.json').read_text(encoding='utf-8'))
    ids={t['id'] for t in registry['tasks']} | {w['id'] for t in registry['tasks'] for w in t['work_items']}
    output=root/'harness/evidence/commands'/dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    output.mkdir(parents=True)
    record={'suite':suite,'task':task,'started_at':stamp(),'commands':[], 'status':'IN_PROGRESS',
            'manifest_sha256':hashlib.sha256((root/'ops/commands.json').read_bytes()).hexdigest(),
            'scope':'Named suite only; no task/release approval or unavailable-suite success inferred'}
    def finish(code,status,reason=None):
        record.update(exit_code=code,status=status,finished_at=stamp())
        if reason:record['reason']=reason
        (output/'manifest.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
        print(status+': '+str(output.relative_to(root)));return code
    if task not in ids or not re.fullmatch(r'RR-\d{2}(?:\.[12])?',task):
        return finish(2,'BLOCKED','Unknown task')
    if suite not in manifest['suites']:
        return finish(2,'BLOCKED','Unknown suite')
    chosen=manifest['suites'][suite]
    # Resolve all required commands before doing work; never skip a missing command.
    if chosen['status']!='IMPLEMENTED':
        return finish(2,'BLOCKED',chosen.get('reason','Suite unavailable'))
    if not chosen.get('commands') or any(x not in manifest['commands'] for x in chosen['commands']):
        return finish(2,'BLOCKED','Incomplete command manifest')
    try:
        env=os.environ.copy();local=root/'harness/local-toolchain.json'
        if os.name=='nt' and local.exists():
            tools=json.loads(local.read_text())['tools'];java_home=root/tools['java']['home'];node=root/tools['node']['executable'];npm_cli=node.parent/'node_modules/npm/bin/npm-cli.js'
        else:
            java_home=Path(env.get('JAVA_HOME','__JAVA_HOME_REQUIRED__'));node=Path(shutil.which('node') or '__NODE_REQUIRED__');npm=shutil.which('npm');npm_cli=(node.parent/'node_modules/npm/bin/npm-cli.js') if os.name=='nt' else (Path(npm).resolve() if npm else Path('__NPM_REQUIRED__'))
        suffix='.exe' if os.name=='nt' else ''
        java=java_home/('bin/java'+suffix);javac=java_home/('bin/javac'+suffix)
        if not all(p.is_file() for p in [java,javac,node,npm_cli]):return finish(2,'BLOCKED','Missing exact Java/Node/npm prerequisites')
        env.update(JAVA_HOME=str(java_home),RR_NODE=str(node),RR_NPM_CLI=str(npm_cli),MAVEN_USER_HOME=str(root/'.local-tools/maven-user'))
        env['PATH']=str(java.parent)+os.pathsep+str(node.parent)+os.pathsep+env.get('PATH','')
        substitutions={'python':sys.executable,'java':str(java),'javac':str(javac),'node':str(node),'npm_cli':str(npm_cli),'npm_cache':str(root/'.local-tools/npm-cache'),'maven_repo':str(root/'.local-tools/m2/repository'),'wrapper':str(root/('mvnw.cmd' if os.name=='nt' else 'mvnw'))}
        for ident in chosen['commands']:
            spec=manifest['commands'][ident];argv=[a.format(**substitutions) for a in spec['argv']]
            if ident=='maven-verify' and os.name!='nt':argv=['sh',*argv]
            entry={'id':ident,'argv':argv,'started_at':stamp()};record['commands'].append(entry)
            with (output/(ident+'.log')).open('w',encoding='utf-8') as log:
                result=run(argv,cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=1200)
            entry.update(exit_code=result.returncode,finished_at=stamp(),log=ident+'.log')
            if result.returncode!=0:return finish(result.returncode,'FAIL',ident+' failed; subsequent commands not executed')
            if spec.get('expected') and not re.search(spec['expected'],(output/(ident+'.log')).read_text(),re.MULTILINE):return finish(1,'FAIL',ident+' version outside selected family')
        return finish(0,'PASS')
    except (OSError,ValueError,KeyError,subprocess.TimeoutExpired) as e:
        return finish(2,'BLOCKED',type(e).__name__+': '+str(e))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--suite',required=True);parser.add_argument('--task',required=True)
    args=parser.parse_args();sys.exit(execute(args.suite,args.task))
