package com.logichh.capturezones;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ZoneItemRewardEditor implements Listener {
    private static final int EDITOR_SIZE = 54;

    private final CaptureZones plugin;

    public ZoneItemRewardEditor(CaptureZones plugin) {
        this.plugin = plugin;
    }

    public void openEditor(Player player, String zoneId) {
        if (player == null || zoneId == null || zoneId.trim().isEmpty()) {
            return;
        }

        CapturePoint point = plugin.getCapturePoint(zoneId);
        if (point == null) {
            player.sendMessage(Messages.get("errors.zone-not-found", Map.of("id", zoneId)));
            return;
        }

        RewardEditorHolder holder = new RewardEditorHolder(zoneId.trim());
        String title = Messages.get("gui.zoneconfig.itemrewards.title", Map.of(
            "zone", point.getName() != null && !point.getName().trim().isEmpty() ? point.getName() : zoneId
        ));
        Inventory inventory = Bukkit.createInventory(holder, EDITOR_SIZE, plugin.colorize(title));
        holder.setInventory(inventory);

        List<ItemStack> configuredStacks = plugin.getConfiguredItemRewardStacks(zoneId);
        int slot = 0;
        for (ItemStack stack : configuredStacks) {
            if (slot >= EDITOR_SIZE) {
                break;
            }
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                continue;
            }
            inventory.setItem(slot++, stack.clone());
        }

        player.openInventory(inventory);
        player.sendMessage(Messages.get("messages.zoneconfig-itemrewards.opened", Map.of("zone", zoneId)));
    }

    @EventHandler

    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        if (inventory == null || !(inventory.getHolder() instanceof RewardEditorHolder)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        RewardEditorHolder holder = (RewardEditorHolder) inventory.getHolder();
        persistEditorContents(player, holder.getZoneId(), inventory);
    }

    private void persistEditorContents(Player player, String zoneId, Inventory inventory) {
        ZoneConfigManager zoneConfigManager = plugin.getZoneConfigManager();
        if (zoneConfigManager == null) {
            player.sendMessage(Messages.get("errors.zoneconfig-itemrewards-save-failed", Map.of(
                "error", "Zone config manager unavailable"
            )));
            returnEditorItems(player, inventory);
            return;
        }

        List<Map<String, Object>> serializedStacks = new ArrayList<>();
        List<ItemStack> editorItems = new ArrayList<>();
        int totalAmount = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                continue;
            }
            ItemStack clone = stack.clone();
            serializedStacks.add(new LinkedHashMap<>(clone.serialize()));
            editorItems.add(clone);
            totalAmount += clone.getAmount();
        }

        try {
            boolean itemsSaved = zoneConfigManager.setZoneSetting(zoneId, "rewards.item-rewards.items", serializedStacks);
            boolean enabledSaved = zoneConfigManager.setZoneSetting(zoneId, "rewards.item-rewards.enabled", !serializedStacks.isEmpty());
            boolean legacyDisabled = zoneConfigManager.setZoneSetting(zoneId, "rewards.item-payout", "None");
            if (!itemsSaved || !enabledSaved || !legacyDisabled) {
                player.sendMessage(Messages.get("errors.zoneconfig-itemrewards-save-failed", Map.of(
                    "error", "Failed to write zone config"
                )));
                returnEditorItems(player, editorItems);
                return;
            }
        } catch (Exception e) {
            player.sendMessage(Messages.get("errors.zoneconfig-itemrewards-save-failed", Map.of(
                "error", e.getMessage() == null ? "unknown error" : e.getMessage()
            )));
            plugin.getLogger().warning("Failed to save item reward editor for zone '" + zoneId + "': " + e.getMessage());
            returnEditorItems(player, editorItems);
            return;
        }

        returnEditorItems(player, editorItems);

        plugin.getLogger().info(
            "Zone item rewards updated by " + player.getName() + " for zone '" + zoneId + "' (" +
                serializedStacks.size() + " stacks, total amount " + totalAmount + ")."
        );

        if (serializedStacks.isEmpty()) {
            player.sendMessage(Messages.get("messages.zoneconfig-itemrewards-cleared", Map.of("zone", zoneId)));
            return;
        }
        player.sendMessage(Messages.get("messages.zoneconfig-itemrewards-saved", Map.of(
            "zone", zoneId,
            "stacks", String.valueOf(serializedStacks.size()),
            "amount", String.valueOf(totalAmount)
        )));
    }

    private void returnEditorItems(Player player, Inventory inventory) {
        if (inventory == null) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack stack : inventory.getContents()) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                continue;
            }
            items.add(stack.clone());
        }
        returnEditorItems(player, items);
    }

    private void returnEditorItems(Player player, List<ItemStack> items) {
        if (player == null || !player.isOnline() || items == null || items.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        if (leftovers.isEmpty() || player.getWorld() == null) {
            return;
        }
        for (ItemStack leftover : leftovers.values()) {
            if (leftover == null || leftover.getType() == Material.AIR || leftover.getAmount() <= 0) {
                continue;
            }
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private static final class RewardEditorHolder implements InventoryHolder {
        private final String zoneId;
        private Inventory inventory;

        private RewardEditorHolder(String zoneId) {
            this.zoneId = zoneId;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private String getZoneId() {
            return zoneId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}

