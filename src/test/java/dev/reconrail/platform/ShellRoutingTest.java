package dev.reconrail.platform;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShellRoutingTest {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(), HttpResponse.BodyHandlers.ofString());
    }
    @Test void packagedShellAndAllowlistedDeepLinkServeSameArtifact() throws Exception {
        var root = get("/"); var example = get("/example");
        assertEquals(200, root.statusCode()); assertEquals(root.body(), example.body());
        assertTrue(root.body().contains("<title>ReconRail"));
        assertTrue(root.body().contains("/assets/"));
        assertEquals("no-store", root.headers().firstValue("cache-control").orElseThrow());
        assertTrue(root.headers().firstValue("content-security-policy").orElseThrow().contains("frame-ancestors 'none'"));
    }
    @Test void apiManagementAndUnknownPathsNeverMasqueradeAsApplicationHtml() throws Exception {
        for (var path : new String[]{"/api/cases", "/actuator/health", "/unexpected"}) {
            var response = get(path);
            assertEquals(404, response.statusCode(), path);
            assertFalse(response.body().contains("<title>ReconRail"), path);
            assertFalse(response.body().contains("stackTrace"), path);
        }
    }
}
