# Android vocal separation: one-run stop-loss experiment

## Goal
Test whether the existing MDX model can process captured WebView playback faster than real time. Device evidence: 7 seconds of audio took 11,857 ms (CPU 2 threads) and 9,031 ms (CPU 4 threads), with inference taking 10,038 ms and 7,491 ms respectively.

## Single-run benchmark
1. Play YouTube in the WebView and start the capture permission flow.
2. Tap **AI 分析 7 秒** once. Capture seven seconds of stereo PCM.
3. The app tests **CPU 4 threads**, **XNNPACK 4 threads**, and **NNAPI** on the same captured segment. CPU creates the existing preview WAV; other providers discard output. Each provider's load/inference/total time or failure is reported. No repeated capture or installation needed.
4. Use the fastest total runtime (excluding capture, but including session initialization) as a conservative first-pass throughput measurement. A result >=1.5× is *only* permission to investigate further, not proof of live karaoke.

## Caveats
- Providers can silently fall back to CPU for unsupported operators. A successful NNAPI run is **not** proof that the GPU/NPU ran the entire graph.
- CPU writes a WAV while XNNPACK and NNAPI discard the result; therefore total times are not perfectly apples-to-apples. Compare the inference columns for the cleaner compute comparison.
- Session creation time, warm-up, temperature and CPU contention affect results. This is a one-pass screen, not a statistically robust benchmark.
- NNAPI may initialize slowly or fail on this model. The app records a provider-specific failure and continues; no provider is guaranteed to be faster.
- The current model expects [1,4,2048,256] float32 spectrograms; a different lightweight model cannot be swapped into this code without verifying its input/output shapes, preprocessing, source type, output alignment, license and Android runtime support.
- No model download or model replacement is added in this PR. Do **not** claim that a lighter model was tested.
- Playback capture and separation do not mute the original YouTube WebView audio. Real-time playback routing and synchronisation remain unsolved.

## Stop-loss
If no candidate achieves 1.5× throughput, stop adding streaming UI to this MDX pipeline. Prefer existing low-latency stereo DSP, or conduct a **separate** model-selection experiment only after finding a downloadable, licensed, Android-compatible, genuinely smaller model with documented ONNX shape and benchmark protocol.

Official references:
- https://onnxruntime.ai/docs/tutorials/mobile/
- https://onnxruntime.ai/docs/execution-providers/Xnnpack-ExecutionProvider.html
- https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html
