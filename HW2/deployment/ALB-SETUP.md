# AWS Application Load Balancer Setup (Part 3)

## Architecture

```
Client (WebSocket) --> ALB (sticky) --> [server-v2 instance 1]
                                        [server-v2 instance 2]
                                        [server-v2 instance 3]
                                        [server-v2 instance 4]
                                        --> RabbitMQ --> Consumer(s)
```

## Prerequisites

- 1+ EC2 instances running server-v2 (port 8081)
- RabbitMQ reachable from all server-v2 instances (same VPC or security group)
- Consumer app running (separate instance or same VPC)

## 1. Create Target Group

- **Target type**: Instances
- **Protocol**: HTTP
- **Port**: 8081
- **VPC**: Your EC2 VPC
- **Health check**:
  - Protocol: HTTP
  - Path: `/health`
  - Interval: 30 seconds
  - Timeout: 5 seconds
  - Healthy threshold: 2
  - Unhealthy threshold: 3
- Register your server-v2 EC2 instance(s) with the target group.

## 2. Create Application Load Balancer

- **Scheme**: internet-facing (or internal if needed)
- **Listeners**: Add listener **HTTP:80** (or HTTPS:443 if you have a certificate)
- **Default action**: Forward to the target group created above

## 3. Enable WebSocket and Sticky Sessions

- **Target group** → **Attributes**:
  - **Stickiness**: Enable
  - **Stickiness type**: Load balancer generated cookie
  - **Duration**: 86400 seconds (1 day) or as required
- **ALB** → **Attributes**:
  - **Idle timeout**: 60 seconds or higher (e.g. 3600 for long-lived WebSocket)

## 4. Security Groups

- ALB: Allow inbound 80 (and 443 if used), outbound all.
- server-v2 instances: Allow inbound from ALB security group on port 8081; outbound to RabbitMQ (5672) and to consumer if needed.

## 5. Test

- Get ALB DNS name.
- Connect WebSocket client to `ws://<ALB-DNS>/chat/1` (or `wss://` if using HTTPS).
- Send messages; verify responses and that the same connection stays on the same target (sticky).
- For 2 or 4 instances: register more targets and repeat tests; check ALB metrics for request distribution.

## 6. Optional: Multiple server-v2 Instances

- Launch 2 or 4 EC2 instances (e.g. t2.micro in us-west-2).
- On each: install Java 17, copy server-v2 JAR, run `java -jar chat-server-v2-*.jar --server.port=8081`.
- Point all to the same RabbitMQ (same VPC or allow 5672 in security group).
- Register all instances in the same target group; ALB will distribute new connections across them.
