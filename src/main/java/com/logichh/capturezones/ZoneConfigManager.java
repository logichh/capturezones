package com.logichh.capturezones;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.util.*;
import java.util.logging.Logger;

public class ZoneConfigManager {
    
    private final CaptureZones plugin;
    private final Logger logger;
    private final File legacyZoneConfigsFolder;
    private final File zoneConfigsFolder;
    private final File zoneTemplateFile;
    private final Map<String, FileConfiguration> zoneConfigs;
    private final Set<String> warnedSettingIssues;
    private FileConfiguration zoneDefaults;
    private static final String TEMPLATE_BANNER_SEPARATOR = "# ==============================================";

    private enum SettingSource {
        ZONE_OVERRIDE,
        GLOBAL_DEFAULT,
        LEGACY_GLOBAL_DEFAULT,
        CALLER_DEFAULT
    }

    private static final class ResolvedSetting {
        private final Object value;
        private final SettingSource source;
        private final String sourcePath;

        private ResolvedSetting(Object value, SettingSource source, String sourcePath) {
            this.value = value;
            this.source = source;
            this.sourcePath = sourcePath;
        }
    }

    private static final class IntRange {
        private final int min;
        private final int max;

        private IntRange(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }

    private static final class LongRange {
        private final long min;
        private final long max;

        private LongRange(long min, long max) {
            this.min = min;
            this.max = max;
        }
    }

    private static final class DoubleRange {
        private final double min;
        private final double max;

        private DoubleRange(double min, double max) {
            this.min = min;
            this.max = max;
        }
    }
    
    public ZoneConfigManager(CaptureZones plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.legacyZoneConfigsFolder = plugin.getDataFolder();
        this.zoneConfigsFolder = new File(plugin.getDataFolder(), "zones");
        this.zoneTemplateFile = new File(plugin.getDataFolder(), "zone-template.yml");
        if (!zoneConfigsFolder.exists() && !zoneConfigsFolder.mkdirs()) {
            logger.warning("Failed to create zones config folder: " + zoneConfigsFolder.getAbsolutePath());
        }
        this.zoneConfigs = new HashMap<>();
        this.warnedSettingIssues = new HashSet<>();
        loadZoneDefaults();
    }
    
    public void loadAllZoneConfigs() {
        zoneConfigs.clear();
        warnedSettingIssues.clear();

        for (String zoneId : plugin.getCapturePoints().keySet()) {
            loadZoneConfig(zoneId);
        }
        
        logger.info("Loaded " + zoneConfigs.size() + " zone configurations.");
    }

    public void reloadDefaults() {
        warnedSettingIssues.clear();
        loadZoneDefaults();
    }
    
    public FileConfiguration loadZoneConfig(String zoneId) {
        File zoneConfigFile = getZoneConfigFile(zoneId);
        
        if (!zoneConfigFile.exists()) {
            if (migrateLegacyZoneConfig(zoneId)) {
                zoneConfigFile = getZoneConfigFile(zoneId);
            } else {
                logger.info("Zone config not found for '" + zoneId + "', generating from defaults...");
                generateZoneConfig(zoneId);
            }
        }
        
        try {
            FileConfiguration zoneConfig = YamlConfiguration.loadConfiguration(zoneConfigFile);
            zoneConfigs.put(zoneId, zoneConfig);
            
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                logger.info("Loaded config for zone: " + zoneId);
            }
            
            return zoneConfig;
        } catch (Exception e) {
            logger.severe("Failed to load config for zone '" + zoneId + "': " + e.getMessage());
            return null;
        }
    }
    
    public boolean generateZoneConfig(String zoneId) {
        ensureZoneConfigsFolder();
        File zoneConfigFile = getZoneConfigFile(zoneId);
        
        try {
            FileConfiguration defaults = getZoneDefaults();
            if (zoneTemplateFile.exists()) {
                try {
                    copyZoneTemplateForZoneConfig(zoneConfigFile);
                    FileConfiguration zoneConfig = YamlConfiguration.loadConfiguration(zoneConfigFile);
                    zoneConfigs.put(zoneId, zoneConfig);
                    logger.info("Generated config file: " + zoneId + "_config.yml");
                    return true;
                } catch (IOException e) {
                    logger.warning("Failed to copy zone template for '" + zoneId + "': " + e.getMessage());
                }
            }

            if (defaults == null) {
                logger.severe("Zone template defaults not found! Cannot generate zone config.");
                return false;
            }

            FileConfiguration zoneConfig = new YamlConfiguration();
            // Deep copy all settings (comments are not preserved in this fallback path)
            copySection(defaults, zoneConfig);
            zoneConfig.save(zoneConfigFile);
            zoneConfigs.put(zoneId, zoneConfig);

            logger.info("Generated config file: " + zoneId + "_config.yml");
            return true;
        } catch (IOException e) {
            logger.severe("Failed to generate config for zone '" + zoneId + "': " + e.getMessage());
            return false;
        }
    }

