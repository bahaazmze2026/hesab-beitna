#!/usr/bin/env bash
set -uo pipefail
status=0
gradle --no-daemon -PpreviewVersionCode=7 connectedDebugAndroidTest || status=$?
mkdir -p qa
if [ "$status" -eq 0 ]; then
  cp app/build/outputs/apk/debug/app-debug.apk qa/preview-upgrade-baseline.apk
  adb install -r qa/preview-upgrade-baseline.apk || exit $?
  adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || exit $?
  adb shell am instrument -w -e class com.hesabbeitna.app.UpgradePersistenceTest#seedBeforeUpdate -e upgradeStage seed com.hesabbeitna.app.preview.test/androidx.test.runner.AndroidJUnitRunner > qa/upgrade-before.txt
  if ! grep -Fq 'OK (1 test)' qa/upgrade-before.txt; then cat qa/upgrade-before.txt; exit 1; fi
  gradle --no-daemon assembleDebug || exit $?
  adb install -r app/build/outputs/apk/debug/app-debug.apk || exit $?
  adb shell am instrument -w -e class com.hesabbeitna.app.UpgradePersistenceTest#verifyAfterUpdate -e upgradeStage verify com.hesabbeitna.app.preview.test/androidx.test.runner.AndroidJUnitRunner > qa/upgrade-after.txt
  if ! grep -Fq 'OK (1 test)' qa/upgrade-after.txt; then cat qa/upgrade-after.txt; exit 1; fi
  printf 'PASS: same-certificate update 7 -> 8 preserved encrypted ledger, full plan, template, balance, theme and glass setting.\n' > qa/upgrade-result.txt
fi
adb pull /sdcard/Pictures/HesabBeitnaQA/ qa/screenshots/ || true
adb pull /sdcard/Android/data/com.hesabbeitna.app.preview/files/ qa/ui-diagnostics/ || true
exit "$status"
