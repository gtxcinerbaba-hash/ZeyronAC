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
 * Shadow-only behavioral evidence collector for normal ground combat.
 *
 * It combines several weak rotation signals over a window instead of treating
 * one fast turn as a cheat. It deliberately never punishes or changes the AI
 * payload; the signal is only useful for debug logs and later evaluation.
 */
public final class RotationEvidenceDetector {
    private static final int WINDOW_TICKS = 40;
    private static final int REQUIRED_SCORE = 9;
    private static final int COOLDOWN_TICKS = 120;

    private int windowTicks;
    private int score;
    private int cooldown;

    public void reset() {
        windowTicks = 0;
        score = 0;
        cooldown = 0;
    }

    public boolean record(TickData data, boolean eligible) {
        if (!eligible) {
            reset();
            return false;
        }

        windowTicks++;
        if (cooldown > 0) {
            cooldown--;
        }

        int tickScore = 0;
        float yaw = Math.abs(data.deltaYaw);
        float pitch = Math.abs(data.deltaPitch);
        float yawJerk = Math.abs(data.jerkYaw);
        float pitchJerk = Math.abs(data.jerkPitch);

        // A single fast turn is common. It becomes evidence only when paired
        // with another independent property in the same rolling window.
        if ((yaw >= 25.0f && yaw <= 165.0f) || (pitch >= 15.0f && pitch <= 85.0f)) {
            tickScore += 2;
        }
        if (yawJerk >= 18.0f || pitchJerk >= 12.0f) {
            tickScore += 2;
        }
        if (yaw > 2.0f && yaw < 35.0f && Math.abs(data.accelYaw) < 1.25f) {
            tickScore += 1;
        }
        if (data.gcdErrorYaw < 0.02f || data.gcdErrorPitch < 0.02f) {
            tickScore += 1;
        }

        score += tickScore;
        if (windowTicks >= WINDOW_TICKS) {
            windowTicks = WINDOW_TICKS / 2;
            score = Math.max(0, score / 2);
        }

        if (score >= REQUIRED_SCORE && cooldown == 0) {
            score = 0;
            cooldown = COOLDOWN_TICKS;
            return true;
        }
        return false;
    }
}
