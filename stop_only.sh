#!/bin/bash

PID_FILE="/tmp/bot.pid"

if [ ! -f "$PID_FILE" ]; then
  echo "[*] No PID file found, app may not be running."
  exit 0
fi

PID=$(cat "$PID_FILE")
kill "$PID" 2>/dev/null && echo "[*] Stopped app (PID: $PID)" || echo "[!] Process $PID not found."
rm -f "$PID_FILE"
