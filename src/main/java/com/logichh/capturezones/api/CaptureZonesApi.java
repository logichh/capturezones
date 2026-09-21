package com.logichh.capturezones.api;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface CaptureZonesApi {
    String API_VERSION = "1.1.0";

    String getApiVersion();

    String getPluginVersion();

    Set<String> getCapabilities();

    Map<String, Object> getOverviewSnapshot();

    Map<String, Object> getFullSnapshot();

    Map<String, Object> getZonesSnapshot();

    Map<String, Object> getActiveCapturesSnapshot();

    Map<String, Object> getKothSnapshot();

    Map<String, Object> getConquestsSnapshot();

    Map<String, Object> getShopsSnapshot();

    Map<String, Object> getStatisticsSnapshot();

    Map<String, Object> getGlobalConfigSnapshot();

    Map<String, Object> getZoneConfigSnapshot(String zoneId);

    Map<String, Object> getDataFilesSnapshot(int maxDepth);

    CaptureZonesActionResult reloadAll();

    CaptureZonesActionResult reloadLang();

    CaptureZonesActionResult repairSchema(boolean exactTemplates);

    CaptureZonesActionResult createZone(
        String id,
        String name,
        String worldName,
        double x,
        double y,
        double z,
        int chunkRadius,
        double reward
    );

    CaptureZonesActionResult createCuboidZone(
        String id,
        String name,
        String worldName,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        double reward
    );

    CaptureZonesActionResult deleteZone(String pointId);

    CaptureZonesActionResult forceCapture(String pointId, String ownerName);

    CaptureZonesActionResult stopCapture(String pointId, String reason);

    CaptureZonesActionResult resetPoint(String pointId);

    CaptureZonesActionResult resetAllPoints();

    CaptureZonesActionResult setPointType(String pointId, String type);

    CaptureZonesActionResult setPointActive(String pointId, boolean active);

    CaptureZonesActionResult setPointPlayerLimits(String pointId, int minPlayers, int maxPlayers);

    CaptureZonesActionResult toggleChatMessages(boolean enabled);

    CaptureZonesActionResult setZoneConfigValue(String zoneId, String path, Object value);

    CaptureZonesActionResult resetZoneConfigPath(String zoneId, String path);

    CaptureZonesActionResult resetZoneConfig(String zoneId);

    CaptureZonesActionResult reloadZoneConfig(String zoneId);

    CaptureZonesActionResult setGlobalConfigValue(String path, Object value, boolean reloadAfterSave);

    CaptureZonesActionResult saveGlobalConfig();

    CaptureZonesActionResult runAdminCommand(String commandLine);

    CaptureZonesActionResult shopRestock(String zoneId);

    CaptureZonesActionResult shopSetEnabled(String zoneId, boolean enabled);

    CaptureZonesActionResult shopReloadAll();

    CaptureZonesActionResult kothStart(List<String> zoneIds, boolean announce);

    CaptureZonesActionResult kothStop(String zoneId, String reason, boolean announce);

    CaptureZonesActionResult kothStopAll(String reason, boolean announce);

    CaptureZonesActionResult kothAssignZone(String zoneId, boolean assign);

    CaptureZonesActionResult startConquest(String profile);

    CaptureZonesActionResult stopConquest(String profile, String reason, boolean announce);

    CaptureZonesActionResult stopAllConquests(String reason, boolean announce);

    CaptureZonesActionResult removePlayerStats(UUID playerId);

    CaptureZonesActionResult resetAllStats();
}
