# Statement fixtures (fictional)

Every name, account number, reference and amount here is made up. Real statements never go in this repo.

Each `.txt` file is what Android's `PdfRenderer` text API (`Page.getTextContents()`, API 35) returns for a statement PDF: one text block per page, with pages separated by a line holding a single form feed (`\f`).

| File | Layout copied from | What it covers |
|---|---|---|
| `kotak-asha-2026-09-16_2026-10-16.txt` | Kotak Mahindra Bank savings statement (OpenPDF), 2026-11 | 35 rows across 16 Sep–16 Oct, 4 pages. Has an opening balance row and repeated page headers and footers. Narrations wrap onto 2–3 lines, including `UPI(Value Date: …)`. Row types: NEFT IN, cashback (reference wrapped), SMS-alert charge, interest, a metro refund, a numeric-only UPI note, two identical ₹40 payments on one day, and a transfer to an account in the holder's own name. |
| `kotak-asha-2026-10-16_2026-11-16.txt` | same | The next statement. Its first two rows (16 Oct) repeat the last two rows of the first file, with the same balances and references, so overlap handling can be tested. |

Every row's balance follows from the one before it. Opening balance + credits − debits = the closing balance in the Account Summary. Real Kotak PDFs have the same property; it was checked on the owner's 202-row statement (2026-11-02) without copying any of it.
