// Start a fresh Firestore emulator with --rules cloud/firestore.rules --project_id demo-nous --port 8188.
// Synthetic emulator identities only; no SDK, external endpoint or production credentials.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const root = 'http://127.0.0.1:8188/v1/projects/demo-nous/databases/(default)/documents/';
const dir = 'accounts/local-owner/google/12345/hosts', path = `${dir}/${randomUUID()}`;
const enc = obj => Buffer.from(JSON.stringify(obj)).toString('base64url');
function fixture(uid, provider = 'google.com', subject = '12345') {
  const now = Math.floor(Date.now() / 1000);
  return `${enc({ alg: 'none', typ: 'JWT' })}.${enc({ sub: uid, user_id: uid, aud: 'demo-nous', iss: 'https://securetoken.google.com/demo-nous', iat: now, auth_time: now, exp: now + 3600, firebase: { sign_in_provider: provider, identities: { 'google.com': [subject] } } })}.`;
}
const owner = fixture('local-owner');
const doc = { name: 'Test', pin: 'A'.repeat(64) };
let checks = 0;
async function check(method, url, token, status, fields) {
  const response = await fetch(root + url, { method, redirect: 'error', signal: AbortSignal.timeout(5000), headers: {
    ...(token && { Authorization: `Bearer ${token}` }), 'Content-Type': 'application/json'
  }, body: fields && JSON.stringify({ fields: Object.fromEntries(Object.entries(fields).map(([k,v]) => [k,{stringValue:v}])) }) });
  assert.equal(response.status, status, `${method} ${url}: expected ${status}, got ${response.status}`);
  await response.body?.cancel(); checks++;
}
await check('PATCH', path, owner, 200, doc);
try {
  await check('GET', path, owner, 200);
  await check('GET', dir, owner, 200);
  for (const who of [undefined, fixture('other'), fixture('local-owner','password'), fixture('local-owner','google.com','54321')]) {
    for (const [m,p] of [['GET',path],['GET',dir],['PATCH',path],['DELETE',path]]) await check(m,p,who,403,m === 'PATCH' ? doc : undefined);
  }
  await check('PATCH', path, owner, 200, {...doc, name:'Renamed'});
  await check('PATCH', path, owner, 403, {...doc, pin:'B'.repeat(64)});
  await check('PATCH', path, owner, 403, {...doc, grant:'none'});
  await check('PATCH', `${dir}/invalid`, owner, 403, doc);
  await check('PATCH', `${path}/grants/local`, owner, 403, doc);
} finally { await check('DELETE', path, owner, 200); }
console.log(`${checks} local Firestore checks passed`);
