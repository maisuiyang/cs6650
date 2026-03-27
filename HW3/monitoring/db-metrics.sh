#!/usr/bin/env bash
# PostgreSQL activity snapshot. Set PGPASSWORD or use .pgpass before running.
# Usage: PGPASSWORD=secret ./db-metrics.sh cs6650_chat postgres localhost 5432

set -euo pipefail
DB="${1:-cs6650_chat}"
USER="${2:-postgres}"
HOST="${3:-localhost}"
PORT="${4:-5432}"

export PGHOST="$HOST"
export PGPORT="$PORT"
export PGUSER="$USER"
export PGDATABASE="$DB"

psql -v ON_ERROR_STOP=1 <<'SQL'
SELECT now() AS captured_at;
SELECT count(*) AS messages_total FROM messages;
SELECT schemaname, relname, n_live_tup AS est_rows FROM pg_stat_user_tables WHERE relname IN ('messages', 'user_room_activity', 'analytics_minute');
SELECT datname, numbackends AS active_connections FROM pg_stat_database WHERE datname = current_database();
SELECT sum(numbackends) AS total_backends FROM pg_stat_database;
SELECT sum(blks_hit) * 100.0 / NULLIF(sum(blks_hit) + sum(blks_read), 0) AS buffer_hit_ratio FROM pg_stat_database;
SQL
