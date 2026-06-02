package com.logichh.capturezones.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
public final class CaptureZonesActionResult {
    private final boolean success;
    private final String message;
    private final Map<String, Object> data;

    private CaptureZonesActionResult(boolean success, String message, Map<String, Object> data) {
        this.success = success;
        this.message = message == null ? "" : message;
        this.data = toImmutableMap(data);
    }

    public static CaptureZonesActionResult ok(String message) {
        return new CaptureZonesActionResult(true, message, Collections.emptyMap());
    }

    public static CaptureZonesActionResult ok(String message, Map<String, Object> data) {
        return new CaptureZonesActionResult(true, message, data);
    }

    public static CaptureZonesActionResult fail(String message) {
        return new CaptureZonesActionResult(false, message, Collections.emptyMap());
    }

    public static CaptureZonesActionResult fail(String message, Map<String, Object> data) {
        return new CaptureZonesActionResult(false, message, data);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    private static Map<String, Object> toImmutableMap(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}

