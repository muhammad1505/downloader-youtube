# Progres nyata dan kompresi sampul — 10 Oktober 2026

## Perubahan

- **BUG-017 / OPT-006:** heartbeat 90–98% dihapus. `DownloadProgress` membaca template terstruktur yt-dlp: byte terunduh, ukuran aktual/estimasi, kecepatan, ETA, format/track, dan postprocessor. Persen dihitung per track; berganti video→audio berarti tahap baru dengan persen sendiri. Ukuran yang belum diketahui memakai indikator tak tentu; estimasi yang direvisi boleh mengoreksi persen.
- Tahap persisten: menyiapkan, mencari lirik, mengunduh audio/video, memproses, memproses sampul, menyimpan, menyelesaikan penyimpanan. UI dan notifikasi memakai tahap yang sama. Kecepatan/ETA hanya ditampilkan selama transfer jaringan. Persen penyimpanan dihitung dari byte salinan.
- `dao.finish` dipindahkan setelah publikasi sidecar dan pencatatan hasil lirik. Selesai berarti proses publikasi sudah berakhir; kegagalan lirik tetap dilaporkan terpisah dari hasil audio.
- **Room 3→4:** kolom `phase` dengan default `PREPARING`; migrasi mempertahankan riwayat dan URI. Generasi eksekusi tetap melindungi progres worker lama.
- **OPT-009:** sampul JPEG, sisi terpanjang maksimal 800 piksel, kualitas FFmpeg `-q:v 5`, rasio dipertahankan, gambar kecil tidak diperbesar. Berlaku sebelum penyematan dan penyimpanan sampul terpisah, termasuk JPEG sumber. Tidak mengubah berkas hasil lama. Ukuran byte bergantung isi gambar.
- Bootstrap memperluas postprocessor thumbnail pada yt-dlp yang dipin; ZIP resmi dan checksum engine tidak diubah. Konversi memakai berkas sementara, lalu penggantian atomik setelah berhasil. Kegagalan tidak menimpa JPEG sumber. Hook progres postprocessor tetap dijalankan.

## Titik kode untuk pengembang berikutnya

`core/DownloadProgress.kt`: template dan parser; `Engine.download`: opsi progres/thumbnail; `DownloadWorker.executeDownload`: tahap, salinan, finalisasi; `ui/components.kt`: indikator dan teks; `Models.kt`: migrasi dan DAO. Kompresi: `scripts/engine_bootstrap.py` dan salinannya `app/src/main/assets/engine-bootstrap.py` harus identik.

## Pemeriksaan host

```sh
./gradlew -PuseGoogleMirror=true -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs='-Xmx2g -Dfile.encoding=UTF-8' --max-workers=2 \
  testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon
python3 -m unittest discover -s scripts -p 'test_*.py'
```

49 tes Kotlin dan 32 tes Python lulus pada pemeriksaan akhir. Regresi meliputi parser byte/estimasi/angka invalid/track/stage, progres setelah terminal state, migrasi 3→4 dengan retensi riwayat, serta kompresi memakai FFmpeg host. Pipeline video dua track membuktikan progres video/audio terpisah dan hook penggabungan. Pipeline bootstrap + yt-dlp resmi memproduksi MP3 dengan sampul JPEG 800×450; paket audio identik sebelum/sesudah penyematan. Lint debug/release: **0 error, 0 warning, 0 informasi**. Build debug/release ketiga ABI berhasil; pemeriksaan akhir selesai dalam 9m37s. Audit release: 255/255/257 ELF, tanpa kegagalan alignment 64-bit; audit ZIP: 13 kelas reflektif per ABI, tanpa kegagalan. Dua jenis deprecation internal AGP dari batch sebelumnya tetap terpisah dari lint.

APK release ARM64 memakai kunci testing lokal: `app/build/outputs/apk/rc/TubeFlow-arm64-v8a-testing.apk` (62.819.030 byte), SHA256 `2dc813c9b3bad4611550e553372d3c5f031d4cd37af984ca631766f1c644a17d`. Tanda tangan dan zipalign diverifikasi. Bukti host lokal: `/tmp/tubeflow-progress-cover-final-build.log`, `/tmp/tubeflow-progress-cover-python.log`, laporan lint dan JUnit di `app/build/`.

## Uji perangkat — berhasil

Realme RMX1821, Android 10 (API 29), ARM64, halaman memori 4 KB; **tanpa emulator**. Sambungan USB sempat terputus sebelum pemasangan, kemudian pengguna menyambungkan ulang. Verifier menolak instalasi ADB; pembaruan dilakukan lewat Files/installer bawaan dan pemindaian Play Protect yang sudah diizinkan. Installer menampilkan “Aplikasi terinstal”; APK terpasang ditarik kembali dan hash-nya cocok dengan artefak final di atas. Verifier tidak dinonaktifkan.

