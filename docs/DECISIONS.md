# Decision log (ADRs)

Short entries. Newest last. Add one whenever a choice will outlive the day. Format: **context → decision → consequences**.

## ADR-001 — Native Kotlin + Jetpack Compose
**Context.** The app must wake on every payment SMS, stay tiny, and draw bespoke charts. Sasta uses React Native/Expo; MetroNudge (same owner) uses native Kotlin + Compose.
**Decision.** Native Kotlin + Compose, `minSdk 26`, `targetSdk 37`.
**Consequences.** Smallest APK and fastest cold start; no JS runtime to boot for an SMS. Cost: UI is not shareable with a web landing page (we share tokens via `docs/DESIGN.md` instead).

## ADR-002 — Two modules: `:core` (pure Kotlin) and `:app`
**Context.** Parsing is the riskiest logic and changes often.
**Decision.** Everything that does not need Android (money, parsers, merchants, categories, insights) lives in `:core` and is tested on the JVM.
**Consequences.** Parser tests run in seconds without an emulator; `:core` could later be reused (e.g. a desktop import tool).

## ADR-003 — No foreground service
**Context.** "Always live" was the request. A foreground service means a permanent notification, RAM and battery cost, and still gets killed by OEMs.
**Decision.** Manifest `SMS_RECEIVED` receiver + system-bound `NotificationListenerService` + catch-up scan of the SMS inbox on every wake/open.
**Consequences.** Zero idle cost; missed events are recovered on next wake. Diagnostics screen shows "last heard from Android" so silent failures are visible.

## ADR-004 — Two capture paths, one pipeline
**Context.** RCS messages in Google Messages do not fire `SMS_RECEIVED`; UPI apps post notifications only.
**Decision.** SMS receiver and notification listener both emit a `RawSignal` into one pipeline that filters, parses, de-duplicates, categorises and stores.
**Consequences.** Duplicates are expected and handled by a fingerprint (amount + direction + account tail + reference/time window). SMS wins over notification when both exist (full body).

## ADR-005 — Plain SQLite, no Room
**Context.** Room needs KSP; AGP 9 with built-in Kotlin is a known compatibility risk, and Room adds size.
**Decision.** `SQLiteOpenHelper` with hand-written SQL behind a small repository.
**Consequences.** Smaller APK, simpler build, SQL aggregates written directly. Cost: manual migrations and no compile-time query checks (covered by instrumented-style unit tests with an in-memory DB if needed).

## ADR-006 — Money is integer paise
**Decision.** `Long` paise everywhere; formatting only at the UI edge, Indian digit grouping (₹1,23,456).

## ADR-007 — Custom Canvas charts, no chart library
**Decision.** Ring, bars, heatmap, sparkline and meters are drawn with Compose `Canvas`.
**Consequences.** Smaller APK, exact "Signal Noir" look, charts follow the validated palette and mark rules. Cost: we own accessibility (table view = filtered Ledger).

## ADR-008 — Parsers written from scratch
**Context.** PennyWise AI (closest open-source reference) is copyleft; Cashew is GPL-3.
**Decision.** Study approaches only. Every pattern is ours, backed by synthetic fixtures modelled on public bank-SMS styles.
**Consequences.** Real-world coverage grows through the owner's real messages (redacted) via the Diagnostics report and the `add-bank-parser` skill.

## ADR-009 — Release cycle copies Sasta
**Decision.** Push to `main` → CI tests, builds a signed APK, runs an emulator smoke test; tag `vX.Y.Z` publishes the exact tested APK with `SHA256SUMS.txt`. Version lives in `gradle.properties`; `scripts/check-version.sh` guards tag/version mismatch.
**Consequences.** Without keystore secrets CI signs with a committed public debug key so updates install over each other (does not prove publisher identity).

## ADR-010 — Journal folder is `plans/`
**Context.** The owner asked for a folder of per-day plans (said "apps.md").
**Decision.** `plans/week-NN/day-N/planned.md`. A directory named `*.md` breaks tools that glob for markdown.
**Consequences.** Rename with one `git mv` if the owner prefers another name.
