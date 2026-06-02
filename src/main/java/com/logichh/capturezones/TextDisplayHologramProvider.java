package com.logichh.capturezones;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class TextDisplayHologramProvider implements HologramProvider {
    private static final String MANAGED_HOLOGRAM_TAG = "capturezones_hologram";
    private static final String RUNTIME_TAG_PREFIX = "czh_rt_";
    private static final double LEGACY_CLEANUP_HORIZONTAL_RANGE_SQ = 12.0d * 12.0d;
    private static final double LEGACY_CLEANUP_VERTICAL_RANGE = 48.0d;

    private final CaptureZones plugin;
    private final Logger logger;
    private final Map<String, List<TextDisplay>> pointDisplays;
    private final Listener chunkCleanupListener;
    private boolean available;
    private boolean chunkCleanupListenerRegistered;
    private String runtimeTag;
    private Set<String> legacyPointNamesLower;
    private List<String> legacyLinePrefixesLower;

    public TextDisplayHologramProvider(CaptureZones plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.pointDisplays = new HashMap<>();
        this.chunkCleanupListener = new ChunkCleanupListener();
        this.available = false;
        this.chunkCleanupListenerRegistered = false;
        this.runtimeTag = null;
        this.legacyPointNamesLower = Collections.emptySet();
        this.legacyLinePrefixesLower = List.of("status:", "owner:", "reward:", "time:");
    }

    @Override

    public boolean initialize() {
        this.runtimeTag = buildRuntimeTag();
        refreshLegacyCleanupMatchers();
        registerChunkCleanupListener();
        purgeStaleDisplaysInLoadedWorlds();
        this.available = true;
        return true;
    }

    @Override

    public boolean isAvailable() {
        return this.available;
    }

    @Override

    public String getName() {
        return "TextDisplay";
    }

    @Override

    public void createOrUpdate(String pointId, Location baseLocation, List<String> lines, double lineSpacing, boolean fixedOrientation) {
        if (!available || pointId == null || pointId.trim().isEmpty() || baseLocation == null || baseLocation.getWorld() == null) {
            return;
        }
        if (lines == null || lines.isEmpty()) {
            remove(pointId);
            return;
        }

        final List<TextDisplay> existing = pointDisplays.get(pointId);
        if (!canReuseDisplays(existing, lines.size(), baseLocation.getWorld(), fixedOrientation)) {
            recreateDisplays(pointId, baseLocation, lines, lineSpacing, fixedOrientation);
            return;
        }

        for (int i = 0; i < lines.size(); i++) {
            TextDisplay display = existing.get(i);
            String line = lines.get(i) == null ? "" : lines.get(i);
            Location target = computeLineLocation(baseLocation, i, lineSpacing);
            if (!target.getWorld().getUID().equals(display.getWorld().getUID())
                || !isSameTransform(display.getLocation(), target)) {
                display.teleport(target);
            }
            if (!line.equals(display.getText())) {
                display.setText(line);
            }
            Display.Billboard expectedBillboard = fixedOrientation ? Display.Billboard.FIXED : Display.Billboard.CENTER;
            if (display.getBillboard() != expectedBillboard) {
                display.setBillboard(expectedBillboard);
            }
        }
    }

    @Override

    public void remove(String pointId) {
        if (pointId == null || pointId.trim().isEmpty()) {
            return;
        }
        List<TextDisplay> displays = pointDisplays.remove(pointId);
        if (displays == null || displays.isEmpty()) {
            return;
        }
        for (TextDisplay display : displays) {
            if (display != null) {
                display.remove();
            }
        }
    }

    @Override

    public void cleanup() {
        unregisterChunkCleanupListener();
        for (String pointId : new ArrayList<>(pointDisplays.keySet())) {
            remove(pointId);
        }
        pointDisplays.clear();
        runtimeTag = null;
        available = false;
    }

    private void recreateDisplays(String pointId, Location baseLocation, List<String> lines, double lineSpacing, boolean fixedOrientation) {
        remove(pointId);

        World world = baseLocation.getWorld();
        if (world == null) {
            return;
        }

        purgeOrphanDisplaysNear(baseLocation, lines.size(), lineSpacing);

        List<TextDisplay> created = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Location spawnLocation = computeLineLocation(baseLocation, i, lineSpacing);
            String line = lines.get(i) == null ? "" : lines.get(i);
            try {
                TextDisplay display = (TextDisplay) world.spawnEntity(spawnLocation, EntityType.TEXT_DISPLAY);
                configureDisplay(display, line, fixedOrientation);
                created.add(display);
            } catch (Exception e) {
                logger.warning("Failed to create hologram line for zone '" + pointId + "': " + e.getMessage());
            }
        }

        if (created.isEmpty()) {
            pointDisplays.remove(pointId);
            return;
        }
        pointDisplays.put(pointId, created);
    }

    private void configureDisplay(TextDisplay display, String text, boolean fixedOrientation) {
        if (display == null) {
            return;
        }
        display.setText(text);
        display.setBillboard(fixedOrientation ? Display.Billboard.FIXED : Display.Billboard.CENTER);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setShadowed(false);
        display.setSeeThrough(false);
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.addScoreboardTag(MANAGED_HOLOGRAM_TAG);
        if (runtimeTag != null && !runtimeTag.isEmpty()) {
            display.addScoreboardTag(runtimeTag);
        }
    }

    private boolean canReuseDisplays(List<TextDisplay> displays, int requiredLines, World world, boolean fixedOrientation) {
        if (displays == null || displays.size() != requiredLines || world == null) {
            return false;
        }
        Display.Billboard expectedBillboard = fixedOrientation ? Display.Billboard.FIXED : Display.Billboard.CENTER;
        for (TextDisplay display : displays) {
            if (display == null || !display.isValid() || display.isDead()) {
                return false;
            }
            if (display.getWorld() == null || !world.getUID().equals(display.getWorld().getUID())) {
                return false;
            }
            if (!isCurrentRuntimeManagedDisplay(display)) {
                return false;
            }
            if (display.getBillboard() != expectedBillboard) {
                return false;
            }
        }
        return true;
    }

    private Location computeLineLocation(Location base, int lineIndex, double lineSpacing) {
        double safeSpacing = Math.max(0.05d, lineSpacing);
        return base.clone().add(0.0d, -(safeSpacing * lineIndex), 0.0d);
    }

    private boolean isSameTransform(Location first, Location second) {
        if (first == null || second == null) {
            return false;
        }
        if (!first.getWorld().getUID().equals(second.getWorld().getUID())) {
            return false;
        }
        if (first.distanceSquared(second) > 0.0001d) {
            return false;
        }
        return angularDifference(first.getYaw(), second.getYaw()) < 0.5f
            && angularDifference(first.getPitch(), second.getPitch()) < 0.5f;
    }

    private float angularDifference(float first, float second) {
        float diff = Math.abs(first - second) % 360.0f;
        return diff > 180.0f ? 360.0f - diff : diff;
    }

    private String buildRuntimeTag() {
        String token = UUID.randomUUID().toString().replace("-", "");
        if (token.length() > 12) {
            token = token.substring(0, 12);
        }
        return RUNTIME_TAG_PREFIX + token;
    }

    private void registerChunkCleanupListener() {
        if (chunkCleanupListenerRegistered) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(chunkCleanupListener, plugin);
        chunkCleanupListenerRegistered = true;
    }

    private void unregisterChunkCleanupListener() {
        if (!chunkCleanupListenerRegistered) {
            return;
        }
        HandlerList.unregisterAll(chunkCleanupListener);
        chunkCleanupListenerRegistered = false;
    }

    private void refreshLegacyCleanupMatchers() {
        Set<String> pointNames = new HashSet<>();
        for (CapturePoint point : plugin.getCapturePoints().values()) {
            if (point == null || point.getName() == null) {
                continue;
            }
            String normalized = normalizeText(point.getName());
            if (!normalized.isEmpty()) {
                pointNames.add(normalized);
            }
        }
        this.legacyPointNamesLower = pointNames;

        List<String> prefixes = new ArrayList<>();
        addLegacyPrefix(prefixes, Messages.get("hologram.label.status"));
        addLegacyPrefix(prefixes, Messages.get("hologram.label.owner"));
        addLegacyPrefix(prefixes, Messages.get("hologram.label.reward"));
        addLegacyPrefix(prefixes, Messages.get("hologram.label.time"));
        addLegacyPrefix(prefixes, "Status");
        addLegacyPrefix(prefixes, "Owner");
        addLegacyPrefix(prefixes, "Reward");
        addLegacyPrefix(prefixes, "Time");
        this.legacyLinePrefixesLower = prefixes;
    }

    private void addLegacyPrefix(List<String> prefixes, String label) {
        String normalized = normalizeText(label);
        if (normalized.isEmpty()) {
            return;
        }
        String withColon = normalized.endsWith(":") ? normalized : normalized + ":";
        if (!prefixes.contains(withColon)) {
            prefixes.add(withColon);
        }
    }

    private void purgeStaleDisplaysInLoadedWorlds() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            removed += purgeOrphansInWorld(world);
        }
        if (removed > 0) {
            logger.info("Removed " + removed + " stale hologram display entities.");
        }
    }

    private int purgeOrphansInWorld(World world) {
        if (world == null) {
            return 0;
        }
        int removed = 0;
        for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
            if (display == null || display.isDead()) {
                continue;
            }
            if (shouldRemoveAsOrphan(display)) {
                display.remove();
                removed++;
            }
        }
        return removed;
    }

    private void purgeOrphanDisplaysNear(Location baseLocation, int lineCount, double lineSpacing) {
        if (baseLocation == null || baseLocation.getWorld() == null) {
            return;
        }
        World world = baseLocation.getWorld();
        double safeSpacing = Math.max(0.05d, lineSpacing);
        double verticalRange = Math.max(1.0d, ((Math.max(1, lineCount) - 1) * safeSpacing) + 0.6d);

        for (Entity entity : world.getNearbyEntities(baseLocation, 0.8d, verticalRange, 0.8d)) {
            if (!(entity instanceof TextDisplay)) {
                continue;
            }
            TextDisplay display = (TextDisplay) entity;
            if (display.isDead()) {
                continue;
            }
            if (shouldRemoveAsOrphan(display)) {
                display.remove();
            }
        }
    }

    private int purgeOrphansInChunk(Chunk chunk) {
        if (chunk == null) {
            return 0;
        }
        int removed = 0;
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof TextDisplay)) {
                continue;
            }
            TextDisplay display = (TextDisplay) entity;
            if (display.isDead()) {
                continue;
            }
            if (shouldRemoveAsOrphan(display)) {
                display.remove();
                removed++;
            }
        }
        return removed;
    }

    private boolean shouldRemoveAsOrphan(TextDisplay display) {
        if (display == null) {
            return false;
        }
        if (isStaleManagedDisplay(display)) {
            return true;
        }
        return isLegacyUntrackedDisplay(display);
    }

    private boolean isManagedDisplay(TextDisplay display) {
        return display != null && display.getScoreboardTags().contains(MANAGED_HOLOGRAM_TAG);
    }

    private boolean isCurrentRuntimeManagedDisplay(TextDisplay display) {
        if (!isManagedDisplay(display)) {
            return false;
        }
        return runtimeTag != null && display.getScoreboardTags().contains(runtimeTag);
    }

    private boolean isStaleManagedDisplay(TextDisplay display) {
        if (!isManagedDisplay(display)) {
            return false;
        }
        if (runtimeTag == null || runtimeTag.isEmpty()) {
            return true;
        }
        return !display.getScoreboardTags().contains(runtimeTag);
    }

    private boolean isLegacyUntrackedDisplay(TextDisplay display) {
        if (display == null || isManagedDisplay(display)) {
            return false;
        }
        if (display.isPersistent() || !display.isInvulnerable() || display.hasGravity()) {
            return false;
        }
        if (display.isShadowed() || display.isSeeThrough()) {
            return false;
        }
        if (display.getAlignment() != TextDisplay.TextAlignment.CENTER) {
            return false;
        }
        Display.Billboard billboard = display.getBillboard();
        if (billboard != Display.Billboard.CENTER && billboard != Display.Billboard.FIXED) {
            return false;
        }
        if (!isNearAnyCapturePoint(display.getLocation())) {
            return false;
        }

        String normalizedText = normalizeText(display.getText());
        if (normalizedText.isEmpty()) {
            return false;
        }
        if (legacyPointNamesLower.contains(normalizedText)) {
            return true;
        }
        for (String prefix : legacyLinePrefixesLower) {
            if (normalizedText.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean isNearAnyCapturePoint(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        for (CapturePoint point : plugin.getCapturePoints().values()) {
            if (point == null || point.getLocation() == null || point.getLocation().getWorld() == null) {
                continue;
            }
            if (!point.getLocation().getWorld().getUID().equals(location.getWorld().getUID())) {
                continue;
            }
            double vertical = Math.abs(location.getY() - point.getLocation().getY());
            if (vertical > LEGACY_CLEANUP_VERTICAL_RANGE) {
                continue;
            }
            double dx = location.getX() - point.getLocation().getX();
            double dz = location.getZ() - point.getLocation().getZ();
            if ((dx * dx) + (dz * dz) <= LEGACY_CLEANUP_HORIZONTAL_RANGE_SQ) {
                return true;
            }
        }
        return false;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(text);
        if (stripped == null) {
            return "";
        }
        return stripped.trim().toLowerCase(Locale.ROOT);
    }

    private final class ChunkCleanupListener implements Listener {
        @EventHandler
        public void onChunkLoad(ChunkLoadEvent event) {
            if (!available || event == null) {
                return;
            }
            purgeOrphansInChunk(event.getChunk());
        }
    }
}

