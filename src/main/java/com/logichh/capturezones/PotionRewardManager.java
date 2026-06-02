package com.logichh.capturezones;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PotionRewardManager {
    private static final String DATA_FILE_NAME = "potion_rewards.yml";
    private static final int DEFAULT_REFRESH_TICKS = 160;
    private static final int DEFAULT_DURATION_TICKS = 260;
    private static final int MAX_AMPLIFIER = 10;

    private final CaptureZones plugin;
    private final File dataFile;
    private final Map<String, UUID> capturersByZone = new HashMap<>();
    private BukkitTask refreshTask;
    private boolean dirty;

    public PotionRewardManager(CaptureZones plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), DATA_FILE_NAME);
    }

    public void initialize() {
        load();
        startRefreshTask();
        refreshAllOnline();
    }

    public void reload() {
        shutdown(false);
        capturersByZone.clear();
        dirty = false;
        load();
        startRefreshTask();
        refreshAllOnline();
    }

    public void shutdown() {
        shutdown(true);
    }

    private void shutdown(boolean save) {
        if (refreshTask != null && !refreshTask.isCancelled()) {
            refreshTask.cancel();
        }
        refreshTask = null;
        if (save) {
            saveIfDirty();
        }
    }

    public void onCaptureComplete(CapturePoint point, UUID capturerId) {
        if (point == null || point.getId() == null) {
            return;
        }
        String zoneId = point.getId();
        UUID previous = capturersByZone.get(zoneId);
        if (previous != null && !previous.equals(capturerId)) {
            removeConfiguredEffects(previous, zoneId);
        }

        if (capturerId == null || resolveEffects(zoneId).isEmpty()) {
            capturersByZone.remove(zoneId);
        } else {
            capturersByZone.put(zoneId, capturerId);
            applyConfiguredEffects(Bukkit.getPlayer(capturerId), zoneId, true);
        }
        dirty = true;
        saveIfDirty();
    }

    public void clearZone(String zoneId) {
        if (zoneId == null || zoneId.trim().isEmpty()) {
            return;
        }
        UUID previous = capturersByZone.remove(zoneId.trim());
        if (previous != null) {
            removeConfiguredEffects(previous, zoneId.trim());
            dirty = true;
            saveIfDirty();
        }
    }

    public void clearAllZones() {
        Set<String> zoneIds = new HashSet<>(capturersByZone.keySet());
        for (String zoneId : zoneIds) {
            clearZone(zoneId);
        }
    }

    public void onPlayerJoin(Player player) {
        if (player == null) {
            return;
        }
        for (Map.Entry<String, UUID> entry : capturersByZone.entrySet()) {
            if (player.getUniqueId().equals(entry.getValue())) {
                applyConfiguredEffects(player, entry.getKey(), true);
            }
        }
    }

    private void startRefreshTask() {
        int refreshTicks = Math.max(20, plugin.getConfig().getInt("potion-rewards.refresh-interval-ticks", DEFAULT_REFRESH_TICKS));
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAllOnline, 20L, refreshTicks);
    }

    private void refreshAllOnline() {
        if (capturersByZone.isEmpty()) {
            return;
        }
        List<String> staleZones = new ArrayList<>();
        for (Map.Entry<String, UUID> entry : new HashMap<>(capturersByZone).entrySet()) {
            String zoneId = entry.getKey();
            CapturePoint point = plugin.getCapturePoint(zoneId);
            if (point == null || point.getControllingOwner() == null || resolveEffects(zoneId).isEmpty()) {
                staleZones.add(zoneId);
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getValue());
            applyConfiguredEffects(player, zoneId, false);
        }
        for (String zoneId : staleZones) {
            clearZone(zoneId);
        }
    }

    private void applyConfiguredEffects(Player player, String zoneId, boolean notify) {
        if (player == null || !player.isOnline()) {
            return;
        }
        List<ConfiguredPotionEffect> effects = resolveEffects(zoneId);
        if (effects.isEmpty()) {
            return;
        }
        for (ConfiguredPotionEffect effect : effects) {
            player.addPotionEffect(new PotionEffect(
                effect.type,
                effect.durationTicks,
                effect.amplifier,
                effect.ambient,
                effect.particles,
                effect.icon
            ), true);
        }
        if (notify) {
            plugin.sendNotification(player, Messages.get("messages.reward.potion.applied", Map.of(
                "zone", zoneId,
                "effects", summarizeEffects(effects)
            )));
        }
    }

    private void removeConfiguredEffects(UUID playerId, String zoneId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        List<ConfiguredPotionEffect> effects = resolveEffects(zoneId);
        for (ConfiguredPotionEffect effect : effects) {
            PotionEffect active = player.getPotionEffect(effect.type);
            if (active == null) {
                continue;
            }
            if (active.getAmplifier() <= effect.amplifier && active.getDuration() <= effect.durationTicks + 40) {
                player.removePotionEffect(effect.type);
            }
        }
        if (!effects.isEmpty()) {
            plugin.sendNotification(player, Messages.get("messages.reward.potion.removed", Map.of(
                "zone", zoneId,
                "effects", summarizeEffects(effects)
            )));
        }
    }

    private List<ConfiguredPotionEffect> resolveEffects(String zoneId) {
        List<ConfiguredPotionEffect> effects = new ArrayList<>();
        ZoneConfigManager zoneConfigManager = plugin.getZoneConfigManager();
        if (zoneConfigManager == null || !zoneConfigManager.getBoolean(zoneId, "rewards.potion-effects.enabled", false)) {
            return effects;
        }
        List<?> entries = zoneConfigManager.getList(zoneId, "rewards.potion-effects.effects", List.of());
        if (entries == null || entries.isEmpty()) {
            return effects;
        }
        int defaultDuration = Math.max(40, zoneConfigManager.getInt(zoneId, "rewards.potion-effects.duration-ticks", DEFAULT_DURATION_TICKS));
        for (Object entry : entries) {
            ConfiguredPotionEffect effect = parseEffect(entry, defaultDuration);
            if (effect != null) {
                effects.add(effect);
            }
        }
        return effects;
    }

    private ConfiguredPotionEffect parseEffect(Object entry, int defaultDuration) {
        String typeName = null;
        int amplifier = 0;
        int durationTicks = defaultDuration;
        boolean ambient = true;
        boolean particles = true;
        boolean icon = true;

        if (entry instanceof String) {
            String[] parts = ((String) entry).split(":");
            typeName = parts.length > 0 ? parts[0] : null;
            if (parts.length > 1) {
                amplifier = parseInt(parts[1], 0);
            }
        } else if (entry instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) entry;
            typeName = stringValue(map.get("type"));
            if (typeName == null) {
                typeName = stringValue(map.get("effect"));
            }
            amplifier = parseInt(map.get("amplifier"), 0);
            durationTicks = parseInt(map.get("duration-ticks"), defaultDuration);
            ambient = parseBoolean(map.get("ambient"), true);
            particles = parseBoolean(map.get("particles"), true);
            icon = parseBoolean(map.get("icon"), true);
        }

        PotionEffectType type = resolvePotionType(typeName);
        if (type == null) {
            return null;
        }
        return new ConfiguredPotionEffect(
            type,
            Math.max(40, durationTicks),
            Math.max(0, Math.min(MAX_AMPLIFIER, amplifier)),
            ambient,
            particles,
            icon
        );
    }

    private PotionEffectType resolvePotionType(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        PotionEffectType byName = PotionEffectType.getByName(normalized);
        if (byName != null) {
            return byName;
        }
        try {
            return PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(normalized.toLowerCase(Locale.ROOT)));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String summarizeEffects(List<ConfiguredPotionEffect> effects) {
        List<String> parts = new ArrayList<>();
        for (ConfiguredPotionEffect effect : effects) {
            parts.add(effect.type.getName() + " " + (effect.amplifier + 1));
        }
        return String.join(", ", parts);
    }

    private void load() {
        if (!dataFile.exists()) {
            return;
        }
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = data.getConfigurationSection("zones");
        if (section == null) {
            return;
        }
        for (String zoneId : section.getKeys(false)) {
            String raw = section.getString(zoneId + ".capturer");
            try {
                capturersByZone.put(zoneId, UUID.fromString(raw));
            } catch (Exception ignored) {
                // Ignore invalid persisted entries.
            }
        }
    }

    private void saveIfDirty() {
        if (!dirty) {
            return;
        }
        FileConfiguration data = new YamlConfiguration();
        for (Map.Entry<String, UUID> entry : capturersByZone.entrySet()) {
            data.set("zones." + entry.getKey() + ".capturer", entry.getValue().toString());
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            data.save(dataFile);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save potion reward data: " + ex.getMessage());
        }
    }

    private String stringValue(Object raw) {
        return raw == null ? null : String.valueOf(raw).trim();
    }

    private int parseInt(Object raw, int fallback) {
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        try {
            return raw == null ? fallback : Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean parseBoolean(Object raw, boolean fallback) {
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw == null) {
            return fallback;
        }
        String normalized = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "no".equals(normalized) || "off".equals(normalized)) {
            return false;
        }
        return fallback;
    }

    private static final class ConfiguredPotionEffect {
        private final PotionEffectType type;
        private final int durationTicks;
        private final int amplifier;
        private final boolean ambient;
        private final boolean particles;
        private final boolean icon;

        private ConfiguredPotionEffect(PotionEffectType type, int durationTicks, int amplifier, boolean ambient, boolean particles, boolean icon) {
            this.type = type;
            this.durationTicks = durationTicks;
            this.amplifier = amplifier;
            this.ambient = ambient;
            this.particles = particles;
            this.icon = icon;
        }
    }
}
