# Chromium audio interception feasibility — 2026-11-21

## Decision

**GO for source-level Chromium audio-output instrumentation as research. NO-GO for replacing the Android system WebView inside the existing unrooted APK.** No proof yet that a modified Chromium browser will successfully play a YouTube MV, nor that a separator can meet latency requirements.

Observed on S25 Ultra in v0.7: YouTube playback audible before muting; after muting, user reports silence and PCM RMS 0.0000. This disproves the simple mute-then-MediaProjection-capture route on the tested setup, but not every possible route.

## Relevant upstream components

- `media/audio/README.md`: Renderer uses `AudioOutputDevice` (implements `AudioRendererSink`). Audio IPC goes through Mojo/shared memory, then `AudioOutputStream`. Platform output uses pull callback `AudioSourceCallback::OnMoreData()`.
- `media/audio/android/audio_manager_android.cc`: `AudioManagerAndroid::MakeLinearOutputStream()` and `MakeLowLatencyOutputStream()` create Android AAudio or OpenSL ES streams, depending on flags/configuration.
- `media/audio/audio_io.h`: output stream/callback interfaces.

Upstream references:
- https://chromium.googlesource.com/chromium/src/+/main/media/audio/README.md
- https://chromium.googlesource.com/chromium/src/+/main/media/audio/android/audio_manager_android.cc
- https://chromium.googlesource.com/chromium/src/+/main/media/audio/audio_io.h
- https://chromium.googlesource.com/chromium/src/+/main/android_webview/docs/aosp-system-integration.md

## Proposed experimental insertion point

Use a dedicated fork of Chromium browser, **not** a Kotlin WebView hook. Instrument the audio output callback or the upstream AudioRendererSink path, before PCM is delivered to the OS output. First implement a pass-through callback and optional non-destructive test gain (with controlled test audio); only after that implement a lock-free, bounded, preallocated ring buffer to a worker performing separation. Return an instrumental buffer to the same output path. Do not invoke model inference or blocking IPC directly in a real-time callback.

Simplified target:
renderer PCM -> Chromium audio sink / IPC -> callback -> bounded preallocated PCM ring -> inference worker -> aligned instrumental PCM -> output stream -> Android device.

The stream clock, underrun behavior, buffer size, and seek/pause transitions must be explicitly tested. For a model with look-ahead, the playback pipeline must accommodate algorithmic latency; a second independently timed AudioTrack would create drift.

## Experiments and gates

### Gate 1: controllable output
- Build an independent Chromium Android browser with an instrumented audio sink; do not attempt to replace the system WebView.
- Play a locally hosted, authorized HTML5 test video; verify a native pass-through path and a reversible test gain change without modifying video frames.
- Capture logs of audio stream initialization, sample rate, channels, callback frames, underruns, and timestamps.
- Gate passes only if original output is actually changed without silencing the PCM input to the processing hook.

### Gate 2: separation
- Feed known licensed stereo test material; measure actual on-device inference throughput, look-ahead, and memory use.
- First use a deterministic center-cancel baseline for routing; then compare an actual pretrained model, with documented model license.
- Gate passes only if instrumental audio is audible and original audio is absent, with no recurring underruns.

### Gate 3: A/V sync
- Measure lip-sync offset at start, after pause/resume, seek, and after 10 minutes.
- Define acceptance criteria before implementing (suggested research targets: median offset <= 80 ms; no continuous drift; <= 1% audible underrun windows). These are **targets**, not achieved results.

### Gate 4: YouTube compatibility (unverified)
- Verify the independent Chromium browser can actually play the desired YouTube page and that the chosen audio path carries its decoded PCM.
- Do not assume WebView, HTMLMediaElement, MSE, EME/DRM, proprietary codec, or iframe behavior will match controlled HTML5 tests.
- Respect source/content permissions; do not bypass DRM or access restrictions.

## Android deployment feasibility

- Standard Android apps cannot swap out the installed WebView provider. Chromium's AOSP integrator documentation says unmodified retail devices generally cannot replace WebView; custom ROM/system integration is typically required.
- A separately installed Chromium-derived browser is a different experiment and does not replace the provider used by the existing Karaoke Lab WebView.
- Full Chromium source checkout/build requires a substantially larger build environment than the small Kotlin APK. This document does **not** claim the user's Intel MacBook Pro 16GB can complete the build efficiently; evaluate build host and disk first.

## Cost/priority

1. Do **not** implement additional MediaProjection replay loops: source mute eliminated captured PCM in the observed test.
2. Do **not** add an AI karaoke button until a controllable output hook is demonstrated.
3. Do **not** modify the Android WebView provider on the user's daily-use phone.
4. Begin with an isolated Chromium source branch and native pass-through/gain instrumentation on controlled media.
5. If Chromium build is too expensive, switch to a controlled media pipeline for inference research; this is a fallback and **does not** meet the YouTube goal.

## Status

Source architecture researched; no Chromium fork, patch, binary, APK, YouTube stream replacement, or device validation has been completed. Existing Karaoke Lab v0.7 remains a diagnostic APK.
