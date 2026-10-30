# Installing Credu1ar

Credu1ar reads your payment SMS, so it asks for the SMS and notification permissions. In India, Google Play Protect blocks **any** app downloaded from a browser, chat app or file manager that asks for those permissions ("App blocked to protect your device"), because SMS-stealing malware asks for the same ones. The block looks at the permissions, not at Credu1ar, so no version of the app can make it go away. An app that hid its permissions to get past it would be exactly the kind of app the block exists for.

**Why does my other expense app install fine?** It comes from the Play Store. Google checked it there and allows budgeting apps to read SMS, so the block does not apply to Play installs.

**Why can't it work like OTP auto-fill?** OTP auto-fill works because the sender puts a secret code for that one app inside the message, or because you tap "Allow" for that single message within 5 minutes. Banks don't put our code in their debit alerts, and tapping for every alert is not tracking. So that route cannot capture spending.

Pick one of the routes below. **Route A is the one we recommend.**

## Route A — install from the Mac, over USB or Wi-Fi (recommended)

An install from a computer (`adb`) is not a download from the internet, so the block does not apply. It also installs the build signed with your private key, so later updates install over it and keep your data.

On the phone, once:
1. **Settings → About phone → tap Build number 7 times.** This unlocks Developer options.
2. **Settings → System → Developer options:**
   - **USB debugging** on, then plug into the Mac and tap **Allow** on the phone; or
   - **Wireless debugging** on (Android 11+, same Wi-Fi as the Mac). No cable needed after pairing.

On the Mac (in this repo):
```bash
scripts/phone-install.sh                    # builds the release with your key and installs it
# Wi-Fi only:
scripts/phone-install.sh pair 192.168.1.23:37123     # once: "Pair device with pairing code"; enter the 6-digit code
scripts/phone-install.sh connect 192.168.1.23:41567  # each time: the address at the top of Wireless debugging
```
Or ask the agent to do it while the phone is connected. You can turn Developer options off again afterwards; turn them on again for each update.

## Route B — phone only: pause the Play Protect scan for one install

1. Download `Credu1ar-vX.Y.Z-android.apk` from [Releases](https://github.com/Chiragjjjks/Credu1ar/releases/latest).
2. **Play Store → your profile picture → Play Protect → gear icon → turn off "Scan apps with Play Protect".**
3. Open the APK (Files → Downloads) and install. If Android asks, allow installs from your browser or Files app.
4. Go back and turn **Scan apps with Play Protect** on again.

Note: Google documents the switch but does not promise that turning it off lifts this particular block. If you still see "App blocked", use Route A. You need to repeat this for every update. Until the project's signing secrets are set, each Releases build is signed with a different temporary key, so an update needs an uninstall first.

## Route C — the Play Store (not set up)

Putting Credu1ar on a private Play Store "internal testing" track would make it install and update like any store app. Google's policy has an exception for apps that manage money from SMS. It costs a one-time $25 developer account with ID verification, plus a permissions declaration (with a video) that Google reviews, which can take weeks. This would reverse the "no Play Store" plan, so it is the owner's decision. Nothing is set up yet.

## After it is installed: let it read your SMS

1. Open Credu1ar and follow **Arm it**. Tap **Allow SMS**.
2. If "Allow" is greyed out or says *Restricted setting* (Android 13+ does this for apps installed outside a store): **Settings → Apps → Credu1ar → ⋮ (top right) → Allow restricted settings**, then try again. Your phone may ask for your PIN.
3. The moment SMS works, Credu1ar reads your bank SMS by itself:
   - **this month so far**, and
   - **all of last month too if it's the first week** (link on 1 November and you get all of October; link on 15 November and you get 1–15 November).

   **Add September** (and so on) reads one more month each tap. OTPs, chats and offers are dropped unread.
4. Turn on **Notification access** in the same screen. It catches Google Messages RCS chats and UPI-app alerts, which are not in the SMS inbox.

## Is this safe?

- Everything stays on your phone. No account, no server, no cloud backup, no analytics. The only time it uses the internet is the update check you tap yourself.
- The download is the same file GitHub built and published; you can verify it against `SHA256SUMS.txt` on the release page.
- You can read exactly what it captured, and delete any of it, in the **System** and **Ledger** screens.
