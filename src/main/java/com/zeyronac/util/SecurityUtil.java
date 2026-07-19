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

package com.zeyronac.util;

import java.util.regex.Pattern;

/**
 * Input-validation helpers that guard command dispatch, chat output, and file paths against
 * hostile or malformed external data (player names, API payloads, inter-server events).
 */
public final class SecurityUtil {
    /**
     * Java-edition account names. Anything outside this set (spaces, Geyser/Floodgate prefixes,
     * cracked-server exotics) could smuggle extra arguments into a console command built by
     * string substitution, e.g. name {@code "Steve Notch"} in {@code kick {PLAYER} reason}
     * would kick a different player.
     */
    private static final Pattern SAFE_COMMAND_NAME = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");
    private static final Pattern UNSAFE_FILE_CHARS = Pattern.compile("[^a-zA-Z0-9_.-]");
    private static final int MAX_FILE_NAME_FRAGMENT = 32;

    private SecurityUtil() {
    }

    public static boolean isSafeCommandName(String name) {
        return name != null && SAFE_COMMAND_NAME.matcher(name).matches();
    }

    /**
     * Model probabilities are only meaningful in [0, 1]; NaN, Infinity or out-of-range values
     * mark a corrupt or tampered API response and must not reach the violation buffer.
     */
    public static boolean isValidProbability(double probability) {
        return !Double.isNaN(probability) && probability >= 0.0 && probability <= 1.0;
    }

    /** Collapses an externally controlled string into a safe file-name fragment (no separators, no {@code ..}). */
    public static String sanitizeFileName(String name) {
        if (name == null || name.isEmpty()) {
            return "unknown";
        }
        String cleaned = UNSAFE_FILE_CHARS.matcher(name).replaceAll("_");
        while (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", "_");
        }
        if (cleaned.length() > MAX_FILE_NAME_FRAGMENT) {
            cleaned = cleaned.substring(0, MAX_FILE_NAME_FRAGMENT);
        }
        return cleaned.isEmpty() ? "unknown" : cleaned;
    }

    /**
     * Strips control characters (C0/C1), RTL/LTR overrides, zero-width chars, BOM,
     * line/paragraph separators and other Unicode pitfalls that can spoof chat/console logs.
     */
    public static String sanitizeChatText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(text.length(), maxLength));
        for (int i = 0; i < text.length() && sb.length() < maxLength; i++) {
            char c = text.charAt(i);
            // Reject C0 (U+0000-U+001F), DEL (U+007F), C1 (U+0080-U+009F)
            if (c <= 0x1F || c == 0x7F || (c >= 0x80 && c <= 0x9F)) {
                continue;
            }
            // Reject Unicode hazards: BOM, zero-width, RTL/LTR overrides, separators
            if (c == 0xFEFF                            // BOM
                    || c == 0x200B                     // ZERO WIDTH SPACE
                    || c == 0x200C                     // ZERO WIDTH NON-JOINER
                    || c == 0x200D                     // ZERO WIDTH JOINER
                    || c == 0x200E                     // LEFT-TO-RIGHT MARK
                    || c == 0x200F                     // RIGHT-TO-LEFT MARK
                    || c == 0x2028                     // LINE SEPARATOR
                    || c == 0x2029                     // PARAGRAPH SEPARATOR
                    || c == 0x202A                     // LRE
                    || c == 0x202B                     // RLE
                    || c == 0x202C                     // PDF
                    || c == 0x202D                     // LRO
                    || c == 0x202E                     // RLO
                    || c == 0x2060                     // WORD JOINER
                    || c == 0x2061                     // FUNCTION APPLICATION
                    || c == 0x2062                     // INVISIBLE TIMES
                    || c == 0x2063) {                  // INVISIBLE SEPARATOR
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
