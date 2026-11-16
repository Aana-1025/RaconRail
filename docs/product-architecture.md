# Product architecture

ReconRail compares two saved sandbox JSON artifacts: payment-provider evidence and an independent application expectation. Findings must state their evidence scope and distinguish differences from missing or unsupported information. The product does not claim evidence authenticity, root cause or automatic repair.

## Implemented foundation

A Java25/Spring Boot MVC application serves a React19/TypeScript interface built with Vite. Static frontend assets are packaged into the executable JAR. There is no separate runtime Node service. Shell routes and security headers have real HTTP tests; UI controls have component tests.

The Orbit design combines lilac, white and mint with product-related CSS artwork, readable task surfaces, native controls and reduced-motion support. The example is explicitly synthetic.

Versioned PostgreSQL migrations and a disposable PostgreSQL18.7 verification suite now provide the database foundation. Application database connections and repositories are not wired yet.

## Planned capabilities

Private personal workspaces, strict two-file JSON intake, PostgreSQL persistence and exact reconciliation rules are planned. Monetary comparisons use bounded integer representations and explicit currencies rather than floating-point arithmetic or inferred exchange rates.

The planned backend uses PostgreSQL18, explicit JDBC/Flyway migrations and bounded background work in the same application process. Authentication, authorization, tenant isolation, lifecycle deletion, concurrency and recovery need their own implementation and integration evidence before release.

The MVP excludes live payment calls, webhooks, refunds, ledger functionality, team collaboration and automatic correction. Contracts under docs/04-apis-integrations describe intended behavior rather than implemented endpoints.

## Verification boundaries

The build verifies the application shell. Missing financial, persistence, authentication, fault-recovery and full workflow suites fail closed through the dispatcher. Hosted CI, production deployment and full accessibility/security acceptance remain unverified.
