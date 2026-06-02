package com.logichh.capturezones;

import com.logichh.capturezones.CapturePoint;
import com.logichh.capturezones.CaptureSession;
import com.logichh.capturezones.CaptureOwner;
import com.logichh.capturezones.CaptureZones;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

public class CaptureDeathListener
implements Listener {
    private static final String META_REINFORCEMENT = "capture_reinforcement";
    private static final String MSG_KILLER_REINFORCEMENTS = "messages.capture.killer.reinforcements";
    private static final String MSG_KILLER_ENVIRONMENT = "messages.capture.killer.environment";
    private static final String MSG_KILLER_UNKNOWN = "messages.capture.killer.unknown";
    private final CaptureZones plugin;

    public CaptureDeathListener(CaptureZones plugin) {
        this.plugin = plugin;
    }

    @EventHandler

    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String killerName = resolveKillerName(player);
        List<String> sessionsToCancel = new ArrayList<>();
        Set<String> announcedDeathZones = new HashSet<>();
        boolean keepInventory = false;
        ZoneConfigManager zoneConfigManager = plugin.getZoneConfigManager();
        for (Map.Entry<String, CaptureSession> entry : new ArrayList<>(plugin.getActiveSessions().entrySet())) {
            String pointId = entry.getKey();
            CaptureSession session = entry.getValue();
            if (pointId == null || session == null) {
                continue;
            }

            CapturePoint point = session.getPoint();
            if (point == null || !plugin.isWithinZone(point, player.getLocation())) {
                continue;
            }

            if (zoneConfigManager != null
                && zoneConfigManager.getBoolean(pointId, "capture.capture.keep-inventory-on-death", false)) {
                keepInventory = true;
            }

            if (plugin.getDiscordWebhook() != null && announcedDeathZones.add(pointId)) {
                String townName = session.getTownName() != null && !session.getTownName().isEmpty()
                    ? session.getTownName()
                    : "Unknown";
                plugin.getDiscordWebhook().sendPlayerDeath(
                    pointId,
                    point.getName(),
                    player.getName(),
                    killerName,
                    townName
                );
            }

            CaptureOwner owner = session.getOwner();
            boolean matchesOwner = owner != null
                ? plugin.doesPlayerMatchOwner(player, owner)
                : plugin.doesPlayerMatchOwner(player, session.getTownName());
            if (matchesOwner) {
                sessionsToCancel.add(pointId);
            }
        }

        if (keepInventory) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }

        for (String pointId : sessionsToCancel) {
            plugin.cancelCaptureByDeath(pointId, player.getName(), killerName);
        }
    }

    private String resolveKillerName(Player victim) {
        if (victim == null) {
            return getLocalizedOrDefault(MSG_KILLER_UNKNOWN, "Unknown");
        }

        Player directKiller = victim.getKiller();
        if (directKiller != null) {
            return directKiller.getName();
        }

        Entity damager = resolveDamagerEntity(victim.getLastDamageCause());
        if (damager == null) {
            return getLocalizedOrDefault(MSG_KILLER_ENVIRONMENT, "Environment");
        }

        if (damager.hasMetadata(META_REINFORCEMENT)) {
            return getLocalizedOrDefault(MSG_KILLER_REINFORCEMENTS, "Reinforcements");
        }

        if (damager instanceof Player) {
            return ((Player) damager).getName();
        }

        return formatEntityTypeName(damager);
    }

    private Entity resolveDamagerEntity(EntityDamageEvent lastDamageCause) {
        if (!(lastDamageCause instanceof EntityDamageByEntityEvent)) {
            return null;
        }

        Entity damager = ((EntityDamageByEntityEvent) lastDamageCause).getDamager();
        if (!(damager instanceof Projectile)) {
            return damager;
        }

        ProjectileSource shooter = ((Projectile) damager).getShooter();
        if (shooter instanceof Entity) {
            return (Entity) shooter;
        }
        return damager;
    }

    private String formatEntityTypeName(Entity entity) {
        if (entity == null || entity.getType() == null) {
            return getLocalizedOrDefault(MSG_KILLER_UNKNOWN, "Unknown");
        }

        String raw = entity.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] words = raw.split(" ");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                out.append(word.substring(1));
            }
        }
        if (out.length() == 0) {
            return getLocalizedOrDefault(MSG_KILLER_UNKNOWN, "Unknown");
        }
        return out.toString();
    }

    private String getLocalizedOrDefault(String key, String fallback) {
        String value = Messages.get(key);
        if (value == null || value.trim().isEmpty() || key.equals(value)) {
            return fallback;
        }
        return value;
    }
}

