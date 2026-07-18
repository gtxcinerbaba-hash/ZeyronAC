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

package com.zeyronac.commands;

import com.zeyronac.datacollector.DataRestorer;
import com.zeyronac.menu.SuspectsMenu;
import com.zeyronac.penalty.engine.AnimationManager;
import com.zeyronac.server.AIClientProvider;
import com.zeyronac.server.IAIClient;
import com.zeyronac.stats.DailyStats;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import com.zeyronac.Main;
import com.zeyronac.Permissions;
import com.zeyronac.alert.AlertManager;
import com.zeyronac.checks.AICheck;
import com.zeyronac.config.Config;
import com.zeyronac.config.Label;
import com.zeyronac.data.AIPlayerData;
import com.zeyronac.data.DataSession;
import com.zeyronac.data.TickData;
import com.zeyronac.scheduler.ScheduledTask;
import com.zeyronac.scheduler.SchedulerManager;
import com.zeyronac.session.ISessionManager;
import com.zeyronac.util.ColorUtil;
import com.zeyronac.util.ProbabilityFormatUtil;
import com.zeyronac.violation.ViolationManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;

public class CommandHandler implements CommandExecutor, TabCompleter {
    private final ISessionManager sessionManager;
    private final AlertManager alertManager;
    private final AICheck aiCheck;
    private final Main plugin;
    private final DataRestorer dataRestorer;
    private final Map<UUID, UUID> probTracking = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> probTasks = new ConcurrentHashMap<>();
    private final Map<String, Long> reinstallConfirmations = new ConcurrentHashMap<>();
    private static final long REINSTALL_CONFIRM_WINDOW_MILLIS = TimeUnit.SECONDS.toMillis(3);

    /**
     * Registry of sub-commands. Each entry owns its access policy (required permissions and
     * whether it is player-only) so that the dispatcher in {@link #onCommand} enforces those
     * checks in one place instead of every handler repeating the same boilerplate.
     */
    private final Map<String, SubCommand> subCommands = new LinkedHashMap<>();

    public CommandHandler(ISessionManager sessionManager, AlertManager alertManager,
            AICheck aiCheck, Main plugin) {
        this.sessionManager = sessionManager;
        this.alertManager = alertManager;
        this.aiCheck = aiCheck;
        this.plugin = plugin;
        this.dataRestorer = new DataRestorer(plugin);
        registerSubCommands();
    }

    private void registerSubCommands() {
        Set<String> admin = Set.of(Permissions.ADMIN);
        Set<String> alertsOrAdmin = Set.of(Permissions.ALERTS, Permissions.ADMIN);
        Set<String> probOrAdmin = Set.of(Permissions.PROB, Permissions.ADMIN);
        Set<String> reloadOrAdmin = Set.of(Permissions.RELOAD, Permissions.ADMIN);

        // Data collection commands are intentionally permission-less (legacy behavior preserved).
        register("record", Set.of(), false, this::handleRecord);

        register("alerts", alertsOrAdmin, true, (sender, args) -> handleAlerts(sender));
        register("monitor", alertsOrAdmin, true, (sender, args) -> handleMonitor(sender));
        register("suspects", alertsOrAdmin, true, (sender, args) -> handleSuspects(sender));
        register("prob", probOrAdmin, true, this::handleProb);
        register("reload", reloadOrAdmin, false, (sender, args) -> handleReload(sender));

        register("reinstall", admin, false, (sender, args) -> handleReinstall(sender));
        register("datastatus", admin, false, (sender, args) -> handleDataStatus(sender));
        register("kicklist", admin, false, this::handleKickList);
        register("punish", admin, false, this::handlePunish);
        register("profile", admin, false, this::handleProfile);
        register("falsepositive", admin, false, this::handleFalsePositive);
        register("status", admin, false, (sender, args) -> handleStatus(sender));
        register("animation", admin, false, this::handleAnimation);
    }

    private void register(String name, Set<String> anyPermission, boolean playersOnly,
            BiConsumer<CommandSender, String[]> handler) {
        subCommands.put(name, new SubCommand(anyPermission, playersOnly, handler));
    }

    private Config getConfig() {
        return plugin.getPluginConfig();
    }

    private String getPrefix() {
        return ColorUtil.colorize(plugin.getMessagesConfig().getPrefix());
    }

    private String msg(String key) {
        return ColorUtil.colorize(plugin.getMessagesConfig().getMessage(key));
    }

