package com.logichh.capturezones;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConquestManager {
    private static final int MIN_TICK_SECONDS = 5;
    private static final int MAX_STARTING_TICKETS = 100000;
    private static final int MAX_TICKET_DELTA = 10000;

    private final CaptureZones plugin;
    private final Map<String, Integer> ticketsByOwnerKey = new LinkedHashMap<>();
    private final Map<String, CaptureOwner> ownersByKey = new LinkedHashMap<>();
    private BukkitTask tickTask;
    private String activeProfile = "";

    public ConquestManager(CaptureZones plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        cancelTickTask();
        if (isEnabled()) {
            startTickTask();
        }
    }

    public void reload() {
        stopActiveMatch(Messages.get("messages.conquest.reason.reload"), true);
        initialize();
    }

    public void shutdown() {
        stopActiveMatch(Messages.get("messages.conquest.reason.shutdown"), false);
        cancelTickTask();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("conquest.enabled", false);
    }

    public boolean isActive() {
        return !activeProfile.isEmpty() && !ticketsByOwnerKey.isEmpty();
    }

    public String getActiveProfile() {
        return activeProfile;
    }

    public boolean isZoneManaged(String zoneId) {
        if (!isEnabled() || zoneId == null || zoneId.trim().isEmpty()) {
            return false;
        }
        if (isActive()) {
            return resolveProfileZones(activeProfile).stream().anyMatch(id -> id.equalsIgnoreCase(zoneId.trim()));
        }
        return resolveProfileZones(resolveDefaultProfile()).stream().anyMatch(id -> id.equalsIgnoreCase(zoneId.trim()));
    }

    public boolean canOwnerCapture(CapturePoint point, CaptureOwner owner) {
        if (!isActive() || point == null || owner == null || !isZoneManaged(point.getId())) {
            return true;
        }
        return ticketsByOwnerKey.containsKey(ownerKey(owner));
    }

    public boolean startMatch(String profileName) {
        if (!isEnabled()) {
            return false;
        }
        String profile = normalizeProfile(profileName);
        List<String> zoneIds = resolveProfileZones(profile);
        if (zoneIds.isEmpty()) {
            return false;
        }
        List<CaptureOwner> teams = resolveProfileTeams(profile);
        int minTeams = Math.max(2, plugin.getConfig().getInt("conquest.profiles." + profile + ".min-teams", 2));
        if (teams.size() < minTeams) {
            return false;
        }
        stopActiveMatch(Messages.get("messages.conquest.reason.restart"), false);
        int startingTickets = clamp(plugin.getConfig().getInt("conquest.profiles." + profile + ".starting-tickets", 500), 1, MAX_STARTING_TICKETS);
        activeProfile = profile;
        for (CaptureOwner owner : teams) {
            String key = ownerKey(owner);
            ownersByKey.put(key, owner);
            ticketsByOwnerKey.put(key, startingTickets);
        }
        for (String zoneId : zoneIds) {
            if (plugin.isPointActive(zoneId)) {
                plugin.stopCapture(zoneId, "Conquest started for this zone");
            }
            plugin.refreshPointVisuals(zoneId);
        }
        plugin.broadcastChatMessage(Messages.get("messages.conquest.started", Map.of(
            "profile", activeProfile,
            "teams", formatOwners(teams),
            "tickets", String.valueOf(startingTickets)
        )));
        return true;
    }

    public boolean stopActiveMatch(String reason, boolean announce) {
        if (!isActive()) {
            return false;
        }
        String stoppedProfile = activeProfile;
        List<String> zoneIds = resolveProfileZones(activeProfile);
        ticketsByOwnerKey.clear();
        ownersByKey.clear();
        activeProfile = "";
        for (String zoneId : zoneIds) {
            plugin.refreshPointVisuals(zoneId);
        }
        if (announce) {
            plugin.broadcastChatMessage(Messages.get("messages.conquest.stopped", Map.of(
                "profile", stoppedProfile,
                "reason", reason == null || reason.isEmpty() ? Messages.get("messages.conquest.reason.manual") : reason
            )));
        }
        return true;
    }

    public void onCaptureComplete(CapturePoint point, CaptureOwner previousOwner, CaptureOwner newOwner) {
        if (!isActive() || point == null || !isZoneManaged(point.getId()) || newOwner == null) {
            return;
        }
        int captureGain = clamp(plugin.getConfig().getInt("conquest.profiles." + activeProfile + ".capture-ticket-gain", 0), 0, MAX_TICKET_DELTA);
        int captureLoss = clamp(plugin.getConfig().getInt("conquest.profiles." + activeProfile + ".capture-ticket-loss", 25), 0, MAX_TICKET_DELTA);
        if (captureGain > 0) {
            addTickets(newOwner, captureGain);
        }
        if (previousOwner != null && !previousOwner.isSameOwner(newOwner) && captureLoss > 0) {
            addTickets(previousOwner, -captureLoss);
        }
        broadcastTicketStatus();
        checkVictory();
    }

    public Snapshot snapshot() {
        return new Snapshot(activeProfile, new LinkedHashMap<>(ticketsByOwnerKey), new LinkedHashMap<>(ownersByKey), resolveProfileZones(activeProfile));
    }

    public String getStatusLineForZone(String zoneId) {
        if (!isActive()) {
            return "";
        }
        if (zoneId != null && !zoneId.trim().isEmpty() && !isZoneManaged(zoneId)) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ticketsByOwnerKey.entrySet()) {
            CaptureOwner owner = ownersByKey.get(entry.getKey());
            parts.add((owner != null ? owner.getDisplayName() : entry.getKey()) + ": " + Math.max(0, entry.getValue()));
        }
        return String.join(" | ", parts);
    }

    public List<String> getConfiguredZones(String profileName) {
        return resolveProfileZones(normalizeProfile(profileName));
    }

    public boolean setConfiguredZones(String profileName, List<String> zoneIds) {
        String profile = normalizeProfile(profileName);
        List<String> cleaned = new ArrayList<>();
        if (zoneIds != null) {
            for (String zoneId : zoneIds) {
                if (zoneId != null && !zoneId.trim().isEmpty() && !containsIgnoreCase(cleaned, zoneId.trim())) {
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
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTickets, 20L, intervalSeconds * 20L);
    }

    private void cancelTickTask() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
        }
        tickTask = null;
    }

    private void tickTickets() {
        if (!isActive()) {
            return;
        }
        int pointDrain = clamp(plugin.getConfig().getInt("conquest.profiles." + activeProfile + ".point-drain", 1), 0, MAX_TICKET_DELTA);
        if (pointDrain <= 0) {
            return;
        }
        Map<String, Integer> controlledPointsByOwner = new HashMap<>();
        int controlledTotal = 0;
        for (String zoneId : resolveProfileZones(activeProfile)) {
            CapturePoint point = plugin.getCapturePoint(zoneId);
            CaptureOwner owner = point != null ? point.getControllingOwner() : null;
            String key = ownerKey(owner);
            if (key != null && ticketsByOwnerKey.containsKey(key)) {
                controlledPointsByOwner.merge(key, 1, Integer::sum);
                controlledTotal++;
            }
        }
        if (controlledTotal <= 0) {
            return;
        }
        for (String ownerKey : new ArrayList<>(ticketsByOwnerKey.keySet())) {
            int friendly = controlledPointsByOwner.getOrDefault(ownerKey, 0);
            int enemy = Math.max(0, controlledTotal - friendly);
            if (enemy > 0) {
                ticketsByOwnerKey.put(ownerKey, Math.max(0, ticketsByOwnerKey.get(ownerKey) - (enemy * pointDrain)));
            }
        }
        sendActionBars();
        checkVictory();
    }

    private void checkVictory() {
        if (!isActive()) {
            return;
        }
        List<String> alive = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ticketsByOwnerKey.entrySet()) {
            if (entry.getValue() > 0) {
                alive.add(entry.getKey());
            }
        }
        if (alive.size() > 1) {
            return;
        }
        String winnerKey = alive.isEmpty() ? "" : alive.get(0);
        CaptureOwner winner = ownersByKey.get(winnerKey);
        String winnerName = winner != null ? winner.getDisplayName() : Messages.get("messages.conquest.winner.none");
        String profile = activeProfile;
        if (winner != null && plugin.getCommandRewardManager() != null) {
            plugin.getCommandRewardManager().executeConquestWin(profile, winner);
        }
        stopActiveMatch(Messages.get("messages.conquest.reason.victory"), false);
        plugin.broadcastChatMessage(Messages.get("messages.conquest.finished", Map.of(
            "profile", profile,
            "winner", winnerName
        )));
    }

    private void addTickets(CaptureOwner owner, int delta) {
        String key = ownerKey(owner);
        if (key == null || !ticketsByOwnerKey.containsKey(key)) {
            return;
        }
        ticketsByOwnerKey.put(key, Math.max(0, Math.min(MAX_STARTING_TICKETS, ticketsByOwnerKey.get(key) + delta)));
    }

    private void broadcastTicketStatus() {
        plugin.broadcastChatMessage(Messages.get("messages.conquest.tickets", Map.of(
            "tickets", getStatusLineForZone("")
        )));
    }

    private void sendActionBars() {
        String status = getStatusLineForZone("");
        if (status.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(plugin.colorize(
                Messages.get("messages.conquest.actionbar", Map.of("tickets", status))
            )));
        }
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
            if (normalized == null || normalized.trim().isEmpty()) {
                continue;
            }
            CaptureOwner owner = new CaptureOwner(
                ownerType,
                adapter.resolveOwnerId(normalized, ownerType),
                normalized
            );
            if (!containsOwner(owners, owner)) {
                owners.add(owner);
            }
        }
        return owners;
    }

    private List<String> resolveProfileZones(String profile) {
        if (profile == null || profile.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> raw = plugin.getConfig().getStringList("conquest.profiles." + profile + ".zones");
        List<String> zones = new ArrayList<>();
        for (String zoneId : raw) {
            if (zoneId != null && !zoneId.trim().isEmpty() && plugin.getCapturePoint(zoneId.trim()) != null && !containsIgnoreCase(zones, zoneId.trim())) {
                zones.add(zoneId.trim());
            }
        }
        return zones;
    }

    private String resolveDefaultProfile() {
        return normalizeProfile(plugin.getConfig().getString("conquest.default-profile", "default"));
    }

    private String normalizeProfile(String profileName) {
        String profile = profileName == null || profileName.trim().isEmpty()
            ? plugin.getConfig().getString("conquest.default-profile", "default")
            : profileName.trim();
        return profile.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private String ownerKey(CaptureOwner owner) {
        if (owner == null || owner.getType() == null || owner.getDisplayName() == null) {
            return null;
        }
        return owner.getType().name().toLowerCase(Locale.ROOT) + ":" + owner.getDisplayName().toLowerCase(Locale.ROOT);
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Snapshot {
        public final String profile;
        public final Map<String, Integer> ticketsByOwnerKey;
        public final Map<String, CaptureOwner> ownersByKey;
        public final List<String> zoneIds;

        private Snapshot(String profile, Map<String, Integer> ticketsByOwnerKey, Map<String, CaptureOwner> ownersByKey, List<String> zoneIds) {
            this.profile = profile;
            this.ticketsByOwnerKey = ticketsByOwnerKey;
            this.ownersByKey = ownersByKey;
            this.zoneIds = zoneIds;
        }
    }
}
