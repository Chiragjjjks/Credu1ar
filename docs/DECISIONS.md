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
*Correction 2026-10-31:* there is no committed key. Without secrets each CI run signs with that runner's freshly generated debug key, so every release so far has a different signature (v0.1.0 `9084ad57…`, v0.1.1 `51fd2889…`) and will not install over the previous one. Fix: the owner runs `scripts/set-signing-secrets.sh`. Committing a key was rejected: anyone could then sign an "update" that replaces the app and inherits its data.

## ADR-010 — Journal folder is `plans/`
**Context.** The owner asked for a folder of per-day plans (said "apps.md").
**Decision.** `plans/week-NN/day-N/planned.md`. A directory named `*.md` breaks tools that glob for markdown.
**Consequences.** Rename with one `git mv` if the owner prefers another name.

## ADR-011 — Brief version checklists alongside daily journals
**Context.** The owner wants a saved v0.2.0 plan that the next session can continue, with explicit done/not-done states.
**Decision.** Store each version checklist in `plans/vX.Y.Z/plan.md`; update task statuses and completion evidence with the implementation commit. Keep dated history in the existing daily journals.
**Consequences.** Unfinished, blocked and deferred tasks remain visible across sessions without rewriting older daily records. Creating a plan does not mean its features are implemented or its release is approved.

## ADR-012 — Publish only the landing site through GitHub Pages
**Context.** The owner explicitly approved publishing the existing landing page on 2026-10-29.
**Decision.** Use a separate Pages workflow to upload only `site/`, with Pages write and OIDC permissions scoped to that workflow. No build step or new hosting service.
**Consequences.** Site updates can deploy without changing the Android release cycle. Initial enablement needs the personal account's Pages settings access; the read-only CLI account cannot do it. Verify the live URL before marking publication done.

## ADR-013 — The first SMS link reads this month, plus all of last month in week 1
**Context.** The owner (2026-10-31): linking SMS should fill the app by itself; on Nov 1 bring in all of October, on Nov 15 only Nov 1–15. Before this the first scan read a flat 90 days and the Arm-it screen offered "Import last 90 days".
**Decision.** `HistoryWindow.firstLink` (pure, `:core`, always IST): read from the 1st of this month; if today is day 1–7, from the 1st of last month. Pulse opens on last month when linked on day 1–3 (this month has nothing to show yet). Calendar-based, not "this month has too little data", so the same date always gives the same answer whatever the parser can read. Older history is one tap per month ("Add September"), never automatic. A history read drops a payment dated before its window (an alert that arrived after midnight); "Add <month>" reads 2 days past its month so that payment is found then. First link removes sample rows. "Delete everything" keeps `last_scan_date` so deleted payments do not return on the next open.
**Consequences.** Day 7 is a constant (`PREVIOUS_MONTH_THROUGH_DAY`); change it in one place if the owner wants a different cut-off. RCS bank chats are not in the SMS inbox, so they cannot be read back; they are caught live from notification access.

## ADR-014 — The Play Protect block is solved by how the app is installed, not by the app
**Context.** Since Nov 2024, Play Protect's enhanced fraud protection in India blocks apps that declare `RECEIVE_SMS`, `READ_SMS`, a notification listener or accessibility, when installed from a browser, messaging app or file manager (Google, developers.google.com/android/play-protect/warning-dev-guidance). The owner asked to make it "work like OTP auto-fill".
**Decision.** No app change can lift it, and we will not hide, delay, split or obfuscate permissions to slip past it (that is evasion). OTP auto-fill (SMS Retriever / User Consent APIs) cannot read bank alerts: Retriever needs our app hash inside the message; User Consent needs a tap per message inside a 5-minute window. Supported routes, best first: (1) `adb install` from this Mac over USB or Wi-Fi (`scripts/phone-install.sh`): not an internet sideload, and the privately signed build keeps updates working; (2) turn Play Protect scanning off for the install, then back on (documented setting, but Google does not promise it lifts this block); (3) Google Play internal testing track: the policy has a "SMS-based money management" exception, but it needs a $25 developer account, ID verification, a Permissions Declaration with video, possibly weeks of review, and re-signing by Play. Option 3 reverses the "no Play Store" plan, so it is the owner's call.
**Consequences.** The install guide leads with adb. Android developer verification (enforced in four other countries from 30 Sep 2026; India "2027 and beyond") will not block adb installs; revisit when it reaches India.

