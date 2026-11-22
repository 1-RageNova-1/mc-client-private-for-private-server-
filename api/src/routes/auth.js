const express = require('express');
const router = express.Router();
const { verifyMinecraftAccessToken } = require('../services/minecraftVerify');
const { upsertAccount, issueSession, validateSession, audit } = require('../services/sessionService');

router.post('/login', async (req, res) => {
  try {
    const accessToken = req.body && req.body.accessToken;
    const profile = await verifyMinecraftAccessToken(accessToken);

    const allow = (process.env.ALLOWLIST_UUIDS || '').split(',').map((s) => s.trim()).filter(Boolean);
    if (allow.length && !allow.includes(profile.uuid)) {
      return res.status(403).json({ error: 'not_authorized' });
    }

    const account = await upsertAccount({ uuid: profile.uuid, name: profile.name });
    const session = await issueSession(account);
    await audit(account.id, 'login', { name: profile.name }, req.ip);

    res.json({
      token: session.token,
      expiresAt: session.expiresAt,
      account: {
        id: account.id,
        uuid: account.minecraft_uuid,
        name: account.minecraft_name,
        status: account.status,
      },
    });
  } catch (e) {
    const status = e.status || 500;
    res.status(status).json({ error: e.message || 'login_failed' });
  }
});

router.get('/me', async (req, res) => {
  try {
    const hdr = req.headers.authorization || '';
    const token = hdr.startsWith('Bearer ') ? hdr.slice(7) : null;
    if (!token) return res.status(401).json({ error: 'missing_token' });
    const { row } = await validateSession(token);
    res.json({
      uuid: row.minecraft_uuid,
      name: row.minecraft_name,
      status: row.status,
      expiresAt: row.expires_at || row.expiresAt,
    });
  } catch (e) {
    res.status(e.status || 500).json({ error: e.message || 'unauthorized' });
  }
});

router.post('/validate', async (req, res) => {
  try {
    const token = req.body && req.body.token;
    if (!token) return res.status(400).json({ error: 'missing_token' });
    const { row, payload } = await validateSession(token);
    res.json({
      valid: true,
      uuid: row.minecraft_uuid,
      name: row.minecraft_name,
      accountId: payload.sub,
    });
  } catch (e) {
    res.status(e.status || 500).json({ valid: false, error: e.message });
  }
});

module.exports = router;
