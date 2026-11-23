#!/usr/bin/env bash
# Runs the instrumented tests of :app on the Windows emulator from WSL (docs/WORKFLOWS.md section 2).
#
# Exit codes: 0 = `OK (N tests)` with N >= 1; 1 = test/instrumentation failure or no tests;
#             2 = environment problem (a device is already running, emulator did not boot, build failed).
# An emulator is only killed when this script started it. A device that is already running belongs
# to the user and makes the script abort with exit code 2.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WIN_SDK="${WIN_SDK:-/mnt/c/Users/anon/AppData/Local/Android/Sdk}"
AVD="${AVD:-Medium_Phone_API_36.1}"
RUNNER="${RUNNER:-app.plansnap.test/androidx.test.runner.AndroidJUnitRunner}"
BOOT_TIMEOUT="${BOOT_TIMEOUT:-240}"
ADB="$WIN_SDK/platform-tools/adb.exe"
EMULATOR="$WIN_SDK/emulator/emulator.exe"

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

STARTED_EMULATOR=0
OUTPUT_FILE="$(mktemp)"

log() { printf '[instrumented] %s\n' "$*" >&2; }

list_devices() {
  "$ADB" devices 2>/dev/null | tr -d '\r' | awk 'NR > 1 && NF >= 2 { print $1 }'
}

cleanup() {
  local rc=$?
  if [ "$STARTED_EMULATOR" = 1 ]; then
    log "stopping the emulator started by this script"
    "$ADB" emu kill >/dev/null 2>&1 || true
    local i
    for i in $(seq 1 30); do
      [ -z "$(list_devices)" ] && break
      sleep 1
    done
  fi
  rm -f "$OUTPUT_FILE"
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT TERM

[ -x "$ADB" ] || { log "adb.exe not found: $ADB"; exit 2; }

existing="$(list_devices)"
if [ -n "$existing" ]; then
  log "BLOCKED: a device is already running and belongs to the user: $(echo "$existing" | tr '\n' ' ')"
  exit 2
fi

log "building app and test APK"
(cd "$ROOT" && ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest) || { log "Gradle build failed"; exit 2; }
APP_APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
if [ ! -f "$APP_APK" ] || [ ! -f "$TEST_APK" ]; then
  log "APK missing: $APP_APK / $TEST_APK"
  exit 2
fi

log "starting emulator $AVD (headless, read-only)"
STARTED_EMULATOR=1
(cd /mnt/c/Users/anon && nohup "$EMULATOR" -avd "$AVD" -no-window -no-audio -no-boot-anim -no-snapshot -read-only >/dev/null 2>&1 &)

deadline=$((SECONDS + BOOT_TIMEOUT))
booted=0
while [ "$SECONDS" -lt "$deadline" ]; do
  if [ -n "$(list_devices)" ] \
    && [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" = "1" ]; then
    booted=1
    break
  fi
  sleep 3
done
if [ "$booted" != 1 ]; then
  log "emulator did not boot within ${BOOT_TIMEOUT}s"
  exit 2
fi
log "emulator booted"

for apk in "$APP_APK" "$TEST_APK"; do
  win_path="$(wslpath -w "$apk")"
  log "installing $win_path"
  install_out="$("$ADB" install -r -t "$win_path" 2>&1 | tr -d '\r')"
  echo "$install_out" >&2
  if ! echo "$install_out" | grep -q '^Success'; then
    log "install failed"
    exit 2
  fi
done

log "running am instrument ($RUNNER)"
# `am instrument` exits 0 even when tests fail, so the result is decided from the output only.
"$ADB" shell am instrument -w "$RUNNER" 2>&1 | tr -d '\r' | tee "$OUTPUT_FILE"

if grep -q 'INSTRUMENTATION_FAILED\|FAILURES!!!\|Process crashed\|INSTRUMENTATION_RESULT: shortMsg' "$OUTPUT_FILE"; then
  log "FAILED: instrumentation reported failures or crashed"
  exit 1
fi
tests="$(sed -n 's/^OK (\([0-9][0-9]*\) tests\{0,1\})$/\1/p' "$OUTPUT_FILE" | tail -n 1)"
if [ -z "$tests" ] || [ "$tests" -lt 1 ]; then
  log "FAILED: no 'OK (N tests)' with N >= 1 in the output"
  exit 1
fi
log "PASSED: $tests test(s)"
exit 0
