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

