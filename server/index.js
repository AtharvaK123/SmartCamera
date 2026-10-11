/* SmartCam server: keeps API keys off the device, proxies Gemini + ElevenLabs,
   syncs app data to Tiger Data, and serves the web app from ../web. */
import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
if (existsSync(path.join(here, '.env'))) process.loadEnvFile(path.join(here, '.env'));

const env = process.env;
const PORT = Number(env.PORT) || 8787;
const WEB = path.resolve(here, '../web');
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/* ---------------- Tiger Data (optional until the real URL is filled in) ---------------- */
let db = null;
const dbConfigured = env.DATABASE_URL && !/CHANGE_ME|@HOST\b|:PORT\b/.test(env.DATABASE_URL);

async function initDb() {
  if (!dbConfigured) return console.log('Tiger Data: not configured — data stays on devices.');
  if (!/:\/\/[^:@/]+:[^@]+@/.test(env.DATABASE_URL)) {
    return console.log('Tiger Data: DATABASE_URL has no password — use postgres://tsdbadmin:PASSWORD@host:port/tsdb?sslmode=require');
  }
  try {
    const { default: pg } = await import('pg');
    db = new pg.Pool({ connectionString: env.DATABASE_URL, max: 5 });
    // Same tables the Android app writes to (android/.../data/Tiger.kt).
    await db.query(`CREATE TABLE IF NOT EXISTS app_state (
      device_id text PRIMARY KEY, state jsonb NOT NULL, updated_at timestamptz NOT NULL DEFAULT now())`);
    await db.query(`CREATE TABLE IF NOT EXISTS sc_foods (
      id text PRIMARY KEY, user_id text NOT NULL, time timestamptz NOT NULL, name text NOT NULL,
      source text, barcode text, image_url text, basis text,
      kcal real, protein real, carbs real, sugar real, fat real, sat_fat real, fiber real, salt real,
      grade text, deleted boolean NOT NULL DEFAULT false, updated_at timestamptz NOT NULL DEFAULT now())`);
    await db.query(`CREATE TABLE IF NOT EXISTS sc_vitals (
      user_id text NOT NULL, time timestamptz NOT NULL, heart_rate real, breathing real,
      stress real, hrv_rmssd real, mood text, source text)`);
    await db.query(`SELECT create_hypertable('sc_vitals', 'time', if_not_exists => TRUE)`).catch(() => {});
    await db.query(`CREATE TABLE IF NOT EXISTS sc_state (
      user_id text PRIMARY KEY, state jsonb NOT NULL, updated_at timestamptz NOT NULL DEFAULT now())`);
    await db.query(`CREATE TABLE IF NOT EXISTS sc_users (
      username text PRIMARY KEY, role text NOT NULL DEFAULT 'user', pass_hash text NOT NULL, salt text NOT NULL,
      created_at timestamptz NOT NULL DEFAULT now())`);
    console.log('Tiger Data: connected.');
  } catch (e) {
    console.error('Tiger Data: connection failed —', e.message);
    db = null;
  }
}

/* ---------------- accounts ----------------
   Same scheme as the Android app (data/Accounts.kt): PBKDF2-SHA256, 20,000 rounds, 32-byte hash, hex.
   The admin account comes from server/.env. Logins get a signed token (HMAC with SESSION_SECRET). */
const SECRET = env.SESSION_SECRET || crypto.randomBytes(24).toString('hex');
const ADMIN = (env.ADMIN_USERNAME || 'admin').trim().toLowerCase();
const hashPw = (pw, saltHex) => crypto.pbkdf2Sync(pw, Buffer.from(saltHex, 'hex'), 20000, 32, 'sha256').toString('hex');
const b64 = (x) => Buffer.from(x).toString('base64url');
const sign = (body) => crypto.createHmac('sha256', SECRET).update(body).digest('base64url');

function issue(username, role) {
  const body = b64(JSON.stringify({ u: username, r: role, exp: Date.now() + 30 * 864e5 }));
  return `${body}.${sign(body)}`;
}

/** The signed-in user from "Authorization: Bearer <token>", or null. */
function whoami(req) {
  const t = (req.headers.authorization || '').replace(/^Bearer /, '');
  const [body, mac] = t.split('.');
  if (!body || !mac) return null;
  const good = sign(body);
  if (mac.length !== good.length || !crypto.timingSafeEqual(Buffer.from(mac), Buffer.from(good))) return null;
  try {
    const { u, r, exp } = JSON.parse(Buffer.from(body, 'base64url').toString());
    return exp > Date.now() ? { username: u, role: r } : null;
  } catch { return null; }
}

