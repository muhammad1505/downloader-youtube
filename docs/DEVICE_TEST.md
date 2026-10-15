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

## Rilis 1.0.5 — enam perbaikan permintaan pengguna lanjutan, 11 Oktober 2026

Batch 11 Okt (auto-folder artis utama, folder default tanpa SAF, klasifikasi lirik, dropdown Pengaturan, multi-platform tahap awal, teks jumlah entri playlist). Build ARM64 SHA256 `264a342e12846366323be663cb0eb48bc510d4b8b03c0aeb63cde4f017a6d974` (62.810.643 byte, `versionCode` 6). Sertifikat produksi sama dengan 1.0.0–1.0.4 (`ebe3b40e…`) — instalasi akan menjadi update tanpa uninstall.

- **Host:** 74 tes Kotlin dan 44 tes Python lulus; lint debug dan release 0 masalah; audit native (255 ELF 0 gagal 16 KB per ABI; AAB 1024 ELF 0 gagal), zipalign `-c -P 16 4`, dan ZIP reflexive (13 kelas, 0 gagal) lulus; apksigner v2 produksi terverifikasi; engine checksum `1fa6733c…` cocok manifest; bootstrap asset identik.
- **Status perangkat: TERPASANG, belum diuji live.** `adb install -r` sebagai update atas 1.0.4 (5) di Realme RMX1821 Android 10 → **Success**; `dumpsys package` menunjukkan `versionCode=6`/`versionName=1.0.5`; APK yang ditarik dari HP ber-SHA256 `264a342e…` identik dengan artefak distribusi. Data riwayat/pengaturan tidak disentuh (update tanpa uninstall/clear data). Sisa uji yang direkomendasikan sebelum menyebut rilis ini terverifikasi: (1) auto-folder dari judul Mix berkolaborator → `mix-denny-caknan`; (2) Pengaturan → Penyimpanan: pilih ulang folder bawaan lewat pemilih sistem → tujuan tetap MediaStore tanpa SAF, ukuran penuh terbaca; (3) unduh audio dengan lirik lagu yang tidak ada di LRCLIB → kartu Riwayat "Lirik tidak tersedia di penyedia lirik" (bukan "jaringan"); (4) dropdown tema & antrean berfungsi dan tata letak rapi; (5) **tautan platform baru (mis. TikTok/SoundCloud) — belum pernah diuji sama sekali, jangan mengklaim didukung sebelum uji live**; (6) jumlah entri playlist biasa mengikuti link.
- **Artefak:** `app/build/outputs/distribution/1.0.5/` (APK per ABI, AAB, cert, `release.json` dengan daftar perubahan, `SHA256SUMS`); 1.0.0–1.0.4 dipertahankan.

## Batch 7 — nama "Judul - Artis", auto-folder Mix, UI penyimpanan — 11 Oktober 2026

Tujuh poin review 1.0.5 (poin SoundCloud/Instagram sudah sukses tanpa perubahan). Perubahan kode: urutan nama konsisten **"Judul - Artis"** (judul DULU — koreksi eksplisit pengguna) di `Format.neatTitle`, parsing `Lyrics.fetch`, dan tag metadata `Engine.download`; heuristik auto-folder Mix memilih segmen judul TERPANJANG (contoh pengguna: "Mix - LAMUNAN - SASYA ARKHISNA (Official…)" → folder mengandung `sasya-arkhisna`, bukan `lamunan`); pesan error Facebook dijelaskan (privat/login/tautan kedaluwarsa); field edit "Folder penyimpanan" di Pengaturan dihapus (ubah folder hanya via pemilih SAF yang bisa membuat folder baru); "Hapus semua file" ikut menghapus folder kosong lewat grant SAF bawaan (`pruneEmptyFolders`); tombol "Bersihkan" entri gagal/batal di Antrean (sebelumnya hanya Riwayat); chip filter platform + label platform di kartu Riwayat.

- **Host:** 78 tes Kotlin dan 44 tes Python lulus; lint debug 0 masalah; `assembleDebug` lulus. (Regresi baru: `neatTitleStripsNoiseAndAddsArtistTitleFirst`, `tidyFileNameKeepsArtistSuffixWhenTruncating`, `nameParsesAsTitleFirstArtistSecond`, `mixFolderPicksArtistFromEitherTitleOrder`, `platformNamesRecognizeKnownHosts`.)
- **Status perangkat: BELUM dipasang/belum diuji** (belum ada rilis baru; perubahan hanya di source + debug build). Uji live yang direkomendasikan sebelum rilis berikutnya: (1) unduh single `https://youtu.be/3J1Opi7MVHw` → nama file "Judul - Maman Fvndy Rmx" (kreator di belakang); (2) Mix `https://www.youtube.com/watch?v=8uWb5QvR95s&list=RD8uWb5QvR95s&start_radio=1` → folder auto mengandung "sasya-arkhisna"; (3) pencarian lirik memakai nama "Judul - Artis" tetap menemukan lagu yang benar; (4) tautan Facebook gagal → pesan "privat/butuh login/tautan kedaluwarsa"; (5) Pengaturan: tidak ada lagi field edit folder; pemilih SAF membuat folder baru langsung; (6) "Hapus semua file" menghapus folder playlist yang kosong; (7) Antrean: "Batalkan semua" lalu tombol "Bersihkan" membersihkan entri; (8) Riwayat: chip platform memfilter benar dan kartu menampilkan platform.
- **Belum diuji di batch ini:** seluruh poin di atas di perangkat; jalur SAF tree untuk prune folder kosong (perangkat memakai jalur MediaStore default); perilaku nama file lama (baris riwayat sebelum batch 7) saat "Cari ulang lirik" — parsing baru mengasumsikan "Judul - Artis".

## Rilis 1.0.6 — batch 7 (nama "Judul - Artis"), 12 Oktober 2026

Build ARM64 SHA256 `c17deb40fc6595f3c9626aadc6211c026a218f8fd30022aebc17e8e2c3e1bff5` (62.813.959 byte, `versionCode` 7, `versionName` 1.0.6) dipasang via `adb install -r` sebagai **update atas 1.0.5 (6)** — sertifikat produksi sama (`ebe3b40e…`), tanpa uninstall/clear data; APK yang ditarik dari HP ber-SHA256 identik dengan artefak distribusi. Aplikasi terbuka setelah instalasi (PID aktif) tanpa `FATAL` di logcat.

