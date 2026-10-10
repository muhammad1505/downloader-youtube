import io
import struct
import unittest
import zipfile
from audit_native import audit_elf, inspect


def elf(alignment, offset=0, address=0):
    data = bytearray(120)
    data[:6] = b'\x7fELF\x02\x01'
    struct.pack_into('<Q', data, 32, 64)
    struct.pack_into('<HH', data, 54, 56, 1)
    struct.pack_into('<I', data, 64, 1)
    struct.pack_into('<Q', data, 72, offset)
    struct.pack_into('<Q', data, 80, address)
    struct.pack_into('<Q', data, 112, alignment)
    return bytes(data)


class NativeAuditTest(unittest.TestCase):
    def test_rejects_4k_and_incongruent_segment(self):
        self.assertEqual(audit_elf(elf(4096)), [4096])
        self.assertEqual(audit_elf(elf(16384, offset=4096)), [0])
        self.assertEqual(audit_elf(elf(16384, offset=16384)), [16384])

    def test_finds_elf_inside_runtime_zip_disguised_as_so(self):
        nested = io.BytesIO()
        with zipfile.ZipFile(nested, 'w') as archive:
            archive.writestr('usr/lib/libwebp.so', elf(4096))
        apk = io.BytesIO()
        with zipfile.ZipFile(apk, 'w') as archive:
            archive.writestr('lib/arm64-v8a/libffmpeg.zip.so', nested.getvalue())
        result = []
        inspect(apk.getvalue(), 'candidate.apk', result)
        self.assertEqual(len(result), 1)
        self.assertIn('libffmpeg.zip.so!usr/lib/libwebp.so', result[0][0])
        self.assertEqual(result[0][1], [4096])


if __name__ == '__main__':
    unittest.main()
