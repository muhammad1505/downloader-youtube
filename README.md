# TubeFlow

Aplikasi Android Kotlin/Compose untuk mengunduh dan mengelola audio/video multi-platform langsung di perangkat. Tidak membutuhkan backend. Android 10 (API 29) atau lebih baru.

## Fitur

- Tempel link atau bagikan link YouTube ke TubeFlow, dengan deteksi otomatis link di papan klip.
- Link video maupun playlist: picker dengan pilih semua/per video dan pencarian judul. Mix/radio membaca antrean awal sesuai respons YouTube, tanpa pagination otomatis; daftar dapat berbeda antar akun/sesi.
- Tautan lagu, album, dan playlist Spotify (`open.spotify.com/track|album|playlist/…`) dibaca metadata publiknya tanpa login (judul, artis, durasi, sampul, daftar lagu), lalu dicocokkan dengan kandidat video YouTube. Track tunggal menampilkan pilihan kandidat; album/playlist menampilkan daftar lagu dengan pencarian dan pilihan, pencocokan per lagu yang bisa dibatalkan, lalu diantrekan sebagai job terpisah dengan tag Spotify (playlist mengikuti batas 100 lagu pertama dari halaman embed). Audio tetap diunduh dari video YouTube; ini bukan unduhan audio Spotify — stream Spotify ber-DRM dan engine tidak punya extractor-nya. Langkah dan skenario uji perangkatnya ada di [validasi Spotify](docs/SPOTIFY_DEVICE_VALIDATION.md).
- Episode dan season Bstation/Bilibili TV (`bilibili.tv/.../play/<season>` atau `/media/<season>`, episode `/play/<season>/<ep>`, dan video UGC `/video/<id>`) memakai extractor BiliIntl; tautan pendek resmi `bili.im/...` dibuka dulu ke halaman season/episode aslinya. Halaman season membuka pemilih episode: episode gratis bisa dipilih/diantrekan per job ke satu folder season, dan setelah masuk lewat halaman login resmi Bstation di Pengaturan → Akun Bstation, episode Premium juga bisa dipilih/diantrekan. Akses Premium tetap mengikuti langganan akun; salinan sesi disimpan terenkripsi di perangkat. DRM tidak didukung.
- Pratinjau judul, pembuat, durasi, thumbnail, dan daftar subtitle/CC yang tersedia.
- Subtitle/CC untuk video lewat pengaturan lanjutan, dengan pilihan bahasa dan penyematan soft-sub. Mode audio mencari lirik tersinkron dari LRCLIB dan menyimpannya sebagai `.lrc` terpisah; status lirik ditampilkan di riwayat.
- Sampul album (cover): khusus mode audio, disematkan langsung ke file audio (MP3/M4A/Auto) tanpa file gambar terpisah. Sampul dikompresi ke JPEG, maksimal 800 piksel pada sisi terpanjang, dengan rasio gambar tetap.
- Pilihan cepat (preset): 1080p/720p/4K MP4, MP3 320/128, M4A, FLAC, plus pilihan lanjutan resolusi (hingga 8K) / format / bitrate.
- Video: Auto, MP4, MKV, WebM, MOV, AVI; resolusi Auto hingga 4320p.
- Audio: Auto, MP3, M4A, AAC, Opus, OGG Vorbis, FLAC, WAV; bitrate Auto dan pilihan manual.
- Antrean persisten, notifikasi background, pembatalan, coba lagi, dan ulangi semua yang gagal. Progres per tahap: unduh audio/video dan simpan memakai persen terukur; pencarian lirik, konversi, dan pemrosesan sampul memakai indikator tanpa persen tebakan.
- Duplikat diperiksa memakai ID video dan seluruh opsi keluaran, termasuk URL lama di riwayat. Bentuk link berbeda tidak menghindari pemeriksaan; hasil dengan opsi berbeda tetap bisa diunduh.
- Antrean dan riwayat tampil dalam satu daftar tanpa tab terpisah; riwayat lokal dengan pencarian dan filter, statistik unduhan, hapus per entri atau bersihkan.
- Buka/bagikan hasil dan file subtitle melalui URI MediaStore; info pemakaian penyimpanan per folder.
- Tema terang/gelap mengikuti sistem atau pilihan manual, plus warna dinamis (Material You) di Android 12+.
- Semua menu memakai token/panel bersama; font besar dapat membungkus metrik dan tindakan. Rincian perbaikan pembukaan video serta LRCLIB ada pada [laporan 1.1.18](docs/REPAIR_2026-10-18.md).
- Splash screen mengikuti tema aplikasi (terang/gelap) saat aplikasi dibuka.
- Folder penyimpanan bisa diubah di Pengaturan → Penyimpanan (nama satu segmen di bawah Downloads/).

