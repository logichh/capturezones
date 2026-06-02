package com.logichh.capturezones;

import com.logichh.capturezones.AreaStyle;
import com.logichh.capturezones.CapturePoint;
import com.logichh.capturezones.CapturePointInfoWindow;
import com.logichh.capturezones.CaptureSession;
import com.logichh.capturezones.CaptureZones;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

public class UpdateZones {
    private CaptureZones plugin;
    private MarkerSet markerSet;
    private MarkerAPI markerAPI;
    private AreaStyle areaStyle;

    private Map<String, AreaMarker> areaMarkers = new HashMap<String, AreaMarker>();

    private Map<String, Marker> centerMarkers = new HashMap<String, Marker>();

    private Set<String> warnedInvalidCenterIcons = new LinkedHashSet<String>();
    private Plugin dynmapTowny;
    private Plugin papiPlugin;

    public UpdateZones(CaptureZones plugin, MarkerSet markerSet, AreaStyle areaStyle) {
        this.plugin = plugin;
        this.markerSet = markerSet;
        this.areaStyle = areaStyle;
        this.dynmapTowny = plugin.getServer().getPluginManager().getPlugin("Dynmap-Towny");
        this.papiPlugin = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
    }

    public void run() {
        for (CapturePoint point : this.plugin.getCapturePoints().values()) {
            this.updateMarker(point);
        }
    }

    public void updateMarker(CapturePoint point) {
        this.updateAreaMarker(point);
        this.updateBufferMarker(point);
        boolean showCenterMarkerGlobally = this.plugin.getConfig().getBoolean("dynmap.center-marker.show", true);
        boolean showCenterMarkerForZone = this.getZoneBoolean(point, "dynmap.show-marker", true);
        if (showCenterMarkerGlobally && showCenterMarkerForZone) {
            this.updateCenterMarker(point);
        } else {
            this.removeCenterMarker(point.getId());
        }
    }

    private void updateAreaMarker(CapturePoint point) {
        int strokeColor;
        int fillColor;
        String markerId = "capture_" + point.getId();
        if (!this.getZoneBoolean(point, "dynmap.show-area", true)) {
            removeAreaMarker(markerId);
            return;
        }
        AreaMarker marker = this.markerSet.findAreaMarker(markerId);
        Location center = point.getLocation();
        double[][] polygon = point.getMapPolygonXZ();
        double[] x = polygon[0];
        double[] z = polygon[1];
        double fillOpacity = clamp(this.getZoneDouble(point, "dynmap.fillOpacity", this.areaStyle.getFillOpacity()), 0.0, 1.0);
        double strokeOpacity = clamp(this.getZoneDouble(point, "dynmap.strokeOpacity", this.areaStyle.getStrokeOpacity()), 0.0, 1.0);
        int strokeWeight = Math.max(1, this.getZoneInt(point, "dynmap.strokeWeight", this.areaStyle.getStrokeWeight()));
        String townName = null;
        if (this.plugin.getActiveSessions().containsKey(point.getId())) {
            CaptureSession session = this.plugin.getActiveSessions().get(point.getId());
            townName = session.getTownName();
            fillColor = this.parseColor(this.getZoneColor(point, "colors.capturing-fill", "#FF0000"), 0xFF0000);
            strokeColor = this.parseColor(this.getZoneColor(point, "colors.capturing-border", "#FF0000"), 0xFF0000);
        } else {
            townName = point.getControllingTown();
            if (townName != null && !townName.isEmpty()) {
                int[] colors = this.getTownColors(point.getControllingOwner(), townName);
                if (colors != null) {
                    fillColor = colors[0];
                    strokeColor = colors[1];
                } else {
                    fillColor = this.parseColor(this.getZoneColor(point, "colors.claimed-fill", "#0000FF"), 255);
                    strokeColor = this.parseColor(this.getZoneColor(point, "colors.claimed-border", "#FF0000"), 0xFF0000);
                }
            } else {
                fillColor = this.parseColor(this.getZoneColor(point, "colors.unclaimed-fill", "#8B0000"), 0x8B0000); // Dark red
                strokeColor = this.parseColor(this.getZoneColor(point, "colors.unclaimed-border", "#404040"), 0x404040); // Dark gray border
            }
        }
        String customFill = this.getZoneString(point, "dynmap.fillColor", null);
        if (customFill != null && !customFill.trim().isEmpty()) {
            fillColor = this.parseColor(customFill.trim(), fillColor);
        }
        String customStroke = this.getZoneString(point, "dynmap.strokeColor", null);
        if (customStroke != null && !customStroke.trim().isEmpty()) {
            strokeColor = this.parseColor(customStroke.trim(), strokeColor);
        }
        String label = this.createMarkerLabel(point);
        boolean showLabel = this.getZoneBoolean(point, "dynmap.show-label", true);
        if (!showLabel) {
            label = "";
        }
        if (marker == null) {
            marker = this.markerSet.createAreaMarker(markerId, label, true, center.getWorld().getName(), x, z, false);
            if (marker == null) {
                this.plugin.getLogger().warning("Failed to create area marker for " + point.getName());
                return;
            }
            if (this.plugin.getConfig().getBoolean("debug", false)) {
                this.plugin.getLogger().info("Created new marker for " + point.getName());
            }
        } else {
            marker.setCornerLocations(x, z);
            marker.setLabel(label);
        }
        marker.setFillStyle(fillOpacity, fillColor);
        marker.setLineStyle(strokeWeight, strokeOpacity, strokeColor);
        String description = CapturePointInfoWindow.formatInfoWindow(point);
        marker.setDescription(description);
        this.areaMarkers.put(markerId, marker);
    }

