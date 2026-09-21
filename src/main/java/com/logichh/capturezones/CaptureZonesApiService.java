package com.logichh.capturezones;

import com.google.gson.Gson;
import com.logichh.capturezones.api.CaptureZonesActionResult;
import com.logichh.capturezones.api.CaptureZonesApi;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class CaptureZonesApiService implements CaptureZonesApi {
    private static final long SYNC_TIMEOUT_SECONDS = 20L;
    private static final int DEFAULT_FILE_DEPTH = 4;
    private static final int MAX_FILE_DEPTH = 10;

    private final CaptureZones plugin;
    private final Gson gson;
    private final Set<String> baseCapabilities;

    CaptureZonesApiService(CaptureZones plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.baseCapabilities = buildBaseCapabilities();
    }

    @Override

    public String getApiVersion() {
        return API_VERSION;
    }

    @Override

    public String getPluginVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override

    public Set<String> getCapabilities() {
        try {
            return sync(this::capabilitiesInternal);
        } catch (Exception exception) {
            plugin.getLogger().warning("CaptureZones API capabilities failed: " + exception.getMessage());
            return Collections.unmodifiableSet(new LinkedHashSet<>(baseCapabilities));
        }
    }

    @Override

    public Map<String, Object> getOverviewSnapshot() {
        return snapshot("overview", this::overviewSnapshot);
    }

    @Override

    public Map<String, Object> getFullSnapshot() {
        return snapshot("full", this::fullSnapshot);
    }

    @Override

    public Map<String, Object> getZonesSnapshot() {
        return snapshot("zones", this::zonesSnapshot);
    }

    @Override

    public Map<String, Object> getActiveCapturesSnapshot() {
        return snapshot("active-captures", this::capturesSnapshot);
    }

    @Override

    public Map<String, Object> getKothSnapshot() {
        return snapshot("koth", this::kothSnapshot);
    }

    @Override
    public Map<String, Object> getConquestsSnapshot() {
        return snapshot("conquests", this::conquestsSnapshot);
    }

    @Override

    public Map<String, Object> getShopsSnapshot() {
        return snapshot("shops", this::shopsSnapshot);
    }

    @Override

    public Map<String, Object> getStatisticsSnapshot() {
        return snapshot("statistics", this::statisticsSnapshot);
    }

    @Override

    public Map<String, Object> getGlobalConfigSnapshot() {
        return snapshot("global-config", this::globalConfigSnapshot);
    }

    @Override

    public Map<String, Object> getZoneConfigSnapshot(String zoneId) {
        return snapshot("zone-config", () -> zoneConfigSnapshot(zoneId));
    }

    @Override

    public Map<String, Object> getDataFilesSnapshot(int maxDepth) {
        return snapshot("data-files", () -> dataFilesSnapshot(maxDepth));
    }

    @Override

    public CaptureZonesActionResult reloadAll() {
        return action("reload-all", () -> {
            plugin.reloadAll();
            return CaptureZonesActionResult.ok("CaptureZones fully reloaded.");
        });
    }

    @Override

    public CaptureZonesActionResult reloadLang() {
        return action("reload-lang", () -> {
            plugin.reloadLang();
            return CaptureZonesActionResult.ok("CaptureZones language data reloaded.");
        });
    }

    @Override

    public CaptureZonesActionResult repairSchema(boolean exactTemplates) {
        return action("repair-schema", () -> {
            plugin.repairAllConfigsAndData(exactTemplates);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("exactTemplates", exactTemplates);
            data.put("legacyTownOwnerRebindCount", plugin.getLastLegacyTownOwnerRebindCount());
            return CaptureZonesActionResult.ok("Schema repair completed.", data);
        });
    }

    @Override

    public CaptureZonesActionResult createZone(
        String id,
        String name,
        String worldName,
        double x,
        double y,
        double z,
        int chunkRadius,
        double reward
    ) {
        return action("create-zone", () -> {
            if (blank(id) || blank(name) || blank(worldName)) {
                return CaptureZonesActionResult.fail("id, name, and worldName are required.");
            }
            if (plugin.getCapturePoint(id) != null) {
                return CaptureZonesActionResult.fail("Zone already exists: " + id);
            }
            World world = Bukkit.getWorld(worldName.trim());
            if (world == null) {
                return CaptureZonesActionResult.fail("World not found: " + worldName);
            }
            boolean created = plugin.createCapturePoint(id.trim(), name.trim(), new Location(world, x, y, z), chunkRadius, reward);
            if (!created) {
                return CaptureZonesActionResult.fail("createCapturePoint returned false.");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zone", pointMap(plugin.getCapturePoint(id.trim())));
            return CaptureZonesActionResult.ok("Zone created: " + id, data);
        });
    }

    @Override

    public CaptureZonesActionResult createCuboidZone(
        String id,
        String name,
        String worldName,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        double reward
    ) {
        return action("create-cuboid-zone", () -> {
            if (blank(id) || blank(name) || blank(worldName)) {
                return CaptureZonesActionResult.fail("id, name, and worldName are required.");
            }
            if (plugin.getCapturePoint(id) != null) {
                return CaptureZonesActionResult.fail("Zone already exists: " + id);
            }
            World world = Bukkit.getWorld(worldName.trim());
            if (world == null) {
                return CaptureZonesActionResult.fail("World not found: " + worldName);
            }
            boolean created = plugin.createCuboidCapturePoint(
                id.trim(), name.trim(), world, minX, minY, minZ, maxX, maxY, maxZ, reward
            );
            if (!created) {
                return CaptureZonesActionResult.fail("createCuboidCapturePoint returned false.");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zone", pointMap(plugin.getCapturePoint(id.trim())));
            return CaptureZonesActionResult.ok("Cuboid zone created: " + id, data);
        });
    }

    @Override

    public CaptureZonesActionResult deleteZone(String pointId) {
        return action("delete-zone", () -> {
            if (blank(pointId)) {
                return CaptureZonesActionResult.fail("pointId is required.");
            }
            boolean deleted = plugin.deleteCapturePoint(pointId.trim());
            return deleted ? CaptureZonesActionResult.ok("Zone deleted: " + pointId)
                : CaptureZonesActionResult.fail("Zone not found: " + pointId);
        });
    }

    @Override

    public CaptureZonesActionResult forceCapture(String pointId, String ownerName) {
        return action("force-capture", () -> {
            if (blank(pointId) || blank(ownerName)) {
                return CaptureZonesActionResult.fail("pointId and ownerName are required.");
            }
            boolean forced = plugin.forceCapture(pointId.trim(), ownerName.trim());
            if (!forced) {
                return CaptureZonesActionResult.fail("forceCapture returned false.");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zone", pointMap(plugin.getCapturePoint(pointId.trim())));
            return CaptureZonesActionResult.ok("Zone force-captured.", data);
        });
    }

    @Override

    public CaptureZonesActionResult stopCapture(String pointId, String reason) {
        return action("stop-capture", () -> {
            if (blank(pointId)) {
                return CaptureZonesActionResult.fail("pointId is required.");
            }
            String stopReason = blank(reason) ? "Stopped via API" : reason.trim();
            boolean stopped = plugin.stopCapture(pointId.trim(), stopReason);
            if (!stopped) {
                return CaptureZonesActionResult.fail("No active capture for point: " + pointId);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reason", stopReason);
            return CaptureZonesActionResult.ok("Capture stopped for zone: " + pointId, data);
        });
    }

    @Override

    public CaptureZonesActionResult resetPoint(String pointId) {
        return action("reset-point", () -> {
            if (blank(pointId)) {
                return CaptureZonesActionResult.fail("pointId is required.");
            }
            boolean reset = plugin.resetPoint(pointId.trim());
            if (!reset) {
                return CaptureZonesActionResult.fail("Zone not found: " + pointId);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zone", pointMap(plugin.getCapturePoint(pointId.trim())));
            return CaptureZonesActionResult.ok("Zone reset: " + pointId, data);
        });
    }

    @Override

    public CaptureZonesActionResult resetAllPoints() {
        return action("reset-all-points", () -> {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("resetCount", plugin.resetAllPoints());
            return CaptureZonesActionResult.ok("All zones reset.", data);
        });
    }

    @Override

    public CaptureZonesActionResult setPointType(String pointId, String type) {
        return action("set-point-type", () -> {
            if (blank(pointId) || blank(type)) {
                return CaptureZonesActionResult.fail("pointId and type are required.");
            }
            boolean updated = plugin.setPointType(pointId.trim(), type.trim());
            if (!updated) {
                return CaptureZonesActionResult.fail("Zone not found: " + pointId);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zone", pointMap(plugin.getCapturePoint(pointId.trim())));
            return CaptureZonesActionResult.ok("Zone type updated.", data);
        });
    }

    @Override

    public CaptureZonesActionResult setPointActive(String pointId, boolean active) {
        return action("set-point-active", () -> {
            CapturePoint point = plugin.getCapturePoint(pointId == null ? null : pointId.trim());
            if (point == null) {
                return CaptureZonesActionResult.fail("Zone not found: " + pointId);
            }
            plugin.setPointActive(pointId.trim(), active);
            plugin.saveCapturePoints();
            plugin.refreshPointVisuals(pointId.trim());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zone", pointMap(point));
            return CaptureZonesActionResult.ok("Zone active flag updated.", data);
        });
    }

    @Override

    public CaptureZonesActionResult setPointPlayerLimits(String pointId, int minPlayers, int maxPlayers) {
        return action("set-point-player-limits", () -> {
            CapturePoint point = plugin.getCapturePoint(pointId == null ? null : pointId.trim());
            if (point == null) {
                return CaptureZonesActionResult.fail("Zone not found: " + pointId);
            }
            if (minPlayers < 1 || maxPlayers < minPlayers) {
                return CaptureZonesActionResult.fail("Invalid limits (min >= 1 and max >= min required).");
            }
            plugin.setPointPlayerLimits(pointId.trim(), minPlayers, maxPlayers);
            plugin.saveCapturePoints();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zone", pointMap(point));
            return CaptureZonesActionResult.ok("Zone player limits updated.", data);
        });
    }

    @Override

    public CaptureZonesActionResult toggleChatMessages(boolean enabled) {
        return action("toggle-chat-messages", () -> {
            plugin.toggleChatMessages(enabled);
            return CaptureZonesActionResult.ok("Global chat messages " + (enabled ? "enabled" : "disabled") + ".");
        });
    }

    @Override

    public CaptureZonesActionResult setZoneConfigValue(String zoneId, String path, Object value) {
        return action("set-zone-config-value", () -> {
            ZoneConfigManager zcm = plugin.getZoneConfigManager();
            if (zcm == null) {
                return CaptureZonesActionResult.fail("ZoneConfigManager is unavailable.");
            }
            if (blank(zoneId) || blank(path)) {
                return CaptureZonesActionResult.fail("zoneId and path are required.");
            }
            if (plugin.getCapturePoint(zoneId.trim()) == null) {
                return CaptureZonesActionResult.fail("Zone not found: " + zoneId);
            }
            boolean updated = zcm.setZoneSetting(zoneId.trim(), path.trim(), writeValue(value));
            if (!updated) {
                return CaptureZonesActionResult.fail("Failed to set zone config value.");
            }
            plugin.invalidateCapturePointSpatialIndex();
            plugin.refreshPointVisuals(zoneId.trim());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zoneId", zoneId.trim());
            data.put("path", path.trim());
            data.put("value", readValue(value));
            return CaptureZonesActionResult.ok("Zone config updated.", data);
        });
    }

    @Override

    public CaptureZonesActionResult resetZoneConfigPath(String zoneId, String path) {
        return action("reset-zone-config-path", () -> {
            ZoneConfigManager zcm = plugin.getZoneConfigManager();
            if (zcm == null) {
                return CaptureZonesActionResult.fail("ZoneConfigManager is unavailable.");
            }
            if (blank(zoneId) || blank(path)) {
                return CaptureZonesActionResult.fail("zoneId and path are required.");
            }
            if (plugin.getCapturePoint(zoneId.trim()) == null) {
                return CaptureZonesActionResult.fail("Zone not found: " + zoneId);
            }
            Object defaultValue = zcm.getZoneDefault(path.trim());
            boolean updated = zcm.setZoneSetting(zoneId.trim(), path.trim(), defaultValue);
            if (!updated) {
                return CaptureZonesActionResult.fail("Failed to reset zone config path.");
            }
            zcm.saveZoneConfig(zoneId.trim());
            plugin.invalidateCapturePointSpatialIndex();
            plugin.refreshPointVisuals(zoneId.trim());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zoneId", zoneId.trim());
            data.put("path", path.trim());
            data.put("value", readValue(defaultValue));
            return CaptureZonesActionResult.ok("Zone config path reset.", data);
        });
    }

    @Override

    public CaptureZonesActionResult resetZoneConfig(String zoneId) {
        return action("reset-zone-config", () -> {
            ZoneConfigManager zcm = plugin.getZoneConfigManager();
            if (zcm == null) {
                return CaptureZonesActionResult.fail("ZoneConfigManager is unavailable.");
            }
            if (blank(zoneId) || plugin.getCapturePoint(zoneId.trim()) == null) {
                return CaptureZonesActionResult.fail("Zone not found: " + zoneId);
            }
            boolean generated = zcm.generateZoneConfig(zoneId.trim());
            if (!generated) {
                return CaptureZonesActionResult.fail("Failed to regenerate zone config.");
            }
            plugin.invalidateCapturePointSpatialIndex();
            plugin.refreshPointVisuals(zoneId.trim());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zoneId", zoneId.trim());
            return CaptureZonesActionResult.ok("Zone config reset from template.", data);
        });
    }

    @Override

    public CaptureZonesActionResult reloadZoneConfig(String zoneId) {
        return action("reload-zone-config", () -> {
            ZoneConfigManager zcm = plugin.getZoneConfigManager();
            if (zcm == null) {
                return CaptureZonesActionResult.fail("ZoneConfigManager is unavailable.");
            }
            if (blank(zoneId) || plugin.getCapturePoint(zoneId.trim()) == null) {
                return CaptureZonesActionResult.fail("Zone not found: " + zoneId);
            }
            boolean reloaded = zcm.reloadZoneConfig(zoneId.trim());
            if (!reloaded) {
                return CaptureZonesActionResult.fail("Failed to reload zone config.");
            }
            plugin.invalidateCapturePointSpatialIndex();
            plugin.refreshPointVisuals(zoneId.trim());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zoneId", zoneId.trim());
            return CaptureZonesActionResult.ok("Zone config reloaded.", data);
        });
    }

    @Override

    public CaptureZonesActionResult setGlobalConfigValue(String path, Object value, boolean reloadAfterSave) {
        return action("set-global-config-value", () -> {
            if (blank(path)) {
                return CaptureZonesActionResult.fail("path is required.");
            }
            Object normalized = writeValue(value);
            plugin.getConfig().set(path.trim(), normalized);
            plugin.saveConfig();
            if (reloadAfterSave) {
                plugin.reloadAll();
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("path", path.trim());
            data.put("value", readValue(normalized));
            data.put("reloadAfterSave", reloadAfterSave);
            return CaptureZonesActionResult.ok("Global config updated.", data);
        });
    }

    @Override

    public CaptureZonesActionResult saveGlobalConfig() {
        return action("save-global-config", () -> {
            plugin.saveConfig();
            return CaptureZonesActionResult.ok("Global config saved.");
        });
    }

    @Override

    public CaptureZonesActionResult runAdminCommand(String commandLine) {
        return action("run-admin-command", () -> {
            if (blank(commandLine)) {
                return CaptureZonesActionResult.fail("commandLine is required.");
            }
            String normalized = commandLine.trim();
            if (normalized.startsWith("/")) {
                normalized = normalized.substring(1).trim();
            }
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (!(lower.startsWith("capturezones") || lower.startsWith("capturepoint") || lower.startsWith("cap"))) {
                return CaptureZonesActionResult.fail("Only CaptureZones command execution is allowed.");
            }
            ConsoleCommandSender console = Bukkit.getConsoleSender();
            boolean executed = Bukkit.dispatchCommand(console, normalized);
            if (!executed) {
                return CaptureZonesActionResult.fail("Command failed: " + normalized);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("command", normalized);
            return CaptureZonesActionResult.ok("Command executed.", data);
        });
    }

    @Override

    public CaptureZonesActionResult shopRestock(String zoneId) {
        return action("shop-restock", () -> {
            ShopManager shopManager = plugin.getOrCreateShopManagerIfEnabled();
            if (shopManager == null) {
                return CaptureZonesActionResult.fail(shopUnavailableMessage());
            }
            if (blank(zoneId) || plugin.getCapturePoint(zoneId.trim()) == null) {
                return CaptureZonesActionResult.fail("Zone not found: " + zoneId);
            }
            shopManager.restockShop(zoneId.trim());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zoneId", zoneId.trim());
            return CaptureZonesActionResult.ok("Shop restocked.", data);
        });
    }

    @Override

    public CaptureZonesActionResult shopSetEnabled(String zoneId, boolean enabled) {
        return action("shop-set-enabled", () -> {
            ShopManager shopManager = plugin.getOrCreateShopManagerIfEnabled();
            if (shopManager == null) {
                return CaptureZonesActionResult.fail(shopUnavailableMessage());
            }
            if (blank(zoneId) || plugin.getCapturePoint(zoneId.trim()) == null) {
                return CaptureZonesActionResult.fail("Zone not found: " + zoneId);
            }
            ShopData shop = shopManager.getShop(zoneId.trim());
            shop.setEnabled(enabled);
            shopManager.saveShop(zoneId.trim());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zoneId", zoneId.trim());
            data.put("enabled", enabled);
            return CaptureZonesActionResult.ok("Shop enabled state updated.", data);
        });
    }

    @Override

    public CaptureZonesActionResult shopReloadAll() {
        return action("shop-reload-all", () -> {
            ShopManager shopManager = plugin.getOrCreateShopManagerIfEnabled();
            if (shopManager == null) {
                return CaptureZonesActionResult.fail(shopUnavailableMessage());
            }
            shopManager.saveAllShops();
            shopManager.loadAllShops();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("shopCount", shopManager.getShops().size());
            return CaptureZonesActionResult.ok("All shops reloaded.", data);
        });
    }

    @Override

    public CaptureZonesActionResult kothStart(List<String> zoneIds, boolean announce) {
        return action("koth-start", () -> {
            KothManager kothManager = plugin.getKothManager();
            if (kothManager == null) {
                return CaptureZonesActionResult.fail("KothManager is unavailable.");
            }
            if (!kothManager.isEnabled()) {
                return CaptureZonesActionResult.fail("KOTH is disabled in config.");
            }
            boolean started;
            List<String> resolved = new ArrayList<>();
            if (zoneIds == null || zoneIds.isEmpty()) {
                started = kothManager.startConfiguredEvent();
                resolved.addAll(kothManager.getActiveZoneIds());
            } else {
                for (String raw : zoneIds) {
                    if (blank(raw)) {
                        continue;
                    }
                    CapturePoint point = findPoint(raw.trim());
                    if (point != null && !resolved.contains(point.getId())) {
                        resolved.add(point.getId());
                    }
                }
                started = !resolved.isEmpty() && kothManager.startEventForZones(resolved, announce);
            }
            if (!started) {
                return CaptureZonesActionResult.fail("Failed to start KOTH event.");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("requestedZones", resolved);
            data.put("activeZones", new ArrayList<>(kothManager.getActiveZoneIds()));
            data.put("activeZoneCount", kothManager.getActiveZoneCount());
            return CaptureZonesActionResult.ok("KOTH event started.", data);
        });
    }

    @Override

    public CaptureZonesActionResult kothStop(String zoneId, String reason, boolean announce) {
        return action("koth-stop", () -> {
            KothManager kothManager = plugin.getKothManager();
            if (kothManager == null) {
                return CaptureZonesActionResult.fail("KothManager is unavailable.");
            }
            String normalizedReason = blank(reason) ? "Stopped via API" : reason.trim();
            if (blank(zoneId) || "all".equalsIgnoreCase(zoneId.trim())) {
                int stopped = kothManager.stopAllZones(normalizedReason, announce);
                if (stopped <= 0) {
                    return CaptureZonesActionResult.fail("No active KOTH zones to stop.");
                }
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("stopped", stopped);
                return CaptureZonesActionResult.ok("Stopped all active KOTH zones.", data);
            }
            CapturePoint point = findPoint(zoneId.trim());
            String resolvedZoneId = point != null ? point.getId() : zoneId.trim();
            boolean stopped = kothManager.stopZone(resolvedZoneId, normalizedReason, announce);
            if (!stopped) {
                return CaptureZonesActionResult.fail("Failed to stop KOTH zone: " + zoneId);
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zoneId", resolvedZoneId);
            return CaptureZonesActionResult.ok("Stopped KOTH zone.", data);
        });
    }

    @Override

    public CaptureZonesActionResult kothStopAll(String reason, boolean announce) {
        return action("koth-stop-all", () -> {
            KothManager kothManager = plugin.getKothManager();
            if (kothManager == null) {
                return CaptureZonesActionResult.fail("KothManager is unavailable.");
            }
            String normalizedReason = blank(reason) ? "Stopped via API" : reason.trim();
            int stopped = kothManager.stopAllZones(normalizedReason, announce);
            if (stopped <= 0) {
                return CaptureZonesActionResult.fail("No active KOTH zones to stop.");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stopped", stopped);
            return CaptureZonesActionResult.ok("Stopped all active KOTH zones.", data);
        });
    }

    @Override

    public CaptureZonesActionResult kothAssignZone(String zoneId, boolean assign) {
        return action("koth-assign-zone", () -> {
            if (blank(zoneId)) {
                return CaptureZonesActionResult.fail("zoneId is required.");
            }
            CapturePoint point = findPoint(zoneId.trim());
            if (assign && point == null) {
                return CaptureZonesActionResult.fail("Zone not found: " + zoneId);
            }
            String resolved = point != null ? point.getId() : zoneId.trim();
            List<String> configured = new ArrayList<>(plugin.getConfig().getStringList("koth.activation.zones"));
            if (assign) {
                boolean exists = configured.stream().anyMatch(id -> id != null && id.equalsIgnoreCase(resolved));
                if (!exists) {
                    configured.add(resolved);
                }
                String mode = plugin.getConfig().getString("koth.activation.selection-mode", "ALL");
                if ("ALL".equalsIgnoreCase(mode)) {
                    plugin.getConfig().set("koth.activation.selection-mode", "LIST");
                }
            } else {
                configured.removeIf(id -> id != null && id.equalsIgnoreCase(resolved));
            }
            plugin.getConfig().set("koth.activation.zones", configured);
            plugin.saveConfig();
            if (plugin.getKothManager() != null) {
                plugin.getKothManager().reload();
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("zoneId", resolved);
            data.put("assigned", assign);
            data.put("zones", configured);
            data.put("count", configured.size());
            return CaptureZonesActionResult.ok(assign ? "Zone assigned to KOTH." : "Zone unassigned from KOTH.", data);
        });
    }

    @Override
    public CaptureZonesActionResult startConquest(String profile) {
        return action("conquest-start", () -> {
            ConquestManager manager = plugin.getConquestManager();
            if (manager == null || !manager.isEnabled()) {
                return CaptureZonesActionResult.fail("Conquest mode is disabled.");
            }
            if (!manager.startMatch(profile)) {
                return CaptureZonesActionResult.fail(manager.getLastError());
            }
            return CaptureZonesActionResult.ok("Conquest started.", Map.of(
                "profile", manager.snapshot(profile).profile
            ));
        });
    }

    @Override
    public CaptureZonesActionResult stopConquest(String profile, String reason, boolean announce) {
        return action("conquest-stop", () -> {
            ConquestManager manager = plugin.getConquestManager();
            if (manager == null) {
                return CaptureZonesActionResult.fail("Conquest manager is unavailable.");
            }
            String target = blank(profile)
                ? plugin.getConfig().getString("conquest.default-profile", "default")
                : profile.trim();
            if (!manager.stopMatch(target, reason, announce)) {
                return CaptureZonesActionResult.fail(manager.getLastError());
            }
            return CaptureZonesActionResult.ok("Conquest stopped.", Map.of("profile", target));
        });
    }

    @Override
    public CaptureZonesActionResult stopAllConquests(String reason, boolean announce) {
        return action("conquest-stop-all", () -> {
            ConquestManager manager = plugin.getConquestManager();
            if (manager == null) {
                return CaptureZonesActionResult.fail("Conquest manager is unavailable.");
            }
            int count = manager.getActiveProfiles().size();
            if (!manager.stopAllMatches(reason, announce)) {
                return CaptureZonesActionResult.fail(manager.getLastError());
            }
            return CaptureZonesActionResult.ok("All conquests stopped.", Map.of("stopped", count));
        });
    }

    @Override

    public CaptureZonesActionResult removePlayerStats(UUID playerId) {
        return action("remove-player-stats", () -> {
            StatisticsManager statisticsManager = plugin.getStatisticsManager();
            if (statisticsManager == null) {
                return CaptureZonesActionResult.fail("StatisticsManager is unavailable.");
            }
            if (playerId == null) {
                return CaptureZonesActionResult.fail("playerId is required.");
            }
            statisticsManager.removePlayerStats(playerId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("playerId", playerId.toString());
            return CaptureZonesActionResult.ok("Player statistics removed.", data);
        });
    }

    @Override

    public CaptureZonesActionResult resetAllStats() {
        return action("reset-all-stats", () -> {
            StatisticsManager statisticsManager = plugin.getStatisticsManager();
            if (statisticsManager == null) {
                return CaptureZonesActionResult.fail("StatisticsManager is unavailable.");
            }
            statisticsManager.resetAllStats();
            return CaptureZonesActionResult.ok("All statistics reset.");
        });
    }

    private Map<String, Object> overviewSnapshot() {
        Map<String, Object> out = envelope("overview");
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("name", Bukkit.getName());
        server.put("version", Bukkit.getVersion());
        server.put("bukkitVersion", Bukkit.getBukkitVersion());
        server.put("onlinePlayers", Bukkit.getOnlinePlayers().size());
        server.put("maxPlayers", Bukkit.getMaxPlayers());
        out.put("server", server);

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("zones", plugin.getCapturePoints().size());
        counts.put("activeCaptures", plugin.getActiveSessions().size());
        counts.put("pointTypes", plugin.getPointTypes().size());
        counts.put("activeKothZones", plugin.getActiveKothZoneIds().size());
        counts.put("activeConquests", plugin.getConquestManager() == null
            ? 0 : plugin.getConquestManager().getActiveProfiles().size());
        ShopManager shopManager = plugin.getOrCreateShopManagerIfEnabled();
        counts.put("shops", shopManager == null ? 0 : shopManager.getShops().size());
        out.put("counts", counts);

        out.put("townyAvailable", plugin.isTownyAvailable());
        out.put("townyIntegrationEnabled", plugin.isTownyIntegrationEnabled());
        out.put("dynmapEnabled", plugin.isDynmapEnabled());
        out.put("worldGuardEnabled", plugin.isWorldGuardEnabled());
        out.put("mapProvidersEnabled", plugin.hasMapProviders());
        out.put("ownerPlatform", plugin.getOwnerPlatform() == null ? "unknown" : plugin.getOwnerPlatform().getPlatformKey());
        out.put("capabilities", new ArrayList<>(capabilitiesInternal()));
        return out;
    }

    private Map<String, Object> fullSnapshot() {
        Map<String, Object> out = envelope("full");
        out.put("overview", overviewSnapshot());
        out.put("zones", zonesSnapshot());
        out.put("activeCaptures", capturesSnapshot());
        out.put("koth", kothSnapshot());
        out.put("conquests", conquestsSnapshot());
        out.put("shops", shopsSnapshot());
        out.put("statistics", statisticsSnapshot());
        out.put("globalConfig", globalConfigSnapshot());
        out.put("dataFiles", dataFilesSnapshot(DEFAULT_FILE_DEPTH));
        return out;
    }

    private Map<String, Object> zonesSnapshot() {
        Map<String, Object> out = envelope("zones");
        List<Map<String, Object>> zones = new ArrayList<>();
        List<CapturePoint> points = new ArrayList<>(plugin.getCapturePoints().values());
        points.sort(Comparator.comparing(CapturePoint::getId, String.CASE_INSENSITIVE_ORDER));
        for (CapturePoint point : points) {
            zones.add(pointMap(point));
        }
        out.put("zones", zones);
        out.put("total", zones.size());
        return out;
    }

    private Map<String, Object> capturesSnapshot() {
        Map<String, Object> out = envelope("active-captures");
        List<Map<String, Object>> captures = new ArrayList<>();
        List<Map.Entry<String, CaptureSession>> entries = new ArrayList<>(plugin.getActiveSessions().entrySet());
        entries.sort(Comparator.comparing(entry -> entry.getKey().toLowerCase(Locale.ROOT)));
        for (Map.Entry<String, CaptureSession> entry : entries) {
            captures.add(sessionMap(entry.getValue()));
        }
        out.put("captures", captures);
        out.put("total", captures.size());
        return out;
    }

    private Map<String, Object> kothSnapshot() {
        Map<String, Object> out = envelope("koth");
        KothManager kothManager = plugin.getKothManager();
        if (kothManager == null) {
            out.put("available", false);
            out.put("enabled", false);
            out.put("activeZones", Collections.emptyList());
            return out;
        }
        out.put("available", true);
        out.put("enabled", kothManager.isEnabled());
        List<String> ids = new ArrayList<>(kothManager.getActiveZoneIds());
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        List<Map<String, Object>> active = new ArrayList<>();
        for (String id : ids) {
            KothManager.ZoneStateSnapshot state = kothManager.getZoneState(id);
            CapturePoint point = plugin.getCapturePoint(id);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("zoneId", id);
            row.put("zoneName", point == null ? id : point.getName());
            row.put("holder", state == null ? "" : state.holderName);
            row.put("progressSeconds", state == null ? 0 : state.progressSeconds);
            row.put("captureSeconds", state == null ? 0 : state.captureSeconds);
            row.put("remainingSeconds", state == null ? 0 : state.remainingSeconds());
            row.put("progressPercent", state == null ? 0 : state.progressPercent());
            active.add(row);
        }
        out.put("activeZones", active);
        out.put("activeZoneCount", active.size());
        out.put("assignedZones", plugin.getConfig().getStringList("koth.activation.zones"));
        out.put("selectionMode", plugin.getConfig().getString("koth.activation.selection-mode", "ALL"));
        return out;
    }

    private Map<String, Object> conquestsSnapshot() {
        Map<String, Object> out = envelope("conquests");
        ConquestManager manager = plugin.getConquestManager();
        if (manager == null) {
            out.put("available", false);
            out.put("active", Collections.emptyList());
            out.put("activeCount", 0);
            return out;
        }
        out.put("available", true);
        out.put("enabled", manager.isEnabled());
        out.put("allowConcurrentMatches", manager.isConcurrentMatchesAllowed());
        List<Map<String, Object>> active = new ArrayList<>();
        for (ConquestManager.Snapshot snapshot : manager.snapshots().values()) {
            Map<String, Object> match = new LinkedHashMap<>();
            match.put("profile", snapshot.profile);
            match.put("startedAt", snapshot.startedAt);
            match.put("zones", snapshot.zoneIds);
            List<Map<String, Object>> teams = new ArrayList<>();
            for (Map.Entry<String, Integer> ticketEntry : snapshot.ticketsByOwnerKey.entrySet()) {
                CaptureOwner owner = snapshot.ownersByKey.get(ticketEntry.getKey());
                Map<String, Object> team = new LinkedHashMap<>();
                team.put("key", ticketEntry.getKey());
                team.put("type", owner == null ? "" : owner.getType().name());
                team.put("id", owner == null ? "" : owner.getId());
                team.put("name", owner == null ? ticketEntry.getKey() : owner.getDisplayName());
                team.put("tickets", ticketEntry.getValue());
                teams.add(team);
            }
            match.put("teams", teams);
            active.add(match);
        }
        out.put("active", active);
        out.put("activeCount", active.size());
        return out;
    }

    private Map<String, Object> shopsSnapshot() {
        Map<String, Object> out = envelope("shops");
        boolean configured = plugin.getConfig().getBoolean("shops.enabled", false);
        out.put("configured", configured);
        out.put("economyProvider", plugin.getShopEconomyProviderName());
        ShopManager shopManager = plugin.getOrCreateShopManagerIfEnabled();
        if (shopManager == null) {
            out.put("available", false);
            out.put("reason", plugin.getShopSystemUnavailableReason());
            out.put("shops", Collections.emptyList());
            out.put("total", 0);
            return out;
        }
        List<Map<String, Object>> shops = new ArrayList<>();
        List<String> ids = new ArrayList<>(shopManager.getShops().keySet());
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        for (String id : ids) {
            ShopData shop = shopManager.getShops().get(id);
            if (shop == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("zoneId", id);
            row.put("enabled", shop.isEnabled());
            row.put("accessMode", shop.getAccessMode().name());
            row.put("stockSystem", shop.getStockSystem().name());
            row.put("layoutMode", shop.getLayoutMode().name());
            row.put("pricingMode", shop.getPricingMode().name());
            row.put("restockSchedule", shop.getRestockSchedule().name());
            row.put("totalBuys", shop.getTotalBuys());
            row.put("totalSells", shop.getTotalSells());
            row.put("totalRevenue", shop.getTotalRevenue());
            row.put("items", shop.getItems().size());
            shops.add(row);
        }
        out.put("available", true);
        out.put("reason", "");
        out.put("shops", shops);
        out.put("total", shops.size());
        return out;
    }

    private String shopUnavailableMessage() {
        String reason = plugin.getShopSystemUnavailableReason();
        return reason == null || reason.trim().isEmpty() ? "ShopManager is unavailable." : reason;
    }

    private Map<String, Object> statisticsSnapshot() {
        Map<String, Object> out = envelope("statistics");
        StatisticsManager statisticsManager = plugin.getStatisticsManager();
        if (statisticsManager == null) {
            out.put("available", false);
            return out;
        }
        out.put("available", true);
        Object raw = gson.fromJson(gson.toJson(statisticsManager.getData()), Object.class);
        out.put("data", readValue(raw));
        out.put("topTownsByCaptures", statsEntryRows(statisticsManager.getTopTownsByCaptures(10), "town", "captures"));
        out.put("topTownsByHoldTime", statsEntryRows(statisticsManager.getTopTownsByHoldTime(10), "town", "holdTimeMs"));
        out.put("topTownsByRewards", statsEntryRows(statisticsManager.getTopTownsByRewards(10), "town", "rewards"));
        out.put("topPlayersByKills", playerRows(statisticsManager.getTopPlayersByKills(10), "kills"));
        out.put("topPlayersByKDRatio", playerRows(statisticsManager.getTopPlayersByKDRatio(10), "kdRatio"));
        return out;
    }

    private Map<String, Object> globalConfigSnapshot() {
        Map<String, Object> out = envelope("global-config");
        out.put("config", sectionToMap(plugin.getConfig()));
        return out;
    }

    private Map<String, Object> zoneConfigSnapshot(String zoneId) {
        Map<String, Object> out = envelope("zone-config");
        out.put("zoneId", zoneId);
        ZoneConfigManager zcm = plugin.getZoneConfigManager();
        if (zcm == null) {
            out.put("ok", false);
            out.put("error", "ZoneConfigManager is unavailable.");
            return out;
        }
        if (blank(zoneId)) {
            out.put("ok", false);
            out.put("error", "zoneId is required.");
            return out;
        }
        String normalized = zoneId.trim();
        FileConfiguration cfg = zcm.getZoneConfig(normalized);
        out.put("ok", true);
        out.put("zoneExists", plugin.getCapturePoint(normalized) != null);
        out.put("hasDedicatedFile", zcm.hasZoneConfig(normalized));
        out.put("availablePaths", zcm.getAvailableSettingPaths(normalized));
        out.put("config", cfg == null ? Collections.emptyMap() : sectionToMap(cfg));
        return out;
    }

    private Map<String, Object> dataFilesSnapshot(int requestedDepth) {
        Map<String, Object> out = envelope("data-files");
        int maxDepth = Math.max(0, Math.min(MAX_FILE_DEPTH, requestedDepth <= 0 ? DEFAULT_FILE_DEPTH : requestedDepth));
        File root = plugin.getDataFolder();
        List<Map<String, Object>> files = new ArrayList<>();
        if (root.exists() && root.isDirectory()) {
            collectFiles(root, root, 0, maxDepth, files);
        }
        files.sort(Comparator.comparing(entry -> String.valueOf(entry.getOrDefault("path", "")), String.CASE_INSENSITIVE_ORDER));
        out.put("root", root.getAbsolutePath());
        out.put("maxDepth", maxDepth);
        out.put("files", files);
        out.put("total", files.size());
        return out;
    }

    private Map<String, Object> pointMap(CapturePoint point) {
        if (point == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", point.getId());
        row.put("name", point.getName());
        row.put("type", point.getType());
        row.put("shape", point.getShapeType() == null ? "UNKNOWN" : point.getShapeType().name());
        row.put("active", point.isActive());
        row.put("showOnMap", point.isShowOnMap());
        row.put("chunkRadius", point.getChunkRadius());
        row.put("radiusBlocks", point.getRadius());
        row.put("reward", point.getReward());
        row.put("baseReward", plugin.getBaseReward(point));
        row.put("captureProgress", point.getCaptureProgress());
        row.put("controllingOwner", ownerMap(point.getControllingOwner()));
        row.put("capturingOwner", ownerMap(point.getCapturingOwner()));
        row.put("controllingName", point.getControllingTown());
        row.put("capturingName", point.getCapturingTown());
        row.put("location", locationMap(point.getLocation()));
        row.put("minPlayers", point.getMinPlayers());
        row.put("maxPlayers", point.getMaxPlayers());
        row.put("cooldownUntilTime", point.getCooldownUntilTime());
        row.put("lastCaptureTime", point.getLastCaptureTime());
        row.put("activeSession", sessionMap(plugin.getActiveSession(point.getId())));
        return row;
    }

    private Map<String, Object> sessionMap(CaptureSession session) {
        if (session == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        CapturePoint point = plugin.getCapturePoint(session.getPointId());
        row.put("pointId", session.getPointId());
        row.put("zoneName", point == null ? session.getPointId() : point.getName());
        row.put("townName", session.getTownName());
        row.put("owner", ownerMap(session.getOwner()));
        row.put("playerName", session.getPlayerName());
        row.put("initiatorUuid", session.getInitiatorUUID() == null ? null : session.getInitiatorUUID().toString());
        row.put("startTime", session.getStartTime());
        row.put("active", session.isActive());
        row.put("inPreparationPhase", session.isInPreparationPhase());
        row.put("initialPreparationTime", session.getInitialPreparationTime());
        row.put("initialCaptureTime", session.getInitialCaptureTime());
        row.put("remainingTime", session.getRemainingTime());
        row.put("remainingPreparationTime", session.getRemainingPreparationTime());
        row.put("remainingCaptureTime", session.getRemainingCaptureTime());
        row.put("contested", session.isContested());
        row.put("graceActive", session.isGraceActive());
        row.put("graceSecondsRemaining", session.getGraceSecondsRemaining());
        List<String> participants = new ArrayList<>();
        for (Player player : session.getPlayers()) {
            if (player != null) {
                participants.add(player.getName());
            }
        }
        participants.sort(String.CASE_INSENSITIVE_ORDER);
        row.put("participants", participants);
        row.put("participantCount", participants.size());
        return row;
    }

    private Map<String, Object> ownerMap(CaptureOwner owner) {
        if (owner == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", owner.getType() == null ? null : owner.getType().name());
        row.put("id", owner.getId());
        row.put("displayName", owner.getDisplayName());
        return row;
    }

    private Map<String, Object> locationMap(Location location) {
        if (location == null) {
            return null;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        World world = location.getWorld();
        row.put("world", world == null ? null : world.getName());
        row.put("worldUuid", world == null ? null : world.getUID().toString());
        row.put("x", location.getX());
        row.put("y", location.getY());
        row.put("z", location.getZ());
        row.put("yaw", location.getYaw());
        row.put("pitch", location.getPitch());
        return row;
    }

    private List<Map<String, Object>> statsEntryRows(List<? extends Map.Entry<?, ?>> entries, String keyName, String valueName) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<?, ?> entry : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank++);
            row.put(keyName, entry.getKey());
            row.put(valueName, entry.getValue());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> playerRows(List<? extends Map.Entry<UUID, ?>> entries, String valueName) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<UUID, ?> entry : entries) {
            UUID uuid = entry.getKey();
            OfflinePlayer offlinePlayer = uuid == null ? null : Bukkit.getOfflinePlayer(uuid);
            String playerName = offlinePlayer == null || blank(offlinePlayer.getName()) ? String.valueOf(uuid) : offlinePlayer.getName();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", rank++);
            row.put("playerUuid", uuid == null ? null : uuid.toString());
            row.put("playerName", playerName);
            row.put(valueName, entry.getValue());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> sectionToMap(ConfigurationSection section) {
        if (section == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            out.put(key, readValue(section.get(key)));
        }
        return out;
    }

    private Object readValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof UUID) {
            return value.toString();
        }
        if (value instanceof Enum<?>) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof CaptureOwner) {
            return ownerMap((CaptureOwner) value);
        }
        if (value instanceof Location) {
            return locationMap((Location) value);
        }
        if (value instanceof ItemStack) {
            ItemStack stack = (ItemStack) value;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("material", stack.getType().name());
            item.put("amount", stack.getAmount());
            return item;
        }
        if (value instanceof ConfigurationSection) {
            return sectionToMap((ConfigurationSection) value);
        }
        if (value instanceof Map<?, ?>) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                out.put(String.valueOf(entry.getKey()), readValue(entry.getValue()));
            }
            return out;
        }
        if (value instanceof Collection<?>) {
            List<Object> out = new ArrayList<>();
            for (Object element : (Collection<?>) value) {
                out.add(readValue(element));
            }
            return out;
        }
        if (value instanceof Object[]) {
            List<Object> out = new ArrayList<>();
            for (Object element : (Object[]) value) {
                out.add(readValue(element));
            }
            return out;
        }
        return String.valueOf(value);
    }

    private Object writeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof UUID) {
            return value.toString();
        }
        if (value instanceof Enum<?>) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof Map<?, ?>) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                out.put(String.valueOf(entry.getKey()), writeValue(entry.getValue()));
            }
            return out;
        }
        if (value instanceof Collection<?>) {
            List<Object> out = new ArrayList<>();
            for (Object element : (Collection<?>) value) {
                out.add(writeValue(element));
            }
            return out;
        }
        return String.valueOf(value);
    }

    private void collectFiles(File root, File current, int depth, int maxDepth, List<Map<String, Object>> out) {
        if (current == null || !current.exists()) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("path", relative(root, current));
        row.put("directory", current.isDirectory());
        row.put("size", current.isFile() ? current.length() : 0L);
        row.put("lastModified", current.lastModified());
        out.add(row);
        if (!current.isDirectory() || depth >= maxDepth) {
            return;
        }
        File[] children = current.listFiles();
        if (children == null || children.length == 0) {
            return;
        }
        Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File child : children) {
            collectFiles(root, child, depth + 1, maxDepth, out);
        }
    }

    private String relative(File root, File current) {
        try {
            Path rootPath = root.toPath().toAbsolutePath().normalize();
            Path currentPath = current.toPath().toAbsolutePath().normalize();
            Path relative = rootPath.relativize(currentPath);
            String normalized = relative.toString().replace('\\', '/');
            return normalized.isEmpty() ? "." : normalized;
        } catch (Exception ignored) {
            return current.getAbsolutePath();
        }
    }

    private CapturePoint findPoint(String zoneId) {
        if (blank(zoneId)) {
            return null;
        }
        CapturePoint direct = plugin.getCapturePoint(zoneId);
        if (direct != null) {
            return direct;
        }
        for (CapturePoint point : plugin.getCapturePoints().values()) {
            if (point != null && point.getId() != null && point.getId().equalsIgnoreCase(zoneId)) {
                return point;
            }
        }
        return null;
    }

    private Map<String, Object> snapshot(String name, Callable<Map<String, Object>> supplier) {
        try {
            return sync(supplier);
        } catch (Exception exception) {
            plugin.getLogger().warning("CaptureZones API snapshot '" + name + "' failed: " + exception.getMessage());
            Map<String, Object> out = envelope(name);
            out.put("ok", false);
            out.put("error", exception.getMessage());
            return out;
        }
    }

    private CaptureZonesActionResult action(String name, Callable<CaptureZonesActionResult> action) {
        try {
            CaptureZonesActionResult result = sync(action);
            return result == null ? CaptureZonesActionResult.fail("Action returned null result: " + name) : result;
        } catch (Exception exception) {
            plugin.getLogger().warning("CaptureZones API action '" + name + "' failed: " + exception.getMessage());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("action", name);
            return CaptureZonesActionResult.fail("Action failed: " + exception.getMessage(), data);
        }
    }

    private <T> T sync(Callable<T> supplier) throws Exception {
        if (!plugin.isEnabled()) {
            throw new IllegalStateException("CaptureZones is not enabled.");
        }
        if (Bukkit.isPrimaryThread()) {
            return supplier.call();
        }
        Future<T> future = Bukkit.getScheduler().callSyncMethod(plugin, supplier);
        return future.get(SYNC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private Set<String> buildBaseCapabilities() {
        LinkedHashSet<String> caps = new LinkedHashSet<>();
        caps.add("snapshot.overview");
        caps.add("snapshot.full");
        caps.add("snapshot.zones");
        caps.add("snapshot.active-captures");
        caps.add("snapshot.koth");
        caps.add("snapshot.conquests");
        caps.add("snapshot.shops");
        caps.add("snapshot.statistics");
        caps.add("snapshot.global-config");
        caps.add("snapshot.zone-config");
        caps.add("snapshot.data-files");
        caps.add("action.reload-all");
        caps.add("action.reload-lang");
        caps.add("action.repair-schema");
        caps.add("action.create-zone");
        caps.add("action.create-cuboid-zone");
        caps.add("action.delete-zone");
        caps.add("action.force-capture");
        caps.add("action.stop-capture");
        caps.add("action.reset-point");
        caps.add("action.reset-all-points");
        caps.add("action.set-point-type");
        caps.add("action.set-point-active");
        caps.add("action.set-point-player-limits");
        caps.add("action.toggle-chat");
        caps.add("action.zone-config.set");
        caps.add("action.zone-config.reset-path");
        caps.add("action.zone-config.reset-all");
        caps.add("action.zone-config.reload");
        caps.add("action.global-config.set");
        caps.add("action.global-config.save");
        caps.add("action.run-admin-command");
        caps.add("action.shop.restock");
        caps.add("action.shop.set-enabled");
        caps.add("action.shop.reload-all");
        caps.add("action.koth.start");
        caps.add("action.koth.stop");
        caps.add("action.koth.stop-all");
        caps.add("action.koth.assign");
        caps.add("action.conquest.start");
        caps.add("action.conquest.stop");
        caps.add("action.conquest.stop-all");
        caps.add("action.stats.remove-player");
        caps.add("action.stats.reset-all");
        return Collections.unmodifiableSet(caps);
    }

    private Set<String> capabilitiesInternal() {
        LinkedHashSet<String> caps = new LinkedHashSet<>(baseCapabilities);
        if (plugin.getStatisticsManager() != null) {
            caps.add("feature.statistics");
        }
        if (plugin.getOrCreateShopManagerIfEnabled() != null) {
            caps.add("feature.shops");
        }
        if (plugin.getKothManager() != null) {
            caps.add("feature.koth");
        }
        if (plugin.getDiscordWebhook() != null) {
            caps.add("feature.discord-webhooks");
        }
        if (plugin.hasMapProviders()) {
            caps.add("feature.map-providers");
        }
        if (plugin.isWorldGuardEnabled()) {
            caps.add("feature.worldguard");
        }
        if (plugin.isTownyAvailable()) {
            caps.add("feature.towny-available");
        }
        if (plugin.isTownyIntegrationEnabled()) {
            caps.add("feature.towny-enabled");
        }
        if (plugin.getOwnerPlatform() != null) {
            caps.add("feature.owner-platform." + plugin.getOwnerPlatform().getPlatformKey());
        }
        for (CaptureOwnerType ownerType : CaptureOwnerType.values()) {
            if (plugin.isOwnerTypeAllowed(ownerType)) {
                caps.add("owner-type." + ownerType.name().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(caps);
    }

    private Map<String, Object> envelope(String type) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("type", type);
        out.put("timestamp", System.currentTimeMillis());
        out.put("apiVersion", API_VERSION);
        out.put("pluginVersion", getPluginVersion());
        return out;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
