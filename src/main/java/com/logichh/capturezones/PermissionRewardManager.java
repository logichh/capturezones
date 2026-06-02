package com.logichh.capturezones;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class PermissionRewardManager implements Listener {
    private static final String DATA_FILE_NAME = "permission_rewards.yml";
    private static final long EXPIRATION_CHECK_INTERVAL_TICKS = 20L;

    private final CaptureZones plugin;
    private final Logger logger;
    private final File dataFile;

    // playerId -> permission -> sourceKey -> expiresAtMillis (0 = permanent)

    private final Map<UUID, Map<String, Map<String, Long>>> grants = new HashMap<>();

    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    private final Map<UUID, Set<String>> appliedPermissions = new HashMap<>();

    private BukkitTask expirationTask;
    private boolean dirty;

    public PermissionRewardManager(CaptureZones plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.dataFile = new File(plugin.getDataFolder(), DATA_FILE_NAME);
    }

    public void initialize() {
        load();
        applyToOnlinePlayers();
        startExpirationTask();
    }

    public void reload() {
        stopExpirationTask();
        clearAllAttachments();
        grants.clear();
        dirty = false;
        load();
        applyToOnlinePlayers();
        startExpirationTask();
    }

    public void shutdown() {
        stopExpirationTask();
        saveIfDirty();
        clearAllAttachments();
    }

    public GrantResult grantPermission(Player player, String permission, String sourceKey, long durationMs) {
        if (player == null || !player.isOnline()) {
            return GrantResult.notApplied();
        }
        String normalizedPermission = normalizePermissionNode(permission);
        if (normalizedPermission == null) {
            return GrantResult.notApplied();
        }
        String normalizedSource = normalizeSourceKey(sourceKey);
        if (normalizedSource == null) {
            return GrantResult.notApplied();
        }

        long now = System.currentTimeMillis();
        long expiresAt = durationMs <= 0L ? 0L : now + Math.max(1L, durationMs);

        UUID playerId = player.getUniqueId();
        Map<String, Map<String, Long>> byPermission = grants.computeIfAbsent(playerId, ignored -> new HashMap<>());
        Map<String, Long> bySource = byPermission.computeIfAbsent(normalizedPermission, ignored -> new HashMap<>());

        Long previous = bySource.get(normalizedSource);
        long merged = mergeExpiry(previous, expiresAt);
        boolean changed = previous == null || previous.longValue() != merged;
        if (changed) {
            bySource.put(normalizedSource, merged);
            dirty = true;
        }

        if (pruneExpiredForPlayer(playerId, now)) {
            changed = true;
        }
        if (changed) {
            applyPlayerPermissions(player);
            saveIfDirty();
        } else {
            applyPlayerPermissions(player);
        }

        long effectiveExpiresAt = resolveEffectiveExpiry(playerId, normalizedPermission, now);
        boolean active = effectiveExpiresAt == 0L || effectiveExpiresAt > now;
        boolean permanent = effectiveExpiresAt == 0L;
        return new GrantResult(changed, active, permanent, effectiveExpiresAt);
    }

    @EventHandler

    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (pruneExpiredForPlayer(player.getUniqueId(), now)) {
            saveIfDirty();
        }
        applyPlayerPermissions(player);
    }

    @EventHandler

    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        clearAttachment(event.getPlayer().getUniqueId());
    }

    private void startExpirationTask() {
        stopExpirationTask();
        this.expirationTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickExpiration();
            }
        }.runTaskTimer(plugin, EXPIRATION_CHECK_INTERVAL_TICKS, EXPIRATION_CHECK_INTERVAL_TICKS);
    }

    private void stopExpirationTask() {
        if (expirationTask != null && !expirationTask.isCancelled()) {
            expirationTask.cancel();
        }
        expirationTask = null;
    }

    private void tickExpiration() {
        long now = System.currentTimeMillis();
        boolean changed = pruneExpired(now);
        if (!changed) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPlayerPermissions(player);
        }
        saveIfDirty();
    }

    private void applyToOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPlayerPermissions(player);
        }
    }

    private void applyPlayerPermissions(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (pruneExpiredForPlayer(playerId, now)) {
            saveIfDirty();
        }

        Set<String> desired = computeActivePermissions(playerId, now);
        Set<String> current = appliedPermissions.computeIfAbsent(playerId, ignored -> new HashSet<>());

        if (desired.isEmpty()) {
            clearAttachment(playerId);
            return;
        }

        PermissionAttachment attachment = attachments.get(playerId);
        if (attachment == null) {
            attachment = player.addAttachment(plugin);
            attachments.put(playerId, attachment);
        }

        List<String> toUnset = new ArrayList<>();
        for (String existing : current) {
            if (!desired.contains(existing)) {
                toUnset.add(existing);
            }
        }
        for (String permission : toUnset) {
            attachment.unsetPermission(permission);
            current.remove(permission);
        }

        for (String permission : desired) {
            if (!current.contains(permission)) {
                attachment.setPermission(permission, true);
                current.add(permission);
            }
        }
    }

    private Set<String> computeActivePermissions(UUID playerId, long now) {
        Map<String, Map<String, Long>> byPermission = grants.get(playerId);
        if (byPermission == null || byPermission.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> active = new HashSet<>();
        for (Map.Entry<String, Map<String, Long>> entry : byPermission.entrySet()) {
            String permission = entry.getKey();
            Map<String, Long> sources = entry.getValue();
            if (permission == null || permission.trim().isEmpty() || sources == null || sources.isEmpty()) {
                continue;
            }
            for (Long expiresAt : sources.values()) {
                if (expiresAt == null) {
                    continue;
                }
                if (expiresAt == 0L || expiresAt > now) {
                    active.add(permission);
                    break;
                }
            }
        }
        return active;
    }

    private long resolveEffectiveExpiry(UUID playerId, String permission, long now) {
        Map<String, Map<String, Long>> byPermission = grants.get(playerId);
        if (byPermission == null) {
            return -1L;
        }
        Map<String, Long> sources = byPermission.get(permission);
        if (sources == null || sources.isEmpty()) {
            return -1L;
        }

        long effective = -1L;
        for (Long expiresAt : sources.values()) {
            if (expiresAt == null) {
                continue;
            }
            if (expiresAt == 0L) {
                return 0L;
            }
            if (expiresAt > now) {
                effective = Math.max(effective, expiresAt);
            }
        }
        return effective;
    }

    private boolean pruneExpired(long now) {
        boolean changed = false;
        List<UUID> playerIds = new ArrayList<>(grants.keySet());
        for (UUID playerId : playerIds) {
            changed |= pruneExpiredForPlayer(playerId, now);
        }
        return changed;
    }

    private boolean pruneExpiredForPlayer(UUID playerId, long now) {
        if (playerId == null) {
            return false;
        }
        Map<String, Map<String, Long>> byPermission = grants.get(playerId);
        if (byPermission == null || byPermission.isEmpty()) {
            grants.remove(playerId);
            return false;
        }

        boolean changed = false;
        List<String> permissions = new ArrayList<>(byPermission.keySet());
        for (String permission : permissions) {
            Map<String, Long> bySource = byPermission.get(permission);
            if (bySource == null || bySource.isEmpty()) {
                byPermission.remove(permission);
                changed = true;
                continue;
            }
            List<String> sources = new ArrayList<>(bySource.keySet());
            for (String source : sources) {
                Long expiresAt = bySource.get(source);
                if (expiresAt == null) {
                    bySource.remove(source);
                    changed = true;
                    continue;
                }
                if (expiresAt > 0L && expiresAt <= now) {
                    bySource.remove(source);
                    changed = true;
                }
            }
            if (bySource.isEmpty()) {
                byPermission.remove(permission);
                changed = true;
            }
        }
        if (byPermission.isEmpty()) {
            grants.remove(playerId);
            changed = true;
        }
        if (changed) {
            dirty = true;
        }
        return changed;
    }

    private void clearAllAttachments() {
        for (UUID playerId : new ArrayList<>(attachments.keySet())) {
            clearAttachment(playerId);
        }
    }

    private void clearAttachment(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PermissionAttachment attachment = attachments.remove(playerId);
        if (attachment != null) {
            try {
                attachment.remove();
            } catch (Exception ignored) {
                // Ignore attachment removal errors.
            }
        }
        appliedPermissions.remove(playerId);
    }

    private long mergeExpiry(Long previous, long incoming) {
        if (previous == null) {
            return incoming;
        }
        if (previous == 0L || incoming == 0L) {
            return 0L;
        }
        return Math.max(previous, incoming);
    }

    private String normalizePermissionNode(String permission) {
        if (permission == null) {
            return null;
        }
        String normalized = permission.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeSourceKey(String sourceKey) {
        if (sourceKey == null) {
            return null;
        }
        String normalized = sourceKey.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void saveIfDirty() {
        if (!dirty) {
            return;
        }
        save();
    }

    private void save() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                logger.warning("Failed to create data folder for permission rewards.");
                return;
            }

            FileConfiguration config = new YamlConfiguration();
            config.set("version", 1);

            ConfigurationSection playersSection = config.createSection("players");
            for (Map.Entry<UUID, Map<String, Map<String, Long>>> playerEntry : grants.entrySet()) {
                UUID playerId = playerEntry.getKey();
                if (playerId == null) {
                    continue;
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                Map<String, Map<String, Long>> byPermission = playerEntry.getValue();
                if (byPermission == null || byPermission.isEmpty()) {
                    continue;
                }

                for (Map.Entry<String, Map<String, Long>> permissionEntry : byPermission.entrySet()) {
                    String permission = permissionEntry.getKey();
                    Map<String, Long> bySource = permissionEntry.getValue();
                    if (permission == null || permission.isEmpty() || bySource == null || bySource.isEmpty()) {
                        continue;
                    }

                    for (Map.Entry<String, Long> sourceEntry : bySource.entrySet()) {
                        String source = sourceEntry.getKey();
                        Long expiresAt = sourceEntry.getValue();
                        if (source == null || source.trim().isEmpty() || expiresAt == null) {
                            continue;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("permission", permission);
                        row.put("source", source);
                        row.put("expires-at", expiresAt);
                        rows.add(row);
                    }
                }

                if (!rows.isEmpty()) {
                    playersSection.set(playerId.toString() + ".grants", rows);
                }
            }

            config.save(dataFile);
            dirty = false;
        } catch (IOException io) {
            logger.warning("Failed to save permission reward data: " + io.getMessage());
        }
    }

    private void load() {
        grants.clear();
        if (!dataFile.exists()) {
            dirty = false;
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection playersSection = config.getConfigurationSection("players");
        if (playersSection == null) {
            dirty = false;
            return;
        }

        long now = System.currentTimeMillis();
        for (String uuidRaw : playersSection.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(uuidRaw);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            List<?> rows = playersSection.getList(uuidRaw + ".grants");
            if (rows == null || rows.isEmpty()) {
                continue;
            }

            for (Object rawRow : rows) {
                if (!(rawRow instanceof Map<?, ?>)) {
                    continue;
                }
                Map<?, ?> row = (Map<?, ?>) rawRow;
                Object permissionRaw = row.get("permission");
                Object sourceRaw = row.get("source");
                Object expiresRaw = row.get("expires-at");
                if (permissionRaw == null || sourceRaw == null || expiresRaw == null) {
                    continue;
                }

                String permission = normalizePermissionNode(String.valueOf(permissionRaw));
                String source = normalizeSourceKey(String.valueOf(sourceRaw));
                Long expiresAt = parseLongValue(expiresRaw);
                if (permission == null || source == null || expiresAt == null) {
                    continue;
                }
                if (expiresAt > 0L && expiresAt <= now) {
                    continue;
                }

                Map<String, Map<String, Long>> byPermission = grants.computeIfAbsent(playerId, ignored -> new HashMap<>());
                Map<String, Long> bySource = byPermission.computeIfAbsent(permission, ignored -> new HashMap<>());
                long merged = mergeExpiry(bySource.get(source), expiresAt);
                bySource.put(source, merged);
            }
        }

        dirty = false;
    }

    private Long parseLongValue(Object raw) {
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        if (raw == null) {
            return null;
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static final class GrantResult {
        public final boolean changed;
        public final boolean active;
        public final boolean permanent;
        public final long expiresAtMillis;

        private GrantResult(boolean changed, boolean active, boolean permanent, long expiresAtMillis) {
            this.changed = changed;
            this.active = active;
            this.permanent = permanent;
            this.expiresAtMillis = expiresAtMillis;
        }

        private static GrantResult notApplied() {
            return new GrantResult(false, false, false, -1L);
        }
    }
}

