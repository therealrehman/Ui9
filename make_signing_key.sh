#!/usr/bin/env bash
# Ok Launcher - makes YOUR signing key once (Termux) and writes the 4 values GitHub needs.
#   bash make_signing_key.sh
# The key is what proves that every update comes from you. KEEP oneui9-release.jks SAFE (cloud drive / another phone): without it the app can never be updated.
cd "$(dirname "$0")" || exit 1
if ! command -v keytool >/dev/null 2>&1; then
  echo "Java is missing. Install it:  pkg install openjdk-17    then run this again."
  exit 1
fi
KS="$HOME/oneui9-release.jks"
PW="$HOME/oneui9-release.pass"
if [ -f "$KS" ] && [ -f "$PW" ]; then
  echo "The key already exists (not changed): $KS"
else
  rm -f "$KS" "$PW"
  PASS=$(tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 24)
  if ! keytool -genkeypair -keystore "$KS" -alias oneui9 -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$PASS" -keypass "$PASS" -dname "CN=Ok Launcher, O=oklabs, C=XX" >/dev/null 2>&1; then
    echo "Could not create the key."
    rm -f "$KS"
    exit 1
  fi
  echo "$PASS" > "$PW"
  echo "New signing key created: $KS"
fi
PASS=$(cat "$PW")
DL="$HOME/storage/downloads"; [ -d "$DL" ] || DL="$HOME/storage/shared/Download"
if [ ! -d "$DL" ]; then echo "Run  termux-setup-storage  first, then this again."; exit 1; fi
cp -f "$KS" "$DL/oneui9-release.jks"
OUT="$DL/OkLauncher-github-secrets.txt"
{
  echo "Add these 4 secrets in GitHub: your repo > Settings > Secrets and variables > Actions > New repository secret."
  echo "Name = the first word, Secret = everything after the colon (no spaces around it)."
  echo
  echo "RELEASE_STORE_PASSWORD:$PASS"
  echo "RELEASE_KEY_PASSWORD:$PASS"
  echo "RELEASE_KEY_ALIAS:oneui9"
  echo "RELEASE_KEYSTORE_B64:$(base64 -w0 "$KS")"
} > "$OUT"
echo
echo "Done. Two files are now in your Downloads folder:"
echo "  oneui9-release.jks            <- BACK THIS UP (cloud drive / another phone)"
echo "  OkLauncher-github-secrets.txt <- open it, copy each value into GitHub; delete it afterwards"
echo "Never post these files or their content anywhere, not even to me."
