# Persiapan rilis

## Tahapan

1. Implementasi UI dan mesin: audio/video, format/bitrate/resolusi, antrean, Room, WorkManager, MediaStore.
2. Verifikasi komputer: unit tests, build debug/release, lint, audit ELF seluruh payload termasuk ZIP runtime native.
3. Pengujian perangkat fisik: dilakukan setelah konfirmasi pemilik perangkat; tidak menggunakan emulator.
4. Rilis: keystore produksi, inventarisasi lisensi, bukti kompatibilitas perangkat, dan hasil pengujian tersimpan.

## Pengujian perangkat

Gunakan konten pengujian milik sendiri/diizinkan. Catat model HP, versi Android, ABI, ukuran halaman memori, versi APK, dan hasil tiap kasus. Checklist bukti terbaru ada di [DEVICE_TEST_MATRIX.md](DEVICE_TEST_MATRIX.md).

- Link biasa, Shorts, Music, share dari YouTube, link tidak valid, konten tidak tersedia.
- Video Auto, MP4 720p/1080p, MKV, WebM; verifikasi hasil benar-benar dapat diputar.
- Audio Auto, MP3 bitrate manual, M4A, Opus, FLAC, WAV; periksa codec/bitrate dengan ffprobe.
- Lebih dari satu pekerjaan; kegagalan satu pekerjaan tidak menghentikan pekerjaan berikutnya.
- Batalkan pekerjaan yang menunggu, sedang mengambil media, sedang konversi, dan sedang menyalin file.
- Setelah pembatalan, tidak ada proses Python/FFmpeg yang tertinggal dan tidak ada hasil setengah jadi terlihat.
- Aplikasi di background, layar terkunci, jaringan terputus, notifikasi ditolak, storage hampir penuh.
- Matikan proses aplikasi saat download/publish, buka kembali, pastikan status dan file pending konsisten.
- Buka/bagikan hasil dan cek perilaku ketika file sudah dihapus lewat file manager.
- Ukuran font besar, TalkBack, layar sempit, landscape; semua kontrol dapat dijangkau.
- Android API 29, 33, 35+; perangkat ARM64 4 KB dan 16 KB apabila cakupan rilis mencakupnya.

## Persyaratan rilis

- Seluruh pemeriksaan build/tes/lint wajib berhasil. CI GitHub disiapkan tetapi belum dianggap berjalan sebelum repository dipush.
- Audit `python3 scripts/audit_native.py APK` wajib lolos untuk ELF 64-bit. Ini belum menggantikan tes linker native nyata atau audit zipalign APK final.
- Pastikan `apksigner verify --verbose` lolos untuk APK produksi yang ditandatangani.
- Keystore produksi lokal telah dibuat atas permintaan pengelola; lokasi privat, build ulang, dan aturan backup ada di [PRODUCTION_SIGNING.md](PRODUCTION_SIGNING.md). Backup eksternal masih harus disimpan pengelola.
- Pembatalan child FFmpeg menggunakan bootstrap session/grup proses milik engine dengan token, UID, PID, session, dan start-time yang diverifikasi. Host tests membuktikan penghentian child/grandchild yang mengabaikan SIGTERM. Validasi Android SELinux serta pembatalan saat konversi di perangkat tetap wajib sebelum menyatakan terjamin.
- Tinjau kebijakan target API dan distribusi toko yang dipilih pada tanggal rilis.
- Sertakan inventaris lisensi dan source/notice runtime sesuai konfigurasi binary sebenarnya.

## CI dan retensi artefak

CI dipisah menjadi dua workflow agar pekerjaan tidak berulang:

- **Android tests** berjalan pada push branch dan pull request. Isinya unit test Kotlin, `lintRelease`, verifikasi aset engine, tes host Python, dan ringkasan tes. Workflow ini tidak membangun APK dan tidak menyimpan artifact; ringkasan tes ditulis ke halaman run.
- **Release APK arm64** hanya berjalan pada tag `v*` atau dispatch manual. Isinya build APK rilis bertanda tangan, audit native/ZIP, zipalign, verifikasi signature, lalu publikasi. Unit test dan lint tidak diulang karena sudah menjadi gate pada commit yang sama.

Tag rilis harus dibuat dari commit `main` yang sudah lolos **Android tests**. Pada rilis tag, APK dilampirkan langsung ke GitHub Release tanpa membuat artifact Actions. Workflow juga menghapus aset dari rilis yang lebih lama dari tiga rilis terbaru dan menghapus seluruh artifact Actions lama; halaman rilis tetap disimpan agar tautan dan riwayatnya tidak rusak. Dispatch manual tetap menyimpan artifact Actions selama satu hari untuk pengujian.

