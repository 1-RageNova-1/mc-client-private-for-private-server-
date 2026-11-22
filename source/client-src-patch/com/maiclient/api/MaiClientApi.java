package com.maiclient.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTPS client for MaiClient API.
 * Call only after successful Microsoft -> Minecraft Services login.
 * Sends Minecraft access token for server-side verification.
 * Never sends Microsoft refresh tokens.
 * Does not replace Microsoft authentication.
 */
public final class MaiClientApi {
    private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXPIRES = Pattern.compile("\"expiresAt\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern UUID = Pattern.compile("\"uuid\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private final String baseUrl;
    private final HttpClient http;

    public MaiClientApi(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl required");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    /**
     * @param minecraftAccessToken Minecraft Services bearer token only
     */
    public LoginResponse loginWithMinecraftToken(String minecraftAccessToken) throws Exception {
        if (minecraftAccessToken == null || minecraftAccessToken.isBlank()) {
            throw new IllegalArgumentException("minecraft access token required");
        }
        String body = "{\"accessToken\":\"" + jsonEscape(minecraftAccessToken) + "\"}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/auth/login"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new ApiException("login_failed HTTP " + res.statusCode());
        }
        String json = res.body();
        String token = first(TOKEN, json);
        String expiresAt = first(EXPIRES, json);
        String uuid = first(UUID, json);
        String name = first(NAME, json);
        if (token == null || token.isBlank()) {
            throw new ApiException("login_failed missing token");
        }
        long expMs = 0L;
        if (expiresAt != null) {
            try {
                expMs = Instant.parse(expiresAt).toEpochMilli();
            } catch (Exception ignored) {
                expMs = System.currentTimeMillis() + 24L * 3600_000L;
            }
        }
        return new LoginResponse(token, expMs, uuid, name);
    }

    public boolean validate(String maiToken) throws Exception {
        if (maiToken == null || maiToken.isBlank()) return false;
        String body = "{\"token\":\"" + jsonEscape(maiToken) + "\"}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/auth/validate"))
            .timeout(Duration.ofSeconds(8))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        return res.statusCode() >= 200 && res.statusCode() < 300 && res.body() != null && res.body().contains("\"valid\":true");
    }

    private static String first(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static final class LoginResponse {
        public final String token;
        public final long expiresAtEpochMs;
        public final String uuid;
        public final String name;
        public LoginResponse(String token, long expiresAtEpochMs, String uuid, String name) {
            this.token = token;
            this.expiresAtEpochMs = expiresAtEpochMs;
            this.uuid = uuid;
            this.name = name;
        }
    }

    public static final class ApiException extends Exception {
        public ApiException(String msg) { super(msg); }
    }
}
