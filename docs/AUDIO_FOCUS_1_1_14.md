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
- `PlayerViewModel.kt`: [AudioPlaybackCallback](https://developer.android.com/reference/android/media/AudioManager.AudioPlaybackCallback) publik pada Looper audio yang sama, tanpa izin baru atau pemutar kedua. Baca status aktif terbaru, beri250ms untuk penyelesaian pause sendiri dan750ms debounce ketika audio lain sunyi. Sebelum satu request fokus, periksa ulang token track, prepared/playing, tidak ada audio aktif, serta mode audio normal. Jika request ditolak, tidak merebut fokus lewat loop retry. Status audio tidak dapat dibaca berarti tetap paused.
- Jeda manual, Stop/X, ganti track/release, error dan `AUDIO_BECOMING_NOISY` membatalkan niat serta callback tertunda. Callback lama tidak menghidupkan track baru. Play eksplisit memakai jalur fokus yang sudah ada.
- `PlaybackService.kt`: Pause eksplisit selalu diteruskan ke engine, termasuk ketika sesi sudah paused karena interupsi.
- Versi1.1.14/code28; User-Agent mengikuti versi. Room9, sumber audio, antrean/fitur headset, dependensi dan manifest dipertahankan.

Ini perluasan perilaku atas permintaan pengelola. Penanganan Android standar untuk loss permanen menunggu aksi Play pengguna; TubeFlow menambahkan satu pengamatan berhentinya media dan permintaan fokus baru, sambil mempertahankan otoritas jeda manual. Tidak memantau layar, paket/isi Facebook, atau merekam suara.

## Checkpoint verifikasi

- 31 tes terarah playback lulus, termasuk 8 regresi kebijakan baru: loss sementara berulang, transisi ke permanen, menunggu aktivitas nyata, pemutaran lain mulai lagi saat debounce, sekali lanjut, pembatalan niat lanjut, lagu yang sudah paused, dan mode komunikasi. Ini uji kebijakan, bukan pengujian fisik seluruh jalur tombol/headset.
- Suite lengkap **279Kotlin lulus**, **84Python lulus** (35,757detik). Hasil build release dan pengujian APK final ditambahkan setelah selesai.
- Bukti privat berada di `build/reports/focus-1114/`. Probe hanya alat QA, tidak dibundel dalam APK.

## Batas dan tes lanjutan

Tidak ada jaminan bila aplikasi lain terus melaporkan audio aktif setelah tombol jedanya ditekan, atau OEM tidak mengirim callback. Media yang belum teramati aktif tidak memicu perebutan fokus. Niat lanjut tidak dipersistenkan melewati proses mati. Mode komunikasi atau status audio tidak diketahui menghalangi request otomatis. Android15+ memerlukan aplikasi di depan atau foreground service untuk meminta fokus; gunakan PlaybackService yang sudah ada.

Verifikasi kandidat final: permanen→lepas, loss sementara, transisi sementara→permanen, Pause eksplisit selama interupsi, serta urutan Facebook asli. Pengujian terkontrol bukan bukti semua versi Facebook/OEM memiliki lifecycle audio yang sama. Jangan menyatakan panggilan/TWS/perangkat16KB sudah diuji jika belum dijalankan.
