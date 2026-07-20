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
import com.zeyronac.Main;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.function.IntSupplier;
import java.util.logging.Logger;

/**
 * Builds the JSON request bodies the HTTP client sends to the inference backend. Holds the static
 * server-identity context so the client no longer has to carry those fields or repeat the
 * "base payload + session + online count" boilerplate at every call site.
 */
final class PayloadFactory {
    private final Main plugin;
    private final String serverName;
    private final String serverFamily;
    private final boolean interServerEnabled;
    private final IntSupplier onlinePlayersSupplier;
    private final Logger logger;

    // Cache the resolved public IP so we don't make an HTTP request on every payload.
    private volatile String cachedPublicIp = null;
    private volatile long lastIpFetchTime = 0;
    private static final long IP_CACHE_TTL_MS = 300_000; // 5 minutes

    // Reuse a single HttpClient for all IP-echo requests (resource efficiency + TLS reuse).
    private static final HttpClient IP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final String[] IP_ECHO_SERVICES = {
        "https://api.ipify.org",
        "https://ifconfig.me/ip",
        "https://icanhazip.com",
    };

    PayloadFactory(Main plugin, String serverName, String serverFamily,
            boolean interServerEnabled, IntSupplier onlinePlayersSupplier) {
        this.plugin = plugin;
        this.serverName = normalize(serverName);
        this.serverFamily = normalize(serverFamily);
        this.interServerEnabled = interServerEnabled;
        this.onlinePlayersSupplier = onlinePlayersSupplier;
        this.logger = plugin.getLogger();
    }

    private static String normalize(String value) {
        return value != null && !value.trim().isEmpty() ? value.trim() : "default";
    }

    /** The common envelope shared by every request. */
    JsonObject base() {
        JsonObject json = new JsonObject();
        json.addProperty("pluginVersion", plugin.getDescription().getVersion());
        json.addProperty("interserverEventsSupported", true);
        json.addProperty("serverName", serverName);
        json.addProperty("serverFamily", serverFamily);
        json.addProperty("family", serverFamily);
        json.addProperty("serverIp", advertisedServerIp());
        json.addProperty("serverPort", plugin.getServer().getPort());
        json.addProperty("interServer", interServerEnabled);
        return json;
    }

    void addOnline(JsonObject json) {
        int online = onlinePlayersSupplier.getAsInt();
        json.addProperty("onlinePlayers", online);
        json.addProperty("onlineCount", online);
    }

    void addSession(JsonObject json, String sessionId) {
        if (sessionId != null) {
            json.addProperty("sessionId", sessionId);
        }
    }

    JsonObject predict(byte[] playerData, String playerUuid, String playerName, String sessionId) {
        JsonObject json = base();
        addSession(json, sessionId);
        json.addProperty("playerData", Base64.getEncoder().encodeToString(playerData));
        json.addProperty("playerUuid", playerUuid);
        json.addProperty("playerName", playerName);
        return json;
    }

    JsonObject event(String type, String playerUuid, String playerName, String model,
            double probability, double buffer, int violationLevel,
            String action, String command, String sessionId) {
        JsonObject json = base();
        addSession(json, sessionId);
        json.addProperty("type", type);
        json.addProperty("playerUuid", playerUuid);
        json.addProperty("playerId", playerUuid);
        json.addProperty("playerName", playerName);
        json.addProperty("model", model);
        json.addProperty("probability", probability);
        json.addProperty("buffer", buffer);
        json.addProperty("vl", violationLevel);
        json.addProperty("violationLevel", violationLevel);
        json.addProperty("action", action != null ? action : type);
        json.addProperty("command", command != null ? command : "");
        return json;
    }

    /**
     * Returns the server's public IP address.
     *
     * Pterodactyl containers always report 0.0.0.0 for Bukkit.getServer().getIp(), which is
     * useless for license validation. This method queries external IP echo services to find the
     * real public IP. The returned value is validated as a real IPv4/IPv6 literal (prevents log
     * injection and fake-IP bypass). The result is cached for 5 minutes to limit lookups.
     */
    private String advertisedServerIp() {
        // Try Bukkit configured IP first - if set to a real address, use it.
        String bukkitIp = plugin.getServer().getIp();
        if (bukkitIp != null && !bukkitIp.trim().isEmpty()
                && !bukkitIp.equals("0.0.0.0") && !bukkitIp.equals("0:0:0:0:0:0:0:0")) {
            return bukkitIp.trim();
        }

        // Use cached public IP if fresh
        long now = System.currentTimeMillis();
        if (cachedPublicIp != null && (now - lastIpFetchTime) < IP_CACHE_TTL_MS) {
            return cachedPublicIp;
        }

        // Fetch public IP from external services, validating the result.
        for (String service : IP_ECHO_SERVICES) {
            String ip = fetchAndValidateIp(service);
            if (ip != null) {
                cachedPublicIp = ip;
                lastIpFetchTime = now;
                return ip;
            }
        }

        // All services failed; return unknown (last known IP if any, otherwise unknown).
        return cachedPublicIp != null ? cachedPublicIp : "unknown";
    }

