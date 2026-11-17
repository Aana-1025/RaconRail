"""Run only the synthetic PostgreSQL/Keycloak authentication integration boundary."""
import os,subprocess,sys
from pathlib import Path

def main():
    try:
        import requests
        if requests.__version__!='2.33.1':raise RuntimeError('Python test dependency differs from ops/test-requirements.txt')
    except ImportError:raise SystemExit('BLOCKED: install the pinned local test requirements first')
    env=os.environ.copy();env.update(RR_ACCESS_TESTS='1',RR_OIDC_TESTS='1',RR_SECURITY_ONLY='1')
    return subprocess.run([sys.executable,str(Path(__file__).with_name('verify_pg.py'))],env=env).returncode
if __name__=='__main__':sys.exit(main())
