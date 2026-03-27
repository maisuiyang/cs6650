# chat-server-v3

WebSocket entry point on port **8081** with `GET /health` and `GET /api/metrics` (`windowStart`, `windowEnd`, optional `roomId`, `userId`, `topN`). PostgreSQL via `application.properties`. Metrics responses are rate-limited (`metricsApi`) and cached (`metricsSummary`).
