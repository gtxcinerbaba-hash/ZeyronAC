/*
 * Copyright (C) 2026 ZeyronAC Team
 * ZeyronAC is a GPLv3 licensed fork of a Minecraft anti-cheat system.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zeyronac.util;

import com.zeyronac.scheduler.SchedulerManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/** Reports internal plugin failures to the console and online operators. */
public final class PluginErrorNotifier {
    private static final long NOTIFICATION_COOLDOWN_MS = 30_000L;
    private static final AtomicLong lastNotificationAt = new AtomicLong(0L);

    private PluginErrorNotifier() {
    }

    public static void report(JavaPlugin plugin, String context, Throwable error) {
        plugin.getLogger().log(Level.SEVERE, "[ZeyronAC] " + context, error);
        long now = System.currentTimeMillis();
        long previous = lastNotificationAt.get();
        if (now - previous < NOTIFICATION_COOLDOWN_MS
                || !lastNotificationAt.compareAndSet(previous, now)) {
            return;
        }

        Runnable notifyOperators = () -> {
            String message = ColorUtil.colorize(
                    "&c[ZeyronAC] An internal error occurred. Please check the console.");
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp()) {
                    player.sendMessage(message);
                }
            }
        };

        try {
            if (SchedulerManager.isInitialized()) {
                SchedulerManager.getAdapter().runSync(notifyOperators);
            } else {
                notifyOperators.run();
            }
        } catch (Throwable ignored) {
            // Error reporting must never create a second plugin failure.
        }
    }
}
