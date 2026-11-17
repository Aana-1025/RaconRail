package dev.reconrail.access;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.http.*;
import com.zaxxer.hikari.*;
import javax.sql.DataSource;
import java.time.Clock;
import java.net.URI;

@Configuration
@Profile("auth")
@EnableSpringHttpSession
public class SessionConfiguration {
    static String required(Environment e,String name){String value=e.getProperty(name);if(value==null||value.isBlank())throw new IllegalStateException("MISSING_AUTH_CONFIGURATION");return value;}
    static URI origin(Environment e){
        URI origin=URI.create(required(e,"rr.auth.origin"));boolean dev=e.getProperty("rr.auth.development",Boolean.class,false);
        if(origin.getUserInfo()!=null||origin.getQuery()!=null||origin.getFragment()!=null||origin.getHost()==null||!(origin.getPath().isEmpty()||origin.getPath().equals("/")))throw new IllegalStateException("INVALID_AUTH_ORIGIN");
        if(dev){if(!origin.getScheme().equals("http")||!origin.getHost().equals("127.0.0.1")||!e.getProperty("server.address","127.0.0.1").equals("127.0.0.1"))throw new IllegalStateException("LOOPBACK_AUTH_ONLY");}
        else if(!origin.getScheme().equals("https")||!e.getProperty("server.forward-headers-strategy","none").equals("none"))throw new IllegalStateException("VERIFIED_TLS_CONFIGURATION_REQUIRED");
        return origin;
    }
    @Bean Clock authClock(){return new SecurityClock();}
    @Bean(destroyMethod="close") HikariDataSource controlDataSource(Environment e){
        origin(e);var config=new HikariConfig();config.setJdbcUrl(required(e,"rr.auth.jdbc-url"));config.setUsername(required(e,"rr.auth.jdbc-user"));config.setPassword(required(e,"rr.auth.jdbc-password"));config.setMaximumPoolSize(6);config.setMinimumIdle(0);config.setConnectionTimeout(1000);config.setPoolName("rr-control");config.setConnectionInitSql("SET TIME ZONE 'UTC'");return new HikariDataSource(config);
    }
    @Bean JdbcTemplate accessJdbc(DataSource dataSource){return new JdbcTemplate(dataSource);}
    @Bean DataSourceTransactionManager accessTransactions(DataSource dataSource){return new DataSourceTransactionManager(dataSource);}
    @Bean AccountBinder accountBinder(JdbcTemplate jdbc,DataSourceTransactionManager manager){return new AccountBinder(jdbc,manager);}
    @Bean(destroyMethod="close") BoundedJdbcSessions sessionRepository(JdbcTemplate jdbc,DataSourceTransactionManager manager,Clock clock){
        var sessions=new BoundedJdbcSessions(jdbc,manager,new SessionAttributeCodec(SessionEnvelope.ephemeral()),clock);
        // A fresh unpredictable process key/counter never attempts to decode old sessions.
        sessions.invalidateForFreshKey();return sessions;
    }
    @Bean CookieSerializer cookieSerializer(Environment e){var serializer=new DefaultCookieSerializer();boolean dev=e.getProperty("rr.auth.development",Boolean.class,false);serializer.setCookieName(dev?"rr_dev_session":"__Host-rr_session");serializer.setUseSecureCookie(!dev);serializer.setUseHttpOnlyCookie(true);serializer.setCookiePath("/");serializer.setSameSite("Lax");return serializer;}
}
