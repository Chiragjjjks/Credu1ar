#!/usr/bin/env bash
# End-to-end smoke test on a running emulator/device, using the DEBUG apk (it carries the SMS injector).
# Proves: the app launches, a real multi-part SMS PDU is captured through the production SmsReceiver, a notification
# for the same payment de-duplicates against it, an OTP is dropped unread, and nothing crashes.
#   scripts/android-smoke.sh app/build/outputs/apk/debug/app-debug.apk
set -euo pipefail
cd "$(dirname "$0")/.."
APK=${1:?usage: android-smoke.sh <debug.apk>}
PKG=io.github.chiragjjjks.credu1ar
ADB=${ADB:-adb}
OUT=${SMOKE_OUT:-test-results}; mkdir -p "$OUT"
fail() { echo "SMOKE FAIL: $*" >&2; $ADB logcat -d -s AndroidRuntime > "$OUT/crash.txt" 2>&1 || true; exit 1; }

$ADB install -r "$APK" >/dev/null
for p in RECEIVE_SMS READ_SMS POST_NOTIFICATIONS; do $ADB shell pm grant $PKG android.permission.$p; done
$ADB logcat -c
$ADB shell am start -n $PKG/.MainActivity >/dev/null; sleep 6
$ADB shell pidof $PKG >/dev/null || fail "app is not running after launch"
./scripts/inject.sh wipe >/dev/null; sleep 1

# Dates must be "today" so the paired SMS + app notification fall inside the 15-minute de-dup window
# whatever day CI runs. A fixed date would drift a day apart from the live notification after midnight.
TODAY_DMY="$(date +%d-%m-%y)"     # 30-10-26
TODAY_DMONY="$(date +%d-%b-%y)"   # 30-Oct-26

./scripts/inject.sh sms VM-HDFCBK-S "Rs.450.00 debited from a/c **1234 on ${TODAY_DMY} to VPA zepto@axisbank(UPI Ref No 630112345678). Not you? Call 18002586161" >/dev/null
./scripts/inject.sh sms AD-ICICIT-S "INR 2,300.00 spent using ICICI Bank Card XX1234 on ${TODAY_DMONY} on AMAZON PAY. Avl Limit: INR 1,00,000.00. If not you, call 1800 2662/SMS BLOCK 1234 to 9215676766." >/dev/null
./scripts/inject.sh sms VM-AMAZON "123456 is your OTP for txn of Rs 450.00 at AMAZON. Valid for 10 mins. Do not share with anyone." >/dev/null
./scripts/inject.sh notif PhonePe "Paid ₹450 to Zepto" com.phonepe.app >/dev/null
sleep 3

q() { ./scripts/dump-db.sh "$1" | tail -n +3 | tr -d ' '; }
ROWS=$(q "select count(*) from txn")
ACCEPTED=$(q "select value from meta where key='n_accepted'")
IGNORED=$(q "select value from meta where key='n_ignored'")
DUPES=$(q "select value from meta where key='n_duplicate'")
ZEPTO=$(q "select category||'/'||amount_paise||'/'||source from txn where merchant_name='Zepto'")
{ echo "rows=$ROWS accepted=$ACCEPTED ignored=$IGNORED duplicates=$DUPES zepto=$ZEPTO"; } | tee "$OUT/smoke.txt"

[ "$ROWS" = "2" ] || fail "expected 2 stored payments (Zepto + Amazon), got $ROWS"
[ "$ACCEPTED" = "2" ] || fail "expected n_accepted=2, got $ACCEPTED"
[ "$IGNORED" = "1" ] || fail "the OTP must be ignored (n_ignored=1), got $IGNORED"
[ "$DUPES" = "1" ] || fail "the notification must de-duplicate against the SMS (n_duplicate=1), got $DUPES"
[ "$ZEPTO" = "quick_commerce/45000/sms" ] || fail "Zepto row wrong or SMS did not win: $ZEPTO"

$ADB shell am force-stop $PKG; $ADB shell am start -n $PKG/.MainActivity >/dev/null; sleep 6
$ADB shell pidof $PKG >/dev/null || fail "app died on relaunch with data"
$ADB exec-out screencap -p > "$OUT/after-capture.png"
if $ADB logcat -d -s AndroidRuntime | grep -q "FATAL EXCEPTION"; then fail "a crash was logged"; fi
echo "SMOKE PASS"
