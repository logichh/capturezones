package com.logichh.capturezones;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class ShopData {
    
    public enum AccessMode {
        ALWAYS,           // Anyone in zone can use
        CONTROLLED_ONLY,  // Only players whose town controls the zone
        OWNER_ONLY        // Only the controlling town
    }
    
    public enum StockSystem {
        INFINITE,  // Unlimited supply
        LIMITED,   // Refills on schedule
        FINITE     // One-time stock
    }
    
    public enum LayoutMode {
        SINGLE_PAGE,  // All items in one GUI
        CATEGORIES,   // Organized by category
        PAGINATED     // Multiple pages
    }
    
    public enum PricingMode {
        FIXED,    // Static prices
        DYNAMIC   // Fluctuating prices
    }
    
    public enum RestockSchedule {
        HOURLY,
        DAILY,
        WEEKLY,
        MANUAL
    }
    
    private final String zoneId;
    private boolean enabled;
    private AccessMode accessMode;
    private StockSystem stockSystem;
    private LayoutMode layoutMode;
    private PricingMode pricingMode;
    private RestockSchedule restockSchedule;
    private long lastRestock;
    
    // Dynamic pricing settings
    private double dynamicSensitivity;  // How much prices change
    private double dynamicMin;          // Minimum multiplier
    private double dynamicMax;          // Maximum multiplier
    
    // Items
    private final Map<Integer, ShopItemConfig> items;
    private final Map<String, List<ShopItemConfig>> categories;
    
    // Transaction history
    private int totalBuys;
    private int totalSells;
    private double totalRevenue;
    
    public ShopData(String zoneId) {
        this.zoneId = zoneId;
        this.enabled = false;
        this.accessMode = AccessMode.ALWAYS;
        this.stockSystem = StockSystem.INFINITE;
        this.layoutMode = LayoutMode.SINGLE_PAGE;
        this.pricingMode = PricingMode.FIXED;
        this.restockSchedule = RestockSchedule.HOURLY;
        this.lastRestock = System.currentTimeMillis();
        this.dynamicSensitivity = 0.1;
        this.dynamicMin = 0.5;
        this.dynamicMax = 2.0;
        this.items = new HashMap<>();
        this.categories = new HashMap<>();
        this.totalBuys = 0;
        this.totalSells = 0;
        this.totalRevenue = 0.0;
    }
    
    // Getters

    public String getZoneId() { return zoneId; }

    public boolean isEnabled() { return enabled; }

    public AccessMode getAccessMode() { return accessMode; }

    public StockSystem getStockSystem() { return stockSystem; }

    public LayoutMode getLayoutMode() { return layoutMode; }

    public PricingMode getPricingMode() { return pricingMode; }

    public RestockSchedule getRestockSchedule() { return restockSchedule; }

    public long getLastRestock() { return lastRestock; }

    public double getDynamicSensitivity() { return dynamicSensitivity; }

    public double getDynamicMin() { return dynamicMin; }

    public double getDynamicMax() { return dynamicMax; }

    public Map<Integer, ShopItemConfig> getItems() { return items; }

    public int getTotalBuys() { return totalBuys; }

    public int getTotalSells() { return totalSells; }

    public double getTotalRevenue() { return totalRevenue; }
    
    // Setters

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public void setAccessMode(AccessMode mode) { this.accessMode = mode; }

    public void setStockSystem(StockSystem system) { this.stockSystem = system; }

    public void setLayoutMode(LayoutMode mode) { this.layoutMode = mode; }

    public void setPricingMode(PricingMode mode) { this.pricingMode = mode; }

    public void setRestockSchedule(RestockSchedule schedule) { this.restockSchedule = schedule; }

    public void setLastRestock(long time) { this.lastRestock = time; }

    public void setDynamicSensitivity(double sensitivity) { this.dynamicSensitivity = sensitivity; }

    public void setDynamicMin(double min) { this.dynamicMin = min; }

    public void setDynamicMax(double max) { this.dynamicMax = max; }
    
    // Item management

    public void addItem(ShopItemConfig item) {
        items.put(item.getSlot(), item);
        
        String category = item.getCategory();
        categories.computeIfAbsent(category, k -> new ArrayList<>()).add(item);
    }
    
    public void removeItem(int slot) {
        ShopItemConfig item = items.remove(slot);
        if (item != null) {
            List<ShopItemConfig> categoryItems = categories.get(item.getCategory());
            if (categoryItems != null) {
                categoryItems.remove(item);
            }
        }
    }
    
    public ShopItemConfig getItem(int slot) {
        return items.get(slot);
    }
    
    public List<ShopItemConfig> getItemsByCategory(String category) {
        return categories.getOrDefault(category, new ArrayList<>());
    }
    
    public Set<String> getCategories() {
        return categories.keySet();
    }
    
    // Transaction tracking

    public void recordBuy(int quantity, double amount) {
        totalBuys += quantity;
        totalRevenue += amount;
    }
    
    public void recordSell(int quantity, double amount) {
        totalSells += quantity;
        totalRevenue -= amount;
    }
    
    // Restock logic

    public boolean needsRestock() {
        if (stockSystem != StockSystem.LIMITED) return false;
        if (restockSchedule == RestockSchedule.MANUAL) return false;
        
        long now = System.currentTimeMillis();
        long elapsed = now - lastRestock;
        
        switch (restockSchedule) {
            case HOURLY:
                return elapsed >= 3600000; // 1 hour
            case DAILY:
                return elapsed >= 86400000; // 24 hours
            case WEEKLY:
                return elapsed >= 604800000; // 7 days
            default:
                return false;
        }
    }
    
    public void performRestock() {
        for (ShopItemConfig item : items.values()) {
            item.resetStock();
        }
        lastRestock = System.currentTimeMillis();
    }
    
    // Persistence

    public void save(File file) {
        YamlConfiguration config = new YamlConfiguration();
        
        config.set("enabled", enabled);
        config.set("access-mode", accessMode.name());
        config.set("stock-system", stockSystem.name());
        config.set("layout-mode", layoutMode.name());
        config.set("pricing-mode", pricingMode.name());
        config.set("restock-schedule", restockSchedule.name());
        config.set("last-restock", lastRestock);
        
        config.set("dynamic-pricing.sensitivity", dynamicSensitivity);
        config.set("dynamic-pricing.min-multiplier", dynamicMin);
        config.set("dynamic-pricing.max-multiplier", dynamicMax);
        
        config.set("statistics.total-buys", totalBuys);
        config.set("statistics.total-sells", totalSells);
        config.set("statistics.total-revenue", totalRevenue);
        
        int index = 0;
        for (ShopItemConfig item : items.values()) {
            ConfigurationSection itemSection = config.createSection("items." + index);
            item.save(itemSection);
            index++;
        }
        
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public static ShopData load(String zoneId, File file) {
        ShopData data = new ShopData(zoneId);
        
        if (!file.exists()) {
            return data;
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        data.setEnabled(config.getBoolean("enabled", false));
        data.setAccessMode(parseEnum(AccessMode.class, config.getString("access-mode", "ALWAYS"), AccessMode.ALWAYS));
        data.setStockSystem(parseEnum(StockSystem.class, config.getString("stock-system", "INFINITE"), StockSystem.INFINITE));
        data.setLayoutMode(parseEnum(LayoutMode.class, config.getString("layout-mode", "SINGLE_PAGE"), LayoutMode.SINGLE_PAGE));
        data.setPricingMode(parseEnum(PricingMode.class, config.getString("pricing-mode", "FIXED"), PricingMode.FIXED));
        data.setRestockSchedule(parseEnum(RestockSchedule.class, config.getString("restock-schedule", "HOURLY"), RestockSchedule.HOURLY));
        data.setLastRestock(config.getLong("last-restock", System.currentTimeMillis()));
        
        data.setDynamicSensitivity(config.getDouble("dynamic-pricing.sensitivity", 0.1));
        data.setDynamicMin(config.getDouble("dynamic-pricing.min-multiplier", 0.5));
        data.setDynamicMax(config.getDouble("dynamic-pricing.max-multiplier", 2.0));
        
        data.totalBuys = config.getInt("statistics.total-buys", 0);
        data.totalSells = config.getInt("statistics.total-sells", 0);
        data.totalRevenue = config.getDouble("statistics.total-revenue", 0.0);
        
        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection != null) {
                    ShopItemConfig item = ShopItemConfig.load(itemSection);
                    data.addItem(item);
                }
            }
        }
        
        return data;
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> enumClass, String value, T defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }
}

