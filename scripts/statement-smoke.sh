#!/usr/bin/env bash
# End-to-end bank statement test on a running EMULATOR with a DEBUG apk of either edition (ADR-015, ADR-016).
# Builds PDFs from the fictional Kotak fixtures (scripts/make-test-pdf.py), imports them through the real
# StatementImport.run (PdfRenderer text API -> :core parser -> Repo.importStatement), and checks:
#   a locked PDF asks for its password (and says when it was wrong), then imports 35 rows; a non-PDF and a non-statement
#   are refused; the same statement as a different file merges (+0); the same file again adds nothing; a piped copy hashes
#   the same; the overlapping next statement brings the total to 41; the totals match the fixtures; and Remove keeps the
#   rows another statement covers.
# Full edition only: an SMS for a payment the statement already stored (same UPI reference) upgrades that row instead of
# adding one, and re-reading it from the inbox adds nothing either. Statement edition: no SMS permission, SMS ignored.
#   scripts/statement-smoke.sh app/build/outputs/apk/full/debug/app-full-debug.apk
#   scripts/statement-smoke.sh app/build/outputs/apk/statement/debug/app-statement-debug.apk
set -euo pipefail
cd "$(dirname "$0")/.."
APK=${1:?usage: statement-smoke.sh <debug.apk>}
PKG=io.github.chiragjjjks.credu1ar
ADB=${ADB:-adb}
export ADB
OUT=${SMOKE_OUT:-test-results}; mkdir -p "$OUT/pdf"
FIX=core/src/test/resources/statements
fail() { echo "STATEMENT SMOKE FAIL: $*" >&2; $ADB logcat -d -s AndroidRuntime > "$OUT/statement-crash.txt" 2>&1 || true; exit 1; }
[ "$($ADB shell getprop ro.kernel.qemu 2>/dev/null | tr -d '\r')" = "1" ] || [ "$($ADB shell getprop ro.boot.qemu 2>/dev/null | tr -d '\r')" = "1" ] \
  || fail "refusing: not an emulator (this test writes to the SMS inbox)"

# Fictional fixtures only. The locked copy uses a made-up password.
python3 -I scripts/make-test-pdf.py "$FIX/kotak-asha-2026-09-16_2026-10-16.txt" "$OUT/pdf/sep.pdf" >/dev/null
python3 -I scripts/make-test-pdf.py "$FIX/kotak-asha-2026-09-16_2026-10-16.txt" "$OUT/pdf/sep-locked.pdf" --password 4321 >/dev/null
python3 -I scripts/make-test-pdf.py "$FIX/kotak-asha-2026-10-16_2026-11-16.txt" "$OUT/pdf/oct.pdf" >/dev/null
printf 'Dear guest,\nyour table for two is confirmed for 8 pm.\n' > "$OUT/pdf/note.txt"
python3 -I scripts/make-test-pdf.py "$OUT/pdf/note.txt" "$OUT/pdf/note.pdf" >/dev/null
cp "$FIX/kotak-asha-2026-10-16_2026-11-16.txt" "$OUT/pdf/text-only.pdf" # a .pdf name, but no PDF inside

$ADB install -r "$APK" >/dev/null
FULL=0
if $ADB shell dumpsys package $PKG | grep -q "android.permission.READ_SMS"; then FULL=1; fi
if [ $FULL = 1 ]; then for p in RECEIVE_SMS READ_SMS; do $ADB shell pm grant $PKG android.permission.$p; done; fi
$ADB logcat -c
$ADB shell am start -n $PKG/.MainActivity >/dev/null; sleep 6
$ADB shell pidof $PKG >/dev/null || fail "app is not running after launch"
for f in sep sep-locked oct note text-only; do
  $ADB push "$OUT/pdf/$f.pdf" /data/local/tmp/credu1ar-$f.pdf >/dev/null 2>&1
  $ADB shell "run-as $PKG cp /data/local/tmp/credu1ar-$f.pdf files/$f.pdf"
  $ADB shell rm -f /data/local/tmp/credu1ar-$f.pdf
done
./scripts/inject.sh wipe >/dev/null

