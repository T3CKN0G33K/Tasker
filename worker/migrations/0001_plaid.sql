CREATE TABLE items (
  item_id TEXT PRIMARY KEY,
  uid TEXT NOT NULL,
  encrypted_token TEXT NOT NULL,
  institution_name TEXT NOT NULL,
  cursor TEXT,
  last_synced_at INTEGER,
  lock_id TEXT,
  lock_until INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX items_uid ON items(uid);
CREATE TABLE accounts (
  uid TEXT NOT NULL,
  item_id TEXT NOT NULL REFERENCES items(item_id),
  record_id TEXT NOT NULL,
  data TEXT NOT NULL CHECK(json_valid(data)),
  PRIMARY KEY(uid, item_id, record_id)
);
CREATE TABLE transactions (
  uid TEXT NOT NULL,
  item_id TEXT NOT NULL REFERENCES items(item_id),
  record_id TEXT NOT NULL,
  date TEXT NOT NULL,
  data TEXT NOT NULL CHECK(json_valid(data)),
  PRIMARY KEY(uid, item_id, record_id)
);
CREATE INDEX transactions_recent ON transactions(uid, date DESC, record_id);
CREATE TABLE request_limits (
  uid TEXT PRIMARY KEY,
  window INTEGER NOT NULL,
  count INTEGER NOT NULL
);
