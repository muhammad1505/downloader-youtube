# Pemutar, lampiran dan preview Status — 1.1.45

## Cakupan

Pengerjaan 26–27 Oktober 2026 dari baseline **1.1.44/code58 (`08a893b`)**.
Rilis pekerjaan ini **1.1.45/code60**. Perbaikan Bstation, Scrubber, data,
playlist dan konfigurasi pengelola dipertahankan. Room tetap **10**; tidak ada
migrasi, dependensi, izin atau layanan eksternal baru. AGENTS.md tidak diubah.

## Masalah dan solusi

| Bagian | Penyebab dan penerapan |
| --- | --- |
| Rasio video (BUG-089) | Pipeline efek GL Media3 mengatur letterbox sendiri sehingga `PlayerView.resizeMode` tidak cukup. Gunakan `Presentation` fit/stretch/crop dengan output dibatasi piksel layar. Jalur software memakai geometri viewport, termasuk rasio piksel/rotasi. Ukuran sumber dibaca dari `videoFormat` bila callback ukuran tidak tiba. |
| Perubahan visual saat jeda | Mengganti efek hanya menggambar frame berikutnya. Player disiapkan ulang pada posisi dan play intent yang sama saat dijeda; ketika berjalan, efek diperbarui langsung. Tidak mengaktifkan replay cache tambahan. |
| Kecerahan | Ikuti adaptif Android bila aktif. Jika nonaktif, gunakan sensor cahaya foreground dengan kurva lux terbatas, smoothing dan throttle. Tanpa sensor, ikuti sistem. Listener dilepas saat Stop/PiP/dispose; override jendela dipulihkan. Tidak menulis brightness global. |
| Info lampiran (BUG-091) | Label lama tidak memastikan file masih ada. Helper bersama memeriksa LRC/sidecar yang terbaca, pendamping dengan stem tepat di folder yang sama, dan track subtitle tertanam. Dipakai Riwayat, Koleksi, antrean audio/video; refresh saat Resume. Kegagalan izin dibedakan dari kosong. |
| Subtitle di player | Riwayat lama dapat kehilangan asosiasi sidecar meski file pendamping masih ada. Player memakai penemuan folder/stem yang sama, hanya untuk video aktif dan bila belum ada subtitle. Hasil async dibatasi versi player/URI/index; sesi yang sudah ditutup/diganti tidak dibuka ulang. Posisi dan status jeda dipertahankan. |
| Ketuk Riwayat (BUG-090) | `JobCard` sebelumnya hanya mempunyai ikon Putar. Kartu COMPLETED ber-URI valid kini memakai callback yang sama; ikon aksi tetap menang atas ketukan induk. |
| Preview Status | Dialog sebelumnya hanya memilih waktu. Media3 memutar sumber lokal pada rentang terpilih, dimulai jeda; perubahan titik awal menyiapkan frame baru. Tombol Compose dan Scrubber bersama mengontrol preview. Tidak membuat file preview; player dilepas saat tutup/ekspor. |
| Penghemat baterai | Pengaturan Pemutaran: Ikuti sistem/Aktif/Nonaktif. Tick video 1.000 ms vs 500 ms; audio tanpa lirik 500 ms vs 200 ms. Audio berlirik tetap 200 ms. Smart Enhance dilepas sementara tanpa menimpa preferensinya. Layar tetap menyala hanya ketika video berjalan. Download, EQ, kualitas file dan timestamp LRC tidak diubah. |
| Judul URI (BUG-088) | Fallback nama URI di-decode sekali; tanda `+` literal dan nama tampilan provider dipertahankan. |

## Arah tampilan

Mengikuti DESIGN.md: latar/panel dan aksen tema yang ada, teks lampiran
`labelSmall`, spasi 8/12/16 dp, target kontrol 48 dp. Preview menjadi fokus
utama dialog; info lampiran berada di bawah identitas file, memakai ikon dan
teks agar tidak bergantung pada warna. Tidak menambah dekorasi atau navigasi.

```text
[sampul] Judul / artis
         ukuran · durasi · platform
         Lirik tersedia / 1 subtitle tersedia
         aksi kartu

Potong Status
[preview rentang terpilih]
[putar] 00:00 [bilah posisi] 00:30
08:00 – 08:30 [bilah titik awal]
[Batal]                [Potong & bagikan]
```

## Verifikasi host dan artefak

