package com.logichh.capturezones;

import org.bukkit.Location;

import java.util.List;

public class NoopHologramProvider implements HologramProvider {
    @Override

    public boolean initialize() {
        return true;
    }

    @Override

    public boolean isAvailable() {
        return false;
    }

    @Override

    public String getName() {
        return "None";
    }

    @Override

    public void createOrUpdate(String pointId, Location baseLocation, List<String> lines, double lineSpacing, boolean fixedOrientation) {
        // Intentionally empty.
    }

    @Override

    public void remove(String pointId) {
        // Intentionally empty.
    }

    @Override

    public void cleanup() {
        // Intentionally empty.
    }
}

