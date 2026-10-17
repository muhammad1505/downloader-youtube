"""Exercise actual migration/queries with existing rows, replacement and conflicting references."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
MODELS = (ROOT / 'app/src/main/java/id/local/tubeflow/core/Models.kt').read_text()
PERSONAL = (ROOT / 'app/src/main/java/id/local/tubeflow/core/PersonalLibrary.kt').read_text()
MEDIA = (ROOT / 'app/src/main/java/id/local/tubeflow/core/SmartMedia.kt').read_text()


class PersonalLibraryTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.execute('PRAGMA foreign_keys=ON')
        schema = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/9.json').read_text())['database']
        for entity in schema['entities']:
            self.db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            for index in entity.get('indices', []):
                self.db.execute(index['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        self.media('existing')
        section = MODELS.split('private val MIGRATION_9_10', 1)[1].split('private fun createMediaTables', 1)[0]
        for sql in re.findall(r'db.execSQL\("([^\n]+)"\)', section):
            self.db.execute(json.loads('"' + sql + '"'))
        self.query = re.search(r'@Query\("""(.*?)"""\)', MEDIA, re.S).group(1)

    def tearDown(self):
        self.db.close()

    def media(self, uri, **changes):
        row = {col[1]: (None if not col[3] else (0 if col[2] == 'INTEGER' else '')) for col in self.db.execute('PRAGMA table_info(media_items)')}
        row.update(uri=uri, kind='AUDIO', title='Existing song', displayName='Song.mp3', folder='Download/TubeFlow', mime='audio/mpeg', availability='AVAILABLE')
        row.update(changes)
        self.db.execute(f"INSERT OR REPLACE INTO media_items ({','.join(row)}) VALUES ({','.join('?' for _ in row)})", tuple(row.values()))

    def playlist(self, id='test'):
        self.db.execute('INSERT INTO personal_playlists VALUES (?,?,0)', (id, 'For work'))
        return id

    def query_named(self, name):
        match = re.search(r'@Query\("([^\n]+)"\) suspend fun ' + name + r'\(', PERSONAL)
        return json.loads('"' + match.group(1) + '"')

    def test_migration_preserves_catalogue_and_matches_schema(self):
        self.assertEqual('Existing song', self.db.execute("SELECT title FROM media_items WHERE uri='existing'").fetchone()[0])
        schema = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/10.json').read_text())['database']
        reference = sqlite3.connect(':memory:')
        for entity in schema['entities']:
            table = entity['tableName']
            reference.execute(entity['createSql'].replace('${TABLE_NAME}', table))
            self.assertEqual(reference.execute(f'PRAGMA table_info({table})').fetchall(), self.db.execute(f'PRAGMA table_info({table})').fetchall())
            self.assertEqual(reference.execute(f'PRAGMA foreign_key_list({table})').fetchall(), self.db.execute(f'PRAGMA foreign_key_list({table})').fetchall())
        reference.close()
        self.assertEqual([], self.db.execute('PRAGMA foreign_key_check').fetchall())

    def test_scan_replace_keeps_membership_and_delete_playlist_keeps_media(self):
        self.playlist()
        self.db.execute("INSERT INTO playlist_members VALUES('test','existing',0)")
        self.media('existing', title='Refreshed metadata')
        self.assertEqual(1, self.db.execute('SELECT COUNT(*) FROM playlist_members').fetchone()[0])
        self.db.execute("DELETE FROM personal_playlists WHERE id='test'")
        self.assertEqual(0, self.db.execute('SELECT COUNT(*) FROM playlist_members').fetchone()[0])
        self.assertEqual(1, self.db.execute('SELECT COUNT(*) FROM media_items').fetchone()[0])

    def test_playlist_filters_before_limit_and_preserves_saved_order(self):
        self.playlist()
        for n in range(125):
            self.media(f'new{n}', addedAt=n + 1)
        self.db.execute("INSERT INTO playlist_members VALUES('test','new1',0)")
        self.db.execute("INSERT INTO playlist_members VALUES('test','existing',1)")
        params = dict(kind='AUDIO', folder='', artist='', album='', search='%', sort='recent', limit=100, playlistId='test')
        self.assertEqual(['new1', 'existing'], [row[0] for row in self.db.execute(self.query, params)])
        params['playlistId'] = 'favorites'
        self.assertEqual([], self.db.execute(self.query, params).fetchall())

    def test_explicit_playlist_playback_can_load_past_the_visible_page(self):
        self.playlist()
        for n in range(125):
            uri = f'song{n}'
            self.media(uri)
            self.db.execute('INSERT INTO playlist_members VALUES(?,?,?)', ('test', uri, n))
        params = dict(kind='AUDIO', folder='', artist='', album='', search='%', sort='recent', limit=100, playlistId='test')
        self.assertEqual(100, len(self.db.execute(self.query, params).fetchall()))
        params['limit'] = 3000
        result = self.db.execute(self.query, params).fetchall()
        self.assertEqual(125, len(result))
        self.assertEqual('song124', result[-1][0])

    def test_capacity_uses_current_members_not_historical_positions(self):
        self.playlist()
        self.db.execute("INSERT INTO playlist_members VALUES('test','existing',3500)")
        self.assertEqual(1, self.db.execute(self.query_named('count'), dict(id='test')).fetchone()[0])
        self.assertTrue(self.db.execute(self.query_named('contains'), dict(id='test', uri='existing')).fetchone()[0])
        self.assertEqual(3501, self.db.execute(self.query_named('nextPosition'), dict(id='test')).fetchone()[0])

    def test_relink_ignores_duplicate_destination_without_dropping_other_members(self):
        self.playlist()
        self.playlist('second')
        self.db.executemany('INSERT INTO playlist_members VALUES(?,?,?)', [('test','old',0), ('test','new',1), ('second','old',0)])
        self.db.execute(self.query_named('relink'), dict(before='old', next='new'))
        self.db.execute(self.query_named('removeMedia'), dict(uri='old'))
        self.assertEqual([('second','new',0),('test','new',1)], self.db.execute('SELECT * FROM playlist_members ORDER BY playlistId').fetchall())


if __name__ == '__main__':
    unittest.main()
