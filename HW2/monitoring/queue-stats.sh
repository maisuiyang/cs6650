#!/usr/bin/env bash
# Fetch queue depths and message rates from RabbitMQ Management API.
# Usage: ./queue-stats.sh [base_url]
# Default: http://localhost:15672 (guest/guest)
# Requires: curl, jq (optional, for pretty output)

BASE="${1:-http://localhost:15672}"
USER="${RABBITMQ_USER:-guest}"
PASS="${RABBITMQ_PASS:-guest}"

echo "=== Queues (name, messages_ready, messages_unacknowledged, message_stats) ==="
curl -s -u "$USER:$PASS" "$BASE/api/queues/%2F" | jq -r '.[] | select(.name | startswith("room.")) | "\(.name)  ready=\(.messages_ready)  unacked=\(.messages_unacknowledged)  publish=\(.message_stats.publish_details.rate // 0)  deliver_get=\(.message_stats.deliver_get_details.rate // 0)"' 2>/dev/null || \
curl -s -u "$USER:$PASS" "$BASE/api/queues/%2F" | jq '.[] | select(.name | startswith("room.")) | {name, messages_ready, messages_unacknowledged}'

echo ""
echo "=== Exchange (chat.exchange) message rates ==="
curl -s -u "$USER:$PASS" "$BASE/api/exchanges/%2F/chat.exchange" | jq '{name, message_stats_in, message_stats_out}' 2>/dev/null || \
curl -s -u "$USER:$PASS" "$BASE/api/exchanges/%2F/chat.exchange"
