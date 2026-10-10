# Pengujian perangkat fisik

Perangkat: Realme RMX1821, Android 10 (API 29), ARM64, halaman memori 4 KB. Storage /data tersedia sekitar 19 GB pada awal pengujian. Pengguna mengizinkan pengujian dan memberikan link uji. Tidak menggunakan emulator.

APK: release minified ARM64 dengan kunci testing lokal, `app/build/outputs/apk/rc/TubeFlow-arm64-v8a-testing.apk`. Hash artefak berada pada JSON di folder yang sama.

## Hasil sementara, 8 Oktober 2026

- ADB tersambung. Instalasi ADB ditolak dengan `INSTALL_FAILED_VERIFICATION_FAILURE`.
- APK berhasil dipasang melalui Downloads dan installer bawaan. Update perbaikan melewati pemindaian Play Protect dengan hasil “Aplikasi ini tampak aman”. Tidak menonaktifkan verifikasi perangkat.
- Launch dan penerimaan link lewat ACTION_SEND berhasil.
- Kandidat pertama crash saat ekstraksi runtime: R8 mengoptimalkan `AsiExtraField` menjadi abstract tanpa konstruktor yang diperlukan refleksi Commons Compress.
- Aturan keep untuk implementasi `ZipExtraField` dan konstruktor public tanpa argumen memperbaiki crash. APK hasil optimasi diaudit: 13 kelas registry, nol kegagalan.
- Kandidat perbaikan berhasil membaca judul, pembuat, durasi, dan thumbnail link uji. Proses aplikasi tetap berjalan.
- Pemilihan Audio → MP3 → 128 kbps dan enqueue berhasil. Pengambilan media gagal dengan `HTTP Error 403: Forbidden`; UI menampilkan gagal dan Coba lagi tanpa crash. Penyebab sedang ditelusuri.
- Download sukses, codec/bitrate hasil, playback, pembatalan saat konversi, background, dan matriks Android/16 KB belum terbukti.

Aplikasi belum dinyatakan siap produksi sampai penghalang download dan pemeriksaan rilis diselesaikan. URL uji dan identitas ADB perangkat tidak dicantumkan dalam laporan.

## Pembaruan toolchain, 9 Oktober 2026

Diminta menguji langsung pada perangkat fisik, tanpa emulator. APK baru: release minified ARM64, compile/target SDK 37, minimum SDK 29, SHA256 `10f230542a6eb6953c3f46c1437c56e1c4c88c5c6d3c8957552f34d484b4ece1`.

- Build, 43 tes Kotlin, 24 tes Python, lint debug/release (0 error/0 warning), serta audit native/ZIP ketiga ABI lulus.
- Sebelum pembaruan, antrean kosong; 4 entri riwayat dan 5 berkas di folder SAF dicatat sebagai pembanding. APK terpasang masih bertarget SDK 35.
- `adb install -r` kembali ditolak dengan `INSTALL_FAILED_VERIFICATION_FAILURE`. APK disalin ke `Download/TubeFlow-lint-cleanup-testing.apk`, lalu dibuka melalui Files → Unduh → Installer paket.
- Installer mengenali pembaruan, lalu Play Protect meminta APK dikirim ke Google untuk pemindaian. Pengguna menyetujui kelanjutan pada 10 Oktober; saat sesi dilanjutkan, APK sudah terpasang. Hash APK terpasang cocok dengan artefak di atas dan `dumpsys package` menunjukkan target SDK 37. Verifier dan Play Protect tidak dinonaktifkan.

## Hasil uji langsung, 10 Oktober 2026

Perangkat yang sama: Realme RMX1821, Android 10, ARM64, halaman memori 4 KB. Menggunakan APK release minified dengan hash di atas, tanpa emulator.

| Kasus | Hasil yang diamati |
| --- | --- |
| Launch setelah update | Aplikasi terbuka; folder SAF pilihan tetap tersimpan. |
| Inspeksi link melalui ACTION_SEND | Judul, pembuat Blender, thumbnail, dan durasi 10:35 terbaca. |
| Download audio | Preset MP3 128 kbps + sampul masuk antrean, berjalan, lalu menjadi **Selesai**. |
| Background | `SystemForegroundService` tetap aktif (`isForeground=true`) ketika aplikasi dibawa ke Home. |
| Penyimpanan SAF | MP3 tersimpan di folder pilihan, ukuran 11.752.094 byte. |
| Validasi berkas | `ffprobe`: codec MP3, bitrate audio 128.000 bit/detik, durasi 634,608 detik; PNG dengan `attached_pic=1`. |
| Buka hasil | Tombol Buka menjalankan Poweramp; format terbaca MP3 128 kbps/48 kHz, penghitung pemutaran bergerak 0:02 → 0:57. |
| Filter riwayat | Video: 0 entri; Audio dan Semua: 1 entri, sesuai hasil uji. |
| Pengaturan persisten | Batas paralel 3 diubah ke 2; setelah force-stop dan launch ulang tetap 2. Dikembalikan ke 3 setelah tes. |
| Restart dan log aplikasi | Riwayat hasil uji tetap terbaca setelah restart; tidak ditemukan fatal exception atau galat validasi/migrasi Room pada log pemeriksaan. |

