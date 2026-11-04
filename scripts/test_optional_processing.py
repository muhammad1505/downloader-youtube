"""Fault injection against the pinned postprocessors; valid media survives only optional failures."""
from pathlib import Path
import importlib.util
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'app/src/main/assets/yt-dlp'))
spec = importlib.util.spec_from_file_location('optional_bootstrap', ROOT / 'scripts/engine_bootstrap.py')
bootstrap = importlib.util.module_from_spec(spec); spec.loader.exec_module(bootstrap)
from yt_dlp.postprocessor.ffmpeg import FFmpegMetadataPP, FFmpegThumbnailsConvertorPP
from yt_dlp.postprocessor.embedthumbnail import EmbedThumbnailPP
from yt_dlp.utils import PostProcessingError

class OptionalProcessingTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.media = Path(self.directory.name) / 'media.mp3'
        self.media.write_bytes(b'complete media stream')
        self.saved = [(cls, cls.run) for cls in (FFmpegMetadataPP, FFmpegThumbnailsConvertorPP, EmbedThumbnailPP)]
        self.info = {'filepath': str(self.media), 'ext': 'mp3'}

    def tearDown(self):
        for cls, run in self.saved: cls.run = run
        self.directory.cleanup()

    def install_failure(self, failure):
        FFmpegMetadataPP.run = failure
        bootstrap.install_optional_audio_processing()
        instance = FFmpegMetadataPP(None)
        instance.get_metadata_object = lambda filename: {}
        instance.to_screen = lambda message: self.messages.append(message)
        self.messages = []
        return instance

    def test_optional_failure_preserves_original_and_emits_no_exception_url(self):
        def fail(instance, info): raise PostProcessingError('secret https://host/?token=private')
        instance = self.install_failure(fail)
        self.assertEqual(([], self.info), instance.run(self.info))
        self.assertEqual(b'complete media stream', self.media.read_bytes())
        self.assertEqual(1, len(self.messages)); self.assertNotIn('token', self.messages[0])

    def test_missing_empty_or_changed_original_is_not_declared_valid(self):
        for action in ('delete','truncate','change'):
            with self.subTest(action=action):
                self.media.write_bytes(b'complete media stream')
                def fail(instance, info):
                    if action == 'delete': self.media.unlink()
                    else: self.media.write_bytes(b'' if action == 'truncate' else b'changed')
                    raise PostProcessingError('failed')
                instance = self.install_failure(fail)
                with self.assertRaises(PostProcessingError): instance.run(self.info)
                self.assertEqual([], self.messages)

    def test_unrelated_runtime_failure_is_not_suppressed(self):
        def fail(instance, info): raise RuntimeError('unexpected')
        instance = self.install_failure(fail)
        with self.assertRaises(RuntimeError): instance.run(self.info)

    def test_success_keeps_original_return_and_wrapper_is_idempotent(self):
        def success(instance, info): return ['temporary'], info
        instance = self.install_failure(success)
        wrapped = FFmpegMetadataPP.run
        bootstrap.install_optional_audio_processing()
        self.assertIs(wrapped, FFmpegMetadataPP.run)
        self.assertEqual((['temporary'], self.info), instance.run(self.info))


