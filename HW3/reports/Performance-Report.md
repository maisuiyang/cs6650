# CS6650 Assignment 3 — Performance Report

March 27, 2026<br>
Name: Suiyang Mai<br>
GitHub: https://github.com/maisuiyang/cs6650/HW3<br>

Screenshots and the degradation plot are in **`HW3/load-tests/results/`** (the five batch runs are under **`batch-tuning-screenshots/`**). Filenames are in the table below.

<table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;width:100%">
<thead>
<tr><th align="left">Topic</th><th align="left">File (under <code>load-tests/results/</code>)</th></tr>
</thead>
<tbody>
<tr><td>Metrics API — client log / terminal</td><td><code>fig-metrics-api-curl.png</code></td></tr>
<tr><td>RabbitMQ queues</td><td><code>fig-rabbitmq-queue-rates.png</code></td></tr>
<tr><td>Activity Monitor CPU (MAIN)</td><td><code>fig-activity-monitor-cpu-main.png</code></td></tr>
<tr><td>Activity Monitor memory (MAIN)</td><td><code>fig-activity-monitor-memory-main.png</code></td></tr>
<tr><td>Activity Monitor memory (consumer JVM)</td><td><code>fig-activity-monitor-memory-consumer-main.png</code></td></tr>
<tr><td>Batch tuning (five runs)</td><td><code>batch-tuning-screenshots/hw3-batch-exp01-100x100-main.png</code> … <code>exp05-5000x1000-main.png</code></td></tr>
<tr><td>Degradation curve (script-generated)</td><td><code>fig-degradation-latency-p95.png</code></td></tr>
</tbody>
</table>

---

## 1. Environment

<table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;width:100%">
<thead>
<tr><th align="left" style="width:32%">Component</th><th align="left">Setting</th></tr>
</thead>
<tbody>
<tr><td>Server</td><td><code>server-v3</code>, WebSocket producer, <code>GET /api/metrics</code> on <strong>8081</strong></td></tr>
<tr><td>Consumer</td><td><code>consumer-v3</code>, batched JDBC, RabbitMQ manual ack, DLQ <code>db.write.dlq</code></td></tr>
<tr><td>Broker</td><td>RabbitMQ Management UI <code>http://localhost:15672</code></td></tr>
<tr><td>Database</td><td>PostgreSQL 16 (Homebrew), <code>cs6650_chat</code>, user <code>maisuiyang</code></td></tr>
<tr><td>Load client</td><td><code>HW1/client-part2</code>: WARMUP 32k → BASELINE 500k → MAIN 1M, 32 workers (26 in Endurance)</td></tr>
<tr><td>RabbitMQ listeners (consumer)</td><td><code>concurrency</code> <strong>20</strong>, <code>max-concurrency</code> <strong>20</strong>, <code>prefetch</code> <strong>50</strong>, manual ack (<code>consumer-v3/.../application.properties</code>)</td></tr>
<tr><td>DB writer / stats (consumer)</td><td>One <strong><code>database-writer</code></strong> thread for batched JDBC; <strong><code>statistics-aggregator</code></strong> thread for <code>analytics_minute</code> refresh</td></tr>
<tr><td>Resilience4j (consumer)</td><td><code>roomMessages</code> / <code>databaseWrites</code> circuit breakers (see <code>application.properties</code> for window size, failure rate, open-state wait)</td></tr>
<tr><td>Resilience4j (server)</td><td><code>metricsApi</code> rate limiter <strong>60</strong> calls / <strong>1 s</strong> (<code>server-v3/.../application.properties</code>)</td></tr>
</tbody>
</table>

Final consumer persistence: `chat.persistence.batch-size=5000`, `chat.persistence.flush-interval-ms=1000` (`consumer-v3/src/main/resources/application.properties`).

---

## 2. Write performance

### 2.1 Throughput (client aggregate)

The client writes `*-throughput.csv` bucket rows. For **MAIN (1M messages)**, the bucket timestamps in `MAIN-throughput.csv` span **120 s** with **1,000,000** messages counted in the buckets → **≈ 8,333 msg/s** aggregate send-side over that window. **BASELINE (500k)** sums to **500,000** messages in `BASELINE-throughput.csv`; bucket spacing is **10 s**, so the file is coarse but matches a short, high-rate phase.

These numbers are **send-side** (generator + WebSocket workers), not PostgreSQL commits per second. The consumer batches inserts, so `xact_commit` stays small compared to message volume.

MAIN ran at about **8.3k msg/s** for **120 s** (1M messages); BASELINE was **500k** with **10 s** buckets in the CSV, so the throughput view is rougher. End-to-end ACK **p99** was actually **lower on MAIN than BASELINE** here (§2.2), which likely reflects phase timing and batching rather than a simple “more load = always worse latency” pattern.

**p95** ACK latency vs. progress through each phase was plotted from `BASELINE-latency.csv` and `MAIN-latency.csv`. Output: **`load-tests/results/fig-degradation-latency-p95`**

### 2.2 Latency (client ACK, ms)

