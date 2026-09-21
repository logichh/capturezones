package com.logichh.capturezones;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConquestManager {
    private static final int STATE_VERSION = 1;
    private static final int MIN_TICK_SECONDS = 5;
    private static final int MAX_STARTING_TICKETS = 100000;
    private static final int MAX_TICKET_DELTA = 10000;

    private final CaptureZones plugin;
    private final ActiveConquestMatches activeMatches = new ActiveConquestMatches();
    private final File stateFile;
    private BukkitTask tickTask;
    private BukkitTask actionBarTask;
    private String lastError = "";

    public ConquestManager(CaptureZones plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "conquest-state.yml");
    }

    public void initialize() {
        cancelTickTask();
        cancelActionBarTask();
        if (!isEnabled()) {
            activeMatches.removeAll();
            saveState();
            return;
        }
        if (!activeMatches.isEmpty()) {
            saveState();
            activeMatches.removeAll();
        }
        restoreState();
        startTickTask();
        startActionBarTask();
    }

    public void reload() {
        initialize();
    }

    public void shutdown() {
        saveState();
        cancelTickTask();
        cancelActionBarTask();
        activeMatches.removeAll();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("conquest.enabled", false);
    }

    public boolean isActive() {
        return !activeMatches.isEmpty();
    }

    public boolean isConcurrentMatchesAllowed() {
        return plugin.getConfig().getBoolean("conquest.allow-concurrent-matches", false);
    }

    public String getLastError() {
        return lastError;
    }

    /**
     * Returns the oldest active profile. Use {@link #getActiveProfiles()} when concurrent matches are enabled.
     */
    @Deprecated
    public String getActiveProfile() {
        ActiveConquestMatch match = firstMatch();
        return match == null ? "" : match.getProfile();
    }

    public Set<String> getActiveProfiles() {
        return activeMatches.profiles();
    }

    public boolean isZoneManaged(String zoneId) {
        if (!isEnabled() || blank(zoneId)) {
            return false;
        }
        if (isActive()) {
            return findMatchForZone(zoneId) != null;
        }
        return containsIgnoreCase(resolveProfileZones(resolveDefaultProfile()), zoneId.trim());
    }

    public boolean canOwnerCapture(CapturePoint point, CaptureOwner owner) {
        if (point == null || owner == null) {
            return true;
        }
        ActiveConquestMatch match = findMatchForZone(point.getId());
        return match == null || match.hasOwner(owner);
    }

    public boolean startMatch(String profileName) {
        lastError = "";
        if (!isEnabled()) {
            return fail("Conquest mode is disabled.");
        }
        String profile = normalizeProfile(profileName);
        List<String> zoneIds = resolveProfileZones(profile);
        if (zoneIds.isEmpty()) {
            return fail("Conquest profile '" + profile + "' has no valid zones.");
        }
        List<CaptureOwner> teams = resolveProfileTeams(profile);
        int minTeams = Math.max(2, plugin.getConfig().getInt("conquest.profiles." + profile + ".min-teams", 2));
        if (teams.size() < minTeams) {
            return fail("Conquest profile '" + profile + "' does not have enough valid teams.");
        }

        int startingTickets = clamp(plugin.getConfig().getInt(
            "conquest.profiles." + profile + ".starting-tickets", 500
        ), 1, MAX_STARTING_TICKETS);
        Map<String, CaptureOwner> owners = new LinkedHashMap<>();
        Map<String, Integer> tickets = new LinkedHashMap<>();
        for (CaptureOwner owner : teams) {
            String key = ActiveConquestMatch.ownerKey(owner);
            owners.put(key, owner);
            tickets.put(key, startingTickets);
        }
        ActiveConquestMatch match = new ActiveConquestMatch(
            profile, System.currentTimeMillis(), zoneIds, owners, tickets
        );
        boolean allowConcurrent = isConcurrentMatchesAllowed();
        int maxMatches = Math.max(1, plugin.getConfig().getInt("conquest.max-active-matches", 10));
        ActiveConquestMatches.Activation activation = activeMatches.activate(match, allowConcurrent, maxMatches);
        if (!activation.accepted()) {
            if (activation.rejection() == ActiveConquestMatches.Rejection.DUPLICATE_PROFILE) {
                return fail("Conquest profile '" + profile + "' is already active.");
            }
            if (activation.rejection() == ActiveConquestMatches.Rejection.ACTIVE_LIMIT) {
                return fail("The active conquest limit of " + maxMatches + " has been reached.");
            }
            return fail("Zone '" + activation.conflictingZone() + "' is already used by an active conquest.");
        }
        for (ActiveConquestMatch replaced : activation.replaced()) {
            finishStop(replaced, Messages.get("messages.conquest.reason.restart"), false);
        }

        for (String zoneId : zoneIds) {
            if (plugin.isPointActive(zoneId)) {
                plugin.stopCapture(zoneId, "Conquest started for this zone");
            }
            plugin.refreshPointVisuals(zoneId);
        }
        plugin.broadcastChatMessage(Messages.get("messages.conquest.started", Map.of(
            "profile", profile,
            "teams", formatOwners(teams),
            "tickets", String.valueOf(startingTickets)
        )));
        saveState();
        lastError = "";
        return true;
    }

    public boolean stopMatch(String profileName, String reason, boolean announce) {
        String profile = normalizeProfile(profileName);
        ActiveConquestMatch stopped = activeMatches.remove(profile);
        if (stopped == null) {
            lastError = "Conquest profile '" + profile + "' is not active.";
            return false;
        }
        finishStop(stopped, reason, announce);
        saveState();
        lastError = "";
        return true;
    }

    public boolean stopAllMatches(String reason, boolean announce) {
        if (activeMatches.isEmpty()) {
            lastError = "No active conquest match is running.";
            return false;
        }
        List<ActiveConquestMatch> stopped = activeMatches.removeAll();
        for (ActiveConquestMatch match : stopped) {
            finishStop(match, reason, announce);
        }
        saveState();
        lastError = "";
        return true;
    }

    /**
     * Stops the only active match. Use {@link #stopMatch(String, String, boolean)} for profile-aware control.
     */
    @Deprecated
    public boolean stopActiveMatch(String reason, boolean announce) {
        if (activeMatches.size() != 1) {
            lastError = activeMatches.isEmpty()
                ? "No active conquest match is running."
                : "More than one conquest is active. Specify a profile or use 'all'.";
            return false;
        }
        return stopMatch(activeMatches.first().getProfile(), reason, announce);
    }

    private void finishStop(ActiveConquestMatch match, String reason, boolean announce) {
        for (String zoneId : match.getZoneIds()) {
            plugin.refreshPointVisuals(zoneId);
        }
        if (announce) {
            plugin.broadcastChatMessage(Messages.get("messages.conquest.stopped", Map.of(
                "profile", match.getProfile(),
                "reason", blank(reason) ? Messages.get("messages.conquest.reason.manual") : reason
            )));
        }
    }

    public void onCaptureComplete(CapturePoint point, CaptureOwner previousOwner, CaptureOwner newOwner) {
        if (point == null || newOwner == null) {
            return;
        }
        ActiveConquestMatch match = findMatchForZone(point.getId());
        if (match == null) {
            return;
        }
        String base = "conquest.profiles." + match.getProfile();
        int captureGain = clamp(plugin.getConfig().getInt(base + ".capture-ticket-gain", 0), 0, MAX_TICKET_DELTA);
        int captureLoss = clamp(plugin.getConfig().getInt(base + ".capture-ticket-loss", 25), 0, MAX_TICKET_DELTA);
        if (captureGain > 0) {
            match.addTickets(newOwner, captureGain, MAX_STARTING_TICKETS);
        }
        if (previousOwner != null && !previousOwner.isSameOwner(newOwner) && captureLoss > 0) {
            match.addTickets(previousOwner, -captureLoss, MAX_STARTING_TICKETS);
        }
        broadcastTicketStatus(match);
        saveState();
        checkVictory(match);
    }

    /**
     * Returns the oldest active match. Use {@link #snapshots()} when concurrent matches are enabled.
     */
    @Deprecated
    public Snapshot snapshot() {
        ActiveConquestMatch match = firstMatch();
        return match == null ? Snapshot.empty() : snapshot(match);
    }

    public Snapshot snapshot(String profileName) {
        ActiveConquestMatch match = activeMatches.get(normalizeProfile(profileName));
        return match == null ? Snapshot.empty() : snapshot(match);
    }

    public Map<String, Snapshot> snapshots() {
        Map<String, Snapshot> result = new LinkedHashMap<>();
        for (ActiveConquestMatch match : activeMatches.values()) {
            result.put(match.getProfile(), snapshot(match));
        }
        return Collections.unmodifiableMap(result);
    }

    private Snapshot snapshot(ActiveConquestMatch match) {
        return new Snapshot(
            match.getProfile(),
            match.getStartedAt(),
            new LinkedHashMap<>(match.getTicketsByOwnerKey()),
            new LinkedHashMap<>(match.getOwnersByKey()),
            new ArrayList<>(match.getZoneIds())
        );
    }

    public String getStatusLineForZone(String zoneId) {
        ActiveConquestMatch match = blank(zoneId) ? firstMatch() : findMatchForZone(zoneId);
        return match == null ? "" : statusLine(match);
    }

    public List<String> getConfiguredProfiles() {
        ConfigurationSection profiles = plugin.getConfig().getConfigurationSection("conquest.profiles");
        if (profiles == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>(profiles.getKeys(false));
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public List<String> getConfiguredZones(String profileName) {
        return resolveProfileZones(normalizeProfile(profileName));
    }

    public boolean setConfiguredZones(String profileName, List<String> zoneIds) {
        String profile = normalizeProfile(profileName);
        List<String> cleaned = new ArrayList<>();
        if (zoneIds != null) {
            for (String zoneId : zoneIds) {
                if (!blank(zoneId) && !containsIgnoreCase(cleaned, zoneId.trim())) {
                    cleaned.add(zoneId.trim());
                }
            }
        }
        plugin.getConfig().set("conquest.profiles." + profile + ".zones", cleaned);
        plugin.saveConfig();
        return true;
    }

    private void startTickTask() {
        int intervalSeconds = Math.max(MIN_TICK_SECONDS, plugin.getConfig().getInt("conquest.tick-interval-seconds", 30));
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickMatches, 20L, intervalSeconds * 20L);
    }

    private void startActionBarTask() {
        if (!plugin.getConfig().getBoolean("conquest.actionbar.enabled", true)) {
            return;
        }
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::sendActionBars, 20L, 20L);
    }

    private void cancelTickTask() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
        }
        tickTask = null;
    }

    private void cancelActionBarTask() {
        if (actionBarTask != null && !actionBarTask.isCancelled()) {
            actionBarTask.cancel();
        }
        actionBarTask = null;
    }

    private void tickMatches() {
        if (activeMatches.isEmpty()) {
            return;
        }
        for (ActiveConquestMatch match : activeMatches.values()) {
            tickMatch(match);
        }
        saveState();
    }

    private void tickMatch(ActiveConquestMatch match) {
        int pointDrain = clamp(plugin.getConfig().getInt(
            "conquest.profiles." + match.getProfile() + ".point-drain", 1
        ), 0, MAX_TICKET_DELTA);
        if (pointDrain <= 0) {
            return;
        }
        Map<String, Integer> controlledPointsByOwner = new HashMap<>();
        int controlledTotal = 0;
        for (String zoneId : match.getZoneIds()) {
            CapturePoint point = plugin.getCapturePoint(zoneId);
            CaptureOwner owner = point != null ? point.getControllingOwner() : null;
            String key = ActiveConquestMatch.ownerKey(owner);
            if (key != null && match.getTicketsByOwnerKey().containsKey(key)) {
                controlledPointsByOwner.merge(key, 1, Integer::sum);
                controlledTotal++;
            }
        }
        if (controlledTotal <= 0) {
            return;
        }
        for (Map.Entry<String, Integer> entry : new ArrayList<>(match.getTicketsByOwnerKey().entrySet())) {
            int friendly = controlledPointsByOwner.getOrDefault(entry.getKey(), 0);
            int enemy = Math.max(0, controlledTotal - friendly);
            if (enemy > 0) {
                match.setTickets(entry.getKey(), entry.getValue() - (enemy * pointDrain), MAX_STARTING_TICKETS);
            }
        }
        checkVictory(match);
    }

    private void checkVictory(ActiveConquestMatch match) {
        if (!activeMatches.contains(match.getProfile())) {
            return;
        }
        String winnerKey = match.resolvedWinnerKey();
        if (winnerKey == null) {
            return;
        }
        CaptureOwner winner = match.getOwnersByKey().get(winnerKey);
        String winnerName = winner != null ? winner.getDisplayName() : Messages.get("messages.conquest.winner.none");
        String profile = match.getProfile();
        if (winner != null && plugin.getCommandRewardManager() != null) {
            plugin.getCommandRewardManager().executeConquestWin(profile, winner);
        }
        activeMatches.remove(profile);
        finishStop(match, Messages.get("messages.conquest.reason.victory"), false);
        saveState();
        plugin.broadcastChatMessage(Messages.get("messages.conquest.finished", Map.of(
            "profile", profile,
            "winner", winnerName
        )));
    }

    private void broadcastTicketStatus(ActiveConquestMatch match) {
        plugin.broadcastChatMessage(Messages.get("messages.conquest.tickets", Map.of(
            "tickets", statusLine(match)
        )));
    }

    private String statusLine(ActiveConquestMatch match) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : match.getTicketsByOwnerKey().entrySet()) {
            CaptureOwner owner = match.getOwnersByKey().get(entry.getKey());
            parts.add((owner != null ? owner.getDisplayName() : entry.getKey()) + ": " + Math.max(0, entry.getValue()));
        }
        return String.join(" | ", parts);
    }

    private void sendActionBars() {
        if (activeMatches.isEmpty() || !plugin.getConfig().getBoolean("conquest.actionbar.enabled", true)) {
            return;
        }
        String outsideMode = plugin.getConfig().getString("conquest.actionbar.outside-zone-mode", "ROTATE");
        int rotationSeconds = Math.max(1, plugin.getConfig().getInt("conquest.actionbar.rotation-seconds", 5));
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            ActiveConquestMatch match = matchAtPlayerLocation(player);
            if (match == null && "ROTATE".equalsIgnoreCase(outsideMode)) {
                List<ActiveConquestMatch> owned = matchesForPlayer(player);
                if (!owned.isEmpty()) {
                    int index = (int) ((System.currentTimeMillis() / (rotationSeconds * 1000L)) % owned.size());
                    match = owned.get(index);
                }
            }
            if (match != null) {
                plugin.sendActionBarMessage(player, Messages.get("messages.conquest.actionbar", Map.of(
                    "profile", match.getProfile(),
                    "tickets", statusLine(match)
                )));
            }
        }
    }

    private ActiveConquestMatch matchAtPlayerLocation(Player player) {
        for (CapturePoint point : plugin.getCandidateCapturePoints(player.getLocation())) {
            if (plugin.isWithinZone(point, player.getLocation())) {
                ActiveConquestMatch match = findMatchForZone(point.getId());
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private List<ActiveConquestMatch> matchesForPlayer(Player player) {
        List<ActiveConquestMatch> result = new ArrayList<>();
        for (ActiveConquestMatch match : activeMatches.values()) {
            for (CaptureOwner owner : match.getOwnersByKey().values()) {
                if (plugin.doesPlayerMatchOwner(player, owner)) {
                    result.add(match);
                    break;
                }
            }
        }
        return result;
    }

    private ActiveConquestMatch findMatchForZone(String zoneId) {
        if (blank(zoneId)) {
            return null;
        }
        return activeMatches.findByZone(zoneId);
    }

    private String findZoneConflict(List<String> zoneIds) {
        return activeMatches.findZoneConflict(zoneIds);
    }

    private ActiveConquestMatch firstMatch() {
        return activeMatches.first();
    }

    private List<CaptureOwner> resolveProfileTeams(String profile) {
        List<String> rawTeams = plugin.getConfig().getStringList("conquest.profiles." + profile + ".teams");
        CaptureOwnerType ownerType = CaptureOwnerType.fromConfigValue(
            plugin.getConfig().getString("conquest.profiles." + profile + ".owner-type", plugin.getDefaultOwnerType().name()),
            plugin.getDefaultOwnerType()
        );
        List<CaptureOwner> owners = new ArrayList<>();
        OwnerPlatformAdapter adapter = plugin.getOwnerPlatform();
        if (adapter == null) {
            return owners;
        }
        for (String rawTeam : rawTeams) {
            String normalized = adapter.normalizeOwnerName(rawTeam, ownerType);
            if (blank(normalized)) {
                continue;
            }
            CaptureOwner owner = new CaptureOwner(ownerType, adapter.resolveOwnerId(normalized, ownerType), normalized);
            if (!containsOwner(owners, owner)) {
                owners.add(owner);
            }
        }
        return owners;
    }

    private List<String> resolveProfileZones(String profile) {
        if (blank(profile)) {
            return Collections.emptyList();
        }
        List<String> zones = new ArrayList<>();
        for (String zoneId : plugin.getConfig().getStringList("conquest.profiles." + profile + ".zones")) {
            if (!blank(zoneId) && plugin.getCapturePoint(zoneId.trim()) != null && !containsIgnoreCase(zones, zoneId.trim())) {
                zones.add(zoneId.trim());
            }
        }
        return zones;
    }

    private String resolveDefaultProfile() {
        return normalizeProfile(plugin.getConfig().getString("conquest.default-profile", "default"));
    }

    private String normalizeProfile(String profileName) {
        String profile = blank(profileName)
            ? plugin.getConfig().getString("conquest.default-profile", "default")
            : profileName.trim();
        return profile.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private void saveState() {
        if (!plugin.getConfig().getBoolean("conquest.persist-active-matches", true)) {
            return;
        }
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Could not create the plugin folder for conquest state.");
                return;
            }
            YamlConfiguration data = encodeState(activeMatches.values());
            File temporary = new File(plugin.getDataFolder(), stateFile.getName() + ".tmp");
            data.save(temporary);
            try {
                Files.move(temporary.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), stateFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save conquest state: " + exception.getMessage());
        }
    }

    private void restoreState() {
        if (!plugin.getConfig().getBoolean("conquest.persist-active-matches", true) || !stateFile.exists()) {
            return;
        }
        try {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(stateFile);
            List<RestoredMatch> restored = decodeState(data);
            restored.sort(Comparator.comparingLong(entry -> entry.startedAt));
            int maxMatches = isConcurrentMatchesAllowed()
                ? Math.max(1, plugin.getConfig().getInt("conquest.max-active-matches", 10))
                : 1;
            for (RestoredMatch entry : restored) {
                if (activeMatches.size() >= maxMatches) {
                    break;
                }
                restoreMatch(entry);
            }
            if (!activeMatches.isEmpty()) {
                plugin.getLogger().info("Restored " + activeMatches.size() + " active conquest match(es).");
            }
        } catch (Exception exception) {
            backupMalformedState(exception);
        }
    }

    static YamlConfiguration encodeState(Iterable<ActiveConquestMatch> matches) {
        YamlConfiguration data = new YamlConfiguration();
        data.set("version", STATE_VERSION);
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (ActiveConquestMatch match : matches) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("profile", match.getProfile());
            row.put("started-at", match.getStartedAt());
            row.put("zones", new ArrayList<>(match.getZoneIds()));
            List<Map<String, Object>> teams = new ArrayList<>();
            for (Map.Entry<String, CaptureOwner> ownerEntry : match.getOwnersByKey().entrySet()) {
                CaptureOwner owner = ownerEntry.getValue();
                Map<String, Object> team = new LinkedHashMap<>();
                team.put("key", ownerEntry.getKey());
                team.put("type", owner.getType().name());
                team.put("id", owner.getId());
                team.put("name", owner.getDisplayName());
                team.put("tickets", match.getTicketsByOwnerKey().getOrDefault(ownerEntry.getKey(), 0));
                teams.add(team);
            }
            row.put("teams", teams);
            serialized.add(row);
        }
        data.set("matches", serialized);
        return data;
    }

    static List<RestoredMatch> decodeState(YamlConfiguration data) {
        if (data == null || data.getInt("version", -1) != STATE_VERSION) {
            throw new IllegalStateException("unsupported state version");
        }
        Object rawMatches = data.get("matches");
        if (!(rawMatches instanceof List<?>)) {
            throw new IllegalStateException("missing or invalid matches list");
        }
        List<Map<?, ?>> matchRows = data.getMapList("matches");
        if (matchRows.size() != ((List<?>) rawMatches).size()) {
            throw new IllegalStateException("invalid match entry");
        }
        return parseRestoredMatches(matchRows);
    }

    private static List<RestoredMatch> parseRestoredMatches(List<Map<?, ?>> rows) {
        List<RestoredMatch> result = new ArrayList<>();
        for (Map<?, ?> row : rows) {
            String rawProfile = stringValue(row.get("profile"));
            if (blank(rawProfile)) {
                throw new IllegalStateException("match profile is missing");
            }
            String profile = rawProfile.toLowerCase(Locale.ROOT).replace(' ', '_');
            long startedAt = longValue(row.get("started-at"), -1L);
            if (startedAt < 0L) {
                throw new IllegalStateException("invalid start time for " + profile);
            }
            if (!(row.get("zones") instanceof List<?>)) {
                throw new IllegalStateException("missing zones for " + profile);
            }
            List<String> zones = stringList(row.get("zones"));
            if (zones.isEmpty()) {
                throw new IllegalStateException("empty zones for " + profile);
            }
            Map<String, CaptureOwner> owners = new LinkedHashMap<>();
            Map<String, Integer> tickets = new LinkedHashMap<>();
            Object rawTeams = row.get("teams");
            if (!(rawTeams instanceof List<?>) || ((List<?>) rawTeams).isEmpty()) {
                throw new IllegalStateException("missing teams for " + profile);
            }
            for (Object rawTeam : (List<?>) rawTeams) {
                if (!(rawTeam instanceof Map<?, ?>)) {
                    throw new IllegalStateException("invalid team entry for " + profile);
                }
                Map<?, ?> team = (Map<?, ?>) rawTeam;
                CaptureOwnerType type;
                try {
                    type = CaptureOwnerType.valueOf(stringValue(team.get("type")).toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException("invalid owner type for " + profile);
                }
                String name = stringValue(team.get("name"));
                if (blank(name)) {
                    throw new IllegalStateException("missing owner name for " + profile);
                }
                CaptureOwner owner = new CaptureOwner(
                    type, stringValue(team.get("id")), name
                );
                String key = ActiveConquestMatch.ownerKey(owner);
                if (blank(key) || owners.containsKey(key)) {
                    throw new IllegalStateException("invalid or duplicate team for " + profile);
                }
                int ticketCount = intValue(team.get("tickets"), -1);
                if (ticketCount < 0) {
                    throw new IllegalStateException("invalid ticket count for " + profile);
                }
                owners.put(key, owner);
                tickets.put(key, clamp(ticketCount, 0, MAX_STARTING_TICKETS));
            }
            result.add(new RestoredMatch(profile, startedAt, zones, owners, tickets));
        }
        return result;
    }

    private void restoreMatch(RestoredMatch entry) {
        if (activeMatches.contains(entry.profile)) {
            return;
        }
        List<String> configuredZones = resolveProfileZones(entry.profile);
        List<CaptureOwner> configuredTeams = resolveProfileTeams(entry.profile);
        Set<String> configuredKeys = new LinkedHashSet<>();
        for (CaptureOwner owner : configuredTeams) {
            configuredKeys.add(ActiveConquestMatch.ownerKey(owner));
        }
        if (!sameIgnoreCase(configuredZones, entry.zones)
            || !configuredKeys.equals(entry.owners.keySet())
            || findZoneConflict(entry.zones) != null) {
            plugin.getLogger().warning("Skipped saved conquest '" + entry.profile + "' because its profile changed or its zones conflict.");
            return;
        }
        ActiveConquestMatch match = new ActiveConquestMatch(
            entry.profile, entry.startedAt, entry.zones, entry.owners, entry.tickets
        );
        if (!activeMatches.restore(match)) {
            return;
        }
        for (String zoneId : entry.zones) {
            plugin.refreshPointVisuals(zoneId);
        }
    }

    private void backupMalformedState(Exception exception) {
        try {
            File backup = new File(plugin.getDataFolder(), "conquest-state.invalid-" + System.currentTimeMillis() + ".yml");
            Files.move(stateFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().warning("Could not read conquest-state.yml. Moved it to " + backup.getName() + ": " + exception.getMessage());
        } catch (IOException moveException) {
            plugin.getLogger().warning("Could not read or back up conquest-state.yml: " + exception.getMessage());
        }
    }

    private boolean fail(String message) {
        lastError = message;
        return false;
    }

    private String formatOwners(List<CaptureOwner> owners) {
        List<String> names = new ArrayList<>();
        for (CaptureOwner owner : owners) {
            names.add(owner.getDisplayName());
        }
        return String.join(", ", names);
    }

    private boolean containsOwner(List<CaptureOwner> owners, CaptureOwner owner) {
        for (CaptureOwner existing : owners) {
            if (existing.isSameOwner(owner)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(List<String> values, String candidate) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameIgnoreCase(List<String> first, List<String> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (String value : first) {
            if (!containsIgnoreCase(second, value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(stringValue(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : (List<?>) value) {
            if (!blank(stringValue(entry))) {
                result.add(stringValue(entry));
            }
        }
        return result;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class RestoredMatch {
        final String profile;
        final long startedAt;
        final List<String> zones;
        final Map<String, CaptureOwner> owners;
        final Map<String, Integer> tickets;

        private RestoredMatch(
            String profile,
            long startedAt,
            List<String> zones,
            Map<String, CaptureOwner> owners,
            Map<String, Integer> tickets
        ) {
            this.profile = profile;
            this.startedAt = startedAt;
            this.zones = zones;
            this.owners = owners;
            this.tickets = tickets;
        }
    }

    public static final class Snapshot {
        public final String profile;
        public final long startedAt;
        public final Map<String, Integer> ticketsByOwnerKey;
        public final Map<String, CaptureOwner> ownersByKey;
        public final List<String> zoneIds;

        private Snapshot(
            String profile,
            long startedAt,
            Map<String, Integer> ticketsByOwnerKey,
            Map<String, CaptureOwner> ownersByKey,
            List<String> zoneIds
        ) {
            this.profile = profile;
            this.startedAt = startedAt;
            this.ticketsByOwnerKey = Collections.unmodifiableMap(ticketsByOwnerKey);
            this.ownersByKey = Collections.unmodifiableMap(ownersByKey);
            this.zoneIds = Collections.unmodifiableList(zoneIds);
        }

        private static Snapshot empty() {
            return new Snapshot("", 0L, new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>());
        }
    }
}
