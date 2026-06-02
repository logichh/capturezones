package com.logichh.capturezones;

import java.util.Locale;

public enum ZoneShapeType {
    CIRCLE,
    CUBOID;

    public static ZoneShapeType fromConfigValue(String value, ZoneShapeType fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return ZoneShapeType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}

