# Credu1ar: context for coding agents

Read this first. Claude Code (through `CLAUDE.md`) and Codex both load it. It is the authority over older docs. **Update "Current state" and "Next" in the same commit as any feature or release, and append to today's journal file in `plans/` (see "Memory protocol").**

## What we are building

Credu1ar is a personal, on-device Android expense tracker (sideloaded APK, no backend, no Play Store). It reads the payment SMS and notifications the phone already receives, parses them, files each into a category, and shows the owner where their money goes. Full reasoning: `PLAN.md`.

- **Capture:** `SmsReceiver` (manifest-registered, zero idle cost) + `NotificationCaptureService` (RCS / UPI-app alerts) + a catch-up scan of the SMS inbox on every wake. We do **not** run a foreground service; "always live" means "Android wakes us when something arrives".
- **Understand:** pure-Kotlin `:core` — sender gating, amount/direction/merchant/account/date extraction, merchant directory, categoriser, insights.
- **Show:** Compose UI in the "Signal Noir" language (`docs/DESIGN.md`): Pulse, Ledger, Insights, Review, System.
- **Learn:** the owner corrects a category once and the merchant rule is remembered.

Owner: Chirag (GitHub `Chiragjjjks`), Bengaluru. Personal use. Not affiliated with any bank or payment app.

## How the owner works

- Hands work between Claude Code and Codex through T3 Code. **Every doc must stand alone so the next agent can continue cold.**
- Wants visible progress: small commits, real verification (tests, emulator screenshots), plain-language reports.
- Cares about the UI being striking and clear. Check UI visually (emulator screenshot) before declaring it done.
- Wants a day-by-day record so future agents know what happened in each stage (`plans/`).
- When a delegated agent asks a question meant for the owner, tell the owner. Never answer on their behalf.

## Memory protocol (do this every working day)

1. Open or create `plans/week-NN/day-N/planned.md` (see `plans/README.md`). One folder per day actually worked, 1–7 per week.
2. At the **start** of the session, fill *Plan for today*. During work, tick items and keep *What is happening now* current.
3. At the **end**, fill *Done*, *Decisions*, *Verified how*, *Blocked / waiting on owner*, and *Next*, and update `Current state` below.
4. Never rewrite an earlier day's file except to fix a factual error; add a dated correction instead.
5. Decisions with lasting effect also go in `docs/DECISIONS.md` (short ADR entries).
6. Keep the active version checklist (`plans/v0.2.0/plan.md`) current in the same commit as each task. Record `TODO`, `DOING`, `DONE`, `BLOCKED`, `DEFERRED` or `NO`; completed tasks need evidence, and blocked/deferred/dropped tasks need a reason.

## Current state: v0.1.0 released (2026-10-29, week 1 day 1)

