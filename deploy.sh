#!/bin/bash
set -euo pipefail

REMOTE_USER="root"
REMOTE_HOST="REDACTED"
REMOTE_DIR="/root/telegram-bot"
JAR_NAME="chatbot-telegram-1.0.0-SNAPSHOT.jar"

LOCAL_JAR="target/$JAR_NAME"
REMOTE="$REMOTE_USER@$REMOTE_HOST"

echo "[1/5] Building local JAR..."
mvn clean package -DskipTests

if [ ! -f "$LOCAL_JAR" ]; then
  echo "[ERROR] Build did not produce $LOCAL_JAR"
  exit 1
fi

echo "[2/5] Stopping remote service..."
ssh "$REMOTE" "cd $REMOTE_DIR && ./stop_only.sh"

echo "[3/5] Removing old JAR on remote..."
ssh "$REMOTE" "rm -f $REMOTE_DIR/target/$JAR_NAME"

echo "[4/5] Copying new JAR to remote..."
ssh "$REMOTE" "mkdir -p $REMOTE_DIR/target"
scp "$LOCAL_JAR" "$REMOTE:$REMOTE_DIR/target/$JAR_NAME"

echo "[5/5] Starting remote service..."
ssh "$REMOTE" "cd $REMOTE_DIR && ./start_only.sh"

echo "[OK] Deploy finished."
