package com.logichh.capturezones;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class CuboidSelectionManager implements Listener {
    private static final Material SELECTOR_TOOL_MATERIAL = Material.BLAZE_ROD;
    private static final int MAX_TARGET_DISTANCE = 128;
    private static final int OUTLINE_SAMPLE_TARGET = 24;
    private static final long OUTLINE_DURATION_TICKS = 80L;
    private static final long OUTLINE_INTERVAL_TICKS = 5L;

    private final CaptureZones plugin;
    private final NamespacedKey selectionToolKey;

    private final Map<UUID, PendingCuboidSelection> pendingSelections = new HashMap<>();

    public CuboidSelectionManager(CaptureZones plugin) {
        this.plugin = plugin;
        this.selectionToolKey = new NamespacedKey(plugin, "cuboid_selector_tool");
    }

    public boolean startSelection(Player player, String id, String type, double reward) {
        if (player == null || id == null || type == null) {
            return false;
        }

        if (!plugin.canCreateMoreCapturePoints()) {
            player.sendMessage(Messages.get("errors.max-capture-points-reached", Map.of(
                "max", String.valueOf(plugin.getMaxCapturePointsLimit())
            )));
            return false;
        }

        String normalizedId = id.trim();
        String normalizedType = type.trim();
        if (normalizedId.isEmpty() || normalizedType.isEmpty()) {
            return false;
        }

        PendingCuboidSelection selection = new PendingCuboidSelection(normalizedId, normalizedType, reward);
        pendingSelections.put(player.getUniqueId(), selection);
        giveSelectionTool(player);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", normalizedId);
        placeholders.put("type", normalizedType);
        placeholders.put("reward", String.format(Locale.US, "%.2f", reward));
        player.sendMessage(Messages.get("messages.create.cuboid-tool.started", placeholders));
        player.sendMessage(Messages.get("messages.create.cuboid-tool.instructions"));
        return true;
    }

    public void clearAllSelections() {
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            removeSelectionTools(onlinePlayer);
        }
        pendingSelections.clear();
    }

    public void showTemporaryOutline(CapturePoint point) {
        if (point == null || !point.isCuboid() || point.getLocation() == null || point.getLocation().getWorld() == null) {
            return;
        }
        showTemporaryOutline(
            point.getLocation().getWorld(),
            point.getCuboidMinX(),
            point.getCuboidMinY(),
            point.getCuboidMinZ(),
            point.getCuboidMaxX(),
            point.getCuboidMaxY(),
            point.getCuboidMaxZ()
        );
    }

    public void showTemporaryOutline(World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (world == null) {
            return;
        }

        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        int maxZ = Math.max(z1, z2);

        int width = Math.max(1, maxX - minX + 1);
        int height = Math.max(1, maxY - minY + 1);
        int depth = Math.max(1, maxZ - minZ + 1);

        int xStep = Math.max(1, width / OUTLINE_SAMPLE_TARGET);
        int yStep = Math.max(1, height / OUTLINE_SAMPLE_TARGET);
        int zStep = Math.max(1, depth / OUTLINE_SAMPLE_TARGET);
        Particle.DustOptions dustOptions = new Particle.DustOptions(Color.fromRGB(61, 189, 255), 1.0f);

        new BukkitRunnable() {
            private long elapsed = 0L;

            @Override
            public void run() {
                if (elapsed >= OUTLINE_DURATION_TICKS) {
                    cancel();
                    return;
                }
                spawnCuboidOutline(world, minX, minY, minZ, maxX, maxY, maxZ, xStep, yStep, zStep, dustOptions);
                elapsed += OUTLINE_INTERVAL_TICKS;
            }
        }.runTaskTimer((Plugin) plugin, 0L, OUTLINE_INTERVAL_TICKS);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)

    public void onSelectionToolInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isSelectionTool(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        PendingCuboidSelection selection = pendingSelections.get(player.getUniqueId());
        if (selection == null) {
            player.sendMessage(Messages.get("messages.create.cuboid-tool.no-session"));
            removeSelectionTools(player);
            return;
        }

        Action action = event.getAction();
        boolean setCornerOne = action == Action.LEFT_CLICK_BLOCK || action == Action.LEFT_CLICK_AIR;
        boolean setCornerTwo = action == Action.RIGHT_CLICK_BLOCK || action == Action.RIGHT_CLICK_AIR;
        if (!setCornerOne && !setCornerTwo) {
            return;
        }

        Location selected = resolveSelectionLocation(player, event);
        if (selected == null) {
            player.sendMessage(Messages.get("messages.create.cuboid-tool.no-target"));
            return;
        }
        Location blockLocation = selected.getBlock().getLocation();

        if (setCornerOne) {
            selection.cornerOne = blockLocation;
            sendCornerSetMessage(player, 1, blockLocation);
        } else {
            selection.cornerTwo = blockLocation;
            sendCornerSetMessage(player, 2, blockLocation);
        }

        spawnCornerMarker(blockLocation);
        if (!selection.isComplete()) {
            player.sendMessage(Messages.get("messages.create.cuboid-tool.waiting-second-corner"));
            return;
        }

        if (!isSameWorld(selection.cornerOne, selection.cornerTwo)) {
            player.sendMessage(Messages.get("messages.create.cuboid-tool.world-mismatch"));
            return;
        }

        completeSelection(player, selection);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)

    public void onPlayerDropSelectionTool(PlayerDropItemEvent event) {
        if (!isSelectionTool(event.getItemDrop().getItemStack())) {
            return;
        }
        if (!pendingSelections.containsKey(event.getPlayer().getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendMessage(Messages.get("messages.create.cuboid-tool.drop-blocked"));
    }

    @EventHandler

    public void onPlayerQuit(PlayerQuitEvent event) {
        removeSelectionTools(event.getPlayer());
        pendingSelections.remove(event.getPlayer().getUniqueId());
    }

    private void completeSelection(Player player, PendingCuboidSelection selection) {
        if (player == null || selection == null || selection.cornerOne == null || selection.cornerTwo == null) {
            return;
        }

        if (plugin.getCapturePoints().containsKey(selection.id)) {
            player.sendMessage(Messages.get("errors.id-exists"));
            clearSelection(player, true);
            return;
        }

        World world = selection.cornerOne.getWorld();
        if (world == null) {
            player.sendMessage(Messages.get("errors.create-failed"));
            clearSelection(player, true);
            return;
        }

        Map<String, String> creatingPlaceholders = new HashMap<>();
        creatingPlaceholders.put("id", selection.id);
        player.sendMessage(Messages.get("messages.create.cuboid-tool.creating", creatingPlaceholders));

        if (!plugin.canCreateMoreCapturePoints()) {
            player.sendMessage(Messages.get("errors.max-capture-points-reached", Map.of(
                "max", String.valueOf(plugin.getMaxCapturePointsLimit())
            )));
            clearSelection(player, true);
            return;
        }

        boolean createdSuccessfully = plugin.createCuboidCapturePoint(
            selection.id,
            selection.type,
            world,
            selection.cornerOne.getBlockX(),
            selection.cornerOne.getBlockY(),
            selection.cornerOne.getBlockZ(),
            selection.cornerTwo.getBlockX(),
            selection.cornerTwo.getBlockY(),
            selection.cornerTwo.getBlockZ(),
            selection.reward
        );

        if (!createdSuccessfully) {
            if (!plugin.canCreateMoreCapturePoints()) {
                player.sendMessage(Messages.get("errors.max-capture-points-reached", Map.of(
                    "max", String.valueOf(plugin.getMaxCapturePointsLimit())
                )));
            } else {
                player.sendMessage(Messages.get("errors.create-failed"));
            }
            clearSelection(player, true);
            return;
        }

        CapturePoint created = plugin.getCapturePoint(selection.id);
        if (created == null || !created.isCuboid()) {
            player.sendMessage(Messages.get("errors.create-failed"));
            clearSelection(player, true);
            return;
        }

        player.sendMessage(Messages.get("messages.create.success"));
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("width", String.valueOf(created.getCuboidWidthBlocks()));
        placeholders.put("height", String.valueOf(created.getCuboidHeightBlocks()));
        placeholders.put("depth", String.valueOf(created.getCuboidDepthBlocks()));
        player.sendMessage(Messages.get("messages.create.cuboid-info", placeholders));

        showTemporaryOutline(created);

        if (plugin.getDiscordWebhook() != null) {
            plugin.getDiscordWebhook().sendZoneCreated(
                created.getId(),
                created.getId(),
                player.getName(),
                created.getType(),
                created.getChunkRadius(),
                created.getReward()
            );
        }

        Map<String, String> completedPlaceholders = new HashMap<>();
        completedPlaceholders.put("id", created.getId());
        player.sendMessage(Messages.get("messages.create.cuboid-tool.completed", completedPlaceholders));
        clearSelection(player, true);
    }

    private void clearSelection(Player player, boolean removeTool) {
        if (player == null) {
            return;
        }
        pendingSelections.remove(player.getUniqueId());
        if (removeTool) {
            removeSelectionTools(player);
        }
    }

    private void sendCornerSetMessage(Player player, int corner, Location location) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("corner", String.valueOf(corner));
        placeholders.put("x", String.valueOf(location.getBlockX()));
        placeholders.put("y", String.valueOf(location.getBlockY()));
        placeholders.put("z", String.valueOf(location.getBlockZ()));
        player.sendMessage(Messages.get("messages.create.cuboid-tool.corner-set", placeholders));
    }

    private void spawnCornerMarker(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Location markerLocation = location.clone().add(0.5, 0.5, 0.5);
        location.getWorld().spawnParticle(Particle.END_ROD, markerLocation, 8, 0.15, 0.15, 0.15, 0.01);
    }

    private Location resolveSelectionLocation(Player player, PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            return event.getClickedBlock().getLocation();
        }
        Block targetBlock = player.getTargetBlockExact(MAX_TARGET_DISTANCE);
        if (targetBlock == null) {
            return null;
        }
        return targetBlock.getLocation();
    }

    private boolean isSameWorld(Location first, Location second) {
        if (first == null || second == null || first.getWorld() == null || second.getWorld() == null) {
            return false;
        }
        return first.getWorld().getUID().equals(second.getWorld().getUID());
    }

    private void giveSelectionTool(Player player) {
        if (player == null) {
            return;
        }

        removeSelectionTools(player);
        ItemStack selectorTool = createSelectionToolItem();
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(selectorTool);
        if (leftovers.isEmpty()) {
            return;
        }

        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.sendMessage(Messages.get("messages.create.cuboid-tool.item-dropped"));
    }

    private ItemStack createSelectionToolItem() {
        ItemStack item = new ItemStack(SELECTOR_TOOL_MATERIAL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Messages.get("messages.create.cuboid-tool.item-name"));
            List<String> lore = Messages.getList("messages.create.cuboid-tool.item-lore");
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            meta.getPersistentDataContainer().set(selectionToolKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void removeSelectionTools(Player player) {
        if (player == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isSelectionTool(stack)) {
                inventory.setItem(slot, null);
            }
        }
        if (isSelectionTool(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
    }

    private boolean isSelectionTool(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(selectionToolKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private void spawnCuboidOutline(
        World world,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        int xStep,
        int yStep,
        int zStep,
        Particle.DustOptions dustOptions
    ) {
        spawnLineX(world, minX, maxX, minY, minZ, xStep, dustOptions);
        spawnLineX(world, minX, maxX, minY, maxZ, xStep, dustOptions);
        spawnLineX(world, minX, maxX, maxY, minZ, xStep, dustOptions);
        spawnLineX(world, minX, maxX, maxY, maxZ, xStep, dustOptions);

        spawnLineZ(world, minZ, maxZ, minX, minY, zStep, dustOptions);
        spawnLineZ(world, minZ, maxZ, minX, maxY, zStep, dustOptions);
        spawnLineZ(world, minZ, maxZ, maxX, minY, zStep, dustOptions);
        spawnLineZ(world, minZ, maxZ, maxX, maxY, zStep, dustOptions);

        spawnLineY(world, minY, maxY, minX, minZ, yStep, dustOptions);
        spawnLineY(world, minY, maxY, minX, maxZ, yStep, dustOptions);
        spawnLineY(world, minY, maxY, maxX, minZ, yStep, dustOptions);
        spawnLineY(world, minY, maxY, maxX, maxZ, yStep, dustOptions);
    }

    private void spawnLineX(World world, int minX, int maxX, int y, int z, int step, Particle.DustOptions dustOptions) {
        for (int x = minX; x <= maxX; x += step) {
            spawnOutlineParticle(world, x, y, z, dustOptions);
        }
        if ((maxX - minX) % step != 0) {
            spawnOutlineParticle(world, maxX, y, z, dustOptions);
        }
    }

    private void spawnLineY(World world, int minY, int maxY, int x, int z, int step, Particle.DustOptions dustOptions) {
        for (int y = minY; y <= maxY; y += step) {
            spawnOutlineParticle(world, x, y, z, dustOptions);
        }
        if ((maxY - minY) % step != 0) {
            spawnOutlineParticle(world, x, maxY, z, dustOptions);
        }
    }

    private void spawnLineZ(World world, int minZ, int maxZ, int x, int y, int step, Particle.DustOptions dustOptions) {
        for (int z = minZ; z <= maxZ; z += step) {
            spawnOutlineParticle(world, x, y, z, dustOptions);
        }
        if ((maxZ - minZ) % step != 0) {
            spawnOutlineParticle(world, x, y, maxZ, dustOptions);
        }
    }

    private void spawnOutlineParticle(World world, int x, int y, int z, Particle.DustOptions dustOptions) {
        Location location = new Location(world, x + 0.5, y + 0.5, z + 0.5);
        world.spawnParticle(Particle.DUST, location, 1, 0.0, 0.0, 0.0, 0.0, dustOptions);
    }

    private static final class PendingCuboidSelection {
        private final String id;
        private final String type;
        private final double reward;
        private Location cornerOne;
        private Location cornerTwo;

        private PendingCuboidSelection(String id, String type, double reward) {
            this.id = id;
            this.type = type;
            this.reward = reward;
        }

        private boolean isComplete() {
            return this.cornerOne != null && this.cornerTwo != null;
        }
    }
}

