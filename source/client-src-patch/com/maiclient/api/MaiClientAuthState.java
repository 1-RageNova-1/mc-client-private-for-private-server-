package com.maiclient.api;

/**
 * Explicit stages. Never conflate Microsoft success with MaiClient API success.
 */
public enum MaiClientAuthState {
    NONE,
    MICROSOFT_OK,
    MINECRAFT_PROFILE_OK,
    MAICLIENT_API_OK,
    MAICLIENT_API_FAILED,
    MAICLIENT_API_UNAVAILABLE
}
