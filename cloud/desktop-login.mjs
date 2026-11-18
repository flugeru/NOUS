// Local browser test only. No host admin routes, no grants, no tokens in pages or logs.
import http from 'node:http';
import { randomBytes, createHash, createPublicKey, verify } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { join } from 'node:path';
const client = '963969569031-lbvh8j3iv48r4vjr0qsruf8dkhje1f4a.apps.googleusercontent.com';
const origin = 'http://127.0.0.1:48173', redirect = origin + '/google/callback';
const random = () => randomBytes(32).toString('base64url');
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
function page(message, action, label) {
  // All arguments below are static application strings, never provider/user data.
  return `<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Conta NOUS · Teste</title><style>:root{color-scheme:dark;font:18px system-ui;background:#14161e;color:#f1f1f5}main{max-width:33rem;margin:12vh auto;padding:1.5rem}button{background:#ffacd9;color:#251322;font:inherit;border:0;border-radius:.7rem;padding:.7rem 1.2rem;cursor:pointer}:focus-visible{outline:3px solid cyan}a{color:#94e7ed}</style><main><small>NOUS · Teste local</small><h1>Conta NOUS</h1><p>${message}</p>${action ? `<form method="post" action="${action}"><button>${label}</button></form>`:''}<p><a href="https://127.0.0.1:47990/" rel="noreferrer">Abrir painel administrativo do host</a></p></main></html>`;
}
export function serve(configFile, firebaseKey) {
  const pending = new Map(), sessions = new Map();
  const server = http.createServer(async (req,res) => {
    res.setHeader('Cache-Control','no-store'); res.setHeader('Referrer-Policy','no-referrer');
    res.setHeader('X-Content-Type-Options','nosniff'); res.setHeader('Content-Security-Policy',"default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; form-action 'self' https://accounts.google.com; base-uri 'none'");
    const send = (code,html) => {res.writeHead(code,{'Content-Type':'text/html; charset=utf-8'});res.end(html);};
    const go = location => {res.writeHead(303,{Location:location});res.end();};
    if (req.headers.host !== '127.0.0.1:48173' || !req.url.startsWith('/') || req.url.startsWith('//') || req.url.length>8192) return send(400,'Pedido inválido');
    for (const map of [pending,sessions]) for (const [k,v] of map) if(v.expires<=Date.now()) map.delete(k);
    const id = (req.headers.cookie||'').split('; ').find(x=>x.startsWith(cookieName+'='))?.slice(cookieName.length+1);
    try {
      const u=new URL(req.url,origin);
      if (req.method==='GET' && req.url==='/') return send(200,sessions.has(id) ? page('Conta Google autenticada no Firebase. Este PC ainda não concede acesso por conta.','/logout','Sair da conta') : page('Login da conta é separado da senha administrativa. Entrar não autoriza dispositivos.','/login','Entrar com Google'));
      if (req.method==='POST' && req.headers.origin!==origin) return send(403,'Origem inválida');
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
        if (fb.providerId!=='google.com' || !fb.localId || fb.needConfirmation || !fb.idToken) throw Error('Account not confirmed');
        const key=random(); sessions.set(key,{uid:fb.localId,sub,expires:Date.now()+900000});res.setHeader('Set-Cookie',cookie(key,900));return go('/');
      }
      send(404,'Não encontrado');
    } catch {send(502,page('Não foi possível validar a conta. O host não foi alterado.','/login','Recomeçar'));}
  });
  return server.listen(48173,'127.0.0.1');
}
if (process.argv[1] && import.meta.url===pathToFileURL(process.argv[1]).href) {
  const file = process.env.NOUS_GOOGLE_CLIENT_FILE || join(process.env.LOCALAPPDATA,'NOUS','desktop-oauth.json');
  const key = process.env.NOUS_FIREBASE_WEB_KEY || JSON.parse(readFileSync(new URL('../android/app/google-services.json',import.meta.url),'utf8')).client[0].api_key[0].current_key;
  serve(file,key);
}
