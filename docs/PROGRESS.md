# Catatan Kemajuan Pengembangan TubeFlow

Dimulai **9 Oktober 2026**, diperbarui sampai 27 Oktober 2026. Dokumen ini melengkapi [BUG_FINDINGS.md](BUG_FINDINGS.md) (temuan) dan [DEVELOPMENT_ROADMAP.md](DEVELOPMENT_ROADMAP.md) (usulan): berisi keputusan desain yang diambil saat perbaikan, bukti yang sudah dikumpulkan, dan sisa pekerjaan agar serah-terima tidak mengulang investigasi.

## Titik mulai terbaru — 27 Oktober 2026

Terbaru **1.1.48/code63**: pegangan awal/akhir klip mulai di ujung bar dan batas 30 detik
dihapus, jadi klip beberapa menit bisa dibagikan ke chat; batas waktu FFmpeg menyesuaikan
panjang klip. 568 tes Kotlin lulus, lint 0 error/3 warning lama; APK/AAB code63 bertanda tangan
kunci produksi lulus, audit native 262 ELF dan kelas ZIP reflektif 0 gagal; uji perangkat belum.
[Rincian](STATUS_CLIP_2026-10-23.md).

Sebelumnya **1.1.47/code62**: titik awal dan akhir klip Status memakai satu bilah rentang
(`ui/RangeScrubber.kt`), bukan dua slider.
[Rincian](STATUS_CLIP_2026-10-23.md).

Sebelumnya **1.1.46/code61** sudah terpasang: kontrol kecerahan dan penguat video.
[Hasil build, uji HP dan konfirmasi dengar](PLAYER_BRIGHTNESS_VOLUME_1_1_46.md).
Catatan baseline 1.1.44 berikut mengacu pada awal pekerjaan player 1.1.45.

Baseline sebelum perubahan **1.1.44/code58**, Room 10, Media3 1.11.1. Ringkasan rilis
1.1.43/1.1.44 dan hasil Bstation berada di akhir kronologi ini serta di
[DEVICE_TEST](DEVICE_TEST.md). Bagian Batch A/B berikut adalah arsip 9 Oktober;
heartbeat progres, skema 3 dan batas dukungannya bukan spesifikasi saat ini.
Gunakan kode, [VALIDATION](VALIDATION.md), dan status terbaru
[BUG_FINDINGS](BUG_FINDINGS.md) saat melanjutkan pekerjaan.

Implementasi 1.1.45/code60: rasio video, kecerahan otomatis, status lampiran
bersama, ketuk Riwayat untuk memutar, preview Status dan penghemat baterai.
[Laporan dan checkpoint](MEDIA_POLISH_1_1_45.md). Jangan menyimpulkan semua bug
lama sudah tertutup dari keberhasilan satu rilis.

## Arsip 9 Oktober — dua batch perbaikan

### Batch A — perbaikan sesuai permintaan pengguna (terverifikasi di perangkat)

1. **Progres ke 100% (historis, diganti 10 Oktober oleh BUG-017)** — `DownloadWorker.publish` monotonic 0–99 + heartbeat (500 ms) setelah file terakhir ≥98% (maks 98), fase salin 99, `dao.finish` = 100. Terverifikasi di perangkat: 91→96→98→100 tanpa membeku di 90.
2. **Batas konkurensi 3+4=7** — semaphore per-key diganti gerbang tunggal `gateLock`/`activeWorkers` yang membaca `SettingsStore.maxConcurrent` (1–4) setiap kali. Terverifikasi: pengaturan 3 menjalankan tepat 3 paralel.
3. **Jeda/lanjut/batal per-job dan massal** — `JobState.PAUSED`, DAO `pause/pauseAll/resume` (resume naikkan `generation`), aksi di QueueScreen + JobCard. Terverifikasi: jeda di 96%, lanjut, batal tunggal, dialog batal semua.
4. **BUG-001 penyebab utama SAF** — `createDocument` menerima tree URI mentah → Android 10 melempar "Invalid URI" → semua simpanan gagal ±90% saat folder SAF dipilih. Diperbaiki dengan `treeRootUri()`. Terverifikasi: 11 MP3 masuk `/storage/emulated/0/Download/TubeFlow/Denny-Caknan/`.
5. **BUG-003 helper hapus** — `deleteUri` lama memeriksa `scheme == "document"` yang tak pernah cocok untuk `content://`.

Catatan: satu kegagalan perangkat adalah HTTP 403 dari YouTube (rate-limit, bukan bug aplikasi); retry berhasil.

### Batch B — perbaikan berdasarkan dokumen (BUG_FINDINGS + ROADMAP)

Dikerjakan sesuai urutan yang disarankan dokumen. Semua lolos `testDebugUnitTest lintDebug assembleDebug` (43 tes Kotlin) dan `python3 -m unittest` (24 tes Python).

## Keputusan desain checkpoint 9 Oktober (historis)

Bukan kontrak kode saat ini. Gunakan invariant pada skill `tubeflow-dev` dan
laporan bug terbaru. Room kini v10; heartbeat sudah dihapus; `false` pada
penghapusan mencakup kegagalan memastikan file hilang, bukan hanya file yang
terbukti ada. Jurnal sebelum write tidak menutup celah create → journal.


- **Token eksekusi `generation` (BUG-002):** kolom INTEGER DEFAULT 0 di `downloads`, naik saat `resume`/`revive`. Mutasi status utama worker pada checkpoint ini memakai `WHERE generation = :gen` (`progress`, `title`, `pending`, `finish`, `failOwned`, `interrupted`, `cancelOwned`); ViewModel memakai versi tanpa syarat generasi. `doWork` membaca generasi SEBELUM `acquireSlot()`. Jangan menghapus syarat ini agar "jeda→lanjut cepat" tidak kena race. Regresi host: `test_stale_generation_cannot_touch_resumed_job`.
- **Migrasi Room 2→3** menambah `generation`, `durationSeconds`, `lyricStatus`, `sidecarPending`. Schema snapshot v3 ada di `app/schemas/…/3.json`; regresi host `test_migration_2_3_alter_columns_match_schema_v3` memastikan SQL migrasi tangan sama persis dengan skema Room. Jangan menambah kolom tanpa memperbarui keduanya.
- **`StorageUris` (BUG-003):** satu-satunya jalur hapus untuk worker, riwayat, dan recovery. `false` berarti file belum terbukti hilang, termasuk query/izin provider gagal; pemanggil wajib menganggap gagal dan mempertahankan jejak recovery. Jangan kembali ke `contentResolver.delete` langsung.
- **`matchesOutput` (BUG-005):** cek duplikat membandingkan seluruh opsi keluaran lewat satu fungsi. Opsi berbeda (mis. MP3 128 vs 320) = job baru yang sah.
- **`sidecarPending` (BUG-008):** URI sidecar dicatat ke DB SEBELUM ditulis; dibersihkan setelah publikasi sukses dan di awal run/recovery. Proses mati di tengah penulisan harus meninggalkan jejak yang bisa dibersihkan — jangan memperpendek jendela ini.
- **Identitas playlist (BUG-007):** `inspectPlaylist` menginspeksi URL kanonis (`canonicalPlaylistUrl`, host YouTube saja) tetapi `.copy(url=original)` agar cocok dengan `shownPlaylist` yang membandingkan URL persis dengan input. Tautan watch/shorts/youtu.be + `list` tetap membuka VIDEO lebih dulu; buka playlist lewat tombol.
- **Inspeksi (BUG-006):** `inspectError` StateFlow terpisah dari `busy`. `inspectedUrl` tidak lagi menjadi satu-satunya penghalang retry — tombol "Coba lagi" memanggil inspeksi langsung. Jangan membuat loop retry otomatis.
- **Lirik audio = penyedia lirik saja:** nilai internal `subtitles` untuk audio adalah `"asli"` (ubahannya "lirik aktif"); pilihan bahasa hanya untuk subtitle video. Jangan pernah mengirim caption YouTube sebagai lirik audio.
- **Pencocokan lirik `LyricMatch` (BUG-013):** skor = 0.6·judul + 0.4·artis bila keduanya diketahui; syarat minimum judul (0.55) DAN artis (0.5) terpisah — judul persis dengan artis meleset tetap ditolak. Gerbang versi (live/acoustic/cover/remix/demo/instrumental/karaoke/unplugged/session/nightcore/sped/slowed/extended/reprise wajib sama di kedua pihak) dan gerbang durasi (toleransi `max(20 dtk, 15%)`) mengembalikan `< 0` = TOLAK. Kata sampah judul video (official/video/lyrics/4k/dst., termasuk "remaster") dibuang sebelum membandingkan. Respons `/api/get` WAJIB lewat `LyricMatch.score` yang sama, bukan skor 1.0 buta.
- **Status lirik terstruktur (BUG-014):** `LyricOutcome` → kolom `lyricStatus` (`tersimpan`/`tidak_ditemukan`/`instrumental`/`jaringan`/`gagal_simpan`; kosong = tidak diminta). Audio yang sukses TETAP "Selesai" walau lirik gagal — status lirik tampil terpisah di kartu Riwayat dan notifikasi. `revive` me-reset `lyricStatus`.
- **Cari ulang lirik tanpa unduh ulang (`refetchLyrics`, sebagian OPT-002):** menimpa `.lrc` lama in-place bila ada (lirik lama tetap utuh sampai pengganti berhasil ditulis), kalau tidak ada membuat dokumen baru di FOLDER YANG SAMA dengan audio — folder diturunkan dari `outputUri` (`getTreeDocumentId`), bukan dari pengaturan sekarang, sehingga perpindahan folder tidak memutus asosiasi. Guard `lyricRetries` mencegah dua pencarian paralel untuk audio yang sama.
- **Nama sidecar mengikuti nama audio yang benar-benar terbit (BUG-010):** `displayNameOf()` dibaca SETELAH `dao.finish`; provider boleh mengganti nama saat tabrakan (`Judul (1).mp3`).

