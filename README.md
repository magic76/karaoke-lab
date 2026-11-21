# Karaoke Lab — DSP V2

Android WebView karaoke experiment. The app opens YouTube in WebView and offers a compact set of **non-AI** stereo DSP controls.

## Controls
- **開啟／關閉卡拉 OK**: apply or bypass stereo center cancellation.
- **人聲消除強度**: crossfade from original stereo mix to stereo side signal (L−R)/2.
- **低音保留**: below 180 Hz, blend the original low-frequency mix back in to protect bass and kick.
- **輸出音量**: set post-mix gain (0–100%, no boost).
- **全螢幕／收起控制**: keep video visible, especially in landscape.

DSP changes are smoothed with Web Audio gain automation to reduce clicks. It is **not** machine-learning vocal separation. Centered instruments may also be reduced; off-center or reverberant vocals may remain.

## Important limitations
The DSP is injected into media elements accessible to the WebView page using `createMediaElementSource`. YouTube may change its player, use cross-origin media, restrict access or prevent processing. Successful JS setup does **not** guarantee the audio samples are accessible or that the result sounds correct. A cross-origin media element can yield silence through Web Audio. The app does not bypass DRM, extract YouTube streams, or replace the YouTube audio using MediaProjection.

The previous ONNX MDX-NET experiment was retired after a 7-second benchmark: XNNPACK 6,847 ms (1.02× throughput), below the exploratory 1.5× threshold. The app no longer includes model downloads, MediaProjection capture, AI inference, local-file processing or their associated dependencies. Historical experiment results remain in Git history.

## Build
Android SDK 35, JDK 17, Gradle 8.9. Run `gradle :app:assembleDebug` locally. Compilation and physical-device DSP audio validation must be completed before calling this release production-ready.
