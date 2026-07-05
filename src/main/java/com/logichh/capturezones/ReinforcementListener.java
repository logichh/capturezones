package com.logichh.capturezones;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class ReinforcementListener implements Listener {
    private static final String META_REINFORCEMENT = "capture_reinforcement";
    private static final String META_SOURCE = "reinforcement_source";
    private static final String META_MYTHIC_TYPE = "reinforcement_mythic_type";

    private enum TargetingMode {
        CAPTURING_OWNER_ONLY,
        OPPOSING_OWNER_ONLY,
        ANY_PLAYER_IN_ZONE;

        private static TargetingMode fromConfigValue(String rawValue) {
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return CAPTURING_OWNER_ONLY;
            }
            try {
                return TargetingMode.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return CAPTURING_OWNER_ONLY;
            }
        }
    }

    private enum SpawnYMode {
        SURFACE,
        ZONE_CENTER,
        ORIGIN_PLAYER,
        CLAMPED;

        private static SpawnYMode fromConfigValue(String rawValue) {
            if (rawValue == null || rawValue.trim().isEmpty()) {
                return SURFACE;
            }
            try {
                return SpawnYMode.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return SURFACE;
            }
        }
    }

    private final CaptureZones plugin;

    private final Map<String, List<UUID>> activeReinforcements = new HashMap<>();

    private final Map<String, BukkitTask> phaseTasks = new HashMap<>();

    private final Map<String, BukkitTask> trackingTasks = new HashMap<>();

    private final Map<String, Integer> currentPhase = new HashMap<>();

    private final Deque<SpawnRequest> spawnQueue = new ArrayDeque<>();

    private final Map<String, Integer> pendingSpawns = new HashMap<>();

    private final Random random = new Random();
    private BukkitTask spawnProcessorTask;
    private MobSpawner mobSpawner;

    public ReinforcementListener(CaptureZones plugin) {
        this.plugin = plugin;
    }
    
    public void setMobSpawner(MobSpawner mobSpawner) {
        this.mobSpawner = mobSpawner;
        plugin.getLogger().info("Reinforcement mob spawner set to: " + mobSpawner.getName());
    }

    @EventHandler

    public void onEntityTarget(EntityTargetEvent event) {
        // Apply configurable reinforcement targeting policy.
        if (!event.getEntity().hasMetadata(META_REINFORCEMENT)) {
            return;
        }

        if (!(event.getTarget() instanceof Player)) {
            event.setCancelled(true);
            return;
        }

        String pointId = event.getEntity().getMetadata(META_REINFORCEMENT).get(0).asString();
        CapturePoint point = plugin.getCapturePoint(pointId);

        if (point == null) {
            event.setCancelled(true);
            return;
        }

        CaptureSession initialSession = plugin.getActiveSession(pointId);
        CaptureOwner capturingOwner = resolveCapturingOwner(point, initialSession);
        Player target = (Player) event.getTarget();
        if (!isTargetAllowed(pointId, point, capturingOwner, target)) {
            event.setCancelled(true);
        }
    }

    @EventHandler

    public void onEntityDeath(EntityDeathEvent event) {
        if (!event.getEntity().hasMetadata(META_REINFORCEMENT)) {
            return;
        }

        event.getDrops().clear();

        String pointId = event.getEntity().getMetadata(META_REINFORCEMENT).get(0).asString();
        CaptureSession session = plugin.getActiveSession(pointId);

        if (session != null && session.isActive() && !session.isInPreparationPhase()) {
            Player killer = event.getEntity().getKiller();
            if (killer != null) {
                plugin.recordCaptureRewardKill(killer, event.getEntity().getLocation());
            }
            int secondsReduced = getTimerReductionSeconds(pointId, event.getEntity());
            if (secondsReduced > 0) {
                int currentTime = session.getRemainingCaptureTime();
                int newTime = Math.max(0, currentTime - secondsReduced); // Don't go below 0
                session.setRemainingCaptureTime(newTime);

                // Notify the killer if it's a player
                if (killer != null) {
                    plugin.sendNotification(killer, Messages.get("messages.reinforcement.timer-reduced", Map.of(
                        "seconds", String.valueOf(secondsReduced)
                    )));
                }
                
                checkAndSpawnPhase(pointId);
            }
        }

        List<UUID> reinforcements = activeReinforcements.get(pointId);
        if (reinforcements != null) {
            reinforcements.remove(event.getEntity().getUniqueId());
        }
    }
    
    private void checkAndSpawnPhase(String pointId) {
        CaptureSession session = plugin.getActiveSession(pointId);
        if (session == null || !session.isActive()) {
            return;
        }
        
        int timeLeft = session.getRemainingCaptureTime();
        
        if (timeLeft <= getStopSpawningUnderSeconds(pointId)) {
            return;
        }
        
        // Calculate elapsed time and expected phase
        int initialTime = session.getInitialCaptureTime();
        int elapsed = initialTime - timeLeft;
        int intervalSeconds = getWaveIntervalSeconds(pointId);
        int expectedPhase = (elapsed / intervalSeconds) + 1;
        int currentP = currentPhase.getOrDefault(pointId, 1);
        
        // If we've skipped phases due to kill rewards, spawn them now
        if (expectedPhase > currentP) {
            CapturePoint point = plugin.getCapturePoint(pointId);
            if (point != null) {
                for (int phase = currentP + 1; phase <= expectedPhase; phase++) {
                    currentPhase.put(pointId, phase);
                    spawnReinforcementWave(pointId, point, phase);
                }
            }
        }
    }

    private int getWaveIntervalSeconds(String pointId) {
        int interval = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.wave-interval", 30);
        return Math.max(1, interval);
    }

    private int getStopSpawningUnderSeconds(String pointId) {
        int threshold = plugin.getZoneConfigManager().getInt(
            pointId,
            "reinforcements.stop-spawning-under-seconds",
            60
        );
        return Math.max(0, threshold);
    }

    private int getBaseMobsPerWave(String pointId) {
        int base = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.mobs-per-wave", 1);
        return Math.max(0, base);
    }

    private int getWavePhaseIncrease(String pointId) {
        int increase = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.wave-phase-increase", 1);
        return Math.max(0, increase);
    }

    private int getMaxMobsPerWave(String pointId) {
        int maxPerWave = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.max-mobs-per-wave", 12);
        return Math.max(1, maxPerWave);
    }

    private int calculateMobsForPhase(String pointId, int phase) {
        int safePhase = Math.max(1, phase);
        int baseMobs = getBaseMobsPerWave(pointId);
        int phaseIncrease = getWavePhaseIncrease(pointId);
        long calculated = (long) baseMobs + ((long) safePhase * phaseIncrease);
        int cappedForInt = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, calculated));
        return Math.min(cappedForInt, getMaxMobsPerWave(pointId));
    }

    private int getMaxMobsPerPoint(String pointId) {
        int max = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.max-mobs-per-point", 50);
        return Math.max(1, max);
    }

    private TargetingMode getTargetingMode(String pointId) {
        String rawMode = plugin.getZoneConfigManager().getString(
            pointId,
            "reinforcements.targeting.mode",
            TargetingMode.CAPTURING_OWNER_ONLY.name()
        );
        return TargetingMode.fromConfigValue(rawMode);
    }

    private int getTargetSearchExtraChunks(String pointId) {
        int value = plugin.getZoneConfigManager().getInt(
            pointId,
            "reinforcements.targeting.search-extra-chunks",
            4
        );
        return Math.max(0, value);
    }

    private long getTargetRetargetIntervalTicks(String pointId) {
        int value = plugin.getZoneConfigManager().getInt(
            pointId,
            "reinforcements.targeting.retarget-interval-ticks",
            20
        );
        return Math.max(1L, (long) value);
    }

    private double getSpawnMinDistance(String pointId) {
        double value = plugin.getZoneConfigManager().getDouble(
            pointId,
            "reinforcements.spawn-location.min-distance",
            3.0
        );
        return Math.max(0.0, value);
    }

    private double getSpawnMaxDistance(String pointId) {
        double minDistance = getSpawnMinDistance(pointId);
        double value = plugin.getZoneConfigManager().getDouble(
            pointId,
            "reinforcements.spawn-location.max-distance",
            11.0
        );
        return Math.max(minDistance, value);
    }

    private double getSpawnSurfaceOffset(String pointId) {
        return plugin.getZoneConfigManager().getDouble(
            pointId,
            "reinforcements.spawn-location.surface-offset",
            1.0
        );
    }

    private SpawnYMode getSpawnYMode(String pointId) {
        String rawMode = plugin.getZoneConfigManager().getString(
            pointId,
            "reinforcements.spawn-location.y-mode",
            SpawnYMode.SURFACE.name()
        );
        return SpawnYMode.fromConfigValue(rawMode);
    }

    private double getSpawnClampRange(String pointId) {
        double value = plugin.getZoneConfigManager().getDouble(
            pointId,
            "reinforcements.spawn-location.clamp-range",
            8.0
        );
        return Math.max(0.0, value);
    }

    private double resolveSpawnY(String pointId, CapturePoint point, Location origin, Location spawnLoc) {
        if (spawnLoc == null || spawnLoc.getWorld() == null) {
            return 0.0;
        }
        World world = spawnLoc.getWorld();
        int surfaceY = world.getHighestBlockYAt(spawnLoc.getBlockX(), spawnLoc.getBlockZ());

        double zoneCenterY = point != null && point.getLocation() != null
            ? point.getLocation().getY()
            : (origin != null ? origin.getY() : surfaceY);
        double originY = origin != null ? origin.getY() : zoneCenterY;

        double baseY;
        SpawnYMode mode = getSpawnYMode(pointId);
        switch (mode) {
            case ZONE_CENTER:
                baseY = zoneCenterY;
                break;
            case ORIGIN_PLAYER:
                baseY = originY;
                break;
            case CLAMPED:
                double range = getSpawnClampRange(pointId);
                double minY = zoneCenterY - range;
                double maxY = zoneCenterY + range;
                baseY = Math.max(minY, Math.min(maxY, surfaceY));
                break;
            case SURFACE:
            default:
                baseY = surfaceY;
                break;
        }

        double yWithOffset = baseY + getSpawnSurfaceOffset(pointId);
        return clampYToWorld(world, yWithOffset);
    }

    private double clampYToWorld(World world, double y) {
        if (world == null) {
            return y;
        }
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        return Math.max(minY, Math.min(maxY, y));
    }

    private CaptureOwner resolvePlayerOwner(Player player, CaptureOwnerType ownerType) {
        if (player == null || ownerType == null || plugin.getOwnerPlatform() == null) {
            return null;
        }

        String ownerName = plugin.getOwnerPlatform().resolveOwnerName(player, ownerType);
        if (ownerName == null || ownerName.trim().isEmpty()) {
            return null;
        }

        String normalizedOwner = plugin.getOwnerPlatform().normalizeOwnerName(ownerName, ownerType);
        if (normalizedOwner == null || normalizedOwner.trim().isEmpty()) {
            normalizedOwner = ownerName.trim();
        }

        String ownerId = ownerType == CaptureOwnerType.PLAYER
            ? player.getUniqueId().toString()
            : null;
        return new CaptureOwner(ownerType, ownerId, normalizedOwner);
    }

    private boolean isOpposingOwnerTarget(CaptureOwner capturingOwner, Player target) {
        if (capturingOwner == null || target == null) {
            return false;
        }

        CaptureOwnerType ownerType = capturingOwner.getType();
        CaptureOwner targetOwner = resolvePlayerOwner(target, ownerType);
        if (targetOwner == null || targetOwner.getDisplayName() == null || targetOwner.getDisplayName().isEmpty()) {
            return false;
        }
        return !targetOwner.isSameOwner(capturingOwner);
    }

    private boolean isTargetAllowed(String pointId, CapturePoint point, CaptureOwner capturingOwner, Player target) {
        if (pointId == null || point == null || target == null || !target.isOnline() || target.isDead()) {
            return false;
        }

        int extraChunks = getTargetSearchExtraChunks(pointId);
        if (!plugin.isWithinZone(point, target.getLocation(), extraChunks)) {
            return false;
        }

        TargetingMode targetingMode = getTargetingMode(pointId);
        switch (targetingMode) {
            case ANY_PLAYER_IN_ZONE:
                return true;
            case OPPOSING_OWNER_ONLY:
                return isOpposingOwnerTarget(capturingOwner, target);
            case CAPTURING_OWNER_ONLY:
            default:
                return capturingOwner != null && plugin.doesPlayerMatchOwner(target, capturingOwner);
        }
    }

    private void spawnReinforcementWave(String pointId, CapturePoint point, int phase) {
        // Use zone-specific config
        if (!plugin.getZoneConfigManager().getBoolean(pointId, "reinforcements.enabled", true)) {
            return;
        }

        CaptureSession session = plugin.getActiveSession(pointId);
        if (session == null || !session.isActive() || session.isInPreparationPhase()) {
            return;
        }

        int timeLeft = session.getRemainingCaptureTime();
        
        int stopSpawningThreshold = getStopSpawningUnderSeconds(pointId);
        if (timeLeft <= stopSpawningThreshold) {
            return;
        }

        int actualMobs = calculateMobsForPhase(pointId, phase);
        int maxMobs = getMaxMobsPerPoint(pointId);

        CaptureOwner capturingOwner = resolveCapturingOwner(point, session);
        if (capturingOwner == null || capturingOwner.getDisplayName() == null || capturingOwner.getDisplayName().isEmpty()) {
            return;
        }
        
        // Enqueue spawns respecting the global cap; queue processor will rate-limit
        List<UUID> reinforcements = activeReinforcements.computeIfAbsent(pointId, k -> new ArrayList<>());
        int pending = pendingSpawns.getOrDefault(pointId, 0);
        int availableSlots = Math.max(0, maxMobs - (reinforcements.size() + pending));
        int toQueue = Math.min(actualMobs, availableSlots);
        for (int i = 0; i < toQueue; i++) {
            enqueueSpawn(pointId);
        }

        // Send message to capturing players only
        String phaseMessage = Messages.get("messages.reinforcement.phase", Map.of(
            "phase", String.valueOf(phase),
            "count", String.valueOf(toQueue)
        ));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (plugin.doesPlayerMatchOwner(player, capturingOwner) &&
                plugin.isWithinZone(point, player.getLocation())) {
                plugin.sendNotification(player, phaseMessage);
            }
        }

        if (toQueue > 0 && plugin.getDiscordWebhook() != null) {
            plugin.getDiscordWebhook().sendReinforcementPhase(pointId, point.getName(), phase, toQueue);
        }
    }

    private void enqueueSpawn(String pointId) {
        spawnQueue.add(new SpawnRequest(pointId));
        pendingSpawns.merge(pointId, 1, Integer::sum);
        ensureSpawnProcessor();
    }

    private void ensureSpawnProcessor() {
        if (spawnProcessorTask != null && !spawnProcessorTask.isCancelled()) {
            return;
        }
        spawnProcessorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::processSpawnQueue, 1L, 1L);
    }

    private void processSpawnQueue() {
        if (spawnQueue.isEmpty()) {
            if (spawnProcessorTask != null) {
                spawnProcessorTask.cancel();
                spawnProcessorTask = null;
            }
            return;
        }

        int globalLimit = getGlobalSpawnLimitPerTick();
        int maxScans = Math.max(spawnQueue.size(), globalLimit * 8);
        int scans = 0;
        int spawnedThisTick = 0;
        Map<String, Integer> perZoneProcessed = new HashMap<>();
        while (scans < maxScans && spawnedThisTick < globalLimit && !spawnQueue.isEmpty()) {
            SpawnRequest request = spawnQueue.poll();
            if (request == null) {
                break;
            }
            scans++;
            decrementPending(request.pointId);

            int zoneLimit = getZoneSpawnLimit(request.pointId);
            int zoneProcessed = perZoneProcessed.getOrDefault(request.pointId, 0);
            if (zoneProcessed >= zoneLimit) {
                spawnQueue.add(request);
                incrementPending(request.pointId);
                continue;
            }

            CaptureSession session = plugin.getActiveSession(request.pointId);
            CapturePoint point = plugin.getCapturePoint(request.pointId);
            if (!isSpawnAllowed(session, point)) {
                removeQueuedSpawns(request.pointId);
                continue;
            }

            CaptureOwner capturingOwner = resolveCapturingOwner(point, session);

            List<UUID> reinforcements = activeReinforcements.computeIfAbsent(request.pointId, k -> new ArrayList<>());
            // Use zone-specific max-mobs-per-point
            int maxMobs = getMaxMobsPerPoint(request.pointId);
            if (reinforcements.size() >= maxMobs) {
                continue;
            }

            if (spawnReinforcementMob(point, session, capturingOwner, reinforcements)) {
                perZoneProcessed.put(request.pointId, zoneProcessed + 1);
                spawnedThisTick++;
            }
        }

        if (spawnQueue.isEmpty() && spawnProcessorTask != null) {
            spawnProcessorTask.cancel();
            spawnProcessorTask = null;
        }
    }

    private void decrementPending(String pointId) {
        Integer current = pendingSpawns.get(pointId);
        if (current == null) {
            return;
        }
        int next = current - 1;
        if (next <= 0) {
            pendingSpawns.remove(pointId);
        } else {
            pendingSpawns.put(pointId, next);
        }
    }

    private void incrementPending(String pointId) {
        pendingSpawns.merge(pointId, 1, Integer::sum);
    }

    private int getZoneSpawnLimit(String pointId) {
        int value = 3;
        if (plugin.getZoneConfigManager() != null && pointId != null) {
            value = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.spawn-rate.max-per-tick", 3);
        } else if (plugin.getZoneConfigManager() != null) {
            value = plugin.getZoneConfigManager().getDefaultInt("reinforcements.spawn-rate.max-per-tick", 3);
        } else {
            value = plugin.getConfig().getInt("reinforcements.spawn-rate.max-per-tick", 3);
        }
        return Math.max(1, value);
    }

    private int getGlobalSpawnLimitPerTick() {
        int value = plugin.getConfig().getInt("reinforcements.spawn-rate.global-max-per-tick", 8);
        return Math.max(1, value);
    }

    private int getTimerReductionSeconds(String pointId, LivingEntity entity) {
        if (plugin.getZoneConfigManager() == null) {
            return getRandomReduction(1, 3);
        }

        String source = getMetadataString(entity, META_SOURCE);
        boolean isMythic = source != null && "MYTHIC".equalsIgnoreCase(source);
        if (isMythic) {
            int defaultMin = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.timer-reduction.mythic.default.min", 1);
            int defaultMax = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.timer-reduction.mythic.default.max", 3);
            String mythicType = getMetadataString(entity, META_MYTHIC_TYPE);
            if (mythicType != null && !mythicType.isEmpty()) {
                String basePath = "reinforcements.timer-reduction.mythic.per-mob." + mythicType;
                int min = plugin.getZoneConfigManager().getInt(pointId, basePath + ".min", defaultMin);
                int max = plugin.getZoneConfigManager().getInt(pointId, basePath + ".max", defaultMax);
                return getRandomReduction(min, max);
            }
            return getRandomReduction(defaultMin, defaultMax);
        }

        int vanillaMin = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.timer-reduction.vanilla.min", 1);
        int vanillaMax = plugin.getZoneConfigManager().getInt(pointId, "reinforcements.timer-reduction.vanilla.max", 3);
        return getRandomReduction(vanillaMin, vanillaMax);
    }

    private int getRandomReduction(int min, int max) {
        int safeMin = Math.max(0, min);
        int safeMax = Math.max(safeMin, max);
        if (safeMax <= safeMin) {
            return safeMin;
        }
        return safeMin + random.nextInt(safeMax - safeMin + 1);
    }

    private String getMetadataString(Entity entity, String key) {
        if (entity == null || !entity.hasMetadata(key)) {
            return null;
        }
        List<MetadataValue> values = entity.getMetadata(key);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0).asString();
    }

    private CaptureOwner resolveCapturingOwner(CapturePoint point, CaptureSession session) {
        if (point != null) {
            CaptureOwner pointOwner = point.getCapturingOwner();
            if (pointOwner != null && pointOwner.getDisplayName() != null && !pointOwner.getDisplayName().isEmpty()) {
                return pointOwner;
            }
        }

        if (session != null) {
            CaptureOwner sessionOwner = session.getOwner();
            if (sessionOwner != null && sessionOwner.getDisplayName() != null && !sessionOwner.getDisplayName().isEmpty()) {
                return sessionOwner;
            }
        }

        if (point != null) {
            String fallbackName = point.getCapturingTown();
            if (fallbackName != null && !fallbackName.trim().isEmpty()) {
                return CaptureOwner.fromDisplayName(plugin.getDefaultOwnerType(), fallbackName);
            }
        }
        return null;
    }

    private boolean isSpawnAllowed(CaptureSession session, CapturePoint point) {
        if (session == null || point == null) {
            return false;
        }
        if (!session.isActive() || session.isInPreparationPhase()) {
            return false;
        }
        CaptureOwner pointOwner = resolveCapturingOwner(point, session);
        if (pointOwner == null || pointOwner.getDisplayName() == null || pointOwner.getDisplayName().isEmpty()) {
            return false;
        }
        CaptureOwner sessionOwner = session.getOwner();
        if (sessionOwner != null) {
            return sessionOwner.isSameOwner(pointOwner);
        }
        String sessionOwnerName = session.getTownName();
        return sessionOwnerName != null && sessionOwnerName.equalsIgnoreCase(pointOwner.getDisplayName());
    }

    private void removeQueuedSpawns(String pointId) {
        if (spawnQueue.isEmpty()) {
            pendingSpawns.remove(pointId);
            return;
        }

        int removed = 0;
        Iterator<SpawnRequest> iterator = spawnQueue.iterator();
        while (iterator.hasNext()) {
            SpawnRequest request = iterator.next();
            if (request.pointId.equals(pointId)) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            Integer current = pendingSpawns.get(pointId);
            if (current != null) {
                int next = current - removed;
                if (next <= 0) {
                    pendingSpawns.remove(pointId);
                } else {
                    pendingSpawns.put(pointId, next);
                }
            }
        }
    }

    private boolean spawnReinforcementMob(CapturePoint point, CaptureSession session, CaptureOwner capturingOwner, List<UUID> reinforcements) {
        if (mobSpawner == null) {
            plugin.getLogger().warning("MobSpawner not initialized! Cannot spawn reinforcements.");
            return false;
        }
        
        if (!mobSpawner.isAvailable()) {
            plugin.getLogger().warning("MobSpawner is not available! Cannot spawn reinforcements.");
            return false;
        }

        if (session == null || !session.isActive() || session.isInPreparationPhase()) {
            return false;
        }
        if (capturingOwner == null || capturingOwner.getDisplayName() == null || capturingOwner.getDisplayName().isEmpty()) {
            return false;
        }

        String pointId = point.getId();
        
        Player originPlayer = null;
        if (session.getInitiatorUUID() != null) {
            originPlayer = plugin.getServer().getPlayer(session.getInitiatorUUID());
        }
        if (originPlayer == null) {
            originPlayer = session.getPlayers().stream().filter(Player::isOnline).findFirst().orElse(null);
        }

        Location origin = originPlayer != null ? originPlayer.getLocation() : point.getLocation();
        if (origin.getWorld() == null) {
            return false;
        }

        double minDistance = getSpawnMinDistance(pointId);
        double maxDistance = getSpawnMaxDistance(pointId);
        double angle = random.nextDouble() * 2 * Math.PI;
        double radiusRange = Math.max(0.0, maxDistance - minDistance);
        double radius = minDistance + (radiusRange > 0.0 ? random.nextDouble() * radiusRange : 0.0);
        Location spawnLoc = origin.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);

        // Ensure chunk is loaded and resolve final Y
        if (!spawnLoc.getChunk().isLoaded()) {
            spawnLoc.getChunk().load();
        }
        spawnLoc.setY(resolveSpawnY(pointId, point, origin, spawnLoc));

        // Use the mob spawner abstraction to spawn the mob
        LivingEntity mob = mobSpawner.spawnMob(pointId, spawnLoc, originPlayer);
        
        if (mob == null) {
            plugin.getLogger().warning("Failed to spawn reinforcement mob at " + spawnLoc);
            return false;
        }

        mob.setMetadata(META_REINFORCEMENT, new FixedMetadataValue(plugin, pointId));
        mob.setMetadata("capture_point_id", new FixedMetadataValue(plugin, pointId));
        mob.setMetadata("capturing_town", new FixedMetadataValue(plugin, capturingOwner.getDisplayName()));

        reinforcements.add(mob.getUniqueId());

        // Make mob persistent (won't despawn naturally)
        mob.setRemoveWhenFarAway(false);
        mob.setPersistent(true);

        // Add fire resistance to prevent burning
        mob.addPotionEffect(new PotionEffect(
            PotionEffectType.FIRE_RESISTANCE, 
            999999, 
            1, 
            true, 
            false
        ));
        
        if (mob instanceof Slime) {
            ((Slime) mob).setSize(2); // Medium size
        }
        if (mob instanceof MagmaCube) {
            ((MagmaCube) mob).setSize(2); // Medium size
        }

        // Initial target setup (will be continuously updated by tracking task)
        updateMobTarget(mob, point, capturingOwner);
        return true;
    }
    
    private void updateMobTarget(Entity mobEntity, CapturePoint point, CaptureOwner capturingOwner) {
        if (point == null) {
            return;
        }
        updateMobTarget(mobEntity, point, getEligibleTargets(point.getId(), point, capturingOwner));
    }

    private void updateMobTarget(Entity mobEntity, CapturePoint point, List<Player> candidates) {
        if (!(mobEntity instanceof Creature)) {
            return;
        }
        
        if (mobEntity.isDead() || !mobEntity.isValid()) {
            return;
        }

        if (point == null || candidates.isEmpty()) {
            return;
        }

        if (mobEntity.getWorld() != point.getLocation().getWorld()) {
            return;
        }
        
        Creature creature = (Creature) mobEntity;
        Player nearestTarget = null;
        double closestDistSquared = Double.MAX_VALUE;

        for (Player player : candidates) {
            if (!player.isOnline() || player.isDead()) {
                continue;
            }
            double distSquared = player.getLocation().distanceSquared(mobEntity.getLocation());
            if (distSquared < closestDistSquared) {
                closestDistSquared = distSquared;
                nearestTarget = player;
            }
        }
        
        if (nearestTarget != null) {
            try {
                creature.setTarget(nearestTarget);
            } catch (Exception e) {
                // Silently handle any targeting exceptions
            }
        } else if (creature.getTarget() != null) {
            // If no valid target found but mob has a target, keep current target
            // This prevents mobs from losing track of players
        }
    }

    private List<Player> getEligibleTargets(String pointId, CapturePoint point, CaptureOwner capturingOwner) {
        if (pointId == null || point == null) {
            return Collections.emptyList();
        }

        Location center = point.getLocation();
        if (center == null || center.getWorld() == null) {
            return Collections.emptyList();
        }

        TargetingMode targetingMode = getTargetingMode(pointId);
        if (targetingMode != TargetingMode.ANY_PLAYER_IN_ZONE
            && (capturingOwner == null || capturingOwner.getDisplayName() == null || capturingOwner.getDisplayName().isEmpty())) {
            return Collections.emptyList();
        }

        int extraChunks = getTargetSearchExtraChunks(pointId);
        List<Player> candidates = new ArrayList<>();

        for (Player player : center.getWorld().getPlayers()) {
            if (!player.isOnline() || player.isDead()) {
                continue;
            }

            if (!plugin.isWithinZone(point, player.getLocation(), extraChunks)) {
                continue;
            }

            if (targetingMode == TargetingMode.ANY_PLAYER_IN_ZONE) {
                candidates.add(player);
                continue;
            }

            if (targetingMode == TargetingMode.CAPTURING_OWNER_ONLY) {
                if (plugin.doesPlayerMatchOwner(player, capturingOwner)) {
                    candidates.add(player);
                }
                continue;
            }

            if (isOpposingOwnerTarget(capturingOwner, player)) {
                candidates.add(player);
            }
        }

        return candidates;
    }
    
    private void startMobTracking(String pointId) {
        // Don't start multiple tracking tasks for the same point
        if (trackingTasks.containsKey(pointId)) {
            return;
        }

        long retargetIntervalTicks = getTargetRetargetIntervalTicks(pointId);
        
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getActiveSessions().containsKey(pointId)) {
                stopMobTracking(pointId);
                return;
            }
            
            List<UUID> reinforcements = activeReinforcements.get(pointId);
            if (reinforcements == null || reinforcements.isEmpty()) {
                return;
            }

            CapturePoint point = plugin.getCapturePoint(pointId);
            if (point == null) {
                stopMobTracking(pointId);
                return;
            }

            CaptureSession session = plugin.getActiveSession(pointId);
            CaptureOwner capturingOwner = resolveCapturingOwner(point, session);
            List<Player> candidates = getEligibleTargets(pointId, point, capturingOwner);
            if (candidates.isEmpty()) {
                return;
            }
            
            for (UUID mobUUID : new ArrayList<>(reinforcements)) {
                Entity mob = plugin.getServer().getEntity(mobUUID);
                if (mob != null && mob.isValid() && !mob.isDead()) {
                    updateMobTarget(mob, point, candidates);
                }
            }
        }, retargetIntervalTicks, retargetIntervalTicks);
        
        trackingTasks.put(pointId, task);
    }
    
    private void stopMobTracking(String pointId) {
        BukkitTask task = trackingTasks.remove(pointId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        removeQueuedSpawns(pointId);
    }

    public void clearReinforcements(String pointId) {
        List<UUID> reinforcements = activeReinforcements.remove(pointId);
        if (reinforcements != null) {
            for (UUID uuid : reinforcements) {
                org.bukkit.entity.Entity entity = plugin.getServer().getEntity(uuid);
                if (entity != null) {
                    entity.remove();
                }
            }
        }

        // Cancel phase task
        BukkitTask task = phaseTasks.remove(pointId);
        if (task != null) {
            task.cancel();
        }
        
        // Stop tracking task
        stopMobTracking(pointId);
        
        currentPhase.remove(pointId);
    }

    public void shutdown() {
        // Cancel global spawn processor
        if (spawnProcessorTask != null && !spawnProcessorTask.isCancelled()) {
            spawnProcessorTask.cancel();
        }
        spawnQueue.clear();
        pendingSpawns.clear();
        
        // Cancel per-point tasks and clean mobs
        for (String pointId : new ArrayList<>(activeReinforcements.keySet())) {
            clearReinforcements(pointId);
        }
        for (BukkitTask task : phaseTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        phaseTasks.clear();
    }

    public void startReinforcementWaves(String pointId, CapturePoint point) {
        // Use zone-specific config
        if (!plugin.getZoneConfigManager().getBoolean(pointId, "reinforcements.enabled", true)) {
            return;
        }

        currentPhase.put(pointId, 1);
        
        // Initial wave
        spawnReinforcementWave(pointId, point, 1);
        
        startMobTracking(pointId);

        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!plugin.getActiveSessions().containsKey(pointId)) {
                clearReinforcements(pointId);
                return;
            }

            CaptureSession session = plugin.getActiveSession(pointId);
            if (session == null) {
                return;
            }

            int timeLeft = session.getRemainingCaptureTime();
            
            if (timeLeft <= getStopSpawningUnderSeconds(pointId)) {
                return;
            }
            
            int intervalSeconds = getWaveIntervalSeconds(pointId);
            boolean isAtMark = (timeLeft % intervalSeconds == 0);
            
            if (isAtMark) {
                // Calculate expected phase based on timer position
                int initialTime = session.getInitialCaptureTime();
                int elapsed = initialTime - timeLeft;
                int expectedPhase = (elapsed / intervalSeconds) + 1;
                int currentP = currentPhase.getOrDefault(pointId, 1);
                
                // Spawn any phases we've skipped
                if (expectedPhase > currentP) {
                    for (int phase = currentP + 1; phase <= expectedPhase; phase++) {
                        currentPhase.put(pointId, phase);
                        spawnReinforcementWave(pointId, point, phase);
                    }
                }
            }
        }, 20L, 20L); // Every second (20 ticks)

        phaseTasks.put(pointId, task);
    }

    private static class SpawnRequest {
        private final String pointId;

        private SpawnRequest(String pointId) {
            this.pointId = pointId;
        }
    }
}

