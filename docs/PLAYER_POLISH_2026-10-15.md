# Pemutar dan pembacaan penyimpanan — 15 Oktober 2026

## Cakupan dan akar masalah

Checkpoint ini melanjutkan source lokal batch 7; tidak mengulang implementasi karaoke,
player latar, equalizer, atau antrean yang sudah tersedia. Database tetap Room versi 9.

- Mini player memakai bentuk kartu Koleksi (radius tema 20 dp), sampul 48 dp,
  warna sampul transparan, judul/artis, progres, putar/jeda, berikutnya, dan tutup.
- Pemutar penuh mempertahankan swipe sampul/lirik, pencarian/editor lirik,
  kecepatan, equalizer, dan karaoke terpisah. Area halaman dibatasi tingginya;
  seluruh konten dapat digulir pada layar pendek/font besar agar kontrol tetap
  dapat dijangkau. Petunjuk geser menjelaskan akses ke lirik.
- Antrean sebelumnya terjepit dalam alert sempit. Kini panel bawah menyediakan
  sampul dan penanda lagu aktif, acak/ulangi, bersihkan dengan konfirmasi,
  putar sekarang, putar setelah lagu ini, pindah atas/bawah, dan keluarkan.
  Mengubah urutan atau menyisakan lagu aktif tidak memuat ulang audio.
- Layout isi notifikasi dibatasi 48 dp dengan sampul 40 dp, progres, dan X
  langsung. Kontrol media dan MediaSession tetap memakai engine yang sama.
  Tampilan ringkas dan diperluas memakai isi yang sama. Header/panah ekspansi
  milik Android/OEM tidak dapat dijanjikan hilang di seluruh perangkat.
- Ukuran Koleksi sebelumnya hanya disegarkan dari perubahan riwayat atau saat
  membuka Pengaturan. Hasil scan, impor, hapus, atau karaoke bisa terlambat terbaca.
  `DownloadViewModel` sekarang mengamati agregat Room yang sama dengan Koleksi;
  perubahan jumlah/ukuran memicu penyegaran tanpa grant folder tambahan. Ukuran
  katalog diberi label tersendiri, dan ukuran file yang belum diketahui ditandai
  sebagai parsial. Grant SAF tetap diperlukan untuk membaca seluruh isi folder
  di luar file yang sudah dapat diakses.

## Modul terdampak

`ui/PlayerUi.kt`, `core/DownloadViewModel.kt`, `ui/SettingsScreen.kt`,
`ui/TubeFlowApp.kt`, `res/layout/notification_playback.xml`, dan `res/values/strings.xml`.
Helper `core/PlaybackNotifications.kt` dan tiga regresi baru memperbaiki BUG-046 yang ditemukan saat tes HP. Tidak ada dependensi baru atau migrasi database dalam polesan ini.

## Rencana desain

Palet memakai Theme.kt: gelap OLED `#000000`, surface `#111113`, teks `#F0EDF4`,
teks sekunder `#BFC3D0`, aksen fallback `#C6B7FF`. Mode terang tetap memakai peran
Material tema; aksen sampul melewati helper kontras yang tersedia. Ritme 4/8/12/16/24 dp,
judul `titleSmall`/`headlineSmall`, radius kartu 20 dp dan sampul 24 dp.

```text
Mini   [sampul] [judul / artis] [putar] [berikutnya] [X]
       [progres aktual]
Penuh  [turun] [sedang diputar] [kecepatan] [EQ]
       [sampul <geser> lirik]
       [judul / artis / posisi antrean]
       [seek / waktu] [kontrol utama] [acak ulangi karaoke antrean]
Sheet  [Antrean putar / jumlah] [X]
       [acak / ulangi / bersihkan]
       [sampul / judul / artis / aktif / menu per lagu]
```

Kritik desain: hindari dekorasi generik dan bayangan berlebihan. Bahasa kartu
Koleksi dipakai kembali; warna hanya menonjolkan sampul, progres, dan lagu aktif.
Menu lirik/karaoke tetap terhubung dengan fungsi TubeFlow yang sebenarnya.

## Temuan selama uji HP

Kandidat awal 1.1.9 (SHA256 `d21ef4a15f1defefb495dd9b34ea0c76cd22456eae6f0c72f767a326fc6b3221`)
berhasil dipasang. Antrean, menu, mode terang/gelap, dan pembacaan 263,8 MB terlihat
pada HP. Uji slider saat jeda menemukan BUG-046: posisi UI sudah berubah menjadi
1:21, tetapi MediaSession tetap 482 ms. Pengamat service kini mengikuti seek,
kecepatan, dan progres per detik; hasil build dan APK akhir dicatat di bawah.

## Verifikasi

Versi akhir **1.1.9 / versionCode 23**, Room tetap 9.

| Pemeriksaan host | Hasil aktual |
|---|---|
| `bash scripts/build_release.sh assembleDebug --offline --max-workers=2` (JDK 17) | Berhasil; mencakup unit, lint debug/release, APK debug/release, AAB bertanda tangan produksi |
| Kotlin/JUnit | 228 tes, 0 gagal; termasuk 3 regresi sinkron notifikasi dan 2 regresi antrean tambahan |
| `python3 -m unittest discover -s scripts -p 'test_*.py'` | 79 tes, OK; query Room asli turut diuji di SQLite |
| Lint debug/release | 0 error, masing-masing 2 warning: `SelectedPhotoAccess`, pin `GradleDependency` ONNX 1.23.2 |
| APK tiga ABI | Tanda tangan produksi, non-debuggable, versi 1.1.9/23, zipalign 16 KB, native audit, refleksi ZIP lulus |
| AAB | `jarsigner -verify`, `bundletool validate`, audit native lulus |

