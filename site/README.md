# Credu1ar landing page

Plain HTML, CSS and vanilla JavaScript. No build step, npm dependencies, CDNs,
analytics, remote images or runtime network calls. All fonts are bundled locally.
Outbound links open GitHub only when followed.

From the repository root, preview using either:

```sh
python3 -m http.server 8000 -d site
# Open http://localhost:8000
```

or `npx serve site` (downloads a preview server if it is not installed; it is not
a dependency of this page).

**GitHub Pages is NOT enabled. Enabling it or deploying this page requires the
owner's explicit approval.** The APK link points to GitHub's latest-release page,
not an assumed published APK. It will become useful when a release is published.

## Illustrations and motion

All transactions, dates and totals are fictional. The six deterministic samples
illustrate quick commerce, food delivery, a provision store, a supermarket, salary
and a card-bill settlement. This is a presentation of the app's pipeline, not a
JavaScript implementation of its bank parser. Each sample uses the same baseline:
₹1,200 food, ₹1,100 groceries and ₹700 transport. Income and settlements do not
change spend. Monetary arithmetic uses integer paise.

Rings use the fixed category order and colours from `docs/DESIGN.md`; the static
ranked table may sort by value. Chart descriptions and labelled amounts provide
the same information without relying on colour. Geist and Geist Mono licensing
is in `fonts/OFL.txt` and `fonts/OFL-NOTE.txt`.

The intro can be skipped with its button or Escape. Reduced motion skips it
entirely, shows the complete message immediately, disables reveals, and stops the
ticker and live dot. Animation work pauses in a hidden tab; the demo, ticker and
live dot also pause outside the viewport. Without JavaScript, content and the
first SMS remain visible, and native FAQ accordions still work.

## Verification checklist

Serve locally and inspect at 375, 768 and 1280 pixels. Test all six samples,
confirm salary and settlement keep baseline spending at ₹3,000, use keyboard
navigation and FAQ controls, emulate reduced motion, and check console errors,
failed requests, horizontal overflow and external network requests. Capture
screenshots outside the repository. Non-font payload should remain under 250 KB.

## Last verified — 2026-10-29

Served with Python's HTTP server and checked using Playwright with installed
headless Google Chrome at 375 × 900, 768 × 900 and 1280 × 900. Hero, full-page
and demo screenshots were captured outside the checkout and visually inspected.
No horizontal overflow was found. All six sample amounts and totals passed;
salary and card settlement both left spending at ₹3,000. Checks passed for
keyboard FAQ activation, intro skipping/focus return, reduced motion, off-screen
demo pause/resume, ticker pause and the no-JavaScript fallback. No console errors,
failed requests or external runtime requests were recorded. Computed text colours
on their solid backgrounds all met 4.5:1. Runtime HTML/CSS/JS/favicon payload:
45,781 bytes excluding fonts. Physical handset browsers and Android installation
were not tested by this landing-page task.