Konten uji: **Big Buck Bunny 60fps 4K — Official Blender Foundation Short Film**. File hasil dipertahankan untuk pemeriksaan ulang di `Download/TubeFlow/Denny-Caknan/Big Buck Bunny 60fps 4K - Official Blender Foundation Short.mp3`; satu entri Selesai ada di Riwayat. Bukti lokal: `/tmp/tubeflow-device-output-probe.json` dan snapshot UI `/tmp/tubeflow-device-*.xml`.

Pengguna mengonfirmasi bahwa riwayat dan file lama telah dibersihkan sendiri sebelum sesi dilanjutkan. Retensi entri lama saat update tidak dapat dinilai dari sesi ini. Tes ini tidak mencakup Android 17, halaman memori 16 KB, sinkronisasi lirik, race jeda/lanjut cepat, atau seluruh kasus kegagalan cleanup. Status bug pada audit sebelumnya tetap berlaku; lihat [catatan lint](LINT_CLEANUP_2026-10-09.md).

## Progres dan kompresi sampul, 10 Oktober 2026 — berhasil

APK final SHA256 `2dc813c9b3bad4611550e553372d3c5f031d4cd37af984ca631766f1c644a17d` terpasang melalui installer bawaan + Play Protect setelah sambungan USB dipulihkan. Hash APK terpasang cocok. Menggunakan Realme Android 10 yang sama, tanpa emulator; satu entri lama tetap terbaca setelah migrasi Room 3→4.

- **MP3 128 kbps:** JPEG 800×451, `attached_pic=1`, sukses disimpan melalui SAF dan diputar dengan sampul di Poweramp. Sampul 1.597.019→46.665 byte (**−97,08%**); total MP3 11.752.094→10.201.741 byte. 26.442 paket audio identik dengan file lama. Playback dijeda sesudah pemeriksaan.
- **Progres konversi:** “Memproses audio/video” dengan indikator tak tentu, tanpa persen/kecepatan/ETA.
- **Video MP4 360p:** H.264 640×360 + AAC, 28.865.093 byte. Terlihat progres video 43%→77%, audio 40%, lalu pemrosesan dan selesai. Persen adalah per track; tahap simpan yang singkat tidak tertangkap snapshot.
- **Keadaan akhir:** 3 entri selesai (1 lama + MP3 baru + MP4 baru), antrean kosong, file lama tetap ada. Tidak ada fatal exception pada logcat PID aplikasi.
- **Host:** 49 tes Kotlin, 32 tes Python, lint debug/release 0 masalah, build debug/release dan audit native/ZIP ketiga ABI lulus.

Bukti UI, metadata file, jalur artefak, dan batas cakupan tersedia di [laporan progres/sampul](PROGRESS_COVER_2026-10-10.md). Sampul lama tidak diubah otomatis. Uji ini belum mencakup M4A, Android 17/16 KB, lirik, kegagalan provider/ruang penuh, atau race pause/resume.

## Mix dan duplikat, 10 Oktober 2026 — berhasil

APK final SHA256 `75b62581d62da04588f16fb3ed15aa9b26ade278843f5f59e20bf1f0c0ff2b40` terpasang pada Realme Android 10 yang sama, melalui installer bawaan + pemindaian Play Protect; hash terpasang cocok. Riwayat 3 entri dipertahankan, tanpa uninstall/clear data atau emulator.

- Link Mix contoh `watch?v=3J1Opi7MVHw&list=RD3J1Opi7MVHw&start_radio=1` menampilkan **25 video dalam antrean awal** dan video seed yang benar. Jumlah mengikuti panel server, bukan batas tetap 25; cookies akun tidak diberikan pada engine.
- Dua alias baru untuk hasil MP3 128 kbps bersampul (`youtu.be` + `si`, YouTube Music + `t`) ditolak dengan **File ini sudah pernah diunduh.**
- Antrean kosong, riwayat tetap 3, daftar file folder uji identik sebelum/sesudah. Tidak ditemukan fatal exception atau galat Room pada log PID aplikasi.
- Host: **55 tes Kotlin, 37 tes Python**, lint debug/release 0 masalah, build debug/release dan audit native/ZIP ketiga ABI lulus.

