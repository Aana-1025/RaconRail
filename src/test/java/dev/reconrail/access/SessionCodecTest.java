package dev.reconrail.access;

import org.junit.jupiter.api.Test;
import javax.crypto.KeyGenerator;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.context.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import static org.junit.jupiter.api.Assertions.*;

class SessionCodecTest {
    SessionEnvelope ring()throws Exception{var g=KeyGenerator.getInstance("AES");g.init(256);return new SessionEnvelope("k1",Map.of("k1",g.generateKey()));}
    @Test void aeadBindsEveryContextAndRejectsTampering()throws Exception{
        var e=ring();byte[] plain="sensitive pending proof".getBytes(StandardCharsets.UTF_8),a=e.encrypt("session","attr",plain),b=e.encrypt("session","attr",plain);
        assertFalse(Arrays.equals(a,b));assertArrayEquals(plain,e.decrypt("session","attr",a));
        assertThrows(IllegalArgumentException.class,()->e.decrypt("other","attr",a));assertThrows(IllegalArgumentException.class,()->e.decrypt("session","other",a));
        a[a.length-1]^=1;assertThrows(IllegalArgumentException.class,()->e.decrypt("session","attr",a));
        assertThrows(IllegalArgumentException.class,()->e.encrypt("session","attr",new byte[65475]));
        assertTrue(e.encrypt("session","attr",new byte[65474]).length<=65536);
    }
    @Test void restartKeysFailClosedAndRotatedReadersRetainOldKey()throws Exception{
        var g=KeyGenerator.getInstance("AES");g.init(256);var old=g.generateKey();var fresh=g.generateKey();var a=new SessionEnvelope("old",Map.of("old",old));byte[] encoded=a.encrypt("s","a",new byte[]{1});
        assertThrows(IllegalArgumentException.class,()->new SessionEnvelope("fresh",Map.of("fresh",fresh)).decrypt("s","a",encoded));
        assertArrayEquals(new byte[]{1},new SessionEnvelope("fresh",Map.of("fresh",fresh,"old",old)).decrypt("s","a",encoded));
    }
    @Test void onlyCompactTokenFreeContextRoundTrips()throws Exception{
        var c=new SessionAttributeCodec(ring());var now=Instant.parse("2026-01-01T00:00:00Z");var p=new AppPrincipal(UUID.randomUUID(),UUID.randomUUID(),1,now,now,null);
        var context=new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(p,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        byte[] bytes=c.encode("s",SessionAttributeCodec.CONTEXT,context);var decoded=(SecurityContext)c.decode("s",SessionAttributeCodec.CONTEXT,bytes);assertEquals(p,decoded.getAuthentication().getPrincipal());assertNull(decoded.getAuthentication().getCredentials());
        assertThrows(IllegalArgumentException.class,()->c.encode("s","unexpected",new Object()));assertThrows(IllegalArgumentException.class,()->c.encode("s",SessionAttributeCodec.CONTEXT,new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated("provider-token",null,List.of()))));
    }
    @Test void csrfAndTwoPendingRequestsHaveExplicitCodecs()throws Exception{
        var c=new SessionAttributeCodec(ring());var token=new DefaultCsrfToken("X-CSRF-TOKEN","_csrf","opaque");assertEquals("opaque",((org.springframework.security.web.csrf.CsrfToken)c.decode("s",SessionAttributeCodec.CSRF,c.encode("s",SessionAttributeCodec.CSRF,token))).getToken());
        var request=OAuth2AuthorizationRequest.authorizationCode().authorizationUri("http://127.0.0.1:9000/authorize").clientId("rr").redirectUri("http://127.0.0.1:8000/callback").scopes(Set.of("openid")).state("state").attributes(a->a.put("code_verifier","secret")).additionalParameters(a->a.put("code_challenge_method","S256")).build();
        var pending=new PendingLogins(List.of(new PendingLogins.Entry(request,Instant.EPOCH)));var decoded=(PendingLogins)c.decode("s",SessionAttributeCodec.PENDING,c.encode("s",SessionAttributeCodec.PENDING,pending));assertEquals("secret",decoded.requests().getFirst().request().getAttribute("code_verifier"));assertThrows(IllegalArgumentException.class,()->new PendingLogins(List.of(pending.requests().getFirst(),pending.requests().getFirst(),pending.requests().getFirst())));
    }
    @Test void absoluteAndIdleBoundsAreInclusiveAndRejectClockReversal(){
        var now=Instant.parse("2026-01-01T00:00:00Z");var p=new AppPrincipal(UUID.randomUUID(),UUID.randomUUID(),1,now,now,null);assertFalse(p.expired(now.plusSeconds(1799)));assertTrue(p.expired(now.plusSeconds(1800)));assertTrue(p.expired(now.minusSeconds(1)));
        var active=new AppPrincipal(p.accountId(),p.workspaceId(),1,now,now.plusSeconds(28000),null);assertTrue(active.expired(now.plusSeconds(28800)));
    }
    @Test void keyBudgetRotatesBeforeOneMillionAndOldSessionsStillDecode()throws Exception{
        var ring=ring();byte[] old=ring.encrypt("s","a",new byte[]{1});var field=SessionEnvelope.class.getDeclaredField("uses");field.setAccessible(true);((java.util.concurrent.atomic.AtomicLong)field.get(ring)).set(999998);byte[] fresh=ring.encrypt("s","a",new byte[]{2});assertFalse(Arrays.equals(Arrays.copyOfRange(old,2,4),Arrays.copyOfRange(fresh,2,4)));assertArrayEquals(new byte[]{1},ring.decrypt("s","a",old));assertArrayEquals(new byte[]{2},ring.decrypt("s","a",fresh));
    }
    @Test void detectedClockReversalRemainsClosed(){
        var time=new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));var clock=new SecurityClock(time::get);clock.instant();time.set(time.get().minusSeconds(1));assertThrows(IllegalStateException.class,clock::instant);time.set(time.get().plusSeconds(100));assertThrows(IllegalStateException.class,clock::instant);
    }
}
