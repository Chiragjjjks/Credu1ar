#!/usr/bin/env bash
# Guards the release cycle: version format, release notes present, and (for tags) tag == gradle.properties version.
#   scripts/check-version.sh            # on any branch build
#   scripts/check-version.sh v0.1.0     # on a tag build
set -euo pipefail
cd "$(dirname "$0")/.."
fail() { echo "check-version: $*" >&2; exit 1; }
VERSION=$(sed -n 's/^VERSION_NAME=//p' gradle.properties)
CODE=$(sed -n 's/^VERSION_CODE=//p' gradle.properties)
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "VERSION_NAME '$VERSION' is not X.Y.Z"
[[ "$CODE" =~ ^[0-9]+$ ]] || fail "VERSION_CODE '$CODE' is not an integer"
[ -f "docs/releases/v$VERSION.md" ] || fail "docs/releases/v$VERSION.md is missing (write the release notes first)"
if [ -n "${1:-}" ] && [[ "$1" == v* ]]; then
  [ "$1" = "v$VERSION" ] || fail "tag $1 does not match gradle.properties version v$VERSION"
fi
echo "version $VERSION (code $CODE) ok"
