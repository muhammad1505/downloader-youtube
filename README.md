# TubeFlow

Aplikasi Android Kotlin/Compose untuk mengunduh dan mengelola audio/video multi-platform langsung di perangkat. Tidak membutuhkan backend. Android 10 (API 29) atau lebih baru.

## Fitur

- Tempel link atau bagikan link YouTube ke TubeFlow, dengan deteksi otomatis link di papan klip.
- Link video maupun playlist: picker dengan pilih semua/per video dan pencarian judul. Mix/radio membaca antrean awal sesuai respons YouTube, tanpa pagination otomatis; daftar dapat berbeda antar akun/sesi.
- Pratinjau judul, pembuat, durasi, thumbnail, dan daftar subtitle/CC yang tersedia.
- Subtitle/CC untuk video lewat pengaturan lanjutan, dengan pilihan bahasa dan penyematan soft-sub. Mode audio mencari lirik tersinkron dari LRCLIB dan menyimpannya sebagai `.lrc` terpisah; status lirik ditampilkan di riwayat.
- Sampul album (cover): khusus mode audio, disematkan langsung ke file audio (MP3/M4A/Auto) tanpa file gambar terpisah. Sampul dikompresi ke JPEG, maksimal 800 piksel pada sisi terpanjang, dengan rasio gambar tetap.
- Pilihan cepat (preset): 1080p/720p/4K MP4, MP3 320/128, M4A, FLAC, plus pilihan lanjutan resolusi (hingga 8K) / format / bitrate.
- Video: Auto, MP4, MKV, WebM, MOV, AVI; resolusi Auto hingga 4320p.
- Audio: Auto, MP3, M4A, AAC, Opus, OGG Vorbis, FLAC, WAV; bitrate Auto dan pilihan manual.
- Antrean persisten, notifikasi background, pembatalan, coba lagi, dan ulangi semua yang gagal. Progres per tahap: unduh audio/video dan simpan memakai persen terukur; pencarian lirik, konversi, dan pemrosesan sampul memakai indikator tanpa persen tebakan.
- Duplikat diperiksa memakai ID video dan seluruh opsi keluaran, termasuk URL lama di riwayat. Bentuk link berbeda tidak menghindari pemeriksaan; hasil dengan opsi berbeda tetap bisa diunduh.
- Riwayat lokal dengan pencarian dan filter, statistik unduhan, hapus per entri atau bersihkan.
- Buka/bagikan hasil dan file subtitle melalui URI MediaStore; info pemakaian penyimpanan per folder.
- Tema terang/gelap mengikuti sistem atau pilihan manual, plus warna dinamis (Material You) di Android 12+.
- Splash screen mengikuti tema aplikasi (terang/gelap) saat aplikasi dibuka.
- Folder penyimpanan bisa diubah di Pengaturan → Penyimpanan (nama satu segmen di bawah Downloads/).

- Smart Media: satu katalog Semua/Musik/Video, terpisah dari riwayat; pemindaian otomatis tanpa salinan media. Pencarian, filter folder/artis/album, sortir, detail, thumbnail; audio memakai pemutar TubeFlow dan video memakai pemutar perangkat.
- Pemutar audio: mini player beraksen sampul, swipe sampul/lirik, pencarian/editor lirik, seek, kecepatan, equalizer, antrean dengan pencarian judul/artis dan pengaturan urutan, acak/ulangi, dan kontrol notifikasi dengan tombol tutup. Ukuran file Koleksi terbaca di Pengaturan tanpa grant folder tambahan; ukuran folder lengkap dibedakan dari ukuran katalog.
- Karaoke opsional di perangkat: model diunduh terpisah dan diverifikasi; pilih cuplikan dengan waktu mulai manual atau lagu penuh, dengarkan lalu simpan instrumental FLAC terpisah. Proses CPU dapat lama dan hasil pengurangan vokal bergantung rekaman; audio asli dipertahankan.
- Smart Audio: normalisasi nama, tag khusus audio yang menjaga informasi valid, pengaturan otomatis artwork/lirik/katalog. Tahun unggah dan nama uploader tidak dijadikan tahun/artis lagu.
- Koreksi waktu LRC per lagu (±30 detik), baseline asli, preview dan reset; cocok untuk offset konstan, bukan perbedaan aransemen/drift.
- Rename pasangan audio–LRC MediaStore dengan verifikasi/rollback, serta hapus permanen berkonfirmasi. Dukungan rename SAF bergantung kemampuan provider.
- Ringkasan: jumlah/ukuran media tersedia, status metadata/artwork/lirik, penyimpanan internal, aktivitas harian/mingguan/platform sejak upgrade. Riwayat yang dihapus dan statistik lama tidak ditebak.

Detail implementasi, migrasi, hasil tes dan batas cakupan: [laporan Smart Media](docs/SMART_MEDIA_UPGRADE_2026-10-13.md).
Perbaikan terbaru: [pacing, cache dan waktu tunggu LRCLIB1.1.13](docs/LYRICS_RATE_LIMIT_1_1_13.md); [nama, metadata, lirik dan pemulihan Koleksi1.1.12](docs/METADATA_LYRICS_LIBRARY_1_1_12.md). Pemutar: [pencarian antrean dan auto-play1.1.10](docs/PLAYER_AUTOPLAY_1_1_10.md).

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

