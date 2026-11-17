package dev.reconrail.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import com.zaxxer.hikari.*;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@EnabledIfEnvironmentVariable(named = "RR_LIFECYCLE_TESTS", matches = "1")
class LifecycleJdbcIT {
    private HikariDataSource dataSource;
    private JdbcAuthorityProbe probe;
    private UUID accountId, workspaceId, caseId;

    @BeforeEach void open() {
        var config = new HikariConfig();
        config.setJdbcUrl(required("RR_TEST_JDBC_URL")); config.setUsername(required("RR_TEST_JDBC_USER"));
        config.setPassword(required("RR_TEST_JDBC_PASSWORD")); config.setMaximumPoolSize(2);
        config.setConnectionTimeout(1000); config.setConnectionInitSql("SET TIME ZONE 'UTC'");
        dataSource = new HikariDataSource(config);
        probe = new JdbcAuthorityProbe(new JdbcTemplate(dataSource), new DataSourceTransactionManager(dataSource));
        accountId = UUID.fromString(required("RR_TEST_ACCOUNT_ID"));
        workspaceId = UUID.fromString(required("RR_TEST_WORKSPACE_ID"));
        caseId = UUID.fromString(required("RR_TEST_CASE_ID"));
    }
    @AfterEach void close() { dataSource.close(); }

    @Test void realNonownerProbeHonorsAccountCaseGenerationAndWorkspaceRls() throws Exception {
        assertDoesNotThrow(() -> probe.requireCurrent(AuthorityScope.account(accountId, 7)));
        var current = AuthorityScope.caseScope(accountId, 7, workspaceId, caseId, 11);
        assertDoesNotThrow(() -> probe.requireCurrent(current));
        assertEquals("ACCOUNT_AUTHORITY_STALE", assertThrows(IOException.class,
                () -> probe.requireCurrent(AuthorityScope.account(accountId, 6))).getMessage());
        assertEquals("RESOURCE_AUTHORITY_STALE", assertThrows(IOException.class,
                () -> probe.requireCurrent(AuthorityScope.caseScope(accountId, 7, workspaceId, caseId, 10))).getMessage());
        assertEquals("RESOURCE_AUTHORITY_STALE", assertThrows(IOException.class,
                () -> probe.requireCurrent(AuthorityScope.caseScope(accountId, 7, UUID.randomUUID(), caseId, 11))).getMessage());
    }

    private static String required(String name) {
        String value = System.getenv(name); if (value == null || value.isBlank()) throw new IllegalStateException(name);
        return value;
    }
}
