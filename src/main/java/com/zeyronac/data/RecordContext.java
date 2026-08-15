/*
 * Copyright (C) 2026 ZeyronAC Team
 * ZeyronAC is a GPLv3 licensed fork of a Minecraft anti-cheat system.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zeyronac.data;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Main-thread snapshot used only by the offline record collector.
 * Bukkit entity state is never read from the packet thread.
 */
public final class RecordContext {
    public final boolean targetPresent;
    public final float targetYaw;
    public final float targetPitch;
    public final float targetDistance;
    public final boolean playerOnGround;
    public final boolean targetOnGround;
    public final boolean playerGliding;
    public final boolean targetGliding;

    public RecordContext(boolean targetPresent, float targetYaw, float targetPitch,
                         float targetDistance, boolean playerOnGround,
                         boolean targetOnGround, boolean playerGliding,
                         boolean targetGliding) {
        this.targetPresent = targetPresent;
        this.targetYaw = targetYaw;
        this.targetPitch = targetPitch;
        this.targetDistance = targetDistance;
        this.playerOnGround = playerOnGround;
        this.targetOnGround = targetOnGround;
        this.playerGliding = playerGliding;
        this.targetGliding = targetGliding;
    }

    public static RecordContext empty(PlayerState playerState) {
        return new RecordContext(false, 0, 0, -1,
                playerState.onGround, false, playerState.gliding, false);
    }

    public static RecordContext capture(Player attacker, Player target) {
        PlayerState playerState = new PlayerState(attacker.isOnGround(), attacker.isGliding());
        if (target == null || !target.isOnline()
                || !attacker.getWorld().equals(target.getWorld())) {
            return empty(playerState);
        }
        Location from = attacker.getEyeLocation();
        Location to = target.getEyeLocation();
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) Math.toDegrees(Math.atan2(-dy, horizontal));
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new RecordContext(true, targetYaw, targetPitch, distance,
                playerState.onGround, target.isOnGround(),
                playerState.gliding, target.isGliding());
    }

    public static final class PlayerState {
        public final boolean onGround;
        public final boolean gliding;

        public PlayerState(boolean onGround, boolean gliding) {
            this.onGround = onGround;
            this.gliding = gliding;
        }
    }
}
