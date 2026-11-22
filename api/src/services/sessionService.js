const crypto = require('crypto');
const jwt = require('jsonwebtoken');

const useMemory = process.env.MEMORY_DB === '1' || process.env.MEMORY_DB === 'true';
const mem = useMemory ? require('../db/memory') : null;
const pool = useMemory ? null : require('../db/pool');

function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

async function upsertAccount({ uuid, name, microsoftOid = null }) {
  if (useMemory) return mem.upsertAccount({ uuid, name, microsoftOid });
  const r = await pool.query(
    `INSERT INTO accounts (minecraft_uuid, minecraft_name, microsoft_oid)
     VALUES ($1, $2, $3)
     ON CONFLICT (minecraft_uuid) DO UPDATE
       SET minecraft_name = EXCLUDED.minecraft_name,
           microsoft_oid = COALESCE(EXCLUDED.microsoft_oid, accounts.microsoft_oid),
           updated_at = now()
     RETURNING *`,
    [uuid, name, microsoftOid]
  );
  return r.rows[0];
}

async function issueSession(account) {
  if (account.status !== 'active') {
    const err = new Error('account_not_active');
    err.status = 403;
    throw err;
  }
  const hours = Number(process.env.SESSION_HOURS || 24);
  const expiresAt = new Date(Date.now() + hours * 3600 * 1000);
  const token = jwt.sign(
    { sub: account.id, uuid: account.minecraft_uuid, name: account.minecraft_name },
    process.env.JWT_SECRET,
    { expiresIn: `${hours}h` }
  );
  const tokenHash = hashToken(token);
  if (useMemory) {
    await mem.insertSession({ accountId: account.id, tokenHash, expiresAt });
  } else {
    await pool.query(
      `INSERT INTO sessions (account_id, token_hash, expires_at) VALUES ($1, $2, $3)`,
      [account.id, tokenHash, expiresAt]
    );
  }
  return { token, expiresAt, account };
}

async function validateSession(token) {
  let payload;
  try {
    payload = jwt.verify(token, process.env.JWT_SECRET);
  } catch {
    const err = new Error('invalid_session');
    err.status = 401;
    throw err;
  }
  if (useMemory) {
    const row = await mem.findSessionByHash(hashToken(token));
    if (!row || row.status !== 'active') {
      const err = new Error('session_revoked_or_expired');
      err.status = 401;
      throw err;
    }
    return { payload, row };
  }
  const r = await pool.query(
    `SELECT s.*, a.status, a.minecraft_uuid, a.minecraft_name
     FROM sessions s JOIN accounts a ON a.id = s.account_id
     WHERE s.token_hash = $1 AND s.revoked_at IS NULL AND s.expires_at > now()`,
    [hashToken(token)]
  );
  if (!r.rows[0] || r.rows[0].status !== 'active') {
    const err = new Error('session_revoked_or_expired');
    err.status = 401;
    throw err;
  }
  return { payload, row: r.rows[0] };
}

async function audit(accountId, event, detail, ip) {
  if (useMemory) return mem.audit(accountId, event, detail, ip);
  await pool.query(
    `INSERT INTO audit_log (account_id, event, detail, ip) VALUES ($1, $2, $3, $4)`,
    [accountId, event, JSON.stringify(detail || {}), ip]
  );
}

module.exports = { upsertAccount, issueSession, validateSession, hashToken, audit, useMemory };
