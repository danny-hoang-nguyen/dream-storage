#!/bin/bash
set -euo pipefail

SOURCE="${REMINDERS_FILE:-/root/telegram-bot/reminders.json}"
BACKUP_DIR="${REMINDERS_BACKUP_DIR:-/root/telegram-bot/backups}"
RETENTION_DAYS="${REMINDERS_BACKUP_RETENTION_DAYS:-14}"

if [ ! -f "$SOURCE" ]; then
  echo "[*] No reminders file at $SOURCE — skipping backup"
  exit 0
fi

mkdir -p "$BACKUP_DIR"
DEST="$BACKUP_DIR/reminders-$(date +%Y%m%d).json"
cp "$SOURCE" "$DEST"
echo "[OK] Backed up to $DEST"

find "$BACKUP_DIR" -name 'reminders-*.json' -type f -mtime +"$RETENTION_DAYS" -print -delete \
  | sed 's/^/[*] Pruned: /' || true
