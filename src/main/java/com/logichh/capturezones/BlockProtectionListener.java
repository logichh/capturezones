package com.logichh.capturezones;

import com.logichh.capturezones.CapturePoint;
import com.logichh.capturezones.CaptureZones;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockProtectionListener
implements Listener {
    @EventHandler

    public void onBlockBreak(BlockBreakEvent event) {
        if (event == null || event.getPlayer() == null || event.getBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        CaptureZones plugin = (CaptureZones)CaptureZones.getPlugin(CaptureZones.class);
        if (player.isOp()) {
            return;
        }
        for (CapturePoint point : plugin.getCapturePoints().values()) {
            double chunkDistance;
            World blockWorld = event.getBlock().getWorld();
            if (blockWorld == null || !blockWorld.getUID().equals(point.getWorldUUID()) || !((chunkDistance = plugin.getChunkDistance(point.getLocation(), event.getBlock().getLocation())) <= (double)(point.getChunkRadius() + 1))) continue;
            event.setCancelled(true);
            if (chunkDistance <= (double)point.getChunkRadius()) {
                plugin.sendNotification(player, Messages.get("protection.block-break-blocked"));
            } else {
                plugin.sendNotification(player, Messages.get("protection.block-break-buffer-blocked"));
            }
            return;
        }
    }

    @EventHandler

    public void onBlockPlace(BlockPlaceEvent event) {
        if (event == null || event.getPlayer() == null || event.getBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        CaptureZones plugin = (CaptureZones)CaptureZones.getPlugin(CaptureZones.class);
        if (player.isOp()) {
            return;
        }
        for (CapturePoint point : plugin.getCapturePoints().values()) {
            double chunkDistance;
            World blockWorld = event.getBlock().getWorld();
            if (blockWorld == null || !blockWorld.getUID().equals(point.getWorldUUID()) || !((chunkDistance = plugin.getChunkDistance(point.getLocation(), event.getBlock().getLocation())) <= (double)(point.getChunkRadius() + 1))) continue;
            event.setCancelled(true);
            if (chunkDistance <= (double)point.getChunkRadius()) {
                plugin.sendNotification(player, Messages.get("protection.block-place-blocked"));
            } else {
                plugin.sendNotification(player, Messages.get("protection.block-place-buffer-blocked"));
            }
            return;
        }
    }
}

