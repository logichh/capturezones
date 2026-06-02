package com.logichh.capturezones;

import com.logichh.capturezones.api.CaptureZonesApi;
import com.logichh.capturezones.AreaStyle;
import com.logichh.capturezones.CaptureCommandTabCompleter;
import com.logichh.capturezones.CaptureCommands;
import com.logichh.capturezones.CaptureDeathListener;
import com.logichh.capturezones.CaptureEvents;
import com.logichh.capturezones.CapturePoint;
import com.logichh.capturezones.CaptureSession;
import com.logichh.capturezones.CommandBlockListener;
import com.logichh.capturezones.CuboidSelectionManager;
import com.logichh.capturezones.NewDayListener;
import com.logichh.capturezones.UpdateZones;
import com.logichh.capturezones.ZoneProtectionListener;
import com.logichh.capturezones.ReinforcementListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Particle;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.MultiLineChart;
import org.bstats.charts.SingleLineChart;
import org.bstats.charts.SimplePie;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.ChatColor;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

public class CaptureZones
extends JavaPlugin {
    private FileConfiguration config;
    private File capturePointsFile;
    private FileConfiguration capturePointsConfig;
    private Map<String, CapturePoint> capturePoints = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, Map<Long, List<CapturePoint>>> capturePointSpatialIndex = new HashMap<>();
    private final Map<UUID, List<CapturePoint>> capturePointsByWorld = new HashMap<>();
    private boolean capturePointSpatialIndexDirty = true;
    private int capturePointSpatialIndexExtraChunks = 1;
    private Map<String, CaptureSession> activeSessions = Collections.synchronizedMap(new HashMap<>());
    private Map<String, BossBar> captureBossBars = Collections.synchronizedMap(new HashMap<>());
    private Map<String, BukkitTask> captureTasks = Collections.synchronizedMap(new HashMap<>());
    private Map<String, String> pointTypes = Collections.synchronizedMap(new HashMap<>());
    private Map<UUID, Boolean> warnedPlayers = Collections.synchronizedMap(new HashMap<>());
    private Map<Location, BlockData> originalBlocks = Collections.synchronizedMap(new HashMap<>());
    private Map<UUID, Long> firstJoinTimes = Collections.synchronizedMap(new HashMap<>());
    public Map<UUID, Boolean> hiddenBossBars = Collections.synchronizedMap(new HashMap<>());
    public Map<String, BukkitTask> boundaryTasks = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, Set<String>> boundaryPointsByPlayer = Collections.synchronizedMap(new HashMap<>());
    private final Map<UUID, BukkitTask> boundaryPlayerTasks = Collections.synchronizedMap(new HashMap<>());
    public Map<UUID, Boolean> disabledNotifications = Collections.synchronizedMap(new HashMap<>());
    private BukkitTask hourlyRewardTask;
    private Map<String, Long> lastHourlyRewardTimes = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Long> hourlyRewardNextDueTimes = Collections.synchronizedMap(new HashMap<>());
    private boolean hourlyRewardScheduleDirty = true;
    private long hourlyRewardEarliestDueAt = Long.MAX_VALUE;
    private long hourlyRewardScheduleLastRebuildAt = 0L;
    private List<MapProvider> mapProviders = new ArrayList<>();
    private HologramManager hologramManager;
    private KothManager kothManager;
    private ConquestManager conquestManager;
    private PotionRewardManager potionRewardManager;
    private UpdateZones townUpdater;  // Keep for backward compatibility with Dynmap
    private boolean worldGuardEnabled = false;
    private ReinforcementListener reinforcementListener;
    private Map<String, Integer> captureAttempts = Collections.synchronizedMap(new HashMap<>());
    private Map<String, Integer> successfulCaptures = Collections.synchronizedMap(new HashMap<>());
    private Map<String, Integer> captureProgressSoundBuckets = Collections.synchronizedMap(new HashMap<>());
    private Map<String, Long> lastErrorTime = Collections.synchronizedMap(new HashMap<>());
    private Map<String, Boolean> warnedLegacyCapturePointPaths = Collections.synchronizedMap(new HashMap<>());
    private Set<String> warnedInvalidItemRewardEntries = Collections.synchronizedSet(new HashSet<>());
    private Set<String> warnedInvalidPermissionRewardEntries = Collections.synchronizedSet(new HashSet<>());
    private Set<String> warnedInvalidPermissionGrantEntries = Collections.synchronizedSet(new HashSet<>());
    private static final int DEFAULT_SPATIAL_INDEX_EXTRA_CHUNKS = 1;
    private static final int ERROR_THROTTLE_MS = 60000;
    private static final long DEFAULT_CONTESTED_ACTIONBAR_INTERVAL_MS = 2000L;
    private static final long DEFAULT_GRACE_ACTIONBAR_INTERVAL_MS = 1000L;
    private static final int MAX_ITEM_REWARD_ENTRY_AMOUNT = 4096;
    private static final int MAX_PERMISSION_REWARD_TIERS = 64;
    private static final int MAX_PERMISSION_REWARD_GRANTS = 64;
    private static final long MAX_PERMISSION_REWARD_DURATION_MS = 31_536_000_000L;
    private static final long VISUAL_REFRESH_DEBOUNCE_TICKS = 2L;
    private static final long CAPTURE_POINT_SAVE_DEBOUNCE_TICKS = 40L;
    private static final long BOSSBAR_AUDIENCE_SYNC_INTERVAL_MS = 2000L;
    private static final Pattern PERMISSION_REWARD_DURATION_PATTERN = Pattern.compile("^(\\d+)(ms|s|m|h|d|w)$");
    private static final String CANCEL_REASON_MOVED_TOO_FAR = "Player moved too far from capture zone";
    private static final String CANCEL_REASON_MOVED_TOO_FAR_PREFIX = "Player ";
    private static final String CANCEL_REASON_MOVED_TOO_FAR_SUFFIX = " moved too far from capture zone";
    private static final Material PREPARATION_BEAM_GLASS = Material.YELLOW_STAINED_GLASS;
    private static final Material CAPTURE_BEAM_GLASS = Material.RED_STAINED_GLASS;
    private final Map<String, Long> lastWeeklyResetEpochDays = new HashMap<>();
    private BukkitTask sessionTimeoutTask;
    private BukkitTask autoSaveTask;
    private BukkitTask weeklyResetTask;
    private BukkitTask dynmapUpdateTask;
    private BukkitTask statisticsAutoSaveTask;
    private BukkitTask visualRefreshTask;
    private final Set<String> dirtyMarkerPointIds = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<String> dirtyHologramPointIds = Collections.synchronizedSet(new LinkedHashSet<>());
    private boolean fullMarkerRefreshQueued = false;
    private boolean fullHologramRefreshQueued = false;
    private BukkitTask capturePointSaveTask;
    private boolean capturePointsSaveDirty = false;
    private final Map<String, Long> lastBossBarAudienceSyncAt = Collections.synchronizedMap(new HashMap<>());
    
    private StatisticsManager statisticsManager;
    private StatisticsGUI statisticsGUI;

    private ZoneConfigManager zoneConfigManager;

    private DiscordWebhook discordWebhook;
    private PermissionRewardManager permissionRewardManager;

    private ShopManager shopManager;
    private ShopListener shopListener;
    private ShopEconomyAdapter shopEconomyAdapter;
    private CuboidSelectionManager cuboidSelectionManager;
    private ZoneItemRewardEditor zoneItemRewardEditor;

    private UpdateChecker updateChecker;
    private CaptureZonesPlaceholderExpansion placeholderExpansion;

    private DataMigrationManager dataMigrationManager;
    private int lastLegacyTownOwnerRebindCount;
    private boolean townyAvailable;
    private TownyMode townyMode = TownyMode.AUTO;
    private EnumSet<CaptureOwnerType> allowedOwnerTypes = EnumSet.of(CaptureOwnerType.TOWN);
    private CaptureOwnerType defaultOwnerType = CaptureOwnerType.TOWN;
    private OwnerPlatformAdapter ownerPlatform = new StandaloneOwnerPlatformAdapter();
    private CaptureZonesApi apiService;

    private enum ContestedProgressPolicy {
        PAUSE,
        DECAY,
        ROLLBACK;

        private static ContestedProgressPolicy fromConfigValue(String rawValue) {
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return PAUSE;
            }
            try {
                return ContestedProgressPolicy.valueOf(rawValue.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return PAUSE;
            }
        }
    }

    private enum CooldownTrigger {
        SUCCESS,
        CANCEL,
        FAIL
    }

    private enum ItemRewardInventoryFullBehavior {
        DROP,
        CANCEL;

        private static ItemRewardInventoryFullBehavior fromConfigValue(String rawValue) {
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return DROP;
            }
            try {
                return ItemRewardInventoryFullBehavior.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return DROP;
            }
        }
    }

    private enum RewardPermissionConflictPolicy {
        HIGHEST,
        ADDITIVE,
        FIRST_MATCH;

        private static RewardPermissionConflictPolicy fromConfigValue(String rawValue) {
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return HIGHEST;
            }
            String normalized = rawValue.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
            try {
                return RewardPermissionConflictPolicy.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return HIGHEST;
            }
        }
    }

    private enum MoneyPayoutMode {
        OWNER_ACCOUNT,
        SPLIT_ONLINE,
        EACH_ONLINE,
        EACH_RESIDENT;

        private static MoneyPayoutMode fromConfigValue(String rawValue) {
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return OWNER_ACCOUNT;
            }
            String normalized = rawValue.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
            try {
                return MoneyPayoutMode.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return OWNER_ACCOUNT;
            }
        }
    }

    private static final class EconomyRewardPayoutResult {
        private final boolean delivered;
        private final double totalPaid;
        private final int recipients;
        private final int failedRecipients;
        private final MoneyPayoutMode mode;

        private EconomyRewardPayoutResult(boolean delivered, double totalPaid, int recipients, int failedRecipients, MoneyPayoutMode mode) {
            this.delivered = delivered;
            this.totalPaid = totalPaid;
            this.recipients = recipients;
            this.failedRecipients = failedRecipients;
            this.mode = mode;
        }

        private static EconomyRewardPayoutResult none(MoneyPayoutMode mode) {
            return new EconomyRewardPayoutResult(false, 0.0, 0, 0, mode);
        }
    }

    private enum OwnerPlatformMode {
        AUTO,
        TOWNY,
        STANDALONE,
        SCOREBOARD;

        private static OwnerPlatformMode fromConfigValue(String rawValue, OwnerPlatformMode fallback) {
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return fallback;
            }
            try {
                return OwnerPlatformMode.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    private static final class PermissionRewardTier {
        private final String permission;
        private final double multiplier;
        private final double flatBonus;

        private PermissionRewardTier(String permission, double multiplier, double flatBonus) {
            this.permission = permission;
            this.multiplier = multiplier;
            this.flatBonus = flatBonus;
        }
    }

    private static final class RewardPermissionResolution {
        private final double finalAmount;
        private final boolean applied;
        private final String policyName;
        private final String matchedPermissions;
        private final String contextPlayerName;

        private RewardPermissionResolution(
            double finalAmount,
            boolean applied,
            String policyName,
            String matchedPermissions,
            String contextPlayerName
        ) {
            this.finalAmount = finalAmount;
            this.applied = applied;
            this.policyName = policyName;
            this.matchedPermissions = matchedPermissions;
            this.contextPlayerName = contextPlayerName;
        }

        private static RewardPermissionResolution none(double amount) {
            return new RewardPermissionResolution(amount, false, "", "", "");
        }
    }

    private static final class ItemRewardPayoutResult {
        private final boolean configured;
        private final boolean delivered;
        private final boolean dropped;
        private final String summary;
        private final String recipientName;

        private ItemRewardPayoutResult(
            boolean configured,
            boolean delivered,
            boolean dropped,
            String summary,
            String recipientName
        ) {
            this.configured = configured;
            this.delivered = delivered;
            this.dropped = dropped;
            this.summary = summary;
            this.recipientName = recipientName;
        }

        private static ItemRewardPayoutResult none() {
            return new ItemRewardPayoutResult(false, false, false, "", "");
        }
    }

    private static final class PermissionRewardPayoutResult {
        private final boolean configured;
        private final boolean delivered;
        private final String summary;
        private final String recipientName;

        private PermissionRewardPayoutResult(
            boolean configured,
            boolean delivered,
            String summary,
            String recipientName
        ) {
            this.configured = configured;
            this.delivered = delivered;
            this.summary = summary;
            this.recipientName = recipientName;
        }

        private static PermissionRewardPayoutResult none() {
            return new PermissionRewardPayoutResult(false, false, "", "");
        }
    }

    private static final class PermissionGrantReward {
        private final String permission;
        private final long durationMs;

        private PermissionGrantReward(String permission, long durationMs) {
            this.permission = permission;
            this.durationMs = Math.max(0L, durationMs);
        }

        private boolean isPermanent() {
            return durationMs <= 0L;
        }
    }

    private static final class ContestedState {
        private final boolean contested;
        private final int capturingPlayers;
        private final int opposingPlayers;
        private final int opposingOwners;

        private ContestedState(boolean contested, int capturingPlayers, int opposingPlayers, int opposingOwners) {
            this.contested = contested;
            this.capturingPlayers = capturingPlayers;
            this.opposingPlayers = opposingPlayers;
            this.opposingOwners = opposingOwners;
        }

        private static ContestedState clear() {
            return new ContestedState(false, 0, 0, 0);
        }
    }

    @Override
    public void onEnable() {
        migrateLegacyDataFolderIfNeeded();

        saveDefaultConfig();
        this.capturePointsFile = new File(getDataFolder(), "capture_points.yml");

        Messages.init(this);

        this.config = getConfig();

        createDefaultConfig();

        this.dataMigrationManager = new DataMigrationManager(this);
        this.dataMigrationManager.migrateCoreFiles(this.capturePointsFile);

        if (!initializeOwnerPlatform()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!validateConfig()) {
            getLogger().severe("Failed to validate config! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadPointTypes();

        loadCapturePoints();

        zoneConfigManager = new ZoneConfigManager(this);
        zoneConfigManager.migrateExistingZones();
        if (this.dataMigrationManager != null) {
            this.dataMigrationManager.migrateZoneFiles();
        }
        zoneConfigManager.loadAllZoneConfigs();
        invalidateCapturePointSpatialIndex();

        setupKothManager();
        setupConquestManager();
        setupMapProviders();
        setupHolograms();
        setupPlaceholderApiExpansion();
        setupWorldGuard();

        setupStatistics();

        discordWebhook = new DiscordWebhook(this);

        setupShopSystem();
        setupPermissionRewards();
        setupPotionRewardManager();

        startSessionTimeoutChecker();
        startAutoSave();
        startHourlyRewards();
        startWeeklyResetTask();

        getServer().getPluginManager().registerEvents(new CaptureEvents(this), this);
        getServer().getPluginManager().registerEvents(new CommandBlockListener(this), this);
        getServer().getPluginManager().registerEvents(new ZoneProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerEventListener(), this);
        getServer().getPluginManager().registerEvents(new CaptureDeathListener(this), this);
        cuboidSelectionManager = new CuboidSelectionManager(this);
        getServer().getPluginManager().registerEvents(cuboidSelectionManager, this);
        zoneItemRewardEditor = new ZoneItemRewardEditor(this);
        getServer().getPluginManager().registerEvents(zoneItemRewardEditor, this);
        if (isTownyIntegrationEnabled()) {
            getServer().getPluginManager().registerEvents(new NewDayListener(this), this);
        } else {
            getLogger().info("Towny NewDay listener disabled (Towny integration unavailable/disabled).");
        }
        reinforcementListener = new ReinforcementListener(this);
        getServer().getPluginManager().registerEvents(reinforcementListener, this);
        setupMobSpawner();

        CaptureCommands commandExecutor = new CaptureCommands(this, cuboidSelectionManager, zoneItemRewardEditor);
        boolean registered = false;
        registered |= registerCommandBinding("capturezones", commandExecutor);
        registered |= registerCommandBinding("capturepoint", commandExecutor); // Legacy compatibility
        if (!registered) {
            getLogger().severe("Failed to register capture command bindings!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (this.config.getBoolean("settings.auto-show-boundaries", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                this.autoShowBoundariesForPlayer(player);
            }
        }

        getLogger().info("═════════════════════════════════════════════════════════");
        getLogger().info("                  CaptureZones");
        getLogger().info("                 v" + getDescription().getVersion() + " by Milin");
        getLogger().info("═════════════════════════════════════════════════════════");
        getLogger().info("CaptureZones has been enabled!");

        initializeMetrics();

        if (getConfig().getBoolean("settings.check-for-updates", true)) {
            updateChecker = new UpdateChecker(this);
            updateChecker.checkForUpdates().thenAccept(updateAvailable -> {
                if (updateAvailable) {
                    getLogger().info("╔═══════════════════════════════════════════════╗");
                    getLogger().info("║    New version available: " + updateChecker.getLatestVersion() + "              ║");
                    getLogger().info("║    Download: modrinth.com/plugin/capturezones          ║");
                    getLogger().info("╚═══════════════════════════════════════════════╝");
                }
            });
        }

        registerApiService();
    }

    private void initializeMetrics() {
        int pluginId = 28044; // bStats plugin ID
        Metrics metrics = new Metrics(this, pluginId);
        metrics.addCustomChart(new SingleLineChart("capture_points", () -> this.capturePoints.size()));
        metrics.addCustomChart(new SingleLineChart("active_sessions", () -> this.activeSessions.size()));

        metrics.addCustomChart(new SingleLineChart("max_capture_points_limit", this::getMaxCapturePointsLimit));
        metrics.addCustomChart(new SingleLineChart("max_active_captures_limit", this::getMaxActiveCapturesLimit));
        metrics.addCustomChart(new SingleLineChart("koth_managed_zones", this::countManagedKothZonesMetric));
        metrics.addCustomChart(new SingleLineChart("zones_visible_on_map", this::countVisibleOnMapZonesMetric));
        metrics.addCustomChart(new SingleLineChart("zones_with_positive_reward", this::countPositiveRewardZonesMetric));

        metrics.addCustomChart(new MultiLineChart("zone_shape_distribution", this::buildZoneShapeDistributionMetric));

        metrics.addCustomChart(new SimplePie("koth_enabled", () -> booleanState(this.config.getBoolean("koth.enabled", false))));
        metrics.addCustomChart(new SimplePie("koth_schedule_enabled", () -> booleanState(this.config.getBoolean("koth.schedule.enabled", true))));
        metrics.addCustomChart(new SimplePie("koth_selection_mode", this::resolveKothSelectionModeMetric));
        metrics.addCustomChart(new SimplePie("koth_rewards_profile", this::resolveKothRewardsProfileMetric));
        metrics.addCustomChart(new SimplePie("shops_enabled", () -> booleanState(this.config.getBoolean("shops.enabled", false))));
        metrics.addCustomChart(new SimplePie("discord_enabled", () -> booleanState(this.config.getBoolean("discord.enabled", false))));
        metrics.addCustomChart(new SimplePie("statistics_enabled", () -> booleanState(this.config.getBoolean("statistics.enabled", true))));
        metrics.addCustomChart(new SimplePie("capture_teleport_enabled", () -> booleanState(this.config.getBoolean("settings.capture-teleport-enabled", true))));
        metrics.addCustomChart(new SimplePie("capture_concurrency_mode", this::resolveCaptureConcurrencyModeMetric));
        metrics.addCustomChart(new SimplePie("bossbar_visibility_mode", this::resolveBossbarVisibilityMetric));
        metrics.addCustomChart(new SimplePie("owner_platform", this::resolveOwnerPlatformMetric));
        metrics.addCustomChart(new SimplePie("allowed_owner_types", this::resolveAllowedOwnerTypesMetric));
        metrics.addCustomChart(new SimplePie("map_integrations_config", this::resolveMapIntegrationsConfigMetric));
        metrics.addCustomChart(new SimplePie("boundary_visual_mode", this::resolveBoundaryVisualModeMetric));
    }

    private String booleanState(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    private List<CapturePoint> snapshotCapturePointsForMetrics() {
        synchronized (this.capturePoints) {
            return new ArrayList<>(this.capturePoints.values());
        }
    }

    private int countVisibleOnMapZonesMetric() {
        int total = 0;
        for (CapturePoint point : snapshotCapturePointsForMetrics()) {
            if (point != null && point.isShowOnMap()) {
                total++;
            }
        }
        return total;
    }

    private int countPositiveRewardZonesMetric() {
        int total = 0;
        for (CapturePoint point : snapshotCapturePointsForMetrics()) {
            if (point != null && point.getReward() > 0.0) {
                total++;
            }
        }
        return total;
    }

    private Map<String, Integer> buildZoneShapeDistributionMetric() {
        int circle = 0;
        int cuboid = 0;

        for (CapturePoint point : snapshotCapturePointsForMetrics()) {
            if (point == null) {
                continue;
            }
            if (point.isCuboid()) {
                cuboid++;
            } else {
                circle++;
            }
        }

        Map<String, Integer> output = new LinkedHashMap<>();
        output.put("circle", circle);
        output.put("cuboid", cuboid);
        return output;
    }

    private int countManagedKothZonesMetric() {
        if (!this.config.getBoolean("koth.enabled", false)) {
            return 0;
        }

        List<CapturePoint> points = snapshotCapturePointsForMetrics();
        int totalZones = points.size();
        if (totalZones <= 0) {
            return 0;
        }

        String selectionMode = this.config.getString("koth.activation.selection-mode", "ALL");
        String normalizedMode = selectionMode == null ? "ALL" : selectionMode.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(normalizedMode)) {
            return totalZones;
        }

        Set<String> existingZoneIds = new HashSet<>();
        for (CapturePoint point : points) {
            if (point == null || point.getId() == null) {
                continue;
            }
            existingZoneIds.add(point.getId().trim().toLowerCase(Locale.ROOT));
        }

        List<String> configured = this.config.getStringList("koth.activation.zones");
        if (configured == null || configured.isEmpty()) {
            return totalZones;
        }

        Set<String> managed = new HashSet<>();
        for (String zoneId : configured) {
            if (zoneId == null || zoneId.trim().isEmpty()) {
                continue;
            }
            String normalized = zoneId.trim().toLowerCase(Locale.ROOT);
            if (existingZoneIds.contains(normalized)) {
                managed.add(normalized);
            }
        }

        if (managed.isEmpty()) {
            return totalZones;
        }
        return managed.size();
    }

    private String resolveKothSelectionModeMetric() {
        String mode = this.config.getString("koth.activation.selection-mode", "ALL");
        if (mode == null || mode.trim().isEmpty()) {
            return "all";
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveKothRewardsProfileMetric() {
        List<String> enabled = new ArrayList<>();
        if (this.config.getBoolean("koth.rewards.money.enabled", true)) {
            enabled.add("money");
        }
        if (this.config.getBoolean("koth.rewards.items.enabled", true)
            && this.config.getBoolean("koth.rewards.items.use-zone-item-rewards", true)) {
            enabled.add("items");
        }
        if (this.config.getBoolean("koth.rewards.permissions.enabled", true)
            && this.config.getBoolean("koth.rewards.permissions.use-zone-permission-rewards", true)) {
            enabled.add("permissions");
        }
        if (enabled.isEmpty()) {
            return "none";
        }
        return String.join("+", enabled);
    }

    private String resolveCaptureConcurrencyModeMetric() {
        if (!isMultiZoneConcurrentCaptureEnabled()) {
            return "single";
        }
        return "multi_" + getConfiguredMaxConcurrentCaptureZones();
    }

    private String resolveBossbarVisibilityMetric() {
        int extraChunks = resolveBossbarVisibilityExtraChunks();
        if (extraChunks < 0) {
            return "global";
        }
        if (extraChunks == 0) {
            return "zone_only";
        }
        return "zone_plus_" + extraChunks;
    }

    private String resolveOwnerPlatformMetric() {
        if (this.ownerPlatform != null) {
            String key = this.ownerPlatform.getPlatformKey();
            if (key != null && !key.trim().isEmpty()) {
                return key.trim().toLowerCase(Locale.ROOT);
            }
        }
        String configured = this.config.getString("capture-owners.platform", "auto");
        return configured == null || configured.trim().isEmpty()
            ? "auto"
            : configured.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveAllowedOwnerTypesMetric() {
        List<String> configured = this.config.getStringList("capture-owners.allowed");
        if (configured == null || configured.isEmpty()) {
            return "none";
        }

        Set<String> unique = new LinkedHashSet<>();
        for (String raw : configured) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            unique.add(raw.trim().toLowerCase(Locale.ROOT));
        }
        if (unique.isEmpty()) {
            return "none";
        }
        return String.join("+", unique);
    }

    private String resolveMapIntegrationsConfigMetric() {
        boolean dynmapEnabled = this.config.getBoolean("dynmap.enabled", true);
        boolean bluemapEnabled = this.config.getBoolean("bluemap.enabled", true);
        if (dynmapEnabled && bluemapEnabled) {
            return "dynmap+bluemap";
        }
        if (dynmapEnabled) {
            return "dynmap";
        }
        if (bluemapEnabled) {
            return "bluemap";
        }
        return "none";
    }

    private String resolveBoundaryVisualModeMetric() {
        String mode = this.config.getString("boundary-visuals.mode", "OFF");
        if (mode == null || mode.trim().isEmpty()) {
            return "off";
        }
        return mode.trim().toLowerCase(Locale.ROOT);
    }

    private boolean registerCommandBinding(String commandName, CommandExecutor executor) {
        PluginCommand command = getCommand(commandName);
        if (command == null) {
            return false;
        }
        command.setExecutor(executor);
        command.setTabCompleter(new CaptureCommandTabCompleter(this));
        return true;
    }

    private void migrateLegacyDataFolderIfNeeded() {
        File currentDataFolder = getDataFolder();
        File parent = currentDataFolder.getParentFile();
        if (parent == null) {
            return;
        }

        File legacyDataFolder = new File(parent, "CaptureZones");
        if (!legacyDataFolder.exists() || legacyDataFolder.equals(currentDataFolder)) {
            return;
        }

        try {
            if (!currentDataFolder.exists()) {
                Files.move(
                    legacyDataFolder.toPath(),
                    currentDataFolder.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                );
                getLogger().info("Migrated legacy data folder from 'CaptureZones' to 'CaptureZones'.");
                return;
            }

            int copied = copyMissingLegacyFiles(legacyDataFolder.toPath(), currentDataFolder.toPath());
            if (copied > 0) {
                getLogger().info("Copied " + copied + " missing file(s) from legacy data folder 'CaptureZones'.");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to migrate legacy data folder: " + e.getMessage());
        }
    }

    private int copyMissingLegacyFiles(Path sourceRoot, Path targetRoot) throws IOException {
        final int[] copiedCount = {0};
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.forEach(sourcePath -> {
                try {
                    Path relative = sourceRoot.relativize(sourcePath);
                    Path targetPath = targetRoot.resolve(relative);

                    if (Files.isDirectory(sourcePath)) {
                        if (!Files.exists(targetPath)) {
                            Files.createDirectories(targetPath);
                        }
                        return;
                    }

                    if (!Files.exists(targetPath)) {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(sourcePath, targetPath, StandardCopyOption.COPY_ATTRIBUTES);
                        copiedCount[0]++;
                    }
                } catch (Exception ignored) {
                    // continue copying other files
                }
            });
        }
        return copiedCount[0];
    }
    
    public void autoShowBoundariesForPlayer(Player player) {
        if (getBoundaryMode() == BoundaryMode.OFF) {
            return;
        }
        for (CapturePoint point : this.capturePoints.values()) {
            startBoundaryVisualization(player, point);
        }
    }

    public boolean startBoundaryVisualization(Player player, CapturePoint point) {
        BoundaryMode mode = getBoundaryMode();
        if (mode == BoundaryMode.OFF || player == null || point == null || point.getId() == null || point.getId().trim().isEmpty()) {
            return false;
        }

        String key = boundaryKey(player.getUniqueId(), point.getId());
        if (this.boundaryTasks.containsKey(key)) {
            return true;
        }

        UUID playerId = player.getUniqueId();
        this.boundaryPointsByPlayer
            .computeIfAbsent(playerId, ignored -> Collections.synchronizedSet(new LinkedHashSet<>()))
            .add(point.getId());
        BukkitTask task = ensureBoundaryVisualizationTask(playerId);
        this.boundaryTasks.put(key, task);
        return true;
    }

    private BukkitTask ensureBoundaryVisualizationTask(UUID playerId) {
        BukkitTask existing = this.boundaryPlayerTasks.get(playerId);
        if (existing != null && !existing.isCancelled()) {
            return existing;
        }

        BoundaryVisualSettings settings = getBoundarySettings(getBoundaryMode());
        long interval = Math.max(1L, settings.intervalTicks);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline() || getBoundaryMode() == BoundaryMode.OFF) {
                    stopBoundaryVisualizationsForPlayer(playerId);
                    this.cancel();
                    return;
                }

                BoundaryVisualSettings activeSettings = getBoundarySettings(getBoundaryMode());
                renderBoundariesForPlayer(player, activeSettings);
            }
        }.runTaskTimer(this, 0L, interval);
        this.boundaryPlayerTasks.put(playerId, task);
        return task;
    }

    private void renderBoundariesForPlayer(Player player, BoundaryVisualSettings settings) {
        if (player == null || settings == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Set<String> visiblePointIds = this.boundaryPointsByPlayer.get(playerId);
        if (visiblePointIds == null || visiblePointIds.isEmpty()) {
            return;
        }

        List<String> invalidPointIds = new ArrayList<>();
        for (String pointId : new ArrayList<>(visiblePointIds)) {
            if (pointId == null || pointId.trim().isEmpty()) {
                invalidPointIds.add(pointId);
                continue;
            }
            CapturePoint point = this.capturePoints.get(pointId);
            if (point == null || point.getLocation() == null || point.getLocation().getWorld() == null) {
                invalidPointIds.add(pointId);
                continue;
            }
            if (!canRenderBoundaryForPlayer(point, player.getLocation(), settings.visibilityRangeChunks)) {
                continue;
            }
            renderBoundaryForPoint(player, point, settings);
        }

        if (invalidPointIds.isEmpty()) {
            return;
        }
        for (String invalidPointId : invalidPointIds) {
            if (invalidPointId == null || invalidPointId.trim().isEmpty()) {
                continue;
            }
            stopBoundaryVisualization(boundaryKey(playerId, invalidPointId));
        }
    }

    private void renderBoundaryForPoint(Player player, CapturePoint point, BoundaryVisualSettings settings) {
        Location center = point.getLocation();
        World world = center != null ? center.getWorld() : null;
        if (world == null) {
            return;
        }

        int blockRadius = point.getRadius();
        int sides = Math.max(settings.minSides, (int) Math.round(blockRadius * settings.sidesMultiplier));
        int minX = point.getCuboidMinX();
        int maxX = point.getCuboidMaxX();
        int minZ = point.getCuboidMinZ();
        int maxZ = point.getCuboidMaxZ();

        if (point.isCuboid()) {
            int perimeter = Math.max(4, 2 * ((maxX - minX + 1) + (maxZ - minZ + 1)));
            int step = Math.max(1, perimeter / Math.max(8, sides));

            for (int x = minX; x <= maxX; x += step) {
                spawnBoundaryColumn(player, world, x + 0.5, minZ + 0.5, settings);
                spawnBoundaryColumn(player, world, x + 0.5, maxZ + 0.5, settings);
            }
            for (int z = minZ; z <= maxZ; z += step) {
                spawnBoundaryColumn(player, world, minX + 0.5, z + 0.5, settings);
                spawnBoundaryColumn(player, world, maxX + 0.5, z + 0.5, settings);
            }
            return;
        }

        for (int i = 0; i < sides; i++) {
            double angle = 2.0 * Math.PI * i / sides;
            double x = center.getX() + blockRadius * Math.cos(angle);
            double z = center.getZ() + blockRadius * Math.sin(angle);
            spawnBoundaryColumn(player, world, x, z, settings);
        }
    }

    private void spawnBoundaryColumn(Player player, World world, double x, double z, BoundaryVisualSettings settings) {
        int surfaceY = world.getHighestBlockYAt((int)Math.floor(x), (int)Math.floor(z));
        int startY = Math.max(0, surfaceY + settings.minHeight);
        int endY = Math.min(320, surfaceY + settings.maxHeight);

        for (int yPos = startY; yPos <= endY; yPos++) {
            try {
                player.spawnParticle(Particle.GLOW, x, yPos, z, 1, 0.0, 0.0, 0.0, 0.0);
            } catch (Exception e) {
                player.spawnParticle(Particle.SOUL, x, yPos, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    public void stopBoundaryVisualization(String key) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        this.boundaryTasks.remove(key);
        UUID playerId = parseBoundaryPlayerId(key);
        String pointId = parseBoundaryPointId(key);
        if (playerId == null || pointId == null || pointId.isEmpty()) {
            return;
        }

        Set<String> pointIds = this.boundaryPointsByPlayer.get(playerId);
        if (pointIds != null) {
            pointIds.remove(pointId);
            if (!pointIds.isEmpty()) {
                return;
            }
            this.boundaryPointsByPlayer.remove(playerId);
        }

        BukkitTask task = this.boundaryPlayerTasks.remove(playerId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public void stopBoundaryVisualizationsForPlayer(UUID playerId) {
        if (playerId == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        String prefix = playerId + "_";
        for (String key : new ArrayList<>(this.boundaryTasks.keySet())) {
            if (key != null && key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        for (String key : keys) {
            stopBoundaryVisualization(key);
        }
    }

    public void cancelAllBoundaryTasks() {
        for (BukkitTask task : new ArrayList<>(this.boundaryPlayerTasks.values())) {
            cancelTask(task);
        }
        this.boundaryPlayerTasks.clear();
        this.boundaryPointsByPlayer.clear();
        this.boundaryTasks.clear();
    }

    private UUID parseBoundaryPlayerId(String key) {
        int separatorIndex = key.indexOf('_');
        if (separatorIndex <= 0) {
            return null;
        }
        try {
            return UUID.fromString(key.substring(0, separatorIndex));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String parseBoundaryPointId(String key) {
        int separatorIndex = key.indexOf('_');
        if (separatorIndex < 0 || separatorIndex + 1 >= key.length()) {
            return "";
        }
        return key.substring(separatorIndex + 1);
    }

    private void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    private void removeAllCaptureBossBars() {
        for (BossBar bossBar : this.captureBossBars.values()) {
            if (bossBar != null) {
                bossBar.removeAll();
            }
        }
        this.captureBossBars.clear();
        this.lastBossBarAudienceSyncAt.clear();
    }

    private void cancelAllCaptureTasks() {
        for (BukkitTask task : this.captureTasks.values()) {
            cancelTask(task);
        }
        this.captureTasks.clear();
    }

    private CaptureSession removeActiveSession(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return null;
        }

        CaptureSession session = this.activeSessions.remove(pointId.trim());
        if (session != null) {
            session.stop();
        }
        return session;
    }

    private CaptureSession cleanupCaptureRuntime(String pointId, CapturePoint point, boolean clearCapturingOwner, boolean removeBossBar) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return null;
        }

        String normalizedPointId = pointId.trim();
        if (this.reinforcementListener != null) {
            this.reinforcementListener.clearReinforcements(normalizedPointId);
        }

        if (point != null) {
            this.removeBeacon(point);
            if (clearCapturingOwner) {
                point.setCapturingOwner(null);
            }
        }

        if (removeBossBar) {
            this.removeCaptureBossBar(normalizedPointId);
        }

        this.captureProgressSoundBuckets.remove(normalizedPointId);
        cancelTask(this.captureTasks.remove(normalizedPointId));
        return removeActiveSession(normalizedPointId);
    }

    private void cleanupActiveCapturesForLifecycle(String lifecycleContext) {
        List<String> activePointIds = new ArrayList<>(this.activeSessions.keySet());
        int cleaned = 0;

        for (String pointId : activePointIds) {
            CapturePoint point = this.capturePoints.get(pointId);
            cleanupCaptureRuntime(pointId, point, true, true);
            if (point != null) {
                point.setCaptureProgress(0.0);
            }
            cleaned++;
        }

        if (cleaned > 0) {
            this.getLogger().info("Stopped " + cleaned + " active capture session(s) during " + lifecycleContext + ".");
        }
    }

    private String boundaryKey(UUID playerId, String pointId) {
        return playerId + "_" + pointId;
    }

    public boolean boundariesEnabled() {
        return getBoundaryMode() != BoundaryMode.OFF;
    }

    private BoundaryMode getBoundaryMode() {
        String value = this.config.getString("boundary-visuals.mode", "ON");
        try {
            return BoundaryMode.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return BoundaryMode.ON;
        }
    }

    private BoundaryVisualSettings getBoundarySettings(BoundaryMode mode) {
        String path = mode == BoundaryMode.REDUCED ? "boundary-visuals.reduced." : "boundary-visuals.on.";
        int minSides = Math.max(16, this.config.getInt(path + "min-sides", mode == BoundaryMode.REDUCED ? 96 : 256));
        double multiplier = Math.max(1.0, this.config.getDouble(path + "sides-multiplier", mode == BoundaryMode.REDUCED ? 2.0 : 4.0));
        int minHeight = this.config.getInt(path + "vertical-range.min", mode == BoundaryMode.REDUCED ? -2 : -10);
        int maxHeight = this.config.getInt(path + "vertical-range.max", mode == BoundaryMode.REDUCED ? 20 : 40);
        int visibilityRangeChunks = Math.max(0, this.config.getInt(path + "visibility-range-chunks", mode == BoundaryMode.REDUCED ? 6 : 8));
        long interval = Math.max(1L, this.config.getLong(path + "interval-ticks", mode == BoundaryMode.REDUCED ? 30L : 10L));
        return new BoundaryVisualSettings(minSides, multiplier, minHeight, maxHeight, visibilityRangeChunks, interval);
    }

    private boolean canRenderBoundaryForPlayer(CapturePoint point, Location playerLocation, int visibilityRangeChunks) {
        if (point == null || playerLocation == null || point.getLocation() == null || point.getLocation().getWorld() == null || playerLocation.getWorld() == null) {
            return false;
        }
        if (!point.getLocation().getWorld().getUID().equals(playerLocation.getWorld().getUID())) {
            return false;
        }

        double visibilityBlocks = Math.max(0, visibilityRangeChunks) * 16.0;
        if (point.isCuboid()) {
            return isWithinCuboidBoundaryVisibility(point, playerLocation, visibilityBlocks);
        }
        return isWithinCircleBoundaryVisibility(point, playerLocation, visibilityBlocks);
    }

    private boolean isWithinCircleBoundaryVisibility(CapturePoint point, Location playerLocation, double visibilityBlocks) {
        Location center = point.getLocation();
        if (center == null) {
            return false;
        }

        double dx = playerLocation.getX() - center.getX();
        double dz = playerLocation.getZ() - center.getZ();
        double distanceSquared = (dx * dx) + (dz * dz);
        double radius = Math.max(0.0, point.getRadius());
        double minDistance = Math.max(0.0, radius - visibilityBlocks);
        double maxDistance = radius + visibilityBlocks;
        return distanceSquared >= (minDistance * minDistance) && distanceSquared <= (maxDistance * maxDistance);
    }

    private boolean isWithinCuboidBoundaryVisibility(CapturePoint point, Location playerLocation, double visibilityBlocks) {
        double minX = point.getCuboidMinX();
        double maxX = point.getCuboidMaxX() + 1.0;
        double minZ = point.getCuboidMinZ();
        double maxZ = point.getCuboidMaxZ() + 1.0;
        double x = playerLocation.getX();
        double z = playerLocation.getZ();

        double outsideX = 0.0;
        if (x < minX) {
            outsideX = minX - x;
        } else if (x > maxX) {
            outsideX = x - maxX;
        }
        double outsideZ = 0.0;
        if (z < minZ) {
            outsideZ = minZ - z;
        } else if (z > maxZ) {
            outsideZ = z - maxZ;
        }

        if (outsideX > 0.0 || outsideZ > 0.0) {
            double outsideDistanceSquared = (outsideX * outsideX) + (outsideZ * outsideZ);
            return outsideDistanceSquared <= (visibilityBlocks * visibilityBlocks);
        }

        double distanceToBoundary = Math.min(
            Math.min(x - minX, maxX - x),
            Math.min(z - minZ, maxZ - z)
        );
        return distanceToBoundary <= visibilityBlocks;
    }

    private boolean initializeOwnerPlatform() {
        Plugin towny = this.getServer().getPluginManager().getPlugin("Towny");
        this.townyAvailable = towny != null && towny.isEnabled();
        this.townyMode = TownyMode.fromConfigValue(this.config.getString("towny.mode", "auto"), TownyMode.AUTO);
        OwnerPlatformMode platformMode = OwnerPlatformMode.fromConfigValue(
            this.config.getString("capture-owners.platform", "auto"),
            OwnerPlatformMode.AUTO
        );

        if (this.townyMode == TownyMode.REQUIRED && !this.townyAvailable) {
            getLogger().severe("Towny integration mode is 'required' but Towny is not installed or enabled.");
            getLogger().severe("Install Towny or set 'towny.mode: auto|disabled' with non-Towny owner platform.");
            return false;
        }

        OwnerPlatformAdapter selectedPlatform = selectOwnerPlatformAdapter(platformMode);
        if (selectedPlatform == null) {
            return false;
        }
        this.ownerPlatform = selectedPlatform;

        EnumSet<CaptureOwnerType> parsedAllowed = parseAllowedOwnerTypes(this.config.getStringList("capture-owners.allowed"));
        if (parsedAllowed.isEmpty()) {
            parsedAllowed = EnumSet.of(CaptureOwnerType.TOWN);
        }

        if (this.ownerPlatform instanceof TownyOwnerPlatformAdapter) {
            if (!this.config.getBoolean("towny.allow-town-capture", true)) {
                parsedAllowed.remove(CaptureOwnerType.TOWN);
            }
            if (!this.config.getBoolean("towny.allow-nation-capture", true)) {
                parsedAllowed.remove(CaptureOwnerType.NATION);
            }
        }
        this.allowedOwnerTypes = parsedAllowed;

        this.defaultOwnerType = CaptureOwnerType.fromConfigValue(
            this.config.getString("capture-owners.default-owner-type", "town"),
            CaptureOwnerType.TOWN
        );

        if (!applyOwnerPlatformCapabilities()) {
            return false;
        }

        if (this.allowedOwnerTypes.isEmpty()) {
            getLogger().severe("No capture owner types are enabled after applying provider capabilities.");
            return false;
        }

        if (!this.allowedOwnerTypes.contains(this.defaultOwnerType)) {
            getLogger().warning("Default capture owner type '" + this.defaultOwnerType.name().toLowerCase()
                + "' is not supported by active provider '" + this.ownerPlatform.getPlatformKey() + "'. Falling back to a supported type.");
            getLogger().warning("Supported types: " + formatOwnerTypes(this.allowedOwnerTypes));
            // Prefer player if available, otherwise pick the first supported type
            if (this.allowedOwnerTypes.contains(CaptureOwnerType.PLAYER)) {
                this.defaultOwnerType = CaptureOwnerType.PLAYER;
            } else {
                this.defaultOwnerType = this.allowedOwnerTypes.iterator().next();
            }
            getLogger().info("Default owner type set to '" + this.defaultOwnerType.name().toLowerCase() + "'.");
        }

        getLogger().info(
            "Owner platform '" + this.ownerPlatform.getPlatformKey() + "' supports: " + formatOwnerTypes(this.allowedOwnerTypes)
        );
        return true;
    }

    private OwnerPlatformAdapter selectOwnerPlatformAdapter(OwnerPlatformMode mode) {
        if (mode == null) {
            mode = OwnerPlatformMode.AUTO;
        }

        switch (mode) {
            case TOWNY:
                if (!isTownyIntegrationEnabled()) {
                    getLogger().severe("Owner platform is set to 'towny' but Towny integration is unavailable/disabled.");
                    getLogger().severe("Set 'capture-owners.platform: auto|standalone|scoreboard' or enable Towny.");
                    return null;
                }
                getLogger().info("Using Towny owner platform.");
                return new TownyOwnerPlatformAdapter();
            case STANDALONE:
                getLogger().info("Using standalone owner platform.");
                return new StandaloneOwnerPlatformAdapter();
            case SCOREBOARD:
                getLogger().info("Using scoreboard owner platform.");
                return new ScoreboardTeamOwnerPlatformAdapter();
            case AUTO:
            default:
                if (isTownyIntegrationEnabled()) {
                    getLogger().info("Towny integration active. Using Towny owner platform.");
                    return new TownyOwnerPlatformAdapter();
                }
                getLogger().info("Towny integration unavailable/disabled. Falling back to standalone owner platform.");
                return new StandaloneOwnerPlatformAdapter();
        }
    }

    private boolean applyOwnerPlatformCapabilities() {
        if (this.ownerPlatform == null) {
            getLogger().severe("Owner platform adapter is not initialized.");
            return false;
        }

        EnumSet<CaptureOwnerType> supportedTypes = this.ownerPlatform.getSupportedOwnerTypes();
        if (supportedTypes == null || supportedTypes.isEmpty()) {
            getLogger().severe("Owner platform '" + this.ownerPlatform.getPlatformKey() + "' reports no supported owner types.");
            return false;
        }

        EnumSet<CaptureOwnerType> requestedTypes = EnumSet.noneOf(CaptureOwnerType.class);
        requestedTypes.addAll(this.allowedOwnerTypes);
        this.allowedOwnerTypes.retainAll(supportedTypes);

        EnumSet<CaptureOwnerType> removed = EnumSet.noneOf(CaptureOwnerType.class);
        removed.addAll(requestedTypes);
        removed.removeAll(this.allowedOwnerTypes);
        if (!removed.isEmpty()) {
            getLogger().warning(
                "Owner platform '" + this.ownerPlatform.getPlatformKey() + "' does not support: " + formatOwnerTypes(removed)
                    + ". These types were disabled."
            );
        }

        return true;
    }

    private String formatOwnerTypes(Set<CaptureOwnerType> types) {
        if (types == null || types.isEmpty()) {
            return "<none>";
        }
        List<String> values = new ArrayList<>();
        for (CaptureOwnerType type : types) {
            if (type == null) {
                continue;
            }
            values.add(type.name().toLowerCase(Locale.ROOT));
        }
        if (values.isEmpty()) {
            return "<none>";
        }
        return String.join(", ", values);
    }

    private EnumSet<CaptureOwnerType> parseAllowedOwnerTypes(List<String> values) {
        EnumSet<CaptureOwnerType> set = EnumSet.noneOf(CaptureOwnerType.class);
        if (values == null) {
            return set;
        }
        for (String value : values) {
            CaptureOwnerType type = CaptureOwnerType.fromConfigValue(value, null);
            if (type != null) {
                set.add(type);
            }
        }
        return set;
    }

    private boolean validateConfig() {
        try {
            ConfigurationSection capturePointSection;
            int maxActiveCaptures;
            int maxCapturePoints;
            int autoSaveInterval;
            long errorThrottle;
            long sessionTimeout;
            int minPlayers = this.config.getInt("settings.min-online-players", 5);
            if (minPlayers < 1) {
                this.getLogger().warning("Invalid min-online-players value, using default: 5");
            }
            if ((sessionTimeout = this.config.getLong("settings.session-timeout", 3600000L)) < 300000L) {
                this.getLogger().warning("Invalid session-timeout value, using default: 3600000");
            }
            if ((errorThrottle = this.config.getLong("settings.error-throttle-ms", 60000L)) < 1000L) {
                this.getLogger().warning("Invalid error-throttle-ms value, using default: 60000");
            }
            if ((autoSaveInterval = this.config.getInt("settings.auto-save-interval", 300)) < 60) {
                this.getLogger().warning("Invalid auto-save-interval value, using default: 300");
            }
            if ((maxCapturePoints = this.config.getInt("settings.max-capture-points", 50)) < 1) {
                this.getLogger().warning("Invalid max-capture-points value, using default: 50");
            }
            if ((maxActiveCaptures = this.config.getInt("settings.capture-concurrency.max-active-zones", 10)) < 1) {
                this.getLogger().warning("Invalid settings.capture-concurrency.max-active-zones value, using default: 10");
            }
            if ((capturePointSection = this.getCapturePointConfigSection()) != null) {
                int minRadius = capturePointSection.getInt("min-radius", 1);
                int maxRadius = capturePointSection.getInt("max-radius", 10);
                if (minRadius < 1) {
                    this.getLogger().warning("Invalid min-radius value, using default: 1");
                }
                if (maxRadius < minRadius) {
                    this.getLogger().warning("Invalid max-radius value, using default: 10");
                }
                double minReward = capturePointSection.getDouble("min-reward", 100.0);
                double maxReward = capturePointSection.getDouble("max-reward", 10000.0);
                if (minReward < 0.0) {
                    this.getLogger().warning("Invalid min-reward value, using default: 100");
                }
                if (maxReward < minReward) {
                    this.getLogger().warning("Invalid max-reward value, using default: 10000");
                }
                int minPlayersPerPoint = capturePointSection.getInt("min-players", 1);
                int maxPlayersPerPoint = capturePointSection.getInt("max-players", 10);
                if (minPlayersPerPoint < 1) {
                    this.getLogger().warning("Invalid min-players value, using default: 1");
                }
                if (maxPlayersPerPoint < minPlayersPerPoint) {
                    this.getLogger().warning("Invalid max-players value, using default: 10");
                }
            }
            for (CapturePoint point : this.capturePoints.values()) {
                if (point.isCircle()) {
                    if (point.getChunkRadius() < this.getCapturePointInt("min-radius", 1)) {
                        this.getLogger().warning("Point " + point.getId() + " radius is below configured minimum.");
                    }
                    if (point.getChunkRadius() > this.getCapturePointInt("max-radius", 10)) {
                        this.getLogger().warning("Point " + point.getId() + " radius is above configured maximum.");
                    }
                }
                if (point.getReward() < this.getCapturePointDouble("min-reward", 100.0)) {
                    this.getLogger().warning("Point " + point.getId() + " reward is below configured minimum.");
                }
                if (point.getReward() > this.getCapturePointDouble("max-reward", 10000.0)) {
                    this.getLogger().warning("Point " + point.getId() + " reward is above configured maximum.");
                }
                if (point.getMinPlayers() < this.getCapturePointInt("min-players", 1)) {
                    this.getLogger().warning("Point " + point.getId() + " min players is below configured minimum.");
                }
                if (point.getMaxPlayers() <= this.getCapturePointInt("max-players", 10)) continue;
                this.getLogger().warning("Point " + point.getId() + " max players is above configured maximum.");
            }
            String boundaryMode = this.config.getString("boundary-visuals.mode", "ON");
            if (!"ON".equalsIgnoreCase(boundaryMode) && !"REDUCED".equalsIgnoreCase(boundaryMode) && !"OFF".equalsIgnoreCase(boundaryMode)) {
                this.getLogger().warning("Invalid boundary-visuals.mode value. Expected ON, REDUCED, or OFF.");
            }
            if (this.config.getInt("boundary-visuals.on.visibility-range-chunks", 8) < 0) {
                this.getLogger().warning("Invalid boundary-visuals.on.visibility-range-chunks value, using default: 8");
            }
            if (this.config.getInt("boundary-visuals.reduced.visibility-range-chunks", 6) < 0) {
                this.getLogger().warning("Invalid boundary-visuals.reduced.visibility-range-chunks value, using default: 6");
            }
            Object bossbarVisibilityRaw = this.config.get("settings.bossbar-visibility-radius-chunks");
            if (bossbarVisibilityRaw == null && this.config.isSet("bossbar.visibility-radius-chunks")) {
                bossbarVisibilityRaw = this.config.get("bossbar.visibility-radius-chunks");
            }
            if (parseBossbarVisibilityExtraChunks(bossbarVisibilityRaw) == null) {
                this.getLogger().warning(
                    "Invalid settings.bossbar-visibility-radius-chunks value. Use false for global visibility or a number >= 0."
                );
            }
            this.getLogger().info("Configuration validation completed successfully");
            return true;
        }
        catch (Exception e) {
            this.getLogger().severe("Error validating config: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void startSessionTimeoutChecker() {
        if (this.sessionTimeoutTask != null && !this.sessionTimeoutTask.isCancelled()) {
            this.sessionTimeoutTask.cancel();
        }
        long timeout = this.config.getLong("settings.session-timeout", 3600000L);
        this.sessionTimeoutTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkSessionTimeouts(timeout);
            }
        }.runTaskTimer(this, 1200L, 1200L);
    }

    private void checkSessionTimeouts(long timeout) {
        long currentTime = System.currentTimeMillis();
        List<String> timedOutPoints = new ArrayList<>();
        for (Map.Entry<String, CaptureSession> entry : this.activeSessions.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (currentTime - entry.getValue().getStartTime() > timeout) {
                timedOutPoints.add(entry.getKey());
            }
        }
        for (String pointId : timedOutPoints) {
            this.cancelCapture(pointId, "Session timeout");
        }
    }

    private void startAutoSave() {
        if (this.autoSaveTask != null && !this.autoSaveTask.isCancelled()) {
            this.autoSaveTask.cancel();
        }
        int interval = this.config.getInt("settings.auto-save-interval", 300);
        this.autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                saveCapturePoints();
            }
        }.runTaskTimer(this, interval * 20L, interval * 20L);
    }

    private void startHourlyRewards() {
        if (this.hourlyRewardTask != null && !this.hourlyRewardTask.isCancelled()) {
            this.hourlyRewardTask.cancel();
        }
        lastHourlyRewardTimes.clear();
        this.hourlyRewardNextDueTimes.clear();
        this.hourlyRewardScheduleDirty = true;
        this.hourlyRewardEarliestDueAt = Long.MAX_VALUE;
        this.hourlyRewardScheduleLastRebuildAt = 0L;

        long intervalTicks = 20L;
        this.hourlyRewardTask = new BukkitRunnable() {
            @Override
            public void run() {
                distributeHourlyRewards();
            }
        }.runTaskTimer(this, intervalTicks, intervalTicks);
    }

    private void startWeeklyResetTask() {
        if (this.weeklyResetTask != null && !this.weeklyResetTask.isCancelled()) {
            this.weeklyResetTask.cancel();
        }
        this.weeklyResetTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkWeeklyReset();
            }
        }.runTaskTimer(this, 200L, 72000L); // Check hourly
    }

    private void checkWeeklyReset() {
        LocalDateTime now = LocalDateTime.now();
        if (zoneConfigManager == null) {
            return;
        }

        List<CapturePoint> resetPoints = new ArrayList<>();
        long today = now.toLocalDate().toEpochDay();

        for (CapturePoint point : this.capturePoints.values()) {
            String pointId = point.getId();
            if (!zoneConfigManager.getBoolean(pointId, "weekly-reset.enabled", true)) {
                continue;
            }

            DayOfWeek targetDay = parseDay(zoneConfigManager.getString(pointId, "weekly-reset.day", "FRIDAY"));
            LocalTime targetTime = parseTime(zoneConfigManager.getString(pointId, "weekly-reset.time", "00:00"));
            if (now.getDayOfWeek() != targetDay) {
                continue;
            }
            if (now.toLocalTime().isBefore(targetTime)) {
                continue;
            }

            long lastReset = lastWeeklyResetEpochDays.getOrDefault(pointId, -1L);
            if (lastReset == today) {
                continue;
            }

            resetPoints.add(point);
            lastWeeklyResetEpochDays.put(pointId, today);
        }

        if (resetPoints.isEmpty()) {
            return;
        }

        performWeeklyReset(resetPoints);
        saveCapturePoints();
    }

    private DayOfWeek parseDay(String value) {
        try {
            return DayOfWeek.valueOf(value.toUpperCase());
        } catch (Exception e) {
            return DayOfWeek.FRIDAY;
        }
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value);
        } catch (Exception e) {
            return LocalTime.MIDNIGHT;
        }
    }

    private boolean isCaptureWithinTimeWindow(String pointId) {
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        boolean enabled = zoneManager != null
            ? zoneManager.getBoolean(pointId, "time-window.enabled", false)
            : this.config.getBoolean("time-window.enabled", false);
        if (!enabled) {
            return true;
        }

        String startRaw = zoneManager != null
            ? zoneManager.getString(pointId, "time-window.start-time", "00:00")
            : this.config.getString("time-window.start-time", "00:00");
        String endRaw = zoneManager != null
            ? zoneManager.getString(pointId, "time-window.end-time", "23:59")
            : this.config.getString("time-window.end-time", "23:59");
        LocalTime start = parseTime(startRaw);
        LocalTime end = parseTime(endRaw);
        LocalTime now = LocalTime.now();
        if (start.equals(end)) {
            return true;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private boolean isCaptureWithinAllowedHours(String pointId) {
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        int startHour = zoneManager != null
            ? zoneManager.getInt(pointId, "capture-conditions.allowed-hours.start", 0)
            : this.config.getInt("capture-conditions.allowed-hours.start", 0);
        int endHour = zoneManager != null
            ? zoneManager.getInt(pointId, "capture-conditions.allowed-hours.end", 24)
            : this.config.getInt("capture-conditions.allowed-hours.end", 24);
        startHour = Math.max(0, Math.min(24, startHour));
        endHour = Math.max(0, Math.min(24, endHour));
        if (startHour == endHour) {
            return true;
        }

        int currentHour = LocalTime.now().getHour();
        if (startHour < endHour) {
            return currentHour >= startHour && (endHour == 24 || currentHour < endHour);
        }
        if (endHour == 24) {
            return currentHour >= startHour;
        }
        return currentHour >= startHour || currentHour < endHour;
    }

    private boolean isCaptureAllowedForCurrentWeather(String pointId, CapturePoint point) {
        if (point == null || point.getLocation() == null || point.getLocation().getWorld() == null) {
            return true;
        }
        World world = point.getLocation().getWorld();
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        boolean allowRain = zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.allow-capture-in-rain", true)
            : this.config.getBoolean("capture-conditions.allow-capture-in-rain", true);
        boolean allowStorm = zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.allow-capture-in-storm", true)
            : this.config.getBoolean("capture-conditions.allow-capture-in-storm", true);

        if (world.isThundering()) {
            return allowStorm;
        }
        if (world.hasStorm()) {
            return allowRain;
        }
        return true;
    }

    private void performWeeklyReset(List<CapturePoint> points) {
        int resetCount = 0;
        for (CapturePoint point : points) {
            if (point == null) {
                continue;
            }
            String pointId = point.getId();

            if (this.activeSessions.containsKey(pointId)) {
                this.stopCapture(pointId, "Weekly reset");
            }

            point.setCapturingOwner(null);
            point.setCaptureProgress(0.0);
            point.setControllingOwner(null);
            point.setLastCapturingTown("");
            point.setLastCaptureTime(0L);
            point.setColor("#8B0000");
            point.setFirstCaptureBonusAvailable(true);
            resetCount++;
        }

        if (resetCount == 0) {
            return;
        }

        if (this.hasMapProviders()) {
            this.updateAllMarkers();
        }
        this.updateAllHolograms();
        invalidateHourlyRewardSchedule();

        String message = Messages.get("messages.weekly.reset", Map.of("count", String.valueOf(resetCount)));
        broadcastMessage(message);
        playCaptureSound("point-reset");
        
        if (discordWebhook != null) {
            discordWebhook.sendWeeklyReset(resetCount);
        }
    }

    public void onDisable() {
        unregisterApiService();

        if (this.cuboidSelectionManager != null) {
            this.cuboidSelectionManager.clearAllSelections();
        }
        cleanupActiveCapturesForLifecycle("shutdown");
        removeAllCaptureBossBars();
        cancelAllCaptureTasks();
        cancelAllBoundaryTasks();
        cancelTask(this.visualRefreshTask);
        this.visualRefreshTask = null;
        this.saveCapturePoints();
        this.flushPendingCapturePointSave();
        cancelTask(this.capturePointSaveTask);
        this.capturePointSaveTask = null;

        cancelTask(this.hourlyRewardTask);
        cancelTask(this.sessionTimeoutTask);
        cancelTask(this.autoSaveTask);
        cancelTask(this.weeklyResetTask);
        cancelTask(this.dynmapUpdateTask);
        cancelTask(this.statisticsAutoSaveTask);

        if (this.reinforcementListener != null) {
            this.reinforcementListener.shutdown();
        }

        cleanupPlaceholderApiExpansion();
        cleanupKothManager();
        cleanupConquestManager();
        cleanupPotionRewardManager();
        cleanupPermissionRewards();
        cleanupHolograms();
        cleanupMapProviders();

        if (shopManager != null || shopListener != null || shopEconomyAdapter != null) {
            shutdownShopSystem();
            getLogger().info("Shop system shutdown complete");
        }
        
        this.cleanupResources();
        this.getLogger().info("CaptureZones has been disabled!");
    }

    private void registerApiService() {
        unregisterApiService();
        this.apiService = new CaptureZonesApiService(this);
        getServer().getServicesManager().register(
            CaptureZonesApi.class,
            this.apiService,
            this,
            ServicePriority.Normal
        );
        getLogger().info("CaptureZones addon API registered (version " + this.apiService.getApiVersion() + ").");
    }

    private void unregisterApiService() {
        if (this.apiService != null) {
            getServer().getServicesManager().unregister(CaptureZonesApi.class, this.apiService);
        }
        this.apiService = null;
    }

    private void cleanupResources() {
        removeAllCaptureBossBars();
        cancelAllCaptureTasks();
        cancelAllBoundaryTasks();

        this.capturePoints.clear();
        this.capturePointSpatialIndex.clear();
        this.capturePointsByWorld.clear();
        this.capturePointSpatialIndexDirty = true;
        this.activeSessions.clear();
        this.pointTypes.clear();
        this.warnedPlayers.clear();
        this.originalBlocks.clear();
        this.firstJoinTimes.clear();
        this.hiddenBossBars.clear();
        this.boundaryPointsByPlayer.clear();
        this.boundaryPlayerTasks.clear();
        this.boundaryTasks.clear();
        this.disabledNotifications.clear();
        this.warnedLegacyCapturePointPaths.clear();
        this.lastHourlyRewardTimes.clear();
        this.hourlyRewardNextDueTimes.clear();
        this.captureAttempts.clear();
        this.successfulCaptures.clear();
        this.lastErrorTime.clear();
        this.lastWeeklyResetEpochDays.clear();
        this.dirtyMarkerPointIds.clear();
        this.dirtyHologramPointIds.clear();
        this.lastBossBarAudienceSyncAt.clear();
        this.mapProviders.clear();
        this.hologramManager = null;
        this.kothManager = null;
        this.conquestManager = null;
        this.potionRewardManager = null;
        this.townUpdater = null;
        this.placeholderExpansion = null;
        this.hourlyRewardTask = null;
        this.sessionTimeoutTask = null;
        this.autoSaveTask = null;
        this.weeklyResetTask = null;
        this.dynmapUpdateTask = null;
        this.statisticsAutoSaveTask = null;
        this.visualRefreshTask = null;
        this.capturePointSaveTask = null;
        this.capturePointsSaveDirty = false;
        this.fullMarkerRefreshQueued = false;
        this.fullHologramRefreshQueued = false;
        this.hourlyRewardScheduleDirty = true;
        this.hourlyRewardEarliestDueAt = Long.MAX_VALUE;
        this.hourlyRewardScheduleLastRebuildAt = 0L;
        this.cuboidSelectionManager = null;
        this.zoneItemRewardEditor = null;
        this.permissionRewardManager = null;
        this.apiService = null;
    }

    private void createDefaultConfig() {
        if (!this.config.contains("settings.min-online-players")) {
            this.config.set("settings.min-online-players", (Object)5);
        }
        if (!this.config.contains("settings.auto-show-boundaries")) {
            this.config.set("settings.auto-show-boundaries", (Object)true);
        }
        if (!this.config.contains("settings.capture-teleport-enabled")) {
            this.config.set("settings.capture-teleport-enabled", (Object)true);
        }
        if (!this.config.contains("settings.capture-concurrency.allow-multiple-zones")) {
            this.config.set("settings.capture-concurrency.allow-multiple-zones", (Object)true);
        }
        if (!this.config.contains("settings.capture-concurrency.max-active-zones")) {
            this.config.set("settings.capture-concurrency.max-active-zones", (Object)10);
        }
        if (!this.config.contains("settings.bossbar-visibility-radius-chunks")) {
            this.config.set("settings.bossbar-visibility-radius-chunks", (Object)false);
        }
        if (!this.config.contains("reinforcements.spawn-rate.global-max-per-tick")) {
            this.config.set("reinforcements.spawn-rate.global-max-per-tick", 8);
        }
        if (!this.config.contains("blocked-commands")) {
            ArrayList<String> defaultCommands = new ArrayList<String>();
            defaultCommands.add("/home");
            defaultCommands.add("/tp");
            defaultCommands.add("/spawn");
            this.config.set("blocked-commands", defaultCommands);
        }
        if (!this.config.contains("towny.mode")) {
            this.config.set("towny.mode", "auto");
        }
        if (!this.config.contains("towny.allow-town-capture")) {
            this.config.set("towny.allow-town-capture", true);
        }
        if (!this.config.contains("towny.allow-nation-capture")) {
            this.config.set("towny.allow-nation-capture", true);
        }
        if (!this.config.contains("capture-owners.allowed")) {
            this.config.set("capture-owners.allowed", List.of("player", "town", "nation"));
        }
        if (!this.config.contains("capture-owners.default-owner-type")) {
            this.config.set("capture-owners.default-owner-type", "town");
        }
        if (!this.config.contains("capture-owners.platform")) {
            this.config.set("capture-owners.platform", "auto");
        }
        if (!this.config.contains("boundary-visuals.mode")) {
            this.config.set("boundary-visuals.mode", "ON");
            this.config.set("boundary-visuals.on.interval-ticks", 10);
            this.config.set("boundary-visuals.on.min-sides", 256);
            this.config.set("boundary-visuals.on.sides-multiplier", 4.0);
            this.config.set("boundary-visuals.on.vertical-range.min", -10);
            this.config.set("boundary-visuals.on.vertical-range.max", 40);
            this.config.set("boundary-visuals.on.visibility-range-chunks", 8);
            this.config.set("boundary-visuals.reduced.interval-ticks", 30);
            this.config.set("boundary-visuals.reduced.min-sides", 96);
            this.config.set("boundary-visuals.reduced.sides-multiplier", 2.0);
            this.config.set("boundary-visuals.reduced.vertical-range.min", -2);
            this.config.set("boundary-visuals.reduced.vertical-range.max", 20);
            this.config.set("boundary-visuals.reduced.visibility-range-chunks", 6);
        }
        if (!this.config.contains("boundary-visuals.on.visibility-range-chunks")) {
            this.config.set("boundary-visuals.on.visibility-range-chunks", 8);
        }
        if (!this.config.contains("boundary-visuals.reduced.visibility-range-chunks")) {
            this.config.set("boundary-visuals.reduced.visibility-range-chunks", 6);
        }
        if (!this.config.contains("koth.enabled")) {
            this.config.set("koth.enabled", false);
        }
        if (!this.config.contains("koth.schedule.enabled")) {
            this.config.set("koth.schedule.enabled", true);
        }
        if (!this.config.contains("koth.schedule.days")) {
            this.config.set("koth.schedule.days", List.of("ALL"));
        }
        if (!this.config.contains("koth.schedule.times")) {
            this.config.set("koth.schedule.times", List.of("20:00"));
        }
        if (!this.config.contains("koth.schedule.check-interval-seconds")) {
            this.config.set("koth.schedule.check-interval-seconds", 30);
        }
        if (!this.config.contains("koth.schedule.allow-overlap")) {
            this.config.set("koth.schedule.allow-overlap", false);
        }
        if (!this.config.contains("koth.activation.selection-mode")) {
            this.config.set("koth.activation.selection-mode", "ALL");
        }
        if (!this.config.contains("koth.activation.zones")) {
            this.config.set("koth.activation.zones", new ArrayList<>());
        }
        if (!this.config.contains("koth.activation.max-zones")) {
            this.config.set("koth.activation.max-zones", 1);
        }
        if (!this.config.contains("koth.gameplay.capture-seconds")) {
            this.config.set("koth.gameplay.capture-seconds", 180);
        }
        if (!this.config.contains("koth.gameplay.hold-radius-blocks")) {
            this.config.set("koth.gameplay.hold-radius-blocks", 5.0);
        }
        if (!this.config.contains("koth.gameplay.reset-progress-when-empty")) {
            this.config.set("koth.gameplay.reset-progress-when-empty", false);
        }
        if (!this.config.contains("koth.gameplay.contested-behavior")) {
            this.config.set("koth.gameplay.contested-behavior", "PAUSE");
        }
        if (!this.config.contains("koth.rewards.money.enabled")) {
            this.config.set("koth.rewards.money.enabled", true);
        }
        if (!this.config.contains("koth.rewards.money.amount")) {
            this.config.set("koth.rewards.money.amount", -1.0);
        }
        if (!this.config.contains("koth.rewards.items.enabled")) {
            this.config.set("koth.rewards.items.enabled", true);
        }
        if (!this.config.contains("koth.rewards.items.use-zone-item-rewards")) {
            this.config.set("koth.rewards.items.use-zone-item-rewards", true);
        }
        if (!this.config.contains("koth.rewards.items.inventory-full-behavior")) {
            this.config.set("koth.rewards.items.inventory-full-behavior", "DROP");
        }
        if (!this.config.contains("koth.rewards.permissions.enabled")) {
            this.config.set("koth.rewards.permissions.enabled", true);
        }
        if (!this.config.contains("koth.rewards.permissions.use-zone-permission-rewards")) {
            this.config.set("koth.rewards.permissions.use-zone-permission-rewards", true);
        }
        if (!this.config.contains("koth.hologram.use-koth-style")) {
            this.config.set("koth.hologram.use-koth-style", false);
        }
        if (!this.config.contains("koth.hologram.active-lines")) {
            this.config.set(
                "koth.hologram.active-lines",
                List.of(
                    "&c&lKOTH: {zone}",
                    "&7Status: &a{koth_status}",
                    "&7Holder: &f{koth_holder}",
                    "&7Progress: &e{koth_progress}%",
                    "&7Time Left: &f{koth_time_left}"
                )
            );
        }
        if (!this.config.contains("koth.hologram.inactive-lines")) {
            this.config.set(
                "koth.hologram.inactive-lines",
                List.of(
                    "&8&lKOTH: {zone}",
                    "&7Status: &c{koth_status}",
                    "&7Waiting for next event"
                )
            );
        }
        if (!this.config.contains("conquest.enabled")) {
            this.config.set("conquest.enabled", false);
        }
        if (!this.config.contains("conquest.default-profile")) {
            this.config.set("conquest.default-profile", "default");
        }
        if (!this.config.contains("conquest.tick-interval-seconds")) {
            this.config.set("conquest.tick-interval-seconds", 30);
        }
        if (!this.config.contains("conquest.profiles.default.owner-type")) {
            this.config.set("conquest.profiles.default.owner-type", "town");
        }
        if (!this.config.contains("conquest.profiles.default.starting-tickets")) {
            this.config.set("conquest.profiles.default.starting-tickets", 500);
        }
        if (!this.config.contains("conquest.profiles.default.capture-ticket-gain")) {
            this.config.set("conquest.profiles.default.capture-ticket-gain", 0);
        }
        if (!this.config.contains("conquest.profiles.default.capture-ticket-loss")) {
            this.config.set("conquest.profiles.default.capture-ticket-loss", 25);
        }
        if (!this.config.contains("conquest.profiles.default.point-drain")) {
            this.config.set("conquest.profiles.default.point-drain", 1);
        }
        if (!this.config.contains("conquest.profiles.default.min-teams")) {
            this.config.set("conquest.profiles.default.min-teams", 2);
        }
        if (!this.config.contains("conquest.profiles.default.teams")) {
            this.config.set("conquest.profiles.default.teams", new ArrayList<>());
        }
        if (!this.config.contains("conquest.profiles.default.zones")) {
            this.config.set("conquest.profiles.default.zones", new ArrayList<>());
        }
        if (!this.config.contains("potion-rewards.refresh-interval-ticks")) {
            this.config.set("potion-rewards.refresh-interval-ticks", 160);
        }
        if (!this.config.contains("pl3xmap.enabled")) {
            this.config.set("pl3xmap.enabled", false);
        }
        if (!this.config.contains("pl3xmap.layer-id")) {
            this.config.set("pl3xmap.layer-id", "capturezones");
        }
        if (!this.config.contains("pl3xmap.layer-label")) {
            this.config.set("pl3xmap.layer-label", "Capture Zones");
        }
        if (!this.config.contains("pl3xmap.priority")) {
            this.config.set("pl3xmap.priority", 10);
        }
        if (!this.config.contains("pl3xmap.center-marker.enabled")) {
            this.config.set("pl3xmap.center-marker.enabled", true);
        }
        if (!this.config.contains("pl3xmap.center-marker.icon")) {
            this.config.set("pl3xmap.center-marker.icon", "blueflag");
        }
        try {
            this.saveConfig();
        } catch (Exception e) {
            this.getLogger().warning("Failed to save default config: " + e.getMessage());
        }
        this.loadPointTypes();
    }

    private ConfigurationSection getCapturePointConfigSection() {
        if (this.config.isSet("capture-point")) {
            return this.config.getConfigurationSection("capture-point");
        }
        if (this.config.isSet("settings.capture-point")) {
            warnLegacyCapturePointPath("capture-point", "settings.capture-point");
            return this.config.getConfigurationSection("settings.capture-point");
        }
        ConfigurationSection section = this.config.getConfigurationSection("capture-point");
        if (section != null) {
            return section;
        }
        return this.config.getConfigurationSection("settings.capture-point");
    }

    private int getCapturePointInt(String key, int fallback) {
        String primaryPath = "capture-point." + key;
        if (this.config.isSet(primaryPath)) {
            return this.config.getInt(primaryPath, fallback);
        }
        String legacyPath = "settings.capture-point." + key;
        if (this.config.isSet(legacyPath)) {
            warnLegacyCapturePointPath(primaryPath, legacyPath);
            return this.config.getInt(legacyPath, fallback);
        }
        if (this.config.contains(primaryPath)) {
            return this.config.getInt(primaryPath, fallback);
        }
        if (this.config.contains(legacyPath)) {
            warnLegacyCapturePointPath(primaryPath, legacyPath);
            return this.config.getInt(legacyPath, fallback);
        }
        return fallback;
    }

    private double getCapturePointDouble(String key, double fallback) {
        String primaryPath = "capture-point." + key;
        if (this.config.isSet(primaryPath)) {
            return this.config.getDouble(primaryPath, fallback);
        }
        String legacyPath = "settings.capture-point." + key;
        if (this.config.isSet(legacyPath)) {
            warnLegacyCapturePointPath(primaryPath, legacyPath);
            return this.config.getDouble(legacyPath, fallback);
        }
        if (this.config.contains(primaryPath)) {
            return this.config.getDouble(primaryPath, fallback);
        }
        if (this.config.contains(legacyPath)) {
            warnLegacyCapturePointPath(primaryPath, legacyPath);
            return this.config.getDouble(legacyPath, fallback);
        }
        return fallback;
    }

    private boolean isMultiZoneConcurrentCaptureEnabled() {
        return this.config.getBoolean("settings.capture-concurrency.allow-multiple-zones", true);
    }

    private int getConfiguredMaxConcurrentCaptureZones() {
        int configuredLimit = this.config.getInt("settings.capture-concurrency.max-active-zones", 10);
        if (configuredLimit < 1) {
            return 10;
        }
        return configuredLimit;
    }

    private void warnLegacyCapturePointPath(String primaryPath, String legacyPath) {
        if (this.warnedLegacyCapturePointPaths.putIfAbsent(legacyPath, true) == null) {
            this.getLogger().warning("Using legacy config path '" + legacyPath + "'. Please migrate to '" + primaryPath + "'.");
        }
    }

    public boolean isTownyAvailable() {
        return this.townyAvailable;
    }

    public boolean isTownyIntegrationEnabled() {
        return this.townyAvailable && this.townyMode != TownyMode.DISABLED;
    }

    public CaptureOwnerType getDefaultOwnerType() {
        return this.defaultOwnerType;
    }

    public OwnerPlatformAdapter getOwnerPlatform() {
        return this.ownerPlatform;
    }

    public boolean isOwnerTypeAllowed(CaptureOwnerType type) {
        return type != null && this.allowedOwnerTypes.contains(type);
    }

    private CaptureOwner resolveCaptureOwner(Player player) {
        if (player == null || this.ownerPlatform == null) {
            return null;
        }
        for (CaptureOwnerType ownerType : this.buildOwnerResolutionOrder()) {
            String ownerName = this.ownerPlatform.resolveOwnerName(player, ownerType);
            if (ownerName == null || ownerName.trim().isEmpty()) {
                continue;
            }
            CaptureOwner owner = normalizeOwner(ownerType, ownerName);
            if (owner != null) {
                return owner;
            }
        }
        return null;
    }

    private List<CaptureOwnerType> buildOwnerResolutionOrder() {
        List<CaptureOwnerType> ordered = new ArrayList<>();
        if (this.defaultOwnerType != null && this.allowedOwnerTypes.contains(this.defaultOwnerType)) {
            ordered.add(this.defaultOwnerType);
        }
        for (CaptureOwnerType type : CaptureOwnerType.values()) {
            if (type == null || type == this.defaultOwnerType || !this.allowedOwnerTypes.contains(type)) {
                continue;
            }
            ordered.add(type);
        }
        return ordered;
    }

    private CaptureOwner normalizeOwner(CaptureOwnerType type, String ownerName) {
        if (ownerName == null || ownerName.trim().isEmpty()) {
            return null;
        }

        CaptureOwnerType resolvedType = type != null ? type : this.defaultOwnerType;
        String normalizedName = ownerName.trim();
        if (this.ownerPlatform != null) {
            String adapterNormalized = this.ownerPlatform.normalizeOwnerName(normalizedName, resolvedType);
            if (adapterNormalized != null && !adapterNormalized.trim().isEmpty()) {
                normalizedName = adapterNormalized.trim();
            }
        }

        String ownerId = this.ownerPlatform != null ? this.ownerPlatform.resolveOwnerId(normalizedName, resolvedType) : null;
        if (ownerId == null || ownerId.trim().isEmpty()) {
            ownerId = buildOwnerId(resolvedType, normalizedName);
        }
        return new CaptureOwner(resolvedType, ownerId, normalizedName);
    }

    private String buildOwnerId(CaptureOwnerType type, String ownerName) {
        if (ownerName == null || ownerName.trim().isEmpty()) {
            return null;
        }
        String normalizedName = ownerName.trim();

        if (type == CaptureOwnerType.PLAYER) {
            Player online = Bukkit.getPlayerExact(normalizedName);
            if (online != null) {
                return online.getUniqueId().toString();
            }
            try {
                return Bukkit.getOfflinePlayer(normalizedName).getUniqueId().toString();
            } catch (Exception ignored) {
                // fall through to text id
            }
        }

        return type.name().toLowerCase() + ":" + normalizedName.toLowerCase().replace(' ', '_');
    }

    public boolean doesPlayerMatchOwner(Player player, String ownerName) {
        if (player == null || ownerName == null || ownerName.trim().isEmpty() || this.ownerPlatform == null) {
            return false;
        }
        return this.ownerPlatform.doesPlayerMatchOwner(player, ownerName, this.defaultOwnerType);
    }

    public boolean doesPlayerMatchOwner(Player player, CaptureOwner owner) {
        if (player == null || owner == null || this.ownerPlatform == null) {
            return false;
        }
        return this.ownerPlatform.doesPlayerMatchOwner(player, owner);
    }

    private boolean areOwnersEquivalent(CaptureOwner first, CaptureOwner second) {
        if (first == null || second == null) {
            return false;
        }
        return first.isSameOwner(second);
    }

    private ContestedState evaluateContestedState(CapturePoint point, CaptureOwner capturingOwner) {
        if (point == null || capturingOwner == null) {
            return ContestedState.clear();
        }

        Location center = point.getLocation();
        if (center == null || center.getWorld() == null) {
            return ContestedState.clear();
        }

        int capturingPlayers = 0;
        int opposingPlayers = 0;
        Set<String> opposingOwnerKeys = new HashSet<>();

        for (Player online : center.getWorld().getPlayers()) {
            if (online == null || !online.isOnline() || online.isDead()) {
                continue;
            }
            if (!isWithinZone(point, online.getLocation())) {
                continue;
            }

            CaptureOwner playerOwner = resolveCaptureOwner(online);
            if (playerOwner == null || playerOwner.getDisplayName() == null || playerOwner.getDisplayName().isEmpty()) {
                continue;
            }

            if (areOwnersEquivalent(playerOwner, capturingOwner)) {
                capturingPlayers++;
                continue;
            }

            opposingPlayers++;
            opposingOwnerKeys.add(playerOwner.getType().name() + ":" + playerOwner.getDisplayName().toLowerCase());
        }

        boolean contested = capturingPlayers > 0 && !opposingOwnerKeys.isEmpty();
        return new ContestedState(contested, capturingPlayers, opposingPlayers, opposingOwnerKeys.size());
    }

    private boolean hasAnyCapturingOwnerPlayerInZone(CapturePoint point, CaptureOwner capturingOwner) {
        if (point == null || capturingOwner == null) {
            return false;
        }

        Location center = point.getLocation();
        if (center == null || center.getWorld() == null) {
            return false;
        }

        for (Player online : center.getWorld().getPlayers()) {
            if (online == null || !online.isOnline() || online.isDead()) {
                continue;
            }
            if (!isWithinZone(point, online.getLocation())) {
                continue;
            }
            if (doesPlayerMatchOwner(online, capturingOwner)) {
                return true;
            }
        }
        return false;
    }

    private ContestedProgressPolicy resolveContestedProgressPolicy(String pointId) {
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        String rawPolicy = zoneManager != null
            ? zoneManager.getString(pointId, "capture-conditions.contested.progress-policy", "PAUSE")
            : this.config.getString("capture-conditions.contested.progress-policy", "PAUSE");
        return ContestedProgressPolicy.fromConfigValue(rawPolicy);
    }

    private int resolveContestedPolicySeconds(String pointId, ContestedProgressPolicy policy) {
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        if (policy == ContestedProgressPolicy.DECAY) {
            return zoneManager != null
                ? zoneManager.getInt(pointId, "capture-conditions.contested.decay-seconds", 1)
                : this.config.getInt("capture-conditions.contested.decay-seconds", 1);
        }
        if (policy == ContestedProgressPolicy.ROLLBACK) {
            return zoneManager != null
                ? zoneManager.getInt(pointId, "capture-conditions.contested.rollback-seconds", 3)
                : this.config.getInt("capture-conditions.contested.rollback-seconds", 3);
        }
        return 0;
    }

    public boolean isGraceTimerEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.leave-grace.enabled", false)
            : this.config.getBoolean("capture-conditions.leave-grace.enabled", false);
    }

    private int resolveGraceDurationSeconds(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return 0;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        int configured = zoneManager != null
            ? zoneManager.getInt(pointId, "capture-conditions.leave-grace.duration-seconds", 15)
            : this.config.getInt("capture-conditions.leave-grace.duration-seconds", 15);
        return Math.max(0, configured);
    }

    public boolean isAutoCaptureOnEntryEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.auto-capture.enabled", false)
            : this.config.getBoolean("capture-conditions.auto-capture.enabled", false);
    }

    public int getAutoCaptureEntryDebounceSeconds(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return 0;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        int configured = zoneManager != null
            ? zoneManager.getInt(pointId, "capture-conditions.auto-capture.entry-debounce-seconds", 10)
            : this.config.getInt("capture-conditions.auto-capture.entry-debounce-seconds", 10);
        return Math.max(0, configured);
    }

    private boolean isBossbarEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return true;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "bossbar.enabled", true)
            : this.config.getBoolean("bossbar.enabled", true);
    }

    private boolean isContestedBossbarEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return true;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "bossbar.contested-enabled", true)
            : this.config.getBoolean("bossbar.contested-enabled", true);
    }

    private boolean isGraceBossbarEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return true;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "bossbar.grace-enabled", true)
            : this.config.getBoolean("bossbar.grace-enabled", true);
    }

    private boolean isCaptureCountdownEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return true;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture.capture.show-countdown", true)
            : this.config.getBoolean("capture.capture.show-countdown", true);
    }

    private String resolveCaptureBossbarTitleTemplate(String pointId) {
        String fallback = this.config.getString(
            "bossbar.title",
            "&e%town% is capturing %point% (Started by %player%) - %time_left%"
        );
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        if (zoneManager == null) {
            return fallback;
        }
        return zoneManager.getString(pointId, "bossbar.title", fallback);
    }

    private String resolveCaptureBossbarTitle(
        CapturePoint point,
        CaptureSession session,
        String ownerName,
        int remainingSeconds,
        boolean showCountdown
    ) {
        if (point == null) {
            return "";
        }
        String pointId = point.getId();
        String template = resolveCaptureBossbarTitleTemplate(pointId);
        if (template == null || template.trim().isEmpty()) {
            return Messages.get("bossbar.capturing", Map.of(
                "town", ownerName == null ? "" : ownerName,
                "zone", point.getName(),
                "time", formatTime(Math.max(0, remainingSeconds))
            ));
        }
        String starterName = session != null && session.getPlayerName() != null && !session.getPlayerName().isEmpty()
            ? session.getPlayerName()
            : (ownerName == null ? "" : ownerName);
        String rendered = template
            .replace("%town%", ownerName == null ? "" : ownerName)
            .replace("%point%", point.getName())
            .replace("%player%", starterName)
            .replace("%time_left%", showCountdown ? formatTime(Math.max(0, remainingSeconds)) : "");
        return colorize(rendered);
    }

    private BarColor resolveCaptureBossbarColor(String pointId, CaptureSession session) {
        double progress = 0.0;
        if (session != null) {
            int initial = Math.max(1, session.getInitialCaptureTime());
            int remaining = Math.max(0, session.getRemainingCaptureTime());
            progress = 1.0 - ((double) remaining / initial);
        }

        String phaseKey = progress < 0.5 ? "start" : (progress < 0.85 ? "middle" : "end");
        String path = "bossbar.color." + phaseKey;
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        String configured = zoneManager != null
            ? zoneManager.getString(pointId, path, phaseKey.equals("start") ? "RED" : (phaseKey.equals("middle") ? "YELLOW" : "GREEN"))
            : this.config.getString(path, phaseKey.equals("start") ? "RED" : (phaseKey.equals("middle") ? "YELLOW" : "GREEN"));
        BarColor parsed = parseBossbarColor(configured);
        if (parsed != null) {
            return parsed;
        }
        if ("middle".equals(phaseKey)) {
            return BarColor.YELLOW;
        }
        if ("end".equals(phaseKey)) {
            return BarColor.GREEN;
        }
        return BarColor.RED;
    }

    private BarColor parseBossbarColor(String configured) {
        if (configured == null || configured.trim().isEmpty()) {
            return null;
        }
        try {
            return BarColor.valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Integer parseBossbarVisibilityExtraChunks(Object rawValue) {
        if (rawValue == null) {
            return -1;
        }
        if (rawValue instanceof Boolean) {
            return ((Boolean) rawValue).booleanValue() ? null : -1;
        }
        if (rawValue instanceof Number) {
            int numericValue = ((Number) rawValue).intValue();
            return numericValue < 0 ? -1 : numericValue;
        }
        if (rawValue instanceof String) {
            String normalized = ((String) rawValue).trim();
            if (normalized.isEmpty()
                || "false".equalsIgnoreCase(normalized)
                || "global".equalsIgnoreCase(normalized)
                || "off".equalsIgnoreCase(normalized)) {
                return -1;
            }
            try {
                int numericValue = Integer.parseInt(normalized);
                return numericValue < 0 ? -1 : numericValue;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private int resolveBossbarVisibilityExtraChunks() {
        Object rawValue = this.config.get("settings.bossbar-visibility-radius-chunks");
        if (rawValue == null && this.config.isSet("bossbar.visibility-radius-chunks")) {
            rawValue = this.config.get("bossbar.visibility-radius-chunks");
        }
        Integer parsed = parseBossbarVisibilityExtraChunks(rawValue);
        return parsed != null ? parsed.intValue() : -1;
    }

    private boolean isContestedActionbarEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return true;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.progress-display.actionbar.contested.enabled", true)
            : this.config.getBoolean("capture-conditions.progress-display.actionbar.contested.enabled", true);
    }

    private boolean isGraceActionbarEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return true;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.progress-display.actionbar.grace.enabled", true)
            : this.config.getBoolean("capture-conditions.progress-display.actionbar.grace.enabled", true);
    }

    private long resolveContestedActionbarIntervalMs(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return DEFAULT_CONTESTED_ACTIONBAR_INTERVAL_MS;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        long configured = zoneManager != null
            ? zoneManager.getLong(pointId, "capture-conditions.progress-display.actionbar.contested.interval-ms", DEFAULT_CONTESTED_ACTIONBAR_INTERVAL_MS)
            : this.config.getLong("capture-conditions.progress-display.actionbar.contested.interval-ms", DEFAULT_CONTESTED_ACTIONBAR_INTERVAL_MS);
        return Math.max(100L, configured);
    }

    private long resolveGraceActionbarIntervalMs(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return DEFAULT_GRACE_ACTIONBAR_INTERVAL_MS;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        long configured = zoneManager != null
            ? zoneManager.getLong(pointId, "capture-conditions.progress-display.actionbar.grace.interval-ms", DEFAULT_GRACE_ACTIONBAR_INTERVAL_MS)
            : this.config.getLong("capture-conditions.progress-display.actionbar.grace.interval-ms", DEFAULT_GRACE_ACTIONBAR_INTERVAL_MS);
        return Math.max(100L, configured);
    }

    private boolean isCaptureSpeedModifiersEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.speed-modifiers.enabled", false)
            : this.config.getBoolean("capture-conditions.speed-modifiers.enabled", false);
    }

    private boolean isCaptureSpeedPlayerScalingEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.speed-modifiers.owner-player-scaling.enabled", false)
            : this.config.getBoolean("capture-conditions.speed-modifiers.owner-player-scaling.enabled", false);
    }

    private boolean isCaptureCooldownEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.capture-cooldown.enabled", true)
            : this.config.getBoolean("capture-conditions.capture-cooldown.enabled", true);
    }

    private long resolveCaptureCooldownDurationMs(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return 0L;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        long duration = zoneManager != null
            ? zoneManager.getLong(pointId, "capture-conditions.capture-cooldown.duration-ms", 300000L)
            : this.config.getLong("capture-conditions.capture-cooldown.duration-ms", 300000L);
        return Math.max(0L, duration);
    }

    private boolean shouldApplyCooldownForTrigger(String pointId, CooldownTrigger trigger) {
        if (pointId == null || pointId.trim().isEmpty() || trigger == null) {
            return false;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        String path;
        boolean fallback;
        switch (trigger) {
            case SUCCESS:
                path = "capture-conditions.capture-cooldown.on-success";
                fallback = true;
                break;
            case CANCEL:
                path = "capture-conditions.capture-cooldown.on-cancel";
                fallback = false;
                break;
            case FAIL:
                path = "capture-conditions.capture-cooldown.on-fail";
                fallback = false;
                break;
            default:
                return false;
        }
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, path, fallback)
            : this.config.getBoolean(path, fallback);
    }

    private long safeAddMillis(long base, long delta) {
        if (delta <= 0L) {
            return base;
        }
        long remaining = Long.MAX_VALUE - base;
        if (remaining < delta) {
            return Long.MAX_VALUE;
        }
        return base + delta;
    }

    private long resolveActiveCooldownUntil(CapturePoint point, String pointId, long now) {
        if (point == null) {
            return 0L;
        }

        if (!isCaptureCooldownEnabled(pointId)) {
            if (point.getCooldownUntilTime() > 0L) {
                point.setCooldownUntilTime(0L);
            }
            return 0L;
        }

        long storedUntil = point.getCooldownUntilTime();
        if (storedUntil > now) {
            return storedUntil;
        }
        if (storedUntil > 0L) {
            point.setCooldownUntilTime(0L);
        }
        return 0L;
    }

    private boolean applyCaptureCooldown(CapturePoint point, String pointId, CooldownTrigger trigger) {
        if (point == null || pointId == null || pointId.trim().isEmpty() || trigger == null) {
            return false;
        }
        if (!isCaptureCooldownEnabled(pointId) || !shouldApplyCooldownForTrigger(pointId, trigger)) {
            return false;
        }

        long duration = resolveCaptureCooldownDurationMs(pointId);
        if (duration <= 0L) {
            return false;
        }

        long now = System.currentTimeMillis();
        long until = safeAddMillis(now, duration);
        if (point.getCooldownUntilTime() == until) {
            return false;
        }
        point.setCooldownUntilTime(until);
        return true;
    }

    private boolean isAntiInstantRecaptureEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.capture-cooldown.anti-instant-recapture.enabled", false)
            : this.config.getBoolean("capture-conditions.capture-cooldown.anti-instant-recapture.enabled", false);
    }

    private long resolveAntiInstantRecaptureDurationMs(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return 0L;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        long duration = zoneManager != null
            ? zoneManager.getLong(pointId, "capture-conditions.capture-cooldown.anti-instant-recapture.duration-ms", 300000L)
            : this.config.getLong("capture-conditions.capture-cooldown.anti-instant-recapture.duration-ms", 300000L);
        return Math.max(0L, duration);
    }

    private boolean shouldLockPreviousOwner(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.capture-cooldown.anti-instant-recapture.lock-previous-owner", true)
            : this.config.getBoolean("capture-conditions.capture-cooldown.anti-instant-recapture.lock-previous-owner", true);
    }

    private boolean shouldLockPreviousAttacker(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.capture-cooldown.anti-instant-recapture.lock-previous-attacker", false)
            : this.config.getBoolean("capture-conditions.capture-cooldown.anti-instant-recapture.lock-previous-attacker", false);
    }

    private boolean isSameTownRecapturePreventionEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.recapture-policy.prevent-same-town", false)
            : this.config.getBoolean("capture-conditions.recapture-policy.prevent-same-town", false);
    }

    private boolean isSameNationRecapturePreventionEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        ZoneConfigManager zoneManager = this.zoneConfigManager;
        return zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.recapture-policy.prevent-same-nation", false)
            : this.config.getBoolean("capture-conditions.recapture-policy.prevent-same-nation", false);
    }

    private boolean enforceOwnerRecapturePolicy(Player player, CapturePoint point, String pointId, CaptureOwner controllingOwner) {
        if (player == null || point == null || controllingOwner == null || this.ownerPlatform == null) {
            return false;
        }

        boolean preventSameTown = isSameTownRecapturePreventionEnabled(pointId);
        boolean preventSameNation = isSameNationRecapturePreventionEnabled(pointId);
        if (!preventSameTown && !preventSameNation) {
            return false;
        }

        if (preventSameTown && this.ownerPlatform.isPlayerInSameTown(player, controllingOwner)) {
            player.sendMessage(Messages.get("errors.capture-recapture-same-town", Map.of(
                "point", point.getName()
            )));
            return true;
        }

        if (preventSameNation && this.ownerPlatform.isPlayerInSameNation(player, controllingOwner)) {
            player.sendMessage(Messages.get("errors.capture-recapture-same-nation", Map.of(
                "point", point.getName()
            )));
            return true;
        }

        return false;
    }

    private void clearExpiredRecaptureLocks(CapturePoint point, long now) {
        if (point == null) {
            return;
        }
        if (point.getRecaptureLockPreviousOwnerUntilTime() > 0L
            && point.getRecaptureLockPreviousOwnerUntilTime() <= now) {
            point.clearRecaptureLockPreviousOwner();
        }
        if (point.getRecaptureLockPreviousAttackerUntilTime() > 0L
            && point.getRecaptureLockPreviousAttackerUntilTime() <= now) {
            point.clearRecaptureLockPreviousAttacker();
        }
    }

    private boolean enforceRecaptureLocks(Player player, CapturePoint point, String pointId, CaptureOwner challenger, long now) {
        if (player == null || point == null || pointId == null || pointId.trim().isEmpty() || challenger == null) {
            return false;
        }
        if (!isAntiInstantRecaptureEnabled(pointId)) {
            return false;
        }

        clearExpiredRecaptureLocks(point, now);

        if (shouldLockPreviousOwner(pointId)) {
            CaptureOwner lockedOwner = point.getRecaptureLockPreviousOwner();
            long lockedUntil = point.getRecaptureLockPreviousOwnerUntilTime();
            if (lockedOwner != null && lockedUntil > now && areOwnersEquivalent(lockedOwner, challenger)) {
                int secondsLeft = (int) Math.ceil((lockedUntil - now) / 1000.0);
                player.sendMessage(Messages.get("errors.capture-recapture-lock-owner", Map.of(
                    "time", formatTime(Math.max(1, secondsLeft)),
                    "point", point.getName()
                )));
                return true;
            }
        }

        if (shouldLockPreviousAttacker(pointId)) {
            CaptureOwner lockedAttacker = point.getRecaptureLockPreviousAttacker();
            long lockedUntil = point.getRecaptureLockPreviousAttackerUntilTime();
            if (lockedAttacker != null && lockedUntil > now && areOwnersEquivalent(lockedAttacker, challenger)) {
                int secondsLeft = (int) Math.ceil((lockedUntil - now) / 1000.0);
                player.sendMessage(Messages.get("errors.capture-recapture-lock-attacker", Map.of(
                    "time", formatTime(Math.max(1, secondsLeft)),
                    "point", point.getName()
                )));
                return true;
            }
        }

        return false;
    }

    private void applyAntiInstantRecaptureLocks(
        CapturePoint point,
        String pointId,
        CaptureOwner previousOwner,
        CaptureOwner attacker
    ) {
        if (point == null || attacker == null || pointId == null || pointId.trim().isEmpty()) {
            return;
        }
        if (!isAntiInstantRecaptureEnabled(pointId)) {
            return;
        }

        long duration = resolveAntiInstantRecaptureDurationMs(pointId);
        if (duration <= 0L) {
            return;
        }

        long lockUntil = safeAddMillis(System.currentTimeMillis(), duration);

        if (shouldLockPreviousOwner(pointId)
            && previousOwner != null
            && !areOwnersEquivalent(previousOwner, attacker)) {
            point.setRecaptureLockPreviousOwner(previousOwner, lockUntil);
        } else {
            point.clearRecaptureLockPreviousOwner();
        }

        if (shouldLockPreviousAttacker(pointId)) {
            point.setRecaptureLockPreviousAttacker(attacker, lockUntil);
        } else {
            point.clearRecaptureLockPreviousAttacker();
        }
    }

    private void applyContestedProgressPolicy(CaptureSession session, String pointId, ContestedProgressPolicy policy) {
        if (session == null || pointId == null || pointId.trim().isEmpty() || policy == null) {
            return;
        }

        if (policy == ContestedProgressPolicy.PAUSE) {
            return;
        }

        int deltaSeconds = Math.max(0, resolveContestedPolicySeconds(pointId, policy));
        if (deltaSeconds <= 0) {
            return;
        }

        int current = session.getRemainingCaptureTime();
        int maxAllowed = Math.max(1, session.getInitialCaptureTime());
        int updated = Math.min(maxAllowed, current + deltaSeconds);
        session.setRemainingCaptureTime(updated);
    }

    private void updateCaptureBossBar(
        BossBar bossBar,
        CapturePoint point,
        String ownerName,
        CaptureSession session,
        boolean contested,
        ContestedState contestedState
    ) {
        if (bossBar == null || point == null || session == null) {
            return;
        }
        String pointId = point.getId();
        if (!isBossbarEnabled(pointId) || (contested && !isContestedBossbarEnabled(pointId))) {
            bossBar.setVisible(false);
            return;
        }
        bossBar.setVisible(true);
        syncBossBarAudience(bossBar, point, null, false);

        int timeLeft = session.getRemainingCaptureTime();
        String title;
        if (contested) {
            title = Messages.get("bossbar.contested", Map.of(
                "town", ownerName,
                "zone", point.getName(),
                "time", formatTime(timeLeft),
                "owners", String.valueOf(Math.max(1, contestedState.opposingOwners)),
                "players", String.valueOf(Math.max(1, contestedState.opposingPlayers))
            ));
            bossBar.setColor(BarColor.YELLOW);
        } else {
            boolean showCountdown = isCaptureCountdownEnabled(pointId);
            title = resolveCaptureBossbarTitle(point, session, ownerName, timeLeft, showCountdown);
            bossBar.setColor(resolveCaptureBossbarColor(pointId, session));
        }

        bossBar.setTitle(title);
        bossBar.setProgress(Math.max(0.0, (double)timeLeft / Math.max(1, session.getInitialCaptureTime())));
    }

    private void updateGraceBossBar(
        BossBar bossBar,
        CapturePoint point,
        String ownerName,
        CaptureSession session
    ) {
        if (bossBar == null || point == null || session == null) {
            return;
        }
        String pointId = point.getId();
        if (!isBossbarEnabled(pointId) || !isGraceBossbarEnabled(pointId)) {
            bossBar.setVisible(false);
            return;
        }
        bossBar.setVisible(true);
        syncBossBarAudience(bossBar, point, null, false);

        int graceLeft = Math.max(0, session.getGraceSecondsRemaining());
        bossBar.setTitle(Messages.get("bossbar.grace", Map.of(
            "town", ownerName,
            "zone", point.getName(),
            "time", formatTime(graceLeft)
        )));
        bossBar.setColor(BarColor.BLUE);

        int initialGrace = Math.max(1, session.getInitialGraceSeconds());
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) graceLeft / initialGrace)));
    }

    private void sendContestedActionbar(
        CapturePoint point,
        CaptureSession session,
        CaptureOwner capturingOwner,
        ContestedState contestedState,
        ContestedProgressPolicy policy
    ) {
        if (point == null || session == null || capturingOwner == null || contestedState == null || policy == null) {
            return;
        }
        String pointId = point.getId();
        if (!isContestedActionbarEnabled(pointId)) {
            return;
        }

        long now = System.currentTimeMillis();
        long intervalMs = resolveContestedActionbarIntervalMs(pointId);
        if ((now - session.getLastContestedActionbarAt()) < intervalMs) {
            return;
        }
        session.setLastContestedActionbarAt(now);

        String actionbar = Messages.get("messages.capture.contested-actionbar", Map.of(
            "zone", point.getName(),
            "town", capturingOwner.getDisplayName(),
            "time", formatTime(session.getRemainingCaptureTime()),
            "owners", String.valueOf(Math.max(1, contestedState.opposingOwners)),
            "players", String.valueOf(Math.max(1, contestedState.opposingPlayers)),
            "policy", policy.name()
        ));

        World world = point.getLocation() != null ? point.getLocation().getWorld() : null;
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }
            if (!isWithinZone(point, player.getLocation())) {
                continue;
            }
            sendActionBar(player, actionbar);
        }
    }

    private void sendGraceActionbar(CapturePoint point, CaptureSession session, CaptureOwner capturingOwner) {
        if (point == null || session == null || capturingOwner == null) {
            return;
        }
        String pointId = point.getId();
        if (!isGraceActionbarEnabled(pointId)) {
            return;
        }

        long now = System.currentTimeMillis();
        long intervalMs = resolveGraceActionbarIntervalMs(pointId);
        if ((now - session.getLastGraceActionbarAt()) < intervalMs) {
            return;
        }
        session.setLastGraceActionbarAt(now);

        String actionbar = Messages.get("messages.capture.grace-actionbar", Map.of(
            "zone", point.getName(),
            "town", capturingOwner.getDisplayName(),
            "time", formatTime(Math.max(0, session.getGraceSecondsRemaining()))
        ));

        World world = point.getLocation() != null ? point.getLocation().getWorld() : null;
        if (world == null) {
            return;
        }

        for (Player player : world.getPlayers()) {
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }
            boolean matchesOwner = false;
            try {
                matchesOwner = doesPlayerMatchOwner(player, capturingOwner);
            } catch (Exception ignored) {
                // Ignore owner checks that fail for optional integrations.
            }
            if (!matchesOwner) {
                continue;
            }
            sendActionBar(player, actionbar);
        }
    }

    private void sendActionBar(Player player, String message) {
        if (player == null || message == null || message.isEmpty() || isNotificationsDisabled(player)) {
            return;
        }
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(colorize(message)));
        } catch (Exception ignored) {
            // Ignore actionbar delivery failures on unsupported clients/server forks.
        }
    }

    public List<String> getForceCaptureTargets() {
        if (this.ownerPlatform == null) {
            return Collections.emptyList();
        }
        return this.ownerPlatform.getAvailableOwners(this.defaultOwnerType);
    }

    public void loadPointTypes() {
        this.pointTypes.clear();
    }

    private void startTownUpdater() {
        if (!this.isDynmapEnabled() || this.townUpdater == null) {
            this.getLogger().warning("Cannot start town updater: Dynmap is not enabled or townUpdater is not initialized!");
            return;
        }
        if (this.dynmapUpdateTask != null && !this.dynmapUpdateTask.isCancelled()) {
            this.dynmapUpdateTask.cancel();
        }
        this.dynmapUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                townUpdater.run();
            }
        }.runTaskTimer(this, 0L, 300L);
    }

    public int convertBlockRadiusToChunkRadius(int blockRadius) {
        return (blockRadius + 15) / 16;
    }

    public int chunkRadiusToBlockRadius(int chunkRadius) {
        return chunkRadius * 16;
    }

    public int[] getChunkCoordinates(Location location) {
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        return new int[]{chunkX, chunkZ};
    }

    public List<CapturePoint> getCandidateCapturePoints(Location location) {
        return getCandidateCapturePoints(location, 0);
    }

    public List<CapturePoint> getCandidateCapturePoints(Location location, int requiredExtraChunks) {
        if (location == null || location.getWorld() == null) {
            return Collections.emptyList();
        }

        int required = Math.max(0, requiredExtraChunks);
        rebuildCapturePointSpatialIndexIfNeeded();

        UUID worldId = location.getWorld().getUID();
        Map<Long, List<CapturePoint>> worldIndex = this.capturePointSpatialIndex.get(worldId);
        if (worldIndex == null) {
            return Collections.emptyList();
        }

        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (required == 0) {
            List<CapturePoint> candidates = worldIndex.get(toChunkKey(chunkX, chunkZ));
            return candidates != null ? candidates : Collections.emptyList();
        }

        Set<CapturePoint> uniqueCandidates = new LinkedHashSet<>();
        for (int x = chunkX - required; x <= chunkX + required; x++) {
            for (int z = chunkZ - required; z <= chunkZ + required; z++) {
                List<CapturePoint> bucket = worldIndex.get(toChunkKey(x, z));
                if (bucket == null || bucket.isEmpty()) {
                    continue;
                }
                uniqueCandidates.addAll(bucket);
            }
        }
        if (uniqueCandidates.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(uniqueCandidates);
    }

    public int getSpatialIndexExtraChunks() {
        rebuildCapturePointSpatialIndexIfNeeded();
        return this.capturePointSpatialIndexExtraChunks;
    }

    public double getChunkDistance(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null || loc1.getWorld() == null || loc2.getWorld() == null) {
            return Double.MAX_VALUE;
        }
        if (!loc1.getWorld().getUID().equals(loc2.getWorld().getUID())) {
            return Double.MAX_VALUE;
        }
        double dx = loc1.getX() - loc2.getX();
        double dz = loc1.getZ() - loc2.getZ();
        return Math.sqrt(dx * dx + dz * dz) / 16.0;
    }

    public void invalidateCapturePointSpatialIndex() {
        this.capturePointSpatialIndexDirty = true;
    }

    private synchronized void rebuildCapturePointSpatialIndexIfNeeded() {
        if (!this.capturePointSpatialIndexDirty) {
            return;
        }

        Map<UUID, Map<Long, List<CapturePoint>>> newSpatialIndex = new HashMap<>();
        Map<UUID, List<CapturePoint>> newPointsByWorld = new HashMap<>();
        List<CapturePoint> snapshot = new ArrayList<>(this.capturePoints.values());
        int maxExtraChunks = calculateSpatialIndexExtraChunks(snapshot);

        for (CapturePoint point : snapshot) {
            if (point == null || point.getLocation() == null || point.getLocation().getWorld() == null) {
                continue;
            }

            Location center = point.getLocation();
            UUID worldId = center.getWorld().getUID();
            newPointsByWorld.computeIfAbsent(worldId, key -> new ArrayList<>()).add(point);

            Map<Long, List<CapturePoint>> worldIndex = newSpatialIndex.computeIfAbsent(worldId, key -> new HashMap<>());
            int minChunkX = point.getSpatialMinChunkX(0);
            int maxChunkX = point.getSpatialMaxChunkX(0);
            int minChunkZ = point.getSpatialMinChunkZ(0);
            int maxChunkZ = point.getSpatialMaxChunkZ(0);

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    long chunkKey = toChunkKey(chunkX, chunkZ);
                    worldIndex.computeIfAbsent(chunkKey, key -> new ArrayList<>()).add(point);
                }
            }
        }

        this.capturePointSpatialIndex.clear();
        this.capturePointSpatialIndex.putAll(newSpatialIndex);
        this.capturePointsByWorld.clear();
        this.capturePointsByWorld.putAll(newPointsByWorld);
        this.capturePointSpatialIndexExtraChunks = maxExtraChunks;
        this.capturePointSpatialIndexDirty = false;
    }

    private int calculateSpatialIndexExtraChunks(List<CapturePoint> points) {
        int maxExtra = DEFAULT_SPATIAL_INDEX_EXTRA_CHUNKS;
        if (this.zoneConfigManager == null || points == null || points.isEmpty()) {
            return maxExtra;
        }

        for (CapturePoint point : points) {
            if (point == null) {
                continue;
            }

            try {
                boolean bufferEnabled = this.zoneConfigManager.getBoolean(
                    point.getId(),
                    "protection.buffer-zone.enabled",
                    true
                );
                if (!bufferEnabled) {
                    continue;
                }
                int bufferSize = this.zoneConfigManager.getInt(
                    point.getId(),
                    "protection.buffer-zone.size",
                    1
                );
                if (bufferSize > maxExtra) {
                    maxExtra = bufferSize;
                }
            } catch (Exception ignored) {
                // Keep safe default for index sizing.
            }
        }

        return Math.max(DEFAULT_SPATIAL_INDEX_EXTRA_CHUNKS, maxExtra);
    }

    private long toChunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    public boolean isWithinChunkRadius(Location center, Location target, int chunkRadius) {
        if (center == null || target == null) {
            return false;
        }
        int clampedChunkRadius = Math.max(0, chunkRadius);
        return isWithinBlockRadius(center, target, clampedChunkRadius * 16.0);
    }

    public boolean isWithinZone(CapturePoint point, Location target) {
        return isWithinZone(point, target, 0);
    }

    public boolean isWithinZone(CapturePoint point, Location target, int extraChunkRadius) {
        return point != null && point.contains(target, extraChunkRadius);
    }

    private boolean isWithinBlockRadius(Location center, Location target, double blockRadius) {
        if (center == null || target == null || center.getWorld() == null || target.getWorld() == null) {
            return false;
        }
        if (!center.getWorld().getUID().equals(target.getWorld().getUID())) {
            return false;
        }
        double dx = center.getX() - target.getX();
        double dz = center.getZ() - target.getZ();
        double radiusSquared = blockRadius * blockRadius;
        return (dx * dx + dz * dz) <= radiusSquared;
    }

    private enum BoundaryMode {
        ON, REDUCED, OFF
    }

    private static class BoundaryVisualSettings {
        final int minSides;
        final double sidesMultiplier;
        final int minHeight;
        final int maxHeight;
        final int visibilityRangeChunks;
        final long intervalTicks;

        BoundaryVisualSettings(int minSides, double sidesMultiplier, int minHeight, int maxHeight, int visibilityRangeChunks, long intervalTicks) {
            this.minSides = minSides;
            this.sidesMultiplier = sidesMultiplier;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
            this.visibilityRangeChunks = visibilityRangeChunks;
            this.intervalTicks = intervalTicks;
        }
    }

    private boolean shouldShowBossBarToPlayer(CapturePoint point, Player player, int extraChunks) {
        if (point == null || player == null || !player.isOnline()) {
            return false;
        }
        if (this.hiddenBossBars.getOrDefault(player.getUniqueId(), false).booleanValue()) {
            return false;
        }
        if (this.disabledNotifications.getOrDefault(player.getUniqueId(), false)) {
            return false;
        }
        if (extraChunks < 0) {
            return true;
        }
        return isWithinZone(point, player.getLocation(), extraChunks);
    }

    private boolean shouldShowPreparationBossBarToPlayer(
        CapturePoint point,
        CaptureOwner owner,
        Player player,
        int extraChunks
    ) {
        if (owner == null || !doesPlayerMatchOwner(player, owner)) {
            return false;
        }
        return shouldShowBossBarToPlayer(point, player, extraChunks);
    }

    private boolean shouldPlayerSeeBossBarForPoint(Player player, String pointId) {
        if (player == null || pointId == null || pointId.isEmpty()) {
            return false;
        }
        CapturePoint point = this.capturePoints.get(pointId);
        if (point == null) {
            return false;
        }
        int visibilityExtraChunks = resolveBossbarVisibilityExtraChunks();
        CaptureSession session = this.activeSessions.get(pointId);
        if (session != null && session.isInPreparationPhase()) {
            CaptureOwner owner = session.getOwner();
            return shouldShowPreparationBossBarToPlayer(point, owner, player, visibilityExtraChunks);
        }
        return shouldShowBossBarToPlayer(point, player, visibilityExtraChunks);
    }

    private void syncBossBarAudience(BossBar bossBar, CapturePoint point, CaptureOwner owner, boolean ownerOnly) {
        if (bossBar == null || point == null) {
            return;
        }
        String pointId = point.getId();
        if (pointId != null) {
            long now = System.currentTimeMillis();
            long lastSync = this.lastBossBarAudienceSyncAt.getOrDefault(pointId, 0L);
            if ((now - lastSync) < BOSSBAR_AUDIENCE_SYNC_INTERVAL_MS) {
                return;
            }
            this.lastBossBarAudienceSyncAt.put(pointId, now);
        }

        int visibilityExtraChunks = resolveBossbarVisibilityExtraChunks();
        for (Player online : Bukkit.getOnlinePlayers()) {
            boolean shouldShow = ownerOnly
                ? shouldShowPreparationBossBarToPlayer(point, owner, online, visibilityExtraChunks)
                : shouldShowBossBarToPlayer(point, online, visibilityExtraChunks);
            if (shouldShow) {
                bossBar.addPlayer(online);
            } else {
                bossBar.removePlayer(online);
            }
        }
    }

    public void showBossBarForPlayer(Player player) {
        if (player == null) {
            return;
        }
        refreshBossBarAudienceForPlayer(player);
    }

    public void hideBossBarForPlayer(Player player) {
        removePlayerFromAllBossBars(player);
    }

    public void removePlayerFromAllBossBars(Player player) {
        if (player == null) {
            return;
        }
        for (BossBar bossBar : this.captureBossBars.values()) {
            if (bossBar != null) {
                bossBar.removePlayer(player);
            }
        }
    }

    public void refreshBossBarAudienceForPlayer(Player player) {
        refreshBossBarAudienceForPlayer(player, null, null);
    }

    public void refreshBossBarAudienceForPlayer(Player player, Location from, Location to) {
        if (player == null) {
            return;
        }
        if (this.captureBossBars.isEmpty()) {
            return;
        }
        if (this.disabledNotifications.getOrDefault(player.getUniqueId(), false)
            || this.hiddenBossBars.getOrDefault(player.getUniqueId(), false).booleanValue()) {
            removePlayerFromAllBossBars(player);
            return;
        }

        int visibilityExtraChunks = resolveBossbarVisibilityExtraChunks();
        Set<String> candidatePointIds = new LinkedHashSet<>();
        if (visibilityExtraChunks < 0) {
            candidatePointIds.addAll(this.captureBossBars.keySet());
        } else {
            if (from == null && to == null) {
                to = player.getLocation();
            }
            if (to != null) {
                for (CapturePoint point : getCandidateCapturePoints(to, visibilityExtraChunks)) {
                    if (point != null && point.getId() != null) {
                        candidatePointIds.add(point.getId());
                    }
                }
            }
            if (from != null) {
                for (CapturePoint point : getCandidateCapturePoints(from, visibilityExtraChunks)) {
                    if (point != null && point.getId() != null) {
                        candidatePointIds.add(point.getId());
                    }
                }
            }
            for (Map.Entry<String, BossBar> entry : this.captureBossBars.entrySet()) {
                BossBar bossBar = entry.getValue();
                if (bossBar != null && bossBar.getPlayers().contains(player)) {
                    candidatePointIds.add(entry.getKey());
                }
            }
        }

        for (String pointId : candidatePointIds) {
            BossBar bossBar = this.captureBossBars.get(pointId);
            if (bossBar == null) {
                continue;
            }
            if (shouldPlayerSeeBossBarForPoint(player, pointId)) {
                bossBar.addPlayer(player);
            } else {
                bossBar.removePlayer(player);
            }
        }
    }

    public boolean isNotificationsDisabled(Player player) {
        return player != null && disabledNotifications.getOrDefault(player.getUniqueId(), false);
    }

    public void sendNotification(Player player, String message) {
        if (player == null || isNotificationsDisabled(player)) {
            return;
        }
        if (message == null || message.isEmpty()) {
            return;
        }
        player.sendMessage(colorize(message));
    }

    private void setupMapProviders() {
        mapProviders.clear();

        if (getConfig().getBoolean("dynmap.enabled", true)) {
            try {
                DynmapProvider dynmapProvider = new DynmapProvider(this);
                if (dynmapProvider.initialize()) {
                    mapProviders.add(dynmapProvider);
                    
                    // Keep UpdateZones for backward compatibility
                    this.townUpdater = dynmapProvider.getTownUpdater();
                    if (this.townUpdater != null) {
                        startTownUpdater();
                    }
                }
            } catch (NoClassDefFoundError | Exception e) {
                getLogger().warning("Failed to initialize Dynmap: " + e.getMessage());
                if (getConfig().getBoolean("settings.debug-mode", false)) {
                    e.printStackTrace();
                }
            }
        } else {
            getLogger().info("Dynmap integration disabled in config.");
        }

        if (getConfig().getBoolean("bluemap.enabled", true)) {
            try {
                BlueMapProvider blueMapProvider = new BlueMapProvider(this);
                if (blueMapProvider.initialize()) {
                    mapProviders.add(blueMapProvider);
                }
            } catch (NoClassDefFoundError | Exception e) {
                getLogger().warning("Failed to initialize BlueMap: " + e.getMessage());
                if (getConfig().getBoolean("settings.debug-mode", false)) {
                    e.printStackTrace();
                }
            }
        } else {
            getLogger().info("BlueMap integration disabled in config.");
        }

        if (getConfig().getBoolean("pl3xmap.enabled", false)) {
            try {
                Pl3xMapProvider pl3xMapProvider = new Pl3xMapProvider(this);
                if (pl3xMapProvider.initialize()) {
                    mapProviders.add(pl3xMapProvider);
                }
            } catch (NoClassDefFoundError | Exception e) {
                getLogger().warning("Failed to initialize Pl3xMap: " + e.getMessage());
                if (getConfig().getBoolean("settings.debug-mode", false)) {
                    e.printStackTrace();
                }
            }
        } else {
            getLogger().info("Pl3xMap integration disabled in config.");
        }

        if (mapProviders.isEmpty()) {
            getLogger().warning("No map providers active. Capture points will not be visible on web maps.");
        } else {
            StringBuilder providers = new StringBuilder("Active map providers: ");
            for (int i = 0; i < mapProviders.size(); i++) {
                if (i > 0) providers.append(", ");
                providers.append(mapProviders.get(i).getName());
            }
            getLogger().info(providers.toString());
        }
    }

    private void setupHolograms() {
        if (this.hologramManager == null) {
            this.hologramManager = new HologramManager(this);
        }
        this.hologramManager.initialize();
    }

    private void setupKothManager() {
        if (this.kothManager == null) {
            this.kothManager = new KothManager(this);
        }
        this.kothManager.initialize();
    }

    private void setupConquestManager() {
        if (this.conquestManager == null) {
            this.conquestManager = new ConquestManager(this);
        }
        this.conquestManager.initialize();
    }

    private void setupPotionRewardManager() {
        if (this.potionRewardManager == null) {
            this.potionRewardManager = new PotionRewardManager(this);
            this.potionRewardManager.initialize();
            return;
        }
        this.potionRewardManager.reload();
    }

    private void cleanupKothManager() {
        if (this.kothManager == null) {
            return;
        }
        this.kothManager.shutdown();
    }

    private void cleanupConquestManager() {
        if (this.conquestManager == null) {
            return;
        }
        this.conquestManager.shutdown();
    }

    private void cleanupPotionRewardManager() {
        if (this.potionRewardManager == null) {
            return;
        }
        this.potionRewardManager.shutdown();
    }

    private void setupPermissionRewards() {
        if (this.permissionRewardManager == null) {
            this.permissionRewardManager = new PermissionRewardManager(this);
            getServer().getPluginManager().registerEvents(this.permissionRewardManager, this);
            this.permissionRewardManager.initialize();
            return;
        }
        this.permissionRewardManager.reload();
    }

    private void cleanupPermissionRewards() {
        if (this.permissionRewardManager == null) {
            return;
        }
        this.permissionRewardManager.shutdown();
    }

    private void cleanupHolograms() {
        if (this.hologramManager == null) {
            return;
        }
        this.hologramManager.shutdown();
    }

    private void setupPlaceholderApiExpansion() {
        cleanupPlaceholderApiExpansion();

        Plugin placeholderApiPlugin = getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApiPlugin == null || !placeholderApiPlugin.isEnabled()) {
            if (this.config.getBoolean("settings.debug-mode", false)) {
                getLogger().info("PlaceholderAPI not found or disabled. CaptureZones placeholders are unavailable.");
            }
            return;
        }

        try {
            CaptureZonesPlaceholderExpansion expansion = new CaptureZonesPlaceholderExpansion(this);
            if (expansion.register()) {
                this.placeholderExpansion = expansion;
                getLogger().info("Registered PlaceholderAPI expansion: %capturezones_*%");
            } else {
                getLogger().warning("Failed to register PlaceholderAPI expansion.");
            }
        } catch (Exception e) {
            getLogger().warning("Failed to setup PlaceholderAPI expansion: " + e.getMessage());
        }
    }

    private void cleanupPlaceholderApiExpansion() {
        if (this.placeholderExpansion == null) {
            return;
        }
        try {
            this.placeholderExpansion.unregister();
        } catch (Exception e) {
            getLogger().warning("Failed to unregister PlaceholderAPI expansion cleanly: " + e.getMessage());
        } finally {
            this.placeholderExpansion = null;
        }
    }

    private void cleanupMapProviders() {
        if (this.dynmapUpdateTask != null && !this.dynmapUpdateTask.isCancelled()) {
            this.dynmapUpdateTask.cancel();
        }
        this.dynmapUpdateTask = null;

        for (MapProvider provider : mapProviders) {
            try {
                provider.cleanup();
                getLogger().info("Cleaned up " + provider.getName() + " markers");
            } catch (Exception e) {
                getLogger().warning("Error cleaning up " + provider.getName() + ": " + e.getMessage());
            }
        }
        mapProviders.clear();
        this.townUpdater = null;
    }

    public void updateAllMarkers() {
        queueVisualRefresh(true, false, null, null);
    }

    private void updateAllMarkersNow() {
        for (MapProvider provider : mapProviders) {
            try {
                provider.updateAllMarkers();
            } catch (Exception e) {
                getLogger().warning("Error updating markers for " + provider.getName() + ": " + e.getMessage());
            }
        }
    }
    
    public void createOrUpdateMarker(CapturePoint point) {
        if (point == null || point.getId() == null || point.getId().trim().isEmpty()) {
            return;
        }
        queueVisualRefresh(false, false, point.getId(), null);
    }
    
    public void removeMarker(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }
        queueVisualRefresh(false, false, pointId, null);
    }

    public void updateAllHolograms() {
        queueVisualRefresh(false, true, null, null);
    }

    private void updateAllHologramsNow() {
        if (this.hologramManager == null) {
            return;
        }
        this.hologramManager.updateAll();
    }

    public void createOrUpdateHologram(CapturePoint point) {
        if (point == null || point.getId() == null || point.getId().trim().isEmpty()) {
            return;
        }
        queueVisualRefresh(false, false, null, point.getId());
    }

    public void removeHologram(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }
        queueVisualRefresh(false, false, null, pointId);
    }

    public void refreshPointVisuals(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }
        queueVisualRefresh(false, false, pointId, pointId);
    }

    private void queueVisualRefresh(
        boolean fullMarkers,
        boolean fullHolograms,
        String markerPointId,
        String hologramPointId
    ) {
        if (!isEnabled()) {
            return;
        }

        synchronized (this) {
            if (fullMarkers) {
                this.fullMarkerRefreshQueued = true;
            }
            if (fullHolograms) {
                this.fullHologramRefreshQueued = true;
            }
            if (markerPointId != null && !markerPointId.trim().isEmpty()) {
                this.dirtyMarkerPointIds.add(markerPointId.trim());
            }
            if (hologramPointId != null && !hologramPointId.trim().isEmpty()) {
                this.dirtyHologramPointIds.add(hologramPointId.trim());
            }

            if (this.visualRefreshTask != null && !this.visualRefreshTask.isCancelled()) {
                return;
            }
            this.visualRefreshTask = Bukkit.getScheduler().runTaskLater(
                this,
                this::flushPendingVisualRefreshes,
                VISUAL_REFRESH_DEBOUNCE_TICKS
            );
        }
    }

    private void flushPendingVisualRefreshes() {
        boolean runFullMarkers;
        boolean runFullHolograms;
        Set<String> markerPointIds;
        Set<String> hologramPointIds;
        synchronized (this) {
            runFullMarkers = this.fullMarkerRefreshQueued;
            runFullHolograms = this.fullHologramRefreshQueued;
            markerPointIds = new LinkedHashSet<>(this.dirtyMarkerPointIds);
            hologramPointIds = new LinkedHashSet<>(this.dirtyHologramPointIds);
            this.fullMarkerRefreshQueued = false;
            this.fullHologramRefreshQueued = false;
            this.dirtyMarkerPointIds.clear();
            this.dirtyHologramPointIds.clear();
            this.visualRefreshTask = null;
        }

        if (runFullMarkers) {
            updateAllMarkersNow();
        } else {
            for (String pointId : markerPointIds) {
                updateMarkerNow(pointId);
            }
        }

        if (runFullHolograms) {
            updateAllHologramsNow();
        } else {
            for (String pointId : hologramPointIds) {
                updateHologramNow(pointId);
            }
        }
    }

    private void updateMarkerNow(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }
        CapturePoint point = this.capturePoints.get(pointId);
        for (MapProvider provider : this.mapProviders) {
            try {
                if (point == null || !shouldDisplayPoint(point)) {
                    provider.removeMarker(pointId);
                } else {
                    provider.createOrUpdateMarker(point);
                }
            } catch (Exception e) {
                getLogger().warning("Error updating marker for " + provider.getName() + ": " + e.getMessage());
            }
        }
    }

    private void updateHologramNow(String pointId) {
        if (pointId == null || pointId.trim().isEmpty() || this.hologramManager == null) {
            return;
        }
        CapturePoint point = this.capturePoints.get(pointId);
        if (point == null || !shouldDisplayPoint(point)) {
            this.hologramManager.removePoint(pointId);
            return;
        }
        this.hologramManager.updatePoint(point);
    }

    public boolean shouldDisplayPoint(CapturePoint point) {
        if (point == null) {
            return false;
        }
        if (this.kothManager == null) {
            return true;
        }
        return this.kothManager.shouldDisplayPoint(point);
    }

    public void broadcastChatMessage(String message) {
        broadcastMessage(message);
    }

    public boolean depositPlayerReward(Player player, double amount, String reason) {
        if (player == null || amount <= 0.0) {
            return false;
        }
        ShopEconomyAdapter economy = getOrCreateShopEconomyAdapter();
        if (economy != null && economy.isAvailable() && economy.deposit(player, amount, reason)) {
            return true;
        }
        if (this.ownerPlatform == null) {
            return false;
        }
        if (this.ownerPlatform.depositFirstCaptureBonus(player.getUniqueId(), amount, reason)) {
            return true;
        }
        return this.ownerPlatform.depositControlReward(
            player.getName(),
            amount,
            reason,
            CaptureOwnerType.PLAYER
        );
    }

    public boolean canDepositOwnerReward(CaptureOwnerType ownerType) {
        if (ownerType == null) {
            return false;
        }
        ShopEconomyAdapter economy = getOrCreateShopEconomyAdapter();
        if (economy != null && economy.isAvailable() && economy.supportsOwnerType(ownerType)) {
            return true;
        }
        return this.ownerPlatform instanceof TownyOwnerPlatformAdapter && ownerType == CaptureOwnerType.TOWN;
    }

    public boolean depositOwnerReward(String ownerName, CaptureOwnerType ownerType, double amount, String reason) {
        if (ownerName == null || ownerName.trim().isEmpty() || ownerType == null || amount <= 0.0) {
            return false;
        }
        ShopEconomyAdapter economy = getOrCreateShopEconomyAdapter();
        if (economy != null && economy.isAvailable() && economy.depositToOwner(ownerName, ownerType, amount, reason)) {
            return true;
        }
        return this.ownerPlatform != null && this.ownerPlatform.depositControlReward(ownerName, amount, reason, ownerType);
    }

    public boolean depositOwnerReward(CaptureOwner owner, double amount, String reason) {
        if (owner == null || owner.getType() == null || amount <= 0.0) {
            return false;
        }
        ShopEconomyAdapter economy = getOrCreateShopEconomyAdapter();
        if (economy != null && economy.isAvailable() && economy.depositToOwner(owner.getDisplayName(), owner.getType(), amount, reason)) {
            return true;
        }
        return this.ownerPlatform != null && this.ownerPlatform.depositControlReward(owner, amount, reason);
    }

    private EconomyRewardPayoutResult distributeEconomyReward(CapturePoint point, CaptureOwner owner, double amount, String reason) {
        MoneyPayoutMode mode = resolveMoneyPayoutMode(point != null ? point.getId() : null);
        if (point == null || owner == null || owner.getType() == null || amount <= 0.0) {
            return EconomyRewardPayoutResult.none(mode);
        }

        if (mode == MoneyPayoutMode.OWNER_ACCOUNT || !(this.ownerPlatform instanceof TownyOwnerPlatformAdapter)) {
            boolean deposited = canDepositOwnerReward(owner.getType()) && depositOwnerReward(owner, amount, reason);
            return deposited
                ? new EconomyRewardPayoutResult(true, amount, 1, 0, mode)
                : EconomyRewardPayoutResult.none(mode);
        }

        TownyOwnerPlatformAdapter townyAdapter = (TownyOwnerPlatformAdapter) this.ownerPlatform;
        int maxRecipients = resolveMoneyPayoutMaxRecipients(point.getId());
        if (mode == MoneyPayoutMode.SPLIT_ONLINE || mode == MoneyPayoutMode.EACH_ONLINE) {
            List<Player> recipients = townyAdapter.getOnlineOwnerPlayers(owner);
            if (recipients.isEmpty()) {
                return EconomyRewardPayoutResult.none(mode);
            }
            if (maxRecipients > 0 && recipients.size() > maxRecipients) {
                recipients = new ArrayList<>(recipients.subList(0, maxRecipients));
            }
            double perRecipient = mode == MoneyPayoutMode.SPLIT_ONLINE ? amount / recipients.size() : amount;
            double totalPaid = 0.0;
            int paid = 0;
            int failed = 0;
            for (Player recipient : recipients) {
                if (depositPlayerReward(recipient, perRecipient, reason)) {
                    totalPaid += perRecipient;
                    paid++;
                } else {
                    failed++;
                }
            }
            return new EconomyRewardPayoutResult(paid > 0, totalPaid, paid, failed, mode);
        }

        List<String> recipients = townyAdapter.getOwnerResidentNames(owner);
        if (recipients.isEmpty()) {
            return EconomyRewardPayoutResult.none(mode);
        }
        if (maxRecipients > 0 && recipients.size() > maxRecipients) {
            recipients = new ArrayList<>(recipients.subList(0, maxRecipients));
        }
        double totalPaid = 0.0;
        int paid = 0;
        int failed = 0;
        int batchSize = resolveMoneyPayoutBatchSize(point.getId());
        int processedThisTick = 0;
        for (String residentName : recipients) {
            if (townyAdapter.depositResidentReward(residentName, amount, reason)) {
                totalPaid += amount;
                paid++;
            } else {
                failed++;
            }
            processedThisTick++;
            if (processedThisTick >= batchSize) {
                processedThisTick = 0;
            }
        }
        return new EconomyRewardPayoutResult(paid > 0, totalPaid, paid, failed, mode);
    }

    private MoneyPayoutMode resolveMoneyPayoutMode(String pointId) {
        String raw = this.zoneConfigManager != null
            ? this.zoneConfigManager.getString(pointId, "rewards.money.payout-mode", "OWNER_ACCOUNT")
            : "OWNER_ACCOUNT";
        return MoneyPayoutMode.fromConfigValue(raw);
    }

    private int resolveMoneyPayoutMaxRecipients(String pointId) {
        int configured = this.zoneConfigManager != null
            ? this.zoneConfigManager.getInt(pointId, "rewards.money.max-recipients", 250)
            : 250;
        return Math.max(0, Math.min(5000, configured));
    }

    private int resolveMoneyPayoutBatchSize(String pointId) {
        int configured = this.zoneConfigManager != null
            ? this.zoneConfigManager.getInt(pointId, "rewards.money.batch-size", 25)
            : 25;
        return Math.max(1, Math.min(250, configured));
    }

    public List<String> grantConfiguredPermissionRewardsToPlayer(String pointId, Player recipient, String sourcePrefix) {
        if (recipient == null || !recipient.isOnline() || recipient.isDead() || this.permissionRewardManager == null) {
            return Collections.emptyList();
        }

        List<PermissionGrantReward> grants = resolveConfiguredPermissionGrantRewards(pointId);
        if (grants.isEmpty()) {
            return Collections.emptyList();
        }

        String safePointId = pointId == null ? "unknown" : pointId.trim();
        if (safePointId.isEmpty()) {
            safePointId = "unknown";
        }
        String normalizedPrefix = sourcePrefix == null || sourcePrefix.trim().isEmpty()
            ? "zone:" + safePointId + ":manual"
            : sourcePrefix.trim();

        Set<String> summaries = new LinkedHashSet<>();
        int index = 0;
        for (PermissionGrantReward grant : grants) {
            if (grant == null || grant.permission == null || grant.permission.trim().isEmpty()) {
                continue;
            }
            PermissionRewardManager.GrantResult result = this.permissionRewardManager.grantPermission(
                recipient,
                grant.permission,
                normalizedPrefix + ":" + index,
                grant.durationMs
            );
            index++;
            if (!result.active || !result.changed) {
                continue;
            }
            summaries.add(formatPermissionGrantSummary(grant.permission, grant.durationMs));
        }

        if (summaries.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(summaries);
    }

    private void setupWorldGuard() {
        this.worldGuardEnabled = false;
        try {
            Plugin worldGuardPlugin = this.getServer().getPluginManager().getPlugin("WorldGuard");
            if (worldGuardPlugin != null && worldGuardPlugin.isEnabled()) {
                this.worldGuardEnabled = true;
                this.getLogger().info("WorldGuard integration enabled!");
            } else {
                this.getLogger().warning("WorldGuard not found or not enabled. WorldGuard integration disabled.");
            }
        }
        catch (Exception e) {
            this.getLogger().warning("Error setting up WorldGuard integration: " + e.getMessage());
        }
    }

    private void setupMobSpawner() {
        try {
            VanillaMobSpawner vanillaSpawner = new VanillaMobSpawner(this);
            
            boolean mythicEnabled = false;
            if (zoneConfigManager != null) {
                mythicEnabled = zoneConfigManager.getDefaultBoolean("reinforcements.mythicmobs.enabled", false);
            } else {
                mythicEnabled = getConfig().getBoolean("reinforcements.mythicmobs.enabled", false);
            }
            if (!mythicEnabled && zoneConfigManager != null) {
                for (String zoneId : capturePoints.keySet()) {
                    if (zoneConfigManager.getBoolean(zoneId, "reinforcements.mythicmobs.enabled", false)) {
                        mythicEnabled = true;
                        break;
                    }
                }
            }
            
            MobSpawner spawner;
            
            if (mythicEnabled) {
                try {
                    MythicMobsHandler mythicHandler = new MythicMobsHandler(this, vanillaSpawner);
                    spawner = mythicHandler;
                    getLogger().info("MythicMobs integration initialized!");
                } catch (NoClassDefFoundError | Exception e) {
                    getLogger().warning("Failed to initialize MythicMobs integration: " + e.getMessage());
                    getLogger().warning("Falling back to vanilla mob spawning.");
                    spawner = vanillaSpawner;
                }
            } else {
                spawner = vanillaSpawner;
                getLogger().info("Using vanilla mob spawning for reinforcements.");
            }
            
            if (reinforcementListener != null) {
                reinforcementListener.setMobSpawner(spawner);
            } else {
                getLogger().warning("ReinforcementListener not initialized! Mob spawner cannot be set.");
            }
            
            if (getConfig().getBoolean("settings.debug-mode", false)) {
                getLogger().info("Configured mobs: " + String.join(", ", spawner.getConfiguredMobs()));
            }
            
        } catch (Exception e) {
            getLogger().severe("Error setting up mob spawner: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void setupStatistics() {
        try {
            getLogger().info("Initializing statistics system...");

            boolean statsEnabled = getConfig().getBoolean("statistics.enabled", true);

            if (!statsEnabled) {
                getLogger().info("Statistics system is disabled in config.");
                return;
            }

            statisticsManager = new StatisticsManager(this);
            getLogger().info("Statistics manager initialized.");

            statisticsGUI = new StatisticsGUI(this, statisticsManager);
            getLogger().info("Statistics GUI initialized.");

            getServer().getPluginManager().registerEvents(new StatisticsGUIListener(this, statisticsGUI), this);
            getLogger().info("Statistics GUI listener registered.");

            getServer().getPluginManager().registerEvents(new StatisticsTrackingListener(this), this);
            getLogger().info("Statistics tracking listener registered.");

            startStatisticsAutoSaveTask();

            int saveInterval = Math.max(30, getConfig().getInt("statistics.auto-save-interval", 300));
            getLogger().info("Statistics system enabled with auto-save every " + saveInterval + " seconds.");

        } catch (Exception e) {
            getLogger().severe("Error setting up statistics system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startStatisticsAutoSaveTask() {
        if (this.statisticsAutoSaveTask != null && !this.statisticsAutoSaveTask.isCancelled()) {
            this.statisticsAutoSaveTask.cancel();
        }
        if (this.statisticsManager == null) {
            return;
        }

        int saveIntervalSeconds = Math.max(30, getConfig().getInt("statistics.auto-save-interval", 300));
        this.statisticsAutoSaveTask = getServer().getScheduler().runTaskTimer(this, () -> {
            if (statisticsManager != null) {
                statisticsManager.saveStatistics();
            }
        }, saveIntervalSeconds * 20L, saveIntervalSeconds * 20L);
    }
    
    private void setupShopSystem() {
        try {
            boolean shopsEnabled = getConfig().getBoolean("shops.enabled", false);

            if (!shopsEnabled) {
                shutdownShopSystem();
                getLogger().info("Shop system is disabled in config.");
                return;
            }

            ShopEconomyAdapter adapter = getOrCreateShopEconomyAdapter();
            if (adapter == null) {
                shutdownShopSystem();
                getLogger().warning("Shop system requires a supported economy provider (Towny or Vault).");
                return;
            }
            this.shopEconomyAdapter = adapter;

            if (shopManager == null) {
                shopManager = new ShopManager(this);
                getLogger().info("Shop manager initialized.");
            }

            if (shopListener == null) {
                shopListener = new ShopListener(this);
                getServer().getPluginManager().registerEvents(shopListener, this);
                getLogger().info("Shop listener registered.");
            }

            getLogger().info("Shop system enabled using " + adapter.getProviderName() + " economy. Use /cap shop to access zone shops.");

        } catch (Exception e) {
            getLogger().severe("Error setting up shop system: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void shutdownShopSystem() {
        if (shopManager != null) {
            shopManager.shutdown();
            shopManager = null;
        }
        if (shopListener != null) {
            HandlerList.unregisterAll(shopListener);
            shopListener = null;
        }
        shopEconomyAdapter = null;
    }

    public void listCapturePoints(Player player) {
        player.sendMessage(colorize("&6&l────────── &e&lCapture Zones &6&l──────────"));
        if (this.capturePoints.isEmpty()) {
            player.sendMessage(Messages.get("messages.list.empty"));
            return;
        }
        for (CapturePoint point : this.capturePoints.values()) {
            String status = point.getControllingTown().isEmpty() ? 
                Messages.get("messages.list.unclaimed") : 
                Messages.get("messages.list.controlled", Map.of("town", point.getControllingTown()));
            player.sendMessage(Messages.get("messages.list.format", Map.of(
                "id", point.getId(),
                "name", point.getName(),
                "status", status)));
        }
    }

    public void showCapturePointInfo(Player player, String id) {
        if (!this.capturePoints.containsKey(id)) {
            player.sendMessage(Messages.get("messages.info.not-found"));
            return;
        }
        CapturePoint point = this.capturePoints.get(id);
        player.sendMessage(Messages.get("messages.info.header", Map.of("point", point.getName())));
        player.sendMessage(Messages.get("messages.info.id", Map.of("id", point.getId())));
        player.sendMessage(Messages.get("messages.info.location", Map.of(
            "world", point.getLocation().getWorld().getName(),
            "x", String.valueOf(Math.round(point.getLocation().getX())),
            "y", String.valueOf(Math.round(point.getLocation().getY())),
            "z", String.valueOf(Math.round(point.getLocation().getZ())))));
        player.sendMessage(Messages.get("messages.info.point-shape", Map.of("shape", point.getShapeType().name().toLowerCase())));
        if (point.isCuboid()) {
            player.sendMessage(Messages.get("messages.info.point-cuboid", Map.of(
                "width", String.valueOf(point.getCuboidWidthBlocks()),
                "height", String.valueOf(point.getCuboidHeightBlocks()),
                "depth", String.valueOf(point.getCuboidDepthBlocks())
            )));
        } else {
            player.sendMessage(Messages.get("messages.info.radius", Map.of("radius", String.valueOf(point.getChunkRadius()))));
        }
        player.sendMessage(Messages.get("messages.info.reward", Map.of("reward", String.valueOf(getBaseReward(point)))));
        player.sendMessage(Messages.get("messages.info.type", Map.of("type", this.getPointTypeName(point.getType()))));
        if (point.getControllingTown().isEmpty()) {
            player.sendMessage(Messages.get("messages.info.status-unclaimed"));
        } else {
            player.sendMessage(Messages.get("messages.info.status-controlled", Map.of("town", point.getControllingTown())));
        }
        if (this.activeSessions.containsKey(id)) {
            CaptureSession session = this.activeSessions.get(id);
            player.sendMessage(Messages.get("messages.info.capturing-town", Map.of("town", session.getTownName())));
            player.sendMessage(Messages.get("messages.info.capturing-time", Map.of("time", this.formatTime(session.getRemainingTime()))));
            player.sendMessage(Messages.get("messages.info.capturing-phase", Map.of("phase", (session.isInPreparationPhase() ? "Preparation" : "Capture"))));
        }
        if (this.isDynmapEnabled()) {
            player.sendMessage(Messages.get("messages.info.dynmap"));
        }
        if (this.isWorldGuardEnabled()) {
            player.sendMessage(Messages.get("messages.info.worldguard"));
        }
    }

    public void listPointTypes(Player player) {
        player.sendMessage(Messages.get("messages.types.header"));
        for (Map.Entry<String, String> entry : this.pointTypes.entrySet()) {
            player.sendMessage(Messages.get("messages.types.format", 
                "type", entry.getKey(),
                "description", entry.getValue()));
        }
    }

    public void reloadAll() {
        this.reloadConfig();
        this.config = this.getConfig();
        cleanupActiveCapturesForLifecycle("reload");
        removeAllCaptureBossBars();
        cancelAllCaptureTasks();
        cancelAllBoundaryTasks();
        cancelTask(this.visualRefreshTask);
        this.visualRefreshTask = null;
        this.flushPendingCapturePointSave();
        cancelTask(this.capturePointSaveTask);
        this.capturePointSaveTask = null;
        this.fullMarkerRefreshQueued = false;
        this.fullHologramRefreshQueued = false;
        this.dirtyMarkerPointIds.clear();
        this.dirtyHologramPointIds.clear();

        if (this.capturePointsFile == null) {
            this.capturePointsFile = new File(getDataFolder(), "capture_points.yml");
        }
        if (this.dataMigrationManager == null) {
            this.dataMigrationManager = new DataMigrationManager(this);
        }
        this.dataMigrationManager.migrateCoreFiles(this.capturePointsFile);
        if (!initializeOwnerPlatform()) {
            getLogger().severe("Reload aborted due to invalid Towny/owner configuration.");
            return;
        }
        Messages.reload();
        this.capturePointsConfig = YamlConfiguration.loadConfiguration((File)this.capturePointsFile);
        this.capturePoints.clear();
        this.loadCapturePoints();
        this.loadPointTypes();
        if (zoneConfigManager != null) {
            zoneConfigManager.reloadDefaults();
            this.dataMigrationManager.migrateZoneFiles();
            zoneConfigManager.loadAllZoneConfigs();
            invalidateCapturePointSpatialIndex();
        }
        if (this.discordWebhook != null) {
            this.discordWebhook.loadConfig();
        }
        if (this.shopManager != null) {
            this.shopManager.saveAllShops();
        }
        setupShopSystem();
        if (this.shopManager != null) {
            this.shopManager.loadAllShops();
        }

        cleanupHolograms();
        cleanupMapProviders();
        setupKothManager();
        setupConquestManager();
        setupMapProviders();
        setupHolograms();
        setupPlaceholderApiExpansion();
        setupWorldGuard();
        setupPermissionRewards();
        setupPotionRewardManager();
        if (this.hasMapProviders()) {
            this.updateAllMarkers();
        }
        this.updateAllHolograms();
        if (this.reinforcementListener != null) {
            setupMobSpawner();
        }

        startSessionTimeoutChecker();
        startAutoSave();
        startHourlyRewards();
        startWeeklyResetTask();
        if (this.statisticsManager != null) {
            startStatisticsAutoSaveTask();
        }

        if (boundariesEnabled() && this.config.getBoolean("settings.auto-show-boundaries", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                this.autoShowBoundariesForPlayer(player);
            }
        }
    }

    /**
     * Run a manual add-missing schema repair for all plugin-owned files.
     */
    public void repairAllConfigsAndData() {
        repairAllConfigsAndData(false);
    }

    /**
     * Run schema repair for plugin-owned files.
     * Order:
     * 1) config.yml
     * 2) zone-template.yml
     * 3) capture_points.yml
     * 4) shops/*.yml
     * 5) statistics.json
     * 6) zones/*_config.yml
     *
     * @param exactTemplates true to restore bundled config/zone-template as exact copies first
     */
    public void repairAllConfigsAndData(boolean exactTemplates) {
        if (this.capturePointsFile == null) {
            this.capturePointsFile = new File(getDataFolder(), "capture_points.yml");
        }
        if (this.dataMigrationManager == null) {
            this.dataMigrationManager = new DataMigrationManager(this);
        }

        if (exactTemplates) {
            this.dataMigrationManager.restorePrimaryTemplatesExact();
            this.reloadConfig();
            this.config = this.getConfig();
        }

        this.dataMigrationManager.migrateCoreFiles(this.capturePointsFile);

        if (this.zoneConfigManager != null) {
            this.zoneConfigManager.reloadDefaults();
            this.zoneConfigManager.migrateExistingZones();
            this.dataMigrationManager.migrateZoneFiles();
            this.zoneConfigManager.loadAllZoneConfigs();
        } else {
            this.dataMigrationManager.migrateZoneFiles();
        }

        this.reloadConfig();
        this.config = this.getConfig();
        this.loadPointTypes();
        Messages.reload();

        if (this.discordWebhook != null) {
            this.discordWebhook.loadConfig();
        }
        setupShopSystem();
        if (this.shopManager != null) {
            this.shopManager.loadAllShops();
        }
        invalidateCapturePointSpatialIndex();
        invalidateHourlyRewardSchedule();
        if (this.kothManager != null) {
            this.kothManager.reload();
        }
        if (this.conquestManager != null) {
            this.conquestManager.reload();
        }
        if (this.potionRewardManager != null) {
            this.potionRewardManager.reload();
        }

        this.lastLegacyTownOwnerRebindCount = repairLegacyTownOwnerIds();
    }

    public int getLastLegacyTownOwnerRebindCount() {
        return Math.max(0, this.lastLegacyTownOwnerRebindCount);
    }

    private int repairLegacyTownOwnerIds() {
        if (this.ownerPlatform == null || this.capturePoints == null || this.capturePoints.isEmpty()) {
            return 0;
        }

        int repaired = 0;
        for (CapturePoint point : this.capturePoints.values()) {
            if (point == null) {
                continue;
            }

            CaptureOwner controllingOwner = rebindLegacyTownOwner(point.getControllingOwner());
            if (controllingOwner != null) {
                point.setControllingOwner(controllingOwner);
                repaired++;
            }

            CaptureOwner capturingOwner = rebindLegacyTownOwner(point.getCapturingOwner());
            if (capturingOwner != null) {
                point.setCapturingOwner(capturingOwner);
                repaired++;
            }

            CaptureOwner previousOwner = rebindLegacyTownOwner(point.getRecaptureLockPreviousOwner());
            if (previousOwner != null) {
                point.setRecaptureLockPreviousOwner(previousOwner, point.getRecaptureLockPreviousOwnerUntilTime());
                repaired++;
            }

            CaptureOwner previousAttacker = rebindLegacyTownOwner(point.getRecaptureLockPreviousAttacker());
            if (previousAttacker != null) {
                point.setRecaptureLockPreviousAttacker(previousAttacker, point.getRecaptureLockPreviousAttackerUntilTime());
                repaired++;
            }
        }

        if (repaired > 0) {
            saveCapturePoints();
            getLogger().info("Rebound " + repaired + " legacy Towny owner references to stable owner IDs.");
        }
        return repaired;
    }

    private CaptureOwner rebindLegacyTownOwner(CaptureOwner owner) {
        if (owner == null || owner.getType() != CaptureOwnerType.TOWN || this.ownerPlatform == null) {
            return null;
        }

        String displayName = owner.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            return null;
        }

        String normalizedName = this.ownerPlatform.normalizeOwnerName(displayName, CaptureOwnerType.TOWN);
        if (normalizedName == null || normalizedName.trim().isEmpty()) {
            return null;
        }

        String stableId = this.ownerPlatform.resolveOwnerId(normalizedName, CaptureOwnerType.TOWN);
        if (stableId == null || stableId.trim().isEmpty()) {
            return null;
        }

        String currentId = owner.getId();
        boolean sameId = currentId != null && currentId.trim().equalsIgnoreCase(stableId.trim());
        boolean sameDisplay = normalizedName.equals(owner.getDisplayName());
        if (sameId && sameDisplay) {
            return null;
        }

        return new CaptureOwner(CaptureOwnerType.TOWN, stableId, normalizedName);
    }

    public void reloadLang() {
        Messages.reload();
    }

    public void saveCapturePoints() {
        if (!Bukkit.isPrimaryThread()) {
            if (!isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(this, this::saveCapturePoints);
            return;
        }
        this.capturePointsSaveDirty = true;
        scheduleCapturePointSave();
    }

    public void flushPendingCapturePointSave() {
        if (!Bukkit.isPrimaryThread()) {
            if (!isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(this, this::flushPendingCapturePointSave);
            return;
        }
        if (!this.capturePointsSaveDirty) {
            return;
        }
        this.capturePointsSaveDirty = false;
        saveCapturePointsNow();
    }

    private void scheduleCapturePointSave() {
        if (!isEnabled()) {
            flushPendingCapturePointSave();
            return;
        }
        if (this.capturePointSaveTask != null && !this.capturePointSaveTask.isCancelled()) {
            return;
        }
        this.capturePointSaveTask = Bukkit.getScheduler().runTaskLater(
            this,
            () -> {
                this.capturePointSaveTask = null;
                flushPendingCapturePointSave();
            },
            CAPTURE_POINT_SAVE_DEBOUNCE_TICKS
        );
    }

    private void saveCapturePointsNow() {
        try {
            refreshAllCapturePointOwnerReferences();
            if (this.capturePointsFile == null) {
                this.capturePointsFile = new File(this.getDataFolder(), "capture_points.yml");
            }
            File file = this.capturePointsFile;
            if (!file.exists()) {
                file.createNewFile();
            }
            YamlConfiguration config = new YamlConfiguration();
            config.options().header(
                "AUTO-GENERATED FILE - DO NOT EDIT UNLESS YOU KNOW WHAT YOU ARE DOING.\n" +
                "This file stores capture zone state and metadata."
            );
            ConfigurationSection resetSection = config.createSection("metadata.weekly-reset-epoch-days");
            for (Map.Entry<String, Long> entry : this.lastWeeklyResetEpochDays.entrySet()) {
                resetSection.set(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, CapturePoint> entry : this.capturePoints.entrySet()) {
                String path = "points." + entry.getKey();
                CapturePoint point = entry.getValue();
                config.set(path + ".name", (Object)point.getName());
                config.set(path + ".world", (Object)point.getWorldUUID().toString());
                config.set(path + ".x", (Object)point.getLocation().getX());
                config.set(path + ".y", (Object)point.getLocation().getY());
                config.set(path + ".z", (Object)point.getLocation().getZ());
                config.set(path + ".radius", (Object)point.getChunkRadius());
                config.set(path + ".shape", point.getShapeType().name().toLowerCase());
                config.set(path + ".cuboid.min.x", point.getCuboidMinX());
                config.set(path + ".cuboid.min.y", point.getCuboidMinY());
                config.set(path + ".cuboid.min.z", point.getCuboidMinZ());
                config.set(path + ".cuboid.max.x", point.getCuboidMaxX());
                config.set(path + ".cuboid.max.y", point.getCuboidMaxY());
                config.set(path + ".cuboid.max.z", point.getCuboidMaxZ());
                config.set(path + ".reward", (Object)point.getReward());
                config.set(path + ".controllingTown", (Object)point.getControllingTown());
                CaptureOwner controllingOwner = point.getControllingOwner();
                if (controllingOwner != null) {
                    config.set(path + ".owner.type", controllingOwner.getType().name().toLowerCase());
                    config.set(path + ".owner.id", controllingOwner.getId());
                    config.set(path + ".owner.displayName", controllingOwner.getDisplayName());
                } else {
                    config.set(path + ".owner.type", this.defaultOwnerType.name().toLowerCase());
                    config.set(path + ".owner.id", null);
                    config.set(path + ".owner.displayName", point.getControllingTown());
                }
                CaptureOwner capturingOwner = point.getCapturingOwner();
                if (capturingOwner != null) {
                    config.set(path + ".capturingOwner.type", capturingOwner.getType().name().toLowerCase());
                    config.set(path + ".capturingOwner.id", capturingOwner.getId());
                    config.set(path + ".capturingOwner.displayName", capturingOwner.getDisplayName());
                } else {
                    config.set(path + ".capturingOwner.type", this.defaultOwnerType.name().toLowerCase());
                    config.set(path + ".capturingOwner.id", null);
                    config.set(path + ".capturingOwner.displayName", point.getCapturingTown());
                }
                config.set(path + ".type", (Object)point.getType());
                config.set(path + ".showOnMap", (Object)point.isShowOnMap());
                config.set(path + ".lastCaptureTime", (Object)point.getLastCaptureTime());
                config.set(path + ".cooldowns.zone-cooldown-until", point.getCooldownUntilTime());
                CaptureOwner previousOwnerLock = point.getRecaptureLockPreviousOwner();
                if (previousOwnerLock != null) {
                    config.set(path + ".cooldowns.recapture.previous-owner.type", previousOwnerLock.getType().name().toLowerCase());
                    config.set(path + ".cooldowns.recapture.previous-owner.id", previousOwnerLock.getId());
                    config.set(path + ".cooldowns.recapture.previous-owner.displayName", previousOwnerLock.getDisplayName());
                } else {
                    config.set(path + ".cooldowns.recapture.previous-owner.type", this.defaultOwnerType.name().toLowerCase());
                    config.set(path + ".cooldowns.recapture.previous-owner.id", null);
                    config.set(path + ".cooldowns.recapture.previous-owner.displayName", null);
                }
                config.set(path + ".cooldowns.recapture.previous-owner.until", point.getRecaptureLockPreviousOwnerUntilTime());
                CaptureOwner previousAttackerLock = point.getRecaptureLockPreviousAttacker();
                if (previousAttackerLock != null) {
                    config.set(path + ".cooldowns.recapture.previous-attacker.type", previousAttackerLock.getType().name().toLowerCase());
                    config.set(path + ".cooldowns.recapture.previous-attacker.id", previousAttackerLock.getId());
                    config.set(path + ".cooldowns.recapture.previous-attacker.displayName", previousAttackerLock.getDisplayName());
                } else {
                    config.set(path + ".cooldowns.recapture.previous-attacker.type", this.defaultOwnerType.name().toLowerCase());
                    config.set(path + ".cooldowns.recapture.previous-attacker.id", null);
                    config.set(path + ".cooldowns.recapture.previous-attacker.displayName", null);
                }
                config.set(path + ".cooldowns.recapture.previous-attacker.until", point.getRecaptureLockPreviousAttackerUntilTime());
                config.set(path + ".firstCaptureBonusAvailable", point.isFirstCaptureBonusAvailable());
            }
            config.save(file);
        }
        catch (Exception e) {
            this.getLogger().severe("Failed to save capture zones: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void loadCapturePoints() {
        try {
            invalidateCapturePointSpatialIndex();
            if (this.capturePointsFile == null) {
                this.capturePointsFile = new File(this.getDataFolder(), "capture_points.yml");
            }
            File file = this.capturePointsFile;
            if (!file.exists()) {
                return;
            }
            YamlConfiguration config = new YamlConfiguration();
            config.load(file);
            this.lastWeeklyResetEpochDays.clear();
            ConfigurationSection resetSection = config.getConfigurationSection("metadata.weekly-reset-epoch-days");
            if (resetSection != null) {
                for (String key : resetSection.getKeys(false)) {
                    this.lastWeeklyResetEpochDays.put(key, resetSection.getLong(key, -1L));
                }
            }
            long legacyReset = config.getLong("metadata.lastWeeklyResetEpochDay", -1L);
            ConfigurationSection pointsSection = config.getConfigurationSection("points");
            if (pointsSection == null) {
                return;
            }
            for (String id : pointsSection.getKeys(false)) {
                try {
                    String path = "points." + id;
                    String name = config.getString(path + ".name", id);
                    String worldId = config.getString(path + ".world");
                    if (worldId == null || worldId.trim().isEmpty()) {
                        this.getLogger().warning("Missing world UUID for capture zone: " + id + ", skipping.");
                        continue;
                    }
                    UUID worldUUID = UUID.fromString(worldId);
                    double x = config.getDouble(path + ".x");
                    double y = config.getDouble(path + ".y");
                    double z = config.getDouble(path + ".z");
                    int radius = Math.max(1, config.getInt(path + ".radius", 1));
                    ZoneShapeType shapeType = ZoneShapeType.fromConfigValue(
                        config.getString(path + ".shape", "circle"),
                        ZoneShapeType.CIRCLE
                    );
                    double reward = config.getDouble(path + ".reward");
                    String controllingTown = config.getString(path + ".controllingTown", "");
                    CaptureOwnerType ownerType = CaptureOwnerType.fromConfigValue(
                        config.getString(path + ".owner.type", this.defaultOwnerType.name()),
                        this.defaultOwnerType
                    );
                    String ownerId = config.getString(path + ".owner.id", null);
                    String ownerDisplayName = config.getString(path + ".owner.displayName", controllingTown);
                    CaptureOwnerType capturingOwnerType = CaptureOwnerType.fromConfigValue(
                        config.getString(path + ".capturingOwner.type", ownerType.name()),
                        ownerType
                    );
                    String capturingOwnerId = config.getString(path + ".capturingOwner.id", null);
                    String capturingOwnerDisplayName = config.getString(path + ".capturingOwner.displayName", null);
                    String type = config.getString(path + ".type", "default");
                    boolean showOnMap = config.getBoolean(path + ".showOnMap", true);
                    long lastCaptureTime = config.getLong(path + ".lastCaptureTime", 0L);
                    long zoneCooldownUntil = config.getLong(path + ".cooldowns.zone-cooldown-until", 0L);
                    CaptureOwnerType lockPreviousOwnerType = CaptureOwnerType.fromConfigValue(
                        config.getString(path + ".cooldowns.recapture.previous-owner.type", this.defaultOwnerType.name()),
                        this.defaultOwnerType
                    );
                    String lockPreviousOwnerId = config.getString(path + ".cooldowns.recapture.previous-owner.id", null);
                    String lockPreviousOwnerDisplayName = config.getString(path + ".cooldowns.recapture.previous-owner.displayName", null);
                    long lockPreviousOwnerUntil = config.getLong(path + ".cooldowns.recapture.previous-owner.until", 0L);
                    CaptureOwnerType lockPreviousAttackerType = CaptureOwnerType.fromConfigValue(
                        config.getString(path + ".cooldowns.recapture.previous-attacker.type", this.defaultOwnerType.name()),
                        this.defaultOwnerType
                    );
                    String lockPreviousAttackerId = config.getString(path + ".cooldowns.recapture.previous-attacker.id", null);
                    String lockPreviousAttackerDisplayName = config.getString(path + ".cooldowns.recapture.previous-attacker.displayName", null);
                    long lockPreviousAttackerUntil = config.getLong(path + ".cooldowns.recapture.previous-attacker.until", 0L);
                    boolean bonusAvailable = config.getBoolean(path + ".firstCaptureBonusAvailable", false);
                    World world = Bukkit.getWorld((UUID)worldUUID);
                    if (world == null) {
                        this.getLogger().warning("World not found for capture zone: " + id);
                        continue;
                    }
                    Location location = new Location(world, x, y, z);
                    CapturePoint point = new CapturePoint(id, name, location, radius, reward);
                    if (shapeType == ZoneShapeType.CUBOID) {
                        int minX = config.getInt(path + ".cuboid.min.x", location.getBlockX() - radius * 16);
                        int minY = config.getInt(path + ".cuboid.min.y", world.getMinHeight());
                        int minZ = config.getInt(path + ".cuboid.min.z", location.getBlockZ() - radius * 16);
                        int maxX = config.getInt(path + ".cuboid.max.x", location.getBlockX() + radius * 16);
                        int maxY = config.getInt(path + ".cuboid.max.y", world.getMaxHeight() - 1);
                        int maxZ = config.getInt(path + ".cuboid.max.z", location.getBlockZ() + radius * 16);
                        point.setCuboidBounds(minX, minY, minZ, maxX, maxY, maxZ);
                    }
                    point.setType(type);
                    point.setShowOnMap(showOnMap);
                    point.setLastCaptureTime(lastCaptureTime);
                    point.setCooldownUntilTime(zoneCooldownUntil);
                    point.setFirstCaptureBonusAvailable(bonusAvailable);
                    CaptureOwner controllingOwner = normalizeOwner(ownerType, ownerDisplayName);
                    if (controllingOwner != null) {
                        if (ownerId != null && !ownerId.trim().isEmpty()) {
                            controllingOwner = new CaptureOwner(controllingOwner.getType(), ownerId, controllingOwner.getDisplayName());
                        }
                        point.setControllingOwner(controllingOwner);
                    } else if (!controllingTown.isEmpty()) {
                        point.setControllingTown(controllingTown);
                    }
                    CaptureOwner capturingOwner = normalizeOwner(capturingOwnerType, capturingOwnerDisplayName);
                    if (capturingOwner != null) {
                        if (capturingOwnerId != null && !capturingOwnerId.trim().isEmpty()) {
                            capturingOwner = new CaptureOwner(capturingOwner.getType(), capturingOwnerId, capturingOwner.getDisplayName());
                        }
                        point.setCapturingOwner(capturingOwner);
                    }
                    CaptureOwner lockPreviousOwner = normalizeOwner(lockPreviousOwnerType, lockPreviousOwnerDisplayName);
                    if (lockPreviousOwner != null && lockPreviousOwnerId != null && !lockPreviousOwnerId.trim().isEmpty()) {
                        lockPreviousOwner = new CaptureOwner(lockPreviousOwner.getType(), lockPreviousOwnerId, lockPreviousOwner.getDisplayName());
                    }
                    if (lockPreviousOwner != null && lockPreviousOwnerUntil > 0L) {
                        point.setRecaptureLockPreviousOwner(lockPreviousOwner, lockPreviousOwnerUntil);
                    }
                    CaptureOwner lockPreviousAttacker = normalizeOwner(lockPreviousAttackerType, lockPreviousAttackerDisplayName);
                    if (lockPreviousAttacker != null && lockPreviousAttackerId != null && !lockPreviousAttackerId.trim().isEmpty()) {
                        lockPreviousAttacker = new CaptureOwner(lockPreviousAttacker.getType(), lockPreviousAttackerId, lockPreviousAttacker.getDisplayName());
                    }
                    if (lockPreviousAttacker != null && lockPreviousAttackerUntil > 0L) {
                        point.setRecaptureLockPreviousAttacker(lockPreviousAttacker, lockPreviousAttackerUntil);
                    }
                    if (!this.lastWeeklyResetEpochDays.containsKey(id) && legacyReset >= 0L) {
                        this.lastWeeklyResetEpochDays.put(id, legacyReset);
                    }
                    this.capturePoints.put(id, point);
                    this.getLogger().info("Loaded capture zone: " + name);
                } catch (IllegalArgumentException pointLoadError) {
                    this.getLogger().warning("Invalid capture zone entry '" + id + "': " + pointLoadError.getMessage());
                }
            }
            invalidateCapturePointSpatialIndex();
            invalidateHourlyRewardSchedule();
        }
        catch (Exception e) {
            this.getLogger().severe("Failed to load capture zones: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createBeacon(Location location) {
        World world = location.getWorld();
        if (world == null) return;
        
        Location glassLoc = location.clone();
        glassLoc.setY((double)world.getHighestBlockYAt(glassLoc));
        Location beaconLoc = glassLoc.clone().subtract(0.0, 1.0, 0.0);
        
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                Location ironLoc = beaconLoc.clone().add((double)x, -1.0, (double)z);
                storeOriginalBlock(ironLoc);
            }
        }
        
        storeOriginalBlock(beaconLoc);
        storeOriginalBlock(glassLoc);
        
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                Location ironLoc = beaconLoc.clone().add((double)x, -1.0, (double)z);
                Block ironBlock = ironLoc.getBlock();
                ironBlock.setType(Material.IRON_BLOCK);
            }
        }
        
        Block beaconBlock = beaconLoc.getBlock();
        beaconBlock.setType(Material.BEACON);
        
        Block glassBlock = glassLoc.getBlock();
        glassBlock.setType(PREPARATION_BEAM_GLASS);
    }

    private void setBeaconBeamGlass(CapturePoint point, Material glassMaterial) {
        if (point == null || glassMaterial == null) {
            return;
        }

        Location center = point.getLocation();
        if (center == null) {
            return;
        }

        World world = center.getWorld();
        if (world == null) {
            return;
        }

        Location glassLoc = center.clone();
        glassLoc.setY((double) world.getHighestBlockYAt(glassLoc));
        glassLoc.getBlock().setType(glassMaterial);
    }

    private void storeOriginalBlock(Location loc) {
        Block block = loc.getBlock();
        this.originalBlocks.put(loc.clone(), block.getBlockData().clone());
    }

    private void removeBeacon(CapturePoint point) {
        Location center = point.getLocation().clone();
        World world = center.getWorld();
        Location glassLoc = center.clone();
        glassLoc.setY((double)world.getHighestBlockYAt(glassLoc));
        Location beaconLoc = glassLoc.clone().subtract(0.0, 1.0, 0.0);
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                Location ironLoc = beaconLoc.clone().add((double)x, -1.0, (double)z);
                this.restoreBlock(ironLoc);
            }
        }
        this.restoreBlock(beaconLoc);
        this.restoreBlock(glassLoc);
    }

    private void restoreBlock(Location loc) {
        BlockData originalData = this.originalBlocks.remove(loc);
        if (originalData != null) {
            Block block = loc.getBlock();
            block.setBlockData(originalData);
        }
    }

    public boolean startCapture(Player player, String pointId) {
        return startCapture(player, pointId, null);
    }

    public boolean startCapture(Player player, String pointId, Location captureAttemptLocation) {
        CapturePoint point = capturePoints.get(pointId);
        if (point == null) {
            player.sendMessage(Messages.get("messages.capture.point-not-found"));
            return false;
        }

        if (this.kothManager != null
            && this.kothManager.isEnabled()
            && this.kothManager.isZoneManaged(pointId)) {
            player.sendMessage(Messages.get("errors.capture-koth-managed", Map.of(
                "zone", point.getName()
            )));
            return false;
        }

        ZoneConfigManager zoneManager = zoneConfigManager;

        int minOnlinePlayers = config.getInt("settings.min-online-players", 5);
        if (Bukkit.getOnlinePlayers().size() < minOnlinePlayers) {
            String message = Messages.get("errors.not-enough-players", Map.of("minplayers", String.valueOf(minOnlinePlayers)));
            player.sendMessage(message);
            return false;
        }

        CaptureOwner owner = resolveCaptureOwner(player);
        String ownerName = owner != null ? owner.getDisplayName() : null;
        if (ownerName == null || ownerName.isEmpty()) {
            if (this.defaultOwnerType == CaptureOwnerType.NATION) {
                player.sendMessage(Messages.get("messages.capture.not-in-nation"));
            } else if (this.defaultOwnerType == CaptureOwnerType.TOWN) {
                player.sendMessage(Messages.get("messages.capture.not-in-town"));
            } else {
                player.sendMessage(Messages.get("errors.capture-failed"));
            }
            return false;
        }

        if (this.conquestManager != null
            && this.conquestManager.isActive()
            && this.conquestManager.isZoneManaged(pointId)
            && !this.conquestManager.canOwnerCapture(point, owner)) {
            player.sendMessage(Messages.get("errors.capture-conquest-not-participant", Map.of(
                "zone", point.getName()
            )));
            return false;
        }

        Location locationForCaptureAttempt = captureAttemptLocation != null ? captureAttemptLocation : player.getLocation();
        if (!isWithinZone(point, locationForCaptureAttempt)) {
            player.sendMessage(Messages.get("messages.capture.not-in-radius"));
            return false;
        }

        CaptureSession existingSession = activeSessions.get(pointId);
        if (existingSession != null) {
            if (areOwnersEquivalent(existingSession.getOwner(), owner) ||
                existingSession.getTownName().equalsIgnoreCase(ownerName)) {
                existingSession.getPlayers().add(player);
                BossBar bossBar = captureBossBars.get(pointId);
                if (bossBar != null) {
                    if (shouldPlayerSeeBossBarForPoint(player, pointId)) {
                        bossBar.addPlayer(player);
                    } else {
                        bossBar.removePlayer(player);
                    }
                }
                player.sendMessage(Messages.get("messages.capture.joined-own", Map.of("point", point.getName())));
                return true;
            }
            player.sendMessage(Messages.get("messages.capture.already-capturing"));
            return false;
        }

        if (!isCaptureWithinTimeWindow(pointId) || !isCaptureWithinAllowedHours(pointId)) {
            player.sendMessage(Messages.get("errors.capture-outside-time-window"));
            return false;
        }

        if (!isCaptureAllowedForCurrentWeather(pointId, point)) {
            player.sendMessage(Messages.get("errors.capture-weather-blocked"));
            return false;
        }
        
        boolean preventSelfCapture = zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.prevent-self-capture", true)
            : config.getBoolean("capture-conditions.prevent-self-capture", true);
        String controllingTown = point.getControllingTown();
        CaptureOwner controllingOwner = point.getControllingOwner();
        if (preventSelfCapture && controllingTown != null && !controllingTown.isEmpty() &&
            ((controllingOwner != null && areOwnersEquivalent(controllingOwner, owner)) ||
                controllingTown.equalsIgnoreCase(ownerName))) {
            player.sendMessage(Messages.get("errors.already-controls-point", Map.of(
                "point", point.getName()
            )));
            return false;
        }

        CaptureOwner effectiveControllingOwner = controllingOwner != null
            ? controllingOwner
            : normalizeOwner(this.defaultOwnerType, controllingTown);
        if (enforceOwnerRecapturePolicy(player, point, pointId, effectiveControllingOwner)) {
            return false;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = resolveActiveCooldownUntil(point, pointId, now);
        if (cooldownUntil > now) {
            int secondsLeft = (int) Math.ceil((cooldownUntil - now) / 1000.0);
            String timeLeft = formatTime(Math.max(secondsLeft, 1));
            player.sendMessage(Messages.get("errors.capture-cooldown-active", Map.of(
                "time", timeLeft,
                "point", point.getName()
            )));
            return false;
        }

        if (enforceRecaptureLocks(player, point, pointId, owner, now)) {
            return false;
        }

        int maxConcurrentCaptures = getMaxActiveCapturesLimit();
        if (this.activeSessions.size() >= maxConcurrentCaptures) {
            if (!isMultiZoneConcurrentCaptureEnabled()) {
                player.sendMessage(Messages.get("errors.concurrent-captures-disabled"));
                return false;
            }
            player.sendMessage(Messages.get("errors.max-active-captures-reached", Map.of(
                "max", String.valueOf(maxConcurrentCaptures)
            )));
            return false;
        }

        createBeacon(point.getLocation());

        int preparationTime = zoneManager != null
            ? zoneManager.getInt(pointId, "capture.preparation.duration", 1)
            : config.getInt("capture.preparation.duration", 1);
        int captureTime = zoneManager != null
            ? zoneManager.getInt(pointId, "capture.capture.duration", 15)
            : config.getInt("capture.capture.duration", 15);
        boolean showCountdown = zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture.preparation.show-countdown", true)
            : config.getBoolean("capture.preparation.show-countdown", true);

        CaptureSession session = new CaptureSession(point, owner, player, preparationTime, captureTime);
        session.getPlayers().add(player);
        activeSessions.put(pointId, session);
        
        if (statisticsManager != null) {
            statisticsManager.onCaptureStart(point.getId(), ownerName, player.getUniqueId());
        }
        
        if (discordWebhook != null) {
            Location loc = point.getLocation();
            String locationStr = loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
            discordWebhook.sendCaptureStarted(point.getId(), point.getName(), ownerName, locationStr);
        }

        BossBar bossBar = null;
        if (isBossbarEnabled(pointId)) {
            bossBar = Bukkit.createBossBar(
                Messages.get("bossbar.preparing", Map.of("zone", point.getName())),
                BarColor.BLUE,
                BarStyle.SOLID
            );
            captureBossBars.put(pointId, bossBar);
            syncBossBarAudience(bossBar, point, owner, true);
        }

        String prepMessage = Messages.get("messages.capture.started", Map.of(
            "town", ownerName,
            "point", point.getName()
        ));
        broadcastMessage(prepMessage);
        
        playCaptureSoundAtLocation("capture-started", point.getLocation());

        final BossBar preparationBossBar = bossBar;
        BukkitTask prepTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!session.isActive()) {
                return;
            }

            CaptureOwner activeOwner = session.getOwner() != null ? session.getOwner() : owner;
            if (activeOwner == null || !hasAnyCapturingOwnerPlayerInZone(point, activeOwner)) {
                cancelCapture(pointId, buildMovedTooFarReason(resolveMovedTooFarPlayerName(session, activeOwner)));
                return;
            }

            session.decrementPreparationTime();
            int timeLeft = session.getRemainingPreparationTime();

            if (preparationBossBar != null) {
                if (isBossbarEnabled(pointId)) {
                    preparationBossBar.setVisible(true);
                    syncBossBarAudience(preparationBossBar, point, activeOwner, true);
                } else {
                    preparationBossBar.setVisible(false);
                    preparationBossBar.removeAll();
                }
            }
            
            if (timeLeft <= 0) {
                startCapturePhase(point, owner, player);
                return;
            }

            if (showCountdown && preparationBossBar != null) {
                String title = Messages.get("bossbar.preparation-timer", Map.of(
                    "zone", point.getName(),
                    "time", formatTime(timeLeft)
                ));
                preparationBossBar.setTitle(title);
                preparationBossBar.setProgress(Math.max(0.0, (double)timeLeft / session.getInitialPreparationTime()));
            }
        }, 20L, 20L);

        captureTasks.put(pointId, prepTask);

        return true;
    }

    private void startCapturePhase(CapturePoint point, CaptureOwner owner, Player player) {
        String ownerName = owner != null ? owner.getDisplayName() : "Unknown";
        String pointId = point.getId();
        CaptureSession session = activeSessions.get(pointId);
        if (session == null || !session.isInPreparationPhase()) {  // Add check to prevent multiple phase changes
            return;
        }

        BukkitTask prepTask = captureTasks.get(pointId);
        if (prepTask != null && !prepTask.isCancelled()) {
            prepTask.cancel();
        }

        session.startCapturePhase();
        point.setCapturingOwner(owner);
        setBeaconBeamGlass(point, CAPTURE_BEAM_GLASS);
        this.captureProgressSoundBuckets.put(pointId, Math.max(0, session.getRemainingCaptureTime()) / 60);

        BossBar bossBar = captureBossBars.get(pointId);
        if (bossBar != null) {
            boolean showCountdown = isCaptureCountdownEnabled(pointId);
            String title = resolveCaptureBossbarTitle(
                point,
                session,
                ownerName,
                session.getRemainingCaptureTime(),
                showCountdown
            );
            bossBar.setTitle(title);
            bossBar.setColor(resolveCaptureBossbarColor(pointId, session));
            syncBossBarAudience(bossBar, point, null, false);
        }

        String phaseMessage = Messages.get("messages.capture.phase-started", Map.of(
            "town", ownerName,
            "zone", point.getName()
        ));
        broadcastMessage(phaseMessage);
        
        playCaptureSoundAtLocation("capture-phase-started", point.getLocation());
        
        if (reinforcementListener != null) {
            reinforcementListener.startReinforcementWaves(pointId, point);
        }

        ZoneConfigManager zoneManager = this.zoneConfigManager;
        boolean contestedEnabled = zoneManager != null
            ? zoneManager.getBoolean(pointId, "capture-conditions.contested.enabled", false)
            : this.config.getBoolean("capture-conditions.contested.enabled", false);
        boolean graceEnabled = isGraceTimerEnabled(pointId);
        boolean speedModifiersEnabled = isCaptureSpeedModifiersEnabled(pointId);
        boolean speedPlayerScalingEnabled = speedModifiersEnabled && isCaptureSpeedPlayerScalingEnabled(pointId);
        double speedFlatMultiplier = 1.0;
        double speedMinMultiplier = 0.1;
        double speedMaxMultiplier = 5.0;
        int speedMaxExtraPlayers = 4;
        double speedPerExtraPlayer = 0.25;
        if (speedModifiersEnabled) {
            speedFlatMultiplier = zoneManager != null
                ? zoneManager.getDouble(pointId, "capture-conditions.speed-modifiers.flat-multiplier", 1.0)
                : this.config.getDouble("capture-conditions.speed-modifiers.flat-multiplier", 1.0);
            speedMinMultiplier = zoneManager != null
                ? zoneManager.getDouble(pointId, "capture-conditions.speed-modifiers.min-multiplier", 0.1)
                : this.config.getDouble("capture-conditions.speed-modifiers.min-multiplier", 0.1);
            speedMaxMultiplier = zoneManager != null
                ? zoneManager.getDouble(pointId, "capture-conditions.speed-modifiers.max-multiplier", 5.0)
                : this.config.getDouble("capture-conditions.speed-modifiers.max-multiplier", 5.0);
            if (speedMaxMultiplier < speedMinMultiplier) {
                speedMaxMultiplier = speedMinMultiplier;
            }

            if (speedPlayerScalingEnabled) {
                speedMaxExtraPlayers = zoneManager != null
                    ? zoneManager.getInt(pointId, "capture-conditions.speed-modifiers.owner-player-scaling.max-extra-players", 4)
                    : this.config.getInt("capture-conditions.speed-modifiers.owner-player-scaling.max-extra-players", 4);
                speedPerExtraPlayer = zoneManager != null
                    ? zoneManager.getDouble(pointId, "capture-conditions.speed-modifiers.owner-player-scaling.per-extra-player", 0.25)
                    : this.config.getDouble("capture-conditions.speed-modifiers.owner-player-scaling.per-extra-player", 0.25);
            }
        }
        int graceDurationSeconds = graceEnabled ? resolveGraceDurationSeconds(pointId) : 0;
        ContestedProgressPolicy contestedPolicy = resolveContestedProgressPolicy(pointId);
        final boolean speedModifiersEnabledFinal = speedModifiersEnabled;
        final boolean speedPlayerScalingEnabledFinal = speedPlayerScalingEnabled;
        final double speedFlatMultiplierFinal = speedFlatMultiplier;
        final double speedMinMultiplierFinal = speedMinMultiplier;
        final double speedMaxMultiplierFinal = speedMaxMultiplier;
        final int speedMaxExtraPlayersFinal = speedMaxExtraPlayers;
        final double speedPerExtraPlayerFinal = speedPerExtraPlayer;
        
        BukkitTask captureTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!session.isActive()) {
                return;
            }

            CaptureOwner activeOwner = session.getOwner() != null ? session.getOwner() : owner;
            String activeOwnerName = activeOwner != null && activeOwner.getDisplayName() != null
                ? activeOwner.getDisplayName()
                : ownerName;

            if (!graceEnabled && activeOwner != null && !hasAnyCapturingOwnerPlayerInZone(point, activeOwner)) {
                cancelCapture(pointId, buildMovedTooFarReason(resolveMovedTooFarPlayerName(session, activeOwner)));
                return;
            }

            ContestedState contestedState = ContestedState.clear();
            if ((contestedEnabled || graceEnabled || speedPlayerScalingEnabledFinal) && activeOwner != null) {
                contestedState = evaluateContestedState(point, activeOwner);
            }

            if (graceEnabled && activeOwner != null && contestedState.capturingPlayers <= 0) {
                if (!session.isGraceActive()) {
                    session.startGrace(graceDurationSeconds);
                    String graceStartedMessage = Messages.get("messages.capture.grace-started", Map.of(
                        "town", activeOwnerName,
                        "zone", point.getName(),
                        "time", formatTime(Math.max(0, session.getGraceSecondsRemaining()))
                    ));
                    broadcastMessage(graceStartedMessage);
                } else {
                    session.decrementGraceSeconds();
                }

                if (session.getGraceSecondsRemaining() <= 0) {
                    session.clearGrace();
                    cancelCapture(pointId, "Grace timer expired");
                    return;
                }

                if (session.isContested()) {
                    session.setContested(false);
                    String contestedEndMessage = Messages.get("messages.capture.contested-ended", Map.of(
                        "town", activeOwnerName,
                        "zone", point.getName()
                    ));
                    broadcastMessage(contestedEndMessage);
                }

                updateGraceBossBar(
                    bossBar,
                    point,
                    activeOwnerName,
                    session
                );
                sendGraceActionbar(point, session, activeOwner);
                return;
            }

            if (session.isGraceActive()) {
                session.clearGrace();
                String graceEndedMessage = Messages.get("messages.capture.grace-ended", Map.of(
                    "town", activeOwnerName,
                    "zone", point.getName()
                ));
                broadcastMessage(graceEndedMessage);
            }

            if (contestedEnabled && contestedState.contested) {
                if (!session.isContested()) {
                    session.setContested(true);
                    String contestedStartMessage = Messages.get("messages.capture.contested-started", Map.of(
                        "town", activeOwnerName,
                        "zone", point.getName(),
                        "owners", String.valueOf(Math.max(1, contestedState.opposingOwners)),
                        "players", String.valueOf(Math.max(1, contestedState.opposingPlayers))
                    ));
                    broadcastMessage(contestedStartMessage);
                }

                applyContestedProgressPolicy(session, pointId, contestedPolicy);

                updateCaptureBossBar(
                    bossBar,
                    point,
                    activeOwnerName,
                    session,
                    true,
                    contestedState
                );
                sendContestedActionbar(point, session, activeOwner, contestedState, contestedPolicy);
                return;
            }

            if (session.isContested()) {
                session.setContested(false);
                String contestedEndMessage = Messages.get("messages.capture.contested-ended", Map.of(
                    "town", activeOwnerName,
                    "zone", point.getName()
                ));
                broadcastMessage(contestedEndMessage);
            }

            double captureSpeedMultiplier = 1.0;
            if (speedModifiersEnabledFinal) {
                captureSpeedMultiplier = Math.max(0.0, speedFlatMultiplierFinal);
                if (speedPlayerScalingEnabledFinal) {
                    int capturingOwnerPlayers = Math.max(0, contestedState.capturingPlayers);
                    int extraPlayers = Math.max(0, capturingOwnerPlayers - 1);
                    int cappedExtraPlayers = Math.min(extraPlayers, Math.max(0, speedMaxExtraPlayersFinal));
                    captureSpeedMultiplier *= 1.0 + (Math.max(0.0, speedPerExtraPlayerFinal) * cappedExtraPlayers);
                }
                captureSpeedMultiplier = Math.max(
                    speedMinMultiplierFinal,
                    Math.min(speedMaxMultiplierFinal, captureSpeedMultiplier)
                );
            }
            int progressToApply = session.consumeCaptureProgress(captureSpeedMultiplier);
            if (progressToApply > 0) {
                session.decrementCaptureTimeBy(progressToApply);
            }
            int timeLeft = session.getRemainingCaptureTime();

            if (timeLeft <= 0) {
                completeCapture(point);
                return;
            }

            playCaptureProgressSoundIfNeeded(pointId, point, session);
            updateCaptureBossBar(
                bossBar,
                point,
                activeOwnerName,
                session,
                false,
                ContestedState.clear()
            );
        }, 20L, 20L);

        captureTasks.put(pointId, captureTask);
    }

    private void completeCapture(CapturePoint point) {
        // Defensive guards: ensure point still exists and session is valid
        if (point == null) {
            return;
        }
        String pointId = point.getId();
        if (!this.capturePoints.containsKey(pointId)) {
            // Point was removed; bail out
            return;
        }
        CaptureSession session = activeSessions.get(pointId);
        CaptureOwner capturingOwner = point.getCapturingOwner();
        if (capturingOwner == null && session != null) {
            capturingOwner = session.getOwner();
        }
        if (capturingOwner == null || capturingOwner.getDisplayName() == null || capturingOwner.getDisplayName().isEmpty()) {
            String fallbackOwnerName = point.getCapturingTown();
            if ((fallbackOwnerName == null || fallbackOwnerName.isEmpty()) && session != null) {
                fallbackOwnerName = session.getTownName();
            }
            if (fallbackOwnerName == null || fallbackOwnerName.isEmpty()) {
                fallbackOwnerName = "Unknown";
            }
            capturingOwner = normalizeOwner(this.defaultOwnerType, fallbackOwnerName);
            if (capturingOwner == null) {
                capturingOwner = new CaptureOwner(this.defaultOwnerType, null, fallbackOwnerName);
            }
            getLogger().warning("Capture completed for point " + point.getId() + " but no owner model was found; rebuilt from legacy data.");
        }
        String capturingOwnerName = capturingOwner.getDisplayName();
        CaptureOwner previousOwner = point.getControllingOwner();
        if (previousOwner == null) {
            previousOwner = normalizeOwner(this.defaultOwnerType, point.getControllingTown());
        }

        point.setControllingOwner(capturingOwner);
        point.setCapturingOwner(null);
        point.setCaptureProgress(0.0);
        point.setLastCapturingTown(capturingOwnerName);
        point.setLastCaptureTime(System.currentTimeMillis());
        applyCaptureCooldown(point, pointId, CooldownTrigger.SUCCESS);
        applyAntiInstantRecaptureLocks(point, pointId, previousOwner, capturingOwner);
        
        if (statisticsManager != null && session != null) {
            java.util.Set<java.util.UUID> playerUUIDs = session.getPlayers().stream()
                .map(org.bukkit.entity.Player::getUniqueId)
                .collect(java.util.stream.Collectors.toSet());
            statisticsManager.onCaptureComplete(
                point.getId(),
                point.getName(),
                capturingOwnerName,
                session.getInitiatorUUID(),
                playerUUIDs
            );
        }
        
        if (discordWebhook != null && session != null) {
            long captureTimeMs = System.currentTimeMillis() - session.getStartTime();
            int captureTimeSeconds = (int) (captureTimeMs / 1000);
            String captureTimeStr = captureTimeSeconds + "s";
            discordWebhook.sendCaptureCompleted(point.getId(), point.getName(), capturingOwnerName, captureTimeStr);
        }
        
        String townColor = getOwnerColor(capturingOwner);
        point.setColor(townColor);

        if (this.conquestManager != null) {
            this.conquestManager.onCaptureComplete(point, previousOwner, capturingOwner);
        }
        if (this.potionRewardManager != null && session != null) {
            this.potionRewardManager.onCaptureComplete(point, session.getInitiatorUUID());
        }
        
        String completeMessage = Messages.get("messages.capture.complete", Map.of(
            "town", capturingOwnerName,
            "zone", point.getName()
        ));
        broadcastMessage(completeMessage);
        
        String personalMessage = Messages.get("messages.capture.congratulations", Map.of("zone", point.getName()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (doesPlayerMatchOwner(player, capturingOwner) &&
                    isWithinZone(point, player.getLocation())) {
                    sendNotification(player, personalMessage);
                }
            } catch (Exception e) {
                // Ignore errors
            }
        }
        
        playCaptureSoundAtLocation("capture-complete", point.getLocation());

        boolean bonusEnabled = zoneConfigManager != null
            ? zoneConfigManager.getBoolean(pointId, "weekly-reset.first-capture-bonus.enabled", true)
            : this.config.getBoolean("weekly-reset.first-capture-bonus.enabled", true);
        if (point.isFirstCaptureBonusAvailable() && bonusEnabled) {
            double minBonus = zoneConfigManager != null
                ? zoneConfigManager.getDouble(pointId, "weekly-reset.first-capture-bonus.amount-range.min", 500.0)
                : this.config.getDouble("weekly-reset.first-capture-bonus.amount-range.min", 500.0);
            double maxBonus = zoneConfigManager != null
                ? zoneConfigManager.getDouble(pointId, "weekly-reset.first-capture-bonus.amount-range.max", 1500.0)
                : this.config.getDouble("weekly-reset.first-capture-bonus.amount-range.max", 1500.0);
            if (maxBonus < minBonus) {
                maxBonus = minBonus;
            }
            double bonus = minBonus + (Math.random() * (maxBonus - minBonus));
            Player bonusTarget = null;
            if (session != null && session.getInitiatorUUID() != null) {
                bonusTarget = Bukkit.getPlayer(session.getInitiatorUUID());
            }
            if (bonusTarget != null) {
                boolean bonusPaid = depositPlayerReward(
                    bonusTarget,
                    bonus,
                    "First capture weekly bonus for " + point.getName()
                );
                if (bonusPaid) {
                    sendNotification(bonusTarget, Messages.get("messages.weekly.first-capture-bonus", Map.of(
                        "zone", point.getName(),
                        "amount", String.format("%.1f", bonus)
                    )));

                    if (discordWebhook != null) {
                        discordWebhook.sendFirstCaptureBonus(point.getId(), point.getName(), capturingOwnerName, bonus);
                    }
                }
            }
            point.setFirstCaptureBonusAvailable(false);
        }
        
        cleanupCaptureRuntime(pointId, point, false, true);
        refreshPointVisuals(pointId);
        invalidateHourlyRewardSchedule();
        
        logSuccessfulCapture(point.getId());
        
        saveCapturePoints();
    }

    public void cancelCaptureByDeath(String pointId, String victimName, String killerName) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }

        String normalizedPointId = pointId.trim();
        CaptureSession session = this.activeSessions.get(normalizedPointId);
        if (session == null) {
            return;
        }

        CapturePoint point = this.capturePoints.get(normalizedPointId);
        String pointName = point != null ? point.getName() : normalizedPointId;
        String safeVictimName = victimName == null || victimName.isEmpty() ? "Unknown" : victimName;
        String safeKillerName = killerName == null || killerName.isEmpty() ? "Unknown" : killerName;
        if (point != null && point.getLocation() != null) {
            playCaptureSoundAtLocation("capture-failed", point.getLocation());
        } else {
            playCaptureSound("capture-failed");
        }
        
        if (statisticsManager != null) {
            statisticsManager.onCaptureFailed(normalizedPointId, session.getTownName(), session.getInitiatorUUID());
        }
        
        if (discordWebhook != null) {
            String reason = safeVictimName + " was killed by " + safeKillerName;
            discordWebhook.sendCaptureFailed(normalizedPointId, pointName, session.getTownName(), reason);
        }
        
        if (reinforcementListener != null) {
            reinforcementListener.clearReinforcements(normalizedPointId);
        }
        
        if (point != null) {
            point.setCapturingOwner(null);
            this.removeBeacon(point);
        }

        BossBar bossBar = this.captureBossBars.remove(normalizedPointId);
        this.lastBossBarAudienceSyncAt.remove(normalizedPointId);
        if (bossBar != null) {
            String deathTitle = zoneConfigManager != null
                ? zoneConfigManager.getString(normalizedPointId, "bossbar.death_title", "&c%victim% has been defeated by %killer% at %point%!")
                : this.config.getString("bossbar.death_title", "&c%victim% has been defeated by %killer% at %point%!");
            deathTitle = deathTitle.replace("%victim%", safeVictimName).replace("%killer%", safeKillerName).replace("%point%", pointName);
            bossBar.setTitle(this.colorize(deathTitle));
            bossBar.setColor(BarColor.RED);
            Bukkit.getScheduler().runTaskLater((Plugin)this, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    bossBar.removePlayer(player);
                }
                bossBar.removeAll();
            }, 100L);
        }

        cancelTask(this.captureTasks.remove(normalizedPointId));
        removeActiveSession(normalizedPointId);
        if (point != null && applyCaptureCooldown(point, normalizedPointId, CooldownTrigger.FAIL)) {
            saveCapturePoints();
        }

        String message = Messages.get("messages.capture.death_broadcast", Map.of(
            "town", session.getTownName(),
            "zone", pointName,
            "victim", safeVictimName,
            "killer", safeKillerName
        ));
        broadcastMessage(message);
        if (point != null) {
            refreshPointVisuals(normalizedPointId);
        }

        if (point == null) {
            this.getLogger().warning("Capture session '" + normalizedPointId + "' was cancelled by death but point data was missing.");
        }
    }

    public boolean stopCapture(String pointId, String reason) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        String normalizedPointId = pointId.trim();
        CaptureSession session = this.activeSessions.get(normalizedPointId);
        if (session == null) {
            return false;
        }

        CapturePoint point = this.capturePoints.get(normalizedPointId);
        String pointName = point != null ? point.getName() : normalizedPointId;
        if (point != null && point.getLocation() != null) {
            playCaptureSoundAtLocation("capture-failed", point.getLocation());
        } else {
            playCaptureSound("capture-failed");
        }
        
        if (statisticsManager != null) {
            statisticsManager.onCaptureFailed(normalizedPointId, session.getTownName(), session.getInitiatorUUID());
        }
        
        if (discordWebhook != null) {
            String webhookReason = (reason != null && !reason.isEmpty()) ? reason : Messages.get("discord.capture.cancelled.admin-reason");
            discordWebhook.sendCaptureCancelled(normalizedPointId, pointName, session.getTownName(), webhookReason);
        }

        cleanupCaptureRuntime(normalizedPointId, point, true, true);
        if (point != null && applyCaptureCooldown(point, normalizedPointId, CooldownTrigger.CANCEL)) {
            saveCapturePoints();
        }

        String message = Messages.get("messages.capture.admin_cancelled", Map.of(
            "town", session.getTownName(),
            "zone", pointName
        ));
        if (reason != null && !reason.isEmpty()) {
            message = message + " Reason: " + reason;
        }
        broadcastMessage(message);
        if (point != null) {
            refreshPointVisuals(normalizedPointId);
        }
        if (point == null) {
            this.getLogger().warning("Capture session '" + normalizedPointId + "' was stopped but point data was missing.");
        }
        return true;
    }

    public boolean setPointType(String pointId, String type) {
        CapturePoint point = capturePoints.get(pointId);
        if (point == null) {
            return false;
        }
        point.setType(type);
        saveCapturePoints();
        return true;
    }

    public boolean forceCapture(String pointId, String ownerName) {
        CapturePoint point = capturePoints.get(pointId);
        if (point == null) {
            return false;
        }

        if (this.ownerPlatform == null) {
            return false;
        }

        String normalizedOwnerName = this.ownerPlatform.normalizeOwnerName(ownerName, this.defaultOwnerType);
        if (normalizedOwnerName == null || normalizedOwnerName.isEmpty()) {
            return false;
        }
        CaptureOwner forcedOwner = normalizeOwner(this.defaultOwnerType, normalizedOwnerName);
        if (forcedOwner == null) {
            forcedOwner = new CaptureOwner(this.defaultOwnerType, null, normalizedOwnerName);
        }

        CaptureOwner previousOwner = point.getControllingOwner();
        if (previousOwner == null) {
            previousOwner = normalizeOwner(this.defaultOwnerType, point.getControllingTown());
        }
        
        if (activeSessions.containsKey(pointId)) {
            stopCapture(pointId, "Force captured by admin");
        }
        
        // Apply the same success-state transitions as a normal completed capture.
        point.setControllingOwner(forcedOwner);
        point.setCapturingOwner(null);
        point.setCaptureProgress(0.0);
        point.setLastCaptureTime(System.currentTimeMillis());
        point.setLastCapturingTown(forcedOwner.getDisplayName());
        applyCaptureCooldown(point, pointId, CooldownTrigger.SUCCESS);
        applyAntiInstantRecaptureLocks(point, pointId, previousOwner, forcedOwner);
        
        // Set owner color for map providers.
        point.setColor(getOwnerColor(forcedOwner));
        if (this.conquestManager != null) {
            this.conquestManager.onCaptureComplete(point, previousOwner, forcedOwner);
        }
        if (this.potionRewardManager != null) {
            this.potionRewardManager.clearZone(pointId);
        }
        refreshPointVisuals(pointId);
        invalidateHourlyRewardSchedule();
        
        saveCapturePoints();
        
        String message = Messages.get("messages.capture.complete", Map.of(
            "town", forcedOwner.getDisplayName(),
            "zone", point.getName()
        ));
        broadcastMessage(message);
        
        return true;
    }

    public boolean resetPoint(String pointId) {
        CapturePoint point = capturePoints.get(pointId);
        if (point == null) {
            return false;
        }

        if (activeSessions.containsKey(pointId)) {
            stopCapture(pointId, "Zone reset by admin");
        }

        point.setCapturingOwner(null);
        point.setCaptureProgress(0.0);
        point.setControllingOwner(null);
        point.setLastCapturingTown("");
        point.setLastCaptureTime(0L);
        point.clearCaptureCooldownAndLocks();
        point.setColor("#8B0000"); // Reset to default dark red color
        if (this.potionRewardManager != null) {
            this.potionRewardManager.clearZone(pointId);
        }

        refreshPointVisuals(pointId);
        invalidateHourlyRewardSchedule();

        saveCapturePoints();

        String message = Messages.get("messages.capture.reset", Map.of("zone", point.getName()));
        broadcastMessage(message);
        playCaptureSoundAtLocation("point-reset", point.getLocation());

        return true;
    }

    public int resetAllPoints() {
        int count = 0;
        for (String pointId : capturePoints.keySet()) {
            CapturePoint point = capturePoints.get(pointId);
            if (point != null) {
                if (activeSessions.containsKey(pointId)) {
                    stopCapture(pointId, "Zone reset by admin");
                }

                point.setCapturingOwner(null);
                point.setCaptureProgress(0.0);
                point.setControllingOwner(null);
                point.setLastCapturingTown("");
                point.setLastCaptureTime(0L);
                point.clearCaptureCooldownAndLocks();
                point.setColor("#8B0000"); // Reset to default dark red color
                if (this.potionRewardManager != null) {
                    this.potionRewardManager.clearZone(pointId);
                }

                count++;
            }
        }

        if (hasMapProviders()) {
            updateAllMarkers();
        }
        updateAllHolograms();
        invalidateHourlyRewardSchedule();

        saveCapturePoints();

        String message = Messages.get("messages.reset-all", Map.of("count", String.valueOf(count)));
        broadcastMessage(message);
        if (count > 0) {
            playCaptureSound("point-reset");
        }

        return count;
    }

    public boolean deleteCapturePoint(String pointId) {
        CapturePoint point = capturePoints.get(pointId);
        if (point == null) {
            return false;
        }
        
        if (activeSessions.containsKey(pointId)) {
            stopCapture(pointId, "Zone deleted by admin");
        }
        if (this.potionRewardManager != null) {
            this.potionRewardManager.clearZone(pointId);
        }
        
        if (reinforcementListener != null) {
            reinforcementListener.clearReinforcements(pointId);
        }
        
        removeBeacon(point);

        BukkitTask scheduled = captureTasks.remove(pointId);
        if (scheduled != null && !scheduled.isCancelled()) {
            scheduled.cancel();
        }

        removeCaptureBossBar(pointId);
        
        List<String> boundaryKeys = new ArrayList<>(this.boundaryTasks.keySet());
        for (String key : boundaryKeys) {
            if (key != null && key.endsWith("_" + pointId)) {
                stopBoundaryVisualization(key);
            }
        }
        
        if (hasMapProviders()) {
            removeMarker(pointId);
        }
        removeHologram(pointId);
        
        capturePoints.remove(pointId);
        captureTasks.remove(pointId);
        clearHourlyRewardTracking(pointId);
        lastWeeklyResetEpochDays.remove(pointId);
        invalidateCapturePointSpatialIndex();
        invalidateHourlyRewardSchedule();
        
        if (zoneConfigManager != null) {
            zoneConfigManager.deleteZoneConfig(pointId, true);
            getLogger().info("Backed up config for deleted zone: " + pointId);
        }
        
        saveCapturePoints();
        
        getLogger().info("Capture point '" + pointId + "' has been deleted and all associated activities stopped.");
        
        return true;
    }

    public int getMaxCapturePointsLimit() {
        int configuredLimit = config.getInt("settings.max-capture-points", 50);
        if (configuredLimit < 1) {
            return 50;
        }
        return configuredLimit;
    }

    public int getMaxActiveCapturesLimit() {
        if (!isMultiZoneConcurrentCaptureEnabled()) {
            return 1;
        }
        return getConfiguredMaxConcurrentCaptureZones();
    }

    public boolean canCreateMoreCapturePoints() {
        return capturePoints.size() < getMaxCapturePointsLimit();
    }

    public boolean createCapturePoint(String id, String type, Location location, int chunkRadius, double reward) {
        if (!canCreateMoreCapturePoints()) {
            getLogger().warning("Maximum number of capture zones reached!");
            return false;
        }
        if (location == null || location.getWorld() == null) {
            getLogger().warning("Cannot create capture zone '" + id + "': location/world is null.");
            return false;
        }
        
        int minRadius = getCapturePointInt("min-radius", 1);
        int maxRadius = getCapturePointInt("max-radius", 10);
        if (chunkRadius < minRadius) {
            chunkRadius = minRadius;
        } else if (chunkRadius > maxRadius) {
            chunkRadius = maxRadius;
        }
        
        double minReward = getCapturePointDouble("min-reward", 100.0);
        double maxReward = getCapturePointDouble("max-reward", 10000.0);
        if (reward < minReward) {
            reward = minReward;
        } else if (reward > maxReward) {
            reward = maxReward;
        }
        
        CapturePoint point = new CapturePoint(id, type, location, chunkRadius, reward);
        int minPlayers = getCapturePointInt("min-players", 1);
        int maxPlayers = getCapturePointInt("max-players", 10);
        point.setMinPlayers(minPlayers);
        point.setMaxPlayers(maxPlayers);
        capturePoints.put(id, point);
        invalidateCapturePointSpatialIndex();
        invalidateHourlyRewardSchedule();
        saveCapturePoints();
        
        if (zoneConfigManager != null) {
            zoneConfigManager.generateZoneConfig(id);
            zoneConfigManager.setZoneSetting(id, "rewards.base-reward", reward);
            getLogger().info("Generated config file for zone: " + id);
        }
        
        if (hasMapProviders()) {
            createOrUpdateMarker(point);
        }
        createOrUpdateHologram(point);
        
        if (config.getBoolean("settings.auto-show-boundaries", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                startBoundaryVisualization(player, point);
            }
        }
        return true;
    }

    public boolean createCuboidCapturePoint(
        String id,
        String type,
        World world,
        int x1,
        int y1,
        int z1,
        int x2,
        int y2,
        int z2,
        double reward
    ) {
        if (!canCreateMoreCapturePoints()) {
            getLogger().warning("Maximum number of capture zones reached!");
            return false;
        }
        if (world == null) {
            getLogger().warning("Cannot create cuboid capture zone '" + id + "': world is null.");
            return false;
        }

        double minReward = getCapturePointDouble("min-reward", 100.0);
        double maxReward = getCapturePointDouble("max-reward", 10000.0);
        if (reward < minReward) {
            reward = minReward;
        } else if (reward > maxReward) {
            reward = maxReward;
        }

        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);
        int worldMinY = world.getMinHeight();
        int worldMaxY = world.getMaxHeight() - 1;
        minY = Math.max(worldMinY, minY);
        maxY = Math.min(worldMaxY, maxY);
        if (minY > maxY) {
            minY = worldMinY;
            maxY = worldMaxY;
        }

        int width = Math.max(1, maxX - minX + 1);
        int depth = Math.max(1, maxZ - minZ + 1);
        int halfHorizontal = (int) Math.ceil(Math.max(width, depth) / 2.0);
        int approxChunkRadius = Math.max(1, (int) Math.ceil(halfHorizontal / 16.0));

        Location center = new Location(
            world,
            (minX + maxX + 1) / 2.0,
            (minY + maxY + 1) / 2.0,
            (minZ + maxZ + 1) / 2.0
        );

        CapturePoint point = new CapturePoint(id, type, center, approxChunkRadius, reward);
        point.setCuboidBounds(minX, minY, minZ, maxX, maxY, maxZ);
        int minPlayers = getCapturePointInt("min-players", 1);
        int maxPlayers = getCapturePointInt("max-players", 10);
        point.setMinPlayers(minPlayers);
        point.setMaxPlayers(maxPlayers);
        capturePoints.put(id, point);
        invalidateCapturePointSpatialIndex();
        invalidateHourlyRewardSchedule();
        saveCapturePoints();

        if (zoneConfigManager != null) {
            zoneConfigManager.generateZoneConfig(id);
            zoneConfigManager.setZoneSetting(id, "rewards.base-reward", reward);
            getLogger().info("Generated config file for cuboid zone: " + id);
        }

        if (hasMapProviders()) {
            createOrUpdateMarker(point);
        }
        createOrUpdateHologram(point);

        if (config.getBoolean("settings.auto-show-boundaries", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                startBoundaryVisualization(player, point);
            }
        }
        return true;
    }

    public void handleNewDay() {
        this.getLogger().info("New day event triggered");
        distributeRewards();
    }

    private CaptureOwner resolveControllingOwner(CapturePoint point) {
        if (point == null) {
            return null;
        }

        refreshCapturePointOwnerReferences(point);

        CaptureOwner owner = point.getControllingOwner();
        if (owner != null && owner.getDisplayName() != null && !owner.getDisplayName().isEmpty()) {
            return owner;
        }

        String legacyOwnerName = point.getControllingTown();
        if (legacyOwnerName == null || legacyOwnerName.isEmpty()) {
            return null;
        }
        return normalizeOwner(this.defaultOwnerType, legacyOwnerName);
    }

    public CaptureOwner refreshOwnerReference(CaptureOwner owner) {
        if (owner == null || this.ownerPlatform == null) {
            return owner;
        }
        CaptureOwner refreshed = this.ownerPlatform.refreshOwner(owner);
        return refreshed == null ? owner : refreshed;
    }

    public boolean refreshCapturePointOwnerReferences(CapturePoint point) {
        if (point == null) {
            return false;
        }

        boolean changed = false;

        CaptureOwner controllingOwner = point.getControllingOwner();
        CaptureOwner refreshedControllingOwner = refreshOwnerReference(controllingOwner);
        if (refreshedControllingOwner != null && !refreshedControllingOwner.equals(controllingOwner)) {
            point.setControllingOwner(refreshedControllingOwner);
            changed = true;
        }

        CaptureOwner capturingOwner = point.getCapturingOwner();
        CaptureOwner refreshedCapturingOwner = refreshOwnerReference(capturingOwner);
        if (refreshedCapturingOwner != null && !refreshedCapturingOwner.equals(capturingOwner)) {
            point.setCapturingOwner(refreshedCapturingOwner);
            changed = true;
        }

        CaptureOwner previousOwner = point.getRecaptureLockPreviousOwner();
        CaptureOwner refreshedPreviousOwner = refreshOwnerReference(previousOwner);
        if (refreshedPreviousOwner != null && !refreshedPreviousOwner.equals(previousOwner)) {
            point.setRecaptureLockPreviousOwner(refreshedPreviousOwner, point.getRecaptureLockPreviousOwnerUntilTime());
            changed = true;
        }

        CaptureOwner previousAttacker = point.getRecaptureLockPreviousAttacker();
        CaptureOwner refreshedPreviousAttacker = refreshOwnerReference(previousAttacker);
        if (refreshedPreviousAttacker != null && !refreshedPreviousAttacker.equals(previousAttacker)) {
            point.setRecaptureLockPreviousAttacker(refreshedPreviousAttacker, point.getRecaptureLockPreviousAttackerUntilTime());
            changed = true;
        }

        return changed;
    }

    private boolean refreshAllCapturePointOwnerReferences() {
        if (this.capturePoints == null || this.capturePoints.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (CapturePoint point : this.capturePoints.values()) {
            if (refreshCapturePointOwnerReferences(point)) {
                changed = true;
            }
        }
        return changed;
    }

    public String getItemRewardDisplay(String pointId) {
        List<ItemStack> rewardStacks = resolveConfiguredItemRewardStacks(pointId);
        if (rewardStacks.isEmpty()) {
            return this.zoneConfigManager != null
                ? this.zoneConfigManager.getString(pointId, "infowindow.item-payout-placeholder", "None")
                : "None";
        }
        return formatItemRewardSummary(rewardStacks);
    }

    public List<ItemStack> getConfiguredItemRewardStacks(String pointId) {
        return cloneItemStacks(resolveConfiguredItemRewardStacks(pointId));
    }

    private Player resolveRewardPermissionContextPlayer(CapturePoint point, CaptureOwner controllingOwner) {
        return findItemRewardRecipient(point, controllingOwner);
    }

    private RewardPermissionResolution resolvePermissionRewardResolution(
        String pointId,
        double baseAmount,
        Player contextPlayer
    ) {
        if (pointId == null || pointId.trim().isEmpty() || baseAmount <= 0.0 || contextPlayer == null || !contextPlayer.isOnline()) {
            return RewardPermissionResolution.none(Math.max(0.0, baseAmount));
        }
        if (!isPermissionRewardModifiersEnabled(pointId)) {
            return RewardPermissionResolution.none(Math.max(0.0, baseAmount));
        }

        List<PermissionRewardTier> tiers = resolvePermissionRewardTiers(pointId);
        if (tiers.isEmpty()) {
            return RewardPermissionResolution.none(Math.max(0.0, baseAmount));
        }

        List<PermissionRewardTier> matched = new ArrayList<>();
        for (PermissionRewardTier tier : tiers) {
            if (tier == null || tier.permission == null || tier.permission.isEmpty()) {
                continue;
            }
            if (contextPlayer.hasPermission(tier.permission)) {
                matched.add(tier);
            }
        }

        if (matched.isEmpty()) {
            return RewardPermissionResolution.none(Math.max(0.0, baseAmount));
        }

        RewardPermissionConflictPolicy conflictPolicy = resolvePermissionRewardConflictPolicy(pointId);
        double resolvedAmount = baseAmount;
        String matchedPermissions;

        if (conflictPolicy == RewardPermissionConflictPolicy.FIRST_MATCH) {
            PermissionRewardTier first = matched.get(0);
            resolvedAmount = (baseAmount * first.multiplier) + first.flatBonus;
            matchedPermissions = first.permission;
        } else if (conflictPolicy == RewardPermissionConflictPolicy.HIGHEST) {
            PermissionRewardTier bestTier = matched.get(0);
            double bestAmount = (baseAmount * bestTier.multiplier) + bestTier.flatBonus;
            for (int i = 1; i < matched.size(); i++) {
                PermissionRewardTier candidate = matched.get(i);
                double candidateAmount = (baseAmount * candidate.multiplier) + candidate.flatBonus;
                if (candidateAmount > bestAmount) {
                    bestAmount = candidateAmount;
                    bestTier = candidate;
                }
            }
            resolvedAmount = bestAmount;
            matchedPermissions = bestTier.permission;
        } else {
            double multiplierDelta = 0.0;
            double flatBonusSum = 0.0;
            List<String> names = new ArrayList<>();
            for (PermissionRewardTier tier : matched) {
                multiplierDelta += (tier.multiplier - 1.0);
                flatBonusSum += tier.flatBonus;
                names.add(tier.permission);
            }
            resolvedAmount = (baseAmount * (1.0 + multiplierDelta)) + flatBonusSum;
            matchedPermissions = String.join(", ", names);
        }

        double clampedAmount = Math.max(0.0, resolvedAmount);
        boolean applied = Math.abs(clampedAmount - baseAmount) > 0.000001d;
        return new RewardPermissionResolution(
            clampedAmount,
            applied,
            conflictPolicy.name(),
            matchedPermissions,
            contextPlayer.getName()
        );
    }

    private boolean isPermissionRewardModifiersEnabled(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        ZoneConfigManager manager = this.zoneConfigManager;
        return manager != null
            ? manager.getBoolean(pointId, "rewards.permission-modifiers.enabled", false)
            : this.config.getBoolean("rewards.permission-modifiers.enabled", false);
    }

    private RewardPermissionConflictPolicy resolvePermissionRewardConflictPolicy(String pointId) {
        ZoneConfigManager manager = this.zoneConfigManager;
        String raw = manager != null
            ? manager.getString(pointId, "rewards.permission-modifiers.conflict-policy", "HIGHEST")
            : this.config.getString("rewards.permission-modifiers.conflict-policy", "HIGHEST");
        return RewardPermissionConflictPolicy.fromConfigValue(raw);
    }

    private List<PermissionRewardTier> resolvePermissionRewardTiers(String pointId) {
        ZoneConfigManager manager = this.zoneConfigManager;
        List<?> entries = manager != null
            ? manager.getList(pointId, "rewards.permission-modifiers.tiers", Collections.emptyList())
            : this.config.getList("rewards.permission-modifiers.tiers");
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }

        List<PermissionRewardTier> tiers = new ArrayList<>();
        for (Object entry : entries) {
            if (tiers.size() >= MAX_PERMISSION_REWARD_TIERS) {
                break;
            }
            if (entry == null) {
                continue;
            }

            if (entry instanceof Map<?, ?>) {
                Map<?, ?> mapEntry = (Map<?, ?>) entry;
                String permission = mapEntry.get("permission") != null ? mapEntry.get("permission").toString().trim() : "";
                Double multiplier = parseNonNegativeDouble(mapEntry.get("multiplier"));
                Double flatBonus = parseNonNegativeDouble(mapEntry.get("flat-bonus"));
                if (permission.isEmpty() || multiplier == null || flatBonus == null) {
                    warnInvalidPermissionRewardEntry(pointId, entry, "invalid map entry");
                    continue;
                }
                tiers.add(new PermissionRewardTier(permission, multiplier, flatBonus));
                continue;
            }

            if (entry instanceof String) {
                String raw = ((String) entry).trim();
                if (raw.isEmpty()) {
                    continue;
                }
                String[] parts = raw.split(":", 3);
                String permission = parts[0].trim();
                if (permission.isEmpty()) {
                    warnInvalidPermissionRewardEntry(pointId, entry, "missing permission");
                    continue;
                }
                double multiplier = 1.0;
                double flatBonus = 0.0;
                if (parts.length > 1) {
                    Double parsedMultiplier = parseNonNegativeDouble(parts[1]);
                    if (parsedMultiplier == null) {
                        warnInvalidPermissionRewardEntry(pointId, entry, "invalid multiplier");
                        continue;
                    }
                    multiplier = parsedMultiplier;
                }
                if (parts.length > 2) {
                    Double parsedFlatBonus = parseNonNegativeDouble(parts[2]);
                    if (parsedFlatBonus == null) {
                        warnInvalidPermissionRewardEntry(pointId, entry, "invalid flat bonus");
                        continue;
                    }
                    flatBonus = parsedFlatBonus;
                }
                tiers.add(new PermissionRewardTier(permission, multiplier, flatBonus));
                continue;
            }

            warnInvalidPermissionRewardEntry(pointId, entry, "unsupported entry type");
        }

        return tiers;
    }

    private Double parseNonNegativeDouble(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        double parsed;
        if (rawValue instanceof Number) {
            parsed = ((Number) rawValue).doubleValue();
        } else {
            String text = rawValue.toString().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                parsed = Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (Double.isNaN(parsed) || Double.isInfinite(parsed) || parsed < 0.0) {
            return null;
        }
        return parsed;
    }

    private void warnInvalidPermissionRewardEntry(String pointId, Object entry, String reason) {
        String safePoint = pointId == null ? "<unknown>" : pointId;
        String rawValue = String.valueOf(entry);
        String warningKey = safePoint + "|" + rawValue + "|" + reason;
        if (!this.warnedInvalidPermissionRewardEntries.add(warningKey)) {
            return;
        }
        this.getLogger().warning(
            "Invalid permission reward tier for zone '" + safePoint + "' (" + reason + "): " + rawValue
        );
    }

    private PermissionRewardPayoutResult distributeConfiguredPermissionRewards(
        CapturePoint point,
        CaptureOwner controllingOwner,
        Player preferredRecipient,
        boolean hourlyReward
    ) {
        if (point == null || controllingOwner == null || this.permissionRewardManager == null) {
            return PermissionRewardPayoutResult.none();
        }

        List<PermissionGrantReward> grants = resolveConfiguredPermissionGrantRewards(point.getId());
        if (grants.isEmpty()) {
            return PermissionRewardPayoutResult.none();
        }

        Player recipient = preferredRecipient;
        if (recipient == null
            || !recipient.isOnline()
            || recipient.isDead()
            || !doesPlayerMatchOwner(recipient, controllingOwner)) {
            recipient = findItemRewardRecipient(point, controllingOwner);
        }

        if (recipient == null) {
            if (this.config.getBoolean("settings.debug-mode", false)) {
                this.getLogger().info(
                    "Permission reward configured for point '" + point.getId() + "' but no online owner member was found."
                );
            }
            return new PermissionRewardPayoutResult(true, false, "", "");
        }

        String sourcePrefix = "zone:" + point.getId() + ":" + (hourlyReward ? "hourly" : "daily");
        Set<String> summaries = new LinkedHashSet<>();
        int grantIndex = 0;
        for (PermissionGrantReward grant : grants) {
            if (grant == null || grant.permission == null || grant.permission.trim().isEmpty()) {
                continue;
            }
            String sourceKey = sourcePrefix + ":" + grantIndex;
            grantIndex++;
            PermissionRewardManager.GrantResult result = this.permissionRewardManager.grantPermission(
                recipient,
                grant.permission,
                sourceKey,
                grant.durationMs
            );
            if (!result.active || !result.changed) {
                continue;
            }
            summaries.add(formatPermissionGrantSummary(grant.permission, grant.durationMs));
        }

        if (summaries.isEmpty()) {
            return new PermissionRewardPayoutResult(true, false, "", recipient.getName());
        }

        return new PermissionRewardPayoutResult(
            true,
            true,
            String.join(", ", summaries),
            recipient.getName()
        );
    }

    private List<PermissionGrantReward> resolveConfiguredPermissionGrantRewards(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        ZoneConfigManager manager = this.zoneConfigManager;
        boolean enabled = manager != null
            ? manager.getBoolean(pointId, "rewards.permission-rewards.enabled", false)
            : this.config.getBoolean("rewards.permission-rewards.enabled", false);
        if (!enabled) {
            return Collections.emptyList();
        }

        List<?> entries = manager != null
            ? manager.getList(pointId, "rewards.permission-rewards.grants", Collections.emptyList())
            : this.config.getList("rewards.permission-rewards.grants");
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }

        List<PermissionGrantReward> output = new ArrayList<>();
        appendParsedPermissionGrantEntries(pointId, entries, output, "rewards.permission-rewards.grants");
        return output;
    }

    private void appendParsedPermissionGrantEntries(
        String pointId,
        List<?> entries,
        List<PermissionGrantReward> output,
        String sourcePath
    ) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        for (Object entry : entries) {
            if (output.size() >= MAX_PERMISSION_REWARD_GRANTS) {
                break;
            }
            if (entry == null) {
                continue;
            }

            if (entry instanceof String) {
                appendPermissionGrantFromString(pointId, (String) entry, output, sourcePath);
                continue;
            }

            if (entry instanceof Map<?, ?>) {
                appendPermissionGrantFromMap(pointId, (Map<?, ?>) entry, output, sourcePath, entry);
                continue;
            }

            warnInvalidPermissionGrantEntry(pointId, sourcePath, entry, "unsupported entry type");
        }
    }

    private void appendPermissionGrantFromString(
        String pointId,
        String rawValue,
        List<PermissionGrantReward> output,
        String sourcePath
    ) {
        if (rawValue == null) {
            return;
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return;
        }

        String[] parts = trimmed.split(":", 2);
        String permission = parts[0] == null ? "" : parts[0].trim();
        if (permission.isEmpty()) {
            warnInvalidPermissionGrantEntry(pointId, sourcePath, rawValue, "missing permission");
            return;
        }

        long durationMs = 0L;
        if (parts.length > 1) {
            Long parsed = parsePermissionRewardDurationMs(parts[1]);
            if (parsed == null) {
                warnInvalidPermissionGrantEntry(pointId, sourcePath, rawValue, "invalid duration");
                return;
            }
            durationMs = parsed;
        }

        output.add(new PermissionGrantReward(permission, durationMs));
    }

    private void appendPermissionGrantFromMap(
        String pointId,
        Map<?, ?> mapEntry,
        List<PermissionGrantReward> output,
        String sourcePath,
        Object rawEntry
    ) {
        Object permissionRaw = mapEntry.get("permission");
        String permission = permissionRaw == null ? "" : permissionRaw.toString().trim();
        if (permission.isEmpty()) {
            warnInvalidPermissionGrantEntry(pointId, sourcePath, rawEntry, "missing permission");
            return;
        }

        Boolean permanent = null;
        if (mapEntry.containsKey("permanent")) {
            permanent = parseOptionalBoolean(mapEntry.get("permanent"));
            if (permanent == null) {
                warnInvalidPermissionGrantEntry(pointId, sourcePath, rawEntry, "invalid permanent flag");
                return;
            }
        }

        long durationMs = 0L;
        if (Boolean.TRUE.equals(permanent)) {
            durationMs = 0L;
        } else if (mapEntry.containsKey("duration-ms")) {
            Long parsed = parsePermissionRewardDurationMs(mapEntry.get("duration-ms"));
            if (parsed == null) {
                warnInvalidPermissionGrantEntry(pointId, sourcePath, rawEntry, "invalid duration-ms");
                return;
            }
            durationMs = parsed;
        } else if (mapEntry.containsKey("duration")) {
            Long parsed = parsePermissionRewardDurationMs(mapEntry.get("duration"));
            if (parsed == null) {
                warnInvalidPermissionGrantEntry(pointId, sourcePath, rawEntry, "invalid duration");
                return;
            }
            durationMs = parsed;
        } else if (Boolean.FALSE.equals(permanent)) {
            warnInvalidPermissionGrantEntry(pointId, sourcePath, rawEntry, "permanent=false requires duration or duration-ms");
            return;
        }

        output.add(new PermissionGrantReward(permission, durationMs));
    }

    private Boolean parseOptionalBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        if ("true".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw) || "on".equalsIgnoreCase(raw) || "1".equals(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw) || "no".equalsIgnoreCase(raw) || "off".equalsIgnoreCase(raw) || "0".equals(raw)) {
            return false;
        }
        return null;
    }

    private Long parsePermissionRewardDurationMs(Object rawValue) {
        if (rawValue == null) {
            return null;
        }

        if (rawValue instanceof Number) {
            long parsed = ((Number) rawValue).longValue();
            if (parsed < 0L) {
                return null;
            }
            return clampPermissionRewardDuration(parsed);
        }

        String token = rawValue.toString().trim();
        if (token.isEmpty()) {
            return null;
        }
        String normalized = token.toLowerCase(Locale.ROOT);

        if ("permanent".equals(normalized)
            || "perm".equals(normalized)
            || "forever".equals(normalized)
            || "infinite".equals(normalized)
            || "none".equals(normalized)) {
            return 0L;
        }

        try {
            if (normalized.matches("^\\d+$")) {
                return clampPermissionRewardDuration(Long.parseLong(normalized));
            }
        } catch (NumberFormatException ignored) {
            return null;
        }

        Matcher matcher = PERMISSION_REWARD_DURATION_PATTERN.matcher(normalized);
        if (!matcher.matches()) {
            return null;
        }

        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }

        String unit = matcher.group(2);
        long multiplier;
        switch (unit) {
            case "ms":
                multiplier = 1L;
                break;
            case "s":
                multiplier = 1000L;
                break;
            case "m":
                multiplier = 60_000L;
                break;
            case "h":
                multiplier = 3_600_000L;
                break;
            case "d":
                multiplier = 86_400_000L;
                break;
            case "w":
                multiplier = 604_800_000L;
                break;
            default:
                return null;
        }

        if (amount < 0L) {
            return null;
        }
        if (amount > 0L && multiplier > 0L && amount > (Long.MAX_VALUE / multiplier)) {
            return MAX_PERMISSION_REWARD_DURATION_MS;
        }

        return clampPermissionRewardDuration(amount * multiplier);
    }

    private long clampPermissionRewardDuration(long durationMs) {
        if (durationMs <= 0L) {
            return 0L;
        }
        return Math.min(durationMs, MAX_PERMISSION_REWARD_DURATION_MS);
    }

    private String formatPermissionGrantSummary(String permission, long durationMs) {
        String safePermission = permission == null ? "" : permission.trim();
        if (safePermission.isEmpty()) {
            safePermission = "unknown.permission";
        }
        return safePermission + " (" + formatPermissionRewardDuration(durationMs) + ")";
    }

    private String formatPermissionRewardDuration(long durationMs) {
        if (durationMs <= 0L) {
            return Messages.get("messages.reward.permission.duration-permanent");
        }

        long totalSeconds = Math.max(1L, (durationMs + 999L) / 1000L);
        long days = totalSeconds / 86400L;
        totalSeconds %= 86400L;
        long hours = totalSeconds / 3600L;
        totalSeconds %= 3600L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;

        List<String> parts = new ArrayList<>(4);
        if (days > 0L) {
            parts.add(days + "d");
        }
        if (hours > 0L) {
            parts.add(hours + "h");
        }
        if (minutes > 0L) {
            parts.add(minutes + "m");
        }
        if (seconds > 0L || parts.isEmpty()) {
            parts.add(seconds + "s");
        }

        if (parts.size() > 2) {
            return parts.get(0) + " " + parts.get(1);
        }
        return String.join(" ", parts);
    }

    private void warnInvalidPermissionGrantEntry(String pointId, String sourcePath, Object rawEntry, String reason) {
        String safePoint = pointId == null ? "<unknown>" : pointId;
        String path = sourcePath == null ? "<unknown-path>" : sourcePath;
        String rawValue = String.valueOf(rawEntry);
        String warningKey = safePoint + "|" + path + "|" + rawValue + "|" + reason;
        if (!this.warnedInvalidPermissionGrantEntries.add(warningKey)) {
            return;
        }
        this.getLogger().warning(
            "Invalid permission grant reward entry for zone '"
                + safePoint + "' at '" + path + "' (" + reason + "): " + rawValue
        );
    }

    private ItemRewardPayoutResult distributeConfiguredItemReward(CapturePoint point, CaptureOwner controllingOwner, Player preferredRecipient) {
        if (point == null || controllingOwner == null) {
            return ItemRewardPayoutResult.none();
        }

        List<ItemStack> rewardStacks = resolveConfiguredItemRewardStacks(point.getId());
        if (rewardStacks.isEmpty()) {
            return ItemRewardPayoutResult.none();
        }

        String summary = formatItemRewardSummary(rewardStacks);
        Player recipient = preferredRecipient;
        if (recipient == null
            || !recipient.isOnline()
            || recipient.isDead()
            || !doesPlayerMatchOwner(recipient, controllingOwner)) {
            recipient = findItemRewardRecipient(point, controllingOwner);
        }
        if (recipient == null) {
            if (this.config.getBoolean("settings.debug-mode", false)) {
                this.getLogger().info("Item reward configured for point '" + point.getId() + "' but no online owner member was found.");
            }
            return new ItemRewardPayoutResult(true, false, false, summary, "");
        }

        List<ItemStack> clonedStacks = cloneItemStacks(rewardStacks);
        ItemRewardInventoryFullBehavior fullBehavior = resolveItemRewardInventoryFullBehavior(point.getId());
        if (fullBehavior == ItemRewardInventoryFullBehavior.CANCEL) {
            boolean added = tryAddItemsAtomically(recipient.getInventory(), clonedStacks);
            if (!added) {
                sendNotification(recipient, Messages.get("messages.reward.item.inventory-full-cancelled", Map.of(
                    "zone", point.getName(),
                    "items", summary
                )));
                return new ItemRewardPayoutResult(true, false, false, summary, recipient.getName());
            }
            return new ItemRewardPayoutResult(true, true, false, summary, recipient.getName());
        }

        Map<Integer, ItemStack> leftovers = recipient.getInventory().addItem(clonedStacks.toArray(new ItemStack[0]));
        boolean dropped = false;
        if (!leftovers.isEmpty()) {
            Location dropLocation = recipient.getLocation();
            if (dropLocation.getWorld() != null) {
                for (ItemStack leftover : leftovers.values()) {
                    if (leftover == null || leftover.getType() == Material.AIR || leftover.getAmount() <= 0) {
                        continue;
                    }
                    dropLocation.getWorld().dropItemNaturally(dropLocation, leftover);
                    dropped = true;
                }
            }
        }

        return new ItemRewardPayoutResult(true, true, dropped, summary, recipient.getName());
    }

    private List<ItemStack> resolveConfiguredItemRewardStacks(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<ItemStack> output = new ArrayList<>();
        ZoneConfigManager manager = this.zoneConfigManager;

        boolean modernEnabled = manager != null
            ? manager.getBoolean(pointId, "rewards.item-rewards.enabled", false)
            : this.config.getBoolean("rewards.item-rewards.enabled", false);

        if (modernEnabled) {
            List<?> entries = manager != null
                ? manager.getList(pointId, "rewards.item-rewards.items", Collections.emptyList())
                : this.config.getList("rewards.item-rewards.items");
            appendParsedItemRewardEntries(pointId, entries, output, "rewards.item-rewards.items");
        }

        if (output.isEmpty()) {
            String legacyRaw = manager != null
                ? manager.getString(pointId, "rewards.item-payout", "None")
                : this.config.getString("rewards.item-payout", "None");
            appendLegacyItemReward(pointId, legacyRaw, output);
        }

        return output;
    }

    private void appendParsedItemRewardEntries(
        String pointId,
        List<?> entries,
        List<ItemStack> output,
        String sourcePath
    ) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        for (Object entry : entries) {
            if (entry == null) {
                continue;
            }

            if (entry instanceof String) {
                appendItemRewardFromString(pointId, (String) entry, output, sourcePath);
                continue;
            }

            if (entry instanceof Map<?, ?>) {
                Map<?, ?> mapEntry = (Map<?, ?>) entry;
                Object materialRaw = mapEntry.get("material");
                Object amountRaw = mapEntry.get("amount");
                if (materialRaw != null || amountRaw != null) {
                    String materialToken = materialRaw != null ? materialRaw.toString() : "";
                    Integer amount = parsePositiveInteger(amountRaw);
                    if (amount == null) {
                        warnInvalidItemRewardEntry(pointId, sourcePath, entry, "missing or invalid amount");
                        continue;
                    }
                    appendItemRewardFromMaterialAmount(pointId, materialToken, amount, output, sourcePath, entry);
                    continue;
                }

                ItemStack serializedStack = deserializeSerializedItemRewardStack(pointId, sourcePath, mapEntry, entry);
                if (serializedStack == null) {
                    warnInvalidItemRewardEntry(pointId, sourcePath, entry, "invalid item reward map");
                    continue;
                }
                appendItemRewardFromItemStack(pointId, serializedStack, output, sourcePath, entry);
                continue;
            }

            warnInvalidItemRewardEntry(pointId, sourcePath, entry, "unsupported item reward entry type");
        }
    }

    private void appendLegacyItemReward(String pointId, String rawValue, List<ItemStack> output) {
        if (rawValue == null) {
            return;
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty() || "none".equalsIgnoreCase(trimmed)) {
            return;
        }
        appendItemRewardFromString(pointId, trimmed, output, "rewards.item-payout");
    }

    private void appendItemRewardFromString(
        String pointId,
        String rawValue,
        List<ItemStack> output,
        String sourcePath
    ) {
        if (rawValue == null) {
            return;
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty() || "none".equalsIgnoreCase(trimmed)) {
            return;
        }

        String[] parts = trimmed.split(":", 2);
        String materialToken = parts[0] != null ? parts[0].trim() : "";
        int amount = 1;
        if (parts.length > 1) {
            Integer parsedAmount = parsePositiveInteger(parts[1]);
            if (parsedAmount == null) {
                warnInvalidItemRewardEntry(pointId, sourcePath, rawValue, "invalid amount");
                return;
            }
            amount = parsedAmount;
        }

        appendItemRewardFromMaterialAmount(pointId, materialToken, amount, output, sourcePath, rawValue);
    }

    private Integer parsePositiveInteger(Object rawValue) {
        if (rawValue == null) {
            return null;
        }

        int parsed;
        if (rawValue instanceof Number) {
            parsed = ((Number) rawValue).intValue();
        } else {
            String text = rawValue.toString().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                parsed = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return parsed > 0 ? parsed : null;
    }

    private void appendItemRewardFromMaterialAmount(
        String pointId,
        String materialToken,
        int amount,
        List<ItemStack> output,
        String sourcePath,
        Object rawEntry
    ) {
        if (materialToken == null || materialToken.trim().isEmpty()) {
            warnInvalidItemRewardEntry(pointId, sourcePath, rawEntry, "missing material");
            return;
        }

        Material material = Material.matchMaterial(materialToken.trim());
        if (material == null) {
            try {
                material = Material.valueOf(materialToken.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                material = null;
            }
        }

        if (material == null || material == Material.AIR || !material.isItem()) {
            warnInvalidItemRewardEntry(pointId, sourcePath, rawEntry, "invalid material");
            return;
        }

        int clampedAmount = Math.min(amount, MAX_ITEM_REWARD_ENTRY_AMOUNT);
        if (clampedAmount != amount) {
            warnInvalidItemRewardEntry(
                pointId,
                sourcePath,
                rawEntry,
                "amount exceeds max " + MAX_ITEM_REWARD_ENTRY_AMOUNT + " and was clamped"
            );
        }

        int maxStack = Math.max(1, material.getMaxStackSize());
        int remaining = clampedAmount;
        while (remaining > 0) {
            int stackAmount = Math.min(maxStack, remaining);
            output.add(new ItemStack(material, stackAmount));
            remaining -= stackAmount;
        }
    }

    private ItemStack deserializeSerializedItemRewardStack(
        String pointId,
        String sourcePath,
        Map<?, ?> rawMap,
        Object rawEntry
    ) {
        if (rawMap == null || rawMap.isEmpty()) {
            return null;
        }

        Map<String, Object> serialized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            serialized.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        if (serialized.isEmpty()) {
            return null;
        }

        try {
            return ItemStack.deserialize(serialized);
        } catch (Exception ex) {
            warnInvalidItemRewardEntry(
                pointId,
                sourcePath,
                rawEntry,
                "failed to deserialize custom item (" + ex.getMessage() + ")"
            );
            return null;
        }
    }

    private void appendItemRewardFromItemStack(
        String pointId,
        ItemStack stack,
        List<ItemStack> output,
        String sourcePath,
        Object rawEntry
    ) {
        if (stack == null) {
            warnInvalidItemRewardEntry(pointId, sourcePath, rawEntry, "null item stack");
            return;
        }

        Material material = stack.getType();
        if (material == null || material == Material.AIR || !material.isItem()) {
            warnInvalidItemRewardEntry(pointId, sourcePath, rawEntry, "invalid custom item material");
            return;
        }

        int amount = stack.getAmount();
        if (amount <= 0) {
            warnInvalidItemRewardEntry(pointId, sourcePath, rawEntry, "custom item amount must be positive");
            return;
        }

        int clampedAmount = Math.min(amount, MAX_ITEM_REWARD_ENTRY_AMOUNT);
        if (clampedAmount != amount) {
            warnInvalidItemRewardEntry(
                pointId,
                sourcePath,
                rawEntry,
                "custom item amount exceeds max " + MAX_ITEM_REWARD_ENTRY_AMOUNT + " and was clamped"
            );
        }

        int maxStack = Math.max(1, material.getMaxStackSize());
        ItemStack template = stack.clone();
        template.setAmount(1);
        int remaining = clampedAmount;
        while (remaining > 0) {
            int stackAmount = Math.min(maxStack, remaining);
            ItemStack nextStack = template.clone();
            nextStack.setAmount(stackAmount);
            output.add(nextStack);
            remaining -= stackAmount;
        }
    }

    private void warnInvalidItemRewardEntry(String pointId, String sourcePath, Object rawEntry, String reason) {
        String safePoint = pointId == null ? "<unknown>" : pointId;
        String rawValue = String.valueOf(rawEntry);
        String warningKey = safePoint + "|" + sourcePath + "|" + rawValue + "|" + reason;
        if (!this.warnedInvalidItemRewardEntries.add(warningKey)) {
            return;
        }
        this.getLogger().warning(
            "Invalid item reward entry for zone '" + safePoint + "' at '" + sourcePath + "' (" + reason + "): " + rawValue
        );
    }

    private ItemRewardInventoryFullBehavior resolveItemRewardInventoryFullBehavior(String pointId) {
        String raw = this.zoneConfigManager != null
            ? this.zoneConfigManager.getString(pointId, "rewards.item-rewards.inventory-full-behavior", "DROP")
            : this.config.getString("rewards.item-rewards.inventory-full-behavior", "DROP");
        return ItemRewardInventoryFullBehavior.fromConfigValue(raw);
    }

    private Player findItemRewardRecipient(CapturePoint point, CaptureOwner controllingOwner) {
        if (point == null || controllingOwner == null) {
            return null;
        }

        List<Player> candidates = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null || !online.isOnline() || online.isDead()) {
                continue;
            }
            if (!doesPlayerMatchOwner(online, controllingOwner)) {
                continue;
            }
            candidates.add(online);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        candidates.sort((first, second) -> {
            boolean firstInZone = isWithinZone(point, first.getLocation());
            boolean secondInZone = isWithinZone(point, second.getLocation());
            if (firstInZone != secondInZone) {
                return firstInZone ? -1 : 1;
            }
            return first.getName().compareToIgnoreCase(second.getName());
        });
        return candidates.get(0);
    }

    private boolean tryAddItemsAtomically(Inventory inventory, List<ItemStack> itemStacks) {
        if (inventory == null || itemStacks == null || itemStacks.isEmpty()) {
            return false;
        }

        ItemStack[] snapshot = cloneStorageContents(inventory.getStorageContents());
        Map<Integer, ItemStack> leftovers = inventory.addItem(cloneItemStacks(itemStacks).toArray(new ItemStack[0]));
        if (leftovers == null || leftovers.isEmpty()) {
            return true;
        }

        inventory.setStorageContents(snapshot);
        return false;
    }

    private ItemStack[] cloneStorageContents(ItemStack[] storageContents) {
        if (storageContents == null) {
            return new ItemStack[0];
        }
        ItemStack[] snapshot = new ItemStack[storageContents.length];
        for (int i = 0; i < storageContents.length; i++) {
            ItemStack stack = storageContents[i];
            snapshot[i] = stack == null ? null : stack.clone();
        }
        return snapshot;
    }

    private List<ItemStack> cloneItemStacks(List<ItemStack> itemStacks) {
        List<ItemStack> clones = new ArrayList<>();
        if (itemStacks == null) {
            return clones;
        }
        for (ItemStack stack : itemStacks) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                continue;
            }
            clones.add(stack.clone());
        }
        return clones;
    }

    private String formatItemRewardSummary(List<ItemStack> itemStacks) {
        if (itemStacks == null || itemStacks.isEmpty()) {
            return "None";
        }

        Map<Material, Integer> totals = new LinkedHashMap<>();
        for (ItemStack stack : itemStacks) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                continue;
            }
            totals.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }

        if (totals.isEmpty()) {
            return "None";
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : totals.entrySet()) {
            parts.add(entry.getKey().name() + " x" + entry.getValue());
        }
        return String.join(", ", parts);
    }

    public void distributeRewards() {
        for (CapturePoint point : this.capturePoints.values()) {
            if (!isDailyReward(point.getId())) {
                continue;
            }
            CaptureOwner controllingOwner = resolveControllingOwner(point);
            if (controllingOwner == null) {
                continue;
            }
            CaptureOwnerType ownerType = controllingOwner.getType() != null ? controllingOwner.getType() : this.defaultOwnerType;
            String ownerName = controllingOwner.getDisplayName();
            if (ownerName == null || ownerName.trim().isEmpty()) {
                continue;
            }
            if (this.ownerPlatform == null || !this.ownerPlatform.ownerExists(ownerName, ownerType)) {
                point.setControllingOwner(null);
                if (this.hasMapProviders()) {
                    this.createOrUpdateMarker(point);
                }
                this.createOrUpdateHologram(point);
                continue;
            }
            try {
                boolean economyDistributed = false;
                double baseReward = getBaseReward(point);
                Player rewardContextPlayer = resolveRewardPermissionContextPlayer(point, controllingOwner);
                RewardPermissionResolution permissionResolution = resolvePermissionRewardResolution(
                    point.getId(),
                    baseReward,
                    rewardContextPlayer
                );
                double resolvedEconomyReward = permissionResolution.finalAmount;
                EconomyRewardPayoutResult economyResult = EconomyRewardPayoutResult.none(resolveMoneyPayoutMode(point.getId()));
                if (resolvedEconomyReward > 0.0) {
                    economyResult = distributeEconomyReward(controllingOwner == null ? null : point, controllingOwner, resolvedEconomyReward, "Reward for controlling " + point.getName());
                    if (!economyResult.delivered) {
                        this.getLogger().warning("Failed to deposit daily reward for owner '" + ownerName + "'.");
                    } else {
                        economyDistributed = true;
                    }
                }

                if (economyDistributed) {
                    this.getLogger().info("Gave " + economyResult.totalPaid + " to " + ownerName + " for controlling " + point.getName()
                        + " (" + economyResult.mode + ", recipients=" + economyResult.recipients + ", failed=" + economyResult.failedRecipients + ")");
                    if (permissionResolution.applied && this.config.getBoolean("settings.debug-mode", false)) {
                        this.getLogger().info(
                            "Applied permission reward modifiers for zone '" + point.getId() + "' via "
                                + permissionResolution.contextPlayerName + " (policy=" + permissionResolution.policyName
                                + ", matches=" + permissionResolution.matchedPermissions + ")."
                        );
                    }
                    
                    if (statisticsManager != null) {
                        statisticsManager.onRewardDistributed(
                            point.getId(), 
                            point.getName(), 
                            ownerName, 
                            economyResult.totalPaid
                        );
                    }
                    
                    if (discordWebhook != null) {
                        discordWebhook.sendRewardsDistributed(
                            point.getId(), 
                            point.getName(), 
                            ownerName, 
                            economyResult.totalPaid, 
                            "daily"
                        );
                    }
                    
                    String message = Messages.get("messages.reward.distributed", Map.of(
                        "town", ownerName,
                        "reward", String.format("%.2f", economyResult.totalPaid),
                        "zone", point.getName()
                    ));
                    broadcastMessage(message);
                }

                ItemRewardPayoutResult itemResult = distributeConfiguredItemReward(point, controllingOwner, rewardContextPlayer);
                if (itemResult.delivered) {
                    String itemMessage = Messages.get("messages.reward.item.distributed", Map.of(
                        "town", ownerName,
                        "zone", point.getName(),
                        "items", itemResult.summary,
                        "player", itemResult.recipientName,
                        "delivery", itemResult.dropped
                            ? Messages.get("messages.reward.item.delivery-dropped")
                            : Messages.get("messages.reward.item.delivery-inventory")
                    ));
                    broadcastMessage(itemMessage);
                }

                PermissionRewardPayoutResult permissionRewardResult = distributeConfiguredPermissionRewards(
                    point,
                    controllingOwner,
                    rewardContextPlayer,
                    false
                );
                if (permissionRewardResult.delivered) {
                    String permissionMessage = Messages.get("messages.reward.permission.distributed", Map.of(
                        "town", ownerName,
                        "zone", point.getName(),
                        "permissions", permissionRewardResult.summary,
                        "player", permissionRewardResult.recipientName
                    ));
                    broadcastMessage(permissionMessage);
                }
            }
            catch (Exception e) {
                this.getLogger().warning("Failed to give reward to " + ownerName);
                e.printStackTrace();
            }
        }
    }

    public void distributeHourlyRewards() {
        long now = System.currentTimeMillis();
        if (this.hourlyRewardScheduleDirty || (now - this.hourlyRewardScheduleLastRebuildAt) >= 60000L) {
            rebuildHourlyRewardSchedule(now);
        }
        if (this.hourlyRewardNextDueTimes.isEmpty()) {
            return;
        }
        if (now < this.hourlyRewardEarliestDueAt) {
            return;
        }

        boolean debug = this.config.getBoolean("settings.debug-mode", false);
        List<String> duePointIds = new ArrayList<>();
        synchronized (this.hourlyRewardNextDueTimes) {
            for (Map.Entry<String, Long> entry : this.hourlyRewardNextDueTimes.entrySet()) {
                Long dueAt = entry.getValue();
                if (dueAt == null || dueAt.longValue() <= now) {
                    duePointIds.add(entry.getKey());
                }
            }
        }
        if (duePointIds.isEmpty()) {
            recomputeHourlyRewardEarliestDue();
            return;
        }

        for (String pointId : duePointIds) {
            if (pointId == null || pointId.trim().isEmpty()) {
                clearHourlyRewardTracking(pointId);
                continue;
            }
            CapturePoint point = this.capturePoints.get(pointId);
            if (point == null || !isHourlyReward(pointId) || point.getControllingTown().isEmpty()) {
                clearHourlyRewardTracking(pointId);
                continue;
            }
            this.lastHourlyRewardTimes.put(pointId, now);
            scheduleNextHourlyReward(pointId, now);

            CaptureOwner controllingOwner = resolveControllingOwner(point);
            if (controllingOwner == null) {
                continue;
            }
            CaptureOwnerType ownerType = controllingOwner.getType() != null ? controllingOwner.getType() : this.defaultOwnerType;
            String ownerName = controllingOwner.getDisplayName();
            if (ownerName == null || ownerName.trim().isEmpty()) {
                continue;
            }
            if (this.ownerPlatform == null || !this.ownerPlatform.ownerExists(ownerName, ownerType)) {
                this.getLogger().warning("Owner '" + ownerName + "' not found, resetting point");
                point.setControllingOwner(null);
                clearHourlyRewardTracking(pointId);
                refreshPointVisuals(pointId);
                continue;
            }
            try {
                boolean economyDistributed = false;
                double hourlyReward = calculateHourlyReward(point);
                Player rewardContextPlayer = resolveRewardPermissionContextPlayer(point, controllingOwner);
                RewardPermissionResolution permissionResolution = resolvePermissionRewardResolution(
                    point.getId(),
                    hourlyReward,
                    rewardContextPlayer
                );
                double resolvedHourlyReward = permissionResolution.finalAmount;
                EconomyRewardPayoutResult economyResult = EconomyRewardPayoutResult.none(resolveMoneyPayoutMode(point.getId()));
                if (resolvedHourlyReward > 0.0) {
                    if (debug) {
                        this.getLogger().info("Depositing " + resolvedHourlyReward + " to owner " + ownerName);
                    }
                    economyResult = distributeEconomyReward(point, controllingOwner, resolvedHourlyReward, "Hourly reward for controlling " + point.getName());
                    if (!economyResult.delivered) {
                        this.getLogger().warning("Failed to deposit hourly reward for owner '" + ownerName + "'.");
                    } else {
                        economyDistributed = true;
                    }
                }

                if (economyDistributed) {
                    if (debug) {
                        this.getLogger().info("Gave " + economyResult.totalPaid + " to " + ownerName + " for controlling " + point.getName()
                            + " (hourly, " + economyResult.mode + ", recipients=" + economyResult.recipients + ", failed=" + economyResult.failedRecipients + ")");
                    }
                    if (permissionResolution.applied && this.config.getBoolean("settings.debug-mode", false)) {
                        this.getLogger().info(
                            "Applied permission reward modifiers for zone '" + point.getId() + "' via "
                                + permissionResolution.contextPlayerName + " (policy=" + permissionResolution.policyName
                                + ", matches=" + permissionResolution.matchedPermissions + ")."
                        );
                    }
                    
                    if (statisticsManager != null) {
                        statisticsManager.onRewardDistributed(
                            point.getId(), 
                            point.getName(), 
                            ownerName, 
                            economyResult.totalPaid
                        );
                    }
                    
                    if (discordWebhook != null) {
                        discordWebhook.sendRewardsDistributed(
                            point.getId(), 
                            point.getName(), 
                            ownerName, 
                            economyResult.totalPaid, 
                            "hourly"
                        );
                    }
                    
                    String message = Messages.get("messages.reward.hourly_distributed", Map.of(
                        "town", ownerName,
                        "reward", String.format("%.2f", economyResult.totalPaid),
                        "zone", point.getName()
                    ));
                    broadcastMessage(message);
                }

                ItemRewardPayoutResult itemResult = distributeConfiguredItemReward(point, controllingOwner, rewardContextPlayer);
                if (itemResult.delivered) {
                    String itemMessage = Messages.get("messages.reward.item.hourly_distributed", Map.of(
                        "town", ownerName,
                        "zone", point.getName(),
                        "items", itemResult.summary,
                        "player", itemResult.recipientName,
                        "delivery", itemResult.dropped
                            ? Messages.get("messages.reward.item.delivery-dropped")
                            : Messages.get("messages.reward.item.delivery-inventory")
                    ));
                    broadcastMessage(itemMessage);
                }

                PermissionRewardPayoutResult permissionRewardResult = distributeConfiguredPermissionRewards(
                    point,
                    controllingOwner,
                    rewardContextPlayer,
                    true
                );
                if (permissionRewardResult.delivered) {
                    String permissionMessage = Messages.get("messages.reward.permission.hourly_distributed", Map.of(
                        "town", ownerName,
                        "zone", point.getName(),
                        "permissions", permissionRewardResult.summary,
                        "player", permissionRewardResult.recipientName
                    ));
                    broadcastMessage(permissionMessage);
                }
            }
            catch (Exception e) {
                this.getLogger().warning("Failed to give hourly reward to " + ownerName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        recomputeHourlyRewardEarliestDue();
    }

    public void invalidateHourlyRewardSchedule() {
        this.hourlyRewardScheduleDirty = true;
        this.hourlyRewardEarliestDueAt = Long.MAX_VALUE;
        this.hourlyRewardScheduleLastRebuildAt = 0L;
    }

    private void rebuildHourlyRewardSchedule(long now) {
        this.hourlyRewardNextDueTimes.clear();
        this.lastHourlyRewardTimes.keySet().removeIf(pointId -> !this.capturePoints.containsKey(pointId));

        for (CapturePoint point : this.capturePoints.values()) {
            if (point == null || point.getId() == null || point.getId().trim().isEmpty()) {
                continue;
            }

            String pointId = point.getId().trim();
            if (!isHourlyReward(pointId) || point.getControllingTown().isEmpty()) {
                clearHourlyRewardTracking(pointId);
                continue;
            }

            Long lastRewardTime = this.lastHourlyRewardTimes.get(pointId);
            if (lastRewardTime == null || lastRewardTime.longValue() > now) {
                lastRewardTime = now;
                this.lastHourlyRewardTimes.put(pointId, lastRewardTime);
            }
            scheduleNextHourlyReward(pointId, lastRewardTime.longValue());
        }

        recomputeHourlyRewardEarliestDue();
        this.hourlyRewardScheduleDirty = false;
        this.hourlyRewardScheduleLastRebuildAt = now;
    }

    private void scheduleNextHourlyReward(String pointId, long baseTimeMs) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }
        int intervalSeconds = Math.max(1, getHourlyIntervalSeconds(pointId));
        long intervalMs = intervalSeconds * 1000L;
        long dueAt = baseTimeMs + intervalMs;
        if (dueAt < 0L || dueAt < baseTimeMs) {
            dueAt = Long.MAX_VALUE;
        }
        this.hourlyRewardNextDueTimes.put(pointId, dueAt);
        if (dueAt < this.hourlyRewardEarliestDueAt) {
            this.hourlyRewardEarliestDueAt = dueAt;
        }
    }

    private void clearHourlyRewardTracking(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }
        this.lastHourlyRewardTimes.remove(pointId);
        this.hourlyRewardNextDueTimes.remove(pointId);
    }

    private void recomputeHourlyRewardEarliestDue() {
        long earliest = Long.MAX_VALUE;
        synchronized (this.hourlyRewardNextDueTimes) {
            for (Long dueAt : this.hourlyRewardNextDueTimes.values()) {
                if (dueAt == null) {
                    continue;
                }
                if (dueAt.longValue() < earliest) {
                    earliest = dueAt.longValue();
                }
            }
        }
        this.hourlyRewardEarliestDueAt = earliest;
    }

    private double calculateHourlyReward(CapturePoint point) {
        // Use zone-specific config, fallback to zone defaults
        String mode = "STATIC";
        if (zoneConfigManager != null) {
            mode = zoneConfigManager.getString(point.getId(), "rewards.hourly-mode", "STATIC");
        }
        double base = getBaseReward(point) / 24.0;
        if ("DYNAMIC".equalsIgnoreCase(mode)) {
            double min = zoneConfigManager != null
                ? zoneConfigManager.getDouble(point.getId(), "rewards.hourly-dynamic.min", base)
                : base;
            double max = zoneConfigManager != null
                ? zoneConfigManager.getDouble(point.getId(), "rewards.hourly-dynamic.max", base)
                : base;
            if (max < min) {
                max = min;
            }
            return min + (Math.random() * (max - min));
        }
        return base;
    }

    private String getRewardType(String pointId) {
        if (zoneConfigManager == null || pointId == null) {
            return "daily";
        }
        String value = zoneConfigManager.getString(pointId, "rewards.reward-type", "daily");
        if (value == null) {
            return "daily";
        }
        String normalized = value.trim();
        if (!"daily".equalsIgnoreCase(normalized) && !"hourly".equalsIgnoreCase(normalized)) {
            return "daily";
        }
        return normalized;
    }

    private boolean isHourlyReward(String pointId) {
        return "hourly".equalsIgnoreCase(getRewardType(pointId));
    }

    private boolean isDailyReward(String pointId) {
        return "daily".equalsIgnoreCase(getRewardType(pointId));
    }

    private int getHourlyIntervalSeconds(String pointId) {
        if (zoneConfigManager == null || pointId == null) {
            return 3600;
        }
        int interval = zoneConfigManager.getInt(pointId, "rewards.hourly-interval", 3600);
        return Math.max(1, interval);
    }

    public double getBaseReward(CapturePoint point) {
        if (point == null) {
            return 0.0;
        }
        double fallback = point.getReward();
        if (zoneConfigManager == null) {
            return Math.max(0.0, fallback);
        }
        double reward = zoneConfigManager.getDouble(point.getId(), "rewards.base-reward", fallback);
        return Math.max(0.0, reward);
    }

    public String getPointTypeName(String typeKey) {
        if (typeKey == null || typeKey.trim().isEmpty()) {
            return "Unknown";
        }
        return this.pointTypes.getOrDefault(typeKey, typeKey);
    }
    
    public DiscordWebhook getDiscordWebhook() {
        return this.discordWebhook;
    }

    public String colorize(String message) {
        if (message == null) {
            return "";
        }
        message = message.replace("&", "\u00a7");
        Pattern pattern = Pattern.compile("\\{#([a-zA-Z0-9]+)>(.*?)\\}");
        Matcher matcher = pattern.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String colorName = matcher.group(1);
            String text = matcher.group(2);
            String colorCode = this.getColorCodeFromName(colorName);
            if (colorCode != null) {
                matcher.appendReplacement(sb, colorCode + text + "\u00a7r");
                continue;
            }
            matcher.appendReplacement(sb, "\u00a7cInvalid Color: " + colorName + "\u00a7r" + text);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String getColorCodeFromName(String colorName) {
        if (colorName == null) {
            return null;
        }
        switch (colorName = colorName.toLowerCase()) {
            case "black": {
                return "\u00a70";
            }
            case "darkblue": {
                return "\u00a71";
            }
            case "darkgreen": {
                return "\u00a72";
            }
            case "darkaqua": 
            case "darkcyan": {
                return "\u00a73";
            }
            case "darkred": {
                return "\u00a74";
            }
            case "darkpurple": 
            case "darkmagenta": {
                return "\u00a75";
            }
            case "gold": {
                return "\u00a76";
            }
            case "darkyellow": {
                return "\u00a76";
            }
            case "gray": {
                return "\u00a77";
            }
            case "grey": {
                return "\u00a77";
            }
            case "darkgray": {
                return "\u00a78";
            }
            case "darkgrey": {
                return "\u00a78";
            }
            case "blue": {
                return "\u00a79";
            }
            case "green": {
                return "\u00a7a";
            }
            case "aqua": {
                return "\u00a7b";
            }
            case "cyan": {
                return "\u00a7b";
            }
            case "red": {
                return "\u00a7c";
            }
            case "lightpurple": {
                return "\u00a7d";
            }
            case "magenta": {
                return "\u00a7d";
            }
            case "yellow": {
                return "\u00a7e";
            }
            case "white": {
                return "\u00a7f";
            }
        }
        if (colorName.startsWith("#") && colorName.length() == 7) {
            return "\u00a7x" + colorName.charAt(1) + colorName.charAt(1) + colorName.charAt(2) + colorName.charAt(2) + colorName.charAt(3) + colorName.charAt(3) + colorName.charAt(4) + colorName.charAt(4) + colorName.charAt(5) + colorName.charAt(5) + colorName.charAt(6) + colorName.charAt(6);
        }
        return null;
    }

    public Map<String, CapturePoint> getCapturePoints() {
        refreshAllCapturePointOwnerReferences();
        return this.capturePoints;
    }

    public Map<String, CaptureSession> getActiveSessions() {
        return this.activeSessions;
    }

    public KothManager getKothManager() {
        return this.kothManager;
    }

    public ConquestManager getConquestManager() {
        return this.conquestManager;
    }

    public Set<String> getActiveKothZoneIds() {
        if (this.kothManager == null) {
            return Collections.emptySet();
        }
        return this.kothManager.getActiveZoneIds();
    }

    public String getConquestStatusLine(String pointId) {
        if (this.conquestManager == null) {
            return "";
        }
        return this.conquestManager.getStatusLineForZone(pointId);
    }

    public boolean isKothZoneActive(String pointId) {
        if (this.kothManager == null) {
            return false;
        }
        return this.kothManager.isZoneActive(pointId);
    }

    public boolean isCaptureTeleportEnabled() {
        return this.config.getBoolean("settings.capture-teleport-enabled", true);
    }

    public boolean hasMapProviders() {
        return !this.mapProviders.isEmpty();
    }

    public boolean isDynmapEnabled() {
        return this.townUpdater != null;
    }

    public boolean isWorldGuardEnabled() {
        return this.worldGuardEnabled;
    }

    public Map<String, String> getPointTypes() {
        if (!this.pointTypes.isEmpty()) {
            return this.pointTypes;
        }
        Map<String, String> discoveredTypes = new LinkedHashMap<>();
        for (CapturePoint point : this.capturePoints.values()) {
            if (point == null || point.getType() == null || point.getType().trim().isEmpty()) {
                continue;
            }
            String type = point.getType().trim();
            discoveredTypes.putIfAbsent(type, type);
        }
        return discoveredTypes;
    }

    public void cancelCapture(String pointId, String reason) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }

        String normalizedPointId = pointId.trim();
        CaptureSession session = this.activeSessions.get(normalizedPointId);
        if (session == null) {
            return;
        }

        CapturePoint point = this.capturePoints.get(normalizedPointId);
        String pointName = point != null ? point.getName() : normalizedPointId;
        if (point != null && point.getLocation() != null) {
            playCaptureSoundAtLocation("capture-failed", point.getLocation());
        } else {
            playCaptureSound("capture-failed");
        }
        cleanupCaptureRuntime(normalizedPointId, point, true, true);
        if (point != null && applyCaptureCooldown(point, normalizedPointId, CooldownTrigger.CANCEL)) {
            saveCapturePoints();
        }

        String message;
        if (reason != null && !reason.isEmpty()) {
            message = Messages.get("messages.capture.broadcast-cancelled-reason", Map.of(
                "town", session.getTownName(),
                "zone", pointName,
                "reason", getLocalizedReason(reason)
            ));
        } else {
            message = Messages.get("messages.capture.broadcast-cancelled", Map.of(
                "town", session.getTownName(),
                "zone", pointName
            ));
        }
        broadcastMessage(message);
        if (this.hasMapProviders()) {
            this.updateAllMarkers();
        }
        this.updateAllHolograms();
        if (point == null) {
            this.getLogger().warning("Capture session '" + normalizedPointId + "' was cancelled but point data was missing.");
        }
    }

    public CapturePoint getCapturePoint(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        CapturePoint point = this.capturePoints.get(id.trim());
        refreshCapturePointOwnerReferences(point);
        return point;
    }

    public CaptureSession getActiveSession(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return null;
        }
        return this.activeSessions.get(pointId.trim());
    }

    public boolean isPointActive(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return false;
        }
        return this.activeSessions.containsKey(pointId.trim());
    }

    public void stopCapture(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }
        String normalizedPointId = pointId.trim();
        CapturePoint point = this.capturePoints.get(normalizedPointId);
        cleanupCaptureRuntime(normalizedPointId, point, true, true);
    }

    public void removeCapturePoint(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }
        String normalizedPointId = pointId.trim();
        this.stopCapture(normalizedPointId);
        this.capturePoints.remove(normalizedPointId);
        if (this.potionRewardManager != null) {
            this.potionRewardManager.clearZone(normalizedPointId);
        }
        clearHourlyRewardTracking(normalizedPointId);
        this.removeHologram(normalizedPointId);
        invalidateCapturePointSpatialIndex();
        invalidateHourlyRewardSchedule();
    }

    public void logCaptureAttempt(String pointId) {
        this.captureAttempts.merge(pointId, 1, Integer::sum);
        this.getLogger().info("Capture attempt recorded for point: " + pointId);
    }

    public void logSuccessfulCapture(String pointId) {
        this.successfulCaptures.merge(pointId, 1, Integer::sum);
        this.getLogger().info("Successful capture recorded for point: " + pointId);
    }

    public void logError(String pointId, String error) {
        long currentTime = System.currentTimeMillis();
        Long lastError = this.lastErrorTime.get(pointId);
        long throttleMs = this.config.getLong("settings.error-throttle-ms", 60000L);
        if (lastError == null || currentTime - lastError >= throttleMs) {
            if (this.config.getBoolean("settings.debug-mode", false)) {
                this.getLogger().severe("Error for point " + pointId + ": " + error);
            }
            this.lastErrorTime.put(pointId, currentTime);
        }
    }

    public Map<String, Integer> getCaptureAttempts() {
        return new HashMap<String, Integer>(this.captureAttempts);
    }

    public Map<String, Integer> getSuccessfulCaptures() {
        return new HashMap<String, Integer>(this.successfulCaptures);
    }

    public void resetPointStatistics(String pointId) {
        this.captureAttempts.remove(pointId);
        this.successfulCaptures.remove(pointId);
        this.lastErrorTime.remove(pointId);
        this.getLogger().info("Statistics reset for point: " + pointId);
    }

    public void setPointActive(String pointId, boolean active) {
        CapturePoint point = this.getCapturePoint(pointId);
        if (point != null) {
            point.setActive(active);
            this.getLogger().info("Point " + pointId + " set to " + (active ? "active" : "inactive"));
        }
    }

    public void setPointPlayerLimits(String pointId, int minPlayers, int maxPlayers) {
        CapturePoint point = this.getCapturePoint(pointId);
        if (point != null) {
            point.setMinPlayers(minPlayers);
            point.setMaxPlayers(maxPlayers);
            this.getLogger().info("Player limits set for point " + pointId + ": min=" + minPlayers + ", max=" + maxPlayers);
        }
    }

    private String getOwnerColor(CaptureOwner owner) {
        if (owner == null || owner.getDisplayName() == null || owner.getDisplayName().isEmpty()) {
            return "#8B0000";
        }
        if (this.ownerPlatform == null) {
            return "#8B0000";
        }
        return this.ownerPlatform.resolveMapColorHex(owner, "#8B0000");
    }

    private String getTownColor(String townName) {
        CaptureOwner owner = normalizeOwner(this.defaultOwnerType, townName);
        if (owner == null) {
            return "#8B0000";
        }
        return getOwnerColor(owner);
    }

    private void removeCaptureBossBar(String pointId) {
        BossBar bossBar = captureBossBars.remove(pointId);
        this.lastBossBarAudienceSyncAt.remove(pointId);
        if (bossBar != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                bossBar.removePlayer(player);
            }
            bossBar.removeAll();
        }
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }

    private void playCaptureProgressSoundIfNeeded(String pointId, CapturePoint point, CaptureSession session) {
        if (pointId == null || point == null || session == null || point.getLocation() == null) {
            return;
        }

        int initialCaptureTime = Math.max(1, session.getInitialCaptureTime());
        int remainingCaptureTime = Math.max(0, session.getRemainingCaptureTime());
        if (remainingCaptureTime <= 0 || remainingCaptureTime >= initialCaptureTime) {
            return;
        }

        int currentBucket = remainingCaptureTime / 60;
        Integer previousBucket = this.captureProgressSoundBuckets.get(pointId);
        if (previousBucket != null && previousBucket == currentBucket) {
            return;
        }

        this.captureProgressSoundBuckets.put(pointId, currentBucket);
        playCaptureSoundAtLocation("capture-progress", point.getLocation());
    }
    
    public void playCaptureSound(String soundEvent) {
        if (!config.getBoolean("sounds.enabled", true)) {
            return;
        }
        
        String soundName = config.getString("sounds." + soundEvent + ".sound", "entity.player.levelup");
        float volume = (float) config.getDouble("sounds." + soundEvent + ".volume", 1.0);
        float pitch = (float) config.getDouble("sounds." + soundEvent + ".pitch", 1.0);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Skip if player has disabled notifications
            if (disabledNotifications.getOrDefault(player.getUniqueId(), false)) {
                continue;
            }
            try {
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                getLogger().warning("Invalid sound: " + soundName);
            }
        }
    }
    
    public void playCaptureSoundAtLocation(String soundEvent, Location location) {
        if (!config.getBoolean("sounds.enabled", true)) {
            return;
        }
        
        String soundName = config.getString("sounds." + soundEvent + ".sound", "entity.player.levelup");
        float volume = (float) config.getDouble("sounds." + soundEvent + ".volume", 1.0);
        float pitch = (float) config.getDouble("sounds." + soundEvent + ".pitch", 1.0);
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld() == location.getWorld() && 
                player.getLocation().distance(location) < 100 &&
                !disabledNotifications.getOrDefault(player.getUniqueId(), false)) {
                try {
                    org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
                    player.playSound(location, sound, volume, pitch);
                } catch (Exception e) {
                    getLogger().warning("Invalid sound: " + soundName);
                }
            }
        }
    }

    private int getActiveCapturesForTown(String townName) {
        return (int)this.capturePoints.values().stream()
            .filter(point -> point.getCapturingTown() != null && point.getCapturingTown().equals(townName))
            .count();
    }

    private void broadcastMessage(String message) {
        if (!config.getBoolean("settings.show-chat-messages", true)) {
            return;
        }
        if (message == null || message.isEmpty()) {
            return;
        }
        String formatted = colorize(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (disabledNotifications.getOrDefault(player.getUniqueId(), false)) {
                continue;
            }
            player.sendMessage(formatted);
        }
    }

    public void toggleChatMessages(boolean enabled) {
        config.set("settings.show-chat-messages", enabled);
        saveConfig();
        String status = enabled ? "enabled" : "disabled";
        getLogger().info("Capture chat messages have been " + status + " globally");
        if (enabled) {
            broadcastMessage(colorize("&6[CaptureZones] &eCapture messages have been enabled globally"));
        }
    }

    private class PlayerEventListener
    implements Listener {
        @EventHandler
        public void onPlayerJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            if (!CaptureZones.this.hiddenBossBars.getOrDefault(player.getUniqueId(), false).booleanValue() &&
                !CaptureZones.this.disabledNotifications.getOrDefault(player.getUniqueId(), false)) {
                CaptureZones.this.refreshBossBarAudienceForPlayer(player);
            }
            
            // Auto-show boundaries for new player if enabled
            if (CaptureZones.this.config.getBoolean("settings.auto-show-boundaries", true)) {
                CaptureZones.this.autoShowBoundariesForPlayer(player);
            }

            if (CaptureZones.this.potionRewardManager != null) {
                CaptureZones.this.potionRewardManager.onPlayerJoin(player);
            }
            
            // Show update notification to admins
            if (CaptureZones.this.getConfig().getBoolean("settings.check-for-updates", true) && 
                PermissionNode.has(player, "admin.update-notify") &&
                CaptureZones.this.updateChecker != null) {
                CaptureZones.this.updateChecker.showUpdateNotification(player);
            }
        }

        @EventHandler
        public void onPlayerQuit(PlayerQuitEvent event) {
            Player player = event.getPlayer();
            CaptureZones.this.removePlayerFromAllBossBars(player);
            CaptureZones.this.stopBoundaryVisualizationsForPlayer(player.getUniqueId());
        }
    }

    public boolean startTestCapture(Player player, String pointId, int preparationSeconds, int captureSeconds) {
        CapturePoint point = capturePoints.get(pointId);
        if (point == null) {
            return false;
        }

        CaptureOwner owner = resolveCaptureOwner(player);
        String ownerName = owner != null ? owner.getDisplayName() : null;
        if (ownerName == null || ownerName.isEmpty()) {
            return false;
        }

        if (!isWithinZone(point, player.getLocation())) {
            return false;
        }

        createBeacon(point.getLocation());

        CaptureSession session = new CaptureSession(point, owner, player, preparationSeconds / 60, captureSeconds / 60);
        session.getPlayers().add(player);
        activeSessions.put(pointId, session);

        BossBar bossBar = Bukkit.createBossBar(
            colorize("&e[TEST] Capturing: " + point.getName()),
            BarColor.BLUE,
            BarStyle.SOLID
        );
        captureBossBars.put(pointId, bossBar);
        
        int visibilityExtraChunks = resolveBossbarVisibilityExtraChunks();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (shouldShowBossBarToPlayer(point, online, visibilityExtraChunks)) {
                bossBar.addPlayer(online);
            } else {
                bossBar.removePlayer(online);
            }
        }

        BukkitTask prepTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!session.isActive()) {
                return;
            }

            CaptureOwner activeOwner = session.getOwner() != null ? session.getOwner() : owner;
            if (activeOwner == null || !hasAnyCapturingOwnerPlayerInZone(point, activeOwner)) {
                cancelCapture(pointId, buildMovedTooFarReason(resolveMovedTooFarPlayerName(session, activeOwner)));
                return;
            }

            session.decrementPreparationTime();
            int timeLeft = session.getRemainingPreparationTime();
            
            if (timeLeft <= 0) {
                startCapturePhase(point, owner, player);
                return;
            }
        }, 20L, 20L);

        captureTasks.put(pointId, prepTask);
        return true;
    }

    public String buildMovedTooFarReason(Player player) {
        if (player == null) {
            return CANCEL_REASON_MOVED_TOO_FAR;
        }
        return buildMovedTooFarReason(player.getName());
    }

    public String buildMovedTooFarReason(String playerName) {
        if (playerName == null || playerName.trim().isEmpty()) {
            return CANCEL_REASON_MOVED_TOO_FAR;
        }
        return CANCEL_REASON_MOVED_TOO_FAR_PREFIX + playerName.trim() + CANCEL_REASON_MOVED_TOO_FAR_SUFFIX;
    }

    private String resolveMovedTooFarPlayerName(CaptureSession session, CaptureOwner owner) {
        if (session == null) {
            return null;
        }

        UUID initiatorUUID = session.getInitiatorUUID();
        if (initiatorUUID != null) {
            Player initiator = Bukkit.getPlayer(initiatorUUID);
            if (initiator != null) {
                return initiator.getName();
            }
        }

        for (Player participant : session.getPlayers()) {
            if (participant != null && participant.isOnline()) {
                return participant.getName();
            }
        }

        CapturePoint sessionPoint = session.getPoint();
        if (sessionPoint != null && sessionPoint.getLocation() != null &&
            sessionPoint.getLocation().getWorld() != null && owner != null) {
            for (Player online : sessionPoint.getLocation().getWorld().getPlayers()) {
                if (online == null || !online.isOnline()) {
                    continue;
                }
                if (doesPlayerMatchOwner(online, owner)) {
                    return online.getName();
                }
            }
        }

        String fallbackName = session.getPlayerName();
        return (fallbackName == null || fallbackName.trim().isEmpty()) ? null : fallbackName;
    }

    private String getLocalizedReason(String reason) {
        if (reason == null || reason.isEmpty()) {
            return "";
        }

        if (reason.startsWith(CANCEL_REASON_MOVED_TOO_FAR_PREFIX) &&
            reason.endsWith(CANCEL_REASON_MOVED_TOO_FAR_SUFFIX)) {
            String playerName = reason.substring(
                CANCEL_REASON_MOVED_TOO_FAR_PREFIX.length(),
                reason.length() - CANCEL_REASON_MOVED_TOO_FAR_SUFFIX.length()
            ).trim();
            if (!playerName.isEmpty()) {
                return Messages.get("reasons.moved-too-far-player", Map.of("player", playerName));
            }
        }

        switch (reason) {
            case CANCEL_REASON_MOVED_TOO_FAR:
                return Messages.get("reasons.moved-too-far");
            case "All participants disconnected":
                return Messages.get("reasons.all-disconnected");
            case "Session timeout":
                return Messages.get("reasons.session-timeout");
            case "Grace timer expired":
                return Messages.get("reasons.grace-expired");
            default:
                return reason; // Return original reason if not recognized
        }
    }
    
    public StatisticsManager getStatisticsManager() {
        return statisticsManager;
    }
    
    public StatisticsGUI getStatisticsGUI() {
        return statisticsGUI;
    }
    
    public ZoneConfigManager getZoneConfigManager() {
        return zoneConfigManager;
    }
    
    public ShopManager getShopManager() {
        return shopManager;
    }

    public ShopEconomyAdapter getShopEconomyAdapter() {
        return shopEconomyAdapter;
    }

    public ShopEconomyAdapter getOrCreateShopEconomyAdapter() {
        ShopEconomyAdapter resolved = resolveShopEconomyAdapter();
        if (resolved == null || !resolved.isAvailable()) {
            shopEconomyAdapter = null;
            return null;
        }
        if (shopEconomyAdapter != null
            && shopEconomyAdapter.getClass().equals(resolved.getClass())
            && shopEconomyAdapter.isAvailable()) {
            return shopEconomyAdapter;
        }
        shopEconomyAdapter = resolved;
        return shopEconomyAdapter;
    }

    public ShopManager getOrCreateShopManagerIfEnabled() {
        if (!getConfig().getBoolean("shops.enabled", false)) {
            shutdownShopSystem();
            return null;
        }
        if (shopManager != null) {
            if (getOrCreateShopEconomyAdapter() == null) {
                shutdownShopSystem();
                return null;
            }
            return shopManager;
        }
        if (getOrCreateShopEconomyAdapter() == null) {
            return null;
        }
        setupShopSystem();
        return shopManager;
    }

    public String getShopSystemUnavailableReason() {
        if (!getConfig().getBoolean("shops.enabled", false)) {
            return "Shop system is disabled in config (shops.enabled=false).";
        }
        if (getOrCreateShopEconomyAdapter() != null) {
            return "";
        }
        return "Shop system requires a supported economy provider (Towny or Vault).";
    }

    public String getShopEconomyProviderName() {
        ShopEconomyAdapter adapter = getOrCreateShopEconomyAdapter();
        if (adapter != null && adapter.isAvailable()) {
            return adapter.getProviderName();
        }
        return "";
    }

    private ShopEconomyAdapter resolveShopEconomyAdapter() {
        if (this.ownerPlatform instanceof TownyOwnerPlatformAdapter && isTownyIntegrationEnabled()) {
            return new TownyShopEconomyAdapter();
        }
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            ShopEconomyAdapter vaultAdapter = new VaultShopEconomyAdapter(this);
            if (vaultAdapter.isAvailable()) {
                return vaultAdapter;
            }
        }
        if (isTownyIntegrationEnabled()) {
            return new TownyShopEconomyAdapter();
        }
        return null;
    }
    
    public ShopListener getShopListener() {
        return shopListener;
    }

    public ZoneItemRewardEditor getZoneItemRewardEditor() {
        return zoneItemRewardEditor;
    }

    public CaptureZonesApi getApiService() {
        return apiService;
    }
}


