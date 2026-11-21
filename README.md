# Karaoke Lab v0.6 — controlled replacement proof

## Goal

The desired experience is YouTube MV video with original vocals replaced by instrumental playback. **This is not implemented for YouTube.**

## New proof-of-concept

The app has a controlled-media replacement experiment:

1. Tap **測試可控影片音訊替換**.
2. Choose a local video you own or are authorized to process.
3. Android VideoView mutes the source MediaPlayer via `setVolume(0f,0f)`.
4. The same local file is decoded separately by `LocalKaraokePlayer` and outputs center-cancelled stereo PCM through AudioTrack.
5. Tap **返回 YouTube** to stop local playback and restore the WebView.

This demonstrates separate control of source volume and processed output in a **controlled media path**, unlike MediaProjection, which only captures a copy.

## Caveats

- The DSP is center cancellation, not AI source separation.
- VideoView and AudioTrack are separate clocks. Their synchronization is **not guaranteed**. The current test deliberately exposes this gap.
- The player requires supported audio codec/PCM and stereo for cancellation; on unsupported media the video may continue silently.
- YouTube WebView remains unchanged; no stream extraction, modification or audio replacement is performed.
- Next milestone: single clock and frame-accurate synchronization for controlled media, then model inference benchmarks. YouTube cannot be assumed to support this architecture.

## Build

Android SDK 35, JDK 17, Gradle 8.9. Run `gradle :app:assembleDebug` locally.
