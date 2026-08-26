package com.example.proxy;

import com.example.proxy.config.ProxyRelayConfig;
import com.example.proxy.config.RelayConfigLoader;
import com.example.proxy.config.RelayEntry;
import com.example.proxy.relay.Relay;
import com.example.proxy.relay.RestRelay;
import com.example.proxy.relay.TcpRelay;
import com.example.proxy.relay.UdpRelay;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProxyMain {

    public static void main(String[] args) throws Exception {
        String configPath = System.getenv().getOrDefault("TRAFFIC_PROXY_CONFIG", "./config/proxy-relays.yml");
        ProxyRelayConfig config = new RelayConfigLoader().load(Path.of(configPath));

        List<Relay> relays = new ArrayList<>();
        for (RelayEntry entry : config.getRelays()) {
            Relay relay = switch (entry.getProtocol().toUpperCase(Locale.ROOT)) {
                case "UDP" -> new UdpRelay(entry);
                case "TCP" -> new TcpRelay(entry);
                case "REST" -> new RestRelay(entry);
                default -> throw new IllegalArgumentException("Unsupported protocol: " + entry.getProtocol());
            };
            relay.start();
            relays.add(relay);
        }

        System.out.println("Traffic Proxy App started with " + relays.size() + " relay(s)");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> relays.forEach(Relay::stop)));
        Thread.currentThread().join();
    }
}
