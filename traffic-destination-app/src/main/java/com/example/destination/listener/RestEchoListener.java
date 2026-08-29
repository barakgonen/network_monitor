package com.example.destination.listener;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;

public class RestEchoListener implements Listener {

    private final InterfaceEntry config;
    private HttpServer server;

    public RestEchoListener(InterfaceEntry config) {
        this.config = config;
    }

    @Override
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("[" + config.getKey() + "] REST destination listening on port " + config.getPort()
                + " (replyMode=" + config.getReplyMode() + ")");
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] requestBody;
        try (InputStream body = exchange.getRequestBody()) {
            requestBody = body.readAllBytes();
        }

        System.out.println("[" + config.getKey() + "] received " + exchange.getRequestMethod() + " "
                + exchange.getRequestURI() + " (" + requestBody.length + " bytes) from "
                + exchange.getRemoteAddress());

        byte[] responseBody;
        int status;
        if (config.getReplyMode() == ReplyMode.ECHO) {
            status = 200;
            responseBody = requestBody;
            List<String> contentType = exchange.getRequestHeaders().get("Content-Type");
            String type = (contentType != null && !contentType.isEmpty()) ? contentType.get(0) : "application/json";
            exchange.getResponseHeaders().set("Content-Type", type);
        } else {
            status = 204;
            responseBody = new byte[0];
        }

        exchange.sendResponseHeaders(status, responseBody.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(responseBody);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