- **Host:** 78 tes Kotlin dan 44 tes Python lulus; lint debug dan release 0 masalah (`scripts/build_release.sh --no-daemon`, BUILD SUCCESSFUL 15m25s); audit ketiga APK (native 255/255/257 ELF, 0 gagal 16 KB; ZIP reflexive 13 kelas 0 gagal; zipalign `-c -P 16 4` OK; apksigner v2 produksi terverifikasi); AAB `jarsigner -verify` → jar verified.
- **Status perangkat: TERPASANG, 8 uji live batch 7 BELUM dijalankan** — daftar uji ada di bagian "Batch 7" di atas (nama file "Judul - Artis", auto-folder Mix Sasya, lirik, pesan Facebook, SAF picker, folder kosong, Bersihkan Antrean, chip platform Riwayat).
- **Artefak:** `app/build/outputs/distribution/1.0.6/` (APK per ABI, AAB, cert, `release.json` dengan daftar perubahan, `SHA256SUMS`); 1.0.0–1.0.5 dipertahankan.

## Rilis 1.0.7 — auto-folder Mix dari channel kreator, 12 Oktober 2026

Perbaikan atas laporan pengguna: Mix `RD3J1Opi7MVHw` menghasilkan folder `mix-dj-jauh-ko-pergi-sa-ceritakan-pada-b` (potongan judul) — sekarang folder diambil dari nama channel kreator (kasus ini `mix-maman-fvndy-rmx`). Build ARM64 SHA256 `9ffdea3d887a9a4561aac5ec082ddf9470307fb9f91132f5a34be878e9a5c43a` (62.815.027 byte, `versionCode` 8) dipasang via `adb install -r` sebagai **update atas 1.0.6 (7)** — sertifikat produksi sama (`ebe3b40e…`), tanpa uninstall/clear data; APK yang ditarik dari HP ber-SHA256 identik dengan artefak lokal; app launch (PID aktif) tanpa `FATAL` di logcat.

- **Host:** `scripts/build_release.sh --no-daemon` → BUILD SUCCESSFUL 12m9s; 80 tes Kotlin 0 gagal (termasuk regresi `mixFolderPrefersCreatorChannelOverLongTitle` dengan judul+channel link asli, `pickChannelPrefersMostFrequentEntryChannel`); lint debug dan release 0 masalah; audit ketiga APK (native 255/255/257 ELF 0 gagal 16 KB; ZIP reflexive 13 kelas 0 gagal; zipalign OK; apksigner v2 OK); AAB `jarsigner` verified.
- **Status perangkat: TERPASANG, uji live fix folder Mix BELUM dijalankan** — perlu mengulang inspeksi Mix `https://youtube.com/playlist?list=RD3J1Opi7MVHw&playnext=1&si=_3gDTbz41f73ZLsF` dan memastikan preview subfolder menampilkan `mix-maman-fvndy-rmx` lalu mengunduh 1 video. File lama di folder salah TIDAK dipindah.
- **Artefak:** `app/build/outputs/distribution/1.0.7/` (APK per ABI, AAB, cert, `release.json`, `SHA256SUMS`); 1.0.0–1.0.6 dipertahankan.

## Sistem metadata/resume 1.0.9 — 13 Oktober 2026

Perbaikan memakai skill [tubeflow-dev](../.agents/skills/tubeflow-dev/SKILL.md). Identitas terstruktur (raw/track/artist/evidence/creator/remixer/version), source platform, thumbnail dan resume state tersimpan di Room v8 melalui migrasi 7→8; worker/submit/folder/lirik/tag/kartu menggunakan snapshot bersama. Auto Folder per lagu hanya memakai artis terbukti; judul ambigu tetap aman di root. Registry platform dan cache cover awal berlaku bersama antrean/riwayat, dengan recovery artwork media lama.

Resume memvalidasi sumber/format/ukuran/validator/prefix/fragmen sebelum memakai downloader yt-dlp yang dipin; URL diekstrak ulang, parsial FAILED/PAUSED dipertahankan, fallback restart diberi notice. Pengujian file HP menemukan postprocessor masih mengisi artist dari uploader: kini `MetadataTags` mengirim override meta_title/meta_artist (kosong eksplisit jika tak terbukti), creator/remixer terpisah. Larangan fallback channel berlaku juga pada Mix, menggantikan keputusan batch 12 Oktober sebelumnya.

**Final:** signed 1.0.9/code10 ARM64 SHA256 `e3c5b3f3587614f55f6d56a670d04a41e1f55a6325ddc02e3d1c9958a0cdf91f`, terpasang sebagai update dengan kunci produksi sama. 102 Kotlin + 61 Python lulus, lint debug/release 0 masalah, audit APK×3/AAB lulus. Di HP: 25 riwayat lama utuh; Mix 25 panel awal; cover sebelum selesai; Auto Folder unknown root; duplikat youtu.be ditolak; HTTP 403 menyimpan 9,6 MB, gagal/jeda bertahan setelah force-stop/update, retry memvalidasi parsial dan 42%→MP4 selesai (decode penuh lulus). MP3 akhir 128 kbps dengan creator/remixer dan artist kosong, cover 800×450 ±57 KB; ukuran root 84,6 MB sesuai 88.733.655 byte, dua hasil uji. Final 27 riwayat = 25 lama + audio/video sesi ini, antrean kosong. Tidak memakai emulator.

Sinkronisasi timestamp lirik, artis nyata pada sumber NEGORO/PINGAL, live platform lain/SAF/16 KB belum diuji batch ini. Rincian akar masalah, file, fallback, hasil dan batas: [SYSTEM_METADATA_RESUME_2026-10-13](SYSTEM_METADATA_RESUME_2026-10-13.md).


## Lirik/platform 1.0.10–1.0.11 — 13 Oktober 2026

HP Realme Android 10, tanpa emulator. Update 1.0.10 dengan kunci produksi sama mempertahankan 27 riwayat; download Lamunan Live selesai sebagai entri ke-28. Identitas Sasya Arkhisna terpisah dari creator Music Interactive, YouTube dan thumbnail konsisten di antrean/riwayat. MP3 128 kbps/259,392 detik, artwork 800×450 dan LRC nama sama; full decode lulus. Poweramp menampilkan local lyrics, pengelola mengonfirmasi lagu dan lirik sinkron.

Release 1.0.11/code12 terpasang; hash APK pulled cocok dengan distribusi ARM64 `800f3fd703a539c8996c9611ca1c50ae25ba88d661fbb6e55bba70585e55bd48`. Pengelola menghapus riwayat ketika menunggu build; empat file publik tetap ada, tidak ada uninstall/clear data oleh agent. Retensi baris saat update ini tidak dapat dinilai.

Link akhir SoundCloud `bondanfade2black/ya-sudahlah` berhasil menjadi MP3 128 kbps/252,499592 detik, artwork 797×800 dan `.lrc` 62 timestamp berteks. Antrean/riwayat menampilkan SoundCloud dan thumbnail; creator tersimpan, artist kosong karena metadata asli kosong dan identitas provider tidak unik. Full decode berhasil, Poweramp membuka audio dan LRC. Link Muse/Hysteria sebelumnya hanya diinspeksi, tidak diunduh. [Detail, bukti dan batas](LYRICS_PLATFORMS_2026-10-13.md).

