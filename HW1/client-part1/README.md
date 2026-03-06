# Client Part 1: Basic Load Testing

## Overview
Basic multithreaded WebSocket client for load testing the chat server.

## Features
- Multithreaded message sending
- Connection pooling
- Basic throughput measurement
- Retry mechanism

## Running Instructions

### Prerequisites
- Java 17+
- Maven 3.6+
- Chat server running on localhost:8081

### Build and Run
```bash
cd client-part1
mvn clean package
mvn exec:java -Dexec.mainClass="com.example.client.Main"
```

## Test Configuration
- **Warmup**: 32 threads, 32,000 messages
- **Main**: 32 threads, 500,000 messages
- **Rooms**: Random distribution across 20 rooms
- **Message Types**: 90% TEXT, 5% JOIN, 5% LEAVE

## Output
- Total messages sent
- Success/failure counts
- Throughput (messages/second)
- Connection statistics