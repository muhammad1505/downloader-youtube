# Kontrol koleksi, playlist, dan cache 1.1.22

## Akar masalah dan perubahan

Baseline: 1.1.21/code35, commit f86f910. Room tetap 10, tanpa dependensi,
izin, atau migrasi baru. Perubahan dibatasi pada kontrol yang diminta dan
pengukuran cache; alur download, pemutar, serta publikasi lirik tidak diganti.

- **Koleksi/Riwayat:** Putar, Acak, Cari/Tutup, Bersihkan, dan Hapus gagal
  sebelumnya memakai tombol tinggi 48dp, sedangkan filter memakai chip Material.
  Aksi tersebut kini memakai `ActionChip`, dengan ukuran visual mengikuti filter
  dan target sentuh Material tetap 48dp. Bersihkan mempertahankan konfirmasi
  yang sama dan warna galat. Tombol formulir tetap memakai `ActionButton`.
- **Kelola playlist:** kartu kini berupa baris dengan ikon playlist, nama dua
  baris, serta ikon Ubah/Hapus 48dp. Nama aksesibilitas menyertakan nama playlist.
  Formulir, hasil callback, pencarian, batal edit dan konfirmasi hapus tetap
  memakai implementasi yang ada. Tidak membuat sampul atau jumlah lagu rekaan.
- **Cache:** angka lama menjumlahkan lirik, metadata, dan dua direktori sampul,
  tetapi tombol hanya menghapus respons LRCLIB. Sampul yang tersisa membuat
  total terlihat tidak berubah; pembulatan juga bisa menyamarkan penurunan kecil.
  `PrivateStorage.Usage` kini menyediakan rincian tiap jenis dan total turunannya.
  Pengaturan menampilkan rincian dan menjelaskan hasil pembersihan. File LRC,
  sampul, metadata, dan cooldown penyedia dipertahankan oleh aksi cache lirik.
- **Penyegaran ukuran:** `DownloadViewModel.refreshStorage` menerbitkan ukuran
  privat sebelum menunggu pengukuran folder, sehingga cache tidak menunggu
  provider penyimpanan. Operasi pengukuran/penghapusan tetap di dispatcher I/O.

## File utama

`ui/components.kt`, `MediaLibraryScreen.kt`, `HistoryScreen.kt`,
`PersonalPlaylistDialog.kt`, `SettingsScreen.kt`; `core/PrivateStorage.kt`,
`DownloadViewModel.kt`; `res/values/strings.xml`, `PrivateStorageTest.kt`.
Versi menjadi 1.1.22/code36; pola kontrol dicatat pada `DESIGN.md`.

## Build dan paket

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleRelease --no-daemon --max-workers=2
```

Dengan konfigurasi signing produksi, perintah tersebut lulus dalam 9m38s.
347 tes Kotlin lulus, tanpa gagal/error/skip. Dua regresi baru memeriksa hasil
clear yang mempertahankan metadata/sampul/LRC dan pemisahan status parsial.
Lint debug/release: 0 error/2 warning lama (`SelectedPhotoAccess`, `GradleDependency`).
Build awal dihentikan untuk membuang resource label yang tidak lagi digunakan;
warning tersebut tidak muncul pada pemeriksaan final. Deprecation compiler/Gradle
terpisah dari hasil lint.

APK release tiga ABI dibangun. Audit ARM64: signature produksi, non-debuggable,
zipalign 16KB, 257 ELF dan 13 kelas refleksi ZIP lulus. ARM64 **70.873.540 byte**,
SHA256 `a74ddfd647215cfc6cb00b301c430f55c1c69024506e27fe733df27264b5f574`.
Ukuran bertambah 3.428 byte dari 1.1.21. Tes Python dan AAB tidak diulang untuk
perubahan Kotlin/UI ini; runtime 16KB dan ABI lain belum diuji pada perangkat.

## Pengujian HP

Update `adb install -r` berhasil pada Realme RMX1821 Android 10/API29/ARM64,
tanpa uninstall/reset. Versi 1.1.22/code36 dan hash base.apk cocok. Tema/density
pengguna dipertahankan. Chip Semua/Musik/Video/Putar/Acak/Cari masing-masing
memiliki tinggi target 82px, setara 48dp pada density 272dpi; screenshot diperiksa.

- Riwayat: Bersihkan dan tab filter sama-sama 82px; tidak menghapus riwayat.
- Playlist: satu playlist QA dibuat, ikon Ubah/Hapus tampil, batal edit,
  simpan nama baru, batal hapus, dan hapus playlist QA lulus. Ledger memastikan
  playlist QA sudah dihapus. Tidak menambah atau menghapus file lagu.
- Cache: sebelum/sesudah klik terkontrol, lirik 0B, metadata 433B, sampul 6,7MB,
  total 6,7MB. Pesan cache lirik sudah kosong tampil. Jadi sisa total pada
  pengamatan ini berasal hampir seluruhnya dari sampul; cache lain tetap ada.
  Penghapusan cache lirik berisi diuji pada host, bukan pada HP 1.1.22 karena
  cache lirik saat pengujian HP sudah kosong.
- Satu perintah gulir ADB keluar 255. Uji dihentikan, layar dibaca ulang,
  lalu klik dan pemeriksaan counter cache dilanjutkan dari posisi aktual.
  Tidak ada `FATAL EXCEPTION` pada log AndroidRuntime PID aplikasi yang diperiksa.

Mode terang, font besar, TalkBack, provider folder lambat/tidak tersedia, dan
perangkat lain belum diuji ulang pada perubahan ini. Tidak ada klaim retensi
berdasarkan hash seluruh file pengguna; pengujian hanya memakai playlist QA
dan aksi cache lirik yang diminta.

Bukti privat: `build/reports/controls-cache-1122/`; screenshot/log tidak
diikutkan Git. Tidak mengganggu pemutar video eksternal ketika masih terbuka.