    private void copyZoneTemplateForZoneConfig(File zoneConfigFile) throws IOException {
        List<String> lines = Files.readAllLines(zoneTemplateFile.toPath(), StandardCharsets.UTF_8);
        int startIndex = findTemplateContentStartIndex(lines);

        if (startIndex <= 0) {
            Files.copy(zoneTemplateFile.toPath(), zoneConfigFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < lines.size(); i++) {
            builder.append(lines.get(i));
            builder.append(System.lineSeparator());
        }
        Files.writeString(zoneConfigFile.toPath(), builder.toString(), StandardCharsets.UTF_8);
    }

    private int findTemplateContentStartIndex(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return 0;
        }

        int firstNonBlank = 0;
        while (firstNonBlank < lines.size() && lines.get(firstNonBlank).trim().isEmpty()) {
            firstNonBlank++;
        }
        if (firstNonBlank >= lines.size()) {
            return 0;
        }
        if (!TEMPLATE_BANNER_SEPARATOR.equals(lines.get(firstNonBlank).trim())) {
            return 0;
        }

        int secondSeparator = -1;
        for (int i = firstNonBlank + 1; i < lines.size(); i++) {
            if (TEMPLATE_BANNER_SEPARATOR.equals(lines.get(i).trim())) {
                secondSeparator = i;
                break;
            }
        }
        if (secondSeparator < 0) {
            return 0;
        }

        int contentStart = secondSeparator + 1;
        while (contentStart < lines.size() && lines.get(contentStart).trim().isEmpty()) {
            contentStart++;
        }
        return Math.max(contentStart, 0);
    }
    
    private void copySection(ConfigurationSection source, ConfigurationSection target) {
        for (String key : source.getKeys(false)) {
            if (source.isConfigurationSection(key)) {
                ConfigurationSection subSection = target.createSection(key);
                copySection(source.getConfigurationSection(key), subSection);
            } else {
                target.set(key, source.get(key));
            }
        }
    }
    
    public FileConfiguration getZoneConfig(String zoneId) {
        FileConfiguration config = getRawZoneConfig(zoneId);
        if (config != null) {
            return config;
        }
        if (zoneDefaults != null) {
            warnOnce("missing-zone-config:" + zoneId, "Using zone defaults for zone: " + zoneId);
            return zoneDefaults;
        }
        warnOnce("missing-zone-defaults:" + zoneId, "Zone defaults not available for zone: " + zoneId);
        return plugin.getConfig();
    }
    
    public Object getZoneSetting(String zoneId, String path, Object defaultValue) {
        return resolveSetting(zoneId, path, defaultValue).value;
    }

    public String getString(String zoneId, String path, String defaultValue) {
        for (ResolvedSetting candidate : collectSettingCandidates(zoneId, path, defaultValue)) {
            String value = toStringValue(candidate.value);
            if (value == null) {
                warnRejectedValue(zoneId, path, candidate, "expected text value");
                continue;
            }
            return normalizeStringValue(zoneId, path, value, candidate);
        }
        return defaultValue;
    }
    
    public int getInt(String zoneId, String path, int defaultValue) {
        for (ResolvedSetting candidate : collectSettingCandidates(zoneId, path, defaultValue)) {
            Integer value = toIntValue(candidate.value);
            if (value == null) {
                warnRejectedValue(zoneId, path, candidate, "expected integer value");
                continue;
            }
            return normalizeIntValue(zoneId, path, value, candidate);
        }
        return defaultValue;
    }
    
    public double getDouble(String zoneId, String path, double defaultValue) {
        for (ResolvedSetting candidate : collectSettingCandidates(zoneId, path, defaultValue)) {
            Double value = toDoubleValue(candidate.value);
            if (value == null) {
                warnRejectedValue(zoneId, path, candidate, "expected numeric value");
                continue;
            }
            return normalizeDoubleValue(zoneId, path, value, candidate);
        }
        return defaultValue;
    }

    public long getLong(String zoneId, String path, long defaultValue) {
        for (ResolvedSetting candidate : collectSettingCandidates(zoneId, path, defaultValue)) {
            Long value = toLongValue(candidate.value);
            if (value == null) {
                warnRejectedValue(zoneId, path, candidate, "expected long value");
                continue;
            }
            return normalizeLongValue(zoneId, path, value, candidate);
        }
        return defaultValue;
    }
    
