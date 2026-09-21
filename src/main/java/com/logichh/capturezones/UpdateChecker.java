package com.logichh.capturezones;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class UpdateChecker {
    private static final String PROJECT_PAGE_URL = "https://modrinth.com/plugin/capturezones";
    
    private final CaptureZones plugin;
    private final Logger logger;
    private final String projectId = "IdhAg76d";
    private final String currentVersion;
    
    private String latestVersion = null;
    private String changelog = null;
    private long lastCheck = 0;
    private static final long CACHE_DURATION = 6 * 60 * 60 * 1000;
    
    public UpdateChecker(CaptureZones plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.currentVersion = plugin.getDescription().getVersion();
    }
    
    public CompletableFuture<Boolean> checkForUpdates() {
        if (System.currentTimeMillis() - lastCheck < CACHE_DURATION && latestVersion != null) {
            return CompletableFuture.completedFuture(isUpdateAvailable());
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String apiUrl = "https://api.modrinth.com/v2/project/" + projectId + "/version";
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "CaptureZones/" + currentVersion);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                
                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    logger.warning("Failed to check for updates: HTTP " + responseCode);
                    return false;
                }
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JsonArray versions = JsonParser.parseString(response.toString()).getAsJsonArray();
                if (versions.size() == 0) {
                    logger.warning("No versions found on Modrinth");
                    return false;
                }

                JsonObject latestVersionObj = versions.get(0).getAsJsonObject();
                latestVersion = latestVersionObj.get("version_number").getAsString();

                if (latestVersionObj.has("changelog")) {
                    String fullChangelog = latestVersionObj.get("changelog").getAsString();
                    changelog = fullChangelog.length() > 200 ? fullChangelog.substring(0, 200) + "..." : fullChangelog;
                }
                
                lastCheck = System.currentTimeMillis();
                
                if (isUpdateAvailable()) {
                    logger.info("Update available! Current: " + currentVersion + " | Latest: " + latestVersion);
                    return true;
                }
                
                return false;
                
            } catch (Exception e) {
                logger.warning("Failed to check for updates: " + e.getMessage());
                if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                    e.printStackTrace();
                }
                return false;
            }
        });
    }
    
    public boolean isUpdateAvailable() {
        if (latestVersion == null) return false;
        return compareVersions(currentVersion, latestVersion) < 0;
    }

    private int compareVersions(String v1, String v2) {
        v1 = v1.replaceFirst("^v", "");
        v2 = v2.replaceFirst("^v", "");
        
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        
        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int p1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int p2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;
            
            if (p1 < p2) return -1;
            if (p1 > p2) return 1;
        }
        
        return 0;
    }

    private int parseVersionPart(String part) {
        try {
            String numericPart = part.split("-")[0];
            return Integer.parseInt(numericPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void showUpdateNotification(Player player) {
        if (!isUpdateAvailable()) return;
        if (plugin.isNotificationsDisabled(player)) return;
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.isNotificationsDisabled(player)) {
                return;
            }
            player.sendTitle(
                ChatColor.GOLD + "CaptureZones Update",
                ChatColor.WHITE + currentVersion + ChatColor.DARK_GRAY + " -> " + ChatColor.GREEN + latestVersion,
                8, 60, 16
            );

            player.sendMessage("");
            player.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "----------------------------------------");
            player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "CaptureZones update available");
            player.sendMessage(ChatColor.GRAY + "Current: " + ChatColor.RED + currentVersion);
            player.sendMessage(ChatColor.GRAY + "Latest:  " + ChatColor.GREEN + latestVersion);
            if (changelog != null && !changelog.trim().isEmpty()) {
                player.sendMessage(ChatColor.GRAY + "Notes: " + ChatColor.WHITE + formatChangelogPreview(changelog));
            }

            Component downloadComponent = Component.text("Open CaptureZones on Modrinth", NamedTextColor.AQUA)
                .decorate(TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(PROJECT_PAGE_URL))
                .hoverEvent(HoverEvent.showText(Component.text(
                    "Open the official CaptureZones Modrinth page",
                    NamedTextColor.GRAY
                )));
            player.sendMessage(downloadComponent);
            player.sendMessage(ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "----------------------------------------");
            player.sendMessage("");
            
        }, 60L);
    }

    private String formatChangelogPreview(String text) {
        if (text == null) {
            return "";
        }
        String flattened = text
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace("`", "")
            .replace("*", "")
            .replace("_", "")
            .replaceAll("\\s+", " ")
            .trim();
        if (flattened.length() <= 100) {
            return flattened;
        }
        return flattened.substring(0, 100) + "...";
    }
    
    public String getCurrentVersion() {
        return currentVersion;
    }
    
    public String getLatestVersion() {
        return latestVersion;
    }
    
    public String getDownloadUrl() {
        return PROJECT_PAGE_URL;
    }
}

