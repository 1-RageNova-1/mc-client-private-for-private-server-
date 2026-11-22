/** Core logic tests — no external deps. */
const assert = require('assert');
const { spawn } = require('child_process');
const http = require('http');
const path = require('path');

const PORT = 18787;
const JWT_SECRET = 'test_secret_maiclient_phase6_not_prod_xx';
const MOCK = '11111111-2222-3333-4444-555555555555|TestPlayer';

function req(method, urlPath, body, headers = {}) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const r = http.request({ hostname: '127.0.0.1', port: PORT, path: urlPath, method, headers: {
      'Content-Type': 'application/json',
      ...(data ? { 'Content-Length': Buffer.byteLength(data) } : {}),
      ...headers,
    }}, (res) => {
      let d = '';
      res.on('data', (c) => (d += c));
      res.on('end', () => {
        let j = null;
        try { j = JSON.parse(d); } catch {}
        resolve({ status: res.statusCode, body: j, raw: d });
      });
    });
    r.on('error', reject);
    if (data) r.write(data);
    r.end();
  });
}

async function main() {
  const child = spawn(process.execPath, [path.join(__dirname, '../src/standalone.js')], {
    env: { ...process.env, PORT: String(PORT), JWT_SECRET, MOCK_PROFILE: MOCK, SESSION_HOURS: '24' },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  await new Promise((r) => setTimeout(r, 1200));
  const results = [];
  try {
    let res = await req('GET', '/health');
    assert.equal(res.status, 200);
    assert.equal(res.body.ok, true);
    results.push('health');

    res = await req('POST', '/v1/auth/login', { accessToken: '' });
    assert.equal(res.status, 400);
    results.push('reject_empty');

    res = await req('POST', '/v1/auth/login', { accessToken: 'mock.validtokenvalue12345' });
    assert.equal(res.status, 200);
    assert.ok(res.body.token);
    assert.equal(res.body.account.name, 'TestPlayer');
    const token = res.body.token;
    results.push('login_mock');

    res = await req('GET', '/v1/auth/me', null, { Authorization: 'Bearer ' + token });
    assert.equal(res.status, 200);
    assert.equal(res.body.uuid, '11111111-2222-3333-4444-555555555555');
    results.push('me');

    res = await req('POST', '/v1/auth/validate', { token });
    assert.equal(res.status, 200);
    assert.equal(res.body.valid, true);
    results.push('validate');

    res = await req('POST', '/v1/auth/validate', { token: 'a.b.c' });
    assert.ok(res.status === 401 || res.body.valid === false);
    results.push('validate_bad');

    res = await req('POST', '/v1/auth/login', { accessToken: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.invalid' });
    assert.ok(res.status >= 400);
    results.push('reject_fake_live');

    console.log('PASS', results.join(', '));
    process.exitCode = 0;
  } catch (e) {
    console.error('FAIL', e);
    process.exitCode = 1;
  } finally {
    child.kill('SIGTERM');
  }
}
main();
