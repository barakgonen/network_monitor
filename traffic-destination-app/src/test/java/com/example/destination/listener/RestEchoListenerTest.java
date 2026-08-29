package com.example.destination.listener;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RestEchoListenerTest {

    private RestEchoListener listener;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        if (listener != null) {
            listener.stop();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static InterfaceEntry entry(int port, ReplyMode replyMode) {
        InterfaceEntry entry = new InterfaceEntry();
        entry.setKey("test");
        entry.setProtocol("REST");
        entry.setPort(port);
        entry.setReplyMode(replyMode);
        return entry;
    }

    @Test
    void echoMode_returnsSameBodyAndStatus200() throws Exception {
        int port = freePort();
        listener = new RestEchoListener(entry(port, ReplyMode.ECHO));
        listener.start();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/pets"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Fluffy\"}"))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"name\":\"Fluffy\"}");
    }

    @Test
    void noneMode_returns204WithEmptyBody() throws Exception {
        int port = freePort();
        listener = new RestEchoListener(entry(port, ReplyMode.NONE));
        listener.start();

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/pets"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Fluffy\"}"))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
    }
}
