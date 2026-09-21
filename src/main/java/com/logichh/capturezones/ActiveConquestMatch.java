package com.logichh.capturezones;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ActiveConquestMatch {
    private final String profile;
    private final long startedAt;
    private final List<String> zoneIds;
    private final Map<String, CaptureOwner> ownersByKey;
    private final Map<String, Integer> ticketsByOwnerKey;

    ActiveConquestMatch(
        String profile,
        long startedAt,
        List<String> zoneIds,
        Map<String, CaptureOwner> ownersByKey,
        Map<String, Integer> ticketsByOwnerKey
    ) {
        this.profile = profile;
        this.startedAt = startedAt;
        this.zoneIds = new ArrayList<>(zoneIds);
        this.ownersByKey = new LinkedHashMap<>(ownersByKey);
        this.ticketsByOwnerKey = new LinkedHashMap<>(ticketsByOwnerKey);
    }

    String getProfile() {
        return profile;
    }

    long getStartedAt() {
        return startedAt;
    }

    List<String> getZoneIds() {
        return Collections.unmodifiableList(zoneIds);
    }

    Map<String, CaptureOwner> getOwnersByKey() {
        return Collections.unmodifiableMap(ownersByKey);
    }

    Map<String, Integer> getTicketsByOwnerKey() {
        return Collections.unmodifiableMap(ticketsByOwnerKey);
    }

    boolean managesZone(String zoneId) {
        if (zoneId == null) {
            return false;
        }
        for (String configured : zoneIds) {
            if (configured.equalsIgnoreCase(zoneId.trim())) {
                return true;
            }
        }
        return false;
    }

    boolean hasOwner(CaptureOwner owner) {
        String key = ownerKey(owner);
        return key != null && ticketsByOwnerKey.containsKey(key);
    }

    void addTickets(CaptureOwner owner, int delta, int maxTickets) {
        String key = ownerKey(owner);
        if (key == null || !ticketsByOwnerKey.containsKey(key)) {
            return;
        }
        int current = ticketsByOwnerKey.get(key);
        ticketsByOwnerKey.put(key, Math.max(0, Math.min(maxTickets, current + delta)));
    }

    void setTickets(String ownerKey, int tickets, int maxTickets) {
        if (ownerKey == null || !ticketsByOwnerKey.containsKey(ownerKey)) {
            return;
        }
        ticketsByOwnerKey.put(ownerKey, Math.max(0, Math.min(maxTickets, tickets)));
    }

    String resolvedWinnerKey() {
        String winner = "";
        int alive = 0;
        for (Map.Entry<String, Integer> entry : ticketsByOwnerKey.entrySet()) {
            if (entry.getValue() > 0) {
                winner = entry.getKey();
                alive++;
                if (alive > 1) {
                    return null;
                }
            }
        }
        return winner;
    }

    static String ownerKey(CaptureOwner owner) {
        if (owner == null || owner.getType() == null || owner.getDisplayName() == null) {
            return null;
        }
        return owner.getType().name().toLowerCase(Locale.ROOT) + ":" + owner.getDisplayName().toLowerCase(Locale.ROOT);
    }
}
