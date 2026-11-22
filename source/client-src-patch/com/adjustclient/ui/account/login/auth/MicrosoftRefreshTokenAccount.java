/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  io.netty.bootstrap.ServerBootstrap
 *  io.netty.channel.Channel
 *  io.netty.channel.ChannelHandler
 *  io.netty.channel.ChannelOption
 *  io.netty.channel.EventLoopGroup
 *  io.netty.channel.nio.NioEventLoopGroup
 *  io.netty.channel.socket.nio.NioServerSocketChannel
 *  org.apache.commons.io.IOUtils
 */
package com.adjustclient.ui.account.login.auth;

import com.adjustclient.auth.LoginResult;
import com.adjustclient.auth.LoginStatus;
import com.adjustclient.auth.MicrosoftAuth;
import com.adjustclient.auth.MinecraftProfile;
import com.adjustclient.logging.LogLevel;
import com.adjustclient.logging.Logger;
import com.adjustclient.ui.account.AccountEntry;
import com.adjustclient.ui.account.AccountType;
import com.adjustclient.ui.account.login.auth.BrowserLoginChannelInitializer;
import com.adjustclient.ui.hud.notification.NotificationBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.awt.Desktop;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.util.Session;
import com.maiclient.api.MaiClientApi;
import com.maiclient.api.MaiClientConfig;
import com.maiclient.api.MaiClientSessionStore;
import com.maiclient.api.MaiClientAuthState;
import org.apache.commons.io.IOUtils;

