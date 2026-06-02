package com.logichh.capturezones;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

import java.io.File;
import java.util.*;

public class ShopManager {
    
    private final CaptureZones plugin;
    private final Map<String, ShopData> shops;
    private final Map<String, DynamicPricing> pricingEngines;
    private final File shopsFolder;
    private org.bukkit.scheduler.BukkitTask restockTask;
    private org.bukkit.scheduler.BukkitTask pricingTask;
    
    public ShopManager(CaptureZones plugin) {
        this.plugin = plugin;
        this.shops = new HashMap<>();
        this.pricingEngines = new HashMap<>();
        this.shopsFolder = new File(plugin.getDataFolder(), "shops");
        
        if (!shopsFolder.exists()) {
            shopsFolder.mkdirs();
        }
        
        loadAllShops();
        startPeriodicTasks();
    }

    public void loadAllShops() {
        shops.clear();
        pricingEngines.clear();
        
        File[] files = shopsFolder.listFiles((dir, name) -> name.endsWith("_shop.yml"));
        if (files != null) {
            for (File file : files) {
                String zoneId = file.getName().replace("_shop.yml", "");
                ShopData shop = ShopData.load(zoneId, file);
                shops.put(zoneId, shop);
                pricingEngines.put(zoneId, new DynamicPricing(shop));
                
                plugin.getLogger().info("Loaded shop for zone: " + zoneId);
            }
        }
    }

    public ShopData getShop(String zoneId) {
        return shops.computeIfAbsent(zoneId, id -> {
            ShopData shop = new ShopData(id);
            applyConfiguredDefaults(shop);
            pricingEngines.put(id, new DynamicPricing(shop));
            return shop;
        });
    }

    private void applyConfiguredDefaults(ShopData shop) {
        if (shop == null) {
            return;
        }
        String defaultsPath = "shops.defaults.";
        shop.setAccessMode(parseEnum(
            ShopData.AccessMode.class,
            plugin.getConfig().getString(defaultsPath + "access-mode", ShopData.AccessMode.ALWAYS.name()),
            ShopData.AccessMode.ALWAYS
        ));
        shop.setStockSystem(parseEnum(
            ShopData.StockSystem.class,
            plugin.getConfig().getString(defaultsPath + "stock-system", ShopData.StockSystem.INFINITE.name()),
            ShopData.StockSystem.INFINITE
        ));
        shop.setLayoutMode(parseEnum(
            ShopData.LayoutMode.class,
            plugin.getConfig().getString(defaultsPath + "layout-mode", ShopData.LayoutMode.SINGLE_PAGE.name()),
            ShopData.LayoutMode.SINGLE_PAGE
        ));
        shop.setPricingMode(parseEnum(
            ShopData.PricingMode.class,
            plugin.getConfig().getString(defaultsPath + "pricing-mode", ShopData.PricingMode.FIXED.name()),
            ShopData.PricingMode.FIXED
        ));
        shop.setRestockSchedule(parseEnum(
            ShopData.RestockSchedule.class,
            plugin.getConfig().getString(defaultsPath + "restock-schedule", ShopData.RestockSchedule.HOURLY.name()),
            ShopData.RestockSchedule.HOURLY
        ));
        shop.setDynamicSensitivity(plugin.getConfig().getDouble("shops.dynamic-pricing.sensitivity", 0.1));
        shop.setDynamicMin(plugin.getConfig().getDouble("shops.dynamic-pricing.min-multiplier", 0.5));
        shop.setDynamicMax(plugin.getConfig().getDouble("shops.dynamic-pricing.max-multiplier", 2.0));
    }

