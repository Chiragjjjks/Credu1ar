#!/usr/bin/env bash
# OWNER ONLY. Copies the private signing key in ~/.credu1ar/ into this repo's GitHub Actions secrets, so every
# published release is signed with the same key and installs over the previous one (keeping your data).
# Values go to `gh` on stdin; nothing is printed or written to disk.
#
# Needs `gh` logged in as the account that owns Chiragjjjks/Credu1ar (not the work account):
#   gh auth login            # or: gh auth switch
#   scripts/set-signing-secrets.sh
set -euo pipefail
REPO=Chiragjjjks/Credu1ar
ENV_FILE=~/.credu1ar/keystore.env
[ -f "$ENV_FILE" ] || { echo "Missing $ENV_FILE" >&2; exit 1; }
set -a; . "$ENV_FILE"; set +a
[ -f "${CREDU1AR_KEYSTORE_PATH:-}" ] || { echo "Keystore not found at CREDU1AR_KEYSTORE_PATH" >&2; exit 1; }
# All four or none: never leave CI with a half-set key.
for v in CREDU1AR_KEY_ALIAS CREDU1AR_STORE_PASSWORD CREDU1AR_KEY_PASSWORD; do
  [ -n "${!v:-}" ] || { echo "$v is empty in $ENV_FILE" >&2; exit 1; }
done

# Fail early (and quietly) if this login cannot write secrets here.
gh api "repos/$REPO" --jq '.permissions.admin' 2>/dev/null | grep -q true \
  || { echo "The current gh login cannot set secrets on $REPO. Run: gh auth login (personal account)" >&2; exit 1; }

base64 < "$CREDU1AR_KEYSTORE_PATH" | tr -d '\n' | gh secret set CREDU1AR_KEYSTORE_BASE64 -R "$REPO"
printf '%s' "$CREDU1AR_KEY_ALIAS"      | gh secret set CREDU1AR_KEY_ALIAS      -R "$REPO"
printf '%s' "$CREDU1AR_STORE_PASSWORD" | gh secret set CREDU1AR_STORE_PASSWORD -R "$REPO"
printf '%s' "$CREDU1AR_KEY_PASSWORD"   | gh secret set CREDU1AR_KEY_PASSWORD   -R "$REPO"
echo "Done. Four secrets set on $REPO; the next CI build is signed with your key."
gh secret list -R "$REPO"
