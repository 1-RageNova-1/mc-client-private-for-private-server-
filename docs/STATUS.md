# MaiClient — Status (Phase 8)

**Updated:** 2026-11-21

## JDK 21

| Tool | Version used |
|------|----------------|
| `java` | OpenJDK 21.0.13.1 |
| `javac` | **21.0.13.1** (`/usr/lib/jvm/java-21-openjdk-amd64/bin/javac`) |
| `jar` | 21.0.13.1 |

## Successfully compiled (class file major 65)

| Class |
|-------|
| `com.maiclient.api.MaiClientApi` (+ ApiException, LoginResponse) |
| `com.maiclient.api.MaiClientConfig` |
| `com.maiclient.api.MaiClientSessionStore` |
| `com.adjustclient.ui.account.login.auth.MicrosoftCookieAccount` |
| `com.adjustclient.ui.account.login.auth.MicrosoftCredentialAccount` |
| `com.adjustclient.ui.account.login.auth.MicrosoftRefreshTokenAccount` |

## Successfully packaged JAR

| File | Notes |
|------|--------|
| `dist-patched/dist/maiclient-1.8.9.jar` | Copy of original + injected classes |
| `dist-patched/dist/adjust-client-1.8.9.jar` | **Original preserved** |
| Manifest | Unchanged (`Main-Class: net.minecraft.client.main.Main`) |
| libraries/ natives/ | Copied with dist |

## Runtime verification (this environment)

| Test | Result |
|------|--------|
| Launcher / GUI client start | **NOT RUN** — Linux headless sandbox, no Windows/GPU session |
| Account manager UI | **NOT RUN** |
| Microsoft cookie/credential/refresh login | **NOT RUN** (requires interactive Microsoft login on your machine) |
| MaiClient API connection from client | **NOT RUN** end-to-end with real login |
| Minecraft 1.8.9 world | **NOT RUN** |
| API standalone HTTP tests | Still valid from Phase 6/7 (`test-core` PASS) |

## Security audit (static)

| Check | Result |
|-------|--------|
| Only Minecraft access token to MaiClient API | YES (`loginWithMinecraftToken`) |
| Refresh token sent to MaiClient API | **NO** in integration code |
| Access tokens logged | Failure path uses `getMessage()` only |
| Fake auth success | **NO** — Microsoft Session still required; API failure does not invent Session |
| Adjust license/Hydra removed | **NO** — still in jar |
| Production HTTPS | Config via env/`-Dmaiclient.api`; not hardcoded |

## Remaining Adjust dependencies

Hydra, Authenticator, HWID, Cerberus still inside `maiclient-1.8.9.jar` (unmodified except the three account classes + new api package).

