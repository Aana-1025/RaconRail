package dev.reconrail.access;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.client.oidc.authentication.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.client.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.net.URI;
import java.util.*;

@Configuration
@Profile("auth")
class OidcConfiguration {
    @Bean ClientRegistrationRepository clients(Environment e){
        String issuer=SessionConfiguration.required(e,"rr.auth.issuer"),origin=SessionConfiguration.origin(e).toString();URI i=URI.create(issuer);
        if(i.getHost()==null||i.getUserInfo()!=null||i.getQuery()!=null||i.getFragment()!=null||!(i.getScheme().equals("https")||(e.getProperty("rr.auth.development",Boolean.class,false)&&i.getScheme().equals("http")&&i.getHost().equals("127.0.0.1"))))throw new IllegalStateException("INVALID_TRUSTED_ISSUER");
        var registration=ClientRegistration.withRegistrationId("reconrail").clientId(SessionConfiguration.required(e,"rr.auth.client-id")).clientSecret(SessionConfiguration.required(e,"rr.auth.client-secret")).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).scope("openid").redirectUri(origin+"/login/oauth2/code/reconrail").issuerUri(issuer).authorizationUri(issuer+"/protocol/openid-connect/auth").tokenUri(issuer+"/protocol/openid-connect/token").jwkSetUri(issuer+"/protocol/openid-connect/certs").userNameAttributeName("sub").build();
        return new InMemoryClientRegistrationRepository(registration);
    }
    @Bean JwtDecoderFactory<ClientRegistration> idTokenDecoder(){
        var factory=new SimpleClientHttpRequestFactory();factory.setConnectTimeout(3000);factory.setReadTimeout(3000);var rest=new RestTemplate(factory);Map<String,JwtDecoder> cache=new HashMap<>();
        return client->{synchronized(cache){return cache.computeIfAbsent(client.getRegistrationId(),id->{var decoder=NimbusJwtDecoder.withJwkSetUri(client.getProviderDetails().getJwkSetUri()).jwsAlgorithm(SignatureAlgorithm.RS256).restOperations(rest).build();decoder.setClaimSetConverter(OidcIdTokenDecoderFactory.createDefaultClaimTypeConverter());decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ofSeconds(60)),new OidcIdTokenValidator(client)));return decoder;});}};
    }
    @Bean SecurityFilterChain authenticatedSecurity(HttpSecurity http,ClientRegistrationRepository clients,AccountBinder accounts,Clock clock,Environment env)throws Exception{
        String origin=SessionConfiguration.origin(env).toString();var contexts=new CompactSecurityContexts(accounts,clock);
        var resolver=new DefaultOAuth2AuthorizationRequestResolver(clients,"/oauth2/authorization");resolver.setAuthorizationRequestCustomizer(builder->{OAuth2AuthorizationRequestCustomizers.withPkce().accept(builder);builder.additionalParameters(p->p.put("max_age","0"));});
        var tokenFactory=new SimpleClientHttpRequestFactory();tokenFactory.setConnectTimeout(3000);tokenFactory.setReadTimeout(3000);
        var tokenClient=new org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient();
        tokenClient.setRestClient(RestClient.builder().requestFactory(tokenFactory).messageConverters(converters->{converters.clear();converters.add(new org.springframework.http.converter.FormHttpMessageConverter());converters.add(new org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter());}).defaultStatusHandler(new org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler()).build());
        http.headers(h->h.referrerPolicy(p->p.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            .securityContext(c->c.securityContextRepository(contexts)).requestCache(c->c.requestCache(new NullRequestCache()))
            .authorizeHttpRequests(a->a.requestMatchers("/api/csrf","/api/auth/availability").permitAll().requestMatchers("/api/session","/api/auth/logout").authenticated().requestMatchers("/api/**").denyAll().anyRequest().permitAll())
            .exceptionHandling(c->c.authenticationEntryPoint((r,s,x)->s.setStatus(401)).accessDeniedHandler((r,s,x)->s.setStatus(403)))
            .csrf(c->c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
            .oauth2Login(c->c.tokenEndpoint(t->t.accessTokenResponseClient(tokenClient)).loginPage("/sign-in").authorizationEndpoint(a->a.authorizationRequestResolver(resolver).authorizationRequestRepository(new BoundedAuthorizationRequests(clock))).authorizedClientRepository(new DiscardAuthorizedClients())
                .userInfoEndpoint(u->u.oidcUserService(request->{var token=request.getIdToken();var current=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();AppPrincipal previous=current!=null&&current.getPrincipal() instanceof AppPrincipal p?p:null;try{accounts.requireSameSubject(token.getIssuer().toString(),token.getSubject(),token.getAuthenticatedAt(),previous,clock.instant());}catch(IllegalArgumentException denied){throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"));}return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")),token);}))
                .successHandler((r,s,a)->{var session=r.getSession(false);if(session!=null)session.removeAttribute(SessionAttributeCodec.PENDING);s.sendRedirect("/cases");})
                .failureHandler((r,s,x)->{String code=x instanceof OAuth2AuthenticationException a?a.getError().getErrorCode():"LOGIN_PROOF_FAILED";if(!Set.of("invalid_nonce","invalid_id_token","authorization_request_not_found","invalid_grant","invalid_token_response","invalid_state_parameter","invalid_request","invalid_client").contains(code))code="LOGIN_PROOF_FAILED";org.slf4j.LoggerFactory.getLogger(OidcConfiguration.class).warn("Login denied: {}",code);s.sendRedirect("/sign-in?error=login");}))
            .logout(c->c.logoutUrl("/api/auth/logout").invalidateHttpSession(true).clearAuthentication(true).logoutSuccessHandler((r,s,a)->{var cookie=new jakarta.servlet.http.Cookie(env.getProperty("rr.auth.development",Boolean.class,false)?"rr_dev_session":"__Host-rr_session","");cookie.setPath("/");cookie.setHttpOnly(true);cookie.setSecure(!env.getProperty("rr.auth.development",Boolean.class,false));cookie.setMaxAge(0);cookie.setAttribute("SameSite","Lax");s.addCookie(cookie);s.setStatus(204);}));
        http.addFilterBefore(new OncePerRequestFilter(){
            @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws ServletException,IOException{
                if(!java.net.URI.create(origin).getRawAuthority().equals(r.getHeader("Host"))){s.setStatus(400);return;}
                if(!Set.of("GET","HEAD","OPTIONS").contains(r.getMethod())&&!origin.equals(r.getHeader("Origin"))){s.setStatus(403);return;}
                chain.doFilter(r,s);
            }
        },CsrfFilter.class);
        return http.build();
    }
}
