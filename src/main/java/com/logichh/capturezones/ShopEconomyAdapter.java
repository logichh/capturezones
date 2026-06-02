package com.logichh.capturezones;

import org.bukkit.entity.Player;

public interface ShopEconomyAdapter {

    String getProviderName();

    boolean isAvailable();

    boolean hasAccount(Player player);

    boolean has(Player player, double amount);

    boolean withdraw(Player player, double amount, String reason);

    boolean deposit(Player player, double amount, String reason);

    default boolean supportsOwnerType(CaptureOwnerType ownerType) {
        return false;
    }

    default boolean depositToOwner(String ownerName, CaptureOwnerType ownerType, double amount, String reason) {
        return false;
    }
}
