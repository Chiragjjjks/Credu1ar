#!/usr/bin/env bash
# Push a fake SMS or notification through the DEBUG build's real SmsReceiver / ingest pipeline.
#   scripts/inject.sh sms   VM-HDFCBK-S "Rs.450.00 debited from a/c **1234 ..."
#   scripts/inject.sh notif "Google Pay" "Paid ₹312 to Zepto" com.google.android.apps.nbu.paisa.user
#   scripts/inject.sh sample | clear-sample | catchup | wipe
#   scripts/inject.sh sms-at <epoch-ms> VM-KOTAKB-S "Sent Rs.15000.00 ..."   # an SMS with its own timestamp
#   scripts/inject.sh catchup-from <epoch-ms>                            # the "Add <month>" read from that time
#   scripts/inject.sh stmt <file in the app's files dir> [password]      # import a statement PDF; prints the counts
#   scripts/inject.sh stmt-pipe <file in the app's files dir>            # same, served through a pipe (in-memory copy path)
#   scripts/inject.sh stmt-delete <statement id>
# Needs a running emulator/device with a debug APK installed (./gradlew :app:installFullDebug).
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
  sms-at) send "--el at $(q "$2") --es sender $(q "$3") --es body $(q "$4")" ;;
  catchup-from) send "--el catchupfrom $(q "$2")" ;;
  stmt|stmt-pipe)
    # The receiver writes counts only (never text, amounts or the file name) to files/stmt-result.txt.
    $ADB shell "run-as $PKG rm -f files/stmt-result.txt"
    extra="--es stmt $(q "$2")"
    [ "$1" = stmt-pipe ] && extra="$extra --ez pipe true"
    [ -n "${3:-}" ] && extra="$extra --es pw $(q "$3")"
    send "$extra" >/dev/null
    $ADB exec-out run-as $PKG cat files/stmt-result.txt ;;
  stmt-delete) send "--el stmtdelete $(q "$2")" ;;
  *) echo "usage: $0 sms|notif|sample|clear-sample|catchup|wipe|sms-at|catchup-from|stmt|stmt-pipe|stmt-delete ..." >&2; exit 2 ;;
esac
