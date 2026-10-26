# Kecerahan dan suara video 1.1.46

## Permintaan dan audit

27 Oktober 2026, baseline **1.1.45/code60 (`b3957f1`)**, Room 10/Media3 1.11.1.
Pengelola meminta Otomatis/Manual untuk kecerahan dan melaporkan video kecil
suaranya pada speaker/headset, termasuk pada volume sistem 50%. Musik normal.
Menurut pengelola, video yang sama lebih keras di pemutar lain; mematikan EQ
serta efek video TubeFlow tidak banyak membantu.

Auto sebelumnya sudah diuji mengikuti adaptif Android; perubahan kondisi
ruangan melalui sensor fallback belum diuji langsung. Tidak mengklaim semua
cabang otomatis sudah terbukti.

Sampel AAC stereo 48 kHz dari episode lokal diperiksa di host, tanpa mengubah
file HP. Rentang3:00–4:00: rata-rata −32,5 dBFS/puncak −11,3 dBFS;
7:00–8:00: rata-rata −27,6 dBFS/puncak −7,2 dBFS. Sumber ini cukup pelan, tetapi
angka tersebut belum membuktikan penyebab perbedaan dengan pemutar lain.
Snapshot Android tidak menunjukkan player yang sedang di-duck; level volume
saat snapshot berbeda dari kondisi50% yang dilaporkan pengguna. Tidak memakai
snapshot itu untuk menyalahkan level volume pengguna.

## Implementasi

- **Kecerahan:** Opsi pemutar → Tampilan menyediakan Otomatis/Manual. Manual
  memakai Scrubber bersama, dibatasi 4–100% agar tidak membuat layar hitam.
  Pertama kali manual memakai override jendela/level sistem saat itu; pilihan
  berikutnya memakai nilai manual tersimpan. Geser sisi kiri beralih ke manual.
- Mode/nilai dipersisten pada preferensi video. Override hanya pada jendela
  player dan sheet; dipulihkan saat keluar, Stop atau PiP. Sensor hanya dipakai
  pada mode otomatis bila adaptif Android nonaktif. Tidak menulis setting
  brightness global atau meminta WRITE_SETTINGS.
- **Suara:** Opsi pemutar → Equalizer & efek audio menyediakan Normal/+3/+6/+9 dB.
  Default Normal mempertahankan level asli. Penguat hanya memproses PCM video,
  memakai knee lembut mulai 0,9 fullscale agar puncak tidak overflow/berbalik tanda.
  Tidak mengubah file, bitrate, tag, volume sistem, EQ musik atau timestamp.
- Tidak bergantung pada LoudnessEnhancer vendor; layanan efek HP uji tidak
  melaporkannya. Gunakan AudioProcessor Media3 yang sudah menjadi dependensi.
  Gain dihitung sekali per sesi dan sampel diproses pada thread audio.
- Sesi dengan boost mematikan passthrough/offload agar PCM benar-benar diproses;
  decoder perangkat/software tetap tersedia. Normal memakai AudioSink semula.
  Format yang tidak didukung dibypass dan diberi status, bukan dibaca sebagai
  PCM16. Status dukungan async dijaga versi player agar sesi lama diabaikan.
- Mengganti gain menyiapkan ulang player dengan posisi dan play intent yang
  sama. Subtitle/antrean dipertahankan. Preview Status memakai Normal.

Perubahan memakai komponen/tema yang ada; rencana dan alasan kontrol ada di
DESIGN.md. Tidak menambah dependensi, izin, migrasi atau model. Room tetap 10.

## File terdampak

Core: `VideoBrightnessPolicy.kt`, `VideoVolumePolicy.kt`,
`VideoVolumeProcessor.kt`, `VideoPlayerViewModel.kt`, `VideoSubtitleRenderers.kt`.
UI: `VideoBrightness.kt`, `VideoPlayerScreen.kt`, `strings.xml`.
Regresi: `VideoBrightnessPolicyTest.kt`, `VideoVolumeProcessorTest.kt`.
Versi: `app/build.gradle.kts` menjadi 1.1.46/code61. Tidak mengubah
`VideoAudioFx` atau engine musik; AGENTS.md tetap.

## Verifikasi

543 tes Kotlin lulus (7 regresi baru, 0 gagal/error/skip); 98 tes Python lulus
dalam 44,561 detik. Lint ulang debug/release 0 error/3 warning lama. Kode memakai
LocalActivity dan fungsi composable pengembali nilai mengikuti aturan penamaan.
Tes PCM memakai API flush(StreamMetadata.DEFAULT).

