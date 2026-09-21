package com.logichh.capturezones;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConquestStatePersistenceTest {
    @Test
    void stateRoundTripKeepsProfilesZonesOwnersTicketsAndStartOrder() throws Exception {
        ActiveConquestMatch older = match("older", 10L, "north", 400);
        ActiveConquestMatch newer = match("newer", 20L, "south", 250);
        String yaml = ConquestManager.encodeState(List.of(older, newer)).saveToString();
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(yaml);

        List<ConquestManager.RestoredMatch> restored = ConquestManager.decodeState(reloaded);

        assertEquals(List.of("older", "newer"), restored.stream().map(entry -> entry.profile).toList());
        assertEquals(List.of("north"), restored.get(0).zones);
        assertEquals(400, restored.get(0).tickets.get("town:sharedowner"));
        assertEquals(20L, restored.get(1).startedAt);
    }

    @Test
    void malformedStateIsRejectedBeforeRestore() throws Exception {
        YamlConfiguration missingMatches = new YamlConfiguration();
        missingMatches.loadFromString("version: 1\nmatches: broken\n");
        assertThrows(IllegalStateException.class, () -> ConquestManager.decodeState(missingMatches));

        YamlConfiguration missingTeamName = new YamlConfiguration();
        missingTeamName.loadFromString("""
            version: 1
            matches:
              - profile: test
                started-at: 10
                zones: [north]
                teams:
                  - type: TOWN
                    tickets: 100
            """);
        assertThrows(IllegalStateException.class, () -> ConquestManager.decodeState(missingTeamName));
    }

    private ActiveConquestMatch match(String profile, long startedAt, String zone, int tickets) {
        CaptureOwner owner = new CaptureOwner(CaptureOwnerType.TOWN, "shared-id", "SharedOwner");
        String key = ActiveConquestMatch.ownerKey(owner);
        Map<String, CaptureOwner> owners = new LinkedHashMap<>();
        owners.put(key, owner);
        Map<String, Integer> ticketMap = new LinkedHashMap<>();
        ticketMap.put(key, tickets);
        return new ActiveConquestMatch(profile, startedAt, List.of(zone), owners, ticketMap);
    }
}
