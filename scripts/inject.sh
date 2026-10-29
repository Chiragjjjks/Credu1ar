#!/usr/bin/env bash
# Push a fake SMS or notification through the DEBUG build's real SmsReceiver / ingest pipeline.
#   scripts/inject.sh sms   VM-HDFCBK-S "Rs.450.00 debited from a/c **1234 ..."
#   scripts/inject.sh notif "Google Pay" "Paid ₹312 to Zepto" com.google.android.apps.nbu.paisa.user
#   scripts/inject.sh sample | clear-sample | catchup | wipe
# Needs a running emulator/device with the debug APK installed (./gradlew :app:installDebug).
set -euo pipefail
PKG=io.github.chiragjjjks.credu1ar
ADB=${ADB:-adb}
# adb shell re-parses its arguments on the device, so wrap values in single quotes (and escape any inside).
q() { printf "'%s'" "${1//\'/\'\\\'\'}"; }
send() { $ADB shell "am broadcast -a $PKG.DEBUG_SMS -n $PKG/.debug.DebugInjectReceiver $*" | grep -E "Broadcast completed|Error|Exception" || true; }
case "${1:-}" in
  sms)    send "--es sender $(q "$2") --es body $(q "$3")" ;;
  notif)  send "--es sender $(q "$2") --es body $(q "$3") --ez notif true --es pkg $(q "${4:-com.google.android.apps.nbu.paisa.user}")" ;;
  sample) send "--es sample true" ;;
  clear-sample) send "--es sample clear" ;;
  catchup) send "--ez catchup true" ;;
  wipe)   send "--ez wipe true" ;;
  *) echo "usage: $0 sms|notif|sample|clear-sample|catchup|wipe ..." >&2; exit 2 ;;
esac
