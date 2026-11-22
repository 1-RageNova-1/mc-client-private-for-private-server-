# MaiClient owns application authentication

Flow:
  Microsoft OAuth -> Xbox -> XSTS -> Minecraft Services
  -> MaiClient API (verifies access token server-side)
  -> MaiClient JWT (MaiClientSessionStore)
  -> your private server

Microsoft = identity provider only.
MaiClient API = application auth, accounts, sessions, authorization.

Changes in this build:
- HydraConnection: no network to Adjust, no exit, no HWID auth packet
- HardwareId: inert
- System.exit removed from auth/license failure paths (bytecode)

Adjust branding may still appear in unrelated UI/strings; auth backend is not required to start.
