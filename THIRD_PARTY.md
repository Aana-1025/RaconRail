# Third-party dependency record

This local development build includes unmodified public open-source dependencies. The project has not selected a public distribution/license policy or claimed complete legal clearance. Keep upstream license/NOTICE texts with any later reviewed distribution; don't infer permission from an audit count or this summary.

Direct frontend declarations: React/ReactDOM, React Router, TanStack Query, Vite/React plugin, Vitest, Testing Library, jsdom and selected types declare MIT; TypeScript declares Apache-2.0. Resolved frontend dependencies also declare MPL-2.0, BSD, ISC, BlueOak-1.0.0 and0BSD. Backend includes Spring/Tomcat/Jackson and other managed dependencies; Logback declares EPL-2.0 and LGPL-2.1-only alternatives, SLF4J MIT. Exact declarations and POM inheritance are recorded in the current private dependency inventory; this summary doesn't choose or interpret dual-license obligations.

The supplier tool archives retain their JDK legal directory/NOTICE, Node LICENSE and Maven LICENSE/NOTICE. No tool binaries, npm modules or build outputs should be committed. No remote images/fonts, copied design-reference screenshots or downloaded asset pack is shipped in the shell; decorative CSS is project source. Full artifact/transitive notice review is required before redistribution.

## Migration tooling and adapted framework SQL

The offline migration profile uses Boot-managed Flyway12.4.0 (core, PostgreSQL module and Maven plugin; parent POM declares Apache-2.0) and PostgreSQL JDBC42.7.14 (BSD-2-Clause). These are build-time migration dependencies, not runtime database credentials. A scoped OSV query for these four coordinates returned no listed advisories on 2026-11-17; this is not an audit of the complete build-plugin dependency closure.

V3__framework_storage.sql adapts Spring Session4.1.1 and db-scheduler17.0.0 upstream PostgreSQL DDL by qualifying table names with rr_internal. Columns, keys and indexes retain their vendor shapes. Sources:

- https://github.com/spring-projects/spring-session/blob/4.1.1/spring-session-jdbc/src/main/resources/org/springframework/session/jdbc/schema-postgresql.sql — source SHA256 d5333e408d24d210020c3a60d5e1e3f4b52e858b904d47d00b462dde20c24954.
- https://github.com/kagkarlsson/db-scheduler/blob/v17.0.0/db-scheduler/src/test/resources/postgresql_tables.sql — source SHA256 250c9ce181b49dd0d0b51652c87473ff0fdc0dc8107614e5d9e00e4ea8bb9c15.

Upstream Apache-2.0 license texts are retained under ops/db/licenses. The scheduler source is a schema example; its successful migration does not prove scheduler polling/enlistment integration. No library runtime integration is claimed yet. PostgreSQL image tests pin digest sha256:2ea8630df176ad090ec92136dee850397c5af8781e4bcf2e4162593691c61bbc (Linux amd64 verified as PostgreSQL18.7); the image is not shipped in the application artifact. Container-layer license/advisory clearance remains outside the four-coordinate query.
