"""Transcribe an audio file to text using OpenAI's open-source Whisper model.

Usage:
    python transcribe.py <audio_path> [--model MODEL] [--language LANG] [--output FILE]

Examples:
    python transcribe.py meeting.mp3
    python transcribe.py interview.wav --model small --language en
    python transcribe.py call.m4a --output transcript.txt
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

try:
    import whisper
except ImportError:
    sys.stderr.write(
        "Error: the 'openai-whisper' package is not installed.\n"
        "Install it with:  pip install -r requirements.txt\n"
    )
    sys.exit(1)


VALID_MODELS = ("tiny", "base", "small", "medium", "large", "turbo")


def transcribe(
    audio_path: Path,
    model_name: str = "base",
    language: str | None = None,
) -> dict:
    if not audio_path.exists():
        raise FileNotFoundError(f"Audio file not found: {audio_path}")

    model = whisper.load_model(model_name)
    return model.transcribe(
        str(audio_path),
        language=language,
        verbose=False,
        fp16=False,
    )


def format_segments(segments: list[dict]) -> str:
    lines = []
    for seg in segments:
        start = _format_timestamp(seg["start"])
        end = _format_timestamp(seg["end"])
        lines.append(f"[{start} --> {end}] {seg['text'].strip()}")
    return "\n".join(lines)


def _format_timestamp(seconds: float) -> str:
    hours, remainder = divmod(int(seconds), 3600)
    minutes, secs = divmod(remainder, 60)
    millis = int((seconds - int(seconds)) * 1000)
    return f"{hours:02d}:{minutes:02d}:{secs:02d}.{millis:03d}"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Transcribe an audio file to text using OpenAI Whisper."
    )
    parser.add_argument("audio", type=Path, help="Path to the audio file (mp3, wav, m4a, ...).")
    parser.add_argument(
        "--model",
        default="base",
        choices=VALID_MODELS,
        help="Whisper model size (default: base). Larger = more accurate but slower.",
    )
    parser.add_argument(
        "--language",
        default=None,
        help="ISO language code (e.g. 'en', 'es'). Auto-detected if omitted.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="Write transcript to this file. Prints to stdout if omitted.",
    )
    parser.add_argument(
        "--timestamps",
        action="store_true",
        help="Include per-segment timestamps in the output.",
    )
    args = parser.parse_args()

    try:
        result = transcribe(args.audio, args.model, args.language)
    except FileNotFoundError as exc:
        sys.stderr.write(f"{exc}\n")
        return 1

    text = format_segments(result["segments"]) if args.timestamps else result["text"].strip()

    if args.output:
        args.output.write_text(text + "\n", encoding="utf-8")
        print(f"Transcript written to {args.output}")
    else:
        print(text)

    return 0


if __name__ == "__main__":
    sys.exit(main())
