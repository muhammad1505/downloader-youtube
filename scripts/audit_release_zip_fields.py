#!/usr/bin/env python3
"""Check actual APK DEX constructors used reflectively by Commons Compress ZIP init."""
import argparse
from pathlib import Path
import re
import struct
import zipfile

FIELDS = ('AsiExtraField', 'X5455_ExtendedTimestamp', 'X7875_NewUnix', 'JarMarker',
          'UnicodePathExtraField', 'UnicodeCommentExtraField', 'Zip64ExtendedInformationExtraField',
          'X000A_NTFS', 'X0014_X509Certificates', 'X0015_CertificateIdForFile',
          'X0016_CertificateIdForCentralDirectory', 'X0017_StrongEncryptionHeader',
          'X0019_EncryptionRecipientCertificateList')
PREFIX = 'org.apache.commons.compress.archivers.zip.'


def uleb(data, offset):
    result = shift = 0
    for _ in range(5):
        byte = data[offset]
        offset += 1
        result |= (byte & 127) << shift
        if byte < 128:
            return result, offset
        shift += 7
    raise ValueError('Malformed DEX ULEB128')


def classes(data):
    if not data.startswith(b'dex\n'):
        raise ValueError('Expected standard DEX')
    def integer(offset):
        return struct.unpack_from('<I', data, offset)[0]
    strings = []
    for index in range(integer(56)):
        _, offset = uleb(data, integer(integer(60) + 4 * index))
        strings.append(data[offset:data.index(0, offset)].decode('utf-8', errors='replace'))
    types = [strings[integer(integer(68) + 4 * index)] for index in range(integer(64))]
    for index in range(integer(96)):
        offset = integer(100) + index * 32
        descriptor, flags, class_data = types[integer(offset)], integer(offset + 4), integer(offset + 24)
        constructor = False
        if class_data:
            counts = []
            cursor = class_data
            for _ in range(4):
                count, cursor = uleb(data, cursor)
                counts.append(count)
            for _ in range(counts[0] + counts[1]):
                _, cursor = uleb(data, cursor)
                _, cursor = uleb(data, cursor)
            method_index = 0
            for _ in range(counts[2]):
                difference, cursor = uleb(data, cursor)
                method_flags, cursor = uleb(data, cursor)
                code_offset, cursor = uleb(data, cursor)
                method_index += difference
                method = integer(92) + method_index * 8
                proto = struct.unpack_from('<H', data, method + 2)[0]
                parameters = integer(integer(76) + proto * 12 + 8)
                if strings[integer(method + 4)] == '<init>' and method_flags & 1 and code_offset and (parameters == 0 or integer(parameters) == 0):
                    constructor = True
        yield descriptor, flags, constructor


def audit(apk, mapping=None):
    names = {PREFIX + field: PREFIX + field for field in FIELDS}
    if mapping:
        for line in Path(mapping).read_text().splitlines():
            match = re.fullmatch(r'(\S+) -> (\S+):', line)
            if match and match[1] in names:
                names[match[1]] = match[2]
    found = {}
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if re.fullmatch(r'classes\d*\.dex', name):
                found.update((descriptor, (flags, constructor)) for descriptor, flags, constructor in classes(archive.read(name)))
    failures = []
    for original, mapped in names.items():
        item = found.get('L' + mapped.replace('.', '/') + ';')
        if item is None:
            failures.append((original, 'missing DEX class'))
        elif not item[0] & 1:
            failures.append((original, 'non-public DEX class'))
        elif item[0] & 0x600:
            failures.append((original, 'abstract/interface DEX class'))
        elif not item[1]:
            failures.append((original, 'missing public no-argument constructor'))
    return failures


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    parser.add_argument('--mapping')
    args = parser.parse_args()
    failures = audit(args.apk, args.mapping)
    print(f'Audited {len(FIELDS)} reflective ZIP field classes; {len(failures)} failures.')
    for name, reason in failures:
        print(f'FAIL {name}: {reason}')
    return 1 if failures else 0


if __name__ == '__main__':
    raise SystemExit(main())
