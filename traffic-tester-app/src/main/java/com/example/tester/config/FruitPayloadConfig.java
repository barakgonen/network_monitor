package com.example.tester.config;

public class FruitPayloadConfig {
    // Orange fields
    private String sourceFarm = "default-farm";
    private String freshness = "unknown";

    // Banana fields
    private String color = "yellow";
    private double weight = 120.5;

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

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }
}
