package dev.reconrail.access;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

/** Issuer/subject are from verified ID-token claims, never request parameters. */
public final class AccountBinder {
    private final JdbcTemplate jdbc;private final TransactionTemplate transaction;
    public AccountBinder(JdbcTemplate jdbc,PlatformTransactionManager manager){this.jdbc=jdbc;transaction=new TransactionTemplate(manager);transaction.setTimeout(5);}
    public void requireSameSubject(String issuer,String subject,Instant authTime,AppPrincipal previous,Instant now){
        if(previous==null)return;
        if(authTime==null||authTime.isBefore(now.minusSeconds(360))||authTime.isAfter(now.plusSeconds(60)))throw new IllegalArgumentException("FRESH_AUTH_REQUIRED");
        Long count=jdbc.queryForObject("SELECT count(*) FROM rr.identity_binding i JOIN rr.account a ON a.account_id=i.account_id WHERE i.issuer=? AND i.subject=? AND a.account_id=? AND a.generation=? AND a.state='ACTIVE'",Long.class,issuer,subject,previous.accountId(),previous.generation());
        if(count==null||count!=1)throw new IllegalArgumentException("SAME_SUBJECT_REQUIRED");
    }
    public AppPrincipal resolve(String issuer,String subject,Instant now,Instant authTime,AppPrincipal previous){
        if(issuer==null||subject==null||issuer.isEmpty()||subject.isEmpty()||issuer.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1024||subject.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1024)throw SessionEnvelope.invalid();
        return transaction.execute(status->{
            jdbc.execute("SET LOCAL lock_timeout='250ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");
            jdbc.update("INSERT INTO rr.service_admission(singleton,queued_runs,nonterminal_runs,staged_pairs,staged_bytes) VALUES(1,0,0,0,0) ON CONFLICT(singleton) DO NOTHING");
            jdbc.queryForObject("SELECT singleton FROM rr.service_admission WHERE singleton=1 FOR UPDATE",Integer.class);
            var matches=jdbc.queryForList("SELECT account_id FROM rr.identity_binding WHERE issuer=? AND subject=?",issuer,subject);
            if((previous!=null&&authTime==null)||(authTime!=null&&(authTime.isBefore(now.minusSeconds(360))||authTime.isAfter(now.plusSeconds(60)))))throw new IllegalArgumentException("FRESH_AUTH_REQUIRED");
            UUID account,workspace;long generation;
            if(matches.isEmpty()){
                if(previous!=null)throw new IllegalArgumentException("SAME_SUBJECT_REQUIRED");
                account=UUID.randomUUID();workspace=UUID.randomUUID();generation=1;
                jdbc.update("INSERT INTO rr.account(account_id,state,generation) VALUES(?,'ACTIVE',1)",account);
                jdbc.update("INSERT INTO rr.identity_binding(issuer,subject,account_id) VALUES(?,?,?)",issuer,subject,account);
                jdbc.queryForObject("SELECT set_config('rr.workspace_id',?,true)",String.class,workspace.toString());
                jdbc.update("INSERT INTO rr.workspace(workspace_id,account_id) VALUES(?,?)",workspace,account);
            }else{
                account=(UUID)matches.getFirst().get("account_id");var rows=jdbc.queryForList("SELECT state,generation FROM rr.account WHERE account_id=? FOR UPDATE",account);
                if(rows.size()!=1||!"ACTIVE".equals(rows.getFirst().get("state")))throw SessionEnvelope.invalid();generation=((Number)rows.getFirst().get("generation")).longValue();
                if(previous!=null&&(!previous.accountId().equals(account)||previous.generation()!=generation))throw new IllegalArgumentException("SAME_SUBJECT_REQUIRED");
                jdbc.queryForObject("SELECT set_config('rr.account_id',?,true)",String.class,account.toString());
                workspace=jdbc.queryForObject("SELECT workspace_id FROM rr.workspace WHERE account_id=?",UUID.class,account);
            }
            Instant original=previous!=null&&previous.accountId().equals(account)&&previous.generation()==generation?previous.originalLogin():now;
            return new AppPrincipal(account,workspace,generation,original,now,authTime);
        });
    }
}
