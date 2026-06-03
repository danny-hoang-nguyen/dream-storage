#!/bin/bash

PID_FILE="/tmp/dream-storage.pid"

echo "[*] Stopping Dream Storage..."
if [ -f "$PID_FILE" ]; then
  while read pid; do
    kill "$pid" 2>/dev/null && echo "    killed PID $pid" || true
  done < "$PID_FILE"
  rm -f "$PID_FILE"
fi

# Fallback
pkill -f "telegram-claude-bot" 2>/dev/null || true
pkill -f "cloudflared tunnel" 2>/dev/null || true

echo "[*] Stopped."
