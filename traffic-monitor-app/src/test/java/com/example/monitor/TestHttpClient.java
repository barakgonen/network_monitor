package com.example.monitor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Thin adapter reproducing the handful of {@code TestRestTemplate} methods this IT suite used
 * ({@code getForEntity}/{@code postForEntity}/{@code getForObject}), backed by
 * {@link RestTestClient} - Spring Boot 4 dropped {@code TestRestTemplate} (and the
 * {@code @SpringBootTest} auto-wiring that provided it) in favor of the WebTestClient-style
 * fluent {@code RestTestClient}. This keeps every existing IT call site unchanged rather than
 * rewriting each one to the new fluent API.
 */
class TestHttpClient {

    private final RestTestClient client;

    TestHttpClient() {
        this.client = RestTestClient.bindToServer().build();
    }

    <T> ResponseEntity<T> getForEntity(String url, Class<T> responseType) {
        EntityExchangeResult<T> result = client.get().uri(url).exchange().expectBody(responseType).returnResult();
        return ResponseEntity.status(result.getStatus()).body(result.getResponseBody());
    }

    <T> T getForObject(String url, Class<T> responseType) {
        return getForEntity(url, responseType).getBody();
    }

    <T> ResponseEntity<T> postForEntity(String url, Object body, Class<T> responseType) {
        var spec = (body != null)
                ? client.post().uri(url).contentType(MediaType.APPLICATION_JSON).body(body)
                : client.post().uri(url);

        EntityExchangeResult<T> result = spec.exchange().expectBody(responseType).returnResult();
        return ResponseEntity.status(result.getStatus()).body(result.getResponseBody());
    }
}
