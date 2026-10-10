#!/usr/bin/env python3
"""Audit PT_LOAD alignment, including ELF libraries in packaged runtime ZIPs.
Does not certify runtime correctness or APK zipalign; it checks ELF page alignment.
"""
import argparse
import io
import struct
import sys
import zipfile


def audit_elf(data):
    if data[:4] != b'\x7fELF':
        return None
    bits, endian = data[4], data[5]
    if bits not in (1, 2) or endian not in (1, 2):
        raise ValueError('Invalid ELF header')
    prefix = '<' if endian == 1 else '>'
    if bits == 2:
        offset = struct.unpack_from(prefix + 'Q', data, 32)[0]
        size, count = struct.unpack_from(prefix + 'HH', data, 54)
        align_offset, align_fmt = 48, 'Q'
        offset_offset, address_offset = 8, 16
    else:
        offset = struct.unpack_from(prefix + 'I', data, 28)[0]
        size, count = struct.unpack_from(prefix + 'HH', data, 42)
        align_offset, align_fmt = 28, 'I'
        offset_offset, address_offset = 4, 8
    loads = []
    for index in range(count):
        start = offset + index * size
        kind = struct.unpack_from(prefix + 'I', data, start)[0]
        if kind == 1:
            alignment = struct.unpack_from(prefix + align_fmt, data, start + align_offset)[0]
            file_offset = struct.unpack_from(prefix + align_fmt, data, start + offset_offset)[0]
            address = struct.unpack_from(prefix + align_fmt, data, start + address_offset)[0]
            loads.append(alignment if (address - file_offset) % 16384 == 0 else 0)
    return loads


def inspect(data, path, result, depth=0):
    aligns = audit_elf(data)
    if aligns is not None:
        result.append((path, aligns, data[4] == 2))
    elif depth < 4 and zipfile.is_zipfile(io.BytesIO(data)):
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for entry in archive.infolist():
                if not entry.is_dir() and ('.so' in entry.filename or '/bin/' in entry.filename or entry.filename.endswith('.zip')):
                    inspect(archive.read(entry), path + '!' + entry.filename, result, depth + 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    args = parser.parse_args()
    result = []
    with open(args.apk, 'rb') as handle:
        inspect(handle.read(), args.apk, result)
    failures = [(path, aligns) for path, aligns, is64 in result if is64 and (not aligns or min(aligns) < 16384)]
    print(f'Audited {len(result)} ELF files; {len(failures)} 64-bit files fail 16 KB PT_LOAD alignment.')
    for path, aligns in failures:
        print(f'FAIL {path}: {aligns}')
    return 1 if failures or not result else 0


if __name__ == '__main__':
    sys.exit(main())
