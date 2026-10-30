# Week 1 · Day 3 — 2026-10-31 (Saturday)

**Version in progress:** v0.1.2 (last tagged: v0.1.1)
**Status:** in progress — release commit pushed, waiting for CI before tagging

## Plan for today
Owner asked (paraphrased): (1) "solve the Google Play Protect thing", make it just work the way OTP auto-fill works; (2) when SMS is linked, read the SMS history automatically and fill the right month: on Nov 1 bring in all of October, on Nov 15 only Nov 1–15; (3) one more release; (4) use several **Claude** agents (no Codex) and discuss it properly first.

- [x] Claude agents in parallel: (a) Play Protect research, (b) design for the month-window import, (c) critic: edge cases, tests, first-run UX
- [x] Decide from their findings; record in `docs/DECISIONS.md` (ADR-013, ADR-014, ADR-009 correction)
- [x] Implement the import window (pure function in `:core` + tests, wire into first SMS link)
- [x] Verify on the emulator (screenshots), tests, lint, smoke
- [x] Independent review by a Claude agent → 0 blockers, 5 should-fix + nits, all fixed
- [ ] Cut v0.1.2 (owner approved "one more release" on 2026-10-31): CI green on the release commit → tag → verify the published release

## What is happening now
Release commit pushed; waiting for the `main` CI run, then tag `v0.1.2`.

## Done
- `50772e9` day plan · `dd6ad71` `HistoryWindow` + 6 tests · `0f07b2c` automatic first-link read, Arm-it/System/Pulse UI · `12620b4` past-tense headline · `24cee52` `scripts/seed-inbox.sh` · `eef708c` `scripts/phone-install.sh`, `scripts/set-signing-secrets.sh` · `6987a43` docs (INSTALL rewrite, ADR-013/014) · `642b9c9` review fixes.

## How the agents were used (all Claude, no Codex)
| Agent | Brief | Outcome |
|---|---|---|
| Research (general-purpose, web) | What triggers the India block, exempt install routes, OTP APIs, how Play expense apps do it, developer verification | Not solvable in-app; declared permissions + internet install trigger it; adb exempt; OTP APIs can't read bank alerts; Play has an "SMS-based money management" exception; dev verification not in India before 2027 |
| Design (Plan) | Pure window function, CatchUp changes, UI, performance | Day ≤ 7 calendar rule, `history_from`, lock, 200-row batches, "Add <month>" |
| Critic (general-purpose) | Ambiguities, edge cases, tests, copy | Same rule independently; found the double-scan race, empty-window re-import, late-alert misfiling, Pulse "this month" label; test table used in `HistoryWindowTest` |
| Reviewer (general-purpose) | Review `c977e57..HEAD` | No deadlock/blockers; delete re-import of the newest row, `last_sms_at` going back, truncated past-month comparison, receiver starvation, unparsed duplicates → fixed |

## CI failure and fix
- Run `41116330845` on `6987a43` failed the emulator smoke test (`rows=3 … duplicates=`): CI runners are UTC, and it ran at 01:07 IST, so `date` wrote yesterday's date (30-10-26) into the bank SMS while the paired notification was stamped 31 Oct IST, more than 15 min apart, no de-dup. Test-data bug, not app behaviour (real bank SMS carry IST dates). Reproduced locally with `TZ=UTC` (old script FAIL, fixed script PASS); fix: `TZ=Asia/Kolkata date` in `scripts/android-smoke.sh`. Run `41118370314` on the release commit would have failed the same way and was superseded.

## Decisions
- **Play Protect (ADR-014):** no in-app fix and no permission hiding. The install guide now leads with adb (USB/Wi-Fi) from this Mac, which also keeps updates working with the private key. Play internal testing is written up as the owner's option, not started.
- **Import window (ADR-013):** calendar rule, day ≤ 7 includes all of last month; Pulse opens on last month on days 1–3. Older months only on request ("Add September").
- **Version:** v0.1.2, not v0.2.0, though it adds a feature, so the planned v0.2.0 milestone (rules editor, merchant pages, budgets) keeps its scope.
- Delete everything no longer re-imports by itself (`wiped_at`).

## Verified how
- `./gradlew :core:test :app:lintDebug :app:assembleRelease :app:assembleDebug`: 35 tests 0 failures, 0 lint errors; release APK 1,403,876 bytes, `CN=Credu1ar`, versionCode 3, dex contains the new code.
- `scripts/android-smoke.sh`: SMOKE PASS (after the feature and again after the review fixes).
- Emulator `mn35` with root-seeded inbox (fictional messages only):
  - Clock 2 Nov, real permission dialog: `1 OCT → TODAY · 4 PAYMENTS FILED`; DB shows 1 Oct, 20 Oct, 31 Oct, 2 Nov; Aug/Sep/OTP/chat not stored; Pulse opened on October with "SPENT IN OCTOBER".
  - Clock 15 Nov (permission via settings path): November only. The 31 Oct alert received 1 Nov was held back; **Add October** filed it.
  - Link → wipe → reopen: 0 rows. Add October / Add September restore rows; `last_sms_at` unchanged.
  - 3,000-SMS inbox: first link ≈12.6 s incl. app start; 1,000 filed, 2,000 ignored, 0 unreadable; ~90 MB PSS right after.
- `apksigner`: published v0.1.0 cert `9084ad57…` ≠ v0.1.1 `51fd2889…` (each CI release has a new throwaway key).
- Emulator clock restored to automatic; seeded SMS deleted.

## Blocked / waiting on owner
- Get the app onto the phone: plug in / pair Wi-Fi debugging so the agent can run `scripts/phone-install.sh`, or try the scan pause.
- `scripts/set-signing-secrets.sh` with the personal `gh` login (stops the throwaway-key problem).
- Whether to pursue Play internal testing (ADR-014 Route C).
- Real-phone test (`docs/PHONE-TEST.md`).

## Next
Tag v0.1.2 after CI is green on the release commit; verify the release page and checksum; mark this file done.
