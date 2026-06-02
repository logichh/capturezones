package com.logichh.capturezones;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
public final class VaultShopEconomyAdapter implements ShopEconomyAdapter {

    private final CaptureZones plugin;
    private Economy economy;

    public VaultShopEconomyAdapter(CaptureZones plugin) {
        this.plugin = plugin;
    }

    @Override

    public String getProviderName() {
        Economy provider = getProvider();
        return provider == null ? "Vault" : "Vault (" + provider.getName() + ")";
    }

    @Override

    public boolean isAvailable() {
        return getProvider() != null;
    }

    @Override

    public boolean hasAccount(Player player) {
        Economy provider = getProvider();
        return provider != null && player != null && provider.hasAccount(player);
    }

    @Override

    public boolean has(Player player, double amount) {
        Economy provider = getProvider();
        return provider != null && player != null && provider.has(player, amount);
    }

    @Override

    public boolean withdraw(Player player, double amount, String reason) {
        Economy provider = getProvider();
        return provider != null
            && player != null
            && provider.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override

    public boolean deposit(Player player, double amount, String reason) {
        Economy provider = getProvider();
        return provider != null
            && player != null
            && provider.depositPlayer(player, amount).transactionSuccess();
    }

    @Override

    public boolean supportsOwnerType(CaptureOwnerType ownerType) {
        return ownerType == CaptureOwnerType.PLAYER;
    }

    @Override

    public boolean depositToOwner(String ownerName, CaptureOwnerType ownerType, double amount, String reason) {
        Economy provider = getProvider();
        if (provider == null || ownerType != CaptureOwnerType.PLAYER || ownerName == null || ownerName.trim().isEmpty()) {
            return false;
        }
        String normalized = ownerName.trim();
        try {
            if (provider.hasAccount(normalized)) {
                return provider.depositPlayer(normalized, amount).transactionSuccess();
            }
            Player online = Bukkit.getPlayerExact(normalized);
            if (online != null) {
                return provider.depositPlayer(online, amount).transactionSuccess();
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private Economy getProvider() {
        if (economy != null) {
            return economy;
        }
        if (plugin == null) {
            return null;
        }
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null
            || !plugin.getServer().getPluginManager().getPlugin("Vault").isEnabled()) {
            return null;
        }
        RegisteredServiceProvider<Economy> registration = plugin.getServer()
            .getServicesManager()
            .getRegistration(Economy.class);
        if (registration == null) {
            return null;
        }
        economy = registration.getProvider();
        return economy;
    }
}

