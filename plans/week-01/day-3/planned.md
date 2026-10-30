# Week 1 · Day 3 — 2026-10-31 (Saturday)

**Version in progress:** v0.1.2 (last tagged: v0.1.1)
**Status:** in progress

## Plan for today
Owner asked (paraphrased): (1) "solve the Google Play Protect thing", make it just work the way OTP auto-fill works; (2) when SMS is linked, read the SMS history automatically and fill the right month: on Nov 1 bring in all of October, on Nov 15 only Nov 1–15; (3) one more release; (4) use several **Claude** agents (no Codex) and discuss it properly first.

- [ ] Claude agents in parallel: (a) Play Protect research — what actually triggers the India block, which install routes are exempt, whether an OTP-style API can read bank alerts; (b) design for the automatic month-window import in this codebase; (c) critic — edge cases, tests, first-run UX
- [ ] Decide from their findings; record decisions here and in `docs/DECISIONS.md`
- [ ] Implement the import window (pure function in `:core` + tests, wire into first SMS link)
- [ ] Verify on the emulator (screenshots), tests, lint, smoke
- [ ] Independent review by a Claude agent
- [ ] Cut v0.1.2 (owner approved "one more release" on 2026-10-31)

## What is happening now
Briefing the Claude agents.
