"""Verify radio snapshots against the packaged extractor, without YouTube traffic."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import uuid

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'app/src/main/assets/yt-dlp'))
from yt_dlp import YoutubeDL
from yt_dlp.extractor.youtube import YoutubeTabIE
from engine_bootstrap import install_mix_snapshot


def panel(count, offset=0):
    return {'contents': [{'playlistPanelVideoRenderer': {
        'videoId': f'{index:011d}', 'title': {'simpleText': f'Track {index}'},
        'lengthText': {'simpleText': '3:00'},
        'navigationEndpoint': {'watchEndpoint': {'videoId': f'{index:011d}', 'index': index}},
    }} for index in range(offset, offset + count)]}


class MixSnapshotTest(unittest.TestCase):
    def setUp(self):
        original = YoutubeTabIE._extract_inline_playlist
        self.addCleanup(setattr, YoutubeTabIE, '_extract_inline_playlist', original)
        install_mix_snapshot()
        self.ydl = YoutubeDL({'quiet': True, 'no_warnings': True})
        self.addCleanup(self.ydl.close)
        self.extractor = YoutubeTabIE(self.ydl)

    def snapshot(self, count):
        with patch.object(self.extractor, '_extract_response', side_effect=AssertionError('Fetched continuation')):
            return list(self.extractor._extract_inline_playlist(panel(count), 'RD3J1Opi7MVHw', {}, {}))

    def test_initial_25_entry_mix_does_not_fetch_next_page(self):
        entries = self.snapshot(25)
        self.assertEqual([entry['id'] for entry in entries], [f'{index:011d}' for index in range(25)])
        self.assertEqual(entries[0]['title'], 'Track 0')
        self.assertEqual(entries[0]['duration'], 180)

    def test_actual_panel_size_is_not_hardcoded_to_25(self):
        for count in (8, 50):
            with self.subTest(count=count):
                self.assertEqual(len(self.snapshot(count)), count)

    def test_empty_panel_remains_empty(self):
        self.assertEqual(self.snapshot(0), [])

    def test_non_radio_inline_playlist_retains_upstream_pagination(self):
        def response(contents):
            return {'contents': {'twoColumnWatchNextResults': {'playlist': {'playlist': contents}}}}
        with patch.object(self.extractor, 'generate_api_headers', return_value={}), \
                patch.object(self.extractor, '_extract_response', side_effect=[response(panel(25, 25)), response(panel(0))]) as continuation:
            entries = list(self.extractor._extract_inline_playlist(panel(25), 'PLabc123', {}, {}))
        self.assertEqual(len(entries), 50)
        self.assertEqual(continuation.call_count, 2)

    def test_bootstrap_consumes_snapshot_flag_with_or_without_cover_extension(self):
        manifest = json.loads((ROOT / 'app/src/main/assets/engine.json').read_text())
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            entry = directory / 'yt-dlp'
            shutil.copyfile(ROOT / 'app/src/main/assets/engine-bootstrap.py', entry)
            shutil.copyfile(ROOT / 'app/src/main/assets/yt-dlp', directory / 'yt-dlp-original')
            for flags in (['--tubeflow-mix-snapshot'], ['--tubeflow-mix-snapshot', '--tubeflow-compress-cover']):
                with self.subTest(flags=flags):
                    control = directory / 'process.json'
                    result = subprocess.run([sys.executable, str(entry), '--tubeflow-process-file', str(control),
                                             '--tubeflow-process-token', uuid.uuid4().hex, *flags, '--version'],
                                            capture_output=True, text=True, timeout=20)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    self.assertEqual(result.stdout.strip(), manifest['version'])
                    self.assertFalse(control.exists())


if __name__ == '__main__':
    unittest.main()
