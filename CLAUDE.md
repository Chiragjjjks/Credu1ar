@AGENTS.md

## Claude Code notes

- `AGENTS.md` (imported above) holds the project context shared with Codex. Put project facts there, not here.
- **Start of every session:** read `AGENTS.md` and the newest `plans/week-*/day-*/planned.md`. **End of every session:** update that file, `Current state` in `AGENTS.md`, and commit.
- Verify UI on the emulator (`mn35`), not by reading code. Take screenshots with `adb exec-out screencap -p`.
- Project skills live in `.claude/skills/`: `add-bank-parser` (new SMS format → fixture → rule → test) and `cut-release` (version bump, notes, checks; stops before tagging until the owner approves).
- If usage is about to run out mid-task, hand the remaining work to Codex through `delegate_task` using `AGENTS.md` plus the relevant `docs/*.md` as the brief.

## Compact instructions

When compacting, write the summary so the next context window knows **what we are building first, then where we are**. `AGENTS.md` is reloaded after compaction, so don't copy it. Summarise only what this conversation added or changed, in this order:

1. **Goal.** One line: Credu1ar (see `AGENTS.md`) plus what this session is trying to achieve.
2. **The owner's requests.** Quote each verbatim, newest last, marked done / in progress / not started, with corrections and reasons.
3. **Decisions.** What was chosen and why, including approaches tried and dropped.
4. **Work state.** Commits (SHA + one line), uncommitted files, CI run IDs, what was verified and how, unresolved errors verbatim.
5. **Waiting on the owner.** Approvals (releases, tags, Pages), phone tests, unanswered questions.
6. **Live environment.** Running emulator/dev processes, delegated task IDs and their worktrees, temp files holding personal data (paths only).
7. **Next step.** The exact command or edit, and whether it needs approval.

Leave out tool output dumps and file contents already in git. Never put tokens, real SMS text or the owner's personal data in the summary.
