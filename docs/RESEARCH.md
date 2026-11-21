# Android WebView capture research

This is an **instrumentation-only** APK, not a karaoke engine yet.

## Build
Requires JDK 17, Android SDK 35, Gradle 8.9 (Android Studio can import project).
Run `gradle :app:assembleDebug`. A Gradle wrapper is not yet committed.

## Experiment
1. Install on Android 10+; open the embedded YouTube page.
2. Tap Start capture test and grant microphone and screen/audio capture permission.
3. In Android Studio Logcat filter `KaraokeCapture`.
4. Compare silence vs playback, recording RMS/peak. Nonzero samples may come from other eligible apps; use controlled tests.
5. Stop and verify notification disappears.

## Constraints
Capture is allowed only when the source permits it. This project does not bypass DRM or capture policies, download YouTube content, alter YouTube streams, save audio, or remove vocals. A successful capture alone does not solve original-audio muting, video sync, or rights clearance.

## Next
Add local licensed MP4 playback, model benchmarks (ONNX), vocal separation, then low-latency mic monitoring.