Langkah reproduksi, hash, screenshot, dan cakupan yang belum diuji ada di [MIX_DUPLICATES_2026-10-10.md](MIX_DUPLICATES_2026-10-10.md). Batch ini menguji duplikat hasil selesai pada perangkat; skenario aktif/dijeda dan file hilang belum dijalankan di HP. Status temuan audit lain tidak berubah.

## Produksi 1.0.0 dan baseline ukuran folder, 10 Oktober 2026

APK produksi ARM64 1.0.0 terpasang melalui installer bawaan dan Play Protect. APK yang ditarik dari HP cocok SHA256 `8e9ae75a91c6323ab74fa385cd722096df41c2a46a6bfb8e5354ed749db3e1c0` dan fingerprint produksi. Agent tidak menjalankan uninstall/clear data; pengguna juga berinteraksi dengan installer, sehingga retensi data saat mengganti kunci tidak dapat dipastikan. Riwayat kemudian kosong, sementara file publik tetap ada.

Pada Realme Android 10: root `Download/TubeFlow` berisi **53 file, 309.199.528 byte** melalui ADB. Pengaturan mode default menunjukkan **0 B**; setelah root dipilih melalui SAF menunjukkan **294,9 MB**. Kembali ke mode default menghasilkan nol lagi. Subfolder Denny-Caknan berasal dari tes lama; default kode tetap TubeFlow. Bukti ini mendasari BUG-020.

## Perbaikan ukuran release 1.0.1 — berhasil pada root default

Build final ARM64 SHA256 `3436ec61465a5a6e7a710158d21529c75a5feb50c9a3dbf35587aa004acf23ac` selesai, ditandatangani dengan kunci produksi yang sama. Host: 60 tes Kotlin/37 Python, lint 0 masalah, signature/alignment/native/ZIP dan AAB validation lulus. **APK terpasang melalui installer bawaan**, hash yang ditarik dari HP dan sertifikat produksi cocok. Pengguna ikut berinteraksi dengan installer/pemilih folder; agent tidak melakukan uninstall/clear data.

Hasil pada Realme Android 10: **50 file / 258.380.600 byte** terbaca **246,4 MB**, default `Downloads/TubeFlow`. Picker membuka root TubeFlow; mode pilihan menunjukkan ukuran sama. Setelah kembali ke default, Segarkan, force-stop/buka ulang saat antrean kosong, lokasi dan ukuran tetap benar. Jumlah file dan daftar ukuran identik sebelum/sesudah; riwayat sudah kosong. Tidak ditemukan fatal/Room error pada PID setelah restart. Screenshot [sesudah restart](images/storage-default-restart-2026-10-10.png).

Cakupan root BUG-020 terverifikasi; grant subfolder/sibling, provider gagal, izin dicabut dan batas folder besar belum diuji runtime. Rincian dan langkah reproduksi pada [laporan storage](STORAGE_SIZE_2026-10-10.md).

## Rilis 1.0.2 — batch perbaikan R1–R7 + BUG-021–026, 11 Oktober 2026

Build ARM64 SHA256 `bdefcf7314af7cc0a9a2990eaddb4813dc3c3d0ec4e026c37bd15d5e03e214a6` (62.801.803 byte, `versionCode` 3) dipasang via `adb install -r` sebagai **update atas 1.0.1 (2)** pada Realme RMX1821 Android 10/API29 ARM64 — sertifikat produksi sama, tanpa uninstall/clear data; kali ini ADB langsung `Success` tanpa workaround verifier. APK yang ditarik dari HP (62.801.803 byte) ber-SHA256 identik dengan artefak lokal dan sertifikat pemasang = produksi `ebe3b40e…`. Setelah install, aplikasi dibuka: **migrasi Room 4→5 terbuka tanpa FATAL/Room error** pada logcat PID aplikasi.

- **Host:** 66 tes Kotlin dan 43 tes Python lulus; lint debug 0 masalah (release lulus pada pipeline build); audit native (255 ELF, 0 gagal 16 KB), zipalign `-P 16 4`, dan ZIP reflexive lulus untuk arm64-v8a; sertifikat/aplikasi `versionName` 1.0.2 terverifikasi. Bundletool tidak tersedia di mesin batch ini — AAB dibangun/ditandatangani pipeline sama dengan 1.0.1 tanpa `bundletool validate` ulang.
- **Artefak:** `app/build/outputs/distribution/1.0.2/` (APK per ABI, AAB, cert, `release.json`, `SHA256SUMS`); 1.0.0/1.0.1 dipertahankan.
- **Belum diuji di batch ini (menunggu sesi perangkat lanjutan):** fungsional unduh/playlist/lirik pada 1.0.2, lanjutan `.part` setelah putus koneksi (BUG-025), alur Unduh lagi + dialog (BUG-026), tampilan `completedAt` setelah migrasi (BUG-023), dan BUG-006 retry mode pesawat. Instalasi + launch hanya membuktikan migrasi terbuka tanpa crash, bukan retensi perilaku.