q() { ./scripts/dump-db.sh "$1" | tail -n +3 | tr -d ' '; }
expect() { # expect <label> <actual> <wanted>
  echo "$1: $2" | tee -a "$OUT/statement-smoke.txt"
  [ "$2" = "$3" ] || fail "$1: expected '$3', got '$2'"
}
has() { # has <label> <line> <fragment>...
  local label=$1 line=$2; shift 2
  echo "$label: $line" | tee -a "$OUT/statement-smoke.txt"
  for frag in "$@"; do [[ " $line " == *" $frag "* ]] || fail "$label: missing '$frag' in: $line"; done
}
: > "$OUT/statement-smoke.txt"

has "locked, no password"    "$(./scripts/inject.sh stmt sep-locked.pdf)" outcome=needs_password wrong=false
has "locked, wrong password" "$(./scripts/inject.sh stmt sep-locked.pdf 0000)" outcome=needs_password wrong=true
has "locked, right password" "$(./scripts/inject.sh stmt sep-locked.pdf 4321)" outcome=done rows=35 added=35 already=0 complete=true left_out=0
has "not a PDF"              "$(./scripts/inject.sh stmt text-only.pdf)" problem=NOT_PDF added=0
has "not a statement"        "$(./scripts/inject.sh stmt note.pdf)" problem=NOT_A_STATEMENT added=0
expect "rows after the first statement" "$(q "select count(*) from txn")" 35
has "same period, other file" "$(./scripts/inject.sh stmt sep.pdf)" rows=35 added=0 already=35 same_file=false
has "same file again"         "$(./scripts/inject.sh stmt sep.pdf)" rows=35 added=0 already=35 same_file=true
has "same file through a pipe" "$(./scripts/inject.sh stmt-pipe sep.pdf)" added=0 same_file=true
expect "statements after re-imports" "$(q "select count(*) from statement")" 1
expect "rows after re-imports" "$(q "select count(*) from txn")" 35
has "overlapping next statement" "$(./scripts/inject.sh stmt oct.pdf)" rows=8 added=6 already=2 complete=true
expect "rows after both" "$(q "select count(*) from txn")" 41
# Fixture totals: Sep file debits 35,042.62 + credits 76,010.00; Oct file adds debits 16,199.00 + credits 45,000.00.
expect "debits (paise)" "$(q "select sum(amount_paise) from txn where direction = 0")" 5124162
expect "credits (paise)" "$(q "select sum(amount_paise) from txn where direction = 1")" 12101000
expect "date-only rows" "$(q "select count(*) from txn where time_known = 0 and source = 'stmt'")" 41
expect "statements" "$(q "select group_concat(bank||'/'||account_tail||'/'||period_from||'/'||period_to||'/'||complete, ' ') from (select * from statement order by period_from)")" \
  "Kotak/7890/2026-09-16/2026-10-16/1Kotak/7890/2026-10-16/2026-11-16/1"
# Privacy: the full account number is never stored (the interest narration keeps XX + last 4), nor any header line.
expect "full account number or header text stored" \
  "$(q "select count(*) from txn where raw like '%1234567890%' or merchant_name like '%1234567890%' or raw like '%Account Statement%' or raw like '%Bangalore North%'")" 0
expect "Couldn't-read entries for complete statements" "$(q "select count(*) from unparsed")" 0