    public boolean getBoolean(String zoneId, String path, boolean defaultValue) {
        for (ResolvedSetting candidate : collectSettingCandidates(zoneId, path, defaultValue)) {
            Boolean value = toBooleanValue(candidate.value);
            if (value == null) {
                warnRejectedValue(zoneId, path, candidate, "expected boolean value");
                continue;
            }
            return value;
        }
        return defaultValue;
    }
    
    public List<?> getList(String zoneId, String path, List<?> defaultValue) {
        for (ResolvedSetting candidate : collectSettingCandidates(zoneId, path, defaultValue)) {
            if (candidate.value instanceof List<?>) {
                return (List<?>) candidate.value;
            }
            warnRejectedValue(zoneId, path, candidate, "expected list value");
        }
        return defaultValue;
    }
    
    public ConfigurationSection getSection(String zoneId, String path) {
        for (ResolvedSetting candidate : collectSettingCandidates(zoneId, path, null)) {
            if (candidate.value instanceof ConfigurationSection) {
                return (ConfigurationSection) candidate.value;
            }
            if (candidate.source == SettingSource.CALLER_DEFAULT) {
                continue;
            }
            if (candidate.value != null) {
                warnRejectedValue(zoneId, path, candidate, "expected configuration section");
            }
        }
        return null;
    }
    
    public boolean setZoneSetting(String zoneId, String path, Object value) {
        FileConfiguration zoneConfig = getRawZoneConfig(zoneId);
        if (zoneConfig == null) {
            return false;
        }

        Object normalized = normalizeValueForWrite(zoneId, path, value);
        if (normalized == InvalidValue.INSTANCE) {
            return false;
        }

        zoneConfig.set(path, normalized);
        return saveZoneConfig(zoneId);
    }

    private enum InvalidValue {
        INSTANCE
    }

    private FileConfiguration getRawZoneConfig(String zoneId) {
        if (zoneId == null || zoneId.trim().isEmpty()) {
            return null;
        }

        FileConfiguration config = zoneConfigs.get(zoneId);
        if (config != null) {
            return config;
        }
        return loadZoneConfig(zoneId);
    }

    private List<ResolvedSetting> collectSettingCandidates(String zoneId, String path, Object defaultValue) {
        List<ResolvedSetting> candidates = new ArrayList<>(6);
        String normalizedPath = path == null ? "" : path.trim();

        FileConfiguration zoneConfig = getRawZoneConfig(zoneId);
        if (zoneConfig != null && zoneConfig.isSet(normalizedPath)) {
            candidates.add(new ResolvedSetting(
                zoneConfig.get(normalizedPath),
                SettingSource.ZONE_OVERRIDE,
                "zones/" + zoneId + "_config.yml:" + normalizedPath
            ));
        }

        if (zoneDefaults != null && zoneDefaults.isSet(normalizedPath)) {
            candidates.add(new ResolvedSetting(
                zoneDefaults.get(normalizedPath),
                SettingSource.GLOBAL_DEFAULT,
                "zone-template.yml:" + normalizedPath
            ));
        }

        String legacyPath = "zone-defaults." + normalizedPath;
        if (plugin.getConfig().isSet(legacyPath)) {
            candidates.add(new ResolvedSetting(
                plugin.getConfig().get(legacyPath),
                SettingSource.LEGACY_GLOBAL_DEFAULT,
                "config.yml:" + legacyPath
            ));
        }

        candidates.add(new ResolvedSetting(defaultValue, SettingSource.CALLER_DEFAULT, "<caller-default>"));
        return candidates;
    }

    private ResolvedSetting resolveSetting(String zoneId, String path, Object defaultValue) {
        List<ResolvedSetting> candidates = collectSettingCandidates(zoneId, path, defaultValue);
        if (candidates.isEmpty()) {
            return new ResolvedSetting(defaultValue, SettingSource.CALLER_DEFAULT, "<caller-default>");
        }
        return candidates.get(0);
    }

