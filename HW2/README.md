# CS6650 Assignment 2: Message Distribution and Queue Management

## Project Structure

```
HW2/
├── server-v2/     # WebSocket server with RabbitMQ producer
├── consumer/      # Consumer app (queue → broadcast to WebSocket clients)
├── deployment/    # ALB config, deployment scripts
└── monitoring/    # Monitoring scripts and tools
```

## Prerequisites

- Java 17
- Maven
- RabbitMQ (local or EC2)

---

## How to run locally

### 1. Start RabbitMQ

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

Management UI: http://localhost:15672 (guest/guest)

### 2. Start server-v2 (producer)

```bash
cd server-v2
mvn spring-boot:run
```

- Port: **8081**
- WebSocket: `ws://localhost:8081/chat/{roomId}` (clients **send** messages here)
- Health: `curl http://localhost:8081/health`

### 3. Start consumer (broadcast receiver)

In a **second terminal**:

```bash
cd consumer
mvn spring-boot:run
```

- Port: **8082**
- WebSocket: `ws://localhost:8082/chat/{roomId}` (clients **receive** broadcasts here)
- Health: `curl http://localhost:8082/health`

### 4. Test the flow

1. Connect to **consumer** for receiving: `wscat -c ws://localhost:8082/chat/1` (terminal A).
2. In another terminal, connect to **server-v2** for sending: `wscat -c ws://localhost:8081/chat/1` (terminal B).
3. In terminal B send a valid JSON message, e.g.:
   ```json
   {"userId":"1","username":"alice","message":"Hi","timestamp":"2026-02-27T12:00:00Z","messageType":"TEXT"}
   ```
4. Terminal A (consumer) should receive the same message as a broadcast from the queue.

---

## Architecture

- **server-v2**: Receives WebSocket messages, validates, publishes to RabbitMQ, sends ACK to sender.
- **RabbitMQ**: Topic exchange `chat.exchange`, queues `room.1`–`room.20`, routing key `room.{roomId}`.
- **consumer**: Consumes from queues, broadcasts to all WebSocket clients in that room (clients connect to consumer on port 8082 for receiving).

## Configuration

- `server-v2/src/main/resources/application.properties`: port 8081, RabbitMQ host/port.
- `consumer/src/main/resources/application.properties`: port 8082, RabbitMQ host/port.
- Override with env: `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`, etc.

## Part 3 & 4

- **deployment/**: ALB setup (`ALB-SETUP.md`), script to deploy server-v2 (`deploy-server-v2.sh`).
- **monitoring/**: RabbitMQ queue stats script (`queue-stats.sh`), tuning notes in `README.md`.
- Consumer concurrency: edit `consumer/src/main/resources/application.properties` (`spring.rabbitmq.listener.simple.concurrency` / `max-concurrency`) and prefetch for tuning.

