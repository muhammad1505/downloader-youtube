#!/usr/bin/env python3
"""Audit and replace only the five WebP ELF files in the upstream FFmpeg AAR."""
import argparse
import io
import json
import re
import struct
import subprocess
import tempfile
import zipfile
from pathlib import Path

LIBRARIES = ("libsharpyuv.so", "libwebp.so", "libwebpdecoder.so", "libwebpdemux.so", "libwebpmux.so")
ABIS = ("arm64-v8a", "x86_64")


def segments(data):
    if data[:4] != b"\x7fELF":
        raise ValueError("Expected an ELF file")
    if data[4] != 2 or data[5] != 1:
        raise ValueError("Expected a little-endian 64-bit ELF")
    offset = struct.unpack_from("<Q", data, 32)[0]
    size, count = struct.unpack_from("<HH", data, 54)
    result = []
    for index in range(count):
        start = offset + index * size
        if struct.unpack_from("<I", data, start)[0] != 1:
            continue
        file_offset, address = struct.unpack_from("<QQ", data, start + 8)
        alignment = struct.unpack_from("<Q", data, start + 48)[0]
        result.append((file_offset, address, alignment))
    return result


def aligned(data):
    return all(alignment >= 16384 and (offset - address) % 16384 == 0
               for offset, address, alignment in segments(data))


def dynamic(toolchain, path):
    output = subprocess.check_output([str(toolchain / "llvm-readelf"), "-d", str(path)], text=True)
    soname = re.findall(r"\(SONAME\).*?\[([^]]+)\]", output)
    needed = sorted(re.findall(r"\(NEEDED\).*?\[([^]]+)\]", output))
    symbols = subprocess.check_output([str(toolchain / "llvm-nm"), "-D", "--defined-only", "--format=posix", str(path)], text=True)
    names = {line.split()[0].split("@")[0] for line in symbols.splitlines() if line.strip()}
    return soname, needed, names


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("build", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--toolchain", type=Path, required=True)
    args = parser.parse_args()
    audit = []
    replacements = {}
    with zipfile.ZipFile(args.input) as original, tempfile.TemporaryDirectory() as temp:
        for abi in ABIS:
            nested_name = f"jni/{abi}/libffmpeg.zip.so"
            nested_original = original.read(nested_name)
            with zipfile.ZipFile(io.BytesIO(nested_original)) as nested:
                updated = {}
                for library in LIBRARIES:
                    entry = f"usr/lib/{library}"
                    before = nested.read(entry)
                    old = Path(temp) / library
                    old.write_bytes(before)
                    replacement = args.build / abi / library
                    after = replacement.read_bytes()
                    if before[4:6] != after[4:6] or before[18:20] != after[18:20]:
                        raise ValueError(f"{abi}/{library}: ELF class, endianness, or architecture changed")
                    old_soname, old_needed, old_symbols = dynamic(args.toolchain, old)
                    new_soname, new_needed, new_symbols = dynamic(args.toolchain, replacement)
                    if old_soname != new_soname:
                        raise ValueError(f"{abi}/{library}: SONAME changed: {old_soname} -> {new_soname}")
                    missing = old_symbols - new_symbols
                    if missing:
                        raise ValueError(f"{abi}/{library}: exported symbols removed: {sorted(missing)}")
                    if old_needed != new_needed:
                        raise ValueError(f"{abi}/{library}: dependencies changed: {old_needed} -> {new_needed}")
                    if not aligned(after):
                        raise ValueError(f"{abi}/{library}: replacement is not compatible with 16 KB pages")
                    updated[entry] = after
                    audit.append({"abi": abi, "library": library, "soname": new_soname,
                                  "original_needed": old_needed, "needed": new_needed,
                                  "original_exports": len(old_symbols), "exports": len(new_symbols),
                                  "missing_exports": [], "aligned_16kb": True})
                buffer = io.BytesIO()
                with zipfile.ZipFile(buffer, "w") as patched:
                    for info in nested.infolist():
                        data = updated.get(info.filename, nested.read(info.filename))
                        if ".so" in info.filename and data.startswith(b"\x7fELF") and not aligned(data):
                            raise ValueError(f"{abi}/{info.filename}: incompatible ELF remains")
                        patched.writestr(info, data)
                replacements[nested_name] = buffer.getvalue()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.output, "w") as result:
            for info in original.infolist():
                result.writestr(info, replacements.get(info.filename, original.read(info.filename)))
    args.output.with_suffix(".audit.json").write_text(json.dumps(audit, indent=2) + "\n")
    print(f"Patched {len(audit)} libraries; SONAME, exports, dependencies and 16 KB ELF checks passed.")


if __name__ == "__main__":
    main()
