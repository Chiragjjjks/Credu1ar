---
name: add-bank-parser
description: Teach Credu1ar a new bank, UPI app or SMS format. Use when the owner pastes (or describes) a payment message the app misread, ignored, or listed under "Couldn't read", or asks to support a new bank or merchant. Fixture first, then the smallest rule, then tests.
---

# Add a bank / SMS format

Read `docs/PARSING.md` and `docs/CATEGORIES.md` first. Code lives in `core/` (pure Kotlin, JVM tests).

## Steps

1. **Anonymise.** Never keep a real message. Replace names, account tails, references and amounts with invented ones; keep the *shape* (wording, punctuation, line breaks, the order of fields). If the owner pasted a masked report (digits as 9s), invent plausible digits.
2. **Write the fixture first** in `core/src/test/kotlin/io/github/chiragjjjks/credu1ar/core/ParserFixtures.kt`:
   `Fixture("short descriptive name", "VM-BANKHDR-S", "<message>", DEBIT|CREDIT, <paise>, "<Merchant display name>", <Category>, "<tail>", Instrument.X, "<ref>")`.
   Omit fields you do not care about. Messages that must be ignored have no direction. Foreign-currency or unreadable ones use `unreadable = true`.
3. `./gradlew :core:test` → it must fail, and for the reason you expect (the runner lists every mismatch).
4. **Smallest fix**, in this order of preference:
   - a sender prefix in `parse/SenderDirectory.kt`,
   - a brand alias in `merchant/MerchantDirectory.kt` (lowercase; remember glued handles),
   - a keyword in `category/Categorizer.kt`,
   - a regex tweak in `parse/SmsParser.kt` (check `STRONG_CUE`, `BALANCE_CTX`, `BOILERPLATE`, `DEBIT_PAYEE`/`CREDIT_PAYEE`).
5. Run `./gradlew :core:test`. **All** tests must pass: old fixtures guard against regressions. Never edit an old fixture to make it pass unless it was factually wrong, and say so in the commit.
6. If the adversarial ratchet exists (`AdversarialFixtureTest.knownFailures`) and your fix makes a listed case pass, remove its name from the set in the same commit.
7. Optional end-to-end check on the emulator: `scripts/inject.sh sms <HEADER> "<message>"` then `scripts/dump-db.sh`.
8. Commit: `fix(parser): <bank> <what>` and append a line to today's `plans/week-NN/day-N/planned.md`.

## Do not

- Do not copy code or regexes from other open-source SMS parsers (PennyWise AI is copyleft).
- Do not log, store or print message bodies outside the debug injector and the masked report.
- Do not make the parser accept a message that has no completed-event verb to "catch" a format; add a verb to the cue list instead.
