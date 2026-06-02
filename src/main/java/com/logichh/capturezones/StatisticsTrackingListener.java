package com.logichh.capturezones;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class StatisticsTrackingListener implements Listener {
    
    private final CaptureZones plugin;
    private final StatisticsManager statsManager;
    
    public StatisticsTrackingListener(CaptureZones plugin) {
        this.plugin = plugin;
        this.statsManager = plugin.getStatisticsManager();
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)

    public void onEntityDeath(EntityDeathEvent event) {
        if (statsManager == null) return;
        
        LivingEntity victim = event.getEntity();
        Entity killerEntity = victim.getKiller();
        String zoneId = findZoneAtLocation(victim.getLocation());
        if (zoneId == null) return;
        if (victim instanceof Player && killerEntity instanceof Player) {
            Player killerPlayer = (Player) killerEntity;
            Player victimPlayer = (Player) victim;
            
            statsManager.onPlayerKillInZone(
                killerPlayer.getUniqueId(), 
                victimPlayer.getUniqueId(), 
                zoneId
            );
        }
        else if (!(victim instanceof Player) && killerEntity instanceof Player) {
            Player killerPlayer = (Player) killerEntity;
            statsManager.onMobKillInZone(killerPlayer.getUniqueId(), zoneId);
        }
    }

    private String findZoneAtLocation(org.bukkit.Location location) {
        for (CapturePoint point : plugin.getCandidateCapturePoints(location, 0)) {
            if (plugin.isWithinZone(point, location)) {
                return point.getId();
            }
        }
        return null;
    }
}

