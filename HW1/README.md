# CS6650 Assignment 1: WebSocket Chat Server and Client

## Project Structure

```
HW1/
├── server/              # WebSocket server implementation
├── client-part1/        # Basic multithreaded client
├── client-part2/        # Client with performance analysis
└── results/             # Test results and screenshots
```

## Server Implementation

### Features
- WebSocket endpoint: `/chat/{roomId}`
- REST health endpoint: `/health`
- JSON message validation
- Thread-safe message handling
- Deployed on localhost:8081

### Message Format
```json
{
  "userId": "string (1-100000)",
  "username": "string (3-20 chars)",
  "message": "string (1-500 chars)",
  "timestamp": "ISO-8601 timestamp",
  "messageType": "TEXT|JOIN|LEAVE"
}
```


## Client Implementation

### Architecture
- **Message Generator**: Single thread generates all messages, places in queue
- **Sender Workers**: Multiple threads with persistent WebSocket connections
- **Connection Pooling**: Per-room connection reuse
- **Retry Logic**: Up to 5 retries with exponential backoff
- **Metrics Collection**: Latency tracking and performance analysis

### Threading Model
- **Warmup Phase**: 32 threads, 32,000 messages (1,000 per thread)
- **Main Phase**: 32 threads, 500,000 messages total
- **Connection Strategy**: Persistent connections per room, automatic reconnection

## Test Results

### Part 1: Basic Load Testing (Local Environment)

#### Warmup Phase Results
- **Messages**: 32,000
- **Threads**: 32
- **Duration**: 3.82 seconds
- **Throughput**: 8,381 msg/s
- **Success Rate**: 100% (32,000/32,000)
- **Connections**: 636 total, 636 reconnections

#### Main Phase Results
- **Messages**: 500,000
- **Threads**: 32
- **Duration**: 10.53 seconds
- **Throughput**: 47,501 msg/s
- **Success Rate**: 100% (500,000/500,000)
- **Connections**: 1,280 total, 1,280 reconnections

### Part 2: Latency Analysis

#### Warmup Phase Latency
- **Mean**: 193 ms
- **Median**: 182 ms
- **95th Percentile**: 483 ms
- **99th Percentile**: 617 ms
- **Min**: 0 ms
- **Max**: 753 ms

#### Main Phase Latency
- **Mean**: 277 ms
- **Median**: 187 ms
- **95th Percentile**: 815 ms
- **99th Percentile**: 1,157 ms
- **Min**: 0 ms
- **Max**: 1,934 ms

## Running Instructions

### Prerequisites
- Java 17+
- Maven 3.6+

### Start Server
```bash
cd server
mvn spring-boot:run
```

### Run Client Tests
```bash
cd client-part1
mvn clean package -DskipTests
java -cp "target/client-part1-1.0-SNAPSHOT.jar:target/lib/*" com.example.client.Main
```

## AWS EC2 Deployment

### EC2 Instance Configuration
- **Instance Type**: t2.micro (1 vCPU, 1GB RAM)
- **Region**: us-west-2
- **AMI**: Amazon Linux 2
- **Public IP**: 54.189.65.140
- **Security Groups**: Ports 22 (SSH), 8081 (WebSocket)

## Conclusions

The implementation successfully demonstrates:

### Technical Achievements
- **High Throughput**: 47,501 msg/s sustained performance
- **Excellent Reliability**: 100% success rate across all tests
- **Scalable Architecture**: Efficient connection pooling and thread management
- **Robust Error Handling**: Retry mechanisms with exponential backoff
- **Cloud Deployment**: Successful AWS EC2 deployment capability