From `*-latency.csv` (`latency` column), with the client sampling every 10th message (`LATENCY_SAMPLE_EVERY_N = 10`).

| Phase    | Samples |    p50 |    p95 |    p99 | Min |    Max |
| :------- | ------: | -----: | -----: | -----: | --: | -----: |
| BASELINE |  42,104 | 11,188 | 14,710 | 15,022 |   0 | 15,195 |
| MAIN     |   2,398 |  2,216 |  5,872 |  6,260 |   0 |  7,561 |

BASELINE has higher median and tail latency than MAIN in this dataset. Both are end-to-end ACK latency (WebSocket path + server), not JDBC-only.

Room history by time is **database** latency, not the WebSocket load-test path. `EXPLAIN (ANALYZE, BUFFERS)` on `messages` with `room_id = '1'`, `sent_at` in the last day, `ORDER BY sent_at ASC`, `LIMIT 1000`: **Planning Time** 3.43 ms, **Execution Time** **18.15 ms** (1000 rows). Plan: **`idx_messages_sent`** + **`room_id` filter** (18,736 rows removed by filter before `LIMIT`), not **`idx_messages_room_sent`**. MAIN/BASELINE CSV latency is dominated by the full pipeline, not this query shape in isolation.

### 2.2.1 Core query timings (`/api/metrics` SQL shapes)

`EXPLAIN (ANALYZE, BUFFERS)` on **`cs6650_chat`**, rolling **24-hour** window, **`user_id = '61276'`** (highest-volume sender in `messages`), **`room_id = '1'`**.

<table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;width:100%">
<thead>
<tr><th align="left">Query</th><th align="left">Execution</th><th align="left">Plan / notes</th></tr>
</thead>
<tbody>
<tr><td>Room timeline · <code>LIMIT 1000</code></td><td><strong>18.15 ms</strong></td><td><code>idx_messages_sent</code> + <code>room_id</code> filter</td></tr>
<tr><td>User history · <code>LIMIT 2000</code> (161 rows)</td><td><strong>2.95 ms</strong></td><td><code>idx_messages_user_sent</code></td></tr>
<tr><td>Active users · <code>COUNT(DISTINCT)</code> · 1 d</td><td><strong>~4.9–45 s</strong></td><td>~11.9M rows, <strong>over 500 ms</strong> (see diagnostics)</td></tr>
<tr><td>Rooms for user · <code>LIMIT 500</code> (20 rows)</td><td><strong>0.52 ms</strong></td><td><code>idx_ura_user</code> (<code>user_room_activity</code>)</td></tr>
</tbody>
</table>

**Active users (extra notes):** The initial plan used **`idx_messages_user_sent`** with large heap fetches (~**4.9 s**). **`idx_messages_sent (sent_at) INCLUDE (user_id)`** was tested (with **`idx_messages_user_sent`** dropped only inside a rolled-back transaction), yielding an index-only-style scan (**Heap Fetches ~31**), but Postgres still processed ~**11.9M** `user_id` values for **`COUNT(DISTINCT)`** — about **42–45 s** at **`work_mem = 256MB`** (disk spill) and ~**45 s** at **`work_mem = 2GB`** (in-memory sort). The cost is dominated by sort/dedup, not only index choice. A sub-second SLA would likely need rollups, sampling, or a narrower window. The other three queries in the table behaved well on the indexes above.

### 2.3 Batch / flush tuning (five runs)

Five **100k** MAIN configurations with **32** workers compared batch size and flush interval; wall time, aggregate msg/s, and p50 / p95 / p99 were recorded per trial.

<table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;width:100%">
<thead>
<tr>
<th align="left">Trial</th>
<th align="left">Batch</th>
<th align="left">Flush (ms)</th>
<th align="left">Wall (s)</th>
<th align="left">Msg/s</th>
<th align="left">p50</th>
<th align="left">p95</th>
<th align="left">p99</th>
</tr>
</thead>
<tbody>
<tr><td>1</td><td>100</td><td>100</td><td>3.071</td><td>32,563</td><td>1,526</td><td>2,322</td><td>2,467</td></tr>
<tr><td>2</td><td>500</td><td>500</td><td>4.761</td><td>21,004</td><td>1,151</td><td>2,219</td><td>2,475</td></tr>
<tr><td>3</td><td>1,000</td><td>500</td><td>5.186</td><td>19,283</td><td>1,479</td><td>2,430</td><td>2,776</td></tr>
<tr><td>4</td><td>1,000</td><td>1,000</td><td>4.133</td><td>24,196</td><td>1,198</td><td>2,255</td><td>2,506</td></tr>
<tr><td>5</td><td><strong>5,000</strong></td><td><strong>1,000</strong></td><td>3.033</td><td><strong>32,971</strong></td><td>1,506</td><td><strong>2,204</strong></td><td>2,537</td></tr>
</tbody>
</table>

Trial **5** had the best throughput and the lowest p95, so **5000 / 1000 ms** was left in `consumer-v3` `application.properties` for the full BASELINE + MAIN runs.

