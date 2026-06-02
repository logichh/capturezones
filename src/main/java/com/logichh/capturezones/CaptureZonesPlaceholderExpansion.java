package com.logichh.capturezones;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CaptureZonesPlaceholderExpansion extends PlaceholderExpansion {

    private static final List<String> ZONE_FIELD_SUFFIXES = Arrays.asList(
        "capturing_owner_type",
        "capturing_owner",
        "owner_type",
        "item_reward",
        "time_left_formatted",
        "time_left",
        "base_reward",
        "reward",
        "progress",
        "grace_left",
        "grace_active",
        "contested",
        "phase",
        "status",
        "owner",
        "active",
        "zone_name",
        "zone_id"
    );

    private final CaptureZones plugin;

    private static final class ZoneFieldRequest {
        private final String zoneId;
        private final String field;

        private ZoneFieldRequest(String zoneId, String field) {
            this.zoneId = zoneId;
            this.field = field;
        }
    }

    public CaptureZonesPlaceholderExpansion(CaptureZones plugin) {
        this.plugin = plugin;
    }

    @Override

    public String getIdentifier() {
        return "capturezones";
    }

    @Override

    public String getAuthor() {
        List<String> authors = plugin.getDescription().getAuthors();
        if (authors == null || authors.isEmpty()) {
            return "CaptureZones";
        }
        return String.join(", ", authors);
    }

    @Override

    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override

    public boolean persist() {
        return true;
    }

    @Override

    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return "";
        }

        String raw = params.trim();
        if (raw.isEmpty()) {
            return "";
        }
        String normalized = raw.toLowerCase(Locale.ROOT);

        switch (normalized) {
            case "total_zones":
                return String.valueOf(plugin.getCapturePoints().size());
            case "active_zones":
                return String.valueOf(plugin.getActiveSessions().size());
            case "contested_zones":
                return String.valueOf(countContestedSessions());
            case "player_zone_id":
                return resolveHerePlaceholder(player, "zone_id");
            case "player_zone_name":
                return resolveHerePlaceholder(player, "zone_name");
            case "player_zone_status":
                return resolveHerePlaceholder(player, "status");
            default:
                break;
        }

        if (normalized.startsWith("here_")) {
            return resolveHerePlaceholder(player, normalized.substring("here_".length()));
        }

        if (normalized.startsWith("zone_")) {
            return resolveZonePlaceholder(raw.substring("zone_".length()));
        }

        return null;
    }

    private String resolveHerePlaceholder(OfflinePlayer offlinePlayer, String field) {
        String normalizedField = normalizeField(field);
        Player player = offlinePlayer != null ? offlinePlayer.getPlayer() : null;
        if (player == null || !player.isOnline()) {
            return emptyValueForField(normalizedField);
        }

        CapturePoint point = findCapturePointAt(player.getLocation());
        CaptureSession session = point != null ? plugin.getActiveSession(point.getId()) : null;
        return resolveFieldValue(point, session, normalizedField);
    }

    private String resolveZonePlaceholder(String token) {
        ZoneFieldRequest request = parseZoneFieldRequest(token);
        if (request == null) {
            return "";
        }

        CapturePoint point = findCapturePointByIdIgnoreCase(request.zoneId);
        CaptureSession session = point != null ? plugin.getActiveSession(point.getId()) : null;
        return resolveFieldValue(point, session, request.field);
    }

    private ZoneFieldRequest parseZoneFieldRequest(String token) {
        if (token == null) {
            return null;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String lowered = trimmed.toLowerCase(Locale.ROOT);

        for (String suffix : ZONE_FIELD_SUFFIXES) {
            String marker = "_" + suffix;
            if (!lowered.endsWith(marker)) {
                continue;
            }
            int zoneIdLength = trimmed.length() - marker.length();
            if (zoneIdLength <= 0) {
                return null;
            }
            String zoneId = trimmed.substring(0, zoneIdLength);
            return new ZoneFieldRequest(zoneId, suffix);
        }

        return null;
    }

    private CapturePoint findCapturePointByIdIgnoreCase(String zoneId) {
        if (zoneId == null || zoneId.trim().isEmpty()) {
            return null;
        }

        CapturePoint direct = plugin.getCapturePoint(zoneId);
        if (direct != null) {
            return direct;
        }

        String lowered = zoneId.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, CapturePoint> entry : plugin.getCapturePoints().entrySet()) {
            String key = entry.getKey();
            if (key != null && key.toLowerCase(Locale.ROOT).equals(lowered)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private CapturePoint findCapturePointAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        List<CapturePoint> candidates = plugin.getCandidateCapturePoints(location);
        if (candidates.isEmpty()) {
            return null;
        }

        CapturePoint best = null;
        for (CapturePoint point : candidates) {
            if (point == null || !plugin.isWithinZone(point, location)) {
                continue;
            }
            if (best == null || point.getId().compareToIgnoreCase(best.getId()) < 0) {
                best = point;
            }
        }
        return best;
    }

    private int countContestedSessions() {
        int count = 0;
        for (CaptureSession session : plugin.getActiveSessions().values()) {
            if (session != null && session.isContested()) {
                count++;
            }
        }
        return count;
    }

    private String resolveFieldValue(CapturePoint point, CaptureSession session, String field) {
        String normalizedField = normalizeField(field);
        switch (normalizedField) {
            case "zone_id":
                return point != null ? point.getId() : "none";
            case "zone_name":
                return point != null ? point.getName() : "none";
            case "active":
                return String.valueOf(session != null);
            case "status":
                return deriveStatus(point, session);
            case "phase":
                return derivePhase(session);
            case "contested":
                return String.valueOf(session != null && session.isContested());
            case "grace_active":
                return String.valueOf(session != null && session.isGraceActive());
            case "grace_left":
                return String.valueOf(session != null ? Math.max(0, session.getGraceSecondsRemaining()) : 0);
            case "owner":
                return resolveOwnerName(point);
            case "owner_type":
                return resolveOwnerType(point);
            case "capturing_owner":
                return resolveCapturingOwnerName(point, session);
            case "capturing_owner_type":
                return resolveCapturingOwnerType(point, session);
            case "progress":
                return String.format(Locale.ROOT, "%.2f", resolveProgressPercent(point, session));
            case "reward":
            case "base_reward":
                return String.format(Locale.ROOT, "%.2f", point != null ? plugin.getBaseReward(point) : 0.0);
            case "item_reward":
                return point != null ? plugin.getItemRewardDisplay(point.getId()) : "None";
            case "time_left":
                return String.valueOf(session != null ? Math.max(0, session.getRemainingTime()) : 0);
            case "time_left_formatted":
                return formatDuration(session != null ? Math.max(0, session.getRemainingTime()) : 0);
            default:
                return "";
        }
    }

    private String normalizeField(String field) {
        if (field == null) {
            return "";
        }
        return field.trim().toLowerCase(Locale.ROOT);
    }

    private String emptyValueForField(String field) {
        switch (normalizeField(field)) {
            case "active":
            case "contested":
            case "grace_active":
                return "false";
            case "time_left":
            case "grace_left":
                return "0";
            case "time_left_formatted":
                return "0:00";
            case "reward":
            case "base_reward":
            case "progress":
                return "0.00";
            case "item_reward":
                return "None";
            case "zone_id":
            case "zone_name":
            case "owner":
            case "capturing_owner":
                return "none";
            case "owner_type":
            case "capturing_owner_type":
                return plugin.getDefaultOwnerType().name().toLowerCase(Locale.ROOT);
            case "status":
                return "none";
            case "phase":
                return "idle";
            default:
                return "";
        }
    }

    private String deriveStatus(CapturePoint point, CaptureSession session) {
        if (point == null) {
            return "none";
        }
        if (session != null) {
            if (session.isGraceActive()) {
                return "grace";
            }
            if (session.isInPreparationPhase()) {
                return "preparing";
            }
            if (session.isContested()) {
                return "contested";
            }
            return "capturing";
        }
        String ownerName = resolveOwnerName(point);
        if (ownerName == null || ownerName.isEmpty() || "none".equalsIgnoreCase(ownerName)) {
            return "unclaimed";
        }
        return "controlled";
    }

    private String derivePhase(CaptureSession session) {
        if (session == null) {
            return "idle";
        }
        if (session.isGraceActive()) {
            return "grace";
        }
        if (session.isInPreparationPhase()) {
            return "preparation";
        }
        if (session.isContested()) {
            return "contested";
        }
        return "capture";
    }

    private String resolveOwnerName(CapturePoint point) {
        if (point == null) {
            return "none";
        }
        CaptureOwner owner = point.getControllingOwner();
        if (owner != null && owner.getDisplayName() != null && !owner.getDisplayName().trim().isEmpty()) {
            return owner.getDisplayName();
        }
        String legacy = point.getControllingTown();
        if (legacy == null || legacy.trim().isEmpty()) {
            return "none";
        }
        return legacy;
    }

    private String resolveOwnerType(CapturePoint point) {
        if (point == null) {
            return plugin.getDefaultOwnerType().name().toLowerCase(Locale.ROOT);
        }
        CaptureOwner owner = point.getControllingOwner();
        CaptureOwnerType type = owner != null && owner.getType() != null
            ? owner.getType()
            : plugin.getDefaultOwnerType();
        return type.name().toLowerCase(Locale.ROOT);
    }

    private String resolveCapturingOwnerName(CapturePoint point, CaptureSession session) {
        if (session != null && session.getOwner() != null && session.getOwner().getDisplayName() != null) {
            return session.getOwner().getDisplayName();
        }
        if (point == null) {
            return "none";
        }
        CaptureOwner capturingOwner = point.getCapturingOwner();
        if (capturingOwner != null && capturingOwner.getDisplayName() != null && !capturingOwner.getDisplayName().trim().isEmpty()) {
            return capturingOwner.getDisplayName();
        }
        String legacy = point.getCapturingTown();
        if (legacy == null || legacy.trim().isEmpty()) {
            return "none";
        }
        return legacy;
    }

    private String resolveCapturingOwnerType(CapturePoint point, CaptureSession session) {
        if (session != null && session.getOwner() != null && session.getOwner().getType() != null) {
            return session.getOwner().getType().name().toLowerCase(Locale.ROOT);
        }
        if (point != null && point.getCapturingOwner() != null && point.getCapturingOwner().getType() != null) {
            return point.getCapturingOwner().getType().name().toLowerCase(Locale.ROOT);
        }
        return plugin.getDefaultOwnerType().name().toLowerCase(Locale.ROOT);
    }

    private double resolveProgressPercent(CapturePoint point, CaptureSession session) {
        if (session == null) {
            if (point == null) {
                return 0.0;
            }
            return clamp(point.getCaptureProgress(), 0.0, 100.0);
        }

        if (session.isInPreparationPhase()) {
            int initial = Math.max(1, session.getInitialPreparationTime());
            int elapsed = Math.max(0, initial - session.getRemainingPreparationTime());
            return clamp((elapsed * 100.0) / initial, 0.0, 100.0);
        }

        int initial = Math.max(1, session.getInitialCaptureTime());
        int elapsed = Math.max(0, initial - session.getRemainingCaptureTime());
        return clamp((elapsed * 100.0) / initial, 0.0, 100.0);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String formatDuration(int totalSeconds) {
        int safe = Math.max(0, totalSeconds);
        int hours = safe / 3600;
        int minutes = (safe % 3600) / 60;
        int seconds = safe % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
}

