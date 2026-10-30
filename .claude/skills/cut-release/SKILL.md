---
name: cut-release
description: Prepare a Credu1ar release (version bump, release notes, checks) and stop before tagging. Use when the owner says to release, ship, publish or tag a version. Tagging and publishing require the owner's explicit approval each time.
---

# Cut a release

The cycle (ADR-009): push to `main` → CI runs `:core:test`, `lintDebug`, builds the signed APK and smoke-tests it on an emulator → a `vX.Y.Z` tag publishes **that exact tested APK** with `SHA256SUMS.txt`. Never tag from a commit whose `main` run has not passed.

## Steps

1. Clean tree, on `main`, up to date. `git status`, `git log origin/main..`.
2. Pick the version (semver: `0.Y.0` features, `0.Y.Z` fixes). Edit `gradle.properties`: `VERSION_NAME`, and raise `VERSION_CODE` by at least 1.
3. Write `docs/releases/vX.Y.Z.md` by hand (what's new for a person, install steps, **measured** numbers only, "Not verified yet", known limits). Copy the structure of the previous file. Do not claim anything you did not run.
4. Update `AGENTS.md` (*Current state*, *Next*), today's `plans/…/planned.md`, and the version in `README.md` if mentioned.
5. Local gate (same as CI):
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
   set -a; . ~/.credu1ar/keystore.env; set +a
   scripts/check-version.sh
   ./gradlew :core:test :app:lintDebug :app:assembleRelease :app:assembleDebug
   scripts/android-smoke.sh app/build/outputs/apk/debug/app-debug.apk   # emulator running
   $ANDROID_HOME/build-tools/36.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
   ```
6. Commit (`chore(release): vX.Y.Z`), `git push origin main`, wait for the GitHub Actions run on that commit to go green (check the Actions tab; the smoke evidence is an artifact).
7. **Tag only when it works.** The owner gave standing approval on 2026-10-31 ("After you think it will work just do a release"): tag once steps 5–6 are green and the change was checked on the emulator. Never tag a commit whose CI failed, was cancelled or is still running. If the owner withdraws that approval, go back to asking first.
   ```bash
   git tag vX.Y.Z && git push origin vX.Y.Z
   ```
8. After the release workflow finishes, check the Release page: APK, `SHA256SUMS.txt`, notes. Record the outcome in the journal.

## Signing reminder

CI signs with the private key only if repository secrets `CREDU1AR_KEYSTORE_BASE64`, `CREDU1AR_KEY_ALIAS`, `CREDU1AR_STORE_PASSWORD`, `CREDU1AR_KEY_PASSWORD` exist. Without them it uses a debug key and the owner cannot update in place from a build made on another machine. Setting those secrets is the owner's action; never print or commit the keystore or its passwords.
