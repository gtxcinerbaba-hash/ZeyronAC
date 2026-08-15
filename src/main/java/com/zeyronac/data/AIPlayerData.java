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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.zeyronac.util.AimProcessor;
import com.zeyronac.util.BufferCalculator;
import com.zeyronac.util.SnapDetector;
import com.zeyronac.util.RotationEvidenceDetector;

public class AIPlayerData {
    private final UUID playerId;
    private final AimProcessor aimProcessor;
    private final SnapDetector snapDetector;
    private final RotationEvidenceDetector rotationEvidenceDetector;
    private final Deque<TickData> tickBuffer;
    private final Deque<Double> probabilityHistory;
    private final Deque<ModelProbabilityEntry> modelProbabilityHistory;
    private final Map<String, Double> lastProbabilitiesByModel;
    private final Map<String, Deque<Double>> probabilityHistoryByModel;
    private int sequence;
    private int ticksSinceAttack;
    private int ticksStep;
    private volatile double buffer;
    private volatile boolean bufferIncreasing;
    private volatile double lastProbability;
    private volatile boolean isBedrock;
    private volatile int highProbabilityDetections;
    private volatile boolean lastSnapSignal;
    private volatile boolean lastRotationEvidenceSignal;
    private volatile UUID targetId;
    private volatile RecordContext recordContext = new RecordContext(
            false, 0, 0, -1, false, false, false, false);
    private final Deque<TickData> tickHistory = new ArrayDeque<>(5000);
    private static final int MAX_TICK_HISTORY = 5000;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, Boolean> bufferThresholdLatched = new HashMap<>();

    public AIPlayerData(UUID playerId) {
        this(playerId, 40);
    }

    public AIPlayerData(UUID playerId, int sequence) {
        this.playerId = playerId;
        this.sequence = sequence;
        this.aimProcessor = new AimProcessor();
        this.snapDetector = new SnapDetector();
        this.rotationEvidenceDetector = new RotationEvidenceDetector();
        this.tickBuffer = new ArrayDeque<>(sequence);
        this.probabilityHistory = new ArrayDeque<>(10);
        this.modelProbabilityHistory = new ArrayDeque<>(10);
        this.lastProbabilitiesByModel = new HashMap<>();
        this.probabilityHistoryByModel = new HashMap<>();
        this.ticksSinceAttack = sequence + 1;
        this.ticksStep = 0;
        this.buffer = 0.0;
        this.bufferIncreasing = false;
        this.lastProbability = 0.0;
        this.isBedrock = false;
        this.highProbabilityDetections = 0;
        this.lastSnapSignal = false;
        this.lastRotationEvidenceSignal = false;
    }

    public TickData processTick(float yaw, float pitch) {
        return processTick(yaw, pitch, true);
    }

    public TickData processTick(float yaw, float pitch, boolean snapEligible) {
        return processTick(yaw, pitch, snapEligible, recordContext);
    }

