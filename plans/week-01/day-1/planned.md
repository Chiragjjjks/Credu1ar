# Week 1 · Day 1 — 2026-10-29 (Thursday)

**Version in progress:** v0.1.0 (last tagged: v0.1.0)
**Status:** done (v0.1.0 and landing site published; v0.2.0 checklist saved)

## Plan for today
- [x] Publish the approved landing site through GitHub Pages and verify its live URL.
- [x] Save a brief v0.2.0 plan in its own directory, with task statuses and links for the next session.
- [x] Continuation: inspect Claude's final commit and exact-commit CI; finish the approved v0.1.0 release, verify published assets, and record the outcome.
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
v0.1.0 is published from `2029153`. Exact-commit main CI and the tag workflow passed; the downloaded release APK matches the tested artifact. Release handoff and next-version checklist are saved. The landing site is now published and verified; next implementation starts at `plans/v0.2.0/plan.md`.

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
- **Owner instructions mid-day:** commit every small change and push to GitHub so progress is visible there (`AGENTS.md` rule, memory saved); "Do a release also" approves v0.1.0. I held the tag until the parser red-team findings were fixed, because shipping known phantom-payment bugs in a money app would be the wrong release.
- CI runs without signing secrets (`gh` here is the work account with read-only access; secrets are the owner's), so the published v0.1.0 APK is signed with a throwaway key. Stated plainly in the release notes with the recovery path (re-import from SMS, CSV export).

## Verified how
- `./gradlew :core:test` → 26 tests, 0 failures. `lintDebug` → 0 errors.
- Emulator (Android 15, arm64): injected HDFC UPI (single PDU), ICICI card (2-part PDU), Google Pay notification, OTP, duplicate PhonePe notification → DB had exactly 2 payments, `n_ignored=1`, `n_duplicate=1`, SMS source kept. `scripts/android-smoke.sh` → SMOKE PASS.
- Release APK (R8) installed with the private key: launches, loads sample data via the UI, no crash, identical numbers; `DebugInjectReceiver` absent. Size 1.32 MB; PSS ~38 MB with UI open.
- Screenshots reviewed by eye for every screen; palette validated with `validate_palette.js` (dark, #000 and #0a0a0a).

## Delegated (Codex, gpt-6.1-sol, worktrees)
- **Landing site `site/`: DONE, reviewed, merged** (`121d348`, from branch `t3/build-privacy-landing-page` @ `3fbdb2c`). I did not take the report on trust: confirmed only `site/` changed (9 files, owner identity), no external URLs beyond the project's own GitHub links, no `fetch`/XHR/beacon, no trackers or invented stats, ~46 KB excluding fonts, and looked at it in headless Chrome (desktop hero, demo, category ring; true 390 px layout via iframe; zero horizontal overflow at 320/360/375/390/414/768). Headless Chrome crops below ~500 px wide, which looked like a clipped layout until re-tested at true width. Not tested: real phone browsers. Not deployed (GitHub Pages needs the owner).
- **Adversarial parser fixtures: DONE, reviewed, merged, all findings fixed.** Branch `t3/adversarial-parser-fixtures` @ `9515d6d`; scope verified (exactly 2 test files). 100 synthetic fixtures written from ground truth: **64 passed, 36 failed on my parser**, several of the dangerous kind (a balance or credit limit recorded as the payment amount, a promo or merchant receipt recorded as a payment, one wrong direction). I did not release on top of that. Fixed in three commits, each with the ratchet updated: `289f38b` (balances/limits, owner-account direction, `INR.450`/`Dr`/`Cr`/`Txn completed`, refund-with-failed, rail vs "card"), `6e19974` (promo/receipt/OTP-challenge/future-credit/pending-refund guards), `a336313` (brand-prefix suffixes, CRED store purchases, payee names, two payments in one SMS). Now 100/100, ratchet `knownFailures` is empty, 28 tests total.
  - **A regression I caused and the ratchet caught:** making promo wording reject unconditionally broke `T22` (a real debit followed by "Apply now for our new card."). Fixed by scoping promo rejection to wording at/before the amount's sentence or with no account named.
  - Codex also flagged design questions I am recording, not solving today: sender role (merchant vs bank headers) should gate ingestion more; one `Outcome.Txn` cannot represent two payments (now reported as unreadable); `CRED` category needs transaction-purpose evidence (partly done via the `merch` hint).

## Blocked / waiting on owner
- Real-phone test (`docs/PHONE-TEST.md`) — only the owner can do it.
- CI signing secrets (`CREDU1AR_KEYSTORE_BASE64` etc.) before the next release. v0.1.0 and Pages were approved and are now published.
- Confirm the app/package name (`Credu1ar`, `io.github.chiragjjjks.credu1ar`).

## Next
Owner: install v0.1.0 and run `docs/PHONE-TEST.md`; return Diagnostics and missed formats. Set CI signing secrets before a later release. Continue the v0.2.0 checklist; Pages publication is complete.

## Continuation — 2026-10-29, Codex following Claude's release handoff

**Plan:** keep Claude's release scope and product decisions; inspect `2029153` and CI `40916416903`, fix any failed gate, publish the already-approved v0.1.0 tag only after its exact commit passes, then verify the APK/checksum and update the handoff. No new feature work or Pages deployment.

**Done:** `v0.1.0` tagged at `20291534dd777315e90659e347601f09843a3112` using personal SSH; [release published](https://github.com/Chiragjjjks/Credu1ar/releases/tag/v0.1.0) at 17:02:53 UTC. Both assets are uploaded: `Credu1ar-v0.1.0-android.apk` and `SHA256SUMS.txt`. Updated AGENTS Current state / Next and this journal to remove completed integration, CI and approval items.

**Decisions:** followed Claude's scope and release checklist (`.claude/skills/cut-release/SKILL.md`), using the owner's existing explicit v0.1.0 approval (AGENTS.md). No product decisions changed; Pages remains undeployed. No need for another approval of the same release.

**Verified how:**
- [Main CI 40916416903](https://github.com/Chiragjjjks/Credu1ar/actions/runs/40916416903) succeeded on exactly `2029153`: version check, core tests, lint, release/debug builds, signature verification and Android 35 x86_64 smoke. Downloaded smoke evidence: `rows=2 accepted=2 ignored=1 duplicates=1 zepto=quick_commerce/45000/sms`.
- [Tag workflow 40917498126](https://github.com/Chiragjjjks/Credu1ar/actions/runs/40917498126) succeeded and reused the main artifact without rebuilding.
- Downloaded the published APK and checksum; SHA-256 **`edcfeba1e7651d4c52fb71986f14b30c3a4e6daa092e913cd3a4d30e219dfad0`** matches `SHA256SUMS.txt`, GitHub's asset digest, and the CI APK byte for byte. Size **1,403,876 bytes**, version **0.1.0 / 10004**, min SDK 26. `apksigner verify --verbose --print-certs` passed; certificate is Android Debug as disclosed. Packaged manifest has `allowBackup=false`, no `debuggable=true`, no debug injector.
- Installed that exact CI release APK on the local Android 15 `mn35` emulator (replacing only the earlier synthetic debug-test install), cold launch succeeded, loaded sample data, visually checked Pulse and its honest SAMPLE DATA / NOT LISTENING strip; no fatal exception. Evidence: ignored local `out/v0.1.0-ci/pulse-release.png`.
- Local `:core:test` rechecked (up-to-date), existing XML reports 28 tests / zero failures; `:app:testDebugUnitTest` has no test sources. `scripts/check-version.sh v0.1.0` passed. Claude's local full gate remains recorded above.

**Blocked / waiting on owner:** physical-phone checks and real formats; signing secrets for future update continuity; optional Pages approval. None prevented the approved preview release.

**Next:** owner runs `docs/PHONE-TEST.md` against the published APK and returns Diagnostics. Future agents should start at AGENTS.md, add fixtures for actual missed formats, and keep the release signing caveat visible.

## Next-version planning — 2026-10-29

**Plan for this session:** create `plans/v0.2.0/plan.md`, keep it brief and actionable, define status updates and completion evidence, and link it from the project handoff. Planning only; implementation and a later release are separate work.

**Done:** created `plans/v0.2.0/plan.md` with ordered tasks, explicit statuses, completion checks, owner dependencies and deferred improvements. Linked it from AGENTS.md, PLAN.md and plans/README.md.

**Decisions:** version checklists stay separate from day-by-day history; update statuses with the work and attach evidence before marking DONE (ADR-011). The first feature milestones are rules editor, merchant pages and budgets. Recurring calendar, richer corrections, encrypted backup, app lock, widget/recap and decorative grain remain visible as deferred work.

**Verified how:** checked relative Markdown links, checklist statuses and `git diff --check`. Documentation only; app tests are unnecessary for this change.

**Blocked / waiting on owner:** no blocker to saving the plan. Phone results, signing secrets and approval remain requirements for the relevant future tasks.

**Next:** use the version checklist in the next session; inspect the rules storage/correction flow and begin task 2. No app implementation or version bump in this planning session.

## GitHub Pages publication — 2026-10-29

**Plan:** owner explicitly approved publishing the landing site ("yessss"). Add a minimal Pages workflow for `site/`, enable Pages using the personal account, deploy and check the live URL/assets/download link.

**Done:** prepared `.github/workflows/pages.yml` using GitHub's static Pages template, publishing only `site/` with path-filtered pushes and a manual trigger. Recorded owner approval and pending setup in AGENTS.md, README, site/README and the version checklist.

**Decisions:** separate site deployment from Android APK releases; no new hosting service or build dependency (ADR-012).

**Verified how:** workflow YAML parses, uploads only `site/`; HTML/CSS asset references resolve and JavaScript syntax passes. The first asset-check draft incorrectly treated an embedded SVG fragment as a file; corrected the checker, then reran successfully. Latest APK release is already published. No app or visual changes.

**Blocked / waiting on owner:** the CLI account has `pull=true`, `push=false`, `admin=false`; Pages is disabled. The available browser shows Sign in, so no authenticated personal settings session exists. The official configure-pages action documents that enabling Pages needs a token other than GITHUB_TOKEN. Owner must open Settings → Pages → Source = GitHub Actions and run Actions → Landing page → Run workflow.

**Next:** verify a successful Pages deployment and HTTP 200 for the expected URL `https://chiragjjjks.github.io/Credu1ar/` plus CSS/JS/fonts and the APK link. Update publication status to DONE only after those checks.

### Pages verification complete — 2026-10-29

**Done:** owner confirmed "Enabled Pages and ran workflow". [Deployment 40920360529](https://github.com/Chiragjjjks/Credu1ar/actions/runs/40920360529) succeeded from `affc533`; live URL: **https://chiragjjjks.github.io/Credu1ar/**. Updated the current handoff, README, site deployment instructions and version checklist to mark publication DONE. The initial run `40919987007` had failed at Configure Pages before the owner enabled the setting; that blocker is now resolved.

**Decisions:** no further hosting or UI changes; the existing workflow handles future site updates.

**Verified how:** Pages API confirms `build_type=workflow` and HTTPS enforced. Python HTTP checks verified 200 responses and exact byte matches for index.html, styles.css, main.js, favicon.svg and both bundled fonts. Both APK buttons follow GitHub Releases/latest to v0.1.0 (HTTP 200). The web fetch tool could not access this URL, so verification used direct HTTP requests instead. No fresh browser visual check was needed: published assets match the already-reviewed site exactly.

**Blocked / waiting on owner:** no remaining Pages blocker. Real-phone test and future signing secrets remain separate app tasks.

**Next:** start the v0.2.0 rules editor from `plans/v0.2.0/plan.md` in the next implementation session.