| Pemeriksaan | Hasil |
| --- | --- |
| Migrasi Room 3→4 | Satu entri Selesai lama tetap tampil setelah update; file MP3 lama tetap ada dengan ukuran semula. |
| MP3 128 kbps + sampul | Sukses di folder SAF; durasi 634,608 detik, codec MP3 128.000 bps, JPEG/MJPEG `attached_pic=1`. |
| Sampul sebelum → sesudah | PNG 1278×720 / **1.597.019 byte** → JPEG 800×451 / **46.665 byte**: **97,08% lebih kecil**. |
| Ukuran MP3 sebelum → sesudah | **11.752.094 → 10.201.741 byte**, berkurang 1.550.353 byte / 13,19%. |
| Kesamaan audio | Seluruh **26.442 paket MP3 identik** berdasarkan SHA256 setiap paket; perubahan sampul tidak mengubah audio hasil. |
| Pemutaran | Poweramp menampilkan sampul, 48 kHz / 128 kbps / MP3; timer maju 0:01→0:15. Playback dijeda sesudah pemeriksaan. |
| Konversi audio | UI “Memproses audio/video” dengan indikator tak tentu, tanpa persen/kecepatan/ETA; antrean kosong setelah selesai sekitar 95 detik. |
| Video MP4 360p | Sukses; H.264 640×360 + AAC, durasi 634,62458 detik, 28.865.093 byte. |
| Progres video dua track | UI video **43% / 2,00 MiB/s / sisa 4 dtk → 77% / 2,67 MiB/s / sisa 1 dtk**, lalu audio **40% / 2,52 MiB/s / sisa 2 dtk**, pemrosesan tanpa angka transfer, selesai sekitar 29 detik. |
| Hasil akhir | Riwayat 3 entri selesai (1 lama + 2 baru), antrean kosong; logcat PID aplikasi tidak memuat fatal exception. |

File uji baru dipertahankan di `Download/TubeFlow/Denny-Caknan/`: audio memakai akhiran ` (1).mp3` agar file lama tetap utuh, video memakai `.mp4`. Tidak menghapus data aplikasi atau riwayat lama. Kebijakan JPEG berlaku untuk unduhan berikutnya; sampul berkas lama tidak dikonversi otomatis.

Bukti UI tersimpan di repositori: [transfer video](images/progress-video-2026-10-10.png), [transfer audio](images/progress-audio-2026-10-10.png), [pemrosesan](images/progress-processing-2026-10-10.png), [Poweramp](images/cover-poweramp-2026-10-10.png). Bukti lokal lain: `/tmp/tubeflow-video-device-samples.json`, `/tmp/tubeflow-audio-progress-device-samples.json`, `/tmp/tubeflow-cover-device-probe.json`, `/tmp/tubeflow-progress-device-video-probe.json`, `/tmp/tubeflow-progress-final-history.xml`. Sampul pembanding: `/tmp/tubeflow-before-cover.png` dan `/tmp/tubeflow-after-cover.jpg`.

Persen salinan/finalisasi berlangsung singkat dan tidak tertangkap snapshot perangkat; perhitungan serta transisi DAO diverifikasi host. Notifikasi memakai nama tahap dari helper yang sama, tetapi tidak direkam per frame pada sesi ini.

## Batas cakupan

Perubahan ini tidak menutup temuan lirik/publikasi/cleanup lain dari audit sebelumnya. Nilai persen bukan estimasi waktu keseluruhan pekerjaan. Pemrosesan tanpa progres terukur dapat berlangsung lama, tetapi indikator tahap tetap aktif. Uji Android hanya mencakup perangkat di atas, MP3 dengan sampul dan MP4 dua track. M4A, Android 17, perangkat 16 KB, pencarian lirik, kegagalan provider/ruang penuh, dan race jeda/lanjut cepat belum diuji dalam batch ini.

## Koreksi hasil uji duplikat

Laporan pengguna setelah pengujian mengungkap BUG-019: file pembanding sengaja diunduh lewat `youtu.be`, sementara file lama berasal dari `watch?v=`. Pemeriksaan URL mentah membiarkan keduanya, walau ID video dan opsi keluaran identik. Keberhasilan pengujian progres/kompresi tetap berlaku, tetapi **tidak membuktikan deteksi duplikat bekerja**. Perbaikan identitas sumber dan regresinya tercatat di [BUG_FINDINGS.md](BUG_FINDINGS.md).
