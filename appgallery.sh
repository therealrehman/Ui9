#!/usr/bin/env bash
# Ok Launcher - Huawei AppGallery helper (Termux).
#   bash appgallery.sh prepare   (once, before building: makes the Accessibility dialog text match what the service really does)
#   bash appgallery.sh check     (checks the signed release APK - from the GitHub build you downloaded, or built here - and collects the upload files)
#   bash appgallery.sh check /path/to/app-release.apk
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
    sed -i 's#YOUR_NAME.github.io/OneUI9Launcher#therealrehman.github.io/Ui9#g' app/src/main/res/values/strings.xml
    echo "Fixed: privacy_url / terms_url pointed to YOUR_NAME; they now point to therealrehman.github.io/Ui9"
  fi
  echo "Next: bash push.sh, then follow docs/APPGALLERY.md section 2 (signed release APK built on GitHub)."
  exit 0
fi

# ---- check
SDK_DIR=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | head -n 1)
[ -z "$SDK_DIR" ] && SDK_DIR="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
AAPT2=$(command -v aapt2)
[ -z "$AAPT2" ] && AAPT2=$(ls -d "$SDK_DIR"/build-tools/*/aapt2 2>/dev/null | tail -n 1)
APKSIGNER=$(command -v apksigner)
[ -z "$APKSIGNER" ] && APKSIGNER=$(ls -d "$SDK_DIR"/build-tools/*/apksigner 2>/dev/null | tail -n 1)
DLD="$HOME/storage/downloads"; [ -d "$DLD" ] || DLD="$HOME/storage/shared/Download"
APK="${2:-}"
if [ -z "$APK" ]; then
  Z=$(ls -t "$DLD"/OkLauncher-release-apk*.zip 2>/dev/null | head -n 1)
  if [ -n "$Z" ]; then
    rm -rf "$HOME/ag_release"; mkdir -p "$HOME/ag_release"
    unzip -oq "$Z" -d "$HOME/ag_release"
    APK="$HOME/ag_release/app-release.apk"
    echo "Using the GitHub build you downloaded: $Z"
  elif [ -f app/build/outputs/apk/release/app-release.apk ]; then
    APK="app/build/outputs/apk/release/app-release.apk"
  fi
fi
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo "No signed release APK found."
  echo "Build it on GitHub (docs/APPGALLERY.md section 2), download the artifact zip OkLauncher-release-apk.zip to Downloads, then run this again."
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
  echo "(apksigner not found: install it with  pkg install apksigner  to check the signature here; the GitHub build already verified it)"
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
echo "Test this SIGNED APK on your phone first (it needs your debug build uninstalled; use Home settings > Back up first)."
