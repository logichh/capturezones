package com.logichh.capturezones;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveConquestMatchesTest {
    @Test
    void concurrentModeRejectsDuplicatesOverlapsAndTheActiveLimit() {
        ActiveConquestMatches matches = new ActiveConquestMatches();
        assertTrue(matches.activate(match("first", "north"), true, 2).accepted());

        ActiveConquestMatches.Activation duplicate = matches.activate(match("first", "south"), true, 2);
        assertEquals(ActiveConquestMatches.Rejection.DUPLICATE_PROFILE, duplicate.rejection());

        ActiveConquestMatches.Activation overlap = matches.activate(match("second", "NORTH"), true, 2);
        assertEquals(ActiveConquestMatches.Rejection.ZONE_CONFLICT, overlap.rejection());
        assertEquals("NORTH", overlap.conflictingZone());

        assertTrue(matches.activate(match("second", "south"), true, 2).accepted());
        ActiveConquestMatches.Activation limited = matches.activate(match("third", "west"), true, 2);
        assertEquals(ActiveConquestMatches.Rejection.ACTIVE_LIMIT, limited.rejection());
    }

    @Test
    void legacyModeReplacesEveryActiveMatchIncludingTheSameProfile() {
        ActiveConquestMatches matches = new ActiveConquestMatches();
        ActiveConquestMatch original = match("first", "north");
        matches.activate(original, true, 10);
        matches.activate(match("second", "south"), true, 10);

        ActiveConquestMatch replacement = match("first", "west");
        ActiveConquestMatches.Activation activation = matches.activate(replacement, false, 10);

        assertTrue(activation.accepted());
        assertEquals(List.of(original.getProfile(), "second"), activation.replaced().stream()
            .map(ActiveConquestMatch::getProfile).toList());
        assertEquals(1, matches.size());
        assertEquals(replacement, matches.first());
    }

    @Test
    void stoppingOneMatchLeavesOthersAndStoppingAllPreservesOldestFirstOrder() {
        ActiveConquestMatches matches = new ActiveConquestMatches();
        matches.activate(match("first", "north"), true, 10);
        matches.activate(match("second", "south"), true, 10);

        assertEquals("first", matches.remove("first").getProfile());
        assertFalse(matches.isEmpty());
        assertNull(matches.get("first"));
        assertEquals(List.of("second"), matches.removeAll().stream().map(ActiveConquestMatch::getProfile).toList());
        assertTrue(matches.isEmpty());
    }

    private ActiveConquestMatch match(String profile, String zone) {
        CaptureOwner owner = new CaptureOwner(CaptureOwnerType.TOWN, profile + "-id", "SharedOwner");
        String key = ActiveConquestMatch.ownerKey(owner);
        Map<String, CaptureOwner> owners = new LinkedHashMap<>();
        owners.put(key, owner);
        Map<String, Integer> tickets = new LinkedHashMap<>();
        tickets.put(key, 500);
        return new ActiveConquestMatch(profile, 10L, List.of(zone), owners, tickets);
    }
}
