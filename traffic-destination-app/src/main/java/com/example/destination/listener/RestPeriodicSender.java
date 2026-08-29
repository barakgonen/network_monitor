package com.example.destination.listener;

import com.example.destination.config.InterfaceEntry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * REST CLIENT mode: HTTP has no persistent-connection equivalent of TCP's reconnect loop, so
 * instead of binding a server, this periodically POSTs {@link InterfaceEntry#getRequestBody()}
 * to {@code host:port}{@code path} on a fixed interval - needed when the peer this interface
 * talks to (e.g. traffic-proxy-app's RestRelay in a reversed-role relay entry) is itself the one
 * listening rather than connecting in.
 */
public class RestPeriodicSender implements Listener {

    private final InterfaceEntry config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private ScheduledExecutorService executor;

    public RestPeriodicSender(InterfaceEntry config) {
        this.config = config;
    }

    @Override
    public void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "destination-rest-client-" + config.getKey());
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::sendRequest, 0, config.getIntervalMillis(), TimeUnit.MILLISECONDS);

        System.out.println("[" + config.getKey() + "] REST periodic client sending to "
                + config.getHost() + ":" + config.getPort() + config.getPath()
                + " every " + config.getIntervalMillis() + "ms");
    }

    private void sendRequest() {
        try {
            URI uri = URI.create("http://" + config.getHost() + ":" + config.getPort() + config.getPath());
            String body = config.getRequestBody() != null ? config.getRequestBody() : "{}";
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[" + config.getKey() + "] sent REST request, status=" + response.statusCode()
                    + ", responseBody=" + response.body());
        } catch (Exception e) {
            System.err.println("[" + config.getKey() + "] REST periodic send failed: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
