#!/usr/bin/env python3
"""Download the Piper en_AU voice and convert it for sherpa-onnx.

Writes into app/src/main/assets/voice/:
  en_AU-librivox-medium.onnx   model with the metadata sherpa-onnx expects
  tokens.txt                   phoneme symbol -> id table
  espeak-ng-data/              pronunciation data used to turn text into phonemes

Requires: pip install onnx
Run from anywhere: python3 scripts/prepare_voice.py
"""

import io
import json
import shutil
import sys
import urllib.request
import zipfile
from pathlib import Path

import onnx

MODEL_REPO = "https://huggingface.co/DataCraftsmanAustralia/piper-en_AU-librivox-medium/resolve/main"
MODEL_NAME = "en_AU-librivox-medium"
ESPEAK_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.zip"

OUT_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "voice"


def download(url: str) -> bytes:
    print(f"Downloading {url}")
    with urllib.request.urlopen(url) as resp:
        return resp.read()


def write_tokens(config: dict, path: Path) -> None:
    # Piper 1.5's default map includes merged diphthongs ("aɪ" etc.). They're only used
    # when the voice config sets vowel_clusters, and sherpa-onnx rejects multi-character
    # tokens, so leave them out.
    if config.get("vowel_clusters"):
        raise SystemExit("Voice uses vowel_clusters; sherpa-onnx can't feed merged diphthongs")
    with path.open("w", encoding="utf-8") as f:
        for symbol, ids in config["phoneme_id_map"].items():
            if len(symbol) == 1:
                f.write(f"{symbol} {ids[0]}\n")


def prune_espeak(espeak_dir: Path) -> None:
    # Only English is needed; the other language dictionaries are most of the size.
    for dict_file in espeak_dir.glob("*_dict"):
        if dict_file.name != "en_dict":
            dict_file.unlink()


def add_metadata(model_bytes: bytes, config: dict, path: Path) -> None:
    model = onnx.load_from_string(model_bytes)
    meta = {
        "model_type": "vits",
        "comment": "piper",
        "language": "English",
        "voice": config["espeak"]["voice"],
        "has_espeak": 1,
        "n_speakers": config["num_speakers"],
        "sample_rate": config["audio"]["sample_rate"],
    }
    del model.metadata_props[:]
    for key, value in meta.items():
        prop = model.metadata_props.add()
        prop.key = key
        prop.value = str(value)
    onnx.save(model, str(path))


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    config = json.loads(download(f"{MODEL_REPO}/{MODEL_NAME}.onnx.json"))
    write_tokens(config, OUT_DIR / "tokens.txt")
    add_metadata(download(f"{MODEL_REPO}/{MODEL_NAME}.onnx"), config, OUT_DIR / f"{MODEL_NAME}.onnx")

    espeak_dir = OUT_DIR / "espeak-ng-data"
    shutil.rmtree(espeak_dir, ignore_errors=True)
    with zipfile.ZipFile(io.BytesIO(download(ESPEAK_URL))) as zf:
        zf.extractall(OUT_DIR)
    prune_espeak(espeak_dir)

    print(f"Voice ready in {OUT_DIR}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
