# System.exit sites neutralized (license/auth crash paths)

These were process kills on Adjust license/backend failure, not Minecraft protocol auth.
Neutralized so MaiClient can run without Adjust servers. **Not** a substitute for MaiClient API success.

| Class | Original trigger (from decompilation) |
|-------|--------------------------------------|
| HydraConnection | Handshake/key failure against Adjust backend |
| HydraMessageDispatcher | Bad license/message handling from Adjust protocol |
| Authenticator | Adjust challenge/auth failure |
| HardwareId | Hard failure collecting HWID for Adjust |
| CerberusClient / CerberusChannelHandler | Secondary Adjust protocol failure |
| ServiceUtil | Vendor verify / backend challenge failure |
| ConfigurationService | Config integrity path tied to vendor state |
| LocalStorage | Storage load hard-fail path |
| ClientBootstrap | Init phase throwable (still logs; exit removed) |
| BlacklistedScreen | User shown blacklist then exit |

**MaiClient application auth** does not use these paths. It uses:
Microsoft login → Minecraft profile → MaiClient API JWT → MaiClientSessionStore.hasValidMaiClientSession().

HydraConnection.authenticated remains **false** (no simulated Adjust license success).
