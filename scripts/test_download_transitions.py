"""Exercise actual Room schema and DAO SQL using host SQLite; no Android emulation."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]


class DownloadTransitionsTest(unittest.TestCase):
    def setUp(self):
        schema = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/6.json').read_text())
        self.db = sqlite3.connect(':memory:')
        self.db.row_factory = sqlite3.Row
        entity = schema['database']['entities'][0]
        self.db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        source = (ROOT / 'app/src/main/java/id/local/tubeflow/core/Models.kt').read_text()
        self.queries = dict((method, sql) for sql, method in re.findall(r'@Query\("([^"\n]+)"\)\s+(?:suspend\s+)?fun\s+(\w+)', source))
        self.db.execute('INSERT INTO downloads (id,url,title,mode,format,quality,bitrate,state,progress,speed,eta,createdAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)', ('job','https://youtu.be/test','Test','VIDEO','auto',0,0,'RUNNING',25,'','',1))

    def tearDown(self):
        self.db.close()

    def query(self, method, **parameters):
        # gen=0: baris segar punya generation 0; worker lama memakai generasinya sendiri (BUG-002).
        if method == 'progress':
            parameters.setdefault('phase', 'PREPARING')
        return self.db.execute(self.queries[method], dict(parameters, id='job', gen=parameters.get('gen', 0))).rowcount

    def row(self):
        return self.db.execute('SELECT * FROM downloads WHERE id=?', ('job',)).fetchone()

    def test_late_progress_never_revives_terminal_job(self):
        for state in ('CANCELLED', 'COMPLETED', 'FAILED'):
            with self.subTest(state=state):
                self.db.execute('UPDATE downloads SET state=? WHERE id=?', (state, 'job'))
                self.assertEqual(self.query('progress', state='RUNNING', progress=90, speed='late', eta='1s'), 0)
                self.assertEqual(self.row()['state'], state)

    def test_cancel_prevents_publication_commit(self):
        self.query('pending', uri='content://media/pending')
        self.query('cancel')
        self.assertEqual(self.query('finish', uri='content://media/pending', now=1), 0)
        self.assertIsNone(self.row()['outputUri'])
        self.assertEqual(self.row()['state'], 'CANCELLED')

    def test_completed_job_survives_late_cancel_failure(self):
        self.query('pending', uri='content://media/result')
        self.assertEqual(self.query('finish', uri='content://media/result', now=55), 1)
        # finish mencatat waktu publikasi sungguhan — label Riwayat tidak lagi memakai waktu antre (BUG-023).
        self.assertEqual(self.row()['completedAt'], 55)
        self.query('cancel')
        self.assertEqual(self.query('fail', message='late error'), 0)
        self.assertEqual(self.row()['state'], 'COMPLETED')
        self.assertEqual(self.row()['outputUri'], 'content://media/result')
        self.assertIsNone(self.row()['pendingUri'])

    def test_system_interruption_requeues_but_user_cancel_stays_terminal(self):
        self.query('interrupted')
        self.assertEqual(self.row()['state'], 'QUEUED')
        self.query('cancel')
        self.query('interrupted')
        self.assertEqual(self.row()['state'], 'CANCELLED')

    def test_interruption_keeps_same_owner_for_partial_resume(self):
        """Putus koneksi: QUEUED dengan generasi SAMA (tanpa bump) — pemilik boleh lanjut,
        jadi worker boleh menyimpan parsial untuk dilanjutkan dari .part (BUG-025)."""
        gen_before = self.row()['generation']
        self.assertEqual(self.query('interrupted'), 1)
        row = self.row()
        self.assertEqual(row['state'], 'QUEUED')
        self.assertEqual(row['generation'], gen_before)  # beda dengan resume yang selalu bump
        # Setelah jaringan pulih, pemilik generasi yang sama tetap bisa menulis progres.
        self.assertEqual(self.query('progress', state='RUNNING', progress=42, speed='', eta=''), 1)
        self.assertEqual(self.row()['state'], 'RUNNING')

    @staticmethod
    def migration_alters(source, name):
        """ALTER TABLE milik SATU blok migrasi — jangan tercampur migrasi lain (2 kolom vs 4 kolom)."""
        segment = next(
            part for part in re.split(r'(?=private val MIGRATION_)', source)
            if part.startswith('private val ' + name)
        )
        return re.findall(r'db\.execSQL\("(ALTER TABLE [^"]+)"\)', segment)

    def test_migration_1_2_alter_columns_match_schema_v2(self):
        """The hand-written migration must produce exactly the v2 schema Room expects."""
        v1 = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/1.json').read_text())
        v2 = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/2.json').read_text())
        source = (ROOT / 'app/src/main/java/id/local/tubeflow/core/Models.kt').read_text()
        alters = self.migration_alters(source, 'MIGRATION_1_2')
        self.assertTrue(alters, 'migration SQL not found in Models.kt')
        migrated = sqlite3.connect(':memory:')
        entity1 = v1['database']['entities'][0]
        migrated.execute(entity1['createSql'].replace('${TABLE_NAME}', entity1['tableName']))
        for statement in alters:
            migrated.execute(statement)
        expected = sqlite3.connect(':memory:')
        entity2 = v2['database']['entities'][0]
        expected.execute(entity2['createSql'].replace('${TABLE_NAME}', entity2['tableName']))
        columns = lambda conn: sorted(
            (row[1], row[2].upper(), bool(row[3]), row[4])
            for row in conn.execute('PRAGMA table_info(downloads)'))
        self.assertEqual(columns(migrated), columns(expected))
        migrated.close(); expected.close()

    def test_migration_2_3_alter_columns_match_schema_v3(self):
        """Migrasi 2→3 (generation/durationSeconds/lyricStatus/sidecarPending) harus tepat sama dengan schema v3 Room."""
        v2 = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/2.json').read_text())
        v3 = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/3.json').read_text())
        source = (ROOT / 'app/src/main/java/id/local/tubeflow/core/Models.kt').read_text()
        alters = self.migration_alters(source, 'MIGRATION_2_3')
        self.assertTrue(alters, 'MIGRATION_2_3 SQL not found in Models.kt')
        migrated = sqlite3.connect(':memory:')
        entity2 = v2['database']['entities'][0]
        migrated.execute(entity2['createSql'].replace('${TABLE_NAME}', entity2['tableName']))
        for statement in alters:
            migrated.execute(statement)
        expected = sqlite3.connect(':memory:')
        entity3 = v3['database']['entities'][0]
        expected.execute(entity3['createSql'].replace('${TABLE_NAME}', entity3['tableName']))
        columns = lambda conn: sorted(
            (row[1], row[2].upper(), bool(row[3]), row[4])
            for row in conn.execute('PRAGMA table_info(downloads)'))
        self.assertEqual(columns(migrated), columns(expected))
        migrated.close(); expected.close()

    def test_migration_3_4_preserves_history_and_matches_schema(self):
        source = (ROOT / 'app/src/main/java/id/local/tubeflow/core/Models.kt').read_text()
        migrated = sqlite3.connect(':memory:')
        old = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/3.json').read_text())['database']['entities'][0]
        migrated.execute(old['createSql'].replace('${TABLE_NAME}', old['tableName']))
        migrated.execute("INSERT INTO downloads (id,url,title,mode,format,quality,bitrate,state,progress,speed,eta,createdAt,outputUri) VALUES ('old','url','Old audio','AUDIO','mp3',0,128,'COMPLETED',100,'','',1,'content://audio')")
        for sql in self.migration_alters(source, 'MIGRATION_3_4'):
            migrated.execute(sql)
        # 3→4 harus menghasilkan persis schema v4 (bukan skema terkini — v5 menambah kolom BUG-023).
        v4 = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/4.json').read_text())['database']['entities'][0]
        expected = sqlite3.connect(':memory:')
        expected.execute(v4['createSql'].replace('${TABLE_NAME}', v4['tableName']))
        columns = lambda conn: sorted((r[1], r[2].upper(), bool(r[3]), r[4]) for r in conn.execute('PRAGMA table_info(downloads)'))
        self.assertEqual(columns(migrated), columns(expected))
        self.assertEqual(migrated.execute('SELECT state,outputUri,phase FROM downloads').fetchone(), ('COMPLETED', 'content://audio', 'PREPARING'))
        migrated.close(); expected.close()

    def test_migration_4_5_adds_completed_at_like_schema_v5(self):
        """Migrasi 4→5 (completedAt) harus tepat sama dengan schema v5 Room; riwayat lama tetap terbaca (BUG-023)."""
        source = (ROOT / 'app/src/main/java/id/local/tubeflow/core/Models.kt').read_text()
        alters = self.migration_alters(source, 'MIGRATION_4_5')
        self.assertTrue(alters, 'MIGRATION_4_5 SQL not found in Models.kt')
        migrated = sqlite3.connect(':memory:')
        v4 = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/4.json').read_text())['database']['entities'][0]
        migrated.execute(v4['createSql'].replace('${TABLE_NAME}', v4['tableName']))
        migrated.execute("INSERT INTO downloads (id,url,title,mode,format,quality,bitrate,state,progress,speed,eta,createdAt,outputUri) VALUES ('old','url','Old audio','AUDIO','mp3',0,128,'COMPLETED',100,'','',1,'content://audio')")
        for statement in alters:
            migrated.execute(statement)
        v5 = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/5.json').read_text())['database']['entities'][0]
        expected = sqlite3.connect(':memory:')
        expected.execute(v5['createSql'].replace('${TABLE_NAME}', v5['tableName']))
        columns = lambda conn: sorted((r[1], r[2].upper(), bool(r[3]), r[4]) for r in conn.execute('PRAGMA table_info(downloads)'))
        self.assertEqual(columns(migrated), columns(expected))
        # Baris lama terbaca; completedAt = 0 → UI Riwayat jatuh ke createdAt.
        self.assertEqual(migrated.execute('SELECT state,outputUri,completedAt FROM downloads').fetchone(), ('COMPLETED', 'content://audio', 0))
        migrated.close(); expected.close()

    def test_migration_5_6_adds_subfolder_like_schema_v6(self):
        """Migrasi 5→6 (subfolder per playlist/Mix) harus tepat sama dengan schema v6 Room; baris lama tanpa subfolder (kosong = folder utama)."""
        source = (ROOT / 'app/src/main/java/id/local/tubeflow/core/Models.kt').read_text()
        alters = self.migration_alters(source, 'MIGRATION_5_6')
        self.assertTrue(alters, 'MIGRATION_5_6 SQL not found in Models.kt')
        migrated = sqlite3.connect(':memory:')
        v5 = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/5.json').read_text())['database']['entities'][0]
        migrated.execute(v5['createSql'].replace('${TABLE_NAME}', v5['tableName']))
        migrated.execute("INSERT INTO downloads (id,url,title,mode,format,quality,bitrate,state,progress,speed,eta,createdAt,outputUri) VALUES ('old','url','Old mix','VIDEO','auto',0,0,'COMPLETED',100,'','',1,'content://video')")
        for statement in alters:
            migrated.execute(statement)
        v6 = json.loads((ROOT / 'app/schemas/id.local.tubeflow.core.DownloadDatabase/6.json').read_text())['database']['entities'][0]
        expected = sqlite3.connect(':memory:')
        expected.execute(v6['createSql'].replace('${TABLE_NAME}', v6['tableName']))
        columns = lambda conn: sorted((r[1], r[2].upper(), bool(r[3]), r[4]) for r in conn.execute('PRAGMA table_info(downloads)'))
        self.assertEqual(columns(migrated), columns(expected))
        # Baris lama tetap terbaca; subfolder '' → hasil lama tetap di folder utama.
        self.assertEqual(migrated.execute('SELECT state,outputUri,subfolder FROM downloads').fetchone(), ('COMPLETED', 'content://video', ''))
        migrated.close(); expected.close()

    def test_phase_transition_clears_transfer_metrics_and_completion(self):
        self.query('progress', state='RUNNING', progress=75, phase='AUDIO', speed='at 1MiB/s', eta='3s')
        self.query('progress', state='RUNNING', progress=-1, phase='PROCESSING', speed='', eta='')
        self.assertEqual((self.row()['phase'], self.row()['progress'], self.row()['speed'], self.row()['eta']), ('PROCESSING', -1, '', ''))
        self.assertEqual(self.row()['state'], 'RUNNING')
        self.query('progress', state='RUNNING', progress=100, phase='SAVING', speed='', eta='')
        self.assertEqual(self.row()['state'], 'RUNNING')
        self.query('finish', uri='content://audio', now=7)
        self.assertEqual((self.row()['state'], self.row()['progress'], self.row()['completedAt']), ('COMPLETED', 100, 7))

    def test_stale_generation_cannot_touch_resumed_job(self):
        """Worker lama (generasi 0) tak boleh menimpa job yang sudah dilanjutkan — BUG-002."""
        self.db.execute("UPDATE downloads SET state='QUEUED', generation=1")
        self.assertEqual(self.query('progress', state='RUNNING', progress=90, speed='late', eta='1s'), 0)
        self.assertEqual(self.query('finish', uri='content://media/late', now=2), 0)
        self.assertEqual(self.query('failOwned', gen=0, message='worker lama gagal'), 0)
        self.assertEqual(self.row()['state'], 'QUEUED')
        # Pemilik generasi baru tetap bisa menulis.
        self.assertEqual(self.query('progress', gen=1, state='QUEUED', progress=10, speed='', eta=''), 1)
        self.assertEqual(self.query('failOwned', gen=1, message='gagal milik sendiri'), 1)
        self.assertEqual(self.row()['state'], 'FAILED')

    def test_resume_bumps_generation(self):
        self.db.execute("UPDATE downloads SET state='PAUSED'")
        self.assertEqual(self.query('resume'), 1)
        self.assertEqual(self.row()['generation'], 1)
        self.assertEqual(self.row()['state'], 'QUEUED')

    def test_lyric_status_recorded_and_reset_on_revive(self):
        self.assertEqual(self.query('lyricStatus', status='tersimpan'), 1)
        self.assertEqual(self.row()['lyricStatus'], 'tersimpan')
        self.db.execute("UPDATE downloads SET state='FAILED'")
        self.db.execute(self.queries['revive'], dict(
            id='job', title='Revived', mode='AUDIO', format='mp3', quality=0, bitrate=320,
            subtitles='asli', embedSubs=0, thumbnail=0, embedThumb=1, now=98))
        self.assertEqual(self.row()['lyricStatus'], '')

    def test_new_option_columns_have_defaults(self):
        self.db.execute('INSERT INTO downloads (id,url,title,mode,format,quality,bitrate,state,progress,speed,eta,createdAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)', ('fresh','https://youtu.be/new','New','AUDIO','mp3',0,0,'QUEUED',0,'','',2))
        row = self.db.execute('SELECT * FROM downloads WHERE id=?', ('fresh',)).fetchone()
        self.assertEqual(row['subtitles'], '')
        self.assertEqual(row['embedSubs'], 0)
        self.assertEqual(row['thumbnail'], 0)
        self.assertEqual(row['embedThumb'], 0)
        self.assertIsNone(row['sidecarUris'])
        self.assertEqual(row['generation'], 0)
        self.assertEqual(row['durationSeconds'], 0)
        self.assertEqual(row['lyricStatus'], '')
        self.assertIsNone(row['sidecarPending'])
        self.assertEqual(row['completedAt'], 0)
        self.assertEqual(row['subfolder'], '')

    def test_requeue_completed_reuses_row_without_duplicate(self):
        """'Unduh lagi' memakai ulang baris yang sama: hasil dibersihkan, generasi naik (BUG-026)."""
        self.db.execute("UPDATE downloads SET state='COMPLETED', progress=100, outputUri='content://media/old', sidecarUris='sub|content://media/lrc', completedAt=77, generation=2 WHERE id='job'")
        self.assertEqual(self.query('requeueCompleted', now=99), 1)
        row = self.row()
        self.assertEqual(row['state'], 'QUEUED')
        self.assertEqual(row['generation'], 3)
        self.assertEqual(row['progress'], 0)
        self.assertIsNone(row['outputUri'])
        self.assertIsNone(row['sidecarUris'])
        self.assertEqual(row['completedAt'], 0)
        self.assertEqual(row['createdAt'], 99)
        # Sudah QUEUED → tidak bisa diantre ulang lagi (tombol dobel/tap ganda aman).
        self.assertEqual(self.query('requeueCompleted', now=100), 0)
        # Job berjalan tidak boleh tersentuh oleh antre-ulang.
        self.db.execute("UPDATE downloads SET state='RUNNING' WHERE id='job'")
        self.assertEqual(self.query('requeueCompleted', now=101), 0)

    def test_sidecar_uris_recorded_after_publish(self):
        self.db.execute("UPDATE downloads SET state='COMPLETED' WHERE id='job'")
        self.db.execute(self.queries['sidecars'], dict(id='job', uris='sub|content://media/1\nthumb|content://media/2'))
        self.assertEqual(self.row()['sidecarUris'].count('|'), 2)

    def test_revive_carries_subtitle_and_thumbnail_options(self):
        self.db.execute("UPDATE downloads SET state='FAILED' WHERE id='job'")
        self.db.execute(self.queries['revive'], dict(
            id='job', title='Revived', mode='AUDIO', format='mp3', quality=0, bitrate=320,
            subtitles='id', embedSubs=0, thumbnail=1, embedThumb=1, now=99))
        row = self.row()
        self.assertEqual(row['state'], 'QUEUED')
        self.assertEqual(row['subtitles'], 'id')
        self.assertEqual(row['thumbnail'], 1)
        self.assertEqual(row['embedThumb'], 1)
        self.assertEqual(row['bitrate'], 320)


    def test_stale_generation_cannot_clear_new_pending(self):
        """Cleanup akhir worker lama (gen 0) tak boleh menghapus jejak pending milik generasi baru (BUG-002/R1)."""
        self.db.execute("UPDATE downloads SET state='PAUSED'")
        self.query('resume')  # gen 0→1, state QUEUED
        self.assertEqual(self.row()['generation'], 1)
        self.db.execute("UPDATE downloads SET state='RUNNING'")
        self.query('pending', gen=1, uri='content://media/baru')
        # clearPendingOwned milik worker LAMA tidak mengenai apa pun.
        self.assertEqual(self.query('clearPendingOwned', gen=0), 0)
        self.assertEqual(self.row()['pendingUri'], 'content://media/baru')
        # Pemilik generasi baru tetap bisa melepas jejaknya sendiri.
        self.assertEqual(self.query('clearPendingOwned', gen=1), 1)
        self.assertIsNone(self.row()['pendingUri'])

    def test_duration_update_is_generation_guarded(self):
        """Durasi hasil inspeksi worker hanya ditulis milik generasi sendiri (BUG-013/R3)."""
        self.assertEqual(self.query('duration', gen=0, seconds=210), 1)
        self.assertEqual(self.row()['durationSeconds'], 210)
        self.db.execute("UPDATE downloads SET generation=1")
        self.assertEqual(self.query('duration', gen=0, seconds=999), 0)
        self.assertEqual(self.row()['durationSeconds'], 210)
        self.assertEqual(self.query('duration', gen=1, seconds=233), 1)
        self.assertEqual(self.row()['durationSeconds'], 233)

    def test_sidecar_commit_is_atomic_with_journal_release(self):
        """Baris terbit dicatat dan jurnal sidecarPending dilepas dalam SATU update (BUG-008/R2)."""
        self.query('commitSidecar', line='sub|content://media/lrc', pending=None)
        row = self.row()
        self.assertEqual(row['sidecarUris'], 'sub|content://media/lrc')
        self.assertIsNone(row['sidecarPending'])
        # Baris kedua menumpuk dengan newline; sisa jurnal (yang belum terbit) ikut diperbarui.
        self.query('commitSidecar', line='thumb|content://media/thumb', pending='content://media/sisa')
        row = self.row()
        self.assertEqual(row['sidecarUris'].split('\n'), ['sub|content://media/lrc', 'thumb|content://media/thumb'])
        self.assertEqual(row['sidecarPending'], 'content://media/sisa')
        # Kondisi jendela lama (file terbit tapi jurnal sudah lepas) tidak mungkin terbentuk dari satu commit.
        self.db.execute("UPDATE downloads SET sidecarPending='content://media/sisa'")
        self.query('commitSidecar', line='sub|content://media/lrc2', pending=None)
        self.assertIsNone(self.row()['sidecarPending'])
        self.assertEqual(self.row()['sidecarUris'].count('sub|'), 2)


if __name__ == '__main__':
    unittest.main()
