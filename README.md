# ReconRail

Compare saved sandbox payment evidence with an independent application expectation.

## Current status

The application currently provides a public overview, guide and interactive synthetic example in a packaged Java application. The Orbit interface uses CSS sculpture, floating evidence cards and motion controls. Sign-in, workspace, account and closure routes clearly indicate that their features are unavailable.

Authentication, uploads, PostgreSQL persistence and the financial evaluator are planned. The example does not perform reconciliation or make financial correctness claims. No live payment integration is included.

## Build

Prerequisites: Python3, JDK25, Node24 with npm11. The Maven wrapper pins Maven3.10.1. Set JAVA_HOME to the JDK directory and put Node/npm on PATH. Windows tool paths can optionally be supplied through a local toolchain map.

```powershell
./ops/rr.ps1 verify -Suite build -Task RR-02.2
```

On Linux:

```sh
python3 ops/run_commands.py --suite build --task RR-02.2
```

The build installs locked frontend dependencies with lifecycle scripts disabled, checks TypeScript, runs component tests, builds static assets and packages them with the Java application. Java tests exercise the real HTTP shell and unavailable routes. Linux CI is configured for manual execution; it has not yet been validated on a hosted runner.

`preflight` checks tool versions. Unimplemented suites, including `all`, fail explicitly with exit2. Build success does not establish full product readiness. Command logs are kept locally under harness/evidence/commands.

## Run

```sh
java -jar target/reconrail-0.1.0-SNAPSHOT.jar
```

Open http://127.0.0.1:8080/. The default bind address is loopback. Routes include /, /guide and /example. The example uses labelled synthetic data and supports source selection, perspective controls, reset and motion pause. Reduced-motion preferences are respected.

## Design and contracts

See [product architecture](docs/product-architecture.md), [API specification](docs/04-apis-integrations/openapi.json) and [design tokens](docs/07-frontend/orbit-tokens.json). API contracts describe intended behavior; only the shell is currently implemented.

See [third-party dependencies](THIRD_PARTY.md) for dependency and notice information.
