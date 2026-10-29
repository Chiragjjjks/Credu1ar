# Design language — "Signal Noir"

The app should feel like a precision instrument that happens to know your money, not a spreadsheet and not a bank. Reference: the Downmix Renderer page (`prospeck.github.io/Downmix-renderer-windows`): pure black, hairlines, mono labels, live meters, one cool gradient. Sasta's `site/` already adapted the same page; reuse its ideas, not its colours.

This file is the contract for the Compose app (`app/.../ui/theme`) **and** the landing site (`site/`). Change tokens here first.

## 1. Principles

1. **Black is the canvas.** `#000` background; depth comes from hairlines and a faint glow, never from drop shadows or grey cards.
2. **One number per glance.** Each screen has a single hero figure; everything else supports it.
3. **Mono = machine truth, Geist = human voice.** Labels, amounts, timestamps, IDs in Geist Mono; sentences and headlines in Geist.
4. **Colour means something.** Categories use the fixed data palette; the brand gradient is chrome only (never a category); green = money in; amber = needs attention.
5. **Motion explains.** Count-ups, sweeps and the live dot show that data is real and recent. Nothing loops while off-screen; everything respects the system "remove animations" setting.
6. **Plain language first.** Every chart has a one-line English headline above it.

## 2. Tokens

### Surfaces and lines
| Token | Value | Use |
|---|---|---|
| `black` | `#000000` | app background |
| `panel` | `#050505` | cards, sheets |
| `lift` | `#0A0A0A` | raised/pressed panel, chart surface |
| `line` | `#FFFFFF` @ 8% | default hairline (1 dp) |
| `lineHot` | `#FFFFFF` @ 18% | focused/selected hairline |

### Text
| Token | Value | Use |
|---|---|---|
| `ink` | `#EDEDED` | primary |
| `mid` | `#A3A3A3` | secondary |
| `dim` | `#858585` | tertiary, meets 4.5:1 on black |
| `faint` | `#6B6B6B` | disabled, decorative only |

### Brand and semantic
| Token | Value |
|---|---|
| `cyan` | `#46C1DE` |
| `blue` | `#3B7FE0` |
| `indigo` | `#2C3FB8` |
| `brand` | linear 135°: `#5FD0E6` → `#3B7FE0` (48%) → `#2C3FB8` |
| `moneyIn` | `#68D98F` (always with a `+` and an ↓ arrow) |
| `attention` | `#D8A548` (needs-review, over-budget) |

### Category palette (validated)
Dark-mode categorical set from the dataviz skill, validated on `#000` and `#0A0A0A`: lightness band, chroma floor, adjacent colour-blind ΔE ≥ 8.4, normal-vision ΔE ≥ 19.3, contrast ≥ 3:1. **Slot order is the safety mechanism — never reorder or cycle.** Charts draw categories in this fixed order (not sorted by size); ranked lists may sort.

| Slot | Group | Hex |
|---|---|---|
| 1 | Food & Drink | `#3987E5` |
| 2 | Groceries | `#D95926` |
| 3 | Transport | `#199E70` |
| 4 | Home & Bills | `#C98500` |
| 5 | Shopping | `#D55181` |
| 6 | Health | `#008300` |
| 7 | Fun & Subscriptions | `#9085E9` |
| 8 | People & Cash | `#E66767` |
| — | Other | `#6B6B6B` (neutral, not a slot) |

Sub-categories use the parent hue at lower alpha/lightness steps, never a new hue. Re-run `validate_palette.js` if any value changes. Text never takes a series colour: values stay `ink`/`mid`; a coloured dot or bar carries identity. Charts keep a 2 dp gap between adjacent fills, 4 dp rounded data ends, and 2 dp lines.

### Type
| Role | Font | Size / tracking |
|---|---|---|
| Display (hero amount) | Geist 600, tabular figures | 56 sp, -0.04 em |
| Headline | Geist 600 | 28 sp, -0.03 em; second line in `dim` |
| Title | Geist 500 | 18 sp |
| Body | Geist 400 | 15 sp / 1.5 |
| Amount (rows) | Geist Mono 500 | 15 sp |
| Eyebrow | Geist Mono 500, UPPERCASE | 11 sp, +0.12 em, `dim`; optional number prefix `01 —` and a LiveDot |

Fonts: Geist and Geist Mono (SIL OFL), bundled as TTF in `app/src/main/res/font/`.

### Shape, space, motion
- Radius: 14 dp panels, 10 dp chips, full pills for nav. Spacing scale 4/8/12/16/24/32/48; screen gutter 20 dp.
- Easing `out` = cubic-bezier(0.16, 1, 0.3, 1); `inOut` = cubic-bezier(0.65, 0, 0.35, 1).
- Durations: micro 120 ms, standard 280 ms, reveal 600 ms, intro 1.1 s.
- Film grain: 6% opacity noise overlay on Pulse hero only (static on low-end devices; off with reduce-motion).

