# v0.8 direct YouTube WebAudio experiment

This build replaces the previous mute/local-media controls with **實驗：YouTube 去人聲**. It attempts to route the top-frame HTMLVideoElement through a Web Audio graph: source -> stereo channel split -> L-R/R-L (center cancellation) -> AudioContext destination. It is NOT an AI separation model, and center-panned instruments may also be removed.

## Test

1. Play a YouTube MV in the embedded WebView.
2. Tap the experimental karaoke button and listen. Tap again to compare original vs processed output.
3. If playback becomes silent or returns `no-video-in-top-frame`, capture the displayed diagnostic JSON. Reload the page to recover.
4. Optional: use PCM meter to compare output level with and without the experiment.

## Limitations

The YouTube video may be cross-origin to the top frame or use a cross-origin media resource. In that case the Web Audio MediaElementAudioSourceNode can output silence due to browser security. Creating the source node can permanently change routing for the current video element; a page reload may be required. The graph cannot access cross-origin iframes. No Chromium binary is modified and no AI inference is performed.

**Success** means the music remains audible and center vocals become noticeably quieter while video continues. **Failure** is an important feasibility result; do not interpret button success JSON alone as audio success.
