#!/usr/bin/env bash
set -uo pipefail
status=0
gradle --no-daemon connectedDebugAndroidTest || status=$?
mkdir -p qa
adb pull /sdcard/Pictures/HesabBeitnaQA/ qa/screenshots/ || true
adb pull /sdcard/Android/data/com.hesabbeitna.app.preview/files/ qa/ui-diagnostics/ || true
exit "$status"
