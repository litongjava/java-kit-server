CREATE TABLE IF NOT EXISTS uni_tts_cache (
  id            INTEGER PRIMARY KEY,
  input         TEXT,
  md5           TEXT,
  path          TEXT,
  lang          TEXT,
  voice         TEXT,
  model         TEXT,
  provider      TEXT,
  creator       TEXT    DEFAULT '',
  create_time   DATETIME NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  updater       TEXT    DEFAULT '',
  update_time   DATETIME NOT NULL DEFAULT (CURRENT_TIMESTAMP),
  deleted       INTEGER NOT NULL DEFAULT 0,
  tenant_id     INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS uni_tts_cache_md5 ON uni_tts_cache (md5);