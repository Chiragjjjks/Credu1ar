# Architecture

## Data flow

```
 SMS arrives ─────► SmsReceiver ──┐                                   ┌─► Pulse
 (manifest receiver,              │                                   ├─► Ledger
  process wakes ~100 ms)          ├─► Ingest.handle ─► Pipeline ─► SQLite ─► Insights ─┼─► Insights
 Notification ────► Listener ─────┤     (count)     parse·merchant·   (on phone)       ├─► Review
 (Android-owned, allow-listed)    │                 category·dedup                      └─► System
 App opens / SMS wake ► CatchUp ──┘
 (inbox scan since last seen)
```

`Ingest.handle` is the only door. Every source (live SMS, notification, catch-up, sample data, the debug injector) passes through it, so the privacy rules and de-duplication are enforced in one place.

## Modules

| Module | Depends on | Contents |
|---|---|---|
| `:core` (pure Kotlin/JVM) | nothing | `model` (money, payment, category), `parse` (SmsParser, SenderDirectory), `merchant` (directory of ~150 brands, normaliser), `category` (Categorizer), `Dedup`, `Pipeline`, `insights` (month summary, leaks, movers, recurring, rhythm, headline), `sample` |
| `:app` (Android) | `:core` | `ingest` (SmsReceiver, NotificationCaptureService, CatchUp, Ingest), `data` (Db, Repo), `notify`, `ui` (tokens, kit, charts, screens, AppModel), `src/debug` (SMS injector) |

`:core` has no Android imports, so the parser and the analytics run as fast JVM unit tests with no emulator.

## Why no always-on service (ADR-003)

A foreground service costs a permanent notification, RAM and battery, and phone makers kill it anyway. Android already wakes a manifest-declared receiver for each SMS, and owns the notification listener's lifetime. The risk is a missed event while the process is frozen, which is covered by **catch-up on wake**: every app open, and every SMS wake after 6 h, re-reads the inbox for anything newer than the last message seen. Rows are keyed by the inbox row id, so re-reading is harmless.

## Capture paths and what each can and cannot see

| Path | Sees | Cannot see |
|---|---|---|
| `SMS_RECEIVED` receiver | every plain SMS the moment it arrives | RCS chats in Google Messages (Android never broadcasts them as SMS) |
| Notification listener | Google Messages / Samsung Messages notifications (incl. RCS) and allow-listed UPI/bank apps | other apps (dropped before their text is read); text can be truncated |
| Catch-up scan (`READ_SMS`) | the SMS inbox history | RCS |

## Permissions

| Permission | Why | Notes |
|---|---|---|
| `RECEIVE_SMS`, `READ_SMS` | live capture, catch-up | hard-restricted for sideloaded apps on Android 13+ → "Allow restricted settings" |
| Notification access (system setting) | RCS + UPI-app alerts | also restricted for sideloaded apps; Settings → Apps → Credu1ar → ⋮ |
| `POST_NOTIFICATIONS` | the quiet "captured" note | optional |
| `INTERNET` | the update check only | user-initiated, nothing about the user is sent |
| *not requested* | contacts, location, accounts, storage, phone state | |

## Privacy model and threat model

Invariants (also in `AGENTS.md`): non-payments are counted then dropped, never stored or logged; no payment data leaves the phone; `allowBackup=false` plus empty `dataExtractionRules`; no analytics/crash reporters. In scope: a curious second app on the phone (app-private storage, no exported providers, only `BROADCAST_SMS`-protected receiver exported), shoulder surfing (hide-amounts, `FLAG_SECURE`), a leaked APK (no secrets in it). Out of scope for v0.1: a rooted phone, a stolen unlocked phone (app lock is planned for v0.4), disk encryption beyond Android's own file-based encryption.

The notification listener only reads packages in `Allowlist`; everything else returns before its text is touched. The CSV export defuses spreadsheet formula injection from merchant names.

## Failure modes

| Failure | What happens | Where it shows |
|---|---|---|
| Phone maker freezes the app | missed events recovered by catch-up on next wake | System → "Last catch-up" |
| Notification listener unbound | `requestRebind` on disconnect and on every app open | System → "Connected / last alert" |
| Bank changes its SMS wording | message lands in `unparsed` (masked on screen) | System → "Couldn't read" |
| Same payment via SMS + UPI-app notification | merged; SMS wins (full text, reference) | System → "Duplicates" |
| Wrong category guess | Needs a look queue; one tap teaches a per-payee rule | Pulse chip, Review |

## Budgets (targets vs measured)

| Item | Target | Measured so far |
|---|---|---|
| APK size | < 8 MB | **1.32 MB** release (emulator build, R8) |
| Idle cost | no resident service | by design: nothing runs between SMS |
| Foreground memory | — | ~38 MB PSS with sample data (emulator) |
| CPU per SMS | < 150 ms | not yet measured on a phone |
| Storage per year | < 5 MB | not yet measured (≈ 1.5 KB per payment with raw text) |

## Build and release

See `README.md` and `docs/DECISIONS.md` (ADR-009). Version lives in `gradle.properties`; `scripts/check-version.sh` guards tag/version/notes; CI runs `:core:test`, `lintDebug`, `assembleRelease`, then `scripts/android-smoke.sh` on an API 35 emulator. Signing uses CI secrets `CREDU1AR_KEYSTORE_BASE64`, `CREDU1AR_KEY_ALIAS`, `CREDU1AR_STORE_PASSWORD`, `CREDU1AR_KEY_PASSWORD`; locally `~/.credu1ar/keystore.env` (outside the repo).
