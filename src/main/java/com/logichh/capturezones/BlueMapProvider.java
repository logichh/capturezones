package com.logichh.capturezones;

import com.flowpowered.math.vector.Vector2d;
import com.flowpowered.math.vector.Vector2i;
import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.markers.MarkerSet;
import de.bluecolored.bluemap.api.markers.POIMarker;
import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import de.bluecolored.bluemap.api.math.Shape;
import org.bukkit.Location;
import org.bukkit.World;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

public class BlueMapProvider implements MapProvider {
    
    private final CaptureZones plugin;
    private final Logger logger;
    private BlueMapAPI blueMapAPI;
    private final Map<String, ShapeMarker> markers;
    private final Map<String, POIMarker> centerMarkers;
    private boolean available;
    private String markerSetId;
    private String markerSetLabel;
    private boolean centerMarkerEnabled;
    private String centerMarkerIcon;
    private int centerMarkerIconSize;
    private String centerIconAddress;
    private final Set<String> warnedInvalidCenterIcons;
    
    public BlueMapProvider(CaptureZones plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.markers = new HashMap<>();
        this.centerMarkers = new HashMap<>();
        this.warnedInvalidCenterIcons = new HashSet<>();
        this.available = false;
    }
    
    @Override

    public boolean initialize() {
        try {
            // BlueMap API uses a callback pattern for initialization
            BlueMapAPI.onEnable(this::onBlueMapEnable);
            BlueMapAPI.onDisable(this::onBlueMapDisable);
            
            // Try to get API instance if already loaded
            BlueMapAPI.getInstance().ifPresent(this::onBlueMapEnable);
            
            return true;
        } catch (Exception e) {
            logger.warning("Failed to initialize BlueMap: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
            return false;
        }
    }
    
    private void onBlueMapEnable(BlueMapAPI api) {
        try {
            this.blueMapAPI = api;
            loadCenterMarkerConfig();
            this.centerIconAddress = resolveCenterIconAddress();
            this.available = true;
            
            logger.info("BlueMap integration enabled!");
            
            plugin.getServer().getScheduler().runTaskLater(plugin, this::updateAllMarkers, 20L);
            
        } catch (Exception e) {
            logger.severe("Error enabling BlueMap integration: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
        }
    }
    
    private void onBlueMapDisable(BlueMapAPI api) {
        this.available = false;
        this.markers.clear();
        this.centerMarkers.clear();
        this.centerIconAddress = null;
        this.warnedInvalidCenterIcons.clear();
        logger.info("BlueMap integration disabled.");
    }
    
    @Override

    public boolean isAvailable() {
        return available && blueMapAPI != null;
    }
    
    @Override

    public String getName() {
        return "BlueMap";
    }
    
    @Override

    public void createOrUpdateMarker(CapturePoint point) {
        if (!isAvailable() || point == null) {
            return;
        }
        if (!point.isShowOnMap() || !plugin.shouldDisplayPoint(point)) {
            removeMarker(point.getId());
            return;
        }
        
        try {
            Location location = point.getLocation();
            World world = location.getWorld();
            
            if (world == null) {
                return;
            }

            float markerY = getMarkerHeight(point);
            
            blueMapAPI.getWorld(world).ifPresent(blueMapWorld -> {
                for (BlueMapMap map : blueMapWorld.getMaps()) {
                    try {
                        MarkerSet markerSet = map.getMarkerSets().computeIfAbsent(
                            markerSetId,
                            id -> MarkerSet.builder()
                                .label(markerSetLabel)
                                .toggleable(true)
                                .defaultHidden(false)
                                .build()
                        );
                        
                        Shape shape = createShape(point);
                        
                        // Determine color based on control status
                        Color color = getMarkerColor(point);
                        double lineOpacity = clamp(getZoneDouble(point, "bluemap.line-opacity", 0.8), 0.0, 1.0);
                        double fillOpacity = clamp(getZoneDouble(point, "bluemap.fill-opacity", 0.3), 0.0, 1.0);
                        int lineWidth = Math.max(1, getZoneInt(point, "bluemap.line-width", 2));
                        boolean showDetails = getZoneBoolean(point, "bluemap.show-details", true);
                        Color lineColor = withAlpha(color, (float) lineOpacity);
                        Color fillColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), (float) fillOpacity);
                        
                        String infoHtml = createInfoWindow(point);
                        String detailHtml = showDetails ? infoHtml : "";
                        
                        String markerId = point.getId();
                        ShapeMarker marker = ShapeMarker.builder()
                            .label(point.getName())
                            .position(new Vector3d(location.getX(), markerY, location.getZ()))
                            .shape(shape, markerY)
                            .depthTestEnabled(false)
                            .lineColor(lineColor)
                            .fillColor(fillColor)
                            .lineWidth(lineWidth)
                            .detail(detailHtml)
                            .build();
                        
                        markerSet.put(markerId, marker);
                        markers.put(point.getId(), marker);
                        createOrUpdateCenterMarker(markerSet, point, markerY, infoHtml);
                        
                        if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                            logger.info("Updated BlueMap marker for " + point.getId());
                        }
                        
                    } catch (Exception e) {
                        logger.warning("Failed to create marker on map " + map.getId() + ": " + e.getMessage());
                    }
                }
            });
            
        } catch (Exception e) {
            logger.warning("Failed to update BlueMap marker for " + point.getId() + ": " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
        }
    }
    
