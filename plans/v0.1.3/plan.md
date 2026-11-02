# v0.1.3: upload last month's bank statement, see where it went

**Status:** planned · **Updated:** 2026-11-02 · **Last release:** v0.1.2

Read [AGENTS.md](../../AGENTS.md) first. This is the checklist for the statement feature; daily work goes in `plans/week-NN/day-N/planned.md`. Status words are the same as in [v0.2.0](../v0.2.0/plan.md). v0.2.0 keeps its scope (rules editor, merchant pages, budgets) and follows this release.

## Why

The owner asked on 2026-11-02 (paraphrased): reading SMS isn't working for them yet, so add a button to upload last month's bank statement. The app should then show a simple dashboard of what happened that month, with a bar per category ("Shopping could be one category"), in the style of Navi's spend screen.

On Navi: no public screenshot or help page of Navi's spend screen could be found (2026-11-02 search). The design below follows the common pattern of Indian UPI apps: the month's total at the top, one bar per category below it, and tap to drill in. A screenshot from the owner would let us match Navi more closely.

## What the owner sees

Mockup with made-up numbers: [`mockup.html`](mockup.html) (open in a browser).

1. **The button.** "Upload October statement" sits at the top of **Arm it**, above the SMS steps, and works without SMS. It also appears on an empty Pulse and in **System → Bank statements**. The month in the label is last month.
2. **Picking the file.** Android's own file picker opens; it needs no permission. PDF, Excel and CSV files are accepted. A locked PDF asks for its password, which is used once to open the file and never saved.
3. **Progress.** `READING STATEMENT · 1 OCT → 31 OCT · 143 / 210 ROWS`. At the end it shows `210 ROWS · ADDS UP TO THE CLOSING BALANCE ✓`, or says which rows did not add up.
4. **The report.** Pulse opens on the statement's month. A finished month shows this simple layout:
   - the sentence "You spent ₹38,420 in October" and which category led;
   - the big number, then **Money in · Payments · Per day**;
   - **Where it went:** one bar per category group, biggest first. Each bar has its fixed colour, the amount, its % of spending and the number of payments;
   - **Top places:** the five biggest merchants;
   - **Not spending:** card bill, own-account transfers and SIPs, shown but never counted as spending;
   - a **Needs a look** chip for rows the app could not place.
5. **Drill in and teach.** Tapping a bar shows the sub-categories inside it (for example Groceries → quick commerce vs provision store) and the payments. Fixing a category once remembers it for that merchant, using the existing rules.

## Categories

Keep the existing taxonomy in [`docs/CATEGORIES.md`](../../docs/CATEGORIES.md). Its spend groups already match the usual "Navi-style" set:

- Food & Drink · Groceries · Transport · Home & Bills · **Shopping** · Health · Fun & Subscriptions · People & Cash · Other

Money moves (not spending) and Money in stay separate. Statements contain rows that SMS rarely do, so they map like this:

| Statement row | Category |
|---|---|
| ATM / cash withdrawal | ATM cash |
| ACH/NACH loan debit | EMI & loans |
| SIP debit (BSE/NSE clearing, fund house) | Investments (not spending) |
| Credit-card payment | Credit-card bill (not spending) |
| Transfer to an account in the owner's own name | Own-account transfer (not spending). The holder's name is read from the statement header. |
| Interest credit | Money in |
| Salary credit | Salary |
| Bank charges, GST on charges | **new: Bank charges & fees** (Other group) |

## How it works (for agents)

**`:core` → `statement/` (pure Kotlin, fixture-tested):**
- **`StatementTable`:** takes rows of cells.
  - Finds the header row from synonyms (`Date / Txn Date / Value Date`, `Narration / Description / Particulars / Remarks`, `Withdrawal / Debit / Dr`, `Deposit / Credit / Cr`, `Balance`).
  - Handles one amount column with a Dr/Cr column, Indian number formats (`1,23,456.78`, a trailing `Dr`/`Cr`) and dates (`01/10/26`, `01-10-2026`, `01 Oct 2026`).
  - Skips repeated page headers, the opening-balance row and footers. Joins narrations that wrap onto a second line.
- **`BalanceCheck`:**
  - Uses the running balance to settle the direction whenever the columns are unclear.
  - Checks that opening balance + credits − debits = closing balance, which proves the import is complete.