- Smart Media: satu katalog Semua/Musik/Video, terpisah dari riwayat; pemindaian otomatis tanpa salinan media. Pencarian, filter folder/artis/album, sortir, detail, thumbnail; audio memakai pemutar TubeFlow dan video memakai Media3 ExoPlayer di dalam aplikasi (opsi buka di aplikasi lain tetap tersedia).
- Pemutar video Media3 ExoPlayer: antrean video Koleksi/Riwayat, scrub dan ±10 detik, gestur seek/volume, kecerahan Otomatis/Manual dan penguat volume video opsional, kecepatan 0,25–2×, rasio layar, ulangi, pilihan track audio/subtitle termasuk subtitle hasil unduhan, lanjut tonton per item antrean (termasuk saat pindah/auto-lanjut), kontrol play/pause di jendela Picture-in-Picture, dan auto-PiP Android 10+. Orientasi mengikuti sensor saat pemutar terbuka (dengan pilihan otomatis/lanskap/potret), kontrol bawah menyesuaikan potret/lanskap dan safe area, pengaturan subtitle mencakup ukuran/gaya/posisi/latar/delay yang tersimpan, serta dukungan dekoder software FFmpeg/dav1d (H.264/HEVC/VP8/VP9/AV1, AC3/EAC3/DTS/TrueHD, Opus/FLAC/ALAC) saat codec tidak didukung perangkat; bila tetap gagal, video dapat dibuka di pemutar lain. TubeFlow juga terdaftar di chooser "Buka dengan" untuk berkas audio/video lokal: audio masuk pemutar lagu dan video masuk pemutar Media3, tanpa membuat pemutar kedua. Pemutar audio tetap memakai jalur terpisah.
- Antrean putar dan posisi tersimpan sebagai snapshot lokal; pemulihan proses dibuka dalam keadaan jeda. Pengaturan menyediakan pilihan lanjut otomatis setelah interupsi, tanpa mengubah auto-play pada sambungan headset baru.
- Detail error inspeksi/download/lirik dapat disalin secara manual dengan redaksi URL/token/path; tidak dikirim otomatis. Pengaturan membedakan cache dan berkas sementara download dari media; cleanup parsial berkonfirmasi memulai retry dari nol dan melewati worker aktif.
- Pemutar audio: mini player beraksen sampul, swipe sampul/lirik, pencarian/editor lirik, seek, kecepatan, equalizer, antrean dengan pencarian judul/artis dan pengaturan urutan, acak/ulangi, dan kontrol notifikasi dengan tombol tutup. Ukuran file Koleksi terbaca di Pengaturan tanpa grant folder tambahan; ukuran folder lengkap dibedakan dari ukuran katalog.
- Karaoke opsional di perangkat: pilih waktu cuplikan, dengarkan, lalu buat lagu penuh atau simpan cuplikan dengan konfirmasi. Hasil terikat pada lagu/mode/posisi; Batalkan/Buang pratinjau membersihkan data privat setelah proses berhenti, termasuk fragmen model. Audio asli, model terverifikasi dan hasil Koleksi dipertahankan. Model diunduh terpisah; proses CPU dapat lama dan pengurangan vokal bergantung rekaman.
- Perbandingan Asli/Instrumental memakai player yang sama dan dapat kembali ke antrean sebelumnya. Hasil karaoke dapat disimpan sebagai FLAC, MP3 atau AAC/M4A; penyimpanan model, pratinjau dan data sementara terpisah di Pengaturan.
- Favorit dan playlist pribadi menyimpan referensi tanpa menggandakan musik. Timer tidur menyediakan durasi 1–180 menit atau selesai lagu, dengan fade pada akhir countdown.
- Cadangan JSON terverifikasi melalui pemilih dokumen: pengaturan portabel, Favorit/playlist, metadata katalog dan pilihan/koreksi lirik. Pratinjau pemulihan melaporkan media hilang/ambigu; penggantian LRC lama memerlukan pilihan eksplisit. Audio/video dan izin folder tidak disalin.
- Smart Audio: normalisasi nama, tag khusus audio yang menjaga informasi valid, pengaturan otomatis artwork/lirik/katalog. Tahun unggah dan nama uploader tidak dijadikan tahun/artis lagu.
- Koreksi waktu LRC per lagu (±30 detik), baseline asli, preview dan reset; cocok untuk offset konstan, bukan perbedaan aransemen/drift.
- Rename pasangan audio–LRC MediaStore dengan verifikasi/rollback, serta hapus permanen berkonfirmasi. Dukungan rename SAF bergantung kemampuan provider.
- Ringkasan: jumlah/ukuran media tersedia, status metadata/artwork/lirik, penyimpanan internal, aktivitas harian/mingguan/platform sejak upgrade. Riwayat yang dihapus dan statistik lama tidak ditebak.

