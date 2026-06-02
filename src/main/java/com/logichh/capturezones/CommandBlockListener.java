package com.logichh.capturezones;

import com.logichh.capturezones.CapturePoint;
import com.logichh.capturezones.CaptureSession;
import com.logichh.capturezones.CaptureZones;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class CommandBlockListener
implements Listener {
    private static final long BLOCKED_COMMAND_CACHE_TTL_MS = 1000L;
    private final CaptureZones plugin;

    private volatile List<String> cachedBlockedCommands = Collections.emptyList();
    private volatile long blockedCommandCacheExpiresAt;

    public CommandBlockListener(CaptureZones plugin) {
        this.plugin = plugin;
    }

    @EventHandler

    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.isOp()) {
            return;
        }
        String rawMessage = event.getMessage();
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return;
        }
        String message = rawMessage.toLowerCase(Locale.ROOT);
        List<String> blockedCommands = this.getBlockedCommands();
        Location playerLocation = player.getLocation();
        ZoneConfigManager zoneConfigManager = this.plugin.getZoneConfigManager();
        int indexExtra = this.plugin.getSpatialIndexExtraChunks();
        for (CapturePoint point : this.plugin.getCandidateCapturePoints(playerLocation, indexExtra)) {
            boolean bufferEnabled = zoneConfigManager != null
                ? zoneConfigManager.getBoolean(point.getId(), "protection.buffer-zone.enabled", true)
                : true;
            int bufferSize = bufferEnabled && zoneConfigManager != null
                ? zoneConfigManager.getInt(point.getId(), "protection.buffer-zone.size", 1)
                : (bufferEnabled ? 1 : 0);

            boolean isInCaptureZone = this.plugin.isWithinZone(point, playerLocation);
            boolean isInBufferZone = bufferSize > 0 && this.plugin.isWithinZone(point, playerLocation, bufferSize);
            if (!isInCaptureZone && !isInBufferZone) continue;

            if (isInCaptureZone
                && this.plugin.isPointActive(point.getId())
                && getZoneBoolean(point.getId(), "capture.allow-commands", false)) {
                continue;
            }

            for (String blockedCommand : blockedCommands) {
                if (!message.startsWith(blockedCommand)) continue;
                event.setCancelled(true);
                String zone = isInCaptureZone ? "capture zone" : "buffer zone";
                plugin.sendNotification(player, Messages.get("messages.command-blocked", Map.of("zone", zone)));
                return;
            }
            if (!isInBufferZone || !isTownClaimCommand(message)) continue;
            CaptureSession activeSession = this.plugin.getActiveSession(point.getId());
            if (activeSession != null
                && !activeSession.isInPreparationPhase()
                && getZoneBoolean(point.getId(), "capture.capture.allow-claiming", false)) {
                continue;
            }
            event.setCancelled(true);
            plugin.sendNotification(player, Messages.get("messages.claiming-blocked"));
            return;
        }
    }

    private List<String> getBlockedCommands() {
        long now = System.currentTimeMillis();
        if (now < this.blockedCommandCacheExpiresAt) {
            return this.cachedBlockedCommands;
        }
        List<String> configured = this.plugin.getConfig().getStringList("blocked-commands");
        this.cachedBlockedCommands = normalizeBlockedCommands(configured);
        this.blockedCommandCacheExpiresAt = now + BLOCKED_COMMAND_CACHE_TTL_MS;
        return this.cachedBlockedCommands;
    }

    private List<String> normalizeBlockedCommands(List<String> configured) {
        if (configured == null || configured.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<>();
        for (String entry : configured) {
            if (entry == null) {
                continue;
            }
            String value = entry.trim().toLowerCase(Locale.ROOT);
            if (value.isEmpty()) {
                continue;
            }
            if (!value.startsWith("/")) {
                value = "/" + value;
            }
            normalized.add(value);
        }
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(normalized);
    }

    private boolean isTownClaimCommand(String message) {
        return message.startsWith("/t new")
            || message.startsWith("/t claim")
            || message.startsWith("/town new")
            || message.startsWith("/town claim");
    }

    private boolean getZoneBoolean(String zoneId, String path, boolean fallback) {
        ZoneConfigManager manager = this.plugin.getZoneConfigManager();
        if (manager != null) {
            return manager.getBoolean(zoneId, path, fallback);
        }
        return this.plugin.getConfig().getBoolean(path, fallback);
    }
}

