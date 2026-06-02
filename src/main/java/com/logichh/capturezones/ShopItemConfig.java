package com.logichh.capturezones;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;

public class ShopItemConfig {
    private final Material material;
    private int slot;
    private boolean buyable;
    private boolean sellable;
    private double buyPrice;
    private double sellPrice;
    private int stock;
    private int maxStock;
    private String category;
    private String displayName;
    private ItemStack displayItem;
    
    // Dynamic pricing state
    private double priceMultiplier;
    private long lastPriceUpdate;
    private int transactionCount;
    
    public ShopItemConfig(Material material, int slot) {
        this.material = material;
        this.slot = slot;
        this.buyable = false;
        this.sellable = false;
        this.buyPrice = 0.0;
        this.sellPrice = 0.0;
        this.stock = 0;
        this.maxStock = -1; // -1 = infinite
        this.category = "GENERAL";
        this.displayName = null;
        this.priceMultiplier = 1.0;
        this.lastPriceUpdate = System.currentTimeMillis();
        this.transactionCount = 0;
    }
    
    // Getters

    public Material getMaterial() { return material; }

    public int getSlot() { return slot; }

    public boolean isBuyable() { return buyable; }

    public boolean isSellable() { return sellable; }

    public double getBuyPrice() { return buyPrice; }

    public double getSellPrice() { return sellPrice; }

    public int getStock() { return stock; }

    public int getMaxStock() { return maxStock; }

    public String getCategory() { return category; }

    public String getDisplayName() { return displayName; }

    public ItemStack getDisplayItem() { return displayItem; }

    public double getPriceMultiplier() { return priceMultiplier; }

    public long getLastPriceUpdate() { return lastPriceUpdate; }

    public int getTransactionCount() { return transactionCount; }
    
    // Setters

    public void setBuyable(boolean buyable) { this.buyable = buyable; }

    public void setSellable(boolean sellable) { this.sellable = sellable; }

    public void setBuyPrice(double buyPrice) { this.buyPrice = buyPrice; }

    public void setSellPrice(double sellPrice) { this.sellPrice = sellPrice; }

    public void setStock(int stock) { this.stock = Math.max(0, stock); }

    public void setMaxStock(int maxStock) { this.maxStock = maxStock; }

    public void setSlot(int slot) { this.slot = slot; }

    public void setCategory(String category) { this.category = category; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public void setDisplayItem(ItemStack displayItem) { this.displayItem = displayItem; }

    public void setPriceMultiplier(double multiplier) { this.priceMultiplier = multiplier; }

    public void setLastPriceUpdate(long time) { this.lastPriceUpdate = time; }

    public void setTransactionCount(int count) { this.transactionCount = count; }
    
    // Utility methods

    public boolean hasStock(int amount) {
        return maxStock == -1 || stock >= amount;
    }
    
    public void addStock(int amount) {
        if (maxStock == -1) return; // Infinite stock
        stock = Math.min(stock + amount, maxStock);
    }
    
    public void removeStock(int amount) {
        if (maxStock == -1) return; // Infinite stock
        stock = Math.max(0, stock - amount);
    }
    
    public void resetStock() {
        if (maxStock > 0) {
            stock = maxStock;
        }
    }
    
    public double getEffectiveBuyPrice() {
        return buyPrice * priceMultiplier;
    }
    
    public double getEffectiveSellPrice() {
        return sellPrice * priceMultiplier;
    }
    
    public boolean isInStock() {
        return maxStock == -1 || stock > 0;
    }
    
    public void incrementTransactions() {
        transactionCount++;
    }
    
    // Serialization

    public void save(ConfigurationSection section) {
        section.set("material", material.name());
        section.set("slot", slot);
        section.set("buyable", buyable);
        section.set("sellable", sellable);
        section.set("buy-price", buyPrice);
        section.set("sell-price", sellPrice);
        section.set("stock", stock);
        section.set("max-stock", maxStock);
        section.set("category", category);
        section.set("display-name", displayName);
        if (displayItem != null) {
            section.set("display-item", displayItem);
        }
        section.set("price-multiplier", priceMultiplier);
        section.set("last-price-update", lastPriceUpdate);
        section.set("transaction-count", transactionCount);
    }
    
    public static ShopItemConfig load(ConfigurationSection section) {
        Material material = Material.valueOf(section.getString("material", "STONE"));
        int slot = section.getInt("slot", 0);
        
        ShopItemConfig item = new ShopItemConfig(material, slot);
        item.setBuyable(section.getBoolean("buyable", false));
        item.setSellable(section.getBoolean("sellable", false));
        item.setBuyPrice(section.getDouble("buy-price", 0.0));
        item.setSellPrice(section.getDouble("sell-price", 0.0));
        item.setStock(section.getInt("stock", 0));
        item.setMaxStock(section.getInt("max-stock", -1));
        item.setCategory(section.getString("category", "GENERAL"));
        item.setDisplayName(section.getString("display-name", null));
        ItemStack displayItem = section.getItemStack("display-item");
        if (displayItem != null) {
            item.setDisplayItem(displayItem);
        }
        item.setPriceMultiplier(section.getDouble("price-multiplier", 1.0));
        item.setLastPriceUpdate(section.getLong("last-price-update", System.currentTimeMillis()));
        item.setTransactionCount(section.getInt("transaction-count", 0));
        
        return item;
    }
}