## Bukti pemeriksaan (9 Oktober 2026)

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew -PuseGoogleMirror=true testDebugUnitTest lintDebug assembleDebug
# 43 tes Kotlin lulus; lint 0 error / 16 warning; schema 3.json tergenerasi
python3 -m unittest discover -s scripts -p "test_*.py"
# 24 tes Python lulus (termasuk regresi migrasi 1→2, 2→3, generasi, lyricStatus)
```

Verifikasi perangkat (Realme RMX1821, Android 10, arm64 — batch A): progres 100%, batas konkurensi, jeda/lanjut/batal, simpanan SAF BUG-001. Satu kegagalan perangkat adalah HTTP 403 YouTube (rate-limit), retry berhasil.

### Sesi perangkat 9 Okt 2026 (malam) — pasang APK release hasil batch B

- **Migrasi 2→3:** aplikasi terbuka tanpa crash; baris lama tetap terbaca (Room memvalidasi skema saat start).
- **BUG-007:** `watch?v=…&list=…` → video terbuka lebih dulu + tombol "buka sebagai playlist" → kartu playlist 31 video tampil (URL kanonis, identitas cocok).
- **BUG-009:** mode audio menampilkan toggle "Download lirik" tanpa menu bahasa; ringkasan kartu "Auto · MP3 · Lirik · Sampul" (tanpa "bahasa asli" — `formatMeta` ikut diperbaiki di sesi ini).
- **BUG-014 + BUG-015 (temuan baru):** unduhan pertama (Rick Astley) menghasilkan `.lrc.txt` — provider SAF Android 10 menyisipkan ekstensi mime saat nama `.lrc` tidak dikenali; ada juga anomali dua baris sidecar yang perlu pengamatan lanjut (kemungkinan intervensi file manual saat debugging). Diperbaiki: `renameDocument` paksa setelah create + penilaian status via nama tampilan. Unduhan kedua (Despacito): **tepat satu** `Luis Fonsi - Despacito....lrc` (3373 byte, nama benar), kartu Riwayat "Lirik tersimpan", satu tombol Lirik — terverifikasi.
- **Observasi terbuka (belum dikonfirmasi reproduksi):** job yang dibatalkan saat berjalan pernah tercatat `FAILED` dengan `error` kosong (kartu tanpa teks pesan). Belum ada jalur DAO yang menulis `FAILED` tanpa pesan — perlu logcat saat kejadian bila berulang; kemungkinan berkaitan dengan pesan exception kosong dari mesin download.
- **Anomali pemanggilan adb (bukan aplikasi):** URL berisi `&` harus dikutip untuk shell remote (`adb shell "am start … 'url' …"`), kalau tidak perintah terpecah.

## Sisa pekerjaan (checkpoint9Oktober, historis)

Status di bagian ini telah dilanjutkan oleh [rekonsiliasi backlog terbaru](#rekonsiliasi-backlog-bug-roadmap-dan-release--13-oktober-2026); jangan mengulang build, migrasi atau fitur yang sudah selesai di batch berikutnya.

1. **Verifikasi runtime untuk status "menunggu verifikasi"** di BUG_FINDINGS: retry inspeksi (006), simulasi gagal sidecar (008), ganti mode bahasa (009), nama `Judul (1).lrc` (010), ukuran folder (011/012), penghapusan SAF jujur (003), cleanup parsial setelah restart (004), status lirik + cari ulang (014), dengarkan sinkronisasi `.lrc` (013).
2. **Migrasi 2→3 di perangkat:** pasang APK di atas instalasi lama, pastikan entri unduhan lama terbaca (kolom baru DEFAULT aman).
3. **OPT tersisa:** OPT-001 sisa penyimpanan ID rekaman/sumber lirik; OPT-002 penuh (pratinjau kandidat, riwayat status per job setelah restart sudah ada); OPT-003..008 sesuai roadmap.
4. **Audit rilis:** `python3 scripts/audit_native.py` dan `audit_release_zip_fields.py` setelah `assembleRelease` (belum dijalankan pada batch ini).

Ikuti [RELEASE.md](RELEASE.md) untuk pengujian perangkat fisik; catat hasil di [DEVICE_TEST.md](DEVICE_TEST.md).

## Audit ulang setelah batch perbaikan — 9 Oktober 2026

Audit berikutnya menemukan bahwa klaim seluruh bug diperbaiki belum dapat dipertahankan: dari 15 temuan, 9 memiliki perbaikan kode sesuai masalah awal, 6 masih memiliki celah, ditambah BUG-016 mengenai penimpaan lirik lama. Progres batch di atas tetap merupakan riwayat; status terbaru ada di [BUG_FINDINGS.md](BUG_FINDINGS.md) dan bukti/rencana lanjut ada di [REVIEW_2026-10-09.md](REVIEW_2026-10-09.md).

Gradle test/lint/debug/release berhasil (laporan 43 tes Kotlin), 24 tes Python lulus, lint 0 error/17 warning, dan audit native/ZIP ketiga ABI tanpa kegagalan. Probe JVM membuktikan fallback Live menerima studio, durasi 200 vs 229 detik mendapat skor penuh, dan kegagalan search parsial dilaporkan tidak ditemukan. Simulasi SQL menunjukkan celah pencatatan sidecar dan cleanup pending lintas generasi. Tidak ada tes perangkat baru atau perubahan kode aplikasi dalam sesi audit ulang.

## Pembersihan warning — 9 Oktober 2026

17 warning lint dan 2 catatan informasi diperbaiki melalui KTX, state Int, pembaruan AndroidX, serta compile/target SDK 37. Toolchain, CI, dan README ikut diperbarui. Pemeriksaan terbaru: lint debug/release **0 error, 0 warning, 0 informasi**, 43 tes Kotlin dan 24 tes Python lulus, build debug/release ketiga ABI berhasil, audit native/ZIP tanpa kegagalan. Kompilasi Kotlin bersih; dua jenis deprecation internal plugin AGP masih dicatat oleh Gradle.

APK release ARM64 dengan kunci testing disalin ke Downloads HP. Instalasi ADB ditolak verifier; installer bawaan meminta pemindaian Google Play Protect. Pada 10 Oktober pengguna menyetujui kelanjutan, dan APK sudah terpasang dengan hash sesuai artefak saat sesi dilanjutkan.

Uji langsung pada Realme Android 10 lulus untuk inspeksi, antrean/background, download dan konversi MP3 128 kbps dengan sampul, penyimpanan SAF, pemutaran melalui Poweramp, filter riwayat, serta persistensi pengaturan setelah restart. Batas paralel dikembalikan ke 3; file uji disimpan untuk pemeriksaan ulang. Pengguna telah membersihkan data riwayat/media lama, sehingga retensi entri lama saat update belum dinilai. Rincian ada di [DEVICE_TEST.md](DEVICE_TEST.md) dan [LINT_CLEANUP_2026-10-09.md](LINT_CLEANUP_2026-10-09.md). Status bug fungsional pada audit sebelumnya tetap berlaku.

## Progres terukur dan kompresi sampul — 10 Oktober 2026

BUG-017 / OPT-006: heartbeat persen dihapus; progres byte per track, tahap pemrosesan tak tentu, persen salinan, notifikasi tahap, dan finalisasi setelah sidecar. Room v4 dengan migrasi 3→4. OPT-009: JPEG maksimal 800 piksel sebelum embedding/penyimpanan, tanpa upscale atau perubahan rasio; ZIP yt-dlp resmi tetap dipin. Regresi: 49 tes Kotlin, 32 tes Python, lint debug/release 0 masalah, build debug/release serta audit native/ZIP ketiga ABI lulus. Pipeline host membuktikan embedding JPEG dan paket audio tidak berubah.

APK final SHA256 `2dc813c9b3bad4611550e553372d3c5f031d4cd37af984ca631766f1c644a17d` terpasang dan hash diverifikasi. Setelah HP disambungkan ulang, uji langsung Realme Android 10 berhasil: migrasi mempertahankan 1 entri lama, MP3 128 kbps dengan JPEG 800×451 diputar di Poweramp, sampul 97,08% lebih kecil (1.597.019→46.665 byte), seluruh 26.442 paket audio identik. Video MP4 360p memperlihatkan progres video 43→77%, audio 40%, pemrosesan tanpa persen/kecepatan/ETA, lalu selesai. Riwayat akhir 3 entri, antrean kosong. Bukti, screenshot, dan batas cakupan ada di [PROGRESS_COVER_2026-10-10.md](PROGRESS_COVER_2026-10-10.md). Status bug lain pada audit sebelumnya tetap berlaku.

## 10 Oktober 2026 — Mix dan identitas duplikat

BUG-018/019 diperbaiki: Mix `RD...` mempertahankan seed dan membaca panel awal tanpa continuation; playlist biasa tetap mengikuti pagination. Duplikat memakai ID video + seluruh opsi, termasuk URL riwayat lama dan retry gagal yang punya sibling selesai. Tidak ada perubahan schema atau dependensi.

55 tes Kotlin, 37 tes Python, lint debug/release tanpa masalah, build serta audit native/ZIP ketiga ABI lulus. APK final SHA256 `75b62581d62da04588f16fb3ed15aa9b26ade278843f5f59e20bf1f0c0ff2b40` terpasang pada HP Realme Android 10, hash cocok. Link pengguna menghasilkan 25 entri awal; dua alias baru MP3 yang sudah ada ditolak. Antrean kosong, riwayat dan daftar file tetap 3. Tidak ada fatal/Room error pada log aplikasi. Bukti dan sisa cakupan runtime ada di [MIX_DUPLICATES_2026-10-10.md](MIX_DUPLICATES_2026-10-10.md); temuan audit lain tetap berlaku.

## 10 Oktober 2026 — Keystore dan release signed 1.0.0

Atas permintaan pengguna, dibuat keystore produksi RSA 4096/PKCS12 dan konfigurasi password privat di luar repository (`~/.local/share/tubeflow/signing/`). Script `build_release.sh` memakai konfigurasi lokal/CI dan menolak kredensial tidak lengkap. Build menghasilkan signed APK tiga ABI dan AAB; salinan dengan nama distribusi, checksum, manifest release, dan sertifikat publik berada di `app/build/outputs/distribution/1.0.0/`.

Signature/certificate APK/AAB cocok dengan keystore produksi; APK zipalign, audit native/ZIP, validasi bundletool AAB, dan audit 1.024 ELF bundle lulus. Unit Kotlin 55 dan lint 0 masalah; tidak ada perubahan kode aplikasi setelah tes Python/perangkat sebelumnya. APK baru belum diinstal di HP karena kunci berbeda dari testing. Backup eksternal dan upload toko belum dilakukan. Rincian artefak/fingerprint serta warning JAR tersimpan di [PRODUCTION_SIGNING.md](PRODUCTION_SIGNING.md).

## Folder default dan ukuran — release 1.0.1, 10 Oktober 2026

Produksi 1.0.0 telah terpasang melalui installer bawaan; hash dan sertifikat yang ditarik dari HP cocok. Agent tidak melakukan uninstall/clear data; retensi data saat perpindahan kunci tidak terbukti karena interaksi installer tidak teramati penuh dan riwayat kemudian kosong.

BUG-020 ditemukan: mode default menunjukkan 0 B, padahal root TubeFlow berisi 53 file/309.199.528 byte; pemilihan root SAF menunjukkan 294,9 MB. Default tetap `Download/TubeFlow`, bukan subfolder pengujian Denny-Caknan. Perbaikan memakai grant baca persisten yang cocok tepat dengan root, label cakupan lengkap/terbatas, status kegagalan baca, dan pemilih folder dengan tujuan awal yang benar.

Signed release **1.0.1 (2)** selesai dengan kunci produksi lama. **60 tes Kotlin, 37 tes Python, lint 0 masalah**, verifikasi signature/alignment/audit APK tiga ABI serta AAB signing/bundletool/1.024 ELF lulus. Artefak dan checksum di `app/build/outputs/distribution/1.0.1/`. APK ARM64 SHA256 `3436ec61465a5a6e7a710158d21529c75a5feb50c9a3dbf35587aa004acf23ac`.

**Tes root default 1.0.1 berhasil di HP** setelah sambungan tersedia: hash/signature terpasang cocok, 50 file/258.380.600 byte terbaca 246,4 MB. Mode folder pilihan, kembali ke default, Segarkan dan restart tetap sesuai. Antrean dan riwayat kosong; jumlah/daftar ukuran file identik sebelum-sesudah. Tidak ada fatal/Room error pada PID setelah restart. Checklist root BUG-020 ditutup; kasus kegagalan provider/grant lain masih terbuka. Rincian tersedia di [STORAGE_SIZE_2026-10-10.md](STORAGE_SIZE_2026-10-10.md). Tidak ada emulator, penghapusan atau pemindahan file untuk perbaikan ini.

## Auto-folder playlist dan hapus semua file — 11 Oktober 2026

Dua fitur atas permintaan pengguna.

1. **Subfolder otomatis per playlist/Mix** — `Selection.playlistFolder(title)` membuat slug aman yang SELALU lolos `safeFolder` (maks 40 karakter; "Mix - DENNY CAKNAN" → `mix-denny-caknan`; judul tanpa huruf/angka → tanpa subfolder). Karena engine selalu `--no-playlist` (tiap job = 1 video), subfolder diwirakan saat batch diajukan: `queuePlaylist(entries, options, folderTitle)` menerima judul panel playlist lalu meneruskannya ke `submit(..., subfolder=...)`. Kolom baru `subfolder` di `DownloadJob` (Room v6, `MIGRATION_5_6`, snapshot `6.json`; baris lama `''` = folder utama, perilaku lama). Worker menggabungkannya ke `RELATIVE_PATH` MediaStore dan membuat/mencari direktori di tree SAF (`ensureSubfolderDoc` — dua worker yang balapan memakai direktori paling awal, tidak meninggalkan "name (1)"). Sidecar (`.lrc`/`.srt`) ikut subfolder yang sama. "Cari ulang lirik" kini menurunkan induk dokumen asli lewat kolom kontrak `parent_document_id` (fallback akar tree bila provider tidak mendukung) agar `.lrc` tetap ditulis di folder yang sama dengan audionya. Unduhan tunggal tanpa playlist tetap di folder utama.
2. **Hapus semua file di folder TubeFlow** — `clearStorageFolder()` di ViewModel + tombol di kartu Penyimpanan Pengaturan dengan dialog konfirmasi (pola dialog Riwayat). Urutan: (1) file yang tercatat di riwayat dihapus via `StorageUris` (jujur — gagal = masih dihitung ada), (2) folder tujuan ditelusuri agar file yatim ikut hilang — walk tree SAF, atau query MediaStore relatif dengan escape `%`/`_` (BUG-012) untuk jalur default, (3) parsial `noBackupFilesDir` semua job dibuang + notifikasi hasil dibatalkan. Diblokir selama ada job QUEUED/RUNNING/PAUSED (parsial aktif tidak boleh tersentuh — koordinasi OPT-008); riwayat Room TIDAK dihapus (`outputStillExists` menandai baris basi), `refreshStorage()` dijalankan di akhir.

Regresi host: `playlistFolderSlugsAreAlwaysSafeSubfolders`, `test_migration_5_6_adds_subfolder_like_schema_v6`, dan default `subfolder` di `test_new_option_columns_have_defaults`. Jalur: **68 tes Kotlin, 44 tes Python**, lint debug 0 masalah, `assembleDebug` lulus. Uji perangkat (folder Mix nyata, tombol hapus di jalur SAF & MediaStore) belum dijalankan.

**Rilis 1.0.4 (versionCode 5) signed** dibangun bertahap (lintRelease → assembleRelease+bundleRelease, signing env lokal), seluruh audit hijau (255 ELF 0 gagal 16 KB, zipalign `-P 16 4` OK, ZIP reflexive 0 gagal, apksigner v2 produksi `ebe3b40e…`); artefak di `distribution/1.0.4/`. Terpasang via `adb install -r` di Realme RMX1821 Android 10 dengan hash identik `09032c38…`. Uji live: tautan Mix `RDrEjSkBl47OY` → seed recovery → panel Mix → 1 video SATRU 2 (Audio MP3) → file 6.306.446 byte masuk ke subfolder `mix-denny-caknan-x-happy-asmara-satru-2/` di `Download/TubeFlow/` — auto-folder MediaStore terbukti. Tombol "Hapus semua file" tidak diuji di perangkat agar koleksi lama pengguna tidak terhapus; jalur SAF juga belum diuji runtime. Rincian di [DEVICE_TEST.md](DEVICE_TEST.md).

## Enam perbaikan permintaan pengguna lanjutan — 11 Oktober 2026

Setelah review 1.0.4 di perangkat, pengguna mengajukan 6 poin; semuanya dikerjakan dalam satu batch.

1. **Auto-folder hanya artis utama** — `Selection.playlistFolder` kini memotong judul pada segmen lagu (pemisah `" - "`) dan pemisah kolaborasi (` x `, ` ft`, ` feat`, `&`, `+`, `vs`, `with` — regex `collaborator`, case-insensitive, kata utuh). "Mix - DENNY CAKNAN X HAPPY ASMARA - SATRU 2" → `mix-denny-caknan` (sebelumnya slug 40 karakter penuh). Judul "ARTIS - LAGU" biasa juga hanya memakai bagian artis. Hasil tetap SELALU lolos `safeFolder`. Regresi baru: `playlistFolderKeepsOnlyMainArtistName`.
2. **Folder default tanpa izin SAF** — sebelumnya memilih folder bawaan lewat pemilih sistem menjadikannya target SAF (grant persisten) meskipun tujuan penyimpanan MediaStore sebenarnya tidak butuh izin. Kini `matchesDefault` mendeteksi pemilihan root bawaan dan tujuan KEMBALI ke default (tree dibersihkan); grant hanya dipakai untuk menghitung ukuran penuh. Teks kartu Penyimpanan menjelaskan bahwa folder bawaan tidak butuh izin apa pun dan pemilihan folder hanya untuk lokasi lain (SD card).
3. **Jumlah entri playlist diklarifikasi di UI** — tidak ada pembatasan 25 di aplikasi: `inspectPlaylist` memakai `--playlist-end 500` (playlist biasa mengikuti pagination link hingga 500 entri). Angka ±25 hanya antrean awal Mix dari YouTube (radio tanpa halaman lanjutan). Label kartu playlist kini "N video · mengikuti isi link (maks 500)" dan teks Mix menjelaskan asal antrean awal.
4. **Status lirik: 'tidak ada' bukan 'jaringan'** — klasifikasi HTTP di `Lyrics.get` diperinci: 404 = tidak ditemukan; 400/422/451 = provider menolak query itu sendiri (`Res.Rejected` → dihitung seperti jawaban tegas, bukan kegagalan); 401/403/408/429/5xx/exception tetap `NETWORK` (retryable). Sebelumnya semua non-200/404 jadi "jaringan". Label diperjelas: NOT_FOUND = "Lirik tidak tersedia di penyedia lirik", NETWORK = "Pencarian lirik gagal (periksa jaringan)". Regresi baru: `providerRejectionIsNotFoundNotNetwork`, `rateLimitStillReportsNetwork`.
5. **Pengaturan: segmented → dropdown** — kartu Tampilan (mode tema) dan Antrean (1–4×) memakai `OptionMenu` (dropdown) alih-alih `SingleChoiceSegmentedButtonRow`; tata letak kartu Penyimpanan dirapikan (penjelasan izin, tombol "Pilih folder lain…").
6. **Multi-platform (tahap awal)** — `Selection.validUrl` menerima allowlist host non-YouTube yang didukung yt-dlp (TikTok, Instagram, Facebook/fb.watch, X/Twitter, SoundCloud, Vimeo, Dailymotion/dai.ly, Twitch/clips, Reddit/redd.it, Pinterest/pin.it, Threads, Bandcamp, Bilibili/b23.tv, Likee, Snapchat — subdomain diterima, https + path wajib, userInfo/port asing ditolak). Identitas duplikat diperluas: `Selection.identity` = `videoId` (YouTube) atau URL utuh tanpa fragment (platform lain); `matchesOutput` memakainya. `videoId` diberi gerbang host agar path platform lain tidak terbaca sebagai "ID" dan tabrakan. Teks UI layar Unduh/Pengaturan disesuaikan. **Batas:** playlist/Mix, subtitle, dan lirik tetap optimalkan YouTube; validasi judul/format tetap sama; platform baru tidak diuji perangkat — perlu uji live sebelum rilis.

Regresi host batch ini: **74 tes Kotlin, 44 tes Python**, lint debug 0 masalah, `assembleDebug` lulus. Uji perangkat untuk keenam poin belum dijalankan saat batch ditulis.

### Rilis 1.0.5 (versionCode 6) signed — 11 Oktober 2026

Dibangun sesuai permintaan pengguna: `lintRelease` (3m43s) lalu `assembleRelease`+`bundleRelease` dengan signing env lokal (5m56s), `--no-daemon -PuseGoogleMirror=true`. Audit ketiga APK: native 255 ELF 0 gagal 16 KB, ZIP reflexive 13 kelas 0 gagal, zipalign `-c -P 16 4` OK, apksigner v2 produksi; AAB: `jarsigner -verify` OK (warning PKIX wajar — self-signed), audit native 1024 ELF 0 gagal. Metadata `id.local.tubeflow` v1.0.5 (6), non-debuggable; engine checksum `1fa6733c…` cocok manifest; bootstrap asset identik.

Artefak di `distribution/1.0.5/` (APK×3, AAB, cert, `release.json` dengan daftar perubahan, `SHA256SUMS`). ARM64 SHA256 `264a342e…` (62.810.643 byte); sertifikat identik rilis sebelumnya `ebe3b40e…`. Terpasang via `adb install -r` di HP Realme; hash APK terpasang identik dengan artefak.

## Tujuh perbaikan permintaan pengguna lanjutan (batch 7) — 11 Oktober 2026

Setelah review 1.0.5 di perangkat, pengguna mengajukan 7 poin lagi. Poin 4 (SoundCloud/Instagram) sudah sukses tanpa perubahan.

1. **Nama file konsisten "Judul - Artis" (judul DULU — koreksi eksplisit pengguna)** — `Format.neatTitle` kini menempelkan kreator di BELAKANG judul (`"$title - $name"`, bukan sebelumnya `"$name - $title"`); pengecekan "nama sudah ada di judul" memakai segmen utuh (awal/akhir dengan spasi) agar tidak diduplikasi. `Lyrics.fetch` membalik parsing: `substringBefore(" - ")` = track, `substringAfter(" - ")` = artis. Tag metadata `--replace-in-metadata` di `Engine.download` ikut dibalik (title = segmen pertama, artist = sisanya). Nama file panjang: `tidyFileName` memotong bagian judul saja, sufiks " - Artis" (≤24 karakter) dipertahankan. Regresi baru: `neatTitleStripsNoiseAndAddsArtistTitleFirst`, `tidyFileNameKeepsArtistSuffixWhenTruncating`, `nameParsesAsTitleFirstArtistSecond`.
2. **Auto-folder Mix: deteksi judul vs artis** — `Selection.playlistFolder` (untuk judul Mix saja) membuang embel-embel "(...)" lalu memilih segmen " - " TERPANJANG sebagai kandidat nama; judul "Mix - LAMUNAN - SASYA ARKHISNA (Official…)" → `mix-sasya-arkhisna-…` (mengandung "sasya-arkhisna", bukan "lamunan"/judul lagu). Playlist biasa TIDAK membuang "(...)" ("(Full Album)" tetap ada — perilaku lama dipertahankan). Regresi baru: `mixFolderPicksArtistFromEitherTitleOrder`.
3. **Facebook: pesan error jelas** — `DownloadViewModel.inspectErrorMessage(url, msg)`: untuk host facebook.com/fb.watch pesan diubah menjadi penjelasan "video mungkin privat, butuh login, atau tautan kedaluwarsa — salin tautan video publik langsung dari aplikasi Facebook" alih-alih pesan mentah mesin.
4. **Pengaturan: folder hanya via SAF** — field teks "Folder penyimpanan" yang bisa diedit DIHAPUS; perubahan folder hanya lewat tombol pemilih folder sistem (dari sana pengguna bisa membuat folder baru langsung). Teks kartu dijelaskan bahwa folder bawaan tidak butuh izin dan pemilih folder untuk lokasi lain.
5. **Hapus semua file ikut membersihkan folder kosong** — `pruneEmptyFolders(root)` (SAF, bottom-up, hanya direktori yang terbukti kosong; root tree tidak dihapus) dijalankan pada jalur default memakai grant SAF bawaan (`defaultTreeGrant()` — helper bersama dengan `measureStorage`). Jalur MediaStore tetap menghapus file saja karena Android tidak mengizinkan penghapusan direktori lewat MediaStore tanpa izin.
6. **Antrean: tombol "Bersihkan"** — kartu status gagal/batal di QueueScreen kini punya tombol "Bersihkan" (memanggil `clearUnsuccessful()`) di samping "Ulangi semua" — sebelumnya hanya ada di Riwayat. Kartu tampil juga bila hanya ada entri dibatalkan.
7. **Riwayat: chip filter platform** — `Selection.platform(url)` (peta host → nama: YouTube, TikTok, SoundCloud, Instagram, Facebook, X, dst.; host asing = nama host) + baris `FilterChip` "Semua platform" di Riwayat (hanya bila >1 platform ada). Kartu riwayat kini menampilkan platform asal di baris meta.

Regresi host batch ini: **78 tes Kotlin, 44 tes Python**, lint debug 0 masalah, `assembleDebug` lulus. Uji perangkat batch 7 belum dijalankan — terutama: auto-folder Mix dengan contoh pengguna, nama file "Judul - Artis" + lirik, pesan error Facebook, hapus folder kosong, tombol bersih Antrean, dan chip platform Riwayat.

### Rilis 1.0.6 (versionCode 7) signed — 12 Oktober 2026

Membawa seluruh perubahan batch 7 (nama konsisten "Judul - Artis", heuristik auto-folder Mix, pesan error Facebook, Pengaturan SAF-only, prune folder kosong, tombol Bersihkan di Antrean, chip platform Riwayat). Dibangun dengan `bash scripts/build_release.sh --no-daemon` → BUILD SUCCESSFUL 15m25s (78 tes Kotlin + lint debug/release + assembleRelease + bundleRelease dalam satu script); 44 tes Python lulus terpisah. Audit ketiga APK: native 255/255/257 ELF 0 gagal 16 KB, ZIP reflexive 13 kelas 0 gagal, zipalign `-c -P 16 4` OK, apksigner v2 produksi; AAB `jarsigner -verify` OK.

Artefak di `distribution/1.0.6/` (APK×3, AAB, cert, `release.json`, `SHA256SUMS`); ARM64 SHA256 `c17deb40…` (62.813.959 byte); sertifikat identik rilis sebelumnya `ebe3b40e…`. Terpasang via `adb install -r` di HP Realme RMX1821 (update atas 1.0.5, tanpa uninstall); hash APK terpasang identik dengan artefak lokal; app launch tanpa crash di logcat. Delapan uji live batch 7 belum dijalankan — status rilis: **terpasang, belum diuji live**.

## Auto-folder Mix memakai channel kreator — 12 Oktober 2026

Kasus pengguna: Mix `RD3J1Opi7MVHw` menghasilkan folder `mix-dj-jauh-ko-pergi-sa-ceritakan-pada-b` (potongan judul lagu), padahal kreatornya "Maman Fvndy Rmx". Akar masalah: judul Mix YouTube bisa berupa SATU kalimat panjang tanpa pemisah " - ", sehingga heuristik segmen terpanjang mengambil seluruh judul; channel asli ada di tiap entri flat-playlist tapi dibuang `inspectPlaylist`. Perbaikan: `PlaylistMetadata` kini membawa `channel` (dihitung `Selection.pickChannel` — channel playlist, atau channel paling sering muncul di antara entri; kasus ini "Maman Fvndy Rmx" 418/1666) dan `Selection.playlistFolder(title, channel)` memakai channel itu untuk Mix (dibersihkan: embel-embel "(...)", sufiks "- Topic", pemisah kolaborator; fallback ke heuristik judul lama bila channel kosong). Playlist biasa TIDAK memakai channel pemilik (konvensi judul "ARTIS - TOPIK" dipertahankan). Terpasang di alur: `Engine.inspectPlaylist`, preview di DownloadScreen, dan `queuePlaylist` via TubeFlowApp. Regresi baru: `mixFolderPrefersCreatorChannelOverLongTitle`, `pickChannelPrefersMostFrequentEntryChannel` — 80 tes Kotlin lulus, lint debug 0, assembleDebug sukses. Belum diuji di perangkat (HP terputus dari adb) dan belum masuk build release.

### Rilis 1.0.7 (versionCode 8) signed — 12 Oktober 2026

Membawa fix auto-folder Mix dari channel kreator (bagian atas entri "Auto-folder Mix memakai channel kreator — 12 Oktober 2026"). `scripts/build_release.sh --no-daemon` → BUILD SUCCESSFUL 12m9s (80 tes Kotlin, lint debug+release, assembleRelease, bundleRelease). Audit ketiga APK lulus (native/ZIP reflexive/zipalign/apksigner v2), AAB jarsigner verified. ARM64 SHA256 `9ffdea3d…` (62.815.027 byte), sertifikat identik `ebe3b40e…`. Artefak di `distribution/1.0.7/`. Terpasang via `adb install -r` di Realme RMX1821 (update atas 1.0.6, hash APK terpasang identik); app launch tanpa crash. Uji live folder Mix (link RD3J1Opi7MVHw) belum dijalankan.

## Batch 8: identitas judul-artis, resume, platform, sampul — 12 Oktober 2026

Permintaan pengguna: 9 poin perbaikan (identifikasi artis, penamaan file konsisten "JUDUL - ARTIS", pola DJ/remix, lirik multi-variasi, platform di antrean, resume download, thumbnail, konsistensi antrean/riwayat). Akar masalah dan solusi:

1. **Identitas judul-artis terpusat (P1)** — modul baru `core/TitleSplit.kt`: `identify(rawTitle, channel, metadataArtist)` menghasilkan `SongIdentity(title, artist, versionTag)`. Artis HANYA dengan bukti berurutan: metadata `artist` platform → channel "- Topic" → nama channel yang cocok di judul lewat token di batas kata (token terakhir channel boleh terpotong di ekor judul — kasus nyata "... FULL SONG MAMAN FVNDY 2026" dari "Maman Fvndy Rmx"). Tanpa bukti → artist null (TIDAK menebak; posisi teks bukan bukti). Penanda versi "(Live)" dipisah ke versionTag; noise promosi "(Official Video)"/kata ekor (official, 4k, remaster, …) dibuang. `Format.neatTitle` kini delegasi ke jalur ini — penamaan file, lirik, dan auto-folder memakai identitas SAMA. Kolom Room baru `artist` (v7, migrasi 6→7, baris lama '' = tanpa bukti); diisi saat submit dan saat worker inspeksi placeholder.
2. **Lirik multi-variasi (P1)** — `Lyrics.fetch` memakai identitas tersimpan; tanpa bukti, pemisahan posisional "Judul - Artis" UTUH (termasuk kurung versi — gerbang versi BUG-013/R3 dipertahankan). Varian pasangan: judul+artis lalu judul-saja hanya bila tanpa kandidat artis. Status NETWORK vs NOT_FOUND tetap (BUG-014).
3. **Resume download (P1)** — akar: yt-dlp default `-c` (lanjut) + `--retries 10`; aplikasi menurunkan retries ke 3 dan MENGHAPUS parsial saat FAILED, sehingga "Coba lagi" selalu dari nol. Perbaikan: parsial dipertahankan untuk PAUSED/QUEUED/FAILED (hanya CANCELLED dibersihkan; recovery tidak menghapus parsial job gagal), retries & fragment-retries kembali ke 10, pesan gagal/putus menjelaskan lanjut dari progres terakhir, kartu antrean menampilkan hint "Progres tersimpan" bila direktori kerja job masih ada. Validasi keaslian parsial saat lanjut tetap tugas yt-dlp (Range/ukuran) — tidak ditempel buta.
4. **Auto-folder (P2)** — `playlistFolder(title, channel, provenArtist, entryArtists)`: prioritas artis terbukti (dihitung `TitleSplit.identify` dari judul playlist) → suara terbanyak artis entri (≥2) → channel HANYA untuk Mix (invariant: Mix radio = kreator; konsisten dengan format "DJ JAUH KO PERGI - MAMAN FVNDY RMX") → heuristik judul lama. Playlist biasa tidak memakai channel pemilik (sering label/perusahaan).
5. **Platform sumber (P2)** — diturunkan dari URL via `Selection.platform` (sumber data sama di antrean & riwayat; tanpa kolom baru). Ikon platform baru (`PlatformIcon` di components.kt; peta ringkas + globe untuk platform baru) tampil di kartu Antrean DAN Riwayat.
6. **Thumbnail (P2)** — pada checkpoint ini thumbnail selalu diminta ke yt-dlp (`--write-thumbnail --convert-thumbnails jpg` + kompresi); worker menyalinnya ke `covers/<id>.jpg` (jalur internal bertahan setelah direktori kerja dibersihkan) dan mencatat kolom baru `coverPath` (v7). Kartu menampilkan sampul via `CoverThumb` (decode di IO; gagal/tanpa sampul = ikon mode lama). Sidecar thumbnail tetap hanya bila diminta pengguna; cache ikut dibersihkan bersama direktori job.
7. **Regresi** — 89 tes Kotlin lulus (baru: `TitleSplitTest` 8 tes, `playlistFolderPrefersProvenArtistOverChannel`, `channelEmbeddedInTitleCountsAsEvidence`, `artistAndCoverUpdatesAreGenerationGuarded`; `FormatTest`/`SelectionTest` disesuaikan kontrak bukti); 46 tes Python lulus (migrasi 6→7 diverifikasi kolom-per-kolom terhadap `7.json`); lintDebug + assembleDebug sukses. Snapshot `app/schemas/.../7.json` tergenerasi.

Belum diverifikasi di perangkat: resume setelah kegagalan nyata, tampilan sampul/ikon platform, migrasi 6→7 di aplikasi terpasang (perlu build release + install).

## Sistem metadata/resume 1.0.9 — 13 Oktober 2026

Perbaikan memakai skill [tubeflow-dev](../.agents/skills/tubeflow-dev/SKILL.md). Identitas terstruktur (raw/track/artist/evidence/creator/remixer/version), source platform, thumbnail dan resume state tersimpan di Room v8 melalui migrasi 7→8; worker/submit/folder/lirik/tag/kartu menggunakan snapshot bersama. Auto Folder per lagu hanya memakai artis terbukti; judul ambigu tetap aman di root. Registry platform dan cache cover awal berlaku bersama antrean/riwayat, dengan recovery artwork media lama.

Resume memvalidasi sumber/format/ukuran/validator/prefix/fragmen sebelum memakai downloader yt-dlp yang dipin; URL diekstrak ulang, parsial FAILED/PAUSED dipertahankan, fallback restart diberi notice. Pengujian file HP menemukan postprocessor masih mengisi artist dari uploader: kini `MetadataTags` mengirim override meta_title/meta_artist (kosong eksplisit jika tak terbukti), creator/remixer terpisah. Larangan fallback channel berlaku juga pada Mix, menggantikan keputusan batch 12 Oktober sebelumnya.

**Final:** signed 1.0.9/code10 ARM64 SHA256 `e3c5b3f3587614f55f6d56a670d04a41e1f55a6325ddc02e3d1c9958a0cdf91f`, terpasang sebagai update dengan kunci produksi sama. 102 Kotlin + 61 Python lulus, lint debug/release 0 masalah, audit APK×3/AAB lulus. Di HP: 25 riwayat lama utuh; Mix 25 panel awal; cover sebelum selesai; Auto Folder unknown root; duplikat youtu.be ditolak; HTTP 403 menyimpan 9,6 MB, gagal/jeda bertahan setelah force-stop/update, retry memvalidasi parsial dan 42%→MP4 selesai (decode penuh lulus). MP3 akhir 128 kbps dengan creator/remixer dan artist kosong, cover 800×450 ±57 KB; ukuran root 84,6 MB sesuai 88.733.655 byte, dua hasil uji. Final 27 riwayat = 25 lama + audio/video sesi ini, antrean kosong. Tidak memakai emulator.

Sinkronisasi timestamp lirik, artis nyata pada sumber NEGORO/PINGAL, live platform lain/SAF/16 KB belum diuji batch ini. Rincian akar masalah, file, fallback, hasil dan batas: [SYSTEM_METADATA_RESUME_2026-10-13](SYSTEM_METADATA_RESUME_2026-10-13.md).


## Lirik dan SoundCloud 1.0.10–1.0.11 — 13 Oktober 2026

Dengan skill tubeflow-dev, jalur exact/fallback lirik disamakan pada ±2 detik, durasi kosong ditolak, LRC timestamp/offset diperiksa dan status tidak_cocok dipisahkan dari tidak_ditemukan. Kredit performer/track eksplisit pada deskripsi harus cocok dengan judul dan unik; sumber Lamunan memperoleh Sasya Arkhisna tanpa memakai uploader sebagai artist. SoundCloud structured track dibersihkan memakai helper raw title yang sama untuk mencegah versi Slowed/Live ganda.

Signed 1.0.11/code12, Room v8, keystore sama: 112 Kotlin, 61 Python pada batch, lint debug/release 0 masalah, seluruh audit APK×3/AAB lulus. APK final terpasang dan hash cocok; tidak uninstall/reset. User mengonfirmasi telah membersihkan riwayat selama build, empat file sebelumnya tetap ada. Tes Lamunan pada 1.0.10 dikonfirmasi sinkron oleh pengelola; SoundCloud Ya Sudahlah pada 1.0.11 selesai MP3+lirik+cover, decode penuh lulus, sumber/thumbnail konsisten. Artis sumber kosong/identitas provider ambigu tetap fallback kosong, creator terpisah. Bukan klaim semua versi/platform selesai. [Laporan akar masalah dan hasil](LYRICS_PLATFORMS_2026-10-13.md).

**Hasil auditori SoundCloud:** pengguna melaporkan delay lirik sekitar 0,5 detik; sinkronisasi Ya Sudahlah belum lulus, dicatat sebagai BUG-033. Lamunan tetap dikonfirmasi sinkron secara terpisah.


**Final 1.0.12/code13:** 116 tes Kotlin, lint debug/release 0 masalah; 61 Python pada batch (engine tidak berubah), signed APK×3/AAB/audit/bundletool lulus. ARM64 SHA256 `e6fe701f96de98c83c0ab4cca598b0b71e02978d9df30c54bd44d504ba83d60a` cocok APK terpasang. Riwayat SoundCloud bertahan melalui update/restart; link bersih beropsi sama ditolak sebagai sudah diunduh (BUG-034), antrean kosong, enam file/97.031.658 byte tetap ada dan Settings 92,5 MB pada root TubeFlow. Tie skor lirik memakai durasi terdekat, tanpa mengubah LRC yang telah tersimpan; delay tetap 0,5 detik menurut pengguna, BUG-033 terbuka. [Hasil dan batas lengkap](LYRICS_PLATFORMS_2026-10-13.md).

## Checkpoint terbaru: Smart Media1.1.0 dan uji Lamunan — 13 Oktober 2026

> **Catatan 13 Okt (sesi 1.1.1):** bagian ini adalah status saat 1.1.0/code14; APK terkini kini **1.1.1/code15** — lihat [bagian dashboard 1.1.1](#dashboard-ringkasan-media-dan-penguatan-cari-ulang-lirik--rilis-111-13-oktober-2026) di bawah. Angka 136 Kotlin di sini berlaku untuk batch 1.1.0.

Bagian1.0.12 di atas adalah sejarah, bukan status APK terkini. [Laporan Smart Media](SMART_MEDIA_UPGRADE_2026-10-13.md) mencatat upgrade1.1.0/code14, Room9, katalog/dashboard, tag audio, rename pasangan media–LRC, koreksi per lagu dan perbaikan entrypoint bootstrap. Build final:136Kotlin/76Python, lint debug/release0issue, signedAPK×3/AAB dan audit/bundletool lulus. Ya Sudahlah dikonfirmasi sinkron pengelola setelah koreksi khusus rekaman−2detik; bukan aturan untuk seluruh lagu.

Pengecekan ulang sesi ini memastikan APK1.1.0 terpasang dengan hash final `06da7742a451ad27ae8dd88b15c31fad0b047e2bffcead4de1a6f9482e45eda6`. Kode lirik terkait belum berubah setelah tes terakhir; laporan136JUnit/0gagal dan lint0issue diperiksa, tanpa build ulang/downgrade atau perubahan sumber.

Lamunan aktual: MP3 hash `f784d0bdc337be8b3e93147b0e83fe7ab1dbfe8e7992923493a3ec009ae4c7aa`,259,392detik/128kbps/Sasya Arkhisna/JPEG800×450. LRC hash `e642dfb16226e4263b25a367023050381a3312c79ee306eae530e70c92f62b4a`,31timestamp16,62–253,30detik, valid dan byte-identik dengan LRC yang dahulu dikonfirmasi sinkron. Dialog Lamunan menunjukkan **Waktu asli**, dibatalkan tanpa simpan; Ya Sudahlah tetap−2detik/hash terkoreksi sebelumnya. File audio/lirik tidak diubah.

**Tahap aktif berikutnya:** pengelola menguji Lamunan lewat Poweramp pada awal/tengah/akhir dan melaporkan arah/besar/ketetapan selisih. Sinkronisasi auditori terbaru belum dikonfirmasi; jangan menggeser Lamunan−2detik berdasarkan SoundCloud atau mencari ulang lirik sebelum baseline diperiksa. Bila offset tetap terbukti, koreksi hanya file ini; drift memerlukan tinjauan kandidat. [Bukti, cakupan dan panduan uji](SMART_MEDIA_UPGRADE_2026-10-13.md#pengecekan-ulang-sebelum-uji-lamunan--13-oktober-2026).

## Rekonsiliasi backlog bug, roadmap dan release — 13 Oktober 2026

Pengelola menanyakan pekerjaan yang tertinggal setelah banyak perubahan. Pengecekan Lamunan sebelumnya bukan audit seluruh aplikasi. Sesi ini menginventarisasi37ID bug,9OPT, checklist release dan laporan historis/terbaru, lalu mencocokkan area backlog dengan kode aktual. Ini **review status dan risiko statis**, bukan uji semua37bug, benchmark, atau implementasi fitur baru. Tidak ada build/audit/test task aktif pada sampel proses saat pemeriksaan; tidak memulai ulang build final yang sudah lulus. Hanya dokumentasi diubah; perubahan sumber pengguna tetap dipertahankan.

### Pekerjaan yang sudah ada, jangan diulang dari nol

- Build/signing/distribusi1.1.0/code14/Room9 dan APK final di HP sudah tersedia;136Kotlin/76Python serta lint/audit paket adalah bukti batch final, bukan eksekusi ulang sesi ini.
- Snapshot identitas/creator/platform, metadata native/tag audio, Auto Folder berbukti, cover cache, validasi resume dan alias URL telah diimplementasikan. YouTube/SoundCloud/download MP3/MP4 serta playback diuji terbatas; tidak menjamin seluruh extractor/codec.
- OPT-004 tersedia: koreksi per lagu dengan baseline/reset/jurnal/read-back. Ya Sudahlah−2detik dikonfirmasi sinkron. Lamunan memiliki file valid/hash identik dan Waktu asli; auditori terbaru masih menunggu pengelola.
- OPT-006 dan009 telah diimplementasikan/diuji. OPT-005 sudah mempunyai cache respons LRCLIB dan OPT-008 mempunyai budget/prune serta clear cache lirik, tetapi keduanya belum seluruh lingkup roadmap.
- BUG-027 tidak lagi menunggu keyguard: laporan perangkat1.0.4 memuat Mix SATRU2/25entri dan satu download selesai. BUG-026 sudah diuji dengan dialog penggantian/reuse row pada MP3 penguji1.0.9. BUG-029 membuktikan resume nyata dari9,6MB setelah HTTP403; bukan simulasi mematikan jaringan pada HP.

### Pekerjaan kode yang benar-benar belum selesai

1. **BUG-016 / OPT-002 (prioritas keamanan data):** refetch lama dapat menimpa LRC saat backup null dan tidak memverifikasi rollback. `LyricEditor` aman pada jalur koreksi tidak otomatis melindungi refetch. Perlu penyatuan operasi dan fault injection open/partial-write/close/read-back/rollback gagal, tanpa menguji destruktif pada lirik pengguna.
2. **BUG-008 /003:** recovery/awal worker melepas jurnal walau delete gagal; create/rename→journal dan proses mati sebelum finish masih perlu diaudit. Simpan jejak URI sampai sukses terverifikasi; jangan menganggap komit sidecar atomik menutup seluruh jalur.
3. **BUG-002 /004 /008:** DAO sidecar/status lirik belum generation-guarded; stop WorkManager/thread engine tidak menjamin seluruh cleanup coroutine berhenti. Perlu ownership dan koordinasi edit/refetch/rename/delete serta uji race terkontrol.
4. **BUG-010 /015 / OPT-002:** periksa nama dasar aktual secara penuh dan induk audio SAF saat query parent tidak tersedia; akhiran `.lrc` saja tidak membuktikan pasangan tepat.
5. **OPT-001/003/007:** record ID/atribusi persisten, pemilihan/pratinjau kandidat dan diagnostik error yang bisa disalin dengan redaksi rahasia belum tersedia. “Tambah lirik” untuk audio tanpa opsi lirik awal juga belum tersedia melalui refetch (guard memerlukan subtitles tidak kosong).
6. **OPT-005/008:** cache snapshot metadata dengan engine/TTL, tes coalescing/benchmark request+waktu, angka cache vs parsial terpisah dan cleanup aman saat aktif/pause/restart belum lengkap. FAILED sengaja mempertahankan parsial untuk retry; kebijakan cleanup tidak boleh membuangnya diam-diam.

### Cakupan perangkat/checklist yang masih tertunda

| Kelompok | ID / cakupan | Sisa bukti |
| --- | --- | --- |
| Penyimpanan SAF dan cleanup | BUG-001/003/008/010/011/012/015/016/020 | Audio+LRC normal/root ukuran pernah diuji; video/subtitle SAF, collision stem, provider menolak, grant dicabut/sibling/subfolder/depth/ukuran null, process death dan gagal publish/rollback belum menyeluruh |
| Ownership/cancel/duplikat | BUG-002/004/005/019/025/026/029 | Resume/replacement normal teruji; jeda/lanjut cepat/resumeAll, cancel saat conversion/copy, parsial setelah remove/restart, duplikat aktif/pause/retry sibling dan hasil dihapus manual masih perlu fixture |
| Inspeksi/UI/format | BUG-006/007/009/014/021/022/023/024/027 | Retry URL sama setelah gangguan jaringan/klik ganda (BUG-006 dijadwalkan paling akhir), share tepat satu inspeksi, pergantian subtitle/lirik, label completedAt dan matriks codec/preset/notifikasi belum penuh. BUG-027 normal sudah ada bukti perangkat |
| Identitas/lirik/platform | BUG-013/028/030/031/032/033/034/035 | Sumber DJ/Lamunan/YaSudahlah dan duplikat tertentu teruji; lirik baru Lamunan menunggu auditori, NEGORO/PINGAL perlu link sumber, slowed/lintas pemutar/offset metadata/thumbnail kedaluwarsa/embedded recovery/platform lain belum penuh |
| Perbaikan runtime1.1.0 | BUG-036/037 | Rename MediaStore dan bootstrap/download video final terbukti; bukan klaim rename pasangan semua provider SAF atau semua kombinasi extractor |

BUG-017/018 (progres/Mix) mempunyai bukti host/perangkat untuk skenario yang dilaporkan; sisa stress/seluruh platform tetap mengikuti checklist umum, bukan fitur belum dimulai. SemuaID1–37 tercakup pada kelompok di atas dan bagian ini.

### Pekerjaan release di luar build

- Perangkat Android33/35+/17, native runtime16KB, seluruh ABI/codec, background/lock/notifikasi ditolak, storage penuh dan process death tiap fase masih memerlukan matriks fisik; audit ELF/zipalign bukan penggantinya.
- Inventaris lisensi/transitive runtime dan corresponding source FFmpeg GPLv3 belum lengkap; notice WebP yang ada tidak menyelesaikan semua kewajiban payload. Backup signing eksternal perlu konfirmasi pengelola, jangan mencetak kredensial.
- Validasi CI remote, kebijakan toko/targetAPI saat publikasi dan upload/publishing belum dibuktikan/diotorisasi oleh audit ini. Tidak menjalankan push/upload/deploy.
- Benchmark ribuan media, request/cache timing, FPS/daya belum diukur; tidak mengklaim peningkatan kecepatan.

### Urutan lanjut yang disarankan

1. Tunggu konfirmasi auditori Lamunan dari pengelola, tanpa cari ulang/koreksi global.
2. Kerjakan keamanan refetch+rollback/jurnal (BUG-016/008/003) dan koordinasi ownership (BUG-002/004); tambah regresi sebelum build kandidat berikutnya.
3. Uji perangkat memakai fixture milik penguji: gagal simpan/delete/rename, collision, process death dan cancel/lanjut cepat. Retry inspeksi jaringan BUG-006 paling akhir sesuai pilihan lama, tanpa memutus koneksi pengguna tanpa koordinasi.
4. Setelah dasar aman, lanjutkan kandidat manual/atribusi (OPT-003/sisa001/002), diagnostik tersanitasi (OPT-007), cache/ruang+benchmark (sisa005/008).
5. Lengkapi matriks fisik/lisensi/backup sebelum klaim siap publikasi; kandidat signed lokal tidak sama dengan release toko terverifikasi.

Dokumen diselaraskan: [BUG_FINDINGS](BUG_FINDINGS.md) menghapus baris028–030 ganda dan mengoreksi status risiko/hasil lama; [roadmap](DEVELOPMENT_ROADMAP.md) memuat tabel aktual seluruhOPT; [RELEASE](RELEASE.md)/[VALIDATION](VALIDATION.md) mengarahkan pembaca ke produksi1.1.0, bukan klaim lama “belum signing/belum terpasang”. Checklist historis tidak dicentang massal dan temuan kode di atas belum diperbaiki pada sesi ini.

## Dashboard Ringkasan media dan penguatan cari ulang lirik — rilis 1.1.1, 13 Oktober 2026

Permintaan pengelola: lanjutkan perbaikan Cari ulang lirik dan tambah grafik donat/batang/garis pada dashboard, tanpa dependency/layanan/pemutar baru dan tanpa mengubah schema Room 9. Pengerjaan, kontrak donat/batang/garis (media AVAILABLE saja, transisi COMPLETED per tanggal kalender 7/30/90 hari, hari sebelum `analyticsSince` bukan dianggap 0, tanpa tren byte historis), serta pengamanan refetch (`JournaledTextReplacement` wajib backup/jurnal/read-back/rollback, mutex bersama `MediaFileOperations`, verifikasi stem/nama/isi sebelum mengganti) semuanya terdokumentasi pada [laporan DASHBOARD_LYRICS_2026-10-13.md](DASHBOARD_LYRICS_2026-10-13.md).

**Hasil yang benar-benar diverifikasi (sesi dokumentasi 13 Okt):**

- **Host:** 145 tes Kotlin 0 gagal; **76 tes Python dijalankan ulang → `OK` (23,173 detik)**; lint debug/release “No issues found”; `sha256sum -c SHA256SUMS` semua OK; audit native (255/255/257 ELF + AAB 1.024 ELF, 0 gagal 16 KB) dan ZIP reflektif (13 kelas, 0 gagal) ketiga APK; `zipalign -c -P 16 4` OK; `apksigner` v2 verified, sertifikat `ebe3b40e…` identik rilis sebelumnya; AAB `jarsigner -verify` jar verified dengan peringatan self-signed/timestamp/POSIX wajar. Tidak ada build ulang — audit dijalankan pada artefak yang sudah ada karena source tidak berubah sesudah build (`find app/src scripts -newer <APK>` kosong).
- **Artefak:** `app/build/outputs/distribution/1.1.1/` — ARM64 **63.025.099 byte, SHA256 `437d42759c2b953587f885d70b0a5ffd44fee21dc47dd0774fa088ee847405c0`** (APK×3/AAB/cert/`release.json`/`SHA256SUMS`).
- **Perangkat (Realme RMX1821, Android 10):** update `install -r` 1.1.0→1.1.1 code15 sukses tanpa uninstall/clear data; hash `base.apk` terpasang identik artefak. UI dashboard diperiksa via UIAutomator + screenshot 09:59–10:03: donat kondisi kosong (“0 media tersedia”, “5 file tidak dapat diakses tidak masuk grafik”), batang 7 hari (puncak 2/hari, ringkasan 2/1/0, rasio 66%, pencatatan sejak 13 Okt 08:21, hari pra-pencatatan bertanda abu-abu), garis akumulasi total 2, sumber “YouTube · 2”. Bukti: [donut](images/dashboard-1.1.1-donut.png), [bars](images/dashboard-1.1.1-bars.png), [line](images/dashboard-1.1.1-line.png); rincian di [DEVICE_TEST.md](DEVICE_TEST.md).

**Batas cakupan (jangan dilebih-lebihkan):**

- **Retensi media `not established`** — pengelola membersihkan media selama build; donat hanya diuji pada kondisi koleksi kosong, bukan dengan media nyata.
- **Refetch lirik:** fault injection host lulus, tetapi kegagalan provider Android, refetch live di perangkat, dan proses mati belum diuji; **Lamunan tidak di-refetch** (file audio/LRC-nya tidak diubah, baseline sinkron sebelumnya tetap berlaku).
- Periode 30/90 hari tercatat diperiksa via UIAutomator menurut `release.json`, tetapi screenshot tersimpan hanya untuk 7 hari. Tidak ada benchmark FPS/daya/kecepatan, tidak ada uji unduhan live pada batch ini, dan tidak ada sinkronisasi lirik baru yang didengarkan.
- Temuan terbuka pada [rekonsiliasi](#rekonsiliasi-backlog-bug-roadmap-dan-release--13-oktober-2026) — BUG-008/003 (create→journal, proses mati), generation guard sidecar/status lirik, serta matriks perangkat/lisensi — **tetap terbuka**; batch ini hanya memperkuat jalur refetch (BUG-016/OPT-002) di tingkat host. Checklist historis tidak dicentang massal.


## Pemeriksaan perubahan pengguna, kredit penyanyi dan Auto Folder1.1.2 — 13 Oktober2026

Commit2415049/02ce4ed serta APK1.1.1 diverifikasi. Dashboard koleksi nyata50audio/259.937.411byte/7LRC cocok;43metadata parsial mengungkap sisa penamaan yang belum tuntas. LinkrEjSkBl47OY yang disebutPINGAL sebenarnyaSATRU2live dengan labelTitle/Voc. SourceMetadata kini memakai kredit performer eksplisit yang cocok dengan rawtitle; Cipt/produser/channel bukanartis (BUG-038).

Pengelola memilih folder perartis/remixer diTubeFlow. Jobplaylistbaru memakai policyperformer danhelperfolderbersama sumber/nativeprobe; remixerteridentifikasi boleh menjadi folder ketika artistasli belum pasti, tetapi tidak menjadi artistmetadata/LRCLIB (BUG-039). Legacy artist/mix dan schema9 dipertahankan. Panduan skill invariants diperbarui; filelama tidak dipindahkan otomatis.

149Kotlin/76Python, lint0, signedAPK×3/AAB serta audit/bundletool lulus. SuitePython pertama menemukan raceESRCH padahelper /proc tes proses; helper diperbaiki dan suiteulangOK. Buildfinal11m11s, ARM64 63.025.815byte/SHA256d7210fba…, terpasang update-r diRealmeAndroid10;57file lama nama/hash sama. Mixuser25entri; seeddipilih sendiri menghasilkan Opus4.114.238byte/247,800s dancover800×450 diTubeFlow/maman-fvndy-rmx, judul/version/remixer benar, decode penuh exit0. Antrean kosong dan57filelama utuh. Uji sumberduet final juga lulus: SATRU2Live terbit dengan tag/folder DennyCaknanFTHappyAsmara, creatorDC.PRODUCTION terpisah, decode penuh dan hash filelama lulus; [laporan rinci](SOURCE_REVIEW_2026-10-13.md).


## Checkpoint 13 Oktober 2026 — koleksi dan cleanup1.1.3

Pilihan terbaru menggantikan folder per artis: satu folder untuk semua pilihan Mix/album. Policy collection mempertahankan snapshot folder melalui metadata/retry. Cleanup SAF bottom-up menjaga root dan melaporkan kegagalan; grant root tepat diminta sebelum pembersihan lengkap. 160 Kotlin/76 Python/lint0 lulus; signed build10m8s, audit APK×3/AAB dan bundletool lulus. ARM64 terpasang update-r pada RMX1821Android10, version/code/hash sesuai release. Pengelola meminta install segera dan tes pemakaian sendiri; runtime playlist/cleanup1.1.3 belum diklaim. Lihat [laporan](COLLECTION_CLEANUP_2026-10-13.md).


## Checkpoint 14 Oktober 2026 — upgrade 1.1.4

Diterapkan dari baseline 1.1.3 karena source perangkat lain belum dipush. Kredit koleksi hanya menggunakan identitas terverifikasi; versi filename memakai kurung. Mutasi sidecar dibatasi generation dan cleanup gagal dijurnal privat. UI empat tab, aksen sampul, satu MediaPlayer foreground dan timeline LRC nyata. 175 Kotlin/78 Python/lint 0, build signed APK×3/AAB dan audit lulus; update ARM64 ke HP Android 10 berhasil tanpa reset. Room 9, dependensi dan engine tidak berubah. Cakupan runtime dan backlog: [laporan](UPGRADE_1_1_4_2026-10-14.md).


## Checkpoint 14 Oktober 2026 — lirik manual1.1.5

Input detik absolut per lagu, pemilihan ID LRCLIB/preview/peringatan, pencarian setelah rename, publisher bersama refetch, urutan tab baru, rata tengah dan kontrol kanal diterapkan. 184 Kotlin/78 Python/lint0 serta signed build/audit/bundletool lulus. Pengelola memilih/simpan SATRU2; payload LRC10455242 cocok sumber. Final ARM64 terpasang, hash cocok, 61file dipertahankan saat update. Karaoke AI masih kajian. [Laporan](MANUAL_LYRICS_1_1_5_2026-10-14.md).


## Checkpoint 14 Oktober 2026 — antrean putar dan Koleksi (pengembangan)

Pengalaman player dilengkapi tanpa mengganti engine: `core/PlaybackQueue.kt` (mesin antrean murni: posisi, add-next/add, remove, shuffle dengan urutan asli pulih, repeat OFF/ALL/ONE) dipakai `PlayerViewModel` yang tetap satu `MediaPlayer` pada satu HandlerThread. Semua mutasi antrean dijalankan di thread engine. Mini player mendapat tombol next; full player mendapat previous/next, shuffle, repeat dan dialog daftar antrean (ketuk = lompat, keluarkan item non-aktif). Tombol next mengikuti antrean nyata; completion lanjut otomatis, repeat ONE memutar ulang tanpa engine baru, akhir antrean berhenti. Di Koleksi: tap kartu = putar (audio inline, video intent pemutar perangkat), aksi sekunder pindah ke menu tiga titik (play next, tambah antrean, detail, lirik, atur waktu, rename, hapus), plus "Putar semua"/"Acak". Regresi host: `PlaybackQueueTest` 10 tes (termasuk bug indeks unshuffle yang ditemukan tes dan diperbaiki). 207 Kotlin/78 Python/lint 0 masalah/assembleDebug APK×3 lulus.

Batas awal: antrean tidak dipersistensi antar sesi; video tetap memakai pemutar perangkat; optimasi RAM karaoke tidak diubah (penurunan RAM tidak diukur). Kondisi "foreground-only tanpa service" sudah digantikan checkpoint pemutar ultimate di bawah.


## Checkpoint 14 Oktober 2026 — pemutar ultimate, background playback — 1.1.6 (20) build ulang

Atas permintaan pengelola setelah tes perangkat 1.1.6 dinyatakan sesuai: **kontrol kanal L/R dihapus** (termasuk `AudioChannel.kt`), **lirik dibuat tampil otomatis** dengan chip **Sampul/Lirik** yang jelas (penyebab "hilang": tombol ikon kecil), **tap satu lagu = seluruh daftar masuk antrean** (Koleksi & Riwayat, mulai dari item yang diketuk), **pemutaran tetap hidup saat layar mati** via `PlaybackService` (foreground `mediaPlayback`, MediaSession, notifikasi MediaStyle 4 aksi, partial wake lock hanya saat putar), plus **equalizer** (`PlayerEqualizer` membungkus audiofx bawaan, preset/level tersimpan, aman bila perangkat tidak mendukung) dan **kecepatan putar 0,25–3×**. Auto-pause saat app ke latar dihapus; fokus audio dipertahankan (pause saat noisy/telepon). Semua mutasi tetap pada satu HandlerThread engine; tidak ada player kedua.

Host: 207 Kotlin/78 Python/lint debug+release 0, signed APK×3/AAB (`build_release.sh`) lulus. ARM64 70.408.643 byte SHA256 `08fa5da2…` terpasang update-r di Realme Android 10, hash base.apk cocok, 62 file publik utuh. Perangkat membuktikan: antrean 51 item dari satu tap, lirik otomatis + chip, lompat antrean, equalizer 5 band + preset "Normal", speed 1,5×, **layar mati → state PLAYING terus berjalan**, `media dispatch pause/play/stop` bekerja (jalur notifikasi/headset) dan stop membersihkan service/notifikasi. Rincian dan batas cakupan: [DEVICE_TEST](DEVICE_TEST.md#upgrade-pemutar-ultimate--antrean--14-oktober-2026). Belum diuji: kontrol headset/Bluetooth fisik, perubahan suara equalizer secara auditori, baterai/durasi panjang, persistensi antrean antar sesi (belum ditambahkan).


## Checkpoint 15 Oktober 2026 — pemulihan Koleksi, notifikasi, karaoke, autoplay (20) build ulang

Laporan pengelola setelah hapus data aplikasi: file lama hilang dari Koleksi/ukuran folder 0, ikon status bar kotak putih, progressbar notifikasi mentok di akhir, detail karaoke tidak ikut berubah saat ganti lagu, plus permintaan auto-play saat headset/TWS konek. Akar Koleksi: file `mix-*` ditulis via SAF/versi lama (`owner_package_name` kosong) sehingga tak terlihat query MediaStore tanpa izin; grant SAF persisten ikut hilang saat data dihapus dan `scan()` tidak memeriksa grant folder bawaan. Perbaikan: `MediaRepository.scan()` memakai grant persisten `Download/TubeFlow` lewat jalur dokumen (tanpa memindah tujuan output) + dedup folder+nama+ukuran; tombol **Pindai folder** membuka pemilih folder otomatis bila akses tidak ada (`MediaLibraryViewModel.grantFolder`); ukuran folder memakai grant yang sama. Notifikasi: small icon baru `ic_stat_playback` (siluet monokrom; ikon berwarna tampil kotak putih) dan MediaSession kini memuat metadata durasi + posisi reset 0 tiap lagu baru (progressbar sistem sempat "penuh" karena durasi absen). Karaoke: judul dialog selalu mengikuti lagu terpilih dan panel proses lama diberi label sumber. Auto-play: `AudioDeviceCallback` tanpa izin melanjutkan musik saat output kabel/Bluetooth/USB tersambung.

Host: 207 Kotlin + 78 Python 0 gagal, lint debug/release 0, signed APK×3/AAB (`build_release.sh`); audit native 257/257/259 + 1.038 ELF 0 gagal, ZIP reflektif 13 kelas 0 gagal, apksigner v2 Verifies. ARM64 70.415.371 byte SHA256 `b01188725889e146c39f2c13b2f61843ce5cedbdefe36fdb7c51362a6dc90809` terpasang update-r di Realme Android 10. Perangkat: Koleksi pulih **2 → 52 media / 267,3 MB** (1 file memang sudah terhapus), ikon `ic_stat_playback` pada notifikasi, bar 00.28/05.36 saat lagu mulai, dialog karaoke mengikuti lagu terpilih dengan label sumber proses lama. [Bukti dan batas](DEVICE_TEST.md#perbaikan-koleksi-notifikasi-karaoke-dan-autoplay--15-oktober-2026). Cuplikan karaoke selesai penuh setelah checkpoint: pratinjau `Dengarkan instrumental` berbunyi dan `Simpan cuplikan FLAC` menerbitkan 3,57 MB ke `Download/TubeFlow/` yang otomatis masuk Koleksi (52 → 53). Belum diuji: auto-play headset/TWS fisik dan jalur pemilih folder saat grant benar-benar hilang.


## Checkpoint 15 Oktober 2026 — legacy storage + engine proses untuk auto-play (lanjutan)

Dua laporan pengelola: scan Koleksi tetap nol setelah hapus data meski izin baca disetujui, dan auto-play headset mati saat aplikasi ditutup. Akar pertama: Android 10 scoped storage menyembunyikan baris MediaStore `owner=NULL` (file lama SAF/versi lama) meski `READ_EXTERNAL_STORAGE` granted — dibuktikan dari `content query` shell vs query aplikasi. Fix: `requestLegacyExternalStorage` (dihormati di perangkat API 29), izin baca/tulis klasik maxSdk 29 + `READ_MEDIA_*` untuk 33+, `MediaRepository.scan()` union MediaStore+grant SAF dengan `ScanSummary` jujur, dan `scanIfEmpty` (auto-scan start saat koleksi kosong). Akar kedua: engine `PlayerViewModel` dimiliki activity sehingga `onCleared` mematikan pemutaran saat UI ditutup; kini `PlayerViewModel.obtain()` milik proses + `HeadsetAutoPlayReceiver` manifest + persistensi lagu terakhir.

Host: 207 Kotlin 0 gagal, lint debug/release 0, audit native 257 ELF + ZIP reflektif 13 kelas 0 gagal, apksigner v2 Verifies. ARM64 SHA256 `fac5ecaef0e0b5491f596e3bab90129c6202acacd18fb7f388323d7a19fd9f5c` terpasang update di Realme Android 10; flag legacy + receiver terverifikasi terdaftar, headset tersambung langsung memutar saat app terbuka. [Detail](DEVICE_TEST.md#fix-auto-scan-koleksi-dan-auto-play-saat-app-tertutup--15-oktober-2026-lanjutan). Menunggu uji pengelola: hapus data → Koleksi terisi tanpa pilih folder; tutup app → TWS/headset → auto-play. Limitasi: broadcast manifest `HEADSET_PLUG` dapat dibatasi sebagian OEM saat proses mati.


## Checkpoint 15 Oktober 2026 — batch 3: hapus bersih, auto-play A2DP, sampul notifikasi (lanjutan)

Lima laporan pengelola: baris "file tidak tersedia" tersisa setelah hapus; auto-play tetap mati; notifikasi mini player perlu sampul + aksen; tombol "Pindai folder"/"Impor file" dibuang karena Koleksi sudah otomatis; riwayat membanjiri antrean pemutar. Akar: `delete()` sengaja menyimpan baris `UNAVAILABLE` (kini `dao.remove` + event `DELETED`); receiver hanya mendengar profil headset padahal TWS memakai A2DP (`BluetoothA2dp.CONNECTION_STATE_CHANGED` dikecualikan dari batas broadcast implicit sehingga bisa membangunkan proses mati; `HEADSET_PLUG` kabel tetap dibatasi platform ke receiver runtime); notifikasi tanpa artwork/aksen; `HistoryScreen.onPlay` memainkan seluruh daftar.

Host: 207 tes Kotlin 0 gagal, lint debug/release 0, `build_release.sh` sukses; audit native 257 ELF 0 gagal, ZIP reflektif 13 kelas 0 gagal, apksigner v2 Verifies. Perangkat: hapus file → baris hilang instan + toast "File dihapus."; tombol scan/impor hilang; notifikasi menampilkan sampul besar + progress bar aksen sampul. [Bukti dan batas](DEVICE_TEST.md#fix-hapus-baris-auto-play-a2dp-sampul-notifikasi-tombol-koleksi-riwayat-vs-antrean--15-oktober-2026-batch-3). Menunggu uji pengelola: TWS/headset saat aplikasi benar-benar ditutup (proses mati) untuk memastikan broadcast A2DP membangunkan aplikasi.


## Checkpoint 15 Oktober 2026 — antrean auto-play isi koleksi utuh (batch 4)

Laporan pengelola: auto-play sudah jalan, tetapi antrean putar hanya berisi 1 file sehingga diam setelah lagu selesai. Akar: `autoPlayOnDevice()` melanjutkan lagu terakhir lewat `play(track)` → `startQueue(listOf(track))`. Fix: auto-play kini mengisi antrean dengan **SELURUH koleksi audio tersedia** (query baru `MediaDao.availableAudio()`) mulai dari lagu terakhir — fallback awal koleksi bila lagu terhapus — lewat perencana murni `AutoPlayQueue.plan()` yang diuji host; lagu aktif yang masih siap dilebarkan antreannya **tanpa reload** (posisi putar aman), dan koleksi kosong jatuh ke perilaku lama.

Host: 213 tes Kotlin 0 gagal (6 regresi baru `AutoPlayQueueTest`), 78 tes Python OK; `build_release.sh` sukses 16m20s; audit native 257 ELF + ZIP reflektif 13 kelas 0 gagal; apksigner Verifies (sertifikat produksi `ebe3b40e…`). Lint debug/release: 1 warning pralahir `SelectedPhotoAccess` (izin media visual Android 14+, di luar perubahan ini — koreksi klaim "lint 0" sebelumnya). Versi **1.1.7 (code21)**, ARM64 SHA256 `a7721a5534278d0de2cf98d11526d2bb994f9285b28e4bab2e07f3f82babb821` terpasang `install -r` di Realme Android 10, base.apk cocok, tanpa FATAL. [Detail](DEVICE_TEST.md#fix-antrean-auto-play-isi-koleksi-utuh--15-oktober-2026-batch-4). Menunggu uji pengelola: tutup app → sambung TWS → antrean penuh (jumlah = koleksi, bukan 1); trigger headset tidak bisa dipalsukan via ADB (protected broadcast) dan layar terkunci passcode.


## Checkpoint 15 Oktober 2026 — UI pemutar: swipe, alat lirik, aksen sampul, antrean, EQ Otomatis (batch 5)

Lima permintaan pengelola: swipe sampul↔lirik; menu Cari lirik + Editor lirik di lirik kosong (tanpa bolak-balik ke Koleksi); latar lirik aksen sampul; antrean lebih premium; equalizer Otomatis agar bass bulat di semua musik. Implementasi: `HorizontalPager` (chip Sampul/Lirik tetap; swipe menang atas auto-switch), tombol **Cari lirik** (alur `LyricsSearchDialog` yang sama) + **Editor lirik** (`LyricTimingDialog` yang sama) via `MediaLibraryViewModel.openLyricsForTrack`/`itemFor` dengan dialog dihost di jendela pemutar; gradien latar memakai `rememberCoverAccent`; dialog Antrean dengan sampul per lagu + kartu aktif beraksen "Sedang diputar"; kurva `AutoEq` (bass +5 dB @60 Hz, mid −1 dB, treble +2,5 dB, per frekuensi tengah band, clamp rentang) menjadi preset bawaan **Otomatis (bass bulat)** untuk semua lagu (pilihan pengguna tetap dipertahankan).

Host: 220 Kotlin 0 gagal (7 regresi `AutoEqTest`), 78 Python OK, `build_release.sh` sukses 13m58s, audit native 257 ELF + ZIP reflektif 0 gagal. Lint: 2 warning di luar perubahan ini (`SelectedPhotoAccess` pralahir; `GradleDependency` = pin onnxruntime 1.23.2 disengaja karena 1.31.0 gagal JNI di perangkat target). **1.1.8 (code22)** ARM64 SHA256 `622796f208b4089210089fa70b54344bf87245740a1a40c8b06e8d41a2f7a963` terpasang update-r di Realme Android 10, base.apk cocok, tanpa FATAL. [Detail](DEVICE_TEST.md#upgrade-ui-pemutar-swipe-sampllirik-alat-lirik-aksen-sampul-antrean-eq-otomatis--15-oktober-2026-batch-5). Catatan build: disk sempat 100% (`No space left on device`) — hanya intermediates/scratch yang dibersihkan, 21 artefak rilis di `distribution/` (7,5 GB) dipertahankan; perlu keputusan pengelola soal retensi artefak lama. Menunggu uji pengelola: swipe, alat lirik di pemutar, antrean baru, dan EQ Otomatis (kualitas suara auditori).

**Cleanup storage 15 Okt 2026:** atas permintaan pengelola ("storage membengkak"), **seluruh artefak `app/build/outputs/distribution/` (21 rilis 1.0.0–1.1.6, 140 file, 7,5 GB) dihapus**. Hash SHA256 + ukuran tiap file tersimpan di [DISTRIBUTION_ARCHIVED_SHA256.txt](DISTRIBUTION_ARCHIVED_SHA256.txt) — rilis lama tetap dapat diverifikasi/dibangun ulang dari hash ini. Rujukan path `distribution/…` pada catatan rilis sebelumnya bersifat historis. APK release terkini (1.1.8 di `app/build/outputs/apk/release/`) tetap dipertahankan. Disk: 9,9 GB → 17 GB bebas.


## Checkpoint 15 Oktober 2026 — batch 6: preferensi acak/ulangi, tab Riwayat, tata letak pemutar, mini player ramping

Delapan laporan pengelola: auto-play headset tidak langsung jalan sebelum aplikasi dibuka; acak dan ulangi playlist selalu kembali mati tiap mulai baru; ubah nama hanya mengubah baris kedua kartu; Riwayat menumpang menu lain; tombol Sampul/Lirik tidak perlu lagi; karaoke dan antrean sebaiknya sejajar dengan acak/ulangi; kontrol koleksi sebagai tombol ikon; mini player notifikasi lebih ramping.

Perubahan kode: (1) **preferensi acak/ulangi tersimpan** di `player-fx` dan diterapkan ke setiap antrean BARU lewat `PlaybackQueue.withPreferences()` (batch 4–5 selalu memulai antrean dengan `Repeat.OFF`/tidak acak) — niat eksplisit "Putar semua"/"Acak" hanya berlaku untuk antrean itu, sedangkan auto-play dan tap lagu mewarisi preferensi; (2) **rename ikut mengubah judul katalog** (`MediaRepository.renamedRow()` menyetel `title` = nama berkas baru dan mengambil ulang waktu modifikasi provider) sehingga judul di kartu, pemutar, dan pencarian lirik memakai nama baru, dan pemindaian berikutnya tidak mengembalikannya; (3) **Riwayat menjadi tab navigasi sendiri** (indeks 4, Pengaturan pindah ke indeks 5) — chip "Riwayat" di Koleksi dihapus; (4) **chip Sampul/Lirik dihapus** (swipe tetap, geser manual tetap menang atas auto-switch); (5) **satu baris kontrol** acak–ulangi–karaoke–antrean (badge jumlah lagu) di pemutar penuh; (6) **kontrol koleksi berupa tombol ikon** putar/acak/cari (kolom pencarian muncul saat diminta, ditutup = pencarian dibersihkan); (7) **mini player aplikasi ramping** (sampul 40dp, padding 2dp, tombol karaoke pindah ke pemutar penuh) dan **notifikasi ramping dengan sampul tetap tampil**: laporan lanjutan pengelola ("masih lebar, sampulnya hilang") menolak varian tanpa sampul, jadi `PlaybackService` memakai `Notification.DecoratedMediaCustomViewStyle` + tata letak sendiri `res/layout/notification_playback.xml` (sampul 40dp, judul + artis satu baris, garis progres 2dp beraksen `@color/accent`, aksi media sebelumnya/putar/berikutnya/tutup tetap). Susunan ini sengaja sama dengan mini player di aplikasi supaya notifikasi dan aplikasi konsisten; sampul hilang hanya saat trek benar-benar tidak punya artwork. Catatan rujukan: `invariants.md`/`ui-design-system.md` menyebut notifikasi dengan "sampul besar" — kini sampul kecil 40dp, jadi teks rujukan itu perlu disesuaikan; (8) auto-play latar **mengulang permintaan fokus audio sekali** (700 ms) sebelum melaporkan gagal, dan `HeadsetAutoPlayReceiver` mencatat pemicu di logcat `TubeFlow-autoload` untuk diagnosis lapangan.

Host: 221 tes Kotlin 0 gagal (`PlaybackQueueTest` +1 untuk warisan preferensi), `assembleDebug` sukses 1m21s, `assembleRelease` (R8 + lintVital) sukses 3m51s dengan sertifikat produksi (APK ARM64 70.472.547 byte). Bukti notifikasi dari `dumpsys notification --noredact` di perangkat: `android.template=android.app.Notification$DecoratedMediaCustomViewStyle`, `android.contains.customView=true`, `contentView`/`bigContentView` = `id.local.tubeflow/0x7f0b0021` (tata letak `notification_playback.xml`, identik untuk ringkas dan diperluas), `android.compactActions=[0,1,2]`, aksi 4 (Sebelumnya/Jeda/Berikutnya/Tutup pemutar), `color` diambil dari aksen sampul; tanpa FATAL/error RemoteViews di logcat. Batas: lebar notifikasi tetap diatur sistem (aplikasi tidak bisa mempersempit kartu notifikasi) — yang dikendalikan aplikasi adalah isi/tata letaknya, dan itu kini 40dp sampul + dua baris teks + garis progres 2dp. Catatan: sebagian perangkat menandai `android.reduced.images=true`, jadi kehadiran sampul sebaiknya tetap dicocokkan mata pengelola di layar. Perangkat (Realme Android 10): APK rilis ARM64 70.470.699 byte terpasang `install -r` **tanpa kehilangan data** (tanda tangan produksi sama), `MainActivity` terkonfirmasi `mResumedActivity` setelah dibuka. **Belum terverifikasi di perangkat:** tata letak baru (dump aksesibilitas tertahan dialog Norton 360 "Aplikasi mencurigakan" sesaat setelah sideload APK — perlu ditutup manual di HP), auto-play headset/TWS fisik (broadcast `BluetoothA2dp`/`HEADSET_PLUG` protected sehingga tidak bisa dipalsukan via ADB, dan ColorOS membekukan aplikasi latar), serta persistensi acak/ulangi lewat putar-ulang nyata. Saran lapangan: aktifkan autostart/izin latar untuk TubeFlow di pengaturan ColorOS bila auto-play dari proses mati masih diam.


## Checkpoint 15 Oktober 2026 — batch 7: pemutar premium, menu antrean lengkap, notifikasi ringkas, ukuran penyimpanan

Lima permintaan pengelola: (1) naikkan kelas tampilan pemutar dari mini player sampai pemutar penuh, (2) rapikan antrean di pemutar dan lengkapi menunya, (3) notifikasi pemutar dibuat versi ringkas tanpa menu detail tetapi ada tombol tutup, (4) ukuran penyimpanan di Pengaturan tidak boleh menunggu grant folder — Koleksi sudah bisa membaca file langsung, (5) mini player dibuat seperti kartu lagu di Koleksi (round, ukuran mirip, pakai aksen).

Implementasi: **mini player** kini kartu beraksen sampul (radius medium, tepi tipis, sampul 48dp, garis progres beraksen) sehingga sebahasa dengan kartu Koleksi, dengan tiga kontrol tetap (putar/jeda, berikutnya, tutup). **Pemutar penuh**: header dengan pil kecepatan tonal, sampul di atas "pelat" beraksen (bukan kotak melayang), baris konteks "Lagu n dari m · acak/ulangi", slider beraksen, dan cluster kontrol sekunder (acak–ulangi–karaoke–antrean) dalam satu permukaan pill. **Antrean putar**: header menyebut jumlah lagu + mode acak/urutan + mode ulangi, baris menu **Acak / Ulangi / Bersihkan**, dan setiap lagu punya menu tiga titik (**Putar sekarang, Pindah ke atas, Pindah ke bawah, Keluarkan dari antrean**) plus tombol keluarkan cepat; "Bersihkan" hanya menyisakan lagu aktif tanpa menghentikan pemutaran (`PlaybackQueue.onlyCurrent()`), dan pemindahan posisi memakai `PlaybackQueue.move()` sehingga lagu aktif tidak dimuat ulang. **Notifikasi** kembali ke tiga aksi (sebelumnya/putar/berikutnya) tanpa aksi tersembunyi, sementara tombol tutup (X) menyatu di dalam kartu notifikasi (`ic_notification_close.xml` + `setOnClickPendingIntent(ACTION_STOP)`) sehingga tidak perlu membuka detail. **Ukuran penyimpanan**: `DownloadViewModel.measureStorage()` memakai jumlah katalog media (query `totals()` yang sama dengan layar Koleksi) saat tidak ada grant SAF, lalu menandainya `storageCatalogue` agar Pengaturan menampilkan label "Ukuran file di koleksi" — bukan klaim ukuran folder penuh; grant/kartu memori tetap memakai pengukuran folder penuh seperti sebelumnya.

Host: 222 tes Kotlin 0 gagal (termasuk regresi baru `PlaybackQueueTest` untuk `move`/`onlyCurrent`), `assembleDebug` 1m10s, `assembleRelease` (R8 + lintVital, sertifikat produksi) 3m49s. Perangkat: APK rilis ARM64 70.487.783 byte SHA256 `3a3c2e0f8e4e67708e20222416e8b6c90e67d1f182daef43424f8ddb4fe01183` terpasang `install -r` tanpa kehilangan data (versi 1.1.8/code22, `lastUpdateTime` 18:42). **Belum terverifikasi di perangkat:** tata letak baru dan label ukuran koleksi — saat verifikasi dijalankan pengelola memakai HP (aplikasi lain di latar depan), jadi otomasi layar dihentikan; tata letak/fungsi baru masih perlu satu kali lihat di HP.

## Checkpoint 15 Oktober 2026 — 1.1.9: player, antrean lega, penyimpanan reaktif, progres notifikasi

Melanjutkan batch 7 yang sudah ada di source lokal: mini player tetap memakai kartu membulat beraksen sampul seperti Koleksi; pemutar penuh dapat digulir pada layar pendek dan memberi petunjuk swipe; antrean dipindahkan ke panel bawah dengan menu putar sekarang/setelah lagu ini, pindah atas/bawah, keluarkan, acak/ulangi, dan bersihkan berkonfirmasi. BUG-045: ukuran Pengaturan kini mengamati agregat Room sehingga scan/impor/hapus/karaoke ikut memperbarui angka; ukuran null diberi keterangan parsial. Uji HP menemukan BUG-046 (seek saat jeda tidak memperbarui MediaSession), diperbaiki lewat `PlaybackNotifications`: publikasi progres per detik serta seek/kecepatan segera. Notifikasi custom 48dp punya X langsung; tinta X diganti netral karena atribut tema Activity menghasilkan hitam pada drawer gelap OEM. Garis progres custom memakai aksen tema; warna kontrol sistem mengikuti sampul.

Bukti akhir: **228 Kotlin, 79 Python**, lint debug/release **0 error/2 warning** (`SelectedPhotoAccess`, pin ONNX), build signed APK×3/AAB, audit native/ZIP reflektif/zipalign/tanda tangan dan bundletool lulus. **1.1.9/code23 ARM64 70.546.231 byte**, SHA256 `042d64db675617826441da0c0e76e7aab39ecad849d73732aa8279a0d9696af0`, terpasang update-r pada Realme Android 10; `base.apk` cocok. Penyimpanan terbaca **263,8 MB** tanpa grant folder baru, cocok dengan 51 audio fisik/276.635.482 byte. Seek jeda 2:37 = MediaSession 157.042ms; progres PLAYING 4.089→6.101ms; X menghapus sesi/notifikasi; **69/69 file lama hash identik**. Terang/gelap dan antrean satu lagu diuji. Font besar via ADB ditolak ColorOS, jadi belum terverifikasi; Android versi lain/16KB runtime, TalkBack, landscape dan gangguan SAF masih di luar cakupan. [Laporan, foto, akar masalah, dan batas](PLAYER_POLISH_2026-10-15.md).


## Pemutar dan auto-play 1.1.10 / code24 — 15 Oktober 2026

Seekbar penuh dirampingkan menjadi track4dp/thumb12dp dengan target sentuh48dp;
jumlah antrean masuk tombol agar tidak terpotong; pencarian judul/artis mempertahankan
indeks asli. Receiver koneksi, bootstrap foreground sebelum I/O/fokus, izin Bluetooth
sesuai Android dan pemulihan sesi aktif diperbaiki tanpa mengganti engine/Room9.

Host:233 Kotlin/83 Python lulus; signed debug/releaseAPK×3/AAB berhasil;
lint debug/release0error/2warning diketahui, audit signature/native/alignment16KB/
ZIP reflektif serta AAB bundletool lulus. ARM64 produksi70.564.491byte SHA256
`3ff8b5a250a82b3e2aef65a1cf06f9ca2d7200e262b328418ea628e3b3f70d3a`
terpasang update-r di RealmeRMX1821Android10, base.apk cocok;70/70file awal identik.

HP: full player dan jumlah51 terbaca; pencarian nemen→2hasil, pilih→lagu12dari51,
clear/tanpa hasil sesuai. **Kabel sesudah sapu Recent langsung memutar tanpa buka UI**,
dikonfirmasi pengelola; log task removed/koneksi dan PLAYING1.095→3.108ms sementara
Launcher foreground mendukung hasil. Belum diuji TWS/Android12+/process death penuh,
font besar/lightmode versi ini dan runtime16KB. [Laporan dan bukti visual](PLAYER_AUTOPLAY_1_1_10.md).


## Hapus semua tanpa SAF tambahan — 1.1.11/code25, 15 Oktober 2026

BUG-050: UI/backend hanya menerima tree SAF padahal Android10 legacy sudah punya
izin tulis. Capability bersama kini memilih akses filesystem terbatas tepat di
Download/<folder> (API29+legacy+write), atau mempertahankan SAF sesuai kebutuhan.
Adapter memakai FolderCleanup bottom-up, menolak symlink/escape, menjaga root dan
sibling, serta menghapus indeks MediaStore. Room9/dependensi/default tidak berubah.

240Kotlin/83Python lulus; signed/debug APK×3/AAB BUILD SUCCESSFUL4m53s;
lint0error/2warning diketahui, audit artefak dan AAB bundletool lulus. ARM64
70.566.627byte SHA256 `93c6318b682a3a4837b171f5ff264e55fc56e60859e0874f43635bee9004a662`
terpasang update-r di RMX1821Android10, base.apk cocok.70/70file utuh saat update.
Pengelola kemudian menjalankan Hapus semua dan mengonfirmasi **langsung bersih
tanpa SAF**; pemeriksaan ADB sesudahnya rootTubeFlow ada/0entri (file maupun
subfolder). Agent tidak menekan hapus media pengguna. Android scoped/provider lain
belum diuji ulang. [Laporan lengkap](STORAGE_CLEANUP_1_1_11.md).


## Nama, metadata, LRCLIB dan Koleksi — 1.1.12/code26, 16 Oktober 2026

BUG-051/052/053: kredit deskripsi/track parsial, segmen label, artist asli+remixer,
artist sumber yang terlambat tersedia dan label title diperbaiki. Lookup LRCLIB
manual/otomatis memakai discovery terbatas dan parser bersama;429/provider/JSON
invalid dipisahkan dari koneksi gagal, Retry-After dihormati. Blank timestamp tidak
dihilangkan. Cari ulang membaca nama publikasi/durasi aktual; artist ambigu tidak
dipilih otomatis. Koleksi menghapus hanya row yang terbukti kehilangan file,
mempertahankan DENIED/UNKNOWN, mengamati MediaStore dan memeriksa saat ON_RESUME.

260Kotlin/84Python lulus; build final7m7s, debug/signedAPK×3/AAB/R8, lint0error/
2warning yang diketahui dan audit paket/native/signature/alignment/refleksiZIP/
bundletool lulus. Room9/keystore/dependensi tetap. ARM64 70.573.823byte SHA256
`497716d5938b8ef8d8147fe2078a76bab21cf890e95af707828ba9abde617a44`
terpasang update-r di RealmeAndroid10 dan hashbase.apk cocok. Folder fisik0file
sebelum update; agent tidak menghapus data pengguna. Tidak ada TubeFlow pada
buffer crash setelah launch. Tes UI baru tertahan halaman hasil Norton; agent
tidak mengubah keamanan dan meminta pengelola membuka TubeFlow kembali.

Baseline1.1.11 membuktikan kartu Koleksi hilang masih tampil dan downloadDJ
3J1Opi7MVHw memberi403 di HP; host berhasil10KB dengan engine sama (BUG-054 tetap
terbuka). Probe LRCLIB nyata mendapat429/Retry-After1. DJ248detik vs sumberoriginal
199detik tidak membuktikan timestamp studio sinkron. Belum ada konfirmasi auditori
baru atau klaim semua penamaan/lirik universal beres. [Laporan dan batas](METADATA_LYRICS_LIBRARY_1_1_12.md).


## Pencarian LRCLIB1.1.13/code27 — 16 Oktober 2026

Baseline1.1.12 di HP mereproduksi429 sekaligus “Tidak ada hasil” untuk SATRU2Live.
Query mentah yang sama juga429 di host, sedangkan judul bersih/artis utama200;
perbedaan ini bukan bukti masalah DNS pengguna. Query bersih didahulukan, permintaan
jaringan diberi jeda bersama, cache positif dipertahankan, Retry-After/backoff
persisten dihormati, dan dialog menyediakan countdown serta perluasan hasil manual.

271Kotlin/84Python lulus; lint debug/release0error/2warning lama; signedAPK×3/AAB,
native/signature/alignment/refleksiZIP/checksum engine/bootstrap lulus. Build9m18s;
AAB jarsigner/native lulus, bundletool tidak dijalankan ulang. Update-r di
RealmeAndroid10 berhasil; base.apk cocok SHA256
`9ed1d31e2abde344e30f1613da238bc7fc008a50424762e22cfbdbc68c9a6326`.
Cari lirik pada audio sama menampilkan17hasil, perluasan24hasil tanpa galat.
Satu audio tetap memiliki hash yang sama; tidak ada LRC dipilih/disimpan atau
file dihapus. Tidak ada FATAL EXCEPTION pada PID yang diperiksa. Tidak mengubah
pengaturan DNS/keamanan; UI kini bisa diuji tanpa hambatan Norton sesi sebelumnya.

Countdown429/restart terbukti pada fixture, belum teramati di HP setelah update.
Sinkronisasi auditori, download baru audio/video, dan perangkat16KB belum diuji
ulang. [Akar, perubahan, checksum seluruh artefak dan batas](LYRICS_RATE_LIMIT_1_1_13.md).


## Pemulihan interupsi audio 1.1.14/code28 — 16 Oktober 2026

Log Facebook pada 1.1.13 dan probe HP membuktikan loss fokus permanen tidak
menghasilkan GAIN kembali. Kebijakan baru mempertahankan niat track aktif,
mengamati audio lain berhenti, lalu meminta fokus sekali. Pause eksplisit tetap
membatalkan pemulihan. Kandidat callback-only masih gagal pada media native;
final menambah pemeriksaan status 1 detik selama penantian, tanpa loop request fokus.

279 Kotlin/84 Python lulus; debug dan signed release/R8/APK×3/AAB berhasil,
lint 0 error/2 warning lama. Audit APK/signature/native/ZIP/alignment/checksum
lulus; AAB jarsigner/native lulus, bundletool tidak dijalankan ulang. Update-r
Realme Android 10 berhasil dan base.apk cocok. Enam skenario fokus di latar
lulus; 58 file media tetap identik. Pengelola mengonfirmasi musik otomatis lanjut
setelah video Facebook asli dijeda; BUG-055 terverifikasi pada HP ini.
[Akar, hasil per skenario, checksum dan batas](AUDIO_FOCUS_1_1_14.md).


## Lima perbaikan bertahap1.1.15/code29 — 16 Oktober 2026

Snapshot antrean/seek ber-revisi, preferensi auto-resume, detail error media/
inspeksi/lirik, ukuran cache/parsial dan cleanup eksplisit berlease tersedia;
validasi koleksi menyimpan event selama pass. Discovery lirik search-first
memakai jalur manual/cache sebelum exact fallback, tanpa bypass429 atau perubahan
syarat versi/durasi.296Kotlin/84Python dan build debug/signedrelease/R8 lulus;
lint0error/2warninglama; auditAPK/AAB terbatas sesuai laporan.

HP kandidat awal membuktikan51queue/31589ms/jeda pulih,5kasus fokus preferensi,
2QAfile hilang otomatis, cache6,9→5,2MB/emptycleanup, dialogcopyerror nyata, serta
M4A SATRU2Live nama/tag/decode penuh.60file baseline identik sesudah upgrade.
APK final hash4e2ce13b649d93e072402f3bdf8688f44c78c46334cd8541bd619116ca2e58d4
terpasang dan memulihkan51queue/jeda44817ms. Tes layar akhir tertunda saat HP
menerima panggilan; interaksi dihentikan. Lirik Live masih berstatus pembatasan
pada kandidat awal, belum klaim sinkron. [Status per tahap dan checksum](IMPROVEMENTS_1_1_15.md).

## Proses karaoke tidak nyangkut saat ganti lagu — BUG-058, 16 Oktober 2026

Laporan pengelola: status proses karaoke sebelumnya tetap tersimpan saat beralih
lagu dan tidak ada tombol batal yang membersihkannya. Akar: guard `start()` hanya
memeriksa id tersimpan sehingga permintaan baru bisa dibuang `KEEP` diam-diam
selama proses lama berjalan tak terlihat, dan status selesai/gagal tidak pernah
bisa dibersihkan. Keputusan status kini di `KaraokeProcessPolicy` (ganti lagu
membersihkan proses yang tidak berjalan; proses berjalan tetap dengan label sumber
+ tombol batal eksplisit); `start()` menolak saat pekerjaan unique masih berjalan
dan mengadopsinya; `cancel()` membatalkan unique work + id dan menghapus
`karaoke-request.json`; dialog punya tombol batal/bersihkan di semua status.
299 Kotlin (3 regresi baru) 0 gagal, lint 0 masalah, assembleDebug lulus; 84 Python
tetap OK. Verifikasi perangkat menunggu — lihat [BUG-058](BUG_FINDINGS.md).


## Alur dan cleanup karaoke 1.1.16/code30 — 17 Oktober 2026

Menggantikan checkpoint lokal BUG-058 sebelumnya: hasil lama dipertahankan dengan
label konteks, bukan dihapus diam-diam saat ganti lagu. Aksi hasil diikat pada
ID/sumber/mode/posisi; preview untuk evaluasi, proses penuh terpisah dan simpan
cuplikan berkonfirmasi. Cancel/Buang memakai lease dan jurnal intent, membersihkan
private job + fragmen model, memeriksa penghapusan dan memblokir start bila gagal.
Model terverifikasi/asli/hasil Koleksi tetap ada. Notifikasi CANCELLED juga ditangani.

306 Kotlin/84 Python lulus; debug+signed release APK×3/AAB/R8 selesai10m8s,
lint0error/2warninglama. Audit signature/native/alignment16KB/refleksiZIP/engine
lulus; AAB jarsigner/native lulus, bundletool belum diulang. ARM64 hash
c0e472d74978739b6588d10a53ecf9f351ab1f8f8e499eed91352e5b35144b7c
terpasang dan base.apk cocok. HP membuktikan mismatch mode/lagu, modal simpan,
batal dialog/notifikasi, clip0,877687s decode penuh, buang privat dengan publik
terjaga, cleanup QA, seek/queue terjaga dan restart tanpa status lama.63file
baseline identik. Batas/fault yang belum diuji tetap terbuka di
[laporan1.1.16](KARAOKE_LOGIC_1_1_16.md).


## Refresh visual nuansa pemutar musik — 17 Oktober 2026

Upgrade UI/UX bertahap (sistem visual → kartu → pemutar → navigasi) dengan arah
gelap-aksen-hijau; nilai warna dipilih sendiri, bukan menyalin warna merek
aplikasi lain. [Theme.kt](../app/src/main/java/id/local/tubeflow/ui/theme/Theme.kt):
palet gelap netral + aksen hijau, palet terang hijau tua. Bahasa kartu datar
`mediaCardColors` dipakai bersama oleh kartu unduhan, kartu koleksi, dan mini
player (tanpa garis tepi, hierarki teks artis/meta ke onSurfaceVariant). Pemutar
penuh: sampul 380dp, bilah progres putih tipis, tombol putar hijau 72dp.
Bilah bawah: tab aktif putih tanpa pil indikator.
Rencana token: Ink #121212, kartu #1D1D1D, teks redup #B3B3B3, hijau aksi
#33D07C (gelap)/#0B7A42 (terang), wadah hijau #1E5B39. Kritik pola generik
"gelap + hijau": hijau hanya untuk SATU aksi utama (tombol putar) dan tab aktif;
identitas per-lagu tetap dari aksen sampul (`rememberCoverAccent`) pada kartu,
mini player dan gradien pemutar. Regresi kontras `ThemeContrastTest` mengunci
pasangan teks ≥4,5:1 di kedua palet (primary terang digelapkan dari #0E8F4F
karena hanya ~4,0:1).
Bukti 17 Okt: Python 90 OK; Kotlin 326 0 gagal; lint debug+release 0 error,
2 warning yang diketahui (SelectedPhotoAccess, GradleDependency onnxruntime);
`build_release.sh` BUILD SUCCESSFUL 20m21s (APK×3 + AAB signed); audit native
257 ELF 0 gagal 16KB, refleksi ZIP 13 kelas 0 gagal, apksigner cert
CN=TubeFlow Release, zipalign OK. Terpasang di HP Realme RMX1821 (Android 10,
arm64): versionName 1.1.17/code31, hash base.apk di HP == artefak
ce45874515de4d78f7bac5acbf53ff6bb7194f15f3e6686f1034d9f71d8e2dab,
smoke launch tanpa crash. Matriks screenshot (terang/gelap, font 1,3x/2,0x,
lebar sempit, status kosong/galat) masih menunggu — lihat ui-design-system.

## Perbaikan video dan LRCLIB — 18 Oktober 2026

BUG-060 memisahkan URI kosong dari hasil sukses launcher, sehingga membuka atau
membagikan media tidak menghasilkan pesan file hilang palsu. BUG-061 memvalidasi
kontrak JSON LRCLIB sebelum cache dan saat membaca cache lama; hasil manual dengan
ID sama dapat dilengkapi timestamp oleh varian berikut. BUG-062 membedakan angka
judul lagu (Satru/Satru 2) walau artis dan durasinya mirip. 333 Kotlin/90 Python
lulus; lint debug nol error/dua warning lama, assembleDebug lulus. Release baru,
pemasangan, dan riwayat HP belum diperiksa untuk perubahan ini. Pilihan mode
Anti Slop masih menunggu jawaban; HP terdeteksi tetapi terkunci. Rincian dan
langkah berikutnya: [checkpoint perbaikan](REPAIR_2026-10-18.md).


## Penyelesaian checkpoint 1.1.18 — 18 Oktober 2026

Menggantikan status tertunda pada entri sebelumnya: release1.1.18/code32 sudah
terpasang tanpa reset dan hash cocok. BUG-060–066 diperbaiki, dengan batas bukti
penyedia lirik dicatat. Seluruh menu memakai panel, spasi, judul dan tema bersama;
font besar, dialog pendek dan state inspeksi diuji di HP. 339Kotlin/90Python lulus,
build signed APK×3/AAB9m45s, lint0error/2warning lama, audit paket lulus.
24screenshot kandidat dan enam layar final1,35×; video, clear inspeksi, timer1menit
serta selesai lagu diuji.62file awal identik setelah hanya video QA dibersihkan.
Room10/enam fitur media dibawa tanpa dependensi baru. Tidak mengklaim audit produksi
penuh, seluruh lirik sinkron, TalkBack atau runtime16KB sudah lulus.
[Laporan akhir dan pekerjaan tersisa](REPAIR_2026-10-18.md).

## Penyatuan aksi dan filter 1.1.19/code33 — 18 Oktober 2026

Arahan pengelola: lanjutkan arah pemutar musik (gelap, satu aksen hijau) dan
samakan seluruh menu, khususnya tombol. Dua permintaan lanjutan: aksi di daftar
Riwayat cukup ikon (label panjang memakan ruang), deret filter Koleksi
(Favorit/semua playlist/kelola playlist/terbaru/semua folder) diseragamkan, ikon
favorit–playlist–antrean dikeluarkan dari menu tiga titik, dan aksi "buka file
lirik" di Riwayat dihapus karena sudah ada aksi pencarian lirik.

Lapisan interaksi baru di `ui/components.kt` (dipakai 13 layar/dialog):

- `ActionButton` dengan tiga peran (utama 52dp, sekunder, tenang) plus warna
  galat untuk destruktif, bentuk pil; menggantikan campuran
  Button/OutlinedButton/FilledTonalButton/TextButton dan CTA gradien di Download.
- `Panel` (padding 16dp, judul `titleMedium`, deskripsi redup, jarak isi 12dp)
  menggantikan padding 14/16/18dp yang berbeda per layar.
- `ToggleRow`/`CheckRow` (satu fokus TalkBack, target 48dp), `ScrollRow` (jarak
  8dp), `StatRow` (metrik menumpuk saat font >1,3x), `SearchField`,
  `ConfirmDialog` (aksi berisiko berwarna galat), `IconAction`, `MenuChip`,
  `ActionChip`.
- Tipografi tema melengkapi `bodyLarge` dan `headlineMedium` supaya skala tipe
  tidak jatuh ke nilai bawaan Material.

Keputusan yang perlu dijaga penerus: satu aksi utama per layar; aksi di dalam
kartu daftar memakai ikon 48dp dengan `contentDescription` (bukan label);
header, panel, dialog, dan pesan galat tetap memakai teks; deret filter memakai
satu bentuk chip; kartu media tetap `mediaCardColors` dan aksen sampul tidak
dipaksa kembali hijau. Lirik di kartu Riwayat hanya menawarkan pencarian lirik;
lampiran yang tersisa adalah thumbnail dan subtitle video.

Bukti host: `bash scripts/build_release.sh --no-daemon --max-workers=2`
**BUILD SUCCESSFUL 15m59s**; **339 tes Kotlin lulus** (0 gagal/error); lint debug
dan release **0 error, 2 warning lama** (`SelectedPhotoAccess`, `GradleDependency`
ONNX yang dipin). Audit APK rilis arm64: 257 ELF lolos alignment 16KB, 13 kelas
refleksi ZIP lolos, signature produksi dan zipalign lolos. APK arm64
SHA256 `5a0fe86f91009b744536d2240f537653ac0dfc18ae6e1713c7e8fe3c076fa2aa`,
terpasang `adb install -r` tanpa uninstall/reset.

Bukti perangkat: Realme RMX1821 (Android 10/API 29, arm64, 720x1520 @272dpi,
mode gelap, font 0,9). Tangkapan menu Ringkasan, Download, Antrean, Pengaturan,
dan Koleksi (chip seragam) pada build yang sama; Riwayat sesudah perubahan
menampilkan satu baris ikon tanpa tombol lirik. Mode terang, font >1,2x, dan
lebar 360dp **belum diuji**: ColorOS menolak `cmd uimode night no` (nilai tetap
"yes") dan `font_scale`/`wm density` lewat ADB. Tangkapan terakhir Koleksi dan
Riwayat tidak disimpan karena layar HP memuat konten pribadi pengguna — berkas
tersebut dihapus dari `build/reports/ui-1119/`, bukan dijadikan bukti publik.

Versi dinaikkan ke **1.1.19/code33** mengikuti pola setiap checkpoint UI
sebelumnya. Batas lain: TalkBack/keyboard eksternal, tablet/foldable, dynamic
color Android 12+, runtime halaman 16KB, dan seluruh kombinasi error belum diuji.

## CI hijau: ffmpeg di runner — 18 Oktober 2026

Gate CI di `main` merah sejak 17 Oktober pada langkah Python, bukan karena kode
aplikasi: runner `ubuntu-latest` tidak punya binary `ffmpeg`. Akibatnya
`test_optional_processing…native_opus_packets` gagal dengan
`FileNotFoundError: 'ffmpeg'`, lima tes kompresi sampul ter-skip, langkah audit
16KB/refleksi ZIP ikut ter-skip, dan langkah `upload-artifact` gagal karena
"Artifact storage quota has been hit" (APK debug ~70 MB per run).

Perbaikan di `.github/workflows/android.yml`:

- Menambah langkah **Install host media tools** (`apt-get install ffmpeg`) sebelum
  tes Python, sehingga tes yang memang butuh ffmpeg/ffprobe benar-benar berjalan
  (bukan di-skip atau diberi pengecualian).
- Artefak yang diunggah dibatasi ke laporan teks (`app/build/reports/`,
  `app/build/test-results/`) dengan `retention-days: 7`; APK debug tidak lagi
  diunggah.
- Langkah **Publish unit-test summary** menjalankan `scripts/ci_summary.py` yang
  membaca XML JUnit dan menulis jumlah tes/gagal/dilewati ke halaman run. Ini
  menjaga laporan tetap terbaca ketika unggahan artifact ditolak kuota, sehingga
  `continue-on-error` pada unggahan tidak menyembunyikan informasi apa pun.
  Kuota penyimpanan artifact bersifat akun (repo ini sendiri tidak menyimpan
  artifact), jadi unggahan masih bisa gagal sampai kuota akun pulih.

Bukti: run `39091726565` (commit `c59886f`) dan run `39092901718` (commit
`a0f0981`) selesai **success** dengan semua langkah hijau. Log Python mencatat
**`Ran 90 tests ... OK` tanpa skip** (sebelumnya 90 tes dengan 1 error dan 6
skip), kedua langkah audit kini dijalankan, dan ringkasan tes 339 unit test
ditulis ke halaman run. Tidak ada asersi yang dilemahkan dan tidak ada tes yang
dilewati untuk membuat pipeline hijau.


## Cache, notifikasi dan playlist 1.1.20/code34

Baseline 1.1.19/1d7caf3 dipertahankan. Cache lirik bekerja sebelum Engine dengan status hasil; MainActivity singleTask dan tujuan notifikasi bersama, MediaStyle native; tombol outlined serta Kelola playlist diselaraskan. 345 Kotlin/93 Python lulus, signed APK×3/AAB 11m24s, lint0error/2warning lama. Release ARM64 terpasang dan hash cocok. Pengelola mengonfirmasi notifikasi sesuai; rincian tes dan batas pada laporan.
[Laporan, checksum dan pengujian](NOTIFICATIONS_PLAYLIST_1_1_20.md).

## Kontrol dan rincian cache 1.1.22/code36

Aksi Koleksi dan Bersihkan Riwayat mengikuti ukuran chip; Kelola playlist memakai
baris dengan ikon Ubah/Hapus. Cache dirinci lirik/metadata/sampul dan diperbarui
sebelum pengukuran folder. Signed APK terpasang di Realme Android10 dan hash cocok;
347Kotlin, lint0error/2warning lama, audit ARM64 lulus. Uji HP membuktikan target
aksi/filter82px, operasi playlist QA, serta respons clear lirik0B sementara
sampul6,7MB tetap ada. Playlist QA dibersihkan. [Laporan dan batas](CONTROLS_CACHE_1_1_22.md).

## Pembaruan 19 Oktober 2026: aksi koleksi dan EQ 1.1.23

Kontrol antrean putar/download memakai chip, kartu video mempunyai lima aksi
langsung, audio/video mendukung Bagikan dari Koleksi. Default EQ otomatis
seimbang hingga+3dB bass/treble dan mid netral tanpa mereset preset manual.
348 tes Kotlin, lint0error/2warning lama, build debug/release dan audit ARM64
lulus. Update fisik/code37/hash cocok; tes antrean, share, dialog video,
playback video dan preset EQ lulus dalam cakupan yang dicatat.
Hi-Res/ANC masih penilaian kemampuan, bukan fitur baru yang diklaim selesai.
[Laporan dan batas verifikasi](ACTIONS_EQ_1_1_23.md).

## Pembaruan 19 Oktober 2026: kontrol menyeluruh 1.1.24

ActionButton dan ActionChip memakai AssistChip bersama; OptionMenu menjadi
chip label:nilai. Ukuran visual ringkas berlaku bagi seluruh pemanggil,
termasuk Pengaturan, Download, lirik, timer, karaoke dan playlist. EQ tetap
sesuai umpan balik pengguna dengan QKZ AK6 Pro. 348Kotlin, lint0error/2warning
lama, build debug/release dan audit ARM64 lulus. HP/code38/hash cocok;
24kontrol target82px, tema terang/gelap, form playlist dan dialog alat media
diperiksa. QA playlist dibersihkan dan tema dipulihkan. Pencarian lirik
mencatat HTTP503 pada salah satu jalur; belum dianggap selesai oleh polesan UI.
[Hasil dan batas](COMPACT_CONTROLS_1_1_24.md).


## Pembaruan 19 Oktober 2026: kontrol adaptif 1.1.25

Ukuran berdasarkan peran dan isi menggantikan penyamaan semua tombol menjadi
chip. AdaptivePair memisahkan label/aksi saat sempit atau font besar; mini
player beradaptasi, kontrol sekunder player menjadi2/4kolom, dan navigasi rail
mulai840dp dengan gulir pada jendela pendek. Room10/engine/EQ tetap.
348Kotlin, lint0error/2warning lama, debug/release dan audit ARM64 lulus.
Code39/hash terpasang cocok; HP diuji pada font0,9/1,6, terang/gelap dan
portrait/landscape. QA playlist dibersihkan, setelan dipulihkan, daftar65file
sama. [Perubahan, bukti dan batas matriks](ADAPTIVE_CONTROLS_1_1_25.md).

## Pembaruan 20 Oktober 2026: aksi kartu seragam dan EQ bass penuh 1.1.26

Satu `MediaCardPlan.card` menjadi sumber urutan, grup, dan slot ikon untuk
Koleksi dan Riwayat. Koleksi mendapat **Unduh lagi** (hanya bila item masih
tertaut ke job selesai; konfirmasi karena file lama diganti), sedangkan
Riwayat mendapat **Tambah ke antrean**, **Putar berikutnya**, dan **Atur waktu
lirik** untuk LRC tersimpan. Semua aksi tampil langsung di kartu (tanpa menu
titik tiga), sehingga Unduh lagi + Cari lirik selalu terlihat di baris yang sama
di kedua daftar; aksi yang tidak dimiliki layar (playlist/favorit/ubah nama di
Riwayat) tetap tidak dipalsukan.

EQ **Otomatis (bass bulat)** berubah dari kurva seimbang +3 dB menjadi bass
penuh: +6 dB pada 30–60 Hz, +4,5 dB 120 Hz, +2 dB 250 Hz, netral 500 Hz–2 kHz,
+2,5 dB treble. Interpolasi logaritmik dan clamp rentang band dipertahankan;
preset/level manual dan pilihan nonaktif pengguna tidak direset. Tidak ada
perubahan Room, engine, izin, atau dependensi.

362 tes Kotlin 0 gagal (termasuk tes baru urutan Unduh lagi/Cari lirik,
kurva bass landai, dan LRC audio yang tidak boleh tampil sebagai subtitle),
lint 0 error/2 warning lama, debug dan release bertanda tangan lulus.
[Rincian dan batas](CARDS_EQ_1_1_26.md).

## Pembaruan 20 Oktober 2026: enam aksi di kartu 1.1.27

Kartu Koleksi dan Riwayat kembali diringkas: hanya **Putar/Buka, Favorit,
Bagikan, Ubah nama, Cari lirik, dan Hapus** yang tampil langsung. Antrean,
playlist, atur waktu lirik, lampiran, detail, buka dengan aplikasi lain, dan
unduh lagi pindah ke menu ⋮ yang berkelompok (*Antrean & playlist*, *Lirik*,
*Lampiran*, *Berkas*). `MediaCardPlan` menandai slot tiap aksi dan
`MediaActionBar` menggambar ikon langsung plus tombol ⋮; Hapus tetap
berkonfirmasi.

Berbagi mengisi `EXTRA_TEXT` dengan nama file tanpa ekstensi (cadangan: judul
media), sehingga aplikasi yang mendukung caption membuka pesan siap kirim.
WhatsApp Android tidak punya kolom caption untuk audio, jadi saat audio dibagikan
caption juga disalin ke clipboard dengan toast penjelas; untuk gambar/video dan
aplikasi seperti Telegram/email teks tetap terisi otomatis. Share dari Koleksi
sempat gagal karena toast dibuat di thread IO (exception tertangkap sebagai
"file tidak dapat dibagikan"); toast kini diposting ke main thread dan share
dari Koleksi sudah dicoba lagi di HP berhasil.

Pengaturan **Riwayat unduhan → Hapus otomatis** (Nonaktif/7/30/90 hari) menghapus
catatan riwayat selesai yang lama saat aplikasi dibuka atau pilihan diubah.
File media, LRC, sampul, dan playlist tidak disentuh. 370 tes Kotlin 0 gagal,
lint 0 error/2 warning lama, release bertanda tangan lulus; kartu dan menu
diperiksa di Realme Android 10. [Rincian dan batas](CARD_ACTIONS_1_1_27.md).

## Pembaruan 23 Oktober 2026: pembaruan engine 1.1.28

Pengaturan **Mesin media** dapat memeriksa rilis stabil resmi GitHub, memverifikasi
SHA-256 dari API dan `SHA2-256SUMS`, memvalidasi zipapp/versi, memasang override
secara atomik, dan kembali ke engine bawaan. Hanya host GitHub yang diizinkan,
ukuran dibatasi 20 MB, dan pembaruan ditolak saat inspeksi/unduhan aktif. Tidak
ada pembaruan otomatis; FFmpeg tidak ikut diperbarui. 1.1.28 sengaja membawa
engine bawaan 2026.08.19 agar jalur nyata diuji di HP: cek menemukan 2026.10.21,
pasang berhasil, lalu rollback kembali ke 2026.08.19. 378 tes Kotlin dan 93 tes
Python lulus; lint 0 error/3 warning. [Rincian dan batas](ENGINE_UPDATE_1_1_29.md).

## Pembaruan 23 Oktober 2026: engine bawaan 2026.10.21 dan recovery 1.1.29

Engine bawaan diganti ke yt-dlp 2026.10.21. Recovery kini memakai
`DownloadRecovery` untuk mempertahankan `pendingUri`/`sidecarPending`/sidecar
yatim yang ditolak dihapus provider; update DAO memakai generation agar recovery
tidak menimpa job yang dilanjutkan. Enam regresi baru mengunci perilaku ini.
384 tes Kotlin 0 gagal, 93 tes Python OK, lint 0 error/3 warning, signed APK×3/AAB
dan audit ARM64/AAB lulus. 1.1.29 terpasang sebagai update (hash base.apk identik,
riwayat 212 entri utuh); cek "versi terbaru", blokir saat unduhan aktif, unduhan
320 kbps, dan BUG-054 MP3 128 (4.023.060 byte, decode penuh, sampul tertanam)
berhasil. Lirik gagal karena LRCLIB. [Laporan dan batas](ENGINE_UPDATE_1_1_29.md).

## Pembaruan 23 Oktober 2026: inspeksi bertahap, cache, dan lirik manual 1.1.30

Inspeksi link kini melaporkan fase kerja (`Menyiapkan mesin`, `Mengambil detail
video`, `Membaca playlist`, `Memilih format`) dan elapsed setelah 3 detik.
Tombol **Batalkan pemeriksaan** menghentikan engine tanpa menghapus URL. Metadata
hasil inspeksi disimpan di cache privat selama 30 menit (maksimal 200 entri,
nama berkas SHA-256) dan dipanaskan 800 ms setelah aplikasi dibuka; cache tetap
tersedia setelah force-stop. Cache ini dihitung sebagai metadata pada ukuran
penyimpanan.

Dialog **Cari dan pilih lirik** mendapat **Tempel LRC manual**. Teks divalidasi
(timestamp wajib, batas ukuran/durasi), lalu dipublikasikan ke sidecar audio
dengan header `[re:TubeFlow / manual]`; kegagalan tidak menimpa lirik lama.
Detail kegagalan lirik di kartu dipadatkan menjadi satu ikon, dan `PageHeading`
memakai semantics heading.

Recovery menangkap exception provider saat menghapus berkas dan mempertahankan
jurnal, bukan menganggapnya sukses. CI menjalankan
`scripts/verify_engine_asset.py` (versi/checksum/sumber/bootstrap) dan audit
native untuk APK debug serta release.

**402 tes Kotlin** 0 gagal, **98 tes Python** OK, lint 0 error/3 warning lama,
signed APK×3/AAB lulus. ARM64 **70.948.952 byte**, SHA256
`6bf4957d6d16d4bfe22589fa7818b1d531211204ea4c43e223fa14918cefe7c5`;
AAB **256.040.881 byte**, SHA256
`b686b07ceb7cd5596ff40320f61ffd4e9b3bdb1a9d5f5f91348f363aafd53eee`.
Audit APK 257/257/259 ELF/13 kelas ZIP dan AAB 1.038 ELF 0 gagal 16 KB,
zipalign 16 KB dan signature v2 lulus. 1.1.30 terpasang sebagai update di Realme RMX1821 Android 10; hash `base.apk` identik dengan artefak.

Di HP: fase inspeksi terlihat (`Mengambil detail video… · 13 dtk`), cancel
mengembalikan layar ke input tanpa hasil, URL yang sama setelah force-stop
langsung memakai cache, LRC manual tersimpan (sidecar 49 byte) dan status
berubah menjadi **Lirik tersimpan**, lalu entri uji dihapus kembali. Unduhan
BUG-054 MP3 128 selesai 4.023.060 byte/247,824 detik; lirik otomatis gagal
karena LRCLIB, bukan 403. Riwayat kosong pada awal sesi karena setelan pengguna
**Hapus otomatis: Setelah 7 hari**, bukan reset database; file media tetap ada.

**Batas:** TWS/Android 12+ belum diuji ulang, fault injection provider/process
death, runtime 16 KB, ABI/Android lain, dan matriks font besar/landscape belum
dijalankan pada 1.1.30. Cache inspeksi tidak menggantikan validasi media hasil
unduhan.

## Pembaruan 23 Oktober 2026: Antrean dan Riwayat dalam satu daftar 1.1.31

Tab segmented **Antrean/Riwayat** dihapus. Tab Download kini menampilkan satu
daftar vertikal: composer URL selalu terlihat, lalu Antrean bila ada job aktif,
pemisah tipis bila keduanya ada, lalu Riwayat. Saat tidak ada job, halaman tetap
bersih tanpa kartu kosong. `DownloadSection` dan state `downloadSection` dihapus;
`AppDestination.HISTORY`/`QUEUE` sama-sama membuka tab Download, dan deep link
lama tidak lagi menggulir ke section tertentu.

Ringkasan Antrean memakai `"Antrean · …"`; aksi massal (jeda/lanjut/batal semua)
hanya muncul bila ada lebih dari satu job, sehingga satu job tidak menampilkan
kontrol ganda di header dan kartu. Ringkasan Riwayat memakai `"Riwayat · …"`.
Pencarian dan filter Riwayat disembunyikan sampai ada lebih dari tiga entri
selesai atau pengguna sedang memakai query/filter; empty state Riwayat dihapus
karena section hanya dirender saat ada entri.

**405 tes Kotlin** 0 gagal, **98 tes Python** OK, lint 0 error/3 warning lama,
debug arm64 bertanda tangan release key terpasang sebagai update di Realme
RMX1821 Android 10. Sertifikat tetap `ebe3b40e…`; kandidat debug
`/tmp/opencode/tubeflow-1.1.31-unified-debug-releasekey-g.apk` SHA256
`ef70dd62ef56291b1487ca3e9af29e90d975601c4d971d225d1081ab2bd7896d`.

Release **1.1.31/code45** dibangun (16m01s): ARM64 **70.946.520 byte**, SHA256
`cd265cee31516c9a5f4a0e3a70388fbc704617ebde190ad50cb29bef9978c18f`;
AAB **256.041.323 byte**, SHA256
`7341a6adf0fc3ffc4bb62df941828682cc9f05390ba5ba4a18b528028a8c3e80`.
APK×3/AAB bertanda tangan; audit APK 257/257/259 ELF dan 13 kelas ZIP lulus,
`zipalign -c -P 16 4` dan signature v2 sertifikat `ebe3b40e…` lulus; AAB 1.038
ELF dan `jarsigner` jar verified. Release terpasang sebagai update di Realme
RMX1821 Android 10, `base.apk` identik dengan artefak, riwayat uji tetap ada.

Di HP: keadaan kosong bersih; job aktif + riwayat tampil berurutan dengan
pemisah; job dijeda, dilanjutkan, dibatalkan, lalu entri batal dihapus tanpa
menghapus riwayat; riwayat satu entri tetap tampil setelah force-stop; dialog
**Bersihkan riwayat?** dibuka lalu dibatalkan; deep link `OPEN_HISTORY` membuka
tab Download. Riwayat uji `"Me at the zoo"` dibuat ulang dan bertahan. Tidak
melakukan uninstall, clear data, atau reset database.

**Batas:** state Antrean aktif pada build release belum diulang (diverifikasi pada
debug bertanda tangan kunci yang sama); pencarian/filter Riwayat dengan >3 entri,
TWS/Android 12+, fault injection provider/process death, runtime 16 KB,
ABI/Android lain, serta matriks font/landscape belum diuji. Audit host tidak
membuktikan seluruh jalur gagal Android aman.

## Pembaruan 23 Oktober 2026: pemolesan pemutar, cleanup berlease, retensi terlihat

Pemutar penuh memisahkan halaman **Sampul** dan **Lirik** menjadi composable sendiri
(`PlayerCoverPage`, `LyricsPage`) dengan callback yang diingat, sehingga pembaruan posisi
tiap 200 ms hanya mengubah `activeIndex`, bukan menyusun ulang daftar lirik dan tombol
alat. Tinggi halaman memakai ambang ringkas (tinggi < 600dp atau font ≥ 1,3×) agar
kontrol bawah tetap terjangkau di layar pendek/landscape. Menu kecepatan mengumumkan
baris aktif sebagai "dipilih" untuk TalkBack, bukan hanya ikon centang.

Pembersihan direktori sementara job (`deleteJobDir`) kini memegang lease
`DownloadFileAccess` dan menolak hapus saat `Engine.isRunning`; pembatalan job yang
masih ENQUEUED tidak lagi menunggu worker yang tak akan berjalan, sedangkan worker aktif
membersihkan sendiri di `finally` (BUG-004). Retensi riwayat otomatis memberi tahu jumlah
entri yang dihapus lewat notice, tanpa menyentuh file media.

**Verifikasi host:** 405 tes Kotlin 0 gagal/error/skip; lint debug **0 error, 3 warning
lama** (ulang setelah perubahan terakhir); `assembleDebug` dan `assembleRelease` signed
ARM64 70.962.676 byte, SHA256
`15d9c83a324ffe4187c3e8bdf25ba51e66454a7b23bad1e2fc40368663e0950d` sukses. Audit APK
rilis: 257 ELF 0 gagal 16 KB, 13 kelas refleksi ZIP 0 gagal. **Di HP** (Realme RMX1821
Android 10): rilis dipasang sebagai update (versionCode 45, hash `base.apk` identik
dengan artefak, aplikasi berjalan tanpa FATAL/ANR). Titik halaman berpindah
sampul↔lirik lewat ketukan, tombol kecepatan membuka menu dengan centang pada 1×,
memilih 1,25× langsung mengubah label header, dan pil indikator opak setelah
perbaikan tembus pandang. **Uji pembatalan nyata:** unduhan Big Buck Bunny 4K
dibuat lewat share intent, direktori `no_backup/downloads/200a94bd-…` terlihat
saat job berjalan, lalu dibatalkan dari kartu; setelah 3 detik direktori itu
hilang (tersisa `.` dan `..`), status menjadi **Dibatalkan**, dan entri uji
dihapus dari riwayat. **Matriks tata letak:** portrait pada font **0,9×**
menampilkan empat aksi sekunder dalam satu baris; font **1,6×** (maksimum
ColorOS) memindahkannya ke dua kolom, teks membungkus tanpa tombol terpotong.
Landscape memakai **dua kolom** — kolom kiri hanya lirik, tanpa pager sampul
atau pil indikator; kolom kanan memuat judul, artis/posisi antrean, sampul,
slider dengan waktu di kiri/kanan, transport, dan aksi
Acak/Ulangi/Karaoke/Antrean yang menetap di dasar. Pada font besar judul
dibatasi satu baris dan sampul diperkecil agar kolom tetap ringkas. Pada build
rilis final (font 0,9×) keempat aksi terlihat tanpa menggulir. Rotasi otomatis,
font 0,9×, dan status ulangi semula dipulihkan setelah uji. **Belum:** TalkBack aktif, notice retensi dengan baris
kedaluwarsa, pengukuran
recomposition/perfetto, dan celah `createDocument` → `dao.pending` (BUG-008) masih
terbuka — jurnal-intent sebelum create belum dirancang.

Catatan implementasi saat ini: engine hanya meminta thumbnail bila `job.thumbnail`
atau `job.embedThumb` aktif; preview sumber memakai CoverCache terpisah. Aturan
"selalu diminta" pada arsip 13 Oktober tidak lagi berlaku.

## 25 Oktober 2026 — Tiga temuan resolusi, 1.1.42/code56

Update pengguna 1.1.41 beserta Scrubber baru sudah disimpan dan dipush pada
`0b2665f`. Perbaikan berikutnya menangani BUG-078/079/080: state kualitas
menyimpan tier, cap piksel baru dibentuk di DownloadOptions; label tanpa bukti
menggunakan sisi pendek aktual; preset ultrawide tetap terpilih. Cache inspeksi
schema 3 menolak label lama, Room tetap 10 tanpa migrasi.

Tiga regresi gagal pada kode lama; setelah perbaikan **479 tes Kotlin** dan
**98 tes Python** lulus. Gradle test/lint debug+release/assemble debug+release
berhasil (13m 1s), lint 0 error/3 warning lama. Audit ARM64: 262 ELF/13 kelas ZIP,
signature produksi dan zipalign 16 KB lulus. Release dipasang update di HP;
preset/menu/ringkasan 1080p dan persistensi setelah rotasi berhasil diuji.
AAB tidak dibangun ulang. [Laporan lengkap](RESOLUTION_FIXES_1_1_42.md).

## 25 Oktober 2026 — Geometri Scrubber setelah rotasi/resize

BUG-081: tap/geser Scrubber sekarang memetakan koordinat dengan `size.width`
pada setiap kejadian lewat `scrubberPositionAt`, bukan `span` yang disalin saat
`pointerInput` dimulai. Key `pointerInput` ditambah `radiusPx`. Lima regresi
`ScrubberGeometryTest` lulus melalui harness kotlinc/JUnit di luar Gradle;
suite Gradle penuh dan uji rotasi di HP belum dijalankan pada sesi ini.
[Detail](BUG_FINDINGS.md).

## 25 Oktober 2026 — Episode Bstation

Dukungan episode Bstation (`bilibili.tv`/`biliintl.com`) ditambahkan melalui
`BstationUrls`: path `/play/<season>/<ep>` dan `/video/<id>` diterima, halaman
season ditolak dengan pesan yang menjelaskan, dan identitas duplikat memakai
bentuk kanonis tanpa locale/query. Error geo (`10004001`) dan login/premium
kini punya kategori serta pesan pengguna sendiri. Regresi `BstationUrlsTest` (5),
`SelectionTest` (45, termasuk Bstation), dan `FailureDetailsTest` (7, kategori
geo/premium) lulus di harness kotlinc/JUnit dengan stub
Android/Room. Suite Gradle dan uji perangkat Indonesia belum dijalankan.
[Rincian](BSTATION_EPISODES_2026-10-25.md).

## Pemilih episode season Bstation — 25 Oktober 2026

Season Bstation (`/play/<season>`, `/media/<season>`) kini dibuka sebagai koleksi, bukan satu video. Katalog episode dibaca dari API Bstation yang sama dengan extractor BiliIntl (`season_info` + `episodes`), lalu dipetakan ke `PlaylistMetadata`/`PlaylistEntry`. Pemilih mengurutkan episode, memberi padding `E01`, menandai Premium dari `limit`/`limit_text`, dan memilih episode gratis bernomor secara otomatis; PV/spesial tidak dipilih dan Premium dinonaktifkan karena login belum didukung.

Setiap episode tetap menjadi satu `DownloadJob` agar progress, retry, cancel, publikasi, dan deteksi duplikat bekerja seperti video biasa. Season memakai satu subfolder dari judul (`Selection.seriesFolder`), `autoFolder=""`, dan judul episode dipertahankan tanpa diproses sebagai lagu. Worker melewati pencarian lirik/identitas musik untuk episode season. URL episode memakai bentuk kanonis, sehingga locale dan query pelacakan tidak menghasilkan duplikat.

Verifikasi: `BstationUrlsTest` 7, `BstationSeasonTest` 6, `SelectionTest` 47, dan `FailureDetailsTest` 8 lulus (total 68) dengan kotlinc + JUnit di luar Gradle. API dari VM US mengembalikan 4 episode + PV dan menandai E4 Premium. Gradle `:app:compileDebugKotlin` sukses, `:app:testDebugUnitTest` 504 tes lulus, `:app:lintDebug` sukses, dan `:app:assembleDebug` sukses dengan AAR FFmpeg stok sementara; uji perangkat dan build dengan AAR WebP proyek belum dijalankan.

## Login/cookie Bstation untuk episode Premium — 25 Oktober 2026

Pengguna dapat masuk ke Bstation lewat WebView halaman resmi dari Pengaturan → Akun Bstation atau tombol di pemilih season. Setelah cookie `SESSDATA` muncul, `BstationAuth` menyimpannya dengan AES-GCM dan kunci Android Keystore di `noBackupFilesDir`; kata sandi tidak pernah masuk ke TubeFlow. Episode Premium tetap terkunci sebelum login, lalu bisa dicentang dan diantrekan setelah login; `queuePlaylist` memeriksa ulang status sesi agar pilihan lama tidak lolos setelah logout.

Katalog API dan engine memakai cookie hanya untuk host Bstation. Engine menerima file Netscape sementara (dua domain Bstation) lewat `--cookies`; file dihapus di `finally`. Logout menghapus file sesi dan cookie WebView. Akses Premium tetap mengikuti langganan akun dan tidak membuka konten ber-DRM.

Verifikasi: `BstationUrlsTest` 7, `BstationSeasonTest` 7, `BstationCookiesTest` 6, `SelectionTest` 47, dan `FailureDetailsTest` 8 lulus (total 75); file Netscape juga diuji dengan `http.cookiejar.MozillaCookieJar`. Gradle `:app:compileDebugKotlin`, `:app:testDebugUnitTest` (511 tes), `:app:lintDebug`, dan `:app:assembleDebug` sukses; assemble memakai AAR FFmpeg stok sementara sehingga bukan build setara AAR WebP proyek. Login dengan akun nyata, decrypt Keystore di perangkat, dan unduhan Premium dari jaringan Indonesia belum diuji. [Rincian](BSTATION_EPISODES_2026-10-25.md).

## Subtitle Bstation — 26 Oktober 2026

Unduhan episode Bstation berhasil di perangkat (Realme, jaringan Indonesia):
`E01 - The Battle Begins.mp4`, 102 MB, 23:50, tampil dan diputar di pemutar
bawaan. Selector format diperbaiki lebih dulu karena rantai lama menuntut audio
`m4a` sementara BiliIntl menyediakan audio AAC ber-ext `mp4`. Selector MP4 kini
memakai `bestvideo[ext=mp4][vcodec^=avc1]+bestaudio` dan berakhir `/best`,
sehingga sumber tanpa audio m4a tidak gagal dengan "Requested format is not
available".

Subtitle sempat tidak terdeteksi sama sekali. Penyebabnya `--dump-single-json`
tanpa `--write-subs`: `extract_subtitles()` yt-dlp hanya membaca daftar subtitle
bila subtitle diminta, jadi BiliIntl mengembalikan `subtitles` kosong. Setelah
`--write-subs` ditambahkan ke inspeksi, preview menampilkan
"Subtitle tersedia: en, id, th" di perangkat. Pilihan "asli" dibuat deterministik
(bahasa video → `-orig` → bahasa perangkat → `en` → terurut) karena urutan kunci
JSON tidak dijamin.Verifikasi: `:app:testDebugUnitTest` **516 tes, 0 gagal** (termasuk 3 regresi baru `SourceMetadataTest`), `:app:assembleDebug` sukses dengan AAR FFmpeg WebP proyek (`app/libs/ffmpeg-0.18.1-webp16k.aar`, audit 16 KB alignment tersedia). APK debug dipasang ke perangkat. [Rincian](BUG_FINDINGS.md).

### Pemilih subtitle per sumber

Daftar chip subtitle tidak lagi tetap ("Bahasa asli/Indonesia/Inggris/Semua"). `SubtitlePicker` membaca bahasa yang benar-benar dilaporkan sumber:

- YouTube tetap memakai "Bahasa asli" (caption otomatis `-orig` membuat artinya
  pasti), disusul bahasa yang tersedia dan "Semua bahasa".
- Sumber non-YouTube (Bstation dan lainnya) hanya menampilkan bahasa yang ada,
  mis. Inggris/Thai/Indonesia, tanpa "Bahasa asli" yang tidak punya arti di
  sana. Pilihan awal mengikuti bahasa perangkat bila tersedia, lalu bahasa
  pertama sumber.
- `Selection.validSubtitleChoice` menerima tag ISO 639 (`th`, `pt-BR`, …) alih-alih
  daftar tetap, sehingga bahasa yang tidak ada di daftar lama tetap bisa dipilih.

Kartu pratinjau juga diperbaiki: durasi hanya ditampilkan bila sumber
melaporkannya (BiliIntl tidak menyertakan `duration`), baris penulis
menggunakan judul seri Bstation sebagai kredit sumber lewat
`BstationSeason.seriesTitle`, dan baris yang kosong tidak lagi dirender sebagai
0:00/kosong.

Verifikasi: `:app:testDebugUnitTest` **522 tes, 0 gagal** (termasuk 6 regresi
`SubtitlePickerTest`), `:app:lintDebug` sukses dengan 2 peringatan lama, dan
`:app:assembleDebug` sukses. Uji perangkat untuk chip dan unduhan subtitle
belum dijalankan pada sesi ini.

## Uji perangkat 26 Oktober — unduhan subtitle Bstation + BUG-083

Alur nyata di RMX1821 (Bstation, jaringan Indonesia): tempel tautan
`bilibili.tv/play/2432940/31524599` → pratinjau "Black Clover Season 2",
"Subtitle tersedia: Inggris, Indonesia, Thai" → buka Pengaturan lanjutan →
aktifkan "Download subtitle" (default memilih **Indonesia** sesuai bahasa
perangkat) → Download video.

Hasil: `E01 - The Battle Begins (1).mp4` (102 MB) dan
`E01 - The Battle Begins (1).id.srt` (23.761 byte, 318 cue, sampai 22:11)
terbit di `Download/TubeFlow`; baris job `COMPLETED` dengan
`subtitles=id`, `sidecarUris=sub|content://media/external/downloads/197934`.
Artinya jalur unduh subtitle bekerja ujung ke ujung, termasuk penamaan
`(1)` saat berkas sudah ada.