/** Whose data a request is about: the signed-in user, or (for the admin) ?user=… */
function owner(req, url) {
  const me = whoami(req);
  if (!me) throw Object.assign(new Error('Sign in first'), { status: 401 });
  const asUser = url.searchParams.get('user');
  return me.role === 'admin' && asUser ? asUser : me.username;
}

const validName = (u) => /^[a-z0-9._-]{3,20}$/.test(u);

async function login(req, res) {
  const { username = '', password = '' } = await readJson(req);
  const u = String(username).trim().toLowerCase();
  if (env.ADMIN_PASSWORD && u === ADMIN) {
    return password === env.ADMIN_PASSWORD ? send(res, 200, { token: issue(u, 'admin'), username: u, role: 'admin' }) : send(res, 401, { error: 'Wrong password.' });
  }
  if (!db) return send(res, 503, { error: 'Accounts need Tiger Data — add the database password to server/.env.' });
  const r = await db.query('SELECT role, pass_hash, salt FROM sc_users WHERE username = $1', [u]);
  if (!r.rows[0]) return send(res, 401, { error: `No account called “${u}”.` });
  if (hashPw(String(password), r.rows[0].salt) !== r.rows[0].pass_hash) return send(res, 401, { error: 'Wrong password.' });
  send(res, 200, { token: issue(u, r.rows[0].role), username: u, role: r.rows[0].role });
}

async function signup(req, res) {
  const { username = '', password = '' } = await readJson(req);
  const u = String(username).trim().toLowerCase();
  if (!validName(u)) return send(res, 400, { error: 'Usernames are 3–20 letters, numbers, dots, dashes or underscores.' });
  if (String(password).length < 6) return send(res, 400, { error: 'Use at least 6 characters for the password.' });
  if (u === ADMIN) return send(res, 409, { error: 'That username is taken.' });
  if (!db) return send(res, 503, { error: 'Accounts need Tiger Data — add the database password to server/.env.' });
  const salt = crypto.randomBytes(16).toString('hex');
  const r = await db.query('INSERT INTO sc_users (username, role, pass_hash, salt) VALUES ($1, $2, $3, $4) ON CONFLICT (username) DO NOTHING',
    [u, 'user', hashPw(String(password), salt), salt]);
  if (r.rowCount !== 1) return send(res, 409, { error: 'That username is taken.' });
  send(res, 200, { token: issue(u, 'user'), username: u, role: 'user' });
}

async function users(req, res) {
  const me = whoami(req);
  if (me?.role !== 'admin') return send(res, 403, { error: 'Admins only' });
  if (!db) return send(res, 200, []);
  const r = await db.query('SELECT username, role, created_at FROM sc_users ORDER BY created_at');
  send(res, 200, r.rows.map((x) => ({ username: x.username, role: x.role, created: new Date(x.created_at).getTime() })));
}

/* ---------------- helpers ---------------- */
function send(res, status, body, type = 'application/json') {
  res.writeHead(status, { 'Content-Type': type, ...cors });
  res.end(type === 'application/json' ? JSON.stringify(body) : body);
}
const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
};

async function readJson(req, limit = 8 * 1024 * 1024) {
  let size = 0;
  const chunks = [];
  for await (const c of req) {
    size += c.length;
    if (size > limit) throw Object.assign(new Error('Body too large'), { status: 413 });
    chunks.push(c);
  }
  return chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {};
}

// Retry on "busy" responses — Gemini returns 503 under heavy load.
async function fetchRetry(url, opts, tries = 4) {
  let res;
  for (let i = 0; i < tries; i++) {
    res = await fetch(url, opts);
    if (res.status !== 503 && res.status !== 429) return res;
    await sleep(800 * 2 ** i);
  }
  return res;
}

