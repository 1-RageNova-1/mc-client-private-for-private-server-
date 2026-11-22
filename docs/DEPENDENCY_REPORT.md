# Adjust backend dependency report (Phase 4 audit — static)

Source: decompiled `com.adjustclient` / renamed `com.maiclient` inspection from prior sessions.

## Categories

### Authentication (Microsoft — KEEP)
- `auth.MicrosoftCookieLogin`, `MicrosoftAuth`, `LoginWithXbox*`, `ProfileRequest`, `XboxIdentity`
- `ui.account.login.auth.MicrosoftCookieAccount`, `MicrosoftCredentialAccount`, `MicrosoftRefreshTokenAccount`
- Account manager UI / LocalAccountStore

**Action:** Preserve. MaiClient API hooks after SUCCESS only.

### Licensing / private backend (Adjust-owned protocol)
- `backend.transport.HydraConnection` — TCP license channel, HWID send, `System.exit` on failure
- `backend.transport.HydraMessageDispatcher` — message handling, exits
- `backend.auth.Authenticator`, `AuthStatics`, Challenge* — challenge/license
- `backend.protocol.CerberusClient`, `CerberusChannelHandler` — secondary channel, exits
- Credits / `creditsExpiry` fields

**Action:** Replace call sites with MaiClient session checks when E2E proven. Do not fake success. Do not claim ownership of Adjust protocol.

### HWID
- `backend.hwid.HardwareId` and collectors (oshi/JNA)
**Action:** Only needed for Adjust license. Remove from bootstrap after MaiClient session is authoritative.

### Telemetry
- `service.telemetry.*` (if present), vendor telemetry stubs in Adjust bootstrap
**Action:** Audit before disable; may be unrelated to auth.

### Config / storage exits
- `ConfigurationService`, `LocalStorage`, `ServiceUtil`, `ClientBootstrap`, `BlacklistedScreen` — some `System.exit` paths
**Action:** Treat case-by-case; only license/blacklist exits related to Adjust backend.

### Unrelated functionality
- Modules (combat/render/movement), Hypixel helpers, voice chat, scripting, Lunar Apollo, rendering
**Action:** Do not modify for auth work.

## Replacement rule
Only replace integrations we own (MaiClient API session). Microsoft OAuth remains the identity source. Adjust Hydra/Cerberus is third-party license infrastructure — remove dependency for private-server MaiClient builds after session validation works; do not bypass others' licensing on their services.

