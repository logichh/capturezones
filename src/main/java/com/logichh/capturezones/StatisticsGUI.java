package com.logichh.capturezones;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class StatisticsGUI {
    
    private final CaptureZones plugin;
    private final StatisticsManager statsManager;

    private final Map<UUID, MenuSession> activeSessions = new HashMap<>();

    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    private final Map<UUID, Long> commandCooldowns = new HashMap<>();
    private static final Material MAIN_BORDER_MATERIAL = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
    private static final Material SUB_BORDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material FILL_MATERIAL = Material.BLACK_STAINED_GLASS_PANE;
    private static final int BACK_SLOT = 49;
    private static final int INFO_SLOT = 49;
    private static final int HEADER_LEFT_SLOT = 2;
    private static final int HEADER_RIGHT_SLOT = 6;
    private static final int HEADER_CENTER_SLOT = 4;
    private static final int[] LEFT_GRID_SLOTS = {
        10, 11, 12,
        19, 20, 21,
        28, 29, 30,
        37, 38, 39
    };
    private static final int[] RIGHT_GRID_SLOTS = {
        14, 15, 16,
        23, 24, 25,
        32, 33, 34,
        41, 42, 43
    };
    
    public StatisticsGUI(CaptureZones plugin, StatisticsManager statsManager) {
        this.plugin = plugin;
        this.statsManager = statsManager;
    }

    public boolean canUseStatsCommand(Player player) {
        if (PermissionNode.has(player, "admin.stats.nocooldown")) {
            return true;
        }
        long cooldownMs = getCommandCooldownMs();
        if (cooldownMs <= 0) {
            return true;
        }
        
        Long lastUse = commandCooldowns.get(player.getUniqueId());
        if (lastUse == null) {
            return true;
        }
        
        long elapsed = System.currentTimeMillis() - lastUse;
        return elapsed >= cooldownMs;
    }

    public long getCooldownSeconds(Player player) {
        long cooldownMs = getCommandCooldownMs();
        if (cooldownMs <= 0) {
            return 0;
        }
        Long lastUse = commandCooldowns.get(player.getUniqueId());
        if (lastUse == null) {
            return 0;
        }
        
        long elapsed = System.currentTimeMillis() - lastUse;
        long remaining = cooldownMs - elapsed;
        return remaining > 0 ? TimeUnit.MILLISECONDS.toSeconds(remaining) : 0;
    }

    public void setCooldown(Player player) {
        if (!PermissionNode.has(player, "admin.stats.nocooldown") && getCommandCooldownMs() > 0) {
            commandCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    private long getCommandCooldownMs() {
        int cooldownSeconds = plugin.getConfig().getInt("statistics.command-cooldown", 300);
        if (cooldownSeconds <= 0) {
            return 0L;
        }
        return TimeUnit.SECONDS.toMillis(cooldownSeconds);
    }

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Messages.get("gui.stats.main.title"));
        applyFrame(inv, MAIN_BORDER_MATERIAL, FILL_MATERIAL);

        StatisticsData.ServerRecords records = statsManager.getData().getServerRecords();
        inv.setItem(HEADER_CENTER_SLOT, createStatItem(Material.NETHER_STAR,
            Messages.get("gui.stats.snapshot.title"),
            Messages.get("gui.stats.snapshot.captures", Map.of("count", String.valueOf(records.totalServerCaptures))),
            Messages.get("gui.stats.snapshot.deaths", Map.of("count", String.valueOf(records.totalServerDeaths))),
            Messages.get("gui.stats.snapshot.economy", Map.of("amount", String.format("%.2f", records.totalServerEconomy)))
        ));

        inv.setItem(20, createCategoryItem(Material.BEACON, 
            Messages.get("gui.stats.category.captures.name"),
            Messages.getList("gui.stats.category.captures.lore")
        ));
        
        inv.setItem(22, createCategoryItem(Material.DIAMOND_SWORD, 
            Messages.get("gui.stats.category.combat.name"),
            Messages.getList("gui.stats.category.combat.lore")
        ));
        
        inv.setItem(24, createCategoryItem(Material.SHIELD, 
            Messages.get("gui.stats.category.control.name"),
            Messages.getList("gui.stats.category.control.lore")
        ));
        
        inv.setItem(29, createCategoryItem(Material.EMERALD, 
            Messages.get("gui.stats.category.economy.name"),
            Messages.getList("gui.stats.category.economy.lore")
        ));
        
        inv.setItem(31, createCategoryItem(Material.CLOCK, 
            Messages.get("gui.stats.category.activity.name"),
            Messages.getList("gui.stats.category.activity.lore")
        ));
        
        inv.setItem(33, createCategoryItem(Material.BOOK, 
            Messages.get("gui.stats.category.records.name"),
            Messages.getList("gui.stats.category.records.lore")
        ));
        
        inv.setItem(INFO_SLOT, createInfoItem());
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.MAIN, 0));
        activeInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void openCapturesMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, Messages.get("gui.stats.captures.title"));
        applyFrame(inv, SUB_BORDER_MATERIAL, FILL_MATERIAL);
        
        inv.setItem(BACK_SLOT, createBackButton());
        inv.setItem(HEADER_CENTER_SLOT, createHeaderItem(Material.BEACON, Messages.get("gui.stats.header.captures")));
        inv.setItem(HEADER_LEFT_SLOT, createHeaderItem(Material.WHITE_BANNER, Messages.get("gui.stats.header.top-towns")));
        inv.setItem(HEADER_RIGHT_SLOT, createHeaderItem(Material.PLAYER_HEAD, Messages.get("gui.stats.header.top-players")));
        
        List<Map.Entry<String, Integer>> topTowns = statsManager.getTopTownsByCaptures(10);
        for (int i = 0; i < topTowns.size() && i < LEFT_GRID_SLOTS.length; i++) {
            Map.Entry<String, Integer> entry = topTowns.get(i);
            inv.setItem(LEFT_GRID_SLOTS[i], createTownStatItem(
                i + 1,
                entry.getKey(),
                Messages.get("gui.stats.captures.town.captures", Map.of("count", String.valueOf(entry.getValue())))
            ));
        }
        
        List<Map.Entry<UUID, StatisticsData.PlayerStats>> topPlayers = 
            statsManager.getData().getAllPlayerStats().entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().totalCaptures, e1.getValue().totalCaptures))
                .limit(10)
                .toList();
        
        for (int i = 0; i < topPlayers.size() && i < RIGHT_GRID_SLOTS.length; i++) {
            Map.Entry<UUID, StatisticsData.PlayerStats> entry = topPlayers.get(i);
            inv.setItem(RIGHT_GRID_SLOTS[i], createPlayerStatItem(
                i + 1,
                entry.getKey(),
                Messages.get("gui.stats.captures.player.captures", Map.of("count", String.valueOf(entry.getValue().totalCaptures))),
                Messages.get("gui.stats.captures.player.failed", Map.of("count", String.valueOf(entry.getValue().failedCaptures))),
                Messages.get("gui.stats.captures.player.success-rate", Map.of(
                    "rate", String.format("%.1f%%", entry.getValue().getSuccessRate())
                ))
            ));
        }
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.CAPTURES, page));
        activeInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void openCombatMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, Messages.get("gui.stats.combat.title"));
        applyFrame(inv, SUB_BORDER_MATERIAL, FILL_MATERIAL);
        
        inv.setItem(BACK_SLOT, createBackButton());
        inv.setItem(HEADER_CENTER_SLOT, createHeaderItem(Material.DIAMOND_SWORD, Messages.get("gui.stats.header.combat")));
        inv.setItem(HEADER_LEFT_SLOT, createHeaderItem(Material.IRON_SWORD, Messages.get("gui.stats.header.most-kills")));
        inv.setItem(HEADER_RIGHT_SLOT, createHeaderItem(Material.DIAMOND_SWORD, Messages.get("gui.stats.header.best-kd")));
        
        List<Map.Entry<UUID, Integer>> topKillers = statsManager.getTopPlayersByKills(10);
        for (int i = 0; i < topKillers.size() && i < LEFT_GRID_SLOTS.length; i++) {
            Map.Entry<UUID, Integer> entry = topKillers.get(i);
            StatisticsData.PlayerStats stats = statsManager.getData().getPlayerStats(entry.getKey());
            inv.setItem(LEFT_GRID_SLOTS[i], createPlayerStatItem(
                i + 1,
                entry.getKey(),
                Messages.get("gui.stats.combat.player.kills", Map.of("count", String.valueOf(entry.getValue()))),
                Messages.get("gui.stats.combat.player.deaths", Map.of("count", String.valueOf(stats.deathsInZones))),
                Messages.get("gui.stats.combat.player.kd", Map.of("ratio", String.format("%.2f", stats.getKDRatio())))
            ));
        }
        
        List<Map.Entry<UUID, Double>> topKD = statsManager.getTopPlayersByKDRatio(10);
        for (int i = 0; i < topKD.size() && i < RIGHT_GRID_SLOTS.length; i++) {
            Map.Entry<UUID, Double> entry = topKD.get(i);
            StatisticsData.PlayerStats stats = statsManager.getData().getPlayerStats(entry.getKey());
            inv.setItem(RIGHT_GRID_SLOTS[i], createPlayerStatItem(
                i + 1,
                entry.getKey(),
                Messages.get("gui.stats.combat.player.kd", Map.of("ratio", String.format("%.2f", entry.getValue()))),
                Messages.get("gui.stats.combat.player.kills", Map.of("count", String.valueOf(stats.killsInZones))),
                Messages.get("gui.stats.combat.player.deaths", Map.of("count", String.valueOf(stats.deathsInZones)))
            ));
        }
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.COMBAT, page));
        activeInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void openControlMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, Messages.get("gui.stats.control.title"));
        applyFrame(inv, SUB_BORDER_MATERIAL, FILL_MATERIAL);
        
        inv.setItem(BACK_SLOT, createBackButton());
        inv.setItem(HEADER_CENTER_SLOT, createHeaderItem(Material.SHIELD, Messages.get("gui.stats.header.top-hold-times")));
        
        List<Map.Entry<String, Long>> topTowns = statsManager.getTopTownsByHoldTime(10);
        for (int i = 0; i < topTowns.size() && i < LEFT_GRID_SLOTS.length; i++) {
            Map.Entry<String, Long> entry = topTowns.get(i);
            StatisticsData.TownStats stats = statsManager.getData().getTownStats(entry.getKey());
            inv.setItem(LEFT_GRID_SLOTS[i], createTownStatItem(
                i + 1,
                entry.getKey(),
                Messages.get("gui.stats.control.town.hold-time", Map.of("duration", formatDuration(entry.getValue()))),
                Messages.get("gui.stats.control.town.current-zones", Map.of("count", String.valueOf(stats.currentControlledZones))),
                Messages.get("gui.stats.control.town.max-zones", Map.of("count", String.valueOf(stats.maxSimultaneousZones)))
            ));
        }
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.CONTROL, page));
        activeInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void openEconomyMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, Messages.get("gui.stats.economy.title"));
        applyFrame(inv, SUB_BORDER_MATERIAL, FILL_MATERIAL);
        
        inv.setItem(BACK_SLOT, createBackButton());
        
        double totalEconomy = statsManager.getData().getServerRecords().totalServerEconomy;
        inv.setItem(HEADER_CENTER_SLOT, createStatItem(Material.EMERALD_BLOCK, 
            Messages.get("gui.stats.economy.total.title"),
            Messages.get("gui.stats.economy.total.amount", Map.of("amount", String.format("%.2f", totalEconomy))),
            Messages.get("gui.stats.economy.total.subtitle")
        ));
        
        List<Map.Entry<String, Double>> topTowns = statsManager.getTopTownsByRewards(10);
        for (int i = 0; i < topTowns.size() && i < LEFT_GRID_SLOTS.length; i++) {
            Map.Entry<String, Double> entry = topTowns.get(i);
            inv.setItem(LEFT_GRID_SLOTS[i], createTownStatItem(
                i + 1,
                entry.getKey(),
                Messages.get("gui.stats.economy.town.rewards", Map.of("amount", String.format("%.2f", entry.getValue())))
            ));
        }
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.ECONOMY, page));
        activeInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void openActivityMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, Messages.get("gui.stats.activity.title"));
        applyFrame(inv, SUB_BORDER_MATERIAL, FILL_MATERIAL);
        
        inv.setItem(BACK_SLOT, createBackButton());
        inv.setItem(HEADER_CENTER_SLOT, createHeaderItem(Material.CLOCK, Messages.get("gui.stats.header.top-mob-kills")));
        
        List<Map.Entry<String, Integer>> topTowns = statsManager.getTopTownsByMobKills(10);
        for (int i = 0; i < topTowns.size() && i < LEFT_GRID_SLOTS.length; i++) {
            Map.Entry<String, Integer> entry = topTowns.get(i);
            inv.setItem(LEFT_GRID_SLOTS[i], createTownStatItem(
                i + 1,
                entry.getKey(),
                Messages.get("gui.stats.activity.town.mob-kills", Map.of("count", String.valueOf(entry.getValue())))
            ));
        }
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.ACTIVITY, page));
        activeInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void openRecordsMenu(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, Messages.get("gui.stats.records.title"));
        applyFrame(inv, SUB_BORDER_MATERIAL, FILL_MATERIAL);
        
        inv.setItem(BACK_SLOT, createBackButton());
        inv.setItem(HEADER_CENTER_SLOT, createHeaderItem(Material.BOOK, Messages.get("gui.stats.header.server-records")));
        
        StatisticsData.ServerRecords records = statsManager.getData().getServerRecords();
        inv.setItem(10, createStatItem(Material.FEATHER,
            Messages.get("gui.stats.records.fastest.title"),
            Messages.get("gui.stats.records.fastest.time", Map.of("duration", formatDuration(records.fastestCaptureTime))),
            Messages.get("gui.stats.records.zone", Map.of("zone", String.valueOf(records.fastestCaptureZone)))
        ));
        inv.setItem(12, createStatItem(Material.TURTLE_HELMET,
            Messages.get("gui.stats.records.longest.title"),
            Messages.get("gui.stats.records.longest.time", Map.of("duration", formatDuration(records.longestCaptureTime))),
            Messages.get("gui.stats.records.zone", Map.of("zone", String.valueOf(records.longestCaptureZone)))
        ));
        inv.setItem(14, createStatItem(Material.TARGET,
            Messages.get("gui.stats.records.most-captured.title"),
            Messages.get("gui.stats.records.most-captured.zone", Map.of("zone", String.valueOf(records.mostCapturedZone))),
            Messages.get("gui.stats.records.captures", Map.of("count", String.valueOf(records.mostCapturesCount)))
        ));
        inv.setItem(16, createStatItem(Material.GOLD_BLOCK,
            Messages.get("gui.stats.records.most-profitable.title"),
            Messages.get("gui.stats.records.most-profitable.zone", Map.of("zone", String.valueOf(records.mostProfitableZone))),
            Messages.get("gui.stats.records.total", Map.of("amount", String.format("%.2f", records.mostProfitableReward)))
        ));
        inv.setItem(20, createStatItem(Material.WHITE_BANNER,
            Messages.get("gui.stats.records.dominant-town.title"),
            Messages.get("gui.stats.records.dominant-town.town", Map.of("town", String.valueOf(records.dominantTown))),
            Messages.get("gui.stats.records.captures", Map.of("count", String.valueOf(records.dominantTownCaptures)))
        ));
        inv.setItem(22, createStatItem(Material.NETHER_STAR,
            Messages.get("gui.stats.records.first-capture.title"),
            records.firstCaptureTime > 0
                ? Messages.get("gui.stats.records.first-capture.date", Map.of(
                    "date", new Date(records.firstCaptureTime).toString()))
                : Messages.get("gui.stats.records.first-capture.none"),
            Messages.get("gui.stats.records.town", Map.of("town", String.valueOf(records.firstCapturingTown))),
            Messages.get("gui.stats.records.player", Map.of("player", String.valueOf(records.firstCapturingPlayer)))
        ));
        inv.setItem(24, createStatItem(Material.SKELETON_SKULL,
            Messages.get("gui.stats.records.most-deadly.title"),
            Messages.get("gui.stats.records.most-deadly.zone", Map.of("zone", String.valueOf(records.mostDeadlyZone))),
            Messages.get("gui.stats.records.deaths", Map.of("count", String.valueOf(records.mostDeaths)))
        ));
        inv.setItem(31, createStatItem(Material.DIAMOND,
            Messages.get("gui.stats.records.server-totals.title"),
            Messages.get("gui.stats.records.server.captures", Map.of("count", String.valueOf(records.totalServerCaptures))),
            Messages.get("gui.stats.records.server.deaths", Map.of("count", String.valueOf(records.totalServerDeaths))),
            Messages.get("gui.stats.records.server.mob-kills", Map.of("count", String.valueOf(records.totalServerMobKills))),
            Messages.get("gui.stats.records.server.economy", Map.of("amount", String.format("%.2f", records.totalServerEconomy)))
        ));
        
        activeSessions.put(player.getUniqueId(), new MenuSession(MenuType.RECORDS, page));
        activeInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    public void handleClick(Player player, int slot, Inventory inv) {
        MenuSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;
        
        switch (session.type) {
            case MAIN:
                handleMainMenuClick(player, slot);
                break;
            case CAPTURES:
            case COMBAT:
            case CONTROL:
            case ECONOMY:
            case ACTIVITY:
            case RECORDS:
                if (slot == BACK_SLOT) {
                    player.closeInventory();
                    player.performCommand("cap stats");
                }
                break;
        }
    }
    
    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 20: openCapturesMenu(player, 0); break;
            case 22: openCombatMenu(player, 0); break;
            case 24: openControlMenu(player, 0); break;
            case 29: openEconomyMenu(player, 0); break;
            case 31: openActivityMenu(player, 0); break;
            case 33: openRecordsMenu(player, 0); break;
        }
    }

    public void closeMenu(Player player) {
        activeSessions.remove(player.getUniqueId());
        activeInventories.remove(player.getUniqueId());
    }

    public boolean isStatsView(Player player, InventoryView view) {
        if (player == null || view == null) {
            return false;
        }
        Inventory current = activeInventories.get(player.getUniqueId());
        if (current != null && current.equals(view.getTopInventory())) {
            return true;
        }
        return isStatsTitle(view.getTitle());
    }

    private boolean isStatsTitle(String title) {
        if (title == null) {
            return false;
        }
        String plainTitle = ChatColor.stripColor(title);
        return plainTitle.equals(ChatColor.stripColor(Messages.get("gui.stats.main.title"))) ||
            plainTitle.equals(ChatColor.stripColor(Messages.get("gui.stats.captures.title"))) ||
            plainTitle.equals(ChatColor.stripColor(Messages.get("gui.stats.combat.title"))) ||
            plainTitle.equals(ChatColor.stripColor(Messages.get("gui.stats.control.title"))) ||
            plainTitle.equals(ChatColor.stripColor(Messages.get("gui.stats.economy.title"))) ||
            plainTitle.equals(ChatColor.stripColor(Messages.get("gui.stats.activity.title"))) ||
            plainTitle.equals(ChatColor.stripColor(Messages.get("gui.stats.records.title")));
    }
    
    private void applyFrame(Inventory inv, Material borderMaterial, Material fillMaterial) {
        ItemStack border = createFiller(borderMaterial);
        ItemStack fill = createFiller(fillMaterial);
        int size = inv.getSize();
        int rows = size / 9;

        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                setIfEmpty(inv, slot, border);
            }
        }

        for (int slot = 0; slot < size; slot++) {
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, fill);
            }
        }
    }

    private void setIfEmpty(Inventory inv, int slot, ItemStack item) {
        if (inv.getItem(slot) == null) {
            inv.setItem(slot, item);
        }
    }

    private ItemStack createFiller(Material material) {
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        return filler;
    }
    
    private ItemStack createCategoryItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createHeaderItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createTownStatItem(int rank, String townName, String... stats) {
        ItemStack item = new ItemStack(Material.WHITE_BANNER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Messages.get("gui.stats.rank.town", Map.of(
                "rank", String.valueOf(rank),
                "town", String.valueOf(townName)
            )));
            meta.setLore(Arrays.asList(stats));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createPlayerStatItem(int rank, UUID playerId, String... stats) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(offlinePlayer);
            String playerName = offlinePlayer.getName() != null ? 
                offlinePlayer.getName() : 
                Messages.get("gui.stats.player.unknown");
            meta.setDisplayName(Messages.get("gui.stats.rank.player", Map.of(
                "rank", String.valueOf(rank),
                "player", playerName
            )));
            meta.setLore(Arrays.asList(stats));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createStatItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Messages.get("gui.stats.back.title"));
            meta.setLore(Messages.getList("gui.stats.back.lore"));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Messages.get("gui.stats.info.title"));
            meta.setLore(Messages.getList("gui.stats.info.lore"));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private String formatDuration(long millis) {
        if (millis == 0) return Messages.get("gui.stats.duration.na");
        
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        long hours = TimeUnit.MILLISECONDS.toHours(millis) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        
        if (days > 0) {
            return Messages.get("gui.stats.duration.days-hours-minutes", Map.of(
                "days", String.valueOf(days),
                "hours", String.valueOf(hours),
                "minutes", String.valueOf(minutes)
            ));
        } else if (hours > 0) {
            return Messages.get("gui.stats.duration.hours-minutes", Map.of(
                "hours", String.valueOf(hours),
                "minutes", String.valueOf(minutes)
            ));
        } else {
            return Messages.get("gui.stats.duration.minutes", Map.of(
                "minutes", String.valueOf(minutes)
            ));
        }
    }
    
    private static class MenuSession {
        final MenuType type;
        final int page;
        
        MenuSession(MenuType type, int page) {
            this.type = type;
            this.page = page;
        }
    }
    
    private enum MenuType {
        MAIN, CAPTURES, COMBAT, CONTROL, ECONOMY, ACTIVITY, RECORDS
    }
}

