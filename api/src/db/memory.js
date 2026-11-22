/** In-memory store for environments without PostgreSQL. Not for production. */
const crypto = require('crypto');

const accounts = new Map();
const sessions = new Map();
const audit = [];

function id() {
  return crypto.randomUUID();
}
function now() { return new Date(); }

module.exports = {
  async upsertAccount({ uuid, name, microsoftOid = null }) {
    let acc = accounts.get(uuid);
    if (!acc) {
      acc = {
        id: id(),
        minecraft_uuid: uuid,
        minecraft_name: name,
        microsoft_oid: microsoftOid,
        status: 'active',
        created_at: now(),
        updated_at: now(),
      };
      accounts.set(uuid, acc);
    } else {
      acc.minecraft_name = name;
      if (microsoftOid) acc.microsoft_oid = microsoftOid;
      acc.updated_at = now();
    }
    return { ...acc };
  },
  async insertSession({ accountId, tokenHash, expiresAt }) {
    const row = {
      id: id(),
      account_id: accountId,
      token_hash: tokenHash,
      expires_at: expiresAt,
      created_at: now(),
      revoked_at: null,
    };
    sessions.set(tokenHash, row);
    return row;
  },
  async findSessionByHash(tokenHash) {
    const s = sessions.get(tokenHash);
    if (!s || s.revoked_at) return null;
    if (s.expires_at.getTime() <= Date.now()) return null;
    const acc = [...accounts.values()].find((a) => a.id === s.account_id);
    if (!acc) return null;
    return {
      ...s,
      status: acc.status,
      minecraft_uuid: acc.minecraft_uuid,
      minecraft_name: acc.minecraft_name,
    };
  },
  async revokeSession(tokenHash) {
    const s = sessions.get(tokenHash);
    if (s) s.revoked_at = now();
  },
  async audit(accountId, event, detail, ip) {
    audit.push({ accountId, event, detail, ip, at: now() });
  },
  _reset() { accounts.clear(); sessions.clear(); audit.length = 0; },
  _stats() { return { accounts: accounts.size, sessions: sessions.size, audit: audit.length }; },
};
