# CS6650 Assignment 3 — Persistence and data management

PostgreSQL-backed chat persistence with `server-v3` (WebSocket producer and metrics API), `consumer-v3` (batched writes, DLQ, circuit breakers), and load-test artifacts under `load-tests/results/`. Messaging uses the same RabbitMQ layout as Assignment 2 (`chat.exchange`, `room.*` queues).
