#!/usr/bin/env bash
# Deploy consumer JAR to EC2 (e.g. consumer instance).
# Usage: ./deploy-consumer.sh <key.pem> <ec2-user@host>
# Example: ./deploy-consumer.sh mykey.pem ec2-user@54.189.65.140

set -e
KEY="$1"
HOST="$2"
JAR="$(dirname "$0")/../consumer/target/chat-consumer-0.0.1-SNAPSHOT.jar"

if [ -z "$KEY" ] || [ -z "$HOST" ]; then
  echo "Usage: $0 <key.pem> <ec2-user@host>"
  exit 1
fi

if [ ! -f "$JAR" ]; then
  echo "Build consumer first: cd consumer && mvn clean package -DskipTests"
  exit 1
fi

echo "Copying JAR to $HOST..."
scp -i "$KEY" "$JAR" "$HOST:~/chat-consumer.jar"

echo "To run on EC2:"
echo "  ssh -i $KEY $HOST"
echo "  export SPRING_RABBITMQ_HOST=<RABBITMQ_PRIVATE_IP>"
echo "  nohup java -jar ~/chat-consumer.jar > consumer.log 2>&1 &"
echo "  curl http://localhost:8082/health"
