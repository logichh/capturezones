package com.logichh.capturezones;

import java.util.Locale;
import java.util.Objects;

public final class CaptureOwner {
    private final CaptureOwnerType type;
    private final String id;
    private final String displayName;

    public CaptureOwner(CaptureOwnerType type, String id, String displayName) {
        this.type = type != null ? type : CaptureOwnerType.TOWN;
        this.displayName = sanitizeDisplayName(displayName);
        this.id = sanitizeId(id, this.type, this.displayName);
    }

    public static CaptureOwner fromDisplayName(CaptureOwnerType type, String displayName) {
        String sanitizedDisplay = sanitizeDisplayName(displayName);
        if (sanitizedDisplay == null) {
            return null;
        }
        return new CaptureOwner(type, null, sanitizedDisplay);
    }

    public CaptureOwnerType getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isSameOwner(CaptureOwner other) {
        if (other == null) {
            return false;
        }
        if (this.type != other.type) {
            return false;
        }
        if (this.id != null && other.id != null) {
            if (this.id.equalsIgnoreCase(other.id)) {
                return true;
            }
        }
        return this.displayName != null
            && other.displayName != null
            && this.displayName.equalsIgnoreCase(other.displayName);
    }

    @Override

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CaptureOwner)) {
            return false;
        }
        CaptureOwner other = (CaptureOwner) obj;
        return this.type == other.type
            && Objects.equals(lower(this.id), lower(other.id))
            && Objects.equals(lower(this.displayName), lower(other.displayName));
    }

    @Override

    public int hashCode() {
        return Objects.hash(
            this.type,
            lower(this.id),
            lower(this.displayName)
        );
    }

    @Override

    public String toString() {
        return "CaptureOwner{type=" + this.type + ", id='" + this.id + "', displayName='" + this.displayName + "'}";
    }

    private static String sanitizeDisplayName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String sanitizeId(String rawId, CaptureOwnerType type, String displayName) {
        String candidate = rawId != null ? rawId.trim() : "";
        if (candidate.isEmpty()) {
            String base = displayName != null ? displayName : "unknown";
            candidate = buildFallbackId(type, base);
        }
        return candidate;
    }

    private static String buildFallbackId(CaptureOwnerType type, String token) {
        String normalizedToken = token.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return type.name().toLowerCase(Locale.ROOT) + ":" + normalizedToken;
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}

