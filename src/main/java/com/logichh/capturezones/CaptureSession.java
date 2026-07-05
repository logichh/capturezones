package com.logichh.capturezones;

import com.logichh.capturezones.CapturePoint;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CaptureSession {
    private final String pointId;
    private final String townName;
    private final CaptureOwner owner;
    private final String playerName;
    private final long startTime;
    private boolean inPreparationPhase;
    private int remainingTime;
    private int remainingPreparationTime;
    private int initialPreparationTime;
    private int remainingCaptureTime;
    private int initialCaptureTime;
    private final CapturePoint point;
    private final Set<Player> players;
    private final Map<Player, Double> progress;
    private final Set<Player> warnedPlayers;
    private final Map<Location, BlockData> originalBlocks;
    private final Map<Player, Long> firstJoinTimes;
    private final Map<UUID, Integer> participationSeconds;
    private final Map<UUID, Integer> rewardKills;
    private final Set<Player> hiddenBossBars;
    private BossBar bossBar;
    private BukkitRunnable task;
    private boolean isActive;
    private final UUID initiatorUUID;
    private boolean contested;
    private long lastContestedActionbarAt;
    private long lastGraceActionbarAt;
    private boolean graceActive;
    private int graceSecondsRemaining;
    private int initialGraceSeconds;
    private double captureProgressAccumulator;

    public CaptureSession(CapturePoint point, String ownerName, Player player, int preparationTime, int captureTime) {
        this(point, CaptureOwner.fromDisplayName(CaptureOwnerType.TOWN, ownerName), player, preparationTime, captureTime);
    }

    public CaptureSession(CapturePoint point, CaptureOwner owner, Player player, int preparationTime, int captureTime) {
        this.pointId = point.getId();
        this.owner = owner;
        this.townName = owner != null && owner.getDisplayName() != null ? owner.getDisplayName() : "";
        this.playerName = player.getName();
        this.startTime = System.currentTimeMillis();
        this.inPreparationPhase = true;
        this.remainingPreparationTime = preparationTime * 60;
        this.initialPreparationTime = preparationTime * 60;
        this.remainingCaptureTime = captureTime * 60;
        this.initialCaptureTime = captureTime * 60;
        this.remainingTime = (preparationTime + captureTime) * 60;
        this.point = point;
        this.players = new HashSet<Player>();
        this.progress = new HashMap<Player, Double>();
        this.warnedPlayers = new HashSet<Player>();
        this.originalBlocks = new HashMap<Location, BlockData>();
        this.firstJoinTimes = new HashMap<Player, Long>();
        this.participationSeconds = new HashMap<UUID, Integer>();
        this.rewardKills = new HashMap<UUID, Integer>();
        this.hiddenBossBars = new HashSet<Player>();
        this.isActive = true;
        this.initiatorUUID = player.getUniqueId();
        this.participationSeconds.put(this.initiatorUUID, 0);
        this.contested = false;
        this.lastContestedActionbarAt = 0L;
        this.lastGraceActionbarAt = 0L;
        this.graceActive = false;
        this.graceSecondsRemaining = 0;
        this.initialGraceSeconds = 0;
        this.captureProgressAccumulator = 0.0;
    }

    public String getPointId() {
        return pointId;
    }

    public String getTownName() {
        return townName;
    }

    public CaptureOwner getOwner() {
        return owner;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getStartTime() {
        return startTime;
    }

    public boolean isInPreparationPhase() {
        return inPreparationPhase;
    }

    public void setInPreparationPhase(boolean inPreparationPhase) {
        this.inPreparationPhase = inPreparationPhase;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public void setRemainingTime(int remainingTime) {
        this.remainingTime = remainingTime;
    }

    public void stop() {
        if (this.task != null && !this.task.isCancelled()) {
            this.task.cancel();
        }
        if (this.bossBar != null) {
            this.bossBar.removeAll();
        }
        this.players.clear();
        this.progress.clear();
        this.warnedPlayers.clear();
        this.originalBlocks.clear();
        this.firstJoinTimes.clear();
        this.participationSeconds.clear();
        this.rewardKills.clear();
        this.hiddenBossBars.clear();
        this.isActive = false;
    }

    public CapturePoint getPoint() {
        return this.point;
    }

    public Set<Player> getPlayers() {
        return this.players;
    }

    public Map<Player, Double> getProgress() {
        return this.progress;
    }

    public Set<Player> getWarnedPlayers() {
        return this.warnedPlayers;
    }

    public Map<Location, BlockData> getOriginalBlocks() {
        return this.originalBlocks;
    }

    public Map<Player, Long> getFirstJoinTimes() {
        return this.firstJoinTimes;
    }

    public Set<Player> getHiddenBossBars() {
        return this.hiddenBossBars;
    }

    public BossBar getBossBar() {
        return this.bossBar;
    }

    public void setBossBar(BossBar bossBar) {
        this.bossBar = bossBar;
    }

    public BukkitRunnable getTask() {
        return this.task;
    }

    public void setTask(BukkitRunnable task) {
        this.task = task;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public int getRemainingPreparationTime() {
        return this.remainingPreparationTime;
    }

    public int getInitialPreparationTime() {
        return this.initialPreparationTime;
    }

    public int getRemainingCaptureTime() {
        return this.remainingCaptureTime;
    }
    
    public void setRemainingCaptureTime(int time) {
        this.remainingCaptureTime = time;
    }

    public int getInitialCaptureTime() {
        return this.initialCaptureTime;
    }

    public UUID getInitiatorUUID() {
        return this.initiatorUUID;
    }

    public void recordParticipation(Player player) {
        if (player == null) {
            return;
        }
        this.participationSeconds.merge(player.getUniqueId(), 1, Integer::sum);
    }

    public void recordRewardKill(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        this.participationSeconds.putIfAbsent(playerId, 0);
        this.rewardKills.merge(playerId, 1, Integer::sum);
    }

    public Map<UUID, Integer> getParticipationSeconds() {
        return new HashMap<>(this.participationSeconds);
    }

    public Map<UUID, Integer> getRewardKills() {
        return new HashMap<>(this.rewardKills);
    }

    public void decrementPreparationTime() {
        if (this.remainingPreparationTime > 0) {
            this.remainingPreparationTime--;
            this.remainingTime--;
        }
    }

    public void decrementCaptureTime() {
        decrementCaptureTimeBy(1);
    }

    public void decrementCaptureTimeBy(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        if (safeSeconds <= 0 || this.remainingCaptureTime <= 0) {
            return;
        }
        int applied = Math.min(safeSeconds, this.remainingCaptureTime);
        this.remainingCaptureTime -= applied;
        this.remainingTime = Math.max(0, this.remainingTime - applied);
    }

    public int consumeCaptureProgress(double progressUnits) {
        if (progressUnits <= 0.0 || this.remainingCaptureTime <= 0) {
            return 0;
        }
        this.captureProgressAccumulator += progressUnits;
        int wholeSeconds = (int) Math.floor(this.captureProgressAccumulator);
        if (wholeSeconds <= 0) {
            return 0;
        }
        this.captureProgressAccumulator -= wholeSeconds;
        return wholeSeconds;
    }

    public void startCapturePhase() {
        this.inPreparationPhase = false;
    }

    public boolean isContested() {
        return this.contested;
    }

    public void setContested(boolean contested) {
        this.contested = contested;
    }

    public long getLastContestedActionbarAt() {
        return this.lastContestedActionbarAt;
    }

    public void setLastContestedActionbarAt(long lastContestedActionbarAt) {
        this.lastContestedActionbarAt = lastContestedActionbarAt;
    }

    public long getLastGraceActionbarAt() {
        return this.lastGraceActionbarAt;
    }

    public void setLastGraceActionbarAt(long lastGraceActionbarAt) {
        this.lastGraceActionbarAt = lastGraceActionbarAt;
    }

    public boolean isGraceActive() {
        return this.graceActive;
    }

    public void startGrace(int durationSeconds) {
        int clampedDuration = Math.max(0, durationSeconds);
        this.graceActive = true;
        this.initialGraceSeconds = clampedDuration;
        this.graceSecondsRemaining = clampedDuration;
    }

    public void clearGrace() {
        this.graceActive = false;
        this.graceSecondsRemaining = 0;
        this.initialGraceSeconds = 0;
    }

    public void decrementGraceSeconds() {
        if (this.graceSecondsRemaining > 0) {
            this.graceSecondsRemaining--;
        }
    }

    public int getGraceSecondsRemaining() {
        return this.graceSecondsRemaining;
    }

    public int getInitialGraceSeconds() {
        return this.initialGraceSeconds;
    }
}

