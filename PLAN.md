# Credu1ar — know exactly where your money went

> One sentence: Credu1ar quietly reads the payment SMS and notifications your phone already receives, files each one under the right category, and shows you — in plain words and a striking dark UI — where your money is going. Everything stays on your phone.

Agents: start with `AGENTS.md` (current state, rules, code map). This file is the **why and the roadmap**. The day-by-day log lives in `plans/`.

**Next-version task statuses and delivery scope:** [`plans/v0.2.0/plan.md`](plans/v0.2.0/plan.md). Use that checklist for current priorities and deferred items; the version table below is the original broad roadmap.

**Status (2026-10-29):** v0.1.0 is built and verified on an emulator (1.32 MB signed APK, 26 JVM tests, end-to-end SMS smoke test). It is not released and has not been tried on a real phone: see `docs/PHONE-TEST.md`.

---

## 1. The problem, in plain words

Every time you pay, your bank texts you: *"Rs.450 debited … to ZEPTO"*. Over a month that is 150+ texts. Nobody reads them back, so at month end the question "where did my money go?" has no answer.

Typical month for someone in a big Indian city:

| What you *feel* | What the texts would say |
|---|---|
| "I barely eat out." | 26 Swiggy/Zomato orders = ₹9,400 |
| "Groceries are fine." | 31 quick-commerce orders (Zepto/Blinkit/Instamart) averaging ₹310 = ₹9,600, plus ₹3,200 at the corner provision store |
| "I don't have subscriptions." | 6 auto-debits you forgot, ₹2,100/month |

Credu1ar's job is to turn that pile of texts into an answer you can read in five seconds.

## 2. What the app does (the whole product)

1. **Listens** for payment messages (SMS, and notifications from Google Messages / UPI apps).
2. **Understands** each one: amount, in or out, who was paid, which account or card, when.
3. **Files** it into a category — and gets smarter when you correct it once.
4. **Shows** it: a live *Pulse* screen (this month at a glance), a *Ledger* (every transaction, searchable), and *Insights* (what changed, where you leak money, what is recurring).

No account. No server. No ads. It works in airplane mode.

## 3. Your question: "should the app always be live?"

**No — and that is what makes it light.** Keeping an app running 24/7 (a "foreground service") costs battery, RAM and a permanent notification. Android already offers something better:

- When an SMS arrives, Android **wakes our app for ~100 ms**, hands it the message, and lets it sleep again. This is the same mechanism OTP auto-fill apps use. Nothing runs in between. Idle cost is zero.
- A **notification listener** is owned by Android itself, not by us; we only get called when a notification is posted.
- **Catch-up on wake:** every time the app wakes or opens it re-checks the SMS inbox for anything newer than the last message it saw. If a phone maker's battery saver killed us, nothing is lost — it is picked up on the next wake.

So it *behaves* as always-on, but runs as little as possible. (Puppeteer, which you mentioned, drives web browsers and cannot see phone SMS; the native Android receiver is the right tool.)

**Targets** (to be measured and recorded in each release note, not promises yet): APK under 8 MB; no resident process when idle; under 150 ms of CPU per message; a year of transactions under 5 MB on disk.

## 4. The hard parts nobody tells you (read before building)

| # | Reality | What we do about it |
|---|---|---|
| 1 | **Sideloaded apps are restricted on Android 13+.** SMS and notification access are blocked until you open App info → ⋮ → *Allow restricted settings*. | A guided "Unlock" onboarding with the exact taps. Verify on your phone (we cannot test a real handset from here). |
| 2 | **Google Messages RCS chats are not SMS.** Android never broadcasts them as SMS. | Two capture paths: SMS receiver (most bank alerts are plain SMS) + notification listener (RCS and UPI-app alerts). Both feed one de-duplicating pipeline. Notification text can be truncated, so SMS wins when both exist. |
| 3 | **Bank SMS formats differ and change.** | Rule-based parsers per bank plus a generic fallback, all unit-tested against a fixture corpus. Unparsed-but-financial messages go to a "Needs review" queue instead of being dropped. |
| 4 | **Double counting.** A credit-card bill payment duplicates every card swipe; moving money between your own accounts is not spending. | Settlements and self-transfers are tagged *not spending* and excluded from totals. |
| 5 | **Paying a person ≠ knowing what for.** A UPI payment to "Raju" could be the kirana store or a friend. | Heuristics (business words, shop-style names) + you tag once → remembered forever. The *Needs review* queue makes this a 2-second swipe. |
| 6 | **Phone makers kill background apps** (Xiaomi, Oppo, Vivo, Samsung). | Catch-up on wake, a battery-saver deep-link in onboarding, and a *Diagnostics* screen showing exactly when we last heard from Android. |
| 7 | **Licensing.** The closest open-source app (PennyWise AI) uses a copyleft licence. | We take ideas only; every parser is written from scratch. |