**Done and verified on this Mac (JDK 17, Android 15 emulator `mn35`):**
- `:core` parser/merchants/categoriser/insights/sample data, 28 JVM tests green: 37 hand-written parser fixtures plus 100 adversarial fixtures written by Codex (ratchet `knownFailures` is empty; the first run failed 36, all fixed).
- `:app` capture (SmsReceiver, NotificationCaptureService, CatchUp), SQLite repo with de-dup, and the full Signal Noir UI (intro, Arm-it, Pulse, Ledger, Insights, grouped Needs-a-look, payment sheet, System).
- End-to-end: a real multi-part SMS PDU through the production receiver is stored, a UPI-app notification for the same payment is de-duplicated (SMS wins), an OTP is dropped unread. `scripts/android-smoke.sh` passes.
- Local release build: 1.32 MB, R8-minified, signed with the owner's private key (`~/.credu1ar/`, outside the repo), runs identically to debug. `lintDebug` has 0 errors.
- **Published [v0.1.0](https://github.com/Chiragjjjks/Credu1ar/releases/tag/v0.1.0)** from `2029153`: [main CI](https://github.com/Chiragjjjks/Credu1ar/actions/runs/40916416903) passed tests, lint, builds and x86_64 emulator smoke; [tag workflow](https://github.com/Chiragjjjks/Credu1ar/actions/runs/40917498126) published that exact APK + `SHA256SUMS.txt`. Downloaded the published APK, checked SHA-256 and byte-for-byte equality with the CI artifact. CI APK: 1,403,876 bytes, version code 10004, debug-key signed (CI signing secrets are absent). Installed this exact APK on `mn35`, loaded sample data and visually checked Pulse; no crash. Signing/update caveat is in the release notes.

**Not verified:** a real phone (restricted-settings flow, Google Messages RCS, OEM battery kill, real bank formats) → `docs/PHONE-TEST.md`. Idle battery/RAM/CPU per SMS are unmeasured.

**Next version planned:** [`plans/v0.2.0/plan.md`](plans/v0.2.0/plan.md) tracks rules editor → merchant pages → category budgets, verification, release dependencies and saved follow-ups. Planning is done; feature implementation has not started.

**Known sharp edges:** the emulator console `sms send` does not deliver on this image, so use `scripts/inject.sh` (debug build only). `adb shell` re-parses quotes: always go through `scripts/inject.sh`. Launch the emulator detached (`start_new_session`) or the harness kills it when the command returns.

**Delegated to Codex (gpt-6.1-sol, worktrees):** (1) landing page `site/`: done, reviewed and merged (`121d348`), not deployed; (2) adversarial parser fixtures with a known-failures ratchet: done, reviewed, merged, and all 36 failures it found are fixed. See `plans/week-01/day-1/planned.md` → *Delegated*.

**Reviewing delegated work:** do not trust the summary. Check `git diff --stat` for scope, grep for secrets/external calls, run the tests, and look at screenshots yourself. A first look can mislead (headless Chrome is at least ~500 px wide, so test narrow layouts through a sized iframe).

## Next, in order

1. Owner: install v0.1.0, run `docs/PHONE-TEST.md`, paste back the Diagnostics report; add real formats as fixtures before changing parser rules.
2. Owner: set the four CI signing secrets (`docs/ARCHITECTURE.md`) before the next release. The published v0.1.0 uses a temporary debug key; moving to the private key requires uninstall/reinstall (export first; re-import SMS afterward).
3. Owner decides whether to enable GitHub Pages for the completed `site/`; deployment is not approved yet.
4. Follow [`plans/v0.2.0/plan.md`](plans/v0.2.0/plan.md): start with the rules editor, then merchant pages and category budgets. Keep statuses current; recurring calendar and other follow-ups are recorded there. Later releases need fresh approval.

## Rules (hard)

- **Privacy invariants.** (a) A message that is not financial is discarded immediately: never stored, never logged. (b) No transaction data, merchant name or message text ever leaves the phone. The only network calls are the update check and optional FX rates, through one allow-listed client, with no user data in the request. (c) `allowBackup=false`; no analytics, crash reporters or ads. (d) Logs never contain message bodies or amounts above `DEBUG` builds.
- **No copyleft code.** Do not copy from PennyWise AI (GPL/AGPL), Cashew (GPL-3) or any other app. Ideas are fine; write parsers from scratch against our own fixtures.
- **Money is integer paise** (`Long`). Never `Double` for amounts.
- **Parsers are pure functions** in `:core` with fixture tests. A new bank format means a new fixture first, then the rule. Never weaken a test to make it pass.
- **Dedup before insert.** SMS and notification for the same payment must become one row.
- **Not-spending kinds** (card-bill settlement, self transfer, investment) never count toward spend totals.
- **Category colours** come from `docs/DESIGN.md` and are fixed per category; re-validate if you change one.
- **Commit small, push often.** Every logical change (even a typo, a doc, a rename) is its own commit with a clear message, and `main` is pushed after each meaningful commit. The owner follows progress on GitHub, not in an IDE. Do not push while a CI run you still need is in progress (the workflow cancels in-progress runs on the same branch); push the moment it ends.
- **Releases, tags, GitHub Pages and pushes to other repos need the owner's explicit approval.** Pushing to `main` of this repo is part of normal work. The owner approved the v0.1.0 release on 2026-10-29 ("Do a release also"); confirm before later ones.
- **`gh` is the work account** and has read-only access to this repo: it cannot create releases or set secrets here. Releases are made by the tag workflow inside GitHub; secrets are set by the owner.
- **Git identity.** This repo uses the personal identity (`Chiragjjjks`, set locally) and the SSH host alias: `git@github-personal:Chiragjjjks/Credu1ar.git`. The global git/`gh` login is a work account; do **not** use HTTPS or `gh` to push here.

## Build and test (this Mac)

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
# local.properties (gitignored) needs: sdk.dir=/opt/homebrew/share/android-commandlinetools
./gradlew :core:test                 # fast, JVM only
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
$ANDROID_HOME/emulator/emulator -avd mn35 -no-snapshot -no-audio &   # Android 35 arm64 image
adb emu sms send HDFCBK "Rs.450.00 debited from A/c XX1234 on 29-10-26 to VPA zepto@axisbank (UPI Ref No 123456789012)"
```

## Repository map

| Path | What it is |
|---|---|
| `PLAN.md` | Why, analysis, roadmap |
| `docs/ARCHITECTURE.md` | Data flow, battery/RAM budget, threat model |
| `docs/DESIGN.md` | Tokens, screens, motion (contract for app and site) |
| `docs/CATEGORIES.md` | Taxonomy and merchant rules |
| `docs/PARSING.md` | How SMS are parsed; how to add a bank |
| `docs/DECISIONS.md` | ADR log |
| `docs/PHONE-TEST.md` | Owner's real-phone checklist |
| `docs/releases/vX.Y.Z.md` | Hand-written release notes, one per tag |
| `plans/` | Day-by-day journal (the project's memory) |
| `plans/v0.2.0/plan.md` | Next-version task statuses, completion checks and saved follow-ups |
| `core/` | Pure Kotlin: `model`, `parse`, `merchant`, `category`, `insights` |
| `app/` | Android: `ingest` (receiver, listener, catch-up), `data` (SQLite), `ui` (theme, kit, screens), `sample` |
| `scripts/` | `check-version.sh`, `android-smoke.sh` (CI), `inject.sh` + `dump-db.sh` (debug-build emulator tools) |
| `.github/workflows/android.yml` | CI + release |
| `.claude/skills/` | Project skills (add a bank parser, cut a release) |
| `site/` | Landing page (built by Codex in a worktree; not deployed) |

## Delegating to Codex

Codex (`delegate_task`, provider instance `codex`, model `gpt-6.1-sol`) is the owner-approved subordinate. Claude gives the commands; Codex does not make product decisions.

- Call `orchestrator_capabilities` once per thread first. Use `isolation:"worktree"` for anything that edits files, and name the files the child owns so work never collides.
- The brief must stand alone: point at `AGENTS.md` and the specific docs, state acceptance checks (commands that must pass), and say where to write results (`plans/…`).
- Good tasks: landing site, fixture corpora and adversarial parser review, emulator smoke script, independent code review. Bad tasks: anything needing the owner's physical phone or accounts. Those go on the owner's list in `docs/PHONE-TEST.md`.
- The T3 device panel was switched off at the start of this project, so agents cannot drive a real handset. The emulator (`mn35`) works through `adb`.
