#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_ENV="$SCRIPT_DIR/.deploy.env"

if [ -f "$DEPLOY_ENV" ]; then
  set -a
  # shellcheck disable=SC1090
  source "$DEPLOY_ENV"
  set +a
fi

: "${DEPLOY_USER:?DEPLOY_USER not set (define in .deploy.env or export it)}"
: "${DEPLOY_HOST:?DEPLOY_HOST not set (define in .deploy.env or export it)}"
: "${DEPLOY_DIR:?DEPLOY_DIR not set (define in .deploy.env or export it)}"

JAR_NAME="chatbot-telegram-1.0.0-SNAPSHOT.jar"
LOCAL_JAR="target/$JAR_NAME"
REMOTE="$DEPLOY_USER@$DEPLOY_HOST"

echo "[1/5] Building local JAR..."
mvn clean package -DskipTests

if [ ! -f "$LOCAL_JAR" ]; then
  echo "[ERROR] Build did not produce $LOCAL_JAR"
  exit 1
fi

echo "[2/5] Stopping remote service..."
ssh "$REMOTE" "cd $DEPLOY_DIR && ./stop_only.sh"

echo "[3/5] Removing old JAR on remote..."
ssh "$REMOTE" "rm -f $DEPLOY_DIR/target/$JAR_NAME"

echo "[4/5] Copying new JAR to remote..."
ssh "$REMOTE" "mkdir -p $DEPLOY_DIR/target"
scp "$LOCAL_JAR" "$REMOTE:$DEPLOY_DIR/target/$JAR_NAME"

echo "[5/5] Starting remote service..."
ssh "$REMOTE" "cd $DEPLOY_DIR && ./start_only.sh"

echo "[OK] Deploy finished."