Perintah pemeriksaan:

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon --max-workers=2
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/audit_native.py app/build/outputs/apk/release/app-arm64-v8a-release.apk
python3 scripts/audit_release_zip_fields.py app/build/outputs/apk/release/app-arm64-v8a-release.apk
```

**536 tes Kotlin** lulus (0 gagal/error/skip; 10 regresi baru) dan **98 tes
Python** lulus (42,934 detik). Setelah perubahan Kotlin terakhir, seluruh
perintah Gradle di atas lulus dalam **11m 13s**. Python dijalankan sebelum
koreksi Kotlin terakhir; engine/script Python tidak berubah.

Lint debug/release: **0 error, 3 warning lama** (`SelectedPhotoAccess`,
`AndroidGradlePluginVersion`, `GradleDependency`). Warning deprecation
Kotlin/Gradle terpisah tetap ada. APK ARM64 signed/minified/non-debuggable:
**76.357.958 byte**, bertambah **35.784 byte** dari baseline 1.1.44.
SHA256 **`64899c7b4e26e1d133026aa07ec7b768e1d815ee057f00ca28803d7880458c4c`**.
Audit **262 ELF/13 kelas ZIP**, signature produksi dan zipalign 16 KB lulus.
`adb install -r` berhasil; versi perangkat **1.1.45/code60**, hash `base.apk`
identik dengan artefak. APK debug/release dibangun; AAB tidak dibangun ulang.

Bukti privat berada di `build/reports/media-polish-1145/` (log final
`subtitle-verified-build.log`, `artifact.json`, `installed.json`); tangkapan
perangkat tidak dipublikasikan karena dapat memuat status bar atau data akun.

## Pemeriksaan perangkat

Perangkat **Realme RMX1821, Android 10/API29, ARM64, halaman 4 KB**.
Pemasangan memakai update `adb install -r`, tanpa uninstall/reset data.

- Pada kandidat yang sudah lulus build, Pas/Penuh/Zoom menghasilkan frame yang
  berbeda pada posisi **17:52** tanpa melanjutkan pemutaran. Jalur hardware
  dengan penghemat aktif dan jalur software sama-sama diperiksa. Dengan efek
  warna aktif, ketiga mode juga berubah saat jeda di **21:52**. Pemeriksaan
  gambar dilakukan selain perbandingan piksel.
- Ketukan judul/kartu Riwayat membuka player video langsung; tidak menekan
  ikon Putar. Judul file URI tidak lagi menampilkan `%20`.
- Koleksi menampilkan LRC yang tersedia serta lagu DJ tanpa LRC sebagai
  **Belum ada lirik**. Antrean video menampilkan **1 subtitle tersedia**.
  Antrean audio Sigar/Widodari menampilkan **Lirik tersedia**; lagu dijeda
  setelah pemeriksaan, tanpa mengubah playlist tersimpan.
- Preview diubah ke **8:00–8:30**, frame berubah, lalu diputar sampai berhenti
  pada **0:30**. Ekspor/bagikan tidak ditekan.
- Adaptif Android aktif (`screen_brightness_mode=1`, sensor sistem aktif).
  Cabang fallback sensor dan perpindahan kondisi ruangan belum diuji fisik.
- Penghemat diuji Aktif lalu dipulihkan **Ikuti sistem**; dekoder dipulihkan
  **Otomatis**, rasio **Penuh**. Preferensi EQ/enhance pengguna tidak direset.

Pada APK final SHA256 `64899c7b…458c4c`, video dibuka dari kartu Riwayat:
SRT ditemukan otomatis, kontrol menampilkan **Subtitle E01 - The Battle
Begins.srt** dan teks subtitle terlihat pada gambar. Tidak memilih subtitle
manual atau memindahkan file. Posisi dijeda di **7:12** sebelum cek rasio ulang.
Cek ulang Pas/Penuh/Zoom pada APK final mempertahankan **7:12**, tetap jeda,
dan track SRT tetap ada setelah ketiga kali persiapan ulang. Antrean tetap
menampilkan **1 subtitle tersedia**. Preview final dibuka dalam keadaan jeda
**0:00/0:30** dan ditutup lewat Batal; tidak mengekspor.

Daftar filesystem sebelum/sesudah tetap **15 file** dengan path yang sama;
tidak ada ekspor, rename, pemindahan atau penghapusan file selama pengujian.

## Koreksi dokumen dan BUG-087

DEVICE_TEST, PROGRESS, BUG_FINDINGS, VALIDATION dan PRODUCTION_SIGNING dibedakan
antara status terkini dan bukti historis. Heartbeat progres/skema 3 dalam
catatan 9 Oktober bukan kontrak implementasi saat ini. Login email Bstation
berhasil pada 1.1.44; Premium/login sosial tetap belum terbukti.

Laporan lama BUG-087 menyebut SRT di akar. Snapshot awal pekerjaan ini dan
query MediaStore terbaru justru menunjukkan MP4 dan SRT di subfolder season
yang sama. Waktu modifikasi keduanya berbeda **20.351 detik**; ini tidak
membuktikan satu publikasi yang sama atau penyebab perpindahan sebelumnya.
Worker terkini sudah menerima suffix bahasa dan memakai folder media.
**BUG-087 tetap terbuka untuk satu unduhan baru yang dicatat lengkap**, bukan
menuduh filter basename tanpa bukti. Tidak memindahkan/menghapus file lama.

## Pengamatan audio yang belum tertutup

Sesudah update dan rangkaian uji video, mini player sesi lama Sigar sempat
menampilkan galat audio. Memilih Sigar lagi dari Koleksi berhasil memutar
hingga **0:34**; setelah dijeda, dibuka video dan kembali ke audio, posisi
0:34 tetap jeda tanpa galat. Penyebab pesan awal belum terbukti; **BUG-092**
dicatat untuk reproduksi, bukan dianggap selesai karena retry berhasil.
Pada log PID aplikasi sesi final tidak ditemukan FATAL EXCEPTION, ANR,
PlaybackException, UnsupportedOperationException atau OutOfMemoryError;
ini tidak meniadakan pesan galat yang sempat tampil di UI.

## Batas verifikasi

Belum menjalankan ulang download jaringan audio/video penuh, pencarian LRCLIB
atau keselarasan vokal, ekspor Status, uji sensor fallback di ruangan berbeda,
rotasi pada build ini, matriks tema/font besar/TalkBack, fault injection,
Android/ABI lain, dan runtime halaman 16 KB. Tidak menyatakan semua bug selesai
atau semua gerbang produksi terbukti. Audit alignment bukan tes runtime 16 KB.
Konsumsi daya/persentase penghematan belum diukur. Impor SAF tanpa asosiasi
sidecar tidak bisa menelusuri sibling otomatis tanpa grant yang sesuai;
statusnya dapat **Lampiran belum dapat diperiksa**, bukan dianggap kosong.
Tidak membangun ulang AAB atau menerbitkan ke toko.

## File dan pemeliharaan

- Core baru: `MediaAttachments`, `PlaybackPolicy`, `PlaybackPower`, `VideoViewport`.
- UI baru: `AttachmentInfo`, `VideoBrightness`, `StatusClipPreview`.
- Integrasi: `VideoPlayerViewModel`, `PlayerViewModel`, `SettingsStore`,
  `MediaIntent`; layar Koleksi/player/Pengaturan/Status dan `components.kt`.
- Regresi: `MediaAttachmentsTest`, `PlaybackPolicyTest`, `VideoViewportTest`,
  tambahan `MediaIntentTest`; versi di `app/build.gradle.kts`, teks di `strings.xml`.
- Dokumen: laporan ini, DESIGN, README, DEVICE_TEST, PROGRESS, BUG_FINDINGS,
  VALIDATION, PRODUCTION_SIGNING. Tidak ada file sumber yang dihapus.

Jangan memakai `VideoFrameProcessor.REDRAW` tanpa replayable cache: kandidat
uji memicu error karena cache default nonaktif. Solusi final menyiapkan ulang
player saat jeda dan mempertahankan play intent; tidak menambah cache frame.
Jangan mengganti kembali hanya `PlayerView.resizeMode` pada jalur efek GL.

Rujukan implementasi:
[Presentation Media3](https://developer.android.com/reference/androidx/media3/effect/Presentation),
[interaksi efek dan resizeMode](https://github.com/androidx/media/issues/2842),
[syarat replayable cache](https://developer.android.com/reference/androidx/media3/effect/DefaultVideoFrameProcessor.Factory.Builder#setEnableReplayableCache(boolean)),
[sensor cahaya](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview),
[brightness jendela](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness).
