package dev.reconrail.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GatedHttpIT.Config.class)
class GatedHttpIT {
    static final AuthorityScope SCOPE = AuthorityScope.account(
            UUID.fromString("00000000-0000-0000-0000-000000000701"), 7);
    static volatile CountDownLatch firstChunk;
    static volatile CountDownLatch continueWrite;
    @LocalServerPort int port;
    @Autowired IoGate gate;

    @BeforeEach void reset() { firstChunk = new CountDownLatch(1); continueWrite = new CountDownLatch(1); }

    @Test void actualServletWriterCannotAuthorizeANewChunkAfterDrain() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/__rr_test/gated")).build();
        var pending = HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        assertTrue(firstChunk.await(3, TimeUnit.SECONDS));
        assertEquals(IoGate.DrainResult.DRAINED, gate.prepareDeny(SCOPE));
        continueWrite.countDown();
        var response = pending.get(3, TimeUnit.SECONDS);
        assertEquals(200, response.statusCode());
        assertEquals("before", new String(response.body(), StandardCharsets.UTF_8));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean IoGate rr07TestGate() { return new IoGate(); }
        @Bean GatedEndpoint gatedEndpoint(IoGate rr07TestGate) { return new GatedEndpoint(rr07TestGate); }
    }

    @RestController
    static class GatedEndpoint {
        private final IoGate gate;
        GatedEndpoint(IoGate gate) { this.gate = gate; }
        @GetMapping("/__rr_test/gated") void write(HttpServletResponse response) throws Exception {
            response.setContentType("application/octet-stream"); response.setBufferSize(1);
            var output = new GatedServletOutputStream(response.getOutputStream(), gate, SCOPE, ignored -> {});
            output.write("before".getBytes(StandardCharsets.UTF_8)); response.flushBuffer(); firstChunk.countDown();
            if (!continueWrite.await(3, TimeUnit.SECONDS)) throw new IOException("TEST_COORDINATION_TIMEOUT");
            try { output.write("after".getBytes(StandardCharsets.UTF_8)); }
            catch (IOException expected) {
                if (!"RESPONSE_AUTHORITY_DENIED".equals(expected.getMessage())) throw expected;
            }
        }
    }
}
