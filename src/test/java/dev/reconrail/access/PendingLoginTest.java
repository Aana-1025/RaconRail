package dev.reconrail.access;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class PendingLoginTest {
    OAuth2AuthorizationRequest auth(String state){return OAuth2AuthorizationRequest.authorizationCode().authorizationUri("https://issuer.example.invalid/auth").clientId("client").redirectUri("https://app.example.invalid/callback").scope("openid").state(state).build();}
    @Test void pendingStatesAreBrowserBoundSingleUseBoundedAndExpire(){
        var now=new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));var repo=new BoundedAuthorizationRequests(new Clock(){public Instant instant(){return now.get();}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}});
        var browser=new MockHttpServletRequest();var response=new MockHttpServletResponse();repo.saveAuthorizationRequest(auth("one"),browser,response);repo.saveAuthorizationRequest(auth("two"),browser,response);assertThrows(IllegalStateException.class,()->repo.saveAuthorizationRequest(auth("three"),browser,response));
        browser.setParameter("state","unknown");assertNull(repo.removeAuthorizationRequest(browser,response));browser.setParameter("state","one");assertNotNull(repo.removeAuthorizationRequest(browser,response));assertNull(repo.removeAuthorizationRequest(browser,response));
        var other=new MockHttpServletRequest();other.setParameter("state","two");assertNull(repo.loadAuthorizationRequest(other));now.set(now.get().plusSeconds(300));browser.setParameter("state","two");assertNull(repo.loadAuthorizationRequest(browser));
    }
}
