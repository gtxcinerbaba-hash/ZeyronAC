/*
 * Copyright (C) 2026 ZeyronAC Team
 * ZeyronAC is a GPLv3 licensed fork of a Minecraft anti-cheat system.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zeyronac.data;

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

    public static final class PlayerState {
        public final boolean onGround;
        public final boolean gliding;

        public PlayerState(boolean onGround, boolean gliding) {
            this.onGround = onGround;
            this.gliding = gliding;
        }
    }
}
