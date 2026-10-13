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
6. **Thumbnail (P2)** — thumbnail SELALU diminta ke yt-dlp (`--write-thumbnail --convert-thumbnails jpg` + kompresi); worker menyalinnya ke `covers/<id>.jpg` (jalur internal bertahan setelah direktori kerja dibersihkan) dan mencatat kolom baru `coverPath` (v7). Kartu menampilkan sampul via `CoverThumb` (decode di IO; gagal/tanpa sampul = ikon mode lama). Sidecar thumbnail tetap hanya bila diminta pengguna; cache ikut dibersihkan bersama direktori job.
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
