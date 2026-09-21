package com.logichh.capturezones;

import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public interface OwnerPlatformAdapter {
    default String getPlatformKey() {
        return "custom";
    }

    default EnumSet<CaptureOwnerType> getSupportedOwnerTypes() {
        return EnumSet.of(CaptureOwnerType.PLAYER);
    }

    String resolveOwnerName(Player player, CaptureOwnerType ownerType);

    boolean doesPlayerMatchOwner(Player player, String ownerName, CaptureOwnerType ownerType);

    List<String> getAvailableOwners(CaptureOwnerType ownerType);

    String normalizeOwnerName(String ownerName, CaptureOwnerType ownerType);

    default String resolveOwnerId(String ownerName, CaptureOwnerType ownerType) {
        return null;
    }

    boolean ownerExists(String ownerName, CaptureOwnerType ownerType);

    boolean depositControlReward(String ownerName, double amount, String reason, CaptureOwnerType ownerType);

    default boolean depositControlReward(CaptureOwner owner, double amount, String reason) {
        if (owner == null) {
            return false;
        }
        return depositControlReward(owner.getDisplayName(), amount, reason, owner.getType());
    }

    boolean depositFirstCaptureBonus(UUID playerId, double amount, String reason);

    String resolveMapColorHex(String ownerName, CaptureOwnerType ownerType, String fallbackHex);

    default String resolveMapColorHex(CaptureOwner owner, String fallbackHex) {
        if (owner == null) {
            return fallbackHex;
        }
        return resolveMapColorHex(owner.getDisplayName(), owner.getType(), fallbackHex);
    }

    default boolean doesPlayerMatchOwner(Player player, CaptureOwner owner) {
        if (owner == null) {
            return false;
        }
        return doesPlayerMatchOwner(player, owner.getDisplayName(), owner.getType());
    }

    default CaptureOwner refreshOwner(CaptureOwner owner) {
        return owner;
    }

    default List<OwnerMember> getOwnerMembers(CaptureOwner owner) {
        return List.of();
    }

    boolean isPlayerInSameTown(Player player, CaptureOwner owner);

    boolean isPlayerInSameNation(Player player, CaptureOwner owner);
}

