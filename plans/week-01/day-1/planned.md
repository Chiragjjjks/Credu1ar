# Week 1 · Day 1 — 2026-10-29 (Thursday)

**Version in progress:** v0.1.0 (last tagged: none)
**Status:** in progress

## Plan for today
- [x] Research: sibling repos (sasta, openscribe, metronudge), Downmix UI reference, SMS/RCS/sideload constraints, open-source references
- [x] Repo init with the personal identity + SSH alias remote
- [x] `PLAN.md`, `docs/DESIGN.md`, `AGENTS.md`, `CLAUDE.md`, `plans/` journal
- [ ] Gradle project: `:core` (pure Kotlin) + `:app` (Compose), builds locally
- [ ] `:core`: money, parser, merchant directory, categoriser, insights — with fixture tests
- [ ] `:app`: SMS receiver, notification listener, catch-up scan, SQLite, dedup
- [ ] `:app` UI: intro, onboarding, Pulse, Ledger, Insights, Review, System (Signal Noir)
- [ ] Emulator proof: fake bank SMS → row appears; screenshots
- [ ] CI workflow + release notes draft (no tag)
- [ ] Delegate to Codex: landing site, independent parser review
- [ ] Push `main` to `git@github-personal:Chiragjjjks/Credu1ar.git`

## What is happening now
Writing the planning docs and agent memory before any code, so the next agent can resume from the repo alone.

## Done
- Research findings are folded into `PLAN.md` §3–§5.

## Decisions
- Native Kotlin + Compose, two modules (`:core` pure JVM, `:app`). Reason: smallest APK, instant wake-ups, direct SMS/notification APIs, fast JVM tests for the parser.
- No foreground service; manifest receiver + catch-up scan instead. Reason: zero idle cost, nothing lost if the OS kills the process.
- Plain `SQLiteOpenHelper`, not Room. Reason: smaller APK, no annotation processor (KSP/AGP 9 compatibility risk), SQL aggregates are the heart of analytics.
- Journal folder is `plans/` (the owner asked for a folder called "apps.md"; a folder named `*.md` breaks tools that glob for markdown files, so it is `plans/`; renaming is one `git mv`).
- Git remote uses the SSH alias `github-personal`, local identity `Chiragjjjks`, copied from the owner's other personal repos. The HTTPS URL in the request would authenticate as the work account.
- Parsers written from scratch; PennyWise AI (copyleft) is idea-only.

## Verified how
- Palette: `validate_palette.js` dark run on `#0a0a0a` and `#000000`: all checks pass (worst adjacent CVD ΔE 8.4, normal-vision 19.3).
- Toolchain on this Mac: JDK 17 (`/opt/homebrew/opt/openjdk@17`), Android SDK 37 + build-tools 36 (`/opt/homebrew/share/android-commandlinetools`), Gradle 9.3.1 wrapper, AVD `mn35` (Android 35, arm64).

## Delegated (Codex)
- none yet

## Blocked / waiting on owner
- Real-phone test (`docs/PHONE-TEST.md`) — only the owner can do it.
- Approval to tag/publish v0.1.0.

## Next
Create the Gradle project and get `./gradlew :core:test` green.
