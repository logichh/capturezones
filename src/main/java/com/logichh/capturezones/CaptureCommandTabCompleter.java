package com.logichh.capturezones;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class CaptureCommandTabCompleter implements TabCompleter {
    private final CaptureZones plugin;

    public CaptureCommandTabCompleter(CaptureZones plugin) {
        this.plugin = plugin;
    }

    @Override

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        ArrayList<String> completions = new ArrayList<>();
        if (!(sender instanceof Player)) {
            return completions;
        }

        Player player = (Player) sender;
        if (args.length == 1) {
            ArrayList<String> subCommands = new ArrayList<>();
            subCommands.add("help");
            subCommands.add("list");
            subCommands.add("info");
            subCommands.add("capture");
            subCommands.add("showzone");
            subCommands.add("notifications");
            if (plugin.isCaptureTeleportEnabled()) {
                subCommands.add("tp");
            }
            subCommands.add("stats");
            subCommands.add("shop");
            if (PermissionNode.has(player, "admin")) {
                subCommands.add("admin");
            }
            return filterCompletions(subCommands, args[0]);
        }

        switch (args[0].toLowerCase()) {
            case "info":
                if (args.length == 2) {
                    return filterCompletions(new ArrayList<>(plugin.getCapturePoints().keySet()), args[1]);
                }
                break;
            case "showzone":
                if (args.length == 2) {
                    ArrayList<String> options = new ArrayList<>(plugin.getCapturePoints().keySet());
                    options.add("all");
                    return filterCompletions(options, args[1]);
                }
                break;
            case "tp":
                if (!plugin.isCaptureTeleportEnabled()) {
                    break;
                }
                if (args.length == 2) {
                    ArrayList<String> activePointIds = new ArrayList<>();
                    for (java.util.Map.Entry<String, CaptureSession> entry : plugin.getActiveSessions().entrySet()) {
                        String pointId = entry.getKey();
                        CaptureSession session = entry.getValue();
                        if (pointId == null || session == null || !session.isActive()) {
                            continue;
                        }
                        activePointIds.add(pointId);
                    }
                    activePointIds.addAll(plugin.getActiveKothZoneIds());
                    return filterCompletions(activePointIds, args[1]);
                }
                break;
            case "admin":
                return completeAdmin(player, args);
            default:
                break;
        }

        return completions;
    }

    private List<String> completeAdmin(Player player, String[] args) {
        ArrayList<String> completions = new ArrayList<>();
        if (!PermissionNode.has(player, "admin")) {
            return completions;
        }

        if (args.length == 2) {
            List<String> adminSubcommands = new ArrayList<>();
            adminSubcommands.add("help");
            if (PermissionNode.has(player, "admin.create")) {
                adminSubcommands.add("create");
                adminSubcommands.add("createcuboid");
            }
            if (PermissionNode.has(player, "admin.stop")) {
                adminSubcommands.add("stop");
            }
            if (PermissionNode.has(player, "admin.delete")) {
                adminSubcommands.add("deletezone");
            }
            if (PermissionNode.has(player, "admin.forcecapture")) {
                adminSubcommands.add("forcecapture");
            }
            if (PermissionNode.has(player, "admin.reset")) {
                adminSubcommands.add("reset");
                adminSubcommands.add("resetall");
            }
            if (PermissionNode.has(player, "admin.zoneconfig")) {
                adminSubcommands.add("zoneconfig");
            }
            if (PermissionNode.has(player, "admin.shop") || PermissionNode.has(player, "admin.shop.restock")) {
                adminSubcommands.add("shop");
            }
            if (PermissionNode.has(player, "admin.stats")) {
                adminSubcommands.add("stats");
            }
            if (PermissionNode.has(player, "admin.reload")) {
                adminSubcommands.add("reload");
                adminSubcommands.add("reloadlang");
            }
            if (PermissionNode.has(player, "admin.togglechat")) {
                adminSubcommands.add("togglechat");
            }
            if (PermissionNode.has(player, "admin.migrate")) {
                adminSubcommands.add("repair");
                adminSubcommands.add("migrate");
            }
            if (PermissionNode.has(player, "admin.koth")) {
                adminSubcommands.add("koth");
            }
            if (PermissionNode.has(player, "admin.conquest")) {
                adminSubcommands.add("conquest");
            }
            return filterCompletions(adminSubcommands, args[1]);
        }

        String adminSubcommand = args[1].toLowerCase();
        switch (adminSubcommand) {
            case "create":
                if (!PermissionNode.has(player, "admin.create")) {
                    break;
                }
                if (args.length == 3) {
                    return filterCompletions(List.of("zone1", "zone2", "zone3"), args[2]);
                }
                if (args.length == 4) {
                    return filterCompletions(List.of("Capital", "Resource_Point", "Military_Base"), args[3]);
                }
                if (args.length == 5) {
                    return filterCompletions(List.of("1", "2", "3", "4", "5"), args[4]);
                }
                if (args.length == 6) {
                    return filterCompletions(List.of("100", "500", "1000", "5000"), args[5]);
                }
                break;
            case "createcuboid":
                if (!PermissionNode.has(player, "admin.create")) {
                    break;
                }
                if (args.length == 3) {
                    return filterCompletions(List.of("zone1", "zone2", "zone3"), args[2]);
                }
                if (args.length == 4) {
                    return filterCompletions(List.of("Capital", "Resource_Point", "Military_Base"), args[3]);
                }
                if (args.length == 5) {
                    List<String> options = new ArrayList<>();
                    options.add("100");
                    options.add("500");
                    options.add("1000");
                    options.add("5000");
                    options.add(String.valueOf(player.getLocation().getBlockX()));
                    return filterCompletions(options, args[4]);
                }
                if (args.length >= 6 && args.length <= 10) {
                    int coord = player.getLocation().getBlockX();
                    if (args.length == 6 || args.length == 9) {
                        coord = player.getLocation().getBlockY();
                    } else if (args.length == 7 || args.length == 10) {
                        coord = player.getLocation().getBlockZ();
                    }
                    return filterCompletions(List.of(String.valueOf(coord)), args[args.length - 1]);
                }
                if (args.length == 11) {
                    return filterCompletions(List.of("100", "500", "1000", "5000"), args[10]);
                }
                break;
            case "stop":
                if (PermissionNode.has(player, "admin.stop") && args.length == 3) {
                    return filterCompletions(new ArrayList<>(plugin.getActiveSessions().keySet()), args[2]);
                }
                break;
            case "deletezone":
                if (PermissionNode.has(player, "admin.delete") && args.length == 3) {
                    return filterCompletions(new ArrayList<>(plugin.getCapturePoints().keySet()), args[2]);
                }
                break;
            case "reset":
                if (PermissionNode.has(player, "admin.reset") && args.length == 3) {
                    return filterCompletions(new ArrayList<>(plugin.getCapturePoints().keySet()), args[2]);
                }
                break;
            case "forcecapture":
                if (!PermissionNode.has(player, "admin.forcecapture")) {
                    break;
                }
                if (args.length == 3) {
                    return filterCompletions(new ArrayList<>(plugin.getCapturePoints().keySet()), args[2]);
                }
                if (args.length == 4) {
                    return filterCompletions(plugin.getForceCaptureTargets(), args[3]);
                }
                break;
            case "togglechat":
                if (PermissionNode.has(player, "admin.togglechat") && args.length == 3) {
                    return filterCompletions(List.of("on", "off"), args[2]);
                }
                break;
            case "zoneconfig":
                if (!PermissionNode.has(player, "admin.zoneconfig")) {
                    break;
                }
                if (args.length == 3) {
                    return filterCompletions(new ArrayList<>(plugin.getCapturePoints().keySet()), args[2]);
                }
                if (args.length == 4) {
                    return filterCompletions(List.of("set", "reset", "reload", "itemrewards", "items"), args[3]);
                }
                if (args.length == 5 && (args[3].equalsIgnoreCase("set") || args[3].equalsIgnoreCase("reset"))) {
                    String zoneId = args[2];
                    List<String> settingPaths = plugin.getZoneConfigManager().getAvailableSettingPaths(zoneId);
                    return filterCompletions(settingPaths, args[4]);
                }
                break;
            case "shop":
                if (args.length == 3) {
                    List<String> shopCommands = new ArrayList<>();
                    if (PermissionNode.has(player, "admin.shop")) {
                        shopCommands.add("edit");
                        shopCommands.add("reload");
                        shopCommands.add("enable");
                        shopCommands.add("disable");
                    }
                    if (PermissionNode.has(player, "admin.shop.restock")) {
                        shopCommands.add("restock");
                    }
                    return filterCompletions(shopCommands, args[2]);
                }
                if (args.length == 4) {
                    return filterCompletions(new ArrayList<>(plugin.getCapturePoints().keySet()), args[3]);
                }
                break;
            case "stats":
                if (PermissionNode.has(player, "admin.stats") && args.length == 3) {
                    return filterCompletions(List.of("remove", "reset"), args[2]);
                }
                break;
            case "koth":
                if (!PermissionNode.has(player, "admin.koth")) {
                    break;
                }
                if (args.length == 3) {
                    return filterCompletions(List.of("start", "stop", "status", "assign", "unassign", "zones"), args[2]);
                }
                if (args.length >= 4 && "start".equalsIgnoreCase(args[2])) {
                    return filterCompletions(getAssignedKothZoneCompletions(), args[args.length - 1]);
                }
                if (args.length == 4 && "stop".equalsIgnoreCase(args[2])) {
                    ArrayList<String> active = new ArrayList<>(plugin.getActiveKothZoneIds());
                    active.add("all");
                    return filterCompletions(active, args[3]);
                }
                if (args.length == 4 && "assign".equalsIgnoreCase(args[2])) {
                    return filterCompletions(new ArrayList<>(plugin.getCapturePoints().keySet()), args[3]);
                }
                if (args.length == 4 && ("unassign".equalsIgnoreCase(args[2]) || "remove".equalsIgnoreCase(args[2]))) {
                    ArrayList<String> configured = new ArrayList<>(plugin.getConfig().getStringList("koth.activation.zones"));
                    return filterCompletions(configured, args[3]);
                }
                break;
            case "conquest":
                if (!PermissionNode.has(player, "admin.conquest")) {
                    break;
                }
                if (args.length == 3) {
                    return filterCompletions(List.of("start", "stop", "status", "assign", "unassign", "zones"), args[2]);
                }
                if (args.length == 4 && ("assign".equalsIgnoreCase(args[2]) || "unassign".equalsIgnoreCase(args[2]) || "remove".equalsIgnoreCase(args[2]))) {
                    ArrayList<String> options = new ArrayList<>();
                    options.add(plugin.getConfig().getString("conquest.default-profile", "default"));
                    options.addAll(plugin.getCapturePoints().keySet());
                    return filterCompletions(options, args[3]);
                }
                if (args.length == 5 && ("assign".equalsIgnoreCase(args[2]) || "unassign".equalsIgnoreCase(args[2]) || "remove".equalsIgnoreCase(args[2]))) {
                    return filterCompletions(new ArrayList<>(plugin.getCapturePoints().keySet()), args[4]);
                }
                break;
            default:
                break;
        }

        return completions;
    }

    private List<String> filterCompletions(List<String> options, String input) {
        return options.stream()
            .filter(option -> option.toLowerCase().startsWith(input.toLowerCase()))
            .collect(Collectors.toList());
    }

    private List<String> getAssignedKothZoneCompletions() {
        ArrayList<String> configured = new ArrayList<>(plugin.getConfig().getStringList("koth.activation.zones"));
        ArrayList<String> result = new ArrayList<>();
        for (String rawZoneId : configured) {
            if (rawZoneId == null || rawZoneId.trim().isEmpty()) {
                continue;
            }
            CapturePoint point = plugin.getCapturePoint(rawZoneId.trim());
            if (point == null) {
                continue;
            }
            String zoneId = point.getId();
            if (!result.contains(zoneId)) {
                result.add(zoneId);
            }
        }
        return result;
    }
}