    private void updateBufferMarker(CapturePoint point) {
        String markerId = "capture_buffer_" + point.getId();
        boolean showArea = this.getZoneBoolean(point, "dynmap.show-area", true);
        boolean showBuffer = this.getZoneBoolean(point, "dynmap.show-buffer", false);
        if (!showArea || !showBuffer) {
            removeAreaMarker(markerId);
            return;
        }
        int bufferChunks = Math.max(0, this.getZoneInt(point, "protection.buffer-zone.size", 1));
        if (bufferChunks <= 0) {
            removeAreaMarker(markerId);
            return;
        }
        double[][] polygon = this.getBufferPolygon(point, bufferChunks);
        if (polygon == null) {
            removeAreaMarker(markerId);
            return;
        }
        AreaMarker marker = this.markerSet.findAreaMarker(markerId);
        String label = this.getZoneBoolean(point, "dynmap.show-label", true) ? point.getName() + " Buffer" : "";
        Location center = point.getLocation();
        if (center == null || center.getWorld() == null) {
            return;
        }
        if (marker == null) {
            marker = this.markerSet.createAreaMarker(markerId, label, true, center.getWorld().getName(), polygon[0], polygon[1], false);
            if (marker == null) {
                return;
            }
        } else {
            marker.setCornerLocations(polygon[0], polygon[1]);
            marker.setLabel(label);
        }
        int color = this.parseColor(this.getZoneString(point, "dynmap.buffer-color", "#FF0000"), 0xFF0000);
        double opacity = clamp(this.getZoneDouble(point, "dynmap.buffer-opacity", 0.2), 0.0, 1.0);
        marker.setFillStyle(opacity, color);
        marker.setLineStyle(1, clamp(opacity, 0.0, 1.0), color);
        marker.setDescription(CapturePointInfoWindow.formatInfoWindow(point));
        this.areaMarkers.put(markerId, marker);
    }

