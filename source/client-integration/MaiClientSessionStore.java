package com.maiclient.api;

/**
 * In-memory store for the MaiClient session JWT issued after Microsoft login.
 * Does not store Microsoft access/refresh tokens.
 */
public final class MaiClientSessionStore {
    private static volatile String token;
    private static volatile long expiresAtEpochMs;
    private static volatile String uuid;
    private static volatile String name;

    private MaiClientSessionStore() {}

    public static synchronized void set(String maiToken, long expiresAtEpochMs, String uuid, String name) {
        token = maiToken;
        MaiClientSessionStore.expiresAtEpochMs = expiresAtEpochMs;
        MaiClientSessionStore.uuid = uuid;
        MaiClientSessionStore.name = name;
    }

    public static synchronized void clear() {
        token = null;
        expiresAtEpochMs = 0L;
        uuid = null;
        name = null;
    }

    public static synchronized String getToken() {
        if (token == null) return null;
        if (expiresAtEpochMs > 0 && System.currentTimeMillis() > expiresAtEpochMs) {
            clear();
            return null;
        }
        return token;
    }

    public static synchronized boolean isValid() {
        return getToken() != null;
    }

    public static synchronized String getUuid() { return uuid; }
    public static synchronized String getName() { return name; }
}
