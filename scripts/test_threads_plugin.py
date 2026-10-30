"""Verify the pinned Threads extractor loads in TubeFlow's bundled yt-dlp zipapp."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
ENGINE = ASSETS / "yt-dlp"
PLUGIN = ASSETS / "yt_dlp_plugins" / "extractor" / "threads.py"
EXPECTED_SHA256 = "c28e410b69a0c2377c8530b36f6dca4b973484855b42e281846b97b3305b28ba"


class ThreadsPluginTest(unittest.TestCase):
    def test_plugin_source_is_pinned(self):
        self.assertEqual(hashlib.sha256(PLUGIN.read_bytes()).hexdigest(), EXPECTED_SHA256)

    def test_bundled_engine_loads_threads_urls_before_generic_extractor(self):
        with tempfile.TemporaryDirectory() as temporary:
            plugin_root = Path(temporary) / "plugins"
            package = plugin_root / "threads-plugin" / "yt_dlp_plugins" / "extractor"
            package.mkdir(parents=True)
            shutil.copyfile(PLUGIN, package / "threads.py")

            script = """
import json, sys
engine, plugin_root = sys.argv[1:]
sys.path.insert(0, engine)
import yt_dlp
from yt_dlp.globals import plugin_dirs
from yt_dlp.plugins import load_all_plugins
plugin_dirs.value = [plugin_root]
load_all_plugins()
from yt_dlp.extractor import gen_extractor_classes
classes = gen_extractor_classes()
urls = [
    "https://www.threads.com/@user/post/ABC123",
    "https://www.threads.net/@user/post/ABC123",
    "https://www.threads.com/t/ABC123",
    "https://threads.net/share/ABC123/",
]
print(json.dumps({url: next((ie.__name__ for ie in classes if ie.suitable(url)), None) for url in urls}))
print(json.dumps({"other": next((ie.__name__ for ie in classes if ie.suitable("https://example.com/video/1")), None)}))
"""
            result = subprocess.run(
                [sys.executable, "-c", script, str(ENGINE), str(plugin_root)],
                capture_output=True, text=True, check=True, timeout=30,
            )
            lines = result.stdout.splitlines()
            matches = json.loads(lines[-2])
            other = json.loads(lines[-1])["other"]
            self.assertEqual(set(matches.values()), {"ThreadsIE"})
            self.assertNotEqual(other, "ThreadsIE")


if __name__ == "__main__":
    unittest.main()
