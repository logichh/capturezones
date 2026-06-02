package com.logichh.capturezones;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TownyOwnerPlatformAdapter implements OwnerPlatformAdapter {
    @Override

    public String getPlatformKey() {
        return "towny";
    }

    @Override

    public EnumSet<CaptureOwnerType> getSupportedOwnerTypes() {
        return EnumSet.of(CaptureOwnerType.PLAYER, CaptureOwnerType.TOWN, CaptureOwnerType.NATION);
    }

    @Override

    public String resolveOwnerName(Player player, CaptureOwnerType ownerType) {
        if (player == null || ownerType == null) {
            return null;
        }

        switch (ownerType) {
            case PLAYER:
                return player.getName();
            case TOWN:
                return resolveTownName(player);
            case NATION:
                return resolveNationName(player);
            default:
                return null;
        }
    }

    @Override

    public boolean doesPlayerMatchOwner(Player player, String ownerName, CaptureOwnerType ownerType) {
        if (player == null || ownerName == null || ownerType == null) {
            return false;
        }
        String resolved = resolveOwnerName(player, ownerType);
        return resolved != null && resolved.equalsIgnoreCase(ownerName.trim());
    }

    @Override

    public List<String> getAvailableOwners(CaptureOwnerType ownerType) {
        if (ownerType == null) {
            return Collections.emptyList();
        }

        if (ownerType == CaptureOwnerType.PLAYER) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return names;
        }

        try {
            if (ownerType == CaptureOwnerType.TOWN) {
                List<String> names = new ArrayList<>();
                for (Town town : TownyAPI.getInstance().getTowns()) {
                    if (town != null) {
                        names.add(town.getName());
                    }
                }
                return names;
            }

            Set<String> nationNames = new HashSet<>();
            for (Town town : TownyAPI.getInstance().getTowns()) {
                if (town == null || !town.hasNation()) {
                    continue;
                }
                try {
                    nationNames.add(town.getNation().getName());
                } catch (Exception ignored) {
                    // Ignore broken nation references.
                }
            }
            return new ArrayList<>(nationNames);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    @Override

    public String normalizeOwnerName(String ownerName, CaptureOwnerType ownerType) {
        if (ownerName == null || ownerType == null) {
            return null;
        }
        String raw = ownerName.trim();
        if (raw.isEmpty()) {
            return null;
        }

        switch (ownerType) {
            case PLAYER:
                return raw;
            case TOWN:
                try {
                    Town town = TownyAPI.getInstance().getTown(raw);
                    return town != null ? town.getName() : null;
                } catch (Exception ex) {
                    return null;
                }
            case NATION:
                try {
                    for (Town town : TownyAPI.getInstance().getTowns()) {
                        if (town == null || !town.hasNation()) {
                            continue;
                        }
                        try {
                            String nationName = town.getNation().getName();
                            if (nationName.equalsIgnoreCase(raw)) {
                                return nationName;
                            }
                        } catch (Exception ignored) {
                            // Ignore broken nation entries.
                        }
                    }
                } catch (Exception ignored) {
                    // Ignore Towny iteration failures.
                }
                return null;
            default:
                return null;
        }
    }

    @Override

    public String resolveOwnerId(String ownerName, CaptureOwnerType ownerType) {
        if (ownerName == null || ownerType == null) {
            return null;
        }
        if (ownerType != CaptureOwnerType.TOWN) {
            return null;
        }
        Town town = resolveTownByName(ownerName);
        if (town == null || town.getUUID() == null) {
            return null;
        }
        return town.getUUID().toString();
    }

    @Override

    public boolean ownerExists(String ownerName, CaptureOwnerType ownerType) {
        return normalizeOwnerName(ownerName, ownerType) != null;
    }

    @Override

    public boolean depositControlReward(String ownerName, double amount, String reason, CaptureOwnerType ownerType) {
        if (ownerType != CaptureOwnerType.TOWN) {
            return false;
        }
        try {
            Town town = resolveTownByName(ownerName);
            if (town == null || town.getAccount() == null) {
                return false;
            }
            town.getAccount().deposit(amount, reason);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override

    public boolean depositControlReward(CaptureOwner owner, double amount, String reason) {
        if (owner == null || owner.getType() != CaptureOwnerType.TOWN) {
            return false;
        }
        try {
            Town town = resolveTown(owner);
            if (town == null || town.getAccount() == null) {
                return false;
            }
            town.getAccount().deposit(amount, reason);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override

    public boolean depositFirstCaptureBonus(UUID playerId, double amount, String reason) {
        if (playerId == null) {
            return false;
        }
        try {
            Resident resident = TownyAPI.getInstance().getResident(playerId);
            if (resident == null || resident.getAccount() == null) {
                return false;
            }
            resident.getAccount().deposit(amount, reason);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public List<Player> getOnlineOwnerPlayers(CaptureOwner owner) {
        if (owner == null || owner.getType() == null) {
            return Collections.emptyList();
        }
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (doesPlayerMatchOwner(player, owner)) {
                players.add(player);
            }
        }
        return players;
    }

    public List<String> getOwnerResidentNames(CaptureOwner owner) {
        if (owner == null || owner.getType() == null) {
            return Collections.emptyList();
        }
        try {
            Set<String> names = new LinkedHashSet<>();
            if (owner.getType() == CaptureOwnerType.PLAYER) {
                Resident resident = resolveResident(owner);
                if (resident != null && resident.getName() != null) {
                    names.add(resident.getName());
                }
            } else if (owner.getType() == CaptureOwnerType.TOWN) {
                Town town = resolveTown(owner);
                if (town != null) {
                    for (Resident resident : town.getResidents()) {
                        if (resident != null && resident.getName() != null) {
                            names.add(resident.getName());
                        }
                    }
                }
            } else if (owner.getType() == CaptureOwnerType.NATION) {
                for (Town town : TownyAPI.getInstance().getTowns()) {
                    if (town == null || !town.hasNation()) {
                        continue;
                    }
                    try {
                        if (!town.getNation().getName().equalsIgnoreCase(owner.getDisplayName())) {
                            continue;
                        }
                        for (Resident resident : town.getResidents()) {
                            if (resident != null && resident.getName() != null) {
                                names.add(resident.getName());
                            }
                        }
                    } catch (Exception ignored) {
                        // Ignore broken nation or resident references.
                    }
                }
            }
            return new ArrayList<>(names);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public boolean depositResidentReward(String residentName, double amount, String reason) {
        if (residentName == null || residentName.trim().isEmpty() || amount <= 0.0) {
            return false;
        }
        try {
            Resident resident = TownyAPI.getInstance().getResident(residentName.trim());
            if (resident == null || resident.getAccount() == null) {
                return false;
            }
            resident.getAccount().deposit(amount, reason == null ? "Control reward" : reason);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override

    public String resolveMapColorHex(String ownerName, CaptureOwnerType ownerType, String fallbackHex) {
        if (ownerName == null || ownerName.trim().isEmpty()) {
            return fallbackHex;
        }
        try {
            if (ownerType == CaptureOwnerType.TOWN) {
                Town town = resolveTownByName(ownerName);
                if (town != null) {
                    Color color = town.getMapColor();
                    if (color != null) {
                        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
                    }
                }
            } else if (ownerType == CaptureOwnerType.NATION) {
                for (Town town : TownyAPI.getInstance().getTowns()) {
                    if (town == null || !town.hasNation()) {
                        continue;
                    }
                    try {
                        if (!town.getNation().getName().equalsIgnoreCase(ownerName)) {
                            continue;
                        }
                        Color color = town.getNation().getMapColor();
                        if (color != null) {
                            return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
                        }
                    } catch (Exception ignored) {
                        // Ignore nation lookups that fail.
                    }
                }
            }
        } catch (Exception ignored) {
            // Fallback below.
        }
        return fallbackHex;
    }

    @Override

    public String resolveMapColorHex(CaptureOwner owner, String fallbackHex) {
        if (owner == null || owner.getType() == null) {
            return fallbackHex;
        }
        try {
            if (owner.getType() == CaptureOwnerType.TOWN) {
                Town town = resolveTown(owner);
                if (town != null) {
                    Color color = town.getMapColor();
                    if (color != null) {
                        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
                    }
                }
                return fallbackHex;
            }
            return resolveMapColorHex(owner.getDisplayName(), owner.getType(), fallbackHex);
        } catch (Exception ignored) {
            return fallbackHex;
        }
    }

    @Override

    public boolean doesPlayerMatchOwner(Player player, CaptureOwner owner) {
        if (player == null || owner == null || owner.getType() == null) {
            return false;
        }
        try {
            switch (owner.getType()) {
                case PLAYER:
                    UUID ownerPlayerId = parseUuid(owner.getId());
                    if (ownerPlayerId != null) {
                        return ownerPlayerId.equals(player.getUniqueId());
                    }
                    return owner.getDisplayName() != null && owner.getDisplayName().equalsIgnoreCase(player.getName());
                case TOWN:
                    Town playerTown = TownyAPI.getInstance().getTown(player);
                    Town ownerTown = resolveTown(owner);
                    return playerTown != null && ownerTown != null && sameTown(playerTown, ownerTown);
                case NATION:
                    Town playerNationTown = TownyAPI.getInstance().getTown(player);
                    if (playerNationTown == null || !playerNationTown.hasNation()) {
                        return false;
                    }
                    String ownerNationName = resolveOwnerNationName(owner);
                    return ownerNationName != null && ownerNationName.equalsIgnoreCase(playerNationTown.getNation().getName());
                default:
                    return false;
            }
        } catch (Exception ex) {
            return false;
        }
    }

    @Override

    public CaptureOwner refreshOwner(CaptureOwner owner) {
        if (owner == null || owner.getType() == null) {
            return owner;
        }
        try {
            if (owner.getType() == CaptureOwnerType.TOWN) {
                Town town = resolveTown(owner);
                if (town == null) {
                    return owner;
                }
                String refreshedId = town.getUUID() != null ? town.getUUID().toString() : owner.getId();
                String refreshedName = town.getName();
                boolean sameId = owner.getId() != null && owner.getId().equalsIgnoreCase(refreshedId);
                boolean sameName = owner.getDisplayName() != null && owner.getDisplayName().equals(refreshedName);
                if (sameId && sameName) {
                    return owner;
                }
                return new CaptureOwner(CaptureOwnerType.TOWN, refreshedId, refreshedName);
            }
            if (owner.getType() == CaptureOwnerType.PLAYER) {
                Resident resident = resolveResident(owner);
                if (resident == null) {
                    return owner;
                }
                String refreshedId = resident.getUUID() != null ? resident.getUUID().toString() : owner.getId();
                String refreshedName = resident.getName();
                boolean sameId = owner.getId() != null && owner.getId().equalsIgnoreCase(refreshedId);
                boolean sameName = owner.getDisplayName() != null && owner.getDisplayName().equals(refreshedName);
                if (sameId && sameName) {
                    return owner;
                }
                return new CaptureOwner(CaptureOwnerType.PLAYER, refreshedId, refreshedName);
            }
        } catch (Exception ignored) {
            return owner;
        }
        return owner;
    }

    @Override

    public boolean isPlayerInSameTown(Player player, CaptureOwner owner) {
        if (player == null || owner == null) {
            return false;
        }
        try {
            Town playerTown = TownyAPI.getInstance().getTown(player);
            Town ownerTown = resolveTown(owner);
            if (playerTown == null || ownerTown == null) {
                return false;
            }
            return sameTown(playerTown, ownerTown);
        } catch (Exception ex) {
            return false;
        }
    }

    @Override

    public boolean isPlayerInSameNation(Player player, CaptureOwner owner) {
        if (player == null || owner == null) {
            return false;
        }
        try {
            Town playerTown = TownyAPI.getInstance().getTown(player);
            if (playerTown == null || !playerTown.hasNation()) {
                return false;
            }
            String playerNationName = playerTown.getNation().getName();
            String ownerNationName = resolveOwnerNationName(owner);
            return ownerNationName != null && ownerNationName.equalsIgnoreCase(playerNationName);
        } catch (Exception ex) {
            return false;
        }
    }

    private String resolveOwnerTownName(CaptureOwner owner) {
        Town town = resolveTown(owner);
        return town != null ? town.getName() : null;
    }

    private String resolveOwnerNationName(CaptureOwner owner) {
        if (owner == null || owner.getType() == null) {
            return null;
        }
        try {
            if (owner.getType() == CaptureOwnerType.NATION) {
                return owner.getDisplayName();
            }

            String ownerTownName = resolveOwnerTownName(owner);
            if (ownerTownName == null || ownerTownName.isEmpty()) {
                return null;
            }
            Town town = TownyAPI.getInstance().getTown(ownerTownName);
            if (town == null || !town.hasNation()) {
                return null;
            }
            return town.getNation().getName();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveTownName(Player player) {
        try {
            Town town = TownyAPI.getInstance().getTown(player);
            return town != null ? town.getName() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveNationName(Player player) {
        try {
            Town town = TownyAPI.getInstance().getTown(player);
            if (town == null || !town.hasNation()) {
                return null;
            }
            return town.getNation().getName();
        } catch (Exception ex) {
            return null;
        }
    }

    private Town resolveTown(CaptureOwner owner) {
        if (owner == null || owner.getType() == null) {
            return null;
        }
        try {
            if (owner.getType() == CaptureOwnerType.TOWN) {
                Town town = resolveTownById(owner.getId());
                if (town != null) {
                    return town;
                }
                return resolveTownByName(owner.getDisplayName());
            }

            if (owner.getType() == CaptureOwnerType.PLAYER) {
                Resident resident = resolveResident(owner);
                if (resident == null || !resident.hasTown()) {
                    return null;
                }
                return resident.getTown();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private Resident resolveResident(CaptureOwner owner) {
        if (owner == null) {
            return null;
        }
        try {
            UUID residentId = parseUuid(owner.getId());
            if (residentId != null) {
                Resident resident = TownyAPI.getInstance().getResident(residentId);
                if (resident != null) {
                    return resident;
                }
            }
            String displayName = owner.getDisplayName();
            if (displayName != null && !displayName.trim().isEmpty()) {
                return TownyAPI.getInstance().getResident(displayName.trim());
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private Town resolveTownByName(String ownerName) {
        if (ownerName == null || ownerName.trim().isEmpty()) {
            return null;
        }
        try {
            return TownyAPI.getInstance().getTown(ownerName.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Town resolveTownById(String ownerId) {
        UUID townId = parseUuid(ownerId);
        if (townId == null) {
            return null;
        }
        try {
            for (Town town : TownyAPI.getInstance().getTowns()) {
                if (town == null || town.getUUID() == null) {
                    continue;
                }
                if (town.getUUID().equals(townId)) {
                    return town;
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean sameTown(Town first, Town second) {
        if (first == null || second == null) {
            return false;
        }
        try {
            if (first.getUUID() != null && second.getUUID() != null) {
                return first.getUUID().equals(second.getUUID());
            }
        } catch (Exception ignored) {
            // Fallback to name compare below.
        }
        return first.getName().equalsIgnoreCase(second.getName());
    }
}

