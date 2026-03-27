-- Run during load (psql -d cs6650_chat). User: e.g. maisuiyang on Homebrew Mac.

-- Buffer cache hit ratio (cluster-wide sum; for single DB use WHERE datname = current_database())
SELECT round(
  100.0 * sum(blks_hit) / NULLIF(sum(blks_hit) + sum(blks_read), 0),
  2
) AS buffer_hit_pct
FROM pg_stat_database;

SELECT round(
  100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0),
  2
) AS buffer_hit_pct_this_db
FROM pg_stat_database
WHERE datname = current_database();

-- Active connections to this database
SELECT count(*) AS connections
FROM pg_stat_activity
WHERE datname = current_database();

-- Snapshot 1 for transaction rate (repeat after ~20–30 s)
SELECT now() AS t1,
       xact_commit + xact_rollback AS txn_total
FROM pg_stat_database
WHERE datname = current_database();

-- Lock / LWLock waits
SELECT pid, usename, state, wait_event_type, wait_event,
       left(query, 120) AS q
FROM pg_stat_activity
WHERE datname = current_database()
  AND (wait_event_type = 'Lock' OR wait_event_type = 'LWLock');

-- PostgreSQL 16+ I/O stats
SELECT * FROM pg_stat_io;
