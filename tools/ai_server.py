"""Self-hosted Demucs inference API for Karaoke Lab authorized local media.

Run with: pip install fastapi uvicorn python-multipart demucs
           uvicorn tools.ai_server:app --host 0.0.0.0 --port 8765
Requires ffmpeg and ffprobe. Use behind HTTPS and authentication if exposed.
"""
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Header, HTTPException, UploadFile
from fastapi.responses import FileResponse
from starlette.background import BackgroundTask

app = FastAPI(title="Karaoke Lab AI separation")
TOKEN = os.environ.get("KARAOKE_AI_TOKEN", "")
MAX_BYTES = 250 * 1024 * 1024


def remove_dir(path: Path):
    shutil.rmtree(path, ignore_errors=True)


@app.post("/separate")
def separate(file: UploadFile = File(...), authorization: str | None = Header(None)):
    if not TOKEN or authorization != f"Bearer {TOKEN}":
        raise HTTPException(401, "Configure KARAOKE_AI_TOKEN and supply bearer token")
    if not shutil.which("ffmpeg") or not shutil.which("ffprobe"):
        raise HTTPException(503, "ffmpeg/ffprobe unavailable")
    folder = Path(tempfile.mkdtemp(prefix="karaoke-ai-"))
    try:
        original = folder / "source"
        size = 0
        with original.open("wb") as output:
            while data := file.file.read(1024 * 1024):
                size += len(data)
                if size > MAX_BYTES:
                    raise HTTPException(413, "File too large (250 MB max)")
                output.write(data)
        wav = folder / "input.wav"
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(original),
                        "-vn", "-ac", "2", "-ar", "44100", str(wav)],
                       check=True, timeout=180)
        subprocess.run([sys.executable, "-m", "demucs", "--two-stems", "vocals",
                        "-n", "htdemucs", "-d", "cpu", "-o", str(folder), str(wav)],
                       check=True, timeout=3600)
        instrumental = folder / "htdemucs" / "input" / "no_vocals.wav"
        if not instrumental.exists():
            raise RuntimeError("Demucs instrumental output missing")
        video = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v:0",
                                "-show_entries", "stream=index", "-of", "csv=p=0", str(original)],
                               capture_output=True, text=True, check=True)
        if video.stdout.strip():
            result = folder / "karaoke.mp4"
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(original),
                            "-i", str(instrumental), "-map", "0:v:0", "-map", "1:a:0",
                            "-c:v", "copy", "-c:a", "aac", "-b:a", "256k",
                            "-shortest", str(result)], check=True, timeout=180)
            media_type = "video/mp4"
        else:
            result = instrumental
            media_type = "audio/wav"
        return FileResponse(result, media_type=media_type, filename=result.name,
                            background=BackgroundTask(remove_dir, folder))
    except HTTPException:
        remove_dir(folder)
        raise
    except Exception as exc:
        remove_dir(folder)
        raise HTTPException(422, f"Separation failed: {type(exc).__name__}") from exc
