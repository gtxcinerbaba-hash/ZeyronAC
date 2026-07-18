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
import com.zeyronac.config.Label;
import com.zeyronac.data.DataSession;
import java.util.Collection;
import java.util.UUID;
public interface ISessionManager {
    DataSession startSession(Player player, Label label, String comment);
    void stopSession(Player player);
    void stopSession(UUID playerId);
    void stopAllSessions();
    boolean hasActiveSession(Player player);
    boolean hasActiveSession(UUID playerId);
    DataSession getSession(UUID playerId);
    DataSession getSession(Player player);
    Collection<DataSession> getActiveSessions();
    int getActiveSessionCount();
    String getCurrentSessionFolder();
    void onAttack(Player player);
    void onTick(Player player, float yaw, float pitch);

    /**
     * Oyuncu sunucudan ayrildiginda cagrilir. Aktif kayit varsa 5 dakikalik
     * geri sayim baslatir; oyuncu geri gelmezse session save edilir.
     */
    void handlePlayerDisconnect(Player player);

    /**
     * Oyuncu sunucuya tekrar katildiginda cagrilir. Hala bekleyen aktif
     * session varsa otomatik resume edilir.
     *
     * @return Resume edilen session, yoksa null.
     */
    DataSession resumeSession(Player player);
}