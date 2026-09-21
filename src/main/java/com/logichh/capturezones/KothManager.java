package com.logichh.capturezones;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

public final class KothManager {

    private static final DateTimeFormatter SCHEDULE_TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");
    private static final int DEFAULT_CAPTURE_SECONDS = 180;
    private static final double DEFAULT_HOLD_RADIUS_BLOCKS = 5.0;
    private static final Material HOLD_OUTLINE_MATERIAL = Material.RED_CONCRETE;
    private static final int VISUAL_PROGRESS_UPDATE_STEP_SECONDS = 5;

    private enum ActivationSelectionMode {
        ALL,
        LIST,
        RANDOM;

        private static ActivationSelectionMode fromConfig(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return ALL;
            }
            try {
                return ActivationSelectionMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ALL;
            }
        }
    }

    private enum ContestedBehavior {
        PAUSE,
        RESET;

        private static ContestedBehavior fromConfig(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return PAUSE;
            }
            try {
                return ContestedBehavior.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return PAUSE;
            }
        }
    }

    private enum InventoryFullBehavior {
        DROP,
        CANCEL;

        private static InventoryFullBehavior fromConfig(String raw) {
            if (raw == null || raw.trim().isEmpty()) {
                return DROP;
            }
            try {
                return InventoryFullBehavior.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return DROP;
            }
        }
    }

    public static final class ZoneStateSnapshot {
        public final boolean active;
        public final String holderName;
        public final int progressSeconds;
        public final int captureSeconds;

        private ZoneStateSnapshot(boolean active, String holderName, int progressSeconds, int captureSeconds) {
            this.active = active;
            this.holderName = holderName;
            this.progressSeconds = progressSeconds;
            this.captureSeconds = captureSeconds;
        }

        public int remainingSeconds() {
            return Math.max(0, captureSeconds - progressSeconds);
        }

        public int progressPercent() {
            if (captureSeconds <= 0) {
                return 0;
            }
            double ratio = (double) progressSeconds / (double) captureSeconds;
            return (int) Math.max(0, Math.min(100, Math.round(ratio * 100.0)));
        }
    }

    private static final class ActiveKothZone {
        private final String zoneId;
        private int captureSeconds;
        private UUID holderUuid;
        private String holderName;
        private int progressSeconds;

        private ActiveKothZone(String zoneId, int captureSeconds) {
            this.zoneId = zoneId;
            this.captureSeconds = Math.max(1, captureSeconds);
            this.holderUuid = null;
            this.holderName = "";
            this.progressSeconds = 0;
        }

        private ZoneStateSnapshot snapshot() {
            return new ZoneStateSnapshot(true, holderName, progressSeconds, captureSeconds);
        }
    }

    private static final class RewardResult {
        private double moneyAmount;
        private String itemsSummary;
        private String permissionsSummary;

        private RewardResult() {
            this.moneyAmount = 0.0;
            this.itemsSummary = "";
            this.permissionsSummary = "";
        }
    }

    private static final class OutlineSurfaceSnapshot {
        private final UUID worldId;
        private final int x;
        private final int y;
        private final int z;
        private final BlockData originalData;
        private int references;

        private OutlineSurfaceSnapshot(UUID worldId, int x, int y, int z, BlockData originalData) {
            this.worldId = worldId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.originalData = originalData;
            this.references = 0;
        }
    }

    private final CaptureZones plugin;
    private final Random random;
    private final Map<String, ActiveKothZone> activeZones;
    private final Map<String, Set<String>> outlineKeysByZone;
    private final Map<String, OutlineSurfaceSnapshot> outlineSnapshotsByKey;
    private final Set<String> scheduleTriggerKeys;
    private final Set<String> warnedInvalidScheduleEntries;
    private BukkitTask scheduleTask;
    private BukkitTask tickTask;

    public KothManager(CaptureZones plugin) {
        this.plugin = plugin;
        this.random = new Random();
        this.activeZones = new LinkedHashMap<>();
        this.outlineKeysByZone = new LinkedHashMap<>();
        this.outlineSnapshotsByKey = new LinkedHashMap<>();
        this.scheduleTriggerKeys = new LinkedHashSet<>();
        this.warnedInvalidScheduleEntries = new HashSet<>();
    }

    public void initialize() {
        cancelTasks();
        if (!isEnabled()) {
            clearActiveZones();
            return;
        }
        startScheduleTask();
        startTickTask();
    }

    public void reload() {
        initialize();
    }

    public void shutdown() {
        cancelTasks();
        clearActiveZones();
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("koth.enabled", false);
    }

    public boolean isZoneActive(String zoneId) {
        if (zoneId == null || zoneId.trim().isEmpty()) {
            return false;
        }
        return activeZones.containsKey(zoneId.trim());
    }

    public boolean isZoneManaged(String zoneId) {
        if (!isEnabled() || zoneId == null || zoneId.trim().isEmpty()) {
            return false;
        }

        String normalized = zoneId.trim();
        ActivationSelectionMode mode = resolveSelectionMode();
        if (mode == ActivationSelectionMode.ALL) {
            return plugin.getCapturePoint(normalized) != null;
        }

        List<String> configured = resolveConfiguredZoneIds();
        if (configured.isEmpty()) {
            return plugin.getCapturePoint(normalized) != null;
        }
        return configured.stream().anyMatch(id -> id.equalsIgnoreCase(normalized));
    }

    public boolean shouldDisplayPoint(CapturePoint point) {
        return true;
    }

    public List<String> getHologramLines(String zoneId) {
        if (!isEnabled() || zoneId == null || zoneId.trim().isEmpty()) {
            return Collections.emptyList();
        }
        if (!plugin.getConfig().getBoolean("koth.hologram.use-koth-style", false)) {
            return Collections.emptyList();
        }
        if (!isZoneManaged(zoneId)) {
            return Collections.emptyList();
        }

        String path = isZoneActive(zoneId)
            ? "koth.hologram.active-lines"
            : "koth.hologram.inactive-lines";
        List<String> raw = plugin.getConfig().getStringList(path);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(raw);
    }

    public ZoneStateSnapshot getZoneState(String zoneId) {
        if (zoneId == null || zoneId.trim().isEmpty()) {
            return new ZoneStateSnapshot(false, "", 0, resolveCaptureSeconds());
        }
        ActiveKothZone state = activeZones.get(zoneId.trim());
        if (state == null) {
            return new ZoneStateSnapshot(false, "", 0, resolveCaptureSeconds());
        }
        return state.snapshot();
    }

    public Set<String> getActiveZoneIds() {
        return new LinkedHashSet<>(activeZones.keySet());
    }

    public int getActiveZoneCount() {
        return activeZones.size();
    }

    public boolean startConfiguredEvent() {
        if (!isEnabled()) {
            return false;
        }
        List<String> selectedZoneIds = resolveNextScheduledZoneIds();
        if (selectedZoneIds.isEmpty()) {
            return false;
        }
        return startEventForZones(selectedZoneIds, true);
    }

    public boolean startEventForZones(List<String> zoneIds, boolean announce) {
        if (!isEnabled() || zoneIds == null || zoneIds.isEmpty()) {
            return false;
        }
        if (!isScheduleOverlapAllowed() && !activeZones.isEmpty()) {
            return false;
        }

        int captureSeconds = resolveCaptureSeconds();
        int activated = 0;
        Set<String> activatedZoneIds = new LinkedHashSet<>();
        for (String rawId : zoneIds) {
            if (rawId == null || rawId.trim().isEmpty()) {
                continue;
            }
            String zoneId = rawId.trim();
            if (activeZones.containsKey(zoneId)) {
                continue;
            }
            CapturePoint point = plugin.getCapturePoint(zoneId);
            if (point == null) {
                continue;
            }

            // Keep normal capture sessions separate from KOTH events.
            if (plugin.isPointActive(zoneId)) {
                plugin.stopCapture(zoneId, "KOTH activated for this zone");
            }

            activeZones.put(zoneId, new ActiveKothZone(zoneId, captureSeconds));
            applyHoldOutline(zoneId, point, resolveHoldRadiusBlocks());
            activated++;
            activatedZoneIds.add(zoneId);

            if (announce) {
                plugin.broadcastChatMessage(Messages.get("messages.koth.zone-activated", Map.of(
                    "zone", point.getName(),
                    "time", formatTime(captureSeconds),
                    "radius", formatRadius(resolveHoldRadiusBlocks())
                )));
                if (plugin.getDiscordWebhook() != null) {
                    plugin.getDiscordWebhook().sendKothActivated(
                        zoneId,
                        point.getName(),
                        formatTime(captureSeconds),
                        formatRadius(resolveHoldRadiusBlocks())
                    );
                }
            }
            if (point.getLocation() != null) {
                plugin.playCaptureSoundAtLocation("capture-started", point.getLocation());
            }
        }

        if (activated > 0) {
            for (String zoneId : activatedZoneIds) {
                plugin.refreshPointVisuals(zoneId);
            }
            return true;
        }
        return false;
    }

    public boolean stopZone(String zoneId, String reason, boolean announce) {
        if (zoneId == null || zoneId.trim().isEmpty()) {
            return false;
        }
        ActiveKothZone removed = activeZones.remove(zoneId.trim());
        if (removed == null) {
            return false;
        }
        removeHoldOutline(zoneId.trim());
        if (announce) {
            CapturePoint point = plugin.getCapturePoint(zoneId);
            String zoneName = point != null ? point.getName() : zoneId;
            String normalizedReason = reason == null || reason.isEmpty() ? Messages.get("messages.koth.reason.manual") : reason;
            plugin.broadcastChatMessage(Messages.get("messages.koth.zone-stopped", Map.of(
                "zone", zoneName,
                "reason", normalizedReason
            )));
            if (plugin.getDiscordWebhook() != null) {
                plugin.getDiscordWebhook().sendKothStopped(zoneId.trim(), zoneName, normalizedReason);
            }
        }
        plugin.refreshPointVisuals(zoneId.trim());
        return true;
    }

    public int stopAllZones(String reason, boolean announce) {
        if (activeZones.isEmpty()) {
            return 0;
        }
        int count = activeZones.size();
        List<String> ids = new ArrayList<>(activeZones.keySet());
        for (String zoneId : ids) {
            stopZone(zoneId, reason, announce);
        }
        return count;
    }

    private void startScheduleTask() {
        if (!plugin.getConfig().getBoolean("koth.schedule.enabled", true)) {
            return;
        }
        int intervalSeconds = Math.max(5, plugin.getConfig().getInt("koth.schedule.check-interval-seconds", 30));
        this.scheduleTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::checkAndTriggerScheduledEvents,
            20L,
            intervalSeconds * 20L
        );
    }

    private void startTickTask() {
        this.tickTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::tickActiveZones,
            20L,
            20L
        );
    }

    private void cancelTasks() {
        if (scheduleTask != null && !scheduleTask.isCancelled()) {
            scheduleTask.cancel();
        }
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
        }
        scheduleTask = null;
        tickTask = null;
    }

    private void clearActiveZones() {
        if (activeZones.isEmpty() && outlineKeysByZone.isEmpty()) {
            return;
        }
        List<String> removedZoneIds = new ArrayList<>(activeZones.keySet());
        for (String zoneId : removedZoneIds) {
            removeHoldOutline(zoneId);
        }
        clearDanglingOutlines();
        activeZones.clear();
        for (String zoneId : removedZoneIds) {
            plugin.refreshPointVisuals(zoneId);
        }
    }

    private void checkAndTriggerScheduledEvents() {
        if (!isEnabled()) {
            return;
        }
        if (!isScheduleDayAllowed()) {
            return;
        }
        if (!isScheduleOverlapAllowed() && !activeZones.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<LocalTime> scheduleTimes = resolveScheduleTimes();
        if (scheduleTimes.isEmpty()) {
            return;
        }

        for (LocalTime configuredTime : scheduleTimes) {
            if (configuredTime.getHour() != now.getHour() || configuredTime.getMinute() != now.getMinute()) {
                continue;
            }
            String triggerKey = now.toLocalDate() + "|" + configuredTime;
            if (!scheduleTriggerKeys.add(triggerKey)) {
                continue;
            }
            startConfiguredEvent();
        }

        // Keep this set bounded and naturally self-cleaning.
        if (scheduleTriggerKeys.size() > 512) {
            Iterator<String> iterator = scheduleTriggerKeys.iterator();
            while (scheduleTriggerKeys.size() > 384 && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    private void tickActiveZones() {
        if (!isEnabled() || activeZones.isEmpty()) {
            return;
        }

        Set<String> changedZoneIds = new LinkedHashSet<>();
        double holdRadius = resolveHoldRadiusBlocks();
        boolean resetWhenEmpty = plugin.getConfig().getBoolean("koth.gameplay.reset-progress-when-empty", false);
        ContestedBehavior contestedBehavior = ContestedBehavior.fromConfig(
            plugin.getConfig().getString("koth.gameplay.contested-behavior", "PAUSE")
        );

        Iterator<Map.Entry<String, ActiveKothZone>> iterator = activeZones.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, ActiveKothZone> entry = iterator.next();
            String zoneId = entry.getKey();
            ActiveKothZone state = entry.getValue();
            if (state == null) {
                removeHoldOutline(zoneId);
                iterator.remove();
                changedZoneIds.add(zoneId);
                continue;
            }

            CapturePoint point = plugin.getCapturePoint(zoneId);
            if (point == null || point.getLocation() == null || point.getLocation().getWorld() == null) {
                removeHoldOutline(zoneId);
                iterator.remove();
                changedZoneIds.add(zoneId);
                continue;
            }

            state.captureSeconds = Math.max(1, resolveCaptureSeconds());
            List<Player> contenders = collectContenders(point, holdRadius);
            if (contenders.isEmpty()) {
                if (resetWhenEmpty && state.progressSeconds > 0) {
                    state.progressSeconds = 0;
                    state.holderUuid = null;
                    state.holderName = "";
                    changedZoneIds.add(zoneId);
                }
                continue;
            }

            if (contenders.size() > 1) {
                if (contestedBehavior == ContestedBehavior.RESET && state.progressSeconds > 0) {
                    state.progressSeconds = 0;
                    state.holderUuid = null;
                    state.holderName = "";
                    changedZoneIds.add(zoneId);
                }
                for (Player contender : contenders) {
                    sendActionBar(contender, Messages.get("messages.koth.actionbar.contested", Map.of(
                        "zone", point.getName()
                    )));
                }
                continue;
            }

            Player holder = contenders.get(0);
            UUID holderId = holder.getUniqueId();
            if (state.holderUuid == null || !state.holderUuid.equals(holderId)) {
                state.holderUuid = holderId;
                state.holderName = holder.getName();
                state.progressSeconds = 0;
                plugin.broadcastChatMessage(Messages.get("messages.koth.new-holder", Map.of(
                    "zone", point.getName(),
                    "player", holder.getName()
                )));
                changedZoneIds.add(zoneId);
            }

            state.progressSeconds = Math.min(state.captureSeconds, state.progressSeconds + 1);
            int remainingSeconds = Math.max(0, state.captureSeconds - state.progressSeconds);
            int progressPercent = state.captureSeconds <= 0
                ? 0
                : (int) Math.max(0, Math.min(100, Math.round(((double) state.progressSeconds / (double) state.captureSeconds) * 100.0)));

            sendActionBar(holder, Messages.get("messages.koth.actionbar.capturing", Map.of(
                "zone", point.getName(),
                "time", formatTime(remainingSeconds),
                "progress", String.valueOf(progressPercent)
            )));
            if (state.progressSeconds >= state.captureSeconds
                || remainingSeconds <= 10
                || (state.progressSeconds % VISUAL_PROGRESS_UPDATE_STEP_SECONDS) == 0) {
                changedZoneIds.add(zoneId);
            }

            if (state.progressSeconds < state.captureSeconds) {
                continue;
            }

            RewardResult rewards = rewardWinner(point, holder);
            CommandRewardManager commandRewards = plugin.getCommandRewardManager();
            if (commandRewards != null) {
                CaptureOwner winnerOwner = plugin.resolveRewardOwner(holder);
                if (winnerOwner != null) {
                    commandRewards.executeKothWin(point, winnerOwner, holder, state.captureSeconds);
                }
            }
            String rewardSummary = buildRewardSummary(rewards);
            plugin.broadcastChatMessage(Messages.get("messages.koth.captured", Map.of(
                "zone", point.getName(),
                "player", holder.getName(),
                "rewards", rewardSummary
            )));
            if (plugin.getDiscordWebhook() != null) {
                plugin.getDiscordWebhook().sendKothCaptured(
                    zoneId,
                    point.getName(),
                    holder.getName(),
                    formatTime(state.captureSeconds),
                    rewardSummary
                );
            }
            plugin.playCaptureSoundAtLocation("capture-complete", point.getLocation());
            removeHoldOutline(zoneId);
            iterator.remove();
            changedZoneIds.add(zoneId);
        }

        for (String zoneId : changedZoneIds) {
            plugin.refreshPointVisuals(zoneId);
        }
    }

    private List<Player> collectContenders(CapturePoint point, double holdRadiusBlocks) {
        if (point == null || point.getLocation() == null || point.getLocation().getWorld() == null) {
            return Collections.emptyList();
        }
        World world = point.getLocation().getWorld();
        if (world == null) {
            return Collections.emptyList();
        }

        Location center = point.getLocation();
        List<Player> contenders = new ArrayList<>();
        for (Player online : world.getPlayers()) {
            if (online == null || !online.isOnline() || online.isDead()) {
                continue;
            }
            if (!plugin.isWithinZone(point, online.getLocation())) {
                continue;
            }
            if (!isWithinSquareRadius(center, online.getLocation(), holdRadiusBlocks)) {
                continue;
            }
            contenders.add(online);
        }
        return contenders;
    }

    private boolean isWithinSquareRadius(Location center, Location target, double holdRadiusBlocks) {
        if (center == null || target == null || center.getWorld() == null || target.getWorld() == null) {
            return false;
        }
        if (!center.getWorld().getUID().equals(target.getWorld().getUID())) {
            return false;
        }
        double dx = Math.abs(center.getX() - target.getX());
        double dz = Math.abs(center.getZ() - target.getZ());
        return dx <= holdRadiusBlocks && dz <= holdRadiusBlocks;
    }

    private RewardResult rewardWinner(CapturePoint point, Player winner) {
        RewardResult result = new RewardResult();
        if (point == null || winner == null) {
            return result;
        }

        String pointId = point.getId();

        if (plugin.getConfig().getBoolean("koth.rewards.money.enabled", true)) {
            double configuredAmount = plugin.getConfig().getDouble("koth.rewards.money.amount", -1.0);
            double amount = configuredAmount > 0.0 ? configuredAmount : plugin.getBaseReward(point);
            if (amount > 0.0) {
                boolean paid = plugin.depositPlayerReward(
                    winner,
                    amount,
                    "KOTH capture reward for " + point.getName()
                );
                if (paid) {
                    result.moneyAmount = amount;
                    plugin.sendNotification(winner, Messages.get("messages.koth.reward.money", Map.of(
                        "zone", point.getName(),
                        "amount", String.format(Locale.ROOT, "%.2f", amount)
                    )));
                }
            }
        }

        boolean giveItems = plugin.getConfig().getBoolean("koth.rewards.items.enabled", true)
            && plugin.getConfig().getBoolean("koth.rewards.items.use-zone-item-rewards", true);
        if (giveItems) {
            List<ItemStack> rewardStacks = plugin.getConfiguredItemRewardStacks(pointId);
            if (!rewardStacks.isEmpty()) {
                InventoryFullBehavior inventoryBehavior = InventoryFullBehavior.fromConfig(
                    plugin.getConfig().getString("koth.rewards.items.inventory-full-behavior", "DROP")
                );
                if (inventoryBehavior == InventoryFullBehavior.CANCEL
                    && !canFitAllItems(winner.getInventory(), rewardStacks)) {
                    plugin.sendNotification(winner, Messages.get("messages.koth.reward.items-cancelled", Map.of(
                        "zone", point.getName()
                    )));
                } else {
                    List<ItemStack> dropped = giveItemsToPlayer(winner, rewardStacks, inventoryBehavior == InventoryFullBehavior.DROP);
                    result.itemsSummary = summarizeItemStacks(rewardStacks);
                    plugin.sendNotification(winner, Messages.get("messages.koth.reward.items", Map.of(
                        "zone", point.getName(),
                        "items", result.itemsSummary
                    )));
                    if (!dropped.isEmpty()) {
                        plugin.sendNotification(winner, Messages.get("messages.koth.reward.items-dropped", Map.of(
                            "zone", point.getName()
                        )));
                    }
                }
            }
        }

        boolean grantPermissions = plugin.getConfig().getBoolean("koth.rewards.permissions.enabled", true)
            && plugin.getConfig().getBoolean("koth.rewards.permissions.use-zone-permission-rewards", true);
        if (grantPermissions) {
            List<String> granted = plugin.grantConfiguredPermissionRewardsToPlayer(
                pointId,
                winner,
                "koth:" + pointId
            );
            if (!granted.isEmpty()) {
                result.permissionsSummary = String.join(", ", granted);
                plugin.sendNotification(winner, Messages.get("messages.koth.reward.permissions", Map.of(
                    "zone", point.getName(),
                    "permissions", result.permissionsSummary
                )));
            }
        }

        return result;
    }

    private List<ItemStack> giveItemsToPlayer(Player player, List<ItemStack> rewardStacks, boolean dropOverflow) {
        if (player == null || rewardStacks == null || rewardStacks.isEmpty()) {
            return Collections.emptyList();
        }

        List<ItemStack> dropped = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        for (ItemStack rewardStack : rewardStacks) {
            if (rewardStack == null || rewardStack.getAmount() <= 0) {
                continue;
            }
            Map<Integer, ItemStack> leftovers = inventory.addItem(rewardStack.clone());
            if (leftovers.isEmpty()) {
                continue;
            }
            for (ItemStack leftover : leftovers.values()) {
                if (leftover == null || leftover.getAmount() <= 0) {
                    continue;
                }
                if (dropOverflow) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover.clone());
                    dropped.add(leftover.clone());
                }
            }
        }
        return dropped;
    }

    private boolean canFitAllItems(PlayerInventory inventory, List<ItemStack> rewardStacks) {
        if (inventory == null || rewardStacks == null || rewardStacks.isEmpty()) {
            return true;
        }

        ItemStack[] simulated = inventory.getStorageContents();
        if (simulated == null) {
            simulated = new ItemStack[0];
        }
        simulated = cloneItemArray(simulated);

        for (ItemStack reward : rewardStacks) {
            if (reward == null || reward.getAmount() <= 0) {
                continue;
            }
            int remaining = reward.getAmount();

            for (int i = 0; i < simulated.length && remaining > 0; i++) {
                ItemStack current = simulated[i];
                if (current == null || !current.isSimilar(reward)) {
                    continue;
                }
                int maxStack = Math.max(1, current.getMaxStackSize());
                int free = maxStack - current.getAmount();
                if (free <= 0) {
                    continue;
                }
                int used = Math.min(free, remaining);
                current.setAmount(current.getAmount() + used);
                remaining -= used;
            }

            for (int i = 0; i < simulated.length && remaining > 0; i++) {
                if (simulated[i] != null) {
                    continue;
                }
                int maxStack = Math.max(1, reward.getMaxStackSize());
                int placed = Math.min(maxStack, remaining);
                ItemStack cloned = reward.clone();
                cloned.setAmount(placed);
                simulated[i] = cloned;
                remaining -= placed;
            }

            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private ItemStack[] cloneItemArray(ItemStack[] source) {
        ItemStack[] clone = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            clone[i] = source[i] == null ? null : source[i].clone();
        }
        return clone;
    }

    private String summarizeItemStacks(List<ItemStack> stacks) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType() == null || stack.getAmount() <= 0) {
                continue;
            }
            totals.merge(stack.getType().name(), stack.getAmount(), Integer::sum);
        }
        if (totals.isEmpty()) {
            return Messages.get("messages.koth.reward.none");
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            parts.add(entry.getKey() + " x" + entry.getValue());
        }
        return String.join(", ", parts);
    }

    private String buildRewardSummary(RewardResult rewards) {
        if (rewards == null) {
            return Messages.get("messages.koth.reward.none");
        }
        List<String> parts = new ArrayList<>();
        if (rewards.moneyAmount > 0.0) {
            parts.add("$" + String.format(Locale.ROOT, "%.2f", rewards.moneyAmount));
        }
        if (rewards.itemsSummary != null && !rewards.itemsSummary.isEmpty()) {
            parts.add(rewards.itemsSummary);
        }
        if (rewards.permissionsSummary != null && !rewards.permissionsSummary.isEmpty()) {
            parts.add(rewards.permissionsSummary);
        }
        if (parts.isEmpty()) {
            return Messages.get("messages.koth.reward.none");
        }
        return String.join(" | ", parts);
    }

    private void applyHoldOutline(String zoneId, CapturePoint point, double holdRadiusBlocks) {
        if (zoneId == null || zoneId.trim().isEmpty() || point == null || point.getLocation() == null) {
            return;
        }
        Location center = point.getLocation();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        removeHoldOutline(zoneId);

        int centerX = center.getBlockX();
        int centerZ = center.getBlockZ();
        int holdRadius = Math.max(1, (int) Math.ceil(Math.max(0.5d, holdRadiusBlocks)));
        int outlineRadius = holdRadius + 1;
        Set<String> zoneKeys = new LinkedHashSet<>();

        for (int x = centerX - outlineRadius; x <= centerX + outlineRadius; x++) {
            for (int z = centerZ - outlineRadius; z <= centerZ + outlineRadius; z++) {
                boolean edge = x == centerX - outlineRadius
                    || x == centerX + outlineRadius
                    || z == centerZ - outlineRadius
                    || z == centerZ + outlineRadius;
                if (!edge) {
                    continue;
                }

                Block surface = world.getHighestBlockAt(x, z);
                if (surface == null || surface.getY() < world.getMinHeight() || surface.getY() >= world.getMaxHeight()) {
                    continue;
                }

                String key = toSurfaceKey(world.getUID(), x, surface.getY(), z);
                OutlineSurfaceSnapshot snapshot = outlineSnapshotsByKey.get(key);
                if (snapshot == null) {
                    snapshot = new OutlineSurfaceSnapshot(
                        world.getUID(),
                        x,
                        surface.getY(),
                        z,
                        surface.getBlockData().clone()
                    );
                    outlineSnapshotsByKey.put(key, snapshot);
                }
                snapshot.references++;
                zoneKeys.add(key);
                surface.setType(HOLD_OUTLINE_MATERIAL, false);
            }
        }

        if (!zoneKeys.isEmpty()) {
            outlineKeysByZone.put(zoneId.trim(), zoneKeys);
        }
    }

    private void removeHoldOutline(String zoneId) {
        if (zoneId == null || zoneId.trim().isEmpty()) {
            return;
        }
        Set<String> keys = outlineKeysByZone.remove(zoneId.trim());
        if (keys == null || keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            if (key == null || key.trim().isEmpty()) {
                continue;
            }
            OutlineSurfaceSnapshot snapshot = outlineSnapshotsByKey.get(key);
            if (snapshot == null) {
                continue;
            }
            snapshot.references = Math.max(0, snapshot.references - 1);
            if (snapshot.references > 0) {
                continue;
            }

            World world = Bukkit.getWorld(snapshot.worldId);
            if (world != null) {
                Block block = world.getBlockAt(snapshot.x, snapshot.y, snapshot.z);
                block.setBlockData(snapshot.originalData, false);
            }
            outlineSnapshotsByKey.remove(key);
        }
    }

    private void clearDanglingOutlines() {
        if (!outlineKeysByZone.isEmpty()) {
            for (String zoneId : new ArrayList<>(outlineKeysByZone.keySet())) {
                removeHoldOutline(zoneId);
            }
        }
        outlineSnapshotsByKey.clear();
    }

    private String toSurfaceKey(UUID worldId, int x, int y, int z) {
        return worldId + "|" + x + "|" + y + "|" + z;
    }

    private void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline() || player.isDead() || plugin.isNotificationsDisabled(player)) {
            return;
        }
        plugin.sendActionBarMessage(player, message);
    }

    private boolean isScheduleDayAllowed() {
        List<String> rawDays = plugin.getConfig().getStringList("koth.schedule.days");
        if (rawDays == null || rawDays.isEmpty()) {
            return true;
        }

        DayOfWeek now = LocalDate.now().getDayOfWeek();
        for (String raw : rawDays) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            if ("ALL".equals(normalized) || "*".equals(normalized)) {
                return true;
            }
            try {
                if (DayOfWeek.valueOf(normalized) == now) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                warnInvalidScheduleEntry("day:" + normalized, "Invalid KOTH schedule day: " + raw);
            }
        }
        return false;
    }

    private List<LocalTime> resolveScheduleTimes() {
        List<String> rawTimes = plugin.getConfig().getStringList("koth.schedule.times");
        if ((rawTimes == null || rawTimes.isEmpty()) && plugin.getConfig().isString("koth.schedule.time")) {
            rawTimes = List.of(plugin.getConfig().getString("koth.schedule.time", "20:00"));
        }
        if (rawTimes == null || rawTimes.isEmpty()) {
            return Collections.emptyList();
        }

        List<LocalTime> parsed = new ArrayList<>();
        for (String rawTime : rawTimes) {
            if (rawTime == null || rawTime.trim().isEmpty()) {
                continue;
            }
            try {
                LocalTime time = LocalTime.parse(rawTime.trim(), SCHEDULE_TIME_FORMAT)
                    .withSecond(0)
                    .withNano(0);
                parsed.add(time);
            } catch (DateTimeParseException ignored) {
                warnInvalidScheduleEntry(
                    "time:" + rawTime.trim().toLowerCase(Locale.ROOT),
                    "Invalid KOTH schedule time '" + rawTime + "'. Expected format: HH:mm"
                );
            }
        }
        return parsed;
    }

    private boolean isScheduleOverlapAllowed() {
        return plugin.getConfig().getBoolean("koth.schedule.allow-overlap", false);
    }

    private ActivationSelectionMode resolveSelectionMode() {
        return ActivationSelectionMode.fromConfig(
            plugin.getConfig().getString("koth.activation.selection-mode", "ALL")
        );
    }

    private List<String> resolveConfiguredZoneIds() {
        List<String> configured = plugin.getConfig().getStringList("koth.activation.zones");
        if (configured == null || configured.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String raw : configured) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            String normalized = raw.trim();
            if (plugin.getCapturePoint(normalized) == null) {
                continue;
            }
            if (!result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private List<String> resolveNextScheduledZoneIds() {
        ActivationSelectionMode mode = resolveSelectionMode();
        List<String> candidates;

        if (mode == ActivationSelectionMode.ALL) {
            candidates = new ArrayList<>(plugin.getCapturePoints().keySet());
        } else {
            candidates = resolveConfiguredZoneIds();
            if (candidates.isEmpty()) {
                candidates = new ArrayList<>(plugin.getCapturePoints().keySet());
            }
        }

        candidates.removeIf(id -> id == null || id.trim().isEmpty() || plugin.getCapturePoint(id) == null);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        int maxZones = plugin.getConfig().getInt("koth.activation.max-zones", 0);
        if (mode == ActivationSelectionMode.RANDOM) {
            Collections.shuffle(candidates, random);
        }

        if (maxZones <= 0 || maxZones >= candidates.size()) {
            return candidates;
        }
        return new ArrayList<>(candidates.subList(0, maxZones));
    }

    private int resolveCaptureSeconds() {
        return Math.max(1, plugin.getConfig().getInt("koth.gameplay.capture-seconds", DEFAULT_CAPTURE_SECONDS));
    }

    private double resolveHoldRadiusBlocks() {
        return Math.max(0.5, plugin.getConfig().getDouble("koth.gameplay.hold-radius-blocks", DEFAULT_HOLD_RADIUS_BLOCKS));
    }

    private void warnInvalidScheduleEntry(String key, String message) {
        if (!warnedInvalidScheduleEntries.add(key)) {
            return;
        }
        plugin.getLogger().warning(message);
    }

    private String formatTime(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int minutes = safeSeconds / 60;
        int seconds = safeSeconds % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private String formatRadius(double radius) {
        if (Math.abs(radius - Math.rint(radius)) < 0.0001) {
            return String.valueOf((int) Math.rint(radius));
        }
        return String.format(Locale.ROOT, "%.1f", radius);
    }
}