Kompiler juga masih melaporkan deprecation API lama MediaSession/Notification.Action;
Gradle melaporkan fitur yang akan dihapus pada Gradle 10. Warning tersebut bukan
error build dan bukan klaim lint sepenuhnya tanpa warning.

APK ARM64: **70.546.231 byte**, SHA256
`042d64db675617826441da0c0e76e7aab39ecad849d73732aa8279a0d9696af0`.
Sertifikat produksi SHA256
`ebe3b40eb42c9b60fd044a011fc6a9e0cbfcde97c53accb2ea49934f4934dbd0`.
APK ditarik kembali dari HP; hash `base.apk` cocok dengan artefak akhir.

### Pengujian HP

Realme RMX1821, Android 10/API 29, ARM64, halaman memori 4 KB; tanpa emulator.
Pembaruan dilakukan `adb install --no-incremental -r`, tanpa uninstall/clear data.

- Ukuran Pengaturan **263,8 MB** langsung terlihat tanpa memilih folder. Angka
  sesuai 51 file audio fisik sebesar **276.635.482 byte**. Dump grant URI tidak
  menunjukkan grant SAF untuk TubeFlow. Izin baca media yang sudah diberikan tetap berlaku.
- Mini player, halaman sampul/lirik, dan panel antrean diperiksa pada mode terang
  dan gelap. Antrean awal berisi 51 lagu; menu per lagu tampil lengkap.
- “Putar setelah lagu ini” memindahkan Nemen ke setelah Lamunan; lagu aktif
  bertahan. “Bersihkan” dengan konfirmasi menyisakan satu lagu dan tidak menghapus
  media. Menu acak/bersihkan nonaktif ketika hanya satu lagu.
- Tombol X notifikasi menghentikan pemutar dan menghilangkan MediaSession.
  Pemeriksaan visual menemukan tinta X hitam pada latar gelap OEM; ikon kemudian
  diganti warna netral dengan kontras ikon ≥3:1 pada latar terang/gelap.
- Uji final sinkron seek, kontras X akhir, dan perbandingan hash 69 file: lihat
  catatan hasil akhir di bawah.

### Bukti visual dan batas cakupan

Tangkapan berikut berasal dari kandidat awal 1.1.9 yang tata letaknya sama dengan
rilis akhir; perbaikan sesudahnya menyentuh sinkron notifikasi dan warna ikon X:
[sampul gelap](images/player-artwork-dark-1.1.9.png),
[sampul terang](images/player-artwork-light-1.1.9.png),
[antrean](images/player-queue-dark-1.1.9.png),
[ukuran Koleksi](images/storage-catalogue-1.1.9.png).
Snapshot drawer notifikasi tetap privat karena memuat informasi aplikasi lain.

Belum diuji: font 1,3×/2× (perubahan `font_scale` via ADB ditolak ColorOS dengan
`WRITE_SETTINGS`; nilai awal 0,9 tetap), lebar 360 dp, landscape/tablet, TalkBack,
Android 12+ dan pemutaran runtime pada HP 16 KB. Lint/native alignment tidak
membuktikan matriks runtime tersebut. Gangguan provider SAF, storage penuh,
download jaringan audio/video baru, dan pemisahan karaoke lagu penuh tidak diuji
ulang dalam checkpoint polesan ini. Statistik tidak memicu unduhan lirik/model.

### Pemeliharaan

Gunakan `PlaybackNotifications` untuk kebijakan penerbitan posisi; perubahan
antrean tidak memuat ulang MediaPlayer. Pertahankan perbedaan ukuran katalog,
folder lengkap, dan ruang perangkat. Hindari mengembalikan warna X ke atribut
tema Activity karena tema SystemUI dapat berbeda.

Rujukan platform: [custom notification Android](https://developer.android.com/develop/ui/views/notifications/custom-notification).

### Hasil akhir APK yang diserahkan

- Seek saat jeda: UI **2:37**, MediaSession **157.042 ms**; BUG-046 terverifikasi pada APK akhir.
- Saat putar: dua snapshot MediaSession bergerak **4.089 → 6.101 ms** (state PLAYING), membuktikan state sistem diperbarui selama progres berjalan.
- X abu netral terlihat pada drawer gelap ColorOS; ketuk X menghilangkan MediaSession dan notifikasi ID 42. Aksen kontrol sistem mengikuti sampul; garis progres custom memakai aksen tema.
- Perbandingan SHA256: **69/69 file awal tetap identik**, jumlah file tetap 69. Tidak ada reset data, rename, atau penghapusan file media selama tes.
- Preferensi tema dikembalikan ke **Ikut sistem**; font sistem tetap **0,9**. Sesi uji ditutup melalui X.

Klaim ini terbatas pada perangkat dan skenario yang dicatat; tidak menutup seluruh matriks rilis atau membuktikan kualitas pemisahan vokal.
