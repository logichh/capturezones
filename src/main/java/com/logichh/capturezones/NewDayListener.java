package com.logichh.capturezones;

import com.logichh.capturezones.CaptureZones;
import com.palmergames.bukkit.towny.event.NewDayEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class NewDayListener
implements Listener {
    private final CaptureZones plugin;

    public NewDayListener(CaptureZones plugin) {
        this.plugin = plugin;
    }

    @EventHandler

    public void onNewDay(NewDayEvent event) {
        this.plugin.handleNewDay();
    }
}

