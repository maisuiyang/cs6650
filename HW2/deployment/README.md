# Deployment

Place here:

- ALB configuration (target groups, health checks, sticky sessions)
- Scripts to deploy server-v2 and consumer to EC2
- Any CloudFormation or Terraform if used

## ALB (Assignment Part 3)

- Target group: WebSocket servers (server-v2 instances)
- Health check: path `/health`, interval 30s, timeout 5s
- Sticky sessions: required for WebSocket
- Idle timeout: > 60 seconds

## Architecture

```
Client -> ALB -> [Server1, Server2, ...] -> RabbitMQ -> Consumer(s)
```
