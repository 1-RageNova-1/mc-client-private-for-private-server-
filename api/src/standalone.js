/**
 * MaiClient API standalone (Node stdlib only).
 * Supports MEMORY_DB for local tests without npm/Postgres.
 * Production still uses Express + pg via index.js after `npm install`.
 */
const http = require('http');
const https = require('https');
const crypto = require('crypto');
const { URL } = require('url');

const PORT = Number(process.env.PORT || 8787);
const JWT_SECRET = process.env.JWT_SECRET || '';
const SESSION_HOURS = Number(process.env.SESSION_HOURS || 24);
const MOCK_PROFILE = process.env.MOCK_PROFILE || '';

if (!JWT_SECRET || JWT_SECRET.length < 16) {
  console.error('FATAL: JWT_SECRET must be set (min 16 chars). Refusing to start.');
  process.exit(1);
}

const accounts = new Map();
const sessions = new Map();

function b64url(input) {
  return Buffer.from(input).toString('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
}
function signJwt(payload) {
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = { ...payload, iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + SESSION_HOURS * 3600 };
  const mid = b64url(JSON.stringify(body));
  const data = `${header}.${mid}`;
  const sig = crypto.createHmac('sha256', JWT_SECRET).update(data).digest('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${data}.${sig}`;
}
function verifyJwt(token) {
  const parts = token.split('.');
  if (parts.length !== 3) throw Object.assign(new Error('invalid_session'), { status: 401 });
  const data = `${parts[0]}.${parts[1]}`;
  const sig = crypto.createHmac('sha256', JWT_SECRET).update(data).digest('base64').replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  if (sig !== parts[2]) throw Object.assign(new Error('invalid_session'), { status: 401 });
  const payload = JSON.parse(Buffer.from(parts[1].replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString());
  if (payload.exp && payload.exp < Math.floor(Date.now() / 1000)) throw Object.assign(new Error('session_revoked_or_expired'), { status: 401 });
  return payload;
}
function hashToken(t) {
  return crypto.createHash('sha256').update(t).digest('hex');
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', (c) => { data += c; if (data.length > 65536) reject(new Error('body_too_large')); });
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}
function json(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(body) });
  res.end(body);
}
function getJsonHttps(url, headers) {
  return new Promise((resolve, reject) => {
    const req = https.get(url, { headers, timeout: 8000 }, (r) => {
      let d = '';
      r.on('data', (c) => (d += c));
      r.on('end', () => {
        if (r.statusCode === 401 || r.statusCode === 403) return reject(Object.assign(new Error('invalid_token'), { status: 401 }));
        if (r.statusCode < 200 || r.statusCode >= 300) return reject(Object.assign(new Error('minecraft_http_' + r.statusCode), { status: 502 }));
        try { resolve(JSON.parse(d)); } catch (e) { reject(e); }
      });
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('minecraft_timeout')); });
  });
}
async function verifyMinecraftAccessToken(accessToken) {
  if (!accessToken || typeof accessToken !== 'string' || accessToken.length < 20) {
    throw Object.assign(new Error('missing_token'), { status: 400 });
  }
  if (MOCK_PROFILE && accessToken.startsWith('mock.')) {
    const [uuid, name] = MOCK_PROFILE.split('|');
    if (uuid && name) return { uuid, name };
  }
  const profile = await getJsonHttps('https://api.minecraftservices.com/minecraft/profile', {
    Authorization: 'Bearer ' + accessToken,
    Accept: 'application/json',
  });
  if (!profile || !profile.id || !profile.name) throw Object.assign(new Error('invalid_profile'), { status: 401 });
  const raw = String(profile.id).replace(/-/g, '');
  const uuid = `${raw.slice(0, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}-${raw.slice(16, 20)}-${raw.slice(20)}`;
  return { uuid, name: profile.name };
}

const server = http.createServer(async (req, res) => {
  try {
    const u = new URL(req.url, 'http://127.0.0.1');
    if (req.method === 'GET' && u.pathname === '/health') {
      return json(res, 200, { ok: true, service: 'maiclient-api-standalone', memoryDb: true });
    }
    if (req.method === 'POST' && u.pathname === '/v1/auth/login') {
      const raw = await readBody(req);
      const body = raw ? JSON.parse(raw) : {};
      const profile = await verifyMinecraftAccessToken(body.accessToken);
      let acc = accounts.get(profile.uuid);
      if (!acc) {
        acc = { id: crypto.randomUUID(), minecraft_uuid: profile.uuid, minecraft_name: profile.name, status: 'active' };
        accounts.set(profile.uuid, acc);
      } else {
        acc.minecraft_name = profile.name;
      }
      if (acc.status !== 'active') return json(res, 403, { error: 'account_not_active' });
      const token = signJwt({ sub: acc.id, uuid: acc.minecraft_uuid, name: acc.minecraft_name });
      sessions.set(hashToken(token), { accountId: acc.id, expiresAt: Date.now() + SESSION_HOURS * 3600e3, revoked: false });
      return json(res, 200, {
        token,
        expiresAt: new Date(Date.now() + SESSION_HOURS * 3600e3).toISOString(),
        account: { id: acc.id, uuid: acc.minecraft_uuid, name: acc.minecraft_name, status: acc.status },
      });
    }
    if (req.method === 'GET' && u.pathname === '/v1/auth/me') {
      const hdr = req.headers.authorization || '';
      const token = hdr.startsWith('Bearer ') ? hdr.slice(7) : null;
      if (!token) return json(res, 401, { error: 'missing_token' });
      const payload = verifyJwt(token);
      const sess = sessions.get(hashToken(token));
      if (!sess || sess.revoked || sess.expiresAt < Date.now()) return json(res, 401, { error: 'session_revoked_or_expired' });
      const acc = [...accounts.values()].find((a) => a.id === payload.sub);
      if (!acc || acc.status !== 'active') return json(res, 401, { error: 'unauthorized' });
      return json(res, 200, { uuid: acc.minecraft_uuid, name: acc.minecraft_name, status: acc.status });
    }
    if (req.method === 'POST' && u.pathname === '/v1/auth/validate') {
      const raw = await readBody(req);
      const body = raw ? JSON.parse(raw) : {};
      if (!body.token) return json(res, 400, { error: 'missing_token' });
      const payload = verifyJwt(body.token);
      const sess = sessions.get(hashToken(body.token));
      if (!sess || sess.revoked || sess.expiresAt < Date.now()) return json(res, 401, { valid: false, error: 'session_revoked_or_expired' });
      return json(res, 200, { valid: true, uuid: payload.uuid, name: payload.name, accountId: payload.sub });
    }
    json(res, 404, { error: 'not_found' });
  } catch (e) {
    json(res, e.status || 500, { error: e.message || 'internal' });
  }
});

server.listen(PORT, () => {
  console.log(`MaiClient standalone API on :${PORT} (MEMORY only)`);
});
