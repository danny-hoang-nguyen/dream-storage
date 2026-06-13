#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"
JAR="$SCRIPT_DIR/target/chatbot-telegram-1.0.0-SNAPSHOT.jar"
APP_LOG="/tmp/bot.log"
PID_FILE="/tmp/bot.pid"

# ── Load .env ─────────────────────────────────────────────
if [ ! -f "$ENV_FILE" ]; then
  echo "[ERROR] .env file not found at $ENV_FILE"
  exit 1
fi
export $(cat "$ENV_FILE" | grep -v '^#' | grep -v '^$' | xargs)

# ── Kill existing instance if running ─────────────────────
if [ -f "$PID_FILE" ]; then
  OLD_PID=$(cat "$PID_FILE")
  kill "$OLD_PID" 2>/dev/null && echo "[*] Stopped existing process (PID: $OLD_PID)"
  rm -f "$PID_FILE"
fi

# ── Start app ─────────────────────────────────────────────
echo "[*] Starting app..."
nohup java -jar "$JAR" > "$APP_LOG" 2>&1 &
APP_PID=$!
echo $APP_PID > "$PID_FILE"

echo "[*] App started (PID: $APP_PID)"
echo "[*] Log: tail -f $APP_LOG"
