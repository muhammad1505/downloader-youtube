# TubeFlow

Aplikasi Android Kotlin/Compose untuk mengunduh audio/video YouTube langsung di perangkat. Tidak membutuhkan backend. Android 10 (API 29) atau lebih baru.

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

Validasi terbaru: signed release **1.0.1 (2)**, **60 unit tests Kotlin**, **37 host tests**, lint debug/release (0 masalah), serta signature/alignment/audit native dan ZIP APK ketiga ABI lulus. AAB juga lolos verifikasi signing, bundletool dan audit native. Artefak tersedia di `app/build/outputs/distribution/1.0.1/`; kunci produksi sama dengan 1.0.0 yang sudah terpasang di HP. Release 1.0.1 sudah terpasang dan hash terverifikasi di Realme Android 10: ukuran 246,4 MB sesuai 50 file/258.380.600 byte, tetap benar setelah kembali ke default dan restart. Default tetap `Download/TubeFlow`. Lihat [rincian perbaikan](docs/STORAGE_SIZE_2026-10-10.md) dan [signing produksi](docs/PRODUCTION_SIGNING.md). Rincian toolchain dan sisa deprecation internal AGP ada di [catatan lint](docs/LINT_CLEANUP_2026-10-09.md). Kesiapan distribusi tetap mengikuti [persiapan rilis](docs/RELEASE.md). Payload FFmpeg upstream telah diperbaiki dengan membangun ulang sepuluh library WebP 1.6.0 pada ARM64/x86_64 dengan alignment 16 KB. Audit 448 ELF payload menemukan nol kegagalan alignment 64-bit, dengan simbol ekspor dan dependensi yang sama. Pengujian runtime pada perangkat 16 KB tetap wajib dilakukan.

Mesin yt-dlp disertakan dalam aplikasi. Pembaruan dilakukan lewat rilis aplikasi; tidak ada pembaruan executable jaringan tanpa verifikasi.

## Privasi dan dependensi

[Privasi](docs/PRIVACY.md) · [Dependensi](docs/DEPENDENCIES.md). Gunakan hanya konten yang dimiliki atau diizinkan untuk diunduh.

## Skill pengembangan

Skill Codex [tubeflow-dev](.agents/skills/tubeflow-dev/SKILL.md) menyimpan alur kerja, aturan perilaku download/SAF/lirik, serta validasi dan signing. Contoh pemakaian: `$tubeflow-dev perbaiki BUG-013 dan catat hasil pengujiannya`. Skill mengikuti kode dan laporan terkini; status bug tetap pada `docs/BUG_FINDINGS.md`.

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
