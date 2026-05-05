package com.example.tester;

import com.example.tester.config.ScenarioLoader;
import com.example.tester.config.TesterScenario;
import com.example.tester.payload.PayloadFactory;
import com.example.tester.udp.UdpPublisher;

import java.nio.file.Path;
import java.util.HexFormat;

public class TesterMain {
    public static void main(String[] args) throws Exception {
        String configPath = System.getenv().getOrDefault("TRAFFIC_TESTER_CONFIG", "/app/config/tester-scenario.yml");

        TesterScenario scenario = new ScenarioLoader().load(Path.of(configPath));
        byte[] payload = new PayloadFactory().create(scenario.getPayload());

        UdpPublisher udpPublisher = new UdpPublisher();

        System.out.println("Traffic Tester App started");
        System.out.println("Scenario: " + configPath);
        System.out.println("UDP target: " + scenario.getUdp().getHost() + ":" + scenario.getUdp().getPort());
        System.out.println("Payload size: " + payload.length + " bytes");
        System.out.println("Payload hex: " + HexFormat.of().formatHex(payload));

        for (int i = 1; i <= scenario.getRepeat(); i++) {
            udpPublisher.send(scenario.getUdp().getHost(), scenario.getUdp().getPort(), payload);
            System.out.println("Sent UDP message " + i + "/" + scenario.getRepeat());

            if (i < scenario.getRepeat() && scenario.getIntervalMillis() > 0) {
                Thread.sleep(scenario.getIntervalMillis());
            }
        }

        System.out.println("Traffic Tester App finished");
        while (true) {
            Thread.sleep(1000);
        }
    }
}