## 3. Signature elements (borrowed from the reference)

| Element | In the reference | In Credu1ar |
|---|---|---|
| Loader count-up + clip reveal | 000→100 then wipe | Logo blur-in, counter, wipe to Pulse (once per cold start, skippable) |
| Mono numbered eyebrows | `01 — SIGNAL` | `01 — PULSE`, `02 — WHERE IT WENT`, `03 — LEAKS` |
| Live strip | green dot + spec chips | Live dot + "LISTENING · SMS ✓ · NOTIF ✓ · last signal 2 m ago" |
| Spec ticker band | looping marquee | Latest captures scroll as `ZEPTO −₹312 · SWIGGY −₹489 · SALARY +₹60,000` (only while visible) |
| Level meters | per-speaker meters | Budget/forecast meter and per-category bars that "fill" on entry |
| Signal chain list | numbered 1–8 | Onboarding "Arm it" steps and the ingest trace in Diagnostics |
| Stat row | big numerals + caption | Pulse stats: Spent · In · Daily burn · Forecast |

## 4. Screens

Bottom navigation is a floating hairline pill with four mono labels: `PULSE · LEDGER · INSIGHTS · SYSTEM` (no icon font needed).

1. **Intro** — see above.
2. **Arm it (onboarding)** — four numbered steps, each with a live status dot that turns green the moment Android grants it: `01 Read payment SMS` · `02 Unlock restricted settings` (shows the exact taps) · `03 Notification access` (RCS + UPI apps) · `04 Keep alive` (battery-saver shortcut). A *Try with sample data* button skips all of it.
3. **Pulse** — live strip; one-sentence headline ("You've spent ₹23,480 — 12% more than this time last month."); hero amount counting up; forecast meter; stat row; category ring with fixed-order segments and a centred total; ticker band; *Needs review (3)* chip.
4. **Ledger** — sticky day headers with day totals; row = monogram avatar tinted by category, merchant, category chip, mono amount (`moneyIn` for credits); search, filter chips (category, in/out, account); tap → detail sheet with raw message, parsed fields, category picker, "always do this for this merchant".
5. **Insights** — headline sentence; *Where it went* (ring + ranked bars, grocery split into quick commerce / provision / supermarket); *Leaks* (small frequent spends); *Movers* (±% vs last month); *Recurring* (detected subscriptions with next date); *Rhythm* (7×24 heatmap, single-hue sequential ramp from the brand blue).
6. **Review** — card stack of uncategorised/low-confidence items with one-tap category chips and "remember for this merchant".
7. **System** — permission status with LiveDots, "last heard from Android", counts (received / parsed / ignored / needs review), privacy mode (blur amounts, hide from recents), sample-data toggle, CSV export, redacted diagnostics report, update check.

## 5. Accessibility and performance rules
- Touch targets ≥ 48 dp; contrast ≥ 4.5:1 for text, ≥ 3:1 for marks; every chart has a table view (the Ledger filtered to it).
- Colour is never the only carrier: bars have direct labels, credits have `+` and an arrow.
- Charts are custom `Canvas` drawings (no chart library): smaller APK and full control. Animations run only while the screen is resumed.
- Large font scale (up to 200%) must not truncate amounts; amounts never wrap.

## 6. Implementation status (v0.1.0)

| Element | Status |
|---|---|
| Tokens (surfaces, text, brand, validated category palette), Geist + Geist Mono | **built** (`ui/Tokens.kt`) |
| Intro: ring draws itself + 000→100 counter + wipe | **built** (`ui/Intro.kt`), once per cold start, tap to skip |
| Mono numbered eyebrows, live dot, live strip, floating nav pill | **built** |
| Hero count-up, tick-meter pace gauge with forecast marker, fixed-order category ring, ranked bars | **built** (`ui/Charts.kt`) |
| Spec ticker band (recent captures) | **built**, scrolls only while composed |
| Ledger (search, filters, sticky day headers), Insights (day-by-day, leaks, movers, recurring, rhythm heatmap), grouped Review, System | **built** |
| Film grain overlay on the Pulse hero | **not built** (v0.2) |
| Haptics, "remove animations" handling | **not built** (v0.2): animations are short and only run while visible |
| Large-font (200%) and TalkBack pass | **not tested** |
| Light theme | not planned: the identity is black |

## 7. Landing site (`site/`)
Static HTML/CSS/JS, no build step, same tokens, hero "Every payment. Filed.", live demo that animates a fake SMS into a categorised row. Not deployed until the owner enables GitHub Pages.