Pengujian tambahan menjalankan kelas gain release yang dikompilasi terhadap
5.759.968 sampel PCM dari sumber: peningkatan energi **6,0000 dB**, puncak
0,27109 menjadi 0,54089 tanpa overflow. Ini membuktikan perhitungan pada sumber
nyata, bukan hasil dengar atau pengukuran speaker HP.

Build final lulus **11m 57s**:

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon --max-workers=2
python3 -m unittest discover -s scripts -p 'test_*.py'
```

APK ARM64 signed/minified/non-debuggable **76.376.658 byte**, bertambah
18.700 byte dari 1.1.45. SHA256
`e80bd42ce9251941e0971727df63cd134d4fc1eb9b88227142fa6efcff06a4ca`.
Audit 262 ELF/13 kelas ZIP, signature produksi dan zipalign 16 KB lulus.
Update-r **1.1.46/code61** di Realme RMX1821 Android 10/API29 (halaman 4 KB)
berhasil; hash `base.apk` identik, tanpa uninstall/reset. APK debug/release
dibangun; AAB tidak dibangun ulang atau dipublikasikan ke toko.

### Bukti HP dan pengguna

- Manual di sheet berubah sekitar **24% → 65%**; PowerManager melaporkan
  override backlight **498 → 1340** (maksimum perangkat 2047). Jadi bukan
  sekadar label bilah; jendela sheet juga mengikuti override player.
- Memilih Otomatis mengembalikan override **−1**. Mode adaptif sistem tetap1;
  tidak menulis setting brightness global. Ini membuktikan delegasi ke Android,
  bukan pengujian terkontrol semua kondisi ruangan/sensor fallback.
- Geser vertikal sisi kiri beralih ke Manual sekitar **25%**. Setelah tes,
  dikembalikan Otomatis.
- **+6/+9/Normal** dapat dipilih saat jeda. Pipeline menampilkan penguat aktif
  untuk +6/+9, lalu kembali ke level asli pada Normal. Posisi **14:57** dan
  status jeda tetap; SRT tetap tersedia.
- +6 juga aktif dengan dekoder Software; dekoder kemudian dikembalikan
  Otomatis. Normal memakai jalur sink semula.
- Pengguna mengonfirmasi **“Sudah jelas dan bersih”** pada +6 dB dengan volume
  HP yang pengguna sebut50%. Ini hasil dengar kasus uji, bukan pengukuran SPL
  atau bukti semua rute/sumber identik dengan pemutar lain. Pilihan +6 dB
  dipertahankan sesuai hasil uji; volume HP tidak dinaikkan oleh alat uji.
- Seluruh **15 path media** sebelum/sesudah sama; tidak ada ekspor/rename/delete.
  Log PID sesi final tidak memuat FATAL EXCEPTION, ANR, PlaybackException atau
  OutOfMemoryError. Tidak menganggapnya jaminan semua skenario bebas galat.

Bukti privat berada di `build/reports/player-controls-1146/` (verified-build.log,
artifact/installed.json, brightness-*-power.txt, gain-*-paused.xml,
software-gain-6.xml, source-boost-measurement.json, user-hearing-result.json).
Tidak memublikasikan screenshot/data akun pribadi.

## Batas dan tindak lanjut

Belum menguji perubahan ruangan terkontrol/sensor fallback saat adaptif Android
nonaktif, seluruh transisi background/PiP, pemulihan proses setelah mode manual,
semua codec/multichannel/passthrough HDMI, Android/ABI lain, runtime 16 KB,
matriks tema/font/TalkBack, atau konsumsi baterai/latensi DSP. Konfirmasi dengar
speaker/headset tidak dipisahkan menjadi dua tes yang terdokumentasi.

Penguat puncak lembut bukan normalisasi LUFS otomatis atau jaminan semua sumber
setara. Normal mempertahankan jalur biasa; gain dapat menambah biaya decode
ketika passthrough/offload sebelumnya tersedia. BUG-093 teratasi pada kasus uji
pengguna lewat +6 dB; akar semua selisih antarpemutar belum dibuktikan.
BUG-087/092 tidak diubah atau ditutup oleh pekerjaan ini. Tidak ada klaim
produksi menyeluruh.

API diperiksa terhadap AAR1.11.1 yang terpasang dan dokumentasi primer:
[AudioProcessor chain](https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink.Builder),
[buildAudioSink](https://developer.android.com/reference/androidx/media3/exoplayer/DefaultRenderersFactory),
[brightness per jendela](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#screenBrightness).
