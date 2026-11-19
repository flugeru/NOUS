import assert from 'node:assert/strict';
import {generateKeyPairSync, sign, createHash} from 'node:crypto';
import {begin,identity,serve} from './desktop-login.mjs';
const flow=begin(), query=flow.url.searchParams, enc=x=>Buffer.from(JSON.stringify(x)).toString('base64url');
assert.equal(query.get('code_challenge'),createHash('sha256').update(flow.verifier).digest('base64url'));
assert.notEqual(begin().state, flow.state);
assert.equal(query.get('code_challenge_method'),'S256');
const {publicKey,privateKey}=generateKeyPairSync('rsa',{modulusLength:2048});
const keys=[{...publicKey.export({format:'jwk'}),kid:'local',alg:'RS256',use:'sig'}];
const claims={iss:'https://accounts.google.com',aud:query.get('client_id'),sub:'12345',iat:1000,exp:4600,nonce:flow.nonce};
const token=(values)=>{const msg=enc({alg:'RS256',kid:'local'})+'.'+enc(values);return msg+'.'+sign('RSA-SHA256',Buffer.from(msg),privateKey).toString('base64url');};
assert.equal(identity(token(claims),keys,flow.nonce,1200),'12345');
for (const diff of [{aud:'other'},{iss:'other'},{azp:'other'},{nonce:'other'},{sub:'email@example.org'},{exp:1150}]) assert.throws(()=>identity(token({...claims,...diff}),keys,flow.nonce,1200));
const app=serve(undefined,undefined);
try {
  await new Promise((ok,no)=>{app.once('listening',ok);app.once('error',no);});
  const root='http://127.0.0.1:48173';
  const form = await fetch(root);
  assert.equal(form.headers.get('referrer-policy'),'strict-origin'); // Chromium must send Origin on the local form POST.
  assert.match(await form.text(),/Entrar com Google/);
  assert.equal((await fetch(root+'/login',{method:'POST',headers:{Origin:'null'}})).status,403);
  assert.equal((await fetch(root+'/login',{method:'POST',headers:{Origin:'https://example.org'}})).status,403);
  assert.equal((await fetch(root+'/login',{method:'POST',headers:{Origin:root}})).status,503);
  assert.equal((await fetch(root+'/google/callback?state=wrong&code=fixture')).status,400);
  assert.equal((await fetch(root+'/link')).status,401);
  assert.equal((await fetch(root+'/link',{method:'POST',headers:{Origin:root},body:new URLSearchParams({ticket:'A'.repeat(64)})})).status,400);
  assert.equal((await fetch(root+'/link',{method:'POST',headers:{Origin:'https://example.org'},body:new URLSearchParams({ticket:'A'.repeat(64)})})).status,403);
} finally {app.close();}
console.log('PKCE, signed OIDC identity, loopback form and callback checks passed.');
