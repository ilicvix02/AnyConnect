# AnyConnect

An Android app with an agentic AI that carries out tasks **inside any app** on your phone.
You type a task in AnyConnect; it reads the current screen and taps, types and scrolls to
get it done, one step at a time, using Google's Gemini model.

It works through Android's Accessibility API — the same mechanism screen readers and
automation apps use. That means it acts only on what is on screen (what you could tap
yourself); it does not reach other apps' internal servers or data, which Android blocks
for every app.

## Get the app

Every push builds an installable APK automatically. Go to the **Releases** page of this
repo and download `app-debug.apk` from the latest release, then open it on your phone.

## First-time setup

1. Install the APK (you may need to allow "install unknown apps" for your browser/files app).
2. Open AnyConnect → **Settings** → paste your **Gemini API key** (from
   https://aistudio.google.com/apikey) → Save.
3. Tap **Open accessibility settings**, find **AnyConnect**, and switch it on.
4. Type a task, tap **Run task**, then open the app you want it to work in.

## Build it yourself

Open the project in Android Studio and run, or from a machine with the Android SDK:

```
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Notes

- Your API key is stored only on your device.
- The free Gemini tier is rate-limited; heavy use may hit daily caps.
- This is a debug build, meant for your own phone.
