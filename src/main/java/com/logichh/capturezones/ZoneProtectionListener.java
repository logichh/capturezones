package com.logichh.capturezones;

import com.logichh.capturezones.CapturePoint;
import com.logichh.capturezones.CaptureSession;
import com.logichh.capturezones.CaptureZones;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class ZoneProtectionListener implements Listener {
    private final CaptureZones plugin;

    public ZoneProtectionListener(CaptureZones plugin) {
        this.plugin = plugin;
    }

    @EventHandler

    public void onBlockBreak(BlockBreakEvent event) {
        if (event == null || event.getPlayer() == null || event.getBlock() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getBlock().getLocation());
        if (zoneId != null) {
            if (isRuleAllowedDuringCapturePhase(zoneId, "capture.capture.allow-block-break", false)) {
                return;
            }
            boolean preventBreak = plugin.getZoneConfigManager().getBoolean(zoneId, "protection.prevent-block-break", true);
            if (preventBreak) {
                event.setCancelled(true);
                plugin.sendNotification(event.getPlayer(), Messages.get("protection.block-break-blocked"));
            }
        }
    }

    @EventHandler

    public void onBlockPlace(BlockPlaceEvent event) {
        if (event == null || event.getPlayer() == null || event.getBlock() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getBlock().getLocation());
        if (zoneId != null) {
            if (isRuleAllowedDuringCapturePhase(zoneId, "capture.capture.allow-block-place", false)) {
                return;
            }
            boolean preventPlace = plugin.getZoneConfigManager().getBoolean(zoneId, "protection.prevent-block-place", true);
            if (preventPlace) {
                event.setCancelled(true);
                plugin.sendNotification(event.getPlayer(), Messages.get("protection.block-place-blocked"));
            }
        }
    }

    @EventHandler

    public void onEntityExplode(EntityExplodeEvent event) {
        if (event == null || event.getLocation() == null) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getLocation());
        if (zoneId != null) {
            if (isRuleAllowedDuringCapturePhase(zoneId, "capture.capture.allow-explosions", false)) {
                return;
            }
            boolean preventExplosions = plugin.getZoneConfigManager().getBoolean(zoneId, "protection.prevent-explosions", true);
            if (preventExplosions) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler

    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event == null || event.getEntity() == null || event.getDamager() == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getEntity().getLocation());
        if (zoneId != null) {
            if (isCaptureInPreparationPhase(zoneId)
                && getZoneBoolean(zoneId, "capture.preparation.allow-pvp", true)) {
                return;
            }
            if (isRuleAllowedDuringCapturePhase(zoneId, "capture.capture.allow-pvp", true)) {
                return;
            }
            boolean preventPvp = plugin.getZoneConfigManager().getBoolean(zoneId, "protection.prevent-pvp", false);
            if (preventPvp) {
                event.setCancelled(true);
                plugin.sendNotification((Player) event.getDamager(), Messages.get("protection.pvp-blocked"));
            }
        }
    }

    @EventHandler

    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getPlayer().getLocation());
        if (zoneId != null) {
            if (isRuleAllowedDuringCapturePhase(zoneId, "capture.capture.allow-item-use", true)) {
                return;
            }
            boolean preventItemUse = plugin.getZoneConfigManager().getBoolean(zoneId, "protection.prevent-item-use", true);
            if (preventItemUse) {
                Material itemType = event.getItem() != null ? event.getItem().getType() : null;
                if (itemType == Material.ENDER_PEARL) {
                    boolean allowEnderPearls = plugin.getZoneConfigManager().getBoolean(zoneId, "protection.allow-ender-pearls", false);
                    if (allowEnderPearls) {
                        return;
                    }
                } else if (itemType == Material.CHORUS_FRUIT) {
                    boolean allowChorusFruit = plugin.getZoneConfigManager().getBoolean(zoneId, "protection.allow-chorus-fruit", false);
                    if (allowChorusFruit) {
                        return;
                    }
                }
                event.setCancelled(true);
                plugin.sendNotification(event.getPlayer(), Messages.get("protection.item-use-blocked"));
            }
        }
    }

    @EventHandler

    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event == null || event.getPlayer() == null || event.getTo() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getTo());
        if (zoneId != null) {
            if (isRuleAllowedDuringCapturePhase(zoneId, "capture.capture.allow-teleport", false)) {
                return;
            }
            boolean preventTeleporting = plugin.getZoneConfigManager().getBoolean(zoneId, "protection.prevent-teleporting", true);
            if (preventTeleporting) {
                PlayerTeleportEvent.TeleportCause cause = event.getCause();
                if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
                    boolean allowEnderPearls = plugin.getZoneConfigManager().getBoolean(zoneId, "protection.allow-ender-pearls", false);
                    if (allowEnderPearls) {
                        return;
                    }
                } else if (cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
                    boolean allowChorusFruit = plugin.getZoneConfigManager().getBoolean(zoneId, "protection.allow-chorus-fruit", false);
                    if (allowChorusFruit) {
                        return;
                    }
                }
                event.setCancelled(true);
                plugin.sendNotification(event.getPlayer(), Messages.get("protection.teleport-blocked"));
            }
        }
    }

    @EventHandler

    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event == null || event.getPlayer() == null || event.getMessage() == null) {
            return;
        }
        if (event.getPlayer().isOp()) {
            return;
        }
        String zoneId = getZoneIdAtLocation(event.getPlayer().getLocation());
        if (zoneId != null) {
            if (plugin.isPointActive(zoneId) && getZoneBoolean(zoneId, "capture.allow-commands", false)) {
                return;
            }
            String command = event.getMessage().toLowerCase();
            boolean homeCommand = command.startsWith("/home")
                || command.startsWith("/homes")
                || command.startsWith("/sethome")
                || command.startsWith("/delhome");
            boolean claimCommand = command.startsWith("/t claim")
                || command.startsWith("/town claim")
                || command.startsWith("/n claim")
                || command.startsWith("/nation claim");

            if (homeCommand && isRuleAllowedDuringCapturePhase(zoneId, "capture.capture.allow-homes", false)) {
                return;
            }
            boolean preventHomes = plugin.getZoneConfigManager().getBoolean(zoneId, "protection.prevent-homes", true);
            if (preventHomes && homeCommand) {
                event.setCancelled(true);
                plugin.sendNotification(event.getPlayer(), Messages.get("protection.home-blocked"));
                return;
            }

            if (claimCommand && isRuleAllowedDuringCapturePhase(zoneId, "capture.capture.allow-claiming", false)) {
                return;
            }
            boolean preventClaiming = plugin.getZoneConfigManager().getBoolean(zoneId, "protection.prevent-claiming", true);
            if (preventClaiming && claimCommand) {
                event.setCancelled(true);
                plugin.sendNotification(event.getPlayer(), Messages.get("protection.claiming-blocked"));
            }
        }
    }

    @EventHandler

    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Allow all plugin/spawner/egg/custom spawns
        if (event == null || event.getLocation() == null || event.getEntity() == null) {
            return;
        }
        
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        
        // Only block natural world spawning (NATURAL, DEFAULT)
        // Allow: CUSTOM (plugin spawns like our reinforcements), SPAWNER (mob spawners), 
        //        EGG (spawn eggs), BUILD (builds), etc.
        if (reason == CreatureSpawnEvent.SpawnReason.NATURAL || 
            reason == CreatureSpawnEvent.SpawnReason.DEFAULT) {
            
            // Prevent natural mob spawning in capture zones
            if (this.isInProtectedZone(event.getLocation())) {
                event.setCancelled(true);
            }
        }
        // All other spawn reasons (including CUSTOM for reinforcement mobs) are allowed
    }

    private String getZoneIdAtLocation(Location location) {
        int indexExtra = this.plugin.getSpatialIndexExtraChunks();
        for (CapturePoint point : this.plugin.getCandidateCapturePoints(location, indexExtra)) {
            // Get buffer size from zone-specific config
            boolean bufferEnabled = plugin.getZoneConfigManager().getBoolean(point.getId(), "protection.buffer-zone.enabled", true);
            int bufferSize = bufferEnabled ? plugin.getZoneConfigManager().getInt(point.getId(), "protection.buffer-zone.size", 1) : 0;

            if (this.plugin.isWithinZone(point, location, bufferSize)) {
                return point.getId();
            }
        }
        return null;
    }
    
    /**
     * @deprecated Use getZoneIdAtLocation instead for per-zone config support
     */
    @Deprecated

    private boolean isInProtectedZone(Location location) {
        return getZoneIdAtLocation(location) != null;
    }

    private boolean getZoneBoolean(String zoneId, String path, boolean fallback) {
        ZoneConfigManager zoneConfigManager = plugin.getZoneConfigManager();
        if (zoneConfigManager != null) {
            return zoneConfigManager.getBoolean(zoneId, path, fallback);
        }
        return plugin.getConfig().getBoolean(path, fallback);
    }

    private boolean isCaptureInPreparationPhase(String zoneId) {
        CaptureSession session = plugin.getActiveSession(zoneId);
        return session != null && session.isInPreparationPhase();
    }

    private boolean isRuleAllowedDuringCapturePhase(String zoneId, String path, boolean fallback) {
        CaptureSession session = plugin.getActiveSession(zoneId);
        if (session == null || session.isInPreparationPhase()) {
            return false;
        }
        return getZoneBoolean(zoneId, path, fallback);
    }
}

