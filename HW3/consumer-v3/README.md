# chat-consumer-v3

Subscribes to room queues, broadcasts to WebSocket clients, and persists messages with batched JDBC (`chat.persistence.*` in `application.properties`), DLQ `db.write.dlq`, and Resilience4j circuit breakers on `roomMessages` and `databaseWrites`. A dedicated scheduler refreshes `analytics_minute`.