Detail implementasi, migrasi, hasil tes dan batas cakupan: [laporan Smart Media](docs/SMART_MEDIA_UPGRADE_2026-10-13.md).
Perbaikan terbaru: [kecerahan Otomatis/Manual dan penguat video 1.1.46](docs/PLAYER_BRIGHTNESS_VOLUME_1_1_46.md); [player, lampiran, preview Status dan penghemat baterai 1.1.45](docs/MEDIA_POLISH_1_1_45.md).
Fitur terbaru: [daftar Antrean/Riwayat terpadu 1.1.31](docs/PROGRESS.md); [inspeksi bertahap, cache metadata, dan LRC manual 1.1.30](docs/PROGRESS.md); [pembaruan engine yt-dlp di Pengaturan 1.1.28–1.1.29](docs/ENGINE_UPDATE_1_1_29.md).
Kontrol terbaru: [ukuran kontekstual dan layout adaptif 1.1.25](docs/ADAPTIVE_CONTROLS_1_1_25.md).
Perbaikan terbaru: [enam aksi kartu, menu ⋮, dan caption berbagi 1.1.27](docs/CARD_ACTIONS_1_1_27.md); [aksi kartu seragam dan EQ bass penuh 1.1.26](docs/CARDS_EQ_1_1_26.md); [aksi video/bagikan, kontrol ringkas dan EQ 1.1.23](docs/ACTIONS_EQ_1_1_23.md); [ukuran kontrol, ikon playlist dan rincian cache 1.1.22](docs/CONTROLS_CACHE_1_1_22.md); [cache, notifikasi dan playlist 1.1.20](docs/NOTIFICATIONS_PLAYLIST_1_1_20.md); [enam peningkatan media1.1.17](docs/MEDIA_FEATURES_1_1_17.md); [alur cuplikan, identitas hasil dan cleanup karaoke 1.1.16](docs/KARAOKE_LOGIC_1_1_16.md); [pemulihan player, diagnostik, cache/parsial dan lookup lirik1.1.15](docs/IMPROVEMENTS_1_1_15.md); [lanjut musik setelah interupsi audio1.1.14](docs/AUDIO_FOCUS_1_1_14.md); [pacing, cache dan waktu tunggu LRCLIB1.1.13](docs/LYRICS_RATE_LIMIT_1_1_13.md); [nama, metadata, lirik dan pemulihan Koleksi1.1.12](docs/METADATA_LYRICS_LIBRARY_1_1_12.md). Pemutar: [pencarian antrean dan auto-play1.1.10](docs/PLAYER_AUTOPLAY_1_1_10.md).

Auto memilih format sumber/kualitas tersedia. Bitrate manual dengan format audio Auto memakai MP3 agar bitrate dapat diterapkan. Bitrate lebih tinggi dan format lossless tidak meningkatkan kualitas sumber. Resolusi manual membatasi kualitas maksimum, bukan menjamin sumber tersedia pada resolusi itu.

## Build

JDK 17, Android SDK 37, Build Tools 36.0.0, NDK 27.1.12297006, CMake 3.22.1. Gradle wrapper memakai 9.8.1 dengan AGP 9.1.1, Kotlin bawaan AGP, dan Compose compiler 2.4.21. FFmpeg AAR lokal merupakan artefak build (135 MB), tidak disimpan dalam Git. Untuk checkout baru, bangun payload dengan source WebP yang sudah dipin dan diaudit:

Gunakan Android SDK Command-line Tools 23.0 atau lebih baru agar paket platform `android-37.0` dikenali. Minimum perangkat tetap Android 10 (`minSdk = 29`).

```sh
sdkmanager "platforms;android-37.0" "build-tools;36.0.0" "ndk;27.1.12297006" "cmake;3.22.1"
bash scripts/rebuild_ffmpeg_webp.sh
```

