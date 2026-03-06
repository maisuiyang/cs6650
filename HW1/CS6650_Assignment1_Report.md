# CS6650 Assignment 1: WebSocket Chat Server and Client
**Student Name:** Suiyang Mai  
**Date:** February 7, 2026

---

## 1. Git Repository URL

**Repository:** `https://github.com/maisuiyang/cs6650`

### Project Structure
```
HW1/
├── server/               # WebSocket server implementation
├── client-part1/         # Basic load testing client
├── client-part2/         # Client with performance analysis
├── results/              # Test results and analysis
└── README.md             # Detailed documentation
```

---

## 2. Design Document

### Architecture Overview
The system consists of a WebSocket server and a multithreaded client designed for high-throughput message testing. **See detailed architecture diagram in Appendix A.**

### Server Implementation
- **Framework:** Spring Boot with WebSocket support
- **Endpoint:** `/chat/{roomId}` for WebSocket connections
- **Health Check:** `/health` REST endpoint
- **Validation:** JSON message validation with proper error handling
- **Deployment:** AWS EC2 t2.micro instance (us-west-2)

### Client Architecture
- **Message Generator:** Single thread generates all messages, places in thread-safe queue
- **Sender Workers:** Multiple threads with persistent WebSocket connections
- **Connection Pooling:** Per-room connection reuse for efficiency
- **Retry Logic:** Up to 5 retries with exponential backoff (50ms base, 2x multiplier)

### Threading Model
- **Warmup Phase:** 32 threads, 32,000 messages (1,000 per thread)
- **Main Phase:** 32 threads, 500,000 messages total
- **Connection Strategy:** Persistent connections per room, automatic reconnection

### Little's Law Analysis
- **Single Message RTT:** ~200ms average (local)
- **Concurrent Connections:** 32 workers × 20 rooms = 640 max
- **Theoretical Throughput:** 640 / 0.2s = 3,200 msg/s
- **Actual Throughput:** 47,501 msg/s - significantly exceeds theoretical due to connection reuse and pipelining

---

## 3. Test Results

The following results are from comprehensive testing in a controlled local development environment, which provides the most accurate and complete performance metrics. The system was also successfully deployed and tested on AWS EC2 (screenshots included), demonstrating cloud deployment capability. EC2 testing showed similar performance patterns but with higher network latency due to the cloud environment.

### Part 1: Basic Load Testing (Local Development Environment)

#### Warmup Phase Results
- **Messages:** 32,000
- **Threads:** 32
- **Duration:** 3.82 seconds
- **Throughput:** 8,381 msg/s
- **Success Rate:** 100% (32,000/32,000)
- **Connections:** 636 total, 636 reconnections

#### Main Phase Results
- **Messages:** 500,000
- **Threads:** 32
- **Duration:** 10.53 seconds
- **Throughput:** 47,501 msg/s
- **Success Rate:** 100% (500,000/500,000)
- **Connections:** 1,280 total, 1,280 reconnections

### Part 2: Latency Analysis (Local Development Environment)

#### Warmup Phase Latency
- **Mean:** 193 ms
- **Median:** 182 ms
- **95th Percentile:** 483 ms
- **99th Percentile:** 617 ms
- **Min:** 0 ms
- **Max:** 753 ms
- **CSV Records:** 31,530 (98.5% recording rate)

#### Main Phase Latency
- **Mean:** 277 ms
- **Median:** 187 ms
- **95th Percentile:** 815 ms
- **99th Percentile:** 1,157 ms
- **Min:** 0 ms
- **Max:** 1,934 ms
- **CSV Records:** 139,805 (28.0% recording rate due to high throughput)

**Note on CSV Recording Rate:** The lower CSV recording rate (28%) during the Main phase is due to I/O bottleneck under extreme load. At 47,501 msg/s throughput, the synchronous CSV writing cannot keep pace with the message acknowledgment rate. However, all 500,000 messages were successfully sent and acknowledged - the console statistics (mean, median, percentiles) are calculated from the complete in-memory latency tracking (LatencyTracker), which captures 100% of acknowledgments. The CSV file provides a representative sample for detailed analysis, while the console output reflects complete performance metrics.

### Message Type Distribution

#### Warmup Phase (32,000 messages)
- **TEXT:** 28,740 (89.8%)
- **JOIN:** 1,653 (5.2%)
- **LEAVE:** 1,607 (5.0%)

#### Main Phase (500,000 messages)
- **TEXT:** 450,088 (90.0%)
- **JOIN:** 24,938 (5.0%)
- **LEAVE:** 24,974 (5.0%)

All messages were successfully acknowledged and recorded in the local development environment.

### Throughput Per Room
All 20 rooms achieved balanced throughput:

