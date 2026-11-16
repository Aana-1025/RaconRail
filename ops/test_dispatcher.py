"""Meaningful failure-policy checks with controlled commands; no app test claims."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('rr',Path(__file__).with_name('run_commands.py'));rr=importlib.util.module_from_spec(spec);spec.loader.exec_module(rr)

class DispatcherPolicy(unittest.TestCase):
    def setup_tree(self,base):
        root=Path(base);(root/'ops').mkdir();(root/'harness').mkdir()
        manifest=json.loads((rr.ROOT/'ops/commands.json').read_text());(root/'ops/commands.json').write_text(json.dumps(manifest))
        (root/'ops/tasks.json').write_text(json.dumps({'tasks':[{'id':'RR-02','work_items':[{'id':'RR-02.2'}]}]}));return root,manifest
    def test_every_unavailable_suite_and_unknown_task_refuse_without_running_commands(self):
        with tempfile.TemporaryDirectory() as directory:
            root,m=self.setup_tree(directory)
            def forbidden(*a,**k):self.fail('Unimplemented suite executed a command')
            for name,suite in m['suites'].items():
                if suite['status']=='BLOCKED':self.assertEqual(2,rr.execute(name,'RR-02.2',root=root,run=forbidden))
            self.assertEqual(2,rr.execute('build','RR-99',root=root,run=forbidden))
            self.assertEqual(2,rr.execute('made-up','RR-02.2',root=root,run=forbidden))
    def test_failure_propagates_and_remaining_commands_do_not_run(self):
        with tempfile.TemporaryDirectory() as directory:
            root,m=self.setup_tree(directory)
            tools=root/'tools';java_home=tools/'java';node=tools/('node.exe' if rr.os.name=='nt' else 'node')
            npm_cli=tools/'node_modules/npm/bin/npm-cli.js'
            for f in [java_home/('bin/java.exe' if rr.os.name=='nt' else 'bin/java'),java_home/('bin/javac.exe' if rr.os.name=='nt' else 'bin/javac'),node,npm_cli]:
                f.parent.mkdir(parents=True,exist_ok=True);f.touch()
            (root/'harness/local-toolchain.json').write_text(json.dumps({'tools':{'java':{'home':str(java_home)},'node':{'executable':str(node)}}}))
            calls=[]
            def fail(argv,**kwargs):calls.append(argv);return subprocess.CompletedProcess(argv,23)
            with patch.dict(rr.os.environ,{'JAVA_HOME':str(java_home)}), patch.object(rr.shutil,'which',side_effect=lambda name: str(node if name=='node' else npm_cli)):
                self.assertEqual(23,rr.execute('build','RR-02.2',root=root,run=fail))
            self.assertEqual(1,len(calls))

    def test_missing_command_blocks_before_any_execution(self):
        with tempfile.TemporaryDirectory() as directory:
            root,m=self.setup_tree(directory)
            del m['commands']['maven-verify']
            (root/'ops/commands.json').write_text(json.dumps(m))
            def forbidden(*a,**k):self.fail('Incomplete manifest executed a command')
            self.assertEqual(2,rr.execute('build','RR-02.2',root=root,run=forbidden))

class ScopedSuitePolicy(unittest.TestCase):
    def test_domain_override_is_limited_to_rr04(self):
        manifest=json.loads((rr.ROOT/'ops/commands.json').read_text())
        self.assertEqual('BLOCKED',manifest['suites']['domain']['status'])
        self.assertEqual({'RR-04','RR-04.1','RR-04.2'},set(manifest['task_suites']))
        self.assertEqual('BLOCKED',manifest['suites']['all']['status'])
        for task in manifest['task_suites']:
            suite=manifest['task_suites'][task]['domain']
            self.assertIn('domain-codec',suite['commands'])
            self.assertIn('financial evaluator suite remains unavailable',suite['scope'])

if __name__=='__main__':unittest.main()
