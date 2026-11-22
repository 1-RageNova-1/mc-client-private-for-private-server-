/*
 * Decompiled with CFR 0.152.
 */
package com.maiclient.ui.account.login.auth;

import com.maiclient.auth.AuthApiException;
import com.maiclient.auth.Cookie;
import com.maiclient.auth.CookieFileParser;
import com.maiclient.auth.EntitlementsRequest;
import com.maiclient.auth.LoginResult;
import com.maiclient.auth.LoginStatus;
import com.maiclient.auth.LoginWithXboxRequest;
import com.maiclient.auth.LoginWithXboxResponse;
import com.maiclient.auth.MicrosoftCookieLogin;
import com.maiclient.auth.ProfileRequest;
import com.maiclient.auth.ProfileResponse;
import com.maiclient.auth.SisuLoginUrlRequest;
import com.maiclient.auth.XboxIdentity;
import com.maiclient.ui.account.AccountEntry;
import com.maiclient.ui.account.AccountType;
import com.maiclient.util.ByteUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.util.Session;
import com.maiclient.api.MaiClientApi;
import com.maiclient.api.MaiClientConfig;
import com.maiclient.api.MaiClientSessionStore;

public final class MicrosoftCookieAccount
extends AccountEntry {
    private static byte[] MAGIC = new byte[]{-69, 63, -84, -103, -17, 51};
    private String cookies;
    private Object field76;

    public MicrosoftCookieAccount(String cookies, boolean flag) {
        this.cookies = cookies;
    }

    public MicrosoftCookieAccount() {
    }

    @Override
    public void deserialize(byte[] data) throws IOException {
        this.cookies = MicrosoftCookieAccount.decode(data);
    }

    @Override
    public AccountType getType() {
        return AccountType.COOKIE;
    }

    @Override
    public LoginResult login() {
        String loginUrl = new SisuLoginUrlRequest().a();
        List<Cookie> cookieList = CookieFileParser.a(this.cookies);
        StringBuilder cookieHeader = new StringBuilder();
        for (int i = 0; i < cookieList.size(); ++i) {
            Cookie cookie = cookieList.get(i);
            cookieHeader.append(cookie.toString().trim());
            if (i == cookieList.size() - 1) continue;
            cookieHeader.append("; ");
        }
        String cookieString = cookieHeader.toString().trim();
        try {
            URI uri = URI.create(loginUrl);
            XboxIdentity identity = new MicrosoftCookieLogin(uri, cookieString).a();
            LoginWithXboxResponse response = new LoginWithXboxRequest("{\"identityToken\":\"" + identity.identityToken() + "\"}").a();
            if (response.accessToken == null) {
                return new LoginResult(LoginStatus.VALID_RATE_LIMITED);
            }
            String accessToken = response.accessToken;
            try {
                ProfileResponse profile = new ProfileRequest(accessToken, null).a();
                // MaiClient: after verified Microsoft/Minecraft identity, request MaiClient session.
                // Does not replace Microsoft login. Does not send refresh tokens.
                try {
                    if (MaiClientConfig.isConfigured()) {
                        MaiClientApi api = new MaiClientApi(MaiClientConfig.apiBaseUrl());
                        MaiClientApi.LoginResponse mai = api.loginWithMinecraftToken(accessToken);
                        MaiClientSessionStore.set(mai.token, mai.expiresAtEpochMs,
                            mai.uuid != null ? mai.uuid : profile.id,
                            mai.name != null ? mai.name : profile.name);
                    }
                } catch (Exception maiEx) {
                    // API failure must not destroy Microsoft session; log lightly, continue.
                    System.err.println("[MaiClient] API session issue failed: " + maiEx.getMessage());
                }
                return new LoginResult(new Session(profile.name, profile.id, accessToken, "mojang"), LoginStatus.SUCCESS);
            }
            catch (AuthApiException e) {
                if (new EntitlementsRequest(accessToken).a().valid()) {
                    return new LoginResult(new Session(null, null, accessToken, "mojang"), LoginStatus.NO_PROFILE);
                }
                return new LoginResult(new Session(null, null, accessToken, "mojang"), LoginStatus.NO_MINECRAFT);
            }
        }
        catch (IllegalArgumentException e) {
            try {
                return new LoginResult(LoginStatus.FAILED);
            }
            catch (IllegalArgumentException e2) {
                return new LoginResult(LoginStatus.RATE_LIMITED);
            }
        }
    }

    public static String decode(byte[] data) throws IOException {
        byte[] raw;
        for (int i = 0; i < MAGIC.length; ++i) {
            if (data[i] == MAGIC[i]) continue;
            return MicrosoftCookieAccount.decodeLegacy(data);
        }
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data, MAGIC.length, data.length - MAGIC.length));){
            raw = in.readAllBytes();
        }
        return new String(raw, StandardCharsets.UTF_16BE);
    }

    @Override
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(MAGIC, 0, MAGIC.length);
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes);){
            gzip.write(this.cookies.getBytes(StandardCharsets.UTF_16BE));
        }
        return bytes.toByteArray();
    }

    private static String decodeLegacy(byte[] data) throws IOException {
        int extraLength;
        int textLength = ByteUtil.readInt(data, 0);
        if (data.length != textLength + (extraLength = ByteUtil.readInt(data, 4)) + 8) {
            throw new IOException(textLength + " " + extraLength + " != " + data.length);
        }
        return new String(data, 8, textLength, StandardCharsets.UTF_16BE);
    }
}