class AudioTagPolicyTest(unittest.TestCase):
    def test_existing_tags_survive_missing_source_music_fields(self):
        info = dict(title='Upload caption', meta_artist='', upload_date='20261013')
        bootstrap.prepare_audio_tags(info, {'streams':[{'codec_type':'audio','tags':{'TITLE':'Song','ARTIST':'Performer','ALBUM':'Record','DATE':'1999'}}]})
        self.assertEqual(('Song','Performer','Record','1999'), tuple(info[k] for k in ('meta_title','meta_artist','meta_album','meta_date')))

    def test_explicit_music_fields_take_priority_over_native_tags(self):
        info = dict(track='Verified Song', meta_title='Verified Song | Live', artist='Verified Artist', release_year=2001, genre='Rock')
        bootstrap.prepare_audio_tags(info, {'format':{'tags':{'title':'Old Song','artist':'Old Artist','date':'1999','genre':'Pop'}}})
        self.assertEqual(('Verified Song | Live','Verified Artist','2001','Rock'), tuple(info[k] for k in ('meta_title','meta_artist','meta_date','meta_genre')))

    def test_upload_information_does_not_become_music_metadata(self):
        info = dict(title='Song', uploader='Channel', creator='Uploader', upload_date='20120513', categories=['Music'], tags=['viral'], series='Playlist', webpage_url='https://host/?token=secret', description='Promotion')
        bootstrap.prepare_audio_tags(info, {'streams':[{'codec_type':'video','tags':{'artist':'Cover image'}}]})
        for key in ('artist','date','album','purl','comment','description'):
            self.assertEqual('', info['meta_'+key])
        # Jangan menulis genre kosong: yt-dlp berhak memakai fallback kategori/tag.
        self.assertNotIn('meta_genre', info)

    def test_empty_genre_leaves_room_for_downloader_fallback(self):
        info = dict(title='Song', genre='', meta_genre='', categories=['Music'])
        bootstrap.prepare_audio_tags(info, {'format': {'tags': {}}})
        self.assertNotIn('meta_genre', info)

    def test_failed_probe_leaves_original_tags_untouched(self):
        saved = FFmpegMetadataPP.run
        try:
            bootstrap.install_optional_audio_processing()
            instance = FFmpegMetadataPP(None)
            instance.get_metadata_object = lambda filename: (_ for _ in ()).throw(PostProcessingError('probe failed'))
            messages=[]; instance.to_screen=messages.append
            with tempfile.TemporaryDirectory() as directory:
                audio=Path(directory)/'media.mp3'; audio.write_bytes(b'complete audio')
                info={'filepath':str(audio), 'meta_artist':''}
                self.assertEqual(([],info), instance.run(info))
                self.assertEqual(b'complete audio',audio.read_bytes())
                self.assertEqual(1,len(messages))
        finally:
            FFmpegMetadataPP.run = saved


class NativeStreamProcessingTest(unittest.TestCase):
    def test_metadata_and_unsupported_artwork_preserve_native_opus_packets(self):
        import subprocess
        from yt_dlp import YoutubeDL
        import yt_dlp.postprocessor.embedthumbnail as thumbnail
        saved = [(cls, cls.run) for cls in (FFmpegMetadataPP, FFmpegThumbnailsConvertorPP, EmbedThumbnailPP)]
        try:
            with tempfile.TemporaryDirectory() as directory:
                audio = Path(directory) / 'media.opus'; cover = Path(directory) / 'cover.jpg'
                subprocess.run(['ffmpeg','-v','error','-f','lavfi','-i','sine=frequency=440:duration=2','-c:a','libopus',str(audio)],check=True)
                subprocess.run(['ffmpeg','-v','error','-f','lavfi','-i','color=c=blue:s=32x32','-frames:v','1','-update','1',str(cover)],check=True)
                def packet_hash():
                    return subprocess.check_output(['ffmpeg','-v','error','-i',str(audio),'-map','0:a','-c','copy','-f','hash','-hash','sha256','-'])
                before = packet_hash()
                info = dict(filepath=str(audio), ext='opus', title='Native Song', artist='Known Artist', upload_date='20120513', thumbnails=[dict(filepath=str(cover), id='cover')])
                with patch.object(thumbnail, 'mutagen', None), YoutubeDL(dict(quiet=True, no_warnings=True)) as downloader:
                    bootstrap.install_optional_audio_processing()
                    FFmpegMetadataPP(downloader, add_metadata=True).run(info)
                    EmbedThumbnailPP(downloader, already_have_thumbnail=True).run(info)
                self.assertEqual(before, packet_hash())
                tags = __import__('json').loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','stream_tags=title,artist,date','-of','json',str(audio)]))
                self.assertEqual('Native Song', tags['streams'][0]['tags']['title'])
                self.assertEqual('Known Artist', tags['streams'][0]['tags']['artist'])
                self.assertNotIn('date', tags['streams'][0]['tags'])
        finally:
            for cls, run in saved: cls.run = run

if __name__ == '__main__': unittest.main()
