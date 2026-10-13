"""Run the actual Room 8->9 migration, catalogue queries and event triggers on SQLite."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / 'app/src/main/java/id/local/tubeflow/core/Models.kt').read_text()
MEDIA = (ROOT / 'app/src/main/java/id/local/tubeflow/core/SmartMedia.kt').read_text()

class MediaDatabaseTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.row_factory = sqlite3.Row
        old = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/8.json').read_text())['database']['entities'][0]
        self.db.execute(old['createSql'].replace('${TABLE_NAME}', old['tableName']))
        self.db.execute("INSERT INTO downloads(id,url,title,mode,format,quality,bitrate,state,progress,speed,eta,createdAt,outputUri) VALUES('old','https://example.com/media','Existing','AUDIO','mp3',0,128,'COMPLETED',100,'','',1,'content://media/external/file/1')")
        migration = SOURCE.split('private val MIGRATION_8_9', 1)[1].split('private fun createMediaTables', 1)[0]
        tables = SOURCE.split('private fun createMediaTables', 1)[1].split('private val eventCallback', 1)[0]
        for sql in re.findall(r'db.execSQL\("([^\n]+)"\)', migration + tables):
            self.db.execute(json.loads('"' + sql + '"'))
        triggers = SOURCE.split('private val eventCallback', 1)[1].split('@Volatile', 1)[0]
        for sql in re.findall(r'db.execSQL\("([^\n]+)"\)', triggers):
            self.db.execute(json.loads('"' + sql + '"'))
        self.queries = re.findall(r'@Query\("""(.*?)"""\)', MEDIA, re.S)

    def test_worker_sidecar_mutations_reject_replaced_generations(self):
        self.db.execute("UPDATE downloads SET generation=2,state='RUNNING',sidecarPending='new-pending',sidecarUris='new-sidecar',lyricStatus='new-status' WHERE id='old'")
        names = ('sidecarPendingOwned', 'sidecarsOwned', 'commitSidecarOwned', 'lyricStatusOwned')
        for name in names:
            pattern = r'@Query\("([^\n]+)"\) suspend fun ' + name + r'\('
            sql = json.loads('"' + re.search(pattern, SOURCE).group(1) + '"')
            params = dict(id='old', gen=1, uris='stale', line='stale', pending='stale', status='stale')
            self.assertEqual(0, self.db.execute(sql, params).rowcount, name)
        row = self.db.execute("SELECT sidecarPending,sidecarUris,lyricStatus FROM downloads WHERE id='old'").fetchone()
        self.assertEqual(('new-pending','new-sidecar','new-status'),tuple(row))

    def test_sidecar_commit_and_lyric_status_require_running_owner(self):
        for state in ('PAUSED','FAILED','CANCELLED','COMPLETED'):
            self.db.execute("UPDATE downloads SET generation=2,state=? WHERE id='old'",(state,))
            for name in ('commitSidecarOwned','lyricStatusOwned'):
                sql=json.loads('"'+re.search(r'@Query\("([^\n]+)"\) suspend fun '+name+r'\(', SOURCE).group(1)+'"')
                self.assertEqual(0,self.db.execute(sql,dict(id='old',gen=2,line='stale',pending='stale',status='stale')).rowcount)
        self.db.execute("UPDATE downloads SET state='RUNNING' WHERE id='old'")
        sql=json.loads('"'+re.search(r'@Query\("([^\n]+)"\) suspend fun commitSidecarOwned\(',SOURCE).group(1)+'"')
        self.assertEqual(1,self.db.execute(sql,dict(id='old',gen=2,line='sub|actual',pending=None)).rowcount)
        self.assertEqual(('sub|actual',None),tuple(self.db.execute("SELECT sidecarUris,sidecarPending FROM downloads WHERE id='old'").fetchone()))

    def tearDown(self):
        self.db.close()

    def media(self, uri, **values):
        row = {c['name']: (None if not c['notnull'] else (0 if c['type'] == 'INTEGER' else '')) for c in self.db.execute('PRAGMA table_info(media_items)')}
        row.update(uri=uri, kind='AUDIO', title='Song', displayName='Song.mp3', folder='Download/TubeFlow', mime='audio/mpeg', availability='AVAILABLE', metadataStatus='UNCHECKED')
        row.update(values)
        self.db.execute(f"INSERT OR REPLACE INTO media_items ({','.join(row)}) VALUES ({','.join('?' for _ in row)})", tuple(row.values()))

    def test_migration_preserves_download_and_matches_exported_schema(self):
        row = self.db.execute('SELECT * FROM downloads').fetchone()
        self.assertEqual('Existing', row['title'])
        self.assertEqual('content://media/external/file/1', row['outputUri'])
        self.assertEqual((1, 1, 1), tuple(row[k] for k in ('autoRename', 'autoTag', 'autoLibrary')))
        new = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/9.json').read_text())['database']['entities']
        expected = sqlite3.connect(':memory:')
        for entity in new:
            expected.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
            actual = [tuple(r)[1:6] for r in self.db.execute('PRAGMA table_info(' + entity['tableName'] + ')')]
            reference = [tuple(r)[1:6] for r in expected.execute('PRAGMA table_info(' + entity['tableName'] + ')')]
            self.assertEqual(sorted(reference), sorted(actual))
        expected.close()

    def test_counts_are_unique_uri_available_only_and_unknown_size_is_null(self):
        self.media('one', sizeBytes=100, artist='Artist', album='Album')
        self.media('one', sizeBytes=120, artist='Artist', album='Album')
        self.media('two', sizeBytes=None)
        self.media('video', kind='VIDEO', sizeBytes=200)
        self.media('gone', sizeBytes=999, availability='UNAVAILABLE')
        row = self.db.execute(self.queries[1]).fetchone()
        self.assertEqual((2, 1, 1, 120, 200, 1, 1, 1), tuple(row[k] for k in ('audio','video','unavailable','audioBytes','videoBytes','unknownSizes','artists','albums')))
        self.db.execute('DELETE FROM media_items'); self.media('unknown', sizeBytes=None)
        self.assertIsNone(self.db.execute(self.queries[1]).fetchone()['audioBytes'])

    def test_search_escapes_wildcards_and_filters_media_without_cross_category(self):
        self.media('one', title='100%_Song', sizeBytes=123, artist='Artist')
        self.media('two', title='100XXSong', sizeBytes=456, artist='Other')
        self.media('video', kind='VIDEO', title='100%_Song')
        params = dict(kind='AUDIO', folder='', artist='', album='', search=r'%100\%\_Song%', sort='size', limit=100)
        self.assertEqual(['one'], [r['uri'] for r in self.db.execute(self.queries[0], params)])
        params.update(search='%', artist='Other', limit=1)
        self.assertEqual(['two'], [r['uri'] for r in self.db.execute(self.queries[0], params)])

    def test_events_record_new_transitions_not_old_history_and_survive_history_clear(self):
        self.assertEqual(0, self.db.execute('SELECT count(*) FROM media_events').fetchone()[0])
        self.db.execute("UPDATE downloads SET state='QUEUED' WHERE id='old'")
        self.db.execute("UPDATE downloads SET state='RUNNING' WHERE id='old'")
        self.db.execute("UPDATE downloads SET state='FAILED' WHERE id='old'")
        self.db.execute("UPDATE downloads SET state='FAILED' WHERE id='old'")
        self.db.execute("UPDATE downloads SET state='QUEUED' WHERE id='old'")
        self.db.execute("UPDATE downloads SET state='COMPLETED' WHERE id='old'")
        self.assertEqual(['QUEUED','FAILED','QUEUED','COMPLETED'], [r[0] for r in self.db.execute('SELECT action FROM media_events ORDER BY id')])
        self.db.execute('DELETE FROM downloads')
        self.assertEqual(4, self.db.execute('SELECT count(*) FROM media_events').fetchone()[0])

    def test_catalogue_survives_clearing_history_and_rename_replaces_uri(self):
        self.media('one', jobId='old', sizeBytes=10, accessUri='content://provider/document/one')
        self.db.execute('DELETE FROM downloads')
        self.assertEqual(1, self.db.execute('SELECT count(*) FROM media_items').fetchone()[0])
        self.assertEqual('content://provider/document/one', self.db.execute('SELECT accessUri FROM media_items').fetchone()[0])
        self.media('renamed', jobId='old', sizeBytes=10)
        self.db.execute("DELETE FROM media_items WHERE uri='one'")
        self.assertEqual(1, self.db.execute(self.queries[1]).fetchone()['audio'])

if __name__ == '__main__':
    unittest.main()