- **`Narration`:**
  - Turns the narration into a payee, VPA, instrument (UPI, POS/card, ATM, NEFT/IMPS/RTGS, ACH/NACH, cheque, interest, charges), the 12-digit UPI reference and hints.
  - Feeds the existing `MerchantNormalizer` and `Categorizer`, so statements and SMS are categorised by the same rules.

**`:app` → `ingest/StatementImport`:**
- Opens the file with the Storage Access Framework (`ACTION_OPEN_DOCUMENT`), so no storage permission is needed, and picks a reader by file type:
  - **CSV/TSV, and "XLS" files that are really HTML or text** (common from Indian net banking): a small reader of our own, no library.
  - **XLSX:** unzip it and read the XML with `XmlPullParser`, both built into Android. No library.
  - **PDF:** Android's own `PdfRenderer` text API. It is built in on Android 15+; on Android 11–14 it comes through `PdfRendererPreV` where the phone's system updates include it. It supports passwords and adds 0 bytes to the APK.
    - Fallback if it can't keep table rows together: PdfBox-Android (Apache-2.0, adds megabytes). Decide using the owner's real file.
  - **Old binary `.xls`:** only if the owner's bank offers nothing else.
- **Storage:**
  - New `Source.STATEMENT`.
  - Database v1 → v2 is the first real migration, and existing data must survive it.
  - `source_id` is a stable hash of each row, so importing the same file twice adds nothing.
  - Rows that have a date but no time are stamped 12:00 IST.
- **Matching against SMS:**
  - A statement row and an SMS row with the same UPI reference are one payment, and the SMS row is kept because it has the time.
  - Without a reference, rows match one-to-one on direction, amount and date (±1 day). Two ₹20 chais on the same day stay two rows.
- **Privacy is unchanged:**
  - The file is never copied and the password lives only in memory.
  - Nothing leaves the phone and no new permission is added.
  - Unreadable rows go to "Couldn't read", as with SMS.

## The owner's real statement

- The real file never goes into git, a doc, a commit message or a journal. It is kept in a scratch folder outside the repo and deleted after use. Only its *layout* (column names, narration styles) is copied into a **fictional** fixture with made-up names and amounts.
- Needed from the owner:
  - last month's statement as the bank sends it (the emailed PDF is fine);
  - which bank it is from;
  - the password if it is locked, or a copy with the lock removed;
  - if net banking also offers Excel or CSV, that file too.

## Work in order

| ID | Status | Task / done when | Evidence or blocker |
|---|---|---|---|
| 1 | TODO | Critic + formats agents (Claude): edge cases, and fictional layouts and narrations for SBI, HDFC, ICICI, Axis and Kotak. | Agent reports summarised in the day journal. |
| 2 | TODO | `:core` table, amounts, dates and balance check, with fixtures for each layout. | `./gradlew :core:test` green. |
| 3 | TODO | `:core` narration → categoriser, with fixtures: UPI, POS, ATM, NEFT/IMPS, ACH EMI/SIP, card bill, salary, interest, charges, refunds and reversals. | Tests green; no existing test weakened. |
| 4 | TODO | `:app` picker, readers (CSV, HTML-XLS, XLSX, PDF), password, progress; DB v2 migration; matching with SMS; re-import adds nothing. | Migration test from a v1 database; import twice → same row count. |
| 5 | TODO | UI: the upload button (Arm it, empty Pulse, System) and the report layout for a finished month (ranked group bars, top places, not spending, tap-through). | Emulator screenshots checked by eye. |
| 6 | BLOCKED | Owner's bank format: fictional copy of its layout as a fixture; the real file gives the same totals as the bank's own summary. | Owner: send the statement (see above). |
| 7 | TODO | Verify: core tests, lint, smoke, emulator with fictional PDF and CSV, independent Claude review. | Commands and screenshots in the journal. |
| 8 | TODO | Release v0.1.3 under the standing approval, once the exact-commit CI is green. | Release page and checksum checked. |
| 9 | ASK | **Statement-only edition:** a second APK that never asks for SMS or notification access, so Play Protect's SMS block does not apply and it installs from the browser like any app. It is a separate build that simply cannot read SMS, not a way around the check. | Owner decides. Recommended: yes. |
