#!/usr/bin/env bash
# Checks the two editions' built manifests (ADR-016): the statement APK declares no SMS permission, no SMS receiver and
# no notification listener; the full APK does (so this check can't pass by reading the wrong file). Both share one
# application id and keep backups off. Prints the statement APK's permissions. Used by CI and the release skill.
#   scripts/check-editions.sh <full.apk> <statement.apk>
set -euo pipefail
FULL=${1:?usage: check-editions.sh <full.apk> <statement.apk>}
STMT=${2:?usage: check-editions.sh <full.apk> <statement.apk>}
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
AAPT2=${AAPT2:-}
if [ -z "$AAPT2" ]; then
  if [ -x "$SDK/build-tools/36.0.0/aapt2" ]; then AAPT2="$SDK/build-tools/36.0.0/aapt2"
  else AAPT2=$(ls "$SDK"/build-tools/*/aapt2 2>/dev/null | tail -1); fi
fi
[ -x "$AAPT2" ] || { echo "check-editions: aapt2 not found (set ANDROID_HOME or AAPT2)" >&2; exit 1; }
fail() { echo "check-editions: $*" >&2; exit 1; }

SMS_MARKERS='android.permission.RECEIVE_SMS|android.permission.READ_SMS|android.provider.Telephony.SMS_RECEIVED|android.service.notification.NotificationListenerService|android.permission.BIND_NOTIFICATION_LISTENER_SERVICE'
# Read each manifest once into a variable: `aapt2 … | grep -q` would trip pipefail when grep stops reading early.
FULL_XML=$("$AAPT2" dump xmltree --file AndroidManifest.xml "$FULL")
STMT_XML=$("$AAPT2" dump xmltree --file AndroidManifest.xml "$STMT")
FULL_PKG=$("$AAPT2" dump packagename "$FULL")
STMT_PKG=$("$AAPT2" dump packagename "$STMT")

echo "statement edition permissions ($(basename "$STMT")):"
"$AAPT2" dump permissions "$STMT" | sed 's/^/  /'
FOUND=$(grep -Eo "$SMS_MARKERS" <<<"$STMT_XML" | sort -u || true)
[ -z "$FOUND" ] || fail "the statement APK declares SMS or notification-listener items, it must declare none: $FOUND"
for m in android.permission.RECEIVE_SMS android.permission.READ_SMS android.provider.Telephony.SMS_RECEIVED android.service.notification.NotificationListenerService; do
  grep -qF "$m" <<<"$FULL_XML" || fail "the full APK is missing $m: wrong file, or the full manifest lost it"
done
[ "$FULL_PKG" = "$STMT_PKG" ] || fail "application ids differ: $FULL_PKG vs $STMT_PKG"
grep -q 'allowBackup.*=false' <<<"$FULL_XML" || fail "$(basename "$FULL"): allowBackup is not false"
grep -q 'allowBackup.*=false' <<<"$STMT_XML" || fail "$(basename "$STMT"): allowBackup is not false"
echo "editions ok: statement APK has no SMS permission, SMS receiver or notification listener; full APK has them; one id ($FULL_PKG)"
