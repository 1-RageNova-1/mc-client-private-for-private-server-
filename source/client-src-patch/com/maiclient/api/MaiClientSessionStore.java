package com.maiclient.api;

/**
 * Holds MaiClient JWT only after successful API response.
 * Does not store Microsoft access/refresh tokens.
 */
public final class MaiClientSessionStore {
    private static volatile String token;
    private static volatile long expiresAtEpochMs;
    private static volatile String uuid;
    private static volatile String name;
    private static volatile MaiClientAuthState state = MaiClientAuthState.NONE;

    private MaiClientSessionStore() {}

    public static synchronized void setMicrosoftOk() {
        if (state == MaiClientAuthState.NONE || state == MaiClientAuthState.MAICLIENT_API_FAILED
                || state == MaiClientAuthState.MAICLIENT_API_UNAVAILABLE) {
            state = MaiClientAuthState.MICROSOFT_OK;
        }
    }

    public static synchronized void setMinecraftProfileOk(String uuid, String name) {
        state = MaiClientAuthState.MINECRAFT_PROFILE_OK;
        MaiClientSessionStore.uuid = uuid;
        MaiClientSessionStore.name = name;
        // clear any previous Mai session until API succeeds again
        token = null;
        expiresAtEpochMs = 0L;
    }

    public static synchronized void setMaiClientSession(String maiToken, long expiresAtEpochMs, String uuid, String name) {
        if (maiToken == null || maiToken.isBlank()) {
            state = MaiClientAuthState.MAICLIENT_API_FAILED;
            token = null;
            return;
        }
        token = maiToken;
        MaiClientSessionStore.expiresAtEpochMs = expiresAtEpochMs;
        if (uuid != null) MaiClientSessionStore.uuid = uuid;
        if (name != null) MaiClientSessionStore.name = name;
        state = MaiClientAuthState.MAICLIENT_API_OK;
    }

    public static synchronized void setApiFailed() {
        token = null;
        expiresAtEpochMs = 0L;
        state = MaiClientAuthState.MAICLIENT_API_FAILED;
    }

    public static synchronized void setApiUnavailable() {
        token = null;
        expiresAtEpochMs = 0L;
        state = MaiClientAuthState.MAICLIENT_API_UNAVAILABLE;
    }

    public static synchronized void clear() {
        token = null;
        expiresAtEpochMs = 0L;
        uuid = null;
        name = null;
        state = MaiClientAuthState.NONE;
    }

    public static synchronized MaiClientAuthState getState() {
        if (state == MaiClientAuthState.MAICLIENT_API_OK && token != null
                && expiresAtEpochMs > 0 && System.currentTimeMillis() > expiresAtEpochMs) {
            token = null;
            state = MaiClientAuthState.MAICLIENT_API_FAILED;
        }
        return state;
    }

    /** True only if MaiClient API issued a non-expired JWT. */
    public static synchronized boolean hasValidMaiClientSession() {
        return getState() == MaiClientAuthState.MAICLIENT_API_OK && getToken() != null;
    }

    public static synchronized String getToken() {
        if (token == null) return null;
        if (expiresAtEpochMs > 0 && System.currentTimeMillis() > expiresAtEpochMs) {
            token = null;
            if (state == MaiClientAuthState.MAICLIENT_API_OK) {
                state = MaiClientAuthState.MAICLIENT_API_FAILED;
            }
            return null;
        }
        return token;
    }

    public static synchronized String getUuid() { return uuid; }
    public static synchronized String getName() { return name; }
}
