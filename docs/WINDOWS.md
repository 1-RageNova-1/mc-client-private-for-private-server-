# MaiClient on Windows — exact steps

## A. API (Node 18+)

1. Install Node.js LTS: https://nodejs.org/
2. Copy folder `MaiClient\api` to your PC.
3. In PowerShell:

```powershell
cd path\to\MaiClient\api
npm install
copy config\example.env config\.env
notepad config\.env
```

4. Edit `.env`:

```
PORT=8787
DATABASE_URL=postgres://maiclient:YOUR_PASSWORD@localhost:5432/maiclient
JWT_SECRET=generate_a_long_random_string_here
SESSION_HOURS=24
```

5. Start Postgres, apply `sql\schema.sql` (see POSTGRES.md).
6. Run:

```powershell
npm start
```

Dev without Postgres:

```powershell
$env:MEMORY_DB="1"
$env:JWT_SECRET="dev_only_secret_min_16_chars"
$env:MOCK_PROFILE="11111111-2222-3333-4444-555555555555|TestPlayer"
npm run start:standalone
```

Test:

```powershell
npm run test:core
curl http://127.0.0.1:8787/health
```

## B. Client integration sources

Files in `MaiClient\client-integration\` are **patches** for the decompiled client:

- `MaiClientApi.java`, `MaiClientConfig.java`, `MaiClientSessionStore.java` → package `com.maiclient.api`
- `MicrosoftCookieAccount.java`, `MicrosoftCredentialAccount.java`, `MicrosoftRefreshTokenAccount.java` → replace the matching files under `ui/account/login/auth/`

You still need the full decompiled tree from `dist.rar` (CFR) + original `libraries/` + `natives/` to produce a runnable jar. This workspace does not ship a complete Gradle Minecraft 1.8.9 rebuild.

## C. Client JVM flag

```
-Dmaiclient.api=http://127.0.0.1:8787
```

or environment variable `MAICLIENT_API`.

## D. Auth flow (final architecture)

```
Microsoft OAuth (browser/cookie/credentials)
  → Xbox Live / sisu
  → XSTS
  → Minecraft Services (access token + profile)
  → MaiClient client: POST /v1/auth/login { accessToken }
  → MaiClient API verifies token at api.minecraftservices.com
  → MaiClient JWT session stored in MaiClientSessionStore
  → Join your private Minecraft server (normal MS session for protocol)
```

Never send Microsoft refresh tokens to MaiClient API.

