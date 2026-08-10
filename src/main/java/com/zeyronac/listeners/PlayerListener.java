/*
 * Copyright (C) 2026 ZeyronAC Team
 * ZeyronAC is a GPLv3 licensed fork of a Minecraft anti-cheat system.
 * This project is community-maintained and not affiliated with any single upstream repository.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * This file is based on GPLv3 licensed work and includes modifications.
 * Derived from:
 *   - Shard (© 2025 KaelusAI, https://github.com/KaelusAI/Shard)
 *   - Grim (© 2025 GrimAnticheat, https://github.com/GrimAnticheat/Grim)
 *   - MLSAC (GPLv3: https://github.com/SoMax1soft/mls-network-plugin)
 *
 * Modifications:
 *   - Modified by SoMax1soft for the ZeyronAC.com project in 2026.
 */

package com.zeyronac.listeners;

import com.zeyronac.hologram.HologramManager;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import com.zeyronac.Main;
import com.zeyronac.Permissions;
import com.zeyronac.alert.AlertManager;
import com.zeyronac.checks.AICheck;
import com.zeyronac.scheduler.SchedulerManager;
import com.zeyronac.session.SessionManager;
import com.zeyronac.violation.ViolationManager;

public class PlayerListener implements Listener {
    private final Main plugin;
    private final AICheck aiCheck;
    private final AlertManager alertManager;
    private final ViolationManager violationManager;
    private final SessionManager sessionManager;
    private final TickListener tickListener;
    private HologramManager hologramManager;
    private final RotationListener rotationListener;
    private HitListener hitListener;

    public PlayerListener(Main plugin, AICheck aiCheck, AlertManager alertManager,
            ViolationManager violationManager, SessionManager sessionManager,
            TickListener tickListener, HologramManager hologramManager,
            RotationListener rotationListener) {
        this.plugin = plugin;
        this.aiCheck = aiCheck;
        this.alertManager = alertManager;
        this.violationManager = violationManager;
        this.sessionManager = sessionManager;
        this.tickListener = tickListener;
        this.hologramManager = hologramManager;
        this.rotationListener = rotationListener;
    }

    public void setHitListener(HitListener hitListener) {
        this.hitListener = hitListener;
    }

