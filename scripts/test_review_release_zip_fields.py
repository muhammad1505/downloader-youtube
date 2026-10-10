"""Regression checks for reflection requirements in optimized APK bytecode."""
from pathlib import Path
import re
import struct
import subprocess
import tempfile
import unittest
import zipfile

from audit_release_zip_fields import FIELDS, PREFIX, audit, classes, uleb


def fixture(overrides=None, omit=None):
    """Create DEX tables read by the auditor; no Android runtime is needed."""
    overrides = overrides or {}
    names = [field for field in FIELDS if field != omit]
    descriptors = ['L' + (PREFIX + name).replace('.', '/') + ';' for name in names]
    strings = descriptors + ['V', 'I', '<init>']
    count = len(names)
    data = bytearray(112)
    data[:8] = b'dex\n035\x00'

    def reserve(size):
        offset = len(data)
        data.extend(bytes(size))
        return offset

    def word(offset, value):
        struct.pack_into('<I', data, offset, value)

    string_ids = reserve(4 * len(strings))
    type_ids = reserve(4 * (count + 2))
    protos = reserve(24)
    methods = reserve(8 * count)
    definitions = reserve(32 * count)
    parameters = reserve(8)
    word(parameters, 1)
    struct.pack_into('<H', data, parameters + 4, count + 1)
    struct.pack_into('<III', data, protos, count, count, 0)
    struct.pack_into('<III', data, protos + 12, count, count, parameters)
    for index, name in enumerate(names):
        flags, method_flags, argument = overrides.get(name, (1, 1, False))
        struct.pack_into('<HHI', data, methods + 8 * index, index, int(argument), count + 2)
        class_data = len(data)
        # Static/instance fields, direct/virtual methods, index delta, access, code.
        data.extend(bytes([0, 0, 1, 0, index, method_flags, 1]))
        word(definitions + index * 32, index)
        word(definitions + index * 32 + 4, flags)
        word(definitions + index * 32 + 24, class_data)
    for index, value in enumerate(strings):
        word(string_ids + 4 * index, len(data))
        data.extend(bytes([len(value)]))
        data.extend(value.encode() + b'\x00')
    for index in range(count + 2):
        word(type_ids + 4 * index, index)
    for size_offset, offset_offset, size, offset in [
        (56, 60, len(strings), string_ids), (64, 68, count + 2, type_ids),
        (72, 76, 2, protos), (88, 92, count, methods), (96, 100, count, definitions),
    ]:
        word(size_offset, size)
        word(offset_offset, offset)
    return bytes(data)


class ReleaseZipReflectionTest(unittest.TestCase):
    def audit_fixture(self, payload):
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'fixture.apk'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('classes.dex', payload)
            return audit(apk)

    def test_accepts_all_concrete_public_zero_argument_fields(self):
        self.assertEqual(self.audit_fixture(fixture()), [])

    def test_rejects_abstract_class_even_with_public_constructor(self):
        self.assertEqual(self.audit_fixture(fixture({'AsiExtraField': (0x401, 1, False)})),
                         [(PREFIX + 'AsiExtraField', 'abstract/interface DEX class')])

    def test_rejects_non_public_class_despite_public_constructor(self):
        self.assertEqual(self.audit_fixture(fixture({'AsiExtraField': (0, 1, False)})),
                         [(PREFIX + 'AsiExtraField', 'non-public DEX class')])

    def test_rejects_private_and_parameterized_constructors(self):
        for flags, argument in [(2, False), (1, True)]:
            with self.subTest(flags=flags, argument=argument):
                failures = self.audit_fixture(fixture({'AsiExtraField': (1, flags, argument)}))
                self.assertEqual(failures, [(PREFIX + 'AsiExtraField', 'missing public no-argument constructor')])

    def test_missing_registry_class_fails(self):
        self.assertEqual(self.audit_fixture(fixture(omit='AsiExtraField')),
                         [(PREFIX + 'AsiExtraField', 'missing DEX class')])

    def test_malformed_uleb_and_non_dex_fail(self):
        with self.assertRaises(ValueError):
            uleb(bytes([0x80] * 5), 0)
        with self.assertRaises(ValueError):
            list(classes(b'not DEX'))

    def test_registry_matches_actual_pinned_dependency_initializer(self):
        cache = Path.home() / '.gradle/caches/modules-2/files-2.1/org.apache.commons/commons-compress/1.12'
        jars = list(cache.glob('*/commons-compress-1.12.jar'))
        if not jars:
            self.skipTest('Pinned Commons Compress 1.12 is not cached; resolve Gradle dependencies first.')
        bytecode = subprocess.run(['javap', '-c', '-p', '-classpath', str(jars[0]),
                                  PREFIX + 'ExtraFieldUtils'], check=True,
                                 capture_output=True, text=True).stdout
        initializer = bytecode.split('static {};', 1)[1]
        actual = re.findall(r'// class org/apache/commons/compress/archivers/zip/(\w+)', initializer)
        self.assertEqual(set(actual), set(FIELDS))
        self.assertEqual(len(actual), len(FIELDS))


if __name__ == '__main__':
    unittest.main()
