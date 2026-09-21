package com.logichh.capturezones;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActiveConquestMatchTest {
    @Test
    void matchesKeepTicketsIndependentWhenTheyShareAnOwner() {
        CaptureOwner alpha = new CaptureOwner(CaptureOwnerType.TOWN, "alpha-id", "Alpha");
        ActiveConquestMatch first = match("first", List.of("north"), alpha, 500);
        ActiveConquestMatch second = match("second", List.of("south"), alpha, 500);

        first.addTickets(alpha, -75, 100000);

        assertEquals(425, first.getTicketsByOwnerKey().get("town:alpha"));
        assertEquals(500, second.getTicketsByOwnerKey().get("town:alpha"));
    }

    @Test
    void zoneAndOwnerChecksIgnoreDisplayCase() {
        CaptureOwner alpha = new CaptureOwner(CaptureOwnerType.TOWN, "alpha-id", "Alpha");
        ActiveConquestMatch match = match("first", List.of("North_Gate"), alpha, 500);

        assertTrue(match.managesZone("north_gate"));
        assertTrue(match.hasOwner(new CaptureOwner(CaptureOwnerType.TOWN, "other-id", "ALPHA")));
        assertFalse(match.managesZone("south_gate"));
    }

    @Test
    void ticketChangesStayWithinBounds() {
        CaptureOwner alpha = new CaptureOwner(CaptureOwnerType.TOWN, "alpha-id", "Alpha");
        ActiveConquestMatch match = match("first", List.of("north"), alpha, 50);

        match.addTickets(alpha, -100, 100);
        assertEquals(0, match.getTicketsByOwnerKey().get("town:alpha"));
        match.addTickets(alpha, 1000, 100);
        assertEquals(100, match.getTicketsByOwnerKey().get("town:alpha"));
    }

    @Test
    void snapshotsCannotMutateMatchCollections() {
        CaptureOwner alpha = new CaptureOwner(CaptureOwnerType.TOWN, "alpha-id", "Alpha");
        ActiveConquestMatch match = match("first", List.of("north"), alpha, 500);

        assertThrows(UnsupportedOperationException.class, () -> match.getZoneIds().add("south"));
        assertThrows(UnsupportedOperationException.class, () -> match.getTicketsByOwnerKey().put("town:beta", 5));
    }

    @Test
    void victoryResolvesOnlyAfterAtMostOneTeamHasTickets() {
        CaptureOwner alpha = new CaptureOwner(CaptureOwnerType.TOWN, "alpha-id", "Alpha");
        CaptureOwner beta = new CaptureOwner(CaptureOwnerType.TOWN, "beta-id", "Beta");
        String alphaKey = ActiveConquestMatch.ownerKey(alpha);
        String betaKey = ActiveConquestMatch.ownerKey(beta);
        Map<String, CaptureOwner> owners = new LinkedHashMap<>();
        owners.put(alphaKey, alpha);
        owners.put(betaKey, beta);
        Map<String, Integer> tickets = new LinkedHashMap<>();
        tickets.put(alphaKey, 100);
        tickets.put(betaKey, 100);
        ActiveConquestMatch match = new ActiveConquestMatch("first", 10L, List.of("north"), owners, tickets);

        assertEquals(null, match.resolvedWinnerKey());
        match.setTickets(betaKey, 0, 100000);
        assertEquals(alphaKey, match.resolvedWinnerKey());
        match.setTickets(alphaKey, 0, 100000);
        assertEquals("", match.resolvedWinnerKey());
    }

    private ActiveConquestMatch match(String profile, List<String> zones, CaptureOwner owner, int tickets) {
        String key = ActiveConquestMatch.ownerKey(owner);
        Map<String, CaptureOwner> owners = new LinkedHashMap<>();
        owners.put(key, owner);
        Map<String, Integer> ticketMap = new LinkedHashMap<>();
        ticketMap.put(key, tickets);
        return new ActiveConquestMatch(profile, 10L, zones, owners, ticketMap);
    }
}
