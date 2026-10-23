#!/usr/bin/env bash
# Ok Launcher - Huawei AppGallery helper (Termux).
#   bash appgallery.sh prepare   (once, before building: makes the Accessibility dialog text match what the service really does)
#   bash appgallery.sh check     (after "bash release.sh": checks the signed APK and collects the upload files)
cd "$(dirname "$0")" || exit 1
MODE="${1:-check}"

if [ "$MODE" = "prepare" ]; then
  F="app/src/main/java/com/oklabs/oneui9launcher/HomeSettingsActivity.kt"
  [ -f "$F" ] || { echo "Cannot find $F - run this inside the project folder."; exit 1; }
  if grep -q "only for the optional Quick panel" "$F"; then
    echo "The Accessibility dialog text is already up to date."
  else
    cat > .ag_sed.txt <<'EOF'
s|uses Android's Accessibility service for one thing: to notice that the phone's own status-bar panel opened, so it can close it and show the Quick panel instead. |uses Android's Accessibility service only for the optional Quick panel: to notice that the phone's own status-bar panel opened and show the Quick panel instead, to take a picture of the screen in memory to blur it behind the panel, and, when you tap a button such as Wi-Fi or Bluetooth, to open the phone's own Quick settings for a moment, find that button by its name and press it for you. |
s|It does not read your screen, passwords or messages, and nothing leaves the phone.|It does not read your messages, passwords or other apps, it keeps nothing, and nothing leaves the phone. The launcher works fully without this service, and you can switch it off at any time in Settings > Accessibility.|
EOF
    sed -i -f .ag_sed.txt "$F"
    rm -f .ag_sed.txt
    if grep -q "only for the optional Quick panel" "$F"; then
      echo "OK: the Accessibility dialog text now says what the service really does."
    else
      echo "Could not change the dialog text (your file differs from what I expected)."
      echo "Send me the lines of askAccessibility() in HomeSettingsActivity.kt and I will give you the exact edit."
      exit 1
    fi
  fi
  if grep -q "YOUR_NAME" app/src/main/res/values/strings.xml; then
    echo "WARNING: privacy_url / terms_url in strings.xml still say YOUR_NAME."
  fi
  echo "Next:  bash release.sh   then   bash appgallery.sh check"
  exit 0
fi

# ---- check
SDK_DIR=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | head -n 1)
[ -z "$SDK_DIR" ] && SDK_DIR="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
AAPT2=$(command -v aapt2)
[ -z "$AAPT2" ] && AAPT2=$(ls -d "$SDK_DIR"/build-tools/*/aapt2 2>/dev/null | tail -n 1)
APKSIGNER=$(ls -d "$SDK_DIR"/build-tools/*/apksigner 2>/dev/null | tail -n 1)
APK="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
  if [ -f "app/build/outputs/apk/release/app-release-unsigned.apk" ]; then
    echo "PROBLEM: the release APK is NOT signed. Run  bash release.sh  (it creates / uses keystore.properties)."
  else
    echo "No release APK found. Run  bash release.sh  first."
  fi
  exit 1
fi
echo "APK: $APK ($(du -k "$APK" | cut -f1) KB)"
BAD=0
if [ -n "$AAPT2" ]; then
  B=$("$AAPT2" dump badging "$APK" 2>/dev/null)
  echo "$B" | grep -m1 "^package:" | sed "s/^package: /Package: /"
  echo "$B" | grep -E "^(sdkVersion|targetSdkVersion|native-code)" | sed 's/^/  /'
  if echo "$B" | grep -q "application-debuggable"; then echo "PROBLEM: the APK is debuggable."; BAD=1; else echo "OK: not debuggable"; fi
  if echo "$B" | grep -q "^native-code"; then echo "Note: it has native code (needs a 64-bit part for AppGallery)."; else echo "OK: no native code (pure Kotlin: works as 64-bit)"; fi
  if echo "$B" | grep -q "WRITE_SECURE_SETTINGS"; then echo "PROBLEM: WRITE_SECURE_SETTINGS is still in the APK (src/release/AndroidManifest.xml missing?)."; BAD=1; else echo "OK: no WRITE_SECURE_SETTINGS"; fi
  echo "Permissions asked:"
  echo "$B" | sed -n "s/^uses-permission: name='\([^']*\)'.*/  \1/p"
else
  echo "(aapt2 not found - install it with: pkg install aapt2   to see package / permissions)"
fi
if [ -n "$APKSIGNER" ]; then
  if "$APKSIGNER" verify --print-certs "$APK" 2>&1 | grep -q "Signer #1 certificate DN"; then
    echo "OK: signed ($("$APKSIGNER" verify --print-certs "$APK" 2>&1 | grep -m1 'certificate DN'))"
    if "$APKSIGNER" verify --print-certs "$APK" 2>&1 | grep -qi "Android Debug"; then echo "PROBLEM: signed with the debug key."; BAD=1; fi
  else
    echo "PROBLEM: the signature did not verify."; BAD=1
  fi
else
  echo "(apksigner not found: the name app-release.apk means Gradle signed it with your keystore)"
fi
[ "$BAD" = "1" ] && { echo; echo "Fix the PROBLEM lines above, build again, then re-run this check."; exit 1; }

DL="$HOME/storage/downloads"; [ -d "$DL" ] || DL="$HOME/storage/shared/Download"
OUT="$DL/AppGallery"
mkdir -p "$OUT" || { echo "Cannot create $OUT"; exit 1; }
VN=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' app/build.gradle.kts | head -n 1)
cp -f "$APK" "$OUT/OkLauncher-${VN:-release}-appgallery.apk"
cp -f store/appgallery/icon-216.png "$OUT/icon-216.png" 2>/dev/null
cp -f docs/APPGALLERY.md "$OUT/APPGALLERY.md" 2>/dev/null
echo
echo "Ready in: $OUT"
ls -1 "$OUT"
echo
echo "Upload that APK and icon-216.png in AppGallery Connect. Texts and answers: APPGALLERY.md"
echo "Test the SIGNED APK on your phone first (it needs your debug build uninstalled; use Home settings > Back up first)."