/* ---------------- routes ---------------- */
async function gemini(req, res) {
  if (!env.GEMINI_API_KEY) return send(res, 503, { error: 'Gemini is not configured on the server.' });
  const { prompt, system, json, image, history = [] } = await readJson(req);
  if (!prompt) return send(res, 400, { error: 'prompt required' });
  const parts = [{ text: String(prompt).slice(0, 8000) }];
  if (image) {
    const [meta, data] = String(image).split(',');
    parts.unshift({ inline_data: { mime_type: (meta.match(/data:(.*?);/) || [])[1] || 'image/jpeg', data } });
  }
  const body = { contents: [...history.slice(-12), { role: 'user', parts }] };
  if (system) body.system_instruction = { parts: [{ text: String(system).slice(0, 8000) }] };
  if (json) body.generationConfig = { responseMimeType: 'application/json' };

  // Configured model first; if it's overloaded (503/429) or unavailable (404), try other Flash models.
  const models = [...new Set([env.GEMINI_MODEL || 'gemini-3.8-flash', 'gemini-3.7-flash', 'gemini-3.6-flash', 'gemini-3.5-flash', 'gemini-flash-latest'])];
  let r, data;
  for (const model of models) {
    r = await fetchRetry(`https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(model)}:generateContent`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-goog-api-key': env.GEMINI_API_KEY },
      body: JSON.stringify(body),
    }, 2);
    data = await r.json().catch(() => ({}));
    if (r.ok || ![404, 429, 503].includes(r.status)) break;
  }
  if (!r.ok) return send(res, r.status, { error: data.error?.message || `Gemini error ${r.status}` });
  const text = (data.candidates?.[0]?.content?.parts || []).map((p) => p.text || '').join('').trim();
  send(res, 200, { text });
}

async function tts(req, res) {
  if (!env.ELEVENLABS_API_KEY) return send(res, 503, { error: 'ElevenLabs is not configured on the server.' });
  const { text } = await readJson(req);
  if (!text) return send(res, 400, { error: 'text required' });
  // The voice the person picked during setup on the phone, so every experience sounds the same.
  let voice = env.ELEVENLABS_VOICE_ID || '21m00Tcm4TlvDq8ikWAM';
  const me = whoami(req);
  if (me && db) {
    const r = await db.query("SELECT state->'settings'->>'voiceId' AS v FROM sc_state WHERE user_id = $1", [me.username]).catch(() => null);
    const v = r?.rows[0]?.v;
    if (v && /^[A-Za-z0-9]{10,40}$/.test(v)) voice = v;
  }
  const r = await fetchRetry(`https://api.elevenlabs.io/v1/text-to-speech/${encodeURIComponent(voice)}?output_format=mp3_44100_128`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'xi-api-key': env.ELEVENLABS_API_KEY },
    body: JSON.stringify({ text: String(text).slice(0, 1200), model_id: 'eleven_multilingual_v2' }),
  });
  if (!r.ok) return send(res, r.status, { error: `ElevenLabs error ${r.status}` });
  res.writeHead(200, { 'Content-Type': 'audio/mpeg', ...cors });
  res.end(Buffer.from(await r.arrayBuffer()));
}

async function state(req, res, url) {
  if (!db) return send(res, 503, { error: 'Database not configured' });
  if (req.method === 'GET') {
    const device = url.searchParams.get('device');
    const r = await db.query('SELECT state, updated_at FROM app_state WHERE device_id = $1', [device]);
    return send(res, 200, r.rows[0] || { state: null });
  }
  const { device, state: s } = await readJson(req);
  if (!device || !s) return send(res, 400, { error: 'device and state required' });
  await db.query(`INSERT INTO app_state (device_id, state, updated_at) VALUES ($1, $2, now())
    ON CONFLICT (device_id) DO UPDATE SET state = EXCLUDED.state, updated_at = now()`, [device, s]);
  send(res, 200, { ok: true });
}

async function vitals(req, res, url) {
  const user = owner(req, url);
  if (!db) return send(res, 202, { ok: false });
  const { t, hr, br, stress, hrv, mood, source } = await readJson(req);
  await db.query('INSERT INTO sc_vitals (user_id, time, heart_rate, breathing, stress, hrv_rmssd, mood, source) VALUES ($1, to_timestamp($2 / 1000.0), $3, $4, $5, $6, $7, $8)',
    [user, t, hr, br, stress, hrv ?? null, mood ?? null, source || 'web']);
  send(res, 200, { ok: true });
}