    private String msg(String key, String... replacements) {
        return ColorUtil.colorize(plugin.getMessagesConfig().getMessage(key, replacements));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        SubCommand sub = subCommands.get(args[0].toLowerCase());
        if (sub == null) {
            sender.sendMessage(getPrefix() + msg("unknown-command", "{ARGS}", args[0]));
            sendUsage(sender);
            return true;
        }
        if (sub.playersOnly && !(sender instanceof Player)) {
            sender.sendMessage(getPrefix() + msg("players-only"));
            return true;
        }
        if (!hasAnyPermission(sender, sub.anyPermission)) {
            sender.sendMessage(getPrefix() + msg("no-permission"));
            return true;
        }
        sub.handler.accept(sender, args);
        return true;
    }

    private boolean hasAnyPermission(CommandSender sender, Set<String> anyPermission) {
        if (anyPermission.isEmpty()) {
            return true;
        }
        for (String permission : anyPermission) {
            if (sender.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    private void handleSuspects(CommandSender sender) {
        new SuspectsMenu(plugin, (Player) sender).open();
    }

    private void handleAlerts(CommandSender sender) {
        alertManager.toggleAlerts((Player) sender);
    }

    private void handleMonitor(CommandSender sender) {
        alertManager.toggleMonitor((Player) sender);
    }

    private void handleProb(CommandSender sender, String[] args) {
        Player admin = (Player) sender;
        if (probTracking.containsKey(admin.getUniqueId())) {
            stopTracking(admin);
            admin.sendMessage(getPrefix() + msg("tracking-stopped"));
            return;
        }
        if (args.length < 2) {
            admin.sendMessage(getPrefix() + msg("prob-usage"));
            return;
        }
        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            admin.sendMessage(getPrefix() + msg("player-not-found", "{PLAYER}", playerName));
            return;
        }
        startTracking(admin, target);
        admin.sendMessage(getPrefix() + msg("tracking-started", "{PLAYER}", target.getName()));
    }

    private void startTracking(Player admin, Player target) {
        UUID adminId = admin.getUniqueId();
        UUID targetId = target.getUniqueId();
        String targetName = target.getName();
        stopTracking(admin);
        probTracking.put(adminId, targetId);
        ScheduledTask task = SchedulerManager.getAdapter().runEntitySyncRepeating(admin, () -> {
            Player adminPlayer = Bukkit.getPlayer(adminId);
            Player targetPlayer = Bukkit.getPlayer(targetId);
            if (adminPlayer == null || !adminPlayer.isOnline()) {
                stopTracking(adminId);
                return;
            }
            if (targetPlayer == null || !targetPlayer.isOnline()) {
                sendActionBar(adminPlayer, msg("player-offline"));
                stopTracking(adminId);
                return;
            }
            AIPlayerData data = aiCheck.getPlayerData(targetId);
            String message;
            if (data == null) {
                message = ColorUtil.colorize("&7" + targetName + ": &eNo data");
            } else {
                double buffer = data.getBuffer();
                int vl = plugin.getViolationManager().getViolationLevel(targetId);
                String template = plugin.getMessagesConfig().getMessage("actionbar-format",
                        targetName, data.getLastProbability(), buffer, vl);
                String status = plugin.getMessagesConfig().getMessage(
                        data.isBufferIncreasing() ? "STATUS_UP" : "STATUS_DOWN");
                template = ProbabilityFormatUtil.applyModelPlaceholders(template, data)
                        .replace("{STATUS}", status)
                        .replace("{PLAYER}", targetName);
                message = ColorUtil.colorize(template);
            }
            sendActionBar(adminPlayer, message);
        }, 0L, 10L);
        probTasks.put(adminId, task);
    }

    private void stopTracking(Player admin) {
        stopTracking(admin.getUniqueId());
    }

    private void stopTracking(UUID adminId) {
        probTracking.remove(adminId);
        ScheduledTask task = probTasks.remove(adminId);
        if (task != null) {
            task.cancel();
        }
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadPluginConfig();

        AnimationManager animationManager = plugin.getAnimationManager();
        if (animationManager != null) {
            try {
                animationManager.reload();
                sender.sendMessage(getPrefix() + ColorUtil.colorize("&aAnimations reloaded! &7(" +
                        animationManager.getAvailableAnimations().size() + " animations)"));
            } catch (Exception e) {
                sender.sendMessage(getPrefix() + ColorUtil.colorize("&cError reloading animations!"));
                plugin.getLogger().severe("Failed to reload animations: " + e.getMessage());
            }
        }

        sender.sendMessage(getPrefix() + msg("config-reloaded"));
    }

    private void handleReinstall(CommandSender sender) {
        String confirmationKey = getConfirmationKey(sender);
        long now = System.currentTimeMillis();
        Long expiresAt = reinstallConfirmations.get(confirmationKey);
        if (expiresAt == null || expiresAt < now) {
            reinstallConfirmations.put(confirmationKey, now + REINSTALL_CONFIRM_WINDOW_MILLIS);
            sender.sendMessage(getPrefix() + ColorUtil.colorize(
                    "&eRe-enter &f/zeyronac reinstall &ewithin 3 seconds to confirm."));
            return;
        }
        reinstallConfirmations.remove(confirmationKey);
        boolean success = plugin.reinstallPluginConfig();
        if (success) {
            sender.sendMessage(getPrefix() + ColorUtil.colorize(
                    "&aYAML configs were synced with current defaults. Existing values were kept and missing fields were added."));
        } else {
            sender.sendMessage(getPrefix() + ColorUtil.colorize("&cFailed to sync YAML configs. Check console."));
        }
    }

    private String getConfirmationKey(CommandSender sender) {
        if (sender instanceof Player) {
            return ((Player) sender).getUniqueId().toString();
        }
        return "console:" + sender.getName().toLowerCase();
    }

    private void handleKickList(CommandSender sender, String[] args) {
        List<ViolationManager.KickRecord> kicks = plugin.getViolationManager().getKickHistory();
        if (kicks.isEmpty()) {
            sender.sendMessage(getPrefix() + msg("kicklist-empty"));
            return;
        }

        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        int pageSize = 10;
        int maxPage = (int) Math.ceil((double) kicks.size() / pageSize);
        if (page < 1)
            page = 1;
        if (page > maxPage)
            page = maxPage;

        sender.sendMessage(getPrefix()
                + msg("kicklist-header", "{PAGE}", String.valueOf(page), "{MAX_PAGE}", String.valueOf(maxPage)));
        sender.sendMessage(ColorUtil.colorize("&7─────────────────────────────────"));

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, kicks.size());

        for (int i = start; i < end; i++) {
            ViolationManager.KickRecord kick = kicks.get(i);
            sender.sendMessage(ColorUtil.colorize(String.format(java.util.Locale.ROOT,
                    "&e%d. &f%s &7[&c%s&7] &8- &bProb: &f%.2f &8| &bBuf: &f%.1f &8| &bVL: &f%d",
                    i + 1,
                    kick.getPlayerName(),
                    kick.getFormattedTime(),
                    kick.getProbability(),
                    kick.getBuffer(),
                    kick.getVl())));
        }
        sender.sendMessage(ColorUtil.colorize("&7─────────────────────────────────"));
        if (page < maxPage) {
            sender.sendMessage(getPrefix() + msg("kicklist-footer", "{NEXT_PAGE}", String.valueOf(page + 1)));
        }
    }

    private void handleFalsePositive(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("restore")) {
            sender.sendMessage(getPrefix() + msg("falsepositive-usage"));
            return;
        }

        String targetName = args[2];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(getPrefix() + msg("player-not-found", "{PLAYER}", targetName));
            return;
        }

