# v0.1.3: upload last month's bank statement, see where it went

**Status:** building · **Updated:** 2026-11-02 · **Last release:** v0.1.2

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

## Agreed after the agent discussion (2026-11-02)

The owner sent their real Kotak statement and asked to "think about multiple months". Three Claude agents (designer, architect, critic) reported, then the critic ruled on their disagreements. Full reasoning: ADR-015 and ADR-016. This list **overrides** the sections above where they differ.

- **Owner answers:** build the statement-only APK too ("Go ahead"); the phone runs Android 15+, so the built-in PDF text API is enough.
- **Kotak PDF only** for v0.1.3. CSV, XLSX and other banks are deferred (fictional fixtures first, later).
- **The button** says **"Add bank statement"**, with the line `KOTAK PDF · ANY DATE RANGE · READ ON THIS PHONE, NEVER SENT`. It names no month (Kotak runs 16th → 16th) and never says "upload".
  - It sits as a `00 — QUICKEST START` panel at the top of Arm it, on the empty Pulse, and in System → Bank statements (list with Remove, plus "Add another statement").
- **Pulse** gets a `BY STATEMENT · BY MONTH` toggle once a statement exists.
  - Default: the latest fully covered month, else the statement period.
  - A part month says which days it covers ("You spent ₹X from 16 to 30 September", "That's 15 of 30 days"). It has no ▲▼, no pace meter, and per-day uses covered days.
  - Comparisons are only between fully covered periods.
- **The report**, top to bottom:
  - strip;
  - eyebrow and switcher;
  - two-line headline;
  - the total;
  - Money in · Payments · Per day;
  - an unsorted banner when ≥ 10% isn't sorted;
  - **Where it went**: group bars, biggest first, with **Not sorted yet** as its own amber bar;
  - **Top places** (5);
  - **Not spending** (one line).

  There is no ring, pace meter or ticker in this view. "Led at" ignores the unsorted bar.
- **Tapping a bar** opens a sheet with that group's payments in the period. Each payment opens the existing payment sheet to teach a category. No Ledger date filter in v0.1.3.
- **Categoriser fixes** (fixture first):
  - Complete truncated names from the directory's own words, for phrase aliases and keyword stems only: `BANGALORE METR` → Namma Metro, `Sri Lakshmi Stor` → store, but `MANOJ GROW` ≠ Groww.
  - Own-name transfers (`ASHA K`, `K ASHA` vs holder "Asha K") → self transfer at 0.6, so they go to review; the holder's name is never stored.
  - `CHRG` → Bank charges & fees; `Int.Pd` → Interest; cashback → Refunds; `NEFT IN` from a company → Money in.
- **Data:** schema v2, `statement` table, `stmt_key` unique, `time_known`.
  - A re-imported file adds 0; overlapping statements add only new rows.
  - SMS ↔ statement matching works in both directions. An upgraded row takes the SMS time and SMS id, so a re-scan doesn't duplicate it.
- **Privacy:**
  - Holder name, address and full account number are never stored (the interest narration is masked to `XX` + last 4).
  - No file name or URI is kept, and the password is never saved.
  - "Couldn't read" stores one record per import, not text.

## Work in order

Builders are Claude agents in separate git worktrees, each owning distinct files, merged in the order A → B → C.

| ID | Status | Task / done when | Evidence or blocker |
|---|---|---|---|
| 0 | DONE | Shared foundation: Kotak page-text parser + completeness checks, statement types, `StatementTxns` keys, `Reports` (coverage, report), `Source.STATEMENT`, new categories, `Pipeline.accept`, app stubs (`StatementImport`, Repo statement methods), `BuildConfig.SMS_CAPTURE`. | 46 core tests green (11 new on fictional fixtures); app builds. Real statement: 202/202 rows, 0 balance mismatches (prototype, no data copied). |
| 1 | DONE | Agent discussion: designer, architect, critic, then a critic ruling. | Summarised above; ADR-015/016. |
| 2 | TODO | **Agent A (`:core`):** categoriser and merchant fixes, own-name, `StatementMatch`, refine `Reports`, rhythm skips `timeKnown=false`. | Core tests; no existing test weakened. |
| 3 | TODO | **Agent B (data, ingest, build):** DB v2 migration, Repo import/merge/dedup/coverage/delete, `PdfText` + `StatementImport`, flavors `full`/`statement`, CI publishes both APKs, debug import hook, smoke test still passes. | Migration keeps v1 data; same file twice → +0 rows; overlap → 41 rows. |
| 4 | TODO | **Agent C (UI):** the button, picker, password sheet, progress/result copy, Pulse toggle + report + Not sorted bar + bar sheet, System → Bank statements, statement edition hides SMS steps. | Emulator screenshots checked by eye. |
| 5 | TODO | Merge, then end-to-end on the emulator with the fictional fixture PDFs, and privately with the owner's real file (totals match the bank's summary). | Journal; no personal data written down. |
| 6 | TODO | Independent Claude review; fix findings. | Review summary in the journal. |
| 7 | TODO | Release v0.1.3 (both APKs) under the standing approval once the exact-commit CI is green. | Release page and checksums checked. |
