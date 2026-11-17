package dev.reconrail.access;

import org.springframework.session.*;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.security.core.context.SecurityContext;
import java.time.*;
import java.util.*;

/** Atomic independent session admission over the existing control datasource. */
public final class BoundedJdbcSessions implements SessionRepository<BoundedJdbcSessions.SafeSession>,AutoCloseable {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final JdbcIndexedSessionRepository implementation;
    private final SessionRepository<Session> delegate;
    private final SessionAttributeCodec codec;
    private final Clock clock;
    private record Encoded(byte[] bytes){Encoded{bytes=bytes.clone();} @Override public byte[] bytes(){return bytes.clone();}}
    @SuppressWarnings({"rawtypes","unchecked"})
    public BoundedJdbcSessions(JdbcTemplate jdbc,PlatformTransactionManager manager,SessionAttributeCodec codec,Clock clock){
        this.jdbc=jdbc;this.codec=codec;this.clock=clock;
        transaction=new TransactionTemplate(manager);transaction.setTimeout(5);
        implementation=new JdbcIndexedSessionRepository(jdbc,transaction);implementation.setTableName("rr_internal.spring_session");implementation.setCleanupCron("-");implementation.setFlushMode(FlushMode.ON_SAVE);implementation.setSaveMode(SaveMode.ON_SET_ATTRIBUTE);
        var conversion=new GenericConversionService();
        conversion.addConverter(Object.class,byte[].class,(Converter<Object,byte[]>)value->{if(!(value instanceof Encoded e))throw SessionEnvelope.invalid();return e.bytes();});
        conversion.addConverter(byte[].class,Object.class,(Converter<byte[],Object>)Encoded::new);implementation.setConversionService(conversion);
        implementation.setIndexResolver(session->{Object raw=session.getAttribute(SessionAttributeCodec.CONTEXT);if(raw==null)return Map.of();var context=(SecurityContext)codec.decode(session.getId(),SessionAttributeCodec.CONTEXT,((Encoded)raw).bytes());return Map.of(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,((AppPrincipal)context.getAuthentication().getPrincipal()).getName());});
        implementation.afterPropertiesSet();delegate=(SessionRepository)(Object)implementation;
    }
    /** New ephemeral key/counter may start only after this invalidation commits. */
    public void invalidateForFreshKey(){independent();transaction.executeWithoutResult(s->jdbc.update("DELETE FROM rr_internal.spring_session"));}
    @Override public SafeSession createSession(){Session raw=delegate.createSession();raw.setMaxInactiveInterval(Duration.ofMinutes(5));return new SafeSession(raw,true);}
    @Override public SafeSession findById(String id){
        independent();Session raw=delegate.findById(id);if(raw==null)return null;
        try{var safe=new SafeSession(raw,false);for(String name:raw.getAttributeNames())safe.getAttribute(name);if(safe.isExpired()){deleteById(id);return null;}AppPrincipal p=safe.principal();if(p!=null&&!active(p)){deleteById(id);return null;}return safe;}catch(RuntimeException e){deleteById(id);return null;}
    }
    @Override public void save(SafeSession session){
        independent();if(session.isExpired())throw SessionEnvelope.invalid();
        transaction.executeWithoutResult(status->{
            jdbc.execute("SET LOCAL lock_timeout='250ms'");jdbc.execute("SET LOCAL statement_timeout='2s'");jdbc.execute("SET LOCAL idle_in_transaction_session_timeout='2s'");
            jdbc.update("INSERT INTO rr.service_admission(singleton,queued_runs,nonterminal_runs,staged_pairs,staged_bytes) VALUES(1,0,0,0,0) ON CONFLICT(singleton) DO NOTHING");
            jdbc.queryForObject("SELECT singleton FROM rr.service_admission WHERE singleton=1 FOR UPDATE",Integer.class);
            jdbc.update("DELETE FROM rr_internal.spring_session WHERE expiry_time<=?",clock.millis());
            if(!session.fresh&&count("SELECT count(*) FROM rr_internal.spring_session WHERE session_id=?",session.persistedId)==0)throw SessionEnvelope.invalid();
            AppPrincipal principal=session.principal();
            if(principal!=null){
                var state=jdbc.queryForList("SELECT state,generation FROM rr.account WHERE account_id=? FOR UPDATE",principal.accountId());
                if(state.size()!=1||!"ACTIVE".equals(state.getFirst().get("state"))||((Number)state.getFirst().get("generation")).longValue()!=principal.generation())throw SessionEnvelope.invalid();
                if(count("SELECT count(*) FROM rr_internal.spring_session WHERE principal_name LIKE ? AND session_id<>?",principal.accountId()+":%",session.persistedId)>=5)throw new IllegalStateException("SESSION_CAP");
                session.raw.setLastAccessedTime(principal.lastActivity());session.raw.setMaxInactiveInterval(Duration.ofSeconds(Math.max(1,Math.min(1800,Duration.between(principal.lastActivity(),principal.originalLogin().plusSeconds(28800)).getSeconds()))));
            }else if(count("SELECT count(*) FROM rr_internal.spring_session WHERE principal_name IS NULL AND session_id<>?",session.persistedId)>=128)throw new IllegalStateException("LOGIN_CAP");
            if(count("SELECT count(*) FROM rr_internal.spring_session WHERE session_id<>?",session.persistedId)>=1024)throw new IllegalStateException("SERVICE_SESSION_CAP");
            int size=0;for(String name:session.raw.getAttributeNames()){Object value=session.raw.getAttribute(name);if(!(value instanceof Encoded encoded))throw SessionEnvelope.invalid();size=Math.addExact(size,encoded.bytes().length);if(size>262144)throw SessionEnvelope.invalid();}
            delegate.save(session.raw);
        });
        session.fresh=false;session.persistedId=session.getId();
    }
    @Override public void deleteById(String id){independent();delegate.deleteById(id);}
    private boolean active(AppPrincipal p){return count("SELECT count(*) FROM rr.account WHERE account_id=? AND generation=? AND state='ACTIVE'",p.accountId(),p.generation())==1;}
    private long count(String sql,Object...args){return Objects.requireNonNull(jdbc.queryForObject(sql,Long.class,args));}
    private static void independent(){if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("OUTER_TRANSACTION_FORBIDDEN");}
    @Override public void close(){implementation.destroy();}
    public final class SafeSession implements Session {
        private final Session raw;private boolean fresh;private String persistedId;
        SafeSession(Session raw,boolean fresh){this.raw=raw;this.fresh=fresh;persistedId=raw.getId();}
        public AppPrincipal principal(){SecurityContext context=getAttribute(SessionAttributeCodec.CONTEXT);return context==null?null:(AppPrincipal)context.getAuthentication().getPrincipal();}
        @Override public String getId(){return raw.getId();}
        @Override public String changeSessionId(){Map<String,Object> decoded=new HashMap<>();for(String name:raw.getAttributeNames())decoded.put(name,getAttribute(name));String id=raw.changeSessionId();decoded.forEach(this::setAttribute);return id;}
        @Override @SuppressWarnings("unchecked") public <T>T getAttribute(String name){Object value=raw.getAttribute(name);return value==null?null:(T)codec.decode(raw.getId(),name,((Encoded)value).bytes());}
        @Override public Set<String>getAttributeNames(){return Set.copyOf(raw.getAttributeNames());}
        @Override public void setAttribute(String name,Object value){if(value==null){raw.removeAttribute(name);return;}raw.setAttribute(name,new Encoded(codec.encode(raw.getId(),name,value)));}
        @Override public void removeAttribute(String name){raw.removeAttribute(name);}
        @Override public Instant getCreationTime(){return raw.getCreationTime();}
        @Override public void setLastAccessedTime(Instant ignored){/* Polling never extends interactive activity. */}
        @Override public Instant getLastAccessedTime(){return raw.getLastAccessedTime();}
        @Override public void setMaxInactiveInterval(Duration ignored){/* Bounds are set by admission, not a framework caller. */}
        @Override public Duration getMaxInactiveInterval(){return raw.getMaxInactiveInterval();}
        @Override public boolean isExpired(){AppPrincipal p=principal();return p==null?!clock.instant().isBefore(raw.getCreationTime().plusSeconds(300)):p.expired(clock.instant());}
    }
}
