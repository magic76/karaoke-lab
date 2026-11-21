#!/usr/bin/env python3
"""Run a genuine pretrained Demucs model on a local song, then export instrumental WAV.
Requires Python 3.10+, torch, demucs, soundfile. No YouTube downloading.
"""
import argparse
import pathlib
import shutil
import subprocess
import sys

def main():
    parser = argparse.ArgumentParser(description="Separate local audio with pretrained Demucs")
    parser.add_argument("song", type=pathlib.Path)
    parser.add_argument("--out", type=pathlib.Path, default=pathlib.Path("karaoke-output"))
    parser.add_argument("--model", default="htdemucs")
    args = parser.parse_args()
    if not args.song.is_file():
        parser.error("Song file does not exist")
    if shutil.which("ffmpeg") is None:
        parser.error("ffmpeg must be installed")
    args.out.mkdir(parents=True, exist_ok=True)
    cmd = [sys.executable, "-m", "demucs", "--two-stems=vocals",
           "-n", args.model, "-o", str(args.out), str(args.song)]
    print("Separating with pretrained Demucs:", " ".join(cmd), flush=True)
    subprocess.run(cmd, check=True)
    folder = args.out / args.model / args.song.stem
    instrumental = folder / "no_vocals.wav"
    if not instrumental.is_file():
        raise RuntimeError(f"Expected instrumental not found: {instrumental}")
    # Android MediaCodec-compatible 16-bit stereo PCM, 48kHz.
    target = args.out / (args.song.stem + "-instrumental-48k.wav")
    subprocess.run(["ffmpeg", "-y", "-i", str(instrumental), "-ar", "48000",
                    "-ac", "2", "-c:a", "pcm_s16le", str(target)], check=True)
    print(f"SUCCESS: {target.resolve()}")
    print("Copy WAV to your Android phone and choose Demucs instrumental in Karaoke Lab.")

if __name__ == "__main__":
    main()
