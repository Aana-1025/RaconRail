"""Meaningful failure-policy checks with controlled commands; no app test claims."""
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('rr',Path(__file__).with_name('run_commands.py'));rr=importlib.util.module_from_spec(spec);spec.loader.exec_module(rr)

class DispatcherPolicy(unittest.TestCase):
    def setup_tree(self,base):
        root=Path(base);(root/'ops').mkdir();(root/'harness').mkdir()
        manifest=json.loads((rr.ROOT/'ops/commands.json').read_text());(root/'ops/commands.json').write_text(json.dumps(manifest))
        (root/'harness/task-registry.json').write_text(json.dumps({'tasks':[{'id':'RR-02','work_items':[{'id':'RR-02.2'}]}]}));return root,manifest
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
            # Real local prepared executables serve as identity-only fixture inputs.
            if rr.os.name=='nt':
                d=json.loads((rr.ROOT/'harness/local-toolchain.json').read_text())
                for t in d['tools'].values():
                    for k in ['home','executable']:
                        if k in t:t[k]=str(rr.ROOT/t[k])
                (root/'harness/local-toolchain.json').write_text(json.dumps(d))
            calls=[]
            def fail(argv,**kwargs):calls.append(argv);return subprocess.CompletedProcess(argv,23)
            self.assertEqual(23,rr.execute('build','RR-02.2',root=root,run=fail));self.assertEqual(1,len(calls))

    def test_missing_command_blocks_before_any_execution(self):
        with tempfile.TemporaryDirectory() as directory:
            root,m=self.setup_tree(directory)
            del m['commands']['maven-verify']
            (root/'ops/commands.json').write_text(json.dumps(m))
            def forbidden(*a,**k):self.fail('Incomplete manifest executed a command')
            self.assertEqual(2,rr.execute('build','RR-02.2',root=root,run=forbidden))

if __name__=='__main__':unittest.main()
