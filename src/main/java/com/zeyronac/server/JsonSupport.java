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

package com.zeyronac.server;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class JsonSupport {
    private JsonSupport() {
    }

    static String getString(JsonObject object, String key, String fallback) {
        try {
            if (object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key).getAsString();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    static double getDouble(JsonObject object, String key, double fallback) {
        try {
            if (object.has(key) && !object.get(key).isJsonNull()) {
                return object.get(key).getAsDouble();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    static AIResponse parsePredictResponse(String responseBody) {
        try {
            JsonObject json = new JsonParser().parse(responseBody).getAsJsonObject();
            double probability = json.has("probability") ? json.get("probability").getAsDouble() : 0.0;
            // M4: Backend kontrollu 'model' stringini sanitize + length-cap yap.
            // Serversel whitelist: sadece "fast"/"pro"/"ultra"/"experimental".
            // Bilinmeyen model = "unknown" (alert/chat'e zarar veremez).
            String model = json.has("model") ? json.get("model").getAsString() : null;
            if (model != null) {
                model = com.zeyronac.util.SecurityUtil.sanitizeChatText(model, 32);
                if (!model.matches("(?i)fast|pro|ultra|experimental")) {
                    model = "unknown";
                }
            }
            String error = json.has("error") ? json.get("error").getAsString() : null;

            if (error != null && !error.isEmpty()) {
                // M5: Backend kontrollu hata mesajini log enjeksiyonundan korumak icin
                // sanitize et ve uzunluk siniri koy (orn. cok satirli ANSI/log spoofing).
                String safe = com.zeyronac.util.SecurityUtil.sanitizeChatText(error, 256);
                throw new RuntimeException(safe);
            }

            return new AIResponse(probability, null, model);
        } catch (Exception e) {
            // M5: Tekrar sanitize - getAsString veya parse hatasi mesajini bilestirmeden once.
            String safe = com.zeyronac.util.SecurityUtil.sanitizeChatText(e.getMessage(), 256);
            throw new RuntimeException("Failed to parse response: " + safe);
        }
    }
}
