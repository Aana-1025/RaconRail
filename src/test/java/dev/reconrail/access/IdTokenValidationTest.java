package dev.reconrail.access;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IdTokenValidationTest {
    @Test void actualJwtDecoderRejectsSignatureIssuerAudienceAndExpiry()throws Exception{
        RSAKey key=new RSAKeyGenerator(2048).keyID("fixture").generate();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        byte[] jwks=new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server.createContext("/jwks",exchange->{exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,jwks.length);exchange.getResponseBody().write(jwks);exchange.close();});server.start();
        try{
            var client=ClientRegistration.withRegistrationId("fixture").clientId("client").clientSecret("ephemeral-fixture-only").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://127.0.0.1/callback").scope("openid").issuerUri("https://issuer.example.invalid").authorizationUri("https://issuer.example.invalid/auth").tokenUri("https://issuer.example.invalid/token").jwkSetUri("http://127.0.0.1:"+server.getAddress().getPort()+"/jwks").userNameAttributeName("sub").build();
            var decoder=new OidcConfiguration().idTokenDecoder().createDecoder(client);
            String valid=token(key,"https://issuer.example.invalid","client",Instant.now().plusSeconds(300));assertEquals("subject",decoder.decode(valid).getSubject());
            assertThrows(Exception.class,()->decoder.decode(token(key,"https://other.example.invalid","client",Instant.now().plusSeconds(300))));
            assertThrows(Exception.class,()->decoder.decode(token(key,"https://issuer.example.invalid","wrong-audience",Instant.now().plusSeconds(300))));
            assertThrows(Exception.class,()->decoder.decode(token(key,"https://issuer.example.invalid","client",Instant.now().minusSeconds(120))));
            RSAKey other=new RSAKeyGenerator(2048).keyID("fixture").generate();assertThrows(Exception.class,()->decoder.decode(token(other,"https://issuer.example.invalid","client",Instant.now().plusSeconds(300))));
        }finally{server.stop(0);}
    }
    private static String token(RSAKey key,String issuer,String audience,Instant expiry)throws Exception{
        var claims=new JWTClaimsSet.Builder().issuer(issuer).subject("subject").audience(audience).issueTime(Date.from(Instant.now().minusSeconds(10))).expirationTime(Date.from(expiry)).build();
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),claims);jwt.sign(new RSASSASigner(key));return jwt.serialize();
    }
}
