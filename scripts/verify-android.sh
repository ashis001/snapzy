#!/usr/bin/env bash
#
# End-to-end smoke test for Snapzy on an Android emulator.
#
#   ./scripts/verify-android.sh [serial]
#
# Installs the debug APK, drives the app through the flow with adb input
# taps, and reports whether the process survived each step (a crash shows up
# as a FATAL in logcat). Screenshots land in build/verify/ so the result can
# be eyeballed against docs/reference/.
#
# The tap coordinates assume a 1280x2856 screen (Pixel 9 Pro). Override the
# tap targets at the top if you use a different device.
set -uo pipefail

SERIAL="${1:-$(adb devices | awk 'NR==2 {print $1}')}"
PKG="com.snapzy.app"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SHOTS="$ROOT/build/verify"
APK="$ROOT/composeApp/build/outputs/apk/debug/composeApp-debug.apk"

if [[ -z "$SERIAL" ]]; then
  echo "No emulator/device found. Start one, or pass a serial: $0 <serial>"
  exit 1
fi

mkdir -p "$SHOTS"
adb() { command adb -s "$SERIAL" "$@"; }

shot() {
  local name="$1"
  adb shell screencap -p /sdcard/_snap.png >/dev/null 2>&1
  adb pull /sdcard/_snap.png "$SHOTS/$name.png" >/dev/null 2>&1 \
    && echo "  -> build/verify/$name.png"
}

tap() { adb shell input tap "$1" "$2" >/dev/null 2>&1; }

crash_report() {
  adb logcat -d -t 500 2>/dev/null \
    | grep -E "FATAL EXCEPTION|AndroidRuntime.*com\.snapzy\.app" \
    | tail -6
}

step() {
  echo ""
  echo "==> $1"
}

step "Building debug APK"
(cd "$ROOT" && ./gradlew :composeApp:assembleDebug --console=plain -q) || exit 1

if [[ ! -f "$APK" ]]; then
  echo "APK not found at $APK"
  exit 1
fi

step "Installing on $SERIAL"
adb install -r "$APK" >/dev/null || exit 1
adb shell pm clear "$PKG" >/dev/null 2>&1
adb logcat -c >/dev/null 2>&1

step "Launching"
adb shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 3
shot "01-onboarding"

step "Skipping onboarding"
tap 1120 265          # "Skip"
sleep 2
shot "02-home"

step "Opening the video picker"
tap 284 1754          # the "Video" circle on the springboard
sleep 3
shot "03-picker"

CRASH=$(crash_report)
if [[ -n "$CRASH" ]]; then
  echo "!! Crash detected:"
  echo "$CRASH"
  exit 1
fi

echo ""
echo "==> OK — no crash through onboarding, home and picker."
echo "    Screenshots in build/verify/"
echo "    Compare them against docs/reference/ (real InShot captures)."
