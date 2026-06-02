package com.logichh.capturezones;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

public class MythicMobsHandler implements MobSpawner {
    
    private enum SpawnMode {
        MYTHIC_ONLY,
        MIXED
    }
    
    private final CaptureZones plugin;
    private final Logger logger;
    private final Random random;
    private final List<String> defaultMobTypes;
    private final double defaultMobLevel;
    private final boolean defaultEnabled;
    private final SpawnMode defaultSpawnMode;
    private final int defaultMixChance;
    private final MobSpawner fallbackSpawner;
    private boolean mythicMobsAvailable;

    private final Map<String, Set<String>> warnedInvalidMobs = new HashMap<>();

    private final Set<String> warnedEmptyMobLists = new HashSet<>();

    private final Map<String, Set<String>> warnedSpawnFailures = new HashMap<>();

    private final Set<String> warnedInvalidSpawnModes = new HashSet<>();
    
    public MythicMobsHandler(CaptureZones plugin, MobSpawner fallbackSpawner) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.random = new Random();
        this.fallbackSpawner = fallbackSpawner;
        
        boolean fallbackEnabled = plugin.getConfig().getBoolean("reinforcements.mythicmobs.enabled", false);
        ZoneConfigManager zoneManager = plugin.getZoneConfigManager();
        this.defaultEnabled = zoneManager != null
            ? zoneManager.getDefaultBoolean("reinforcements.mythicmobs.enabled", fallbackEnabled)
            : fallbackEnabled;
        this.defaultMobTypes = getDefaultMobTypes();
        this.defaultMobLevel = zoneManager != null
            ? zoneManager.getDefaultDouble("reinforcements.mythicmobs.mob-level", 1.0)
            : plugin.getConfig().getDouble("reinforcements.mythicmobs.mob-level", 1.0);
        String fallbackSpawnMode = plugin.getConfig().getString("reinforcements.mythicmobs.spawn-mode", "MIXED");
        String defaultSpawnModeRaw = fallbackSpawnMode;
        if (zoneManager != null) {
            Object zoneSpawnMode = zoneManager.getZoneDefault("reinforcements.mythicmobs.spawn-mode");
            if (zoneSpawnMode != null) {
                defaultSpawnModeRaw = zoneSpawnMode.toString();
            }
        }
        this.defaultSpawnMode = parseSpawnMode("default", defaultSpawnModeRaw, SpawnMode.MIXED);
        int fallbackMixChance = plugin.getConfig().getInt("reinforcements.mythicmobs.mix-chance", 100);
        int defaultMixChanceRaw = zoneManager != null
            ? zoneManager.getDefaultInt("reinforcements.mythicmobs.mix-chance", fallbackMixChance)
            : fallbackMixChance;
        this.defaultMixChance = clampMixChance(defaultMixChanceRaw);
        
        this.mythicMobsAvailable = checkMythicMobsAvailability();
        
