#!/usr/bin/env bash
# Copy the debug app's SQLite files off the device and run a query (default: show transactions + counters).
set -euo pipefail
PKG=io.github.chiragjjjks.credu1ar; ADB=${ADB:-adb}; OUT=${TMPDIR:-/tmp}/credu1ar-db
rm -rf "$OUT"; mkdir -p "$OUT"
$ADB exec-out run-as $PKG sh -c 'cd databases && tar cf - credu1ar.db credu1ar.db-wal credu1ar.db-shm 2>/dev/null' | tar xf - -C "$OUT"
sqlite3 -header -column "$OUT/credu1ar.db" "${1:-select id, source, amount_paise as paise, merchant_name as merchant, category, review as rv, account_tail as tail, reference as ref from txn order by id; select key, value from meta where key like 'n_%' order by key;}"