    private Object normalizeValueForWrite(String zoneId, String path, Object value) {
        if (value == null) {
            return null;
        }

        ResolvedSetting writeCandidate = new ResolvedSetting(value, SettingSource.ZONE_OVERRIDE, "set:" + path);

        if (isEnumStringPath(path)) {
            String asString = toStringValue(value);
            if (asString == null) {
                warnRejectedValue(zoneId, path, writeCandidate, "expected text enum value");
                return InvalidValue.INSTANCE;
            }
            String normalizedEnum = normalizeStringValue(zoneId, path, asString, writeCandidate);
            if (normalizedEnum == null) {
                return InvalidValue.INSTANCE;
            }
            return normalizedEnum;
        }

        if (value instanceof Boolean) {
            return value;
        }

        if (value instanceof Integer) {
            return normalizeIntValue(zoneId, path, (Integer) value, writeCandidate);
        }
        if (value instanceof Long) {
            return normalizeLongValue(zoneId, path, (Long) value, writeCandidate);
        }
        if (value instanceof Double) {
            return normalizeDoubleValue(zoneId, path, (Double) value, writeCandidate);
        }
        if (value instanceof Float) {
            return normalizeDoubleValue(zoneId, path, ((Float) value).doubleValue(), writeCandidate);
        }
        if (value instanceof List<?>) {
            return value;
        }

        String asString = value.toString();
        if (asString == null) {
            return InvalidValue.INSTANCE;
        }

        if (toBooleanValue(asString) != null) {
            return toBooleanValue(asString);
        }

        Integer asInt = toIntValue(asString);
        if (asInt != null) {
            return normalizeIntValue(zoneId, path, asInt, writeCandidate);
        }

        Double asDouble = toDoubleValue(asString);
        if (asDouble != null) {
            return normalizeDoubleValue(zoneId, path, asDouble, writeCandidate);
        }

        String normalized = normalizeStringValue(zoneId, path, asString, writeCandidate);
        if (normalized == null) {
            warnOnce(
                "reject-write:" + zoneId + ":" + path + ":" + value,
                "Rejected zone config write for '" + zoneId + "." + path + "' with value '" + value + "'."
            );
            return InvalidValue.INSTANCE;
        }
        return normalized;
    }

    private boolean isEnumStringPath(String path) {
        return "rewards.reward-type".equals(path)
            || "rewards.hourly-mode".equals(path)
            || "rewards.money.payout-mode".equals(path)
            || "rewards.item-rewards.inventory-full-behavior".equals(path)
            || "rewards.permission-modifiers.conflict-policy".equals(path)
            || "reinforcements.mythicmobs.spawn-mode".equals(path)
            || "reinforcements.targeting.mode".equals(path)
            || "capture-conditions.contested.progress-policy".equals(path)
            || "weekly-reset.day".equals(path)
            || (path.startsWith("rewards.command-rewards.") && path.endsWith(".recipient"))
            || (path.startsWith("rewards.command-rewards.") && path.endsWith(".execution"));
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private Integer toIntValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.isEmpty()) {
                return null;
            }
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long toLongValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            String raw = ((String) value).trim();
            if (raw.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double toDoubleValue(Object value) {
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

    private Boolean toBooleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (!(value instanceof String)) {
            return null;
        }
        String raw = ((String) value).trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            return null;
        }
        if ("true".equals(raw) || "yes".equals(raw) || "on".equals(raw) || "1".equals(raw)) {
            return true;
        }
        if ("false".equals(raw) || "no".equals(raw) || "off".equals(raw) || "0".equals(raw)) {
            return false;
        }
        return null;
    }

    private int normalizeIntValue(String zoneId, String path, int value, ResolvedSetting candidate) {
        IntRange range = getIntRange(path);
        if (range == null) {
            return value;
        }
        int clamped = Math.max(range.min, Math.min(range.max, value));
        if (clamped != value) {
            warnOnce(
                "clamp-int:" + zoneId + ":" + path + ":" + candidate.sourcePath + ":" + value,
                "Clamped zone setting '" + path + "' for zone '" + zoneId + "' from " + value + " to " + clamped +
                    " (source: " + candidate.sourcePath + ")."
            );
        }
        return clamped;
    }

    private long normalizeLongValue(String zoneId, String path, long value, ResolvedSetting candidate) {
        LongRange range = getLongRange(path);
        if (range == null) {
            return value;
        }
        long clamped = Math.max(range.min, Math.min(range.max, value));
        if (clamped != value) {
            warnOnce(
                "clamp-long:" + zoneId + ":" + path + ":" + candidate.sourcePath + ":" + value,
                "Clamped zone setting '" + path + "' for zone '" + zoneId + "' from " + value + " to " + clamped +
                    " (source: " + candidate.sourcePath + ")."
            );
        }
        return clamped;
    }

    private double normalizeDoubleValue(String zoneId, String path, double value, ResolvedSetting candidate) {
        DoubleRange range = getDoubleRange(path);
        if (range == null) {
            return value;
        }
        double clamped = Math.max(range.min, Math.min(range.max, value));
        if (Double.compare(clamped, value) != 0) {
            warnOnce(
                "clamp-double:" + zoneId + ":" + path + ":" + candidate.sourcePath + ":" + value,
                "Clamped zone setting '" + path + "' for zone '" + zoneId + "' from " + value + " to " + clamped +
                    " (source: " + candidate.sourcePath + ")."
            );
        }
        return clamped;
    }

