package com.phenikaa.cse702051.medbook.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** B8-L01: HTTP thật qua Tomcat; local HTTP nên HSTS được kiểm riêng ở online. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StaticHeadHeadersTest {

    @LocalServerPort
    private int port;

    private void assertHeaders(String method, String path) throws Exception {
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        var response = client.send(request, HttpResponse.BodyHandlers.discarding());
        assertEquals(200, response.statusCode(), method + " " + path);
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(null));
        assertEquals("DENY", response.headers().firstValue("X-Frame-Options").orElse(null));
        assertEquals("strict-origin-when-cross-origin",
                response.headers().firstValue("Referrer-Policy").orElse(null));
        org.junit.jupiter.api.Assertions.assertTrue(response.headers()
                .firstValue("Content-Security-Policy").orElse("").contains("default-src 'self'"));
    }

    @Test
    void headRootCarriesSecurityHeaders() throws Exception {
        assertHeaders("HEAD", "/");
    }

    @Test
    void headIndexCarriesSecurityHeaders() throws Exception {
        assertHeaders("HEAD", "/index.html");
    }

    @Test
    void getRootCarriesSecurityHeaders() throws Exception {
        assertHeaders("GET", "/");
    }
}
