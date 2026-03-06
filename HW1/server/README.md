# WebSocket Chat Server

## Overview
Spring Boot WebSocket server for real-time chat messaging with validation and health monitoring.

## Features
- WebSocket endpoint: `/chat/{roomId}`
- REST health endpoint: `/health`
- JSON message validation
- Thread-safe message handling
- Error handling with detailed responses

## Message Format
```json
{
  "userId": "string (1-100000)",
  "username": "string (3-20 chars)",
  "message": "string (1-500 chars)",
  "timestamp": "ISO-8601 timestamp",
  "messageType": "TEXT|JOIN|LEAVE"
}
```

Client encodes message IDs within the message content for latency tracking.

## Running Instructions

### Local Development
```bash
cd server
mvn spring-boot:run
```
Server starts on port 8081.

### AWS EC2 Deployment
```bash
# Build JAR
mvn clean package

# Upload to EC2 (example with actual deployment)
scp -i ~/.ssh/keypair.pem target/chat-server-*.jar ec2-user@54.189.65.140:~/

# Run on EC2
ssh -i ~/.ssh/keypair.pem ec2-user@54.189.65.140
java -jar chat-server-*.jar --server.port=8081
```

**Deployment Details:**
- EC2 Instance: t2.micro (us-west-2)
- Security Group: Ports 22 (SSH) and 8081 (WebSocket) open
- Successfully deployed and tested with client connections

## Testing

### Command Line Testing
```bash
# Health check
curl http://localhost:8081/health

# WebSocket test with wscat
wscat -c ws://localhost:8081/chat/1
```

### Alternative Testing Methods
```bash
# Install wscat if needed
npm install -g wscat

# Test with different rooms
wscat -c ws://localhost:8081/chat/5
```

**Postman WebSocket Testing:**
1. Create new WebSocket request
2. URL: `ws://localhost:8081/chat/1`
3. Send JSON messages for validation testing

**Browser Testing:**
- Use browser developer console WebSocket API
- Test connection: `new WebSocket('ws://localhost:8081/chat/1')`

## Configuration
- Port: 8081 (configurable via `--server.port`)
- CORS: Enabled for all origins
- WebSocket: Supports text messages only