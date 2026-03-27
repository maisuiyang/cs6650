-- Run once on an existing database that already has the old non-covering idx_messages_sent.
DROP INDEX IF EXISTS idx_messages_sent;
CREATE INDEX idx_messages_sent ON messages (sent_at) INCLUDE (user_id);
ANALYZE messages;
