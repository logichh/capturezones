package com.logichh.capturezones;

import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.layer.CustomLayer;
import net.pl3x.map.core.markers.layer.Layer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Polygon;
import net.pl3x.map.core.markers.marker.Polyline;
import net.pl3x.map.core.markers.option.Fill;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Popup;
import net.pl3x.map.core.markers.option.Stroke;
import net.pl3x.map.core.markers.option.Tooltip;
import net.pl3x.map.core.world.World;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

public final class Pl3xMapProvider implements MapProvider {
    private final CaptureZones plugin;
    private final Logger logger;
    private Pl3xMap api;
    private boolean available;
    private String layerId;
    private String layerLabel;
    private final Set<String> markerIds = new HashSet<>();

    public Pl3xMapProvider(CaptureZones plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public boolean initialize() {
        try {
            if (plugin.getServer().getPluginManager().getPlugin("Pl3xMap") == null
                || !plugin.getServer().getPluginManager().getPlugin("Pl3xMap").isEnabled()) {
                logger.info("Pl3xMap not found or not enabled.");
                return false;
            }
            this.api = Pl3xMap.api();
            if (this.api == null || !this.api.isEnabled()) {
                logger.info("Pl3xMap API is not ready.");
                return false;
            }
            reloadConfig();
            this.available = true;
            updateAllMarkers();
            logger.info("Pl3xMap integration initialized successfully!");
            return true;
        } catch (Exception ex) {
            logger.warning("Failed to initialize Pl3xMap: " + ex.getMessage());
            this.available = false;
            return false;
        }
    }

    @Override
    public boolean isAvailable() {
        return available && api != null && api.isEnabled();
    }

    @Override
    public String getName() {
        return "Pl3xMap";
    }

    @Override
    public void createOrUpdateMarker(CapturePoint point) {
        if (!isAvailable() || point == null) {
            return;
        }
        if (!point.isShowOnMap() || !plugin.shouldDisplayPoint(point) || !getZoneBoolean(point, "pl3xmap.enabled", true)) {
            removeMarker(point.getId());
            return;
        }
        try {
            World world = resolveWorld(point);
            if (world == null) {
                return;
            }
            CustomLayer layer = getOrCreateLayer(world);
            String markerId = markerId(point.getId());
            layer.removeMarker(markerId);
            Marker<?> marker = createMarker(point, markerId);
            layer.addMarker(marker);
            markerIds.add(markerId);

            String centerMarkerId = centerMarkerId(point.getId());
            layer.removeMarker(centerMarkerId);
            if (plugin.getConfig().getBoolean("pl3xmap.center-marker.enabled", true)) {
                Marker<?> center = createCenterMarker(point, centerMarkerId);
                layer.addMarker(center);
                markerIds.add(centerMarkerId);
            }
        } catch (Exception ex) {
            logger.warning("Failed to update Pl3xMap marker for " + point.getId() + ": " + ex.getMessage());
        }
    }

    @Override
    public void removeMarker(String pointId) {
        if (!isAvailable() || pointId == null) {
            return;
        }
        String markerId = markerId(pointId);
        String centerId = centerMarkerId(pointId);
        for (World world : api.getWorldRegistry().values()) {
            Layer rawLayer = world.getLayerRegistry().get(layerId);
            if (rawLayer instanceof CustomLayer) {
                ((CustomLayer) rawLayer).removeMarker(markerId);
                ((CustomLayer) rawLayer).removeMarker(centerId);
            }
        }
        markerIds.remove(markerId);
        markerIds.remove(centerId);
    }

    @Override
    public void updateAllMarkers() {
        if (!isAvailable()) {
            return;
        }
        cleanupMarkersOnly();
        for (CapturePoint point : plugin.getCapturePoints().values()) {
            if (point != null && point.isShowOnMap() && plugin.shouldDisplayPoint(point)) {
                createOrUpdateMarker(point);
            }
        }
    }

    @Override
    public void cleanup() {
        if (isAvailable()) {
            cleanupMarkersOnly();
        }
        markerIds.clear();
        available = false;
    }

    @Override
    public void reload() {
        reloadConfig();
        updateAllMarkers();
    }

    private void reloadConfig() {
        this.layerId = plugin.getConfig().getString("pl3xmap.layer-id", "capturezones");
        this.layerLabel = plugin.getConfig().getString("pl3xmap.layer-label", "Capture Zones");
    }

    private CustomLayer getOrCreateLayer(World world) {
        Layer raw = world.getLayerRegistry().get(layerId);
        if (raw instanceof CustomLayer) {
            return (CustomLayer) raw;
        }
        CustomLayer layer = new CustomLayer(layerId, world, () -> layerLabel);
        layer.setShowControls(true);
        layer.setDefaultHidden(false);
        layer.setPriority(plugin.getConfig().getInt("pl3xmap.priority", 10));
        world.getLayerRegistry().register(layerId, layer);
        return layer;
    }

    private Marker<?> createMarker(CapturePoint point, String markerId) {
        Marker<?> marker;
        if (point.isCuboid()) {
            Polyline line = Marker.polyline(markerId + "_line", List.of(
                Point.of(point.getCuboidMinX(), point.getCuboidMinZ()),
                Point.of(point.getCuboidMaxX() + 1, point.getCuboidMinZ()),
                Point.of(point.getCuboidMaxX() + 1, point.getCuboidMaxZ() + 1),
                Point.of(point.getCuboidMinX(), point.getCuboidMaxZ() + 1),
                Point.of(point.getCuboidMinX(), point.getCuboidMinZ())
            ));
            marker = Marker.polygon(markerId, line);
        } else {
            Location location = point.getLocation();
            marker = Marker.circle(markerId, Point.of(location.getX(), location.getZ()), point.getRadius());
        }
        return marker.setOptions(createOptions(point, true));
    }

    private Marker<?> createCenterMarker(CapturePoint point, String markerId) {
        Location location = point.getLocation();
        String icon = plugin.getConfig().getString("pl3xmap.center-marker.icon", "blueflag");
        return Marker.icon(markerId, Point.of(location.getX(), location.getZ()), icon)
            .setOptions(createOptions(point, false));
    }

    private Options createOptions(CapturePoint point, boolean area) {
        int color = colorWithAlpha(resolveMarkerColor(point), area ? fillAlpha(point) : 255);
        Options options = new Options();
        options.setTooltip(new Tooltip(point.getName()));
        options.setPopup(new Popup(createPopup(point)));
        if (area) {
            options.setStroke(new Stroke(true)
                .setColor(colorWithAlpha(resolveMarkerColor(point), lineAlpha(point)))
                .setWeight(Math.max(1, getZoneInt(point, "pl3xmap.line-width", 2))));
            options.setFill(new Fill(true).setColor(color));
        }
        return options;
    }

    private String createPopup(CapturePoint point) {
        String owner = point.getControllingTown();
        if (owner == null || owner.isEmpty()) {
            owner = "Unclaimed";
        }
        String conquest = plugin.getConquestStatusLine(point.getId());
        StringBuilder html = new StringBuilder();
        html.append("<div class=\"capturezones-popup\"><strong>").append(escape(point.getName())).append("</strong>");
        html.append("<div>Owner: ").append(escape(owner)).append("</div>");
        html.append("<div>Reward: $").append(String.format(Locale.ROOT, "%.2f", plugin.getBaseReward(point))).append("</div>");
        if (conquest != null && !conquest.isEmpty()) {
            html.append("<div>Conquest: ").append(escape(conquest)).append("</div>");
        }
        html.append("</div>");
        return html.toString();
    }

    private World resolveWorld(CapturePoint point) {
        if (point == null || point.getLocation() == null || point.getLocation().getWorld() == null || api == null) {
            return null;
        }
        String bukkitName = point.getLocation().getWorld().getName();
        for (World world : api.getWorldRegistry().values()) {
            if (world != null && world.getName() != null && world.getName().equalsIgnoreCase(bukkitName)) {
                return world;
            }
        }
        return api.getWorldRegistry().get(bukkitName);
    }

    private int resolveMarkerColor(CapturePoint point) {
        String hex;
        if (plugin.isPointActive(point.getId())) {
            hex = getZoneString(point, "pl3xmap.colors.capturing", "#FFA500");
        } else if (point.getControllingTown() != null && !point.getControllingTown().isEmpty()) {
            hex = point.getColor() == null || point.getColor().isEmpty()
                ? getZoneString(point, "pl3xmap.colors.controlled", "#8B0000")
                : point.getColor();
        } else {
            hex = getZoneString(point, "pl3xmap.colors.unclaimed", "#808080");
        }
        return parseRgb(hex, 0x808080);
    }

    private void cleanupMarkersOnly() {
        for (World world : api.getWorldRegistry().values()) {
            Layer rawLayer = world.getLayerRegistry().get(layerId);
            if (rawLayer instanceof CustomLayer) {
                ((CustomLayer) rawLayer).clearMarkers();
            }
        }
        markerIds.clear();
    }

    private int lineAlpha(CapturePoint point) {
        return (int) Math.round(clamp(getZoneDouble(point, "pl3xmap.line-opacity", 0.8), 0.0, 1.0) * 255.0);
    }

    private int fillAlpha(CapturePoint point) {
        return (int) Math.round(clamp(getZoneDouble(point, "pl3xmap.fill-opacity", 0.3), 0.0, 1.0) * 255.0);
    }

    private int colorWithAlpha(int rgb, int alpha) {
        return ((Math.max(0, Math.min(255, alpha)) & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    private int parseRgb(String hex, int fallback) {
        if (hex == null || hex.trim().isEmpty()) {
            return fallback;
        }
        String normalized = hex.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        try {
            return Integer.parseInt(normalized, 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String markerId(String pointId) {
        return "capturezones_" + pointId;
    }

    private String centerMarkerId(String pointId) {
        return "capturezones_center_" + pointId;
    }

    private boolean getZoneBoolean(CapturePoint point, String path, boolean fallback) {
        return plugin.getZoneConfigManager() != null ? plugin.getZoneConfigManager().getBoolean(point.getId(), path, fallback) : fallback;
    }

    private int getZoneInt(CapturePoint point, String path, int fallback) {
        return plugin.getZoneConfigManager() != null ? plugin.getZoneConfigManager().getInt(point.getId(), path, fallback) : fallback;
    }

    private double getZoneDouble(CapturePoint point, String path, double fallback) {
        return plugin.getZoneConfigManager() != null ? plugin.getZoneConfigManager().getDouble(point.getId(), path, fallback) : fallback;
    }

    private String getZoneString(CapturePoint point, String path, String fallback) {
        return plugin.getZoneConfigManager() != null ? plugin.getZoneConfigManager().getString(point.getId(), path, fallback) : fallback;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