    private double[][] getBufferPolygon(CapturePoint point, int bufferChunks) {
        if (point == null || bufferChunks <= 0) {
            return null;
        }
        int extraBlocks = bufferChunks * 16;
        if (point.isCuboid()) {
            double[] xs = {
                point.getCuboidMinX() - extraBlocks,
                point.getCuboidMaxX() + 1 + extraBlocks,
                point.getCuboidMaxX() + 1 + extraBlocks,
                point.getCuboidMinX() - extraBlocks
            };
            double[] zs = {
                point.getCuboidMinZ() - extraBlocks,
                point.getCuboidMinZ() - extraBlocks,
                point.getCuboidMaxZ() + 1 + extraBlocks,
                point.getCuboidMaxZ() + 1 + extraBlocks
            };
            return new double[][]{xs, zs};
        }

        Location center = point.getLocation();
        if (center == null) {
            return null;
        }
        int radiusBlocks = Math.max(1, point.getChunkRadius() + bufferChunks) * 16;
        int sides = Math.max(32, radiusBlocks / 2);
        double[] xs = new double[sides];
        double[] zs = new double[sides];
        for (int i = 0; i < sides; i++) {
            double angle = (2.0 * Math.PI * i) / sides;
            xs[i] = center.getX() + radiusBlocks * Math.cos(angle);
            zs[i] = center.getZ() + radiusBlocks * Math.sin(angle);
        }
        return new double[][]{xs, zs};
    }

    private void updateCenterMarker(CapturePoint point) {
        String markerId = "center_" + point.getId();
        Marker marker = this.markerSet.findMarker(markerId);
        Location center = point.getLocation();
        boolean showLabel = this.getZoneBoolean(point, "dynmap.show-label", true)
            && this.plugin.getConfig().getBoolean("dynmap.center-marker.show-label", true);
        String labelFormat = this.plugin.getConfig().getString("dynmap.center-marker.label-format", "%name%");
        MarkerAPI markerAPI = this.resolveDynmapMarkerApi();
        if (markerAPI == null) {
            this.plugin.getLogger().warning("Failed to get MarkerAPI from Dynmap for center marker updates.");
            return;
        }
        MarkerIcon icon = this.resolveCenterMarkerIcon(point, markerAPI);
        String label = labelFormat.replace("%name%", point.getName()).replace("%type%", this.plugin.getPointTypeName(point.getType())).replace("%town%", point.getControllingTown());
        if (!showLabel) {
            label = "";
        }
        if (marker == null) {
            if (icon == null) {
                return;
            }
            marker = this.markerSet.createMarker(markerId, label, center.getWorld().getName(), center.getX(), center.getY(), center.getZ(), icon, false);
            if (marker == null) {
                this.plugin.getLogger().warning("Failed to create center marker for " + point.getName());
                return;
            }
        } else {
            marker.setLocation(center.getWorld().getName(), center.getX(), center.getY(), center.getZ());
            marker.setLabel(label);
            if (icon != null) {
                marker.setMarkerIcon(icon);
            }
        }
        marker.setDescription(CapturePointInfoWindow.formatInfoWindow(point));
        this.centerMarkers.put(markerId, marker);
    }

    private MarkerAPI resolveDynmapMarkerApi() {
        if (this.markerAPI != null) {
            return this.markerAPI;
        }

        Plugin dynmapPlugin = this.plugin.getServer().getPluginManager().getPlugin("Dynmap");
        if (dynmapPlugin == null) {
            dynmapPlugin = this.plugin.getServer().getPluginManager().getPlugin("dynmap");
        }
        if (!(dynmapPlugin instanceof DynmapAPI) || !dynmapPlugin.isEnabled()) {
            return null;
        }

        DynmapAPI dynmapAPI = (DynmapAPI) dynmapPlugin;
        this.markerAPI = dynmapAPI.getMarkerAPI();
        return this.markerAPI;
    }

