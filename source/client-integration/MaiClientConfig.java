package com.maiclient.api;

/**
 * Runtime config for MaiClient API endpoint.
 * Set via system property -Dmaiclient.api=https://your-api.example or env MAICLIENT_API.
 */
public final class MaiClientConfig {
    private MaiClientConfig() {}

    public static String apiBaseUrl() {
        String p = System.getProperty("maiclient.api");
        if (p != null && !p.isBlank()) return p.trim();
        String e = System.getenv("MAICLIENT_API");
        if (e != null && !e.isBlank()) return e.trim();
        return "http://127.0.0.1:8787";
    }

    public static boolean isConfigured() {
        String u = apiBaseUrl();
        return u != null && !u.isBlank();
    }
}