    public TickData processTick(float yaw, float pitch, boolean snapEligible,
                                RecordContext context) {
        lock.writeLock().lock();
        try {
            // aimProcessor shared state tutuyor — rotation (netty) ve main thread'den
            // parallel erisimi engellemek icin write lock icinde olmali.
            TickData tickData = aimProcessor.process(yaw, pitch)
                    .withRecordContext(context, yaw, pitch, ticksSinceAttack);
            if (tickBuffer.size() >= sequence) {
                tickBuffer.pollFirst();
            }
            tickBuffer.addLast(tickData);
            if (tickHistory.size() >= MAX_TICK_HISTORY) {
                tickHistory.pollFirst();
            }
            tickHistory.addLast(tickData);
            lastSnapSignal = snapDetector.record(tickData, snapEligible && isInCombatUnsafe());
            lastRotationEvidenceSignal = rotationEvidenceDetector.record(
                    tickData, snapEligible && isInCombatUnsafe());
            return tickData;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void onAttack(UUID targetId) {
        lock.writeLock().lock();
        try {
            this.targetId = targetId;
            this.ticksSinceAttack = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Main-thread snapshot; rotation packets only consume the immutable result. */
    public void updateRecordContext(Player attacker) {
        UUID currentTarget = targetId;
        Player target = currentTarget == null ? null : Bukkit.getPlayer(currentTarget);
        recordContext = RecordContext.capture(attacker, target);
    }

    public void onTeleport() {
        lock.writeLock().lock();
        try {
            aimProcessor.reset();
            targetId = null;
            recordContext = new RecordContext(false, 0, 0, -1, false, false, false, false);
            clearBuffer();
            snapDetector.reset();
            rotationEvidenceDetector.reset();
            lastSnapSignal = false;
            lastRotationEvidenceSignal = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void incrementTicksSinceAttack() {
        lock.writeLock().lock();
        try {
            if (this.ticksSinceAttack <= sequence + 1) {
                this.ticksSinceAttack++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void incrementStepCounter() {
        lock.writeLock().lock();
        try {
            this.ticksStep++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean shouldSendData(int step, int sequence) {
        lock.readLock().lock();
        try {
            // Shard-style: fire every `step` ticks once the rolling window is full, without
            // waiting for the previous request to return (no in-flight gate).
            return ticksStep >= step && tickBuffer.size() >= sequence;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void resetStepCounter() {
        lock.writeLock().lock();
        try {
            this.ticksStep = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<TickData> getTickBuffer() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(tickBuffer);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clearBuffer() {
        lock.writeLock().lock();
        try {
            tickBuffer.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void fullReset() {
        lock.writeLock().lock();
        try {
            tickBuffer.clear();
            tickHistory.clear();
            probabilityHistory.clear();
            modelProbabilityHistory.clear();
            lastProbabilitiesByModel.clear();
            probabilityHistoryByModel.clear();
            aimProcessor.reset();
            snapDetector.reset();
            rotationEvidenceDetector.reset();
            ticksSinceAttack = sequence + 1;
            ticksStep = 0;
            lastSnapSignal = false;
            lastRotationEvidenceSignal = false;
            bufferThresholdLatched.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void resetSequence(int newSequence) {
        lock.writeLock().lock();
        try {
            this.sequence = Math.max(1, newSequence);
            tickBuffer.clear();
            ticksSinceAttack = this.sequence + 1;
            ticksStep = 0;
            aimProcessor.reset();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isInCombat() {
        lock.readLock().lock();
        try {
            return ticksSinceAttack <= sequence;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getBufferSize() {
        lock.readLock().lock();
        try {
            return tickBuffer.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getSequence() {
        return sequence;
    }

    public int getTicksSinceAttack() {
        lock.readLock().lock();
        try {
            return ticksSinceAttack;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean consumeSnapSignal() {
        lock.writeLock().lock();
        try {
            boolean signal = lastSnapSignal;
            lastSnapSignal = false;
            return signal;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean consumeRotationEvidenceSignal() {
        lock.writeLock().lock();
        try {
            boolean signal = lastRotationEvidenceSignal;
            lastRotationEvidenceSignal = false;
            return signal;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean isInCombatUnsafe() {
        return ticksSinceAttack <= sequence;
    }

    public void updateBuffer(double probability, double multiplier, double decreaseAmount, double threshold, double decreaseThreshold) {
        updateBuffer(probability, null, multiplier, decreaseAmount, threshold, decreaseThreshold);
    }

    public void updateBuffer(double probability, String modelName, double multiplier, double decreaseAmount,
            double threshold, double decreaseThreshold) {
        lock.writeLock().lock();
        try {
            this.lastProbability = probability;
            if (probabilityHistory.size() >= 10) {
                probabilityHistory.pollFirst();
            }
            probabilityHistory.addLast(probability);
            String normalizedModel = normalizeModelName(modelName);
            if (modelProbabilityHistory.size() >= 10) {
                modelProbabilityHistory.pollFirst();
            }
            modelProbabilityHistory.addLast(new ModelProbabilityEntry(normalizedModel, probability));
            lastProbabilitiesByModel.put(normalizedModel, probability);
            Deque<Double> perModelHistory = probabilityHistoryByModel.computeIfAbsent(normalizedModel,
                    ignored -> new ArrayDeque<>(10));
            if (perModelHistory.size() >= 10) {
                perModelHistory.pollFirst();
            }
            perModelHistory.addLast(probability);
            if (probability > 0.8) {
                this.highProbabilityDetections++;
            }
            double previousBuffer = this.buffer;
            this.buffer = BufferCalculator.updateBuffer(buffer, probability, multiplier, decreaseAmount,
                    threshold, decreaseThreshold);
            if (this.buffer > previousBuffer) {
                this.bufferIncreasing = true;
            } else if (this.buffer < previousBuffer) {
                this.bufferIncreasing = false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Whether the last buffer update increased ({@code true}) or decreased ({@code false}) it. */
    public boolean isBufferIncreasing() {
        return bufferIncreasing;
    }

    public boolean shouldFlag(double flagThreshold) {
        lock.readLock().lock();
        try {
            return BufferCalculator.shouldFlag(buffer, flagThreshold);
        } finally {
            lock.readLock().unlock();
        }
    }

    public void resetBuffer(double resetValue) {
        lock.writeLock().lock();
        try {
            this.buffer = BufferCalculator.resetBuffer(resetValue);
            // Do NOT clear bufferThresholdLatched here: consumeBufferCrossing already auto-rearms
            // each latch the moment its threshold sees buffer < threshold, so any latch that stays
            // true (because resetValue is still above that threshold) correctly suppresses re-firing.
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Edge detector for buffer crossings. Returns {@code true} exactly once per upward crossing
     * of {@code threshold}; auto-rearms when the buffer drops back below.
     */
    public boolean consumeBufferCrossing(String key, double threshold) {
        lock.writeLock().lock();
        try {
            if (buffer < threshold) {
                bufferThresholdLatched.put(key, false);
                return false;
            }
            if (Boolean.TRUE.equals(bufferThresholdLatched.get(key))) {
                return false;
            }
            bufferThresholdLatched.put(key, true);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public double getBuffer() {
        lock.readLock().lock();
        try {
            return buffer;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getLastProbability() {
        return lastProbability;
    }

    public List<Double> getProbabilityHistory() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(probabilityHistory);
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getAverageProbability() {
        lock.readLock().lock();
        try {
            if (probabilityHistory.isEmpty()) {
                return 0.0;
            }
            return probabilityHistory.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getLastProbability(String modelName) {
        lock.readLock().lock();
        try {
            return lastProbabilitiesByModel.getOrDefault(normalizeModelName(modelName), 0.0D);
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getLastProbabilityContains(String substring) {
        lock.readLock().lock();
        try {
            String search = normalizeModelName(substring);
            for (Map.Entry<String, Double> entry : lastProbabilitiesByModel.entrySet()) {
                if (entry.getKey().contains(search)) {
                    return entry.getValue();
                }
            }
            return 0.0D;
        } finally {
            lock.readLock().unlock();
        }
    }

    public double getAverageProbability(String modelName) {
        lock.readLock().lock();
        try {
            Deque<Double> history = probabilityHistoryByModel.get(normalizeModelName(modelName));
            if (history == null || history.isEmpty()) {
                return 0.0D;
            }
            return history.stream().mapToDouble(Double::doubleValue).average().orElse(0.0D);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<ModelProbabilityEntry> getModelProbabilityHistory() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(modelProbabilityHistory);
        } finally {
            lock.readLock().unlock();
        }
    }

    private String normalizeModelName(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            return "unknown";
        }
        return modelName.trim().toLowerCase(Locale.ROOT);
    }

    public AimProcessor getAimProcessor() {
        return aimProcessor;
    }

    public boolean isBedrock() {
        return isBedrock;
    }

    public void setBedrock(boolean bedrock) {
        this.isBedrock = bedrock;
    }

    public List<TickData> getTickHistory() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(tickHistory);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getHighProbabilityDetections() {
        return highProbabilityDetections;
    }

    public static final class ModelProbabilityEntry {
        private final String modelName;
        private final double probability;

        public ModelProbabilityEntry(String modelName, double probability) {
            this.modelName = modelName;
            this.probability = probability;
        }

        public String getModelName() {
            return modelName;
        }

        public double getProbability() {
            return probability;
        }
    }
}
