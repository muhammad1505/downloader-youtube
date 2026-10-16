# Lanjut otomatis setelah interupsi audio — 1.1.14 (28)

## BUG-055: akar dan bukti awal

Pengelola melaporkan musik TubeFlow terjeda saat video Facebook dimulai, tetapi tidak lanjut setelah video dijeda.

- Log audio HP pada1.1.13 menunjukkan Facebook meminta `AUDIOFOCUS_GAIN` permanen (`req=1`), disusul TubeFlow melepas fokus. Sesudah Facebook melepas fokus, sesi TubeFlow tetap `STATE_PAUSED`.
- `PlayerViewModel.focusRequest` menangani `AUDIOFOCUS_LOSS` dengan `pauseInternal(true)`: niat melanjutkan dibatalkan dan request dilepas. Untuk loss permanen, Android memang tidak mengirim GAIN kembali; menunggu callback itu saja tidak menyelesaikan kebutuhan pengguna. [Panduan Android](https://developer.android.com/media/optimize/audio-focus#java).
- Pada loss sementara berulang, `transientResume = state.playing` menimpa penanda menjadi false karena callback pertama sudah menjeda musik.
- MediaSession mengabaikan perintah Pause jika sesi sudah paused. Dengan penantian lanjut otomatis, Pause eksplisit tetap harus membatalkan niat melanjutkan.

Probe terkontrol di HP memakai proses shell Java/Dex sementara: memutar media senyap dan meminta fokus permanen selama6detik, kemudian menghentikan media/melepas fokus. Pada1.1.13 TubeFlow berubah PLAYING→PAUSED dan tetap PAUSED sesudah pelepasan. Probe tidak memasang aplikasi tambahan atau mengubah file musik.

## Implementasi

- `PlaybackInterruption.kt`: niat melanjutkan untuk track aktif di RAM, dipertahankan saat loss berulang. Loss sementara menunggu GAIN. Loss permanen hanya boleh mencoba kembali setelah aktivitas audio lain teramati lalu berhenti.
- `PlayerViewModel.kt`: [AudioPlaybackCallback](https://developer.android.com/reference/android/media/AudioManager.AudioPlaybackCallback) publik pada Looper audio yang sama, tanpa izin baru atau pemutar kedua. Baca status aktif terbaru, beri 250 ms untuk penyelesaian pause sendiri dan 750 ms debounce ketika audio lain sunyi. Selama track menunggu pemulihan dari loss permanen, pemeriksaan status ringan setiap 1 detik melengkapi callback; hentikan saat niat lanjut dibatalkan atau dikonsumsi. Sebelum satu request fokus, periksa ulang token track, prepared/playing, tidak ada audio aktif, serta mode audio normal. Jika request ditolak, tidak merebut fokus lewat loop retry. Status audio tidak dapat dibaca berarti tetap paused.
- Jeda manual, Stop/X, ganti track/release, error dan `AUDIO_BECOMING_NOISY` membatalkan niat serta callback tertunda. Callback lama tidak menghidupkan track baru. Play eksplisit memakai jalur fokus yang sudah ada.
- `PlaybackService.kt`: Pause eksplisit selalu diteruskan ke engine, termasuk ketika sesi sudah paused karena interupsi.
- Versi1.1.14/code28; User-Agent mengikuti versi. Room9, sumber audio, antrean/fitur headset, dependensi dan manifest dipertahankan.

Ini perluasan perilaku atas permintaan pengelola. Penanganan Android standar untuk loss permanen menunggu aksi Play pengguna; TubeFlow menambahkan satu pengamatan berhentinya media dan permintaan fokus baru, sambil mempertahankan otoritas jeda manual. Tidak memantau layar, paket/isi Facebook, atau merekam suara.

## Hasil host dan build final

- Kandidat awal 1.1.14 yang hanya mengandalkan callback masih gagal pada probe media native di HP: PLAYING (16.069 ms) → PAUSED (16.676 ms) → tetap PAUSED setelah pelepasan. Ini kegagalan nyata meskipun tes host/build sudah lulus. Pemeriksaan status berkala ditambahkan agar berhentinya audio native tidak bergantung pada callback Java. Kandidat awal tersebut bukan artefak final.
- 31 tes terarah playback lulus, termasuk 8 regresi kebijakan baru: loss sementara berulang, transisi ke permanen, menunggu aktivitas nyata, pemutaran lain mulai lagi saat debounce, sekali lanjut, pembatalan niat lanjut, lagu yang sudah paused, dan mode komunikasi. Ini uji kebijakan, bukan pengujian fisik seluruh jalur tombol/headset.
- Suite lengkap **279 Kotlin lulus**, **84 Python lulus** (35,757 detik). Perubahan pemeriksaan status sesudah kandidat awal kembali lulus seluruh 279 tes Kotlin; skrip Python tidak berubah.
- `bash scripts/build_release.sh --no-daemon --max-workers=2`: **BUILD SUCCESSFUL, 8 menit 55 detik**, termasuk unit, lint debug/release, R8, signed APK tiga ABI dan AAB. `./gradlew assembleDebug --no-daemon --max-workers=2`: **BUILD SUCCESSFUL, 58 detik**.
- Lint debug dan release masing-masing **0 error, 2 warning** yang sudah diketahui: `SelectedPhotoAccess` serta `GradleDependency` untuk ONNX yang sengaja di-pin. Ini tidak menghapus peringatan deprecation Gradle/API yang terpisah dari lint.
- Ketiga APK lulus verifikasi sertifikat produksi/signature, zipalign 16 KB, audit native ELF, refleksi ZIP, checksum yt-dlp dan kesamaan bootstrap. AAB lulus jarsigner dan audit native; bundletool tidak dijalankan ulang pada artefak ini.
- Bukti privat berada di `build/reports/focus-1114/`. Probe hanya alat QA, tidak dibundel dalam APK.

## Hasil HP fisik

Realme RMX1821, Android 10/API 29, ARM64, RAM 4 GB, halaman memori 4 KB. Update `adb install -r` berhasil; APK yang ditarik dari HP cocok dengan SHA256 ARM64 final di bawah. Tidak uninstall/reset. Sebanyak **58 file media** dari baseline sebelum pembaruan mempertahankan SHA256 yang sama. Buffer log proses TubeFlow yang diperiksa tidak memuat `FATAL EXCEPTION`.

Probe dijalankan dengan Activity di Home, memakai media native senyap selama 6 detik. Angka posisi berasal dari MediaSession, bukan penilaian auditori atau perkiraan UI.

| Skenario final | Hasil aktual |
|---|---|
| Fokus permanen, media berhenti lalu fokus dilepas | PLAYING → PAUSED → PLAYING; posisi 27.100 → 28.713 → 30.105 ms, tanpa reset |
| Fokus sementara lalu dilepas | PLAYING → PAUSED → PLAYING |
| Fokus sementara diikuti duck berulang | PLAYING → PAUSED → PLAYING |
| Fokus sementara berubah menjadi permanen | PLAYING → PAUSED → PLAYING |
| Pause MediaSession eksplisit ketika sudah terinterupsi | Tetap PAUSED sesudah interupsi berakhir; posisi tetap 44.204 ms |
| Lagu sudah dijeda manual sebelum media lain dimulai | Tetap PAUSED |
| Video Facebook asli: musik TubeFlow → putar video → jeda video | Pengelola mengonfirmasi “Ya, otomatis lanjut” setelah final terpasang |

Keenam skenario lulus pada APK release yang di-minify. Pemutar ditinggalkan dalam keadaan jeda. Pengelola juga mengonfirmasi urutan Facebook asli pada HP yang sama: musik otomatis lanjut. Konfirmasi ini merupakan laporan pengguna, terpisah dari enam pengamatan MediaSession terkontrol; belum menyatakan semua versi Facebook/OEM telah diuji.

## Artefak final

Lokasi APK: `app/build/outputs/apk/release/`; AAB: `app/build/outputs/bundle/release/`. Tidak menambah salinan distribusi atau melakukan publikasi toko.

| Artefak | Byte | SHA256 |
|---|---:|---|
| ARM64 APK, terpasang/diuji | 70580383 | `0aef3db354514119812ea4e0119299f708d1a71f7aee3b06d0a113d24751e99d` |
| ARMv7 APK | 62270841 | `fac4ab9ad52ad8af0ad0c67af55bbeaf69691d348d1fbbef2018ac81ce976ff1` |
| x86_64 APK | 75021192 | `3d1563eefde2ea98d400b7384f2ee7dcc1198ef69eab43ff5859290a53079654` |
| AAB | 255611820 | `39699ad063f9aa2596ebae76a557bc98550113f1223729fb0ed570c6dd0e0dd1` |

APK ARM64 bertambah 1.464 byte dibanding 1.1.13. Tidak ada migrasi database, izin, engine pemutar, atau dependensi baru. Tidak mengubah isi audio/LRC, setelan DNS, atau keamanan perangkat.

## Batas dan tes lanjutan

Tidak ada jaminan bila aplikasi lain terus melaporkan audio aktif setelah tombol jedanya ditekan. Pemeriksaan setiap detik berjalan pada Handler audio, bukan alarm yang membangunkan perangkat; pemulihan saat deep sleep belum dibuktikan. Media yang belum teramati aktif tidak memicu perebutan fokus. Niat lanjut tidak dipersistenkan melewati proses mati. Mode komunikasi atau status audio tidak diketahui menghalangi request otomatis. Android15+ memerlukan aplikasi di depan atau foreground service untuk meminta fokus; gunakan PlaybackService yang sudah ada.

Yang belum diuji ulang pada sesi ini: panggilan nyata, TWS/cabut-pasang kabel, proses mati/deep sleep, Android 15+, perangkat dengan halaman memori 16 KB, serta download audio/video baru. Downloader dan LRCLIB tidak diubah selain User-Agent versi; kelulusan host bukan bukti runtime semua alur tersebut.
