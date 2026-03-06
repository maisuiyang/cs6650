# Monitoring (Part 4)

## RabbitMQ Management UI

- URL: http://localhost:15672 (or your RabbitMQ host)
- Login: guest/guest (or your credentials)
- **Queues**: Monitor `room.1`–`room.20` for depth (Ready, Unacked, Total) and message rates.
- **Exchanges**: Monitor `chat.exchange` for publish/deliver rates.
- **Connections**: See producer (server-v2) and consumer connections.

## Script: queue-stats.sh

Fetches queue depths and rates from the Management API (requires `curl`; `jq` optional for formatted output).

```bash
chmod +x queue-stats.sh
./queue-stats.sh
# Or with remote RabbitMQ:
RABBITMQ_USER=guest RABBITMQ_PASS=guest ./queue-stats.sh http://your-rabbitmq:15672
```

## Target Metrics (Assignment)

- **Queue depth**: Keep &lt; 1000 messages consistently.
- **Consumer lag**: &lt; 100 ms (queue draining quickly).
- **No message loss** under load.

## Tuning Parameters

- **RabbitMQ**: prefetch count per consumer, connection pool size.
- **server-v2**: connection pool for RabbitTemplate (Spring AMQP default).
- **consumer**: `SimpleRabbitListenerContainerFactory` concurrency (e.g. 10, 20, 40, 80); prefetch.
- **Client (HW1-style)**: test with 64, 128, 256, 512 threads; find optimal throughput.

## Application Metrics (Optional)

- Add Micrometer or Spring Boot Actuator to server-v2 and consumer to expose metrics (e.g. messages published/sec, connections).
- CloudWatch: if on AWS, push custom metrics or use ALB metrics (request count, target response time).
