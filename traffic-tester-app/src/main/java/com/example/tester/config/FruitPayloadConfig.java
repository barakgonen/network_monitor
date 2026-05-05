package com.example.tester.config;

public class FruitPayloadConfig {
    private String sourceFarm = "default-farm";
    private String freshness = "unknown";

    public String getSourceFarm() {
        return sourceFarm;
    }

    public void setSourceFarm(String sourceFarm) {
        this.sourceFarm = sourceFarm;
    }

    public String getFreshness() {
        return freshness;
    }

    public void setFreshness(String freshness) {
        this.freshness = freshness;
    }
}
