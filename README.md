# EmOS Android wrapper

A WebView app that loads EmOS from the APK's own assets.

## Option A: Android Studio
1. `./prepare.sh /path/to/EmOS-with-Emulator3.html` (on Windows, split the file into 90 MB pieces named
   `emos_000.bin`, `emos_001.bin`, `emos_002.bin` and put them in `app/src/main/assets/`).
2. Open this folder in Android Studio, let it sync, then Build > Build APK(s).
3. APK: `app/build/outputs/apk/debug/app-debug.apk`. Copy to the phone and install (allow unknown sources).

## Option B: GitHub Actions (no Android Studio)
1. Run `./prepare.sh ...`, commit everything to a new GitHub repo (each chunk is under the 100 MB limit).
2. Actions tab > "Build APK" > Run workflow. Download the `EmOS-apk` artifact.
