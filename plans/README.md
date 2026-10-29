# plans/ — the project's day-by-day memory

Every day that work happens gets one folder and one file. Anyone (human or agent) can read `plans/` from week 1 to now and know what was planned, what happened, what was decided and what is next.

```
plans/
  README.md                      <- this file (conventions + index)
  week-01/
    day-1/planned.md             <- first day of work in week 1
    day-2/planned.md
  week-02/
    day-1/planned.md             <- only days with real work exist: 1 to 7 per week
```

## Rules

- **Week N** = seven calendar days counted from the project start (week 1 starts 2026-10-29, so week 2 starts 2026-11-05). **Day N** = the Nth day of that week. Skip days with no work; there are no empty files.
- One `planned.md` per day. It is written at the start (plan) and finished at the end (outcome). Do not rewrite older days; add a dated correction line.
- Keep it factual and short. Link to commits by SHA, docs by path. Never paste tokens, real SMS text or personal data.

## Template for `planned.md`

```markdown
# Week N · Day N — YYYY-MM-DD (weekday)

**Version in progress:** vX.Y.Z (last tagged: none | vA.B.C)
**Status:** planning | in progress | done | blocked

## Plan for today
- [ ] item

## What is happening now
One or two lines: the thing being worked on this minute.

## Done
- item (commit SHA)

## Decisions
- what, why (and ADR number if added to docs/DECISIONS.md)

## Verified how
- command / screenshot / emulator check, with the result

## Delegated (Codex)
- task, brief location, worktree, result

## Blocked / waiting on owner
- item

## Next
- the exact next step
```

## Index

| Week | Day | Date | Headline |
|---|---|---|---|
| 1 | 1 | 2026-10-29 | Research, plan, repo, core parser, app skeleton (see file) |
