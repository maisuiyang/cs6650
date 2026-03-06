# Client Part 2: Performance Analysis

## Overview
Advanced WebSocket client with comprehensive performance analysis and latency tracking.

## Features
- Per-message latency tracking
- CSV output for detailed analysis
- Statistical calculations (mean, median, percentiles)
- Throughput over time analysis
- Room-based performance metrics
- Message type distribution analysis

## Running Instructions

### Prerequisites
- Java 17+
- Maven 3.6+
- Chat server running (local or EC2)

### Build and Run
```bash
cd client-part2
mvn clean package
mvn exec:java -Dexec.mainClass="com.example.client.Main"
```

## Configuration
Update `Main.java` to point to your server:
```java
String host = "127.0.0.1";  // localhost or EC2 IP (e.g., "54.189.65.140")
int port = 8081;
```

## Output Files
- `results/WARMUP-latency.csv` - Per-message latency data
- `results/MAIN-latency.csv` - Per-message latency data
- `results/WARMUP-throughput.csv` - Throughput buckets
- `results/MAIN-throughput.csv` - Throughput buckets

## Performance Metrics
- Mean, median, p95, p99 latency
- Throughput per room
- Message type distribution
- Connection statistics