import assert from 'node:assert/strict';
import {generateKeyPairSync, sign, createHash, X509Certificate} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {mkdtempSync, readFileSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {createServer} from 'node:https';
import {begin,enableHost,identity,serve} from './desktop-login.mjs';

// Generated fixture only. The real OAuth token never enters this test.
const fixture = mkdtempSync(join(tmpdir(),'nous-tls-'));
const pem = join(fixture,'cert.pem'), pk = join(fixture,'key.pem');
try {
  execFileSync('openssl',['req','-x509','-newkey','rsa:2048','-nodes','-subj','/CN=Test','-days','1','-out',pem,'-keyout',pk],{stdio:'ignore'});
  const cert=readFileSync(pem), pin=new X509Certificate(cert).fingerprint256.replaceAll(':','');
  const received=[];
  const host=createServer({cert,key:readFileSync(pk)},async (req,res)=>{
    const chunks=[];
    for await (const chunk of req) chunks.push(chunk);
    received.push({path:req.url,method:req.method,body:JSON.parse(Buffer.concat(chunks).toString())});
    res.end(JSON.stringify({status:true}));
  });
  await new Promise((resolve,reject)=>host.listen(0,'127.0.0.1',resolve).once('error',reject));
  try {
    const port=host.address().port;
    const wrong=(pin[0]==='A' ? 'B' : 'A')+pin.slice(1);
    await assert.rejects(enableHost(port,wrong,'fixture-ticket','fixture-token'),/certificate rejected/);
    assert.deepEqual(received,[]);
    assert.deepEqual(await enableHost(port,pin,'fixture-ticket','fixture-token'),{status:true});
    assert.deepEqual(received,[{path:'/nous/account/enable',method:'POST',body:{ticket:'fixture-ticket',token:'fixture-token'}}]);
  } finally { await new Promise(resolve=>host.close(resolve)); }
} finally { rmSync(fixture,{recursive:true,force:true}); }
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
