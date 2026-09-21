package com.logichh.capturezones;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ScoreboardTeamOwnerPlatformAdapter implements OwnerPlatformAdapter {
    @Override

    public String getPlatformKey() {
        return "scoreboard";
    }

    @Override

    public EnumSet<CaptureOwnerType> getSupportedOwnerTypes() {
        return EnumSet.of(CaptureOwnerType.PLAYER, CaptureOwnerType.TOWN);
    }

    @Override

    public String resolveOwnerName(Player player, CaptureOwnerType ownerType) {
        if (player == null || ownerType == null) {
            return null;
        }
        if (ownerType == CaptureOwnerType.PLAYER) {
            return player.getName();
        }
        if (ownerType == CaptureOwnerType.TOWN) {
            Team team = resolvePlayerTeam(player);
            return team != null ? team.getName() : null;
        }
        return null;
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
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return names;
        }
        if (ownerType != CaptureOwnerType.TOWN) {
            return Collections.emptyList();
        }

        Set<String> names = new LinkedHashSet<>();
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            collectTeamNames(manager.getMainScoreboard(), names);
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null) {
                continue;
            }
            collectTeamNames(online.getScoreboard(), names);
        }
        return new ArrayList<>(names);
    }

    @Override

    public String normalizeOwnerName(String ownerName, CaptureOwnerType ownerType) {
        if (ownerType == null || ownerName == null) {
            return null;
        }
        String raw = ownerName.trim();
        if (raw.isEmpty()) {
            return null;
        }

        if (ownerType == CaptureOwnerType.PLAYER) {
            return raw;
        }
        if (ownerType != CaptureOwnerType.TOWN) {
            return null;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            String matched = matchTeamName(manager.getMainScoreboard(), raw);
            if (matched != null) {
                return matched;
            }
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            String matched = matchTeamName(online.getScoreboard(), raw);
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    @Override

    public boolean ownerExists(String ownerName, CaptureOwnerType ownerType) {
        return normalizeOwnerName(ownerName, ownerType) != null;
    }

    @Override
    public List<OwnerMember> getOwnerMembers(CaptureOwner owner) {
        if (owner == null || owner.getType() == null || owner.getDisplayName() == null) {
            return Collections.emptyList();
        }
        Set<String> entries = new LinkedHashSet<>();
        if (owner.getType() == CaptureOwnerType.PLAYER) {
            entries.add(owner.getDisplayName());
        } else if (owner.getType() == CaptureOwnerType.TOWN) {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                collectTeamEntries(manager.getMainScoreboard(), owner.getDisplayName(), entries);
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online != null) {
                    collectTeamEntries(online.getScoreboard(), owner.getDisplayName(), entries);
                }
            }
        }
        Map<UUID, OwnerMember> members = new LinkedHashMap<>();
        for (String entry : entries) {
            if (entry == null || entry.trim().isEmpty()) {
                continue;
            }
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.trim());
            if (!player.isOnline() && !player.hasPlayedBefore()) {
                continue;
            }
            String name = player.getName() == null ? entry.trim() : player.getName();
            members.putIfAbsent(player.getUniqueId(), new OwnerMember(player.getUniqueId(), name));
        }
        return new ArrayList<>(members.values());
    }

    private void collectTeamEntries(Scoreboard scoreboard, String teamName, Set<String> output) {
        String matched = matchTeamName(scoreboard, teamName);
        if (matched == null) {
            return;
        }
        Team team = scoreboard.getTeam(matched);
        if (team != null) {
            output.addAll(team.getEntries());
        }
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
        if (ownerType != CaptureOwnerType.TOWN || ownerName == null || ownerName.trim().isEmpty()) {
            return fallbackHex;
        }

        String teamName = normalizeOwnerName(ownerName, CaptureOwnerType.TOWN);
        if (teamName == null) {
            return fallbackHex;
        }

        Team team = resolveTeamByName(teamName);
        if (team == null) {
            return fallbackHex;
        }

        String mapped = mapChatColorToHex(team.getColor());
        return mapped != null ? mapped : fallbackHex;
    }

    @Override

    public boolean isPlayerInSameTown(Player player, CaptureOwner owner) {
        if (player == null || owner == null) {
            return false;
        }

        String ownerTeam = resolveOwnerTeamName(owner);
        if (ownerTeam == null || ownerTeam.isEmpty()) {
            return false;
        }

        Team playerTeam = resolvePlayerTeam(player);
        return playerTeam != null && playerTeam.getName().equalsIgnoreCase(ownerTeam);
    }

    @Override

    public boolean isPlayerInSameNation(Player player, CaptureOwner owner) {
        return false;
    }

    private void collectTeamNames(Scoreboard scoreboard, Set<String> output) {
        if (scoreboard == null || output == null) {
            return;
        }
        for (Team team : scoreboard.getTeams()) {
            if (team == null || team.getName() == null || team.getName().trim().isEmpty()) {
                continue;
            }
            output.add(team.getName());
        }
    }

    private String matchTeamName(Scoreboard scoreboard, String lookupName) {
        if (scoreboard == null || lookupName == null || lookupName.trim().isEmpty()) {
            return null;
        }
        Team exact = scoreboard.getTeam(lookupName);
        if (exact != null) {
            return exact.getName();
        }

        String lowered = lookupName.toLowerCase(Locale.ROOT);
        for (Team team : scoreboard.getTeams()) {
            if (team == null || team.getName() == null) {
                continue;
            }
            if (team.getName().toLowerCase(Locale.ROOT).equals(lowered)) {
                return team.getName();
            }
        }
        return null;
    }

    private Team resolvePlayerTeam(Player player) {
        if (player == null) {
            return null;
        }
        Team team = resolveEntryTeam(player.getScoreboard(), player.getName());
        if (team != null) {
            return team;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return null;
        }
        return resolveEntryTeam(manager.getMainScoreboard(), player.getName());
    }

    private Team resolveEntryTeam(Scoreboard scoreboard, String entry) {
        if (scoreboard == null || entry == null || entry.trim().isEmpty()) {
            return null;
        }
        return scoreboard.getEntryTeam(entry);
    }

    private Team resolveTeamByName(String teamName) {
        if (teamName == null || teamName.trim().isEmpty()) {
            return null;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            Team team = manager.getMainScoreboard().getTeam(teamName);
            if (team != null) {
                return team;
            }
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == null || online.getScoreboard() == null) {
                continue;
            }
            Team team = online.getScoreboard().getTeam(teamName);
            if (team != null) {
                return team;
            }
        }
        return null;
    }

    private String resolveOwnerTeamName(CaptureOwner owner) {
        if (owner == null || owner.getType() == null) {
            return null;
        }

        if (owner.getType() == CaptureOwnerType.TOWN) {
            return normalizeOwnerName(owner.getDisplayName(), CaptureOwnerType.TOWN);
        }

        if (owner.getType() != CaptureOwnerType.PLAYER) {
            return null;
        }

        Player online = owner.getDisplayName() != null ? Bukkit.getPlayerExact(owner.getDisplayName()) : null;
        if (online != null) {
            Team team = resolvePlayerTeam(online);
            return team != null ? team.getName() : null;
        }

        UUID uuid = null;
        if (owner.getId() != null && !owner.getId().trim().isEmpty()) {
            try {
                uuid = UUID.fromString(owner.getId().trim());
            } catch (Exception ignored) {
                uuid = null;
            }
        }
        if (uuid == null) {
            return null;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline == null || offline.getName() == null || offline.getName().trim().isEmpty()) {
            return null;
        }

        Team team = resolveEntryTeam(Bukkit.getScoreboardManager() != null ? Bukkit.getScoreboardManager().getMainScoreboard() : null, offline.getName());
        return team != null ? team.getName() : null;
    }

    private String mapChatColorToHex(ChatColor color) {
        if (color == null) {
            return null;
        }
        switch (color) {
            case BLACK:
                return "#000000";
            case DARK_BLUE:
                return "#0000AA";
            case DARK_GREEN:
                return "#00AA00";
            case DARK_AQUA:
                return "#00AAAA";
            case DARK_RED:
                return "#AA0000";
            case DARK_PURPLE:
                return "#AA00AA";
            case GOLD:
                return "#FFAA00";
            case GRAY:
                return "#AAAAAA";
            case DARK_GRAY:
                return "#555555";
            case BLUE:
                return "#5555FF";
            case GREEN:
                return "#55FF55";
            case AQUA:
                return "#55FFFF";
            case RED:
                return "#FF5555";
            case LIGHT_PURPLE:
                return "#FF55FF";
            case YELLOW:
                return "#FFFF55";
            case WHITE:
                return "#FFFFFF";
            default:
                return null;
        }
    }
}

