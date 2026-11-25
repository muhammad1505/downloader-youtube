# Auto-play headset, notifikasi saat menonton, dan pengecualian baterai — 1.1.81/code96

Laporan pengguna 16 November 2026 dari log aplikasi 1.1.80/code95 (Realme RMX1821, Android 10,
117 catatan). Tiga keluhan: auto-play saat headset tersambung kadang terlambat atau tidak jalan
(TWS dan speaker Bluetooth lancar), notifikasi melayang di atas film saat menonton di pemutar
video, dan pertanyaan apakah perlu menu "jangan batasi baterai". Versi dinaikkan ke
**1.1.81/code96** dengan lima sorotan changelog.

> **Diperbarui 1.1.84/code99.** Izin `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dan dialog
> pengecualian langsung dihapus karena dibatasi Google dan menjadi kandidat pemicu peringatan
> Play Protect. Panduan baterai kini hanya membuka Info aplikasi; lihat
> [PLAY_PROTECT](PLAY_PROTECT.md) dan [PROGRESS](PROGRESS.md). Bagian 4 di bawah adalah catatan
> historis 1.1.81, bukan perilaku aplikasi saat ini.

## 1. Bukti dari log

- `ERROR` berulang `ERROR_CODE_IO_UNSPECIFIED` pada 15 November 14:20 untuk
  `Download/SamehadakuDL/ao-ashi-s2/ao-ashi-s2-episode-6_MKV_480p.mkv` dan
  `.../ao-no-exorcist-yuki-no-hate-hen-episode-1_X265_480p.mkv`. Rantai penyebab di tumpukan:
  `IllegalArgumentException: Failed to determine if primary:… is child of primary:Download/SamehadakuDL`
  yang dibungkus `java.io.FileNotFoundException` dari `ContentProviderProxy.openTypedAssetFile`.
  Artinya baris katalog masih ada, berkasnya sudah tidak ada; pemutar mencoba tiga kali lalu
  berhenti dengan pesan umum "Tidak dapat memutar video (ERROR_CODE_IO_UNSPECIFIED)".
- Tidak ada satu pun baris `TubeFlow-autoload` pada log yang diekspor. Jalur auto-play sebelumnya
  hanya menulis `android.util.Log`, yang tidak ikut berkas log pengguna, sehingga laporan tidak
  dapat menunjukkan apakah broadcast headset sampai atau permintaan dibuang.

## 2. Akar masalah auto-play yang hilang

`PlayerViewModel.resumeForConnectedDevice()` diawali `if (_state.value.playing || _state.value.loading) return`.
Pada proses baru, `init` menjalankan `restorePlaybackSession()` yang memuat sesi tersimpan dalam
keadaan **jeda**; selama pemuatan itu `state.loading = true`. Bila sambungan headset tiba tepat
pada jendela tersebut — justru kasus paling umum setelah aplikasi ditutup lalu headset dipasang —
permintaan auto-play dibuang **tanpa percobaan ulang**, lalu `PlaybackService` menutup foreground
di `finally` karena `track` masih null. Hasilnya sama dengan "tidak auto-play" atau musik baru
berbunyi jauh kemudian saat pengguna membuka pemutar.

Perbaikan di 1.1.81:

- `resumeForConnectedDevice()` menunggu `loading` selesai maksimal 3 detik (`RESTORE_SETTLE_MS`,
  poll `RESTORE_POLL_MS` = 100 ms) sebelum merencanakan antrean. Bila pemutaran ternyata sudah
  berjalan, permintaan dilewati dan alasannya dicatat.
- `PlaybackService` tidak menutup foreground/`stopSelf()` selama `state.loading` masih benar,
  sehingga notifikasi dan wake lock tidak hilang di tengah pemuatan track.
- `AudioDeviceCallback`, receiver runtime `ACTION_HEADSET_PLUG`/`BluetoothA2dp`, dan
  `HeadsetAutoPlayReceiver` manifest menulis `AppLog` kategori Pemutar; perencanaan antrean
  mencatat jumlah lagu, posisi mulai, koleksi kosong, atau alasan dilewati.

### Mengapa TWS lebih lancar daripada headset kabel

Jalur Bluetooth memakai dua pemicu: `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` di manifest
(dikecualikan dari pembatasan implicit broadcast Android 8+, sehingga proses mati dapat
dibangunkan) dan `AudioDeviceCallback` saat proses hidup. `ACTION_HEADSET_PLUG` untuk headset
kabel hanya diterima receiver runtime, jadi bila proses sudah dihentikan sistem/OEM, kabel tidak
membangunkan apa pun sampai aplikasi dibuka. Batas platform ini tidak berubah pada 1.1.81; yang
berubah adalah permintaan yang sudah masuk tidak lagi dibuang. Pengecualian baterai (bagian 4)
mengurangi frekuensi proses dihentikan, bukan menghapus batas broadcast tersebut.

## 3. Notifikasi di atas film

Aplikasi tidak dapat menahan heads-up aplikasi lain tanpa izin kebijakan notifikasi Android.
`WatchAlerts` (baru) menyimpan `currentInterruptionFilter` satu kali, memasang
`INTERRUPTION_FILTER_PRIORITY` selama video penuh diputar, lalu mengembalikan filter sebelumnya
saat video dijeda, masuk PiP, atau pemutar ditutup. `MainActivity` memanggil
`restoreLeftover()` saat dibuka untuk membersihkan filter yang tertinggal bila proses berhenti
saat menonton.

- Pilihan `Pengaturan → Pemutaran → "Sembunyikan notifikasi saat menonton"` (`hide_alerts_watching`)
  default **mati**. Menyalakannya saat izin belum ada akan membuka halaman akses Jangan Ganggu.
- Notifikasi tetap tercatat di panel dan riwayat sistem; hanya bunyi dan tampilan melayang yang
  ditahan. Tidak ada notifikasi aplikasi lain yang dibaca atau dibatalkan.
- Modus prioritas tetap meloloskan alarm dan panggilan dari kontak prioritas sesuai setelan
  Jangan Ganggu pengguna.

## 4. Menu "jangan batasi baterai"

`BatteryExemption` (baru) membaca `PowerManager.isIgnoringBatteryOptimizations` dan membuka
`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` untuk paket ini, dengan fallback ke daftar
pengaturan baterai lalu detail aplikasi. Izin manifest
`android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` ditambahkan. Panel di
`Pengaturan → Pemutaran` menampilkan status dan membacanya ulang pada `ON_RESUME`; tidak ada
setelan lain (selalu aktif, autostart OEM, dsb.) yang diubah aplikasi.

## 5. Galat video untuk berkas yang sudah hilang

`VideoPlayerViewModel.playbackError` kini menelusuri rantai `cause` sebelum memetakan kode galat.
Bila ditemukan `FileNotFoundException`, pesan menjadi "File video tidak ditemukan. Mungkin sudah
dipindah atau dihapus." alih-alih galat IO generik. Baris katalog yang rusak tidak dihapus
otomatis; pengguna tetap dapat memindai ulang atau menghapusnya dari Koleksi.

## 6. Berkas yang berubah

| Area | Berkas |
| --- | --- |
| Auto-play & log | `core/PlayerViewModel.kt`, `core/PlaybackService.kt`, `core/HeadsetAutoPlayReceiver.kt` |
| Notifikasi saat menonton | `core/WatchAlerts.kt` (baru), `ui/VideoPlayerScreen.kt`, `core/SettingsStore.kt`, `MainActivity.kt` |
| Pengecualian baterai | `core/BatteryExemption.kt` (baru), `ui/SettingsScreen.kt`, `AndroidManifest.xml` |
| Galat video | `core/VideoPlayerViewModel.kt` |
| Teks & changelog | `res/values/strings.xml`, `assets/changelog.json` |

## 7. Verifikasi

- **CI GitHub "Android tests" lulus** untuk commit kode `7f793c5`: unit test Kotlin (termasuk
  `AppChangelogTest`), `lintRelease`, verifikasi manifes engine, dan tes host Python. Hasil yang
  sama muncul di ringkasan run `43825287298`.
- **Host lokal:** `python3 -m unittest discover -s scripts -p "test_*.py"` menjalankan 103 tes;
  satu error hanya karena `ffmpeg` tidak terpasang di host (tidak terkait perubahan ini), dan tes
  manifest `test_playback_receivers.py` lulus.
- **Build lokal belum dijalankan.** `app/libs/ffmpeg-0.18.1-webp16k.aar` belum dibangun di host
  ini dan NDK 27.1/CMake 3.22.1 tidak terpasang, jadi `compileDebugKotlin`, APK rilis, dan audit
  native 1.1.81 belum ada. Percobaan kompilasi dengan AAR upstream hanya untuk typecheck
  dibatalkan sesuai arahan pengguna dan berkas sementaranya dihapus sebelum commit.

## 8. Batas cakupan

- Belum ada uji perangkat untuk 1.1.81: perilaku Jangan Ganggu (termasuk pemulihan filter setelah
  proses mati), auto-play dengan headset kabel/TWS fisik, dan skenario proses dihentikan OEM belum
  diukur. `DEVICE_TEST.md` dan `DEVICE_TEST_MATRIX.md` belum diperbarui untuk versi ini.
- Auto-play headset kabel saat proses mati tetap terbatas pengiriman `ACTION_HEADSET_PLUG` oleh
  Android/OEM; pengecualian baterai hanya mengurangi kemungkinan proses dihentikan.
- Pemetaan galat berkas hilang hanya diuji lewat pembacaan rantai `cause` pada kode; belum ada
  reproduksi perangkat pada 1.1.81.

## 9. Perbaikan susulan setelah 1.1.81 terpasang

Dua laporan pengguna pada APK rilis 1.1.81:

1. **TubeFlow tidak ada di daftar akses Jangan Ganggu.** Manifest belum mendeklarasikan
   `android.permission.ACCESS_NOTIFICATION_POLICY`. Tanpa izin itu Android tidak menampilkan
   aplikasi di daftar akses Jangan Ganggu dan `isNotificationPolicyAccessGranted` selalu false,
   sehingga opsi tahan notifikasi tidak pernah bisa aktif. Perbaikan: izin ditambahkan beserta
   komentar alasannya, status izin di Pengaturan dibaca ulang pada `ON_RESUME`, dan
   `scripts/test_playback_receivers.py` memeriksa izin Jangan Ganggu serta baterai.
2. **Headset kabel tetap tidak auto-play.** Selain batas platform (proses mati tidak menerima
   `ACTION_HEADSET_PLUG`), perencanaan antrean masih punya satu jalur keluar tanpa percobaan
   ulang: pemeriksaan `loading` di dalam `withContext(engineDispatcher)`. Bila pemulihan sesi baru
   dimulai setelah batas tunggu pertama, permintaan hilang lagi. Perbaikan: perencanaan dipindah
   ke `tryResumeForConnectedDevice()` yang mengembalikan `false` selama sesi masih dipulihkan,
   diulang tiap 100 ms sampai 3 detik, dengan koleksi dibaca sekali di awal. Setiap broadcast
   sambungan kini dicatat dengan `action`, `state` plug/profil, dan `sticky`, dan `PlaybackService`
   mencatat permintaan yang diterima maupun yang diabaikan karena permintaan sebelumnya masih
   berjalan. Log itu yang membedakan proses mati (broadcast tidak sampai) dari permintaan yang
   sampai tetapi sesi masih dipulihkan.

Kedua perbaikan ini belum masuk ke APK 1.1.81 yang sudah dipublikasikan; keduanya perlu rilis
berikutnya (mis. 1.1.82) agar sampai ke perangkat.
