package com.logichh.capturezones;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.entity.Player;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class StatisticsManager {
    
    private final CaptureZones plugin;
    private final Logger logger;
    private final Gson gson;
    private final File statsFile;
    private StatisticsData data;
    
    private final Map<String, Long> activeCaptureStarts = new HashMap<>();

    private final Map<String, Long> zoneControlStarts = new HashMap<>();
    private static final int[] CAPTURE_MILESTONE_THRESHOLDS = {10, 25, 50, 100, 250, 500, 1000};
    
    public StatisticsManager(CaptureZones plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.statsFile = new File(plugin.getDataFolder(), "statistics.json");
        this.data = new StatisticsData();
        
        loadStatistics();
    }
    
    public void loadStatistics() {
        if (!statsFile.exists()) {
            logger.info("No statistics file found, starting fresh.");
            return;
        }
        
        try (Reader reader = new FileReader(statsFile)) {
            data = gson.fromJson(reader, StatisticsData.class);
            if (data == null) {
                data = new StatisticsData();
            }
            logger.info("Statistics loaded successfully.");
        } catch (Exception e) {
            logger.severe("Failed to load statistics: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
            data = new StatisticsData();
        }
    }
    
    public void saveStatistics() {
        try {
            if (!statsFile.exists()) {
                statsFile.getParentFile().mkdirs();
                statsFile.createNewFile();
            }
            
            try (Writer writer = new FileWriter(statsFile)) {
                gson.toJson(data, writer);
            }
            
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                logger.info("Statistics saved successfully.");
            }
        } catch (Exception e) {
            logger.severe("Failed to save statistics: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
        }
    }
    
    public void onCaptureStart(String zoneId, String townName, UUID playerId) {
        activeCaptureStarts.put(zoneId, System.currentTimeMillis());
        
        StatisticsData.PlayerStats playerStats = data.getPlayerStats(playerId);
        playerStats.capturesParticipated++;
        
        if (data.getServerRecords().firstCaptureTime == 0) {
            data.getServerRecords().firstCaptureTime = System.currentTimeMillis();
            data.getServerRecords().firstCapturingTown = townName;
            data.getServerRecords().firstCapturingPlayer = plugin.getServer().getOfflinePlayer(playerId).getName();
        }
    }

    public void onCaptureComplete(String zoneId, String zoneName, String townName, UUID playerId, Set<UUID> participants) {
        long captureTime = System.currentTimeMillis() - activeCaptureStarts.getOrDefault(zoneId, System.currentTimeMillis());
        activeCaptureStarts.remove(zoneId);
        DiscordWebhook webhook = plugin.getDiscordWebhook();
        String safeZoneName = zoneName != null && !zoneName.isEmpty() ? zoneName : zoneId;
        String safeTownName = townName != null && !townName.isEmpty() ? townName : "Unknown";
        String safePlayerName = resolvePlayerName(playerId);
        
        StatisticsData.PlayerStats mainPlayerStats = data.getPlayerStats(playerId);
        mainPlayerStats.totalCaptures++;
        mainPlayerStats.currentWinStreak++;
        if (mainPlayerStats.currentWinStreak > mainPlayerStats.longestWinStreak) {
            mainPlayerStats.longestWinStreak = mainPlayerStats.currentWinStreak;
        }
        
        if (captureTime < mainPlayerStats.fastestCapture) {
            mainPlayerStats.fastestCapture = captureTime;
        }
        if (captureTime > mainPlayerStats.longestCapture) {
            mainPlayerStats.longestCapture = captureTime;
        }
        
        for (UUID participantId : participants) {
            StatisticsData.PlayerStats participantStats = data.getPlayerStats(participantId);
            participantStats.totalTimeInCaptures += captureTime / participants.size();
        }
        
        StatisticsData.TownStats townStats = data.getTownStats(safeTownName);
        townStats.totalCaptures++;
        townStats.currentControlledZones++;
        if (townStats.currentControlledZones > townStats.maxSimultaneousZones) {
            townStats.maxSimultaneousZones = townStats.currentControlledZones;
        }
        townStats.capturesPerZone.merge(zoneId, 1, Integer::sum);
        
        StatisticsData.ZoneStats zoneStats = data.getZoneStats(zoneId);
        zoneStats.totalCaptures++;
        zoneStats.controlChanges++;
        
        if (!zoneStats.currentController.isEmpty() && !zoneStats.currentController.equals(safeTownName)) {
            long controlDuration = System.currentTimeMillis() - zoneStats.currentControlStart;
            StatisticsData.TownStats previousTown = data.getTownStats(zoneStats.currentController);
            previousTown.totalHoldTime += controlDuration;
            previousTown.holdTimePerZone.merge(zoneId, controlDuration, Long::sum);
            
            if (controlDuration > previousTown.longestControlStreak) {
                previousTown.longestControlStreak = controlDuration;
            }
            if (controlDuration > zoneStats.longestSingleControl) {
                zoneStats.longestSingleControl = controlDuration;
            }
        }
        
        zoneStats.currentController = safeTownName;
        zoneStats.currentControlStart = System.currentTimeMillis();
        zoneControlStarts.put(zoneId, System.currentTimeMillis());
        
        if (captureTime < zoneStats.fastestCapture) {
            zoneStats.fastestCapture = captureTime;
        }
        if (captureTime > zoneStats.longestCapture) {
            zoneStats.longestCapture = captureTime;
        }
        
        StatisticsData.ServerRecords records = data.getServerRecords();
        records.totalServerCaptures++;
        maybeSendCaptureMilestone(webhook, "Server", "Server", records.totalServerCaptures);

        if (captureTime < records.fastestCaptureTime) {
            records.fastestCaptureTime = captureTime;
            records.fastestCaptureZone = safeZoneName;
            sendNewRecord(
                webhook,
                "Fastest Capture Time",
                safeZoneName,
                formatDuration(captureTime)
            );
        }
        if (captureTime > records.longestCaptureTime) {
            records.longestCaptureTime = captureTime;
            records.longestCaptureZone = safeZoneName;
            sendNewRecord(
                webhook,
                "Longest Capture Time",
                safeZoneName,
                formatDuration(captureTime)
            );
        }
        
        if (zoneStats.totalCaptures > records.mostCapturesCount) {
            records.mostCapturesCount = zoneStats.totalCaptures;
            records.mostCapturedZone = safeZoneName;
            sendNewRecord(
                webhook,
                "Most Captured Zone",
                safeZoneName,
                String.valueOf(zoneStats.totalCaptures)
            );
        }
        
        if (townStats.totalCaptures > records.dominantTownCaptures) {
            records.dominantTownCaptures = townStats.totalCaptures;
            records.dominantTown = safeTownName;
            sendNewRecord(
                webhook,
                "Dominant Town Captures",
                safeTownName,
                String.valueOf(townStats.totalCaptures)
            );
        }

        maybeSendCaptureMilestone(webhook, safeTownName, "Town", townStats.totalCaptures);
        maybeSendCaptureMilestone(webhook, safeZoneName, "Zone", zoneStats.totalCaptures);
        maybeSendCaptureMilestone(webhook, safePlayerName, "Player", mainPlayerStats.totalCaptures);
    }
    
    public void onCaptureFailed(String zoneId, String townName, UUID playerId) {
        activeCaptureStarts.remove(zoneId);
        
        StatisticsData.PlayerStats playerStats = data.getPlayerStats(playerId);
        playerStats.failedCaptures++;
        playerStats.currentWinStreak = 0;
        
        StatisticsData.TownStats townStats = data.getTownStats(townName);
        townStats.failedCaptures++;
        
        StatisticsData.ZoneStats zoneStats = data.getZoneStats(zoneId);
        zoneStats.failedAttempts++;
    }
    
    public void onPlayerKillInZone(UUID killerId, UUID victimId, String zoneId) {
        DiscordWebhook webhook = plugin.getDiscordWebhook();
        StatisticsData.PlayerStats killerStats = data.getPlayerStats(killerId);
        killerStats.killsInZones++;
        
        StatisticsData.PlayerStats victimStats = data.getPlayerStats(victimId);
        victimStats.deathsInZones++;
        
        Player killer = plugin.getServer().getPlayer(killerId);
        Player victim = plugin.getServer().getPlayer(victimId);

        String killerTown = resolveOwnerName(killer);
        if (killerTown != null) {
            data.getTownStats(killerTown).totalKills++;
        }

        String victimTown = resolveOwnerName(victim);
        if (victimTown != null) {
            data.getTownStats(victimTown).totalDeaths++;
        }
        
        StatisticsData.ZoneStats zoneStats = data.getZoneStats(zoneId);
        zoneStats.totalDeaths++;

        data.getServerRecords().totalServerDeaths++;
        
        if (zoneStats.totalDeaths > data.getServerRecords().mostDeaths) {
            data.getServerRecords().mostDeaths = zoneStats.totalDeaths;
            data.getServerRecords().mostDeadlyZone = zoneId;
            sendNewRecord(
                webhook,
                "Most Deaths in Zone",
                zoneId,
                String.valueOf(zoneStats.totalDeaths)
            );
        }
    }
    
    public void onMobKillInZone(UUID playerId, String zoneId) {
        StatisticsData.PlayerStats playerStats = data.getPlayerStats(playerId);
        playerStats.mobKills++;

        Player player = plugin.getServer().getPlayer(playerId);
        String townName = resolveOwnerName(player);
        if (townName != null) {
            data.getTownStats(townName).mobsKilled++;
        }
        
        StatisticsData.ZoneStats zoneStats = data.getZoneStats(zoneId);
        zoneStats.totalMobKills++;
        
        data.getServerRecords().totalServerMobKills++;
    }
    
    public void onRewardDistributed(String zoneId, String zoneName, String townName, double amount) {
        DiscordWebhook webhook = plugin.getDiscordWebhook();
        StatisticsData.TownStats townStats = data.getTownStats(townName);
        townStats.totalRewardsEarned += amount;
        
        StatisticsData.ZoneStats zoneStats = data.getZoneStats(zoneId);
        zoneStats.totalRewardsPaid += amount;
        
        StatisticsData.ServerRecords records = data.getServerRecords();
        records.totalServerEconomy += amount;
        
        if (zoneStats.totalRewardsPaid > records.mostProfitableReward) {
            records.mostProfitableReward = zoneStats.totalRewardsPaid;
            records.mostProfitableZone = zoneName;
            String safeZoneName = zoneName != null && !zoneName.isEmpty() ? zoneName : zoneId;
            sendNewRecord(
                webhook,
                "Most Profitable Zone",
                safeZoneName,
                String.format(Locale.US, "%.2f", zoneStats.totalRewardsPaid)
            );
        }
    }
    
    public List<Map.Entry<String, Integer>> getTopTownsByCaptures(int limit) {
        return data.getAllTownStats().entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue().totalCaptures, e1.getValue().totalCaptures))
            .limit(limit)
            .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().totalCaptures))
            .collect(Collectors.toList());
    }
    
    public List<Map.Entry<UUID, Integer>> getTopPlayersByKills(int limit) {
        return data.getAllPlayerStats().entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue().killsInZones, e1.getValue().killsInZones))
            .limit(limit)
            .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().killsInZones))
            .collect(Collectors.toList());
    }
    
    public List<Map.Entry<String, Long>> getTopTownsByHoldTime(int limit) {
        return data.getAllTownStats().entrySet().stream()
            .sorted((e1, e2) -> Long.compare(e2.getValue().totalHoldTime, e1.getValue().totalHoldTime))
            .limit(limit)
            .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().totalHoldTime))
            .collect(Collectors.toList());
    }
    
    public List<Map.Entry<String, Double>> getTopTownsByRewards(int limit) {
        return data.getAllTownStats().entrySet().stream()
            .sorted((e1, e2) -> Double.compare(e2.getValue().totalRewardsEarned, e1.getValue().totalRewardsEarned))
            .limit(limit)
            .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().totalRewardsEarned))
            .collect(Collectors.toList());
    }
    
    public List<Map.Entry<String, Integer>> getTopTownsByMobKills(int limit) {
        return data.getAllTownStats().entrySet().stream()
            .sorted((e1, e2) -> Integer.compare(e2.getValue().mobsKilled, e1.getValue().mobsKilled))
            .limit(limit)
            .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().mobsKilled))
            .collect(Collectors.toList());
    }
    
    public List<Map.Entry<UUID, Double>> getTopPlayersByKDRatio(int limit) {
        return data.getAllPlayerStats().entrySet().stream()
            .filter(e -> e.getValue().deathsInZones > 0 || e.getValue().killsInZones > 0)
            .sorted((e1, e2) -> Double.compare(e2.getValue().getKDRatio(), e1.getValue().getKDRatio()))
            .limit(limit)
            .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue().getKDRatio()))
            .collect(Collectors.toList());
    }
    
    public StatisticsData getData() {
        return data;
    }

    public void removePlayerStats(UUID playerId) {
        data.removePlayerStats(playerId);
        saveStatistics();
    }

    public void resetAllStats() {
        data.resetAllStats();
        activeCaptureStarts.clear();
        zoneControlStarts.clear();
        saveStatistics();
    }

    private void sendNewRecord(DiscordWebhook webhook, String recordType, String holder, String value) {
        if (webhook == null) {
            return;
        }
        String safeRecordType = recordType == null || recordType.isEmpty() ? "Record" : recordType;
        String safeHolder = holder == null || holder.isEmpty() ? "Unknown" : holder;
        String safeValue = value == null || value.isEmpty() ? "0" : value;
        webhook.sendNewRecord(safeRecordType, safeHolder, safeValue);
    }

    private void maybeSendCaptureMilestone(DiscordWebhook webhook, String entityName, String entityType, int captures) {
        if (webhook == null || captures <= 0 || !isCaptureMilestone(captures)) {
            return;
        }
        String safeEntityName = entityName == null || entityName.isEmpty() ? "Unknown" : entityName;
        String safeEntityType = entityType == null || entityType.isEmpty() ? "Entity" : entityType;
        webhook.sendMilestone(safeEntityName, safeEntityType, captures + " captures");
    }

    private boolean isCaptureMilestone(int captures) {
        for (int threshold : CAPTURE_MILESTONE_THRESHOLDS) {
            if (captures == threshold) {
                return true;
            }
        }
        return false;
    }

    private String resolvePlayerName(UUID playerId) {
        if (playerId == null) {
            return "Unknown";
        }
        String playerName = plugin.getServer().getOfflinePlayer(playerId).getName();
        if (playerName == null || playerName.isEmpty()) {
            return playerId.toString();
        }
        return playerName;
    }

    private String formatDuration(long millis) {
        double seconds = millis / 1000.0;
        return String.format(Locale.US, "%.1fs", seconds);
    }

    private String resolveOwnerName(Player player) {
        if (player == null) {
            return null;
        }
        OwnerPlatformAdapter adapter = plugin.getOwnerPlatform();
        if (adapter == null) {
            return null;
        }
        return adapter.resolveOwnerName(player, plugin.getDefaultOwnerType());
    }
}

