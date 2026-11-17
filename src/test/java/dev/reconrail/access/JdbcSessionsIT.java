package dev.reconrail.access;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import com.zaxxer.hikari.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="RR_TEST_JDBC_URL",matches=".+")
class JdbcSessionsIT {
    HikariDataSource pool;JdbcTemplate jdbc;DataSourceTransactionManager manager;BoundedJdbcSessions sessions;AccountBinder binder;SessionAttributeCodec codec;
    @BeforeEach void setup(){var c=new HikariConfig();c.setJdbcUrl(System.getenv("RR_TEST_JDBC_URL"));c.setUsername(System.getenv("RR_TEST_JDBC_USER"));c.setPassword(System.getenv("RR_TEST_JDBC_PASSWORD"));c.setMaximumPoolSize(6);c.setMinimumIdle(0);c.setConnectionTimeout(1000);c.setConnectionInitSql("SET TIME ZONE 'UTC'");pool=new HikariDataSource(c);jdbc=new JdbcTemplate(pool);manager=new DataSourceTransactionManager(pool);codec=new SessionAttributeCodec(SessionEnvelope.ephemeral());sessions=new BoundedJdbcSessions(jdbc,manager,codec,Clock.systemUTC());sessions.invalidateForFreshKey();binder=new AccountBinder(jdbc,manager);}
    @AfterEach void close(){if(sessions!=null)sessions.close();if(pool!=null)pool.close();}
    AppPrincipal user(String subject){return binder.resolve("http://127.0.0.1/realms/test",subject,Instant.now(),Instant.now(),null);}
    BoundedJdbcSessions.SafeSession authenticated(AppPrincipal p){var s=sessions.createSession();s.setAttribute(SessionAttributeCodec.CONTEXT,new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(p,null,List.of(new SimpleGrantedAuthority("ROLE_USER")))));return s;}
    @Test void realFrameworkBytesRoundTripAndRotationInvalidateOldId(){var p=user("rotation");var s=authenticated(p);sessions.save(s);String old=s.getId();var loaded=sessions.findById(old);assertEquals(p,loaded.principal());String changed=loaded.changeSessionId();sessions.save(loaded);assertNull(sessions.findById(old));assertEquals(p,sessions.findById(changed).principal());byte[] bytes=jdbc.queryForObject("SELECT attribute_bytes FROM rr_internal.spring_session_attributes WHERE attribute_name=?",byte[].class,SessionAttributeCodec.CONTEXT);assertFalse(bytes.length>1&&bytes[0]==(byte)0xac&&bytes[1]==(byte)0xed);}
    @Test void accountCapIsAtomicWithFrameworkInsert(){var p=user("cap");for(int i=0;i<5;i++)sessions.save(authenticated(p));assertThrows(IllegalStateException.class,()->sessions.save(authenticated(p)));assertEquals(5L,jdbc.queryForObject("SELECT count(*) FROM rr_internal.spring_session",Long.class));}
    @Test void concurrentFinalAccountSlotHasOneWinner()throws Exception{var p=user("race");for(int i=0;i<4;i++)sessions.save(authenticated(p));var gate=new CountDownLatch(1);try(var executor=Executors.newFixedThreadPool(2)){List<Future<Boolean>> results=new ArrayList<>();for(int i=0;i<2;i++)results.add(executor.submit(()->{var s=authenticated(p);gate.await();try{sessions.save(s);return true;}catch(IllegalStateException e){return false;}}));gate.countDown();int wins=0;for(var f:results)if(f.get(10,TimeUnit.SECONDS))wins++;assertEquals(1,wins);}assertEquals(5L,jdbc.queryForObject("SELECT count(*) FROM rr_internal.spring_session",Long.class));}
    @Test void nestedBusinessTransactionRejectedBeforeIndependentSave(){var p=user("nested");var tx=new TransactionTemplate(manager);tx.executeWithoutResult(status->assertThrows(IllegalStateException.class,()->sessions.save(authenticated(p))));assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM rr_internal.spring_session",Long.class));}
    @Test void lateSaveCannotReviveClosedGeneration(){var p=user("closed");var s=authenticated(p);sessions.save(s);jdbc.update("UPDATE rr.account SET state='CLOSED' WHERE account_id=?",p.accountId());assertThrows(IllegalArgumentException.class,()->sessions.save(s));assertNull(sessions.findById(s.getId()));assertThrows(IllegalArgumentException.class,()->user("closed"));}
    @Test void loggedOutSessionCannotBeRecreatedByLateSave(){var p=user("logout");var s=authenticated(p);sessions.save(s);sessions.deleteById(s.getId());assertThrows(IllegalArgumentException.class,()->sessions.save(s));assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM rr_internal.spring_session",Long.class));}
    @Test void pollingCannotRefreshInteractiveTimestamp(){var p=user("poll");var s=authenticated(p);sessions.save(s);long before=jdbc.queryForObject("SELECT last_access_time FROM rr_internal.spring_session WHERE session_id=?",Long.class,s.getId());s.setLastAccessedTime(Instant.now().plusSeconds(100));sessions.save(s);assertEquals(before,jdbc.queryForObject("SELECT last_access_time FROM rr_internal.spring_session WHERE session_id=?",Long.class,s.getId()));}
    @Test void corruptCiphertextAndRestartInvalidateRatherThanDeserialize(){var p=user("bytes");var s=authenticated(p);sessions.save(s);jdbc.update("UPDATE rr_internal.spring_session_attributes SET attribute_bytes=decode('aced0005','hex')");assertNull(sessions.findById(s.getId()));sessions.save(authenticated(p));sessions.invalidateForFreshKey();assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM rr_internal.spring_session",Long.class));}
    @Test void twoSubjectsMapByExactIdentityAndBootstrapScopeClears(){var a=user("alice");var b=user("bob");assertNotEquals(a.accountId(),b.accountId());assertEquals(a.accountId(),user("alice").accountId());assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM rr.workspace",Long.class));var tx=new TransactionTemplate(manager);tx.executeWithoutResult(s->{jdbc.queryForObject("SELECT set_config('rr.account_id',?,true)",String.class,a.accountId().toString());assertEquals(List.of(a.workspaceId()),jdbc.queryForList("SELECT workspace_id FROM rr.workspace",UUID.class));assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM rr.case_record",Long.class));});assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM rr.workspace",Long.class));}
    @Test void wrongReauthenticationSubjectDoesNotEnrollOrMutate(){var a=user("reauth-a");long before=jdbc.queryForObject("SELECT count(*) FROM rr.account",Long.class);assertThrows(IllegalArgumentException.class,()->binder.resolve("http://127.0.0.1/realms/test","new-wrong-subject",Instant.now(),Instant.now(),a));assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM rr.account",Long.class));assertThrows(IllegalArgumentException.class,()->binder.resolve("http://127.0.0.1/realms/test","reauth-a",Instant.now(),Instant.now().minusSeconds(400),a));}
    @Test void anonymousAndServiceCapsAreEnforcedAgainstActualRows(){
        long now=System.currentTimeMillis();jdbc.update("INSERT INTO rr_internal.spring_session(primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time) SELECT md5(i::text)::uuid::text,md5((i+10000)::text)::uuid::text,?,?,300,? FROM generate_series(1,128) i",now,now,now+300000);
        var anon=sessions.createSession();anon.setAttribute(SessionAttributeCodec.CSRF,new DefaultCsrfToken("X-CSRF-TOKEN","_csrf","opaque"));assertThrows(IllegalStateException.class,()->sessions.save(anon));
        sessions.invalidateForFreshKey();jdbc.update("INSERT INTO rr_internal.spring_session(primary_id,session_id,creation_time,last_access_time,max_inactive_interval,expiry_time,principal_name) SELECT md5(i::text)::uuid::text,md5((i+10000)::text)::uuid::text,?,?,1800,?,'synthetic:1' FROM generate_series(1,1024) i",now,now,now+1800000);
        var p=user("service-cap");assertThrows(IllegalStateException.class,()->sessions.save(authenticated(p)));assertEquals(1024L,jdbc.queryForObject("SELECT count(*) FROM rr_internal.spring_session",Long.class));
    }
    @Test void realJdbcLoadsEnforceIdleAbsoluteAndCurrentGeneration(){
        for(long minutes:List.of(30L,480L)){var p=user("expiry-"+minutes);var s=authenticated(p);sessions.save(s);try(var later=new BoundedJdbcSessions(jdbc,manager,codec,Clock.offset(Clock.systemUTC(),Duration.ofMinutes(minutes)))){assertNull(later.findById(s.getId()));}}
        var p=user("changed-generation");var s=authenticated(p);sessions.save(s);jdbc.update("UPDATE rr.account SET generation=2 WHERE account_id=?",p.accountId());assertThrows(IllegalArgumentException.class,()->sessions.save(s));assertNull(sessions.findById(s.getId()));
    }
    @Test void accountContextIsClearedAfterRollback(){
        var p=user("rollback-context");var tx=new TransactionTemplate(manager);assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(status->{jdbc.queryForObject("SELECT set_config('rr.account_id',?,true)",String.class,p.accountId().toString());assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM rr.workspace",Long.class));throw new IllegalStateException("rollback fixture");}));assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM rr.workspace",Long.class));
    }
    @Test void concurrentFirstCallbacksConvergeOnOneIdentity()throws Exception{
        var gate=new CountDownLatch(1);try(var executor=Executors.newFixedThreadPool(2)){var a=executor.submit(()->{gate.await();return user("first-callback-race");});var b=executor.submit(()->{gate.await();return user("first-callback-race");});gate.countDown();assertEquals(a.get(10,TimeUnit.SECONDS).accountId(),b.get(10,TimeUnit.SECONDS).accountId());}assertEquals(1L,jdbc.queryForObject("SELECT count(*) FROM rr.identity_binding WHERE subject='first-callback-race'",Long.class));
    }
}
