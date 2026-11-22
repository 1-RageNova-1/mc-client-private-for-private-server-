/**
 * Server-side verification against Minecraft Services.
 * Never trust client-provided UUID/username alone.
 */
const https = require('https');

function getJson(url, headers = {}) {
  return new Promise((resolve, reject) => {
    const req = https.get(url, { headers, timeout: 8000 }, (res) => {
      let data = '';
      res.on('data', (c) => (data += c));
      res.on('end', () => {
        if (res.statusCode === 401 || res.statusCode === 403) {
          return reject(Object.assign(new Error('invalid_token'), { status: 401 }));
        }
        if (res.statusCode < 200 || res.statusCode >= 300) {
          return reject(Object.assign(new Error(`minecraft_http_${res.statusCode}`), { status: 502 }));
        }
        try {
          resolve(JSON.parse(data));
        } catch (e) {
          reject(e);
        }
      });
    });
    req.on('error', reject);
    req.on('timeout', () => {
      req.destroy();
      reject(new Error('minecraft_timeout'));
    });
  });
}

/**
 * Verify a Minecraft access token and return profile { id, name }.
 * Token must be a real Minecraft Services bearer token obtained via Microsoft OAuth.
 */
async function verifyMinecraftAccessToken(accessToken) {
  // Test-only: MOCK_PROFILE=uuid|name bypasses live Microsoft call. Never use in production.
  if (process.env.MOCK_PROFILE && accessToken && accessToken.startsWith('mock.')) {
    const [uuid, name] = process.env.MOCK_PROFILE.split('|');
    if (uuid && name) return { uuid, name, rawId: uuid.replace(/-/g, '') };
  }

  if (!accessToken || typeof accessToken !== 'string' || accessToken.length < 20) {
    const err = new Error('missing_token');
    err.status = 400;
    throw err;
  }
  // Official profile endpoint — returns UUID (no dashes) + name when token is valid
  const profile = await getJson('https://api.minecraftservices.com/minecraft/profile', {
    Authorization: `Bearer ${accessToken}`,
    Accept: 'application/json',
  });
  if (!profile || !profile.id || !profile.name) {
    const err = new Error('invalid_profile');
    err.status = 401;
    throw err;
  }
  // Normalize UUID to dashed form
  const raw = profile.id.replace(/-/g, '');
  if (raw.length !== 32) {
    const err = new Error('bad_uuid');
    err.status = 502;
    throw err;
  }
  const uuid = `${raw.slice(0, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}-${raw.slice(16, 20)}-${raw.slice(20)}`;
  return { uuid, name: profile.name, rawId: profile.id };
}

module.exports = { verifyMinecraftAccessToken };