    private <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value, T defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return defaultValue;
        }
    }

    public void saveShop(String zoneId) {
        ShopData shop = shops.get(zoneId);
        if (shop != null) {
            File file = new File(shopsFolder, zoneId + "_shop.yml");
            shop.save(file);
        }
    }

    public void saveAllShops() {
        for (String zoneId : shops.keySet()) {
            saveShop(zoneId);
        }
    }

    public void deleteShop(String zoneId) {
        shops.remove(zoneId);
        pricingEngines.remove(zoneId);
        File file = new File(shopsFolder, zoneId + "_shop.yml");
        if (file.exists()) {
            file.delete();
        }
    }

    public boolean canAccessShop(Player player, String zoneId) {
        ShopData shop = shops.get(zoneId);
        if (shop == null || !shop.isEnabled()) {
            return false;
        }
        
        CapturePoint zone = plugin.getCapturePoint(zoneId);
        if (zone == null) {
            return false;
        }
        if (!plugin.isWithinZone(zone, player.getLocation())) {
            return false;
        }
        ShopData.AccessMode accessMode = shop.getAccessMode();

        if (accessMode == ShopData.AccessMode.CONTROLLED_ONLY && plugin.isPointActive(zoneId)) {
            return false;
        }
        
        if (accessMode == ShopData.AccessMode.ALWAYS) {
            return true;
        }
        
        CaptureOwner controllingOwner = zone.getControllingOwner();
        String controllingTown = zone.getControllingTown();
        if ((controllingOwner == null) && (controllingTown == null || controllingTown.isEmpty())) {
            return accessMode == ShopData.AccessMode.ALWAYS;
        }
        
        if (accessMode == ShopData.AccessMode.CONTROLLED_ONLY) {
            if (controllingOwner != null) {
                return plugin.doesPlayerMatchOwner(player, controllingOwner);
            }
            return plugin.doesPlayerMatchOwner(player, controllingTown);
        }
        
        if (accessMode == ShopData.AccessMode.OWNER_ONLY) {
            if (controllingOwner != null) {
                return plugin.doesPlayerMatchOwner(player, controllingOwner);
            }
            return plugin.doesPlayerMatchOwner(player, controllingTown);
        }
        
        return false;
    }

    public boolean isShopBlockedByCapture(String zoneId) {
        ShopData shop = shops.get(zoneId);
        if (shop == null) {
            return false;
        }
        return shop.getAccessMode() == ShopData.AccessMode.CONTROLLED_ONLY && plugin.isPointActive(zoneId);
    }

    public boolean processBuy(Player player, String zoneId, int slot, int quantity) {
        ShopEconomyAdapter economy = plugin.getOrCreateShopEconomyAdapter();
        if (economy == null || !economy.isAvailable()) {
            player.sendMessage(Messages.get("errors.shop.no-account"));
            return false;
        }
        ShopData shop = shops.get(zoneId);
        if (shop == null) return false;
        
        ShopItemConfig item = shop.getItem(slot);
        if (item == null || !item.isBuyable()) {
            player.sendMessage(Messages.get("errors.shop.not-buyable"));
            return false;
        }
        if (shop.getStockSystem() != ShopData.StockSystem.INFINITE && !item.hasStock(quantity)) {
            player.sendMessage(Messages.get("errors.shop.out-of-stock"));
            return false;
        }
        double totalCost = item.getEffectiveBuyPrice() * quantity;
        ItemStack itemStack = createPurchaseItem(item, quantity);
        if (!canFit(player.getInventory(), itemStack, quantity)) {
            player.sendMessage(Messages.get("errors.shop.inventory-full"));
            return false;
        }
        try {
            if (!economy.hasAccount(player)) {
                player.sendMessage(Messages.get("errors.shop.no-account"));
                return false;
            }
            
            if (!economy.has(player, totalCost)) {
                player.sendMessage(Messages.get("errors.shop.insufficient-funds", Map.of(
                    "cost", String.format("%.2f", totalCost)
                )));
                return false;
            }
            if (!economy.withdraw(player, totalCost, "Shop purchase")) {
                player.sendMessage(Messages.get("errors.shop.transaction-failed"));
                return false;
            }
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack);
            if (!leftovers.isEmpty()) {
                economy.deposit(player, totalCost, "Shop purchase refund");
                player.sendMessage(Messages.get("errors.shop.inventory-full"));
                return false;
            }
            if (shop.getStockSystem() != ShopData.StockSystem.INFINITE) {
                item.removeStock(quantity);
            }
            DynamicPricing pricing = pricingEngines.get(zoneId);
            if (pricing != null) {
                pricing.onBuyTransaction(item, quantity);
            }
            shop.recordBuy(quantity, totalCost);
            saveShop(zoneId);
            
            player.sendMessage(Messages.get("messages.shop.buy-success", Map.of(
                "quantity", String.valueOf(quantity),
                "item", item.getMaterial().name(),
                "cost", String.format("%.2f", totalCost)
            )));
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Shop buy transaction failed: " + e.getMessage());
            player.sendMessage(Messages.get("errors.shop.transaction-failed"));
            return false;
        }
    }

    public boolean processSell(Player player, String zoneId, int slot, int quantity) {
        ShopEconomyAdapter economy = plugin.getOrCreateShopEconomyAdapter();
        if (economy == null || !economy.isAvailable()) {
            player.sendMessage(Messages.get("errors.shop.no-account"));
            return false;
        }
        ShopData shop = shops.get(zoneId);
        if (shop == null) return false;
        
        ShopItemConfig item = shop.getItem(slot);
        if (item == null || !item.isSellable()) {
            player.sendMessage(Messages.get("errors.shop.not-sellable"));
            return false;
        }
        ItemStack checkStack = new ItemStack(item.getMaterial());
        int playerAmount = 0;
        for (ItemStack invItem : player.getInventory().getStorageContents()) {
            if (invItem != null && invItem.getType() == item.getMaterial()) {
                playerAmount += invItem.getAmount();
            }
        }
        
        if (playerAmount < quantity) {
            player.sendMessage(Messages.get("errors.shop.insufficient-items"));
            return false;
        }
        double totalEarnings = item.getEffectiveSellPrice() * quantity;
        
        try {
            if (!economy.hasAccount(player)) {
                player.sendMessage(Messages.get("errors.shop.no-account"));
                return false;
            }
            int remaining = quantity;
            ItemStack[] storage = player.getInventory().getStorageContents();
            for (int i = 0; i < storage.length && remaining > 0; i++) {
                ItemStack invItem = storage[i];
                if (invItem != null && invItem.getType() == item.getMaterial()) {
                    int removeAmount = Math.min(remaining, invItem.getAmount());
                    int newAmount = invItem.getAmount() - removeAmount;
                    if (newAmount <= 0) {
                        player.getInventory().setItem(i, null);
                    } else {
                        invItem.setAmount(newAmount);
                    }
                    remaining -= removeAmount;
                }
            }
            if (!economy.deposit(player, totalEarnings, "Shop sale")) {
                player.sendMessage(Messages.get("errors.shop.transaction-failed"));
                return false;
            }
            if (shop.getStockSystem() != ShopData.StockSystem.INFINITE) {
                item.addStock(quantity);
            }
            DynamicPricing pricing = pricingEngines.get(zoneId);
            if (pricing != null) {
                pricing.onSellTransaction(item, quantity);
            }
            shop.recordSell(quantity, totalEarnings);
            saveShop(zoneId);
            
            player.sendMessage(Messages.get("messages.shop.sell-success", Map.of(
                "quantity", String.valueOf(quantity),
                "item", item.getMaterial().name(),
                "earnings", String.format("%.2f", totalEarnings)
            )));
            
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().warning("Shop sell transaction failed: " + e.getMessage());
            player.sendMessage(Messages.get("errors.shop.transaction-failed"));
            return false;
        }
    }

    public void restockShop(String zoneId) {
        ShopData shop = shops.get(zoneId);
        if (shop != null) {
            shop.performRestock();
            saveShop(zoneId);
        }
    }

    private void startPeriodicTasks() {
        restockTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<String, ShopData> entry : shops.entrySet()) {
                ShopData shop = entry.getValue();
                if (shop.isEnabled() && shop.needsRestock()) {
                    shop.performRestock();
                    saveShop(entry.getKey());
                    plugin.getLogger().info("Auto-restocked shop: " + entry.getKey());
                }
            }
        }, 6000L, 6000L); // Every 5 minutes
        pricingTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<String, DynamicPricing> entry : pricingEngines.entrySet()) {
                entry.getValue().updateAllPrices();
            }
        }, 72000L, 72000L); // Every hour
    }

    public void shutdown() {
        if (restockTask != null && !restockTask.isCancelled()) {
            restockTask.cancel();
        }
        if (pricingTask != null && !pricingTask.isCancelled()) {
            pricingTask.cancel();
        }
        saveAllShops();
    }
    
    public Map<String, ShopData> getShops() {
        return shops;
    }
    
    public DynamicPricing getPricingEngine(String zoneId) {
        return pricingEngines.get(zoneId);
    }

    private boolean canFit(Inventory inventory, ItemStack stack, int quantity) {
        int maxStack = Math.max(1, stack.getMaxStackSize());
        int remaining = quantity;

        for (ItemStack invItem : inventory.getStorageContents()) {
            if (remaining <= 0) {
                return true;
            }
            if (invItem == null || invItem.getType() == Material.AIR) {
                remaining -= maxStack;
                continue;
            }
            if (invItem.isSimilar(stack)) {
                remaining -= Math.max(0, maxStack - invItem.getAmount());
            }
        }

        return remaining <= 0;
    }

    private ItemStack createPurchaseItem(ShopItemConfig item, int quantity) {
        ItemStack base = item.getDisplayItem() != null ? item.getDisplayItem().clone() : new ItemStack(item.getMaterial());
        if (item.getDisplayItem() == null && item.getDisplayName() != null && !item.getDisplayName().isEmpty()) {
            ItemMeta meta = base.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(item.getDisplayName());
                base.setItemMeta(meta);
            }
        }
        base.setAmount(quantity);
        return base;
    }
}

