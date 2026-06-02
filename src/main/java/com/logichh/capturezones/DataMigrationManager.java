package com.logichh.capturezones;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public final class DataMigrationManager {

    private static final Object TYPE_INCOMPATIBLE = new Object();
    private final CaptureZones plugin;
    private final Logger logger;
    private final Gson gson;

    public DataMigrationManager(CaptureZones plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public void migrateCoreFiles(File capturePointsFile) {
        migrateConfigFile();
        migrateZoneTemplateSchema();
        migrateCapturePointsFile(capturePointsFile);
        migrateShopFiles();
        migrateStatisticsFile();
    }

    public void restorePrimaryTemplatesExact() {
        restoreBundledResourceExact("config.yml", new File(plugin.getDataFolder(), "config.yml"));
        restoreBundledResourceExact("zone-template.yml", new File(plugin.getDataFolder(), "zone-template.yml"));
    }

    public void migrateZoneFiles() {
        File zoneTemplateFile = new File(plugin.getDataFolder(), "zone-template.yml");
        if (!zoneTemplateFile.exists()) {
            try {
                plugin.saveResource("zone-template.yml", false);
            } catch (IllegalArgumentException ignored) {
                logger.warning("zone-template.yml is missing in plugin resources; skipping zone schema migration.");
                return;
            }
        }

        migrateZoneTemplateFile(zoneTemplateFile);

        File zonesFolder = new File(plugin.getDataFolder(), "zones");
        if (!zonesFolder.exists()) {
            return;
        }

        FileConfiguration defaults = YamlConfiguration.loadConfiguration(zoneTemplateFile);
        if (defaults.getKeys(true).isEmpty()) {
            logger.warning("zone-template.yml is empty; skipping zone schema migration.");
            return;
        }

        File[] files = zonesFolder.listFiles((dir, name) -> name.endsWith("_config.yml"));
        if (files == null || files.length == 0) {
            return;
        }

        for (File zoneFile : files) {
            try {
                YamlConfiguration zoneConfig = YamlConfiguration.loadConfiguration(zoneFile);
                List<String> addedPaths = new ArrayList<>();
                boolean changed = mergeMissingSection(zoneConfig, defaults, "", addedPaths);
                if (!changed) {
                    continue;
                }
                backupBeforeWrite(zoneFile);
                zoneConfig.save(zoneFile);
                logger.info("Zone schema migrated (add-missing-only): " + zoneFile.getName() + " (" + addedPaths.size() + " keys added)");
            } catch (Exception e) {
                logger.warning("Failed migrating zone schema for " + zoneFile.getName() + ": " + e.getMessage());
            }
        }
    }

    private void migrateZoneTemplateSchema() {
        File zoneTemplateFile = new File(plugin.getDataFolder(), "zone-template.yml");
        if (!zoneTemplateFile.exists()) {
            try {
                plugin.saveResource("zone-template.yml", false);
            } catch (IllegalArgumentException ignored) {
                logger.warning("zone-template.yml is missing in plugin resources; skipping zone template migration.");
                return;
            }
        }
        migrateZoneTemplateFile(zoneTemplateFile);
    }

    private void migrateZoneTemplateFile(File zoneTemplateFile) {
        if (zoneTemplateFile == null || !zoneTemplateFile.exists()) {
            return;
        }

        FileConfiguration bundledDefaults = loadBundledYaml("zone-template.yml");
        if (bundledDefaults == null || bundledDefaults.getKeys(true).isEmpty()) {
            return;
        }

        try {
            YamlConfiguration activeTemplate = YamlConfiguration.loadConfiguration(zoneTemplateFile);
            List<String> addedPaths = new ArrayList<>();
            boolean changed = mergeMissingSection(activeTemplate, bundledDefaults, "", addedPaths);
            if (!changed) {
                return;
            }

            backupBeforeWrite(zoneTemplateFile);
            activeTemplate.save(zoneTemplateFile);
            logger.info("zone-template.yml schema migrated (add-missing-only): " + addedPaths.size() + " keys added.");
        } catch (Exception e) {
            logger.warning("Failed migrating zone-template.yml schema: " + e.getMessage());
        }
    }

    private void migrateConfigFile() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            if (restoreBundledResourceExact("config.yml", configFile)) {
                plugin.reloadConfig();
                logger.info("config.yml restored from bundled template.");
                return;
            }
        }
        List<String> addedPaths = new ArrayList<>();
        YamlConfiguration active = YamlConfiguration.loadConfiguration(configFile);
        FileConfiguration defaults = loadBundledYaml("config.yml");
        if (defaults == null) {
            logger.warning("Unable to load bundled config.yml for schema migration.");
            return;
        }

        boolean changed = mergeMissingSection(active, defaults, "", addedPaths);
        if (!changed) {
            return;
        }

        try {
            backupBeforeWrite(configFile);
            active.save(configFile);
            plugin.reloadConfig();
            logger.info("Config schema migrated (add-missing-only): " + addedPaths.size() + " keys added.");
        } catch (Exception e) {
            logger.warning("Failed to save migrated config.yml: " + e.getMessage());
        }
    }

    private boolean restoreBundledResourceExact(String resourceName, File targetFile) {
        if (resourceName == null || resourceName.trim().isEmpty() || targetFile == null) {
            return false;
        }

        InputStream resource = plugin.getResource(resourceName);
        if (resource == null) {
            logger.warning("Bundled resource not found: " + resourceName);
            return false;
        }

        if (targetFile.exists()) {
            backupBeforeWrite(targetFile);
        } else {
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.warning("Failed to create folder for " + targetFile.getName() + ": " + parent.getPath());
                try {
                    resource.close();
                } catch (IOException ignored) {
                }
                return false;
            }
        }

        try (InputStream in = resource) {
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Restored exact template: " + resourceName);
            return true;
        } catch (IOException e) {
            logger.warning("Failed to restore resource " + resourceName + ": " + e.getMessage());
            return false;
        }
    }

    private void migrateCapturePointsFile(File capturePointsFile) {
        File file = capturePointsFile != null
            ? capturePointsFile
            : new File(plugin.getDataFolder(), "capture_points.yml");

        try {
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                YamlConfiguration fresh = new YamlConfiguration();
                fresh.createSection("metadata.weekly-reset-epoch-days");
                fresh.createSection("points");
                fresh.save(file);
                logger.info("Created missing capture_points.yml with default schema.");
                return;
            }

            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            List<String> addedPaths = new ArrayList<>();
            boolean changed = false;

            changed |= addMissing(config, "metadata", new LinkedHashMap<>(), addedPaths);
            changed |= addMissing(config, "metadata.weekly-reset-epoch-days", new LinkedHashMap<>(), addedPaths);
            changed |= addMissing(config, "points", new LinkedHashMap<>(), addedPaths);

            String configuredDefaultOwnerType = plugin.getConfig().getString("capture-owners.default-owner-type", "town");
            CaptureOwnerType defaultOwnerType = CaptureOwnerType.fromConfigValue(configuredDefaultOwnerType, CaptureOwnerType.TOWN);
            String defaultOwnerTypeValue = defaultOwnerType.name().toLowerCase(Locale.ROOT);

            ConfigurationSection points = config.getConfigurationSection("points");
            if (points != null) {
                String defaultWorldUuid = getFirstWorldUuid();
                for (String pointId : points.getKeys(false)) {
                    String base = "points." + pointId;
                    changed |= addMissing(config, base + ".name", pointId, addedPaths);
                    if (defaultWorldUuid != null) {
                        changed |= addMissing(config, base + ".world", defaultWorldUuid, addedPaths);
                    }
                    changed |= addMissing(config, base + ".x", 0.0, addedPaths);
                    changed |= addMissing(config, base + ".y", 64.0, addedPaths);
                    changed |= addMissing(config, base + ".z", 0.0, addedPaths);
                    changed |= addMissing(config, base + ".radius", 1, addedPaths);
                    changed |= addMissing(config, base + ".shape", "circle", addedPaths);
                    int defaultCenterX = (int) Math.floor(config.getDouble(base + ".x", 0.0));
                    int defaultCenterZ = (int) Math.floor(config.getDouble(base + ".z", 0.0));
                    int defaultRadiusBlocks = Math.max(1, config.getInt(base + ".radius", 1)) * 16;
                    int defaultMinY = 0;
                    int defaultMaxY = 319;
                    if (!plugin.getServer().getWorlds().isEmpty()) {
                        World world = plugin.getServer().getWorlds().get(0);
                        if (world != null) {
                            defaultMinY = world.getMinHeight();
                            defaultMaxY = world.getMaxHeight() - 1;
                        }
                    }
                    changed |= addMissing(config, base + ".cuboid.min.x", defaultCenterX - defaultRadiusBlocks, addedPaths);
                    changed |= addMissing(config, base + ".cuboid.min.y", defaultMinY, addedPaths);
                    changed |= addMissing(config, base + ".cuboid.min.z", defaultCenterZ - defaultRadiusBlocks, addedPaths);
                    changed |= addMissing(config, base + ".cuboid.max.x", defaultCenterX + defaultRadiusBlocks, addedPaths);
                    changed |= addMissing(config, base + ".cuboid.max.y", defaultMaxY, addedPaths);
                    changed |= addMissing(config, base + ".cuboid.max.z", defaultCenterZ + defaultRadiusBlocks, addedPaths);
                    changed |= addMissing(config, base + ".reward", 0.0, addedPaths);
                    changed |= addMissing(config, base + ".controllingTown", "", addedPaths);
                    String controllingTown = config.getString(base + ".controllingTown", "");
                    changed |= addMissing(config, base + ".owner.type", defaultOwnerTypeValue, addedPaths);
                    changed |= addMissing(config, base + ".owner.id", "", addedPaths);
                    changed |= addMissing(config, base + ".owner.displayName", controllingTown, addedPaths);
                    changed |= addMissing(config, base + ".capturingOwner.type", defaultOwnerTypeValue, addedPaths);
                    changed |= addMissing(config, base + ".capturingOwner.id", "", addedPaths);
                    changed |= addMissing(config, base + ".capturingOwner.displayName", "", addedPaths);
                    changed |= addMissing(config, base + ".type", "default", addedPaths);
                    changed |= addMissing(config, base + ".showOnMap", true, addedPaths);
                    changed |= addMissing(config, base + ".lastCaptureTime", 0L, addedPaths);
                    changed |= addMissing(config, base + ".cooldowns.zone-cooldown-until", 0L, addedPaths);
                    changed |= addMissing(config, base + ".cooldowns.recapture.previous-owner.type", defaultOwnerTypeValue, addedPaths);
                    changed |= addMissing(config, base + ".cooldowns.recapture.previous-owner.id", "", addedPaths);
                    changed |= addMissing(config, base + ".cooldowns.recapture.previous-owner.displayName", "", addedPaths);
                    changed |= addMissing(config, base + ".cooldowns.recapture.previous-owner.until", 0L, addedPaths);
                    changed |= addMissing(config, base + ".cooldowns.recapture.previous-attacker.type", defaultOwnerTypeValue, addedPaths);
                    changed |= addMissing(config, base + ".cooldowns.recapture.previous-attacker.id", "", addedPaths);
                    changed |= addMissing(config, base + ".cooldowns.recapture.previous-attacker.displayName", "", addedPaths);
                    changed |= addMissing(config, base + ".cooldowns.recapture.previous-attacker.until", 0L, addedPaths);
                    changed |= addMissing(config, base + ".firstCaptureBonusAvailable", false, addedPaths);
                }
            }

            if (!changed) {
                return;
            }

            backupBeforeWrite(file);
            config.save(file);
            logger.info("capture_points.yml schema migrated (add-missing-only): " + addedPaths.size() + " keys added.");
        } catch (Exception e) {
            logger.warning("Failed migrating capture_points.yml: " + e.getMessage());
        }
    }

    private void migrateShopFiles() {
        File shopsFolder = new File(plugin.getDataFolder(), "shops");
        if (!shopsFolder.exists()) {
            return;
        }

        File[] shopFiles = shopsFolder.listFiles((dir, name) -> name.endsWith("_shop.yml"));
        if (shopFiles == null || shopFiles.length == 0) {
            return;
        }

        for (File shopFile : shopFiles) {
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(shopFile);
                List<String> addedPaths = new ArrayList<>();
                boolean changed = false;
                String shopDefaultsPath = "shops.defaults.";
                String defaultAccessMode = plugin.getConfig().getString(shopDefaultsPath + "access-mode", "ALWAYS");
                String defaultStockSystem = plugin.getConfig().getString(shopDefaultsPath + "stock-system", "INFINITE");
                String defaultLayoutMode = plugin.getConfig().getString(shopDefaultsPath + "layout-mode", "SINGLE_PAGE");
                String defaultPricingMode = plugin.getConfig().getString(shopDefaultsPath + "pricing-mode", "FIXED");
                String defaultRestockSchedule = plugin.getConfig().getString(shopDefaultsPath + "restock-schedule", "HOURLY");
                double defaultDynamicSensitivity = plugin.getConfig().getDouble("shops.dynamic-pricing.sensitivity", 0.1);
                double defaultDynamicMin = plugin.getConfig().getDouble("shops.dynamic-pricing.min-multiplier", 0.5);
                double defaultDynamicMax = plugin.getConfig().getDouble("shops.dynamic-pricing.max-multiplier", 2.0);

                changed |= addMissing(config, "enabled", false, addedPaths);
                changed |= addMissing(config, "access-mode", defaultAccessMode, addedPaths);
                changed |= addMissing(config, "stock-system", defaultStockSystem, addedPaths);
                changed |= addMissing(config, "layout-mode", defaultLayoutMode, addedPaths);
                changed |= addMissing(config, "pricing-mode", defaultPricingMode, addedPaths);
                changed |= addMissing(config, "restock-schedule", defaultRestockSchedule, addedPaths);
                changed |= addMissing(config, "last-restock", System.currentTimeMillis(), addedPaths);

                changed |= addMissing(config, "dynamic-pricing.sensitivity", defaultDynamicSensitivity, addedPaths);
                changed |= addMissing(config, "dynamic-pricing.min-multiplier", defaultDynamicMin, addedPaths);
                changed |= addMissing(config, "dynamic-pricing.max-multiplier", defaultDynamicMax, addedPaths);

                changed |= addMissing(config, "statistics.total-buys", 0, addedPaths);
                changed |= addMissing(config, "statistics.total-sells", 0, addedPaths);
                changed |= addMissing(config, "statistics.total-revenue", 0.0, addedPaths);

                ConfigurationSection items = config.getConfigurationSection("items");
                if (items != null) {
                    for (String key : items.getKeys(false)) {
                        ConfigurationSection itemSection = items.getConfigurationSection(key);
                        if (itemSection == null) {
                            continue;
                        }

                        ShopItemConfig itemDefaults;
                        try {
                            itemDefaults = ShopItemConfig.load(itemSection);
                        } catch (Exception ignored) {
                            continue;
                        }

                        String base = "items." + key;
                        changed |= addMissing(config, base + ".material", itemDefaults.getMaterial().name(), addedPaths);
                        changed |= addMissing(config, base + ".slot", itemDefaults.getSlot(), addedPaths);
                        changed |= addMissing(config, base + ".buyable", itemDefaults.isBuyable(), addedPaths);
                        changed |= addMissing(config, base + ".sellable", itemDefaults.isSellable(), addedPaths);
                        changed |= addMissing(config, base + ".buy-price", itemDefaults.getBuyPrice(), addedPaths);
                        changed |= addMissing(config, base + ".sell-price", itemDefaults.getSellPrice(), addedPaths);
                        changed |= addMissing(config, base + ".stock", itemDefaults.getStock(), addedPaths);
                        changed |= addMissing(config, base + ".max-stock", itemDefaults.getMaxStock(), addedPaths);
                        changed |= addMissing(config, base + ".category", itemDefaults.getCategory(), addedPaths);
                        changed |= addMissing(config, base + ".display-name", itemDefaults.getDisplayName(), addedPaths);
                        changed |= addMissing(config, base + ".price-multiplier", itemDefaults.getPriceMultiplier(), addedPaths);
                        changed |= addMissing(config, base + ".last-price-update", itemDefaults.getLastPriceUpdate(), addedPaths);
                        changed |= addMissing(config, base + ".transaction-count", itemDefaults.getTransactionCount(), addedPaths);
                    }
                }

                if (!changed) {
                    continue;
                }

                backupBeforeWrite(shopFile);
                config.save(shopFile);
                logger.info("Shop schema migrated (add-missing-only): " + shopFile.getName() + " (" + addedPaths.size() + " keys added)");
            } catch (Exception e) {
                logger.warning("Failed migrating shop schema for " + shopFile.getName() + ": " + e.getMessage());
            }
        }
    }

    private void migrateStatisticsFile() {
        File statsFile = new File(plugin.getDataFolder(), "statistics.json");
        JsonElement defaultTree = gson.toJsonTree(new StatisticsData());

        try {
            if (!statsFile.exists()) {
                statsFile.getParentFile().mkdirs();
                try (FileWriter writer = new FileWriter(statsFile, StandardCharsets.UTF_8)) {
                    gson.toJson(defaultTree, writer);
                }
                logger.info("Created missing statistics.json with default schema.");
                return;
            }

            String raw;
            try (Reader reader = new FileReader(statsFile, StandardCharsets.UTF_8)) {
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[2048];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    builder.append(buffer, 0, read);
                }
                raw = builder.toString().trim();
            }

            if (raw.isEmpty()) {
                backupBeforeWrite(statsFile);
                try (FileWriter writer = new FileWriter(statsFile, StandardCharsets.UTF_8)) {
                    gson.toJson(defaultTree, writer);
                }
                logger.info("statistics.json was empty and has been initialized with default schema.");
                return;
            }

            JsonElement current = JsonParser.parseString(raw);
            if (!current.isJsonObject() || !defaultTree.isJsonObject()) {
                backupBeforeWrite(statsFile);
                try (FileWriter writer = new FileWriter(statsFile, StandardCharsets.UTF_8)) {
                    gson.toJson(defaultTree, writer);
                }
                logger.warning("statistics.json had invalid root structure and was reset to default schema.");
                return;
            }

            List<String> addedPaths = new ArrayList<>();
            boolean changed = mergeMissingJson(current.getAsJsonObject(), defaultTree.getAsJsonObject(), "", addedPaths);
            if (!changed) {
                return;
            }

            backupBeforeWrite(statsFile);
            try (FileWriter writer = new FileWriter(statsFile, StandardCharsets.UTF_8)) {
                gson.toJson(current, writer);
            }
            logger.info("statistics.json schema migrated (add-missing-only): " + addedPaths.size() + " keys added.");
        } catch (Exception e) {
            logger.warning("Failed migrating statistics.json: " + e.getMessage());
        }
    }

    private boolean mergeMissingSection(ConfigurationSection target, ConfigurationSection defaults, String basePath, List<String> addedPaths) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            String currentPath = basePath.isEmpty() ? key : basePath + "." + key;
            boolean hasLocalValue = target.contains(key, true);
            if (defaults.isConfigurationSection(key)) {
                ConfigurationSection defaultChild = defaults.getConfigurationSection(key);
                if (defaultChild == null) {
                    continue;
                }

                ConfigurationSection targetChild = hasLocalValue ? target.getConfigurationSection(key) : null;
                if (targetChild == null) {
                    if (hasLocalValue) {
                        logger.warning(
                            "Schema migration replaced incompatible value at '" + currentPath +
                                "' with a section to restore missing child keys."
                        );
                        target.set(key, null);
                    }
                    targetChild = target.createSection(key);
                    changed = true;
                    addedPaths.add(currentPath);
                }
                changed |= mergeMissingSection(targetChild, defaultChild, currentPath, addedPaths);
                continue;
            }

            if (hasLocalValue && !target.isConfigurationSection(key)) {
                Object defaultValue = defaults.get(key);
                Object currentValue = target.get(key);
                Object normalized = normalizeToExpectedType(currentValue, defaultValue);
                if (normalized == TYPE_INCOMPATIBLE) {
                    logger.warning(
                        "Schema migration replaced incompatible value at '" + currentPath +
                            "' with default scalar/list value."
                    );
                    target.set(key, copyValue(defaultValue));
                    changed = true;
                    addedPaths.add(currentPath);
                    continue;
                }

                if (!Objects.equals(currentValue, normalized)) {
                    target.set(key, copyValue(normalized));
                    changed = true;
                    addedPaths.add(currentPath);
                }
                continue;
            }

            if (!hasLocalValue || target.isConfigurationSection(key)) {
                if (hasLocalValue && target.isConfigurationSection(key)) {
                    logger.warning(
                        "Schema migration replaced incompatible section at '" + currentPath +
                            "' with default scalar/list value."
                    );
                }
                target.set(key, copyValue(defaults.get(key)));
                changed = true;
                addedPaths.add(currentPath);
            }
        }
        return changed;
    }

    private Object copyValue(Object value) {
        if (value instanceof List<?>) {
            return new ArrayList<>((List<?>) value);
        }
        if (value instanceof Map<?, ?>) {
            return new LinkedHashMap<>((Map<?, ?>) value);
        }
        return value;
    }

    private Object normalizeToExpectedType(Object currentValue, Object defaultValue) {
        if (defaultValue == null) {
            return currentValue;
        }
        if (currentValue == null) {
            return TYPE_INCOMPATIBLE;
        }

        if (defaultValue instanceof List<?>) {
            return currentValue instanceof List<?> ? currentValue : TYPE_INCOMPATIBLE;
        }

        if (defaultValue instanceof Boolean) {
            if (currentValue instanceof Boolean) {
                return currentValue;
            }
            if (currentValue instanceof String) {
                String raw = ((String) currentValue).trim();
                if ("true".equalsIgnoreCase(raw)) {
                    return Boolean.TRUE;
                }
                if ("false".equalsIgnoreCase(raw)) {
                    return Boolean.FALSE;
                }
            }
            return TYPE_INCOMPATIBLE;
        }

        if (defaultValue instanceof String) {
            return currentValue instanceof String ? currentValue : String.valueOf(currentValue);
        }

        if (defaultValue instanceof Number) {
            return normalizeNumber(currentValue, (Number) defaultValue);
        }

        return defaultValue.getClass().isInstance(currentValue) ? currentValue : TYPE_INCOMPATIBLE;
    }

    private Object normalizeNumber(Object currentValue, Number defaultValue) {
        Double numericValue = coerceToDouble(currentValue);
        if (numericValue == null || !Double.isFinite(numericValue)) {
            return TYPE_INCOMPATIBLE;
        }

        if (defaultValue instanceof Integer) {
            return toIntegralNumber(numericValue, Integer.MIN_VALUE, Integer.MAX_VALUE, NumberKind.INTEGER);
        }
        if (defaultValue instanceof Long) {
            return toIntegralNumber(numericValue, Long.MIN_VALUE, Long.MAX_VALUE, NumberKind.LONG);
        }
        if (defaultValue instanceof Short) {
            return toIntegralNumber(numericValue, Short.MIN_VALUE, Short.MAX_VALUE, NumberKind.SHORT);
        }
        if (defaultValue instanceof Byte) {
            return toIntegralNumber(numericValue, Byte.MIN_VALUE, Byte.MAX_VALUE, NumberKind.BYTE);
        }
        if (defaultValue instanceof Float) {
            return (float) numericValue.doubleValue();
        }
        if (defaultValue instanceof Double) {
            return numericValue;
        }

        return currentValue instanceof Number ? currentValue : TYPE_INCOMPATIBLE;
    }

    private Double coerceToDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(raw);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Object toIntegralNumber(double value, long min, long max, NumberKind kind) {
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) > 0.0000001d) {
            return TYPE_INCOMPATIBLE;
        }
        if (rounded < min || rounded > max) {
            return TYPE_INCOMPATIBLE;
        }

        switch (kind) {
            case BYTE:
                return (byte) rounded;
            case SHORT:
                return (short) rounded;
            case INTEGER:
                return (int) rounded;
            case LONG:
            default:
                return rounded;
        }
    }

    private enum NumberKind {
        BYTE,
        SHORT,
        INTEGER,
        LONG
    }

    private boolean addMissing(FileConfiguration config, String path, Object value, List<String> addedPaths) {
        if (config.contains(path, true)) {
            return false;
        }
        config.set(path, value);
        addedPaths.add(path);
        return true;
    }

    private boolean mergeMissingJson(JsonObject target, JsonObject defaults, String basePath, List<String> addedPaths) {
        boolean changed = false;
        for (Map.Entry<String, JsonElement> entry : defaults.entrySet()) {
            String key = entry.getKey();
            String currentPath = basePath.isEmpty() ? key : basePath + "." + key;

            if (!target.has(key)) {
                target.add(key, entry.getValue().deepCopy());
                changed = true;
                addedPaths.add(currentPath);
                continue;
            }

            JsonElement targetValue = target.get(key);
            JsonElement defaultValue = entry.getValue();
            if (targetValue != null && targetValue.isJsonObject() && defaultValue != null && defaultValue.isJsonObject()) {
                changed |= mergeMissingJson(targetValue.getAsJsonObject(), defaultValue.getAsJsonObject(), currentPath, addedPaths);
            }
        }
        return changed;
    }

    private FileConfiguration loadBundledYaml(String resourceName) {
        InputStream resource = plugin.getResource(resourceName);
        if (resource == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            logger.warning("Failed to read bundled resource " + resourceName + ": " + e.getMessage());
            return null;
        }
    }

    private String getFirstWorldUuid() {
        List<World> worlds = plugin.getServer().getWorlds();
        if (worlds == null || worlds.isEmpty()) {
            return null;
        }
        return worlds.get(0).getUID().toString();
    }

    private void backupBeforeWrite(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            return;
        }

        String version = plugin.getDescription().getVersion();
        File versionBackupRoot = new File(plugin.getDataFolder(), "backups" + File.separator + version);
        Path dataPath = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path sourcePath = sourceFile.toPath().toAbsolutePath().normalize();

        Path relative;
        try {
            relative = dataPath.relativize(sourcePath);
        } catch (IllegalArgumentException ex) {
            relative = Path.of(sourceFile.getName());
        }

        File backupFile = new File(versionBackupRoot, relative.toString() + ".bak");
        if (backupFile.exists()) {
            return;
        }
        if (!backupFile.getParentFile().exists() && !backupFile.getParentFile().mkdirs()) {
            logger.warning("Failed to create backup folder: " + backupFile.getParent());
            return;
        }

        try {
            Files.copy(sourcePath, backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Migration backup created: " + backupFile.getPath());
        } catch (IOException e) {
            logger.warning("Failed to create migration backup for " + sourceFile.getName() + ": " + e.getMessage());
        }
    }
}

