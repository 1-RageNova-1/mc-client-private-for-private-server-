-- MaiClient API schema
-- Server never trusts client-supplied UUID/username without verification.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE accounts (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  microsoft_oid   TEXT UNIQUE,                  -- Microsoft object id if available
  minecraft_uuid  UUID UNIQUE NOT NULL,         -- verified UUID from Minecraft Services
  minecraft_name  TEXT NOT NULL,                 -- verified username
  status          TEXT NOT NULL DEFAULT 'active'
                  CHECK (status IN ('active','suspended','banned')),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE sessions (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  account_id      UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
  token_hash      TEXT NOT NULL UNIQUE,          -- sha256 of issued MaiClient token
  expires_at      TIMESTAMPTZ NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  revoked_at      TIMESTAMPTZ
);

CREATE TABLE audit_log (
  id              BIGSERIAL PRIMARY KEY,
  account_id      UUID REFERENCES accounts(id) ON DELETE SET NULL,
  event           TEXT NOT NULL,
  detail          JSONB,
  ip              INET,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sessions_account ON sessions(account_id);
CREATE INDEX idx_sessions_expires ON sessions(expires_at);
CREATE INDEX idx_audit_account ON audit_log(account_id);
