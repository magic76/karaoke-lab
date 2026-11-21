# v0.4: actual pretrained AI vocal separation

This release adds an **end-to-end, genuine AI vocal separation workflow** using pretrained Demucs htdemucs on a computer, with playback of its instrumental output in the Android APK.

It does **not** yet run htdemucs inference on the phone or process YouTube WebView audio. This avoids misleading claims about model compatibility and real-time performance.

## Generate instrumental with Demucs

On a PC/Mac with Python 3.10+ and FFmpeg:

```bash
python3 -m pip install demucs
python3 tools/separate_local.py "/path/to/your-owned-song.mp3" --out ./karaoke-output
```

The script downloads the public pretrained Demucs weights via the package on first use. Check the model and music licenses yourself.

The output is `karaoke-output/<song>-instrumental-48k.wav`.

Copy it to Android (e.g. adb push), open the app, select **Choose AI-separated instrumental (Demucs)**, choose the WAV, switch to **Demucs instrumental**, then press Play.

Compare with the original and Center cancel on the same song. The AI output is produced by pretrained Demucs, not center cancellation.

## Phone-only AI limitations

The existing experimental ONNX mode expects a custom float32 [1,2,48000] -> [1,2,48000] instrumental model. htdemucs is **not compatible** with this contract. True on-device Demucs requires export, segmentation, resampling, overlap-add, benchmark, and thermal testing. None is claimed here.

## Not included

No YouTube audio extraction or modification, no direct real-time vocal removal of YouTube playback, no synchronized MV, no microphone monitor.
