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

package com.zeyronac.penalty.engine;

import org.bukkit.Particle;
import org.bukkit.Sound;
import java.util.ArrayList;
import java.util.List;

/**
 * Data-driven configuration for ban animations.
 * Supports parallel stages, custom particle colors and sound effects.
 */
public class BanAnimationConfig {
    /** Total animation duration in ticks (20 ticks = 1 second) */
    public int totalTicks = 80;
    
    /** Whether to freeze the player (Slowness, Jump Boost, Levitation) */
    public boolean freezePlayer = true;
    
    /** Whether to strike lightning at the end of the animation */
    public boolean strikeLightningAtEnd = true;
    
    /** Strip the player (remove armor and drop items) */
    public boolean stripPlayer = false;
    
    /** Item drop interval in ticks */
    public int itemDropIntervalTicks = 5;
    
    /** Custom levitation (if -1, standard is used) */
    public int customLevitationAmplifier = -1;
    
    /** Custom player movement */
    public MovementConfig movement = null;
    
    /** List of animation stages (can run in parallel) */
    public List<StageConfig> stages = new ArrayList<>();
    
    /**
     * Configuration for player movement in space.
     */
    public static class MovementConfig {
        /** Start tick of movement */
        public int startTick = 0;
        
        /** End tick of movement */
        public int endTick = 0;
        
        /** X offset */
        public double offsetX = 0.0;
        
        /** Y offset */
        public double offsetY = 0.0;
        
        /** Z offset */
        public double offsetZ = 0.0;
        
        /** Smooth movement (interpolation) */
        public boolean smooth = true;
    }

    /**
     * Configuration of a single animation stage.
     * Stages can overlap in time to create complex effects.
     */
    public static class StageConfig {
        /** Stage name (for debugging) */
        public String name = "Unnamed Stage";
        
        /** Start tick of the stage (inclusive) */
        public int startTick = 0;
        
        /** End tick of the stage (inclusive) */
        public int endTick = 0;
        
        /** List of particle effects in this stage */
        public List<ParticleEffectConfig> particles = new ArrayList<>();
        
        /** List of sound effects in this stage */
        public List<SoundEffectConfig> sounds = new ArrayList<>();
        
        /** List of titles in this stage */
        public List<TitleConfig> titles = new ArrayList<>();
        
        /** Strike lightning at a specific tick of the stage */
        public List<Integer> lightningTicks = new ArrayList<>();
        
        /** Particle trail behind the player */
        public ParticleTrailConfig particleTrail = null;
    }

    /**
     * Configuration of particle effect with color and transform support.
     */
    public static class ParticleEffectConfig {
        /** Particle type (Bukkit Particle enum) */
        public Particle particleType = Particle.FLAME;
        
        /** Effect shape: POINT, RISING_SPIRAL, SPHERE, EXPLOSION, HELIX, CIRCLE */
        public String shape = "POINT";
        
        /** Interval between particle spawns (in ticks) */
        public int intervalTicks = 1;
        
        /** Number of particles per spawn */
        public int count = 1;

        // === TRANSFORMS (linear interpolation from start to end) ===
        /** Start radius of the effect */
        public double radiusStart = 1.0;
        
        /** End radius of the effect */
        public double radiusEnd = 1.0;
        
        /** Start height offset */
        public double heightOffsetStart = 0.0;
        
        /** End height offset */
        public double heightOffsetEnd = 0.0;

        // === COLOR AND SIZE ===
        /** Whether to use custom color (for REDSTONE/DUST particles) */
        public boolean isColored = false;
        
        /** Red color component (0-255) */
        public int red = 255;
        
        /** Green color component (0-255) */
        public int green = 255;
        
        /** Blue color component (0-255) */
        public int blue = 255;
        
        /** Particle size (for DUST particles) */
        public float particleSize = 1.0f;
        
        /** Particle speed */
        public double speed = 0.0;
        
        /** Particle spread on X */
        public double offsetX = 0.0;
        
        /** Particle spread on Y */
        public double offsetY = 0.0;
        
        /** Particle spread on Z */
        public double offsetZ = 0.0;
    }

    /**
     * Configuration of sound effect with looping support.
     */
    public static class SoundEffectConfig {
        /** Sound type (Bukkit Sound enum) */
        public Sound soundType = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        
        /** Playback tick relative to stage start */
        public int playAtTick = 0;
        
        /** Sound volume */
        public float volume = 1.0f;
        
        /** Sound pitch */
        public float pitch = 1.0f;
        
        /** Whether to loop the sound */
        public boolean loop = false;
        
        /** Loop interval (in ticks) */
        public int loopIntervalTicks = 20;
    }
    
    /**
     * Configuration of title on the player's screen.
     */
    public static class TitleConfig {
        /** Title show tick relative to stage start */
        public int showAtTick = 0;
        
        /** Title */
        public String title = "";
        
        /** Subtitle */
        public String subtitle = "";
        
        /** Fade-in time (in ticks) */
        public int fadeIn = 10;
        
        /** Display time (in ticks) */
        public int stay = 40;
        
        /** Fade-out time (in ticks) */
        public int fadeOut = 10;
    }
    
    /**
     * Configuration of particle trail behind the player.
     */
    public static class ParticleTrailConfig {
        /** Particle type */
        public Particle particleType = Particle.FLAME;
        
        /** Particle spawn interval (in ticks) */
        public int intervalTicks = 2;
        
        /** Number of particles per spawn */
        public int count = 3;
        
        /** Use custom color */
        public boolean isColored = false;
        
        /** Red color component (0-255) */
        public int red = 255;
        
        /** Green color component (0-255) */
        public int green = 255;
        
        /** Blue color component (0-255) */
        public int blue = 255;
        
        /** Particle size */
        public float particleSize = 1.0f;
        
        /** Particle spread */
        public double offsetX = 0.2;
        public double offsetY = 0.2;
        public double offsetZ = 0.2;
    }
}
