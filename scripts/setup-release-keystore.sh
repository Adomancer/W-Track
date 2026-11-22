#!/usr/bin/env bash
set -euo pipefail

# Helper to generate a dedicated W-Track release keystore, write a local
# gitignored keystore.properties, and optionally push the 4 GitHub Actions
# signing secrets via `gh secret set`.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_PATH="${KEYSTORE_PATH:-$ROOT_DIR/wtrack-release.jks}"
KEY_ALIAS="${KEY_ALIAS:-wtrack-release}"
KEYSTORE_PROPERTIES="$ROOT_DIR/keystore.properties"

if ! command -v keytool >/dev/null 2>&1; then
  echo "Error: keytool not found on PATH (install JDK 21+)." >&2
  exit 1
fi

if [[ -f "$KEYSTORE_PATH" ]]; then
  echo "Using existing keystore at: $KEYSTORE_PATH"
  read -r -s -p "Enter storePassword: " STORE_PASSWORD
  echo
  read -r -s -p "Enter keyPassword: " KEY_PASSWORD
  echo
else
  echo "Creating new release keystore at: $KEYSTORE_PATH"
  read -r -s -p "Choose storePassword (min 6 chars): " STORE_PASSWORD
  echo
  read -r -s -p "Choose keyPassword (press Enter to reuse storePassword): " KEY_PASSWORD
  echo
  KEY_PASSWORD="${KEY_PASSWORD:-$STORE_PASSWORD}"

  keytool -genkeypair -v \
    -keystore "$KEYSTORE_PATH" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$STORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=W-Track, OU=Mobile, O=Adomancer, L=Jakarta, C=ID"
fi

cat >"$KEYSTORE_PROPERTIES" <<EOF
storeFile=$KEYSTORE_PATH
storePassword=$STORE_PASSWORD
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASSWORD
EOF
chmod 600 "$KEYSTORE_PROPERTIES"
echo "Wrote local signing config (gitignored): $KEYSTORE_PROPERTIES"

if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
  read -r -p "Upload signing secrets to GitHub Actions via gh? [y/N] " UPLOAD_REPLY
  if [[ "$UPLOAD_REPLY" =~ ^[Yy]$ ]]; then
    base64 -w 0 "$KEYSTORE_PATH" | gh secret set ANDROID_KEYSTORE_BASE64
    printf '%s' "$STORE_PASSWORD" | gh secret set ANDROID_KEYSTORE_PASSWORD
    printf '%s' "$KEY_ALIAS" | gh secret set ANDROID_KEY_ALIAS
    printf '%s' "$KEY_PASSWORD" | gh secret set ANDROID_KEY_PASSWORD
    echo "Uploaded all 4 signing secrets to GitHub Actions."
  fi
else
  echo "Skipping GitHub secret upload (gh CLI missing or unauthenticated)."
fi
