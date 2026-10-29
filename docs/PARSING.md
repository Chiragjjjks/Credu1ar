# How SMS become payments

Code: `core/src/main/kotlin/.../core/parse/SmsParser.kt`, `merchant/`, `category/Categorizer.kt`, `Pipeline.kt`. Tests: `core/src/test/.../ParserFixtures.kt`.

## The contract

A message is a **completed payment** only if all of these hold:

1. It has an INR amount that is not a balance (`Avl Bal`, `limit`, `due`, `total`…), or an `X debited by 312.0` style amount.
2. It has a past-tense money verb (debited, credited, spent, paid, sent, received, withdrawn, refunded…). "payment of" and "purchase" alone are not enough, because OTP texts use them.
3. None of: OTP without a completed-event verb, failed/declined, requested/collect, upcoming ("will be debited", "is due", statements, reminders), promo without a verb.
4. An SMS sender that is a phone number is never a bank alert.

Anything else is `Ignored`: counted, dropped, never stored or logged. A payment-looking message the parser cannot read (e.g. foreign currency) is `Unreadable` and kept, masked on screen, so a rule can be added.

## Stages

`sanitise` (newlines → ` | `) → `gate` (cues + rejects) → `amount` (first non-balance amount) → `direction` (the money verb nearest the amount) → `account tail`, `reference`, `VPA`, `date/time` (message date wins over arrival time, absurd dates fall back to arrival) → `payee` (slash format `UPI/P2M/ref/NAME`, then `to|at|towards NAME`, `; NAME credited`, `on NAME`, bracketed name; boilerplate such as "Not you? Call…" is cut first) → `merchant` (rail prefixes stripped, brand directory, glued handles like `apollopharmacy@icici`) → `category` (your rule → hints → brand → name keywords → looks like a person → unknown).

Hints are lowercase tags the categoriser reads: `salary`, `refund`, `card_bill`, `emi`, `rent`, `invest`, `self`, `interest`, `atm`, `mandate`.

## Adding or fixing a bank format (the `add-bank-parser` skill does this)

1. **Fixture first.** Add a `Fixture(...)` to `ParserFixtures.kt` with a *synthetic* message: invented names, numbers and references. Never paste a real SMS. State the expected direction, paise, merchant, category, tail, instrument, reference.
2. Run `./gradlew :core:test` and watch it fail for the right reason. The runner reports every mismatch at once.
3. Change the smallest thing: a regex in `SmsParser`, an alias in `MerchantDirectory`, a keyword in `Categorizer`. Prefer data (aliases, sender prefixes) over new regex branches.
4. All 26+ tests must pass. Never weaken a fixture to make it pass.
5. If it is a new sender header, add the prefix to `SenderDirectory`.
6. Add the case to the journal (`plans/…`) and mention it in the release notes.

To turn a **real** message from your phone into a fixture: System → Couldn't read → *Copy report* (digits become 9s), then replace names and write the expected result.

## Testing on the emulator

The emulator console's `sms send` does not deliver on current images, so the debug build ships an injector that builds real multi-part SMS PDUs and calls the production `SmsReceiver`:

```bash
scripts/inject.sh sms VM-HDFCBK-S "Rs.450.00 debited from a/c **1234 on 29-10-26 to VPA zepto@axisbank(UPI Ref No 630112345678)"
scripts/inject.sh notif "Google Pay" "Paid ₹312 to Zepto"
scripts/inject.sh sample         # a fictional 3-month ledger
scripts/dump-db.sh               # what the app stored
scripts/android-smoke.sh app/build/outputs/apk/debug/app-debug.apk
```

## Independent red-team corpus

`AdversarialFixtures.kt` holds 100 synthetic messages written by a *different* agent (Codex) from ground truth, not from the parser's behaviour: false positives that must be ignored (promos, receipts, OTP challenges, future credits, refunds that are only initiated), false negatives that must parse (other banks' styles, `INR.450`, `Dr`/`Cr`, `Txn … completed`), and tricky ones (balances before the payment, names containing "on"/"from", two payments in one SMS). The first run failed 36 of 100; all 36 are fixed.

`AdversarialFixtureTest` is a **ratchet**: it asserts that the set of failing fixture names equals `knownFailures` (currently empty). Break a fixture and the build fails; fix one that is listed and the build also fails until you remove its name. When a real message is misread, add it to `ParserFixtures.kt` (or here), and if you cannot fix it right away list its name in `knownFailures` so the debt is visible.

## Rules the red-team forced (read before changing the gate)

- A balance or limit phrase ("Available balance in A/c XX1 is Rs 9000", "Available credit limit on Card XX1: Rs 80000") consumes the first amount after it (`BALANCE_SPAN`); it is never the payment.
- The account's own verb wins ("A/c XX1 has been debited … which is credited").
- Promo wording only rejects a message when it sits at or before the sentence holding the amount, or when no account is named. A marketing footer after a real debit must not hide the payment (fixture T22).
- An OTP *challenge* (code next to "OTP") before the money verb is a checkout step; a "never share your OTP" footer is not.
- Merchant receipts, telco activations and complaint acknowledgements are not bank alerts unless an account is named.
- Promotional `-P` SMS headers are never payment alerts.

## Known gaps

Fixtures are still synthetic, so coverage of real bank wording is unproven until the owner's real messages arrive (System → Couldn't read, then the `add-bank-parser` skill). One SMS holding two payments is reported as unreadable rather than split.
