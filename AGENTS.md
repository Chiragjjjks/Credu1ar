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

## Current state: day 1 (2026-10-29), pre-release

See `plans/week-01/day-1/planned.md` for the live status. Summary is kept here once something ships.

## Next, in order

1. Ship v0.1.0 as scoped in `PLAN.md` §9 (needs the owner's approval to tag).
2. Owner runs `docs/PHONE-TEST.md` on the real phone and pastes back the Diagnostics report.
3. v0.2.0: rules editor, merchant pages, budgets, recurring calendar.

## Rules (hard)

- **Privacy invariants.** (a) A message that is not financial is discarded immediately: never stored, never logged. (b) No transaction data, merchant name or message text ever leaves the phone. The only network calls are the update check and optional FX rates, through one allow-listed client, with no user data in the request. (c) `allowBackup=false`; no analytics, crash reporters or ads. (d) Logs never contain message bodies or amounts above `DEBUG` builds.
- **No copyleft code.** Do not copy from PennyWise AI (GPL/AGPL), Cashew (GPL-3) or any other app. Ideas are fine; write parsers from scratch against our own fixtures.
- **Money is integer paise** (`Long`). Never `Double` for amounts.
- **Parsers are pure functions** in `:core` with fixture tests. A new bank format means a new fixture first, then the rule. Never weaken a test to make it pass.
- **Dedup before insert.** SMS and notification for the same payment must become one row.
- **Not-spending kinds** (card-bill settlement, self transfer, investment) never count toward spend totals.
- **Category colours** come from `docs/DESIGN.md` and are fixed per category; re-validate if you change one.
- **Releases, tags, GitHub Pages and pushes to other repos need the owner's explicit approval.** Pushing to `main` of this repo is part of normal work.
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
| `core/` | Pure Kotlin: `model`, `parse`, `merchant`, `category`, `insights` |
| `app/` | Android: `ingest` (receiver, listener, catch-up), `data` (SQLite), `ui` (theme, kit, screens), `sample` |
| `scripts/` | `check-version.sh`, emulator smoke test |
| `.github/workflows/android.yml` | CI + release |
| `.claude/skills/` | Project skills (add a bank parser, cut a release) |
| `site/` | Landing page (planned) |

## Delegating to Codex

Codex (`delegate_task`, provider instance `codex`, model `gpt-6.1-sol`) is the owner-approved subordinate. Claude gives the commands; Codex does not make product decisions.

- Call `orchestrator_capabilities` once per thread first. Use `isolation:"worktree"` for anything that edits files, and name the files the child owns so work never collides.
- The brief must stand alone: point at `AGENTS.md` and the specific docs, state acceptance checks (commands that must pass), and say where to write results (`plans/…`).
- Good tasks: landing site, fixture corpora and adversarial parser review, emulator smoke script, independent code review. Bad tasks: anything needing the owner's physical phone or accounts. Those go on the owner's list in `docs/PHONE-TEST.md`.
- The T3 device panel was switched off at the start of this project, so agents cannot drive a real handset. The emulator (`mn35`) works through `adb`.
