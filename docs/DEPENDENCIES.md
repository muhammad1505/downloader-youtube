# Dependensi dan distribusi

Runtime: [youtubedl-android](https://github.com/yausername/youtubedl-android), [yt-dlp](https://github.com/yt-dlp/yt-dlp), Python, QuickJS, dan FFmpeg beserta library codec yang dikemas oleh wrapper. UI: AndroidX Compose/Material3, Coil. Penyimpanan/pekerjaan: Room dan WorkManager.

Sebelum mendistribusikan APK, inventarisasi lisensi runtime native dan library transitif dari artefak versi yang benar-benar dipakai. FFmpeg dapat memiliki konfigurasi lisensi berbeda menurut fitur build; jangan menganggap semua payload hanya Apache/MIT. Sertakan notice, teks lisensi, penawaran/source sesuai kewajiban distribusi, dan sumber build yang cocok dengan binary. Dokumen ini belum menggantikan inventarisasi lengkap payload.

Versi engine yang tertanam perlu diperbarui secara berkala dan diuji terhadap YouTube. Sejak 1.1.28 pembaruan dapat dipasang dari Pengaturan dengan allowlist host, checksum ganda, batas ukuran, timeout, penggantian atomic, dan rollback; engine bawaan tetap menjadi fallback. Jangan menambah jalur download executable lain tanpa kontrol tersebut.

Audit binary FFmpeg 0.18.1 ARM64 menemukan konfigurasi `--enable-gpl --enable-version3` serta libx264/libx265. Payload ini tidak boleh diperlakukan sebagai FFmpeg LGPL-only. Bukti konfigurasi tertanam dalam libavcodec.so.61.19.101; sumber build menunjukkan paket Termux. Distribusi wajib menangani kewajiban GPLv3 runtime dan source yang cocok serta lisensi library lain.
