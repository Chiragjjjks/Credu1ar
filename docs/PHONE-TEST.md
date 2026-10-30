# Real-phone test (10 minutes, owner only)

Agents cannot drive a physical phone (the T3 device panel was off and no handset is attached). Everything below is therefore **unverified** until you run it. Paste the result of step 8 back to the agent.

You need: your Android phone, the APK (`scripts/phone-install.sh` from the Mac is the easiest, see `docs/INSTALL.md`; or `Credu1ar-vX.Y.Z-android.apk` from Releases), and at least one real bank SMS or UPI payment you can make (₹1 to yourself counts).

| # | Do | Expect | If not |
|---|---|---|---|
| 1 | Install and open. | Intro ring, then **Arm it**. | Note the phone model + Android version. |
| 2 | Step 01 **Allow SMS**. | System dialog, or it is greyed out / says *Restricted setting*. | This is the Android 13+ sideload block: go to step 3. |
| 3 | Step 02 **Open App info** → ⋮ (top right) → **Allow restricted settings** → back → step 01 again. | SMS granted, dot turns green. | Screenshot the App info screen. Some phones hide ⋮ until you try step 01 once. |
| 4 | Step 03 **Notification access** → enable Credu1ar. | Dot turns green. If blocked, repeat step 3 first. | Note any Android message. |
| 5 | Step 05 **Keep it alive** → Battery → **Unrestricted**. | Dot green. | Xiaomi/Oppo/Vivo/Samsung: also enable Autostart and lock the app in Recents. |
| 6 | Right after step 2/3 succeeds, watch the bottom of **Arm it**. | `READING SMS · 1 NOV → TODAY · n / total`, then `… · N PAYMENTS FILED` (from the 1st of last month if today is day 1–7). Tap **Add <month>** once: one more month appears. Pulse opens on last month if you linked on day 1–3. | Note how long the read took and roughly how many SMS your phone has. System → *Couldn't read* lists formats we miss. |
| 7 | Make a small UPI payment (or ask a friend to send you ₹1). Wait for the bank SMS. | Within seconds a quiet note "−₹1 · …" and a new row in Ledger. System → Traffic shows SMS seen +1, Payments +1. | See step 8. |
| 8 | System → **Diagnostics report → Copy**, paste it to the agent. | Digits are masked; read it first and remove names if you prefer. | |

Also check, once: (a) Google Messages **RCS** bank chat (if you receive any) shows up as an *alert* in Traffic; (b) restart the phone, wait 5 minutes, send yourself ₹1: it should still be captured; (c) turn on **Hide amounts** and confirm amounts show as ••••• and the notification text still opens the right payment.

## What to report back

- Phone make/model, Android version, how you installed (browser / file manager / adb).
- Which steps failed and the exact on-screen wording.
- The Diagnostics report.
- Any real bank messages (digits masked) that appeared under *Couldn't read*: those become new fixtures.
