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

package com.zeyronac.penalty.handlers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import com.zeyronac.Main;
import com.zeyronac.penalty.ActionHandler;
import com.zeyronac.penalty.ActionType;
import com.zeyronac.penalty.BanAnimation;
import com.zeyronac.penalty.PenaltyContext;
import com.zeyronac.penalty.engine.AnimationManager;
import com.zeyronac.scheduler.SchedulerManager;

public class BanHandler implements ActionHandler {
    private final Main plugin;
    private final BanAnimation legacyAnimation; // Fallback for cases when AnimationManager is unavailable
    private boolean animationEnabled = true;

    public BanHandler(JavaPlugin plugin) {
        this.plugin = (Main) plugin;
        // Create legacy animation as fallback
        this.legacyAnimation = new BanAnimation(plugin, false); // false = do not use new engine in legacy
    }

    @Override
    public void handle(String command, PenaltyContext context) {
        if (command == null || command.isEmpty()) {
            return;
        }
        
        Player player = null;
        if (context != null && context.getPlayerName() != null) {
            player = Bukkit.getPlayer(context.getPlayerName());
        }
        
        if (animationEnabled && player != null && player.isOnline()) {
            // Try to use the new AnimationManager
            AnimationManager animationManager = plugin.getAnimationManager();
            
            if (animationManager != null) {
                // Get animation name from configuration
                String animationType = plugin.getPluginConfig().getAnimationType();
                if (animationType == null || animationType.isEmpty()) {
                    animationType = "classic_ban"; // Fallback to classic_ban
                }
                
                plugin.getLogger().info("Using AnimationManager to play animation: " + animationType);
                
                // Use the new AnimationManager
                boolean success = animationManager.playAnimation(player, animationType, command);
                
                if (!success) {
                    plugin.getLogger().warning("Failed to play animation " + animationType + ", falling back to direct command execution");
                    // If animation failed, execute command directly
                    SchedulerManager.getAdapter().runSync(() -> {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    });
                }
            } else {
                plugin.getLogger().warning("AnimationManager not available, using legacy animation");
                // Fallback to legacy animation
                legacyAnimation.playAnimation(player, command, context);
            }
        } else {
            // Animations disabled or player not online - execute command directly
            SchedulerManager.getAdapter().runSync(() -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            });
        }
    }

    public void setAnimationEnabled(boolean enabled) {
        this.animationEnabled = enabled;
    }

    public boolean isAnimationEnabled() {
        return animationEnabled;
    }

    public BanAnimation getLegacyAnimation() {
        return legacyAnimation;
    }

    public void shutdown() {
        if (legacyAnimation != null) {
            legacyAnimation.shutdown();
        }
    }

    @Override
    public ActionType getActionType() {
        return ActionType.ANIMATION;
    }
}