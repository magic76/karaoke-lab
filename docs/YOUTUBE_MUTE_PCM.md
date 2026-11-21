# YouTube mute versus PCM capture experiment

Play a YouTube MV in WebView with all other apps silent. Start PCM capture and grant MediaProjection. Observe RMS for 5 seconds. Tap the YouTube mute test, listen for actual silence and compare RMS for another 5 seconds. Restore sound and compare again.

If mute fails, top-level media element access is insufficient. If mute works but RMS drops to zero, captured PCM is not independently available after source mute. If mute works but RMS persists, rule out other audio sources before concluding success.

The app does not save, process, or replay YouTube audio. Android capture is not an audio replacement API.