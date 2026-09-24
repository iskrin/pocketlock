# Pocket Lock

A custom lock screen for Android handhelds, inspired by the Nintendo Switch lock screen.
When the display wakes up, the lock screen shows three dots — unlock it by pressing any
button (or tapping the screen) three times.

Originally built for the **Retroid Pocket Nova** (Android 13, 4:3 screen), but it should
work on any Android 8.0+ device.

## Features

- **3-press unlock** — any button, trigger or screen tap counts; progress shown as three dots.
- **Lock on wake** — appears automatically every time the display turns on.
- **Apps are paused while locked** — the lock screen is an opaque activity above the running app,
  so music or gameplay does not continue in the background.
- **No flash** — with the system lock set to *None*, the lock screen is a pre-attached overlay,
  so the first frame after wake-up is the lock screen itself.
- **Slide-down unlock animation.**
- **Click sound & vibration** (can be turned off).
- **Custom appearance** — your own background photo (stored losslessly as WebP/PNG, up to 4096 px),
  pan/zoom, plus dot size, position and colors from a palette.
- **Aspect-ratio independent** — positions are stored as fractions of the screen, so the same
  settings adapt to any resolution (16:9, 4:3, ...).
- **No ads, no analytics, no internet permission.**

## Installation

1. Download the latest `PocketLock-<version>.apk` from the [Releases](../../releases) page.
2. Copy it to the device and install it (allow installation from unknown sources), or use adb:

```
adb install -r PocketLock-1.0.apk
```

## Setup

1. Open **Pocket Lock**.
2. **Permissions** tab: grant *Display over other apps* and disable battery optimization.
3. Android Settings → Security → Screen lock → set **None** (recommended) or *Swipe*.
4. **Options** tab: turn on **Lock enabled**.
5. Full instructions are available under the **(i)** button in the app.

## Appearance

Open **Appearance…** from the Options tab:

- **Background** — choose your own photo or keep the black background; pan/zoom the image.
- **Dots** — size, spacing and position of the three dots.
- **Colors** — color palette + opacity for the active and inactive dot.

## Building from source

Requirements: JDK 17, Android SDK (platform 35, build-tools 35.0.0), Gradle 8.11.1.

```
./gradlew assembleRelease
```

For a signed release build, create `keystore.properties` in the project root:

```
storeFile=keystore/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

## Disclaimer

This is a personal hobby project. **The code, documentation and release process were created
with the help of AI** (an AI coding assistant). The app is provided "as is", without any
warranty — use it at your own risk. It is not a security-grade lock (it can be bypassed via
ADB/recovery) and it is not affiliated with Retroid or Nintendo.
