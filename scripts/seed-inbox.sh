#!/usr/bin/env bash
# EMULATOR ONLY. Writes fictional SMS straight into the phone's SMS inbox with a chosen received time, so the
# first-link history read (CatchUp + HistoryWindow) can be tested on real inbox rows. Needs a rootable emulator
# image (`adb root`); it never touches a real phone's inbox.
#   scripts/seed-inbox.sh add VM-HDFCBK-S "2026-10-20 13:00" "Rs.450.00 debited from a/c **1234 on 20-10-26 ..."
#   scripts/seed-inbox.sh clear      # delete every SMS in the emulator inbox
#   scripts/seed-inbox.sh list
# Times are IST.
set -euo pipefail
ADB=${ADB:-adb}
q() { printf "'%s'" "${1//\'/\'\\\'\'}"; }
[ "$($ADB shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" = "1" ] || [ "$($ADB shell getprop ro.boot.qemu 2>/dev/null | tr -d '\r')" = "1" ] \
  || { echo "refusing: not an emulator" >&2; exit 1; }
$ADB root >/dev/null 2>&1 || true
$ADB wait-for-device
case "${1:-}" in
  add)
    ms="$(TZ=Asia/Kolkata date -j -f "%Y-%m-%d %H:%M:%S" "$3:00" +%s)000"
    $ADB shell "content insert --uri content://sms/inbox --bind address:s:$(q "$2") --bind date:l:$ms --bind read:i:1 --bind body:s:$(q "$4")"
    ;;
  clear) $ADB shell "content delete --uri content://sms" ;;
  list)  $ADB shell "content query --uri content://sms/inbox --projection _id:address:date" ;;
  *) echo "usage: $0 add <sender> '<YYYY-MM-DD HH:MM>' <body> | clear | list" >&2; exit 2 ;;
esac
