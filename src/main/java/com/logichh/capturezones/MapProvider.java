package com.logichh.capturezones;

import org.bukkit.Location;

public interface MapProvider {
    
    /**
     * Initialize the map provider and create necessary marker sets.
     * 
     * @return true if initialization was successful, false otherwise
     */
    boolean initialize();
    
    /**
     * Check if this provider is available and ready to use.
     * 
     * @return true if the provider can create markers, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Get the name of this map provider.
     * 
     * @return The provider name (e.g., "Dynmap", "BlueMap")
     */
    String getName();
    
    /**
     * Create or update a marker for a capture zone.
     * 
     * @param point The capture zone to create a marker for
     */
    void createOrUpdateMarker(CapturePoint point);
    
    /**
     * Remove a marker for a capture zone.
     * 
     * @param pointId The ID of the capture zone
     */
    void removeMarker(String pointId);
    
    void updateAllMarkers();
    
    void cleanup();
    
    void reload();
}

