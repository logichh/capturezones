package com.logichh.capturezones;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ActiveConquestMatches {
    enum Rejection {
        NONE,
        DUPLICATE_PROFILE,
        ACTIVE_LIMIT,
        ZONE_CONFLICT
    }

    static final class Activation {
        private final Rejection rejection;
        private final String conflictingZone;
        private final List<ActiveConquestMatch> replaced;

        private Activation(Rejection rejection, String conflictingZone, List<ActiveConquestMatch> replaced) {
            this.rejection = rejection;
            this.conflictingZone = conflictingZone;
            this.replaced = Collections.unmodifiableList(new ArrayList<>(replaced));
        }

        boolean accepted() {
            return rejection == Rejection.NONE;
        }

        Rejection rejection() {
            return rejection;
        }

        String conflictingZone() {
            return conflictingZone;
        }

        List<ActiveConquestMatch> replaced() {
            return replaced;
        }
    }

    private final Map<String, ActiveConquestMatch> matches = new LinkedHashMap<>();

    Activation activate(ActiveConquestMatch match, boolean allowConcurrent, int maxActiveMatches) {
        if (match == null) {
            throw new IllegalArgumentException("match cannot be null");
        }
        if (!allowConcurrent) {
            List<ActiveConquestMatch> replaced = removeAll();
            matches.put(match.getProfile(), match);
            return new Activation(Rejection.NONE, null, replaced);
        }
        if (matches.containsKey(match.getProfile())) {
            return new Activation(Rejection.DUPLICATE_PROFILE, null, List.of());
        }
        if (matches.size() >= Math.max(1, maxActiveMatches)) {
            return new Activation(Rejection.ACTIVE_LIMIT, null, List.of());
        }
        String conflict = findZoneConflict(match.getZoneIds());
        if (conflict != null) {
            return new Activation(Rejection.ZONE_CONFLICT, conflict, List.of());
        }
        matches.put(match.getProfile(), match);
        return new Activation(Rejection.NONE, null, List.of());
    }

    boolean restore(ActiveConquestMatch match) {
        if (match == null || matches.containsKey(match.getProfile()) || findZoneConflict(match.getZoneIds()) != null) {
            return false;
        }
        matches.put(match.getProfile(), match);
        return true;
    }

    ActiveConquestMatch get(String profile) {
        return matches.get(profile);
    }

    ActiveConquestMatch remove(String profile) {
        return matches.remove(profile);
    }

    List<ActiveConquestMatch> removeAll() {
        List<ActiveConquestMatch> removed = new ArrayList<>(matches.values());
        matches.clear();
        return removed;
    }

    ActiveConquestMatch first() {
        return matches.isEmpty() ? null : matches.values().iterator().next();
    }

    ActiveConquestMatch findByZone(String zoneId) {
        if (zoneId == null || zoneId.trim().isEmpty()) {
            return null;
        }
        for (ActiveConquestMatch match : matches.values()) {
            if (match.managesZone(zoneId)) {
                return match;
            }
        }
        return null;
    }

    String findZoneConflict(List<String> zoneIds) {
        if (zoneIds == null) {
            return null;
        }
        for (String zoneId : zoneIds) {
            if (findByZone(zoneId) != null) {
                return zoneId;
            }
        }
        return null;
    }

    boolean contains(String profile) {
        return matches.containsKey(profile);
    }

    boolean isEmpty() {
        return matches.isEmpty();
    }

    int size() {
        return matches.size();
    }

    List<ActiveConquestMatch> values() {
        return Collections.unmodifiableList(new ArrayList<>(matches.values()));
    }

    Set<String> profiles() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(matches.keySet()));
    }
}
