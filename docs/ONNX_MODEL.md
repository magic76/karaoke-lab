# v0.3 experimental ONNX contract

This version adds a **real ONNX Runtime inference path** for local audio. It does not ship pretrained weights.

## Accepted model

- A model whose sole input is a float32 tensor **[1,2,48000]** containing one second of planar stereo audio at **48 kHz**.
- Its sole output must be a float32 **[1,2,48000]** tensor of **instrumental-only** audio.
- No spectral transforms, overlap, lookahead or postprocessing are provided by the app.
- **Typical Demucs / MDX models are NOT compatible with this contract directly.** They require model-specific preprocessing and overlap-add.
- The input/output contract is validated at model loading. A pass-through model would only demonstrate runtime integration, **not vocal removal**.

## Testing

1. Select an ONNX model explicitly exported to the above contract.
2. Choose a **local stereo 48 kHz** audio file, and choose AI ONNX mode.
3. Press Play. Check status for load errors, inference time, unsupported formats, and underruns.
4. Compare original / center cancel / AI modes on the same local file. Inference happens on a worker thread, never the UI thread.
5. Report device thermal behavior, audio glitches, real-time factor and output quality.

## Limitations

- This is an **experimental inference integration**, not a pretrained turnkey vocal separator.
- Model selection is explicit; the repository does not download or bundle any copyrighted music or model weights.
- AI mode is local-media only. WebView/YouTube remains an independent playback/capture diagnostics view.
- One-second independent blocks may produce audible boundaries. Production source separation requires overlap-add, context windows and correct model architecture.
- Requires a 48 kHz stereo 16-bit decoder output. Other files should be converted before use.
