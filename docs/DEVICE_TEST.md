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
