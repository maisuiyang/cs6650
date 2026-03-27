CREATE TABLE IF NOT EXISTS messages (
  message_id VARCHAR(36) PRIMARY KEY,
  room_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(255) NOT NULL,
  username VARCHAR(512),
  body TEXT,
  message_type VARCHAR(64),
  server_id VARCHAR(128),
  client_ip VARCHAR(128),
  sent_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_messages_room_sent ON messages (room_id, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_user_sent ON messages (user_id, sent_at DESC);

CREATE TABLE IF NOT EXISTS user_room_activity (
  user_id VARCHAR(255) NOT NULL,
  room_id VARCHAR(64) NOT NULL,
  last_active_at TIMESTAMP WITH TIME ZONE NOT NULL,
  PRIMARY KEY (user_id, room_id)
);

CREATE TABLE IF NOT EXISTS analytics_minute (
  bucket_start TIMESTAMP WITH TIME ZONE PRIMARY KEY,
  message_count BIGINT NOT NULL
);
