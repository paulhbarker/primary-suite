#!/usr/bin/env bash
# Build the debug APK, install it on the USB-connected phone, and launch it.
#
# The phone is plugged into Windows, so this uses the Windows adb.exe. WSL's own
# adb can't see USB devices without usbipd. Override with ADB=/path/to/adb.
#
# Usage: scripts/device-smoke.sh [--no-build] [--logcat]
set -euo pipefail

cd "$(dirname "$0")/.."

ADB="${ADB:-/mnt/c/Users/paul/AppData/Local/Android/sdk/platform-tools/adb.exe}"
BUILD=1
LOGCAT=0
for arg in "$@"; do
  case "$arg" in
    --no-build) BUILD=0 ;;
    --logcat) LOGCAT=1 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

if [[ ! -x "$ADB" ]]; then
  echo "adb not found at $ADB (set ADB=...)" >&2
  exit 1
fi

APP_ID="$(sed -n 's/^ *applicationId *= *"\(.*\)".*/\1/p' app/build.gradle.kts)"
APK="app/build/outputs/apk/debug/app-debug.apk"

devices="$("$ADB" devices | tr -d '\r' | awk 'NR>1 && $2=="device" {print $1}')"
count="$(printf '%s' "$devices" | grep -c . || true)"
if [[ "$count" -eq 0 ]]; then
  echo "No device connected. Plug the phone in and allow USB debugging." >&2
  "$ADB" devices -l >&2
  exit 1
elif [[ "$count" -gt 1 && -z "${ANDROID_SERIAL:-}" ]]; then
  echo "More than one device connected; set ANDROID_SERIAL to one of:" >&2
  echo "$devices" >&2
  exit 1
fi

model="$("$ADB" shell getprop ro.product.model | tr -d '\r')"
release="$("$ADB" shell getprop ro.build.version.release | tr -d '\r')"
sdk="$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')"
echo "Device: $model, Android $release (API $sdk)"

if [[ "$BUILD" -eq 1 ]]; then
  bash scripts/gradle-locked.sh assembleDebug
fi

# adb.exe is a Windows program, so it needs a Windows path to the APK.
"$ADB" install -r "$(wslpath -w "$APK")"
"$ADB" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null
echo "Launched $APP_ID"

if [[ "$LOGCAT" -eq 1 ]]; then
  pid="$("$ADB" shell pidof "$APP_ID" | tr -d '\r')"
  "$ADB" logcat --pid="$pid"
fi
