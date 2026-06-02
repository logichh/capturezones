package com.logichh.capturezones;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.entity.Player;
public final class TownyShopEconomyAdapter implements ShopEconomyAdapter {

    @Override

    public String getProviderName() {
        return "Towny";
    }

    @Override

    public boolean isAvailable() {
        return true;
    }

    @Override

    public boolean hasAccount(Player player) {
        return getResident(player) != null;
    }

    @Override

    public boolean has(Player player, double amount) {
        Resident resident = getResident(player);
        return resident != null && resident.getAccount().canPayFromHoldings(amount);
    }

    @Override

    public boolean withdraw(Player player, double amount, String reason) {
        Resident resident = getResident(player);
        if (resident == null) {
            return false;
        }
        resident.getAccount().withdraw(amount, reason == null ? "Shop purchase" : reason);
        return true;
    }

    @Override

    public boolean deposit(Player player, double amount, String reason) {
        Resident resident = getResident(player);
        if (resident == null) {
            return false;
        }
        resident.getAccount().deposit(amount, reason == null ? "Shop transaction" : reason);
        return true;
    }

    @Override

    public boolean supportsOwnerType(CaptureOwnerType ownerType) {
        return ownerType == CaptureOwnerType.TOWN || ownerType == CaptureOwnerType.PLAYER;
    }

    @Override

    public boolean depositToOwner(String ownerName, CaptureOwnerType ownerType, double amount, String reason) {
        if (ownerName == null || ownerName.trim().isEmpty() || ownerType == null) {
            return false;
        }
        try {
            if (ownerType == CaptureOwnerType.TOWN) {
                Town town = TownyAPI.getInstance().getTown(ownerName.trim());
                if (town == null || town.getAccount() == null) {
                    return false;
                }
                town.getAccount().deposit(amount, reason == null ? "Control reward" : reason);
                return true;
            }
            if (ownerType == CaptureOwnerType.PLAYER) {
                Resident resident = TownyAPI.getInstance().getResident(ownerName.trim());
                if (resident == null || resident.getAccount() == null) {
                    return false;
                }
                resident.getAccount().deposit(amount, reason == null ? "Control reward" : reason);
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private Resident getResident(Player player) {
        if (player == null) {
            return null;
        }
        Resident resident = TownyAPI.getInstance().getResident(player.getUniqueId());
        if (resident == null || resident.getAccount() == null) {
            return null;
        }
        return resident;
    }
}