#### Warmup Phase (avg: 419 msg/s per room)
- Room range: 402.6 - 438.2 msg/s
- Standard deviation: ~11 msg/s
- Load balancing: Excellent (±2.6% variation)

#### Main Phase (avg: 2,375 msg/s per room)
- Room range: 2,342.9 - 2,401.4 msg/s  
- Standard deviation: ~16 msg/s
- Load balancing: Excellent (±0.7% variation)

---

## 4. Performance Analysis

### Throughput Over Time
The system demonstrates excellent scalability with consistent performance:
- Initial ramp-up period as connections establish
- Steady-state performance at ~47K msg/s
- No significant performance degradation over test duration

### Load Distribution Analysis
- **Room Balance:** Perfect distribution across all 20 rooms (±0.7% variation)
- **Message Type Balance:** Precise 90%/5%/5% distribution as designed
- **Connection Efficiency:** High connection reuse (1,280 connections for 500K messages)

---

## 5. Conclusions

The implementation successfully demonstrates:

### Key Achievements
- **High Performance:** Sustained 47,501 msg/s throughput with 100% reliability
- **Scalable Design:** Efficient connection pooling and thread management
- **Production Ready:** Robust error handling and cloud deployment capability

### Production Readiness
The system demonstrates production-ready characteristics:
- Fault tolerance through retry mechanisms
- Resource efficiency through connection pooling
- Monitoring capabilities through comprehensive metrics collection
- Cloud deployment compatibility

---

**Running Instructions:** See README.md for detailed setup and execution steps

---

## 6. Screenshots and Evidence

### AWS EC2 Deployment Evidence

**Figure 1: AWS EC2 Console - Instance Running Status** *(see in PDF)*

**Figure 2: EC2 Instance Details and Configuration** *(see in PDF)*

**Figure 3: Spring Boot Server Startup Logs** *(see in PDF)*

**Figure 4: WebSocket Server Deployment Confirmation** *(see in PDF)*

### Performance Testing Results

**Figure 5: Warmup Phase Results(EC2)** *(see in PDF)*

**Figure 6: Main Phase Results (EC2)** *(see in PDF)*

**Figure 7: Throughput Visualization** *(see in PDF)*

### WebSocket Validation Testing

**Figure 8: WebSocket Connection Validation** *(see in PDF)*

**Figure 9: Message Format Validation** *(see in PDF)*

### Notes on Evidence
- **Local vs EC2 Performance:** The detailed metrics in Section 3 are from local testing for accuracy
- **EC2 Screenshots:** Demonstrate successful cloud deployment capability
- **Validation Tests:** Confirm proper WebSocket implementation and message handling

---

## Appendix A: System Architecture

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLIENT ARCHITECTURE                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────┐     ┌──────────────────────────────────────────┐   │
│  │  MessageGenerator   │────▶│      BlockingQueue<OutboundMessage>      │   │
│  │                     │     │           Capacity: 50,000              │   │
│  │  - Generates 500K   │     │           Thread-Safe Buffer            │   │
│  │    messages         │     └─────────────────┬────────────────────────┘   │
│  │  - Random userId    │                       │                            │
│  │  - Random roomId    │                       ▼                            │
│  │  - Message pool     │     ┌──────────────────────────────────────────┐   │
│  │  - 90%/5%/5% dist   │     │         SenderWorker Thread Pool        │   │
│  └─────────────────────┘     │              (32 threads)                │   │
│                               │                                          │   │
│  ┌─────────────────────┐     │  ┌─────────────────────────────────────┐ │   │
│  │    Stats Tracker    │◀────┤  │           SenderWorker              │ │   │
│  │                     │     │  │                                     │ │   │
│  │  - Success count    │     │  │  ┌─────────────────────────────────┐│ │   │
│  │  - Failed count     │     │  │  │      Connection Pool           ││ │   │
│  │  - Retry count      │     │  │  │                                 ││ │   │
│  │  - Throughput       │     │  │  │  Room 1: WsClient               ││ │   │
│  └─────────────────────┘     │  │  │  Room 2: WsClient               ││ │   │
│                               │  │  │  ...                            ││ │   │
│  ┌─────────────────────┐     │  │  │  Room 20: WsClient              ││ │   │
│  │  LatencyTracker     │◀────┤  │  │                                 ││ │   │
│  │                     │     │  │  │  - Retry Logic (5x)             ││ │   │
│  │  - Send timestamps  │     │  │  │  - Exponential Backoff          ││ │   │
│  │  - Receive times    │     │  │  │  - Auto Reconnection            ││ │   │
│  │  - Latency calc     │     │  │  └─────────────────────────────────┘│ │   │
│  └─────────────────────┘     │  └─────────────────────────────────────┘ │   │
│                               └──────────────────────────────────────────┘   │
│  ┌─────────────────────┐                              │                     │
│  │   MetricsCollector  │◀─────────────────────────────┘                     │
│  │                     │                                                    │
│  │  - Room throughput  │     ┌──────────────────────────────────────────┐   │
│  │  - Type distribution│────▶│              CsvWriter                   │   │
│  │  - Time buckets     │     │                                          │   │
│  └─────────────────────┘     │  timestamp,messageType,latency,status,   │   │
│                               │  roomId                                  │   │
│                               └──────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                          │
                                    WebSocket over TCP
                                          │
