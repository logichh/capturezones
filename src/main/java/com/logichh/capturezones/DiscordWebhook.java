package com.logichh.capturezones;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.configuration.ConfigurationSection;

import java.awt.Color;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class DiscordWebhook {
    
    private final CaptureZones plugin;
    private final Logger logger;
    private String webhookUrl;
    private boolean enabled;
    private boolean useEmbeds;
    private String mentionRole;
    private String mentionRoleId;
    private boolean showCoordinates;
    private boolean showRewardsAmount;
    private String embedAuthorName;
    private String embedAuthorIcon;
    private String embedFooterText;
    private String embedFooterIcon;
    private String embedThumbnailUrl;
    private boolean embedTimestamp;
    
    private final Map<String, Boolean> alertToggles = new HashMap<>();
    private final Map<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    private long rateLimitMs = 1000;
        private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    
    public DiscordWebhook(CaptureZones plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        loadConfig();
    }
    
    public void loadConfig() {
        ConfigurationSection discord = plugin.getConfig().getConfigurationSection("discord");
        if (discord == null) {
            this.enabled = false;
            logger.info("Discord webhook not configured.");
            return;
        }
        
        this.enabled = discord.getBoolean("enabled", false);
        this.webhookUrl = discord.getString("webhook-url", "").trim();
        this.useEmbeds = discord.getBoolean("use-embeds", true);
        parseMentionRole(discord.getString("mention-role", ""));
        this.showCoordinates = discord.getBoolean("show-coordinates", true);
        this.showRewardsAmount = discord.getBoolean("show-rewards-amount", true);
        this.rateLimitMs = discord.getLong("rate-limit-ms", 1000);

        ConfigurationSection embed = discord.getConfigurationSection("embed");
        String serverName = plugin.getServer() != null ? plugin.getServer().getName() : "Server";
        String authorNameRaw = embed != null ? embed.getString("author-name", "CaptureZones") : "CaptureZones";
        this.embedAuthorName = resolveEmbedText(authorNameRaw, serverName);
        this.embedAuthorIcon = embed != null ? embed.getString("author-icon", "") : "";
        String footerTextRaw = embed != null ? embed.getString("footer-text", "CaptureZones") : "CaptureZones";
        this.embedFooterText = resolveEmbedText(footerTextRaw, serverName);
        this.embedFooterIcon = embed != null ? embed.getString("footer-icon", "") : "";
        this.embedThumbnailUrl = embed != null ? embed.getString("thumbnail-url", "") : "";
        this.embedTimestamp = embed == null || embed.getBoolean("show-timestamp", true);
        
        ConfigurationSection alerts = discord.getConfigurationSection("alerts");
        if (alerts != null) {
            alertToggles.put("capture-started", alerts.getBoolean("capture-started", true));
            alertToggles.put("capture-completed", alerts.getBoolean("capture-completed", true));
            alertToggles.put("capture-failed", alerts.getBoolean("capture-failed", true));
            alertToggles.put("capture-cancelled", alerts.getBoolean("capture-cancelled", true));
            alertToggles.put("rewards-distributed", alerts.getBoolean("rewards-distributed", true));
            alertToggles.put("reinforcement-phases", alerts.getBoolean("reinforcement-phases", false));
            alertToggles.put("zone-created", alerts.getBoolean("zone-created", true));
            alertToggles.put("zone-deleted", alerts.getBoolean("zone-deleted", true));
            alertToggles.put("weekly-reset", alerts.getBoolean("weekly-reset", true));
            alertToggles.put("first-capture-bonus", alerts.getBoolean("first-capture-bonus", true));
            alertToggles.put("player-death", alerts.getBoolean("player-death", true));
            alertToggles.put("koth-activated", alerts.getBoolean("koth-activated", true));
            alertToggles.put("koth-stopped", alerts.getBoolean("koth-stopped", true));
            alertToggles.put("koth-captured", alerts.getBoolean("koth-captured", true));
            alertToggles.put("new-records", alerts.getBoolean("new-records", false));
            alertToggles.put("milestones", alerts.getBoolean("milestones", false));
        }
        
        if (enabled && !webhookUrl.isEmpty()) {
            // validate basic webhook URL format to catch misconfiguration early
            Pattern webhookPattern = Pattern.compile("^https?://(canary\\.|ptb\\.)?discord(app)?\\.com/api/webhooks/\\d+(/[A-Za-z0-9-_/.]+)?$");
            if (!webhookPattern.matcher(webhookUrl).matches()) {
                logger.warning("Discord webhook URL looks invalid; disabling webhook. Please verify the URL in config.yml");
                this.enabled = false;
                return;
            }
            logger.info("Discord webhook enabled! Alerts will be sent to Discord.");
        } else if (enabled) {
            logger.warning("Discord webhook enabled but URL not configured!");
            this.enabled = false;
        }
    }

    private void parseMentionRole(String rawValue) {
        mentionRole = "";
        mentionRoleId = null;
        if (rawValue == null) {
            return;
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (trimmed.matches("^<@&(\\d+)>$")) {
            mentionRole = trimmed;
            mentionRoleId = trimmed.substring(3, trimmed.length() - 1);
            return;
        }
        if (trimmed.matches("^\\d+$")) {
            mentionRoleId = trimmed;
            mentionRole = "<@&" + trimmed + ">";
            return;
        }
        mentionRole = trimmed;
        if (enabled) {
            logger.warning("Discord mention-role should be a role ID or <@&ROLE_ID> to ensure role pings work.");
        }
    }

    private String resolveEmbedText(String value, String serverName) {
        if (value == null) {
            return "";
        }
        String resolved = value.replace("{server}", serverName);
        resolved = resolved.replace("{plugin}", plugin.getName());
        resolved = resolved.replace("{version}", plugin.getDescription().getVersion());
        return resolved;
    }
    
    private boolean isAlertEnabled(String alertType) {
        return enabled && alertToggles.getOrDefault(alertType, false);
    }

    private boolean isRateLimited(String zoneId) {
        long now = System.currentTimeMillis();
        Long lastTime = lastMessageTime.get(zoneId);

        if (lastTime != null && (now - lastTime) < rateLimitMs) {
            return true;
        }

        lastMessageTime.compute(zoneId, (key, oldValue) -> {
            if (oldValue == null || (now - oldValue) >= rateLimitMs) {
                return now;
            }
            return oldValue;
        });
        
        return false;
    }
    
    public void sendCaptureStarted(String zoneId, String zoneName, String townName, String location) {
        if (!isAlertEnabled("capture-started") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.capture.started.title"), 
                Messages.get("discord.capture.started.description", Map.of(
                    "town", townName,
                    "zone", zoneName
                )),
                Color.YELLOW,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.town"), townName, true),
                showCoordinates ? createField(Messages.get("discord.field.location"), location, true) : null
            );
        } else {
            sendPlainText(Messages.get("discord.capture.started.plain", Map.of(
                "town", townName,
                "zone", zoneName
            )));
        }
    }
    
    public void sendCaptureCompleted(String zoneId, String zoneName, String townName, String captureTime) {
        if (!isAlertEnabled("capture-completed") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.capture.completed.title"), 
                Messages.get("discord.capture.completed.description", Map.of(
                    "town", townName,
                    "zone", zoneName
                )),
                Color.GREEN,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.controlling-town"), townName, true),
                createField(Messages.get("discord.field.capture-time"), captureTime, true)
            );
        } else {
            sendPlainText(Messages.get("discord.capture.completed.plain", Map.of(
                "town", townName,
                "zone", zoneName
            )));
        }
    }
    
    public void sendCaptureFailed(String zoneId, String zoneName, String townName, String reason) {
        if (!isAlertEnabled("capture-failed") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.capture.failed.title"), 
                Messages.get("discord.capture.failed.description", Map.of(
                    "town", townName,
                    "zone", zoneName
                )),
                Color.RED,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.town"), townName, true),
                createField(Messages.get("discord.field.reason"), reason, false)
            );
        } else {
            sendPlainText(Messages.get("discord.capture.failed.plain", Map.of(
                "town", townName,
                "zone", zoneName,
                "reason", reason
            )));
        }
    }
    
    public void sendCaptureCancelled(String zoneId, String zoneName, String townName, String reason) {
        if (!isAlertEnabled("capture-cancelled") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.capture.cancelled.title"), 
                Messages.get("discord.capture.cancelled.description", Map.of(
                    "town", townName,
                    "zone", zoneName
                )),
                Color.ORANGE,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.town"), townName, true),
                createField(Messages.get("discord.field.reason"), reason, false)
            );
        } else {
            sendPlainText(Messages.get("discord.capture.cancelled.plain", Map.of(
                "town", townName,
                "zone", zoneName,
                "reason", reason
            )));
        }
    }
    
    public void sendRewardsDistributed(String zoneId, String zoneName, String townName, double amount, String rewardType) {
        if (!isAlertEnabled("rewards-distributed")) return;
        
        String amountStr = showRewardsAmount ? 
            Messages.get("discord.format.currency", Map.of("amount", String.format("%.2f", amount))) : 
            Messages.get("discord.value.hidden-amount");
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.rewards.distributed.title"), 
                Messages.get("discord.rewards.distributed.description", Map.of(
                    "town", townName,
                    "type", rewardType,
                    "zone", zoneName
                )),
                new Color(255, 215, 0), // Gold
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.town"), townName, true),
                showRewardsAmount ? createField(Messages.get("discord.field.amount"), amountStr, true) : null,
                createField(Messages.get("discord.field.type"), rewardType, true)
            );
        } else {
            sendPlainText(Messages.get("discord.rewards.distributed.plain", Map.of(
                "town", townName,
                "amount", amountStr,
                "type", rewardType,
                "zone", zoneName
            )));
        }
    }
    
    public void sendReinforcementPhase(String zoneId, String zoneName, int phase, int mobCount) {
        if (!isAlertEnabled("reinforcement-phases") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.reinforcements.title"), 
                Messages.get("discord.reinforcements.description", Map.of(
                    "phase", String.valueOf(phase),
                    "zone", zoneName
                )),
                Color.RED,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.phase"), String.valueOf(phase), true),
                createField(Messages.get("discord.field.defenders"), 
                    Messages.get("discord.value.mobs", Map.of("count", String.valueOf(mobCount))), true)
            );
        } else {
            sendPlainText(Messages.get("discord.reinforcements.plain", Map.of(
                "phase", String.valueOf(phase),
                "zone", zoneName,
                "count", String.valueOf(mobCount)
            )));
        }
    }
    
    public void sendZoneCreated(String zoneId, String zoneName, String creator, String type, int radius, double reward) {
        if (!isAlertEnabled("zone-created")) return;
        
        if (useEmbeds) {
            String rewardStr = showRewardsAmount ? 
                Messages.get("discord.format.currency", Map.of("amount", String.format("%.2f", reward))) : 
                Messages.get("discord.value.hidden-amount");
            sendEmbed(Messages.get("discord.zone.created.title"), 
                Messages.get("discord.zone.created.description"),
                Color.CYAN,
                createField(Messages.get("discord.field.zone-name"), zoneName, true),
                createField(Messages.get("discord.field.type"), type, true),
                createField(Messages.get("discord.field.radius"), 
                    Messages.get("discord.value.chunks", Map.of("count", String.valueOf(radius))), true),
                showRewardsAmount ? createField(Messages.get("discord.field.reward"), rewardStr, true) : null,
                createField(Messages.get("discord.field.created-by"), creator, true)
            );
        } else {
            sendPlainText(Messages.get("discord.zone.created.plain", Map.of(
                "zone", zoneName,
                "creator", creator
            )));
        }
    }
    
    public void sendZoneDeleted(String zoneId, String zoneName, String deletedBy) {
        if (!isAlertEnabled("zone-deleted")) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.zone.deleted.title"), 
                Messages.get("discord.zone.deleted.description", Map.of("zone", zoneName)),
                Color.GRAY,
                createField(Messages.get("discord.field.zone-name"), zoneName, true),
                createField(Messages.get("discord.field.deleted-by"), deletedBy, true)
            );
        } else {
            sendPlainText(Messages.get("discord.zone.deleted.plain", Map.of(
                "zone", zoneName,
                "deleted_by", deletedBy
            )));
        }
    }
    
    public void sendWeeklyReset(int zonesReset) {
        if (!isAlertEnabled("weekly-reset")) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.weekly-reset.title"), 
                Messages.get("discord.weekly-reset.description"),
                Color.MAGENTA,
                createField(Messages.get("discord.field.zones-reset"), String.valueOf(zonesReset), true),
                createField(Messages.get("discord.field.first-capture-bonus"), 
                    Messages.get("discord.value.first-capture-bonus-active"), false)
            );
        } else {
            sendPlainText(Messages.get("discord.weekly-reset.plain", Map.of(
                "count", String.valueOf(zonesReset)
            )));
        }
    }
    
    public void sendFirstCaptureBonus(String zoneId, String zoneName, String townName, double bonusAmount) {
        if (!isAlertEnabled("first-capture-bonus")) return;
        
        String amountStr = showRewardsAmount ? 
            Messages.get("discord.format.currency", Map.of("amount", String.format("%.2f", bonusAmount))) : 
            Messages.get("discord.value.hidden-amount");
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.first-capture-bonus.title"), 
                Messages.get("discord.first-capture-bonus.description", Map.of(
                    "town", townName,
                    "zone", zoneName
                )),
                new Color(255, 140, 0), // Dark orange
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.town"), townName, true),
                showRewardsAmount ? createField(Messages.get("discord.field.bonus"), amountStr, true) : null
            );
        } else {
            sendPlainText(Messages.get("discord.first-capture-bonus.plain", Map.of(
                "town", townName,
                "amount", amountStr,
                "zone", zoneName
            )));
        }
    }
    
    public void sendPlayerDeath(String zoneId, String zoneName, String victim, String killer, String townName) {
        if (!isAlertEnabled("player-death") || isRateLimited(zoneId)) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.player-death.title"), 
                Messages.get("discord.player-death.description", Map.of(
                    "victim", victim,
                    "killer", killer,
                    "town", townName
                )),
                Color.DARK_GRAY,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.victim"), victim, true),
                createField(Messages.get("discord.field.killer"), killer, true)
            );
        } else {
            sendPlainText(Messages.get("discord.player-death.plain", Map.of(
                "victim", victim,
                "killer", killer,
                "zone", zoneName
            )));
        }
    }

    public void sendKothActivated(String zoneId, String zoneName, String captureTime, String radius) {
        if (!isAlertEnabled("koth-activated") || isRateLimited("koth-activated:" + zoneId)) return;

        if (useEmbeds) {
            sendEmbed(Messages.get("discord.koth.activated.title"),
                Messages.get("discord.koth.activated.description", Map.of(
                    "zone", zoneName
                )),
                Color.RED,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.capture-time"), captureTime, true),
                createField(Messages.get("discord.field.radius"), radius, true)
            );
        } else {
            sendPlainText(Messages.get("discord.koth.activated.plain", Map.of(
                "zone", zoneName,
                "time", captureTime,
                "radius", radius
            )));
        }
    }

    public void sendKothStopped(String zoneId, String zoneName, String reason) {
        if (!isAlertEnabled("koth-stopped") || isRateLimited("koth-stopped:" + zoneId)) return;

        if (useEmbeds) {
            sendEmbed(Messages.get("discord.koth.stopped.title"),
                Messages.get("discord.koth.stopped.description", Map.of(
                    "zone", zoneName
                )),
                Color.ORANGE,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.reason"), reason, false)
            );
        } else {
            sendPlainText(Messages.get("discord.koth.stopped.plain", Map.of(
                "zone", zoneName,
                "reason", reason
            )));
        }
    }

    public void sendKothCaptured(String zoneId, String zoneName, String playerName, String captureTime, String rewards) {
        if (!isAlertEnabled("koth-captured") || isRateLimited("koth-captured:" + zoneId)) return;

        if (useEmbeds) {
            sendEmbed(Messages.get("discord.koth.captured.title"),
                Messages.get("discord.koth.captured.description", Map.of(
                    "player", playerName,
                    "zone", zoneName
                )),
                Color.GREEN,
                createField(Messages.get("discord.field.zone"), zoneName, true),
                createField(Messages.get("discord.field.player"), playerName, true),
                createField(Messages.get("discord.field.capture-time"), captureTime, true),
                createField(Messages.get("discord.field.reward"), rewards, false)
            );
        } else {
            sendPlainText(Messages.get("discord.koth.captured.plain", Map.of(
                "player", playerName,
                "zone", zoneName,
                "time", captureTime,
                "rewards", rewards
            )));
        }
    }
    
    public void sendNewRecord(String recordType, String holder, String value) {
        if (!isAlertEnabled("new-records")) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.records.new.title"), 
                Messages.get("discord.records.new.description", Map.of("record", recordType)),
                new Color(218, 165, 32), // Goldenrod
                createField(Messages.get("discord.field.record-type"), recordType, true),
                createField(Messages.get("discord.field.holder"), holder, true),
                createField(Messages.get("discord.field.value"), value, true)
            );
        } else {
            sendPlainText(Messages.get("discord.records.new.plain", Map.of(
                "holder", holder,
                "record", recordType,
                "value", value
            )));
        }
    }
    
    public void sendMilestone(String entityName, String entityType, String milestone) {
        if (!isAlertEnabled("milestones")) return;
        
        if (useEmbeds) {
            sendEmbed(Messages.get("discord.milestone.title"), 
                Messages.get("discord.milestone.description", Map.of("entity", entityName)),
                Color.PINK,
                createField(entityType, entityName, true),
                createField(Messages.get("discord.field.milestone"), milestone, false)
            );
        } else {
            sendPlainText(Messages.get("discord.milestone.plain", Map.of(
                "entity", entityName,
                "milestone", milestone
            )));
        }
    }
    
    private JsonObject createField(String name, String value, boolean inline) {
        if (name == null || value == null) return null;
        
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value);
        field.addProperty("inline", inline);
        return field;
    }
    
    private void sendEmbed(String title, String description, Color color, JsonObject... fields) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject embed = buildEmbed(title, description, color, fields);

                JsonObject payload = new JsonObject();
                applyRoleMention(payload, null);
                JsonArray embeds = new JsonArray();
                embeds.add(embed);
                payload.add("embeds", embeds);
                
                sendWebhook(payload);
                
            } catch (Exception e) {
                logger.warning("Failed to send Discord embed: " + e.getMessage());
            }
        });
    }
    
    private void sendPlainText(String message) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject payload = new JsonObject();
                applyRoleMention(payload, message);
                sendWebhook(payload);
                
            } catch (Exception e) {
                logger.warning("Failed to send Discord message: " + e.getMessage());
            }
        });
    }

    private JsonObject buildEmbed(String title, String description, Color color, JsonObject... fields) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("description", description);
        embed.addProperty("color", color.getRGB() & 0xFFFFFF);
        if (embedTimestamp) {
            embed.addProperty("timestamp", Instant.now().toString());
        }
        if (embedAuthorName != null && !embedAuthorName.isEmpty()) {
            JsonObject author = new JsonObject();
            author.addProperty("name", embedAuthorName);
            if (embedAuthorIcon != null && !embedAuthorIcon.isEmpty()) {
                author.addProperty("icon_url", embedAuthorIcon);
            }
            embed.add("author", author);
        }
        if (embedFooterText != null && !embedFooterText.isEmpty()) {
            JsonObject footer = new JsonObject();
            footer.addProperty("text", embedFooterText);
            if (embedFooterIcon != null && !embedFooterIcon.isEmpty()) {
                footer.addProperty("icon_url", embedFooterIcon);
            }
            embed.add("footer", footer);
        }
        if (embedThumbnailUrl != null && !embedThumbnailUrl.isEmpty()) {
            JsonObject thumbnail = new JsonObject();
            thumbnail.addProperty("url", embedThumbnailUrl);
            embed.add("thumbnail", thumbnail);
        }
        if (fields != null && fields.length > 0) {
            JsonArray fieldArray = new JsonArray();
            for (JsonObject field : fields) {
                if (field != null) {
                    fieldArray.add(field);
                }
            }
            if (!fieldArray.isEmpty()) {
                embed.add("fields", fieldArray);
            }
        }
        return embed;
    }

    private void applyRoleMention(JsonObject payload, String message) {
        if (message == null) {
            message = "";
        }
        if (mentionRole == null || mentionRole.isEmpty()) {
            if (!message.isEmpty()) {
                payload.addProperty("content", message);
            }
            return;
        }
        String content = message.isEmpty() ? mentionRole : mentionRole + " " + message;
        payload.addProperty("content", content);
        if (mentionRoleId != null && !mentionRoleId.isEmpty()) {
            JsonObject allowedMentions = new JsonObject();
            allowedMentions.add("parse", new JsonArray());
            JsonArray roles = new JsonArray();
            roles.add(mentionRoleId);
            allowedMentions.add("roles", roles);
            payload.add("allowed_mentions", allowedMentions);
        }
    }
    
    private boolean sendWebhook(JsonObject payload) {
        final int maxRetries = 3;
        long backoffMs = 1000L;
        int attempt = 0;

        HttpRequest.Builder baseRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("User-Agent", "CaptureZones-Webhook/1.0");

        while (attempt <= maxRetries) {
            attempt++;
            try {
                HttpRequest request = baseRequestBuilder
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();

                if (status == 204 || status == 200) {
                    if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                        logger.info("Discord webhook sent successfully.");
                    }
                    return true;
                }

                if (status == 429) {
                    String retryAfter = response.headers().firstValue("Retry-After").orElse("");
                    long waitMs = backoffMs;
                    if (!retryAfter.isEmpty()) {
                        try {
                            long seconds = Long.parseLong(retryAfter.trim());
                            waitMs = seconds * 1000L;
                        } catch (NumberFormatException ignored) {
                            // ignore
                        }
                    }

                    if (attempt > maxRetries) {
                        logger.warning("Discord webhook returned 429 too many times; giving up.");
                        if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                            logger.warning("Discord 429 response body: " + response.body());
                        }
                        return false;
                    }

                    if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                        logger.info("Discord webhook rate-limited (429). Waiting " + waitMs + "ms before retry (attempt " + attempt + ").");
                    }
                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    backoffMs = Math.min(backoffMs * 2, 30_000L);
                    continue;
                }

                if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                    logger.warning("Discord webhook returned code " + status + ", body: " + response.body());
                } else {
                    logger.warning("Discord webhook returned code " + status);
                }
                return false;

            } catch (Exception e) {
                logger.warning("Failed to send Discord webhook: " + e.getMessage());
                if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                    e.printStackTrace();
                }

                if (attempt > maxRetries) return false;
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                backoffMs = Math.min(backoffMs * 2, 30_000L);
            }
        }
        return false;
    }

    
    
    public boolean testWebhook() {
        if (!enabled || webhookUrl.isEmpty()) {
            return false;
        }
        
        try {
            if (useEmbeds) {
                JsonObject embed = buildEmbed(Messages.get("discord.test.title"),
                        Messages.get("discord.test.description"),
                        Color.GREEN,
                        createField(Messages.get("discord.field.status"), Messages.get("discord.value.connected"), true),
                        createField(Messages.get("discord.field.plugin-version"), plugin.getDescription().getVersion(), true)
                );

                JsonObject payload = new JsonObject();
                JsonArray embeds = new JsonArray();
                embeds.add(embed);
                payload.add("embeds", embeds);
                applyRoleMention(payload, null);

                return sendWebhook(payload);
            } else {
                JsonObject payload = new JsonObject();
                applyRoleMention(payload, Messages.get("discord.test.plain"));
                return sendWebhook(payload);
            }
        } catch (Exception e) {
            logger.severe("Webhook test failed: " + e.getMessage());
            return false;
        }
    }
}

