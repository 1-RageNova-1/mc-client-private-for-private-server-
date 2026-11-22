# Authentication architecture

```
[Player]
   │ Microsoft account (OAuth / cookie / refresh)
   ▼
[MaiClient] existing MicrosoftCookieAccount /
            MicrosoftCredentialAccount /
            MicrosoftRefreshTokenAccount
   │ Xbox Live → XSTS → Minecraft Services
   │ obtains: minecraft access token + UUID + name
   │ creates: net.minecraft.util.Session (UNCHANGED)
   │
   │ POST /v1/auth/login  { "accessToken": "<minecraft access token only>" }
   ▼
[MaiClient API]
   │ GET api.minecraftservices.com/minecraft/profile
   │ Authorization: Bearer <access token>
   │ verifies UUID + name server-side
   │ upserts accounts row
   │ issues MaiClient JWT (hashed in sessions table)
   ▼
[MaiClientSessionStore]  (client memory — Mai JWT only)
   │
   ▼
[Private Minecraft server]
   optional: POST /v1/auth/validate with Mai JWT
```

Microsoft identity remains the source of truth for the game session.  
MaiClient API is an **authorization layer** you own for private-server access control.


## Microsoft desktop browser flow (PKCE)

The browser account now uses the Microsoft identity platform v2 authorization-code flow:
1. Bind a temporary callback listener on `localhost` and choose an ephemeral port.
2. Generate a random OAuth state and PKCE verifier/challenge (S256).
3. Open the system browser at the Microsoft `consumers` authorize endpoint.
4. Validate the returned state and exchange the one-time authorization code at the v2 token endpoint with the original verifier.
5. Store the returned refresh token in the existing browser account format.
6. Refresh Microsoft access tokens without a client secret, then continue Xbox Live -> XSTS -> Minecraft Services.

### Entra app registration

For the desktop app registration:
- Supported account type: personal Microsoft accounts (matches the `consumers` authority).
- Authentication > Add a platform > **Mobile and desktop applications**.
- Add the system-browser redirect URI: `http://localhost` (the app uses an ephemeral port).
- Authentication > Advanced settings: set **Allow public client flows** to Yes.
- Do not create or embed a client secret; a desktop application is a public client.

The previously configured `nativeclient` URI is for embedded-browser scenarios; the `msal...://auth` URI is for MSAL broker-style redirects. Neither replaces the localhost redirect used by this system-browser callback.