    /**
     * Fetches the public IP from one echo service and validates that the returned body is a
     * single, parseable IPv4/IPv6 literal. This prevents a malicious/compromised echo service
     * from injecting extra content (e.g. "1.2.3.4 X-Injected: x") into the serverIp field.
     */
    private String fetchAndValidateIp(String service) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(service))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .header("User-Agent", "ZeyronAC/1.0")
                    .build();
            HttpResponse<String> response = IP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (body == null) {
                return null;
            }
            // Take the first non-empty line only (some services append a trailing newline).
            String candidate = body.trim();
            int nl = candidate.indexOf('\n');
            if (nl >= 0) {
                candidate = candidate.substring(0, nl).trim();
            }
            if (candidate.isEmpty() || candidate.length() > 45) {
                return null;
            }
            // Guvenlik: sadece IP literal kabul et (hostname resolve ETME).
            // InetAddress.getByName hostname'leri de resolve eder → internal IP leak.
            // Bunun yerine strict IP literal validation yap.
            if (!isIpLiteral(candidate)) {
                if (logger != null && logger.isLoggable(java.util.logging.Level.FINE)) {
                    logger.fine("[PayloadFactory] IP echo returned non-literal: " + candidate);
                }
                return null;
            }
            try {
                InetAddress parsed = InetAddress.getByName(candidate);
                // getHostAddress() gives the canonical, normalized form (no IPv6 shorthand surprises).
                return parsed.getHostAddress();
            } catch (Exception e) {
                return null;
            }
        } catch (Exception e) {
            if (logger != null && logger.isLoggable(java.util.logging.Level.FINE)) {
                logger.fine("[PayloadFactory] IP echo " + service + " failed: " + e.getMessage());
            }
            return null;
        }
    }

    /**
     * Bir string'in strict bir IPv4 veya IPv6 literal'i olup olmadigini kontrol eder.
     * Hostname'leri (orn. metadata.google.internal) REDEDER — DNS resolve yapmaz.
     * Bu, kompromize olmus echo servislerinin internal IP leak etmesini onler.
     */
    private static boolean isIpLiteral(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        // IPv4: dotted quad, her octet 0-255
        String trimmed = s.trim();
        // IPv6 icin kolon icerir, IPv4 icin nokta
        if (trimmed.contains(":")) {
            // IPv6 literal dene
            try {
                // getByName yerine strict parse: IPv6 literal sadece
                InetAddress addr = InetAddress.getByName(trimmed);
                // Eger hostname ise getByName resolve eder ama addr.isSiteLocalAddress
                // gibi kontrol yapamayiz. Bunun yerine: literal ise toString
                // "/" icerir ve solda IP vardir. hostname ise solda hostname vardir.
                String hostAddr = addr.getHostAddress();
                // getHostAddress her zaman bir IP formatinda olur.
                // Ama hostname resolve edildiyse, addr.getHostName() hostname doner.
                // En guvenli: candidate'in kendisinin parse edilebilir oldugunu
                // InetAddress.getByName ile degil, manuel regex ile kontrol et.
                return trimmed.equals(hostAddr) || isLikelyIpv6Literal(trimmed);
            } catch (Exception e) {
                return isLikelyIpv6Literal(trimmed);
            }
        }
        // IPv4
        if (trimmed.contains(".")) {
            String[] parts = trimmed.split("\\.");
            if (parts.length != 4) {
                return false;
            }
            for (String p : parts) {
                if (p.isEmpty() || p.length() > 3) {
                    return false;
                }
                try {
                    int octet = Integer.parseInt(p);
                    if (octet < 0 || octet > 255) {
                        return false;
                    }
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static boolean isLikelyIpv6Literal(String s) {
        // Basit IPv6 heuristic: en az 2 kolon icerir ve sadece hex/kolon/nokta karakterleri
        long colons = s.chars().filter(c -> c == ':').count();
        if (colons < 2) {
            return false;
        }
        for (char c : s.toCharArray()) {
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
                    || c == ':' || c == '.' || c == '%')) {
                return false;
            }
        }
        return true;
    }
}
