# Week 1 · Day 1 — 2026-10-29 (Thursday)

**Version in progress:** v0.1.0 (last tagged: none)
**Status:** in progress (end-of-day wrap-up)

## Plan for today
- [x] Research: sibling repos (sasta, openscribe, metronudge), Downmix UI reference, SMS/RCS/sideload constraints, open-source references
- [x] Repo init with the personal identity + SSH alias remote
- [x] `PLAN.md`, `docs/DESIGN.md`, `AGENTS.md`, `CLAUDE.md`, `plans/` journal
- [x] Gradle project: `:core` (pure Kotlin) + `:app` (Compose), builds locally
- [x] `:core`: money, parser, merchant directory, categoriser, insights — with fixture tests (26 green)
- [x] `:app`: SMS receiver, notification listener, catch-up scan, SQLite, dedup
- [x] `:app` UI: intro, onboarding, Pulse, Ledger, Insights, Review, System (Signal Noir)
- [x] Emulator proof: real multi-part SMS PDU → row appears; screenshots reviewed and 8 UI/data bugs fixed
- [x] CI workflow + release notes draft (no tag)
- [x] Delegate to Codex: landing site, independent parser review (running)
- [x] Push `main` to `git@github-personal:Chiragjjjks/Credu1ar.git` (audited first: no keys, passwords, personal email or real SMS tracked)

## What is happening now
Wrapping up: docs and journal written; waiting on the two Codex reports (site, adversarial parser fixtures) before the first push.

## Done
- `59b86b9` core: parser, merchant directory (~150 brands), categoriser, insights, sample data, 24 tests.
- `6049ef0` app: capture pipeline + debug-only PDU injector + scripts.
- `4addbbc` app: Signal Noir UI.
- `b4dba93` CI workflow, scripts (`check-version`, `android-smoke`), docs (ARCHITECTURE, PARSING, CATEGORIES, PHONE-TEST), release notes v0.1.0, project skills, lint fixes (API 26 safety), honest "SAMPLE DATA · NOT LISTENING" strip, review grouped by payee.
- Pushed `main` to `git@github-personal:Chiragjjjks/Credu1ar.git` (public repo). First CI run `40914429040` in progress at time of writing.

## Decisions
- Native Kotlin + Compose, two modules (`:core` pure JVM, `:app`). Smallest APK, instant wake-ups, fast parser tests. (ADR-001/002)
- No foreground service: manifest receiver + catch-up scan. (ADR-003)
- Two capture paths, one pipeline: SMS + notification listener (RCS and UPI apps). (ADR-004)
- Plain SQLite, no Room. (ADR-005) Money is integer paise. (ADR-006) Custom Canvas charts. (ADR-007)
- Parsers written from scratch (PennyWise AI is copyleft). (ADR-008)
- Release cycle copies Sasta; **signing key is private** (`~/.credu1ar/`, never committed), unlike Sasta's public debug key, because this app reads bank SMS.
- Journal folder is `plans/`, not a folder literally named `apps.md` (tools that glob `*.md` would treat it as a file). (ADR-010)
- Git remote uses the SSH alias `github-personal` + local `Chiragjjjks` identity; the HTTPS URL would authenticate as the work account.
- Debug build carries an SMS injector (src/debug only) because `adb emu sms send` is dead on this emulator image; release has none (verified).
- Pace/average figures show whole rupees; the status strip never says LISTENING unless SMS or notification access is really granted.

## Verified how
- `./gradlew :core:test` → 26 tests, 0 failures. `lintDebug` → 0 errors.
- Emulator (Android 15, arm64): injected HDFC UPI (single PDU), ICICI card (2-part PDU), Google Pay notification, OTP, duplicate PhonePe notification → DB had exactly 2 payments, `n_ignored=1`, `n_duplicate=1`, SMS source kept. `scripts/android-smoke.sh` → SMOKE PASS.
- Release APK (R8) installed with the private key: launches, loads sample data via the UI, no crash, identical numbers; `DebugInjectReceiver` absent. Size 1.32 MB; PSS ~38 MB with UI open.
- Screenshots reviewed by eye for every screen; palette validated with `validate_palette.js` (dark, #000 and #0a0a0a).

## Delegated (Codex, gpt-6.1-sol, worktrees)
- **Landing site `site/`: DONE, reviewed, merged** (`121d348`, from branch `t3/build-privacy-landing-page` @ `3fbdb2c`). I did not take the report on trust: confirmed only `site/` changed (9 files, owner identity), no external URLs beyond the project's own GitHub links, no `fetch`/XHR/beacon, no trackers or invented stats, ~46 KB excluding fonts, and looked at it in headless Chrome (desktop hero, demo, category ring; true 390 px layout via iframe; zero horizontal overflow at 320/360/375/390/414/768). Headless Chrome crops below ~500 px wide, which looked like a clipped layout until re-tested at true width. Not tested: real phone browsers. Not deployed (GitHub Pages needs the owner).
- **Adversarial parser fixtures** — task `Red-team the SMS parser`; adds only `core/src/test/.../AdversarialFixtures*.kt` with a known-failures ratchet and a ranked failure report. Results: see below once delivered.

## Blocked / waiting on owner
- Real-phone test (`docs/PHONE-TEST.md`) — only the owner can do it.
- CI signing secrets (`CREDU1AR_KEYSTORE_BASE64` etc.); approval to tag v0.1.0; GitHub Pages for `site/`.
- Confirm the app/package name (`Credu1ar`, `io.github.chiragjjjks.credu1ar`).

## Next
Integrate the Codex outputs, fix top parser failures, push `main`, watch CI.
