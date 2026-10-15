"""Exercise the pinned metadata parser AND FFmpeg tag generator, including uploader fallback."""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'app/src/main/assets/yt-dlp'))
from yt_dlp import YoutubeDL, parse_options
from yt_dlp.postprocessor.metadataparser import MetadataParserPP
from yt_dlp.postprocessor.ffmpeg import FFmpegMetadataPP


class MetadataTagsTest(unittest.TestCase):
    def embedded_tags(self, commands, info):
        options = parse_options(commands).ydl_opts
        with YoutubeDL({'quiet': True, 'no_warnings': True}) as ydl:
            for pp in options['postprocessors']:
                if pp['key'] == 'MetadataParser':
                    MetadataParserPP(ydl, pp['actions']).run(info)
            opts = list(FFmpegMetadataPP(ydl)._get_metadata_opts(info))
            return dict(value.split('=', 1) for flag, value in opts if flag == '-metadata')

    def test_unknown_artist_does_not_become_uploader_in_audio_tags(self):
        tags = self.embedded_tags([
            '--parse-metadata', '%(artist|)s:(?s)^(?P<meta_artist>.*)$',
            '--parse-metadata', 'TubeFlow=Maman Fvndy Rmx:(?s)^TubeFlow=(?P<meta_remixer>.*)$',
        ], {'title': 'DJ JAUH KO PERGI', 'uploader': 'Maman Fvndy Rmx'})
        self.assertEqual(tags['artist'], '')
        self.assertEqual(tags['remixer'], 'Maman Fvndy Rmx')

    def test_snapshot_title_overrides_extractor_track_and_artist(self):
        tags = self.embedded_tags([
            '--parse-metadata', 'TubeFlow=PINGAL (Live):(?s)^TubeFlow=(?P<meta_title>.*)$',
            '--parse-metadata', 'TubeFlow=DENNY CAKNAN:(?s)^TubeFlow=(?P<meta_artist>.*)$',
        ], {'title': 'Noisy original', 'track': 'Original track without version', 'artist': 'Wrong artist', 'uploader': 'DC MUSIK'})
        self.assertEqual(tags['title'], 'PINGAL (Live)')
        self.assertEqual(tags['artist'], 'DENNY CAKNAN')

    def test_late_platform_artist_survives_when_initial_snapshot_artist_was_empty(self):
        tags = self.embedded_tags([
            '--parse-metadata', '%(artist|)s:(?s)^(?P<meta_artist>.*)$',
        ], {'title': 'Song', 'artist': 'Real Artist', 'uploader': 'Different Uploader'})
        self.assertEqual(tags['artist'], 'Real Artist')

    def test_colon_percent_and_empty_fields_survive_actual_cli_parser(self):
        tags = self.embedded_tags([
            '--parse-metadata', r'TubeFlow=Love\: 100%%:(?s)^TubeFlow=(?P<meta_title>.*)$',
            '--parse-metadata', '%(artist|)s:(?s)^(?P<meta_artist>.*)$',
        ], {'title': 'source', 'creator': 'Creator fallback'})
        self.assertEqual(tags['title'], 'Love: 100%')
        self.assertEqual(tags['artist'], '')


if __name__ == '__main__':
    unittest.main()
