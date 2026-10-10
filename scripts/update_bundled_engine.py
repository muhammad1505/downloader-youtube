#!/usr/bin/env python3
"""Pin an explicit official yt-dlp release; verify published SHA-256 before replacing assets."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import urllib.request


def fetch(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'TubeFlow-build'}), timeout=45) as response:
        return response.read()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version', help='Explicit release tag, e.g. 2026.08.19')
    args = parser.parse_args()
    if not re.fullmatch(r'\d{4}\.\d{2}\.\d{2}', args.version):
        parser.error('Use an explicit stable YYYY.MM.DD release tag')
    root = Path(__file__).resolve().parents[1]
    base = f'https://github.com/yt-dlp/yt-dlp/releases/download/{args.version}'
    sums = fetch(base + '/SHA2-256SUMS')
    expected = next(line.split()[0] for line in sums.decode().splitlines() if line.split()[-1] == 'yt-dlp')
    payload = fetch(base + '/yt-dlp')
    actual = hashlib.sha256(payload).hexdigest()
    if expected != actual:
        raise RuntimeError('Official asset checksum mismatch; nothing replaced')
    destination = root / 'app/src/main/assets'
    destination.mkdir(parents=True, exist_ok=True)
    temp = destination / 'yt-dlp.download'
    try:
        temp.write_bytes(payload)
        os.replace(temp, destination / 'yt-dlp')
    finally:
        temp.unlink(missing_ok=True)
    manifest = {'version': args.version, 'sha256': actual, 'source': base + '/yt-dlp'}
    (destination / 'engine.json').write_text(json.dumps(manifest, indent=2) + '\n')
    (root / 'docs' / f'yt-dlp-SHA2-256SUMS-{args.version}.txt').write_bytes(sums)
    print(f'Pinned yt-dlp {args.version}, SHA-256 {actual}; rebuild and test on device before release.')


if __name__ == '__main__':
    main()