Setelah itu buka folder sebagai project di Android Studio atau jalankan:

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease
```

Jika Google Maven tidak dapat dijangkau dari jaringan pengembangan, mirror dapat diaktifkan secara eksplisit:

```sh
./gradlew -PuseGoogleMirror=true testDebugUnitTest lintDebug assembleDebug
```

Release membutuhkan keystore milik pengelola aplikasi. Jangan menyimpan password atau keystore dalam Git. Variabel lingkungan: `TUBEFLOW_KEYSTORE_PATH`, `TUBEFLOW_KEYSTORE_PASSWORD`, `TUBEFLOW_KEY_ALIAS`, `TUBEFLOW_KEY_PASSWORD`. Tanpa konfigurasi ini, release bersifat unsigned.

Keystore produksi lokal sudah dibuat; konfigurasi privat berada di `$HOME/.local/share/tubeflow/signing/release-signing.env`. Build APK ketiga ABI dan Android App Bundle yang ditandatangani:

```sh
bash scripts/build_release.sh -PuseGoogleMirror=true --no-daemon
```

Script juga menerima empat variabel signing dari CI; lokasi file lokal dapat diganti melalui `TUBEFLOW_SIGNING_ENV`. Backup keystore dan kredensial secara aman; gunakan kunci yang sama untuk pembaruan aplikasi. Lihat [panduan signing produksi](docs/PRODUCTION_SIGNING.md).

## Status rilis

Daftar temuan bug dan panduan pengerjaan berikutnya tersedia di [docs/BUG_FINDINGS.md](docs/BUG_FINDINGS.md), termasuk prioritas, lokasi kode, status perbaikan, dan checklist verifikasi.

Hasil [audit ulang 9 Oktober 2026](docs/REVIEW_2026-10-09.md): build dan tes host lulus, tetapi masih ada celah pencocokan lirik, cleanup, dan publikasi sidecar; seluruh temuan belum dinyatakan tuntas.

Usulan fitur dan optimasi berikutnya tersedia di [docs/DEVELOPMENT_ROADMAP.md](docs/DEVELOPMENT_ROADMAP.md), termasuk peningkatan pencocokan lirik, cari ulang lirik, koreksi waktu, cache, progres, dan diagnostik.

Kandidat **2 November 2026 — 1.1.68 (83)**: Pemutaran video tidak lagi tersendat berkala karena pipeline efek warna hanya dibangun ulang ketika pengaturannya benar-benar berubah, dan pengaturan subtitle hanya diterapkan saat nilainya berubah. Mode PiP kembali berjalan tanpa galat dekoder: pemutar tidak lagi dilepas ketika jendela PiP aktif, hanya dijeda saat aplikasi benar-benar ke latar. Gambar di layar penuh tidak terpotong di tepi sehingga hard sub tetap utuh. Tema Transformers memakai bentuk dan palet pemutarnya sendiri, sedangkan Cyberpunk memakai lencana turquoise-cyan dan aksen tema tetap alih-alih warna sampul. **Verifikasi host:** `bash scripts/rebuild_ffmpeg_webp.sh` lalu `./gradlew testDebugUnitTest lintDebug assembleDebug` lulus (BUILD SUCCESSFUL, 55 tugas, termasuk `compileDebugKotlin` dan `lintReportDebug`); `changelog.json` divalidasi untuk code83 lewat `AppChangelogTest`. **Batas:** build rilis, audit native 16 KB, dan uji perangkat 1.1.68 belum dijalankan, jadi jeda, PiP, dan potong gambar pada APK rilis belum diverifikasi di HP; log `adb logcat -s TubeFlow-video` disiapkan untuk memeriksanya.

Kandidat **2 November 2026 — 1.1.67 (82)**: Tautan pendek resmi Bstation (`https://bili.im/...`) kini dibuka ke halaman season/episode/video aslinya sebelum divalidasi, sehingga tautan seperti `https://bili.im/JKqzH3H` membuka pemilih episode season `2432940` dan bisa diunduh seperti tautan `bilibili.tv`. Resolver hanya menerima canonical Bstation yang sah; tautan rusak atau halaman asing tetap ditolak dengan pesan yang jelas. **Verifikasi host:** `./gradlew testDebugUnitTest lintDebug --no-daemon` lulus (635 tes Kotlin, 0 gagal; lint 0 error/4 warning), termasuk `BstationUrlsTest` (10 kasus, termasuk parsing canonical/og:url) dan `AppChangelogTest`; resolver live di host mengubah link laporan menjadi `https://www.bilibili.tv/play/2432940`; `git diff --check` bersih dan `changelog.json` divalidasi untuk code82. **Batas:** build rilis/APK, audit native 16 KB, dan uji perangkat 1.1.67 belum dijalankan, jadi pemilih episode dan unduhan Bstation pada build ini belum diverifikasi di HP.

Kandidat **2 November 2026 — 1.1.66 (81)**: Tema Transformers kini konsisten di pemutar penuh, antrean unduhan, dan dialog: header bersudut dengan lambang, sampul berbingkai gradien, tombol aksi dan indikator halaman bersudut, banner antrean berikon dengan aksi massal, serta judul dialog berikon dan garis aksen. Sheet antrean putar dan opsi video ikut bersudut; tema lain tetap memakai tampilan lama. **Verifikasi host:** `./gradlew testDebugUnitTest lintDebug` lulus (632 tes Kotlin, 0 gagal; lint 0 error/4 warning), termasuk `AppChangelogTest` dan `ThemeContrastTest`; `git diff --check` bersih dan `changelog.json` divalidasi untuk code81. **Batas:** kompilasi JVM memakai AAR FFmpeg upstream resmi karena AAR 16 KB lokal belum dibangun; build rilis/APK, audit native 16 KB, dan uji perangkat untuk 1.1.66 belum dijalankan, jadi tampilan, TalkBack, dan perilaku runtime belum terverifikasi di HP.

