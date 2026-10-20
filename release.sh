#!/usr/bin/env bash
# OneUI 9 Launcher - signed, shrunk release build for Termux.
#   bash release.sh
# The first run creates your own signing key (oneui9-release.jks + keystore.properties).
# KEEP BOTH FILES: every later update must be signed with the same key, or Android refuses to update the app.
cd "$(dirname "$0")" || exit 1

# 1) Android SDK (same lookup as build.sh)
if [ ! -f local.properties ]; then
  for f in "$HOME/storage/shared/Download/KeyAura3/local.properties" \
           "$HOME/KeyAura3/local.properties"; do
    if [ -f "$f" ]; then cp "$f" local.properties; break; fi
  done
fi
SDK_DIR=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null | head -n 1)
[ -z "$SDK_DIR" ] && SDK_DIR="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
if [ -z "$SDK_DIR" ] || [ ! -d "$SDK_DIR/platforms" ]; then
  echo "Android SDK not found."
  echo "Create local.properties here with a line like:  sdk.dir=/path/to/android-sdk"
  exit 1
fi
[ -f local.properties ] || echo "sdk.dir=$SDK_DIR" > local.properties

API=$(ls "$SDK_DIR/platforms" | sed -n 's/^android-\([0-9][0-9]*\)$/\1/p' | awk '$1>=33 && $1<=36' | sort -n | tail -n 1)
if [ -z "$API" ]; then
  echo "No SDK platform 33-36 found in $SDK_DIR/platforms"
  echo 'Install one, e.g.:  sdkmanager "platforms;android-34"'
  exit 1
fi
echo "Using Android SDK platform $API"
if [ "$API" -lt 36 ]; then
  echo "Note: Google Play needs the app to target Android 16 (API 36). Install it with:"
  echo '  sdkmanager "platforms;android-36"   (then run this again)'
fi
if grep -q "YOUR_NAME" app/src/main/res/values/strings.xml; then
  echo "Note: privacy_url / terms_url in app/src/main/res/values/strings.xml still say YOUR_NAME. Fix them before uploading to Play."
fi

# 2) Signing key: made once, then reused
if [ ! -f keystore.properties ]; then
  if ! command -v keytool >/dev/null 2>&1; then
    echo "keytool not found. Install Java first:  pkg install openjdk-17"
    exit 1
  fi
  PASS=$(tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 24)
  rm -f oneui9-release.jks
  if ! keytool -genkeypair -keystore oneui9-release.jks -alias oneui9 \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$PASS" -keypass "$PASS" \
        -dname "CN=OneUI 9 Launcher, O=oklabs, C=XX" >/dev/null 2>&1; then
    echo "Could not create the signing key."
    exit 1
  fi
  {
    echo "storeFile=oneui9-release.jks"
    echo "storePassword=$PASS"
    echo "keyAlias=oneui9"
    echo "keyPassword=$PASS"
  } > keystore.properties
  echo
  echo "New signing key created:  oneui9-release.jks  +  keystore.properties"
  echo "BACK THESE TWO FILES UP (cloud drive, another phone). Lose them and you can not update the app any more."
  echo
fi

# 3) Termux needs its own aapt2 (the one Gradle downloads is x86-only)
AAPT2_ARG=""
if [ "$(uname -o 2>/dev/null)" = "Android" ] && command -v aapt2 >/dev/null 2>&1; then
  AAPT2_ARG="-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)"
fi

# 4) Build
GRADLE_CMD="${GRADLE_CMD:-sh gradlew}"
if ! $GRADLE_CMD --no-daemon --console=plain -Poneui.sdk="$API" $AAPT2_ARG assembleRelease bundleRelease; then
  echo
  echo "Build failed. Send me the first 'error:' line from above."
  exit 1
fi

# 5) Put the signed APK in Downloads
OUT="app/build/outputs/apk/release"
APK="$OUT/app-release.apk"
if [ ! -f "$APK" ]; then
  if [ -f "$OUT/app-release-unsigned.apk" ]; then
    echo "The APK was built but NOT signed (keystore.properties is missing or wrong)."
  else
    echo "Build finished but no APK was found in $OUT"
  fi
  exit 1
fi
DL="$HOME/storage/downloads"
[ -d "$DL" ] || DL="$HOME/storage/shared/Download"
if ! cp -f "$APK" "$DL/OneUI9Launcher-release.apk"; then
  echo "Could not copy to $DL - the APK is at $PWD/$APK"
  exit 1
fi
AAB="app/build/outputs/bundle/release/app-release.aab"
if [ -f "$AAB" ]; then
  cp -f "$AAB" "$DL/OneUI9Launcher-release.aab" && echo "Play Store upload file: $DL/OneUI9Launcher-release.aab"
fi
SIZE=$(du -k "$APK" | cut -f1)
echo
echo "Signed release APK ready (${SIZE} KB): $DL/OneUI9Launcher-release.apk"
echo "Installing over the debug build from build.sh? Android refuses (different key)."
echo "First: Home settings > Back up layout and settings, then uninstall the debug app, install this one, and Restore."
termux-open "$DL/OneUI9Launcher-release.apk" 2>/dev/null || echo "Open it from your Downloads folder to install."