Status 25 November 2026: versi dan changelog dinaikkan ke **1.1.83/code98** (Jangan Ganggu khusus
Pemutar video, panduan baterai/autostart per merek, dan panel DNS). DND audio pemutaran lagu yang
sempat ditambahkan dicabut agar notifikasi tetap masuk saat musik diputar. Build rilis lokal dan uji
perangkat **belum dijalankan**; gate rilis diserahkan ke workflow **Android tests** pada commit ini.

Status 18 November 2026: versi dan changelog dinaikkan ke **1.1.82/code97** (hapus folder Koleksi
ikut menghapus direktori, akses Jangan Ganggu, auto-play headset kabel). Build rilis lokal **tidak
dijalankan** atas permintaan pengguna; gate rilis diserahkan ke workflow tag `v1.1.82`. Bukti host
705 tes Kotlin, regresi `FolderDeleteTest`, dan uji emulator Pixel 4/Android 10 ada di [laporan
1.1.82](RELEASE_1_1_82.md) serta [DEVICE_TEST](DEVICE_TEST.md). APK/AAB bertanda tangan, audit
native/ZIP, zipalign, dan apksigner untuk versi ini belum diperiksa di host; uji HP fisik juga
belum dijalankan.

Status 16 November 2026: versi dan changelog dinaikkan ke **1.1.81/code96** (auto-play headset, opsi menahan notifikasi saat menonton, status pengecualian baterai, pesan berkas video hilang). CI **Android tests** lulus pada commit kode `7f793c5`; commit dokumentasi ini harus lolos **Android tests** yang sama sebelum tag `v1.1.81` dibuat. Build rilis lokal, APK/AAB 1.1.81, audit native/ZIP/zipalign/apksigner, dan uji perangkat untuk Jangan Ganggu serta headset kabel/TWS **belum dijalankan** karena AAR FFmpeg 16 KB belum dibangun dan NDK/CMake tidak tersedia di host ini. Rincian dan batas cakupan ada di [laporan 1.1.81](AUTOPLAY_ALERTS_1_1_81.md).

Status 3 November 2026: produksi signed **1.1.71/code86** dibangun lokal (`build_release.sh`), 654 Kotlin + 101 Python tes lulus, lint debug/release 0 error, audit native/ZIP/zipalign/apksigner lulus, dan terpasang di Realme RMX1821 Android 10 lewat `adb install -r` dengan `base.apk` SHA-256 `3837dc902d12245537ecacfb95cdf8d71eed088d34720f6f612b1c5f05b1c2fa` (cocok build lokal). Skenario [matriks perangkat](DEVICE_TEST_MATRIX.md) belum dijalankan pada build ini.

Status terbaru 13 Oktober 2026: produksi signed **1.1.1/code15** sudah terpasang di Realme Android 10, hash base.apk cocok ARM64 final `437d42759c2b953587f885d70b0a5ffd44fee21dc47dd0774fa088ee847405c0`. 145 Kotlin/76 Python (Python dijalankan ulang sesi dokumentasi), lint debug/release, audit APK×3/AAB lulus pada batch ini; [laporan dashboard 1.1.1](DASHBOARD_LYRICS_2026-10-13.md), [laporan Smart Media 1.1.0](SMART_MEDIA_UPGRADE_2026-10-13.md) dan [hasil perangkat](DEVICE_TEST.md) memisahkan bukti host/perangkat. Catatan lama soal kunci testing berbeda adalah sejarah, bukan blocker instalasi saat ini.

**Belum merupakan penutupan checklist rilis:** fault injection refetch pada provider Android/proses mati, risiko jurnal recovery BUG-008/003 dan ownership BUG-002/004 masih tercatat; matriks Android/ABI/16KB/codec, aksesibilitas/background/notifikasi/ruang penuh, benchmark, inventaris lisensi/source GPLv3 dan backup signing eksternal belum lengkap. CI remote/publikasi toko belum dibuktikan oleh build lokal. [Backlog dan urutan lanjut](PROGRESS.md#rekonsiliasi-backlog-bug-roadmap-dan-release--13-oktober-2026). Tidak ada upload/push atau tes destruktif tambahan dalam sesi dokumentasi ini.