PAY="kotak:7890:2026-11-01:D:1500000:7179378"   # the 01 Nov UPI payment in the Oct file (RRN 610000000033)
if [ $FULL = 1 ]; then
  AT=1793538000000      # 2026-11-01 18:30 IST: the bank's SMS for that payment
  FROM=1793471400000    # 2026-11-01 00:00 IST
  BODY="Sent Rs.15000.00 from Kotak Bank AC X7890 to meena.rao@okaxis on 01-11-26.UPI Ref 610000000033. Not you, https://kotak.com/KBANKT/Fraud"
  ./scripts/inject.sh sms-at $AT VM-KOTAKB-S "$BODY" >/dev/null; sleep 1
  expect "rows after the SMS for a statement row" "$(q "select count(*) from txn")" 41
  OCT_ID=$(q "select id from statement where period_from = '2026-10-16'")
  expect "that row now (source/time known/time/balance/statement)" \
    "$(q "select source||'/'||time_known||'/'||occurred_at||'/'||ifnull(balance_paise,'null')||'/'||statement_id from txn where stmt_key = '$PAY'")" \
    "sms/1/$AT/7179378/$OCT_ID"
  # The same SMS read back from the inbox (catch-up has the inbox row id the live SMS lacked) must not add a row either.
  $ADB root >/dev/null 2>&1 || true; $ADB wait-for-device
  q1() { printf "'%s'" "${1//\'/\'\\\'\'}"; }
  # `content` splits bindings on ':', so colons inside the value are escaped as '\:'.
  $ADB shell "content insert --uri content://sms/inbox --bind address:s:VM-KOTAKB-S --bind date:l:$AT --bind read:i:1 --bind body:s:$(q1 "${BODY//:/\\:}")"
  $ADB shell "content query --uri content://sms/inbox --projection _id --where 'date=$AT'" | grep -q "_id=" || fail "could not write the test SMS into the emulator inbox"
  ./scripts/inject.sh catchup-from $FROM >/dev/null; sleep 1
  $ADB shell "content delete --uri content://sms --where 'date=$AT'" >/dev/null || true
  # Counted on this payment and on statement rows, so other SMS already in a test emulator's inbox can't upset it.
  expect "rows for that payment after the inbox re-read" "$(q "select count(*) from txn where amount_paise = 1500000 and occurred_at >= $FROM")" 1
  expect "statement rows after the inbox re-read" "$(q "select count(*) from txn where stmt_key is not null")" 41
  expect "SMS heard (live + inbox) / duplicates" "$(q "select (select value from meta where key = 'n_sms')||'/'||(select value from meta where key = 'n_duplicate')")" "2/2"
else
  expect "SMS permission declared" "$($ADB shell dumpsys package $PKG | grep -c 'android.permission.READ_SMS\|android.permission.RECEIVE_SMS' || true)" 0
  ./scripts/inject.sh sms-at 1793538000000 VM-KOTAKB-S "Sent Rs.15000.00 from Kotak Bank AC X7890 to meena.rao@okaxis on 01-11-26.UPI Ref 610000000033." >/dev/null; sleep 1
  expect "rows after an SMS in the statement edition" "$(q "select count(*) from txn")" 41
  expect "SMS heard by the statement edition" "$(q "select count(*) from meta where key = 'n_sms'")" 0
fi

# Remove the first statement: its rows go, except 16 Oct, which the second statement also covers.
./scripts/inject.sh stmt-delete "$(q "select id from statement where period_from = '2026-09-16'")" >/dev/null
expect "statement rows after removing the first statement" "$(q "select count(*) from txn where stmt_key is not null")" 8
expect "rows not moved to the remaining statement" "$(q "select count(*) from txn where stmt_key is not null and statement_id is not (select id from statement)")" 0
expect "16 Oct rows kept" "$(q "select count(*) from txn where stmt_key like 'kotak:7890:2026-10-16:%'")" 2
./scripts/inject.sh stmt-delete "$(q "select id from statement")" >/dev/null
expect "statement-only rows after removing both" "$(q "select count(*) from txn where source = 'stmt'")" 0
if [ $FULL = 1 ]; then
  expect "the SMS row stays, detached" "$(q "select count(*)||'/'||source||'/'||ifnull(stmt_key,'none')||'/'||ifnull(statement_id,'none') from txn where amount_paise = 1500000 and occurred_at >= $FROM")" "1/sms/none/none"
fi
expect "statements left" "$(q "select count(*) from statement")" 0

$ADB shell am force-stop $PKG; $ADB shell am start -n $PKG/.MainActivity >/dev/null; sleep 5
$ADB shell pidof $PKG >/dev/null || fail "app died on relaunch"
$ADB exec-out screencap -p > "$OUT/after-statement.png"
if $ADB logcat -d -s AndroidRuntime | grep -q "FATAL EXCEPTION"; then fail "a crash was logged"; fi
$ADB shell "run-as $PKG sh -c 'rm -f files/*.pdf files/stmt-result.txt'"
echo "STATEMENT SMOKE PASS ($([ $FULL = 1 ] && echo full || echo statement) edition)"
