# Chat Server v2 (Queue Producer)

WebSocket server that validates messages and publishes them to RabbitMQ instead of echoing back.

## Features

- WebSocket endpoint: `/chat/{roomId}`
- Health: `/health`
- Validates JSON (userId, username, message, timestamp, messageType)
- Publishes to RabbitMQ topic exchange `chat.exchange`, routing key `room.{roomId}`
- One queue per room: `room.1` through `room.20`
- Sends ACK to client with status, serverTimestamp, messageId, originalMessage

## Queue Message Format

```json
{
  "messageId": "UUID",
  "roomId": "string",
  "userId": "string",
  "username": "string",
  "message": "string",
  "timestamp": "ISO-8601",
  "messageType": "TEXT|JOIN|LEAVE",
  "serverId": "string",
  "clientIp": "string"
}
```

## Run

```bash
# Ensure RabbitMQ is running (e.g. localhost:5672)
mvn spring-boot:run
```

Config: `application.properties` or env `SPRING_RABBITMQ_HOST`, `SPRING_RABBITMQ_PORT`.

## Build JAR

```bash
mvn clean package
java -jar target/chat-server-v2-0.0.1-SNAPSHOT.jar
```
