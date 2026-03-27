-- CS6650 Assignment 3: PostgreSQL schema for chat persistence and analytics

CREATE TABLE IF NOT EXISTS messages (
  message_id UUID PRIMARY KEY,
  room_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(255) NOT NULL,
  username VARCHAR(512),
  body TEXT,
  message_type VARCHAR(64),
  server_id VARCHAR(128),
  client_ip VARCHAR(128),
  sent_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_messages_room_sent ON messages (room_id, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_user_sent ON messages (user_id, sent_at DESC);
-- Time-range scans that only need user_id (e.g. COUNT DISTINCT by sent_at) can stay index-only; avoids heap fetches from a plain (sent_at) index.
CREATE INDEX IF NOT EXISTS idx_messages_sent ON messages (sent_at) INCLUDE (user_id);

CREATE TABLE IF NOT EXISTS user_room_activity (
  user_id VARCHAR(255) NOT NULL,
  room_id VARCHAR(64) NOT NULL,
  last_active_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (user_id, room_id)
);

CREATE INDEX IF NOT EXISTS idx_ura_user ON user_room_activity (user_id, last_active_at DESC);

CREATE TABLE IF NOT EXISTS analytics_minute (
  bucket_start TIMESTAMPTZ PRIMARY KEY,
  message_count BIGINT NOT NULL
);
