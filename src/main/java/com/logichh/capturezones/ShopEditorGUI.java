package com.logichh.capturezones;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ShopEditorGUI {
    
    private final CaptureZones plugin;
    private final ShopData shop;
    private final String zoneId;
    private final Player admin;
    private Inventory currentInventory;
    private int infoSlot = 49;
    private int backSlot = 53;
    
    // Editor state
    private EditorMode mode = EditorMode.MAIN_MENU;
    private ShopItemConfig editingItem = null;
    
    public enum EditorMode {
        MAIN_MENU,
        SETTINGS,
        ITEM_PLACEMENT,
        ITEM_CONFIG,
        STATISTICS
    }
    
    public ShopEditorGUI(CaptureZones plugin, String zoneId, ShopData shop, Player admin) {
        this.plugin = plugin;
        this.shop = shop;
        this.zoneId = zoneId;
        this.admin = admin;
    }
    
    public void openMainMenu() {
        this.mode = EditorMode.MAIN_MENU;
        
        Inventory inv = Bukkit.createInventory(null, 27, Messages.get("gui.editor.title", Map.of("zone", zoneId)));
        
        // Enable/Disable toggle
        ItemStack toggle = new ItemStack(shop.isEnabled() ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta toggleMeta = toggle.getItemMeta();
        if (toggleMeta != null) {
            toggleMeta.setDisplayName(Messages.get(shop.isEnabled() ? 
                "gui.editor.main.toggle.enabled" : 
                "gui.editor.main.toggle.disabled"));
            toggleMeta.setLore(Messages.getList("gui.editor.main.toggle.lore"));
            toggle.setItemMeta(toggleMeta);
        }
        inv.setItem(10, toggle);
        
        // Settings
        ItemStack settings = new ItemStack(Material.COMPARATOR);
        ItemMeta settingsMeta = settings.getItemMeta();
        if (settingsMeta != null) {
            settingsMeta.setDisplayName(Messages.get("gui.editor.main.settings.title"));
            settingsMeta.setLore(Messages.getList("gui.editor.main.settings.lore", Map.of(
                "access", getAccessModeLabel(shop.getAccessMode()),
                "stock", getStockSystemLabel(shop.getStockSystem()),
                "layout", getLayoutModeLabel(shop.getLayoutMode()),
                "pricing", getPricingModeLabel(shop.getPricingMode()),
                "restock", getRestockScheduleLabel(shop.getRestockSchedule())
            )));
            settings.setItemMeta(settingsMeta);
        }
        inv.setItem(11, settings);
        
        // Add/Edit Items
        ItemStack items = new ItemStack(Material.CHEST);
        ItemMeta itemsMeta = items.getItemMeta();
        if (itemsMeta != null) {
            itemsMeta.setDisplayName(Messages.get("gui.editor.main.items.title"));
            itemsMeta.setLore(Messages.getList("gui.editor.main.items.lore", Map.of(
                "count", String.valueOf(shop.getItems().size())
            )));
            items.setItemMeta(itemsMeta);
        }
        inv.setItem(12, items);
        
        // Statistics
        ItemStack stats = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = stats.getItemMeta();
        if (statsMeta != null) {
            statsMeta.setDisplayName(Messages.get("gui.editor.main.stats.title"));
            statsMeta.setLore(Messages.getList("gui.editor.main.stats.lore", Map.of(
                "buys", String.valueOf(shop.getTotalBuys()),
                "sells", String.valueOf(shop.getTotalSells()),
                "revenue", String.format("%.2f", shop.getTotalRevenue())
            )));
            stats.setItemMeta(statsMeta);
        }
        inv.setItem(13, stats);
        
        ItemStack restock = new ItemStack(Material.HOPPER);
        ItemMeta restockMeta = restock.getItemMeta();
        if (restockMeta != null) {
            restockMeta.setDisplayName(Messages.get("gui.editor.main.restock.title"));
            restockMeta.setLore(Messages.getList("gui.editor.main.restock.lore"));
            restock.setItemMeta(restockMeta);
        }
        inv.setItem(14, restock);
        
        ItemStack save = new ItemStack(Material.EMERALD);
        ItemMeta saveMeta = save.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName(Messages.get("gui.editor.main.save"));
            save.setItemMeta(saveMeta);
        }
        inv.setItem(22, save);

        currentInventory = inv;
        admin.openInventory(inv);
    }
    
    public void openSettings() {
        this.mode = EditorMode.SETTINGS;
        
        Inventory inv = Bukkit.createInventory(null, 27, Messages.get("gui.editor.settings-title"));
        
        // Access Mode
        ItemStack access = new ItemStack(Material.OAK_DOOR);
        ItemMeta accessMeta = access.getItemMeta();
        if (accessMeta != null) {
            accessMeta.setDisplayName(Messages.get("gui.editor.settings.access.title"));
            accessMeta.setLore(Messages.getList("gui.editor.settings.access.lore", Map.of(
                "current", getAccessModeLabel(shop.getAccessMode()),
                "always", getAccessModeLabel(ShopData.AccessMode.ALWAYS),
                "controlled_only", getAccessModeLabel(ShopData.AccessMode.CONTROLLED_ONLY),
                "owner_only", getAccessModeLabel(ShopData.AccessMode.OWNER_ONLY)
            )));
            access.setItemMeta(accessMeta);
        }
        inv.setItem(10, access);
        
        // Stock System
        ItemStack stock = new ItemStack(Material.CHEST);
        ItemMeta stockMeta = stock.getItemMeta();
        if (stockMeta != null) {
            stockMeta.setDisplayName(Messages.get("gui.editor.settings.stock.title"));
            stockMeta.setLore(Messages.getList("gui.editor.settings.stock.lore", Map.of(
                "current", getStockSystemLabel(shop.getStockSystem()),
                "infinite", getStockSystemLabel(ShopData.StockSystem.INFINITE),
                "limited", getStockSystemLabel(ShopData.StockSystem.LIMITED),
                "finite", getStockSystemLabel(ShopData.StockSystem.FINITE)
            )));
            stock.setItemMeta(stockMeta);
        }
        inv.setItem(11, stock);
        
        // Layout Mode
        ItemStack layout = new ItemStack(Material.PAINTING);
        ItemMeta layoutMeta = layout.getItemMeta();
        if (layoutMeta != null) {
            layoutMeta.setDisplayName(Messages.get("gui.editor.settings.layout.title"));
            layoutMeta.setLore(Messages.getList("gui.editor.settings.layout.lore", Map.of(
                "current", getLayoutModeLabel(shop.getLayoutMode()),
                "single_page", getLayoutModeLabel(ShopData.LayoutMode.SINGLE_PAGE),
                "categories", getLayoutModeLabel(ShopData.LayoutMode.CATEGORIES),
                "paginated", getLayoutModeLabel(ShopData.LayoutMode.PAGINATED)
            )));
            layout.setItemMeta(layoutMeta);
        }
        inv.setItem(12, layout);
        
        // Pricing Mode
        ItemStack pricing = new ItemStack(Material.GOLD_INGOT);
        ItemMeta pricingMeta = pricing.getItemMeta();
        if (pricingMeta != null) {
            pricingMeta.setDisplayName(Messages.get("gui.editor.settings.pricing.title"));
            pricingMeta.setLore(Messages.getList("gui.editor.settings.pricing.lore", Map.of(
                "current", getPricingModeLabel(shop.getPricingMode()),
                "fixed", getPricingModeLabel(ShopData.PricingMode.FIXED),
                "dynamic", getPricingModeLabel(ShopData.PricingMode.DYNAMIC)
            )));
            pricing.setItemMeta(pricingMeta);
        }
        inv.setItem(13, pricing);
        
        // Restock Schedule
        ItemStack restock = new ItemStack(Material.CLOCK);
        ItemMeta restockMeta = restock.getItemMeta();
        if (restockMeta != null) {
            restockMeta.setDisplayName(Messages.get("gui.editor.settings.restock.title"));
            restockMeta.setLore(Messages.getList("gui.editor.settings.restock.lore", Map.of(
                "current", getRestockScheduleLabel(shop.getRestockSchedule()),
                "hourly", getRestockScheduleLabel(ShopData.RestockSchedule.HOURLY),
                "daily", getRestockScheduleLabel(ShopData.RestockSchedule.DAILY),
                "weekly", getRestockScheduleLabel(ShopData.RestockSchedule.WEEKLY),
                "manual", getRestockScheduleLabel(ShopData.RestockSchedule.MANUAL)
            )));
            restock.setItemMeta(restockMeta);
        }
        inv.setItem(14, restock);
        
        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(Messages.get("gui.editor.settings.back"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(22, back);

        currentInventory = inv;
        admin.openInventory(inv);
    }
    
    public void openItemPlacement() {
        this.mode = EditorMode.ITEM_PLACEMENT;
        
        Inventory inv = Bukkit.createInventory(null, 54, Messages.get("gui.editor.items-title"));
        
        // Add existing shop items
        for (ShopItemConfig item : shop.getItems().values()) {
            if (item.getSlot() >= 0 && item.getSlot() < 54) {
                inv.setItem(item.getSlot(), createEditorItemDisplay(item));
            }
        }
        
        // Instructions item
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(Messages.get("gui.editor.items.info.title"));
            infoMeta.setLore(Messages.getList("gui.editor.items.info.lore"));
            info.setItemMeta(infoMeta);
        }
        
        // Back button
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(Messages.get("gui.editor.items.back"));
            back.setItemMeta(backMeta);
        }

        int[] infoCandidates = {49, 45, 46, 47, 48, 50, 51, 52, 53};
        int[] backCandidates = {53, 52, 51, 50, 48, 47, 46, 45, 49};
        infoSlot = findControlSlot(inv, infoCandidates, -1);
        backSlot = findControlSlot(inv, backCandidates, infoSlot);

        if (infoSlot >= 0) {
            inv.setItem(infoSlot, info);
        }
        if (backSlot >= 0) {
            inv.setItem(backSlot, back);
        }
        
        currentInventory = inv;
        admin.openInventory(inv);
    }
    
    public void openItemConfig(ShopItemConfig item) {
        this.mode = EditorMode.ITEM_CONFIG;
        this.editingItem = item;
        
        String itemName = item.getDisplayName() != null ? item.getDisplayName() : item.getMaterial().name();
        Inventory inv = Bukkit.createInventory(null, 27, Messages.get("gui.editor.config-title", 
            Map.of("item", itemName)));
        
        // Item display
        ItemStack displayItem = item.getDisplayItem() != null ? item.getDisplayItem().clone() : new ItemStack(item.getMaterial());
        inv.setItem(4, displayItem);
        
        // Buyable toggle
        ItemStack buyable = new ItemStack(item.isBuyable() ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta buyableMeta = buyable.getItemMeta();
        if (buyableMeta != null) {
            buyableMeta.setDisplayName(Messages.get(item.isBuyable() ? 
                "gui.editor.config.buyable.enabled" : 
                "gui.editor.config.buyable.disabled"));
            buyableMeta.setLore(Messages.getList("gui.editor.config.buyable.lore", Map.of(
                "price", String.format("%.2f", item.getBuyPrice())
            )));
            buyable.setItemMeta(buyableMeta);
        }
        inv.setItem(10, buyable);
        
        // Sellable toggle
        ItemStack sellable = new ItemStack(item.isSellable() ? Material.RED_DYE : Material.GRAY_DYE);
        ItemMeta sellableMeta = sellable.getItemMeta();
        if (sellableMeta != null) {
            sellableMeta.setDisplayName(Messages.get(item.isSellable() ? 
                "gui.editor.config.sellable.enabled" : 
                "gui.editor.config.sellable.disabled"));
            sellableMeta.setLore(Messages.getList("gui.editor.config.sellable.lore", Map.of(
                "price", String.format("%.2f", item.getSellPrice())
            )));
            sellable.setItemMeta(sellableMeta);
        }
        inv.setItem(11, sellable);
        
        // Stock settings
        ItemStack stockItem = new ItemStack(Material.CHEST);
        ItemMeta stockMeta = stockItem.getItemMeta();
        if (stockMeta != null) {
            String unlimited = Messages.get("gui.editor.stock.unlimited");
            String currentStock = isUnlimitedStock(item) ? unlimited : String.valueOf(item.getStock());
            String maxStock = isUnlimitedStock(item) ? unlimited : String.valueOf(item.getMaxStock());
            stockMeta.setDisplayName(Messages.get("gui.editor.config.stock.title"));
            stockMeta.setLore(Messages.getList("gui.editor.config.stock.lore", Map.of(
                "current", currentStock,
                "max", maxStock
            )));
            stockItem.setItemMeta(stockMeta);
        }
        inv.setItem(12, stockItem);
        
        // Category
        ItemStack category = new ItemStack(Material.NAME_TAG);
        ItemMeta categoryMeta = category.getItemMeta();
        if (categoryMeta != null) {
            categoryMeta.setDisplayName(Messages.get("gui.editor.config.category.title"));
            String cat = item.getCategory() != null ? item.getCategory() : Messages.get("gui.editor.category.none");
            categoryMeta.setLore(Messages.getList("gui.editor.config.category.lore", Map.of(
                "category", cat
            )));
            category.setItemMeta(categoryMeta);
        }
        inv.setItem(13, category);

        // Slot
        ItemStack slotItem = new ItemStack(Material.COMPASS);
        ItemMeta slotMeta = slotItem.getItemMeta();
        if (slotMeta != null) {
            slotMeta.setDisplayName(Messages.get("gui.editor.config.slot.title"));
            slotMeta.setLore(Messages.getList("gui.editor.config.slot.lore", Map.of(
                "slot", String.valueOf(item.getSlot())
            )));
            slotItem.setItemMeta(slotMeta);
        }
        inv.setItem(14, slotItem);

        // Buy price
        ItemStack buyPrice = new ItemStack(Material.EMERALD);
        ItemMeta buyPriceMeta = buyPrice.getItemMeta();
        if (buyPriceMeta != null) {
            buyPriceMeta.setDisplayName(Messages.get("gui.editor.config.buy-price.title"));
            buyPriceMeta.setLore(Messages.getList("gui.editor.config.buy-price.lore", Map.of(
                "price", String.format("%.2f", item.getBuyPrice())
            )));
            buyPrice.setItemMeta(buyPriceMeta);
        }
        inv.setItem(15, buyPrice);

        // Sell price
        ItemStack sellPrice = new ItemStack(Material.GOLD_INGOT);
        ItemMeta sellPriceMeta = sellPrice.getItemMeta();
        if (sellPriceMeta != null) {
            sellPriceMeta.setDisplayName(Messages.get("gui.editor.config.sell-price.title"));
            sellPriceMeta.setLore(Messages.getList("gui.editor.config.sell-price.lore", Map.of(
                "price", String.format("%.2f", item.getSellPrice())
            )));
            sellPrice.setItemMeta(sellPriceMeta);
        }
        inv.setItem(16, sellPrice);
        
        ItemStack save = new ItemStack(Material.EMERALD);
        ItemMeta saveMeta = save.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName(Messages.get("gui.editor.config.save"));
            save.setItemMeta(saveMeta);
        }
        inv.setItem(22, save);

        currentInventory = inv;
        admin.openInventory(inv);
    }
    
    public void openStatistics() {
        this.mode = EditorMode.STATISTICS;
        
        Inventory inv = Bukkit.createInventory(null, 27, Messages.get("gui.editor.stats-title"));
        
        // Total buys
        ItemStack buys = new ItemStack(Material.GREEN_WOOL);
        ItemMeta buysMeta = buys.getItemMeta();
        if (buysMeta != null) {
            buysMeta.setDisplayName(Messages.get("gui.editor.stats.buys.title"));
            buysMeta.setLore(Arrays.asList(Messages.get("gui.editor.stats.value", Map.of(
                "value", String.valueOf(shop.getTotalBuys())
            ))));
            buys.setItemMeta(buysMeta);
        }
        inv.setItem(11, buys);
        
        // Total sells
        ItemStack sells = new ItemStack(Material.RED_WOOL);
        ItemMeta sellsMeta = sells.getItemMeta();
        if (sellsMeta != null) {
            sellsMeta.setDisplayName(Messages.get("gui.editor.stats.sells.title"));
            sellsMeta.setLore(Arrays.asList(Messages.get("gui.editor.stats.value", Map.of(
                "value", String.valueOf(shop.getTotalSells())
            ))));
            sells.setItemMeta(sellsMeta);
        }
        inv.setItem(13, sells);
        
        // Revenue
        ItemStack revenue = new ItemStack(Material.GOLD_INGOT);
        ItemMeta revenueMeta = revenue.getItemMeta();
        if (revenueMeta != null) {
            revenueMeta.setDisplayName(Messages.get("gui.editor.stats.revenue.title"));
            revenueMeta.setLore(Arrays.asList(Messages.get("gui.editor.stats.value", Map.of(
                "value", String.format("%.2f", shop.getTotalRevenue())
            ))));
            revenue.setItemMeta(revenueMeta);
        }
        inv.setItem(15, revenue);
        
        // Back button
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(Messages.get("gui.editor.stats.back"));
            back.setItemMeta(backMeta);
        }
        inv.setItem(22, back);

        currentInventory = inv;
        admin.openInventory(inv);
    }
    
    private ItemStack createEditorItemDisplay(ShopItemConfig item) {
        ItemStack display = item.getDisplayItem() != null ? 
            item.getDisplayItem().clone() : 
            new ItemStack(item.getMaterial());
        ItemMeta meta = display.getItemMeta();
        
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(Messages.get("gui.editor.item.lore.slot", Map.of(
                "slot", String.valueOf(item.getSlot())
            )));
            if (item.isBuyable()) {
                lore.add(Messages.get("gui.editor.item.lore.buy", Map.of(
                    "price", String.format("%.2f", item.getBuyPrice())
                )));
            }
            if (item.isSellable()) {
                lore.add(Messages.get("gui.editor.item.lore.sell", Map.of(
                    "price", String.format("%.2f", item.getSellPrice())
                )));
            }
            String stockValue = isUnlimitedStock(item) ? 
                Messages.get("gui.editor.stock.unlimited") : 
                String.valueOf(item.getStock());
            lore.add(Messages.get("gui.editor.item.lore.stock", Map.of(
                "stock", stockValue
            )));
            if (item.getCategory() != null) {
                lore.add(Messages.get("gui.editor.item.lore.category", Map.of(
                    "category", item.getCategory()
                )));
            }
            lore.add("");
            lore.add(Messages.get("gui.editor.item.lore.click-configure"));
            lore.add(Messages.get("gui.editor.item.lore.shift-remove"));
            
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        
        return display;
    }

    private boolean isUnlimitedStock(ShopItemConfig item) {
        return shop.getStockSystem() == ShopData.StockSystem.INFINITE || item.getMaxStock() < 0;
    }

    private String getAccessModeLabel(ShopData.AccessMode mode) {
        return getEnumLabel("gui.shop.access-mode.", mode);
    }

    private String getStockSystemLabel(ShopData.StockSystem system) {
        return getEnumLabel("gui.shop.stock-system.", system);
    }

    private String getLayoutModeLabel(ShopData.LayoutMode mode) {
        return getEnumLabel("gui.shop.layout-mode.", mode);
    }

    private String getPricingModeLabel(ShopData.PricingMode mode) {
        return getEnumLabel("gui.shop.pricing-mode.", mode);
    }

    private String getRestockScheduleLabel(ShopData.RestockSchedule schedule) {
        return getEnumLabel("gui.shop.restock-schedule.", schedule);
    }

    private String getEnumLabel(String baseKey, Enum<?> value) {
        String key = baseKey + value.name().toLowerCase(Locale.ROOT).replace('_', '-');
        return Messages.get(key);
    }
    
    // Getters

    public EditorMode getMode() { return mode; }

    public ShopItemConfig getEditingItem() { return editingItem; }

    public String getZoneId() { return zoneId; }

    public ShopData getShop() { return shop; }

    public Inventory getCurrentInventory() { return currentInventory; }

    public int getInfoSlot() { return infoSlot; }

    public int getBackSlot() { return backSlot; }

    private int findControlSlot(Inventory inv, int[] candidates, int reservedSlot) {
        for (int slot : candidates) {
            if (slot == reservedSlot) {
                continue;
            }
            ItemStack existing = inv.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }
}

