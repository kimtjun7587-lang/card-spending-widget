#!/usr/bin/env bash
set -euo pipefail
mkdir -p verification
adb install ../original-preservation-fixture/app/build/outputs/apk/debug/app-debug.apk
adb shell run-as com.kimtjun.cardspendingwidget mkdir -p files
printf 'original-data-preserved\n' | adb shell run-as com.kimtjun.cardspendingwidget tee files/preservation_probe >/dev/null
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell pm list packages | sort > verification/installed-packages.txt
gradle :app:connectedDebugAndroidTest
adb shell run-as com.kimtjun.cardspendingwidget cat files/preservation_probe > verification/original-data-after.txt
test "$(tr -d '\r\n' < verification/original-data-after.txt)" = 'original-data-preserved'
grep -F 'package:com.kimtjun.cardspendingwidget.trial' verification/installed-packages.txt
grep -Fx 'package:com.kimtjun.cardspendingwidget' verification/installed-packages.txt
adb shell am start -W -n com.kimtjun.cardspendingwidget.trial/com.kimtjun.cardspendingwidget.MainActivity
adb shell screencap -p /sdcard/trial-screen.png
adb pull /sdcard/trial-screen.png verification/trial-screen.png
adb logcat -d -s AndroidRuntime:E > verification/runtime-errors.txt
