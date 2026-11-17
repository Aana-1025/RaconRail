# Third-party dependency record

This local development build includes unmodified public open-source dependencies. The project has not selected a public distribution/license policy or claimed complete legal clearance. Keep upstream license/NOTICE texts with any later reviewed distribution; don't infer permission from an audit count or this summary.

Direct frontend declarations: React/ReactDOM, React Router, TanStack Query, Vite/React plugin, Vitest, Testing Library, jsdom and selected types declare MIT; TypeScript declares Apache-2.0. Resolved frontend dependencies also declare MPL-2.0, BSD, ISC, BlueOak-1.0.0 and0BSD. Backend includes Spring/Tomcat/Jackson and other managed dependencies; Logback declares EPL-2.0 and LGPL-2.1-only alternatives, SLF4J MIT. Exact declarations and POM inheritance are recorded in the current private dependency inventory; this summary doesn't choose or interpret dual-license obligations.

The supplier tool archives retain their JDK legal directory/NOTICE, Node LICENSE and Maven LICENSE/NOTICE. No tool binaries, npm modules or build outputs should be committed. No remote images/fonts, copied design-reference screenshots or downloaded asset pack is shipped in the shell; decorative CSS is project source. Full artifact/transitive notice review is required before redistribution.

## Migration tooling and adapted framework SQL

The offline migration profile uses Boot-managed Flyway12.4.0 (core, PostgreSQL module and Maven plugin; parent POM declares Apache-2.0) and PostgreSQL JDBC42.7.14 (BSD-2-Clause). Flyway remains offline migration tooling; the PostgreSQL driver also supports the authentication runtime. Migration-owner credentials are absent from the application. A scoped OSV query for these four coordinates returned no listed advisories on 2026-11-17; this is not an audit of the complete build-plugin dependency closure.

V3__framework_storage.sql adapts Spring Session4.1.1 and db-scheduler17.0.0 upstream PostgreSQL DDL by qualifying table names with rr_internal. Columns, keys and indexes retain their vendor shapes. Sources:

- https://github.com/spring-projects/spring-session/blob/4.1.1/spring-session-jdbc/src/main/resources/org/springframework/session/jdbc/schema-postgresql.sql  -  source SHA256 d5333e408d24d210020c3a60d5e1e3f4b52e858b904d47d00b462dde20c24954.

- https://github.com/kagkarlsson/db-scheduler/blob/v17.0.0/db-scheduler/src/test/resources/postgresql_tables.sql  -  source SHA256 250c9ce181b49dd0d0b51652c87473ff0fdc0dc8107614e5d9e00e4ea8bb9c15.

Upstream Apache-2.0 license texts are retained under ops/db/licenses. The scheduler source is a schema example; its successful migration does not prove scheduler polling/enlistment integration. Spring Session runtime integration is now implemented; scheduler integration remains unverified. PostgreSQL image tests pin digest sha256:2ea8630df176ad090ec92136dee850397c5af8781e4bcf2e4162593691c61bbc (Linux amd64 verified as PostgreSQL18.7); the image is not shipped in the application artifact. Container-layer license/advisory clearance remains outside the four-coordinate query.

## Authentication dependencies

The authentication profile resolves Spring Security7.1.2, Spring Session4.1.1, Spring Framework7.0.10, HikariCP7.0.2, Nimbus JOSE JWT10.9.1 and PostgreSQL JDBC42.7.14 through the pinned Boot dependency management. Selected resolved POMs declare Apache-2.0 for Security/Hikari/Nimbus; PGjdbc declares BSD-2-Clause. Spring Session4.1.1 has conflicting declarations: the resolved POM names Broadcom Foundation License, while its JAR META-INF/LICENSE.txt and upstream4.1.1 LICENSE.txt contain Apache-2.0. This record does not resolve that conflict or grant redistribution clearance; establish authoritative licensing before distribution.

Requests2.33.1 is a test-only Python dependency whose upstream LICENSE contains Apache-2.0. Keycloak26.8.2 is used only as a disposable digest-pinned local test image, not embedded in the app. A scoped OSV query of91 external resolved Maven coordinates returned no listed advisory IDs on2026-11-17. Complete transitive/plugin/container licensing and vulnerability review remains a distribution/release gate.
