/*
 * Copyright (C) 2026 ZeyronAC Team
 * ZeyronAC is a GPLv3 licensed fork of a Minecraft anti-cheat system.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.zeyronac.data;

import java.util.Locale;
import java.util.StringJoiner;

/** Rotation tick plus target/movement context for offline training records. */
public final class RecordedTickData {
    private final TickData rotation;
    private final RecordContext context;
    private final float yaw;
    private final float pitch;
    private final int attackAge;

    public RecordedTickData(TickData rotation, RecordContext context,
                            float yaw, float pitch, int attackAge) {
        this.rotation = rotation;
        this.context = context;
        this.yaw = yaw;
        this.pitch = pitch;
        this.attackAge = attackAge;
    }

    public static String getHeader() {
        return TickData.getHeader().replace("gcd_error_pitch", "gcd_error_pitch")
            + ",target_present,target_yaw_error,target_pitch_error,target_distance"
            + ",player_on_ground,target_on_ground,player_gliding,target_gliding,attack_age";
    }

    public String toCsv(String status) {
        StringJoiner joiner = new StringJoiner(",");
        joiner.add(rotation.toCsv(status));
        if (!context.targetPresent) {
            joiner.add("0").add("-1").add("-1").add("-1");
        } else {
            joiner.add("1");
            joiner.add(format(angleError(yaw, context.targetYaw)));
            joiner.add(format(Math.abs(pitch - context.targetPitch)));
            joiner.add(format(context.targetDistance));
        }
        joiner.add(context.playerOnGround ? "1" : "0");
        joiner.add(context.targetOnGround ? "1" : "0");
        joiner.add(context.playerGliding ? "1" : "0");
        joiner.add(context.targetGliding ? "1" : "0");
        joiner.add(String.valueOf(attackAge));
        return joiner.toString();
    }

    private static float angleError(float current, float target) {
        float delta = current - target;
        while (delta > 180) delta -= 360;
        while (delta < -180) delta += 360;
        return Math.abs(delta);
    }

    private static String format(float value) {
        return String.format(Locale.US, "%.6f", value);
    }
}
