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

package com.zeyronac;

import com.github.retrooper.packetevents.PacketEvents;
import com.zeyronac.penalty.engine.AnimationManager;
import com.zeyronac.stats.DailyStats;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import com.zeyronac.alert.AlertManager;
import com.zeyronac.checks.AICheck;
import com.zeyronac.commands.CommandHandler;
import com.zeyronac.compat.VersionAdapter;
import com.zeyronac.config.Config;
import com.zeyronac.config.ConfigSyncUtil;
import com.zeyronac.config.HologramConfig;
import com.zeyronac.config.MenuConfig;
import com.zeyronac.config.MessagesConfig;
import com.zeyronac.datacollector.DataCollectorFactory;
import com.zeyronac.hologram.HologramManager;
import com.zeyronac.listeners.HitListener;
import com.zeyronac.listeners.PlayerListener;
import com.zeyronac.listeners.RotationListener;
import com.zeyronac.listeners.TeleportListener;
import com.zeyronac.listeners.TickListener;
import com.zeyronac.listeners.CombatPenaltyListener;
import com.zeyronac.response.DetectionResponseManager;
import com.zeyronac.scheduler.SchedulerManager;
import com.zeyronac.server.AIClientProvider;
import com.zeyronac.session.ISessionManager;
import com.zeyronac.session.SessionManager;
import com.zeyronac.violation.ViolationManager;
import com.zeyronac.util.FeatureCalculator;
import com.zeyronac.util.UpdateChecker;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public final class Main extends JavaPlugin {
    private Config config;
    private MenuConfig menuConfig;
    private MessagesConfig messagesConfig;
    private HologramConfig hologramConfig;
    private ISessionManager sessionManager;
    private FeatureCalculator featureCalculator;
    private TickListener tickListener;
    private HitListener hitListener;
    private RotationListener rotationListener;
    private PlayerListener playerListener;
    private TeleportListener teleportListener;
    private CommandHandler commandHandler;
    private AIClientProvider aiClientProvider;
    private AlertManager alertManager;
    private ViolationManager violationManager;
    private HologramManager hologramManager;
    private AICheck aiCheck;
    private UpdateChecker updateChecker;
    private DetectionResponseManager detectionResponseManager;
    private CombatPenaltyListener combatPenaltyListener;
    private DailyStats dailyStats;
    private AnimationManager animationManager;

    @Override
    public void onLoad() {
        VersionAdapter.init(getLogger());
    }

    @Override
    public void onEnable() {
        try {
            SchedulerManager.reset();
            SchedulerManager.initialize(this);
        } catch (Throwable e) {
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            if (PacketEvents.getAPI() == null || !PacketEvents.getAPI().isLoaded()) {
                PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
                PacketEvents.getAPI().getSettings()
                        .reEncodeByDefault(false)
                        .checkForUpdates(false)
                        .bStats(false)
                        .debug(false);
                PacketEvents.getAPI().load();
            }

            if (!PacketEvents.getAPI().isInitialized()) {
                PacketEvents.getAPI().init();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        VersionAdapter.get().logCompatibilityInfo();
        ConfigSyncUtil.migrateConfigSchemaIfNeeded(this);
        ConfigSyncUtil.wipeMessagesIfAnyKeyMissing(this);
        ConfigSyncUtil.syncAllPluginConfigs(this);
        this.config = new Config(this, getLogger());
        this.menuConfig = new MenuConfig(this);
        this.menuConfig.load();
        this.messagesConfig = new MessagesConfig(this);
        this.messagesConfig.load();
        this.hologramConfig = new HologramConfig(this);
        this.hologramConfig.load();

        File outputDir = new File(config.getOutputDirectory());
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        this.featureCalculator = new FeatureCalculator();
        this.sessionManager = DataCollectorFactory.createSessionManager(this);
        this.aiClientProvider = new AIClientProvider(this, config);
        this.alertManager = new AlertManager(this, config);
        this.violationManager = new ViolationManager(this, config, alertManager);
        this.aiCheck = new AICheck(this, config, aiClientProvider, alertManager, violationManager);
        this.violationManager.setAICheck(aiCheck);
        this.detectionResponseManager = new DetectionResponseManager(this, config);

        this.animationManager = new AnimationManager(this);
        this.animationManager.initialize();

        this.hologramManager = new HologramManager(this, aiCheck);
        this.hologramManager.start();

        if (config.isAiEnabled()) {
            aiClientProvider.initialize().thenAccept(success -> {
                if (success) {
                    getLogger().info(aiClientProvider.getClientType() + ": Connected to " + config.getServerAddress());
                } else {
                    getLogger().warning(aiClientProvider.getClientType() + ": Failed to connect to InferenceServer");
                }
            });
        }
        this.tickListener = new TickListener(this, sessionManager, aiCheck);
        this.hitListener = new HitListener(sessionManager, aiCheck);
        this.rotationListener = new RotationListener(sessionManager, aiCheck);
        this.dailyStats = new DailyStats(this);
        this.dailyStats.initialize();
        this.playerListener = new PlayerListener(this, aiCheck, alertManager, violationManager,
                sessionManager instanceof SessionManager ? (SessionManager) sessionManager : null, tickListener,
                hologramManager, rotationListener);
        this.teleportListener = new TeleportListener(aiCheck, this);
        this.combatPenaltyListener = new CombatPenaltyListener(this, detectionResponseManager);
        this.tickListener.setHitListener(hitListener);
        this.playerListener.setHitListener(hitListener);
        this.hitListener.cacheOnlinePlayers();
        this.tickListener.start();
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            this.tickListener.startPlayerTask(p);
        }
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(teleportListener, this);
        getServer().getPluginManager().registerEvents(combatPenaltyListener, this);
        PacketEvents.getAPI().getEventManager().registerListener(hitListener);
        PacketEvents.getAPI().getEventManager().registerListener(rotationListener);
        this.commandHandler = new CommandHandler(sessionManager, alertManager, aiCheck, this);
        PluginCommand command = getCommand("zeyronac");
        if (command != null) {
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);
        }
        getLogger().info("Data collector: ENABLED (output: " + config.getOutputDirectory() + ")");
        if (config.isAiEnabled()) {
            getLogger().info("AI detection: ENABLED (threshold: " + config.getAiAlertThreshold() + ")");
        } else {
            getLogger().info("AI detection: DISABLED (TURN IT ON IN CONFIG.YML)");
        }

        this.updateChecker = new UpdateChecker(this, config);
        updateChecker.start();
    }

    @Override
    public void onDisable() {
        if (tickListener != null) {
            tickListener.stop();
        }
        if (hologramManager != null) {
            hologramManager.stop();
        }
        if (sessionManager != null) {
            sessionManager.stopAllSessions();
        }
        if (aiCheck != null) {
            aiCheck.clearAll();
        }
        if (violationManager != null) {
            violationManager.shutdown();
        }
        if (animationManager != null) {
            animationManager.shutdown();
        }
        if (commandHandler != null) {
            commandHandler.cleanup();
        }
        if (updateChecker != null) {
            updateChecker.stop();
        }
        if (aiClientProvider != null) {
            getLogger().info("Shutting down network client...");
            try {
                aiClientProvider.shutdown().get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                if (e.getMessage() != null) {
                    getLogger().warning("Error shutting down network client: " + e.getMessage());
                } else {
                    getLogger().warning("Error shutting down network client during disable:");
                    e.printStackTrace();
                }
            }
        }
        if (dailyStats != null) {
            dailyStats.shutdown();
        }
        if (PacketEvents.getAPI() != null && PacketEvents.getAPI().isInitialized()) {
            try {
                if (hitListener != null) {
                    PacketEvents.getAPI().getEventManager().unregisterListener(hitListener);
                }
                if (rotationListener != null) {
                    PacketEvents.getAPI().getEventManager().unregisterListener(rotationListener);
                }
            } catch (Exception e) {
                getLogger().warning("Error unregistering listeners: " + e.getMessage());
            }
            PacketEvents.getAPI().terminate();
        }
        SchedulerManager.reset();
    }

    public void reloadPluginConfig() {
        SchedulerManager.getAdapter().runSync(() -> {
            try {
                reloadConfig();
                // Reload suresinde config dosyalarini tekrar senkronize etmek
                // kullanici ayarlarinin ezilmesine neden olabiliyor.
                // Eksik anahtarlar onEnable sirasinda zaten senkronize edilir.
                // ConfigSyncUtil.syncAllPluginConfigs(this);
                this.config = new Config(this, getLogger());
                if (menuConfig != null)
                    menuConfig.reload();
                if (messagesConfig != null)
                    messagesConfig.reload();
                if (hologramConfig != null)
                    hologramConfig.reload();

                if (hologramManager != null) {
                    hologramManager.stop();
                }

                hologramManager = new HologramManager(this, aiCheck);
                hologramManager.start();
                if (playerListener != null) {
                    playerListener.setHologramManager(hologramManager);
                }

                alertManager.setConfig(config);
                violationManager.setConfig(config);
                aiCheck.setConfig(config);
                if (detectionResponseManager != null) {
                    detectionResponseManager.setConfig(config);
                }
                if (aiClientProvider != null) {
                    aiClientProvider.setConfig(config);
                    if (config.isAiEnabled()) {
                        aiClientProvider.reload().thenAccept(success -> {
                            if (success) {
                                getLogger().info(aiClientProvider.getClientType() + ": Reconnected to "
                                        + config.getServerAddress());
                            }
                        });
                    } else {
                        aiClientProvider.shutdown();
                    }
                }
                if (updateChecker != null) {
                    updateChecker.stop();
                }
                updateChecker = new UpdateChecker(this, config);
                updateChecker.start();
                getLogger().info("Configuration reloaded!");
            } catch (Exception e) {
                getLogger().severe("Failed to reload configuration: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public boolean reinstallPluginConfig() {
        try {
            ConfigSyncUtil.syncAllPluginConfigs(this);
            reloadPluginConfig();
            getLogger().info(
                    "All configuration YAML files were synced. Existing values were preserved and missing entries were added.");
            return true;
        } catch (Exception e) {
            getLogger().severe("Failed to reinstall configuration: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public MenuConfig getMenuConfig() {
        return menuConfig;
    }

    public MessagesConfig getMessagesConfig() {
        return messagesConfig;
    }

    public HologramConfig getHologramConfig() {
        return hologramConfig;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public Config getPluginConfig() {
        return config;
    }

    public ISessionManager getSessionManager() {
        return sessionManager;
    }

    public FeatureCalculator getFeatureCalculator() {
        return featureCalculator;
    }

    public AICheck getAiCheck() {
        return aiCheck;
    }

    public AlertManager getAlertManager() {
        return alertManager;
    }

    public ViolationManager getViolationManager() {
        return violationManager;
    }

    public AnimationManager getAnimationManager() {
        return animationManager;
    }

    public AIClientProvider getAiClientProvider() {
        return aiClientProvider;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public DetectionResponseManager getDetectionResponseManager() {
        return detectionResponseManager;
    }

    public DailyStats getDailyStats() {
        return dailyStats;
    }

    public void debug(String message) {
        if (config != null && config.isDebug()) {
            getLogger().info("[Debug] " + message);
        }
    }
}
