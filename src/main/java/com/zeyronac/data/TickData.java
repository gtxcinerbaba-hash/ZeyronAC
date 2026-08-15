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


package com.zeyronac.data;
import java.util.Locale;
import java.util.StringJoiner;
public final class TickData {
    public final float deltaYaw;
    public final float deltaPitch;
    public final float accelYaw;
    public final float accelPitch;
    public final float jerkYaw;
    public final float jerkPitch;
    public final float gcdErrorYaw;
    public final float gcdErrorPitch;
    public final float targetPresent;
    public final float targetYawError;
    public final float targetPitchError;
    public final float targetDistance;
    public final float playerOnGround;
    public final float targetOnGround;
    public final float playerGliding;
    public final float targetGliding;
    public final float attackAge;
    public TickData(float deltaYaw, float deltaPitch, 
                    float accelYaw, float accelPitch,
                    float jerkYaw, float jerkPitch,
                    float gcdErrorYaw, float gcdErrorPitch) {
        this.deltaYaw = deltaYaw;
        this.deltaPitch = deltaPitch;
        this.accelYaw = accelYaw;
        this.accelPitch = accelPitch;
        this.jerkYaw = jerkYaw;
        this.jerkPitch = jerkPitch;
        this.gcdErrorYaw = gcdErrorYaw;
        this.gcdErrorPitch = gcdErrorPitch;
        this.targetPresent = 0.0f;
        this.targetYawError = -1.0f;
        this.targetPitchError = -1.0f;
        this.targetDistance = -1.0f;
        this.playerOnGround = 0.0f;
        this.targetOnGround = 0.0f;
        this.playerGliding = 0.0f;
        this.targetGliding = 0.0f;
        this.attackAge = -1.0f;
    }

    public TickData withRecordContext(RecordContext context, float yaw, float pitch, int attackAge) {
        if (context == null) return this;
        float yawError = context.targetPresent ? angleError(yaw, context.targetYaw) : -1.0f;
        float pitchError = context.targetPresent ? Math.abs(pitch - context.targetPitch) : -1.0f;
        return new TickData(this, context, yawError, pitchError, attackAge);
    }

    private TickData(TickData base, RecordContext context,
                     float yawError, float pitchError, int attackAge) {
        this.deltaYaw = base.deltaYaw;
        this.deltaPitch = base.deltaPitch;
        this.accelYaw = base.accelYaw;
        this.accelPitch = base.accelPitch;
        this.jerkYaw = base.jerkYaw;
        this.jerkPitch = base.jerkPitch;
        this.gcdErrorYaw = base.gcdErrorYaw;
        this.gcdErrorPitch = base.gcdErrorPitch;
        this.targetPresent = context.targetPresent ? 1.0f : 0.0f;
        this.targetYawError = yawError;
        this.targetPitchError = pitchError;
        this.targetDistance = context.targetDistance;
        this.playerOnGround = context.playerOnGround ? 1.0f : 0.0f;
        this.targetOnGround = context.targetOnGround ? 1.0f : 0.0f;
        this.playerGliding = context.playerGliding ? 1.0f : 0.0f;
        this.targetGliding = context.targetGliding ? 1.0f : 0.0f;
        this.attackAge = attackAge;
    }

    private static float angleError(float current, float target) {
        float delta = current - target;
        while (delta > 180.0f) delta -= 360.0f;
        while (delta < -180.0f) delta += 360.0f;
        return Math.abs(delta);
    }
    public static String getHeader() {
        return "is_cheating,delta_yaw,delta_pitch,accel_yaw,accel_pitch,jerk_yaw,jerk_pitch,"
            + "gcd_error_yaw,gcd_error_pitch,target_present,target_yaw_error,target_pitch_error,"
            + "target_distance,player_on_ground,target_on_ground,player_gliding,target_gliding,attack_age";
    }
    public String toCsv(String status) {
        int cheatingStatus = status.equalsIgnoreCase("CHEAT") ? 1 : 0;
        StringJoiner joiner = new StringJoiner(",");
        joiner.add(toBaseCsv(status));
        joiner.add(String.format(Locale.US, "%.6f", targetPresent));
        joiner.add(String.format(Locale.US, "%.6f", targetYawError));
        joiner.add(String.format(Locale.US, "%.6f", targetPitchError));
        joiner.add(String.format(Locale.US, "%.6f", targetDistance));
        joiner.add(String.format(Locale.US, "%.6f", playerOnGround));
        joiner.add(String.format(Locale.US, "%.6f", targetOnGround));
        joiner.add(String.format(Locale.US, "%.6f", playerGliding));
        joiner.add(String.format(Locale.US, "%.6f", targetGliding));
        joiner.add(String.format(Locale.US, "%.6f", attackAge));
        return joiner.toString();
    }

    public String toBaseCsv(String status) {
        int cheatingStatus = status.equalsIgnoreCase("CHEAT") ? 1 : 0;
        StringJoiner joiner = new StringJoiner(",");
        joiner.add(String.valueOf(cheatingStatus));
        joiner.add(String.format(Locale.US, "%.6f", deltaYaw));
        joiner.add(String.format(Locale.US, "%.6f", deltaPitch));
        joiner.add(String.format(Locale.US, "%.6f", accelYaw));
        joiner.add(String.format(Locale.US, "%.6f", accelPitch));
        joiner.add(String.format(Locale.US, "%.6f", jerkYaw));
        joiner.add(String.format(Locale.US, "%.6f", jerkPitch));
        joiner.add(String.format(Locale.US, "%.6f", gcdErrorYaw));
        joiner.add(String.format(Locale.US, "%.6f", gcdErrorPitch));
        return joiner.toString();
    }
    @Override
    public String toString() {
        return String.format("TickData[dYaw=%.4f, dPitch=%.4f, aYaw=%.4f, aPitch=%.4f, jYaw=%.4f, jPitch=%.4f, gcdYaw=%.4f, gcdPitch=%.4f]",
            deltaYaw, deltaPitch, accelYaw, accelPitch, jerkYaw, jerkPitch, gcdErrorYaw, gcdErrorPitch);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TickData)) return false;
        TickData other = (TickData) obj;
        return Float.compare(deltaYaw, other.deltaYaw) == 0
            && Float.compare(deltaPitch, other.deltaPitch) == 0
            && Float.compare(accelYaw, other.accelYaw) == 0
            && Float.compare(accelPitch, other.accelPitch) == 0
            && Float.compare(jerkYaw, other.jerkYaw) == 0
            && Float.compare(jerkPitch, other.jerkPitch) == 0
            && Float.compare(gcdErrorYaw, other.gcdErrorYaw) == 0
            && Float.compare(gcdErrorPitch, other.gcdErrorPitch) == 0;
    }
    @Override
    public int hashCode() {
        int result = Float.hashCode(deltaYaw);
        result = 31 * result + Float.hashCode(deltaPitch);
        result = 31 * result + Float.hashCode(accelYaw);
        result = 31 * result + Float.hashCode(accelPitch);
        result = 31 * result + Float.hashCode(jerkYaw);
        result = 31 * result + Float.hashCode(jerkPitch);
        result = 31 * result + Float.hashCode(gcdErrorYaw);
        result = 31 * result + Float.hashCode(gcdErrorPitch);
        return result;
    }
}
