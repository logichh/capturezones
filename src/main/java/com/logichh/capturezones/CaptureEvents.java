package com.logichh.capturezones;

import com.logichh.capturezones.CapturePoint;
import com.logichh.capturezones.CaptureSession;
import com.logichh.capturezones.CaptureOwner;
import com.logichh.capturezones.CaptureZones;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CaptureEvents
implements Listener {
    private CaptureZones plugin;

    private Map<UUID, CapturePoint> playerZones = new HashMap<UUID, CapturePoint>();

    private Map<UUID, Integer> actionBarTasks = new HashMap<UUID, Integer>();

    private final Map<String, Long> autoCaptureEntryAttempts = new HashMap<>();

    public CaptureEvents(CaptureZones plugin) {
        this.plugin = plugin;
    }

    @EventHandler

    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();
        Location from = event.getFrom();

        if (from != null && to != null && 
            from.getBlockX() == to.getBlockX() && 
            from.getBlockY() == to.getBlockY() && 
            from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        if (to == null) {
            return;
        }

        List<CapturePoint> toCandidates = this.plugin.getCandidateCapturePoints(to, 1);
        for (CapturePoint point : toCandidates) {
            handleZoneMovement(player, from, to, point);
        }

        Set<String> relevantPointIds = new LinkedHashSet<>();
        for (CapturePoint point : toCandidates) {
            if (point != null && point.getId() != null) {
                relevantPointIds.add(point.getId());
            }
        }

        if (from != null) {
            List<CapturePoint> fromCandidates = this.plugin.getCandidateCapturePoints(from, 1);
            if (fromCandidates != toCandidates) {
                for (CapturePoint point : fromCandidates) {
                    if (point != null && point.getId() != null) {
                        relevantPointIds.add(point.getId());
                    }
                    if (toCandidates.contains(point)) {
                        continue;
                    }
                    handleZoneMovement(player, from, to, point);
                }
            }
        }

        if (from != null && (from.getBlockX() >> 4 != to.getBlockX() >> 4 || from.getBlockZ() >> 4 != to.getBlockZ() >> 4)) {
            this.plugin.refreshBossBarAudienceForPlayer(player, from, to);
        }

        this.cancelOwnerCapturesIfPlayerExitedZone(player, from, to, relevantPointIds);
    }

    private void handleZoneMovement(Player player, Location from, Location to, CapturePoint point) {
        if (point == null || to == null) {
            return;
        }

        boolean wasInZone = from != null && this.plugin.isWithinZone(point, from);
        boolean isInZone = this.plugin.isWithinZone(point, to);
        boolean wasInBuffer = from != null && this.plugin.isWithinZone(point, from, 1);
        boolean isInBuffer = this.plugin.isWithinZone(point, to, 1);

        if (!wasInBuffer && isInBuffer) {
            this.plugin.sendNotification(player, Messages.get("messages.zone.approaching", Map.of("zone", point.getName())));
        }

        if (!wasInZone && isInZone) {
            this.plugin.sendNotification(player, Messages.get("messages.zone.entered", Map.of("zone", point.getName())));
            this.startContinuousActionBar(player, point);
            this.attemptAutoCaptureOnEntry(player, point, to);
            return;
        }

        if (wasInZone && !isInZone) {
            this.plugin.sendNotification(player, Messages.get("messages.zone.left", Map.of("zone", point.getName())));
            this.stopContinuousActionBar(player);
        }
    }

    @EventHandler

    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        this.stopContinuousActionBar(player);
        this.playerZones.remove(player.getUniqueId());
        String autoCapturePrefix = player.getUniqueId().toString() + ":";
        this.autoCaptureEntryAttempts.keySet().removeIf(key -> key.startsWith(autoCapturePrefix));
        List<String> sessionsToCancel = new ArrayList<>();

        for (Map.Entry<String, CaptureSession> entry : new ArrayList<>(this.plugin.getActiveSessions().entrySet())) {
            CaptureSession session = entry.getValue();
            String pointId = entry.getKey();
            if (session == null || pointId == null) {
                continue;
            }

            session.getPlayers().remove(player);
            if (!this.isPlayerPartOfSessionOwner(player, session)) {
                continue;
            }
            if (this.shouldCancelSessionWhenOwnerLeaves(pointId, session)) {
                sessionsToCancel.add(pointId);
            }
        }
        for (String pointId : sessionsToCancel) {
            this.plugin.cancelCapture(pointId, "All participants disconnected");
        }

        this.plugin.removePlayerFromAllBossBars(player);
        this.plugin.stopBoundaryVisualizationsForPlayer(player.getUniqueId());
    }

    private void attemptAutoCaptureOnEntry(Player player, CapturePoint point, Location entryLocation) {
        if (player == null || point == null) {
            return;
        }
        String pointId = point.getId();
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }
        if (!this.plugin.isAutoCaptureOnEntryEnabled(pointId)) {
            return;
        }

        long now = System.currentTimeMillis();
        int debounceSeconds = this.plugin.getAutoCaptureEntryDebounceSeconds(pointId);
        long debounceMillis = Math.max(0L, debounceSeconds) * 1000L;
        String key = this.autoCaptureKey(player.getUniqueId(), pointId);
        Long lastAttempt = this.autoCaptureEntryAttempts.get(key);
        if (lastAttempt != null && debounceMillis > 0L && (now - lastAttempt) < debounceMillis) {
            return;
        }
        this.autoCaptureEntryAttempts.put(key, now);

        if (this.plugin.isPointActive(pointId)) {
            return;
        }

        Location triggerLocation = entryLocation != null ? entryLocation : player.getLocation();
        if (this.plugin.startCapture(player, pointId, triggerLocation)) {
            this.plugin.sendNotification(player, Messages.get("messages.capture.auto-started", Map.of("point", point.getName())));
        }
    }

    private String autoCaptureKey(UUID playerId, String pointId) {
        return playerId + ":" + pointId;
    }

    private void startContinuousActionBar(Player player, CapturePoint zone) {
        this.playerZones.put(player.getUniqueId(), zone);
        this.stopContinuousActionBar(player);
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, () -> {
            String message;
            if (!player.isOnline()) {
                this.stopContinuousActionBar(player);
                return;
            }
            if (!this.plugin.isWithinZone(zone, player.getLocation())) {
                this.stopContinuousActionBar(player);
                return;
            }
            CaptureSession session = this.plugin.getActiveSessions().get(zone.getId());
            if (session != null) {
                message = Messages.get("messages.zone.continuous-capturing", Map.of("town", session.getTownName(), "zone", zone.getName()));
            } else {
                message = Messages.get("messages.zone.continuous", Map.of("zone", zone.getName()));
            }
            this.sendActionBarMessage(player, message);
        }, 0L, 20L);
        this.actionBarTasks.put(player.getUniqueId(), taskId);
    }

    private void stopContinuousActionBar(Player player) {
        Integer taskId = this.actionBarTasks.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId.intValue());
        }
    }

    private void sendActionBarMessage(Player player, String message) {
        this.plugin.sendActionBarMessage(player, message);
    }

    private void cancelOwnerCapturesIfPlayerExitedZone(Player player, Location from, Location to, Set<String> relevantPointIds) {
        if (player == null || to == null) {
            return;
        }
        if (relevantPointIds == null || relevantPointIds.isEmpty()) {
            return;
        }

        List<String> sessionsToCancel = new ArrayList<>();
        for (String pointId : relevantPointIds) {
            CaptureSession session = this.plugin.getActiveSession(pointId);
            if (pointId == null || session == null || !session.isActive()) {
                continue;
            }

            CapturePoint point = session.getPoint();
            if (point == null || !this.isPlayerPartOfSessionOwner(player, session)) {
                continue;
            }

            boolean wasInside = from != null && this.plugin.isWithinZone(point, from);
            if (!wasInside || this.plugin.isWithinZone(point, to)) {
                continue;
            }

            if (this.shouldCancelSessionWhenOwnerLeaves(pointId, session)) {
                sessionsToCancel.add(pointId);
            }
        }

        for (String pointId : sessionsToCancel) {
            this.plugin.cancelCapture(pointId, this.plugin.buildMovedTooFarReason(player));
            this.plugin.sendNotification(player, Messages.get("errors.moved-too-far"));
        }
    }

    private boolean shouldCancelSessionWhenOwnerLeaves(String pointId, CaptureSession session) {
        if (pointId == null || session == null) {
            return false;
        }
        if (this.hasAnyCapturingOwnerPlayerInZone(session)) {
            return false;
        }
        return session.isInPreparationPhase() || !this.plugin.isGraceTimerEnabled(pointId);
    }

    private boolean hasAnyCapturingOwnerPlayerInZone(CaptureSession session) {
        if (session == null) {
            return false;
        }

        CapturePoint point = session.getPoint();
        if (point == null) {
            return false;
        }
        Location center = point.getLocation();
        if (center == null || center.getWorld() == null) {
            return false;
        }

        for (Player online : center.getWorld().getPlayers()) {
            if (online == null || !online.isOnline() || online.isDead()) {
                continue;
            }
            if (!this.plugin.isWithinZone(point, online.getLocation())) {
                continue;
            }
            if (this.isPlayerPartOfSessionOwner(online, session)) {
                return true;
            }
        }
        return false;
    }

    private boolean isPlayerPartOfSessionOwner(Player player, CaptureSession session) {
        if (player == null || session == null) {
            return false;
        }

        CaptureOwner owner = session.getOwner();
        if (owner != null) {
            return this.plugin.doesPlayerMatchOwner(player, owner);
        }
        String ownerName = session.getTownName();
        return ownerName != null && !ownerName.isEmpty() && this.plugin.doesPlayerMatchOwner(player, ownerName);
    }
}

