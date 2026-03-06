# CS6650 Assignment 2: Message Distribution and Queue Management

**Student Name:** Suiyang Mai  
**Date:** March 3, 2026

---

## 1. Git Repository URL

**Repository:** `https://github.com/maisuiyang/cs6650`

### Project Structure

- `/server-v2` - WebSocket server with RabbitMQ queue integration
- `/consumer` - Consumer application (queue to broadcast)
- `/deployment` - ALB configuration and deployment scripts
- `/monitoring` - Monitoring scripts and tools
- `/results` - Test results and screenshots

See README in each directory for running instructions.

---

## 2. Architecture Document

### 2.1 System Architecture

```
Client (send) --> server-v2:8081 (WebSocket)
                        |
                        v
                  RabbitMQ
                  chat.exchange (topic)
                  room.1 ... room.20 (queues)
                        |
                        v
                  Consumer:8082 (consumes & broadcasts)
                        |
                        v
                  Client (receive) <-- WebSocket
```

- **server-v2**: Accepts WebSocket messages, validates JSON, publishes to RabbitMQ (topic exchange `chat.exchange`, routing key `room.{roomId}`), sends ACK to sender.
- **RabbitMQ**: Topic exchange with 20 queues (`room.1` through `room.20`). One queue per room for ordered delivery per room.
- **consumer**: Consumes from all 20 queues, maintains per-room WebSocket sessions, broadcasts each message to all clients connected to that room on port 8082.

### 2.2 Message Flow

1. Client connects to server-v2 (`ws://host:8081/chat/{roomId}`) to send messages.
2. Client connects to consumer (`ws://host:8082/chat/{roomId}`) to receive broadcasts.
3. On send: server-v2 validates message, publishes to exchange with routing key `room.{roomId}`, responds with status OK and messageId.
4. Message lands in queue `room.{roomId}`. Consumer consumes it, broadcasts JSON to all sessions in that room.
5. All clients in the room receive the same message (group chat).

### 2.3 Queue Topology

- **Exchange:** `chat.exchange` (topic, durable).
- **Queues:** `room.1` ... `room.20` (classic, durable).
- **Bindings:** Each queue bound with routing key `room.1` ... `room.20` respectively.

### 2.4 Consumer Threading Model

- Spring AMQP listener concurrency: 20 (configurable via `spring.rabbitmq.listener.simple.concurrency`).
- Single `@RabbitListener` subscribes to all 20 queues. One thread pool handles all queues; messages are processed in order per queue.
- `RoomSessionManager`: `ConcurrentHashMap<roomId, Set<WebSocketSession>>` for thread-safe add/remove/broadcast.

### 2.5 Load Balancing

- ALB in front of multiple server-v2 instances. Target group health check: `/health`, 30s interval, 5s timeout.
- Sticky sessions enabled so each WebSocket connection stays on the same server-v2 instance.
- Idle timeout set to 60s or higher for WebSocket.

### 2.6 Failure Handling

- server-v2: On RabbitMQ publish failure, client can receive error or retry; connection pooling and channel reuse reduce failures.
- consumer: On broadcast failure (e.g. closed session), message is still acked so it is not redelivered; other sessions in the room receive the message.
- Queue unavailability: Spring AMQP retries and reconnects; circuit breaker can be added for production.

---

## 3. Test Results

### 3.1 Single Instance (AWS)

**Environment:** AWS us-west-2; 1× server-v2 behind ALB, RabbitMQ and consumer on separate EC2; client from local Mac.

#### Client Output (32 threads)

**Warmup (32,000 messages)**  
- Wall time: 6.71 s  
- Throughput: 4,772 msg/s  
- Success: 32,000, Failed: 0  

**Main (500,000 messages)**  
- Wall time: 19.00 s  
- Throughput: 26,313 msg/s  
- Success: 500,000, Failed: 0  

**[screenshot: results/hw2-single-instance-client-output.png]**

#### RabbitMQ Queue State

- During and after the run, queue depths (Ready / Total) for `room.1`–`room.20` remained at 0, indicating the consumer kept up with the producer with no backlog.

**[screenshot: results/hw2-rabbitmq-queues.png]**

### 3.2 Load Balanced (2 instances)

**Environment:** Same as 3.1; 2× server-v2 (server1, server2) in target group behind ALB.

#### Client Output (500K messages)

**Warmup (32,000 messages)**  
- Wall time: 4.45 s  
- Throughput: 7,196 msg/s  
- Success: 32,000, Failed: 0  
- Latency: mean 1190 ms, median 1250 ms, p95 2182 ms, p99 2349 ms  

**Main (500,000 messages)**  
- Wall time: 14.84 s  
- Throughput: **33,695 msg/s**  
- Success: 500,000, Failed: 0  
- Latency: mean 4592 ms, median 4924 ms, p95 9029 ms, p99 9489 ms  

**[screenshot: results/hw2-2-instances-client-output.png]**

**Performance vs single instance:** Throughput increased from ~26,313 msg/s to ~33,695 msg/s (~28% improvement); wall time for Main reduced from 19.0 s to 14.8 s. Queue depths for `room.1`–`room.20` remained low (near 0), similar to the single-instance run.

### 3.3 Load Balanced (4 instances)

**Environment:** Same; 4× server-v2 (server1–server4) in target group behind ALB.

#### Client Output (500K messages)

**Warmup (32,000 messages)**  
- Wall time: 3.80 s  
- Throughput: 8,414 msg/s  
- Success: 32,000, Failed: 0  
- Latency: mean 848 ms, median 1036 ms, p95 1742 ms, p99 1960 ms  

**Main (500,000 messages)**  
- Wall time: 10.87 s  
- Throughput: **45,985 msg/s**  
- Success: 500,000, Failed: 0  
- Latency: mean 2200 ms, median 1915 ms, p95 5380 ms, p99 6276 ms  

**[screenshot: results/hw2-4-instances-client-output.png]**

**Performance comparison:** Throughput improved from ~26,313 msg/s (1 instance) and ~33,695 msg/s (2 instances) to ~45,985 msg/s (4 instances), while keeping error rate at 0; latency increases but remains acceptable for this workload, indicating near-linear scaling with four servers. Queue depths across all rooms stayed well below 1000 messages in all three scenarios, so the system maintained stable queues under load.

---

## 4. Configuration Details

| Component   | Value |
|-------------|--------|
| **Queue**   | Exchange `chat.exchange` (topic, durable). Queues `room.1`–`room.20`. Routing key `room.{roomId}`. |
| **Consumer**| Port 8082. Concurrency 20. Prefetch 50. |
| **server-v2** | Port 8081. RabbitMQ host/port via `SPRING_RABBITMQ_HOST`. |
| **ALB**     | Health `/health`, 30s interval, 5s timeout. Sticky sessions on. Idle timeout 60s+. Listener HTTP:80. |
| **Instances** | t2.micro, us-west-2. Target group hw2-websocket-servers (port 8081). |

---