Temuan lanjutan: pemutar menampilkan chip subtitle "—" karena URI MediaStore
tidak memuat nama berkas, sehingga sidecar dibuang saat dibaca. Diperbaiki
dengan membaca `DISPLAY_NAME` lewat resolver (BUG-083). Suite Gradle naik ke
**523 tes, 0 gagal**; verifikasi visual chip di pemutar belum sempat dilakukan
karena perangkat sedang dipakai.

## Subtitle otomatis + perbaikan pemutar 26 Oktober

Subtitle hasil unduhan sekarang dipilih otomatis saat video dibuka;
`VideoPlayback.preferredSubtitleIndex` mengutamakan subtitle unduhan, lalu
bahasa perangkat (mis. ponsel `id-ID` memilih `id` walau ada subtitle Inggris),
lalu track pertama. Di RMX1821 episode Black Clover E01 menampilkan teks
Indonesia tanpa menekan CC; panel opsi menampilkan "Subtitle · Indonesia".

Dua masalah pemutar diperbaiki setelah uji yang sama:

- Kontrol transport dinaikkan dari 14/16 dp menjadi 48/28 dp agar tidak
  menutupi navigasi sistem (BUG-084). Diukur di HP: tombol putar berakhir di
  y≈1433 px, di atas area navigasi y≈1450 px.
