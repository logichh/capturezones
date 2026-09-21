package com.logichh.capturezones;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CommandRewardManager {
    private static final int MAX_COMMANDS_PER_TRIGGER = 100;

    public enum Trigger {
        CAPTURE("capture"),
        LOSS("loss"),
        HOURLY_CONTROL("hourly-control"),
        DAILY_CONTROL("daily-control"),
        KOTH_WIN("koth-win"),
        CONQUEST_WIN("conquest-win");

        private final String configKey;

        Trigger(String configKey) {
            this.configKey = configKey;
        }

        public String getConfigKey() {
            return configKey;
        }
    }

    private final CaptureZones plugin;
    private final Map<String, Long> playerCooldowns = new LinkedHashMap<>();
    private final Map<String, Long> zoneCooldowns = new LinkedHashMap<>();
    private final Set<String> pendingScopes = new LinkedHashSet<>();
    private final Set<BukkitTask> pendingBatches = new LinkedHashSet<>();
    private final File cooldownFile;
    private BukkitTask pendingSave;

    public CommandRewardManager(CaptureZones plugin) {
        this.plugin = plugin;
        this.cooldownFile = new File(plugin.getDataFolder(), "command-reward-cooldowns.yml");
        loadCooldowns();
    }

    public int executeZoneTrigger(
        Trigger trigger,
        CapturePoint point,
        CaptureOwner rewardOwner,
        CaptureOwner otherOwner,
        UUID initiatorId,
        Map<UUID, Integer> participationSeconds,
        Map<UUID, Integer> kills
    ) {
        if (trigger == null || point == null || rewardOwner == null || plugin.getZoneConfigManager() == null) {
            return 0;
        }
        Settings settings = loadZoneSettings(point.getId(), trigger);
        if (!settings.enabled) {
            return 0;
        }
        Context context = new Context(
            trigger,
            point,
            rewardOwner,
            otherOwner,
            initiatorId,
            participationSeconds,
            kills,
            ""
        );
        return execute(settings, context);
    }

    public int executeKothWin(CapturePoint point, CaptureOwner owner, Player winner, int heldSeconds) {
        if (point == null || owner == null || winner == null) {
            return 0;
        }
        Map<UUID, Integer> participation = Map.of(winner.getUniqueId(), Math.max(0, heldSeconds));
        return executeZoneTrigger(
            Trigger.KOTH_WIN,
            point,
            owner,
            null,
            winner.getUniqueId(),
            participation,
            Collections.emptyMap()
        );
    }

    public int executeConquestWin(String profile, CaptureOwner winner) {
        if (profile == null || profile.trim().isEmpty() || winner == null) {
            return 0;
        }
        String base = "conquest.profiles." + profile.trim() + ".winner-command-rewards";
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean(base + ".enabled", false)) {
            return 0;
        }

        Settings settings = new Settings();
        settings.enabled = true;
        settings.recipient = config.getString(base + ".recipient", "ONLINE_OWNER");
        settings.execution = config.getString(base + ".execution", "CONSOLE");
        settings.chance = clampChance(config.getDouble(base + ".chance", 100.0));
        settings.playerCooldownSeconds = clampNonNegative(config.getLong(base + ".cooldown.player-seconds", 0L));
        settings.zoneCooldownSeconds = clampNonNegative(config.getLong(base + ".cooldown.event-seconds", 0L));
        settings.minParticipationSeconds = 0;
        settings.minKills = 0;
        settings.minPlayerCount = Math.max(1, config.getInt(base + ".conditions.min-player-count", 1));
        settings.maxRecipients = Math.max(1, config.getInt(base + ".max-recipients", 250));
        settings.batchSize = Math.max(1, config.getInt(base + ".batch-size", 25));
        settings.commands = config.getList(base + ".commands", Collections.emptyList());

        Context context = new Context(
            Trigger.CONQUEST_WIN,
            null,
            winner,
            null,
            null,
            Collections.emptyMap(),
            Collections.emptyMap(),
            profile.trim()
        );
        return execute(settings, context);
    }

    public void clearCooldowns() {
        playerCooldowns.clear();
        zoneCooldowns.clear();
    }

    public void shutdown() {
        if (pendingSave != null && !pendingSave.isCancelled()) {
            pendingSave.cancel();
        }
        pendingSave = null;
        for (BukkitTask task : new ArrayList<>(pendingBatches)) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        pendingBatches.clear();
        pendingScopes.clear();
        saveCooldowns();
        clearCooldowns();
    }

    private Settings loadZoneSettings(String zoneId, Trigger trigger) {
        ZoneConfigManager manager = plugin.getZoneConfigManager();
        String root = "rewards.command-rewards";
        String triggerPath = root + ".triggers." + trigger.getConfigKey();

        Settings settings = new Settings();
        boolean defaultTriggerEnabled = trigger == Trigger.CAPTURE;
        settings.enabled = manager.getBoolean(zoneId, root + ".enabled", false)
            && manager.getBoolean(zoneId, triggerPath + ".enabled", defaultTriggerEnabled);
        settings.recipient = manager.getString(
            zoneId,
            triggerPath + ".recipient",
            defaultRecipient(trigger)
        );
        settings.execution = manager.getString(
            zoneId,
            triggerPath + ".execution",
            manager.getString(zoneId, root + ".execution", "CONSOLE")
        );
        settings.chance = clampChance(manager.getDouble(
            zoneId,
            triggerPath + ".chance",
            manager.getDouble(zoneId, root + ".chance", 100.0)
        ));
        settings.playerCooldownSeconds = clampNonNegative(manager.getLong(
            zoneId,
            triggerPath + ".cooldown.player-seconds",
            manager.getLong(zoneId, root + ".cooldown.player-seconds", 0L)
        ));
        settings.zoneCooldownSeconds = clampNonNegative(manager.getLong(
            zoneId,
            triggerPath + ".cooldown.zone-seconds",
            manager.getLong(zoneId, root + ".cooldown.zone-seconds", 0L)
        ));
        settings.minParticipationSeconds = Math.max(0, manager.getInt(
            zoneId,
            triggerPath + ".conditions.min-participation-seconds",
            manager.getInt(zoneId, root + ".conditions.min-participation-seconds", 0)
        ));
        settings.minKills = Math.max(0, manager.getInt(
            zoneId,
            triggerPath + ".conditions.min-kills",
            manager.getInt(zoneId, root + ".conditions.min-kills", 0)
        ));
        settings.minPlayerCount = Math.max(1, manager.getInt(
            zoneId,
            triggerPath + ".conditions.min-player-count",
            manager.getInt(zoneId, root + ".conditions.min-player-count", 1)
        ));
        settings.maxRecipients = Math.max(1, manager.getInt(
            zoneId,
            triggerPath + ".max-recipients",
            manager.getInt(zoneId, root + ".max-recipients", 250)
        ));
        settings.batchSize = Math.max(1, manager.getInt(
            zoneId,
            triggerPath + ".batch-size",
            manager.getInt(zoneId, root + ".batch-size", 25)
        ));

        List<?> triggerCommands = manager.getList(zoneId, triggerPath + ".commands", Collections.emptyList());
        if (trigger == Trigger.CAPTURE && triggerCommands.isEmpty()) {
            triggerCommands = manager.getList(zoneId, root + ".commands", Collections.emptyList());
        }
        settings.commands = triggerCommands;
        return settings;
    }

    private int execute(Settings settings, Context context) {
        if (settings == null || context == null || settings.commands == null || settings.commands.isEmpty()) {
            return 0;
        }
        if (!roll(settings.chance)) {
            return 0;
        }

        long now = System.currentTimeMillis();
        String scopeKey = scopeKey(context);
        if (pendingScopes.contains(scopeKey)
            || isCoolingDown(zoneCooldowns, scopeKey, settings.zoneCooldownSeconds, now)) {
            return 0;
        }

        boolean includeAllOwner = "ALL_OWNER".equalsIgnoreCase(settings.recipient);
        List<Target> eligible = collectEligibleTargets(context, includeAllOwner);
        eligible.removeIf(target ->
            target.participationSeconds < settings.minParticipationSeconds
                || target.kills < settings.minKills
                || isCoolingDown(
                    playerCooldowns,
                    scopeKey + ":" + target.uuid,
                    settings.playerCooldownSeconds,
                    now
                )
        );
        if (eligible.size() < settings.minPlayerCount) {
            return 0;
        }
        List<Target> recipients = selectRecipients(settings.recipient, eligible, context);
        if (recipients.isEmpty()) {
            return 0;
        }
        List<CommandEntry> commands = selectCommands(settings.commands, settings.execution);
        if (commands.isEmpty()) {
            return 0;
        }
        if (includeAllOwner
            && commands.stream().anyMatch(entry -> "PLAYER".equalsIgnoreCase(entry.execution))
            && recipients.stream().anyMatch(target -> Bukkit.getPlayer(target.uuid) == null)) {
            plugin.getLogger().warning(
                "ALL_OWNER reward in scope '" + scopeKey
                    + "' contains PLAYER execution. Those commands will be skipped for offline recipients."
            );
        }
        List<List<Target>> batches = CommandRewardBatchPlanner.plan(
            recipients,
            target -> target.uuid,
            settings.maxRecipients,
            settings.batchSize
        );
        if (batches.isEmpty()) {
            return 0;
        }
        queueBatches(scopeKey, context, batches, commands, now);
        int recipientCount = batches.stream().mapToInt(List::size).sum();
        return recipientCount * commands.size();
    }

    private void queueBatches(
        String scopeKey,
        Context context,
        List<List<Target>> recipientBatches,
        List<CommandEntry> commands,
        long queuedAt
    ) {
        pendingScopes.add(scopeKey);
        BukkitRunnable runner = new BukkitRunnable() {
            private int batchIndex;
            private int executed;
            private final Set<UUID> rewardedPlayers = new LinkedHashSet<>();

            @Override
            public void run() {
                executed += executeBatch(
                    recipientBatches.get(batchIndex),
                    commands,
                    context,
                    rewardedPlayers
                );
                if (executed > 0) {
                    recordCooldowns(scopeKey, queuedAt, rewardedPlayers);
                }
                batchIndex++;
                if (batchIndex < recipientBatches.size()) {
                    return;
                }
                finishQueuedReward(scopeKey, context, queuedAt, executed, rewardedPlayers);
                pendingScopes.remove(scopeKey);
                pendingBatches.removeIf(task -> task != null && task.getTaskId() == getTaskId());
                cancel();
            }
        };
        BukkitTask task = runner.runTaskTimer(plugin, 1L, 1L);
        pendingBatches.add(task);
    }

    private int executeBatch(
        List<Target> recipients,
        List<CommandEntry> commands,
        Context context,
        Set<UUID> rewardedPlayers
    ) {
        int executed = 0;
        for (Target recipient : recipients) {
            for (CommandEntry entry : commands) {
                if (!roll(entry.chance)) {
                    continue;
                }
                String command = applyPlaceholders(entry.command, context, recipient);
                if (command.startsWith("/")) {
                    command = command.substring(1).trim();
                }
                if (command.isEmpty()) {
                    continue;
                }
                try {
                    boolean success;
                    if ("PLAYER".equalsIgnoreCase(entry.execution)) {
                        Player online = Bukkit.getPlayer(recipient.uuid);
                        if (!CommandRewardBatchPlanner.canExecute(
                            entry.execution,
                            online != null && online.isOnline()
                        )) {
                            continue;
                        }
                        success = online.performCommand(command);
                    } else {
                        success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    }
                    if (success) {
                        executed++;
                        rewardedPlayers.add(recipient.uuid);
                    } else {
                        plugin.getLogger().warning(
                            "Command reward failed for trigger '" + context.trigger.getConfigKey() + "': " + command
                        );
                    }
                } catch (Exception exception) {
                    plugin.getLogger().warning(
                        "Command reward failed for trigger '" + context.trigger.getConfigKey() + "': "
                            + command + " (" + exception.getMessage() + ")"
                    );
                }
            }
        }
        return executed;
    }

    private void finishQueuedReward(
        String scopeKey,
        Context context,
        long queuedAt,
        int executed,
        Set<UUID> rewardedPlayers
    ) {
        if (executed > 0) {
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                plugin.getLogger().info(
                    "Executed " + executed + " command reward(s) for trigger '"
                        + context.trigger.getConfigKey() + "' in scope '" + scopeKey + "'."
                );
            }
        }
    }

    private void recordCooldowns(String scopeKey, long queuedAt, Set<UUID> rewardedPlayers) {
        zoneCooldowns.put(scopeKey, queuedAt);
        for (UUID playerId : rewardedPlayers) {
            playerCooldowns.put(scopeKey + ":" + playerId, queuedAt);
        }
        scheduleSave();
    }

    private List<Target> collectEligibleTargets(Context context, boolean includeAllOwner) {
        Map<UUID, Target> targets = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : context.participationSeconds.entrySet()) {
            UUID playerId = entry.getKey();
            if (playerId == null) {
                continue;
            }
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
            String name = offline.getName();
            if (name == null || name.trim().isEmpty()) {
                continue;
            }
            targets.put(playerId, new Target(
                playerId,
                name,
                Math.max(0, entry.getValue()),
                Math.max(0, context.kills.getOrDefault(playerId, 0))
            ));
        }

        if (context.initiatorId != null && !targets.containsKey(context.initiatorId)) {
            OfflinePlayer initiator = Bukkit.getOfflinePlayer(context.initiatorId);
            if (initiator.getName() != null && !initiator.getName().trim().isEmpty()) {
                targets.put(context.initiatorId, new Target(
                    context.initiatorId,
                    initiator.getName(),
                    Math.max(0, context.participationSeconds.getOrDefault(context.initiatorId, 0)),
                    Math.max(0, context.kills.getOrDefault(context.initiatorId, 0))
                ));
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online != null && online.isOnline() && plugin.doesPlayerMatchOwner(online, context.rewardOwner)) {
                targets.putIfAbsent(online.getUniqueId(), new Target(
                    online.getUniqueId(),
                    online.getName(),
                    Math.max(0, context.participationSeconds.getOrDefault(online.getUniqueId(), 0)),
                    Math.max(0, context.kills.getOrDefault(online.getUniqueId(), 0))
                ));
            }
        }
        if (includeAllOwner && plugin.getOwnerPlatform() != null) {
            for (OwnerMember member : plugin.getOwnerPlatform().getOwnerMembers(context.rewardOwner)) {
                if (member == null) {
                    continue;
                }
                UUID playerId = member.getUniqueId();
                targets.putIfAbsent(playerId, new Target(
                    playerId,
                    member.getName(),
                    Math.max(0, context.participationSeconds.getOrDefault(playerId, 0)),
                    Math.max(0, context.kills.getOrDefault(playerId, 0))
                ));
            }
        }
        return new ArrayList<>(targets.values());
    }

    private List<Target> selectRecipients(String configuredMode, List<Target> eligible, Context context) {
        String mode = configuredMode == null ? defaultRecipient(context.trigger) : configuredMode.trim().toUpperCase(Locale.ROOT);
        if ("INITIATOR".equals(mode)) {
            if (context.initiatorId == null) {
                return Collections.emptyList();
            }
            for (Target target : eligible) {
                if (context.initiatorId.equals(target.uuid)) {
                    return new ArrayList<>(List.of(target));
                }
            }
            return Collections.emptyList();
        }
        if ("PARTICIPANTS".equals(mode)) {
            List<Target> participants = new ArrayList<>();
            for (Target target : eligible) {
                if (context.participationSeconds.containsKey(target.uuid)) {
                    participants.add(target);
                }
            }
            return participants;
        }
        if ("MVP".equals(mode)) {
            return eligible.stream()
                .filter(target -> context.participationSeconds.containsKey(target.uuid))
                .max(Comparator.comparingInt((Target target) -> target.kills)
                    .thenComparingInt(target -> target.participationSeconds))
                .map(target -> new ArrayList<>(List.of(target)))
                .orElseGet(ArrayList::new);
        }
        if ("RANDOM_PARTICIPANT".equals(mode)) {
            List<Target> participants = selectRecipients("PARTICIPANTS", eligible, context);
            if (participants.isEmpty()) {
                return participants;
            }
            return new ArrayList<>(List.of(participants.get(ThreadLocalRandom.current().nextInt(participants.size()))));
        }
        if ("ALL_OWNER".equals(mode)) {
            return new ArrayList<>(eligible);
        }

        List<Target> ownerPlayers = new ArrayList<>();
        for (Target target : eligible) {
            Player online = Bukkit.getPlayer(target.uuid);
            if (online != null && online.isOnline() && plugin.doesPlayerMatchOwner(online, context.rewardOwner)) {
                ownerPlayers.add(target);
            }
        }
        return ownerPlayers;
    }

    private List<CommandEntry> selectCommands(List<?> configured, String defaultExecution) {
        List<CommandEntry> ungrouped = new ArrayList<>();
        Map<String, List<CommandEntry>> groups = new LinkedHashMap<>();
        int inspected = 0;
        for (Object raw : configured) {
            if (++inspected > MAX_COMMANDS_PER_TRIGGER) {
                plugin.getLogger().warning("Command reward list exceeded " + MAX_COMMANDS_PER_TRIGGER + " entries; extras were ignored.");
                break;
            }
            CommandEntry entry = parseCommand(raw, defaultExecution);
            if (entry == null) {
                continue;
            }
            if (entry.group.isEmpty()) {
                ungrouped.add(entry);
            } else {
                groups.computeIfAbsent(entry.group, ignored -> new ArrayList<>()).add(entry);
            }
        }
        for (List<CommandEntry> group : groups.values()) {
            CommandEntry selected = selectWeighted(group);
            if (selected != null) {
                ungrouped.add(selected);
            }
        }
        return ungrouped;
    }

    private CommandEntry parseCommand(Object raw, String defaultExecution) {
        if (raw instanceof String) {
            String command = ((String) raw).trim();
            return command.isEmpty() ? null : new CommandEntry(command, defaultExecution, 100.0, 1.0, "");
        }
        if (!(raw instanceof Map<?, ?>)) {
            plugin.getLogger().warning("Ignored invalid command reward entry; expected text or map.");
            return null;
        }
        Map<?, ?> values = (Map<?, ?>) raw;
        String command = stringValue(values.get("command"));
        if (command.isEmpty()) {
            plugin.getLogger().warning("Ignored command reward entry without a command.");
            return null;
        }
        String execution = stringValue(values.get("execution"));
        if (execution.isEmpty()) {
            execution = defaultExecution;
        }
        double chance = numberValue(values.get("chance"), 100.0);
        double weight = Math.max(0.0, numberValue(values.get("weight"), 1.0));
        String group = stringValue(values.get("group")).toLowerCase(Locale.ROOT);
        return new CommandEntry(command, execution, clampChance(chance), weight, group);
    }

    private CommandEntry selectWeighted(List<CommandEntry> entries) {
        double total = 0.0;
        for (CommandEntry entry : entries) {
            total += entry.weight;
        }
        if (total <= 0.0) {
            return null;
        }
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (CommandEntry entry : entries) {
            roll -= entry.weight;
            if (roll <= 0.0) {
                return entry;
            }
        }
        return entries.get(entries.size() - 1);
    }

    private String applyPlaceholders(String command, Context context, Target target) {
        String ownerName = safe(context.rewardOwner == null ? "" : context.rewardOwner.getDisplayName());
        String otherName = safe(context.otherOwner == null ? "" : context.otherOwner.getDisplayName());
        String previousOwner = context.trigger == Trigger.LOSS ? ownerName : otherName;
        String newOwner = context.trigger == Trigger.LOSS ? otherName : ownerName;
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("%player%", safe(target.name));
        placeholders.put("%player_uuid%", target.uuid.toString());
        placeholders.put("%owner%", ownerName);
        placeholders.put("%owner_type%", context.rewardOwner == null || context.rewardOwner.getType() == null
            ? "" : context.rewardOwner.getType().name());
        placeholders.put("%previous_owner%", previousOwner);
        placeholders.put("%new_owner%", newOwner);
        placeholders.put("%zone%", context.point == null ? "" : safe(context.point.getName()));
        placeholders.put("%zone_id%", context.point == null ? "" : safe(context.point.getId()));
        placeholders.put("%trigger%", context.trigger.getConfigKey());
        placeholders.put("%event%", safe(context.eventName));
        placeholders.put("%participation_seconds%", String.valueOf(target.participationSeconds));
        placeholders.put("%kills%", String.valueOf(target.kills));
        String result = command == null ? "" : command.trim().replace('\r', ' ').replace('\n', ' ');
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            result = result.replace(placeholder.getKey(), placeholder.getValue());
        }
        return result;
    }

    private String scopeKey(Context context) {
        String scope = context.point != null ? context.point.getId() : context.eventName;
        return context.trigger.getConfigKey() + ":" + safe(scope).toLowerCase(Locale.ROOT);
    }

    private boolean isCoolingDown(Map<String, Long> cooldowns, String key, long seconds, long now) {
        return CommandRewardBatchPlanner.isCoolingDown(cooldowns.get(key), seconds, now);
    }

    private boolean roll(double chance) {
        return chance >= 100.0 || (chance > 0.0 && ThreadLocalRandom.current().nextDouble(100.0) < chance);
    }

    private static String defaultRecipient(Trigger trigger) {
        if (trigger == Trigger.CAPTURE || trigger == Trigger.KOTH_WIN) {
            return "INITIATOR";
        }
        return "ONLINE_OWNER";
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static double numberValue(Object value, double fallback) {
        double parsed;
        if (value instanceof Number) {
            parsed = ((Number) value).doubleValue();
        } else if (value != null) {
            try {
                parsed = Double.parseDouble(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        } else {
            return fallback;
        }
        return Double.isFinite(parsed) ? parsed : fallback;
    }

    private static double clampChance(double chance) {
        return Math.max(0.0, Math.min(100.0, chance));
    }

    private static long clampNonNegative(long value) {
        return Math.max(0L, Math.min(value, 31_536_000L));
    }

    private void loadCooldowns() {
        if (!cooldownFile.exists()) {
            return;
        }
        try {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(cooldownFile);
            loadCooldownEntries(data.getMapList("player"), playerCooldowns);
            loadCooldownEntries(data.getMapList("zone"), zoneCooldowns);
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to load command reward cooldowns: " + exception.getMessage());
        }
    }

    private void loadCooldownEntries(List<Map<?, ?>> entries, Map<String, Long> target) {
        long oldestAllowed = System.currentTimeMillis() - (31_536_000L * 1000L);
        for (Map<?, ?> entry : entries) {
            String key = stringValue(entry.get("key"));
            long timestamp = longValue(entry.get("timestamp"), 0L);
            if (!key.isEmpty() && timestamp >= oldestAllowed) {
                target.put(key, timestamp);
            }
        }
    }

    private void scheduleSave() {
        if (pendingSave != null && !pendingSave.isCancelled()) {
            return;
        }
        pendingSave = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            pendingSave = null;
            saveCooldowns();
        }, 40L);
    }

    private void saveCooldowns() {
        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Failed to create plugin data folder for command reward cooldowns.");
                return;
            }
            YamlConfiguration data = new YamlConfiguration();
            data.set("player", serializeCooldownEntries(playerCooldowns));
            data.set("zone", serializeCooldownEntries(zoneCooldowns));
            data.save(cooldownFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save command reward cooldowns: " + exception.getMessage());
        }
    }

    private List<Map<String, Object>> serializeCooldownEntries(Map<String, Long> source) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        long oldestAllowed = System.currentTimeMillis() - (31_536_000L * 1000L);
        source.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < oldestAllowed);
        for (Map.Entry<String, Long> entry : source.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", entry.getKey());
            row.put("timestamp", entry.getValue());
            serialized.add(row);
        }
        return serialized;
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static final class Settings {
        private boolean enabled;
        private String recipient;
        private String execution;
        private double chance;
        private long playerCooldownSeconds;
        private long zoneCooldownSeconds;
        private int minParticipationSeconds;
        private int minKills;
        private int minPlayerCount;
        private int maxRecipients;
        private int batchSize;
        private List<?> commands;
    }

    private static final class Context {
        private final Trigger trigger;
        private final CapturePoint point;
        private final CaptureOwner rewardOwner;
        private final CaptureOwner otherOwner;
        private final UUID initiatorId;
        private final Map<UUID, Integer> participationSeconds;
        private final Map<UUID, Integer> kills;
        private final String eventName;

        private Context(
            Trigger trigger,
            CapturePoint point,
            CaptureOwner rewardOwner,
            CaptureOwner otherOwner,
            UUID initiatorId,
            Map<UUID, Integer> participationSeconds,
            Map<UUID, Integer> kills,
            String eventName
        ) {
            this.trigger = trigger;
            this.point = point;
            this.rewardOwner = rewardOwner;
            this.otherOwner = otherOwner;
            this.initiatorId = initiatorId;
            this.participationSeconds = participationSeconds == null
                ? Collections.emptyMap() : new LinkedHashMap<>(participationSeconds);
            this.kills = kills == null ? Collections.emptyMap() : new LinkedHashMap<>(kills);
            this.eventName = eventName == null ? "" : eventName;
        }
    }

    private static final class Target {
        private final UUID uuid;
        private final String name;
        private final int participationSeconds;
        private final int kills;

        private Target(UUID uuid, String name, int participationSeconds, int kills) {
            this.uuid = uuid;
            this.name = name;
            this.participationSeconds = participationSeconds;
            this.kills = kills;
        }
    }

    private static final class CommandEntry {
        private final String command;
        private final String execution;
        private final double chance;
        private final double weight;
        private final String group;

        private CommandEntry(String command, String execution, double chance, double weight, String group) {
            this.command = command;
            this.execution = execution == null ? "CONSOLE" : execution;
            this.chance = chance;
            this.weight = weight;
            this.group = group == null ? "" : group;
        }
    }
}
