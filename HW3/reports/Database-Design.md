# CS6650 Assignment 3 — Database Design

March 27, 2026<br>
Name: Suiyang Mai<br>

---

## 1. Database choice

**PostgreSQL** backs chat history and analytics. ACID + row-level locking help with idempotent inserts and concurrent readers. B-tree and composite indexes match the access patterns below. JDBC + Spring is a straight fit for `server-v3` / `consumer-v3`. Cost: single-node write throughput caps earlier than some sharded NoSQL designs; this project stays on one primary with batched writes.

---

## 2. Schema

Full DDL: `HW3/database/schema.sql`.

- **`messages`** — `message_id` (UUID, unique), `room_id`, `user_id`, `username`, `body`, `message_type`, `server_id`, `client_ip`, `sent_at`.
- **`user_room_activity`** — last activity per `(user_id, room_id)` for “rooms this user joined.”
- **`analytics_minute`** — per-minute `message_count`, filled by the consumer scheduler. **`GET /api/metrics` → `messagesPerMinute`** reads this table first and falls back to aggregating **`messages`** if the rollup is empty (e.g. fresh database or tests).

---

## 3. Indexing

Room timeline and user history filter on **`(room_id, sent_at)`** and **`(user_id, sent_at)`**; composite B-trees match those predicates so the planner can seek a room or user and walk time in sort order. **`message_id` UNIQUE** supports deduping duplicate deliveries.

**Selectivity (high level):** `room_id` and `user_id` are low-cardinality relative to **`messages`** in load tests (many rooms/users, each with many rows), so `(room_id, sent_at)` and `(user_id, sent_at)` shrink scans to one partition of the timeline. **`idx_messages_sent`** is on **`sent_at` with `INCLUDE (user_id)`** so time-window aggregates that need only **`user_id`** (e.g. `COUNT(DISTINCT user_id)` by `sent_at`) can use an **index-only** scan and avoid heap fetches; after large backfills run **`ANALYZE messages`**. Extra indexes add write amplification; batched inserts amortize that cost. Autovacuum left at defaults.

**Active-user counts:** an exact **`COUNT(DISTINCT user_id)`** over a full day at multi-million-row scale can still exceed a sub-second budget; the performance report records observed latency and mitigation options (rollups, narrower windows, or approximate counts).

**Write-path impact:** each secondary index adds **per-row index maintenance** on `INSERT`. The consumer uses **batched transactions** and **`ON CONFLICT DO NOTHING`**, so the dominant cost remains **flush size and disk**, not single-row index updates; trimming unused indexes would marginally help commits at the expense of read plans.

---

## 4. Scaling

Heavy read traffic on the metrics API → read replica. Write ceiling on one host → partition **`messages`** by time or hash. Current setup: single primary + Hikari + batched inserts.

---

## 5. Backup / recovery

`pg_dump cs6650_chat` before big experiments. Restore with `pg_restore` or replay `schema.sql` on a fresh database. Failed consumer batches land on RabbitMQ **`db.write.dlq`** for inspection and manual replay.

---

## 6. Connection pooling

HikariCP on both apps (`spring.datasource.hikari.*`) caps connections so load tests do not open unbounded sessions to Postgres.
