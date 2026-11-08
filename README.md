# Wear OS Social Media Hub (`socialnetwork_wearOS`)

A dedicated Wear OS native application bringing **Instagram**, **Facebook**, **Reddit**, and **LinkedIn** directly to your smartwatch (optimized for Samsung Galaxy Watch 4/5/6/7/Ultra, Google Pixel Watch 1/2/3, and circular Wear OS displays).

---

## 🎯 What We Built & Core Features

- **⚡ 1-Tap Access to Major Social Networks**:
  - 🟣 **Instagram** (`instagram.com`)
  - 🔵 **Facebook** (`m.facebook.com`)
  - 🔴 **Reddit** (`reddit.com`)
  - 🔷 **LinkedIn** (`linkedin.com/feed`)
- **🔍 True Desktop-Grade Viewport Zoom**: Runs at 1:1 pixel density with built-in viewport extension. Rotating the watch crown smoothly zooms between 50% and 200% with true responsive text re-flow and active touch scrolling (no CSS `transform: scale` artifacts).
- **📐 Square & Fullscreen Modes**:
  - **Square Mode (Default)**: Keeps website contents within a centered square area (`SQUARE_FACTOR = 0.74`), ensuring corner icons (like Instagram profile, story avatars, and navigation bars) are 100% visible and unclipped.
  - **Fullscreen Mode**: One-tap toggle to expand across the full circular display.
- **🔒 Full Session & Login Persistence**: Preserves active cookies, sessions, biometrics, and credentials so you never have to re-login on every launch.
- **⌚ Rotary Crown & Physical Back Button**:
  - Crown turns: clockwise zooms in, counter-clockwise zooms out.
  - Watch back button: goes back one page within the social network; on the root page, returns to the app list.

---

## 🚀 How to Build & Install

### 1. Build via Gradle
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Install via ADB Wi-Fi
```bash
adb connect <WATCH_IP>:<PORT>
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📜 Architecture & Technologies

- Native Wear OS application powered by Android SDK, Mozilla GeckoView web engine, and WearableRecyclerView.
