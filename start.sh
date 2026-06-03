#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
JAR="$SCRIPT_DIR/target/telegram-claude-bot-1.0.0-SNAPSHOT.jar"
APP_LOG="/tmp/bot.log"
NGROK_LOG="/tmp/ngrok.log"
PID_FILE="/tmp/dream-storage.pid"
NGROK_DOMAIN="dirk-clouded-daleyza.ngrok-free.dev"

# ── Load .env ─────────────────────────────────────────────
if [ ! -f "$ENV_FILE" ]; then
  echo "[ERROR] .env file not found at $ENV_FILE"
  exit 1
fi
export $(cat "$ENV_FILE" | grep -v '^#' | grep -v '^$' | xargs)

# ── Stop existing processes ────────────────────────────────
echo "[*] Stopping existing processes..."
if [ -f "$PID_FILE" ]; then
  while read pid; do
    kill "$pid" 2>/dev/null && echo "    killed PID $pid" || true
  done < "$PID_FILE"
  rm -f "$PID_FILE"
fi
pkill -f "telegram-claude-bot" 2>/dev/null || true
pkill -f "ngrok" 2>/dev/null || true
sleep 1

# ── Start Spring Boot app ──────────────────────────────────
echo "[*] Starting Dream Storage app..."
java -jar "$JAR" > "$APP_LOG" 2>&1 &
APP_PID=$!
echo $APP_PID >> "$PID_FILE"

echo -n "    Waiting for app"
for i in $(seq 1 20); do
  if curl -s http://localhost:8080/telegram/webhook -o /dev/null 2>/dev/null; then
    break
  fi
  echo -n "."
  sleep 0.5
done
echo " ready (PID: $APP_PID)"

# ── Start ngrok tunnel ─────────────────────────────────────
echo "[*] Starting ngrok tunnel..."
ngrok http 8080 --domain="$NGROK_DOMAIN" --log=stdout > "$NGROK_LOG" 2>&1 &
NGROK_PID=$!
echo $NGROK_PID >> "$PID_FILE"

echo -n "    Waiting for tunnel"
for i in $(seq 1 15); do
  if curl -s --max-time 2 "https://$NGROK_DOMAIN" -o /dev/null 2>/dev/null; then
    break
  fi
  echo -n "."
  sleep 1
done
echo " ready (PID: $NGROK_PID)"

# ── Register Telegram Webhook ──────────────────────────────
echo "[*] Registering Telegram webhook..."
WEBHOOK_URL="https://$NGROK_DOMAIN/telegram/webhook"

curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/deleteWebhook?drop_pending_updates=true" > /dev/null

RESULT=$(curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/setWebhook?url=${WEBHOOK_URL}&secret_token=${TELEGRAM_SECRET_TOKEN}")
OK=$(echo "$RESULT" | python3 -c "import sys,json; print(json.load(sys.stdin).get('ok','false'))")

if [ "$OK" = "True" ]; then
  echo "    Webhook set: $WEBHOOK_URL"
else
  echo "[ERROR] Failed to set webhook: $RESULT"
  exit 1
fi

# ── Done ───────────────────────────────────────────────────
echo ""
echo "=========================================="
echo "  Dream Storage is running!"
echo "  App log:  tail -f $APP_LOG"
echo "  Stop:     ./stop.sh"
echo "=========================================="