- `PlayerView` kini dibuat ulang lewat `key(playerVersion)` setiap ExoPlayer
  dibuat ulang, sehingga pergantian dekoder tidak memakai permukaan video milik
  player lama (BUG-085). Pergantian Perangkat ↔ Software diuji pada episode
  H.264; frame tetap berubah di kedua arah. Kasus audio-only yang dilaporkan
  tidak berhasil direproduksi pada berkas ini.

Verifikasi: `:app:testDebugUnitTest` **526 tes, 0 gagal**, `:app:lintDebug`
0 error dan 2 peringatan lama, `:app:assembleDebug` sukses. Build uji memakai
`applicationIdSuffix` sementara agar tidak menimpa aplikasi rilis; konfigurasi
itu sudah dikembalikan dan APK final dibangun tanpa suffix.

## Rilis 1.1.43/code57 — 26 Oktober 2026

Rilis ini mengemas pekerjaan yang belum pernah keluar dari build 1.1.42/code56:
login dan episode Premium Bstation lewat WebView resmi (sesi AES-GCM di
`noBackupFilesDir`, cookie hanya untuk host Bstation), pemilih subtitle yang
mengikuti sumber (YouTube memakai "Bahasa asli", Bstation menampilkan bahasa
yang benar-benar dilaporkan), subtitle unduhan yang aktif otomatis, perbaikan
geometri Scrubber setelah perubahan lebar, serta tier resolusi dan kredit seri
episode. Rincian perilaku ada di
[BSTATION_EPISODES_2026-10-25.md](BSTATION_EPISODES_2026-10-25.md) dan
[RESOLUTION_FIXES_1_1_42.md](RESOLUTION_FIXES_1_1_42.md).