    private String normalizeStringValue(String zoneId, String path, String value, ResolvedSetting candidate) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null) {
            return null;
        }

        if ("rewards.reward-type".equals(path)) {
            if ("daily".equalsIgnoreCase(normalized) || "hourly".equalsIgnoreCase(normalized)) {
                return normalized.toLowerCase(Locale.ROOT);
            }
            warnRejectedValue(zoneId, path, candidate, "expected one of: daily, hourly");
            return null;
        }

        if ("rewards.hourly-mode".equals(path)) {
            if ("static".equalsIgnoreCase(normalized) || "dynamic".equalsIgnoreCase(normalized)) {
                return normalized.toUpperCase(Locale.ROOT);
            }
            warnRejectedValue(zoneId, path, candidate, "expected one of: STATIC, DYNAMIC");
            return null;
        }

        if ("rewards.money.payout-mode".equals(path)) {
            String normalizedMode = normalized
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
            if ("OWNER_ACCOUNT".equals(normalizedMode)
                || "SPLIT_ONLINE".equals(normalizedMode)
                || "EACH_ONLINE".equals(normalizedMode)
                || "EACH_RESIDENT".equals(normalizedMode)) {
                return normalizedMode;
            }
            warnRejectedValue(zoneId, path, candidate, "expected one of: OWNER_ACCOUNT, SPLIT_ONLINE, EACH_ONLINE, EACH_RESIDENT");
            return null;
        }

        if ("rewards.item-rewards.inventory-full-behavior".equals(path)) {
            if ("drop".equalsIgnoreCase(normalized) || "cancel".equalsIgnoreCase(normalized)) {
                return normalized.toUpperCase(Locale.ROOT);
            }
            warnRejectedValue(zoneId, path, candidate, "expected one of: DROP, CANCEL");
            return null;
        }

        if ("rewards.permission-modifiers.conflict-policy".equals(path)) {
            String normalizedPolicy = normalized
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
            if ("HIGHEST".equals(normalizedPolicy)
                || "ADDITIVE".equals(normalizedPolicy)
                || "FIRST_MATCH".equals(normalizedPolicy)) {
                return normalizedPolicy;
            }
            warnRejectedValue(zoneId, path, candidate, "expected one of: HIGHEST, ADDITIVE, FIRST_MATCH");
            return null;
        }

        if (path.startsWith("rewards.command-rewards.") && path.endsWith(".recipient")) {
            String mode = normalized.replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
            if ("INITIATOR".equals(mode)
                || "PARTICIPANTS".equals(mode)
                || "ONLINE_OWNER".equals(mode)
                || "MVP".equals(mode)
                || "RANDOM_PARTICIPANT".equals(mode)) {
                return mode;
            }
            warnRejectedValue(
                zoneId,
                path,
                candidate,
                "expected one of: INITIATOR, PARTICIPANTS, ONLINE_OWNER, MVP, RANDOM_PARTICIPANT"
            );
            return null;
        }

        if (path.startsWith("rewards.command-rewards.") && path.endsWith(".execution")) {
            if ("console".equalsIgnoreCase(normalized) || "player".equalsIgnoreCase(normalized)) {
                return normalized.toUpperCase(Locale.ROOT);
            }
            warnRejectedValue(zoneId, path, candidate, "expected one of: CONSOLE, PLAYER");
            return null;
        }

        if ("reinforcements.mythicmobs.spawn-mode".equals(path)) {
            if ("mixed".equalsIgnoreCase(normalized) || "mythic_only".equalsIgnoreCase(normalized)) {
                return normalized.toUpperCase(Locale.ROOT);
            }
            warnRejectedValue(zoneId, path, candidate, "expected one of: MIXED, MYTHIC_ONLY");
            return null;
        }

        if ("reinforcements.targeting.mode".equals(path)) {
            String normalizedMode = normalized
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
            if ("CAPTURING_OWNER_ONLY".equals(normalizedMode)
                || "OPPOSING_OWNER_ONLY".equals(normalizedMode)
                || "ANY_PLAYER_IN_ZONE".equals(normalizedMode)) {
                return normalizedMode;
            }
            warnRejectedValue(
                zoneId,
                path,
                candidate,
                "expected one of: CAPTURING_OWNER_ONLY, OPPOSING_OWNER_ONLY, ANY_PLAYER_IN_ZONE"
            );
            return null;
        }

        if ("capture-conditions.contested.progress-policy".equals(path)) {
            if ("pause".equalsIgnoreCase(normalized)
                || "decay".equalsIgnoreCase(normalized)
                || "rollback".equalsIgnoreCase(normalized)) {
                return normalized.toUpperCase(Locale.ROOT);
            }
            warnRejectedValue(zoneId, path, candidate, "expected one of: PAUSE, DECAY, ROLLBACK");
            return null;
        }

        if ("weekly-reset.day".equals(path)) {
            try {
                DayOfWeek.valueOf(normalized.toUpperCase(Locale.ROOT));
                return normalized.toUpperCase(Locale.ROOT);
            } catch (Exception ignored) {
                warnRejectedValue(zoneId, path, candidate, "expected day name MONDAY..SUNDAY");
                return null;
            }
        }

        return normalized;
    }

    private IntRange getIntRange(String path) {
        if (path == null) {
            return null;
        }

        if (path.startsWith("rewards.command-rewards.") && path.endsWith(".conditions.min-participation-seconds")) {
            return new IntRange(0, 604800);
        }
        if (path.startsWith("rewards.command-rewards.") && path.endsWith(".conditions.min-kills")) {
            return new IntRange(0, 10000);
        }
        if (path.startsWith("rewards.command-rewards.") && path.endsWith(".conditions.min-player-count")) {
            return new IntRange(1, 1000);
        }

        switch (path) {
            case "capture.preparation.duration":
            case "capture.capture.duration":
                return new IntRange(0, 1440);
            case "capture-conditions.allowed-hours.start":
            case "capture-conditions.allowed-hours.end":
                return new IntRange(0, 24);
            case "capture-conditions.auto-capture.entry-debounce-seconds":
                return new IntRange(0, 3600);
            case "capture-conditions.speed-modifiers.owner-player-scaling.max-extra-players":
                return new IntRange(0, 100);
            case "hologram.instances":
                return new IntRange(1, 4);
            case "hologram.max-lines":
                return new IntRange(1, 20);
            case "rewards.hourly-interval":
            case "rewards.money.max-recipients":
                return new IntRange(1, 86400);
            case "rewards.money.batch-size":
                return new IntRange(1, 250);
            case "rewards.potion-effects.duration-ticks":
                return new IntRange(40, 72000);
            case "reinforcements.wave-interval":
                return new IntRange(1, 3600);
            case "reinforcements.mobs-per-wave":
                return new IntRange(0, 50);
            case "reinforcements.wave-phase-increase":
                return new IntRange(0, 50);
            case "reinforcements.max-mobs-per-wave":
                return new IntRange(1, 500);
            case "reinforcements.max-mobs-per-point":
                return new IntRange(1, 500);
            case "reinforcements.stop-spawning-under-seconds":
                return new IntRange(0, 86400);
            case "reinforcements.spawn-rate.max-per-tick":
                return new IntRange(1, 50);
            case "reinforcements.targeting.search-extra-chunks":
                return new IntRange(0, 16);
            case "reinforcements.targeting.retarget-interval-ticks":
                return new IntRange(1, 200);
            case "protection.buffer-zone.size":
                return new IntRange(0, 8);
            case "reinforcements.mythicmobs.mix-chance":
                return new IntRange(0, 100);
            case "capture-conditions.contested.decay-seconds":
            case "capture-conditions.contested.rollback-seconds":
            case "capture-conditions.leave-grace.duration-seconds":
                return new IntRange(0, 3600);
            default:
                if (path.startsWith("reinforcements.timer-reduction.") && (path.endsWith(".min") || path.endsWith(".max"))) {
                    return new IntRange(0, 3600);
                }
                return null;
        }
    }

    private LongRange getLongRange(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("rewards.command-rewards.") && path.endsWith("-seconds")) {
            return new LongRange(0L, 31_536_000L);
        }
        if ("capture-conditions.capture-cooldown.duration-ms".equals(path)
            || "capture-conditions.capture-cooldown.anti-instant-recapture.duration-ms".equals(path)) {
            return new LongRange(0L, 604800000L);
        }
        if ("capture-conditions.progress-display.actionbar.contested.interval-ms".equals(path)
            || "capture-conditions.progress-display.actionbar.grace.interval-ms".equals(path)) {
            return new LongRange(100L, 60000L);
        }
        return null;
    }

    private DoubleRange getDoubleRange(String path) {
        if (path == null) {
            return null;
        }
        if (path.startsWith("rewards.command-rewards.") && path.endsWith(".chance")) {
            return new DoubleRange(0.0, 100.0);
        }
        switch (path) {
            case "rewards.base-reward":
            case "rewards.hourly-dynamic.min":
            case "rewards.hourly-dynamic.max":
            case "weekly-reset.first-capture-bonus.amount-range.min":
            case "weekly-reset.first-capture-bonus.amount-range.max":
                return new DoubleRange(0.0, 1_000_000_000.0);
            case "reinforcements.mythicmobs.mob-level":
                return new DoubleRange(0.0, 1000.0);
            case "capture-conditions.speed-modifiers.flat-multiplier":
            case "capture-conditions.speed-modifiers.min-multiplier":
            case "capture-conditions.speed-modifiers.max-multiplier":
                return new DoubleRange(0.0, 20.0);
            case "capture-conditions.speed-modifiers.owner-player-scaling.per-extra-player":
                return new DoubleRange(0.0, 10.0);
            case "reinforcements.spawn-location.min-distance":
            case "reinforcements.spawn-location.max-distance":
                return new DoubleRange(0.0, 256.0);
            case "reinforcements.spawn-location.surface-offset":
                return new DoubleRange(-16.0, 64.0);
            case "hologram.height-offset":
                return new DoubleRange(-64.0, 320.0);
            case "hologram.horizontal-offset":
                return new DoubleRange(0.0, 16.0);
            case "hologram.line-spacing":
                return new DoubleRange(0.05, 2.0);
            case "hologram.visibility-range":
                return new DoubleRange(8.0, 256.0);
            case "dynmap.fillOpacity":
            case "dynmap.strokeOpacity":
            case "bluemap.line-opacity":
            case "bluemap.fill-opacity":
                return new DoubleRange(0.0, 1.0);
            default:
                return null;
        }
    }

    private void warnRejectedValue(String zoneId, String path, ResolvedSetting candidate, String reason) {
        if (candidate.source == SettingSource.CALLER_DEFAULT) {
            return;
        }
        String safeZone = zoneId == null ? "<unknown>" : zoneId;
        String key = "reject:" + safeZone + ":" + path + ":" + candidate.sourcePath + ":" + reason;
        warnOnce(
            key,
            "Rejected invalid zone setting '" + path + "' for zone '" + safeZone + "' from " + candidate.sourcePath +
                " (" + reason + ")."
        );
    }

    private void warnOnce(String key, String message) {
        if (warnedSettingIssues.add(key)) {
            logger.warning(message);
        }
    }
    
    public boolean saveZoneConfig(String zoneId) {
        FileConfiguration zoneConfig = zoneConfigs.get(zoneId);
        if (zoneConfig == null) {
            return false;
        }
        
        ensureZoneConfigsFolder();
        File zoneConfigFile = getZoneConfigFile(zoneId);
        
        try {
            zoneConfig.save(zoneConfigFile);
            
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                logger.info("Saved config for zone: " + zoneId);
            }
            
            return true;
        } catch (IOException e) {
            logger.severe("Failed to save config for zone '" + zoneId + "': " + e.getMessage());
            return false;
        }
    }
    
    public boolean deleteZoneConfig(String zoneId, boolean createBackup) {
        File zoneConfigFile = getZoneConfigFile(zoneId);
        
        if (!zoneConfigFile.exists()) {
            return true; // Already doesn't exist
        }
        
        try {
            if (createBackup) {
                File backupFile = new File(zoneConfigsFolder, zoneId + "_config.yml.backup");
                if (backupFile.exists()) {
                    backupFile.delete();
                }
                zoneConfigFile.renameTo(backupFile);
                logger.info("Backed up config to: " + backupFile.getName());
            } else {
                zoneConfigFile.delete();
                logger.info("Deleted config file: " + zoneConfigFile.getName());
            }
            
            zoneConfigs.remove(zoneId);
            return true;
            
        } catch (Exception e) {
            logger.severe("Failed to delete config for zone '" + zoneId + "': " + e.getMessage());
            return false;
        }
    }
    
    public boolean reloadZoneConfig(String zoneId) {
        zoneConfigs.remove(zoneId);
        FileConfiguration config = loadZoneConfig(zoneId);
        return config != null;
    }
    
    public Set<String> getLoadedZones() {
        return new HashSet<>(zoneConfigs.keySet());
    }

    public boolean hasZoneConfig(String zoneId) {
        if (getZoneConfigFile(zoneId).exists()) {
            return true;
        }
        return getLegacyZoneConfigFile(zoneId).exists();
    }
    
    public void migrateExistingZones() {
        logger.info("Checking for zones without individual configs...");
        
        int moved = 0;
        int generated = 0;
        for (String zoneId : plugin.getCapturePoints().keySet()) {
            if (migrateLegacyZoneConfig(zoneId)) {
                moved++;
                continue;
            }
            if (!getZoneConfigFile(zoneId).exists()) {
                logger.info("Generating config for zone: " + zoneId);
                if (generateZoneConfig(zoneId)) {
                    generated++;
                }
            }
        }
        
        if (moved > 0) {
            logger.info("Moved " + moved + " legacy zone configs into the zones folder.");
        }
        if (generated > 0) {
            logger.info("Generated " + generated + " zone configs from defaults.");
        }
        if (moved == 0 && generated == 0) {
            logger.info("All zones already have individual configs.");
        }
    }
    
    public Object getZoneDefault(String path) {
        if (zoneDefaults != null && zoneDefaults.contains(path)) {
            return zoneDefaults.get(path);
        }
        String legacyPath = "zone-defaults." + path;
        return plugin.getConfig().get(legacyPath);
    }

    public boolean getDefaultBoolean(String path, boolean defaultValue) {
        Object value = getZoneDefault(path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    public int getDefaultInt(String path, int defaultValue) {
        Object value = getZoneDefault(path);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    public long getDefaultLong(String path, long defaultValue) {
        Object value = getZoneDefault(path);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    public double getDefaultDouble(String path, double defaultValue) {
        Object value = getZoneDefault(path);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    public List<?> getDefaultList(String path, List<?> defaultValue) {
        Object value = getZoneDefault(path);
        if (value instanceof List) {
            return (List<?>) value;
        }
        return defaultValue;
    }

    public List<String> getAvailableSettingPaths(String zoneId) {
        Set<String> paths = new LinkedHashSet<>();
        collectLeafPaths(zoneDefaults, paths);
        collectLeafPaths(getRawZoneConfig(zoneId), paths);

        ConfigurationSection legacyDefaults = plugin.getConfig().getConfigurationSection("zone-defaults");
        if (legacyDefaults != null) {
            collectLeafPaths(legacyDefaults, paths);
        }

        List<String> sorted = new ArrayList<>(paths);
        sorted.sort(String::compareToIgnoreCase);
        return sorted;
    }

    private void collectLeafPaths(ConfigurationSection section, Set<String> output) {
        if (section == null) {
            return;
        }
        for (String path : section.getKeys(true)) {
            if (!section.isConfigurationSection(path)) {
                output.add(path);
            }
        }
    }
    
    private File getZoneConfigFile(String zoneId) {
        return new File(zoneConfigsFolder, zoneId + "_config.yml");
    }
    
    private File getLegacyZoneConfigFile(String zoneId) {
        return new File(legacyZoneConfigsFolder, zoneId + "_config.yml");
    }
    
    private void ensureZoneConfigsFolder() {
        if (!zoneConfigsFolder.exists()) {
            zoneConfigsFolder.mkdirs();
        }
    }

    private FileConfiguration getZoneDefaults() {
        if (zoneDefaults == null) {
            loadZoneDefaults();
        }
        return zoneDefaults;
    }

    private void loadZoneDefaults() {
        if (!zoneTemplateFile.exists()) {
            if (!migrateZoneDefaultsFromConfig()) {
                try {
                    plugin.saveResource("zone-template.yml", false);
                } catch (IllegalArgumentException e) {
                    logger.warning("zone-template.yml not found in plugin jar.");
                }
            }
        }

        if (zoneTemplateFile.exists()) {
            zoneDefaults = YamlConfiguration.loadConfiguration(zoneTemplateFile);
        } else {
            zoneDefaults = null;
        }
    }

    private boolean migrateZoneDefaultsFromConfig() {
        ConfigurationSection legacyDefaults = plugin.getConfig().getConfigurationSection("zone-defaults");
        if (legacyDefaults == null) {
            return false;
        }
        try {
            FileConfiguration template = new YamlConfiguration();
            template.options().header(
                "Zone defaults template (migrated from config.yml)\n" +
                "Edit this file to change defaults for new zones."
            );
            copySection(legacyDefaults, template);
            template.save(zoneTemplateFile);
            logger.info("Migrated zone-defaults from config.yml into zone-template.yml.");
            return true;
        } catch (IOException e) {
            logger.warning("Failed to migrate zone-defaults into zone-template.yml: " + e.getMessage());
            return false;
        }
    }
    
    private boolean migrateLegacyZoneConfig(String zoneId) {
        File legacyFile = getLegacyZoneConfigFile(zoneId);
        File newFile = getZoneConfigFile(zoneId);
        
        if (!legacyFile.exists() || newFile.exists()) {
            return false;
        }
        
        if (!zoneConfigsFolder.exists() && !zoneConfigsFolder.mkdirs()) {
            logger.warning("Failed to create zones config folder: " + zoneConfigsFolder.getAbsolutePath());
            return false;
        }
        
        try {
            Files.move(legacyFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info("Moved legacy zone config for '" + zoneId + "' into the zones folder.");
            return true;
        } catch (IOException moveException) {
            try {
                Files.copy(legacyFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.warning("Failed to move legacy zone config for '" + zoneId + "'. Copied into the zones folder.");
                return true;
            } catch (IOException copyException) {
                logger.warning("Failed to migrate legacy zone config for '" + zoneId + "': " + copyException.getMessage());
                return false;
            }
        }
    }
}

