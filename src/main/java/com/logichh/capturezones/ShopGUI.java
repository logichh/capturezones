package com.logichh.capturezones;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ShopGUI {
    
    private static final int[] QUANTITY_OPTIONS = {1, 2, 3, 5, 10, 16, 32, 64};
    
    private final CaptureZones plugin;
    private final ShopData shop;
    private final String zoneId;
    private final Player player;
    private Inventory currentInventory;
    
    // GUI state
    private GUIState currentState = GUIState.BROWSING;
    private int currentPage = 0;
    private String currentCategory = null;
    private ShopItemConfig selectedItem = null;
    private boolean buyMode = true; // true = buy, false = sell
    private boolean paginated = false;
    
    // Maps display slots to actual shop items

    private Map<Integer, ShopItemConfig> slotToItemMap = new HashMap<>();

    private Map<Integer, Integer> slotToQuantityMap = new HashMap<>();
    
    public enum GUIState {
        BROWSING,          // Main shop or paginated view
        QUANTITY_SELECT    // Selecting quantity to buy/sell
    }
    
    public ShopGUI(CaptureZones plugin, String zoneId, ShopData shop, Player player) {
        this.plugin = plugin;
        this.shop = shop;
        this.zoneId = zoneId;
        this.player = player;
    }
    
    public void open() {
        List<ShopItemConfig> allItems = new ArrayList<>(shop.getItems().values());
        ShopData.LayoutMode layoutMode = shop.getLayoutMode();
        
        if (layoutMode == ShopData.LayoutMode.SINGLE_PAGE) {
            openSinglePage();
            return;
        }

        if (layoutMode == ShopData.LayoutMode.PAGINATED && allItems.size() > 0) {
            openPage(0);
            return;
        }
        
        // If 45 items or less, show in single page (no navigation needed)
        // If more than 45, use pagination
        if (allItems.size() <= 45) {
            openSinglePage();
        } else {
            openPage(0);
        }
    }
    
    private void openSinglePage() {
        slotToItemMap.clear();
        slotToQuantityMap.clear();
        currentState = GUIState.BROWSING;
        currentPage = 0;
        paginated = false;
        
        Inventory inv = Bukkit.createInventory(null, 54, Messages.get("gui.shop.title", Map.of("zone", zoneId)));
        
        // Add shop items to their configured slots
        for (ShopItemConfig item : shop.getItems().values()) {
            int slot = item.getSlot();
            if (slot < 0 || slot >= inv.getSize()) {
                continue;
            }
            inv.setItem(slot, createShopItemDisplay(item));
            slotToItemMap.put(slot, item);
        }
        
        applyFillers(inv);
        
        currentInventory = inv;
        player.openInventory(inv);
    }
    
    public void openPage(int page) {
        slotToItemMap.clear();
        slotToQuantityMap.clear();
        currentState = GUIState.BROWSING;
        this.currentPage = page;
        paginated = true;
        
        List<ShopItemConfig> items = new ArrayList<>(shop.getItems().values());
        items.sort(Comparator.comparingInt(ShopItemConfig::getSlot));
        
        int itemsPerPage = 45; // 5 rows of 9 (leave bottom row for navigation)
        int totalPages = (int) Math.ceil((double) items.size() / itemsPerPage);
        
        if (page < 0) page = 0;
        if (page >= totalPages) page = Math.max(0, totalPages - 1);
        this.currentPage = page;
        
        String title = Messages.get("gui.shop.page-title", Map.of("page", String.valueOf(page + 1)));
        Inventory inv = Bukkit.createInventory(null, 54, title);
        
        // Add items for this page - map display slots to actual items
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, items.size());
        
        for (int i = startIndex; i < endIndex; i++) {
            ShopItemConfig item = items.get(i);
            int displaySlot = i - startIndex;
            inv.setItem(displaySlot, createShopItemDisplay(item));
            slotToItemMap.put(displaySlot, item);
        }
        
        // Navigation buttons in bottom row
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Messages.get("gui.shop.previous-page"));
                prev.setItemMeta(meta);
            }
            inv.setItem(48, prev);
        }
        
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Messages.get("gui.shop.next-page"));
                next.setItemMeta(meta);
            }
            inv.setItem(50, next);
        }
        
        applyFillers(inv);
        
        currentInventory = inv;
        player.openInventory(inv);
    }
    
    public void openQuantitySelector(ShopItemConfig item, boolean buyMode) {
        slotToItemMap.clear();
        slotToQuantityMap.clear();
        currentState = GUIState.QUANTITY_SELECT;
        this.selectedItem = item;
        this.buyMode = buyMode;
        
        String itemName = item.getDisplayName() != null ? item.getDisplayName() : item.getMaterial().name();
        Inventory inv = Bukkit.createInventory(null, 27, 
            Messages.get("gui.shop.quantity-title", Map.of("item", itemName)));
        
        // Center row for quantity options
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        
        for (int i = 0; i < Math.min(QUANTITY_OPTIONS.length, slots.length); i++) {
            int quantity = QUANTITY_OPTIONS[i];
            slotToQuantityMap.put(slots[i], quantity);
            
            boolean available = true;
            if (buyMode && shop.getStockSystem() != ShopData.StockSystem.INFINITE && !item.hasStock(quantity)) {
                available = false;
            }
            
            Material paneMaterial = buyMode ? Material.GREEN_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            if (!available) {
                paneMaterial = Material.GRAY_STAINED_GLASS_PANE;
            }
            
            ItemStack pane = new ItemStack(paneMaterial);
            ItemMeta meta = pane.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(Messages.get(buyMode ? "gui.shop.quantity.buy" : "gui.shop.quantity.sell", Map.of(
                    "quantity", String.valueOf(quantity)
                )));
                
                List<String> lore = new ArrayList<>();
                if (buyMode) {
                    double cost = item.getEffectiveBuyPrice() * quantity;
                    lore.add(Messages.get("gui.shop.quantity.cost", Map.of(
                        "cost", String.format("%.2f", cost)
                    )));
                    if (!available) {
                        lore.add(Messages.get("gui.shop.quantity.out-of-stock"));
                    }
                } else {
                    double earnings = item.getEffectiveSellPrice() * quantity;
                    lore.add(Messages.get("gui.shop.quantity.earn", Map.of(
                        "earnings", String.format("%.2f", earnings)
                    )));
                }
                
                meta.setLore(lore);
                pane.setItemMeta(meta);
            }
            
            inv.setItem(slots[i], pane);
        }
        
        // Item display in center
        ItemStack display = new ItemStack(item.getMaterial());
        ItemMeta displayMeta = display.getItemMeta();
        if (displayMeta != null) {
            String displayItemName = item.getDisplayName() != null && !item.getDisplayName().isEmpty() 
                ? item.getDisplayName() 
                : item.getMaterial().name().replace("_", " ");
            displayMeta.setDisplayName(Messages.get("gui.shop.quantity.item-title", Map.of(
                "item", displayItemName
            )));
            List<String> lore = new ArrayList<>();
            lore.add(Messages.get("gui.shop.quantity.select"));
            if (buyMode) {
                lore.add(Messages.get("gui.shop.quantity.price", Map.of(
                    "price", String.format("%.2f", item.getEffectiveBuyPrice())
                )));
                lore.add(Messages.get("gui.shop.quantity.stock", Map.of(
                    "stock", getStockText(item)
                )));
            } else {
                lore.add(Messages.get("gui.shop.quantity.price", Map.of(
                    "price", String.format("%.2f", item.getEffectiveSellPrice())
                )));
            }
            displayMeta.setLore(lore);
            display.setItemMeta(displayMeta);
        }
        inv.setItem(4, display);
        
        // Cancel button
        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancel.getItemMeta();
        if (cancelMeta != null) {
            cancelMeta.setDisplayName(Messages.get("gui.shop.cancel"));
            cancel.setItemMeta(cancelMeta);
        }
        inv.setItem(22, cancel);
        
        currentInventory = inv;
        player.openInventory(inv);
    }
    
    private ItemStack createShopItemDisplay(ShopItemConfig item) {
        ItemStack display = item.getDisplayItem() != null ? 
            item.getDisplayItem().clone() : 
            new ItemStack(item.getMaterial());
        ItemMeta meta = display.getItemMeta();
        
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            
            if (item.isBuyable()) {
                lore.add(Messages.get("gui.shop.item.buy"));
                lore.add(Messages.get("gui.shop.item.price", Map.of(
                    "price", String.format("%.2f", item.getEffectiveBuyPrice())
                )));
                
                if (shop.getStockSystem() != ShopData.StockSystem.INFINITE) {
                    lore.add(Messages.get("gui.shop.item.stock", Map.of(
                        "stock", getStockText(item)
                    )));
                }
                lore.add("");
                lore.add(Messages.get("gui.shop.item.left-click-buy"));
            }
            
            if (item.isSellable()) {
                lore.add(Messages.get("gui.shop.item.sell"));
                lore.add(Messages.get("gui.shop.item.price", Map.of(
                    "price", String.format("%.2f", item.getEffectiveSellPrice())
                )));
                lore.add("");
                lore.add(Messages.get("gui.shop.item.right-click-sell"));
            }
            
            if (!item.isBuyable() && !item.isSellable()) {
                lore.add(Messages.get("gui.shop.item.display-only"));
            }
            
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        
        return display;
    }
    
    // Getters for listener

    public GUIState getCurrentState() { return currentState; }

    public ShopItemConfig getItemAtSlot(int slot) { return slotToItemMap.get(slot); }

    public ShopItemConfig getSelectedItem() { return selectedItem; }

    public boolean isBuyMode() { return buyMode; }

    public int getCurrentPage() { return currentPage; }

    public String getCurrentCategory() { return currentCategory; }

    public void setCurrentCategory(String category) { this.currentCategory = category; }

    public String getZoneId() { return zoneId; }

    public ShopData getShop() { return shop; }

    public Inventory getCurrentInventory() { return currentInventory; }

    public boolean isPaginated() { return paginated; }

    public Integer getQuantityForSlot(int slot) { return slotToQuantityMap.get(slot); }

    private boolean isUnlimitedStock(ShopItemConfig item) {
        return shop.getStockSystem() == ShopData.StockSystem.INFINITE || item.getMaxStock() < 0;
    }

    private String getStockText(ShopItemConfig item) {
        if (isUnlimitedStock(item)) {
            return Messages.get("gui.shop.stock.unlimited");
        }
        if (item.isInStock()) {
            return Messages.get("gui.shop.stock.count", Map.of(
                "count", String.valueOf(item.getStock())
            ));
        }
        return Messages.get("gui.shop.stock.out");
    }

    private void applyFillers(Inventory inv) {
        if (!isFillerEnabled()) {
            return;
        }
        Material fillerMaterial = getFillerMaterial();
        if (fillerMaterial == null || fillerMaterial == Material.AIR) {
            return;
        }

        ItemStack filler = new ItemStack(fillerMaterial);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                inv.setItem(slot, filler.clone());
            }
        }
    }

    private boolean isFillerEnabled() {
        return plugin.getConfig().getBoolean("shops.filler.enabled", false);
    }

    private Material getFillerMaterial() {
        String materialName = plugin.getConfig().getString("shops.filler.material", "BLACK_STAINED_GLASS_PANE");
        return Material.matchMaterial(materialName);
    }
}