Verifikasi host pada 26 Oktober 2026 (`bash scripts/build_release.sh
--no-daemon`, 41m 17s, BUILD SUCCESSFUL):

- **526 tes Kotlin**, 0 gagal/error/skip.
- `:app:lintDebug` dan `:app:lintRelease`: **0 error, 3 warning** lama
  (`SelectedPhotoAccess`, `AndroidGradlePluginVersion`, `GradleDependency`).
- Suite Python: **98 tes lulus** (45,8 detik).
- APK ARM64 76.320.802 byte, SHA256
  `46721f650be26aa924112ac247fa7980bb1c2a046f143dd12e94da0b370d9a42`;
  signature produksi v2 (`CN=TubeFlow Release`), non-debuggable,
  zipalign 16 KB lulus, **262 ELF** dan **13 kelas ZIP** diaudit tanpa
  kegagalan. AAB 273.266.413 byte, SHA256
  `233555ca4f7d5c7a1b98d7054885ed0b62c9c9fd8ffafcd82710fd098f12035f`.
- Artefak berada di `app/build/outputs/apk/release/` dan
  `app/build/outputs/bundle/release/`.

Uji perangkat tercatat di [DEVICE_TEST.md](DEVICE_TEST.md). Login Bstation
dengan akun nyata, akses episode Premium/geo dari jaringan Indonesia, dan
pemutaran episode Bstation belum dijalankan pada build ini.

