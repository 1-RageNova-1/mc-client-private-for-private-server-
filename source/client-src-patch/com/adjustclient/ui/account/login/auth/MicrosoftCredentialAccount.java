/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  org.apache.commons.io.IOUtils
 */
package com.adjustclient.ui.account.login.auth;

import com.adjustclient.auth.LoginResult;
import com.adjustclient.auth.LoginStatus;
import com.adjustclient.auth.MicrosoftAuth;
import com.adjustclient.auth.MinecraftProfile;
import com.adjustclient.logging.Logger;
import com.adjustclient.ui.account.AccountEntry;
import com.adjustclient.ui.account.AccountType;
import com.adjustclient.util.ByteUtil;
import com.adjustclient.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.util.Session;
import com.maiclient.api.MaiClientApi;
import com.maiclient.api.MaiClientConfig;
import com.maiclient.api.MaiClientSessionStore;
import com.maiclient.api.MaiClientAuthState;
import org.apache.commons.io.IOUtils;

public final class MicrosoftCredentialAccount
extends AccountEntry {
    private String email;
    private String password;
    private Object field80;

    public MicrosoftCredentialAccount(String email, String password, boolean flag) {
        this.email = email;
        this.password = password;
    }

    public MicrosoftCredentialAccount() {
    }

    @Override
    public void deserialize(byte[] data) throws IOException {
        String[] parts = MicrosoftCredentialAccount.decode(data);
        this.email = parts[0];
        this.password = parts[1];
    }

    @Override
    public AccountType getType() {
        return AccountType.CREDENTIALS;
    }

    @Override
    public LoginResult login() {
        return MicrosoftCredentialAccount.login(this.email, this.password);
    }

    @Override
    public byte[] serialize() {
        byte[] emailBytes = this.email.getBytes(StandardCharsets.UTF_16BE);
        byte[] passwordBytes = this.password.getBytes(StandardCharsets.UTF_16BE);
        byte[] out = new byte[emailBytes.length + passwordBytes.length + 8];
        out[0] = (byte)(emailBytes.length >>> 24);
        out[1] = (byte)(emailBytes.length >>> 16);
        out[2] = (byte)(emailBytes.length >>> 8);
        out[3] = (byte)emailBytes.length;
        out[4] = (byte)(passwordBytes.length >>> 24);
        out[5] = (byte)(passwordBytes.length >>> 16);
        out[6] = (byte)(passwordBytes.length >>> 8);
        out[7] = (byte)passwordBytes.length;
        System.arraycopy(emailBytes, 0, out, 8, emailBytes.length);
        System.arraycopy(passwordBytes, 0, out, 8 + emailBytes.length, passwordBytes.length);
        return out;
    }

    public static String[] decode(byte[] data) throws IOException {
        int passwordLength;
        int emailLength = ByteUtil.readInt(data, 0);
        if (data.length != emailLength + (passwordLength = ByteUtil.readInt(data, 4)) + 8) {
            throw new IOException(emailLength + " " + passwordLength + " != " + data.length);
        }
        String email = new String(data, 8, emailLength, StandardCharsets.UTF_16BE);
        String password = new String(data, 8 + emailLength, passwordLength, StandardCharsets.UTF_16BE);
        return new String[]{email, password};
    }

    private static String extract(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private static LoginResult login(String email, String password) {
        CookieHandler previousCookieHandler = CookieHandler.getDefault();
        CookieHandler.setDefault(new CookieManager());
        try {
            String accessTokenFragment;
            String urlPost;
            String userAgent = "Mozilla/5.0 (XboxReplay; XboxLiveAuth/3.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/71.0.3578.98 Safari/537.36";
            HttpURLConnection authorize = (HttpURLConnection)URI.create("https://login.live.com/oauth20_authorize.srf?client_id=00000000402B5328&redirect_uri=https://login.live.com/oauth20_desktop.srf&scope=service::user.auth.xboxlive.com::MBI_SSL&display=touch&response_type=token&locale=en").toURL().openConnection();
            authorize.setRequestProperty("User-Agent", userAgent);
            authorize.setConnectTimeout(5000);
            authorize.setReadTimeout(5000);
            authorize.connect();
            String page = IOUtils.toString((InputStream)authorize.getInputStream(), (Charset)StandardCharsets.UTF_8);
            String ppft = StringUtil.firstGroup("value=\\\\\\\"(.+?)\\\\\\\"", page);
            if (ppft == null) {
                ppft = StringUtil.firstGroup("value=\"(.+?)\"", page);
            }
            if ((urlPost = StringUtil.firstGroup("\"urlPost\":\"(.+?)\"", page)) == null) {
                urlPost = StringUtil.firstGroup("urlPost:'(.+?)'", page);
            }
            if (ppft == null || urlPost == null) {
                CookieHandler.setDefault(previousCookieHandler);
                return new LoginResult(LoginStatus.FAILED);
            }
            String loginBody = "loginfmt=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&passwd=" + URLEncoder.encode(password, StandardCharsets.UTF_8) + "&PPFT=" + URLEncoder.encode(ppft, StandardCharsets.UTF_8) + "&login=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
            HttpURLConnection post = (HttpURLConnection)URI.create(urlPost).toURL().openConnection();
            post.setRequestMethod("POST");
            post.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            post.setDoOutput(true);
            post.getOutputStream().write(loginBody.getBytes(StandardCharsets.UTF_8));
            post.setInstanceFollowRedirects(false);
            post.setConnectTimeout(5000);
            post.setReadTimeout(5000);
            post.connect();
            String location = post.getHeaderField("Location");
            if (location == null) {
                String answer = IOUtils.toString((InputStream)post.getInputStream(), (Charset)StandardCharsets.UTF_8);
                if (answer.contains("Abuse?mkt=")) {
                    return new LoginResult(LoginStatus.ABUSE_LOCKED);
                }
                if (answer.contains("recover?mkt=")) {
                    return new LoginResult(LoginStatus.RECOVER_LOCKED);
                }
                if (!answer.contains("cancel?mkt=")) {
                    return new LoginResult(LoginStatus.FAILED);
                }
                String ipt = MicrosoftCredentialAccount.extract(answer, "(?<=\"ipt\" value=\").+?(?=\">)");
                String pprid = MicrosoftCredentialAccount.extract(answer, "(?<=\"pprid\" value=\").+?(?=\">)");
                String uaid = MicrosoftCredentialAccount.extract(answer, "(?<=\"uaid\" value=\").+?(?=\">)");
                String action = MicrosoftCredentialAccount.extract(answer, "(?<=id=\"fmHF\" action=\").+?(?=\" )");
                if (ipt == null) return new LoginResult(LoginStatus.FAILED);
                if (pprid == null) return new LoginResult(LoginStatus.FAILED);
                if (uaid == null) return new LoginResult(LoginStatus.FAILED);
                if (action == null) {
                    return new LoginResult(LoginStatus.FAILED);
                }
                String cancelBody = "ipt=" + ipt + "&pprid=" + pprid + "&uaid=" + uaid;
                HttpURLConnection cancel = (HttpURLConnection)URI.create(action).toURL().openConnection();
                cancel.setRequestMethod("POST");
                cancel.setDoOutput(true);
                cancel.getOutputStream().write(cancelBody.getBytes(StandardCharsets.UTF_8));
                cancel.setInstanceFollowRedirects(false);
                cancel.setConnectTimeout(5000);
                cancel.setReadTimeout(5000);
                cancel.connect();
                String cancelAnswer = IOUtils.toString((InputStream)cancel.getInputStream(), (Charset)StandardCharsets.UTF_8);
                String returnUrl = MicrosoftCredentialAccount.extract(cancelAnswer, "(?<=\"recoveryCancel\":\\{\"returnUrl\":\").+?(?=\",)");
                if (returnUrl == null) {
                    return new LoginResult(LoginStatus.FAILED);
                }
                HttpURLConnection finish = (HttpURLConnection)URI.create(returnUrl).toURL().openConnection();
                finish.setRequestMethod("GET");
                finish.setDoOutput(true);
                finish.getOutputStream().write(cancelBody.getBytes(StandardCharsets.UTF_8));
                finish.setInstanceFollowRedirects(false);
                finish.setConnectTimeout(5000);
                finish.setReadTimeout(5000);
                finish.connect();
                String finishLocation = finish.getHeaderField("Location");
                if (finishLocation == null) {
                    return new LoginResult(LoginStatus.FAILED);
                }
                accessTokenFragment = StringUtil.firstGroup("access_token=([^&]*)", finishLocation);
                if (accessTokenFragment == null) {
                    return new LoginResult(LoginStatus.FAILED);
                }
            } else {
                accessTokenFragment = StringUtil.firstGroup("access_token=([^&]*)", location);
                if (accessTokenFragment == null) {
                    return new LoginResult(LoginStatus.FAILED);
                }
            }
            String msaToken = URLDecoder.decode(accessTokenFragment, StandardCharsets.UTF_8);
            CookieHandler.setDefault(previousCookieHandler);
            String xblBody = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\",\"RpsTicket\":\"" + msaToken + "\"},\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";
            HttpURLConnection xbl = (HttpURLConnection)URI.create("https://user.auth.xboxlive.com/user/authenticate").toURL().openConnection();
            xbl.setRequestMethod("POST");
            xbl.addRequestProperty("Content-Type", "application/json");
            xbl.setDoOutput(true);
            xbl.getOutputStream().write(xblBody.getBytes(StandardCharsets.UTF_8));
            xbl.setConnectTimeout(5000);
            xbl.setReadTimeout(5000);
            xbl.connect();
            Gson gson = new Gson();
            JsonObject xblJson = (JsonObject)gson.fromJson(IOUtils.toString((InputStream)xbl.getInputStream(), (Charset)StandardCharsets.UTF_8), JsonObject.class);
            String xblToken = xblJson.get("Token").getAsString();
            String xstsBody = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken + "\"]},\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
            HttpURLConnection xsts = (HttpURLConnection)URI.create("https://xsts.auth.xboxlive.com/xsts/authorize").toURL().openConnection();
            xsts.setRequestMethod("POST");
            xsts.addRequestProperty("Content-Type", "application/json");
            xsts.setDoOutput(true);
            xsts.getOutputStream().write(xstsBody.getBytes(StandardCharsets.UTF_8));
            xsts.setConnectTimeout(5000);
            xsts.setReadTimeout(5000);
            xsts.connect();
            String xstsAnswer = IOUtils.toString((InputStream)xsts.getInputStream(), (Charset)StandardCharsets.UTF_8);
            String xstsToken = xstsAnswer.split("\"Token\":\"")[1].split("\"")[0];
            String userHash = xstsAnswer.split("\"uhs\":\"")[1].split("\"")[0];
            String mcAnswer = MicrosoftAuth.post("https://api.minecraftservices.com/authentication/login_with_xbox", "{\"identityToken\":\"XBL3.0 x=" + userHash + ";" + xstsToken + "\"}", true);
            if (mcAnswer == null) {
                return new LoginResult(LoginStatus.FAILED);
            }
            JsonObject mcJson = JsonParser.parseString((String)mcAnswer).getAsJsonObject();
            if (!mcJson.has("access_token")) {
                return new LoginResult(LoginStatus.VALID_RATE_LIMITED);
            }
            String mcAccessToken = mcJson.get("access_token").getAsString();
            HttpURLConnection profileConnection = (HttpURLConnection)URI.create("https://api.minecraftservices.com/minecraft/profile").toURL().openConnection();
            try {
                profileConnection.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
                profileConnection.setConnectTimeout(5000);
                profileConnection.setReadTimeout(5000);
                profileConnection.connect();
                MinecraftProfile profile = (MinecraftProfile)gson.fromJson(IOUtils.toString((InputStream)profileConnection.getInputStream(), (Charset)StandardCharsets.UTF_8), MinecraftProfile.class);
                
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
                Logger.debugStackTrace(e, null);
                if (MicrosoftAuth.ownsMinecraft(mcAccessToken)) {
                    LoginResult loginResult = new LoginResult(new Session(null, null, mcAccessToken, "mojang"), LoginStatus.NO_PROFILE);
                    return loginResult;
                }
                LoginResult loginResult = new LoginResult(new Session(null, null, mcAccessToken, "mojang"), LoginStatus.NO_MINECRAFT);
                return loginResult;
            }
            finally {
                profileConnection.disconnect();
            }
        }
        catch (IOException e2) {
            Logger.debugStackTrace(e2, null);
            CookieHandler.setDefault(previousCookieHandler);
            return new LoginResult(LoginStatus.FAILED);
        }
    }
}