    @Override

    public void removeMarker(String pointId) {
        if (!isAvailable()) {
            return;
        }
        
        try {
            for (de.bluecolored.bluemap.api.BlueMapWorld blueMapWorld : blueMapAPI.getWorlds()) {
                for (BlueMapMap map : blueMapWorld.getMaps()) {
                    MarkerSet markerSet = map.getMarkerSets().get(markerSetId);
                    if (markerSet != null) {
                        markerSet.remove(pointId);
                        markerSet.remove(getCenterMarkerId(pointId));
                    }
                }
            }
            
            markers.remove(pointId);
            centerMarkers.remove(pointId);
            
        } catch (Exception e) {
            logger.warning("Failed to remove BlueMap marker " + pointId + ": " + e.getMessage());
        }
    }
    
    @Override

    public void updateAllMarkers() {
        if (!isAvailable()) {
            return;
        }
        
        try {
            for (de.bluecolored.bluemap.api.BlueMapWorld blueMapWorld : blueMapAPI.getWorlds()) {
                for (BlueMapMap map : blueMapWorld.getMaps()) {
                    MarkerSet markerSet = map.getMarkerSets().get(markerSetId);
                    if (markerSet != null) {
                        java.util.Set<String> markerKeys = new java.util.HashSet<>(markerSet.getMarkers().keySet());
                        for (String key : markerKeys) {
                            markerSet.remove(key);
                        }
                    }
                }
            }
            
            markers.clear();
            centerMarkers.clear();
            
            for (CapturePoint point : plugin.getCapturePoints().values()) {
                if (point != null && point.isShowOnMap() && plugin.shouldDisplayPoint(point)) {
                    createOrUpdateMarker(point);
                }
            }
            
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                logger.info("Updated all BlueMap markers");
            }
            
        } catch (Exception e) {
            logger.severe("Failed to update BlueMap markers: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
        }
    }
    
    @Override

    public void cleanup() {
        if (isAvailable()) {
            try {
                for (de.bluecolored.bluemap.api.BlueMapWorld blueMapWorld : blueMapAPI.getWorlds()) {
                    for (BlueMapMap map : blueMapWorld.getMaps()) {
                        MarkerSet markerSet = map.getMarkerSets().get(markerSetId);
                        if (markerSet != null) {
                            map.getMarkerSets().remove(markerSetId);
                        }
                    }
                }
            } catch (Exception e) {
                logger.warning("Error cleaning up BlueMap markers: " + e.getMessage());
            }
        }
        
        markers.clear();
        centerMarkers.clear();
        centerIconAddress = null;
        warnedInvalidCenterIcons.clear();
        this.available = false;
    }
    
    @Override

    public void reload() {
        loadCenterMarkerConfig();
        updateAllMarkers();
    }

    private void loadCenterMarkerConfig() {
        this.markerSetId = plugin.getConfig().getString("bluemap.marker-set", "capturezones");
        this.markerSetLabel = plugin.getConfig().getString("bluemap.marker-set-label", "Capture Zones");
        this.centerMarkerEnabled = plugin.getConfig().getBoolean("bluemap.center-marker.enabled", true);
        this.centerMarkerIcon = plugin.getConfig().getString("bluemap.center-marker.icon", "skull");
        this.centerMarkerIconSize = clampIconSize(plugin.getConfig().getInt("bluemap.center-marker.icon-size", 16));
        this.centerIconAddress = null;
    }
    