## Rilis 1.1.44/code58 — 26 Oktober 2026

Rilis ini memperbaiki login Bstation di WebView (BUG-086). Halaman memakai
`max-height: 90vh`, sedangkan WebView perangkat mengukur `vh` sebagai 0 px
walau `innerHeight` 715 px; kotak dialog login runtuh setinggi nol sehingga
hanya teks yang terlihat dan tombol Facebook/Google/Twitter/Email tidak
tergambar. `BstationLoginDialog` kini menghormati `<meta viewport>`,
mengganti batas tinggi dialog dengan piksel (`0,86 × innerHeight`, diperbarui
saat resize), dan menutup otomatis interstitial “Silakan unduh aplikasi”.
Bukti sebelum/sesudah diambil lewat remote debugging WebView (CDP).

Verifikasi host (`bash scripts/build_release.sh --no-daemon`, 20m 26s):
**526 tes Kotlin** 0 gagal, lint debug/release **0 error/3 warning** lama,
APK ARM64 76.322.174 byte SHA256
`78c35a53959172a114e546148b71f1b5ea2aa57a2940cf610949fd2dfe67447d`,
signature produksi, zipalign 16 KB, 262 ELF dan 13 kelas ZIP diaudit tanpa
kegagalan.

Uji perangkat dengan akun Bstation nyata berhasil untuk login email, katalog
season, kunci Premium, dan unduhan satu episode gratis 720p. Rincian dan dua
temuan baru (BUG-087 subtitle season tidak ikut subfolder, BUG-088 judul
`file://` ber-`%20`) ada di [DEVICE_TEST.md](DEVICE_TEST.md).

