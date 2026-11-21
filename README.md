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

## AI vocal separation experiment (offline, local files)

This is a **real Demucs inference test**, separate from the Android WebView playback path. It does not claim to remove vocals from YouTube in real time. Use only media you own or are authorized to process.

Prerequisites: Python 3.10+ (compatible PyTorch environment), ffmpeg and ffprobe in PATH, and free disk space for downloaded model weights and uncompressed audio. On a Mac with Intel CPU, start with CPU inference; it can be slow.

```bash
python3 -m pip install demucs
python3 tools/ai_separate.py /path/to/authorized-song.mp4 --output-dir ai-output
```

Outputs:
- `ai-output/htdemucs/input-audio/no_vocals.wav`: AI-separated instrumental
- `ai-output/htdemucs/input-audio/vocals.wav`: AI-separated vocals
- `ai-output/authorized-song-ai-karaoke.mp4`: original video stream with instrumental audio (when source has video)

The first invocation may download model weights. This experiment processes the full file offline, and may take substantially longer than the media duration on CPU. There is no automatic YouTube stream capture, DRM bypass, or WebView audio replacement. Audio/video synchronization and separation artifacts must be tested on actual samples before Android integration.


## Android AI mode (v0.19)

The Android app now includes **AI 去人聲（本機檔案）** in its collapsible controls. It opens a dedicated screen where you configure a **self-hosted HTTPS inference endpoint** and bearer token, pick an authorized local audio/video file, upload it for Demucs separation, and play the resulting instrumental (video preserved if provided).

Start the backend on a computer/server (Python environment with PyTorch, Demucs and FFmpeg):

```bash
python3 -m pip install fastapi uvicorn python-multipart demucs
export KARAOKE_AI_TOKEN="replace-with-a-long-random-secret"
python3 -m uvicorn tools.ai_server:app --host 127.0.0.1 --port 8765
```

Expose it only through an authenticated HTTPS reverse proxy / tunnel (for example, `https://your-domain/separate`). **Do not expose the inference port directly to the internet.** Enter that full endpoint URL and the matching token in the Android screen. Processing is offline/batch on the server, not on-device or real-time. It does not extract audio from YouTube or replace the YouTube WebView stream. Uploaded files are deleted by the server after the response finishes, except interrupted/crashed jobs which may require manual cleanup. The token is currently saved in Android app preferences, not hardware-backed encrypted storage; use a dedicated, revocable token.

**Not yet validated:** Android compilation, actual APK, inference throughput, real-device playback and video synchronization.

## On-device AI model milestone (v0.20, experimental)

The former Android server/token/upload screen has been replaced by an **on-device model readiness** screen. It downloads the `UVR_MDXNET_9482.fp16acc.tflite` LiteRT model from Hugging Face into app-private storage and attempts to open it using TensorFlow Lite on the phone. No song or media file is uploaded.

**Important: this is not yet an operational vocal separator.** Audio decoding, 44.1 kHz stereo STFT, segmenting and overlap-add, inference with correctly shaped tensors, inverse STFT, instrumental reconstruction, video playback synchronization, and YouTube WebView source audio replacement are all still missing. The Android UI explicitly reports this status.

Model source: https://huggingface.co/gyoom-sa/UVR-MDX-LiteRT (MIT-licensed repository). Model download requires internet once. The previous `tools/ai_server.py` remains a legacy standalone experiment but is not used by the Android screen.

**Validation pending:** Gradle build, on-device interpreter initialization and actual separation quality.

## v0.21 ONNX on-device foundation (experimental PR)

The earlier Hugging Face LiteRT URL returned 401/404. The Android model test now uses the public MDX-NET 3_9662 ONNX asset referenced by Murem's upstream `android/app/src/main/assets/models.json`:

- Model: `UVR_MDXNET_3_9662.onnx`
- Release: https://github.com/TRvlvr/model_repo/releases/download/all_public_uvr_models/UVR_MDXNET_3_9662.onnx
- Exact size: 29,704,436 bytes
- SHA-256: `e02220e80d8253f4c2209f8924298b2b686bbdf2868b788ff5500fb9bd94aadc`
- Android runtime: ONNX Runtime 1.31.1
- Upstream reference: https://github.com/Ignema/youtube-music-remover (MIT application code)

**This is a foundation only.** The UI validates model download and ONNX Runtime session creation. It does not yet perform AI separation on songs. A functional pipeline requires audio decoding/resampling, MDX-NET STFT, tiled inference, overlap-add iSTFT, instrumental reconstruction, and playback/video synchronization. YouTube WebView audio is not directly available for guaranteed replacement. No claim of functional AI karaoke should be made until those components and actual device tests pass.

The old `tools/ai_server.py` remains in the repository as an unused legacy experiment and is not called by the Android AI screen.

## Additional implementation in PR #1

- Added Android Storage Access Framework picker for local audio/video.
- Added `LocalAudioDecoder` using MediaExtractor and MediaCodec to write interleaved stereo float32 PCM.
- The first slice explicitly supports only 44.1 kHz mono/stereo input and reports unsupported sample rates/codecs.
- The UI reports decoded frame counts and **does not claim vocals were removed**.

**Still unimplemented:** MDX-Net STFT/iSTFT, inference over actual PCM, instrumental subtraction, WAV/MP4 export, and playback synchronization. This PR is not production-ready. Compilation and real-device tests remain outstanding.

## Experimental local AI vocal separation (PR #1, not device-tested)

The local-file pipeline now includes:

1. Android MediaCodec extraction to 44.1kHz stereo float PCM (currently rejects other sample rates).
2. Mixed-radix FFT, MDX STFT/iSTFT, tiled ONNX inference with overlap-add.
3. Vocal-stem compensation and subtraction from the original mix.
4. 16-bit stereo WAV output in app cache and MediaPlayer preview.

The FFT/STFT/separator source is adapted from Murem under its MIT license; see `THIRD_PARTY_MUREM_LICENSE.txt`.

**Unverified:** APK compile, model download availability, exact ONNX runtime tensor compatibility, real-device quality, performance, storage usage, and audio synchronization. This path operates on locally selected files only; it does not capture or replace protected YouTube WebView playback audio. Do not merge or publish as a working AI karaoke feature before testing.
