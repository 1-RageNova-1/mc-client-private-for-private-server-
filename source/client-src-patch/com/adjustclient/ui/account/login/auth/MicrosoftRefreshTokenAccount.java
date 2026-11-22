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
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import net.minecraft.util.Session;
import com.maiclient.api.MaiClientApi;
import com.maiclient.api.MaiClientConfig;
import com.maiclient.api.MaiClientSessionStore;
import com.maiclient.api.MaiClientAuthState;
import org.apache.commons.io.IOUtils;

public final class MicrosoftRefreshTokenAccount
extends AccountEntry {
    private static NioEventLoopGroup bossGroup;
    private static NioEventLoopGroup workerGroup;
    static Consumer<String> refreshTokenCallback;
    static int port;
    private static final String CLIENT_SECRET = "<redacted>";
    private boolean notBrowser;
    private String refreshToken;
    private Object field76;

    public MicrosoftRefreshTokenAccount(String refreshToken, boolean notBrowser, boolean flag) {
        this.refreshToken = refreshToken;
        this.notBrowser = notBrowser;
    }

    public MicrosoftRefreshTokenAccount() {
    }

    public static void stopServer() {
        if (bossGroup == null) {
            return;
        }
        Logger.log(LogLevel.INFO, true, "Ended HTTP browser server running on port {}", port);
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        bossGroup = null;
        workerGroup = null;
        refreshTokenCallback = null;
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
                String msaResponse = MicrosoftAuth.post("https://login.live.com/oauth20_token.srf", "client_id=c4f0db78-5015-4a94-8b34-1a4da87b9ce4&client_secret=<redacted>&refresh_token=" + this.refreshToken + "&grant_type=refresh_token&redirect_uri=http://localhost:" + port, false);
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

    public static String startServer(Consumer<String> callback) {
        boolean running;
        boolean bl = running = bossGroup != null && workerGroup != null;
        if (!running) {
            try {
                refreshTokenCallback = callback;
                bossGroup = new NioEventLoopGroup();
                workerGroup = new NioEventLoopGroup();
                ServerBootstrap bootstrap = new ServerBootstrap();
                ((ServerBootstrap)((ServerBootstrap)bootstrap.group((EventLoopGroup)bossGroup, (EventLoopGroup)workerGroup).channel(NioServerSocketChannel.class)).option(ChannelOption.SO_BACKLOG, Integer.valueOf(128))).childOption(ChannelOption.SO_KEEPALIVE, Boolean.TRUE).childHandler((ChannelHandler)new BrowserLoginChannelInitializer());
                Channel channel = bootstrap.bind((SocketAddress)new InetSocketAddress(0)).sync().channel();
                port = ((InetSocketAddress)channel.localAddress()).getPort();
            }
            catch (Exception e) {
                MicrosoftRefreshTokenAccount.stopServer();
                Logger.log(LogLevel.ERROR, true, "Unable to start HTTP browser server: {}", e);
                Logger.debugStackTrace(e, null);
                new NotificationBuilder("Browser Account").format("Unable to start HTTP server: {}", e).show();
            }
        }
        if (port < 1) {
            return null;
        }
        Logger.log(LogLevel.INFO, true, (running ? "Running" : "Starting") + " HTTP browser server on port {}", port);
        return "https://login.live.com/oauth20_authorize.srf?client_id=c4f0db78-5015-4a94-8b34-1a4da87b9ce4&client_secret=<redacted>&response_type=code&redirect_uri=http://localhost:" + port + "&scope=XboxLive.signin%20offline_access";
    }
}

