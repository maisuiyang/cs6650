# Chat Consumer

Consumes messages from RabbitMQ (room.1 through room.20) and broadcasts to all WebSocket clients connected to that room.

## Flow

1. Clients connect to this app's WebSocket: `ws://localhost:8082/chat/{roomId}` to receive broadcasts.
2. Clients send messages to server-v2 (port 8081); server-v2 publishes to RabbitMQ.
3. This consumer receives from the queue and broadcasts the message JSON to all sessions in that room.

## Run

```bash
# RabbitMQ must be running (same as server-v2)
mvn spring-boot:run
```

- Port: 8082
- WebSocket: `/chat/{roomId}`
- Health: `GET /health`

## Build

```bash
mvn clean package
java -jar target/chat-consumer-0.0.1-SNAPSHOT.jar
```

## Reliability (Assignment 2 feedback)

- **Manual ACK / NACK**: Listener uses `AcknowledgeMode.MANUAL`. Success → `basicAck`; invalid JSON or missing `roomId` → `basicNack(..., requeue=false)`; transient broadcast errors → `basicNack(..., requeue=true)` once; if RabbitMQ already redelivered the message and it fails again → `requeue=false` (production should use a DLX).
- **Reconnection**: `CachingConnectionFactory` reconnects automatically; `spring.rabbitmq.listener.simple.recovery-interval` and a `ConnectionListener` log connect/disconnect events.
- **Circuit breaker**: Resilience4j `roomMessages` wraps the broadcast path so sustained failures open the circuit; while open, messages are nacked with `requeue=true` until half-open retries succeed.