public final class MicrosoftRefreshTokenAccount
extends AccountEntry {
    private static HttpServer browserServer;
    static Consumer<String> refreshTokenCallback;
    static int port;
    private static String codeVerifier;
    private static String oauthState;
    private static String redirectUri;
    private static final String CLIENT_ID = "eaca69b9-f4d8-4dab-92e0-c1e3f7b962dc";
    private boolean notBrowser;
    private String refreshToken;
    private Object field76;

    public MicrosoftRefreshTokenAccount(String refreshToken, boolean notBrowser, boolean flag) {
        this.refreshToken = refreshToken;
        this.notBrowser = notBrowser;
    }

    public MicrosoftRefreshTokenAccount() {
    }

    public static synchronized void stopServer() {
        if (browserServer != null) {
            Logger.log(LogLevel.INFO, true, "Ended HTTP browser server running on port {}", port);
            browserServer.stop(0);
            browserServer = null;
        }
        refreshTokenCallback = null;
        codeVerifier = null;
        oauthState = null;
        redirectUri = null;
        port = 0;
    }

    @Override
    public void deserialize(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        this.refreshToken = in.readUTF();
        if (this.refreshToken.startsWith("NOT_BROWSER@@")) {
            this.notBrowser = true;
            this.refreshToken = this.refreshToken.substring(13);
        }
    }

    @Override
    public AccountType getType() {
        return AccountType.BROWSER;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    @Override
    public LoginResult login() {
        try {
            String xblResponse;
            JsonObject msa;
            if (this.notBrowser) {
                String msaResponse = MicrosoftAuth.post("https://login.live.com/oauth20_token.srf", "client_id=00000000402b5328&refresh_token=" + this.refreshToken + "&grant_type=refresh_token&redirect_uri=https://login.live.com/oauth20_desktop.srf&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL", false);
                if (msaResponse == null) {
                    return new LoginResult(LoginStatus.FAILED);
                }
                msa = JsonParser.parseString((String)msaResponse).getAsJsonObject();
                String msaAccessToken = msa.get("access_token").getAsString();
                this.refreshToken = msa.get("refresh_token").getAsString();
                xblResponse = MicrosoftAuth.post("https://user.auth.xboxlive.com/user/authenticate", "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"t=" + msaAccessToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}", true);
                if (xblResponse == null) {
                    return new LoginResult(LoginStatus.FAILED);
                }
            } else {
                String msaResponse = postForm(
                    "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                    "client_id=" + form(CLIENT_ID)
                        + "&refresh_token=" + form(this.refreshToken)
                        + "&grant_type=refresh_token"
                        + "&scope=" + form("XboxLive.signin offline_access"));
                if (msaResponse == null) {
                    return new LoginResult(LoginStatus.FAILED);
                }
                msa = JsonParser.parseString((String)msaResponse).getAsJsonObject();
                String msaAccessToken = msa.get("access_token").getAsString();
                this.refreshToken = msa.get("refresh_token").getAsString();
                xblResponse = MicrosoftAuth.post("https://user.auth.xboxlive.com/user/authenticate", "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"d=" + msaAccessToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}", true);
                if (xblResponse == null) {
                    return new LoginResult(LoginStatus.FAILED);
                }
            }
            String xblToken = JsonParser.parseString((String)xblResponse).getAsJsonObject().get("Token").getAsString();
            String userHash = xblResponse.split("\"uhs\":\"")[1].split("\"")[0];
            String xstsResponse = MicrosoftAuth.post("https://xsts.auth.xboxlive.com/xsts/authorize", "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}", true);
            if (xstsResponse == null) {
                return new LoginResult(LoginStatus.FAILED);
            }
            String xstsToken = JsonParser.parseString((String)xstsResponse).getAsJsonObject().get("Token").getAsString();
            String mcResponse = MicrosoftAuth.post("https://api.minecraftservices.com/authentication/login_with_xbox", "{\"identityToken\":\"XBL3.0 x=" + userHash + ";" + xstsToken + "\"}", true);
            if (mcResponse == null) {
                return new LoginResult(LoginStatus.FAILED);
            }
            JsonObject mc = JsonParser.parseString((String)mcResponse).getAsJsonObject();
            if (!mc.has("access_token")) {
                return new LoginResult(null, LoginStatus.VALID_RATE_LIMITED);
            }
            String mcAccessToken = mc.get("access_token").getAsString();
            HttpURLConnection connection = (HttpURLConnection)URI.create("https://api.minecraftservices.com/minecraft/profile").toURL().openConnection();
            try {
                connection.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.connect();
                MinecraftProfile profile = (MinecraftProfile)new Gson().fromJson(IOUtils.toString((InputStream)connection.getInputStream(), (Charset)StandardCharsets.UTF_8), MinecraftProfile.class);
                try {
                    if (MaiClientConfig.isConfigured()) {
                        MaiClientSessionStore.setMinecraftProfileOk(profile.id, profile.name);
                        MaiClientApi api = new MaiClientApi(MaiClientConfig.apiBaseUrl());
                        MaiClientApi.LoginResponse mai = api.loginWithMinecraftToken(mcAccessToken);
                        MaiClientSessionStore.setMaiClientSession(mai.token, mai.expiresAtEpochMs,
                            mai.uuid != null ? mai.uuid : profile.id,
                            mai.name != null ? mai.name : profile.name);
                    }
                } catch (Exception maiEx) {
                    MaiClientSessionStore.setApiUnavailable();
                    System.err.println("[MaiClient] API authentication failed (not success): " + maiEx.getClass().getSimpleName());
                }
                LoginResult loginResult = new LoginResult(new Session(profile.name, profile.id, mcAccessToken, "mojang"), LoginStatus.SUCCESS);
                return loginResult;
            }
            catch (FileNotFoundException e) {
                Logger.debugStackTrace(e, "FileNotFound");
                if (MicrosoftAuth.ownsMinecraft(mcAccessToken)) {
                    LoginResult loginResult = new LoginResult(new Session(null, null, mcAccessToken, "mojang"), LoginStatus.NO_PROFILE);
                    return loginResult;
                }
                LoginResult loginResult = new LoginResult(new Session(null, null, mcAccessToken, "mojang"), LoginStatus.NO_MINECRAFT);
                return loginResult;
            }
            finally {
                connection.disconnect();
            }
        }
        catch (Exception e2) {
            Logger.debugStackTrace(e2, null);
            return new LoginResult(LoginStatus.FAILED);
        }
    }

    @Override
    public byte[] serialize() {
        Object value = this.notBrowser ? "NOT_BROWSER@@" + this.refreshToken : this.refreshToken;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            new DataOutputStream(bytes).writeUTF((String)value);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return bytes.toByteArray();
    }

    private static String form(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String postForm(String endpoint, String body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(endpoint).toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = stream == null ? "" : IOUtils.toString(stream, StandardCharsets.UTF_8);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            throw new IOException("Microsoft token endpoint returned HTTP " + status + ": " + response);
        }
        return response;
    }

    private static String randomUrlToken(int bytes) {
        byte[] value = new byte[bytes];
        new SecureRandom().nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String pkceChallenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> values = new HashMap<>();
        if (query == null || query.isEmpty()) return values;
        for (String pair : query.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            String value = equals < 0 ? "" : pair.substring(equals + 1);
            values.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return values;
    }

    private static void handleBrowserCallback(HttpExchange exchange) throws IOException {
        String response = "Microsoft sign-in did not complete. You can close this tab and retry in MaiClient.";
        int status = 400;
        try {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String error = query.get("error");
            if (error != null) {
                throw new IOException("Microsoft sign-in error: " + error + " " + query.get("error_description"));
            }
            String code = query.get("code");
            String state = query.get("state");
            if (code == null || !oauthState.equals(state)) {
                throw new IOException("Missing authorization code or invalid OAuth state.");
            }
            String tokenJson = postForm(
                "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                "client_id=" + form(CLIENT_ID)
                    + "&grant_type=authorization_code"
                    + "&code=" + form(code)
                    + "&redirect_uri=" + form(redirectUri)
                    + "&code_verifier=" + form(codeVerifier)
                    + "&scope=" + form("XboxLive.signin offline_access"));
            JsonObject tokens = JsonParser.parseString(tokenJson).getAsJsonObject();
            if (!tokens.has("refresh_token")) {
                throw new IOException("Microsoft did not return a refresh token.");
            }
            String refreshToken = tokens.get("refresh_token").getAsString();
            Consumer<String> callback = refreshTokenCallback;
            if (callback == null) throw new IOException("Login callback is no longer active.");
            callback.accept(refreshToken);
            response = "Microsoft sign-in completed. Return to MaiClient.";
            status = 200;
            Logger.log(LogLevel.INFO, true, "Microsoft browser sign-in completed.");
        } catch (Exception e) {
            Logger.log(LogLevel.ERROR, true, "Microsoft browser sign-in failed: {}", e.getMessage());
            response = "Microsoft sign-in failed: " + (e.getMessage() == null ? "unknown error" : e.getMessage())
                + ". Return to MaiClient and retry.";
        } finally {
            byte[] bytes = ("<!doctype html><html><head><meta charset=\"utf-8\"><title>MaiClient sign-in</title></head><body><h2>"
                + response + "</h2></body></html>").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
            stopServer();
        }
    }

    public static synchronized String startServer(Consumer<String> callback) {
        try {
            if (browserServer != null) stopServer();
            refreshTokenCallback = callback;
            codeVerifier = randomUrlToken(32);
            oauthState = randomUrlToken(24);
            browserServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            port = browserServer.getAddress().getPort();
            redirectUri = "http://localhost:" + port;
            browserServer.createContext("/", MicrosoftRefreshTokenAccount::handleBrowserCallback);
            browserServer.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread thread = new Thread(r, "MaiClient-Microsoft-OAuth");
                thread.setDaemon(true);
                return thread;
            }));
            browserServer.start();

            String authorizeUrl = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize"
                + "?client_id=" + form(CLIENT_ID)
                + "&response_type=code"
                + "&redirect_uri=" + form(redirectUri)
                + "&response_mode=query"
                + "&scope=" + form("XboxLive.signin offline_access")
                + "&code_challenge=" + form(pkceChallenge(codeVerifier))
                + "&code_challenge_method=S256"
                + "&state=" + form(oauthState)
                + "&prompt=select_account";

            Logger.log(LogLevel.INFO, true, "Microsoft OAuth callback listening on {}", redirectUri);
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(authorizeUrl));
                }
            } catch (Exception browserError) {
                Logger.log(LogLevel.WARN, true, "Could not auto-open browser; use copied login URL: {}", browserError.getMessage());
            }
            return authorizeUrl;
        } catch (Exception e) {
            stopServer();
            Logger.log(LogLevel.ERROR, true, "Unable to start Microsoft browser login: {}", e);
            Logger.debugStackTrace(e, null);
            new NotificationBuilder("Browser Account").format("Unable to start Microsoft login: {}", e).show();
            return null;
        }
    }
}

