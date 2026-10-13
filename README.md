# RideSwitch

An Android app for drivers running Uber Driver and Lyft Driver at the same time.

**What it does**

- You accept a ride on Uber → RideSwitch opens Lyft, taps **Go offline**, and switches back to Uber (and the other way around).
- When the trip ends, it can bring the other app back online. There's a switch for this. It only brings an app back online if RideSwitch took it offline.
- It never taps Cancel or Decline, and it won't touch an app that has its own trip in progress.

It works through Android's Accessibility Service: it reads the text on the Uber/Lyft screens and taps buttons for you.

## Install

1. Download `RideSwitch.apk` from the **Releases** page (the `latest` release) on your phone.
2. Open it and allow "Install unknown apps" for your browser/Files app when asked.
3. Open RideSwitch → **Open Accessibility settings** → Installed apps / Downloaded apps → RideSwitch → turn it on.
   - On Android 13+ it may be greyed out ("Restricted setting"). Fix: tap **Open RideSwitch app info** → ⋮ menu (top right) → **Allow restricted settings**, then turn it on again.
4. Samsung / Xiaomi / OnePlus: set RideSwitch's battery usage to **Unrestricted** so the phone doesn't kill it.

## Test it while parked

1. Go online on both apps.
2. In RideSwitch tap **Lyft offline**. It should jump to Lyft, tap Go offline, and come back.
3. Tap **Lyft online** to bring it back. Repeat for Uber.

If a test times out, the button text didn't match:

1. Turn on **Screen dump**.
2. Open the app and look at the screen you're stuck on.
3. Come back, tap **Copy log**, and find the exact words in the log.
4. Put those words in **Edit keywords**.

## How it decides

| List | Used for |
|---|---|
| Trip in progress | Text that only shows after you accept (e.g. "picking up", "arrived", "complete trip") → triggers taking the other app offline |
| Trip finished | Text that shows when you're back waiting (e.g. "you're online", "rate your rider") → triggers bringing the other app back online |
| Go offline / Go online buttons | What it taps |
| Shows when offline / online | How it knows the tap worked |

The default words are a best guess. Uber and Lyft change their screens, so check them with Screen dump.

## Heads-up

Uber and Lyft don't allow third-party apps to automate their driver apps. Using this carries some risk to your accounts. Don't fiddle with the phone while driving; do the setup and testing parked.

## Build

Every push to `main` builds a signed APK with GitHub Actions and publishes it to the `latest` release. To build locally, open the folder in Android Studio and run **Build → Build APK(s)**.