## Player dan lampiran 1.1.45/code60 — 27 Oktober 2026

Rasio GL diperbaiki dengan Presentation, geometri software terpisah, dan
persiapan ulang saat jeda mempertahankan posisi/play intent. Kecerahan otomatis,
penghemat sistem/aktif/nonaktif, info lampiran bersama, ketuk Riwayat, dan
preview Status terintegrasi. Penemuan subtitle satu folder dipakai label dan
player; pekerjaan async dijaga identitas sesi. Room 10 tetap, tanpa dependensi,
izin atau migrasi baru.

536 tes Kotlin/98 Python lulus, lint debug/release 0 error/3 warning lama;
build debug/release signed lulus 11m13s. Final ARM64 76.357.958 byte,
SHA256 `64899c7b4e26e1d133026aa07ec7b768e1d815ee057f00ca28803d7880458c4c`.
Audit 262 ELF/13 kelas ZIP, signature/zipalign16KB lulus. Update di Android10
berhasil, hash identik, 15 path media tetap. AAB tidak dibangun ulang.

HP membuktikan subtitle terlihat dan tiga rasio berubah saat jeda di7:12.
Preview final dibuka tanpa ekspor; pemeriksaan rentang/play-to-end sebelumnya
tercatat terpisah. BUG-087 publikasi baru dan BUG-092 galat sesaat sesi audio
lama tetap terbuka. [Bukti, batas dan catatan pemeliharaan](MEDIA_POLISH_1_1_45.md).

