# v0.2.0 — useful decisions from your spending

**Status:** planned · **Updated:** 2026-10-29 · **Last release:** v0.1.0

Read [AGENTS.md](../../AGENTS.md) first. This is the next-version checklist; daily work still goes in `plans/week-NN/day-N/planned.md`. Keep all processing on-device and retain Signal Noir.

## Status rules

`TODO` = not started · `DOING` = active · `DONE` = verified · `BLOCKED` = needs input · `DEFERRED` = saved for later · `NO` = dropped, with a reason.

Update the row in the same commit as the work. For `DONE`, add the commit and verification evidence; for `BLOCKED`, name what is needed. Never mark a suggestion done just because it is documented. Keep one implementation row `DOING` at a time.

## Work in order

| ID | Status | Task / done when | Evidence or blocker |
|---|---|---|---|
| 1 | BLOCKED | Real-phone capture: follow the phone checklist; missed formats get fixtures before parser changes. | Owner: [phone test](../../docs/PHONE-TEST.md) and Diagnostics. Independent feature work can proceed. |
| 2 | TODO | Rules editor: list, edit and remove learned rules; clearly choose one payment, past payments or future payments. | Verify rule changes, totals and persistence after restart. |
| 3 | TODO | Merchant pages: total, payment count, average, month comparison and tappable history. | Verify totals against Ledger and review emulator screenshots. |
| 4 | TODO | Category budgets: set a monthly limit, show remaining amount and pace; optional alerts without repeated notifications. | Test month boundaries, overspending and excluded transfers/settlements. |
| 5 | TODO | Finish and verify: coherent navigation, accessible labels/large text, restrained motion and haptics; measure capture CPU, idle memory/battery and APK size. | Core tests, lint, build, emulator smoke, screenshots and recorded measurements; label anything unmeasured. |
| 6 | BLOCKED | Release readiness: preserve v0.1.0 data/rules across migration, write notes and pass exact-commit CI before tagging. | Owner: signing secrets and explicit v0.2.0 release approval. |

## Saved follow-ups

These remain visible but are outside the first three feature milestones. Revisit after budgets; record any scope change here.

- **DEFERRED:** recurring-payment calendar — confirm detected patterns, then offer local reminders.
- **DEFERRED:** richer corrections — manual cash entries, split categories, refund links and self-transfer corrections.
- **DEFERRED:** encrypted backup/restore and app lock — preserve learned rules as well as payments.
- **DEFERRED:** home-screen widget, monthly recap and decorative grain. Pages publication is approved separately; deployment progress is in the daily journal.

**Next agent action:** inspect current rules storage and category correction flow, then move task 2 to `DOING`. Ask the owner for the phone report only if still missing; continue independent work while waiting.