Kandidat **2 November 2026 — 1.1.65 (80)**: Folder di tab Koleksi kini tampil sebagai kartu dua kolom dengan nama folder, induk path, dan jumlah item; mengetuk kartu membuka isinya, sedangkan breadcrumb “Koleksi › path” mengembalikan ke grid. Pencarian, Favorit, dan filter playlist tetap menampilkan berkas langsung. Menu Alat dan daftar kategori Pengaturan memakai grid kartu dua kolom; kartu Alat langsung membuka dialognya tanpa tombol perantara, dan kartu Pengaturan tetap membuka kategorinya. **Verifikasi host:** `./gradlew testDebugUnitTest lintDebug assembleDebug` lulus (631 tes Kotlin, 0 gagal; lint 0 error/4 warning), termasuk `AppChangelogTest` dan `CollectionGroupsTest`; `git diff --check` bersih dan `changelog.json` divalidasi untuk code80. **Batas:** build rilis, audit paket, dan uji perangkat untuk 1.1.65 belum dijalankan, jadi tata letak kartu, breadcrumb, dan TalkBack belum terverifikasi di HP.

Kandidat **2 November 2026 — 1.1.64 (79)**: Header folder di tab Koleksi kini bisa diketuk untuk melipat atau membuka isi grup; seluruh baris header menjadi target ketuk dan chevron menunjukkan statusnya. Status lipat diingat selama sesi (berpindah tab, mengganti filter, memuat halaman berikutnya, rotasi layar) karena disimpan di ViewModel, tetapi tidak ditulis ke disk. Jumlah item dan header lengket tetap tampil saat grup dilipat. **Verifikasi host:** `./gradlew testDebugUnitTest lintDebug` lulus (631 tes Kotlin, 0 gagal; lint 0 error/4 warning), termasuk `AppChangelogTest` dan `CollectionGroupsTest`; `git diff --check` bersih dan `changelog.json` divalidasi untuk code79. Kompilasi pertama sempat gagal karena import `stickyHeader` lama yang tidak lagi tersedia di Compose 1.12; import itu dihapus dan build diulang sampai lulus. **Batas:** build rilis, audit paket, dan uji perangkat untuk 1.1.64 belum dijalankan, jadi interaksi lipat dan pembacaan TalkBack belum terverifikasi.

Kandidat **2 November 2026 — 1.1.63 (78)**: Tab Koleksi mengelompokkan media per folder dengan header lengket; setiap header menampilkan jumlah item dari seluruh database untuk filter aktif, jadi angkanya tetap benar meski baru sebagian halaman dimuat lewat Muat lebih banyak. **Verifikasi host:** `python3 -m unittest scripts.test_media_database -v` lulus (9 tes) termasuk regresi hitungan folder versus `LIMIT` halaman dan validasi `changelog.json`; `./gradlew testDebugUnitTest lintDebug` belum dijalankan karena JDK/Android SDK tidak tersedia di lingkungan ini. **Batas:** build rilis, audit paket, dan uji perangkat untuk 1.1.63 belum dijalankan, jadi header lengket pada perangkat dan APK 1.1.63 belum terverifikasi.

Kandidat **1 November 2026 — 1.1.62 (77)**: Katalog menyegarkan folder koleksi tambahan saat aplikasi kembali aktif atau tab Koleksi dibuka, jadi berkas yang masuk setelah folder didaftarkan muncul tanpa menekan Segarkan; penyegaran dibatasi paling sering sekali per 5 detik agar tidak memindai berulang. **Verifikasi host:** `./gradlew testDebugUnitTest lintDebug` lulus (627 tes Kotlin, 0 gagal; lint 0 error/4 warning), termasuk `AppChangelogTest` dan `CollectionFoldersTest`. **Batas:** build rilis, audit paket, dan uji perangkat untuk 1.1.62 belum dijalankan, jadi belum ada APK 1.1.62 yang dipublikasikan.

Kandidat sebelumnya **2 November 2026 — 1.1.61 (76)**: Pengaturan → Penyimpanan dapat menambahkan hingga 8 folder koleksi SAF (mis. kartu memori) tanpa mengubah tujuan download, dan melepas folder hanya membersihkan indeks sehingga file tetap utuh. Video yang dibatasi wilayah YouTube kini dikenali sebagai kegagalan geo, bukan video hilang; spinner pemutar hanya tampil saat `STATE_BUFFERING` sehingga tidak berkedip ketika buffer latar diisi ulang; pemeriksaan rilis yt-dlp berjalan paling sering sekali sehari saat aplikasi dibuka dan pemasangan tetap menunggu pilihan pengguna. **Verifikasi host:** `./gradlew testDebugUnitTest assembleDebug` lulus (termasuk `AppChangelogTest` dan regresi `CollectionFoldersTest`), `lintDebug` 0 error/4 warning lama pada kode yang sama sebelum nomor versi dinaikkan. **Batas:** build rilis, audit paket, dan uji perangkat untuk 1.1.61 belum dijalankan, jadi belum ada APK 1.1.61 yang dipublikasikan.

