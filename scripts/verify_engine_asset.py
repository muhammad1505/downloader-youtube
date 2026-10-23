#!/usr/bin/env python3
"""Verify the bundled yt-dlp manifest, checksum, source URL, and bootstrap asset."""
import hashlib
import json
import re
import sys
from pathlib import Path

VERSION = re.compile(r"^\d{4}\.\d{2}\.\d{2}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
BOOTSTRAP_MARKERS = ("tubeflow-process-file", "tubeflow-process-token", "tubeflow-optional-audio")


class EngineAssetError(Exception):
    pass


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify(root="."):
    assets = Path(root) / "app" / "src" / "main" / "assets"
    manifest_path = assets / "engine.json"
    engine_path = assets / "yt-dlp"
    bootstrap_path = assets / "engine-bootstrap.py"
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        raise EngineAssetError(f"engine.json tidak dapat dibaca: {error}") from error

    version = manifest.get("version")
    checksum = manifest.get("sha256")
    source = manifest.get("source")
    if not isinstance(version, str) or not VERSION.fullmatch(version):
        raise EngineAssetError("versi engine bukan tanggal rilis YYYY.MM.DD")
    if not isinstance(checksum, str) or not SHA256.fullmatch(checksum):
        raise EngineAssetError("sha256 engine tidak valid")
    expected_source = (
        f"https://github.com/yt-dlp/yt-dlp/releases/download/{version}/yt-dlp"
    )
    if source != expected_source:
        raise EngineAssetError("source engine tidak menunjuk rilis yt-dlp yang dipin")
    for path in (engine_path, bootstrap_path):
        if not path.is_file():
            raise EngineAssetError(f"aset engine hilang: {path.name}")
    actual = sha256(engine_path)
    if actual != checksum:
        raise EngineAssetError(
            f"sha256 aset tidak cocok: manifest {checksum}, file {actual}"
        )
    bootstrap = bootstrap_path.read_text(encoding="utf-8", errors="strict")
    missing = [marker for marker in BOOTSTRAP_MARKERS if marker not in bootstrap]
    if missing:
        raise EngineAssetError("bootstrap tidak memuat penanda: " + ", ".join(missing))
    return version, checksum


def main():
    try:
        version, checksum = verify(Path(__file__).resolve().parent.parent)
    except (EngineAssetError, OSError, UnicodeError) as error:
        print(f"engine asset tidak valid: {error}", file=sys.stderr)
        return 1
    print(f"engine asset valid: {version} ({checksum})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
