package dev.reconrail.access;

import jakarta.servlet.http.*;
import org.springframework.security.core.context.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import java.time.Clock;
import java.util.List;

/** Remove provider tokens before a context reaches JDBC serialization. */
public final class CompactSecurityContexts extends HttpSessionSecurityContextRepository {
    private final AccountBinder accounts;private final Clock clock;
    public CompactSecurityContexts(AccountBinder accounts,Clock clock){this.accounts=accounts;this.clock=clock;}
    @Override public void saveContext(SecurityContext context,HttpServletRequest request,HttpServletResponse response){
        if(context.getAuthentication() instanceof OAuth2AuthenticationToken auth&&auth.getPrincipal() instanceof OidcUser user){
            var session=request.getSession(false);AppPrincipal previous=null;
            if(session!=null&&session.getAttribute(SPRING_SECURITY_CONTEXT_KEY) instanceof SecurityContext old&&old.getAuthentication()!=null&&old.getAuthentication().getPrincipal() instanceof AppPrincipal p)previous=p;
            AppPrincipal principal=accounts.resolve(user.getIssuer().toString(),user.getSubject(),clock.instant(),user.getAuthenticatedAt(),previous);
            context=new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(principal,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            SecurityContextHolder.setContext(context);
        }
        super.saveContext(context,request,response);
    }
}
