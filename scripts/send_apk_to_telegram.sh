#!/usr/bin/env bash
# Send the latest Cardcade debug APK to Telegram via the BCNav phone-agent bot.
# Reads credentials from ~/.config/bcnav/phone_agent.env.
set -euo pipefail

ENV_FILE="$HOME/.config/bcnav/phone_agent.env"
APK="$(dirname "$(dirname "$(readlink -f "$0")")")/app/build/outputs/apk/debug/app-debug.apk"

if [[ ! -f "$ENV_FILE" ]]; then
    echo "Missing $ENV_FILE — cannot send APK." >&2
    exit 1
fi
if [[ ! -f "$APK" ]]; then
    echo "APK not found at $APK — run ./gradlew assembleDebug first." >&2
    exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

: "${TELEGRAM_BOT_TOKEN:?TELEGRAM_BOT_TOKEN not set in env file}"
: "${TELEGRAM_CHAT_ID:?TELEGRAM_CHAT_ID not set in env file}"

CAPTION="${1:-Cardcade debug APK}"
TS="$(date -r "$APK" +'%Y-%m-%d %H:%M:%S')"
SIZE_MB="$(du -m "$APK" | cut -f1)"

ZIPDIR="$(mktemp -d)"
trap 'rm -rf "$ZIPDIR"' EXIT
ZIP="$ZIPDIR/cardcade-debug.zip"
(cd "$(dirname "$APK")" && zip -q "$ZIP" "$(basename "$APK")")

curl -sS -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendDocument" \
    -F "chat_id=${TELEGRAM_CHAT_ID}" \
    -F "caption=${CAPTION} — built ${TS} (${SIZE_MB} MB)" \
    -F "document=@${ZIP};type=application/zip" \
    | python3 -c "import sys,json; r=json.load(sys.stdin); sys.exit(0 if r.get('ok') else (print(r) or 1))"