## 5. Making sense of spending (categories)

Two levels, so the picture is simple at the top and precise underneath. Full list: `docs/CATEGORIES.md`.

| Group (colour) | Examples of what is inside |
|---|---|
| Food & Drink | **Food delivery** (Swiggy, Zomato), dining out, cafés |
| Groceries | **Quick commerce** (Zepto, Blinkit, Instamart), **provision store / kirana**, supermarket, fresh & dairy |
| Transport | Cabs & autos, fuel, metro & bus, trains & flights |
| Home & Bills | Rent, electricity, mobile & internet, EMI, insurance |
| Shopping | Online, fashion, electronics, gifts |
| Health | Pharmacy, doctor, fitness |
| Fun & Subscriptions | Streaming, games, events, recurring subscriptions |
| People & Cash | Sent to people, ATM cash |
| Other | Anything we are not sure about |

That is how it can say: *"Of your ₹9,600 on groceries, 61% was quick commerce, 24% the provision store, 15% supermarkets."*

## 6. The insights that make it worth opening

- **Pulse:** spent this month, vs last month, daily burn, month-end forecast.
- **Where it went:** one ring + ranked bars, every row tappable down to the transactions.
- **Leaks:** many small purchases that add up ("31 quick-commerce orders = ₹9,600").
- **Movers:** what rose or fell most since last month.
- **Recurring:** subscriptions and auto-debits detected from the pattern, with the next expected date.
- **Rhythm:** a heatmap of *when* you spend (late-night food orders, weekend spikes).
- **Plain-English headline** at the top of every month, generated on the phone.

## 7. Look and feel (summary)

Modelled on the Downmix Renderer page you pointed to: **pure black, hairline borders, Geist + Geist Mono, a cyan→indigo gradient, numbered mono "eyebrow" labels, a live dot, film grain, a count-up intro**, and instrument-style meters. Data colours are a validated, colour-blind-safe set. Full spec: `docs/DESIGN.md`.

## 8. Architecture (summary — detail in `docs/ARCHITECTURE.md`)

```
 SMS arrives ──► SmsReceiver ─┐
                              ├─► Ingest pipeline ──► SQLite (on phone)
 Notification ─► Listener ────┘     filter → parse → de-dup → categorise      │
 App opens ───► Catch-up scan ─────────────┘                                  ▼
                                                     Insights engine ──► Compose UI
```

- **Native Android (Kotlin + Jetpack Compose)**, not React Native/Flutter: the smallest APK, instant wake-ups, direct access to SMS and notification APIs, and fully custom-drawn charts.
- **`:core` module (pure Kotlin, no Android):** parsers, merchant directory, categoriser, insights. Runs in fast JVM unit tests with no emulator.
- **`:app` module:** receivers, database, screens.
- **Open APIs, only for small things, never with your data:** GitHub Releases (update check), optional exchange rates. All network calls go through one allow-listed client; the app is fully usable offline.

## 9. Roadmap and release cycle

Follows Sasta's cycle: **push to `main` → CI runs tests, builds a signed APK, boots an emulator and fires fake bank SMS at it → tag `vX.Y.Z` publishes that exact tested APK with checksums.** Release notes live in `docs/releases/`. Releases need the owner's approval.

| Version | Theme | Contents |
|---|---|---|
| **v0.1.0** | *It works* | Parser + categoriser (tested), SMS + notification capture, catch-up scan, SQLite, Pulse / Ledger / Insights / Needs-review / Diagnostics screens, onboarding, sample-data mode, CSV export, CI |
| v0.2.0 | *It learns* | Rules editor, merchant detail pages, budgets per category, recurring-payment calendar |
| v0.3.0 | *It's everywhere* | Home-screen widget, monthly "wrapped" share card, landing site |
| v0.4.0 | *It's yours* | App lock, encrypted backup/restore, multi-account balances |
| later | | On-device language model for ambiguous merchants; more bank templates; Hindi/Kannada SMS |

## 10. Decisions for the owner

1. **Name / package.** Using `Credu1ar` and `io.github.chiragjjjks.credu1ar`. Changing it later is a one-file edit plus a rename.
2. **Signing key.** Until you add keystore secrets, CI signs with a public debug key (same trade-off as Sasta): updates install over each other, but it does not prove who built the APK.
3. **Real-phone test.** Only you can do it. `docs/PHONE-TEST.md` is a 10-minute checklist; the in-app Diagnostics screen produces a redacted report you can paste back.
4. **Releases and GitHub Pages** need your explicit go-ahead; nothing is published automatically.