        AIPlayerData data = aiCheck.getPlayerData(target.getUniqueId());
        if (data == null) {
            sender.sendMessage(getPrefix() + msg("falsepositive-no-data"));
            return;
        }

        List<TickData> history = data.getTickHistory();
        boolean success = dataRestorer.restoreData(target.getName(), history);

        if (success) {
            sender.sendMessage(getPrefix() + msg("falsepositive-success"));
        } else {
            sender.sendMessage(getPrefix() + msg("falsepositive-fail"));
        }
    }

    private void handlePunish(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(getPrefix() + msg("usage-punish"));
            return;
        }
        // Exact-name lookup only: Bukkit.getPlayer() falls back to prefix matching and can
        // resolve a truncated/mistyped name to an unrelated online player (e.g. an AFK one).
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(getPrefix() + msg("player-not-found", "{PLAYER}", args[1]));
            return;
        }

        String issuer = sender instanceof Player ? sender.getName() : "CONSOLE(" + sender.getName() + ")";
        int vl = plugin.getViolationManager().getViolationLevel(target.getUniqueId());
        AIPlayerData data = aiCheck.getPlayerData(target.getUniqueId());
        double buffer = data != null ? data.getBuffer() : 0.0;
        int detections = data != null ? data.getHighProbabilityDetections() : 0;
        boolean confirmed = args.length >= 3 && args[2].equalsIgnoreCase("confirm");

        if (vl <= 0 && buffer <= 0.0 && detections <= 0 && !confirmed) {
            sender.sendMessage(getPrefix() + msg("punish-no-evidence", "{PLAYER}", target.getName()));
            sender.sendMessage(msg("punish-confirm-hint", "{PLAYER}", target.getName()));
            plugin.getLogger().info("[Penalty] /zeyronac punish " + target.getName() + " BLOCKED by "
                    + issuer + " - no evidence (VL=0, buffer=0.0, detections=0)");
            return;
        }

        plugin.getLogger().info("[Penalty] /zeyronac punish " + target.getName() + " issued by " + issuer
                + " | VL=" + vl + " buffer=" + String.format(java.util.Locale.ROOT, "%.1f", buffer)
                + " detections=" + detections + (confirmed ? " (confirmed override)" : ""));
        plugin.getViolationManager().executeMaxPunishment(target);
        if (plugin.getPluginConfig().getPunishmentCommands().isEmpty()) {
            sender.sendMessage(getPrefix() + msg("punish-no-action"));
        } else {
            sender.sendMessage(getPrefix() + msg("punish-success", "{PLAYER}", target.getName(), "{ACTION}", "Max VL"));
        }
    }

    private void handleProfile(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(getPrefix() + msg("usage-profile"));
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(getPrefix() + msg("player-not-found", "{PLAYER}", args[1]));
            return;
        }

        AIPlayerData data = aiCheck.getPlayerData(target.getUniqueId());
        String sens = "N/A";
        int detections = 0;

        if (data != null) {
            int s = data.getAimProcessor().getSensitivity();
            if (s != -1) {
                sens = String.valueOf(s);
            }
            detections = data.getHighProbabilityDetections();
        }

        ClientVersion version = PacketEvents.getAPI().getPlayerManager().getClientVersion(target);
        String clientVer = version != null ? version.toString() : "Unknown";

        sender.sendMessage(ColorUtil.colorize(msg("profile-header", "{PLAYER}", target.getName())));
        List<String> info = plugin.getMessagesConfig().getMessageList("profile-info");
        if (info == null || info.isEmpty()) {
            sender.sendMessage(ColorUtil.colorize("&7Sens: &f" + sens + "%"));
            sender.sendMessage(ColorUtil.colorize("&7Client: &f" + clientVer));
            sender.sendMessage(ColorUtil.colorize("&7Detections (>0.8): &f" + detections));
        } else {
            for (String line : info) {
                sender.sendMessage(ColorUtil.colorize(line
                        .replace("{PLAYER}", target.getName())
                        .replace("{SENS}", sens)
                        .replace("{CLIENT}", clientVer)
                        .replace("{DETECTIONS}", String.valueOf(detections))));
            }
        }
    }

    private void handleDataStatus(CommandSender sender) {
        int activeSessions = sessionManager.getActiveSessionCount();
        sender.sendMessage(getPrefix() + msg("data-status-header"));
        sender.sendMessage(msg("active-sessions", "{COUNT}", String.valueOf(activeSessions)));
        if (activeSessions > 0) {
            sender.sendMessage(ColorUtil.colorize("&7Players collecting data:"));
            for (DataSession session : sessionManager.getActiveSessions()) {
                Player player = Bukkit.getPlayer(session.getUuid());
                String playerName = player != null ? player.getName() : session.getPlayerName();
                String sessionLabel = session.getLabel().name();
                String comment = session.getComment();
                boolean inCombat = session.isInCombat();
                int tickCount = session.getTickCount();
                sender.sendMessage(ColorUtil.colorize("&b  " + playerName + "&7 [&e" + sessionLabel + "&7]" +
                        (comment.isEmpty() ? "" : " \"" + comment + "\"")));
                sender.sendMessage(ColorUtil.colorize("&7    Ticks: &a" + tickCount +
                        "&7 | In combat: " + (inCombat ? "&aYes" : "&cNo")));
            }
        } else {
            sender.sendMessage(msg("no-active-sessions"));
            sender.sendMessage(msg("start-hint"));
        }
    }

    private void handleRecord(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(getPrefix() + msg("record-usage"));
            return;
        }
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "start":
                handleRecordStart(sender, args);
                return;
            case "stop":
                handleRecordStop(sender, args);
                return;
            case "status":
                handleRecordStatus(sender);
                return;
            default:
                sender.sendMessage(getPrefix() + msg("record-usage"));
        }
    }

    private void handleRecordStart(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(getPrefix() + msg("record-start-usage"));
            return;
        }
        String playerName = args[2];
        String labelStr = args[3];
        Label sessionLabel = Label.fromString(labelStr);
        if (sessionLabel == null) {
            sender.sendMessage(getPrefix() + msg("invalid-label", "{LABEL}", labelStr));
            sender.sendMessage(getPrefix() + msg("valid-labels"));
            return;
        }
        String comment = parseComment(args, 4);

        Player player = Bukkit.getPlayerExact(playerName);
        if (player == null) {
            sender.sendMessage(getPrefix() + msg("player-not-found", "{PLAYER}", playerName));
            return;
        }
        if (sessionManager.hasActiveSession(player.getUniqueId())) {
            sender.sendMessage(getPrefix()
                + msg("session-already-active", "{PLAYER}", player.getName()));
            return;
        }
        DataSession session = sessionManager.startSession(player, sessionLabel, comment);
        if (session == null) {
            sender.sendMessage(getPrefix()
                + msg("session-already-active", "{PLAYER}", player.getName()));
            return;
        }
        sender.sendMessage(getPrefix()
            + msg("session-started", "{LABEL}", sessionLabel.name(), "{PLAYER}", player.getName()));
    }

    private void handleRecordStop(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(getPrefix() + msg("record-stop-usage"));
            return;
        }
        String target = args[2];
        if (target.equalsIgnoreCase("all")) {
            int count = sessionManager.getActiveSessionCount();
            sessionManager.stopAllSessions();
            sender.sendMessage(getPrefix()
                + msg("all-sessions-stopped", "{COUNT}", String.valueOf(count)));
            return;
        }
        // Once online oyuncu dene, sonra offline (UUID ile session hala duruyor olabilir).
        Player player = Bukkit.getPlayerExact(target);
        if (player != null) {
            if (!sessionManager.hasActiveSession(player.getUniqueId())) {
                sender.sendMessage(getPrefix()
                    + msg("no-sessions-to-stop", "{PLAYER}", player.getName()));
                return;
            }
            sessionManager.stopSession(player);
            sender.sendMessage(getPrefix()
                + msg("session-stopped", "{PLAYER}", player.getName()));
            return;
        }
        // Offline: ismiyle aktif session varsa UUID werwe bul
        for (DataSession session : sessionManager.getActiveSessions()) {
            if (session.getPlayerName().equalsIgnoreCase(target)) {
                sessionManager.stopSession(session.getUuid());
                sender.sendMessage(getPrefix()
                    + msg("session-stopped", "{PLAYER}", session.getPlayerName()));
                return;
            }
        }
        sender.sendMessage(getPrefix() + msg("player-not-found", "{PLAYER}", target));
    }

    private void handleRecordStatus(CommandSender sender) {
        int count = sessionManager.getActiveSessionCount();
        sender.sendMessage(getPrefix() + msg("record-status-header"));
        if (count == 0) {
            sender.sendMessage(msg("record-status-empty"));
            return;
        }
        sender.sendMessage(ColorUtil.colorize("&fActive records: &a" + count));
        sender.sendMessage(ColorUtil.colorize("&7─────────────────────────────────"));
        for (DataSession session : sessionManager.getActiveSessions()) {
            Player player = Bukkit.getPlayer(session.getUuid());
            String name = player != null ? player.getName() : session.getPlayerName();
            long durationMs = java.time.Duration.between(session.getStartTime(), java.time.Instant.now()).toMillis();
            String duration = formatDuration(durationMs);
            int ticks = session.getTickCount();
            sender.sendMessage(ColorUtil.colorize(String.format(java.util.Locale.ROOT,
                "&b%s &7[&e%s&7] &7ticks: &a%d &7duration: &a%s",
                name, session.getLabel().name(), ticks, duration)));
            if (session.getComment() != null && !session.getComment().isEmpty()) {
                sender.sendMessage(ColorUtil.colorize(
                    msg("record-status-comment", "{COMMENT}", session.getComment())));
            }
            sender.sendMessage(ColorUtil.colorize(msg(
                session.isInCombat() ? "record-status-incombat" : "record-status-notincombat")));
        }
        sender.sendMessage(ColorUtil.colorize("&7─────────────────────────────────"));
    }

    private String formatDuration(long ms) {
        long sec = ms / 1000;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        if (h > 0) return String.format(java.util.Locale.ROOT, "%dh %dm %ds", h, m, s);
        if (m > 0) return String.format(java.util.Locale.ROOT, "%dm %ds", m, s);
        return String.format(java.util.Locale.ROOT, "%ds", s);
    }

    private String parseComment(String[] args, int startIndex) {
        if (startIndex >= args.length) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(args[i]);
        }
        String comment = sb.toString();
        if (comment.startsWith("\"") && comment.endsWith("\"") && comment.length() >= 2) {
            comment = comment.substring(1, comment.length() - 1);
        } else if (comment.startsWith("\"")) {
            comment = comment.substring(1);
        }
        return comment.trim();
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(getPrefix() + msg("usage-header"));
        sender.sendMessage(msg("usage-record"));
        sender.sendMessage(msg("usage-datastatus"));
        sender.sendMessage(msg("usage-alerts"));
        sender.sendMessage(ColorUtil.colorize("&7  /zeyronac monitor - Toggle monitor mode (show all detections)"));
        sender.sendMessage(msg("usage-prob"));
        sender.sendMessage(msg("usage-suspects"));
        sender.sendMessage(msg("usage-punish"));
        sender.sendMessage(msg("usage-profile"));
        sender.sendMessage(ColorUtil.colorize("&7  /zeyronac reload - Reload config and animations"));
        sender.sendMessage(ColorUtil.colorize("&7  /zeyronac status - Check API connection, latency, and daily stats"));
        sender.sendMessage(ColorUtil.colorize("&7  /zeyronac animation test <player> <animation> - Test ban animation"));
        sender.sendMessage(ColorUtil.colorize("&7  /zeyronac animation list - List available animations"));
        sender.sendMessage(ColorUtil.colorize("&7  /zeyronac animation reload - Reload only animations"));
        sender.sendMessage(ColorUtil
                .colorize("&7  /zeyronac reinstall - Add missing fields to config, messages, menu, and holograms"));
        sender.sendMessage(ColorUtil.colorize("&7  /zeyronac kicklist [page] - List of kicks from AI anticheat"));
        sender.sendMessage(
                ColorUtil.colorize("&7  /zeyronac falsepositive restore <player> - Save 5000 ticks of player to CSV"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(filterStartsWith(new ArrayList<>(subCommands.keySet()), args[0]));
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (Arrays.asList("prob", "punish", "profile").contains(subCommand)) {
                List<String> targets = new ArrayList<>(getOnlinePlayerNames());
                completions.addAll(filterStartsWith(targets, args[1]));
            } else if (subCommand.equals("falsepositive")) {
                completions.add("restore");
            } else if (subCommand.equals("animation")) {
                completions.addAll(filterStartsWith(Arrays.asList("test", "list", "reload"), args[1]));
            } else if (subCommand.equals("record")) {
                completions.addAll(filterStartsWith(Arrays.asList("start", "stop", "status"), args[1]));
            }
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equalsIgnoreCase("falsepositive") && args[1].equalsIgnoreCase("restore")) {
                completions.addAll(filterStartsWith(getOnlinePlayerNames(), args[2]));
            } else if (subCommand.equals("punish")) {
                completions.addAll(filterStartsWith(List.of("confirm"), args[2]));
            } else if (subCommand.equals("animation") && args[1].equalsIgnoreCase("test")) {
                completions.addAll(filterStartsWith(getOnlinePlayerNames(), args[2]));
            } else if (subCommand.equals("record") && args[1].equalsIgnoreCase("start")) {
                completions.addAll(filterStartsWith(getOnlinePlayerNames(), args[2]));
            } else if (subCommand.equals("record") && args[1].equalsIgnoreCase("stop")) {
                List<String> targets = new ArrayList<>(getOnlinePlayerNames());
                targets.add("all");
                completions.addAll(filterStartsWith(targets, args[2]));
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("record") && args[1].equalsIgnoreCase("start")) {
                List<String> labels = Arrays.stream(Label.values())
                        .map(Label::name)
                        .collect(Collectors.toList());
                completions.addAll(filterStartsWith(labels, args[3]));
            } else if (args[0].equalsIgnoreCase("animation") && args[1].equalsIgnoreCase("test")) {
                AnimationManager animationManager = plugin.getAnimationManager();
                if (animationManager != null) {
                    completions.addAll(filterStartsWith(
                            new ArrayList<>(animationManager.getAvailableAnimations()),
                            args[3]));
                }
            }
        } else if (args.length == 5) {
            if (args[0].equalsIgnoreCase("record") && args[1].equalsIgnoreCase("start")
                    && (args[3].isEmpty() || args[3].startsWith("\""))) {
                completions.add("\"comment\"");
            }
        }
        return completions;
    }

    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        String lowerPrefix = prefix.toLowerCase();
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(lowerPrefix))
                .collect(Collectors.toList());
    }

    public void cleanup() {
        for (ScheduledTask task : probTasks.values()) {
            task.cancel();
        }
        probTasks.clear();
        probTracking.clear();
        reinstallConfirmations.clear();
    }

    private void handleStatus(CommandSender sender) {
        sender.sendMessage(ColorUtil.colorize("&8=== &7&lZeyronAC STATUS &8==="));

        // API Connection Status
        AIClientProvider provider = plugin.getAiClientProvider();
        IAIClient client = provider != null ? provider.get() : null;

        if (client == null) {
            sender.sendMessage(ColorUtil.colorize("&7API: &cNot initialized"));
        } else {
            boolean connected = client.isConnected();
            boolean limitExceeded = client.isLimitExceeded();
            boolean serverError = client.isServerErrorState();
            boolean stasisMode = client.isInStasisMode();

            String status;
            if (stasisMode) {
                status = "&eStasis Mode (Rate Limited)";
            } else if (connected && !limitExceeded && !serverError) {
                status = "&aConnected";
            } else if (limitExceeded) {
                status = "&eRate Limited";
            } else if (serverError) {
                status = "&cServer Error";
            } else {
                status = "&cDisconnected";
            }

            sender.sendMessage(ColorUtil.colorize("&7API: " + status));
            sender.sendMessage(ColorUtil.colorize(
                    "&7Backend: &f" + (client.getServerAddress() != null ? client.getServerAddress() : "N/A")));
        }

        // Daily Statistics
        DailyStats stats = plugin.getDailyStats();
        if (stats != null) {
            sender.sendMessage(ColorUtil.colorize("&7Today's Detections: &f" + stats.getTodayDetections()));
            sender.sendMessage(ColorUtil.colorize("&7Today's Requests: &f" + stats.getTodayRequests()));
        }

        // Latency Test
        if (client != null && client.isConnected() && !client.isInStasisMode()) {
            List<java.util.concurrent.CompletableFuture<Long>> latencyTests = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                latencyTests.add(client.measureLatency());
            }

            java.util.concurrent.CompletableFuture
                    .allOf(latencyTests.toArray(new java.util.concurrent.CompletableFuture[0]))
                    .thenAccept(v -> {
                        List<Long> results = new ArrayList<>();
                        for (java.util.concurrent.CompletableFuture<Long> future : latencyTests) {
                            try {
                                Long latency = future.get();
                                if (latency != null && latency > 0) {
                                    results.add(latency);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                        // The CompletableFuture callback runs off the main thread; route the
                        // resulting chat messages back onto the server thread before sending.
                        sendSync(sender, formatLatencyResult(results));
                    })
                    .exceptionally(ex -> {
                        sendSync(sender, ColorUtil.colorize("&7Average Latency: &cError during test"));
                        return null;
                    });
        } else {
            sender.sendMessage(ColorUtil.colorize("&7Average Latency: &cAPI not available"));
        }
    }

    private String formatLatencyResult(List<Long> results) {
        if (results.isEmpty()) {
            return ColorUtil.colorize("&7Average Latency: &cFailed to measure");
        }
        long sum = 0;
        for (Long latency : results) {
            sum += latency;
        }
        long avg = sum / results.size();

        String latencyColor;
        if (avg < 50) {
            latencyColor = "&a";
        } else if (avg < 150) {
            latencyColor = "&e";
        } else {
            latencyColor = "&c";
        }
        return ColorUtil.colorize("&7Average Latency: " + latencyColor + avg + "ms &7("
                + results.size() + "/5 successful)");
    }

    private void sendSync(CommandSender sender, String message) {
        SchedulerManager.getAdapter().runSync(() -> sender.sendMessage(message));
    }

    private void handleAnimation(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(getPrefix() + ColorUtil.colorize("&cUsage:"));
            sender.sendMessage(ColorUtil.colorize("&7  /zeyronac animation test <player> <animation> - Test animation"));
            sender.sendMessage(ColorUtil.colorize("&7  /zeyronac animation list - List available animations"));
            sender.sendMessage(ColorUtil.colorize("&7  /zeyronac animation reload - Reload animations"));
            return;
        }

        String subCmd = args[1].toLowerCase();

        switch (subCmd) {
            case "test":
                handleAnimationTest(sender, args);
                break;
            case "list":
                handleAnimationList(sender);
                break;
            case "reload":
                handleAnimationReload(sender);
                break;
            default:
                sender.sendMessage(getPrefix() + ColorUtil.colorize("&cUnknown subcommand: " + args[1]));
        }
    }

    private void handleAnimationTest(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(getPrefix() + ColorUtil.colorize("&cUsage: /zeyronac animation test <player> <animation>"));
            return;
        }

        String playerName = args[2];
        String animationName = args[3];

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage(getPrefix() + msg("player-not-found", "{PLAYER}", playerName));
            return;
        }

        AnimationManager animationManager = plugin.getAnimationManager();
        if (animationManager == null) {
            sender.sendMessage(getPrefix() + ColorUtil.colorize("&cAnimation Manager not initialized!"));
            return;
        }

        if (!animationManager.hasAnimation(animationName)) {
            sender.sendMessage(getPrefix() + ColorUtil.colorize("&cAnimation &f" + animationName + " &cnot found!"));
            sender.sendMessage(ColorUtil.colorize("&7Use &f/zeyronac animation list &7for a list of available animations."));
            return;
        }

        // Test command (not executed)
        String testCommand = "say " + target.getName() + " passed animation test " + animationName;

        boolean success = animationManager.playAnimation(target, animationName, testCommand);

        if (success) {
            sender.sendMessage(getPrefix() + ColorUtil.colorize("&aAnimation started &f" + animationName + " &afor player &f" + target.getName()));
            sender.sendMessage(ColorUtil.colorize("&7This is test mode - ban command will not be executed."));
        } else {
            sender.sendMessage(getPrefix() + ColorUtil.colorize("&cFailed to start animation!"));
        }
    }

    private void handleAnimationList(CommandSender sender) {
        AnimationManager animationManager = plugin.getAnimationManager();
        if (animationManager == null) {
            sender.sendMessage(getPrefix() + ColorUtil.colorize("&cAnimation Manager not initialized!"));
            return;
        }

        java.util.Set<String> animations = animationManager.getAvailableAnimations();

        sender.sendMessage(ColorUtil.colorize("&8=== &7&lAVAILABLE ANIMATIONS &8==="));
        sender.sendMessage(ColorUtil.colorize("&7Total animations: &f" + animations.size()));
        sender.sendMessage(ColorUtil.colorize("&7Current default: &a" + animationManager.getDefaultAnimationName()));
        sender.sendMessage(ColorUtil.colorize("&7─────────────────────────────────"));

        for (String name : animations) {
            boolean isDefault = name.equals(animationManager.getDefaultAnimationName());
            String prefix = isDefault ? "&a● " : "&7• ";
            sender.sendMessage(ColorUtil.colorize(prefix + "&f" + name + (isDefault ? " &7(default)" : "")));
        }

        sender.sendMessage(ColorUtil.colorize("&7─────────────────────────────────"));
        sender.sendMessage(ColorUtil.colorize("&7Use &f/zeyronac animation test <player> <animation> &7to test"));
    }

    private void handleAnimationReload(CommandSender sender) {
        AnimationManager animationManager = plugin.getAnimationManager();
        if (animationManager == null) {
            sender.sendMessage(getPrefix() + ColorUtil.colorize("&cAnimation Manager not initialized!"));
            return;
        }

        try {
            animationManager.reload();
            sender.sendMessage(getPrefix() + ColorUtil.colorize("&aAnimations successfully reloaded!"));
            sender.sendMessage(ColorUtil.colorize("&7Loaded animations: &f" + animationManager.getAvailableAnimations().size()));
        } catch (Exception e) {
            sender.sendMessage(getPrefix() + ColorUtil.colorize("&cError reloading animations!"));
            plugin.getLogger().severe("Failed to reload animations: " + e.getMessage());
        }
    }

    /** Immutable description of a sub-command and its access policy. */
    private static final class SubCommand {
        private final Set<String> anyPermission;
        private final boolean playersOnly;
        private final BiConsumer<CommandSender, String[]> handler;

        private SubCommand(Set<String> anyPermission, boolean playersOnly,
                BiConsumer<CommandSender, String[]> handler) {
            this.anyPermission = anyPermission;
            this.playersOnly = playersOnly;
            this.handler = handler;
        }
    }
}
