#!/usr/bin/env python3
"""Offline AI vocal separation proof for user-owned / authorized media.

Requires Python 3.10+ and ffmpeg. Install: python -m pip install demucs
Run: python tools/ai_separate.py input.mp4 --output-dir ai-output
The first run downloads the selected Demucs weights.
"""
import argparse
import shutil
import subprocess
import sys
from pathlib import Path


def run(args):
    print("+", " ".join(map(str, args)), flush=True)
    subprocess.run(list(map(str, args)), check=True)


def main():
    parser = argparse.ArgumentParser(description="Demucs AI vocal separation on authorized local media")
    parser.add_argument("input", type=Path, help="Local audio or video file")
    parser.add_argument("--output-dir", type=Path, default=Path("ai-output"))
    parser.add_argument("--model", default="htdemucs", help="Demucs model name")
    parser.add_argument("--device", choices=["cpu", "cuda", "mps"], default="cpu")
    parser.add_argument("--no-video", action="store_true", help="Skip video remux")
    args = parser.parse_args()

    source = args.input.expanduser().resolve()
    if not source.is_file():
        parser.error(f"File not found: {source}")
    if not shutil.which("ffmpeg"):
        parser.error("ffmpeg is required (install it and add it to PATH)")

    out = args.output_dir.expanduser().resolve()
    out.mkdir(parents=True, exist_ok=True)
    audio = out / "input-audio.wav"

    # Decode locally to 44.1 kHz stereo WAV for consistent model input.
    run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
         "-i", source, "-vn", "-ac", "2", "-ar", "44100", audio])

    run([sys.executable, "-m", "demucs", "--two-stems", "vocals",
         "-n", args.model, "-d", args.device, "-o", out, audio])
    instrumental = out / args.model / "input-audio" / "no_vocals.wav"
    vocals = out / args.model / "input-audio" / "vocals.wav"
    if not instrumental.is_file():
        raise RuntimeError(f"Demucs output missing: {instrumental}")

    print(f"\nInstrumental: {instrumental}")
    if vocals.is_file():
        print(f"Vocals: {vocals}")

    # Copy original video stream without re-encoding; replace only audio.
    # A file with no video will simply skip this step.
    if not args.no_video:
        probe = subprocess.run(
            ["ffprobe", "-v", "error", "-select_streams", "v:0",
             "-show_entries", "stream=index", "-of", "csv=p=0", str(source)],
            capture_output=True, text=True, check=True,
        )
        if probe.stdout.strip():
            result = out / f"{source.stem}-ai-karaoke.mp4"
            run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
                 "-i", source, "-i", instrumental,
                 "-map", "0:v:0", "-map", "1:a:0",
                 "-c:v", "copy", "-c:a", "aac", "-b:a", "256k",
                 "-shortest", result])
            print(f"AI karaoke video: {result}")


if __name__ == "__main__":
    main()
