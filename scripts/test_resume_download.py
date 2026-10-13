"""Real pinned yt-dlp HTTP downloader, controlled disconnects and Range/validator responses."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import socket
import sys
import tempfile
import threading
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'app/src/main/assets/yt-dlp'))
from yt_dlp import YoutubeDL
from yt_dlp.downloader.http import HttpFD
from yt_dlp.downloader.dash import DashSegmentsFD
from yt_dlp.utils import DownloadError
from engine_bootstrap import install_resume_validation


class ResumeDownloadTest(unittest.TestCase):
    def setUp(self):
        install_resume_validation()
        self.temp = tempfile.TemporaryDirectory()
        self.target = Path(self.temp.name) / 'media.bin'
        self.payload = bytes(range(256)) * 8192
        self.etag = '"version-1"'
        self.reported_size = None
        self.supports_range = True
        self.disconnect = True
        self.unavailable = False
        self.cut_path = None
        self.requests = []
        self.messages = []
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *_args):
                pass

            def do_HEAD(self):
                self.send_response(503 if owner.unavailable else 200)
                self.send_header('Content-Length', str(len(owner.payload)))
                if owner.etag:
                    self.send_header('ETag', owner.etag)
                self.end_headers()

            def do_GET(self):
                ranged = self.headers.get('Range')
                owner.requests.append((self.path, ranged))
                if owner.unavailable:
                    self.send_response(503); self.end_headers(); return
                start, end = 0, len(owner.payload) - 1
                partial = bool(ranged and owner.supports_range and self.headers.get('If-Range', owner.etag) == owner.etag)
                if partial:
                    value = ranged.removeprefix('bytes=')
                    first, last = value.split('-')
                    start = int(first); end = int(last) if last else end
                    end = min(end, len(owner.payload) - 1)
                body = owner.payload[start:end + 1]
                self.send_response(206 if partial else 200)
                if partial:
                    self.send_header('Content-Range', f'bytes {start}-{end}/{owner.reported_size or len(owner.payload)}')
                self.send_header('Content-Length', str(len(body)))
                if owner.etag:
                    self.send_header('ETag', owner.etag)
                self.end_headers()
                try:
                    if owner.disconnect and not ranged and (owner.cut_path is None or owner.cut_path in self.path):
                        owner.disconnect = False
                        self.wfile.write(body[:524288]); self.wfile.flush()
                        self.connection.shutdown(socket.SHUT_RDWR)
                    else:
                        self.wfile.write(body)
                except (BrokenPipeError, ConnectionResetError):
                    pass

        self.server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True); self.thread.start()
        self.url = f'http://127.0.0.1:{self.server.server_port}/song?token=old'

    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join(); self.temp.cleanup()

    def download(self, format_id='one', url=None):
        class Logger:
            def debug(_self, msg): self.messages.append(msg)
            def warning(_self, msg): self.messages.append(msg)
            def error(_self, msg): self.messages.append(msg)
        with YoutubeDL({'quiet': True, 'no_warnings': True, 'logger': Logger(), 'retries': 0, 'buffersize': 65536}) as ydl:
            fd = HttpFD(ydl, ydl.params)
            return fd.download(str(self.target), {'id': 'song-id', 'extractor_key': 'Fixture',
                'format_id': format_id, 'ext': 'bin', 'url': url or self.url,
                'filesize': len(self.payload), 'protocol': 'http'})

    def interrupt(self):
        try:
            self.download()
        except DownloadError:
            pass
        part = Path(str(self.target) + '.part')
        self.assertTrue(part.is_file())
        self.assertGreater(part.stat().st_size, 0)
        self.assertLess(part.stat().st_size, len(self.payload))
        return part

    def test_range_resumes_without_redownloading_prefix_and_rotates_signed_url(self):
        part = self.interrupt(); size = part.stat().st_size
        self.requests.clear()
        self.download(url=self.url.replace('old', 'new'))
        self.assertEqual(self.target.read_bytes(), self.payload)
        self.assertTrue(any(r == f'bytes={size}-' for _, r in self.requests), self.requests)
        self.assertFalse(any(r is None for _, r in self.requests), self.requests)
        self.assertTrue(all('token=new' in path for path, _ in self.requests))

    def test_unsupported_range_restarts_safely_without_duplicate_bytes(self):
        self.interrupt(); self.supports_range = False; self.requests.clear()
        self.download()
        self.assertEqual(self.target.read_bytes(), self.payload)
        self.assertTrue(any(r is None for _, r in self.requests))
        self.assertTrue(any('Server tidak mendukung' in m for m in self.messages))

    def test_changed_etag_same_size_restarts_instead_of_appending(self):
        self.interrupt(); self.payload = b'Z' * len(self.payload); self.etag = '"version-2"'
        self.download()
        self.assertEqual(self.target.read_bytes(), self.payload)
        self.assertTrue(any('mulai ulang' in m for m in self.messages))

    def test_changed_prefix_without_validator_restarts(self):
        self.etag = None
        self.interrupt(); self.payload = b'Z' * len(self.payload)
        self.download()
        self.assertEqual(self.target.read_bytes(), self.payload)
        self.assertTrue(any('Isi parsial berbeda' in m for m in self.messages))

    def test_content_range_size_change_restarts(self):
        self.interrupt(); self.reported_size = len(self.payload) + 1
        self.download()
        self.assertEqual(self.target.read_bytes(), self.payload)
        self.assertTrue(any('Ukuran berkas server berubah' in m for m in self.messages))

    def test_changed_format_and_unjournaled_part_restart(self):
        self.interrupt(); self.requests.clear(); self.download(format_id='two')
        self.assertEqual(self.target.read_bytes(), self.payload)
        self.assertFalse(any(r for _, r in self.requests))
        self.target.unlink(); Path(str(self.target) + '.tubeflow.json').unlink()
        Path(str(self.target) + '.part').write_bytes(b'wrong')
        self.download()
        self.assertEqual(self.target.read_bytes(), self.payload)

    def test_preflight_network_failure_preserves_partial(self):
        part = self.interrupt(); before = part.read_bytes(); self.unavailable = True
        with self.assertRaises(DownloadError): self.download()
        self.assertEqual(part.read_bytes(), before)
        self.assertTrue(any('dipertahankan' in m for m in self.messages))

    def test_fragment_checkpoint_resumes_only_unfinished_fragments(self):
        self.cut_path = '/second'
        target = Path(self.temp.name) / 'media-fragment.bin'
        info = {'id': 'song-id', 'extractor_key': 'Fixture', 'format_id': 'dash', 'ext': 'bin',
                'protocol': 'http_dash_segments', 'fragments': [
                    {'url': self.url.replace('/song', '/first')},
                    {'url': self.url.replace('/song', '/second')}], 'url': self.url}
        def attempt():
            with YoutubeDL({'quiet': True, 'no_warnings': True, 'retries': 0, 'fragment_retries': 0,
                            'skip_unavailable_fragments': False, 'buffersize': 65536}) as ydl:
                return DashSegmentsFD(ydl, ydl.params).download(str(target), info)
        try:
            attempt()
        except DownloadError:
            pass
        checkpoint = Path(str(target) + '.ytdl')
        self.assertTrue(checkpoint.exists())
        self.assertEqual(json.loads(checkpoint.read_text())['downloader']['current_fragment']['index'], 1)
        self.requests.clear()
        attempt()
        self.assertEqual(target.read_bytes(), self.payload + self.payload)
        self.assertFalse(any('/first' in path for path, _ in self.requests), self.requests)

    def test_existing_complete_file_is_not_requested_again(self):
        self.disconnect = False; self.download(); self.requests.clear(); self.download()
        self.assertEqual(self.target.read_bytes(), self.payload)
        self.assertFalse(self.requests)


if __name__ == '__main__':
    unittest.main()
