/*
 * Copyright (C) 2026 ZeyronAC Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * Design inspiration: Shard (GPLv3), https://github.com/KaelusAI/Shard
 * This file is an independent ZeyronAC implementation; no Shard source
 * code is copied into this file.
 */

package com.zeyronac.util;

import com.zeyronac.data.TickData;

/**
 * Low-risk, shadow-mode snap signal detector for normal ground combat.
 * It never punishes a player; it only reports repeated large rotation jumps.
 */
public final class SnapDetector {
    private static final float MIN_YAW_JUMP = 35.0f;
    private static final float MIN_PITCH_JUMP = 20.0f;
    private static final float MAX_YAW_JUMP = 160.0f;
    private static final float MAX_PITCH_JUMP = 85.0f;
    private static final int WINDOW_TICKS = 20;
    private static final int SIGNALS_REQUIRED = 3;
    private static final int LOG_COOLDOWN_TICKS = 100;

    private int ticksSinceSignal;
    private int signalCount;
    private int lastLogTick = -LOG_COOLDOWN_TICKS;
    private int tick;

    public void reset() {
        ticksSinceSignal = 0;
        signalCount = 0;
        lastLogTick = -LOG_COOLDOWN_TICKS;
        tick = 0;
    }

    public boolean record(TickData data, boolean eligible) {
        tick++;
        if (!eligible) {
            resetWindow();
            return false;
        }

        boolean largeYaw = Math.abs(data.deltaYaw) >= MIN_YAW_JUMP
                && Math.abs(data.deltaYaw) <= MAX_YAW_JUMP;
        boolean largePitch = Math.abs(data.deltaPitch) >= MIN_PITCH_JUMP
                && Math.abs(data.deltaPitch) <= MAX_PITCH_JUMP;
        boolean signal = largeYaw || largePitch;

        if (signal) {
            signalCount++;
            ticksSinceSignal = 0;
        } else {
            ticksSinceSignal++;
            if (ticksSinceSignal > WINDOW_TICKS) {
                signalCount = 0;
            }
        }

        if (signalCount >= SIGNALS_REQUIRED && tick - lastLogTick >= LOG_COOLDOWN_TICKS) {
            lastLogTick = tick;
            signalCount = 0;
            return true;
        }
        return false;
    }

    private void resetWindow() {
        ticksSinceSignal = 0;
        signalCount = 0;
    }
}
