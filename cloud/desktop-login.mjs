// Loopback OAuth broker. The host requires an administrator-issued, one-use activation ticket.
import http from 'node:http';
import https from 'node:https';
import { randomBytes, createHash, createPublicKey, verify, X509Certificate } from 'node:crypto';
import tls from 'node:tls';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';

function hostBinding() {
  // Never accept a TLS pin or port from the browser. Registry is written by the host service.
  const reg = join(process.env.SystemRoot, 'System32', 'reg.exe');
  const text = execFileSync(reg,['query','HKLM\\SOFTWARE\\NOUS\\Desktop','/v','LocalHost','/reg:64'],{encoding:'utf8',windowsHide:true,timeout:2000});
  const binding = text.match(/REG_SZ\s+https:\/\/127\.0\.0\.1:(\d{1,5})\/?\|([A-Fa-f0-9]{64})\s*$/);
  if (!binding || Number(binding[1]) < 1 || Number(binding[1]) > 65535) throw Error('Host binding missing');
  return {port:Number(binding[1]),pin:binding[2].toUpperCase()};
}

// Never write request bytes before the exact loopback certificate has been checked.
export function enableHost(port, pin, ticket, token) {
  if (!Number.isInteger(port) || port < 1 || port > 65535 || !/^[A-F0-9]{64}$/.test(pin)) {
    return Promise.reject(Error('Invalid host binding'));
  }
  const agent = new https.Agent();
  agent.createConnection = (_options, done) => {
    const socket = tls.connect({host:'127.0.0.1',port,rejectUnauthorized:false,minVersion:'TLSv1.2'});
    const fail = error => { socket.destroy(); done(error); };
    socket.once('error',fail);
    socket.setTimeout(5000,()=>socket.destroy(Error('Host timeout')));
    socket.once('secureConnect',()=>{
      socket.removeListener('error',fail);
      try {
        const cert = new X509Certificate(socket.getPeerCertificate().raw);
        const now = Date.now();
        if (cert.fingerprint256.replaceAll(':','')!==pin || now<Date.parse(cert.validFrom) || now>=Date.parse(cert.validTo)) throw Error('Host certificate mismatch');
      } catch { return fail(Error('Host certificate rejected')); }
      done(null,socket);
    });
  };
  return new Promise((resolve,reject)=>{
    const body=JSON.stringify({ticket,token});
    const req=https.request({host:'127.0.0.1',port,path:'/nous/account/enable',method:'POST',agent,signal:AbortSignal.timeout(8000),headers:{'Content-Type':'application/json','Content-Length':Buffer.byteLength(body)}},res=>{
      let text=''; res.setEncoding('utf8');
      res.on('data',part=>{text+=part;if(text.length>8192) req.destroy(Error('Host reply too large'));});
      res.on('error',reject);
      res.on('end',()=>{try {if(res.statusCode!==200) throw Error('Host rejected activation');resolve(JSON.parse(text));} catch (e) {reject(e);} });
    });
    req.on('error',reject); req.end(body);
  }).finally(()=>agent.destroy());
}

async function linkHost(ticket, session) {
  const {port,pin} = hostBinding();
  const {status,host,name,pin:replyPin} = await enableHost(port,pin,ticket,session.token);
  if (status!==true || replyPin!==pin || !/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(host) ||
      typeof name!=='string' || !name || name.length>96 || /[\p{Cc}\p{Cf}]/u.test(name)) throw Error('Invalid host metadata');
  const dir = `https://firestore.googleapis.com/v1/projects/nous-game-stream/databases/(default)/documents/accounts/${session.uid}/google/${session.sub}/hosts/${host}`;
  await json(dir,{method:'PATCH',headers:{Authorization:`Bearer ${session.token}`,'Content-Type':'application/json'},
    body:JSON.stringify({fields:{name:{stringValue:name},pin:{stringValue:pin}}})});
}

