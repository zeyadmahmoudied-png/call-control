#!/usr/bin/env bash
# Runs on the CI emulator (API 30). Everything here is real: it fails (non-zero) when any step fails.
set -euo pipefail
PKG=com.callcontrol.app

echo "== 1/2 instrumentation tests (Room, DataStore, Compose UI) =="
./gradlew --no-daemon connectedDebugAndroidTest

# connectedDebugAndroidTest uninstalls the app afterwards, so install again, grant the role (what the user does in the system dialog), seed, then ring.
echo "== 2/2 real incoming calls through the emulated modem =="
./gradlew --no-daemon :app:installDebug :app:installDebugAndroidTest
adb shell cmd role add-role-holder --user 0 android.app.role.CALL_SCREENING "$PKG"
adb shell dumpsys role | tee /tmp/roles.txt
grep -F "android.app.role.CALL_SCREENING" /tmp/roles.txt | grep -Fq "$PKG"
adb shell am instrument -w -e e2e seed -e class com.callcontrol.app.E2eCallControlTest \
  "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee /tmp/seed.txt
grep -q "OK (" /tmp/seed.txt

for number in 01012345678 01112345678; do
  adb emu gsm call "$number"
  sleep 6
  adb emu gsm cancel "$number"
  sleep 2
done

adb shell am instrument -w -e e2e verify -e class com.callcontrol.app.E2eCallControlTest \
  "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee /tmp/verify.txt
grep -q "OK (" /tmp/verify.txt
echo "E2E OK: both calls were screened by the real service and logged with the expected decision."