    public void setHologramManager(HologramManager hologramManager) {
        this.hologramManager = hologramManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // ── ON-JOIN BAN CHECK ──
        // Backend bans tablosunda aktif ban varsa kick et (sureli/kalici) ve Discord
        // appeal linki goster. checkBan "banned" true donerse kick yapar.
        if (plugin.getAiClientProvider() != null && plugin.getAiClientProvider().getClient() != null) {
            plugin.getAiClientProvider().getClient()
                    .checkBan(player.getUniqueId().toString())
                    .thenAccept(banInfo -> {
                        if (banInfo == null || !banInfo.has("banned") || !banInfo.get("banned").getAsBoolean()) {
                            return; // oyuncu temiz — kick yok
                        }
                        // Ban var. player.isOnline() async thread'ten kontrol edilir.
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            String reason = banInfo.has("reason") && !banInfo.get("reason").isJsonNull()
                                    ? banInfo.get("reason").getAsString()
                                    : "Cheat detected";
                            Integer daysRemaining = null;
                            if (banInfo.has("days_remaining") && !banInfo.get("days_remaining").isJsonNull()) {
                                try { daysRemaining = banInfo.get("days_remaining").getAsInt(); } catch (Exception ignored) {}
                            }
                            String durationText = (daysRemaining != null && daysRemaining > 0)
                                    ? daysRemaining + " day" + (daysRemaining == 1 ? "" : "s") + " remaining"
                                    : null;
                            String discord = banInfo.has("discord_invite") && !banInfo.get("discord_invite").isJsonNull()
                                    ? banInfo.get("discord_invite").getAsString()
                                    : "";
                            kickForBan(player, reason, durationText, discord);
                        });
                    })
                    .exceptionally(err -> {
                        // fail-open: ban check hatasi oyuncuyu etkilemez
                        plugin.getLogger().fine("on-join ban-check failed for " + player.getName() + ": " + err.getMessage());
                        return null;
                    });
        }

        if (plugin.getAiClientProvider() != null) {
            plugin.getAiClientProvider().handlePlayerJoin(player.getUniqueId());
        }
        if (hitListener != null) {
            hitListener.cacheEntity(player);
        }
        if (tickListener != null) {
            tickListener.startPlayerTask(player);
        }

        // Disconnect sonrasi geri sayim icinde bekleyen aktif kayit varsa resume et.
        if (sessionManager != null) {
            sessionManager.resumeSession(player);
        }

        try {
            SchedulerManager.getAdapter().runEntitySyncDelayed(player, () -> {
                if (player.isOnline()) {
                    if (player.hasPermission(Permissions.ALERTS) || player.hasPermission(Permissions.ADMIN)) {
                        alertManager.enableAlerts(player);

                        if (plugin.getUpdateChecker() != null && plugin.getUpdateChecker().isUpdateAvailable()) {
                            player.sendMessage(
                                    ChatColor.GOLD + "=================================================");
                            player.sendMessage(ChatColor.YELLOW + "A NEW ZeyronAC UPDATE IS AVAILABLE: "
                                    + ChatColor.WHITE + plugin.getUpdateChecker().getLatestVersion());
                            player.sendMessage(ChatColor.YELLOW + "The updater downloads it automatically. Restart the server to apply it.");
                            player.sendMessage(
                                    ChatColor.GOLD + "=================================================");
                        }
                    }
                }
            }, 20L);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to schedule player join task: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        handlePlayerLeave(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        handlePlayerLeave(event.getPlayer());
    }

    /**
     * Ban efekti: oyuncuyu kick et ve ban ekrani goster (sure + sebep + appeal).
     */
    private void kickForBan(Player player, String reason, String durationText, String discordInvite) {
        StringBuilder msg = new StringBuilder();
        msg.append("§c§l⛔ YOU ARE BANNED\n");
        msg.append("§r\n");
        msg.append("§7Reason: §f").append(reason).append("\n");
        if (durationText != null && !durationText.isEmpty()) {
            msg.append("§7Duration: §e").append(durationText).append("\n");
        } else {
            msg.append("§7Duration: §cPermanent\n");
        }
        msg.append("§r\n");
        if (discordInvite != null && !discordInvite.isEmpty()) {
            msg.append("§7Appeal at: §b").append(discordInvite).append("\n");
            msg.append("§r\n");
        }
        msg.append("§8ZeyronAC Protection");
        player.kickPlayer(msg.toString());
    }

    private void handlePlayerLeave(Player player) {
        if (hitListener != null) {
            hitListener.uncachePlayer(player);
        }
        if (tickListener != null) {
            tickListener.stopPlayerTask(player);
        }
        if (rotationListener != null) {
            rotationListener.handlePlayerQuit(player);
        }
        if (aiCheck != null) {
            aiCheck.handlePlayerQuit(player);
        }
        if (alertManager != null) {
            alertManager.handlePlayerQuit(player);
        }
        if (violationManager != null) {
            violationManager.handlePlayerQuit(player);
        }
        if (plugin.getAiClientProvider() != null) {
            plugin.getAiClientProvider().handlePlayerQuit(player.getUniqueId());
        }
        if (plugin.getDetectionResponseManager() != null) {
            plugin.getDetectionResponseManager().handlePlayerQuit(player);
        }
        if (sessionManager != null) {
            // Aktif kayit varsa 5 dakikalik geri sayim baslatir;
            // yoksa sadece aimProcessor'u temizler.
            sessionManager.handlePlayerDisconnect(player);
        }
        if (hologramManager != null) {
            hologramManager.handleQuit(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (hologramManager != null) {
            hologramManager.handleDeath(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Entity id changes on respawn - refresh the hit cache so attacks on this player resolve.
        if (hitListener != null) {
            hitListener.cacheEntity(event.getPlayer());
        }
        if (hologramManager != null) {
            hologramManager.handleRespawn(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (hitListener != null) {
            hitListener.cacheEntity(event.getPlayer());
        }
        if (hologramManager != null) {
            hologramManager.handleWorldChange(event.getPlayer());
        }
    }
}
