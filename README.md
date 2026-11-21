# Karaoke Lab v0.4

Personal Android research APK, built for local testing on Galaxy S25 Ultra.

## Three independent experiments

1. **Local karaoke DSP:** choose a local audio file, play original audio or switch to center-cancellation mode while playing. This is basic phase-based DSP, **not AI**. Stereo center cancellation may reduce centered lead vocals, but also removes centered instruments and may leave vocals. Mono files have no cancellation.
2. **YouTube WebView audio capture diagnostics:** open YouTube in WebView and request MediaProjection consent. The service logs RMS and peak to Logcat under `KaraokeCapture`. It does not store, modify, or reroute YouTube audio.

3. **Experimental AI ONNX:** choose a compatible instrumental-output ONNX model and play local stereo 48 kHz audio. See [model contract](docs/ONNX_MODEL.md). **No pretrained model is included.**

4. **Real pretrained AI separation (offline):** run [Demucs separation](docs/DEMUCS.md) on your own local audio, transfer the instrumental WAV to the phone, then play it in **Demucs instrumental** mode. This uses a real pretrained model, but separation runs on a computer, not on Android.\n\n## Local build

Requires Android SDK 35, JDK 17, Gradle 8.9 (Gradle wrapper not included). Import with Android Studio or run `gradle :app:assembleDebug`.

## Test checklist

- [ ] Build debug APK locally
- [ ] Pick local stereo MP3/M4A/WAV and play original
- [ ] Switch to Center cancel mid-playback and listen for change
- [ ] Test mono source (should remain original)
- [ ] Stop/restart playback repeatedly
- [ ] Test WebView video playback
- [ ] Grant capture consent and inspect Logcat RMS in silence vs playback
- [ ] Stop capture and verify foreground notification is removed
- [ ] Test Bluetooth output and note audio artifacts

## Known limits

- No bundled pretrained AI model, pitch shifting, lyric synchronization, microphone mixing or YouTube audio modification yet.
- Local playback is decoded via MediaCodec and supports 16-bit mono/stereo PCM output. Some formats/decoders may output float PCM and report unsupported encoding.
- No DRM/capture-policy bypass. A nonzero capture signal is not proof of which app produced it.
- The app is a research prototype, not a finished karaoke player.
