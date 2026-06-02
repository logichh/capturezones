package com.logichh.capturezones;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class StatisticsGUIListener implements Listener {
    
    private final CaptureZones plugin;
    private final StatisticsGUI gui;
    
    public StatisticsGUIListener(CaptureZones plugin, StatisticsGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
    }
    
    @EventHandler

    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!gui.isStatsView(player, event.getView())) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }
        gui.handleClick(player, slot, event.getClickedInventory());
    }
    
    @EventHandler

    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        if (!gui.isStatsView(player, event.getView())) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!gui.isStatsView(player, player.getOpenInventory())) {
                gui.closeMenu(player);
            }
        }, 1L);
    }

    @EventHandler

    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!gui.isStatsView(player, event.getView())) {
            return;
        }
        event.setCancelled(true);
    }
}

