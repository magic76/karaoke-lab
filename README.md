# Karaoke Lab v0.5

## Primary goal

Play a YouTube MV and hear a vocal-reduced soundtrack with synchronized video. **This goal is not yet implemented.**

The app now has a YouTube-first, single-screen UI. The only visible action is **Test audio capture**. It logs signal levels without changing, saving, or rerouting the audio. It must not be presented as vocal removal.

## Research status

- On a Galaxy S25 Ultra, a previous controlled test found nonzero captured RMS while YouTube MV was playing, and zero RMS during silence.
- This does not prove source attribution or establish a way to mute WebView's original audio.
- Android playback capture is a copy of permitted output, not a mechanism to replace the original stream.
- Playing processed capture on top of original audio would still leave the original vocals audible and create latency/echo.
- YouTube platform restrictions on audio extraction and modification must be respected.

## Archived experiments

`LocalKaraokePlayer.kt`, `InstrumentalOnnx.kt`, and `tools/separate_local.py` remain in the repo as research code, but are not part of the user-facing flow. Demucs preprocessing on local audio does not fulfill the YouTube objective.

## Next engineering gate

Validate whether a permitted playback path exists that gives control of original audio output, audio PCM, and synchronization. Do not ship a misleading vocal-removal toggle until that path exists.

## Build

Android SDK 35, JDK 17, Gradle 8.9; `gradle :app:assembleDebug`. Build and device verification still required.