    private MarkerIcon resolveCenterMarkerIcon(CapturePoint point, MarkerAPI markerAPI) {
        LinkedHashSet<String> iconCandidates = new LinkedHashSet<String>();
        ZoneConfigManager zoneManager = this.plugin.getZoneConfigManager();
        if (zoneManager != null && point != null) {
            addIconCandidate(iconCandidates, zoneManager.getString(point.getId(), "dynmap.icon", ""));
        }
        addIconCandidate(iconCandidates, this.plugin.getConfig().getString("dynmap.center-marker.icon", ""));
        addIconCandidate(iconCandidates, this.plugin.getConfig().getString("dynmap.icon", ""));
        addIconCandidate(iconCandidates, "skull");
        addIconCandidate(iconCandidates, "chest");
        addIconCandidate(iconCandidates, "pointer");
        addIconCandidate(iconCandidates, "signpost");
        addIconCandidate(iconCandidates, "man");

        for (String candidate : iconCandidates) {
            MarkerIcon icon = markerAPI.getMarkerIcon(candidate);
            if (icon != null) {
                return icon;
            }
        }

        String warningKey = String.join("|", iconCandidates);
        if (this.warnedInvalidCenterIcons.add(warningKey)) {
            this.plugin.getLogger().warning(
                "No valid Dynmap center icon found for zone '" + (point != null ? point.getId() : "<unknown>")
                    + "'. Tried: " + String.join(", ", iconCandidates)
            );
        }
        return null;
    }

    private void addIconCandidate(Set<String> candidates, String rawValue) {
        if (rawValue == null) {
            return;
        }
        String normalized = rawValue.trim();
        if (normalized.isEmpty()) {
            return;
        }
        candidates.add(normalized);
    }

    private String createMarkerLabel(CapturePoint point) {
        StringBuilder label = new StringBuilder(point.getName());
        if (point.getControllingTown() != null && !point.getControllingTown().isEmpty()) {
            label.append(" (").append(point.getControllingTown()).append(")");
        } else {
            label.append(" (Unclaimed)");
        }
        if (this.plugin.getActiveSessions().containsKey(point.getId())) {
            CaptureSession session = this.plugin.getActiveSessions().get(point.getId());
            label.append(" - Being captured by ").append(session.getTownName());
        }
        return label.toString();
    }

    private int[] getTownColors(CaptureOwner owner, String townName) {
        if (townName == null || townName.trim().isEmpty()) {
            return null;
        }
        CaptureOwnerType ownerType = owner != null ? owner.getType() : this.plugin.getDefaultOwnerType();
        OwnerPlatformAdapter adapter = this.plugin.getOwnerPlatform();
        if (adapter == null) {
            return null;
        }
        String fillHex = adapter.resolveMapColorHex(townName, ownerType, null);
        if (fillHex == null || fillHex.trim().isEmpty()) {
            return null;
        }
        int fillColor = parseColor(fillHex, -1);
        if (fillColor < 0) {
            return null;
        }
        return new int[]{fillColor, fillColor};
    }

    private String getZoneColor(CapturePoint point, String path, String defaultColor) {
        ZoneConfigManager zoneManager = this.plugin.getZoneConfigManager();
        if (zoneManager != null && point != null) {
            return zoneManager.getString(point.getId(), path, defaultColor);
        }
        return this.plugin.getConfig().getString(path, defaultColor);
    }

    private int getColorFromName(String colorName) {
        switch (colorName.toLowerCase()) {
            case "black": {
                return 0;
            }
            case "darkblue": {
                return 170;
            }
            case "darkgreen": {
                return 43520;
            }
            case "darkaqua": {
                return 43690;
            }
            case "darkred": {
                return 0xAA0000;
            }
            case "darkpurple": {
                return 0xAA00AA;
            }
            case "gold": {
                return 0xFFAA00;
            }
            case "gray": {
                return 0xAAAAAA;
            }
            case "darkgray": {
                return 0x555555;
            }
            case "blue": {
                return 0x5555FF;
            }
            case "green": {
                return 0x55FF55;
            }
            case "aqua": {
                return 0x55FFFF;
            }
            case "red": {
                return 0xFF5555;
            }
            case "lightpurple": {
                return 0xFF55FF;
            }
            case "yellow": {
                return 0xFFFF55;
            }
            case "white": {
                return 0xFFFFFF;
            }
            case "purple": {
                return 0xAA00AA;
            }
            case "magenta": {
                return 0xFF55FF;
            }
            case "orange": {
                return 0xFFAA00;
            }
            case "brown": {
                return 0xAA5500;
            }
            case "cyan": {
                return 43690;
            }
            case "lime": {
                return 0x55FF55;
            }
            case "navy": {
                return 128;
            }
            case "pink": {
                return 16738740;
            }
            case "teal": {
                return 32896;
            }
        }
        return -1;
    }

