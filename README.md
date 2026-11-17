# ReconRail

Compare saved sandbox payment evidence with an independent application expectation.

## Current status

The application provides a public overview, guide and interactive synthetic example in a packaged Java application. The Orbit interface uses CSS sculpture, floating evidence cards and motion controls. Configured environments support OIDC sign-in and local sign-out; the default preview keeps sign-in unavailable. Private investigation, account and closure workflows are still being prepared.

PostgreSQL migrations, bounded JDBC sessions and the pure comparison core are implemented. Uploads, investigation persistence wiring and evaluator integration remain planned. The example does not perform reconciliation or make financial correctness claims. No live payment integration is included.

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

See [product architecture](docs/product-architecture.md), [API specification](docs/04-apis-integrations/openapi.json) and [design tokens](docs/07-frontend/orbit-tokens.json). API contracts describe intended behavior; only the documented shell and session endpoints are currently wired.

See [third-party dependencies](THIRD_PARTY.md) for dependency and notice information.

## Database verification

With Docker running, fetch the pinned test image, then run the real PostgreSQL suite:

```sh
docker pull postgres@sha256:2ea8630df176ad090ec92136dee850397c5af8781e4bcf2e4162593691c61bbc
```

```powershell
./ops/rr.ps1 verify -Suite pg -Task RR-03
```

Linux: `python3 ops/run_commands.py --suite pg --task RR-03`. The suite creates its own disposable PostgreSQL18.7 container and synthetic databases, validates Flyway migrations and database integrity/security boundaries, then removes only that container. It requires Python3, Java25 and Docker access; the dispatcher also verifies the standard Node/npm prerequisites. No existing database is used. See [database foundation](docs/database.md).

## Evidence codec core

Strict pair validation, manifest-ordered canonical bytes and logical row accounting are implemented as pure modules. Exact scalar helpers are available in the frontend; there is no upload endpoint or wired financial result workflow yet. See [codec boundaries and verification](docs/evidence-codecs.md).

## Pure comparison evaluator

The qualified comparison core now evaluates frozen saved evidence with separate state/currency/amount outcomes, exact integer money and conservative capture timing. It is not yet connected to application endpoints or financial result screens. See [comparison boundaries](docs/comparison-evaluator.md).

## Authentication

See [authentication configuration and limits](docs/authentication.md) for the explicit authentication profile, local integration command and session boundaries. Local synthetic checks do not establish hosted security or account-closure readiness.
