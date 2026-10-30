# Installing Credu1ar

Credu1ar reads your payment SMS, so it asks for the SMS and notification permissions. In India, Google Play Protect automatically warns about **any** downloaded (sideloaded) app that asks for those permissions — because that is also what SMS-stealing malware asks for. The warning is about the *permission*, not about Credu1ar specifically: there is no way to word the app so the warning disappears, and you would not want one (that is the warning doing its job).

This is your own app on your own phone, so you are allowed to say yes. Pick whichever route fits what your phone shows you.

## Route A — download and allow it (what most phones show)

1. Download `Credu1ar-vX.Y.Z-android.apk` from [Releases](https://github.com/Chiragjjjks/Credu1ar/releases/latest).
2. Open it (Files app → Downloads → tap the APK, or tap it in your browser's downloads).
3. If Android says the source is not allowed, tap **Settings** on that prompt and turn on **Allow from this source** for your browser or Files app, then go back.
4. Play Protect pops up a warning. Tap **More details**, then **Install anyway**.
5. Open Credu1ar and follow **Arm it** (it walks you through the permissions, including step 2 below).

## Route B — no "Install anyway" button (India's stricter block for SMS apps)

For apps that ask for SMS, some phones show a harder block ("…requests access to sensitive data… may be used for fraud") with only an **OK**. When there is no *Install anyway*, turn the scan off for a moment, install, then turn it back on:

1. Open the **Play Store** → tap your **profile picture** (top right) → **Play Protect**.
2. Tap the **gear / settings** icon → turn off **Scan apps with Play Protect**.
3. Open the APK again and install it (Route A steps 2–3).
4. Go back to Play Protect settings and turn **Scan apps with Play Protect** on again.

Leaving the scan on for every other app is good; you only turned it off for this one install.

## Route C — install over USB from a computer (skips the block entirely)

Play Protect's India block only applies to apps installed from the internet (browser, chat, file manager). Installing over USB does not trip it.

1. On the phone: **Settings → About phone → tap Build number 7 times** to unlock Developer options, then **Settings → System → Developer options → USB debugging: on**.
2. Plug the phone into the computer and accept the "Allow USB debugging?" prompt.
3. On the computer (with the Android platform-tools installed): `adb install Credu1ar-vX.Y.Z-android.apk`.

## After it is installed: let it read your SMS

Android 13 and newer also hides the SMS and notification switches for sideloaded apps until you unlock them once:

1. **Settings → Apps → Credu1ar → ⋮ (top-right menu) → Allow restricted settings.** Your phone may ask for your PIN.
2. Now grant **SMS** and turn on **Notification access** — the in-app **Arm it** screen links you straight to both and shows a green dot when each works.
3. Optional: **Import last 90 days** in the app to fill it from bank messages already in your inbox.

## Is this safe?

- Everything stays on your phone. No account, no server, no cloud backup, no analytics. The only time it uses the internet is the update check you tap yourself.
- The download is the same file GitHub built and published; you can verify it against `SHA256SUMS.txt` on the release page.
- You can read exactly what it captured, and delete any of it, in the **System** and **Ledger** screens.