        if (this.defaultEnabled && this.mythicMobsAvailable) {
            logger.info("MythicMobs integration enabled with " + defaultMobTypes.size() + " configured mob types");
            validateMobTypes();
        } else if (this.defaultEnabled && !this.mythicMobsAvailable) {
            logger.warning("MythicMobs integration is enabled in config but MythicMobs plugin not found!");
            logger.warning("Falling back to vanilla mob spawning.");
        }
    }
    
    @Override

    public LivingEntity spawnMob(String pointId, Location location, Player target) {
        if (!isMythicEnabled(pointId) || !mythicMobsAvailable) {
            return fallbackSpawner.spawnMob(pointId, location, target);
        }

        SpawnMode spawnMode = getSpawnMode(pointId);
        if (!shouldUseMythic(spawnMode, pointId)) {
            return fallbackSpawner.spawnMob(pointId, location, target);
        }
        
        List<String> mythicMobTypes = getValidMythicMobTypes(pointId);
        if (mythicMobTypes.isEmpty()) {
            logEmptyMobList(pointId);
            if (spawnMode == SpawnMode.MIXED) {
                return fallbackSpawner.spawnMob(pointId, location, target);
            }
            return null;
        }
        
        String mobType = null;
        try {
            // Select random mythic mob type
            mobType = mythicMobTypes.get(random.nextInt(mythicMobTypes.size()));
            
            // Attempt to spawn the mythic mob
            ActiveMob activeMob = MythicBukkit.inst().getMobManager().spawnMob(
                mobType,
                location,
                getMobLevel(pointId)
            );
            
            if (activeMob != null && activeMob.getEntity() != null &&
                activeMob.getEntity().getBukkitEntity() instanceof LivingEntity) {
                LivingEntity entity = (LivingEntity) activeMob.getEntity().getBukkitEntity();
                
                if (target != null && entity instanceof org.bukkit.entity.Mob) {
                    ((org.bukkit.entity.Mob) entity).setTarget(target);
                }

                entity.setMetadata("reinforcement_source", new FixedMetadataValue(plugin, "MYTHIC"));
                entity.setMetadata("reinforcement_mythic_type", new FixedMetadataValue(plugin, mobType));
                
                if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                    logger.info("Spawned MythicMob: " + mobType + " at " + location);
                }
                
                return entity;
            }
            logSpawnFailure(pointId, mobType, "spawn returned no living entity");
        } catch (Exception e) {
            logSpawnFailure(pointId, mobType, e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
        }
        
        if (spawnMode == SpawnMode.MIXED) {
            return fallbackSpawner.spawnMob(pointId, location, target);
        }
        return null;
    }
    
    @Override

    public boolean isAvailable() {
        return mythicMobsAvailable || fallbackSpawner.isAvailable();
    }
    
    @Override

    public String getName() {
        if (mythicMobsAvailable) {
            return "MythicMobs (with vanilla fallback)";
        }
        return fallbackSpawner.getName();
    }
    
    @Override

    public List<String> getConfiguredMobs() {
        List<String> result = new ArrayList<>();
        if (defaultEnabled && mythicMobsAvailable) {
            result.addAll(defaultMobTypes);
        }
        result.addAll(getZoneOverrideMobTypes());
        result.add("Fallback: " + fallbackSpawner.getName());
        return result;
    }
    
    private boolean checkMythicMobsAvailability() {
        try {
            Plugin mythicPlugin = plugin.getServer().getPluginManager().getPlugin("MythicMobs");
            if (mythicPlugin != null && mythicPlugin.isEnabled()) {
                // Verify API is accessible
                MythicBukkit.inst().getMobManager();
                return true;
            }
        } catch (Exception | NoClassDefFoundError e) {
            logger.warning("MythicMobs API not accessible: " + e.getMessage());
        }
        return false;
    }
    
    private void validateMobTypes() {
        if (!mythicMobsAvailable) return;
        
        List<String> invalidMobs = new ArrayList<>();
        for (String mobType : defaultMobTypes) {
            try {
                if (!MythicBukkit.inst().getMobManager().getMythicMob(mobType).isPresent()) {
                    invalidMobs.add(mobType);
                }
            } catch (Exception e) {
                invalidMobs.add(mobType);
            }
        }
        
        if (!invalidMobs.isEmpty()) {
            logger.warning("The following MythicMobs are configured but not found:");
            for (String mob : invalidMobs) {
                logger.warning("  - " + mob);
            }
            logger.warning("These mobs will be skipped. Please check your MythicMobs configuration.");
        }
    }
    
    public void reload() {
        this.defaultMobTypes.clear();
        this.defaultMobTypes.addAll(getDefaultMobTypes());
        
        this.mythicMobsAvailable = checkMythicMobsAvailability();
        this.warnedInvalidMobs.clear();
        this.warnedEmptyMobLists.clear();
        this.warnedSpawnFailures.clear();
        this.warnedInvalidSpawnModes.clear();
        
        if (defaultEnabled && mythicMobsAvailable) {
            validateMobTypes();
        }
    }

    private boolean isMythicEnabled(String pointId) {
        if (pointId != null && plugin.getZoneConfigManager() != null) {
            return plugin.getZoneConfigManager().getBoolean(pointId, "reinforcements.mythicmobs.enabled", defaultEnabled);
        }
        return defaultEnabled;
    }

    private SpawnMode getSpawnMode(String pointId) {
        String raw = null;
        if (pointId != null && plugin.getZoneConfigManager() != null) {
            raw = plugin.getZoneConfigManager().getString(pointId, "reinforcements.mythicmobs.spawn-mode", defaultSpawnMode.name());
        }
        if (raw == null || raw.trim().isEmpty()) {
            raw = defaultSpawnMode.name();
        }
        return parseSpawnMode(pointId, raw, defaultSpawnMode);
    }

    private int getMixChance(String pointId) {
        if (pointId != null && plugin.getZoneConfigManager() != null) {
            int value = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.mythicmobs.mix-chance", defaultMixChance);
            return clampMixChance(value);
        }
        return defaultMixChance;
    }

    private boolean shouldUseMythic(SpawnMode spawnMode, String pointId) {
        if (spawnMode == SpawnMode.MYTHIC_ONLY) {
            return true;
        }
        int mixChance = getMixChance(pointId);
        if (mixChance <= 0) {
            return false;
        }
        if (mixChance >= 100) {
            return true;
        }
        return random.nextInt(100) < mixChance;
    }

    private List<String> getMythicMobTypes(String pointId) {
        List<?> values = Collections.emptyList();
        if (pointId != null && plugin.getZoneConfigManager() != null) {
            values = plugin.getZoneConfigManager().getList(pointId, "reinforcements.mythicmobs.mob-types", Collections.emptyList());
        } else {
            values = defaultMobTypes;
        }

        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value != null) {
                result.add(value.toString());
            }
        }
        return result;
    }

    private List<String> getValidMythicMobTypes(String pointId) {
        if (!mythicMobsAvailable) {
            return Collections.emptyList();
        }

        List<String> configured = getMythicMobTypes(pointId);
        if (configured.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> valid = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String mobType : configured) {
            try {
                if (MythicBukkit.inst().getMobManager().getMythicMob(mobType).isPresent()) {
                    valid.add(mobType);
                } else {
                    invalid.add(mobType);
                }
            } catch (Exception e) {
                invalid.add(mobType);
            }
        }

        if (!invalid.isEmpty()) {
            logInvalidMobs(pointId, invalid);
        }

        return valid;
    }

    private void logInvalidMobs(String pointId, List<String> invalidMobs) {
        String key = pointId != null ? pointId : "default";
        Set<String> warned = warnedInvalidMobs.computeIfAbsent(key, k -> new HashSet<>());
        List<String> newlyInvalid = new ArrayList<>();
        for (String mob : invalidMobs) {
            if (warned.add(mob)) {
                newlyInvalid.add(mob);
            }
        }

        if (newlyInvalid.isEmpty()) {
            return;
        }

        if ("default".equals(key)) {
            logger.warning("The following MythicMobs are configured but not found:");
        } else {
            logger.warning("The following MythicMobs are configured but not found for zone '" + key + "':");
        }
        for (String mob : newlyInvalid) {
            logger.warning("  - " + mob);
        }
        logger.warning("These mobs will be skipped. Please check your MythicMobs configuration.");
    }

    private void logEmptyMobList(String pointId) {
        String key = pointId != null ? pointId : "default";
        if (!warnedEmptyMobLists.add(key)) {
            return;
        }

        if ("default".equals(key)) {
            logger.warning("No valid MythicMobs configured. Check reinforcements.mythicmobs.mob-types.");
        } else {
            logger.warning("No valid MythicMobs configured for zone '" + key + "'. Check reinforcements.mythicmobs.mob-types.");
        }
    }

    private void logSpawnFailure(String pointId, String mobType, String error) {
        String key = pointId != null ? pointId : "default";
        String safeMobType = (mobType == null || mobType.isEmpty()) ? "unknown" : mobType;
        Set<String> warned = warnedSpawnFailures.computeIfAbsent(key, k -> new HashSet<>());
        if (!warned.add(safeMobType)) {
            return;
        }

        String zoneSuffix = "default".equals(key) ? "" : " for zone '" + key + "'";
        String errorSuffix = (error == null || error.isEmpty()) ? "" : " (" + error + ")";
        logger.warning("Failed to spawn MythicMob '" + safeMobType + "'" + zoneSuffix + errorSuffix + ".");
    }

    private SpawnMode parseSpawnMode(String pointId, String raw, SpawnMode fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return SpawnMode.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            String key = pointId != null ? pointId : "default";
            if (warnedInvalidSpawnModes.add(key)) {
                logger.warning("Invalid MythicMobs spawn-mode '" + raw + "' for zone '" + key +
                    "'. Using " + fallback.name() + ".");
            }
            return fallback;
        }
    }

    private int clampMixChance(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 100) {
            return 100;
        }
        return value;
    }

    private List<String> getZoneOverrideMobTypes() {
        List<String> result = new ArrayList<>();
        if (plugin.getZoneConfigManager() == null) {
            return result;
        }

        for (String zoneId : plugin.getCapturePoints().keySet()) {
            FileConfiguration zoneConfig = plugin.getZoneConfigManager().getZoneConfig(zoneId);
            if (zoneConfig == null) {
                continue;
            }
            if (!zoneConfig.contains("reinforcements.mythicmobs.mob-types")) {
                continue;
            }
            List<String> zoneTypes = zoneConfig.getStringList("reinforcements.mythicmobs.mob-types");
            if (zoneTypes == null || zoneTypes.isEmpty()) {
                continue;
            }
            result.add("Zone " + zoneId + ": " + String.join(", ", zoneTypes));
        }

        return result;
    }

    private double getMobLevel(String pointId) {
        if (pointId != null && plugin.getZoneConfigManager() != null) {
            return plugin.getZoneConfigManager().getDouble(pointId, "reinforcements.mythicmobs.mob-level", defaultMobLevel);
        }
        return defaultMobLevel;
    }

    private List<String> getDefaultMobTypes() {
        List<String> mobTypes = new ArrayList<>();
        ZoneConfigManager zoneManager = plugin.getZoneConfigManager();
        List<?> defaults = zoneManager != null
            ? zoneManager.getDefaultList("reinforcements.mythicmobs.mob-types", Collections.emptyList())
            : Collections.emptyList();
        if (defaults != null) {
            for (Object value : defaults) {
                if (value != null) {
                    mobTypes.add(value.toString());
                }
            }
        }
        if (!mobTypes.isEmpty()) {
            return mobTypes;
        }
        List<String> fallback = plugin.getConfig().getStringList("reinforcements.mythicmobs.mob-types");
        return fallback == null ? new ArrayList<>() : new ArrayList<>(fallback);
    }
}

