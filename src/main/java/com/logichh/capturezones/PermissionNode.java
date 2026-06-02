package com.logichh.capturezones;

import org.bukkit.command.CommandSender;

public final class PermissionNode {

    private PermissionNode() {
    }

    public static boolean has(CommandSender sender, String suffix) {
        if (sender == null || suffix == null || suffix.trim().isEmpty()) {
            return false;
        }
        String normalized = suffix.trim();
        if (normalized.startsWith("capturezones.")) {
            normalized = normalized.substring("capturezones.".length());
        } else if (normalized.startsWith("capturepoints.")) {
            normalized = normalized.substring("capturepoints.".length());
        } else if (normalized.startsWith("townycapture.")) {
            normalized = normalized.substring("townycapture.".length());
        }

        return sender.hasPermission("capturezones." + normalized)
            || sender.hasPermission("capturepoints." + normalized)
            || sender.hasPermission("townycapture." + normalized);
    }
}