/* Foods: shared by the phone (barcode scans, photos) and the website. */
const FOOD_COLS = ['kcal', 'protein', 'carbs', 'sugar', 'fat', 'satFat', 'fiber', 'salt'];
async function foods(req, res, url) {
  const user = owner(req, url);
  if (!db) return send(res, 503, { error: 'Database not configured' });
  if (req.method === 'GET') {
    const r = await db.query(
      `SELECT id, time, name, source, barcode, image_url, basis, kcal, protein, carbs, sugar, fat, sat_fat, fiber, salt, grade
       FROM sc_foods WHERE user_id = $1 AND NOT deleted ORDER BY time DESC LIMIT 300`, [user]);
    return send(res, 200, r.rows.map((f) => ({
      id: f.id, t: new Date(f.time).getTime(), name: f.name, source: f.source, barcode: f.barcode,
      image: f.image_url, basis: f.basis, kcal: f.kcal, protein: f.protein, carbs: f.carbs, sugar: f.sugar,
      fat: f.fat, satFat: f.sat_fat, fiber: f.fiber, salt: f.salt, grade: f.grade,
    })));
  }
  if (req.method === 'DELETE') {
    await db.query('UPDATE sc_foods SET deleted = true, updated_at = now() WHERE id = $1 AND user_id = $2', [url.searchParams.get('id'), user]);
    return send(res, 200, { ok: true });
  }
  const f = await readJson(req);
  if (!f.id || !f.name) return send(res, 400, { error: 'id and name required' });
  const n = (k) => (f[k] == null || f[k] === '' ? null : Number(f[k]));
  await db.query(
    `INSERT INTO sc_foods (id, user_id, time, name, source, barcode, image_url, basis, kcal, protein, carbs, sugar, fat, sat_fat, fiber, salt, grade)
     VALUES ($1,$2,to_timestamp($3 / 1000.0),$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17)
     ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, deleted = false, updated_at = now()`,
    [f.id, user, f.t || Date.now(), f.name, f.source || 'manual', f.barcode || null,
      /^https?:/.test(f.image || '') ? f.image : null, f.basis || 'per serving', ...FOOD_COLS.map(n), f.grade || null]);
  send(res, 200, { ok: true });
}

const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.png': 'image/png', '.svg': 'image/svg+xml', '.ico': 'image/x-icon' };
async function serveStatic(res, pathname) {
  const file = path.resolve(WEB, '.' + (pathname === '/' ? '/index.html' : decodeURIComponent(pathname)));
  if (!file.startsWith(WEB)) return send(res, 403, { error: 'forbidden' });
  try {
    const data = await readFile(file);
    res.writeHead(200, { 'Content-Type': TYPES[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
    res.end(data);
  } catch {
    send(res, 404, 'Not found', 'text/plain');
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  try {
    if (req.method === 'OPTIONS') { res.writeHead(204, cors); return res.end(); }
    if (url.pathname === '/api/config') {
      return send(res, 200, {
        gemini: !!env.GEMINI_API_KEY, tts: !!env.ELEVENLABS_API_KEY, db: !!db,
        geminiModel: env.GEMINI_MODEL || 'gemini-3.8-flash',
        solanaMerchant: env.SOLANA_MERCHANT || '', solanaNetwork: env.SOLANA_NETWORK || 'devnet',
      });
    }
    if (url.pathname === '/api/gemini' && req.method === 'POST') return await gemini(req, res);
    if (url.pathname === '/api/tts' && req.method === 'POST') return await tts(req, res);
    if (url.pathname === '/api/state') return await state(req, res, url);
    if (url.pathname === '/api/vitals' && req.method === 'POST') return await vitals(req, res, url);
    if (url.pathname === '/api/login' && req.method === 'POST') return await login(req, res);
    if (url.pathname === '/api/signup' && req.method === 'POST') return await signup(req, res);
    if (url.pathname === '/api/users') return await users(req, res);
    if (url.pathname === '/api/me') return send(res, 200, whoami(req) || { username: null });
    if (url.pathname === '/api/foods') return await foods(req, res, url);
    if (url.pathname.startsWith('/api/')) return send(res, 404, { error: 'not found' });
    return await serveStatic(res, url.pathname);
  } catch (e) {
    console.error(e);
    send(res, e.status || 500, { error: e.message || 'Server error' });
  }
});

await initDb();
server.listen(PORT, '0.0.0.0', () => console.log(`SmartCam running at http://localhost:${PORT}`));