## Rilis 1.0.3 — BUG-027 tautan Mix tanpa seed, 11 Oktober 2026

Pengguna melaporkan dari 1.0.2 di Realme: menempelkan tautan Bagikan autoplay `https://youtube.com/playlist?list=RDrEjSkBl47OY&playnext=1&si=mvfrZkxYG_CqldGI` menghasilkan `WARNING/ERROR: [youtube:tab] YouTube said: This playlist type is unviewable.` (tertl lewat uiautomator dump di perangkat; reproduksi BUG-027). Perbaikan: seed video dipulihkan dari ID Mix (`RD`/`RDMM` + 11 karakter) dan inspeksi memakai `watch?v=<seed>&list=<id>&start_radio=1` — bentuk yang terbukti diterima YouTube untuk link tersebut (Mix "SATRU 2", 541 entri, engine 2026.08.19 di host).

Build ARM64 SHA256 `46f7e19ce05488d75a8f9c7efef5c845c32e387b2bdf1c6487a404f6a0f6cad6` (62.801.975 byte, `versionCode` 4) dipasang via `adb install -r` sebagai **update atas 1.0.2 (3)** — sertifikat produksi sama, tanpa uninstall/clear data, ADB `Success`; APK yang ditarik dari HP ber-SHA256 identik dengan artefak lokal.

- **Host:** 67 tes Kotlin dan 43 tes Python lulus; lint debug dan release 0 masalah; audit native (255 ELF arm64 / 257 ELF x86_64, 0 gagal 16 KB), zipalign `-c 4`, dan ZIP reflexive lulus untuk ketiga ABI; apksigner v2 produksi terverifikasi.
- **Artefak:** `app/build/outputs/distribution/1.0.3/` (APK per ABI, AAB, cert, `release.json`, `SHA256SUMS`); 1.0.0–1.0.2 dipertahankan.
- **Belum diuji di batch ini:** verifikasi ulang tautan Mix persis laporan di HP — layar terkunci keyguard PIN saat sesi berakhir dan tidak bisa dibuka lewat ADB; verifikasi fungsional 1.0.2 sebelumnya (BUG-023/025/026, BUG-006) juga masih menunggu sesi perangkat.

## Rilis 1.0.4 — auto-folder playlist dan hapus semua file, 11 Oktober 2026

Dua fitur baru: subfolder otomatis per playlist/Mix (Room v6, kolom `subfolder`) dan tombol "Hapus semua file" di kartu Penyimpanan. Build ARM64 SHA256 `09032c38d800bf45e7e171d0bd6bbf3b067b2d40925570c249356ac96d660038` (62.810.803 byte, `versionCode` 5) dipasang via `adb install -r` sebagai **update atas 1.0.3 (4)** — sertifikat produksi sama, tanpa uninstall/clear data; APK yang ditarik dari HP ber-SHA256 identik dengan artefak lokal.

- **Host:** 68 tes Kotlin dan 44 tes Python lulus; lint debug dan release 0 masalah; audit native (255 ELF, 0 gagal 16 KB), zipalign `-c -P 16 4`, dan ZIP reflexive lulus; apksigner v2 produksi terverifikasi.
- **Uji live di Realme Android 10:** tautan Mix `playlist?list=RDrEjSkBl47OY` dibuka lewat intent — seed recovery (BUG-027) memunculkan panel "Mix - DENNY CAKNAN X HAPPY ASMARA - SATRU 2 …" (25 entri awal) dan kartu opsi menampilkan pratinjau subfolder `mix-denny-caknan-x-happy-asmara-satru-2`. Seleksi dikosongkan lalu 1 video dipilih (SATRU 2, 6:34, Audio/MP3) → "Antrekan 1 video" → selesai; file `DENNY CAKNAN X HAPPY ASMARA - SATRU 2 ( Offical Live Music ).mp3` (6.306.446 byte) muncul di `Download/TubeFlow/mix-denny-caknan-x-happy-asmara-satru-2/`, bukan di root. Antrean kosong setelah selesai.
- **Artefak:** `app/build/outputs/distribution/1.0.4/` (APK per ABI, AAB, cert, `release.json`, `SHA256SUMS`); 1.0.0–1.0.3 dipertahankan.
- **Belum diuji di batch ini:** tombol "Hapus semua file" di perangkat (sengaja tidak dijalankan — akan menghapus seluruh koleksi di folder TubeFlow); jalur SAF tree untuk auto-folder dan pembersihan (perangkat memakai jalur MediaStore default); sidecar `.lrc` di dalam subfolder.