## Kecerahan dan volume 1.1.46/code61 — 27 Oktober 2026

Kecerahan Otomatis/Manual pada Tampilan, gestur kiri ke Manual, serta penguat
video Normal/+3/+6/+9dB melalui PCM dengan pembatas puncak. Musik, file asli,
volume/brightness global, Room10 dan izin tetap. Normal memakai jalur semula;
boost melepas passthrough/offload hanya pada sesi yang memakainya.

543 tes Kotlin/98 Python lulus; lint debug/release0error/3warning lama. Build
debug/release signed lulus11m57s, ARM64 76.376.658byte, SHA256
`e80bd42ce9251941e0971727df63cd134d4fc1eb9b88227142fa6efcff06a4ca`. Audit262ELF/13ZIP,
signature/zipalign16KB lulus; update Android10 berhasil, hash identik.
Manual24→65% mengubah override498→1340; Auto mengembalikan−1. Gain6/9/Normal
mempertahankan14:57/jeda/subtitle; Software+6 aktif. Pengguna mengonfirmasi
+6dB jelas/bersih pada volume HP terlapor50%; penguat6 dipertahankan, kecerahan
Auto dan dekoderAuto.15path media tetap. AAB tidak dibangun ulang.

[Bukti, batas dan file terdampak](PLAYER_BRIGHTNESS_VOLUME_1_1_46.md). Sensor
fallback/ruangan terkontrol, semua rute/codec/ABI, runtime16KB dan performa
belum teruji menyeluruh; BUG087/092 tetap, tidak ada klaim produksi lengkap.

## Titik akhir klip Status — 27 Oktober 2026

Dialog Potong Status sebelumnya hanya menggeser awal dan selalu memakai 30 detik
berikutnya. Sekarang pengguna memilih **Awal** dan **Akhir** pada dua slider; rentang
dijepit ke 1–30 detik dan tidak melewati durasi video. `StatusClip.ClipRange`
menggantikan perhitungan `start + 30 detik`, dan `StatusClipper` meneruskan akhir
pilihan ke FFmpeg (`-t` = akhir − awal) untuk stream-copy maupun encode ulang.
Pratinjau mengikuti rentang yang sama.

Verifikasi: 9 regresi `StatusClipTest` lulus dengan kotlinc/JUnit di luar Gradle.
Gradle penuh, lint, build, dan uji perangkat belum dijalankan pada sesi ini; versi
aplikasi belum dinaikkan.

## Tindak lanjut bug terbuka — 27 Oktober 2026

**BUG-092.** `AudioErrorPolicy` memberi satu muat ulang untuk error MediaPlayer
transien (`UNKNOWN`, `SERVER_DIED`, `IO`, `TIMED_OUT`) pada posisi terakhir;
codec/format rusak tidak diulang. `PlayerViewModel.load` menerima
`retryOnError` sehingga percobaan kedua tidak dapat berulang tanpa batas, dan
log hanya memuat `what`/`extra` tanpa URI/judul. Ini mitigasi gejala; akar
pesan sesi lama setelah update belum dibuktikan dan belum ada uji perangkat baru.

**BUG-087.** Penamaan sidecar dipusatkan di `SidecarPublication`. Suffix bahasa
yang valid (`.id`, `.zh-Hant`) dipertahankan, nama sidecar yang tidak berasal
dari stem media tidak lagi menempel ke judul, dan `requestedName` memakai stem
media yang benar-benar diterbitkan. Media dan sidecar tetap memakai
`relativePath`/`parentDoc` yang sama; tidak ada pemindahan file lama otomatis.
Regresi `SidecarPublicationTest` (5 kasus) mengunci perilaku ini. Reproduksi
unduhan perangkat baru tetap terbuka.

**BUG-093.** Tidak ada perubahan gain default; penguat tetap opt-in
Normal/+3/+6/+9 dB. Kasus uji +6 dB yang dikonfirmasi pengguna tetap menjadi
bukti utama; akar seluruh selisih antarpemutar belum diklaim.

Verifikasi host: **18 tes Kotlin murni lulus** (9 `StatusClipTest`, 4
`AudioErrorPolicyTest`, 5 `SidecarPublicationTest`) dengan kotlinc/JUnit di luar
Gradle, serta **98 tes Python OK (1 skip)** setelah FFmpeg statis lokal
disediakan. `git diff --check` bersih. Gradle penuh, lint, build APK/AAB, dan uji
perangkat belum dijalankan pada sesi ini.
