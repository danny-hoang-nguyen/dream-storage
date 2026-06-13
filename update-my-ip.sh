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

: "${FIREWALL_ID:?FIREWALL_ID not set (define in .deploy.env or export it)}"
SSH_PORT="${SSH_PORT:-22}"

CURRENT_IP=$(curl -s https://api.ipify.org)
if [ -z "$CURRENT_IP" ]; then
  echo "[ERROR] Could not detect public IP"
  exit 1
fi
echo "[*] Public IP: $CURRENT_IP"

OLD_IP=$(doctl compute firewall get "$FIREWALL_ID" -o json \
  | jq -r --arg port "$SSH_PORT" \
      '.[0].inbound_rules[] | select(.protocol=="tcp" and .ports==$port) | .sources.addresses[]?' \
  | head -1)

if [ "$OLD_IP" = "${CURRENT_IP}/32" ]; then
  echo "[*] SSH whitelist already at ${CURRENT_IP}/32 — nothing to do"
  exit 0
fi

echo "[*] Adding new rule: ${CURRENT_IP}/32"
doctl compute firewall add-rules "$FIREWALL_ID" \
  --inbound-rules "protocol:tcp,ports:${SSH_PORT},address:${CURRENT_IP}/32"

if [ -n "$OLD_IP" ] && [ "$OLD_IP" != "null" ]; then
  echo "[*] Removing old rule: $OLD_IP"
  doctl compute firewall remove-rules "$FIREWALL_ID" \
    --inbound-rules "protocol:tcp,ports:${SSH_PORT},address:${OLD_IP}"
fi

echo "[OK] SSH whitelist updated to ${CURRENT_IP}/32"
