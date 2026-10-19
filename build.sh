#!/usr/bin/env bash
# OneUI 9 Launcher - one-command build for Termux.
#   bash build.sh
cd "$(dirname "$0")" || exit 1

# 1) Android SDK: reuse the one your KeyAura3 project already uses
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

# 2) Newest installed SDK platform between 33 and 36
API=$(ls "$SDK_DIR/platforms" | sed -n 's/^android-\([0-9][0-9]*\)$/\1/p' | awk '$1>=33 && $1<=36' | sort -n | tail -n 1)
if [ -z "$API" ]; then
  echo "No SDK platform 33-36 found in $SDK_DIR/platforms"
  echo 'Install one, e.g.:  sdkmanager "platforms;android-34"'
  exit 1
fi
echo "Using Android SDK platform $API"

# 3) Termux needs its own aapt2 (the one Gradle downloads is x86-only)
AAPT2_ARG=""
if [ "$(uname -o 2>/dev/null)" = "Android" ] && command -v aapt2 >/dev/null 2>&1; then
  AAPT2_ARG="-Pandroid.aapt2FromMavenOverride=$(command -v aapt2)"
fi

# 4) Build
GRADLE_CMD="${GRADLE_CMD:-sh gradlew}"
if ! $GRADLE_CMD --no-daemon --console=plain -Poneui.sdk="$API" $AAPT2_ARG assembleDebug; then
  echo
  echo "Build failed. Send me the first 'error:' line from above."
  exit 1
fi

# 5) Put the APK in Downloads and open the installer
APK="app/build/outputs/apk/debug/app-debug.apk"
DL="$HOME/storage/downloads"
[ -d "$DL" ] || DL="$HOME/storage/shared/Download"
if [ ! -f "$APK" ]; then
  echo "Build finished but the APK was not found at $APK"
  exit 1
fi
if ! cp -f "$APK" "$DL/OneUI9Launcher.apk"; then
  echo "Could not copy to $DL - the APK is at $PWD/$APK"
  exit 1
fi
echo
echo "APK ready: $DL/OneUI9Launcher.apk"
termux-open "$DL/OneUI9Launcher.apk" 2>/dev/null || echo "Open it from your Downloads folder to install."
echo "After installing: press Home, pick OneUI 9 Launcher, tap Always."
