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


package com.zeyronac.session;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import com.zeyronac.Main;
import com.zeyronac.config.Label;
import com.zeyronac.data.DataSession;
import com.zeyronac.scheduler.ScheduledTask;
import com.zeyronac.scheduler.SchedulerManager;
import com.zeyronac.util.AimProcessor;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
public class SessionManager implements ISessionManager {
    /**
     * Disconnect sonrasi geri sayim suresi (tick). 5 dakika = 6000 tick (50ms * 6000 = 300s).
     * Oyuncu bu sure icinde tekrar baglanirsa session otomatik resume edilir;
     * baglanmazsa session save edilip kapatilir.
     */
    private static final long DISCONNECT_GRACE_TICKS = 6000L;

    private final Map<UUID, DataSession> activeSessions;
    private final Map<UUID, AimProcessor> playerAimProcessors;
    private final Map<UUID, ScheduledTask> disconnectTimers;
    private final Main plugin;
    private volatile String currentSessionFolder = null;
    public SessionManager(Main plugin) {
        this.activeSessions = new ConcurrentHashMap<>();
        this.playerAimProcessors = new ConcurrentHashMap<>();
        this.disconnectTimers = new ConcurrentHashMap<>();
        this.plugin = plugin;
    }
    public AimProcessor getOrCreateAimProcessor(UUID playerId) {
        return playerAimProcessors.computeIfAbsent(playerId, id -> new AimProcessor());
    }
    public void removeAimProcessor(UUID playerId) {
        playerAimProcessors.remove(playerId);
    }
    @Override
    public DataSession startSession(Player player, Label label, String comment) {
        UUID playerId = player.getUniqueId();
        // Kisibasina 1 kayit: aktif session varsa reddet (null dondurur, cagiran mesaj verir).
        if (hasActiveSession(playerId)) {
            return null;
        }
        // Geri sayim varsa iptal et (oyuncu geri geldi, yeni egitim baslatiliyor).
        cancelDisconnectTimer(playerId);
        AimProcessor aimProcessor = getOrCreateAimProcessor(playerId);
        DataSession session = new DataSession(
            playerId,
            player.getName(),
            label,
            comment,
            aimProcessor
        );
        activeSessions.put(playerId, session);
        plugin.getLogger().info("Started data collection for " + player.getName() +
            " [" + label + "]");
        return session;
    }
    @Override
    public void stopSession(Player player) {
        stopSession(player.getUniqueId());
    }
    @Override
    public void stopSession(UUID playerId) {
        cancelDisconnectTimer(playerId);
        DataSession session = activeSessions.remove(playerId);
        if (session != null) {
            try {
                session.saveAndClose(plugin, currentSessionFolder);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE,
                    "Failed to save session for " + session.getPlayerName(), e);
            }
        }
    }
    @Override
    public void stopAllSessions() {
        for (UUID playerId : activeSessions.keySet().toArray(new UUID[0])) {
            stopSession(playerId);
        }
        // Geri sayimlari da iptal et
        for (UUID playerId : disconnectTimers.keySet().toArray(new UUID[0])) {
            cancelDisconnectTimer(playerId);
        }
        this.currentSessionFolder = null;
    }
    @Override
    public boolean hasActiveSession(Player player) {
        return hasActiveSession(player.getUniqueId());
    }
    @Override
    public boolean hasActiveSession(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }
    @Override
    public DataSession getSession(UUID playerId) {
        return activeSessions.get(playerId);
    }
    @Override
    public DataSession getSession(Player player) {
        return getSession(player.getUniqueId());
    }
    @Override
    public Collection<DataSession> getActiveSessions() {
        return Collections.unmodifiableCollection(activeSessions.values());
    }
    @Override
    public int getActiveSessionCount() {
        return activeSessions.size();
    }
    @Override
    public String getCurrentSessionFolder() {
        return currentSessionFolder;
    }
    @Override
    public void onAttack(Player player) {
        DataSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            session.onAttack();
        }
    }
    @Override
    public void onTick(Player player, float yaw, float pitch) {
        DataSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            session.processTick(yaw, pitch);
        }
    }
    /**
     * Oyuncu sunucudan ayrildiginda cagrilir. Aktif kayit varsa 5 dakikalik
     * geri sayim baslatir. Oyuncu bu sure icinde geri donerse {@link #resumeSession}
     * otomatik kaydi devam ettirir; donmezse {@link #stopSession(UUID)} kayit save eder.
     */
    public void handlePlayerDisconnect(Player player) {
        UUID playerId = player.getUniqueId();
        if (!hasActiveSession(playerId)) {
            return;
        }
        // AimProcessor'u kaldir; oyuncu geri gelince start veya yeni tick ile yeniden olusur.
        removeAimProcessor(playerId);
        // Onceden var olan timer varsa iptal et
        cancelDisconnectTimer(playerId);
        // 5 dakika sonra save et + kapat
        ScheduledTask task = SchedulerManager.getAdapter().runSyncDelayed(() -> {
            disconnectTimers.remove(playerId);
            if (hasActiveSession(playerId)) {
                // Hala geri gelmemis: save et ve kapat
                DataSession session = activeSessions.remove(playerId);
                if (session != null) {
                    try {
                        session.saveAndClose(plugin, currentSessionFolder);
                        plugin.getLogger().info("Disconnect grace expired; saved session for "
                            + session.getPlayerName() + " (" + session.getTickCount() + " ticks).");
                    } catch (IOException e) {
                        plugin.getLogger().log(Level.SEVERE,
                            "Failed to save session for " + session.getPlayerName(), e);
                    }
                }
            }
        }, DISCONNECT_GRACE_TICKS);
        disconnectTimers.put(playerId, task);
        plugin.getLogger().info("Player " + player.getName()
            + " disconnected with active record; grace countdown started (5 min).");
    }
    /**
     * Oyuncu sunucuya katildiginda cagrilir. Hala geri sayim icinde bekleyen
     * aktif session varsa otomatik resume edilir; oyuncunun recordedTicks
     * kaybedilmez ve veri toplama devam eder.
     *
     * @return Resume edilen session, yoksa null.
     */
    public DataSession resumeSession(Player player) {
        UUID playerId = player.getUniqueId();
        if (!hasActiveSession(playerId)) {
            return null;
        }
        // Geri sayim iptal
        cancelDisconnectTimer(playerId);
        // AimProcessor yeniden olustur (quit sirasinda kaldirilmisti)
        getOrCreateAimProcessor(playerId);
        DataSession session = activeSessions.get(playerId);
        if (session != null) {
            plugin.getLogger().info("Resumed active record for " + player.getName()
                + " after reconnect (" + session.getTickCount() + " ticks buffered).");
        }
        return session;
    }
    private void cancelDisconnectTimer(UUID playerId) {
        ScheduledTask task = disconnectTimers.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
}