**Hasil auditori SoundCloud:** pengguna melaporkan delay lirik sekitar 0,5 detik; sinkronisasi Ya Sudahlah belum lulus, dicatat sebagai BUG-033. Lamunan tetap dikonfirmasi sinkron secara terpisah.


**Final 1.0.12/code13:** 116 tes Kotlin, lint debug/release 0 masalah; 61 Python pada batch (engine tidak berubah), signed APK×3/AAB/audit/bundletool lulus. ARM64 SHA256 `e6fe701f96de98c83c0ab4cca598b0b71e02978d9df30c54bd44d504ba83d60a` cocok APK terpasang. Riwayat SoundCloud bertahan melalui update/restart; link bersih beropsi sama ditolak sebagai sudah diunduh (BUG-034), antrean kosong, enam file/97.031.658 byte tetap ada dan Settings 92,5 MB pada root TubeFlow. Tie skor lirik memakai durasi terdekat, tanpa mengubah LRC yang telah tersimpan; delay tetap 0,5 detik menurut pengguna, BUG-033 terbuka. [Hasil dan batas lengkap](LYRICS_PLATFORMS_2026-10-13.md).

## Smart Media1.1.0 dan pengecekan ulang Lamunan — 13 Oktober 2026

Status1.0.12 di atas telah dilanjutkan oleh [laporan upgrade1.1.0](SMART_MEDIA_UPGRADE_2026-10-13.md): migrasi8→9 nonempty, katalog/dashboard, rename media–LRC, koreksi waktu, audio/video Lamunan dan playback video pada HP. Ya Sudahlah dikonfirmasi sinkron pengelola setelah koreksi per rekaman−2detik. Cakupan tersebut tidak membuktikan semua lirik/perangkat aman.

Pengecekan ulang saat pengelola menyiapkan uji sinkron Lamunan:

- RealmeRMX1821, APK terpasang1.1.0/code14; SHA256 base.apk `06da7742a451ad27ae8dd88b15c31fad0b047e2bffcead4de1a6f9482e45eda6` cocok final. Tidak install ulang/uninstall/clear-data.
- MP3 Lamunan4.199.522byte,259,392detik/stream128kbps, Sasya Arkhisna/Live, cover800×450; hash cocok final sebelumnya. LRC1.456byte/31timestamp valid16,62–253,30detik, hash `e642dfb16226e4263b25a367023050381a3312c79ee306eae530e70c92f62b4a`, identik dengan file yang dahulu dikonfirmasi sinkron pengguna.
- Koleksi → Musik menampilkan sumber YouTube, artis dan penanda sampul. Dialog waktu Lamunan terbaca **Waktu asli**, preview awal `[00:16.62]`; ditutup dengan Batal, tidak menyimpan koreksi. Snapshot yang sempat berbeda menghentikan tap; pembacaan ulang berhasil.
- Ya Sudahlah tetap memiliki hash LRC hasil koreksi−2detik sebelumnya; tidak diterapkan pada Lamunan. Hash audio/LRC Lamunan diperiksa kembali setelah inspeksi UI.

