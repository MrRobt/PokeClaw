#!/usr/bin/env bash
# adb-stable.sh — 对间歇掉线（offline）的设备做稳健 adb 调用：
#   每次调用前确保设备 online，失败自动 reconnect + 重试，带单次超时。
# 用法：
#   ./adb-stable.sh shell getprop sys.boot_completed
#   ./adb-stable.sh -install /path/app.apk        # 特殊：安装
# 环境变量：SERIAL(默认 emulator-5554) TRIES(默认 6) STEP_TIMEOUT(默认 25)

set -u
ADB="${ADB:-D:/android-sdk/platform-tools/adb.exe}"
SERIAL="${SERIAL:-emulator-5554}"
TRIES="${TRIES:-6}"
STEP_TIMEOUT="${STEP_TIMEOUT:-25}"

ensure_online() {
  for i in $(seq 1 "$TRIES"); do
    state=$(timeout 8 "$ADB" -s "$SERIAL" get-state 2>/dev/null)
    if [ "$state" = "device" ]; then return 0; fi
    "$ADB" reconnect offline >/dev/null 2>&1
    "$ADB" reconnect >/dev/null 2>&1
    timeout 10 "$ADB" -s "$SERIAL" wait-for-device >/dev/null 2>&1
    sleep 1
  done
  return 1
}

run() {
  for i in $(seq 1 "$TRIES"); do
    ensure_online || { echo "[adb-stable] device not online (attempt $i)"; continue; }
    out=$(timeout "$STEP_TIMEOUT" "$ADB" -s "$SERIAL" "$@" 2>&1)
    rc=$?
    if echo "$out" | grep -qiE "device .*not found|device offline|no devices"; then
      echo "[adb-stable] flap on attempt $i, retrying..." >&2
      continue
    fi
    printf '%s\n' "$out"
    return $rc
  done
  echo "[adb-stable] FAILED after $TRIES attempts: $*" >&2
  return 1
}

run "$@"
