# NEXT (Windows runtime)

1. Copy `dist-patched/dist/` to Windows (or use `dist-original` + rebuild with JDK 21).
2. Start API: `api` → `npm run start:standalone` or Postgres + `npm start`.
3. Edit `launcher-maiclient.bat` or set:
   `set MAICLIENT_API=http://127.0.0.1:8787`
4. Run with Java 21: `launcher-maiclient.bat`
5. Log in with Microsoft in the account UI (do not paste tokens in chat).
6. Confirm API logs show `/v1/auth/login` without logging token bodies.
7. Only after that: consider Hydra gating.