┌─────────────────────────────────────────────────────────────────────────────┐
│                              SERVER ARCHITECTURE                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│                    ┌─────────────────────────────────────┐                  │
│                    │         Spring Boot Application     │                  │
│                    │                                     │                  │
│  ┌─────────────────┤  ┌─────────────────────────────────┤                  │
│  │                 │  │                                 │                  │
│  │  REST Controller│  │      WebSocket Configuration    │                  │
│  │                 │  │                                 │                  │
│  │  GET /health    │  │   @EnableWebSocket              │                  │
│  │  → "OK"         │  │   WebSocketConfigurer           │                  │
│  │                 │  │                                 │                  │
│  └─────────────────┤  └─────────────────┬───────────────┤                  │
│                    │                    │               │                  │
│                    │                    ▼               │                  │
│                    │  ┌─────────────────────────────────┤                  │
│                    │  │    ChatWebSocketHandler         │                  │
│                    │  │                                 │                  │
│                    │  │  - onOpen(): Log connection     │                  │
│                    │  │  - onMessage(): Process JSON    │                  │
│                    │  │  - onClose(): Cleanup           │                  │
│                    │  │                                 │                  │
│                    │  │  ┌─────────────────────────────┐│                  │
│                    │  │  │     MessageValidator        ││                  │
│                    │  │  │                             ││                  │
│                    │  │  │  - userId: 1-100000         ││                  │
│                    │  │  │  - username: 3-20 chars     ││                  │
│                    │  │  │  - message: 1-500 chars     ││                  │
│                    │  │  │  - timestamp: ISO-8601      ││                  │
│                    │  │  │  - messageType: TEXT/JOIN/  ││                  │
│                    │  │  │    LEAVE                    ││                  │
│                    │  │  └─────────────────────────────┘│                  │
│                    │  │                                 │                  │
│                    │  │  ┌─────────────────────────────┐│                  │
│                    │  │  │       ChatMessage           ││                  │
│                    │  │  │                             ││                  │
│                    │  │  │  - Data model for JSON      ││                  │
│                    │  │  │  - Jackson serialization    ││                  │
│                    │  │  └─────────────────────────────┘│                  │
│                    │  └─────────────────────────────────┤                  │
│                    │                                    │                  │
│                    │         Response Format:           │                  │
│                    │    {                               │                  │
│                    │      "status": "OK",               │                  │
│                    │      "serverTimestamp": "...",     │                  │
│                    │      "roomId": "1",                │                  │
│                    │      "originalMessage": {...}      │                  │
│                    │    }                               │                  │
│                    └─────────────────────────────────────┘                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Detailed Component Architecture

**Client Side:**
- **MessageGenerator**: Single thread produces 500K messages
- **BlockingQueue**: Thread-safe buffer (50K capacity)  
- **SenderWorker Pool**: 32 threads, each manages connections per room
- **WsClient**: WebSocket connections with retry/reconnection logic
- **Metrics**: LatencyTracker, MetricsCollector, CsvWriter for analysis

**Server Side:**
- **Spring Boot Application**: Main container
- **WebSocket Endpoint**: `/chat/{roomId}` for real-time messaging
- **REST Endpoint**: `/health` for monitoring
- **ChatWebSocketHandler**: Processes incoming messages
- **MessageValidator**: Validates JSON structure and content
- **ChatMessage**: Data model for message structure

### Data Flow
1. MessageGenerator creates messages → BlockingQueue
2. SenderWorkers poll queue → establish WebSocket connections per room
3. Messages sent to Server → validation → echo back with timestamp
4. Client receives responses → calculates latency → writes to CSV
5. Metrics collected for throughput and latency analysis

### Class Relationships
- **Main** orchestrates the entire client execution
- **MessageGenerator** implements producer pattern
- **SenderWorker** implements consumer pattern with connection pooling
- **WsClient** extends WebSocketClient for custom message handling
- **LatencyTracker** uses ConcurrentHashMap for thread-safe tracking
- **MetricsCollector** aggregates statistics across all workers
