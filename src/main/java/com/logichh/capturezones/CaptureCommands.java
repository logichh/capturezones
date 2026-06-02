package com.logichh.capturezones;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class CaptureCommands implements CommandExecutor {
    private final CaptureZones plugin;
    private final CuboidSelectionManager cuboidSelectionManager;
    private final ZoneItemRewardEditor zoneItemRewardEditor;

    public CaptureCommands(CaptureZones plugin) {
        this(plugin, null, null);
    }

    public CaptureCommands(CaptureZones plugin, CuboidSelectionManager cuboidSelectionManager) {
        this(plugin, cuboidSelectionManager, null);
    }

    public CaptureCommands(
        CaptureZones plugin,
        CuboidSelectionManager cuboidSelectionManager,
        ZoneItemRewardEditor zoneItemRewardEditor
    ) {
        this.plugin = plugin;
        this.cuboidSelectionManager = cuboidSelectionManager;
        this.zoneItemRewardEditor = zoneItemRewardEditor;
    }

    @Override

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        Player player = sender instanceof Player ? (Player)sender : null;

        switch (subCommand) {
            case "help":
                sendHelp(sender);
                return true;
            case "admin":
                return handleAdminCommand(sender, player, args);
            case "list":
                if (player != null) {
                    sendCapturePointList(player);
                } else {
                    sender.sendMessage(Messages.get("errors.player-only"));
                }
                return true;
            case "info":
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Messages.get("errors.usage-info"));
                    return true;
                }
                showPointInfo(player, args[1]);
                return true;
            case "capture":
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                startCapture(player);
                return true;
            case "tp":
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                handleTpCommand(player, args);
                return true;
            case "notifications":
            case "silence":
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                boolean currentlyDisabled = plugin.disabledNotifications.getOrDefault(player.getUniqueId(), false);
                plugin.disabledNotifications.put(player.getUniqueId(), !currentlyDisabled);

                if (!currentlyDisabled) {
                    plugin.hideBossBarForPlayer(player);
                    player.sendMessage(Messages.get("messages.notifications-disabled"));
                } else {
                    plugin.showBossBarForPlayer(player);
                    player.sendMessage(Messages.get("messages.notifications-enabled"));
                }
                return true;
            case "showzone":
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(Messages.get("errors.usage-showzone"));
                    player.sendMessage(Messages.get("messages.zone.toggle-hint"));
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    toggleAllZoneBoundaries(player);
                } else {
                    showZoneBoundaries(player, args[1]);
                }
                return true;
            case "stats":
                if (args.length != 1) {
                    sender.sendMessage(Messages.get("errors.usage-stats-admin"));
                    return true;
                }
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                handleStatsCommand(player);
                return true;
            case "shop":
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                if (args.length > 1) {
                    sender.sendMessage(Messages.get("errors.shop.invalid-subcommand"));
                    sender.sendMessage(Messages.get("messages.shop.admin-command-hint"));
                    return true;
                }
                handleShopCommand(player, args);
                return true;
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleAdminCommand(CommandSender sender, Player player, String[] args) {
        if (!PermissionNode.has(sender, "admin")) {
            sender.sendMessage(Messages.get("errors.no-permission"));
            return true;
        }

        if (args.length == 1 || "help".equalsIgnoreCase(args[1])) {
            sendAdminHelp(sender);
            return true;
        }

        String adminSubcommand = args[1].toLowerCase();
        String[] shiftedArgs = Arrays.copyOfRange(args, 1, args.length);
        switch (adminSubcommand) {
            case "create":
                if (!PermissionNode.has(sender, "admin.create")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                if (shiftedArgs.length < 5) {
                    player.sendMessage(Messages.get("errors.usage-create"));
                    return true;
                }
                handleCreateCommand(player, shiftedArgs);
                return true;
            case "createcuboid":
                if (!PermissionNode.has(sender, "admin.create")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                if (shiftedArgs.length != 4 && shiftedArgs.length != 10) {
                    player.sendMessage(Messages.get("errors.usage-createcuboid"));
                    return true;
                }
                handleCreateCuboidCommand(player, shiftedArgs);
                return true;
            case "stop":
                if (!PermissionNode.has(sender, "admin.stop")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (shiftedArgs.length < 2) {
                    sender.sendMessage(Messages.get("errors.usage-stop"));
                    return true;
                }
                handleStopCommand(sender, shiftedArgs);
                return true;
            case "deletezone":
                if (!PermissionNode.has(sender, "admin.delete")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (shiftedArgs.length < 2) {
                    sender.sendMessage(Messages.get("errors.usage-deletezone"));
                    return true;
                }
                deleteZone(sender, shiftedArgs);
                return true;
            case "forcecapture":
                if (!PermissionNode.has(sender, "admin.forcecapture")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (shiftedArgs.length < 3) {
                    sender.sendMessage(Messages.get("errors.usage-forcecapture"));
                    return true;
                }
                handleForceCapture(sender, shiftedArgs[1], shiftedArgs[2]);
                return true;
            case "reset":
                if (!PermissionNode.has(sender, "admin.reset")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (shiftedArgs.length < 2) {
                    sender.sendMessage(Messages.get("errors.usage-reset"));
                    return true;
                }
                handleReset(sender, shiftedArgs[1]);
                return true;
            case "resetall":
                if (!PermissionNode.has(sender, "admin.reset")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                handleResetAll(sender);
                return true;
            case "reload":
                if (!PermissionNode.has(sender, "admin.reload")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage(Messages.get("messages.reload-success"));
                return true;
            case "reloadlang":
                if (!PermissionNode.has(sender, "admin.reload")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                plugin.reloadLang();
                sender.sendMessage(Messages.get("messages.reloadlang-success"));
                return true;
            case "webhook":
                if (shiftedArgs.length < 2) {
                    sender.sendMessage(Messages.get("errors.usage-webhook"));
                    return true;
                }
                String webhookSub = shiftedArgs[1].toLowerCase();
                if ("test".equals(webhookSub)) {
                    if (plugin.getDiscordWebhook() == null) {
                        sender.sendMessage(Messages.get("admin.webhook.test.failure"));
                        return true;
                    }
                    boolean ok = plugin.getDiscordWebhook().testWebhook();
                    if (ok) {
                        sender.sendMessage(Messages.get("admin.webhook.test.success"));
                    } else {
                        sender.sendMessage(Messages.get("admin.webhook.test.failure"));
                    }
                    return true;
                }
                sender.sendMessage(Messages.get("errors.usage-webhook"));
                return true;
            case "repair":
                if (!PermissionNode.has(sender, "admin.migrate")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                handleSchemaRepair(sender);
                return true;
            case "migrate":
                if (!PermissionNode.has(sender, "admin.migrate")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                handleSchemaRepair(sender);
                return true;
            case "togglechat":
                if (!PermissionNode.has(sender, "admin.togglechat")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (shiftedArgs.length < 2) {
                    sender.sendMessage(Messages.get("errors.usage-togglechat"));
                    return true;
                }
                boolean enable = shiftedArgs[1].equalsIgnoreCase("on");
                plugin.toggleChatMessages(enable);
                String status = enable ? "enabled" : "disabled";
                sender.sendMessage(Messages.get("messages.chat-status", Map.of("status", status)));
                return true;
            case "zoneconfig":
                if (!PermissionNode.has(sender, "admin.zoneconfig")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                if (shiftedArgs.length < 2) {
                    sender.sendMessage(Messages.get("errors.usage-zoneconfig"));
                    return true;
                }
                handleZoneConfigCommand(sender, shiftedArgs);
                return true;
            case "shop":
                if (player == null) {
                    sender.sendMessage(Messages.get("errors.player-only"));
                    return true;
                }
                if (shiftedArgs.length < 2) {
                    sender.sendMessage(Messages.get("errors.shop.invalid-subcommand"));
                    return true;
                }
                handleShopCommand(player, shiftedArgs);
                return true;
            case "stats":
                if (!PermissionNode.has(sender, "admin.stats")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                handleAdminStatsCommand(sender, shiftedArgs);
                return true;
            case "koth":
                if (!PermissionNode.has(sender, "admin.koth")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                handleAdminKothCommand(sender, shiftedArgs);
                return true;
            case "conquest":
                if (!PermissionNode.has(sender, "admin.conquest")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return true;
                }
                handleAdminConquestCommand(sender, shiftedArgs);
                return true;
            default:
                sendAdminHelp(sender);
                return true;
        }
    }

    private void handleStopCommand(CommandSender sender, String[] args) {
        String pointId = args[1];
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "No reason provided";
        if (plugin.stopCapture(pointId, reason)) {
            sender.sendMessage(Messages.get("admin.capture-stopped"));
        } else {
            sender.sendMessage(Messages.get("errors.capture-not-found"));
        }
    }

    private void deleteZone(CommandSender sender, String[] args) {
        String pointId = args[1];
        boolean confirmed = args.length > 2 && "CONFIRM".equalsIgnoreCase(args[2]);
        if (!confirmed) {
            sender.sendMessage(Messages.get("messages.deletezone.confirm", Map.of("zone", pointId)));
            return;
        }

        if (plugin.deleteCapturePoint(pointId)) {
            sender.sendMessage(Messages.get("admin.point-deleted"));
            if (plugin.getDiscordWebhook() != null) {
                String deletedBy = (sender instanceof Player) ? ((Player) sender).getName() : "Console";
                plugin.getDiscordWebhook().sendZoneDeleted(pointId, pointId, deletedBy);
            }
        } else {
            sender.sendMessage(Messages.get("messages.errors.point_not_found"));
        }
    }

    private void handleSetType(CommandSender sender, String pointId, String type) {
        if (plugin.setPointType(pointId, type)) {
            sender.sendMessage(Messages.get("admin.type-changed"));
        } else {
            sender.sendMessage(Messages.get("messages.errors.point_not_found"));
        }
    }

    private void showPointTypes(CommandSender sender) {
        Map<String, String> types = plugin.getPointTypes();
        if (types.isEmpty()) {
            sender.sendMessage(Messages.get("errors.no-point-types"));
            return;
        }
        sender.sendMessage(Messages.get("help.point-types-header"));
        for (Map.Entry<String, String> entry : types.entrySet()) {
            sender.sendMessage(Messages.get("help.point-types-format", 
                Map.of("type", entry.getKey(), "description", entry.getValue())));
        }
    }

    private void handleForceCapture(CommandSender sender, String pointId, String townName) {
        if (plugin.forceCapture(pointId, townName)) {
            sender.sendMessage(Messages.get("admin.force-capture-success"));
        } else {
            sender.sendMessage(Messages.get("messages.errors.point_not_found"));
        }
    }

    private void handleReset(CommandSender sender, String pointId) {
        if (plugin.resetPoint(pointId)) {
            sender.sendMessage(Messages.get("admin.point-reset"));
        } else {
            sender.sendMessage(Messages.get("messages.errors.point_not_found"));
        }
    }

    private void handleResetAll(CommandSender sender) {
        int count = plugin.resetAllPoints();
        sender.sendMessage(Messages.get("messages.reset-all", Map.of("count", String.valueOf(count))));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Messages.get("help.main.header"));
        sender.sendMessage(Messages.get("help.main.help"));
        sender.sendMessage(Messages.get("help.main.list"));
        sender.sendMessage(Messages.get("help.main.info"));
        sender.sendMessage(Messages.get("help.main.capture"));
        if (plugin.isCaptureTeleportEnabled()) {
            sender.sendMessage(Messages.get("help.main.tp"));
        }
        sender.sendMessage(Messages.get("help.main.showzone"));
        sender.sendMessage(Messages.get("help.main.notifications"));
        sender.sendMessage(Messages.get("help.main.stats"));
        sender.sendMessage(Messages.get("help.main.shop"));
        
        if (PermissionNode.has(sender, "admin")) {
            sender.sendMessage(Messages.get("help.main.admin-section"));
            sender.sendMessage(Messages.get("help.main.admin"));
        }
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(Messages.get("help.admin.header"));
        sender.sendMessage(Messages.get("help.admin.help"));
        sender.sendMessage(Messages.get("help.admin.create"));
        sender.sendMessage(Messages.get("help.admin.createcuboid"));
        sender.sendMessage(Messages.get("help.admin.deletezone"));
        sender.sendMessage(Messages.get("help.admin.stop"));
        sender.sendMessage(Messages.get("help.admin.forcecapture"));
        sender.sendMessage(Messages.get("help.admin.reset"));
        sender.sendMessage(Messages.get("help.admin.resetall"));
        sender.sendMessage(Messages.get("help.admin.zoneconfig"));
        sender.sendMessage(Messages.get("help.admin.shop"));
        sender.sendMessage(Messages.get("help.admin.stats"));
        sender.sendMessage(Messages.get("help.admin.reload"));
        sender.sendMessage(Messages.get("help.admin.reloadlang"));
        sender.sendMessage(Messages.get("help.admin.togglechat"));
        sender.sendMessage(Messages.get("help.admin.repair"));
        sender.sendMessage(Messages.get("help.admin.migrate"));
        sender.sendMessage(Messages.get("help.admin.koth"));
        sender.sendMessage(Messages.get("help.admin.conquest"));
        sender.sendMessage(Messages.get("help.admin.webhook"));
    }

    private void handleAdminKothCommand(CommandSender sender, String[] args) {
        KothManager kothManager = plugin.getKothManager();
        if (kothManager == null) {
            sender.sendMessage(Messages.get("errors.koth-disabled"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Messages.get("errors.usage-koth"));
            return;
        }

        String subcommand = args[1].toLowerCase();
        switch (subcommand) {
            case "start":
                if (!kothManager.isEnabled()) {
                    sender.sendMessage(Messages.get("errors.koth-disabled"));
                    return;
                }
                handleAdminKothStart(sender, args, kothManager);
                return;
            case "stop":
                if (!kothManager.isEnabled()) {
                    sender.sendMessage(Messages.get("errors.koth-disabled"));
                    return;
                }
                handleAdminKothStop(sender, args, kothManager);
                return;
            case "status":
                handleAdminKothStatus(sender, kothManager);
                return;
            case "assign":
                handleAdminKothAssign(sender, args, true);
                return;
            case "unassign":
            case "remove":
                handleAdminKothAssign(sender, args, false);
                return;
            case "zones":
            case "list":
                handleAdminKothZones(sender);
                return;
            default:
                sender.sendMessage(Messages.get("errors.usage-koth"));
        }
    }

    private void handleAdminKothStart(CommandSender sender, String[] args, KothManager kothManager) {
        List<String> assignedZoneIds = getConfiguredAssignedKothZoneIds();
        if (assignedZoneIds.isEmpty()) {
            sender.sendMessage(Messages.get("errors.koth-no-assigned-zones"));
            return;
        }

        if (args.length <= 2) {
            if (kothManager.startEventForZones(assignedZoneIds, true)) {
                sender.sendMessage(Messages.get("admin.koth.started-configured"));
            } else {
                sender.sendMessage(Messages.get("errors.koth-start-failed"));
            }
            return;
        }

        List<String> zoneIds = new ArrayList<>();
        List<String> skippedUnassigned = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            String requested = args[i];
            if (requested == null || requested.trim().isEmpty()) {
                continue;
            }
            CapturePoint point = resolveCapturePointById(requested.trim());
            if (point == null) {
                continue;
            }
            if (!containsZoneIgnoreCase(assignedZoneIds, point.getId())) {
                if (!containsZoneIgnoreCase(skippedUnassigned, point.getId())) {
                    skippedUnassigned.add(point.getId());
                }
                continue;
            }
            if (!zoneIds.contains(point.getId())) {
                zoneIds.add(point.getId());
            }
        }

        if (zoneIds.isEmpty()) {
            if (!skippedUnassigned.isEmpty()) {
                sender.sendMessage(Messages.get("errors.koth-zone-not-assigned", Map.of(
                    "zones", String.join(", ", skippedUnassigned)
                )));
                return;
            }
            sender.sendMessage(Messages.get("errors.koth-zone-not-found"));
            return;
        }

        if (!kothManager.startEventForZones(zoneIds, true)) {
            sender.sendMessage(Messages.get("errors.koth-start-failed"));
            return;
        }

        sender.sendMessage(Messages.get("admin.koth.started-zones", Map.of(
            "count", String.valueOf(zoneIds.size()),
            "zones", String.join(", ", zoneIds)
        )));
        if (!skippedUnassigned.isEmpty()) {
            sender.sendMessage(Messages.get("admin.koth.start-skipped-unassigned", Map.of(
                "zones", String.join(", ", skippedUnassigned)
            )));
        }
    }

    private void handleAdminKothStop(CommandSender sender, String[] args, KothManager kothManager) {
        if (args.length <= 2 || "all".equalsIgnoreCase(args[2])) {
            int stopped = kothManager.stopAllZones(Messages.get("messages.koth.reason.manual"), true);
            if (stopped <= 0) {
                sender.sendMessage(Messages.get("errors.koth-stop-failed"));
                return;
            }
            sender.sendMessage(Messages.get("admin.koth.stopped-all", Map.of(
                "count", String.valueOf(stopped)
            )));
            return;
        }

        CapturePoint point = resolveCapturePointById(args[2].trim());
        String pointId = point != null ? point.getId() : args[2].trim();
        if (!kothManager.stopZone(pointId, Messages.get("messages.koth.reason.manual"), true)) {
            sender.sendMessage(Messages.get("errors.koth-stop-failed"));
            return;
        }

        String zoneName = point != null ? point.getName() : pointId;
        sender.sendMessage(Messages.get("admin.koth.stopped-zone", Map.of(
            "zone", zoneName
        )));
    }

    private void handleAdminKothStatus(CommandSender sender, KothManager kothManager) {
        sender.sendMessage(Messages.get("admin.koth.status.header"));
        sender.sendMessage(Messages.get("admin.koth.status.enabled", Map.of(
            "enabled", String.valueOf(kothManager.isEnabled())
        )));

        Set<String> activeIds = kothManager.getActiveZoneIds();
        sender.sendMessage(Messages.get("admin.koth.status.active-count", Map.of(
            "count", String.valueOf(activeIds.size())
        )));
        if (activeIds.isEmpty()) {
            sender.sendMessage(Messages.get("admin.koth.status.none"));
            return;
        }

        for (String zoneId : activeIds) {
            CapturePoint point = plugin.getCapturePoint(zoneId);
            String zoneName = point != null ? point.getName() : zoneId;
            KothManager.ZoneStateSnapshot state = kothManager.getZoneState(zoneId);
            sender.sendMessage(Messages.get("admin.koth.status.entry", Map.of(
                "id", zoneId,
                "zone", zoneName,
                "holder", (state.holderName == null || state.holderName.isEmpty()) ? "None" : state.holderName,
                "progress", String.valueOf(state.progressPercent()),
                "time", String.valueOf(state.remainingSeconds())
            )));
        }
    }

    private void handleAdminKothAssign(CommandSender sender, String[] args, boolean assign) {
        if (args.length < 3 || args[2] == null || args[2].trim().isEmpty()) {
            sender.sendMessage(Messages.get(assign ? "errors.usage-koth-assign" : "errors.usage-koth-unassign"));
            return;
        }

        String requested = args[2].trim();
        CapturePoint point = resolveCapturePointById(requested);
        if (assign && point == null) {
            sender.sendMessage(Messages.get("errors.koth-zone-not-found"));
            return;
        }
        String resolvedZoneId = point != null ? point.getId() : requested;
        String displayName = point != null ? point.getName() : resolvedZoneId;

        List<String> configured = new ArrayList<>(plugin.getConfig().getStringList("koth.activation.zones"));
        boolean changed = false;

        if (assign) {
            boolean alreadyAssigned = configured.stream().anyMatch(id -> id.equalsIgnoreCase(resolvedZoneId));
            if (alreadyAssigned) {
                sender.sendMessage(Messages.get("admin.koth.assign.already", Map.of(
                    "zone", displayName,
                    "id", resolvedZoneId
                )));
                return;
            }
            configured.add(resolvedZoneId);
            changed = true;
        } else {
            int before = configured.size();
            configured.removeIf(id -> id != null && id.equalsIgnoreCase(resolvedZoneId));
            changed = configured.size() != before;
            if (!changed) {
                sender.sendMessage(Messages.get("admin.koth.unassign.not-found", Map.of(
                    "zone", displayName,
                    "id", resolvedZoneId
                )));
                return;
            }
        }

        plugin.getConfig().set("koth.activation.zones", configured);
        if (assign) {
            String selectionMode = plugin.getConfig().getString("koth.activation.selection-mode", "ALL");
            if ("ALL".equalsIgnoreCase(selectionMode)) {
                plugin.getConfig().set("koth.activation.selection-mode", "LIST");
                sender.sendMessage(Messages.get("admin.koth.assign.mode-set-list"));
            }
        }

        if (changed) {
            plugin.saveConfig();
            KothManager kothManager = plugin.getKothManager();
            if (kothManager != null) {
                kothManager.reload();
            }
        }

        sender.sendMessage(Messages.get(assign ? "admin.koth.assigned" : "admin.koth.unassigned", Map.of(
            "zone", displayName,
            "id", resolvedZoneId,
            "count", String.valueOf(configured.size())
        )));
    }

    private void handleAdminKothZones(CommandSender sender) {
        List<String> configured = new ArrayList<>(plugin.getConfig().getStringList("koth.activation.zones"));
        String selectionMode = plugin.getConfig().getString("koth.activation.selection-mode", "ALL");
        int maxZones = plugin.getConfig().getInt("koth.activation.max-zones", 0);

        sender.sendMessage(Messages.get("admin.koth.zones.header"));
        sender.sendMessage(Messages.get("admin.koth.zones.mode", Map.of(
            "mode", selectionMode.toUpperCase()
        )));
        sender.sendMessage(Messages.get("admin.koth.zones.max", Map.of(
            "max", String.valueOf(maxZones)
        )));

        if (configured.isEmpty()) {
            sender.sendMessage(Messages.get("admin.koth.zones.none"));
            return;
        }

        for (String zoneId : configured) {
            if (zoneId == null || zoneId.trim().isEmpty()) {
                continue;
            }
            CapturePoint point = resolveCapturePointById(zoneId);
            boolean exists = point != null;
            String zoneName = exists ? point.getName() : zoneId.trim();
            sender.sendMessage(Messages.get("admin.koth.zones.entry", Map.of(
                "id", zoneId.trim(),
                "zone", zoneName,
                "exists", exists ? "yes" : "no"
            )));
        }
    }

    private void handleAdminConquestCommand(CommandSender sender, String[] args) {
        ConquestManager conquestManager = plugin.getConquestManager();
        if (conquestManager == null || !conquestManager.isEnabled()) {
            sender.sendMessage(Messages.get("errors.conquest-disabled"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(Messages.get("errors.usage-conquest"));
            return;
        }

        String subcommand = args[1].toLowerCase();
        switch (subcommand) {
            case "start": {
                String profile = args.length >= 3 ? args[2] : plugin.getConfig().getString("conquest.default-profile", "default");
                if (conquestManager.startMatch(profile)) {
                    sender.sendMessage(Messages.get("admin.conquest.started", Map.of("profile", profile)));
                } else {
                    sender.sendMessage(Messages.get("errors.conquest-start-failed"));
                }
                return;
            }
            case "stop": {
                if (conquestManager.stopActiveMatch(Messages.get("messages.conquest.reason.manual"), true)) {
                    sender.sendMessage(Messages.get("admin.conquest.stopped"));
                } else {
                    sender.sendMessage(Messages.get("errors.conquest-stop-failed"));
                }
                return;
            }
            case "status":
                handleAdminConquestStatus(sender, conquestManager);
                return;
            case "assign":
                handleAdminConquestAssign(sender, args, true);
                return;
            case "unassign":
            case "remove":
                handleAdminConquestAssign(sender, args, false);
                return;
            case "zones":
            case "list":
                handleAdminConquestZones(sender, args);
                return;
            default:
                sender.sendMessage(Messages.get("errors.usage-conquest"));
        }
    }

    private void handleAdminConquestStatus(CommandSender sender, ConquestManager conquestManager) {
        ConquestManager.Snapshot snapshot = conquestManager.snapshot();
        sender.sendMessage(Messages.get("admin.conquest.status.header"));
        sender.sendMessage(Messages.get("admin.conquest.status.active", Map.of(
            "active", String.valueOf(conquestManager.isActive()),
            "profile", snapshot.profile == null || snapshot.profile.isEmpty() ? "none" : snapshot.profile
        )));
        if (!conquestManager.isActive()) {
            return;
        }
        for (Map.Entry<String, Integer> entry : snapshot.ticketsByOwnerKey.entrySet()) {
            CaptureOwner owner = snapshot.ownersByKey.get(entry.getKey());
            sender.sendMessage(Messages.get("admin.conquest.status.entry", Map.of(
                "team", owner != null ? owner.getDisplayName() : entry.getKey(),
                "tickets", String.valueOf(Math.max(0, entry.getValue()))
            )));
        }
        sender.sendMessage(Messages.get("admin.conquest.status.zones", Map.of(
            "zones", String.join(", ", snapshot.zoneIds)
        )));
    }

    private void handleAdminConquestAssign(CommandSender sender, String[] args, boolean assign) {
        String profile = plugin.getConfig().getString("conquest.default-profile", "default");
        String requestedZone;
        if (args.length >= 4) {
            profile = args[2];
            requestedZone = args[3];
        } else if (args.length >= 3) {
            requestedZone = args[2];
        } else {
            sender.sendMessage(Messages.get(assign ? "errors.usage-conquest-assign" : "errors.usage-conquest-unassign"));
            return;
        }

        CapturePoint point = resolveCapturePointById(requestedZone);
        if (assign && point == null) {
            sender.sendMessage(Messages.get("errors.conquest-zone-not-found"));
            return;
        }
        String zoneId = point != null ? point.getId() : requestedZone;
        ConquestManager conquestManager = plugin.getConquestManager();
        List<String> configured = new ArrayList<>(conquestManager.getConfiguredZones(profile));
        boolean changed;
        if (assign) {
            boolean exists = configured.stream().anyMatch(id -> id.equalsIgnoreCase(zoneId));
            if (exists) {
                sender.sendMessage(Messages.get("admin.conquest.assign.already", Map.of("zone", zoneId)));
                return;
            }
            configured.add(zoneId);
            changed = true;
        } else {
            int before = configured.size();
            configured.removeIf(id -> id != null && id.equalsIgnoreCase(zoneId));
            changed = configured.size() != before;
            if (!changed) {
                sender.sendMessage(Messages.get("admin.conquest.unassign.not-found", Map.of("zone", zoneId)));
                return;
            }
        }
        conquestManager.setConfiguredZones(profile, configured);
        sender.sendMessage(Messages.get(assign ? "admin.conquest.assigned" : "admin.conquest.unassigned", Map.of(
            "profile", profile,
            "zone", zoneId,
            "count", String.valueOf(configured.size())
        )));
    }

    private void handleAdminConquestZones(CommandSender sender, String[] args) {
        String profile = args.length >= 3 ? args[2] : plugin.getConfig().getString("conquest.default-profile", "default");
        List<String> configured = plugin.getConquestManager().getConfiguredZones(profile);
        sender.sendMessage(Messages.get("admin.conquest.zones.header", Map.of("profile", profile)));
        if (configured.isEmpty()) {
            sender.sendMessage(Messages.get("admin.conquest.zones.none"));
            return;
        }
        for (String zoneId : configured) {
            CapturePoint point = resolveCapturePointById(zoneId);
            sender.sendMessage(Messages.get("admin.conquest.zones.entry", Map.of(
                "id", zoneId,
                "zone", point != null ? point.getName() : zoneId,
                "exists", point != null ? "yes" : "no"
            )));
        }
    }

    private void handleSchemaRepair(CommandSender sender) {
        try {
            plugin.repairAllConfigsAndData(false);
            sender.sendMessage(Messages.get("messages.migration-success"));
            int rebound = plugin.getLastLegacyTownOwnerRebindCount();
            if (rebound > 0) {
                sender.sendMessage(plugin.colorize("&7Rebound &f" + rebound + "&7 legacy Towny owner references to stable IDs."));
            } else {
                sender.sendMessage(plugin.colorize("&7No legacy Towny owner references needed rebinding."));
            }
            plugin.getLogger().info(String.format("Schema repair executed by %s (non-destructive)", sender.getName()));
        } catch (Exception e) {
            String reason = e.getMessage() == null ? "Unknown error" : e.getMessage();
            sender.sendMessage(Messages.get("messages.migration-failed", Map.of("error", reason)));
            plugin.getLogger().warning("Schema repair failed: " + reason);
        }
    }

    private void sendCapturePointList(Player player) {
        Map<String, CapturePoint> points = this.plugin.getCapturePoints();
        if (points.isEmpty()) {
            player.sendMessage(Messages.get("messages.zone.no-points"));
            return;
        }
        player.sendMessage(plugin.colorize("&6&l────────── &e&lCapture Zones &6&l──────────"));
        for (CapturePoint point : points.values()) {
            String status;
            String controllingTown = point.getControllingTown();
            if (controllingTown.isEmpty()) {
                status = "&c&lUNCLAIMED";
            } else {
                status = "&a&lCONTROLLED &7by &f" + controllingTown;
            }
            if (this.plugin.getActiveSessions().containsKey(point.getId())) {
                CaptureSession session = this.plugin.getActiveSessions().get(point.getId());
                status = "&6&lCAPTURING &7by &f" + session.getTownName();
            }
            player.sendMessage(Messages.get("messages.zone.point-entry", 
                Map.of("name", point.getName(), "status", status)));
        }
        player.sendMessage(plugin.colorize("&6&l────────── &e&lStatistics &6&l──────────"));
        player.sendMessage(Messages.get("messages.zone.total-points", Map.of("count", String.valueOf(points.size()))));
        player.sendMessage(Messages.get("messages.zone.active-captures", Map.of("count", String.valueOf(this.plugin.getActiveSessions().size()))));
        player.sendMessage(plugin.colorize("&6&l──────────"));
    }

    private void showPointInfo(Player player, String pointId) {
        CapturePoint point = this.plugin.getCapturePoints().get(pointId);
        if (point == null) {
            player.sendMessage(Messages.get("messages.info.point-not-found"));
            return;
        }
        player.sendMessage(Messages.get("messages.info.point-header", Map.of("point", point.getName())));
        player.sendMessage(Messages.get("messages.info.point-id", Map.of("id", point.getId())));
        player.sendMessage(Messages.get("messages.info.point-type", Map.of("type", point.getType())));
        player.sendMessage(Messages.get("messages.info.point-shape", Map.of("shape", point.getShapeType().name().toLowerCase())));
        if (point.isCuboid()) {
            player.sendMessage(Messages.get("messages.info.point-cuboid", Map.of(
                "width", String.valueOf(point.getCuboidWidthBlocks()),
                "height", String.valueOf(point.getCuboidHeightBlocks()),
                "depth", String.valueOf(point.getCuboidDepthBlocks())
            )));
        } else {
            player.sendMessage(Messages.get("messages.info.point-radius", Map.of("radius", String.valueOf(point.getChunkRadius()))));
        }
        player.sendMessage(Messages.get("messages.info.point-reward", Map.of("reward", String.valueOf(plugin.getBaseReward(point)))));
        player.sendMessage(Messages.get("messages.info.controlling-town", Map.of("town", point.getControllingTown().isEmpty() ? "None" : point.getControllingTown())));
        if (this.plugin.getActiveSessions().containsKey(pointId)) {
            CaptureSession session = this.plugin.getActiveSessions().get(pointId);
            player.sendMessage(Messages.get("messages.info.capturing-by", Map.of("town", session.getTownName())));
        }
    }

    private void handleTpCommand(Player player, String[] args) {
        if (!plugin.isCaptureTeleportEnabled()) {
            player.sendMessage(Messages.get("errors.tp-disabled"));
            return;
        }

        Map<String, CapturePoint> activePoints = getActiveCapturePoints();
        if (activePoints.isEmpty()) {
            player.sendMessage(Messages.get("errors.tp-no-active"));
            return;
        }

        if (activePoints.size() == 1) {
            teleportToCapturePoint(player, activePoints.values().iterator().next());
            return;
        }

        if (args.length < 2) {
            player.sendMessage(Messages.get("messages.tp.choose-header", Map.of(
                "count", String.valueOf(activePoints.size())
            )));
            for (CapturePoint point : activePoints.values()) {
                CaptureSession session = plugin.getActiveSession(point.getId());
                String owner = resolveActiveCaptureOwner(point);
                player.sendMessage(Messages.get("messages.tp.choose-entry", Map.of(
                    "id", point.getId(),
                    "zone", point.getName(),
                    "owner", owner
                )));
            }
            player.sendMessage(Messages.get("errors.usage-tp"));
            return;
        }

        CapturePoint selectedPoint = findActiveCapturePoint(activePoints, args[1]);
        if (selectedPoint == null) {
            player.sendMessage(Messages.get("errors.tp-invalid-zone"));
            return;
        }

        teleportToCapturePoint(player, selectedPoint);
    }

    private Map<String, CapturePoint> getActiveCapturePoints() {
        Map<String, CapturePoint> activePoints = new HashMap<>();
        for (Map.Entry<String, CaptureSession> entry : plugin.getActiveSessions().entrySet()) {
            String pointId = entry.getKey();
            CaptureSession session = entry.getValue();
            if (pointId == null || session == null || !session.isActive()) {
                continue;
            }
            CapturePoint point = plugin.getCapturePoint(pointId);
            if (point == null || point.getLocation() == null || point.getLocation().getWorld() == null) {
                continue;
            }
            activePoints.put(pointId, point);
        }
        for (String pointId : plugin.getActiveKothZoneIds()) {
            if (pointId == null || pointId.trim().isEmpty()) {
                continue;
            }
            CapturePoint point = plugin.getCapturePoint(pointId);
            if (point == null || point.getLocation() == null || point.getLocation().getWorld() == null) {
                continue;
            }
            activePoints.put(point.getId(), point);
        }
        return activePoints;
    }

    private CapturePoint resolveCapturePointById(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return null;
        }
        CapturePoint direct = plugin.getCapturePoint(pointId.trim());
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, CapturePoint> entry : plugin.getCapturePoints().entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(pointId.trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private List<String> getConfiguredAssignedKothZoneIds() {
        List<String> configured = plugin.getConfig().getStringList("koth.activation.zones");
        List<String> result = new ArrayList<>();
        if (configured == null || configured.isEmpty()) {
            return result;
        }

        for (String raw : configured) {
            if (raw == null || raw.trim().isEmpty()) {
                continue;
            }
            CapturePoint point = resolveCapturePointById(raw.trim());
            if (point == null) {
                continue;
            }
            if (!containsZoneIgnoreCase(result, point.getId())) {
                result.add(point.getId());
            }
        }
        return result;
    }

    private boolean containsZoneIgnoreCase(List<String> zoneIds, String candidateId) {
        if (zoneIds == null || candidateId == null) {
            return false;
        }
        for (String zoneId : zoneIds) {
            if (zoneId != null && zoneId.equalsIgnoreCase(candidateId)) {
                return true;
            }
        }
        return false;
    }

    private String resolveActiveCaptureOwner(CapturePoint point) {
        if (point == null) {
            return "Unknown";
        }
        CaptureSession session = plugin.getActiveSession(point.getId());
        if (session != null && session.getTownName() != null && !session.getTownName().trim().isEmpty()) {
            return session.getTownName();
        }

        KothManager kothManager = plugin.getKothManager();
        if (kothManager != null && kothManager.isZoneActive(point.getId())) {
            KothManager.ZoneStateSnapshot state = kothManager.getZoneState(point.getId());
            if (state.holderName != null && !state.holderName.trim().isEmpty()) {
                return state.holderName;
            }
            return "KOTH";
        }
        return "Unknown";
    }

    private CapturePoint findActiveCapturePoint(Map<String, CapturePoint> activePoints, String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return null;
        }
        String normalized = pointId.trim();
        CapturePoint direct = activePoints.get(normalized);
        if (direct != null) {
            return direct;
        }

        for (Map.Entry<String, CapturePoint> entry : activePoints.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void teleportToCapturePoint(Player player, CapturePoint point) {
        if (point == null || point.getLocation() == null || point.getLocation().getWorld() == null) {
            player.sendMessage(Messages.get("errors.tp-unavailable"));
            return;
        }

        Location destination = point.getLocation().clone();
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());
        destination.getChunk().load();
        player.teleport(destination);

        player.sendMessage(Messages.get("messages.tp-success", Map.of(
            "zone", point.getName(),
            "id", point.getId()
        )));
    }

    private void startCapture(Player player) {
        CapturePoint targetPoint = null;
        for (CapturePoint point : this.plugin.getCandidateCapturePoints(player.getLocation(), 0)) {
            if (!this.plugin.isWithinZone(point, player.getLocation())) continue;
            targetPoint = point;
            break;
        }
        if (targetPoint == null) {
            player.sendMessage(Messages.get("errors.not-in-any-zone"));
            return;
        }

        KothManager kothManager = plugin.getKothManager();
        if (kothManager != null && kothManager.isEnabled() && kothManager.isZoneManaged(targetPoint.getId())) {
            player.sendMessage(Messages.get("errors.capture-koth-managed", Map.of(
                "zone", targetPoint.getName()
            )));
            return;
        }

        int minPlayers = this.plugin.getConfig().getInt("settings.min-online-players", 5);
        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
            player.sendMessage(Messages.get("errors.not-enough-players", Map.of("minplayers", String.valueOf(minPlayers))));
            return;
        }
        boolean success = this.plugin.startCapture(player, targetPoint.getId());
        if (success) {
            player.sendMessage(Messages.get("messages.capture.start-success", Map.of("point", targetPoint.getName())));
        } else {
            player.sendMessage(Messages.get("errors.capture-failed"));
        }
    }

    private void protectCurrentChunk(Player player) {
        Location playerLoc = player.getLocation();
        int chunkX = playerLoc.getBlockX() >> 4;
        int chunkZ = playerLoc.getBlockZ() >> 4;
        String id = "protected_chunk_" + chunkX + "_" + chunkZ;
        if (this.plugin.getCapturePoints().containsKey(id)) {
            player.sendMessage(Messages.get("messages.chunk.already-protected"));
            return;
        }
        String name = "Protected Chunk at " + chunkX + "," + chunkZ;
        Location chunkCenter = new Location(playerLoc.getWorld(), (double)((chunkX << 4) + 8), playerLoc.getY(), (double)((chunkZ << 4) + 8));
        if (!this.plugin.createCapturePoint(id, name, chunkCenter, 0, 0.0)) {
            sendMaxCapturePointsReached(player);
            return;
        }
        player.sendMessage(Messages.get("messages.chunk.protected-success"));
        player.sendMessage(Messages.get("messages.chunk.coordinates", 
            Map.of("x", String.valueOf(chunkX), "z", String.valueOf(chunkZ))));
        this.showChunkBoundaries(player, chunkX, chunkZ);
    }

    private void showChunkBoundaries(final Player player, int chunkX, int chunkZ) {
        final int minX = chunkX << 4;
        final int minZ = chunkZ << 4;
        final int maxX = minX + 15;
        final int maxZ = minZ + 15;
        final int y = player.getLocation().getBlockY();
        new BukkitRunnable(){
            int count = 0;

            public void run() {
                if (this.count >= 200) {
                    this.cancel();
                    return;
                }
                for (int x = minX; x <= maxX; ++x) {
                    for (int z = minZ; z <= maxZ; ++z) {
                        if (x != minX && x != maxX && z != minZ && z != maxZ) continue;
                        Location particleLoc = new Location(player.getWorld(), (double)x + 0.5, (double)(y + 1), (double)z + 0.5);
                        player.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, (Object)new Particle.DustOptions(Color.RED, 1.0f));
                    }
                }
                ++this.count;
            }
        }.runTaskTimer((Plugin)this.plugin, 0L, 1L);
    }

    private void handleCreateCommand(Player player, String[] args) {
        String id = args[1];
        String type = args[2];
        String radiusStr = args[3];
        String rewardStr = args[4];
        
        if (id.isEmpty() || id.length() > 32) {
            player.sendMessage(Messages.get("errors.invalid-id"));
            return;
        }
        
        if (type.isEmpty() || type.length() > 64) {
            player.sendMessage(Messages.get("errors.invalid-type"));
            return;
        }
        
        try {
            int chunkRadius = Integer.parseInt(radiusStr);
            double reward = Double.parseDouble(rewardStr);
            
            if (chunkRadius <= 0) {
                player.sendMessage(Messages.get("errors.invalid-radius"));
                return;
            }
            
            if (reward < 0.0) {
                player.sendMessage(Messages.get("errors.invalid-reward"));
                return;
            }
            
            if (plugin.getCapturePoints().containsKey(id)) {
                player.sendMessage(Messages.get("errors.id-exists"));
                return;
            }

            if (!plugin.canCreateMoreCapturePoints()) {
                sendMaxCapturePointsReached(player);
                return;
            }
            
            boolean created = plugin.createCapturePoint(id, type, player.getLocation(), chunkRadius, reward);
            if (!created) {
                if (!plugin.canCreateMoreCapturePoints()) {
                    sendMaxCapturePointsReached(player);
                } else {
                    player.sendMessage(Messages.get("errors.create-failed"));
                }
                return;
            }
            player.sendMessage(Messages.get("messages.create.success"));
            if (plugin.getDiscordWebhook() != null) {
                plugin.getDiscordWebhook().sendZoneCreated(id, id, player.getName(), type, chunkRadius, reward);
            }
            
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("radius", String.valueOf(chunkRadius));
            placeholders.put("blocks", String.valueOf(chunkRadius * 16));
            player.sendMessage(Messages.get("messages.create.radius-info", placeholders));
        } catch (NumberFormatException e) {
            player.sendMessage(Messages.get("errors.invalid-number"));
        }
    }

    private void handleCreateCuboidCommand(Player player, String[] args) {
        String id = args[1];
        String type = args[2];

        if (id.isEmpty() || id.length() > 32) {
            player.sendMessage(Messages.get("errors.invalid-id"));
            return;
        }

        if (type.isEmpty() || type.length() > 64) {
            player.sendMessage(Messages.get("errors.invalid-type"));
            return;
        }

        if (plugin.getCapturePoints().containsKey(id)) {
            player.sendMessage(Messages.get("errors.id-exists"));
            return;
        }

        if (!plugin.canCreateMoreCapturePoints()) {
            sendMaxCapturePointsReached(player);
            return;
        }

        if (args.length == 4) {
            handleCreateCuboidSelectionMode(player, id, type, args[3]);
            return;
        }

        handleCreateCuboidCoordinateMode(player, id, type, args);
    }

    private void handleCreateCuboidSelectionMode(Player player, String id, String type, String rewardArg) {
        if (!plugin.canCreateMoreCapturePoints()) {
            sendMaxCapturePointsReached(player);
            return;
        }

        double reward;
        try {
            reward = Double.parseDouble(rewardArg);
        } catch (NumberFormatException e) {
            player.sendMessage(Messages.get("errors.invalid-cuboid-number"));
            return;
        }

        if (reward < 0.0) {
            player.sendMessage(Messages.get("errors.invalid-reward"));
            return;
        }

        if (cuboidSelectionManager == null) {
            player.sendMessage(Messages.get("errors.create-failed"));
            return;
        }

        cuboidSelectionManager.startSelection(player, id, type, reward);
    }

    private void handleCreateCuboidCoordinateMode(Player player, String id, String type, String[] args) {
        try {
            if (!plugin.canCreateMoreCapturePoints()) {
                sendMaxCapturePointsReached(player);
                return;
            }

            int x1 = Integer.parseInt(args[3]);
            int y1 = Integer.parseInt(args[4]);
            int z1 = Integer.parseInt(args[5]);
            int x2 = Integer.parseInt(args[6]);
            int y2 = Integer.parseInt(args[7]);
            int z2 = Integer.parseInt(args[8]);
            double reward = Double.parseDouble(args[9]);

            if (reward < 0.0) {
                player.sendMessage(Messages.get("errors.invalid-reward"));
                return;
            }

            int minX = Math.min(x1, x2);
            int minY = Math.min(y1, y2);
            int minZ = Math.min(z1, z2);
            int maxX = Math.max(x1, x2);
            int maxY = Math.max(y1, y2);
            int maxZ = Math.max(z1, z2);
            Location center = new Location(
                player.getWorld(),
                (minX + maxX + 1) / 2.0,
                (minY + maxY + 1) / 2.0,
                (minZ + maxZ + 1) / 2.0
            );

            boolean created = plugin.createCuboidCapturePoint(id, type, player.getWorld(), x1, y1, z1, x2, y2, z2, reward);
            if (!created) {
                if (!plugin.canCreateMoreCapturePoints()) {
                    sendMaxCapturePointsReached(player);
                } else {
                    player.sendMessage(Messages.get("errors.create-failed"));
                }
                return;
            }

            CapturePoint createdPoint = plugin.getCapturePoint(id);
            if (createdPoint == null || !createdPoint.isCuboid()) {
                player.sendMessage(Messages.get("errors.create-failed"));
                return;
            }

            player.sendMessage(Messages.get("messages.create.success"));

            if (cuboidSelectionManager != null) {
                cuboidSelectionManager.showTemporaryOutline(createdPoint);
            }

            if (plugin.getDiscordWebhook() != null) {
                plugin.getDiscordWebhook().sendZoneCreated(
                    createdPoint.getId(),
                    createdPoint.getId(),
                    player.getName(),
                    createdPoint.getType(),
                    createdPoint.getChunkRadius(),
                    createdPoint.getReward()
                );
            }

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("width", String.valueOf(createdPoint.getCuboidWidthBlocks()));
            placeholders.put("height", String.valueOf(createdPoint.getCuboidHeightBlocks()));
            placeholders.put("depth", String.valueOf(createdPoint.getCuboidDepthBlocks()));
            player.sendMessage(Messages.get("messages.create.cuboid-info", placeholders));
        } catch (NumberFormatException e) {
            player.sendMessage(Messages.get("errors.invalid-cuboid-number"));
        }
    }

    private void sendMaxCapturePointsReached(Player player) {
        player.sendMessage(Messages.get("errors.max-capture-points-reached", Map.of(
            "max", String.valueOf(plugin.getMaxCapturePointsLimit())
        )));
    }

    private void showZoneBoundaries(Player player, String pointId) {
        CapturePoint point = this.plugin.getCapturePoints().get(pointId);
        if (point == null) {
            player.sendMessage(this.plugin.colorize("&cCapture point not found!"));
            return;
        }

        if (!plugin.boundariesEnabled()) {
            player.sendMessage(Messages.get("messages.zone.boundaries-disabled"));
            return;
        }

        String key = player.getUniqueId() + "_" + pointId;
        if (this.plugin.boundaryTasks.containsKey(key)) {
            this.plugin.stopBoundaryVisualization(key);
            player.sendMessage(this.plugin.colorize("&7Zone boundary display hidden for " + point.getName()));
            return;
        }

        player.sendMessage(this.plugin.colorize("&eShowing boundaries for " + point.getName() + " (circular zone)"));
        player.sendMessage(this.plugin.colorize("&7Type /capturezones showzone " + pointId + " again to hide"));
        boolean started = this.plugin.startBoundaryVisualization(player, point);
        if (!started) {
            player.sendMessage(Messages.get("messages.zone.boundaries-unavailable"));
        }
    }
    
    private void toggleAllZoneBoundaries(Player player) {
        if (!this.plugin.boundariesEnabled()) {
            player.sendMessage(Messages.get("messages.zone.boundaries-disabled"));
            return;
        }
        int hiddenCount = 0;
        int shownCount = 0;
        
        for (CapturePoint point : this.plugin.getCapturePoints().values()) {
            String key = player.getUniqueId() + "_" + point.getId();
            if (this.plugin.boundaryTasks.containsKey(key)) {
                this.plugin.stopBoundaryVisualization(key);
                hiddenCount++;
            } else {
                showZoneBoundaries(player, point.getId());
                shownCount++;
            }
        }
        
        if (hiddenCount > 0 && shownCount > 0) {
            player.sendMessage(plugin.colorize("&7Toggled " + hiddenCount + " zones off, " + shownCount + " zones on"));
        } else if (hiddenCount > 0) {
            player.sendMessage(plugin.colorize("&cAll zone boundaries hidden"));
        } else if (shownCount > 0) {
            player.sendMessage(plugin.colorize("&aAll zone boundaries visible"));
        } else {
            player.sendMessage(plugin.colorize("&cNo capture zones found"));
        }
    }
    
    private void runTests(Player player) {
        player.sendMessage(plugin.colorize("&6&l=== Running Basic Tests ==="));
        player.sendMessage(plugin.colorize("&7Creating test zones..."));
        
        Location loc1 = player.getLocation().clone().add(20, 0, 0);
        Location loc2 = player.getLocation().clone().add(-20, 0, 0);
        Location loc3 = player.getLocation().clone().add(0, 0, 20);
        
        plugin.createCapturePoint("test_zone_1", "Test Zone 1", loc1, 3, 1000.0);
        plugin.createCapturePoint("test_zone_2", "Test Zone 2", loc2, 5, 2000.0);
        plugin.createCapturePoint("test_zone_3", "Test Zone 3", loc3, 2, 500.0);
        
        player.sendMessage(plugin.colorize("&a[OK] Created 3 test zones"));
        player.sendMessage(plugin.colorize("&7You can now test manually by running /capturezones capture"));
        player.sendMessage(plugin.colorize("&7Or run &e/capturezones testall &7for automated testing"));
    }
    
    private void runAllTests(Player player) {
        player.sendMessage(plugin.colorize("&6&l========== COMPREHENSIVE CAPTURE TESTS =========="));
        
        final int[] testsPassed = {0};
        final int[] testsTotal = {0};
        
        player.sendMessage(plugin.colorize("&7[Test 1-3] Creating multiple test zones..."));
        Location loc1 = player.getLocation().clone().add(50, 0, 50);
        Location loc2 = player.getLocation().clone().add(-50, 0, 50);
        Location loc3 = player.getLocation().clone().add(50, 0, -50);
        
        plugin.createCapturePoint("test_zone_1", "Test Zone 1", loc1, 5, 1000.0);
        plugin.createCapturePoint("test_zone_2", "Test Zone 2", loc2, 5, 2000.0);
        plugin.createCapturePoint("test_zone_3", "Test Zone 3", loc3, 5, 1500.0);
        
        for (int i = 1; i <= 3; i++) {
            testsTotal[0]++;
            if (plugin.getCapturePoints().containsKey("test_zone_" + i)) {
                player.sendMessage(plugin.colorize("&a[PASS] Test zone " + i + " created"));
                testsPassed[0]++;
        } else {
                player.sendMessage(plugin.colorize("&c[FAIL] Test zone " + i + " creation failed"));
            }
        }
        
        player.sendMessage(plugin.colorize("&7[Test 4] Testing normal capture (5 seconds)..."));
        testsTotal[0]++;
        Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
            boolean started = plugin.startTestCapture(player, "test_zone_1", 1, 5);
            if (started && plugin.getActiveSession("test_zone_1") != null) {
                player.sendMessage(plugin.colorize("&a[PASS] Normal capture started"));
                testsPassed[0]++;
                
                testsTotal[0]++;
                Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                    boolean duplicateStarted = plugin.startTestCapture(player, "test_zone_1", 1, 5);
                    if (!duplicateStarted || (plugin.getActiveSessions().size() == 1)) {
                        player.sendMessage(plugin.colorize("&a[PASS] Duplicate capture prevented"));
                        testsPassed[0]++;
                    } else {
                        player.sendMessage(plugin.colorize("&c[FAIL] Duplicate capture allowed"));
                    }
                    
                    Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                        testsTotal[0]++;
                        CapturePoint point = plugin.getCapturePoint("test_zone_1");
                        if (point != null && !point.getControllingTown().isEmpty()) {
                            player.sendMessage(plugin.colorize("&a[PASS] Capture completed"));
                            testsPassed[0]++;
        } else {
                            player.sendMessage(plugin.colorize("&c[FAIL] Capture did not complete"));
                        }
                        
                        testsTotal[0]++;
                        boolean alreadyCapturedStarted = plugin.startTestCapture(player, "test_zone_1", 1, 5);
                        if (!alreadyCapturedStarted || plugin.getActiveSession("test_zone_1") == null) {
                            player.sendMessage(plugin.colorize("&a[PASS] Cannot capture already-controlled zone"));
                            testsPassed[0]++;
        } else {
                            player.sendMessage(plugin.colorize("&c[FAIL] Allowed capture of controlled zone"));
                        }
                        
                        testsTotal[0]++;
                        if (plugin.resetPoint("test_zone_1")) {
                            player.sendMessage(plugin.colorize("&a[PASS] Zone reset successful"));
                            testsPassed[0]++;
        } else {
                            player.sendMessage(plugin.colorize("&c[FAIL] Zone reset failed"));
                        }
                        
                        testsTotal[0]++;
                        player.sendMessage(plugin.colorize("&7[Test 9] Capturing reset zone..."));
                        Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                            boolean resetCaptureStarted = plugin.startTestCapture(player, "test_zone_1", 1, 5);
                            if (resetCaptureStarted) {
                                player.sendMessage(plugin.colorize("&a[PASS] Capture after reset works"));
                                testsPassed[0]++;
        } else {
                                player.sendMessage(plugin.colorize("&c[FAIL] Cannot capture after reset"));
                            }
                            
                            Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                                testsTotal[0]++;
                                if (plugin.stopCapture("test_zone_1", "Test cancellation")) {
                                    player.sendMessage(plugin.colorize("&a[PASS] Admin stop capture works"));
                                    testsPassed[0]++;
        } else {
                                    player.sendMessage(plugin.colorize("&c[FAIL] Admin stop failed"));
                                }
                                
                                testsTotal[0]++;
                                player.sendMessage(plugin.colorize("&7[Test 11] Testing force capture..."));
                                plugin.resetPoint("test_zone_1");
            Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                                    String forceTarget = null;
                                    java.util.List<String> targets = plugin.getForceCaptureTargets();
                                    if (!targets.isEmpty()) {
                                        forceTarget = targets.get(0);
                                    } else if (plugin.getDefaultOwnerType() == CaptureOwnerType.PLAYER) {
                                        forceTarget = player.getName();
                                    }

                                    if (forceTarget != null && !forceTarget.isEmpty()) {
                                        boolean forceResult = plugin.forceCapture("test_zone_1", forceTarget);
                                        if (forceResult) {
                                            player.sendMessage(plugin.colorize("&a[PASS] Force capture works"));
                                            testsPassed[0]++;
                                        } else {
                                            player.sendMessage(plugin.colorize("&c[FAIL] Force capture failed"));
                                        }
                                    } else {
                                        player.sendMessage(plugin.colorize("&7[SKIP] Force capture - no available owner target"));
                                        testsPassed[0]++;
                                    }
                                    
                                    testsTotal[0]++;
                                    player.sendMessage(plugin.colorize("&7[Test 12] Testing multiple concurrent captures..."));
                                    plugin.resetPoint("test_zone_2");
                                    Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                                        boolean concurrent2 = plugin.startTestCapture(player, "test_zone_2", 1, 5);
                                        boolean concurrent3 = plugin.startTestCapture(player, "test_zone_3", 1, 5);
                                        if (concurrent2 && concurrent3) {
                                            player.sendMessage(plugin.colorize("&a[PASS] Multiple concurrent captures work"));
                                            testsPassed[0]++;
                                        } else {
                                            player.sendMessage(plugin.colorize("&c[FAIL] Multiple captures failed"));
                                        }
                                        
                                        Bukkit.getScheduler().runTaskLater((Plugin)plugin, () -> {
                                            testsTotal[0]++;
                                            if (plugin.deleteCapturePoint("test_zone_1")) {
                                                player.sendMessage(plugin.colorize("&a[PASS] Zone 1 deleted with boundary cleanup"));
                                                testsPassed[0]++;
                                            } else {
                                                player.sendMessage(plugin.colorize("&c[FAIL] Zone 1 deletion failed"));
                                            }
                                            
                                            testsTotal[0]++;
                                            if (plugin.deleteCapturePoint("test_zone_2") && plugin.deleteCapturePoint("test_zone_3")) {
                                                player.sendMessage(plugin.colorize("&a[PASS] All zones cleaned up"));
                                                testsPassed[0]++;
        } else {
                                                player.sendMessage(plugin.colorize("&c[FAIL] Zone cleanup failed"));
                                            }
                                            
                                            player.sendMessage(plugin.colorize("&6&l========== FINAL TEST RESULTS =========="));
                                            player.sendMessage(plugin.colorize("&eTests Passed: &a" + testsPassed[0] + "&7/&f" + testsTotal[0]));
                                            
                                            if (testsPassed[0] == testsTotal[0]) {
                                                player.sendMessage(plugin.colorize("&a&l[SUCCESS] ALL TESTS PASSED!"));
                                            } else {
                                                player.sendMessage(plugin.colorize("&c&l[ERROR] Some tests failed: &7" + testsTotal[0] + " - " + testsPassed[0]));
                                            }
                                        }, 120L);
                                    }, 20L);
                                }, 20L);
                            }, 120L);
                        }, 120L);
                    }, 120L);
                }, 20L);
        } else {
                player.sendMessage(plugin.colorize("&c[FAIL] Normal capture failed"));
        }
        }, 20L);
    }
    
    private void handleStatsCommand(Player player) {
        if (plugin.getStatisticsGUI() == null) {
            player.sendMessage(Messages.get("errors.stats-disabled"));
            return;
        }
        
        if (!plugin.getStatisticsGUI().canUseStatsCommand(player)) {
            long remaining = plugin.getStatisticsGUI().getCooldownSeconds(player);
            player.sendMessage(Messages.get("errors.stats-cooldown", Map.of("seconds", String.valueOf(remaining))));
            return;
        }
        
        plugin.getStatisticsGUI().setCooldown(player);
        plugin.getStatisticsGUI().openMainMenu(player);
    }

    private void handleAdminStatsCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Messages.get("errors.usage-stats-admin"));
            return;
        }
        
        String subCmd = args[1].toLowerCase();
        
        switch (subCmd) {
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage(Messages.get("errors.usage-stats-remove"));
                    return;
                }
                handleStatsRemove(sender, args[2]);
                break;
                
            case "reset":
                if (args.length < 3 || !args[2].equals("CONFIRM")) {
                    sender.sendMessage(Messages.get("errors.stats-reset-confirm"));
                    return;
                }
                handleStatsReset(sender);
                break;
                
            default:
                sender.sendMessage(Messages.get("errors.usage-stats-admin"));
                break;
        }
    }
    
    private void handleStatsRemove(CommandSender sender, String playerName) {
        if (plugin.getStatisticsManager() == null) {
            sender.sendMessage(Messages.get("errors.stats-disabled"));
            return;
        }
        
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(Messages.get("errors.player-not-found", Map.of("player", playerName)));
            return;
        }
        
        plugin.getStatisticsManager().removePlayerStats(target.getUniqueId());
        sender.sendMessage(Messages.get("messages.stats-removed", Map.of("player", playerName)));
    }
    
    private void handleStatsReset(CommandSender sender) {
        if (plugin.getStatisticsManager() == null) {
            sender.sendMessage(Messages.get("errors.stats-disabled"));
            return;
        }
        
        plugin.getStatisticsManager().resetAllStats();
        sender.sendMessage(Messages.get("messages.stats-reset"));
    }
    
    /**
     * Handle zone config commands
     * Usage: /cap zoneconfig <zone_id> <subcommand> [args]
     * Subcommands:
     *   set <path> <value> - Set a zone-specific config value
     *   reset <path> - Reset a config path to default (or reset all if no path)
     *   reload - Reload the zone config from disk
     *   itemrewards - Open GUI editor for custom item rewards
     */

    private void handleZoneConfigCommand(CommandSender sender, String[] args) {
        String zoneId = args[1];
        
        CapturePoint point = plugin.getCapturePoint(zoneId);
        if (point == null) {
            sender.sendMessage(Messages.get("errors.zone-not-found", Map.of("id", zoneId)));
            return;
        }
        
        if (args.length < 3) {
            sender.sendMessage(Messages.get("errors.usage-zoneconfig"));
            return;
        }
        
        String subCmd = args[2].toLowerCase();
        
        switch (subCmd) {
            case "set":
                if (!PermissionNode.has(sender, "admin.zoneconfig.set")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length < 5) {
                    sender.sendMessage(Messages.get("errors.usage-zoneconfig-set"));
                    return;
                }
                handleZoneConfigSet(sender, zoneId, args[3], args[4]);
                break;
                
            case "reset":
                if (!PermissionNode.has(sender, "admin.zoneconfig.reset")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length >= 4) {
                    handleZoneConfigReset(sender, zoneId, args[3]);
                } else {
                    handleZoneConfigReset(sender, zoneId, null);
                }
                break;
                
            case "reload":
                if (!PermissionNode.has(sender, "admin.zoneconfig.reload")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                handleZoneConfigReload(sender, zoneId);
                break;

            case "itemrewards":
            case "items":
                if (!PermissionNode.has(sender, "admin.zoneconfig.set")) {
                    sender.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                handleZoneConfigItemRewardsEditor(sender, zoneId);
                break;
                
            default:
                sender.sendMessage(Messages.get("errors.usage-zoneconfig"));
                break;
        }
    }
    
    private void handleZoneConfigSet(CommandSender sender, String zoneId, String path, String value) {
        try {
            Object parsedValue;
            if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
                parsedValue = Boolean.parseBoolean(value);
            } else if (value.matches("-?\\d+")) {
                parsedValue = Integer.parseInt(value);
            } else if (value.matches("-?\\d+\\.\\d+")) {
                parsedValue = Double.parseDouble(value);
            } else {
                parsedValue = value;
            }
            
            boolean updated = plugin.getZoneConfigManager().setZoneSetting(zoneId, path, parsedValue);
            if (!updated) {
                sender.sendMessage(Messages.get("errors.zoneconfig-set-invalid"));
                return;
            }
            plugin.invalidateCapturePointSpatialIndex();
            
            sender.sendMessage(Messages.get("messages.zoneconfig-set-success", Map.of(
                "zone", zoneId,
                "path", path,
                "value", value
            )));
            plugin.getLogger().info(String.format("Zone config updated by %s: %s.%s = %s", 
                sender.getName(), zoneId, path, value));
        } catch (Exception e) {
            sender.sendMessage(Messages.get("errors.zoneconfig-set-failed", Map.of(
                "error", e.getMessage()
            )));
            plugin.getLogger().warning("Failed to set zone config: " + e.getMessage());
        }
    }
    
    private void handleZoneConfigReset(CommandSender sender, String zoneId, String path) {
        try {
            if (path == null) {
                plugin.getZoneConfigManager().generateZoneConfig(zoneId);
                plugin.invalidateCapturePointSpatialIndex();
                sender.sendMessage(Messages.get("messages.zoneconfig-reset-all-success", Map.of(
                    "zone", zoneId
                )));
                plugin.getLogger().info(String.format("Zone config fully reset by %s: %s", sender.getName(), zoneId));
            } else {
                Object defaultValue = plugin.getZoneConfigManager().getZoneDefault(path);
                plugin.getZoneConfigManager().setZoneSetting(zoneId, path, defaultValue);
                plugin.getZoneConfigManager().saveZoneConfig(zoneId);
                plugin.invalidateCapturePointSpatialIndex();
                sender.sendMessage(Messages.get("messages.zoneconfig-reset-path-success", Map.of(
                    "zone", zoneId,
                    "path", path
                )));
                plugin.getLogger().info(String.format("Zone config path reset by %s: %s.%s", sender.getName(), zoneId, path));
            }
        } catch (Exception e) {
            sender.sendMessage(Messages.get("errors.zoneconfig-reset-failed", Map.of(
                "error", e.getMessage()
            )));
            plugin.getLogger().warning("Failed to reset zone config: " + e.getMessage());
        }
    }
    
    private void handleZoneConfigReload(CommandSender sender, String zoneId) {
        try {
            plugin.getZoneConfigManager().loadZoneConfig(zoneId);
            plugin.invalidateCapturePointSpatialIndex();
            sender.sendMessage(Messages.get("messages.zoneconfig-reload-success", Map.of(
                "zone", zoneId
            )));
            plugin.getLogger().info(String.format("Zone config reloaded by %s: %s", sender.getName(), zoneId));
        } catch (Exception e) {
            sender.sendMessage(Messages.get("errors.zoneconfig-reload-failed", Map.of(
                "error", e.getMessage()
            )));
            plugin.getLogger().warning("Failed to reload zone config: " + e.getMessage());
        }
    }

    private void handleZoneConfigItemRewardsEditor(CommandSender sender, String zoneId) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Messages.get("errors.player-only"));
            return;
        }
        Player player = (Player) sender;
        if (zoneItemRewardEditor == null) {
            player.sendMessage(Messages.get("errors.zoneconfig-itemrewards-save-failed", Map.of(
                "error", "Item reward editor unavailable"
            )));
            return;
        }
        zoneItemRewardEditor.openEditor(player, zoneId);
    }
    
    private void handleShopCommand(Player player, String[] args) {
        ShopManager shopManager = plugin.getOrCreateShopManagerIfEnabled();
        if (shopManager == null) {
            player.sendMessage(Messages.get("errors.shop.system-disabled"));
            return;
        }
        
        if (args.length == 1) {
            CapturePoint nearestZone = findNearestZone(player);
            if (nearestZone == null) {
                player.sendMessage(Messages.get("errors.shop.no-zone-nearby"));
                return;
            }
            
            ShopListener listener = plugin.getShopListener();
            if (listener != null) {
                listener.openShop(player, nearestZone.getId());
            }
            return;
        }
        
        String subCommand = args[1].toLowerCase();
        
        switch (subCommand) {
            case "edit":
                if (!PermissionNode.has(player, "admin.shop")) {
                    player.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Messages.get("errors.shop.usage-edit"));
                    return;
                }
                String zoneId = args[2];
                if (plugin.getCapturePoint(zoneId) == null) {
                    player.sendMessage(Messages.get("errors.zone-not-found"));
                    return;
                }
                ShopListener listener = plugin.getShopListener();
                if (listener != null) {
                    listener.openEditor(player, zoneId);
                }
                break;
                
            case "reload":
                if (!PermissionNode.has(player, "admin.shop")) {
                    player.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Messages.get("errors.shop.usage-reload"));
                    return;
                }
                shopManager.saveShop(args[2]);
                shopManager.loadAllShops();
                player.sendMessage(Messages.get("messages.shop.reloaded", Map.of("zone", args[2])));
                break;
                
            case "restock":
                if (!PermissionNode.has(player, "admin.shop.restock")) {
                    player.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Messages.get("errors.shop.usage-restock"));
                    return;
                }
                shopManager.restockShop(args[2]);
                player.sendMessage(Messages.get("messages.shop.restocked", Map.of("zone", args[2])));
                break;
                
            case "enable":
                if (!PermissionNode.has(player, "admin.shop")) {
                    player.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Messages.get("errors.shop.usage-enable"));
                    return;
                }
                ShopData enableShop = shopManager.getShop(args[2]);
                enableShop.setEnabled(true);
                shopManager.saveShop(args[2]);
                player.sendMessage(Messages.get("messages.shop.enabled", Map.of("zone", args[2])));
                break;
                
            case "disable":
                if (!PermissionNode.has(player, "admin.shop")) {
                    player.sendMessage(Messages.get("errors.no-permission"));
                    return;
                }
                if (args.length < 3) {
                    player.sendMessage(Messages.get("errors.shop.usage-disable"));
                    return;
                }
                ShopData disableShop = shopManager.getShop(args[2]);
                disableShop.setEnabled(false);
                shopManager.saveShop(args[2]);
                player.sendMessage(Messages.get("messages.shop.disabled", Map.of("zone", args[2])));
                break;
                
            default:
                player.sendMessage(Messages.get("errors.shop.invalid-subcommand"));
                break;
        }
    }
    
    private CapturePoint findNearestZone(Player player) {
        CapturePoint nearest = null;
        double nearestDist = Double.MAX_VALUE;
        
        for (CapturePoint point : plugin.getCapturePoints().values()) {
            if (point.getLocation().getWorld() != player.getWorld()) continue;
            
            double dist = point.getLocation().distance(player.getLocation());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = point;
            }
        }
        
        return nearestDist <= 100 ? nearest : null; // Within 100 blocks
    }
}