    private int parseColor(String colorStr, int defaultColor) {
        if (colorStr == null || colorStr.isEmpty()) {
            this.plugin.getLogger().warning("Empty color string, using default: " + String.format("#%06X", defaultColor));
            return defaultColor;
        }
        try {
            if (colorStr.startsWith("#")) {
                colorStr = colorStr.substring(1);
            }
            return Integer.parseInt(colorStr, 16);
        }
        catch (NumberFormatException e) {
            this.plugin.getLogger().warning("Failed to parse color: " + colorStr + ", using default: " + String.format("#%06X", defaultColor));
            return defaultColor;
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean getZoneBoolean(CapturePoint point, String path, boolean fallback) {
        ZoneConfigManager manager = this.plugin.getZoneConfigManager();
        if (manager != null && point != null) {
            return manager.getBoolean(point.getId(), path, fallback);
        }
        return fallback;
    }

    private int getZoneInt(CapturePoint point, String path, int fallback) {
        ZoneConfigManager manager = this.plugin.getZoneConfigManager();
        if (manager != null && point != null) {
            return manager.getInt(point.getId(), path, fallback);
        }
        return fallback;
    }

    private double getZoneDouble(CapturePoint point, String path, double fallback) {
        ZoneConfigManager manager = this.plugin.getZoneConfigManager();
        if (manager != null && point != null) {
            return manager.getDouble(point.getId(), path, fallback);
        }
        return fallback;
    }

    private String getZoneString(CapturePoint point, String path, String fallback) {
        ZoneConfigManager manager = this.plugin.getZoneConfigManager();
        if (manager != null && point != null) {
            return manager.getString(point.getId(), path, fallback);
        }
        return fallback;
    }

    private void removeAreaMarker(String markerId) {
        if (markerId == null || markerId.isEmpty()) {
            return;
        }
        AreaMarker marker = this.markerSet.findAreaMarker(markerId);
        if (marker != null) {
            marker.deleteMarker();
        }
        this.areaMarkers.remove(markerId);
    }

    private void removeCenterMarker(String pointId) {
        if (pointId == null || pointId.isEmpty()) {
            return;
        }
        String markerId = "center_" + pointId;
        Marker marker = this.markerSet.findMarker(markerId);
        if (marker != null) {
            marker.deleteMarker();
        }
        this.centerMarkers.remove(markerId);
    }

    public void removeMarkers(String pointId) {
        String centerMarkerId;
        Marker centerMarker;
        String areaMarkerId = "capture_" + pointId;
        AreaMarker areaMarker = this.markerSet.findAreaMarker(areaMarkerId);
        if (areaMarker != null) {
            areaMarker.deleteMarker();
            this.areaMarkers.remove(areaMarkerId);
        }
        AreaMarker bufferMarker = this.markerSet.findAreaMarker("capture_buffer_" + pointId);
        if (bufferMarker != null) {
            bufferMarker.deleteMarker();
            this.areaMarkers.remove("capture_buffer_" + pointId);
        }
        if ((centerMarker = this.markerSet.findMarker(centerMarkerId = "center_" + pointId)) != null) {
            centerMarker.deleteMarker();
            this.centerMarkers.remove(centerMarkerId);
        }
    }

    public AreaMarker getAreaMarker(String id) {
        return this.areaMarkers.get(id);
    }

    public Marker getCenterMarker(String id) {
        return this.centerMarkers.get(id);
    }
}

