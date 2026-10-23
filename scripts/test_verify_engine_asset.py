import hashlib
import json
import tempfile
import unittest
from pathlib import Path

from verify_engine_asset import EngineAssetError, verify

BOOTSTRAP = "tubeflow-process-file tubeflow-process-token tubeflow-optional-audio"


class EngineAssetTest(unittest.TestCase):
    def make_tree(self, payload=b"engine", version="2026.10.21", checksum=None, bootstrap=BOOTSTRAP):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        assets = Path(temporary.name) / "app" / "src" / "main" / "assets"
        assets.mkdir(parents=True)
        engine = assets / "yt-dlp"
        engine.write_bytes(payload)
        (assets / "engine-bootstrap.py").write_text(bootstrap, encoding="utf-8")
        manifest = {
            "version": version,
            "sha256": checksum or hashlib.sha256(payload).hexdigest(),
            "source": f"https://github.com/yt-dlp/yt-dlp/releases/download/{version}/yt-dlp",
        }
        (assets / "engine.json").write_text(json.dumps(manifest), encoding="utf-8")
        return Path(temporary.name)

    def test_repository_asset_is_pinned_and_valid(self):
        version, checksum = verify(Path(__file__).resolve().parent.parent)

        self.assertRegex(version, r"^\d{4}\.\d{2}\.\d{2}$")
        self.assertRegex(checksum, r"^[0-9a-f]{64}$")

    def test_checksum_mismatch_is_rejected(self):
        root = self.make_tree(checksum="0" * 64)

        with self.assertRaises(EngineAssetError):
            verify(root)

    def test_version_and_source_are_cross_checked(self):
        root = self.make_tree(version="2026/10/21")

        with self.assertRaises(EngineAssetError):
            verify(root)

    def test_bootstrap_markers_are_required(self):
        root = self.make_tree(bootstrap="print('tanpa penanda')")

        with self.assertRaises(EngineAssetError):
            verify(root)

    def test_missing_engine_is_rejected(self):
        root = self.make_tree()
        (root / "app" / "src" / "main" / "assets" / "yt-dlp").unlink()

        with self.assertRaises(EngineAssetError):
            verify(root)


if __name__ == "__main__":
    unittest.main()
