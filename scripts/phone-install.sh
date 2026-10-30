#!/usr/bin/env bash
# Install Credu1ar on your own phone from this computer, over USB or Wi-Fi (adb).
# An adb install is not a download from the internet, so Play Protect's India block for sideloaded SMS apps
# does not apply to it (docs/INSTALL.md, Route C). Phone setup, once: Settings → About phone → tap Build number
# 7 times, then Developer options → USB debugging (cable) or Wireless debugging (Wi-Fi, Android 11+).
#
#   scripts/phone-install.sh                  # build the release APK (signed with your private key) and install it
#   scripts/phone-install.sh path/to/app.apk  # install a specific APK, e.g. one downloaded from Releases
#   scripts/phone-install.sh pair IP:PORT     # Wi-Fi, once: Wireless debugging → "Pair device with pairing code";
#                                             # adb then asks for the 6-digit code shown on the phone
#   scripts/phone-install.sh connect IP:PORT  # Wi-Fi, each session: the IP:PORT shown at the top of Wireless debugging
set -euo pipefail
cd "$(dirname "$0")/.."
ADB=${ADB:-adb}
PKG=io.github.chiragjjjks.credu1ar

case "${1:-}" in
  pair)    $ADB pair "${2:?usage: $0 pair IP:PORT}"; exit ;;
  connect) $ADB connect "${2:?usage: $0 connect IP:PORT}"; exit ;;
esac

APK=${1:-}
if [ -z "$APK" ]; then
  # Same key every time, so later builds install over this one and keep your data.
  [ -f ~/.credu1ar/keystore.env ] || { echo "No private key in ~/.credu1ar/ — pass an APK path instead." >&2; exit 1; }
  set -a; . ~/.credu1ar/keystore.env; set +a
  ./gradlew -q :app:assembleRelease
  APK=app/build/outputs/apk/release/app-release.apk
fi
[ -f "$APK" ] || { echo "No such APK: $APK" >&2; exit 1; }

# Only real phones: never install onto an emulator by accident.
PHONES=$($ADB devices | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1 }')
COUNT=$(printf '%s\n' "$PHONES" | grep -c . || true)
if [ "$COUNT" -ne 1 ]; then
  echo "Expected exactly one phone, found $COUNT. Plug in (and tap Allow on the phone), or pair/connect over Wi-Fi." >&2
  $ADB devices -l >&2
  exit 1
fi

echo "Installing $(basename "$APK") on $PHONES …"
if ! OUT=$($ADB -s "$PHONES" install -r "$APK" 2>&1); then
  echo "$OUT" >&2
  if grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE <<<"$OUT"; then
    echo >&2
    echo "The phone has a copy signed with a different key (every Releases build so far used a throwaway key)." >&2
    echo "Export your data first (System → Export → CSV), then: $ADB -s $PHONES uninstall $PKG  and run this again." >&2
    echo "Your SMS stay on the phone, so the new install reads them back." >&2
  fi
  exit 1
fi
echo "Installed. Open Credu1ar and follow Arm it."
