#!/usr/bin/env bash
set -uo pipefail
status=0
collect_qa() {
  mkdir -p qa
  adb pull /sdcard/Pictures/HesabBeitnaQA/ qa/screenshots/ || true
  adb pull /sdcard/Android/data/com.hesabbeitna.app.preview/files/ qa/ui-diagnostics/ || true
}
trap collect_qa EXIT
# Pixel Launcher is unrelated to app acceptance and can raise a background ANR on
# heavily loaded hosted emulators. Explicit ActivityScenario launches need no home app.
adb shell am force-stop com.google.android.apps.nexuslauncher || true
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
gradle --no-daemon -PpreviewVersionCode=11 connectedDebugAndroidTest || status=$?
mkdir -p qa
if [ "$status" -eq 0 ]; then
  cp qa/previous-delivery/app/build/outputs/apk/debug/app-debug.apk qa/preview-upgrade-baseline.apk
  adb install -r qa/preview-upgrade-baseline.apk || exit $?
  adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || exit $?
  adb shell am instrument -w -e class com.hesabbeitna.app.UxReviewTest#matchedScreens -e uxStage before com.hesabbeitna.app.preview.test/androidx.test.runner.AndroidJUnitRunner > qa/ux-before.txt
  if ! grep -Fq 'OK (1 test)' qa/ux-before.txt; then cat qa/ux-before.txt; exit 1; fi
  adb shell am instrument -w -e class com.hesabbeitna.app.UpgradePersistenceTest#seedBeforeUpdate -e upgradeStage seed com.hesabbeitna.app.preview.test/androidx.test.runner.AndroidJUnitRunner > qa/upgrade-before.txt
  if ! grep -Fq 'OK (1 test)' qa/upgrade-before.txt; then cat qa/upgrade-before.txt; exit 1; fi
  gradle --no-daemon assembleDebug || exit $?
  adb install -r app/build/outputs/apk/debug/app-debug.apk || exit $?
  adb shell am instrument -w -e class com.hesabbeitna.app.UpgradePersistenceTest#verifyAfterUpdate -e upgradeStage verify com.hesabbeitna.app.preview.test/androidx.test.runner.AndroidJUnitRunner > qa/upgrade-after.txt
  if ! grep -Fq 'OK (1 test)' qa/upgrade-after.txt; then cat qa/upgrade-after.txt; exit 1; fi
  printf 'PASS: same-certificate update 11 -> 12 preserved encrypted ledger, full plan, template, balance, theme and glass setting.\n' > qa/upgrade-result.txt
  adb shell am instrument -w -e class com.hesabbeitna.app.UxReviewTest#matchedScreens -e uxStage after com.hesabbeitna.app.preview.test/androidx.test.runner.AndroidJUnitRunner > qa/ux-after.txt
  if ! grep -Fq 'OK (1 test)' qa/ux-after.txt; then cat qa/ux-after.txt; exit 1; fi
  adb shell wm size 840x1680
  adb shell wm density 420
  adb shell settings put system font_scale 1.5
  adb shell settings put secure show_ime_with_hard_keyboard 1
  adb shell am instrument -w -e class com.hesabbeitna.app.UxReviewTest#smallViewport -e uxStage small com.hesabbeitna.app.preview.test/androidx.test.runner.AndroidJUnitRunner > qa/ux-small.txt
  adb shell wm size reset
  adb shell wm density reset
  adb shell settings put system font_scale 1.0
  adb shell settings put secure show_ime_with_hard_keyboard 0
  if ! grep -Fq 'OK (1 test)' qa/ux-small.txt; then cat qa/ux-small.txt; exit 1; fi
  # Icon UI verification needs a real launcher, separate from finance UI acceptance.
  adb shell pm enable com.google.android.apps.nexuslauncher || exit $?
  adb shell am force-stop com.google.android.apps.nexuslauncher || true
  adb shell am instrument -w -e class com.hesabbeitna.app.LauncherIconTest -e iconStage verify com.hesabbeitna.app.preview.test/androidx.test.runner.AndroidJUnitRunner > qa/icon-runtime.txt
  if ! grep -Fq 'OK (1 test)' qa/icon-runtime.txt; then cat qa/icon-runtime.txt; adb pull /sdcard/Pictures/HesabBeitnaQA/ qa/screenshots/ || true; exit 1; fi
fi
exit "$status"
