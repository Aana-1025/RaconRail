package dev.reconrail.lifecycle;

import java.io.IOException;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable state probe for response chunks; it performs no socket writes and starts no transaction. */
public final class JdbcAuthorityProbe implements AuthorityProbe {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcAuthorityProbe(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(manager, "manager"));
        this.transactions.setReadOnly(true);
        this.transactions.setTimeout(2);
    }

    @Override public void requireCurrent(AuthorityScope scope) throws IOException {
        String code;
        try {
            code = transactions.execute(status -> {
                Integer account = jdbc.queryForObject(
                        "SELECT count(*) FROM rr.account WHERE account_id=? AND generation=? AND state='ACTIVE'",
                        Integer.class, scope.accountId(), scope.accountGeneration());
                if (account == null || account != 1) return "ACCOUNT_AUTHORITY_STALE";
                if (scope.kind() == AuthorityScope.Kind.CASE) {
                    jdbc.queryForObject("SELECT set_config('rr.workspace_id', ?, true)", String.class,
                            scope.workspaceId().toString());
                    Integer resource = jdbc.queryForObject("""
                            SELECT count(*) FROM rr.case_record c JOIN rr.workspace w USING(workspace_id)
                            WHERE w.account_id=? AND c.workspace_id=? AND c.case_id=? AND c.generation=?
                              AND c.state IN ('ACTIVE','ARCHIVED')
                            """, Integer.class, scope.accountId(), scope.workspaceId(), scope.resourceId(),
                            scope.resourceGeneration());
                    if (resource == null || resource != 1) return "RESOURCE_AUTHORITY_STALE";
                }
                return null;
            });
        } catch (RuntimeException failure) {
            throw new IOException("AUTHORITY_PROBE_FAILED", failure);
        }
        if (code != null) throw new IOException(code);
    }
}