Validasi historis **1.1.5 (19)**: input koreksi lirik dalam detik, pencarian/pemilihan kandidat LRCLIB, pencarian setelah rename, navigasi Ringkasan–Download–Antrean–Koleksi, lirik rata tengah dan kontrol kanal L/R. **184 Kotlin/78 Python lulus; lint debug/release 0 masalah.** Signed APK×3/AAB dan audit paket lulus; ARM64 final terpasang di Realme Android 10 dengan hash cocok dan 61 file dipertahankan saat update. Room 9 dan kunci produksi tetap. Karaoke AI belum diimplementasikan. [Hasil dan batas pengujian](docs/MANUAL_LYRICS_1_1_5_2026-10-14.md).

Validasi sebelumnya **13 Oktober 2026 — 1.1.2 (16), Room9**:149 tes Kotlin dan76 tes Python lulus, lint debug/release0masalah; signed APK×3/AAB, audit native/ZIP/alignment/signature dan bundletool lulus. Kredit judul/penyanyi eksplisit berlabel Title/Voc kini divalidasi; job playlist baru memakai folder artis/remixer teridentifikasi, dengan fallback aman. APK ARM64 final terpasang pada RealmeAndroid10 dan57file sebelum update tetap identik. Distribusi `app/build/outputs/distribution/1.1.2/`; [perubahan, hasil perangkat dan batas](docs/SOURCE_REVIEW_2026-10-13.md).

Baseline **13 Oktober 2026 — 1.1.1 (15), Room9 (tanpa migrasi)**: 145 tes Kotlin dan 76 tes Python lulus (Python dijalankan ulang pada sesi dokumentasi), lint debug/release 0 masalah; signed APK×3/AAB, audit native/ZIP/zipalign/apksigner lulus. APK ARM64 `437d42759c2b953587f885d70b0a5ffd44fee21dc47dd0774fa088ee847405c0` terpasang sebagai update di Realme Android 10, hash cocok. Dashboard Ringkasan media (donat/batang/garis 7/30/90 hari) diperiksa via UIAutomator + screenshot; pengamanan cari ulang lirik (jurnal backup/read-back/rollback + mutex bersama) lulus fault injection host. **Batas:** retensi media tidak terbukti (pengelola membersihkan media selama build), refetch live/proses mati/provider Android belum diuji. Distribusi lokal `app/build/outputs/distribution/1.1.1/`; [hasil, kontrak dan batas cakupan](docs/DASHBOARD_LYRICS_2026-10-13.md).

Validasi sebelumnya **13 Oktober 2026 — 1.1.0 (14), Room9**: 136 tes Kotlin dan76 tes Python lulus, lint debug/release0masalah; build produksi, audit tiga APK/AAB dan bundletool lulus. APK ARM64 final terpasang sebagai update pada Realme Android10, hash cocok. Migrasi nonempty, katalog tanpa duplikat, rename audio–LRC, impor, hapus fixture, pengaturan, dashboard dan download audio/video diuji. Ya Sudahlah dikonfirmasi sinkron setelah koreksi per rekaman−2detik; audio lama utuh. Distribusi lokal `app/build/outputs/distribution/1.1.0/`; [hasil lengkap, status fitur dan batas cakupan](docs/SMART_MEDIA_UPGRADE_2026-10-13.md).

Validasi sistem **13 Oktober 2026 — 1.0.9 (10)**: 102 tes Kotlin, 61 tes Python, lint debug/release 0 masalah, audit signed APK/AAB lulus. APK ARM64 final terpasang dan hash cocok di Realme Android 10. Migrasi mempertahankan 25 riwayat lama; thumbnail/platform, duplikat URL alias, tag audio dan resume setelah HTTP 403 hingga MP4 selesai diuji. Folder tetap root TubeFlow, ukuran 84,6 MB cocok dua file/88.733.655 byte. Distribusi lokal `app/build/outputs/distribution/1.0.9/`. Cakupan dan batas tes: [laporan sistem](docs/SYSTEM_METADATA_RESUME_2026-10-13.md).

Validasi batch penghitung folder: signed release **1.0.1 (2)**, **60 unit tests Kotlin**, **37 host tests**, lint debug/release (0 masalah), serta signature/alignment/audit native dan ZIP APK ketiga ABI lulus. AAB juga lolos verifikasi signing, bundletool dan audit native. Artefak tersedia di `app/build/outputs/distribution/1.0.1/`; kunci produksi sama dengan 1.0.0 yang sudah terpasang di HP. Release 1.0.1 sudah terpasang dan hash terverifikasi di Realme Android 10: ukuran 246,4 MB sesuai 50 file/258.380.600 byte, tetap benar setelah kembali ke default dan restart. Default tetap `Download/TubeFlow`. Lihat [rincian perbaikan](docs/STORAGE_SIZE_2026-10-10.md) dan [signing produksi](docs/PRODUCTION_SIGNING.md). Rincian toolchain dan sisa deprecation internal AGP ada di [catatan lint](docs/LINT_CLEANUP_2026-10-09.md). Kesiapan distribusi tetap mengikuti [persiapan rilis](docs/RELEASE.md). Payload FFmpeg upstream telah diperbaiki dengan membangun ulang sepuluh library WebP 1.6.0 pada ARM64/x86_64 dengan alignment 16 KB. Audit 448 ELF payload menemukan nol kegagalan alignment 64-bit, dengan simbol ekspor dan dependensi yang sama. Pengujian runtime pada perangkat 16 KB tetap wajib dilakukan.

Mesin yt-dlp disertakan dalam aplikasi. Pembaruan dilakukan lewat rilis aplikasi; tidak ada pembaruan executable jaringan tanpa verifikasi.

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
