package com.example.tester.rest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RestListenerTest {

    private RestListener listener;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        if (listener != null) {
            listener.close();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void echoesRequestBodyBack() throws Exception {
        int port = freePort();
        listener = new RestListener(port);
        listener.start();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/pets"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Whiskers\"}"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"name\":\"Whiskers\"}");
    }

    @Test
    void close_stopsAcceptingNewRequests() throws Exception {
        int port = freePort();
        listener = new RestListener(port);
        listener.start();

        listener.close();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/pets"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> httpClient.send(request, HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(java.net.ConnectException.class);
    }
}
