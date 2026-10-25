# Bukti verifikasi komputer

## Status terbaru — 1.1.40/code54 — 25 Oktober 2026

Rilis **1.1.40/code54** memperbaiki fallback dekoder setelah `ERROR_CODE_TIMEOUT`.
Timeout renderer MediaCodec (gejala yang muncul pada AV1 10-bit/AC3 di perangkat
uji setelah rotasi atau resume) kini dianggap kegagalan codec, sama seperti
query decoder gagal dan format melebihi kapasitas, sehingga pemutaran otomatis
dibuka ulang dengan dekoder software FFmpeg/dav1d. Fallback tetap hanya sekali
dan **tidak menimpa pilihan manual mode Perangkat**; aturannya dipindah ke
`VideoPlayback.shouldUseSoftwareFallback` agar bisa diuji tanpa Activity. Layar
error mendapat pesan khusus timeout yang menyarankan **Coba lagi**, dan tombol itu
tetap membuat ulang pemutar bila player sudah dilepas. **470 tes Kotlin**
(0 gagal/error/skip), **98 tes Python** `OK` (27,233 detik), lint debug/release
**0 error/3 warning** lama; `scripts/build_release.sh` lulus (8m59s). Signed
ARM64 **76.264.926 byte**, SHA256
`61c0c2d17a50edaa500f47c70a23c1f0b793a1cc2a99c34b95d769a629a6f844`; AAB
**273.188.764 byte**, SHA256
`5491bc474f9a52e3edfd7e4edd031403b2ba587ec92eda371dedfa4a3dda9d0f`. Audit APK
262 ELF/13 kelas ZIP, zipalign 16 KB, signature v2, dan AAB `jar verified`.
Release terpasang update-r di Realme RMX1821 Android 10 (arm64, halaman 4 KB)
dari 1.1.39/code53; `base.apk` identik (hash di atas). Di HP, klip
**player-av1-10bit-ac3** (20 detik) diputar lalu diputar-ulang lanskap→portret→
lanskap lewat kontrol orientasi aplikasi, diteruskan Home lalu dibuka kembali:
pemutaran berlanjut dengan frame bergerak dan logcat bersih dari
`PlaybackException`/`ERROR_CODE_TIMEOUT`. Batas pengujian ada di
[uji perangkat](DEVICE_TEST.md#fallback-timeout-1140code54--25-oktober-2026).

## Status sebelumnya — 1.1.39/code53 — 25 Oktober 2026

Rilis **1.1.39/code53** merapikan kontrol pemutar video mengikuti pola MX Player.
Pengaturan cepat (kecepatan, subtitle, EQ, efek audio, Smart Enhance, dekoder,
screenshot, dan rasio) menjadi deretan **ikon bulat tanpa caption** di bawah top
bar. Tombol putar dipisah ke bar bawah: kunci di kiri, previous/play/next di
tengah, PiP dan orientasi di kanan. Bar progres memakai kolom waktu kiri/kanan
sama lebar dengan thumb titik 9dp sehingga durasi selalu terlihat dan track
seimbang. Antrean pindah ke ikon top bar; Ulangi tetap di lembar **Opsi pemutar**.
Tombol ±10 detik dilepas dari bar transport agar tidak padat; seek tetap tersedia
lewat slider, double-tap, dan gestur. **469 tes Kotlin** (0 gagal/error/skip),
**98 tes Python** `OK` (38,079 detik), lint debug/release **0 error/3 warning**
lama; `scripts/build_release.sh` lulus (7m33s). Signed ARM64 **76.264.790 byte**,
SHA256 `9465fc12994dbc612f4a1573b7c7c2f0eb03e0af1fb9221610265989d11cef41`; AAB
**273.188.639 byte**, SHA256
`402bc2521a66a77f7d7cde87916497647d6843320450b0ea4fb9a88020a0c3a4`.
Audit APK 262 ELF/13 kelas ZIP, zipalign 16 KB, signature v2, dan AAB
`jarsigner` lulus. Release terpasang update-r di Realme RMX1821 Android 10
(arm64, halaman 4 KB) dari 1.1.38/code52; `base.apk` identik. Di HP: 8 ikon
pengaturan cepat muat satu baris tanpa teks; progres menampilkan **0:13 / 0:20**
setelah seek dan titik berada tepat di track; kunci, buka kunci, antrean, dan
rotasi lanskap/portret bekerja; tombol putar tidak lagi bercampur dengan chip
pengaturan. Satu percobaan resume AV1 10-bit sempat menampilkan
`ERROR_CODE_TIMEOUT`, lalu **Coba lagi** memulihkan pemutaran. Batas pengujian ada
di [uji perangkat](DEVICE_TEST.md#kontrol-pemutar-ikon-1139code53--25-oktober-2026).

## Status sebelumnya — 1.1.38/code52 — 25 Oktober 2026

Rilis **1.1.38/code52** menata ulang Pengaturan dan Koleksi agar tidak menjadi
satu daftar panjang. Pengaturan utama kini berisi **tujuh kategori** (Unduhan,
Penyimpanan, Tampilan, Pemutaran, Riwayat, Media & alat, Tentang) dengan ringkasan
nilai aktif; setiap kategori membuka subhalaman sendiri dan Tentang terpisah dari
pengaturan lain. Tombol kembali top bar maupun tombol sistem kembali dari
subhalaman ke daftar kategori, lalu ke tab sebelumnya. Koleksi dipadatkan menjadi
**dua baris chip**: jenis + Putar/Acak, lalu Favorit/Filter/Cari/Kelola playlist.
Urutan, folder, playlist, artis, dan album pindah ke dialog **Filter**; chip
menampilkan jumlah filter aktif dan dialog menyediakan **Reset filter**.
**469 tes Kotlin** (0 gagal/error/skip), **98 tes Python** `OK` (28,797 detik),
lint debug/release **0 error/3 warning** lama; `scripts/build_release.sh` lulus
(9m35s). Signed ARM64 **76.266.998 byte**, SHA256
`919fdc5742c0733ecbab0d65703711722a385c633f968dc432e069137ccb9c0c`; AAB
**273.184.427 byte**, SHA256
`ad0a997836ec270b8e3434f6fd6405cfdef8dd40a11f9e26d01d123266c46369`.
Audit APK 262 ELF/13 kelas ZIP, zipalign 16 KB, signature v2, dan AAB
`jarsigner` lulus. Release terpasang update-r di Realme RMX1821 Android 10
(arm64, halaman 4 KB) dari 1.1.37/code51; `base.apk` identik. Di HP: tujuh
kategori muat satu layar, subhalaman hanya menampilkan satu judul, kembali
top-bar/sistem kembali ke daftar lalu Koleksi, dua baris chip Koleksi muat tanpa
terpotong, dan dialog filter mengubah urutan menjadi **Nama** (`Filter · 1`) serta
mengembalikan **Terbaru** lewat **Reset filter**. Batas pengujian ada di
[uji perangkat](DEVICE_TEST.md#pengaturan-berkategori-dan-koleksi-ringkas-1138code52--25-oktober-2026).

## Status sebelumnya — 1.1.37/code51 — 25 Oktober 2026

Rilis **1.1.37/code51** merapikan pemutar video dan detail tautan. Baris
pengaturan cepat (kecepatan, subtitle, EQ, efek audio, Smart Enhance, rasio,
ulangi, dekoder, screenshot, antrean, kunci) dipindah **di atas tombol navigasi
putar**, bukan menumpuk di bawahnya. Pratinjau tautan kini menyajikan data nyata
dari `formats[]`: daftar resolusi yang benar-benar tersedia dan status subtitle,
dan pilihan resolusi hanya memuat tinggi tersebut. Pemilih format memakai
filter `height<=?` plus fallback `/best`, jadi sumber seperti Instagram yang
tidak melaporkan tinggi format tidak lagi gagal karena resolusi pilihan tidak
ada. **469 tes Kotlin** (0 gagal/error/skip), **98 tes Python** `OK` (28,980
detik), lint debug/release **0 error/3 warning** lama. Signed ARM64
**76.257.558 byte**, SHA256
`fe826a744552ed7b3b8543634c0cf9beda700e837817339659c9ed57edc816b8`; AAB
**273.169.852 byte**, SHA256
`88bbec8a49f58fa2e9a3fac632bc3154de71e1864e05b4bb6899b876bc989fc0`.
Audit APK 262 ELF/13 kelas ZIP, zipalign 16 KB, signature v2, dan AAB `jarsigner`
lulus. Release terpasang update-r di Realme RMX1821 Android 10 (arm64, halaman
4 KB) dari 1.1.36/code50; `base.apk` identik. Di HP: pratinjau **Big Buck Bunny**
menampilkan **2160p–144p** tanpa subtitle, **Me at the zoo** menampilkan
**240p/144p** dan **Subtitle tersedia: de, en**; dropdown resolusi hanya memuat
tinggi tersebut, memilih **1080p** memperbarui ringkasan, dan baris pengaturan
cepat tampil di atas tombol putar. Batas pengujian ada di
[uji perangkat](DEVICE_TEST.md#pemutar-video-dan-detail-tautan-1137code51--25-oktober-2026).

## Status sebelumnya — 1.1.36/code50 — 25 Oktober 2026

Rilis **1.1.36/code50** memperbaiki fallback dekoder video: kegagalan
`DECODER_QUERY_FAILED` dan `DECODING_FORMAT_EXCEEDS_CAPABILITIES` kini ikut
memicu dekoder software, dan **Coba lagi** membuat ulang pemutar bila
pembukaan gagal sampai `player == null`. Bar bawah pemutar dirapikan: progress
bar ringkas dan semua tombol cepat (kecepatan, subtitle, EQ, efek audio, Smart
Enhance, rasio, ulangi, dekoder, screenshot, antrean, kunci) menjadi siklus satu
ketuk seperti tombol Ulangi; pilihan detail tetap ada di lembar **Opsi pemutar**.
Posisi subtitle kini diterapkan ke `SubtitleView` sehingga cue tertanam ikut
naik, dan subtitle sidecar disimpan per video. **464 tes Kotlin** (0
gagal/error/skip), **98 tes Python** `OK`, lint debug/release **0 error/3 warning**
lama; `scripts/build_release.sh` lulus (17m45s). Signed ARM64 **76.254.890 byte**,
SHA256 `e41cf773c6e8ee9a94cfbebf2cf2e675ed57cdedc9d63a4e6e2e45aa89ad81f3`; AAB
**273.162.811 byte**, SHA256
`787dde4718a335c223c903f43083164e80faf1a4812628f617784bde4fbe1f64`.
Audit APK 262 ELF/13 kelas ZIP, zipalign 16 KB, signature v2, dan AAB `jarsigner`
lulus. Release terpasang update-r di Realme RMX1821 Android 10 (arm64, halaman
4 KB) dari 1.1.35/code49; `base.apk` identik. Di HP: AV1 10-bit + AC3 diputar di
mode Otomatis dan **Perangkat saja** dengan posisi tetap, chip EQ/FX/subtitle
berganti satu ketuk, subtitle sidecar tampil berlabel **Indonesia**, screenshot
tersimpan di `Pictures/TubeFlow/`, dan lanskap 1520x720 muat tanpa terpotong.
Batas pengujian ada di [uji perangkat](DEVICE_TEST.md#pemutar-video-1136code50--25-oktober-2026).

## Status sebelumnya — 1.1.35/code49 — 25 Oktober 2026

Rilis **1.1.35/code49** menggabungkan pemutar video dengan orientasi manual,
pengaturan subtitle, dan dekoder software NextLib/FFmpeg/dav1d, serta integrasi
“Buka dengan”. **459 tes Kotlin** (0 gagal/error/skip), **98 tes Python** `OK`,
lint debug/release **0 error/3 warning** lama; `scripts/build_release.sh` lulus
(14m41s). Signed ARM64 **76.135.624 byte**, SHA256
`556985635e79abeea82dd281498c8d1f59efed4794c2730d667cb6b9b1a5b2cc`; AAB
**273.003.343 byte**, SHA256
`cb69a55acf02ae2c3d81b18ada335f6044c755ba250f93fd436cf4ed728a1085`.
Audit APK 262 ELF/13 kelas ZIP, zipalign 16 KB, signature v2, dan AAB `jarsigner`
lulus. Release terpasang update-r di Realme RMX1821 Android 10 (arm64, halaman
4 KB); `base.apk` identik. Di HP: pemutaran H.264 dengan 2 audio/2 subtitle,
pemilihan track audio dan gaya subtitle, AV1 10-bit + AC3 dengan renderer FFmpeg
aktif,
orientasi lanskap, PiP, ACTION_VIEW video/audio, dan auto-lanjut antrean lulus.
Temuan baru (posisi subtitle tertanam, label track pertama, cakupan
octet-stream/SEND, metadata audio masuk) serta batas pengujian ada di
[uji perangkat](DEVICE_TEST.md#pemutar-video-1135code49--25-oktober-2026).

## Status sebelumnya — 1.1.33/code47 — 24 Oktober 2026

Rilis **1.1.33/code47** menambahkan unduhan **album/playlist Spotify** (metadata
dari halaman embed, audio dari video YouTube hasil pencocokan, antrean per lagu).
**427 tes Kotlin** (0 gagal/error/skip), **98 tes Python** `OK` (28,693 detik),
lint debug 0 error/3 warning lama. `scripts/build_release.sh` lulus (18m49s).
Signed ARM64 **71.042.372 byte**, SHA256
`2f7e2fc7f321b65d66761db9b30de195fa2ec7f80e7247b76ed09d1684cbb317`; AAB
**256.170.772 byte**, SHA256
`c86edea9fca18997edcacf5fc335b524effde9d7eb53624dad5f64cd46761522`.
Audit APK 257 ELF/13 kelas ZIP, `zipalign -c -P 16 4`, dan signature v2 sertifikat
`ebe3b40e…` lulus. Release terpasang sebagai update di Realme RMX1821 Android 10
(arm64, halaman 4 KB); `base.apk` identik dengan artefak.

Di HP: track `Red` menghasilkan MP3 128 bertag Spotify; album `Library Songs 5`
dengan 2 lagu terpilih masuk `scott-buckley/`; playlist 20 lagu mendukung
pencarian tanpa mengubah pilihan, pembatalan pencocokan 2/20, dan antrean ke
folder slug playlist; playlist 150 lagu dibatasi 100 dengan pesan yang sesuai;
riwayat Spotify tampil dan dapat diputar. File diperiksa dengan ffprobe (tag,
bitrate, tanpa gambar tertanam). Tidak ada `FATAL EXCEPTION`.

**Belum diuji:** batalkan pembacaan metadata, mode pesawat penuh (VPN aktif tidak
bisa diputus lewat ADB), kecocokan lemah, force-stop saat mencocokkan/antrean,
pergantian folder, TalkBack/font/landscape, filter platform, dan **Unduh lagi**.
Detail ada di [validasi perangkat Spotify](SPOTIFY_DEVICE_VALIDATION.md#9-hasil-validasi-perangkat--24-oktober-2026).

## Status sebelumnya — 1.1.31/code45

Release **1.1.31/code45**: daftar **Antrean + Riwayat** terpadu tanpa tab
segmented, composer URL selalu terlihat, pemisah hanya saat keduanya ada, dan
empty state bersih. Aksi massal Antrean hanya muncul untuk lebih dari satu job;
pencarian/filter Riwayat muncul setelah lebih dari tiga entri selesai atau saat
query/filter aktif. **405 tes Kotlin** (0 gagal/error/skip, dibaca dari
`test-results/testDebugUnitTest`), **98 tes Python** `OK` (25,178 detik), lint
debug **0 error/3 warning** lama (SelectedPhotoAccess, Gradle 9.8.2 tersedia,
pin onnxruntime 1.23.2 yang disengaja).

`scripts/build_release.sh` lulus (16m01s). Signed ARM64 **70.946.520 byte**,
SHA256 `cd265cee31516c9a5f4a0e3a70388fbc704617ebde190ad50cb29bef9978c18f`;
AAB **256.041.323 byte**, SHA256
`7341a6adf0fc3ffc4bb62df941828682cc9f05390ba5ba4a18b528028a8c3e80`.
Audit APK 257/257/259 ELF, 13 kelas ZIP, `zipalign -c -P 16 4`, dan signature v2
sertifikat `ebe3b40e…` lulus; AAB 1.038 ELF dan `jarsigner` jar verified.
Release terpasang sebagai update di Realme RMX1821 Android 10; `base.apk` identik
dengan artefak. Di HP: keadaan kosong, job aktif + riwayat (debug bertanda tangan
kunci yang sama), jeda/lanjut, batal semua, hapus entri batal tanpa menghapus
riwayat, riwayat satu entri setelah force-stop, dialog **Bersihkan riwayat?**
(dibatalkan), dan deep link `OPEN_HISTORY` membuka tab Download. Riwayat uji
`"Me at the zoo"` dibuat ulang; tidak ada uninstall/clear data/reset database.

**Belum diuji:** state Antrean aktif pada build release (debug sudah), pencarian/
filter Riwayat dengan >3 entri, TWS/Android 12+, runtime 16 KB, ABI/Android lain,
fault injection provider SAF/process death, dan matriks font/landscape. Audit
host tidak membuktikan jalur gagal Android aman.

## Status sebelumnya — 1.1.30/code44

Baseline **1.1.30/code44**: yt-dlp bawaan 2026.10.21
(`b7af77f686e95f7845ce8d09d21957718febb8b48b30555adb9dd2292ad7bf14`),
inspeksi bertahap + cache metadata 30 menit/200 entri, LRC manual, recovery
exception-safe, dan guard checksum engine di CI. **402 tes Kotlin** (0
gagal/error/skip, dibaca dari `test-results/testDebugUnitTest`), **98 tes Python**
`OK` (24,075 detik), lint debug/release **0 error/3 warning** lama.
`scripts/build_release.sh` lulus (16m03s). Signed APK ARM64 70.948.952 byte,
SHA256 `6bf4957d6d16d4bfe22589fa7818b1d531211204ea4c43e223fa14918cefe7c5`;
AAB 256.040.881 byte, SHA256
`b686b07ceb7cd5596ff40320f61ffd4e9b3bdb1a9d5f5f91348f363aafd53eee`.
Audit APK 257/257/259 ELF/13 kelas ZIP, zipalign 16 KB, signature, dan AAB lulus.
Perangkat: fase inspeksi/cancel/cache, LRC manual, dan BUG-054 MP3 128 lulus;
riwayat 0 di awal sesi berasal dari setelan **Hapus otomatis 7 hari**, bukan
reset database. [Ringkasan](PROGRESS.md).

## Status sebelumnya — 13 Oktober 2026

Baseline produksi aktual **1.1.1/code15/Room9**: 145 tes Kotlin (dibaca ulang dari `test-results/testDebugUnitTest`), **76 tes Python dijalankan ulang → `OK` (23,173 detik)**, lint debug/release “No issues found”, signed APK×3/AAB dengan `sha256sum -c SHA256SUMS` semua OK. Audit dijalankan ulang sesi dokumentasi pada artefak distribusi (source tidak berubah sesudah build): native 255/255/257 ELF + AAB 1.024 ELF 0 gagal 16 KB, ZIP reflektif 13 kelas 0 gagal, `zipalign -c -P 16 4` OK, `apksigner` v2 verified sertifikat `ebe3b40e…`, AAB `jarsigner` jar verified. ARM64 SHA256 `437d42759c2b953587f885d70b0a5ffd44fee21dc47dd0774fa088ee847405c0` cocok APK terpasang di Realme Android 10. [Laporan 1.1.1 dan cakupan](DASHBOARD_LYRICS_2026-10-13.md), [signing](PRODUCTION_SIGNING.md), [uji perangkat](DEVICE_TEST.md).

Angka bundletool pada rilis 1.1.0 adalah bukti batch itu; sesi 1.1.1 tidak mengulang bundletool (tidak ada log sesi ini). Pemeriksaan 13 Okt sesi ini hanya dokumentasi + audit ulang artefak — tidak menjalankan ulang build; audit host tidak membuktikan seluruh jalur gagal Android aman. Risiko fault injection provider/proses mati, journal cleanup dan concurrency serta matriks perangkat/lisensi yang belum lengkap ada pada [rekonsiliasi progres](PROGRESS.md#rekonsiliasi-backlog-bug-roadmap-dan-release--13-oktober-2026).

## Bukti historis

Angka tes/lint, kandidat testing dan catatan “signing produksi belum dikonfigurasi” di bawah berlaku pada checkpoint awal masing-masing; status tersebut sudah dilanjutkan oleh release produksi di atas.

- yt-dlp stabil 2026.08.19: SHA256 `1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6`, cocok dengan SHA2-256SUMS resmi yang disimpan di docs.
- Patched FFmpeg AAR: SHA256 `b30168221f89d88dc6ec242e3438dbaab6b7d89dec0647f548259a22d0230d5e`.
- WebP source 1.6.0 dipin, sumber/notice disimpan di third_party/libwebp. Recipe dapat menghasilkan SHA AAR yang sama pada lingkungan build ini.
- Audit patched AAR mencakup 448 ELF termasuk payload ZIP native dan nama library berversi: nol kegagalan PT_LOAD alignment/congruence pada 64-bit. File 32-bit tetap upstream.
- Sepuluh library pengganti memiliki simbol ekspor dan dependensi NEEDED yang sama persis dengan upstream.
- Dua unit tests auditor memverifikasi penolakan alignment 4 KB/congruence keliru dan penemuan ELF di ZIP runtime yang dinamai .so.

Build debug/release per ABI berhasil (`testDebugUnitTest lintDebug assembleDebug assembleRelease`, log lokal `/tmp/tubeflow-final-checks.log`). Sebelas unit tests Kotlin dan 16 host tests Python lulus. Lint debug: nol error, 13 warning (versi dependensi/target SDK/style). Audit APK ARM64 mencakup 255 ELF; x86_64 mencakup 257 ELF, nol kegagalan 64-bit. APK kandidat testing adalah release ARM64 yang ditandatangani dengan kunci Android testing, diverifikasi apksigner dan zipalign. Signing produksi belum dikonfigurasi. Pengujian perangkat fisik berjalan; lihat DEVICE_TEST.md. Hasil audit ELF bukan bukti native runtime berjalan di perangkat nyata.

Redesain UI dan penambahan fitur (tema terang/gelap + warna dinamis, preset download, pencarian/filter riwayat, statistik, ulangi semua gagal, info penyimpanan) diverifikasi ulang dengan `testDebugUnitTest lintDebug assembleDebug assembleRelease`: 15 unit tests Kotlin lulus (termasuk 4 test helper format durasi/ETA/kecepatan/byte), 16 host tests Python lulus (query DAO `remove`/`clearCompleted`/`clearUnsuccessful`/`revive` diuji terhadap Room schema asli), lint debug nol error dengan 13 warning yang sama seperti baseline.

Fitur playlist, subtitle/CC, thumbnail, dan format tambahan (MOV/AVI/AAC/Vorbis, 4320p) ditambahkan dengan skema DB naik ke versi 2 (kolom `subtitles`, `embedSubs`, `thumbnail`, `embedThumb`, `sidecarUris` + migrasi `Migration(1,2)`). Verifikasi: `testDebugUnitTest lintDebug assembleDebug` lulus — 23 unit tests Kotlin (termasuk test validasi URL playlist, whitelist subtitle, dan selector format lanjutan), 20 host tests Python (termasuk test yang membuktikan SQL migrasi 1→2 menghasilkan tabel identik dengan schema `2.json` yang diharapkan Room, plus default kolom baru dan query `revive`/`sidecars`), lint nol error. Instalasi `adb install -r` ke perangkat Realme RMX1821 (Android 10, arm64) sukses tanpa galat migrasi; aplikasi berjalan dan seluruh layar baru tampil. Perhatian: unduhan playlist memakai `--yes-playlist --flat-playlist` saat inspeksi, sedangkan unduhan per video tetap `--no-playlist`.

APK kandidat ARM64: TubeFlow-arm64-v8a-testing.apk; SHA256 `cbb558f2da7605f8d05d3fb84671895226f5f6243accff66e78fb54025a1c266`; 59.4 MiB.

Reorganisasi opsi lanjutan, cover-only, folder penyimpanan configurable, dan splash screen: subtitle dipindah ke kartu "Pengaturan lanjutan" dengan caption bergantung mode (video: "Download subtitle"; audio: "Download lirik"), thumbnail hanya berupa sampul yang disematkan ke audio (tanpa file gambar terpisah; disimpan di `Downloads/<folder>`, RELATIVE_PATH MediaStore dibaca dari `SettingsStore.storageFolder` yang divalidasi `Selection.safeFolder`), dan splash screen memakai `androidx.core:core-splashscreen:1.2.0` (`Theme.TubeFlow.Splash` → `postSplashScreenTheme`). Riset `yt_dlp/postprocessor/embedthumbnail.py` pada zipapp bundle menunjukkan tanpa paket `mutagen`, embed sampul hanya aman untuk mp3 (fallback ffmpeg), m4a (fallback ffmpeg), dan mkv — sehingga `Selection.coverFormats = {auto, mp3, m4a}`; format Auto dikonversi ke MP3 bila sampul aktif, dan file gambar sidecar dilewati saat mode cover-only. Verifikasi: `testDebugUnitTest lintDebug assembleDebug` lulus — 26 unit tests Kotlin (3 baru: whitelist cover-per-format, `safeFolder`, dan `listParam`), 20 host tests Python, lint nol error (14 warning, semua dependensi/target SDK).

Setelah crash pada perangkat, build ulang `testDebugUnitTest lintDebug assembleRelease` lulus (log `/tmp/tubeflow-device-fix-build.log`). Audit DEX kandidat perbaikan memastikan 13 kelas ZIP registry public/concrete dan memiliki konstruktor public tanpa argumen. Tujuh regression tests auditor mencakup kegagalan konstruktor/class flags dan pencocokan registry dependency aktual.

Iterasi UI lanjutan: jarak chip rangkuman → tombol CTA dinaikkan dari 0 menjadi 14dp, picker playlist mendapat pencarian judul + label "Menampilkan X dari Y", dan link mix/radio (`list=RD…`) diberi penjelasan otomatis. Terverifikasi di perangkat lewat `uiautomator dump` (bukan sekadar screenshot): bounds chip `[34,1004][686,1059]` vs tombol `[34,1083][686,1175]` = 24px/1.7density ≈ 14dp; pencarian "backstreet" pada mix 380 video menghasilkan "Menampilkan 3 dari 380 · 380 terpilih"; kartu playlist menampilkan hint mix untuk kasus jumlah beda dengan YouTube.