Rilis terpasang terakhir **28 Oktober 2026 — 1.1.50 (65)**: kontrol cepat pemutar video menjadi ikon ringkas dengan status lewat ketukan; mode **Penuh** memotong tepi tanpa distorsi dan **Zoom** memperbesar 1,25×; shortcut **Unduh** memfokuskan kolom tautan dan **Riwayat** membuka daftar khusus; changelog dibaca dari `assets/changelog.json`. Build release ARM64 **76.425.021 byte**, SHA-256 `ee453d9122528544f8a2cdfa32c409b4239f10ff51aef0b68303ac98f5a2a84a`; **584 tes Kotlin lulus**, lint 0 error/3 warning, signature v2, zipalign 16 KB, audit 262 ELF dan 13 kelas ZIP lulus. [Unduh APK ARM64 1.1.50](https://files.catbox.moe/ty6ys1.apk) (Catbox). **Batas:** hanya APK ARM64 yang dipublikasikan, AAB tidak dibangun, dan uji perangkat belum dilakukan karena build berjalan tanpa ADB/HP. [Rincian build](docs/PRODUCTION_SIGNING.md), [status](docs/PROGRESS.md), [bukti host](docs/VALIDATION.md).

Validasi **23 Oktober 2026 — 1.1.31 (45)**: tab Antrean/Riwayat digabung menjadi satu daftar, composer URL selalu terlihat, empty state bersih, aksi massal Antrean hanya muncul untuk lebih dari satu job, dan tools Riwayat muncul setelah lebih dari tiga entri atau saat filter aktif. **405 tes Kotlin/98 tes Python lulus; lint 0 error/3 warning** (SelectedPhotoAccess, pembaruan Gradle, pin ONNX yang disengaja). Signed APK×3/AAB lulus; ARM64 70.946.520 byte SHA256 `cd265cee31516c9a5f4a0e3a70388fbc704617ebde190ad50cb29bef9978c18f`, AAB 256.041.323 byte SHA256 `7341a6adf0fc3ffc4bb62df941828682cc9f05390ba5ba4a18b528028a8c3e80`; audit APK 257/257/259 ELF/13 kelas ZIP, zipalign 16 KB, signature, dan AAB 1.038 ELF/jarsigner lulus. Release terpasang sebagai update di Realme Android 10, `base.apk` identik; keadaan kosong, job aktif + riwayat (debug bertanda tangan kunci yang sama), jeda/lanjut, batal/hapus, riwayat setelah force-stop, dialog Bersihkan, dan deep link diperiksa. **Batas:** state Antrean aktif pada build release, pencarian/filter >3 entri, TWS/Android 12+, fault injection provider/process death, runtime 16 KB, ABI/Android lain, serta matriks font/landscape belum diuji. [Ringkasan](docs/PROGRESS.md), [bukti host](docs/VALIDATION.md).

Validasi **23 Oktober 2026 — 1.1.30 (44)**: inspeksi link menampilkan fase dan elapsed setelah 3 detik, dapat dibatalkan, dan memakai cache metadata privat 30 menit/maksimal 200 entri yang bertahan setelah force-stop. Dialog lirik menerima tempelan LRC manual dengan validasi timestamp/durasi dan menyimpannya sebagai sidecar. Recovery menahan jurnal saat provider melempar exception; CI memverifikasi checksum engine dan audit native APK release. **402 tes Kotlin/98 tes Python lulus; lint 0 error/3 warning** (SelectedPhotoAccess, pembaruan Gradle, pin ONNX yang disengaja); signed APK×3/AAB, audit APK 257/257/259 ELF/13 kelas ZIP dan AAB 1.038 ELF, zipalign 16 KB, signature, dan AAB lulus. 1.1.30 terpasang di Realme Android 10; fase/cancel inspeksi, cache lintas restart, simpan LRC manual (sidecar 49 byte), dan unduhan BUG-054 MP3 128 (4.023.060 byte) terverifikasi; lirik otomatis gagal karena LRCLIB. **Batas:** TWS/Android 12+, fault injection provider/process death, runtime 16 KB, ABI/Android lain, serta matriks font/landscape belum diuji. [Ringkasan dan checksum](docs/PROGRESS.md), [laporan engine](docs/ENGINE_UPDATE_1_1_29.md).

Validasi sebelumnya **23 Oktober 2026 — 1.1.29 (43)**: updater engine memeriksa rilis stabil resmi GitHub, memverifikasi SHA-256 dari API dan `SHA2-256SUMS` plus isi zipapp, memasang override secara atomik, dan dapat kembali ke engine bawaan. Engine bawaan 2026.10.21. **384 tes Kotlin/93 tes Python lulus; lint 0 error/3 warning**; signed APK×3/AAB dan audit lulus. 1.1.29 terpasang di Realme Android 10; cek “versi terbaru”, blokir saat unduhan aktif, rollback pada 1.1.28, unduhan 320 kbps, dan BUG-054 MP3 128 selesai. **Batas:** rollback tidak ditekan ulang di 1.1.29, runtime 16 KB/ABI lain/Android lain, gangguan jaringan nyata, dan fault injection provider belum diuji; lirik BUG-054 gagal karena LRCLIB. [Laporan dan checksum](docs/ENGINE_UPDATE_1_1_29.md).

Validasi historis **1.1.5 (19)**: input koreksi lirik dalam detik, pencarian/pemilihan kandidat LRCLIB, pencarian setelah rename, navigasi Ringkasan–Download–Antrean–Koleksi, lirik rata tengah dan kontrol kanal L/R. **184 Kotlin/78 Python lulus; lint debug/release 0 masalah.** Signed APK×3/AAB dan audit paket lulus; ARM64 final terpasang di Realme Android 10 dengan hash cocok dan 61 file dipertahankan saat update. Room 9 dan kunci produksi tetap. Karaoke AI belum diimplementasikan. [Hasil dan batas pengujian](docs/MANUAL_LYRICS_1_1_5_2026-10-14.md).

Validasi sebelumnya **13 Oktober 2026 — 1.1.2 (16), Room9**:149 tes Kotlin dan76 tes Python lulus, lint debug/release0masalah; signed APK×3/AAB, audit native/ZIP/alignment/signature dan bundletool lulus. Kredit judul/penyanyi eksplisit berlabel Title/Voc kini divalidasi; job playlist baru memakai folder artis/remixer teridentifikasi, dengan fallback aman. APK ARM64 final terpasang pada RealmeAndroid10 dan57file sebelum update tetap identik. Distribusi `app/build/outputs/distribution/1.1.2/`; [perubahan, hasil perangkat dan batas](docs/SOURCE_REVIEW_2026-10-13.md).

Baseline **13 Oktober 2026 — 1.1.1 (15), Room9 (tanpa migrasi)**: 145 tes Kotlin dan 76 tes Python lulus (Python dijalankan ulang pada sesi dokumentasi), lint debug/release 0 masalah; signed APK×3/AAB, audit native/ZIP/zipalign/apksigner lulus. APK ARM64 `437d42759c2b953587f885d70b0a5ffd44fee21dc47dd0774fa088ee847405c0` terpasang sebagai update di Realme Android 10, hash cocok. Dashboard Ringkasan media (donat/batang/garis 7/30/90 hari) diperiksa via UIAutomator + screenshot; pengamanan cari ulang lirik (jurnal backup/read-back/rollback + mutex bersama) lulus fault injection host. **Batas:** retensi media tidak terbukti (pengelola membersihkan media selama build), refetch live/proses mati/provider Android belum diuji. Distribusi lokal `app/build/outputs/distribution/1.1.1/`; [hasil, kontrak dan batas cakupan](docs/DASHBOARD_LYRICS_2026-10-13.md).

Validasi sebelumnya **13 Oktober 2026 — 1.1.0 (14), Room9**: 136 tes Kotlin dan76 tes Python lulus, lint debug/release0masalah; build produksi, audit tiga APK/AAB dan bundletool lulus. APK ARM64 final terpasang sebagai update pada Realme Android10, hash cocok. Migrasi nonempty, katalog tanpa duplikat, rename audio–LRC, impor, hapus fixture, pengaturan, dashboard dan download audio/video diuji. Ya Sudahlah dikonfirmasi sinkron setelah koreksi per rekaman−2detik; audio lama utuh. Distribusi lokal `app/build/outputs/distribution/1.1.0/`; [hasil lengkap, status fitur dan batas cakupan](docs/SMART_MEDIA_UPGRADE_2026-10-13.md).

Validasi sistem **13 Oktober 2026 — 1.0.9 (10)**: 102 tes Kotlin, 61 tes Python, lint debug/release 0 masalah, audit signed APK/AAB lulus. APK ARM64 final terpasang dan hash cocok di Realme Android 10. Migrasi mempertahankan 25 riwayat lama; thumbnail/platform, duplikat URL alias, tag audio dan resume setelah HTTP 403 hingga MP4 selesai diuji. Folder tetap root TubeFlow, ukuran 84,6 MB cocok dua file/88.733.655 byte. Distribusi lokal `app/build/outputs/distribution/1.0.9/`. Cakupan dan batas tes: [laporan sistem](docs/SYSTEM_METADATA_RESUME_2026-10-13.md).

Validasi batch penghitung folder: signed release **1.0.1 (2)**, **60 unit tests Kotlin**, **37 host tests**, lint debug/release (0 masalah), serta signature/alignment/audit native dan ZIP APK ketiga ABI lulus. AAB juga lolos verifikasi signing, bundletool dan audit native. Artefak tersedia di `app/build/outputs/distribution/1.0.1/`; kunci produksi sama dengan 1.0.0 yang sudah terpasang di HP. Release 1.0.1 sudah terpasang dan hash terverifikasi di Realme Android 10: ukuran 246,4 MB sesuai 50 file/258.380.600 byte, tetap benar setelah kembali ke default dan restart. Default tetap `Download/TubeFlow`. Lihat [rincian perbaikan](docs/STORAGE_SIZE_2026-10-10.md) dan [signing produksi](docs/PRODUCTION_SIGNING.md). Rincian toolchain dan sisa deprecation internal AGP ada di [catatan lint](docs/LINT_CLEANUP_2026-10-09.md). Kesiapan distribusi tetap mengikuti [persiapan rilis](docs/RELEASE.md). Payload FFmpeg upstream telah diperbaiki dengan membangun ulang sepuluh library WebP 1.6.0 pada ARM64/x86_64 dengan alignment 16 KB. Audit 448 ELF payload menemukan nol kegagalan alignment 64-bit, dengan simbol ekspor dan dependensi yang sama. Pengujian runtime pada perangkat 16 KB tetap wajib dilakukan.

Mesin yt-dlp disertakan dalam aplikasi. Pembaruan executable dapat diperiksa dan dipasang dari **Pengaturan → Mesin media**: hanya rilis stabil resmi GitHub dengan SHA-256 ganda dan validasi zipapp yang diterima, dipasang atomik, dan dapat dikembalikan ke engine bawaan. Metadata rilis diperiksa otomatis paling sering sekali sehari saat aplikasi dibuka (tanpa mengunduh atau memasang apa pun); pemasangan hanya terjadi setelah pengguna memilih, dan FFmpeg tidak ikut diperbarui.

## Privasi dan dependensi

[Privasi](docs/PRIVACY.md) · [Dependensi](docs/DEPENDENCIES.md). Gunakan hanya konten yang dimiliki atau diizinkan untuk diunduh.

## Skill pengembangan

Skill Codex [tubeflow-dev](.agents/skills/tubeflow-dev/SKILL.md) menyimpan alur kerja, aturan perilaku download/SAF/lirik, serta validasi dan signing. Contoh pemakaian: `$tubeflow-dev perbaiki BUG-013 dan catat hasil pengujiannya`. Skill mengikuti kode dan laporan terkini; status bug tetap pada `docs/BUG_FINDINGS.md`.

Kontrak identitas lagu, migrasi Room, thumbnail/platform dan validasi resume diperinci pada [laporan sistem 13 Oktober](docs/SYSTEM_METADATA_RESUME_2026-10-13.md). Gunakan snapshot `DownloadJob.withMetadata`/`songIdentity` untuk penamaan, folder dan lirik; uploader/creator/remixer tidak otomatis menjadi artis. Tag output harus memakai override `meta_*` melalui `MetadataTags` agar fallback internal yt-dlp tidak mengisi artist dari uploader.

Skill tersimpan dalam repo dan ditautkan ke direktori skill Codex lokal pada komputer ini. Untuk checkout baru, jalankan dari root repo (jangan menimpa skill lokal yang sudah ada):

```sh
mkdir -p "${CODEX_HOME:-$HOME/.codex}/skills"
ln -s "$PWD/.agents/skills/tubeflow-dev" "${CODEX_HOME:-$HOME/.codex}/skills/tubeflow-dev"
```

## APK kandidat testing

`app/build/outputs/apk/rc/TubeFlow-arm64-v8a-testing.apk` adalah versi release ARM64 dengan kunci testing lokal. Untuk membuat ulang setelah build:

```sh
bash scripts/make_test_apk.sh arm64-v8a
```

Script menandatangani, memverifikasi, dan merekam hash artefak lokal. Pengujian HP telah diizinkan pengguna dan berjalan; lihat [hasil perangkat](docs/DEVICE_TEST.md).

Uji APK hasil pembaruan toolchain pada Realme Android 10 (10 Oktober 2026) berhasil untuk download MP3 128 kbps dengan sampul, penyimpanan SAF, pemutaran Poweramp, filter riwayat, dan persistensi pengaturan. Ini belum mencakup seluruh matriks rilis atau menutup bug fungsional pada audit.

Perubahan progres per tahap dan kompresi JPEG (10 Oktober 2026) lulus 49 tes Kotlin, 32 tes Python, lint tanpa masalah, dan uji langsung Realme Android 10: progres video/audio terpisah, pemrosesan tanpa persen tebakan, MP3 tetap identik dengan sampul 97,08% lebih kecil, serta playback Poweramp. Lihat [bukti dan batas cakupan](docs/PROGRESS_COVER_2026-10-10.md).

Perbaikan Mix dan duplikat (10 Oktober 2026) telah terpasang dan diuji langsung di HP: link contoh menampilkan 25 entri awal, dua bentuk URL lain untuk MP3 yang sudah ada ditolak, riwayat/file tetap 3 dan antrean kosong. Lihat [laporan serta batas pengujian](docs/MIX_DUPLICATES_2026-10-10.md).

Release **1.0.0** dengan keystore produksi baru tersedia di `app/build/outputs/distribution/1.0.0/`: APK ARM64/ARMv7/x86_64, AAB, checksum dan sertifikat publik. Build, signature, alignment, audit native/ZIP, dan validasi struktur bundle lulus. Lokasi privat keystore/kredensial serta fingerprint ada di [panduan signing](docs/PRODUCTION_SIGNING.md). APK ini memakai kunci berbeda dari APK testing di HP; instalasi testing dan datanya tetap dipertahankan.
