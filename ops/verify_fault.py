"""RR-07 isolated physical HTTP/PostgreSQL fault-boundary verification."""
import os,subprocess,sys
from pathlib import Path

def main():
    env=os.environ.copy()
    env.update(RR_LIFECYCLE_TESTS='1',RR_FAULT_ONLY='1')
    return subprocess.run([sys.executable,str(Path(__file__).with_name('verify_pg.py'))],env=env).returncode

if __name__=='__main__':sys.exit(main())
