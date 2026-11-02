# Week 1 · Day 5 — 2026-11-02 (Monday)

**Version in progress:** v0.1.3 (last tagged: v0.1.2)
**Status:** release pending CI

## Plan for today
**First request (paraphrased).** "Since the SMS is not working properly", add a button to upload last month's bank statement and show a simple dashboard of what happened, with category bars (Shopping etc.) like Navi. Plan first, including how the UI will look.

**Second request.** The owner sent their real Kotak statement: "check this pdf … build around it, there could be multiple months, think about that; create multiple agents, talk to each other, update, then build."

- [x] Plan + mockup: [`plans/v0.1.3/plan.md`](../../v0.1.3/plan.md), [`mockup.html`](../../v0.1.3/mockup.html). Navi's spend screen isn't public; asked for a screenshot.
- [x] Read the real PDF privately; prove the approach (Android's PdfRenderer text API on the emulator; balance chain).
- [x] Fictional fixtures of the layout + debug PDF text probe (`b1714f0`)
- [x] Agent discussion (designer, architect, critic; critic rules on disagreements) → ADR-015/016, plan updated
- [x] Owner answers: statement-only APK "Go ahead"; phone on Android 15+
- [x] Shared foundation (`d03af1d`): Kotak reader, types, report model, stubs
- [x] Three builder agents in worktrees, merged A → B → C
- [x] End-to-end on emulators (fictional PDFs via the real picker; the real statement privately), fixes (`272305b`)
- [x] Independent review → fixes (`f735aae`)
- [ ] Release v0.1.3 (both APKs) once the exact-commit CI is green

## What is happening now
Release commit pushed; waiting for CI before tagging.

## Done
- `2bbe73c` plan + mockup · `83c21d9` journal/index · `b1714f0` fictional Kotak fixtures + debug `pdftext` probe · `d03af1d` shared foundation (Kotak reader, completeness checks, keys, Reports, stubs, ADR-015/016)
- Builder A (`:core`), 7 commits: truncated names, own-name, `StatementMatch`, `Reports.headline`, end-to-end fixture test, docs
- Builder B (data/ingest/build), 12 commits: schema v2, Repo import/merge/match/delete + reverse SMS upgrade, `PdfText`/`StatementImport`, `full`/`statement` flavors, CI for both, `make-test-pdf.py`, `statement-smoke.sh`, `check-editions.sh`
- Builder C (UI), 9 commits: Add bank statement (Arm it, empty Pulse, System), password sheet, progress/result copy, Pulse `BY STATEMENT · BY MONTH` report, Not sorted bar, bar sheet, statement-edition hiding, Review bulk "Mark these N as people"
- `272305b` lead fixes from the real statement: headline when most is unsorted; Insights coverage-aware and follows the statement; SMS-first smoke step
- `f735aae` review fixes: holder name scrubbed from every narration; exact vs similar own-name; no rule on the shared "unknown" key; no comparison against a half-covered month; password retry from the locked file; copy and docs

## How the agents were used (all Claude)
| Agent | Brief | Outcome |
|---|---|---|
| Designer (Plan) | Multiple months, the simple dashboard, button placement, review at scale, copy | BY STATEMENT · BY MONTH; part-month copy; "Add bank statement"; Not sorted bar |
| Architect (Plan) | `:core` API, schema v2, dedup keys, SMS matching, coverage, pipeline, work split | Balance-anchored keys, `stmt_key`, two-pass matching, memfd, A/B/C split |
| Critic (general) | Traps in the text format, categorisation on real data, privacy, scope | Ran a throwaway parser: cascade failures without row-start segmentation; current categoriser put metro rides and shops under people; own-name; month labels. Round 2: ruled on each disagreement |
| Builders A/B/C (worktrees) | See Done | Merged without conflicts; gate green |
| Reviewer (general) | Release review of `af55e1b..272305b` | 0 blockers, 4 should-fix, 7 nits → `f735aae` |

## Decisions
- **ADR-015:** statements read on the phone by the platform PDF text API. Rows are found by their start line, with direction from the balance. Completeness checks; balance-anchored row keys; coverage-aware months; Kotak only for now.
- **ADR-016:** a statement-only edition (flavor `statement`) with no SMS or notification-listener declarations. Owner approved.
- Version v0.1.3 (v0.2.0 keeps its scope).
- Debits the app could only guess as "a person" go to a separate Not sorted yet bar, so People & Cash isn't inflated. One tap in Review files them.

## Verified how
- `./gradlew :core:test :app:testFullDebugUnitTest :app:lintFullDebug :app:lintStatementDebug :app:assembleFullRelease :app:assembleStatementRelease :app:assembleFullDebug :app:assembleStatementDebug`: 73 tests, 0 failures, lint 0 errors.
- `scripts/statement-smoke.sh`: PASS, full and statement editions (emulator-5554). `scripts/android-smoke.sh`: PASS.
- Migration: a v0.1.2 install with 237 rows → v2, all rows and rules intact (builder B).
- Real picker flow (emulator-5556), fictional PDF → "35 ROWS · ADDS UP TO THE CLOSING BALANCE ✓" → report. The statement edition's start screen shows no SMS steps.
- Owner's real statement (emulator-5554, privately): complete, totals equal the bank's summary. The app data was wiped afterwards and the PDF removed from the emulator. Nothing was committed; screenshots stayed in /tmp.
- Release APKs: full 1,469,416 bytes; statement 1,452,088 bytes. `aapt2` (reviewer): the statement APK has no SMS permission, receiver or listener.

## Blocked / waiting on owner
- Install the statement edition and try their own PDF; then tap "Mark these N as people".
- Still open: `scripts/set-signing-secrets.sh`; the full app via `scripts/phone-install.sh` for live SMS.

## Next
CI green on the release commit → tag v0.1.3 → verify both APKs and checksums on the release page.
