-- SmartCam schema for Tiger Data (TimescaleDB / Postgres).
-- The server and the Android app create these automatically; running this file by hand is optional.
--
-- In use today — created automatically by the server and the Android app (android/.../data/Tiger.kt):
--   sc_foods  — the food log, shared by the phone and the website
--   sc_vitals — every Presage scan (time-series hypertable)
--   sc_state  — the phone's settings/state as JSON
--   app_state — the website's per-browser state as JSON
CREATE TABLE IF NOT EXISTS sc_foods (
  id text PRIMARY KEY, user_id text NOT NULL, time timestamptz NOT NULL, name text NOT NULL,
  source text, barcode text, image_url text, basis text,
  kcal real, protein real, carbs real, sugar real, fat real, sat_fat real, fiber real, salt real,
  grade text, deleted boolean NOT NULL DEFAULT false, updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS sc_vitals (
  user_id text NOT NULL, time timestamptz NOT NULL, heart_rate real, breathing real,
  stress real, hrv_rmssd real, mood text, source text
);
SELECT create_hypertable('sc_vitals', 'time', if_not_exists => TRUE);
CREATE TABLE IF NOT EXISTS sc_state (
  user_id text PRIMARY KEY, state jsonb NOT NULL, updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS app_state (
  device_id text PRIMARY KEY, state jsonb NOT NULL, updated_at timestamptz NOT NULL DEFAULT now()
);

-- Planned: normalized tables for when accounts are added.

CREATE TABLE users (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email       text UNIQUE NOT NULL,
  step_goal   int  NOT NULL DEFAULT 8000,
  hr_low      int  NOT NULL DEFAULT 50,
  hr_high     int  NOT NULL DEFAULT 100,
  created_at  timestamptz NOT NULL DEFAULT now()
);

-- Time-series: camera scans (Presage) and step counts.
CREATE TABLE vitals (
  user_id   uuid NOT NULL REFERENCES users(id),
  time      timestamptz NOT NULL,
  heart_rate   real,
  breathing    real,
  stress       real,
  source    text NOT NULL DEFAULT 'presage'
);
SELECT create_hypertable('vitals', 'time');
CREATE INDEX ON vitals (user_id, time DESC);

CREATE TABLE steps (
  user_id  uuid NOT NULL REFERENCES users(id),
  time     timestamptz NOT NULL,
  count    int NOT NULL
);
SELECT create_hypertable('steps', 'time');

CREATE TABLE screen_time (
  user_id  uuid NOT NULL REFERENCES users(id),
  time     timestamptz NOT NULL,
  app      text NOT NULL,
  minutes  real NOT NULL
);
SELECT create_hypertable('screen_time', 'time');

CREATE TABLE foods (
  id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id   uuid NOT NULL REFERENCES users(id),
  time      timestamptz NOT NULL DEFAULT now(),
  name      text NOT NULL,
  source    text NOT NULL CHECK (source IN ('barcode', 'photo', 'manual')),
  barcode   text,
  image_url text,              -- store photos in object storage, keep the URL here
  basis     text NOT NULL,     -- 'per serving' or 'per 100 g'
  kcal real, protein real, carbs real, sugar real, fat real, sat_fat real, fiber real, salt real,
  nutri_grade char(1)
);
CREATE INDEX ON foods (user_id, time DESC);

CREATE TABLE alarms (
  id       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id  uuid NOT NULL REFERENCES users(id),
  at_time  time NOT NULL,
  label    text,
  enabled  boolean NOT NULL DEFAULT true
);

CREATE TABLE app_limits (
  user_id   uuid NOT NULL REFERENCES users(id),
  app       text NOT NULL,
  daily_min int  NOT NULL,
  PRIMARY KEY (user_id, app)
);

CREATE TABLE orders (
  id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     uuid NOT NULL REFERENCES users(id),
  time        timestamptz NOT NULL DEFAULT now(),
  item        text NOT NULL,
  usd         numeric(10,2) NOT NULL,
  sol         numeric(18,9),
  tx_signature text,          -- verify on-chain before marking paid
  status      text NOT NULL DEFAULT 'pending'
);