**Menunggu:** auditori baru Lamunan pada Poweramp di awal/tengah/akhir. Pengelola akan melaporkan sinkron/terlambat/terlalu cepat, besar selisih dan apakah tetap. Tidak ada tes putus jaringan, decode ulang file identik, atau pemutaran auditori baru pada sesi pengecekan ini. Bukti privat/panduan langkah lanjutan tersedia pada [bagian pengecekan ulang](SMART_MEDIA_UPGRADE_2026-10-13.md#pengecekan-ulang-sebelum-uji-lamunan--13-oktober-2026).

## Rilis 1.1.1 — dashboard Ringkasan media, 13 Oktober 2026

Batch dashboard + penguatan cari ulang lirik (kontrak, hasil host, dan batas lengkap ada di [laporan DASHBOARD_LYRICS](DASHBOARD_LYRICS_2026-10-13.md)). HP yang sama: Realme RMX1821, Android 10, arm64, tanpa emulator; update memakai keystore produksi yang sama, tanpa uninstall/clear data.

- **Host (dijalankan/diuji ulang sesi dokumentasi ini):** 145 tes Kotlin 0 gagal (`test-results/testDebugUnitTest`), 76 tes Python lulus (dijalankan ulang, 23,173 detik), lint debug/release "No issues found"; `sha256sum -c SHA256SUMS` semua OK; audit native 255/255/257 ELF 0 gagal 16 KB + ZIP reflektif 13 kelas 0 gagal pada ketiga APK, `zipalign -c -P 16 4` OK, `apksigner` v2 verified dengan sertifikat `ebe3b40e…`; AAB audit native 1.024 ELF 0 gagal dan `jarsigner -verify` jar verified (peringatan self-signed/PKIX/timestamp/POSIX wajar, sama seperti rilis sebelumnya; bundletool tidak diulang — tidak ada log sesi ini).
- **Instalasi:** `adb install -r` atas 1.1.0 (code14) → **1.1.1 (code15)** sukses (`lastUpdateTime 13 Okt 2026 09:58:46`); `base.apk` ditarik dari HP ber-SHA256 `437d42759c2b953587f885d70b0a5ffd44fee21dc47dd0774fa088ee847405c0` — identik `distribution/1.1.1/TubeFlow-1.1.1-arm64-v8a-release.apk` (63.025.099 byte). Sertifikat signer sama dengan rilis sebelumnya.
- **UI dashboard (UIAutomator + screenshot, 09:59–10:03):** donat kondisi kosong menampilkan "0 media tersedia / Belum ada media tersedia" dan "5 file tidak dapat diakses tidak masuk grafik"; batang 7 hari (2026-10-07…2026-10-13) menampilkan puncak 2 selesai/hari, ringkasan 2/1/0 dan rasio 66% dengan catatan "Pencatatan dimulai 13 Okt 2026 08:21 … tidak merekonstruksi aktivitas sebelum upgrade", hari tanpa pencatatan bertanda "Titik abu-abu", bukan 0; garis akumulasi total periode 2 dengan legenda "bukan jumlah file atau riwayat ukuran penyimpanan"; kartu sumber "YouTube · 2". Tangkapan layar: [donut](images/dashboard-1.1.1-donut.png), [bars](images/dashboard-1.1.1-bars.png), [line](images/dashboard-1.1.1-line.png). Periode 30/90 hari tercatat diperiksa via UIAutomator pada `release.json`; screenshot yang disimpan hanya 7 hari.
- **Belum diuji di batch ini:** koleksi berisi media setelah pembersihan pengelola (donat hanya diuji pada kondisi kosong — retensi media `not established` menurut `release.json`); refetch lirik live di perangkat, kegagalan provider SAF, dan proses mati (fault injection host saja); sinkronisasi lirik/Lamunan tidak disentuh; benchmark FPS/daya/kecepatan tidak diukur.


## Pemeriksaan perubahan dan Auto Folder1.1.2 — 13 Oktober2026

Baseline1.1.1 yang benar-benar terpasang diperiksa kembali dengan koleksi nyata:50audio/259.937.411byte/7LRC, donat50/0, batang/garis50, rowhistory50completed vs aktivitas50completed/2failed (retry). Normalisasi/tag/artwork aktif.43metadata masih parsial, sehingga penamaan seluruh koleksi belum dinyatakan selesai.

Release1.1.2/code16/Room9 ber-SHA256 `d7210fba3fe44419144629a7c41335296733be6da79fe31b73b2ad0dfd7a093a` terpasang sebagai update pada RealmeAndroid10, base.apk cocok.57file lama memiliki nama/hash identik sebelum/sesudah update.149Kotlin/76Python, lint0, audit APK×3/AAB dan bundletool lulus.

Mix RD3J1Opi7MVHw menampilkan25entri. Hanya seed dipilih, opsiAudioAuto+lirik+sampul: Opus4.114.238byte/247,800s terbit di `Download/TubeFlow/maman-fvndy-rmx/`, nama judul/version/remixer sesuai; tag creator/remixer terpisah dari artist asli yang kosong, cover800×450. Decode penuh exit0, antrean kembali kosong, semua57file lama utuh. Ini bukan unduhan ulang otomatisMP3 lama, melainkan varian sumber untuk uji policyfolder. [Detail dan batas](SOURCE_REVIEW_2026-10-13.md).

Sampel kedua dariMixRDrEjSkBl47OY (SATRU2duet) juga selesai lewat pickerplaylist: `TubeFlow/denny-caknan-ft-happy-asmara/SATRU 2 _ Live - DENNY CAKNAN FT HAPPY ASMARA.opus`,6.693.911byte/393,980s, tag title/artis/creator benar, cover800×450, decode penuh exit0.57file lama dan sampelMix pertama utuh. LRC baru tidak terbit pada kedua sampel; tidak ada klaim auditori baru.


## Release1.1.3 — pemasangan, playlist dan cleanup

160 Kotlin/76 Python/lint0 serta build/audit final lulus. ARM64 terpasang via update-r; version1.1.3/code17 dan SHA256 `c767ac27c705a2adeba408c05ab7c51b7603885bb4085b20cbd80e2b86d35e7a` base.apk cocok. Pengguna akan menguji sendiri karena waktu kerja; skenario runtime playlist/cleanup1.1.3 belum dijalankan. Baseline pengguna setelah reset:0file/5subfolder kosong. [Checklist dan cakupan](COLLECTION_CLEANUP_2026-10-13.md).


## Release 1.1.4 — Android 10 fisik

Realme RMX1821, Android 10/API29, ARM64, halaman memori 4 KB. Update-r berhasil; hash base.apk cocok dengan ARM64 final `61e39a4849db7410982a92840d32b76d94bf94893b0086c0bbfc785953bf56ed`. Factory player release minify, mini/full player, pause, seek ±10 detik dan seek baris LRC unik (3:38) diuji. Sinkronisasi auditori memerlukan konfirmasi pengguna. Detail Mix, integritas file, risiko dan skenario belum diuji ada pada [laporan](UPGRADE_1_1_4_2026-10-14.md).


## Release1.1.5 — Android10 fisik

Final ARM64/code19 terpasang update-r tanpa reset, SHA256 `c4aea0927af8bac992036805986fc1e59982e324760ed37a99d800b544154828` cocok. 61file sebelum/sesudah update memiliki hash sama. Candidate menampilkan17hasil SATRU2; pengelola mengonfirmasi seleksi/simpan, LRC10455242 terbit sesuai stem dan payload. UI kolom detik terlihat; penyimpanan offset custom/rename→search/LR auditori belum diklaim. [Bukti dan cakupan](MANUAL_LYRICS_1_1_5_2026-10-14.md).


## Upgrade pemutar ultimate + antrean — 14 Oktober 2026

Konteks: pengelola mengonfirmasi tes 1.1.6 sebelumnya "sudah sesuai", lalu meminta (1) hapus fitur L/R, (2) lirik di media player yang "hilang" harus kembali jelas, (3) play satu lagu = seluruh daftar masuk antrean, (4) player tidak boleh mati saat layar mati, (5) pemutar lengkap dengan equalizer dkk. Perubahan: `PlaybackQueue` sudah ada dari sesi sebelumnya; sesi ini menghapus `AudioChannel`/kontrol L/R, menambah `PlaybackService` (foreground `mediaPlayback`, MediaSession, notifikasi MediaStyle 4 aksi, partial wake lock saat putar), `PlayerEqualizer` (audiofx bawaan, preset/level tersimpan), kecepatan putar 0,25–3×, chip tampilan **Sampul/Lirik** yang jelas dengan lirik otomatis tampil bila tersedia, dan tap item = seluruh daftar masuk antrean (Koleksi & Riwayat).

**Build/host:** `scripts/build_release.sh` lulus (testDebugUnitTest 207 tes, lintDebug/lintRelease 0 masalah, APK×3/AAB). APK ARM64 70.408.643 byte SHA256 `08fa5da247bb0985592ecd851cb534588b00b2f6e21ee72ceb25dda6c8bb67fa`, sertifikat produksi `ebe3b40e…` (apksigner v2), terpasang `install -r` sebagai update atas 1.1.6 (code20) di Realme RMX1821 Android 10 tanpa uninstall/clear-data; hash `base.apk` yang ditarik dari HP cocok. 62 file publik (11 LRC) tetap utuh setelah update.

**Uji perangkat (UIAutomator/dumpsys, HP sama):**

| Kasus | Hasil yang diamati |
| --- | --- |
| Tap kartu lagu di Koleksi | Memutar langsung dan **seluruh daftar masuk antrean** — dialog Antrean menampilkan 51 item (SATRU aktif, lalu DUMES, SIKSA MENANGGUNG RINDU, dst.), bukan satu lagu. |
| Lirik di pemutar | **Tampil otomatis** penuh dengan sorotan baris aktif (SATRU); chip **Sampul/Lirik** berganti tampilan dua arah. (Versi sebelumnya lirik hanya muncul lewat tombol ikon yang mudah terlewat.) |
| Lompat antrean | Ketuk DUMES di dialog antrean → pemutar berpindah ke DUMES (0:03/5:13). |
| Equalizer | Dialog terbuka; perangkat menyediakan 5 band (60 Hz–14 kHz) + preset bawaan "Normal"; switch Aktif, slider per band, preset tersimpan. |
| Kecepatan putar | Menu 0,5×–2×; pilih 1,5× → label berubah dan posisi maju lebih cepat (1:11 → 2:03 dalam beberapa detik). |
| **Layar mati** | `KEYCODE_SLEEP` + 20 detik → MediaSession `state=3` (PLAYING) terus berjalan; setelah bangun posisi lanjut 1:11 dari 0:51. Wake lock partial hanya diambil saat putar. |
| Kontrol media eksternal | `media dispatch pause/play/stop` (jalur tombol headset/notifikasi) bekerja saat layar mati: state 3→2→3; stop menghentikan service, melepas MediaSession, menutup pemutar, dan menghapus notifikasi (0 ServiceRecord). |
| Notifikasi | Service `PlaybackService` foreground `isForeground=true` dengan notifikasi channel `playback` 4 aksi (sebelumnya/play-pause/berikutnya/tutup). |

**Batas cakupan:** prev/next/shuffle/repeat dari UI tidak dites manual satu per satu (transisinya diuji host `PlaybackQueueTest`); kecepatan 0,25–3× di luar menu tidak ada; equalizer diuji tampilan/preset, bukan perubahan suara auditori; dukungan headset/Bluetooth fisik tidak diuji (hanya dispatch media); durasi panjang/layar mati >20 detik dan audio focus saat telepon tidak diuji; sinkronisasi auditori lirik tidak disentuh. Tidak ada pengukuran baterai/benchmark.


## Perbaikan Koleksi, notifikasi, karaoke dan autoplay — 15 Oktober 2026

Konteks laporan pengelola: setelah **menghapus data aplikasi**, 53 file di `Download/TubeFlow` (51 `mix-*` yang ditulis via SAF/versi lama sehingga `owner_package_name` kosong di MediaProvider + 2 ENGKAULAH) tidak terbaca di Koleksi dan ukuran folder tampil 0; ikon status bar notifikasi berupa kotak putih; progressbar notifikasi mentok di akhir meski lagu baru mulai; detail karaoke tidak ikut berubah saat ganti lagu; permintaan auto-play musik saat headset/TWS konek.

**Akar masalah dan perbaikan:**

1. **Koleksi kosong:** query MediaStore tanpa izin storage hanya melihat kontribusi milik aplikasi; grant SAF persisten ikut hilang saat data dihapus dan `scan()` tidak pernah memeriksa grant folder bawaan. Kini `MediaRepository.scan()` memakai grant persisten `Download/TubeFlow` (`StorageFolderAccess.defaultTree`) lewat jalur dokumen bila `storageTree` kosong, tanpa mengubah tujuan output; tombol **Pindai folder** otomatis membuka pemilih folder bila tidak ada akses (`MediaLibraryViewModel.grantFolder`); dedup folder+nama+ukuran mencegah baris ganda; ukuran folder/pembersihan memakai grant yang sama.
2. **Ikon kotak putih:** `PlaybackService` memakai `ic_launcher_foreground` (vektor berwarna penuh) sebagai small icon. Diganti `drawable/ic_stat_playback.xml` — siluet monokrom alfa 24 dp (bingkai + segitiga putar).
3. **Progressbar penuh:** MediaSession tidak pernah diberi metadata durasi sehingga UI sistem menganggap lagu selesai; posisi juga tidak dikirim ulang. Kini `setMetadata` (judul/artis/DURATION) dipasang tiap render dan `durationMs` ikut disinkronkan setelah prepared; posisi selalu mulai dari 0 untuk lagu baru.
4. **Detail karaoke:** judul dialog menampilkan `source?.title` (lagu proses lama) saat proses lama aktif/selesai. Kini judul selalu lagu terpilih dan panel proses lama diberi label sumber eksplisit.
5. **Auto-play headset:** `AudioDeviceCallback` (tanpa izin tambahan) melanjutkan musik yang sedang berhenti saat output kabel/Bluetooth/USB tersambung.

**Build/host (`scripts/build_release.sh`):** 207 Kotlin + 78 Python 0 gagal, lint debug/release 0 masalah, signed APK×3/AAB. Audit native 257/257/259 ELF (APK) dan 1.038 ELF (AAB) 0 gagal 16 KB, audit ZIP reflektif 13 kelas 0 gagal, `apksigner` v2 **Verifies** (satu signer). ARM64 70.415.371 byte SHA256 `b01188725889e146c39f2c13b2f61843ce5cedbdefe36fdb7c51362a6dc90809`.

**Instalasi:** `adb install -r` sebagai update atas 1.1.6 (code20, keystore produksi sama) di Realme RMX1821 Android 10, `lastUpdateTime 2026-10-15 03:24:48`; tanpa uninstall/clear-data oleh agent (clear data dilakukan pengelola sebelum laporan).

**Uji perangkat (UIAutomator/dumpsys/screenshot):**

| Kasus | Hasil yang diamati |
| --- | --- |
| Koleksi setelah hapus data | Awalnya hanya 2 item ENGKAULAH. **Pindai folder** → dashboard menampilkan **52 media tersedia / 267,3 MB / 13 artis / 6 album**, 1 file tidak dapat diakses (baris ENGKAULAH 5,1 MB yang filenya memang sudah tidak ada). 51 file `mix-*` lama kembali terbaca lengkap dengan artis/cover. Ukuran folder tidak lagi 0. |
| Ikon status bar | `dumpsys notification`: `icon=…drawable/ic_stat_playback` (bukan lagi kotak putih `ic_launcher_foreground`); tangkapan layar notifikasi menampilkan badge putar monokrom. |
| Progressbar notifikasi | `media_session` metadata `size=3` (judul, artis, **durasi**), `state=3 position=0` saat lagu mulai; tangkapan layar menampilkan **00.28 / 05.36** beberapa detik setelah lagu dimulai — bar di posisi awal, bukan mentok di akhir. |
| Detail karaoke ganti lagu | Dengan proses UVR aktif untuk Lamunan (LIVE), dialog karaoke untuk lagu lain menampilkan judul **Nemen (Live Royal)** (lagu terpilih) plus label "Proses karaoke yang tampil adalah untuk Lamunan (LIVE)", progres "Memisahkan vokal" 17%→48%. (Sebelum perbaikan: judul menampilkan lagu proses lama tanpa keterangan.) |
| Antrean dari koleksi | Tap lagu → "Antrean (52)" di pemutar penuh; lirik otomatis tampil; metadata sesi mengikuti lagu aktif (Nemen, Happy Asmara). |
| Proses karaoke cuplikan | Sampel 30 detik Lamunan (sumber 2:44.732–3:14.732) selesai penuh: status "Cuplikan instrumental siap"; **Dengarkan instrumental** memutar pratinjau (metadata sesi `Lamunan (LIVE) (Karaoke cuplikan)`, state PLAYING, 0:30/0:30); **Simpan cuplikan FLAC** menerbitkan `Lamunan (LIVE, Karaoke cuplikan) - Denny Caknan.flac` 3.571.363 byte ke `Download/TubeFlow/` (05:23) dan otomatis masuk Koleksi (52 → 53 media); tombol berubah "Sudah disimpan". |

**Batas cakupan:** auto-play headset/TWS **belum diuji fisik** (perlu aksesori; verifikasi manual: putar musik → jeda → sambungkan TWS → musik lanjut sendiri). Pemulihan folder bila grant benar-benar hilang total akan memunculkan pemilih folder dari tombol Pindai folder; jalur picker ini belum diuji ulang karena grant di perangkat masih ada. File uji cuplikan karaoke tersimpan di Koleksi dan boleh dihapus pengelola. Kinerja/baterai, perilaku saat telepon, dan uji layar mati untuk sesi ini tidak diulang. Tidak ada tes destruktif tambahan.


## Fix auto-scan Koleksi dan auto-play saat app tertutup — 15 Oktober 2026 (lanjutan)

Laporan pengelola setelah batch pagi: (1) setelah hapus data, izin baca memang diminta dan disetujui, tetapi hasil **Pindai folder tetap nol** dan Koleksi kosong sampai memilih folder di Pengaturan; (2) **auto-play headset hanya jalan saat aplikasi terbuka** — menutup aplikasi lalu memasang headset tidak memutar apa pun.

**Akar masalah dan perbaikan:**

1. **Scan nol meski izin diberikan:** di Android 10 (scoped storage), baris MediaStore file lama (`owner_package_name` kosong, ditulis via SAF/versi lama) tidak terlihat oleh query aplikasi meski `READ_EXTERNAL_STORAGE` sudah granted — terbukti dari `content query` shell (baris ada, `mime_type=audio/mpeg`, `is_pending=0`) sementara aplikasi melihat 0 baris. Fix: `android:requestLegacyExternalStorage="true"` pada manifest (dihormati di perangkat Android 10 apa pun targetSdk — penegakan scoped storage untuk targetSdk 30+ ada di platform Android 11+; terverifikasi `PRIVATE_FLAG_REQUEST_LEGACY_EXTERNAL_STORAGE` pada paket), izin `READ_EXTERNAL_STORAGE` + `WRITE_EXTERNAL_STORAGE` (maxSdk 29) untuk mode legacy, dan `MediaRepository.scan()` kini **union dua sumber** (MediaStore + grant SAF) dengan dedup nama-folder-ukuran serta ringkasan `ScanSummary(checked, failed, discovered)` yang jujur. `scanIfEmpty` memindai otomatis saat aplikasi dibuka dan koleksi kosong; izin diminta otomatis sekali. **Alur yang dituju: hapus data → buka app → setujui izin → Koleksi terisi sendiri**, tanpa pilih folder/tanpa impor.
2. **Auto-play mati saat app ditutup:** engine `PlayerViewModel` dimiliki activity (`ViewModelProvider(this)`), sehingga menutup UI mematikan engine lewat `onCleared`. Fix: engine kini milik proses via `PlayerViewModel.obtain(app)` (singleton, dipakai `MainActivity`), pemicu cadangan `ACTION_HEADSET_PLUG`/Bluetooth `CONNECTION_STATE_CHANGED` lewat runtime receiver **dan** manifest `HeadsetAutoPlayReceiver` (bekerja walau proses baru dibangun), serta lagu terakhir dipersist (`player-last`) sehingga auto-play punya sumber saat pemutar kosong. Log `TubeFlow-autoload` membantu diagnosis.

**Build/host:** `scripts/build_release.sh` lulus (207 tes Kotlin 0 gagal, lint debug/release 0 masalah, APK×3/AAB); audit native 257 ELF 0 gagal 16 KB, audit ZIP reflektif 13 kelas 0 gagal, `apksigner` v2 **Verifies**. ARM64 70.418.227 byte SHA256 `fac5ecaef0e0b5491f596e3bab90129c6202acacd18fb7f388323d7a19fd9f5c`, terpasang `install -r` sebagai update (07:33).

**Bukti perangkat sejauh ini:** paket membawa flag legacy storage; `HeadsetAutoPlayReceiver` terdaftar dengan dua filter; aplikasi berjalan normal dengan Koleksi 52 media/267,3 MB; headset yang tersambung saat app terbuka langsung memutar (jalur in-process). **Menunggu uji pengelola:** (a) hapus data → setujui izin → Koleksi terisi tanpa pilih folder; (b) tutup aplikasi (geser recent) → sambungkan TWS/headset → musik berbunyi. Catatan: bila proses benar-benar mati, penerima manifest bergantung pada pengiriman broadcast sistem (beberapa perangkat membatasi `HEADSET_PLUG`); menghapus paket (uninstall) tetap membutuhkan persetujuan izin sekali di pemakaian berikutnya.


## Fix hapus baris, auto-play A2DP, sampul notifikasi, tombol koleksi, riwayat vs antrean — 15 Oktober 2026 (batch 3)

Laporan pengelola: (1) hapus file menyisakan baris "file tidak tersedia"; (2) auto-play masih tidak jalan malah makin tidak jalan; (3) mini player di notifikasi perlu tampilan sampul + aksen; (4) hapus tombol "Pindai folder"/"Impor file" di Koleksi karena sudah otomatis; (5) item riwayat ikut masuk/membanjiri antrean pemutar.

**Akar masalah dan perbaikan:**

1. **Baris sisa setelah hapus:** `MediaRepository.delete()` sebelumnya menyimpan baris dengan availability `UNAVAILABLE` sehingga daftar tetap menampilkan entri mati. Kini baris ikut dihapus (`dao.remove(item.uri)`) karena file sudah tiada dan pengguna memang memilih Hapus; riwayat event `DELETED` tetap dicatat untuk mencegah duplikat.
2. **Auto-play saat proses mati:** receiver manifest sebelumnya hanya mendengar profil *headset* (`BluetoothHeadset`), sedangkan TWS/A2DP memakai `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` — yang dikecualikan dari batas broadcast implicit Android 8+ sehingga bisa membangunkan proses. `HeadsetAutoPlayReceiver` kini mendaftar **tiga filter**: `BluetoothA2dp.CONNECTION_STATE_CHANGED`, `BluetoothHeadset.CONNECTION_STATE_CHANGED`, dan `ACTION_HEADSET_PLUG` (kabel); `PlayerViewModel` menambahkan runtime receiver untuk A2DP saat proses hidup. Catatan platform: `HEADSET_PLUG` (kabel) hanya disalurkan ke receiver runtime saat proses mati — keterbatasan Android, bukan aplikasi.
3. **Notifikasi mini player:** `PlaybackService` kini menyertakan sampul besar (`MediaMetadata.METADATA_KEY_ALBUM_ART`/`ART` dari cache sampul, di-cache antar lagu) dan aksen warna yang diekstrak dari sampul (`CoverColors`) untuk warna progress bar.
4. **Tombol koleksi:** "Pindai folder" dan "Impor file" dihapus dari Koleksi, dashboard, dan turunannya; Koleksi kini sepenuhnya otomatis (MediaStore + grant + `scanIfEmpty`).
5. **Riwayat vs antrean:** `HistoryScreen.onPlay` kini memutar **hanya lagu yang diketuk** (`player.playJobs(listOf(job), 0)`) — daftar riwayat tidak lagi membanjiri antrean pemutar.

**Build/host:** `testDebugUnitTest` 207 tes 0 gagal (EXIT=0), `scripts/build_release.sh` BUILD SUCCESSFUL (test + lintDebug + lintRelease + assemble + bundle), audit native 257 ELF 0 gagal, audit ZIP reflektif 13 kelas 0 gagal, `apksigner` v2 **Verifies**. Terpasang `adb install -r` sebagai update di Realme RMX1821 Android 10; `HeadsetAutoPlayReceiver` terdaftar dengan 3 filter intent.

**Uji perangkat (UIAutomator/screenshot):**

| Kasus | Hasil yang diamati |
| --- | --- |
| Hapus file | Menu ⋮ → Hapus file → konfirmasi → toast "File dihapus."; baris **langsung hilang** dari Koleksi (tidak ada lagi baris "file tidak tersedia"), daftar menata ulang tanpa entri mati. |
| Tombol Koleksi | "Pindai folder"/"Impor file" tidak ada; hanya "Putar semua" dan "Acak". |
| Notifikasi mini player | Panel notifikasi menampilkan **sampul besar** (artwork lagu) dan progress bar dengan **aksen merah** yang cocok dengan sampul lagu. |
| Riwayat vs antrean | Kode terverifikasi: tap lagu di riwayat hanya memasukkan lagu itu ke antrean pemutar (`playJobs(listOf(it), 0)`). |

**Batas cakupan:** auto-play TWS **saat proses mati** masih menunggu uji fisik dengan headset (jalur A2DP manifest sudah terdaftar; jika perangkat membatasi broadcast saat proses mati, log `TubeFlow-autoload` akan kosong — jalur runtime tetap aktif saat aplikasi hidup). Uji hapus dilakukan pada satu item (ENGKAULAH ISTRI PUJAANKU) atas izin pengelola ("bebas hapus file yang mana").


## Fix antrean auto-play isi koleksi utuh — 15 Oktober 2026 (batch 4)

Laporan pengelola: auto-play headset/TWS kini berjalan, tetapi **antrean putar hanya berisi 1 file** — setelah lagu itu selesai/next, pemutar diam tanpa lagu lanjutan.

**Akar masalah dan perbaikan:** `PlayerViewModel.autoPlayOnDevice()` melanjutkan lagu terakhir lewat `play(track)` → `startQueue(listOf(track))`, sehingga antrean terisi satu lagu. Kini auto-play mengisi antrean dengan **SELURUH koleksi audio tersedia** (query baru `MediaDao.availableAudio()`, urutan koleksi terbaru dulu) dan mulai dari **lagu terakhir yang diputar**; bila lagu itu sudah tidak ada di koleksi (file dihapus), mulai dari awal koleksi. Keputusan isi antrean + posisi mulai berada di perencana murni `AutoPlayQueue.plan()` (tanpa Android, dapat diuji host). Jika lagu aktif masih siap (prepared) dan sama, antrean **dilebarkan tanpa memuat ulang** sehingga posisi putar tidak hilang; selain itu antrean utuh dimuat mulai dari lagu terakhir. Koleksi kosong tetap jatuh ke perilaku lama (lagu terakhir apa adanya).

**Host:** 213 tes Kotlin 0 gagal — termasuk 6 regresi baru `AutoPlayQueueTest` (koleksi utuh bukan 1 lagu, mulai dari lagu terakhir, fallback awal koleksi saat lagu terhapus, start null, koleksi kosong tanpa rencana, pemilihan lewat id selector) — dan 78 tes Python OK. `scripts/build_release.sh` BUILD SUCCESSFUL 16m20s (testDebugUnitTest + lintDebug + lintRelease + assembleRelease + bundleRelease). Lint debug/release: **1 warning pralahir `SelectedPhotoAccess`** (penanganan izin media visual Android 14+ dari `READ_MEDIA_VIDEO`; tidak terkait perubahan batch ini — sekaligus koreksi atas klaim "lint 0 masalah" pada batch sebelumnya). Audit native 257 ELF 0 gagal 16 KB, audit ZIP reflektif 13 kelas 0 gagal, `apksigner` **Verifies** (satu signer, sertifikat produksi `ebe3b40e…`). Versi dinaikkan menjadi **1.1.7 (code21)** agar update terverifikasi.

**Instalasi:** `adb install -r` sebagai update atas 1.1.6 (code20) di Realme RMX1821 Android 10 → **Success**; `dumpsys package` menunjukkan `versionCode=21`/`versionName=1.1.7`; `base.apk` yang ditarik dari HP ber-SHA256 `a7721a5534278d0de2cf98d11526d2bb994f9285b28e4bab2e07f3f82babb821` — identik artefak `app-arm64-v8a-release.apk` (70.419.479 byte). Launch tanpa `FATAL` di logcat.

**Batas cakupan:** pemicu auto-play fisik **tidak dapat dipalsukan via ADB** — `am broadcast android.intent.action.HEADSET_PLUG` ditolak platform ("Permission Denial: not allowed to send broadcast" dari uid 2000) dan layar terkunci passcode 6 digit sehingga verifikasi UI tidak mungkin di sesi ini. **Uji pengelola yang ditunggu:** tutup aplikasi (geser recent) → sambungkan TWS/headset → musik mulai dengan **antrean penuh** — buka dialog Antrean dan pastikan jumlahnya = koleksi (mis. 52), bukan 1; lagu berikutnya otomatis lanjut setelah lagu pertama selesai. Log `TubeFlow-autoload` kini mencetak `queue=<jumlah>` untuk diagnosis.


## Upgrade UI pemutar: swipe sampul↔lirik, alat lirik, aksen sampul, antrean, EQ otomatis — 15 Oktober 2026 (batch 5)

Lima permintaan pengelola: (1) pindah sampul↔lirik cukup dengan **swipe**; (2) saat lirik kosong, sediakan **Cari lirik** dan **Editor lirik** langsung di pemutar supaya tidak bolak-balik ke Koleksi; (3) latar lirik memakai **aksen warna sampul**, bukan hitam polos; (4) tampilan **antrean** lebih premium dan rapi; (5) equalizer **Otomatis** supaya semua musik mantap, bassnya bulat.

**Perubahan:**

1. **Swipe Sampul↔Lirik:** halaman utama pemutar penuh kini `HorizontalPager` — geser kiri/kanan untuk berpindah; chip **Sampul/Lirik** tetap ada sebagai indikator yang bisa diketuk. Swipe pengguna menang atas pemilihan otomatis; lagu baru kembali memakai aturan otomatis (lirik tampil bila tersedia).
2. **Alat lirik di pemutar:** lirik kosong menampilkan tombol **Cari lirik** (pencarian LRCLIB yang sama dengan Koleksi — parameter judul/artis dapat diedit, preview, gerbang kecocokan tetap berlaku) dan **Editor lirik** (dialog "Waktu lirik" yang sama untuk koreksi ±detik); saat lirik tersedia tersedia "Atur waktu lirik" dan "Cari lirik lain". `MediaLibraryViewModel.openLyricsForTrack(uri)`/`itemFor(uri)` memetakan URI track pemutar ke item koleksi; dialog lirik dihost di dalam jendela pemutar agar tampil di atas pemutar penuh (z-order dialog).
3. **Latar lirik aksen sampul:** gradien pemutar dan halaman lirik memakai warna dominan sampul (`rememberCoverAccent`/`CoverColors`), baris lirik aktif juga memakai aksen sampul — nuansa mengikuti warna sampul lagu, bukan hitam.
4. **Antrean premium:** dialog Antrean memuat thumbnail sampul per lagu, kartu lagu aktif beraksen + label "Sedang diputar", jumlah lagu di judul, auto-scroll ke posisi aktif, aksi lompat/keluarkan tetap ada.
5. **EQ Otomatis (bass bulat):** kurva `AutoEq` baru — +5 dB di 60 Hz, mid ditekan halus (−1 dB di 2 kHz) agar vokal jernih, +2,5 dB treble — dihitung per **frekuensi tengah band** (jumlah/lebar band beda per perangkat), interpolasi logaritmik, dijepit ke rentang band. Preset **Otomatis (bass bulat)** menjadi bawaan (kecuali pengguna sudah memilih preset/level sendiri) dan diterapkan ke **semua lagu**; equalizer tetap dapat dimatikan.

**Host:** 220 tes Kotlin 0 gagal (7 regresi baru `AutoEqTest`: boost bass, mid jernih, kilau treble, interpolasi mulus antar titik, clamp rentang band, pilihan preset default), 78 tes Python OK (engine tidak berubah), `scripts/build_release.sh` BUILD SUCCESSFUL 13m58s (test + lintDebug + lintRelease + assemble + bundle). Audit native 257 ELF 0 gagal 16 KB, audit ZIP reflektif 13 kelas 0 gagal. Lint debug/release: **2 warning, keduanya bukan dari perubahan ini** — `SelectedPhotoAccess` (pralahir, izin media visual Android 14+) dan `GradleDependency` (onnxruntime 1.23.2→1.31.0 tersedia; **pin disengaja** — 1.31.0 gagal JNI startup di perangkat target, tercatat di `build.gradle.kts`).

**Catatan build:** percobaan pertama gagal `No space left on device` (disk 100%; 21 artefak rilis lama di `app/build/outputs/distribution/` = 7,5 GB) — dibersihkan hanya intermediates/scratch (~5 GB), **artefak rilis lama tidak disentuh**; build kedua gagal karena bentrok overload `openLyrics(uri)` vs `openLyrics(jobId)` — diganti nama `openLyricsForTrack`, lalu lulus.

**Instalasi:** 1.1.8 (code22) ARM64 70.473.503 byte SHA256 `622796f208b4089210089fa70b54344bf87245740a1a40c8b06e8d41a2f7a963` terpasang `adb install -r` sebagai update atas 1.1.7 (code21) di Realme RMX1821 Android 10; `base.apk` yang ditarik dari HP cocok dengan artefak; launch tanpa `FATAL` di logcat.

**Perangkat:** saat sesi, layar terbuka dan **pengelola sedang aktif memakai aplikasi** (dialog "Cari dan pilih lirik" untuk Laut Kidul LIVE dengan 19 hasil terlihat pada tangkapan layar lokal; tidak dipublikasikan karena indikator aplikasi lain pada status bar) — agent sengaja tidak menyentuh UI untuk menghindari gangguan.

**Batas cakupan:** swipe/pager, alat lirik di pemutar, dialog antrean baru, dan EQ Otomatis **belum diuji interaktif di perangkat** (diserahkan ke pengelola); kualitas suara EQ belum dinilai auditori; kontras baris lirik pada sampul warna terang belum diuji; kedua warning lint tetap terbuka.

## Player dan penyimpanan 1.1.9 — 15 Oktober 2026

Perangkat fisik Realme RMX1821/Android 10/ARM64/4KB. APK produksi code23 SHA256 `042d64db675617826441da0c0e76e7aab39ecad849d73732aa8279a0d9696af0` dipasang `install --no-incremental -r`; hash APK ditarik dari HP cocok. Tidak uninstall/clear data.

Terbukti: UI mini/penuh/antrean terang dan gelap, menu antrean, pindah lagu menjadi berikutnya, bersihkan hanya menyisakan lagu aktif, ukuran Koleksi 263,8 MB tanpa pemilih folder/grant baru (51 audio fisik/276.635.482 byte), seek saat jeda 2:37→MediaSession157.042ms, progres sistem PLAYING4.089→6.101ms, X kontras terlihat dan menghapus notifikasi/sesi. **69/69 file awal identik SHA256**, jumlah tetap69. Tema dikembalikan Ikut sistem; font0,9 tidak berubah. Tidak ada TubeFlow pada buffer crash saat pemeriksaan.

Belum diuji: font1,3×/2× (WRITE_SETTINGS ditolak ColorOS), 360dp, landscape/tablet, TalkBack, Android12+/runtime16KB, gangguan SAF/storage penuh atau download jaringan baru dalam checkpoint ini. [Bukti visual dan laporan lengkap](PLAYER_POLISH_2026-10-15.md).
