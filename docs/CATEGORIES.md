# Categories

Two levels so the top is simple and the bottom is precise. The group colour comes from the validated palette in `docs/DESIGN.md` (slot order is fixed).

| Group (slot) | Categories |
|---|---|
| Food & Drink (1) | Food delivery · Dining out · Cafés & snacks |
| Groceries (2) | **Quick commerce** (Zepto, Blinkit, Instamart, BigBasket, JioMart) · **Provision store** (kirana, "stores", general stores) · Supermarket (DMart, More, Reliance Smart…) · Fresh & dairy (milk, vegetables, meat, Nandini, Licious…) |
| Transport (3) | Cabs & autos · Fuel · Metro & bus · Trains, flights & stays · Parking & tolls |
| Home & Bills (4) | Rent · Electricity, water & gas · Mobile & internet · EMI & loans · Insurance |
| Shopping (5) | Online shopping · Fashion · Electronics · Salon & personal care |
| Health (6) | Pharmacy · Doctors & hospitals · Fitness |
| Fun & Subscriptions (7) | Streaming · Games · Movies & events · Apps & subscriptions |
| People & Cash (8) | Sent to people · ATM cash |
| Other | Education · Uncategorised |
| Money moves (not spending) | Credit-card bill · Own-account transfer · Investments |
| Money in | Salary · Refunds & cashback · Money in |

## Why the grocery split exists

"Where did my grocery money go?" is the question that started the project. Quick-commerce habits (many small orders, delivery fees) and the corner provision store are different behaviours, so they are different categories under one group. The Pulse screen shows the split as a stacked bar; the headline says it in a sentence ("Groceries led at ₹13,683: 65% quick commerce, 10% provision store.").

## Not spending

Paying a credit-card bill duplicates every card swipe, moving money between your own accounts is not spending, and an SIP is saving. These are tagged *Money moves*, excluded from every spend total, and shown in the Ledger so nothing looks missing.

## Order of trust (see `Categorizer.kt`)

1. A rule you taught (merchant key → category, applies to past and future payments).
2. Message hints (`card_bill`, ATM, `self`, `invest`; salary/refund for credits).
3. The brand directory (`MerchantDirectory`, ~150 brands, confidence 0.95).
4. Words in the name: `kirana`, `provision`, `pharma`, `hospital`, `restaurant`… (0.6–0.8). "Stores" alone is 0.6, so it goes to review.
5. Looks like a person (1–3 plain words, initials allowed) → Sent to people (0.5).
6. Unknown (0.25).

A debit below 0.65 confidence goes to **Needs a look**. Credits are never queued.

## Adding a merchant

Add `b("Name", CATEGORY, "alias one", "alias two")` to `MerchantDirectory.brands`. Aliases are lowercase; a single word matches an equal word, or (≥ 5 letters) a word that starts with it ("zepto" matches "zeptonow"); a phrase matches whole, and also when a handle runs it together ("apollopharmacy"). Add a fixture in `ParserFixtures.kt` that proves it.
