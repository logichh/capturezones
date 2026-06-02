package com.logichh.capturezones;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

public interface MobSpawner {
    
    /**
     * Spawn a reinforcement mob at the specified location.
     * 
     * @param pointId The capture zone id for per-zone settings
     * @param location The location to spawn the mob
     * @param target The player to target (optional, may be null)
     * @return The spawned entity, or null if spawn failed
     */
    LivingEntity spawnMob(String pointId, Location location, Player target);
    
    /**
     * Check if this spawner is available and ready to use.
     * 
     * @return true if the spawner can spawn mobs, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Get the name of this spawner implementation.
     * 
     * @return The spawner name (e.g., "Vanilla", "MythicMobs")
     */
    String getName();
    
    /**
     * Get debug information about configured mobs.
     * 
     * @return List of mob type names/identifiers
     */
    List<String> getConfiguredMobs();
}

