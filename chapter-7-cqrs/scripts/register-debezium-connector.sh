#!/usr/bin/env bash
# Register the Debezium PostgreSQL connector with Debezium Connect.
# Run after: docker compose up -d && sleep 30
# Usage: ./register-debezium-connector.sh [Debezium Connect URL]

set -e
CONNECT_URL="${1:-http://localhost:8083}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG=$(cat "$SCRIPT_DIR/../debezium-connector.json")

echo "Registering connector at $CONNECT_URL/connectors..."
curl -s -X POST -H "Content-Type: application/json" \
  --data "$CONFIG" \
  "$CONNECT_URL/connectors"

echo ""
echo "Checking connector status..."
curl -s "$CONNECT_URL/connectors/policy-outbox-connector/status" | jq .
