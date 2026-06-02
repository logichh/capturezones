package com.logichh.capturezones;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

public class VanillaMobSpawner implements MobSpawner {
    
    private final CaptureZones plugin;
    private final Logger logger;
    private final Random random;

    private final Set<String> invalidMobTypes = new HashSet<>();

    private final Set<String> invalidHatItems = new HashSet<>();
    private boolean warnedNoMobTypes;
    
    public VanillaMobSpawner(CaptureZones plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.random = new Random();
    }
    
    @Override

    public LivingEntity spawnMob(String pointId, Location location, Player target) {
        List<EntityType> mobTypes = resolveMobTypes(pointId);
        if (mobTypes.isEmpty()) {
            logger.warning("No mob types available for spawning!");
            return null;
        }
        
        try {
            EntityType mobType = mobTypes.get(random.nextInt(mobTypes.size()));
            Entity entity = location.getWorld().spawnEntity(location, mobType);
            
            if (!(entity instanceof LivingEntity)) {
                entity.remove();
                return null;
            }
            
            LivingEntity livingEntity = (LivingEntity) entity;
            equipMob(livingEntity, resolveHatItems(pointId));
            applyEffects(livingEntity);

            livingEntity.setMetadata("reinforcement_source", new FixedMetadataValue(plugin, "VANILLA"));
            if (target != null && livingEntity instanceof Mob) {
                ((Mob) livingEntity).setTarget(target);
            }
            
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                logger.info("Spawned vanilla mob: " + mobType.name() + " at " + location);
            }
            
            return livingEntity;
            
        } catch (Exception e) {
            logger.severe("Failed to spawn vanilla mob: " + e.getMessage());
            if (plugin.getConfig().getBoolean("settings.debug-mode", false)) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private void equipMob(LivingEntity entity, List<Material> hatItems) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;
        if (shouldWearHelmet(entity)) {
            if (!hatItems.isEmpty()) {
                Material hatMaterial = hatItems.get(random.nextInt(hatItems.size()));
                ItemStack helmet = new ItemStack(hatMaterial);
                equipment.setHelmet(helmet);
                equipment.setHelmetDropChance(0.0f);
            }
        }
        if (entity instanceof Zombie || entity instanceof ZombieVillager || entity instanceof Husk) {
            if (random.nextDouble() < 0.3) { // 30% chance for weapon
                ItemStack weapon = getRandomWeapon();
                equipment.setItemInMainHand(weapon);
                equipment.setItemInMainHandDropChance(0.0f);
            }
        }
        if (entity instanceof Skeleton || entity instanceof Stray) {
            ItemStack bow = new ItemStack(Material.BOW);
            equipment.setItemInMainHand(bow);
            equipment.setItemInMainHandDropChance(0.0f);
        }
    }

    private boolean shouldWearHelmet(LivingEntity entity) {
        return entity instanceof Zombie ||
               entity instanceof ZombieVillager ||
               entity instanceof Skeleton ||
               entity instanceof Stray ||
               entity instanceof Husk ||
               entity instanceof WitherSkeleton;
    }

    private ItemStack getRandomWeapon() {
        Material[] weapons = {
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.IRON_AXE
        };
        return new ItemStack(weapons[random.nextInt(weapons.length)]);
    }

    private void applyEffects(LivingEntity entity) {
        if (random.nextDouble() < 0.1) { // 10% chance
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                Integer.MAX_VALUE,
                0,
                false,
                false
            ));
        }
        
        if (random.nextDouble() < 0.05) { // 5% chance
            entity.addPotionEffect(new PotionEffect(
                PotionEffectType.STRENGTH,
                Integer.MAX_VALUE,
                0,
                false,
                false
            ));
        }
    }
    
    @Override

    public boolean isAvailable() {
        return !resolveMobTypes(null).isEmpty();
    }
    
    @Override

    public String getName() {
        return "Vanilla Minecraft";
    }
    
    @Override

    public List<String> getConfiguredMobs() {
        List<EntityType> mobTypes = resolveMobTypes(null);
        List<String> result = new ArrayList<>();
        for (EntityType type : mobTypes) {
            result.add(type.name());
        }
        return result;
    }

    public void reload() {
        invalidMobTypes.clear();
        invalidHatItems.clear();
        warnedNoMobTypes = false;
    }

    private List<EntityType> resolveMobTypes(String pointId) {
        List<String> mobTypeStrings = getConfiguredStrings(pointId, "reinforcements.mob-types");
        List<EntityType> mobTypes = new ArrayList<>();

        for (String mobString : mobTypeStrings) {
            if (mobString == null || mobString.trim().isEmpty()) {
                continue;
            }
            try {
                EntityType type = EntityType.valueOf(mobString.toUpperCase());
                if (type.isAlive() && type != EntityType.ENDER_DRAGON) {
                    mobTypes.add(type);
                }
            } catch (IllegalArgumentException e) {
                if (invalidMobTypes.add(mobString)) {
                    logger.warning("Invalid mob type in config: " + mobString);
                }
            }
        }

        if (mobTypes.isEmpty()) {
            mobTypes.add(EntityType.ZOMBIE);
            mobTypes.add(EntityType.SKELETON);
            if (!warnedNoMobTypes) {
                warnedNoMobTypes = true;
                logger.warning("No valid mob types configured, using defaults: ZOMBIE, SKELETON");
            }
        }

        return mobTypes;
    }

    private List<Material> resolveHatItems(String pointId) {
        List<String> hatStrings = getConfiguredStrings(pointId, "reinforcements.hat-items");
        List<Material> hatItems = new ArrayList<>();

        for (String hatString : hatStrings) {
            if (hatString == null || hatString.trim().isEmpty()) {
                continue;
            }
            try {
                Material material = Material.valueOf(hatString.toUpperCase());
                if (material.isItem()) {
                    hatItems.add(material);
                }
            } catch (IllegalArgumentException e) {
                if (invalidHatItems.add(hatString)) {
                    logger.warning("Invalid hat item in config: " + hatString);
                }
            }
        }

        return hatItems;
    }

    private List<String> getConfiguredStrings(String pointId, String path) {
        List<?> values = Collections.emptyList();
        if (pointId != null && !pointId.isEmpty() && plugin.getZoneConfigManager() != null) {
            values = plugin.getZoneConfigManager().getList(pointId, path, Collections.emptyList());
        } else {
            ZoneConfigManager zoneManager = plugin.getZoneConfigManager();
            if (zoneManager != null) {
                values = zoneManager.getDefaultList(path, Collections.emptyList());
            } else {
                List<String> defaults = plugin.getConfig().getStringList(path);
                return defaults == null ? Collections.emptyList() : defaults;
            }
        }

        if (values == null) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (Object value : values) {
            if (value != null) {
                result.add(value.toString());
            }
        }
        return result;
    }
}

