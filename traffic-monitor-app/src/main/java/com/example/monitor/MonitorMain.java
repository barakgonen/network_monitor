package com.example.monitor;

public class MonitorMain {
    public static void main(String[] args) throws Exception {
        String config = System.getenv().getOrDefault("TRAFFIC_TOOL_CONFIG", "/app/config/traffic-tool.yml");
        System.out.println("Traffic Monitor App started");
        System.out.println("Using config: " + config);
        System.out.println("HTTP/UI planned on :8080");
        System.out.println("UDP listener planned on :5001");
        System.out.println("TCP listener planned on :5002");
        Thread.currentThread().join();
    }
}
