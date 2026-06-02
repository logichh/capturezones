package com.logichh.capturezones;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StatisticsData {

    private Map<UUID, PlayerStats> playerStats = new HashMap<>();

    private Map<String, TownStats> townStats = new HashMap<>();

    private Map<String, ZoneStats> zoneStats = new HashMap<>();

    private ServerRecords serverRecords = new ServerRecords();

    private long lastReset = System.currentTimeMillis();
    
    public PlayerStats getPlayerStats(UUID playerId) {
        return playerStats.computeIfAbsent(playerId, k -> new PlayerStats());
    }
    
    public TownStats getTownStats(String townName) {
        return townStats.computeIfAbsent(townName, k -> new TownStats());
    }
    
    public ZoneStats getZoneStats(String zoneId) {
        return zoneStats.computeIfAbsent(zoneId, k -> new ZoneStats());
    }
    
    public ServerRecords getServerRecords() {
        return serverRecords;
    }
    
    public Map<UUID, PlayerStats> getAllPlayerStats() {
        return playerStats;
    }
    
    public Map<String, TownStats> getAllTownStats() {
        return townStats;
    }
    
    public Map<String, ZoneStats> getAllZoneStats() {
        return zoneStats;
    }
    
    public void removePlayerStats(UUID playerId) {
        playerStats.remove(playerId);
    }
    
    public void resetAllStats() {
        playerStats.clear();
        townStats.clear();
        zoneStats.clear();
        serverRecords = new ServerRecords();
        lastReset = System.currentTimeMillis();
    }
    
    public long getLastReset() {
        return lastReset;
    }
    public static class PlayerStats {
        public int totalCaptures = 0;
        public int failedCaptures = 0;
        public int killsInZones = 0;
        public int deathsInZones = 0;
        public int mobKills = 0;
        public long totalTimeInCaptures = 0; // milliseconds
        public int capturesParticipated = 0;
        public long fastestCapture = Long.MAX_VALUE; // milliseconds
        public long longestCapture = 0; // milliseconds
        public int currentWinStreak = 0;
        public int longestWinStreak = 0;
        
        public double getKDRatio() {
            return deathsInZones == 0 ? killsInZones : (double) killsInZones / deathsInZones;
        }
        
        public double getSuccessRate() {
            int total = totalCaptures + failedCaptures;
            return total == 0 ? 0 : (double) totalCaptures / total * 100;
        }
    }
    public static class TownStats {
        public int totalCaptures = 0;
        public int failedCaptures = 0;
        public long totalHoldTime = 0; // milliseconds
        public double totalRewardsEarned = 0.0;
        public int mobsKilled = 0;
        public int totalDeaths = 0;
        public int totalKills = 0;
        public int maxSimultaneousZones = 0;
        public int currentControlledZones = 0;
        public long longestControlStreak = 0; // milliseconds
        public Map<String, Integer> capturesPerZone = new HashMap<>();
        public Map<String, Long> holdTimePerZone = new HashMap<>();
        
        public double getSuccessRate() {
            int total = totalCaptures + failedCaptures;
            return total == 0 ? 0 : (double) totalCaptures / total * 100;
        }
        
        public double getAverageHoldTime() {
            return totalCaptures == 0 ? 0 : (double) totalHoldTime / totalCaptures;
        }
    }
    public static class ZoneStats {
        public int totalCaptures = 0;
        public int failedAttempts = 0;
        public long totalControlTime = 0; // milliseconds
        public double totalRewardsPaid = 0.0;
        public int totalDeaths = 0;
        public int totalMobKills = 0;
        public String mostFrequentController = "";
        public int controlChanges = 0;
        public long longestSingleControl = 0; // milliseconds
        public long fastestCapture = Long.MAX_VALUE; // milliseconds
        public long longestCapture = 0; // milliseconds
        public String currentController = "";
        public long currentControlStart = 0;
        
        public double getAverageCaptureTime() {
            return totalCaptures == 0 ? 0 : (double) (fastestCapture + longestCapture) / 2;
        }
    }
    public static class ServerRecords {
        public long firstCaptureTime = 0;
        public String firstCapturingTown = "";
        public String firstCapturingPlayer = "";
        public String fastestCaptureZone = "";
        public long fastestCaptureTime = Long.MAX_VALUE;
        public String longestCaptureZone = "";
        public long longestCaptureTime = 0;
        public String mostCapturedZone = "";
        public int mostCapturesCount = 0;
        public String mostDeadlyZone = "";
        public int mostDeaths = 0;
        public String dominantTown = "";
        public int dominantTownCaptures = 0;
        public String mostProfitableZone = "";
        public double mostProfitableReward = 0.0;
        public int totalServerCaptures = 0;
        public int totalServerDeaths = 0;
        public int totalServerMobKills = 0;
        public double totalServerEconomy = 0.0;
        public int maxSimultaneousCaptures = 0;
        public long totalServerPlaytime = 0;
    }
}