async function form(req) {
  if (!/^application\/x-www-form-urlencoded(?:\s*;\s*charset=utf-8)?$/i.test(req.headers['content-type'] ?? '')) return null;
  let text='';
  for await (const buf of req) { text+=buf; if(text.length>1024) throw Error('Form too large'); }
  return new URLSearchParams(text);
}
const client = '963969569031-lbvh8j3iv48r4vjr0qsruf8dkhje1f4a.apps.googleusercontent.com';
const origin = 'http://127.0.0.1:48173', redirect = origin + '/google/callback';
const random = () => randomBytes(32).toString('base64url');
const linkScript = `const params = new URLSearchParams(location.hash.slice(1));
history.replaceState(null,'','/link');
document.getElementById('ticket').value = params.get('ticket') || '';`;
const linkScriptHash = createHash('sha256').update(linkScript).digest('base64');
export function begin() {
  const state = random(), verifier = random(), nonce = random();
  const url = new URL('https://accounts.google.com/o/oauth2/v2/auth');
  url.search = new URLSearchParams({client_id:client, redirect_uri:redirect, response_type:'code', scope:'openid', state, nonce,
    code_challenge_method:'S256', code_challenge:createHash('sha256').update(verifier).digest('base64url')});
  return {url, state, nonce, verifier, expires:Date.now()+300000};
}
async function json(url, options) {
  const res = await fetch(url, {...options, redirect:'error', signal:AbortSignal.timeout(8000)});
  if (!res.ok) throw Error('Provider unavailable');
  return res.json();
}
export function identity(token, keys, nonce, now) {
  const parts = typeof token === 'string' && token.length <= 16384 && token.split('.');
  if (!parts || parts.length !== 3 || !parts.every(x=>/^[a-zA-Z0-9_-]+$/.test(x))) throw Error('Invalid token');
  const [h,p,s] = parts, header = JSON.parse(Buffer.from(h,'base64url'));
  const key = keys.find(k => k.kid === header.kid && k.alg === 'RS256' && k.kty === 'RSA' && k.use === 'sig');
  if (!key || header.alg !== 'RS256' || header.crit || !verify('RSA-SHA256',Buffer.from(h+'.'+p),createPublicKey({key,format:'jwk'}),Buffer.from(s,'base64url'))) throw Error('Invalid signature');
  const claims = JSON.parse(Buffer.from(p,'base64url'));
  if (!['accounts.google.com','https://accounts.google.com'].includes(claims.iss) || claims.aud !== client ||
      (claims.azp !== undefined && claims.azp !== client) || claims.nonce !== nonce || typeof claims.sub !== 'string' || !/^\d{1,128}$/.test(claims.sub) ||
      !Number.isSafeInteger(claims.exp) || !Number.isSafeInteger(claims.iat) || claims.iat <= 0 || claims.iat > now+30 || claims.exp <= now || claims.exp <= claims.iat || claims.exp-claims.iat > 3900) throw Error('Invalid identity');
  return claims.sub;
}
const cookieName = '__Host-nous-account-test';
const cookie = (id,age) => `${cookieName}=${id}; Max-Age=${age}; Path=/; HttpOnly; Secure; SameSite=Lax`;
export function page(message, action, label) {
  // All arguments below are static application strings, never provider/user data.
  // ponytail: standalone broker mirrors NOUS tokens; share generated CSS when broker packaging is unified.
  return `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Conta NOUS · Teste</title><style>
:root{color-scheme:dark;--canvas:#101216;--surface:#1B1F25;--text:#F2F2F4;--muted:#B7BEC9;--border:#343B45;--accent:#FF9EA9;--hover:#FFBAC2;--on-accent:#3B0A16;--focus:#83DBE4;font:15px/1.5 system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;background:var(--canvas);color:var(--text)}
*{box-sizing:border-box}body{margin:0;padding:24px}main{max-inline-size:480px;margin:8vh auto;padding:32px;border:1px solid var(--border);border-radius:28px;background:var(--surface);overflow-wrap:anywhere}small,p{color:var(--muted)}h1{font-size:32px;line-height:1.25;margin:16px 0}p{margin-block:20px}button{min-block-size:44px;max-inline-size:100%;background:var(--accent);color:var(--on-accent);font:600 14px/20px system-ui;border:0;border-radius:20px;padding:12px 16px;cursor:pointer}button:hover{background:var(--hover)}:focus-visible{outline:2px solid var(--focus);outline-offset:2px}a{color:var(--focus);text-underline-offset:3px}main>p:last-child{margin-block-end:0}a{display:inline-block;padding-block:8px}
@media(prefers-color-scheme:light){:root{color-scheme:light;--canvas:#F7F7F5;--surface:#FFFFFF;--text:#252923;--muted:#535D50;--border:#DADFD7;--accent:#9C3F52;--hover:#7D293C;--on-accent:#FFFFFF;--focus:#007987}}
@media(max-width:480px){body{padding:16px}main{margin:24px auto;padding:24px}}
@media(forced-colors:active){button{border:1px solid ButtonText}}
</style><main><small>NOUS · Teste local</small><h1>Conta NOUS</h1><p>${message}</p>${action ? `<form method="post" action="${action}"><button>${label}</button></form>`:''}<p><a href="https://127.0.0.1:47990/v2/account" rel="noreferrer">Abrir Conta NOUS no painel do PC</a></p></main></html>`;
}
export function serve(configFile, firebaseKey) {
  const pending = new Map(), sessions = new Map();
  const server = http.createServer(async (req,res) => {
    res.setHeader('Cache-Control','no-store'); res.setHeader('Referrer-Policy','strict-origin');
    res.setHeader('X-Content-Type-Options','nosniff'); res.setHeader('Content-Security-Policy',`default-src 'none'; script-src 'sha256-${linkScriptHash}'; style-src 'unsafe-inline'; frame-ancestors 'none'; form-action 'self' https://accounts.google.com; base-uri 'none'`);
    const send = (code,html) => {res.writeHead(code,{'Content-Type':'text/html; charset=utf-8'});res.end(html);};
    const go = location => {res.writeHead(303,{Location:location});res.end();};
    if (req.headers.host !== '127.0.0.1:48173' || !req.url.startsWith('/') || req.url.startsWith('//') || req.url.length>8192) return send(400,'Pedido inválido');
    for (const map of [pending,sessions]) for (const [k,v] of map) if(v.expires<=Date.now()) map.delete(k);
    const id = (req.headers.cookie||'').split('; ').find(x=>x.startsWith(cookieName+'='))?.slice(cookieName.length+1);
    try {
      const u=new URL(req.url,origin);
      if (req.method==='GET' && req.url==='/') return send(200,sessions.has(id) ? page('Conta Google autenticada no Firebase. Para autorizar novas instalações, use Conta NOUS no painel do PC.','/logout','Sair da conta') : page('Login da conta é separado da senha administrativa. Entrar não autoriza dispositivos.','/login','Entrar com Google'));
      if (req.method==='GET' && req.url==='/link') {
        if (!sessions.has(id)) return send(401,page('Entre com Google primeiro. Depois volte ao painel do PC e selecione Ativar acesso por conta.','/login','Entrar com Google'));
        return send(200,page('Autorizar instalações da sua conta Google neste PC?','/link','Autorizar e publicar PC').replace('<button>','<input type="hidden" name="ticket" id="ticket"><button>')+`<script>${linkScript}</script>`);
      }
      if (req.method==='POST' && req.headers.origin!==origin) return send(403,'Origem inválida');
      if (req.method==='POST' && req.url==='/link') {
        const session=sessions.get(id), params=await form(req), ticket=params?.get('ticket');
        if (!session || !params || params.getAll('ticket').length!==1 || !/^[A-F0-9]{64}$/.test(ticket ?? '')) return send(400,page('Autorização inválida ou expirada. Volte ao painel do PC.'));
        try { await linkHost(ticket,session); }
        catch { return send(502,page('Não foi possível concluir a publicação. Confira o painel do PC; desative o acesso por conta se não quiser manter a autorização.')); }
        return send(200,page('PC publicado. No Android, abra Conta NOUS → PCs da conta → Conectar.'));
      }
      if (req.method==='POST' && req.url==='/logout') {sessions.delete(id);pending.delete(id);res.setHeader('Set-Cookie',cookie('',0));return go('/');}
      if (req.method==='POST' && req.url==='/login') {
        let c; try { c=JSON.parse(readFileSync(configFile,'utf8')).installed; } catch {}
        if (c?.client_id!==client || !c.client_secret || !firebaseKey) return send(503,page('Falta configurar o arquivo OAuth local do cliente NOUS Desktop. Nenhuma credencial foi enviada.','/login','Tentar novamente'));
        if (pending.size>=64) return send(429,'Tente novamente em cinco minutos');
        const flow=begin(), binding=random(); pending.delete(id); sessions.delete(id); pending.set(binding,{...flow,secret:c.client_secret});
        res.setHeader('Set-Cookie',cookie(binding,300));return go(flow.url.href);
      }
      if (req.method==='GET' && u.origin===origin && u.pathname==='/google/callback') {
        const flow=pending.get(id); if (!flow || u.searchParams.getAll('state').length!==1 || u.searchParams.get('state')!==flow.state) return send(400,page('Retorno inválido ou expirado.','/login','Recomeçar'));
        pending.delete(id); res.setHeader('Set-Cookie',cookie('',0));
        const code=u.searchParams.get('code'); if (u.searchParams.has('error') || !code || u.searchParams.getAll('code').length!==1) return go('/');
        const token=await json('https://oauth2.googleapis.com/token',{method:'POST',body:new URLSearchParams({client_id:client,client_secret:flow.secret,redirect_uri:redirect,grant_type:'authorization_code',code,code_verifier:flow.verifier})});
        const keys=await json('https://www.googleapis.com/oauth2/v3/certs');
        const sub=identity(token.id_token,keys.keys,flow.nonce,Math.floor(Date.now()/1000));
        const fb=await json('https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key='+encodeURIComponent(firebaseKey),{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({requestUri:'http://localhost',postBody:new URLSearchParams({id_token:token.id_token,providerId:'google.com'}).toString(),returnSecureToken:true})});
        if (fb.providerId!=='google.com' || typeof fb.localId!=='string' || !/^[A-Za-z0-9_-]{1,128}$/.test(fb.localId) || fb.needConfirmation || typeof fb.idToken!=='string' || !fb.idToken) throw Error('Account not confirmed');
        if (sessions.size>=64) return send(429,'Tente novamente mais tarde');
        const key=random(); sessions.set(key,{uid:fb.localId,sub,token:fb.idToken,expires:Date.now()+900000});res.setHeader('Set-Cookie',cookie(key,900));return go('/');
      }
      send(404,'Não encontrado');
    } catch {send(502,page('Não foi possível validar a conta. O host não foi alterado.','/login','Recomeçar'));}
  });
  return server.listen(48173,'127.0.0.1');
}
if (process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  const file = process.env.NOUS_GOOGLE_CLIENT_FILE || join(process.env.LOCALAPPDATA,'NOUS','desktop-oauth.json');
  let key = process.env.NOUS_FIREBASE_WEB_KEY;
  for (const config of [join(process.env.LOCALAPPDATA,'NOUS','google-services.json'),new URL('../android/app/google-services.json',import.meta.url)]) {
    if (key) break;
    try {
      const data=JSON.parse(readFileSync(config,'utf8'));
      if (data.project_info?.project_id==='nous-game-stream') key=data.client.find(c=>c.client_info?.android_client_info?.package_name==='com.flugel.nous')?.api_key?.[0]?.current_key;
    } catch { /* Optional public Firebase client configuration. */ }
  }
  serve(file,key);
}
