package com.example.destination;

import com.example.destination.config.DestinationConfig;
import com.example.destination.config.DestinationConfigLoader;
import com.example.destination.config.InterfaceEntry;
import com.example.destination.listener.Listener;
import com.example.destination.listener.RestEchoListener;
import com.example.destination.listener.RestPeriodicSender;
import com.example.destination.listener.TcpEchoListener;
import com.example.destination.listener.UdpEchoListener;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DestinationMain {

    public static void main(String[] args) throws Exception {
        String configPath = System.getenv().getOrDefault("TRAFFIC_DESTINATION_CONFIG", "./config/destination-interfaces.yml");
        DestinationConfig config = new DestinationConfigLoader().load(Path.of(configPath));

        List<Listener> listeners = new ArrayList<>();
        for (InterfaceEntry entry : config.getInterfaces()) {
            boolean clientMode = "CLIENT".equalsIgnoreCase(entry.getMode());
            Listener listener = switch (entry.getProtocol().toUpperCase(Locale.ROOT)) {
                case "UDP" -> new UdpEchoListener(entry);
                case "TCP" -> new TcpEchoListener(entry);
                case "REST" -> clientMode ? new RestPeriodicSender(entry) : new RestEchoListener(entry);
                default -> throw new IllegalArgumentException("Unsupported protocol: " + entry.getProtocol());
            };
            listener.start();
            listeners.add(listener);
        }

        System.out.println("Traffic Destination App started with " + listeners.size() + " listener(s)");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> listeners.forEach(Listener::stop)));
        Thread.currentThread().join();
    }
}