    private Shape createShape(CapturePoint point) {
        if (point.isCuboid()) {
            return createCuboidShape(point);
        }
        return createCircleShape(point.getLocation(), point.getRadius());
    }

    private Shape createCircleShape(Location center, int radius) {
        int points = Math.max(32, radius / 4); // More points for larger circles
        Vector2d[] shapePoints = new Vector2d[points];
        
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            shapePoints[i] = new Vector2d(x, z);
        }
        
        return new Shape(shapePoints);
    }

    private Shape createCuboidShape(CapturePoint point) {
        Vector2d[] shapePoints = new Vector2d[]{
            new Vector2d(point.getCuboidMinX(), point.getCuboidMinZ()),
            new Vector2d(point.getCuboidMaxX() + 1, point.getCuboidMinZ()),
            new Vector2d(point.getCuboidMaxX() + 1, point.getCuboidMaxZ() + 1),
            new Vector2d(point.getCuboidMinX(), point.getCuboidMaxZ() + 1)
        };
        return new Shape(shapePoints);
    }
    
    private Color getMarkerColor(CapturePoint point) {
        if (plugin.isPointActive(point.getId())) {
            String colorHex = getZoneColorHex(point, "capturing", "#FFA500");
            return parseHexColor(colorHex, new Color(255, 165, 0));
        }

        String controllingTown = point.getControllingTown();
        if (controllingTown != null && !controllingTown.isEmpty()) {
            java.awt.Color townColor = getOwnerMapColor(point.getControllingOwner(), controllingTown);
            if (townColor != null) {
                return new Color(townColor.getRed(), townColor.getGreen(), townColor.getBlue());
            }

            String storedHex = point.getColor();
            Color storedColor = parseHexColor(storedHex, null);
            if (storedColor != null) {
                return storedColor;
            }

            String fallbackHex = getZoneColorHex(point, "controlled", "#8B0000");
            return parseHexColor(fallbackHex, new Color(139, 0, 0));
        }

        String colorHex = getZoneColorHex(point, "unclaimed", "#808080");
        return parseHexColor(colorHex, new Color(128, 128, 128));
    }
    
    private String createInfoWindow(CapturePoint point) {
        KothManager kothManager = plugin.getKothManager();
        if (kothManager != null && kothManager.isZoneActive(point.getId())) {
            return createKothInfoWindow(point, kothManager);
        }

        StringBuilder html = new StringBuilder();
        html.append("<div style='font-family: sans-serif;'>");
        
        html.append("<h3 style='margin: 0 0 10px 0; color: ");
        String controllingTown = point.getControllingTown();
        if (controllingTown == null || controllingTown.isEmpty()) {
            html.append("#888888;'>").append(Messages.get("dynmap.infowindow.status.unclaimed"));
        } else {
            html.append(getControlledColorHex(point)).append(";'>")
                .append(Messages.get("dynmap.infowindow.status.controlled"))
                .append(" ").append(controllingTown);
        }
        html.append("</h3>");
        
        html.append("<h4 style='margin: 5px 0;'>").append(point.getName()).append("</h4>");
        
        html.append("<div style='font-size: 13px;'>");
        html.append("<p><strong>").append(Messages.get("dynmap.infowindow.label.type"))
            .append(":</strong> ").append(point.getType()).append("</p>");
        
        html.append("<p><strong>").append(Messages.get("dynmap.infowindow.label.reward"))
            .append(":</strong> $").append(String.format("%.2f", plugin.getBaseReward(point))).append("</p>");
        
        if (point.isCuboid()) {
            html.append("<p><strong>Shape:</strong> Cuboid</p>");
            html.append("<p><strong>Size:</strong> ")
                .append(point.getCuboidWidthBlocks()).append("x")
                .append(point.getCuboidHeightBlocks()).append("x")
                .append(point.getCuboidDepthBlocks()).append(" blocks</p>");
        } else {
            html.append("<p><strong>Shape:</strong> Circle</p>");
            html.append("<p><strong>Radius:</strong> ")
                .append(point.getChunkRadius()).append(" chunks (")
                .append(point.getRadius()).append(" blocks)</p>");
        }
        
        html.append("</div>");
        html.append("</div>");
        
        return html.toString();
    }

    private String createKothInfoWindow(CapturePoint point, KothManager kothManager) {
        KothManager.ZoneStateSnapshot state = kothManager.getZoneState(point.getId());
        String holder = state.holderName == null || state.holderName.trim().isEmpty()
            ? Messages.get("dynmap.infowindow.koth.holder.none")
            : state.holderName;
        int progress = state.progressPercent();
        int remainingSeconds = state.remainingSeconds();
        double holdRadius = Math.max(0.5d, plugin.getConfig().getDouble("koth.gameplay.hold-radius-blocks", 5.0d));

        StringBuilder html = new StringBuilder();
        html.append("<div style='font-family: sans-serif;'>");
        html.append("<h3 style='margin: 0 0 10px 0; color: #FF5555;'>")
            .append(Messages.get("dynmap.infowindow.koth.status.active"))
            .append("</h3>");
        html.append("<h4 style='margin: 5px 0;'>").append(point.getName()).append("</h4>");
        html.append("<div style='font-size: 13px;'>");
        html.append("<p><strong>").append(Messages.get("dynmap.infowindow.koth.label.holder"))
            .append(":</strong> ").append(holder).append("</p>");
        html.append("<p><strong>").append(Messages.get("dynmap.infowindow.koth.label.progress"))
            .append(":</strong> ").append(progress).append("%</p>");
        html.append("<p><strong>").append(Messages.get("dynmap.infowindow.koth.label.time_left"))
            .append(":</strong> ").append(formatSeconds(remainingSeconds)).append("</p>");
        html.append("<p><strong>").append(Messages.get("dynmap.infowindow.koth.label.hold_radius"))
            .append(":</strong> ").append(formatRadius(holdRadius)).append("</p>");
        html.append("<p><strong>").append(Messages.get("dynmap.infowindow.label.reward"))
            .append(":</strong> $").append(String.format(Locale.ROOT, "%.2f", plugin.getBaseReward(point))).append("</p>");
        html.append("</div>");
        html.append("</div>");
        return html.toString();
    }

    private void createOrUpdateCenterMarker(MarkerSet markerSet, CapturePoint point, float markerY, String infoHtml) {
        if (!centerMarkerEnabled) {
            return;
        }

        String iconAddress = resolveCenterIconAddress();
        String markerId = getCenterMarkerId(point.getId());
        Vector3d position = new Vector3d(point.getLocation().getX(), markerY, point.getLocation().getZ());
        String label = point.getName();

        POIMarker marker = null;
        try {
            marker = (POIMarker) markerSet.get(markerId);
        } catch (ClassCastException ignored) {
            markerSet.remove(markerId);
        }

        if (marker != null && (iconAddress == null || iconAddress.isEmpty())) {
            markerSet.remove(markerId);
            marker = null;
        }

        if (marker == null) {
            POIMarker.Builder builder = POIMarker.builder()
                .label(label)
                .position(position)
                .detail(infoHtml);

            if (iconAddress != null && !iconAddress.isEmpty()) {
                builder.icon(iconAddress, centerMarkerIconSize, centerMarkerIconSize)
                    .anchor(new Vector2i(centerMarkerIconSize / 2, centerMarkerIconSize / 2));
            } else {
                builder.defaultIcon();
            }

            marker = builder.build();
            markerSet.put(markerId, marker);
        } else {
            marker.setLabel(label);
            marker.setPosition(position);
            marker.setDetail(infoHtml);
            if (iconAddress != null && !iconAddress.isEmpty()) {
                marker.setIcon(iconAddress, centerMarkerIconSize, centerMarkerIconSize);
                marker.setAnchor(new Vector2i(centerMarkerIconSize / 2, centerMarkerIconSize / 2));
            }
        }

        centerMarkers.put(point.getId(), marker);
    }

    private String getCenterMarkerId(String pointId) {
        return "center_" + pointId;
    }

    private int clampIconSize(int size) {
        int clamped = Math.max(8, size);
        return Math.min(clamped, 64);
    }

    private String resolveCenterIconAddress() {
        if (centerIconAddress != null) {
            return centerIconAddress;
        }
        if (!centerMarkerEnabled) {
            return null;
        }
        String configured = centerMarkerIcon == null ? "" : centerMarkerIcon.trim();
        if (configured.isEmpty()) {
            configured = "skull";
        }
        if ("default".equalsIgnoreCase(configured)) {
            centerIconAddress = null;
            return null;
        }
        if (isSkullIcon(configured)) {
            centerIconAddress = registerSkullIcon();
            if (centerIconAddress == null) {
                warnInvalidCenterIconOnce(configured, "skull icon registration failed; using default marker icon");
            }
            return centerIconAddress;
        }

        if (looksLikeImageAddress(configured)) {
            centerIconAddress = configured;
            return centerIconAddress;
        }

        warnInvalidCenterIconOnce(configured, "unrecognized icon value; falling back to generated skull icon");
        centerIconAddress = registerSkullIcon();
        return centerIconAddress;
    }

    private boolean isSkullIcon(String value) {
        return "skull".equalsIgnoreCase(value.trim());
    }

    private boolean looksLikeImageAddress(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("http://")
            || normalized.startsWith("https://")
            || normalized.startsWith("data:")
            || normalized.startsWith("file:")
            || normalized.startsWith("/")) {
            return true;
        }
        if (normalized.contains("/") || normalized.contains("\\")) {
            return true;
        }
        return normalized.endsWith(".png")
            || normalized.endsWith(".jpg")
            || normalized.endsWith(".jpeg")
            || normalized.endsWith(".gif")
            || normalized.endsWith(".webp")
            || normalized.endsWith(".svg");
    }

    private void warnInvalidCenterIconOnce(String iconValue, String reason) {
        String normalized = iconValue == null ? "<null>" : iconValue.trim().toLowerCase(Locale.ROOT);
        if (!warnedInvalidCenterIcons.add(normalized + "|" + reason)) {
            return;
        }
        logger.warning("BlueMap center marker icon '" + iconValue + "' is invalid (" + reason + ").");
    }

    private String registerSkullIcon() {
        if (blueMapAPI == null || blueMapAPI.getWebApp() == null) {
            return null;
        }
        try {
            String imageName = "capturezones_skull_" + centerMarkerIconSize;
            return blueMapAPI.getWebApp().createImage(createSkullImage(centerMarkerIconSize), imageName);
        } catch (Exception e) {
            logger.warning("Failed to register BlueMap skull icon: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private BufferedImage createSkullImage(int size) {
        int baseSize = 16;
        BufferedImage base = new BufferedImage(baseSize, baseSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = base.createGraphics();

        g.setColor(new java.awt.Color(230, 230, 230, 255));
        g.fillRect(2, 2, 12, 10);
        g.fillRect(4, 12, 8, 2);

        g.setColor(new java.awt.Color(120, 120, 120, 255));
        g.drawRect(2, 2, 12, 10);
        g.drawRect(4, 12, 8, 2);

        g.setColor(new java.awt.Color(60, 60, 60, 255));
        g.fillRect(5, 5, 2, 3);
        g.fillRect(9, 5, 2, 3);
        g.fillRect(7, 8, 2, 2);
        g.fillRect(6, 12, 1, 2);
        g.fillRect(9, 12, 1, 2);

        g.dispose();

        if (size == baseSize) {
            return base;
        }

        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.drawImage(base, 0, 0, size, size, null);
        g2.dispose();
        return scaled;
    }

    private float getMarkerHeight(CapturePoint point) {
        Location location = point.getLocation();
        World world = location.getWorld();
        if (world == null) {
            return (float) location.getY();
        }
        int centerX = location.getBlockX();
        int centerZ = location.getBlockZ();
        int minX;
        int maxX;
        int minZ;
        int maxZ;
        int radiusSq = 0;
        boolean cuboid = point.isCuboid();

        if (cuboid) {
            minX = point.getCuboidMinX();
            maxX = point.getCuboidMaxX();
            minZ = point.getCuboidMinZ();
            maxZ = point.getCuboidMaxZ();
        } else {
            int radius = point.getRadius();
            minX = centerX - radius;
            maxX = centerX + radius;
            minZ = centerZ - radius;
            maxZ = centerZ + radius;
            radiusSq = radius * radius;
        }

        int maxSurfaceY = world.getHighestBlockYAt(location);
        int maxAllowedY = world.getMaxHeight() - 1;
        boolean reachedMax = maxSurfaceY >= maxAllowedY;

        for (int x = minX; x <= maxX && !reachedMax; x++) {
            int dx = x - centerX;
            int dxSq = dx * dx;
            for (int z = minZ; z <= maxZ; z++) {
                int dz = z - centerZ;
                if (!cuboid && dxSq + (dz * dz) > radiusSq) {
                    continue;
                }
                int surfaceY = world.getHighestBlockYAt(x, z);
                if (surfaceY > maxSurfaceY) {
                    maxSurfaceY = surfaceY;
                    if (maxSurfaceY >= maxAllowedY) {
                        reachedMax = true;
                        break;
                    }
                }
            }
        }

        int minY = Math.max(maxSurfaceY + 1, location.getBlockY());
        return (float) Math.min(minY, maxAllowedY);
    }

    private String formatSeconds(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int minutes = safeSeconds / 60;
        int seconds = safeSeconds % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private String formatRadius(double radius) {
        if (Math.abs(radius - Math.rint(radius)) < 0.0001d) {
            return (int) Math.rint(radius) + " blocks (square)";
        }
        return String.format(Locale.ROOT, "%.1f blocks (square)", radius);
    }

    private java.awt.Color getOwnerMapColor(CaptureOwner owner, String ownerNameFallback) {
        String ownerName = owner != null ? owner.getDisplayName() : ownerNameFallback;
        if (ownerName == null || ownerName.trim().isEmpty()) {
            return null;
        }
        CaptureOwnerType ownerType = owner != null ? owner.getType() : plugin.getDefaultOwnerType();
        OwnerPlatformAdapter adapter = plugin.getOwnerPlatform();
        if (adapter == null) {
            return null;
        }
        String colorHex = owner != null
            ? adapter.resolveMapColorHex(owner, null)
            : adapter.resolveMapColorHex(ownerName, ownerType, null);
        if (colorHex == null || colorHex.trim().isEmpty()) {
            return null;
        }
        try {
            String normalized = colorHex.trim();
            if (!normalized.startsWith("#")) {
                normalized = "#" + normalized;
            }
            return java.awt.Color.decode(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String getControlledColorHex(CapturePoint point) {
        String controllingTown = point.getControllingTown();
        if (controllingTown == null || controllingTown.isEmpty()) {
            return "#888888";
        }

        java.awt.Color townColor = getOwnerMapColor(point.getControllingOwner(), controllingTown);
        if (townColor != null) {
            return String.format("#%02x%02x%02x", townColor.getRed(), townColor.getGreen(), townColor.getBlue());
        }

        String storedHex = point.getColor();
        if (storedHex != null && storedHex.trim().startsWith("#")) {
            return storedHex.trim();
        }

        return getZoneColorHex(point, "controlled", "#8B0000");
    }

    private String getZoneColorHex(CapturePoint point, String key, String defaultColor) {
        ZoneConfigManager zoneConfigManager = plugin.getZoneConfigManager();
        if (zoneConfigManager != null) {
            return zoneConfigManager.getString(point.getId(), "bluemap.colors." + key, defaultColor);
        }
        return defaultColor;
    }

    private boolean getZoneBoolean(CapturePoint point, String path, boolean fallback) {
        ZoneConfigManager zoneConfigManager = plugin.getZoneConfigManager();
        if (zoneConfigManager != null && point != null) {
            return zoneConfigManager.getBoolean(point.getId(), path, fallback);
        }
        return fallback;
    }

    private int getZoneInt(CapturePoint point, String path, int fallback) {
        ZoneConfigManager zoneConfigManager = plugin.getZoneConfigManager();
        if (zoneConfigManager != null && point != null) {
            return zoneConfigManager.getInt(point.getId(), path, fallback);
        }
        return fallback;
    }

    private double getZoneDouble(CapturePoint point, String path, double fallback) {
        ZoneConfigManager zoneConfigManager = plugin.getZoneConfigManager();
        if (zoneConfigManager != null && point != null) {
            return zoneConfigManager.getDouble(point.getId(), path, fallback);
        }
        return fallback;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private Color withAlpha(Color base, float alpha) {
        if (base == null) {
            return null;
        }
        float clampedAlpha = (float) clamp(alpha, 0.0, 1.0);
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), clampedAlpha);
    }

    private Color parseHexColor(String colorHex, Color fallback) {
        if (colorHex == null) {
            return fallback;
        }
        String normalized = colorHex.trim();
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() != 6) {
            return fallback;
        }
        try {
            int rgb = Integer.parseInt(normalized, 16);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            return new Color(r, g, b);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

