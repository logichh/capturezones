package com.logichh.capturezones;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class HologramManager {
    private static final String PROVIDER_NONE = "NONE";
    private static final String PROVIDER_TEXT_DISPLAY = "TEXT_DISPLAY";
    private static final int MIN_UPDATE_INTERVAL_TICKS = 5;
    private static final int MAX_LINES_HARD_LIMIT = 20;
    private static final int MAX_INSTANCES_HARD_LIMIT = 4;
    private static final double MAX_HORIZONTAL_OFFSET = 16.0d;
    private static final double MIN_VISIBILITY_RANGE_BLOCKS = 8.0d;
    private static final double MAX_VISIBILITY_RANGE_BLOCKS = 256.0d;
    private static final double DEFAULT_VISIBILITY_RANGE_BLOCKS = 64.0d;
    private static final double[][] CARDINAL_OFFSETS = new double[][]{
        {0.0d, -1.0d},
        {0.0d, 1.0d},
        {-1.0d, 0.0d},
        {1.0d, 0.0d}
    };
    private static final float[] CARDINAL_YAWS = new float[]{180.0f, 0.0f, 90.0f, -90.0f};

    private final CaptureZones plugin;
    private final Logger logger;
    private final Set<String> proximitySuspendedPoints;
    private HologramProvider provider;
    private BukkitTask updateTask;

    public HologramManager(CaptureZones plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.proximitySuspendedPoints = new HashSet<>();
        this.provider = new NoopHologramProvider();
    }

    public void initialize() {
        shutdown();

        if (!isGloballyEnabled()) {
            this.provider = new NoopHologramProvider();
            this.provider.initialize();
            if (isDebugEnabled()) {
                logger.info("Holograms are disabled in config.");
            }
            return;
        }

        this.provider = createProvider();
        if (!this.provider.initialize() || !this.provider.isAvailable()) {
            logger.warning("Hologram provider '" + this.provider.getName() + "' is unavailable. Holograms disabled.");
            this.provider.cleanup();
            this.provider = new NoopHologramProvider();
            this.provider.initialize();
            return;
        }

        int interval = resolveUpdateIntervalTicks();
        this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::safeUpdateAll, 1L, interval);
        safeUpdateAll();
        logger.info("Hologram provider enabled: " + this.provider.getName() + " (update interval " + interval + " ticks)");
    }

    public void reload() {
        initialize();
    }

    public void shutdown() {
        if (this.updateTask != null && !this.updateTask.isCancelled()) {
            this.updateTask.cancel();
        }
        this.updateTask = null;

        if (this.provider != null) {
            this.provider.cleanup();
        }
        this.provider = new NoopHologramProvider();
        this.provider.initialize();
        this.proximitySuspendedPoints.clear();
    }

    public void updateAll() {
        if (!isActive()) {
            return;
        }
        Map<UUID, List<Location>> viewerLocationsByWorld = collectViewerLocationsByWorld();
        this.proximitySuspendedPoints.retainAll(plugin.getCapturePoints().keySet());
        for (CapturePoint point : plugin.getCapturePoints().values()) {
            updatePoint(point, viewerLocationsByWorld);
        }
    }

    public void updatePoint(CapturePoint point) {
        updatePoint(point, null);
    }

    private void updatePoint(CapturePoint point, Map<UUID, List<Location>> viewerLocationsByWorld) {
        if (point == null || point.getId() == null || point.getId().trim().isEmpty()) {
            return;
        }
        String pointId = point.getId();
        if (!isActive() || !isZoneHologramEnabled(pointId) || !plugin.shouldDisplayPoint(point)) {
            proximitySuspendedPoints.remove(pointId);
            removePoint(pointId);
            return;
        }

        Location pointLocation = point.getLocation();
        if (pointLocation == null || pointLocation.getWorld() == null) {
            proximitySuspendedPoints.remove(pointId);
            removePoint(pointId);
            return;
        }

        if (shouldSuspendWhenNoPlayersNearby(pointId)) {
            double visibilityRange = resolveVisibilityRange(pointId);
            if (!hasNearbyViewer(pointLocation, visibilityRange, viewerLocationsByWorld)) {
                if (proximitySuspendedPoints.add(pointId)) {
                    removePoint(pointId);
                }
                return;
            }
        }
        proximitySuspendedPoints.remove(pointId);

        List<String> lines = resolveRenderedLines(point);
        if (lines.isEmpty()) {
            proximitySuspendedPoints.remove(pointId);
            removePoint(pointId);
            return;
        }

        double heightOffset = resolveZoneDouble(pointId, "hologram.height-offset", resolveDefaultHeightOffset());
        double spacing = resolveZoneDouble(pointId, "hologram.line-spacing", resolveDefaultLineSpacing());
        int configuredInstances = resolveZoneInt(pointId, "hologram.instances", resolveDefaultInstances());
        int instances = Math.max(1, Math.min(MAX_INSTANCES_HARD_LIMIT, configuredInstances));
        double configuredHorizontalOffset = resolveZoneDouble(
            pointId,
            "hologram.horizontal-offset",
            resolveDefaultHorizontalOffset()
        );
        double horizontalOffset = clamp(configuredHorizontalOffset, 0.0d, MAX_HORIZONTAL_OFFSET);
        boolean fixedOrientation = resolveZoneBoolean(pointId, "hologram.fixed-orientation", resolveDefaultFixedOrientation());

        Location centerBase = pointLocation.clone().add(0.0d, heightOffset, 0.0d);
        List<Location> instanceBases = buildInstanceBases(centerBase, instances, horizontalOffset, fixedOrientation);
        int effectiveInstances = Math.max(1, instanceBases.size());
        for (int index = 0; index < instanceBases.size(); index++) {
            String hologramId = buildInstanceId(pointId, effectiveInstances, index);
            provider.createOrUpdate(hologramId, instanceBases.get(index), lines, spacing, fixedOrientation);
        }
        removeUnusedInstanceIds(pointId, effectiveInstances);
    }

    public void removePoint(String pointId) {
        if (pointId == null || pointId.trim().isEmpty() || this.provider == null) {
            return;
        }
        this.provider.remove(pointId);
        for (int index = 0; index < MAX_INSTANCES_HARD_LIMIT; index++) {
            this.provider.remove(buildInstanceId(pointId, MAX_INSTANCES_HARD_LIMIT, index));
        }
    }

    public boolean isActive() {
        return this.provider != null && this.provider.isAvailable();
    }

    private void safeUpdateAll() {
        try {
            updateAll();
        } catch (Exception e) {
            logger.warning("Failed to update holograms: " + e.getMessage());
            if (isDebugEnabled()) {
                e.printStackTrace();
            }
        }
    }

    private HologramProvider createProvider() {
        String configured = plugin.getConfig().getString("holograms.provider", PROVIDER_TEXT_DISPLAY);
        String normalized = configured == null ? PROVIDER_TEXT_DISPLAY : configured.trim().toUpperCase(Locale.ROOT);

        switch (normalized) {
            case PROVIDER_TEXT_DISPLAY:
                return new TextDisplayHologramProvider(plugin);
            case PROVIDER_NONE:
                return new NoopHologramProvider();
            default:
                logger.warning("Unknown hologram provider '" + configured + "'. Falling back to " + PROVIDER_TEXT_DISPLAY + ".");
                return new TextDisplayHologramProvider(plugin);
        }
    }

    private boolean isGloballyEnabled() {
        return plugin.getConfig().getBoolean("holograms.enabled", false);
    }

    private int resolveUpdateIntervalTicks() {
        int configured = plugin.getConfig().getInt("holograms.update-interval-ticks", 40);
        return Math.max(MIN_UPDATE_INTERVAL_TICKS, configured);
    }

    private boolean isZoneHologramEnabled(String pointId) {
        return resolveZoneBoolean(pointId, "hologram.enabled", false);
    }

    private double resolveDefaultHeightOffset() {
        return plugin.getConfig().getDouble("holograms.default-height-offset", -5.0d);
    }

    private double resolveDefaultLineSpacing() {
        return plugin.getConfig().getDouble("holograms.default-line-spacing", 0.25d);
    }

    private int resolveDefaultMaxLines() {
        int configured = plugin.getConfig().getInt("holograms.default-max-lines", 8);
        return Math.max(1, Math.min(MAX_LINES_HARD_LIMIT, configured));
    }

    private int resolveDefaultInstances() {
        int configured = plugin.getConfig().getInt("holograms.default-instances", 1);
        return Math.max(1, Math.min(MAX_INSTANCES_HARD_LIMIT, configured));
    }

    private double resolveDefaultHorizontalOffset() {
        double configured = plugin.getConfig().getDouble("holograms.default-horizontal-offset", 1.25d);
        return clamp(configured, 0.0d, MAX_HORIZONTAL_OFFSET);
    }

    private boolean resolveDefaultFixedOrientation() {
        return plugin.getConfig().getBoolean("holograms.default-fixed-orientation", false);
    }

    private boolean shouldSuspendWhenNoPlayersNearby(String pointId) {
        return resolveZoneBoolean(
            pointId,
            "hologram.pause-when-no-players-nearby",
            plugin.getConfig().getBoolean("holograms.pause-when-no-players-nearby", true)
        );
    }

    private double resolveVisibilityRange(String pointId) {
        double defaultRange = plugin.getConfig().getDouble("holograms.visibility-range", DEFAULT_VISIBILITY_RANGE_BLOCKS);
        double configured = resolveZoneDouble(pointId, "hologram.visibility-range", defaultRange);
        return clamp(configured, MIN_VISIBILITY_RANGE_BLOCKS, MAX_VISIBILITY_RANGE_BLOCKS);
    }

    private Map<UUID, List<Location>> collectViewerLocationsByWorld() {
        Map<UUID, List<Location>> viewerLocationsByWorld = new LinkedHashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }
            Location playerLocation = player.getLocation();
            if (playerLocation.getWorld() == null) {
                continue;
            }
            UUID worldId = playerLocation.getWorld().getUID();
            viewerLocationsByWorld.computeIfAbsent(worldId, ignored -> new ArrayList<>()).add(playerLocation);
        }
        return viewerLocationsByWorld;
    }

    private boolean hasNearbyViewer(
        Location pointLocation,
        double rangeBlocks,
        Map<UUID, List<Location>> viewerLocationsByWorld
    ) {
        if (pointLocation == null || pointLocation.getWorld() == null) {
            return false;
        }
        double rangeSquared = rangeBlocks * rangeBlocks;
        if (viewerLocationsByWorld != null) {
            List<Location> worldViewerLocations = viewerLocationsByWorld.get(pointLocation.getWorld().getUID());
            if (worldViewerLocations == null || worldViewerLocations.isEmpty()) {
                return false;
            }
            for (Location playerLocation : worldViewerLocations) {
                if (playerLocation != null && playerLocation.distanceSquared(pointLocation) <= rangeSquared) {
                    return true;
                }
            }
            return false;
        }

        for (Player player : pointLocation.getWorld().getPlayers()) {
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }
            if (player.getLocation().distanceSquared(pointLocation) <= rangeSquared) {
                return true;
            }
        }
        return false;
    }

    private List<String> resolveRenderedLines(CapturePoint point) {
        if (point == null) {
            return Collections.emptyList();
        }
        String pointId = point.getId();
        int maxLines = (int) Math.max(1, Math.min(MAX_LINES_HARD_LIMIT,
            resolveZoneInt(pointId, "hologram.max-lines", resolveDefaultMaxLines())));

        List<String> templates = resolveLineTemplates(pointId);
        if (templates.isEmpty()) {
            return Collections.emptyList();
        }

        CaptureSession session = plugin.getActiveSession(pointId);
        Map<String, String> values = resolveTemplateValues(point, session);

        List<String> rendered = new ArrayList<>();
        for (String template : templates) {
            if (rendered.size() >= maxLines) {
                break;
            }
            if (template == null) {
                continue;
            }
            String line = applyValues(template, values);
            rendered.add(plugin.colorize(line));
        }
        return rendered;
    }

    private List<String> resolveLineTemplates(String pointId) {
        KothManager kothManager = plugin.getKothManager();
        if (kothManager != null) {
            List<String> kothLines = kothManager.getHologramLines(pointId);
            if (!kothLines.isEmpty()) {
                return kothLines;
            }
        }

        List<String> parsed = parseStringList(resolveZoneList(pointId, "hologram.lines"));
        if (!parsed.isEmpty()) {
            return parsed;
        }

        parsed = parseStringList(plugin.getConfig().getList("holograms.default-lines"));
        if (!parsed.isEmpty()) {
            return parsed;
        }

        return List.of(
            "&6&l{zone}",
            "&7{label_status}: &e{status}",
            "&7{label_owner}: &b{owner}",
            "&7{label_reward}: &a${reward}",
            "&7{label_time}: &f{time_left}"
        );
    }

    private Map<String, String> resolveTemplateValues(CapturePoint point, CaptureSession session) {
        Map<String, String> values = new LinkedHashMap<>();
        KothManager.ZoneStateSnapshot kothState = resolveKothState(point);
        String ownerName = resolveOwnerName(point);
        String ownerType = resolveOwnerType(point);
        String capturingOwner = resolveCapturingOwnerName(point, session);
        String capturingOwnerType = resolveCapturingOwnerType(point, session);
        String status = resolveStatusText(point, session);
        String timeLeft = formatTime(session != null ? Math.max(0, session.getRemainingTime()) : 0);
        String progress = String.format(Locale.ROOT, "%.1f", resolveProgress(point, session));

        values.put("{zone}", safe(point.getName()));
        values.put("{zone_id}", safe(point.getId()));
        values.put("{status}", status);
        values.put("{owner}", ownerName);
        values.put("{owner_type}", ownerType);
        values.put("{capturing_owner}", capturingOwner);
        values.put("{capturing_owner_type}", capturingOwnerType);
        values.put("{reward}", String.format(Locale.ROOT, "%.2f", plugin.getBaseReward(point)));
        values.put("{item_reward}", safe(plugin.getItemRewardDisplay(point.getId())));
        values.put("{time_left}", timeLeft);
        values.put("{progress}", progress);
        values.put("{label_status}", Messages.get("hologram.label.status"));
        values.put("{label_owner}", Messages.get("hologram.label.owner"));
        values.put("{label_reward}", Messages.get("hologram.label.reward"));
        values.put("{label_time}", Messages.get("hologram.label.time"));
        values.put("{koth_status}", resolveKothStatusText(kothState));
        values.put("{koth_holder}", resolveKothHolderText(kothState));
        values.put("{koth_progress}", String.valueOf(kothState != null ? kothState.progressPercent() : 0));
        values.put("{koth_time_left}", formatTime(kothState != null ? kothState.remainingSeconds() : 0));
        return values;
    }

    private String resolveStatusText(CapturePoint point, CaptureSession session) {
        KothManager kothManager = plugin.getKothManager();
        if (point != null
            && kothManager != null
            && kothManager.isEnabled()
            && kothManager.isZoneManaged(point.getId())) {
            return kothManager.isZoneActive(point.getId())
                ? Messages.get("hologram.status.koth-active")
                : Messages.get("hologram.status.koth-inactive");
        }

        if (session != null) {
            if (session.isGraceActive()) {
                return Messages.get("hologram.status.grace");
            }
            if (session.isInPreparationPhase()) {
                return Messages.get("hologram.status.preparing");
            }
            if (session.isContested()) {
                return Messages.get("hologram.status.contested");
            }
            return Messages.get("hologram.status.capturing");
        }

        String owner = resolveOwnerName(point);
        if (owner.equalsIgnoreCase(Messages.get("hologram.owner.none"))) {
            return Messages.get("hologram.status.unclaimed");
        }
        return Messages.get("hologram.status.controlled");
    }

    private KothManager.ZoneStateSnapshot resolveKothState(CapturePoint point) {
        KothManager kothManager = plugin.getKothManager();
        if (kothManager == null || point == null || point.getId() == null || point.getId().trim().isEmpty()) {
            return null;
        }
        return kothManager.getZoneState(point.getId());
    }

    private String resolveKothStatusText(KothManager.ZoneStateSnapshot state) {
        if (state != null && state.active) {
            return Messages.get("hologram.koth.status.active");
        }
        return Messages.get("hologram.koth.status.inactive");
    }

    private String resolveKothHolderText(KothManager.ZoneStateSnapshot state) {
        if (state == null || state.holderName == null || state.holderName.trim().isEmpty()) {
            return Messages.get("hologram.owner.none");
        }
        return state.holderName;
    }

    private String resolveOwnerName(CapturePoint point) {
        if (point == null) {
            return Messages.get("hologram.owner.none");
        }

        CaptureOwner owner = point.getControllingOwner();
        if (owner != null && owner.getDisplayName() != null && !owner.getDisplayName().trim().isEmpty()) {
            return owner.getDisplayName();
        }

        String legacy = point.getControllingTown();
        if (legacy == null || legacy.trim().isEmpty()) {
            return Messages.get("hologram.owner.none");
        }
        return legacy;
    }

    private String resolveOwnerType(CapturePoint point) {
        if (point == null) {
            return plugin.getDefaultOwnerType().name().toLowerCase(Locale.ROOT);
        }
        CaptureOwner owner = point.getControllingOwner();
        CaptureOwnerType type = owner != null && owner.getType() != null
            ? owner.getType()
            : plugin.getDefaultOwnerType();
        return type.name().toLowerCase(Locale.ROOT);
    }

    private String resolveCapturingOwnerName(CapturePoint point, CaptureSession session) {
        if (session != null && session.getOwner() != null && session.getOwner().getDisplayName() != null
            && !session.getOwner().getDisplayName().trim().isEmpty()) {
            return session.getOwner().getDisplayName();
        }
        if (point != null && point.getCapturingOwner() != null && point.getCapturingOwner().getDisplayName() != null
            && !point.getCapturingOwner().getDisplayName().trim().isEmpty()) {
            return point.getCapturingOwner().getDisplayName();
        }
        String legacy = point != null ? point.getCapturingTown() : null;
        if (legacy == null || legacy.trim().isEmpty()) {
            return Messages.get("hologram.owner.none");
        }
        return legacy;
    }

    private String resolveCapturingOwnerType(CapturePoint point, CaptureSession session) {
        if (session != null && session.getOwner() != null && session.getOwner().getType() != null) {
            return session.getOwner().getType().name().toLowerCase(Locale.ROOT);
        }
        if (point != null && point.getCapturingOwner() != null && point.getCapturingOwner().getType() != null) {
            return point.getCapturingOwner().getType().name().toLowerCase(Locale.ROOT);
        }
        return plugin.getDefaultOwnerType().name().toLowerCase(Locale.ROOT);
    }

    private double resolveProgress(CapturePoint point, CaptureSession session) {
        if (session == null) {
            return clamp(point != null ? point.getCaptureProgress() : 0.0d, 0.0d, 100.0d);
        }

        if (session.isInPreparationPhase()) {
            int initial = Math.max(1, session.getInitialPreparationTime());
            int elapsed = Math.max(0, initial - session.getRemainingPreparationTime());
            return clamp((elapsed * 100.0d) / initial, 0.0d, 100.0d);
        }

        int initial = Math.max(1, session.getInitialCaptureTime());
        int elapsed = Math.max(0, initial - session.getRemainingCaptureTime());
        return clamp((elapsed * 100.0d) / initial, 0.0d, 100.0d);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private List<Location> buildInstanceBases(
        Location centerBase,
        int instanceCount,
        double horizontalOffset,
        boolean fixedOrientation
    ) {
        if (centerBase == null || centerBase.getWorld() == null) {
            return Collections.emptyList();
        }

        int cappedInstances = Math.max(1, Math.min(MAX_INSTANCES_HARD_LIMIT, instanceCount));
        if (cappedInstances <= 1 || horizontalOffset <= 0.0d) {
            return List.of(centerBase.clone());
        }

        List<Location> bases = new ArrayList<>(cappedInstances);
        for (int index = 0; index < cappedInstances; index++) {
            Location base = centerBase.clone().add(
                CARDINAL_OFFSETS[index][0] * horizontalOffset,
                0.0d,
                CARDINAL_OFFSETS[index][1] * horizontalOffset
            );
            if (fixedOrientation) {
                base.setYaw(CARDINAL_YAWS[index]);
            }
            bases.add(base);
        }
        return bases;
    }

    private void removeUnusedInstanceIds(String pointId, int activeInstances) {
        if (pointId == null || pointId.trim().isEmpty() || provider == null) {
            return;
        }
        int cappedActive = Math.max(1, Math.min(MAX_INSTANCES_HARD_LIMIT, activeInstances));
        if (cappedActive <= 1) {
            for (int index = 0; index < MAX_INSTANCES_HARD_LIMIT; index++) {
                provider.remove(buildInstanceId(pointId, MAX_INSTANCES_HARD_LIMIT, index));
            }
            return;
        }

        provider.remove(pointId);
        for (int index = cappedActive; index < MAX_INSTANCES_HARD_LIMIT; index++) {
            provider.remove(buildInstanceId(pointId, MAX_INSTANCES_HARD_LIMIT, index));
        }
    }

    private String buildInstanceId(String pointId, int totalInstances, int index) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return pointId;
        }
        int safeTotal = Math.max(1, totalInstances);
        if (safeTotal <= 1) {
            return pointId;
        }
        int safeIndex = Math.max(0, index);
        return pointId + "#h" + (safeIndex + 1);
    }

    private String applyValues(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), safe(entry.getValue()));
        }
        return result;
    }

    private List<?> resolveZoneList(String pointId, String path) {
        ZoneConfigManager manager = plugin.getZoneConfigManager();
        if (manager != null) {
            return manager.getList(pointId, path, Collections.emptyList());
        }
        return Collections.emptyList();
    }

    private boolean resolveZoneBoolean(String pointId, String path, boolean fallback) {
        ZoneConfigManager manager = plugin.getZoneConfigManager();
        if (manager != null) {
            return manager.getBoolean(pointId, path, fallback);
        }
        return fallback;
    }

    private double resolveZoneDouble(String pointId, String path, double fallback) {
        ZoneConfigManager manager = plugin.getZoneConfigManager();
        if (manager != null) {
            return manager.getDouble(pointId, path, fallback);
        }
        return fallback;
    }

    private int resolveZoneInt(String pointId, String path, int fallback) {
        ZoneConfigManager manager = plugin.getZoneConfigManager();
        if (manager != null) {
            return manager.getInt(pointId, path, fallback);
        }
        return fallback;
    }

    private List<String> parseStringList(List<?> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (Object entry : raw) {
            if (entry == null) {
                continue;
            }
            String value = String.valueOf(entry);
            if (value == null) {
                continue;
            }
            out.add(value);
        }
        return out;
    }

    private String formatTime(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        int minutes = safeSeconds / 60;
        int remainingSeconds = safeSeconds % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds);
    }

    private boolean isDebugEnabled() {
        FileConfiguration config = plugin.getConfig();
        return config != null && config.getBoolean("settings.debug-mode", false);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

