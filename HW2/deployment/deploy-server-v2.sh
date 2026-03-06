#!/usr/bin/env bash
# Deploy server-v2 JAR to a single EC2 host via SSH/SCP.
# Usage: ./deploy-server-v2.sh <key.pem> <ec2-user@host>
# Example: ./deploy-server-v2.sh mykey.pem ec2-user@54.189.65.140

set -e
KEY="$1"
HOST="$2"
JAR="$(dirname "$0")/../server-v2/target/chat-server-v2-0.0.1-SNAPSHOT.jar"

if [ -z "$KEY" ] || [ -z "$HOST" ]; then
  echo "Usage: $0 <key.pem> <ec2-user@host>"
  exit 1
fi

if [ ! -f "$JAR" ]; then
  echo "Build server-v2 first: cd server-v2 && mvn clean package -DskipTests"
  exit 1
fi

echo "Copying JAR to $HOST..."
scp -i "$KEY" "$JAR" "$HOST:~/chat-server-v2.jar"

echo "To run on EC2:"
echo "  ssh -i $KEY $HOST"
echo "  java -jar chat-server-v2.jar --server.port=8081"
echo "  (Set SPRING_RABBITMQ_HOST to your RabbitMQ private IP or hostname)"
