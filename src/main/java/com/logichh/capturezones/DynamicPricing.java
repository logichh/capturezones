package com.logichh.capturezones;

public class DynamicPricing {
    
    private final ShopData shopData;
    
    public DynamicPricing(ShopData shopData) {
        this.shopData = shopData;
    }
    
    public void updateItemPrice(ShopItemConfig item) {
        if (shopData.getPricingMode() != ShopData.PricingMode.DYNAMIC) {
            return;
        }
        
        double multiplier = calculatePriceMultiplier(item);
        item.setPriceMultiplier(multiplier);
        item.setLastPriceUpdate(System.currentTimeMillis());
    }
    
    private double calculatePriceMultiplier(ShopItemConfig item) {
        double sensitivity = shopData.getDynamicSensitivity();
        double currentMultiplier = item.getPriceMultiplier();
        
        double multiplier = currentMultiplier;
        
        // Factor 1: Time-based decay (prices slowly return to normal)
        long timeSinceUpdate = System.currentTimeMillis() - item.getLastPriceUpdate();
        long dayInMs = 86400000L;
        if (timeSinceUpdate > dayInMs) {
            // Decay towards 1.0 over time
            double decayRate = 0.1; // 10% decay per day
            long daysElapsed = timeSinceUpdate / dayInMs;
            for (int i = 0; i < daysElapsed && i < 10; i++) { // Cap at 10 days to prevent overflow
                multiplier = 1.0 + (multiplier - 1.0) * (1.0 - decayRate);
            }
        }
        
        // Factor 2: Stock level (if limited stock)
        if (shopData.getStockSystem() != ShopData.StockSystem.INFINITE && item.getMaxStock() > 0) {
            double stockRatio = (double) item.getStock() / item.getMaxStock();
            // Low stock = higher prices (inverse relationship)
            double stockFactor = 1.0 + (1.0 - stockRatio) * sensitivity * 2.0;
            multiplier *= stockFactor;
        }
        
        // Factor 3: Transaction frequency
        int transactions = item.getTransactionCount();
        if (transactions > 0) {
            // More transactions = slightly higher prices (demand)
            double transactionFactor = 1.0 + Math.min(transactions * 0.001, 0.5) * sensitivity;
            multiplier *= transactionFactor;
        }
        
        // Clamp to configured min/max
        multiplier = Math.max(shopData.getDynamicMin(), Math.min(shopData.getDynamicMax(), multiplier));
        
        return multiplier;
    }
    
    public void onBuyTransaction(ShopItemConfig item, int quantity) {
        if (shopData.getPricingMode() != ShopData.PricingMode.DYNAMIC) {
            return;
        }
        
        // Buying increases demand, increases price slightly
        double currentMultiplier = item.getPriceMultiplier();
        double increase = quantity * shopData.getDynamicSensitivity() * 0.01;
        double newMultiplier = Math.min(currentMultiplier + increase, shopData.getDynamicMax());
        
        item.setPriceMultiplier(newMultiplier);
        item.incrementTransactions();
        item.setLastPriceUpdate(System.currentTimeMillis());
    }
    
    public void onSellTransaction(ShopItemConfig item, int quantity) {
        if (shopData.getPricingMode() != ShopData.PricingMode.DYNAMIC) {
            return;
        }
        
        // Selling decreases demand, decreases price slightly
        double currentMultiplier = item.getPriceMultiplier();
        double decrease = quantity * shopData.getDynamicSensitivity() * 0.01;
        double newMultiplier = Math.max(currentMultiplier - decrease, shopData.getDynamicMin());
        
        item.setPriceMultiplier(newMultiplier);
        item.incrementTransactions();
        item.setLastPriceUpdate(System.currentTimeMillis());
    }
    
    public void updateAllPrices() {
        if (shopData.getPricingMode() != ShopData.PricingMode.DYNAMIC) {
            return;
        }
        
        for (ShopItemConfig item : shopData.getItems().values()) {
            updateItemPrice(item);
        }
    }
    
    public void resetAllPrices() {
        for (ShopItemConfig item : shopData.getItems().values()) {
            item.setPriceMultiplier(1.0);
            item.setTransactionCount(0);
            item.setLastPriceUpdate(System.currentTimeMillis());
        }
    }
}

