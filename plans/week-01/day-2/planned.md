# Week 1 · Day 2 — 2026-10-30 (Friday)

**Version in progress:** v0.1.1 (last tagged: v0.1.0)
**Status:** done — v0.1.1 published

## Plan for today
- [x] Owner hit Google Play Protect blocking the APK install (reads SMS → flagged in India)
- [x] Explain honestly: the block is tied to the SMS permission + sideloading, not fixable by wording or by "a release"
- [x] Write `docs/INSTALL.md` (install-anyway / scan-toggle / USB routes + restricted-settings), link from README & release notes
- [x] Owner chose: cut v0.1.1 so the download page carries the install steps
- [x] Fix a time-bomb in `scripts/android-smoke.sh` (hard-coded date went stale after midnight)
- [x] Push, CI green on `a941e7d` (run 41045284316), tagged v0.1.1 (owner pre-approved)
- [x] Verified the published release: checksum OK, `/releases/latest` = v0.1.1, APK 1,403,876 bytes, versionCode 10011

## What is happening now
v0.1.1 is published: https://github.com/Chiragjjjks/Credu1ar/releases/tag/v0.1.1. Download page carries the Play Protect install steps. Owner still needs to get it onto the phone (scan-toggle or USB); not a release matter.

## Decisions
- **A new release does not remove the Play Protect warning.** Verified against Google's India enhanced-fraud-protection rules: the flag is on `READ_SMS`/`RECEIVE_SMS`/notification access + install-from-internet, independent of version or signature. Recorded so this is not re-attempted. The honest install paths are the scan-toggle (phone) or USB (`adb install`), both in `docs/INSTALL.md`.
- v0.1.1 is docs/CI only — no app code changed since v0.1.0 (`20291534`). Notes say so and point back to v0.1.0's feature list.
- **Smoke-test fix:** `android-smoke.sh` now injects `date +%d-%m-%y` / `%d-%b-%y` instead of a fixed `29-10-26`. The fixed date made the paired SMS look a day older than its live notification after midnight, pushing them outside the 15-minute de-dup window (3 rows instead of 2). Product behaviour is correct (a real SMS and its app notification both arrive ~now); only the test data was stale. This would also have turned CI red today.

## Verified how
- `scripts/check-version.sh` → version 0.1.1 (code 2) ok.
- `./gradlew :core:test :app:lintDebug :app:assembleRelease :app:assembleDebug` → BUILD SUCCESSFUL, 28 tests 0 failures, 0 lint errors.
- `scripts/android-smoke.sh` on `mn35` → SMOKE PASS (2 payments stored, OTP ignored, SMS+notification de-duplicated). Caught the stale-date bug first; re-ran green after the fix.
- `apksigner verify` → Signer CN=Credu1ar (local key); release APK 1,403,872 bytes.

## Blocked / waiting on owner
- Get it onto the phone: scan-toggle or USB (owner is doing this on their side).
- CI signing secrets before a key-stable release (still absent → throwaway key).
- Real-phone test (`docs/PHONE-TEST.md`).

## Next
Owner: install via scan-toggle or USB (`docs/INSTALL.md`), then run `docs/PHONE-TEST.md`. Set CI signing secrets before a key-stable release. Resume feature work at `plans/v0.2.0/plan.md` (rules editor first).
