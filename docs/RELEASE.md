# Persiapan rilis

## Tahapan

1. Implementasi UI dan mesin: audio/video, format/bitrate/resolusi, antrean, Room, WorkManager, MediaStore.
2. Verifikasi komputer: unit tests, build debug/release, lint, audit ELF seluruh payload termasuk ZIP runtime native.
3. Pengujian perangkat fisik: dilakukan setelah konfirmasi pemilik perangkat; tidak menggunakan emulator.
4. Rilis: keystore produksi, inventarisasi lisensi, bukti kompatibilitas perangkat, dan hasil pengujian tersimpan.

## Pengujian perangkat

Gunakan konten pengujian milik sendiri/diizinkan. Catat model HP, versi Android, ABI, ukuran halaman memori, versi APK, dan hasil tiap kasus.

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

Status terbaru 13 Oktober 2026: produksi signed **1.1.1/code15** sudah terpasang di Realme Android 10, hash base.apk cocok ARM64 final `437d42759c2b953587f885d70b0a5ffd44fee21dc47dd0774fa088ee847405c0`. 145 Kotlin/76 Python (Python dijalankan ulang sesi dokumentasi), lint debug/release, audit APK×3/AAB lulus pada batch ini; [laporan dashboard 1.1.1](DASHBOARD_LYRICS_2026-10-13.md), [laporan Smart Media 1.1.0](SMART_MEDIA_UPGRADE_2026-10-13.md) dan [hasil perangkat](DEVICE_TEST.md) memisahkan bukti host/perangkat. Catatan lama soal kunci testing berbeda adalah sejarah, bukan blocker instalasi saat ini.

**Belum merupakan penutupan checklist rilis:** fault injection refetch pada provider Android/proses mati, risiko jurnal recovery BUG-008/003 dan ownership BUG-002/004 masih tercatat; matriks Android/ABI/16KB/codec, aksesibilitas/background/notifikasi/ruang penuh, benchmark, inventaris lisensi/source GPLv3 dan backup signing eksternal belum lengkap. CI remote/publikasi toko belum dibuktikan oleh build lokal. [Backlog dan urutan lanjut](PROGRESS.md#rekonsiliasi-backlog-bug-roadmap-dan-release--13-oktober-2026). Tidak ada upload/push atau tes destruktif tambahan dalam sesi dokumentasi ini.
