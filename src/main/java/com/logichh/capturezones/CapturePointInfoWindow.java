package com.logichh.capturezones;

import com.logichh.capturezones.CapturePoint;
import com.logichh.capturezones.CaptureSession;
import com.logichh.capturezones.KothManager;
import com.logichh.capturezones.CaptureZones;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class CapturePointInfoWindow {

    public static String formatInfoWindow(CapturePoint point) {
        CaptureZones plugin = (CaptureZones)CaptureZones.getPlugin(CaptureZones.class);
        ZoneConfigManager zoneManager = plugin.getZoneConfigManager();

        KothManager kothManager = plugin.getKothManager();
        if (kothManager != null && kothManager.isZoneActive(point.getId())) {
            return formatKothInfoWindow(point, plugin, kothManager);
        }

        String controllingTown = point.getControllingTown();
        CaptureOwner controllingOwner = point.getControllingOwner();
        boolean isActive = plugin.getActiveSessions().containsKey(point.getId());
        CaptureSession session = isActive ? plugin.getActiveSessions().get(point.getId()) : null;
        CaptureSession captureSession = session;
        String statusText;
        String statusColor;
        if (isActive) {
            controllingTown = session.getTownName();
            CaptureOwner activeOwner = session.getOwner() != null ? session.getOwner() : controllingOwner;
            statusText = zoneManager != null
                ? zoneManager.getString(point.getId(), "infowindow.text.capturing", com.logichh.capturezones.Messages.get("dynmap.infowindow.status.capturing"))
                : com.logichh.capturezones.Messages.get("dynmap.infowindow.status.capturing");
            statusColor = zoneManager != null
                ? zoneManager.getString(point.getId(), "infowindow.colors.capturing", "#FFAA00")
                : "#FFAA00";
            String townColor = CapturePointInfoWindow.getTownColor(activeOwner, controllingTown);
            if (townColor != null) {
                statusColor = townColor;
            }
        } else if (controllingTown == null || controllingTown.isEmpty()) {
            statusText = zoneManager != null
                ? zoneManager.getString(point.getId(), "infowindow.text.unclaimed", com.logichh.capturezones.Messages.get("dynmap.infowindow.status.unclaimed"))
                : com.logichh.capturezones.Messages.get("dynmap.infowindow.status.unclaimed");
            statusColor = zoneManager != null
                ? zoneManager.getString(point.getId(), "infowindow.colors.unclaimed", "#FF0000")
                : "#FF0000";
        } else {
            statusText = zoneManager != null
                ? zoneManager.getString(point.getId(), "infowindow.text.controlled", com.logichh.capturezones.Messages.get("dynmap.infowindow.status.controlled"))
                : com.logichh.capturezones.Messages.get("dynmap.infowindow.status.controlled");
            statusColor = zoneManager != null
                ? zoneManager.getString(point.getId(), "infowindow.colors.controlled", "#00FF00")
                : "#00FF00";
            String townColor = CapturePointInfoWindow.getTownColor(controllingOwner, controllingTown);
            if (townColor != null) {
                statusColor = townColor;
            }
        }

        String template = zoneManager != null
            ? zoneManager.getString(point.getId(), "infowindow.template", com.logichh.capturezones.Messages.get("dynmap.infowindow.template"))
            : com.logichh.capturezones.Messages.get("dynmap.infowindow.template");
        if (template == null || template.isEmpty()) {
            template = "<div class=\"regioninfo\"><div style=\"font-size:120%; font-weight:bold; color:%control_color%;\">%control_status% %controlling_town%</div><div style=\"font-size:115%; margin-top:5px;\">%name%</div><div>\u2022 %label_type% - %type%</div><div>\u2022 %label_active%: %active_status%</div><div>\u2022 %label_prep_time% - %prep_time% minutes</div><div>\u2022 %label_capture_time% - %capture_time% minutes</div><div>\u2022 %label_reward% - %reward%</div></div>";
        }

        Map<String, String> replacements = new HashMap<>();
        replacements.put("%control_status%", statusText);
        replacements.put("%controlling_town%", controllingTown == null ? "" : controllingTown);
        replacements.put("%name%", point.getName());
        replacements.put("%type%", plugin.getPointTypeName(point.getType()));
        replacements.put("%active_status%", isActive ? com.logichh.capturezones.Messages.get("dynmap.infowindow.active.yes") : com.logichh.capturezones.Messages.get("dynmap.infowindow.active.no"));
        int prepTime = zoneManager != null
            ? zoneManager.getInt(point.getId(), "capture.preparation.duration", 1)
            : 1;
        int captureTime = zoneManager != null
            ? zoneManager.getInt(point.getId(), "capture.capture.duration", 15)
            : 15;
        replacements.put("%prep_time%", String.valueOf(prepTime));
        replacements.put("%capture_time%", String.valueOf(captureTime));
        replacements.put("%reward%", String.valueOf(plugin.getBaseReward(point)));
        replacements.put("%item_payout%", plugin.getItemRewardDisplay(point.getId()));
        replacements.put("%control_color%", statusColor);
        replacements.put("%label_type%", com.logichh.capturezones.Messages.get("dynmap.infowindow.label.type"));
        replacements.put("%label_active%", com.logichh.capturezones.Messages.get("dynmap.infowindow.label.active"));
        replacements.put("%label_prep_time%", com.logichh.capturezones.Messages.get("dynmap.infowindow.label.prep_time"));
        replacements.put("%label_capture_time%", com.logichh.capturezones.Messages.get("dynmap.infowindow.label.capture_time"));
        replacements.put("%label_reward%", com.logichh.capturezones.Messages.get("dynmap.infowindow.label.reward"));
        replacements.put("%label_item_reward%", com.logichh.capturezones.Messages.get("dynmap.infowindow.label.item_reward"));

        String html = applyPlaceholders(template, replacements);
        String conquestStatus = plugin.getConquestStatusLine(point.getId());
        if (conquestStatus != null && !conquestStatus.isEmpty()) {
            String conquestLine = "<div>&bull; "
                + com.logichh.capturezones.Messages.get("dynmap.infowindow.label.conquest")
                + " - " + conquestStatus + "</div>";
            int insertAt = html.lastIndexOf("</div>");
            html = insertAt >= 0
                ? html.substring(0, insertAt) + conquestLine + html.substring(insertAt)
                : html + conquestLine;
        }
        return html;
    }

    private static String formatKothInfoWindow(CapturePoint point, CaptureZones plugin, KothManager kothManager) {
        KothManager.ZoneStateSnapshot state = kothManager.getZoneState(point.getId());
        String holder = state.holderName == null || state.holderName.trim().isEmpty()
            ? Messages.get("dynmap.infowindow.koth.holder.none")
            : state.holderName;

        int progress = state.progressPercent();
        int remainingSeconds = state.remainingSeconds();
        double holdRadius = Math.max(0.5d, plugin.getConfig().getDouble("koth.gameplay.hold-radius-blocks", 5.0d));

        StringBuilder html = new StringBuilder();
        html.append("<div class=\"regioninfo\">")
            .append("<div style=\"font-size:120%; font-weight:bold; color:#FF5555;\">")
            .append(Messages.get("dynmap.infowindow.koth.status.active"))
            .append("</div>")
            .append("<div style=\"font-size:115%; margin-top:5px;\">")
            .append(point.getName())
            .append("</div>")
            .append("<div>&bull; ")
            .append(Messages.get("dynmap.infowindow.koth.label.holder"))
            .append(" - ")
            .append(holder)
            .append("</div>")
            .append("<div>&bull; ")
            .append(Messages.get("dynmap.infowindow.koth.label.progress"))
            .append(" - ")
            .append(progress)
            .append("%</div>")
            .append("<div>&bull; ")
            .append(Messages.get("dynmap.infowindow.koth.label.time_left"))
            .append(" - ")
            .append(formatSeconds(remainingSeconds))
            .append("</div>")
            .append("<div>&bull; ")
            .append(Messages.get("dynmap.infowindow.koth.label.hold_radius"))
            .append(" - ")
            .append(formatRadius(holdRadius))
            .append("</div>")
            .append("<div>&bull; ")
            .append(Messages.get("dynmap.infowindow.label.reward"))
            .append(" - ")
            .append(String.format(Locale.ROOT, "%.2f", plugin.getBaseReward(point)))
            .append("</div>")
            .append("</div>");
        return html.toString();
    }

    private static String getTownColor(CaptureOwner owner, String ownerNameFallback) {
        String ownerName = owner != null ? owner.getDisplayName() : ownerNameFallback;
        if (ownerName == null || ownerName.isEmpty()) {
            return null;
        }
        CaptureZones plugin = CaptureZones.getPlugin(CaptureZones.class);
        CaptureOwnerType ownerType = owner != null ? owner.getType() : plugin.getDefaultOwnerType();
        Plugin dynmapTowny = plugin.getServer().getPluginManager().getPlugin("Dynmap-Towny");
        if (dynmapTowny != null && dynmapTowny.isEnabled() && dynmapTowny instanceof JavaPlugin) {
            try {
                FileConfiguration dynmapTownyConfig = ((JavaPlugin)dynmapTowny).getConfig();
                String styleRoot = ownerType == CaptureOwnerType.NATION ? "nationstyle." : "custstyle.";
                String stylePath = styleRoot + ownerName.toLowerCase(Locale.ROOT) + ".fillcolor";
                if (dynmapTownyConfig.contains(stylePath)) {
                    return dynmapTownyConfig.getString(stylePath);
                }
                String fallback = dynmapTownyConfig.getString("fillcolor");
                if (fallback != null && !fallback.isEmpty()) {
                    return fallback;
                }
            } catch (Exception ignored) {
                // Fallback to owner adapter below.
            }
        }

        OwnerPlatformAdapter adapter = plugin.getOwnerPlatform();
        if (adapter == null) {
            return null;
        }
        return owner != null
            ? adapter.resolveMapColorHex(owner, null)
            : adapter.resolveMapColorHex(ownerName, ownerType, null);
    }

    private static String applyPlaceholders(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }

    private static String formatSeconds(int totalSeconds) {
        int safe = Math.max(0, totalSeconds);
        int minutes = safe / 60;
        int seconds = safe % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private static String formatRadius(double radius) {
        if (Math.abs(radius - Math.rint(radius)) < 0.0001d) {
            return (int) Math.rint(radius) + " blocks (square)";
        }
        return String.format(Locale.ROOT, "%.1f blocks (square)", radius);
    }
}

