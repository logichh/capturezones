package com.logichh.capturezones;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public final class StandaloneOwnerPlatformAdapter implements OwnerPlatformAdapter {
    @Override

    public String getPlatformKey() {
        return "standalone";
    }

    @Override

    public EnumSet<CaptureOwnerType> getSupportedOwnerTypes() {
        return EnumSet.of(CaptureOwnerType.PLAYER);
    }

    @Override

    public String resolveOwnerName(Player player, CaptureOwnerType ownerType) {
        if (player == null || ownerType == null) {
            return null;
        }
        if (ownerType == CaptureOwnerType.PLAYER) {
            return player.getName();
        }
        return null;
    }

    @Override

    public boolean doesPlayerMatchOwner(Player player, String ownerName, CaptureOwnerType ownerType) {
        if (player == null || ownerName == null || ownerType == null) {
            return false;
        }
        if (ownerType != CaptureOwnerType.PLAYER) {
            return false;
        }
        return ownerName.trim().equalsIgnoreCase(player.getName());
    }

    @Override

    public List<String> getAvailableOwners(CaptureOwnerType ownerType) {
        if (ownerType != CaptureOwnerType.PLAYER) {
            return Collections.emptyList();
        }

        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    @Override

    public String normalizeOwnerName(String ownerName, CaptureOwnerType ownerType) {
        if (ownerType != CaptureOwnerType.PLAYER || ownerName == null) {
            return null;
        }
        String normalized = ownerName.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    @Override

    public boolean ownerExists(String ownerName, CaptureOwnerType ownerType) {
        return normalizeOwnerName(ownerName, ownerType) != null;
    }

    @Override
    public List<OwnerMember> getOwnerMembers(CaptureOwner owner) {
        if (owner == null || owner.getType() != CaptureOwnerType.PLAYER || owner.getDisplayName() == null) {
            return Collections.emptyList();
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(owner.getDisplayName());
        if (!player.isOnline() && !player.hasPlayedBefore()) {
            return Collections.emptyList();
        }
        String name = player.getName() == null ? owner.getDisplayName() : player.getName();
        return List.of(new OwnerMember(player.getUniqueId(), name));
    }

    @Override

    public boolean depositControlReward(String ownerName, double amount, String reason, CaptureOwnerType ownerType) {
        return false;
    }

    @Override

    public boolean depositFirstCaptureBonus(UUID playerId, double amount, String reason) {
        return false;
    }

    @Override

    public String resolveMapColorHex(String ownerName, CaptureOwnerType ownerType, String fallbackHex) {
        return fallbackHex;
    }

    @Override

    public boolean isPlayerInSameTown(Player player, CaptureOwner owner) {
        return false;
    }

    @Override

    public boolean isPlayerInSameNation(Player player, CaptureOwner owner) {
        return false;
    }
}

