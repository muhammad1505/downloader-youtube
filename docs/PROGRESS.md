# Catatan Kemajuan Pengembangan TubeFlow

Dicatat **9 Oktober 2026**. Dokumen ini melengkapi [BUG_FINDINGS.md](BUG_FINDINGS.md) (temuan) dan [DEVELOPMENT_ROADMAP.md](DEVELOPMENT_ROADMAP.md) (usulan): berisi keputusan desain yang diambil saat perbaikan, bukti yang sudah dikumpulkan, dan sisa pekerjaan agar serah-terima tidak mengulang investigasi.

## Dua batch perbaikan yang sudah dijalankan

### Batch A — perbaikan sesuai permintaan pengguna (terverifikasi di perangkat)

1. **Progres ke 100%** — `DownloadWorker.publish` monotonic 0–99 + heartbeat (500 ms) setelah file terakhir ≥98% (maks 98), fase salin 99, `dao.finish` = 100. Terverifikasi di perangkat: 91→96→98→100 tanpa membeku di 90.
2. **Batas konkurensi 3+4=7** — semaphore per-key diganti gerbang tunggal `gateLock`/`activeWorkers` yang membaca `SettingsStore.maxConcurrent` (1–4) setiap kali. Terverifikasi: pengaturan 3 menjalankan tepat 3 paralel.
3. **Jeda/lanjut/batal per-job dan massal** — `JobState.PAUSED`, DAO `pause/pauseAll/resume` (resume naikkan `generation`), aksi di QueueScreen + JobCard. Terverifikasi: jeda di 96%, lanjut, batal tunggal, dialog batal semua.
4. **BUG-001 penyebab utama SAF** — `createDocument` menerima tree URI mentah → Android 10 melempar "Invalid URI" → semua simpanan gagal ±90% saat folder SAF dipilih. Diperbaiki dengan `treeRootUri()`. Terverifikasi: 11 MP3 masuk `/storage/emulated/0/Download/TubeFlow/Denny-Caknan/`.
5. **BUG-003 helper hapus** — `deleteUri` lama memeriksa `scheme == "document"` yang tak pernah cocok untuk `content://`.

Catatan: satu kegagalan perangkat adalah HTTP 403 dari YouTube (rate-limit, bukan bug aplikasi); retry berhasil.

### Batch B — perbaikan berdasarkan dokumen (BUG_FINDINGS + ROADMAP)

Dikerjakan sesuai urutan yang disarankan dokumen. Semua lolos `testDebugUnitTest lintDebug assembleDebug` (43 tes Kotlin) dan `python3 -m unittest` (24 tes Python).

## Keputusan desain yang perlu diketahui penerus

- **Token eksekusi `generation` (BUG-002):** kolom INTEGER DEFAULT 0 di `downloads`, naik saat `resume`/`revive`. SEMUA mutasi status oleh worker memakai `WHERE generation = :gen` (`progress`, `title`, `pending`, `finish`, `failOwned`, `interrupted`, `cancelOwned`); ViewModel memakai versi tanpa syarat generasi. `doWork` membaca generasi SEBELUM `acquireSlot()`. Jangan menghapus syarat ini agar "jeda→lanjut cepat" tidak kena race. Regresi host: `test_stale_generation_cannot_touch_resumed_job`.
- **Migrasi Room 2→3** menambah `generation`, `durationSeconds`, `lyricStatus`, `sidecarPending`. Schema snapshot v3 ada di `app/schemas/…/3.json`; regresi host `test_migration_2_3_alter_columns_match_schema_v3` memastikan SQL migrasi tangan sama persis dengan skema Room. Jangan menambah kolom tanpa memperbarui keduanya.
- **`StorageUris` (BUG-003):** satu-satunya jalur hapus untuk worker, riwayat, dan recovery. Mengembalikan `false` hanya bila file MASIH ADA — pemanggil wajib menganggap gagal (jangan melapor sukses). Jangan kembali ke `contentResolver.delete` langsung.
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

## Sisa pekerjaan

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
