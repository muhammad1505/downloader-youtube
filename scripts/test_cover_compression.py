"""Exercise the packaged cover processor with the pinned yt-dlp and real FFmpeg."""
import json
from pathlib import Path
import random
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zlib

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'app/src/main/assets/yt-dlp'))
from yt_dlp import YoutubeDL
from yt_dlp.postprocessor.ffmpeg import FFmpegThumbnailsConvertorPP
from engine_bootstrap import install_cover_compression


def png(path, width, height):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    rng = random.Random(42)
    data = b''.join(b'\0' + rng.randbytes(width * 3) for _ in range(height))
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(data)) + chunk(b'IEND', b''))


@unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'Requires host FFmpeg')
class CoverCompressionTest(unittest.TestCase):
    def setUp(self):
        install_cover_compression()
        self.temporary = tempfile.TemporaryDirectory()
        self.directory = Path(self.temporary.name)
        self.ydl = YoutubeDL({'quiet': True, 'no_warnings': True, 'postprocessor_args': {'ThumbnailsConvertor+ffmpeg_o': ['-threads', '1']}})
        self.processor = FFmpegThumbnailsConvertorPP(self.ydl, format='jpg')

    def tearDown(self):
        self.ydl.close()
        self.temporary.cleanup()

    def run_cover(self, source):
        return self.processor.run({'ext': 'mp3', 'thumbnails': [{'filepath': str(source)}], '__files_to_move': {str(source): str(source)}})

    def probe(self, path):
        result = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'stream=codec_name,width,height', '-of', 'json', str(path)], check=True, capture_output=True, text=True)
        return json.loads(result.stdout)['streams'][0]

    def test_large_png_becomes_smaller_jpeg_with_preserved_aspect_ratio(self):
        source = self.directory / 'media.png'
        png(source, 1600, 900)
        deleted, info = self.run_cover(source)
        target = Path(info['thumbnails'][0]['filepath'])
        self.assertEqual(target.suffix, '.jpg')
        self.assertEqual(self.probe(target), {'codec_name': 'mjpeg', 'width': 800, 'height': 450})
        self.assertLess(target.stat().st_size, source.stat().st_size // 4)
        self.assertEqual(deleted, [str(source)])
        self.assertEqual(info['__files_to_move'][str(target)], str(target))

    def test_small_image_is_not_upscaled(self):
        source = self.directory / 'media.png'
        png(source, 120, 80)
        _, info = self.run_cover(source)
        result = self.probe(info['thumbnails'][0]['filepath'])
        self.assertEqual((result['width'], result['height']), (120, 80))

    def test_jpeg_source_is_resized_and_not_marked_for_deletion(self):
        original = self.directory / 'source.png'
        source = self.directory / 'media.jpg'
        png(original, 1200, 1200)
        subprocess.run(['ffmpeg', '-v', 'error', '-i', str(original), '-frames:v', '1', '-threads', '1', str(source)], check=True, capture_output=True)
        deleted, info = self.run_cover(source)
        self.assertEqual(deleted, [])
        self.assertEqual(info['thumbnails'][0]['filepath'], str(source))
        self.assertEqual(self.probe(source)['width'], 800)

    def test_failed_conversion_keeps_original_and_removes_temporary(self):
        source = self.directory / 'media.jpg'
        original = b'original cover'
        source.write_bytes(original)
        def fail(*args):
            (self.directory / 'media.tubeflow-cover.jpg').write_bytes(b'partial')
            raise RuntimeError('storage full')
        with patch.object(self.processor, 'real_run_ffmpeg', side_effect=fail):
            with self.assertRaisesRegex(RuntimeError, 'storage full'):
                self.run_cover(source)
        self.assertEqual(source.read_bytes(), original)
        self.assertFalse((self.directory / 'media.tubeflow-cover.jpg').exists())

    def test_packaged_pipeline_emits_phases_and_embeds_jpeg_without_changing_audio(self):
        source = self.directory / 'source.mp3'
        subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', 'sine=frequency=440:duration=2', '-c:a', 'libmp3lame', '-b:a', '128k', str(source)], check=True, capture_output=True)
        cover = self.directory / 'cover.png'
        png(cover, 1600, 900)
        info = {'id': 'fixture', 'title': 'Fixture', 'extractor': 'generic',
                'webpage_url': source.as_uri(), 'url': source.as_uri(), 'ext': 'mp3',
                'vcodec': 'none', 'acodec': 'mp3', 'format_id': 'fixture-audio',
                'thumbnail': cover.as_uri()}
        fixture = self.directory / 'info.json'
        fixture.write_text(json.dumps(info))
        entry = self.directory / 'yt-dlp'
        shutil.copyfile(ROOT / 'app/src/main/assets/engine-bootstrap.py', entry)
        shutil.copyfile(ROOT / 'app/src/main/assets/yt-dlp', self.directory / 'yt-dlp-original')
        # Read the actual Kotlin templates so CLI and application stay in agreement.
        import re
        kotlin = (ROOT / 'app/src/main/java/id/local/tubeflow/core/DownloadProgress.kt').read_text()
        templates = re.findall(r'const val (?:DOWNLOAD|PROCESS)_TEMPLATE = "([^"]+)"', kotlin)
        arguments = [sys.executable, str(entry), '--tubeflow-process-file', str(self.directory / 'control.json'),
                     '--tubeflow-process-token', 'a' * 32, '--tubeflow-compress-cover',
                     '--ignore-config', '--enable-file-urls', '--load-info-json', str(fixture),
                     '--no-colors', '--newline', '--progress', '--write-thumbnail',
                     '--convert-thumbnails', 'jpg', '--embed-thumbnail',
                     '--ppa', 'ThumbnailsConvertor+ffmpeg_o:-threads 1',
                     '-o', str(self.directory / 'media.%(ext)s'),
                     '--print-to-file', 'after_move:filepath', str(self.directory / 'result.txt')]
        for template in templates:
            arguments.extend(['--progress-template', template])
        result = subprocess.run(arguments, capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('TUBEFLOW_DOWNLOAD|fixture-audio|finished|', result.stdout)
        self.assertIn('TUBEFLOW_PROCESS|started|ThumbnailsConvertor', result.stdout)
        self.assertIn('TUBEFLOW_PROCESS|started|EmbedThumbnail', result.stdout)
        target = self.directory / 'media.mp3'
        streams = json.loads(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'stream=codec_name,width,height:stream_disposition=attached_pic', '-of', 'json', str(target)], check=True, capture_output=True, text=True).stdout)['streams']
        picture = next(stream for stream in streams if stream['disposition']['attached_pic'])
        self.assertEqual((picture['codec_name'], picture['width'], picture['height']), ('mjpeg', 800, 450))
        def audio_packets(path):
            return subprocess.run(['ffprobe', '-v', 'error', '-select_streams', 'a', '-show_packets', '-show_data_hash', 'sha256', '-show_entries', 'packet=data_hash', '-of', 'json', str(path)], check=True, capture_output=True, text=True).stdout
        self.assertEqual(audio_packets(source), audio_packets(target))

    def test_two_track_pipeline_reports_each_track_then_merge(self):
        video = self.directory / 'video.mp4'
        audio = self.directory / 'audio.m4a'
        subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', 'color=c=blue:s=160x90:d=1', '-c:v', 'libx264', '-threads', '1', '-an', str(video)], check=True, capture_output=True)
        subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', 'sine=duration=1', '-c:a', 'aac', str(audio)], check=True, capture_output=True)
        info = {'id': 'fixture', 'title': 'Two tracks', 'extractor': 'generic', 'webpage_url': video.as_uri(),
                'formats': [
                    {'url': audio.as_uri(), 'format_id': 'audio-track', 'ext': 'm4a', 'vcodec': 'none', 'acodec': 'aac'},
                    {'url': video.as_uri(), 'format_id': 'video-track', 'ext': 'mp4', 'vcodec': 'h264', 'acodec': 'none', 'width': 160, 'height': 90}]}
        fixture = self.directory / 'info.json'
        fixture.write_text(json.dumps(info))
        entry = self.directory / 'yt-dlp'
        shutil.copyfile(ROOT / 'app/src/main/assets/engine-bootstrap.py', entry)
        shutil.copyfile(ROOT / 'app/src/main/assets/yt-dlp', self.directory / 'yt-dlp-original')
        import re
        kotlin = (ROOT / 'app/src/main/java/id/local/tubeflow/core/DownloadProgress.kt').read_text()
        templates = re.findall(r'const val (?:DOWNLOAD|PROCESS)_TEMPLATE = "([^"]+)"', kotlin)
        command = [sys.executable, str(entry), '--tubeflow-process-file', str(self.directory / 'control.json'),
                   '--tubeflow-process-token', 'b' * 32, '--ignore-config', '--enable-file-urls',
                   '--load-info-json', str(fixture), '--no-colors', '--newline', '--progress',
                   '-f', 'bestvideo+bestaudio', '-o', str(self.directory / 'media.%(ext)s')]
        for template in templates:
            command.extend(['--progress-template', template])
        result = subprocess.run(command, capture_output=True, text=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        lines = result.stdout.splitlines()
        video_line = next(line for line in lines if line.startswith('TUBEFLOW_DOWNLOAD|video-track|finished|'))
        audio_line = next(line for line in lines if line.startswith('TUBEFLOW_DOWNLOAD|audio-track|finished|'))
        self.assertTrue(video_line.endswith('|h264'), video_line)
        self.assertTrue(audio_line.endswith('|none'), audio_line)
        self.assertIn('TUBEFLOW_PROCESS|started|Merger', lines)


if __name__ == '__main__':
    unittest.main()
