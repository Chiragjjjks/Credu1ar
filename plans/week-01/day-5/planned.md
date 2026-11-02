# Week 1 · Day 5 — 2026-11-02 (Monday)

**Version in progress:** v0.1.3 (last tagged: v0.1.2)
**Status:** planning

## Plan for today
Owner asked (paraphrased): "since the SMS is not working properly", add a button to upload last month's bank statement, then show a simple dashboard of what happened, with category bars (Shopping etc.) like Navi. Plan first, with how the UI will look. The owner sends their real statement afterwards. Then talk to other agents and build.

- [x] Look for how Navi does it. No public screenshots or help pages were found, so the design follows the common UPI-app pattern; asked the owner for a screenshot.
- [x] Plan + mockup: [`plans/v0.1.3/plan.md`](../../v0.1.3/plan.md), [`plans/v0.1.3/mockup.html`](../../v0.1.3/mockup.html) (made-up numbers)
- [ ] Owner: statement file + bank + password; answer the statement-only edition question
- [ ] Claude agents: critic + statement formats (fictional fixtures)
- [ ] Build per the plan's checklist

## What is happening now
Plan delivered; waiting for the owner's statement and their answer on the statement-only edition.

## Decisions
- Version **v0.1.3** (same reasoning as v0.1.2: v0.2.0 keeps its scope).
- Keep the existing category taxonomy (it already matches the Navi-style groups); add only **Bank charges & fees**.
- PDF: try Android's built-in `PdfRenderer` text API first (0 bytes added); PdfBox-Android only if needed.
- The real statement never enters git or docs; only a fictional copy of its layout does.

## Verified how
- Category palette re-run through the dataviz validator on dark: all checks pass (worst adjacent CVD ΔE 8.4, normal 19.3).
- Mockup rendered and checked by eye.

## Blocked / waiting on owner
- The statement (see plan → "The owner's real statement").
- Statement-only edition: yes/no.
- Still open from day 3: phone install via `scripts/phone-install.sh`, `scripts/set-signing-secrets.sh`.

## Next
Start checklist rows 1–5 of `plans/v0.1.3/plan.md` (they don't need the real file); row 6 when the statement arrives.