Terminal captures for trials **1–5** are in **`load-tests/results/batch-tuning-screenshots/`** (names in the first table).

Trials **2–3** were slower overall; smaller batches / shorter flush seemed to hurt sustained msg/s. **5000 / 1000 ms** means a longer possible flush wait but fewer DB round-trips, which worked better here.

---

## 3. System stability

### 3.1 Queues

During BASELINE/MAIN the `room.*` queues in RabbitMQ stayed low on **Ready**; the consumer was keeping up. A Management UI capture is saved as **`load-tests/results/fig-rabbitmq-queue-rates.png`**. After leaving the same page open and refreshing a few minutes later, **Ready** still did not pile up and the rate graph stayed in a similar band, so backlog did not look like a single short spike.

### 3.2 PostgreSQL

- **Connections:** ~20–24 backends to `cs6650_chat` under load (`pg_stat_*`, also `hw3-postgres-db-metrics.txt`).
- **Buffer hit ratio:** ~**90%** from `pg_stat_database` (one snapshot summed across databases gave ~89.9%).
- **Lock waits:** no rows with `wait_event_type` in `Lock` / `LWLock` in the captured samples.
- **Commits/s:** two samples of `xact_commit + xact_rollback` ~20–30 s apart gave ~**0.36–0.43** txn/s; batching explains the gap vs message rate.
- **Query throughput:** QPS was not emphasized as a headline metric because the consumer commits in **large batches**, so **`xact_commit`/s** stays low while row volume stays high. **`SELECT`** during the run was mostly metrics / checks. Per-statement QPS would need **`pg_stat_statements`** on a longer-lived instance.
- **I/O:** `pg_stat_io` shows heavy `client backend` reads/writes during the same period.

### 3.3 Host CPU and memory (macOS)

During **MAIN**, **Activity Monitor** → **CPU** (sorted by `% CPU`) showed **`postgres`** and the two **`java`** processes (server / consumer) as the main CPU users. Captures: **`fig-activity-monitor-cpu-main.png`** and **`fig-activity-monitor-memory-main.png`** under **`load-tests/results/`**.

### 3.4 Process memory

**Activity Monitor → Memory** for the consumer JVM during **MAIN** is **`load-tests/results/fig-activity-monitor-memory-consumer-main.png`**. Over the **12-minute** endurance-style run, RSS stayed roughly flat with no obvious slow climb.

### 3.5 Pools and consumer threading

- **HikariCP:** consumer `maximum-pool-size` **15**, server **20**; consumer `minimum-idle` **3**, server **5** (`application.properties` on both apps).
- **RabbitMQ:** listener **`concurrency` / `max-concurrency` 20**, **`prefetch` 50** (consumer).
- **Write-behind:** **`database-writer`** single-threaded executor drains the persistence queue; **`statistics-aggregator`** single-threaded scheduler runs minute rollups (see `PersistenceExecutorConfiguration`, `SchedulingConfiguration`).

### 3.6 Endurance

Endurance: **26** workers (~**81%** of 32). The run stopped with **Ctrl+C** after **12 minutes**.

`metrics-ENDURANCE-api.json`: `windowStart` `2026-03-27T20:45:00Z`, `windowEnd` `2026-03-27T20:57:00Z` (12 minutes). `metrics-ENDURANCE-room1.json` is in the same folder.

No crashes or forced restarts. `activeUsersInWindow` in that JSON is ~10⁵ for the queried window. Queue depth looked stable on spot-checks of the UI during the longer runs.

**Disk:** Free space on the Mac was fine before and after the endurance window; Postgres **WAL/data** growth wasn’t an issue for these runs.

---

## 4. Metrics API

After each phase the client calls `GET /api/metrics` and saves `metrics-<PHASE>-api.json`. **Client / terminal capture of the same call:** `load-tests/results/fig-metrics-api-curl.png` (day-sized `windowStart` / `windowEnd`).

Example response shape: `core` sections such as `activeUsersInWindow`, and `analytics` with `messagesPerMinute`, `topUsers`, `topRooms`, `participationPatterns`. **`messagesPerMinute`** prefers **`analytics_minute`** (consumer rollup) and falls back to live aggregation on **`messages`** if that table has no rows in the window.

---

## 5. Bottlenecks

Under 1M load the slow part is mostly **persistence** (batched flush + disk) once the pipe is full. The WebSocket client still sees latency from queuing and server work, not JDBC alone.

Mitigations: **5000 / 1000 ms** batching, Hikari limits, DLQ + retry backoff, Resilience4j circuit breakers on `databaseWrites` and `roomMessages`. The metrics HTTP API is additionally guarded by **Resilience4j rate limiting** (`metricsApi`: 60 calls per second per `application.properties`) so accidental client loops cannot stampede Postgres on `/api/metrics`.

Minute-level counts for analytics come from the **`analytics_minute`** table filled by the consumer scheduler (same data a materialized view would cache; refresh is on a timer rather than a DB-native `MATERIALIZED VIEW`).

Trade-off: larger batches improve throughput but stretch worst-case time until flush.
