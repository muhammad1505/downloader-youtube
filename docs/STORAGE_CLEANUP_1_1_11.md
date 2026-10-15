# Hapus semua tanpa permintaan SAF berulang — 1.1.11

## Akar masalah

BUG-041 memperbaiki direktori kosong yang tertinggal dengan mewajibkan tree SAF.
Jalur itu tidak mempertimbangkan Android10 legacy yang sudah diberi izin tulis.
Akibatnya SettingsScreen meminta pemilih folder sebelum memanggil backend,
dan DownloadViewModel juga menolak tanpa grant meskipun akses langsung tersedia.

Di HP penguji Android10, READ/WRITE_EXTERNAL_STORAGE sudah granted. Membaca
Koleksi sendiri tidak membuktikan akses hapus seluruh folder: Android scoped dapat
menampilkan media tetapi tetap membatasi direktori kosong dan file milik instalasi lain.
[Aturan penyimpanan Android](https://developer.android.com/about/versions/11/privacy/storage)
menjelaskan perbedaan legacy Android10 dan scoped Android11+.

## Perubahan

- StorageFolderAccess menyediakan capability bersama: hanya API29, legacy aktif,
  dan izin WRITE_EXTERNAL_STORAGE tersedia yang dapat melewati SAF.
- SettingsScreen menggunakan capability tersebut saat konfirmasi, bukan hanya
  persistedUriPermissions. Teks menjelaskan pemilih hanya bila akses belum ada.
- DownloadViewModel memotret target sebelum I/O, memeriksa ulang izin, menjaga
  mutex dan penolakan job aktif/dijeda, lalu memakai jalur legacy atau SAF.
- LegacyFolderCleanup memakai traversal FolderCleanup yang sudah ada: bottom-up,
  batas8tingkat/4.000entri, cek kosong sesudah hapus, root tetap ada. Canonical path,
  penolakan symlink dan batas direktori mencegah menjangkau sibling/parent.
- Penghapusan berkas terindeks memakai MediaStore; fallback file memicu scanner.
  Katalog dan ukuran disegarkan setelah operasi. Tidak mengubah default folder,
  Room9/schema, engine, player atau dependensi.

## Pengujian

Tujuh regresi baru: satu kebijakan izin (legacy/write/API) dan enam kasus filesystem
nyata pada TemporaryFolder: media/LRC/subfolder kosong, sibling tetap utuh, hapus
gagal, symlink keluar/root, file ditambah saat cleanup, serta root tidak ada.
Suite Kotlin: **240 tes lulus**, termasuk tujuh regresi baru. Suite Python: **83 tes lulus** (54,818detik). Lint debug/release: **0error/2warning** yang diketahui (SelectedPhotoAccess dan pin ONNX1.23.2). Build final **BUILD SUCCESSFUL 4m53s** lewat `JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 bash scripts/build_release.sh assembleDebug --offline --max-workers=2`. Debug, signed release APK×3/AAB dan R8 lulus. Tidak menjalankan
penghapusan seluruh media pribadi HP secara otomatis.


## Berkas terdampak

`StorageFolderAccess.kt`, `LegacyFolderCleanup.kt`, `DownloadViewModel.kt`,
`SettingsScreen.kt`, `strings.xml`, versi Gradle, `StorageFolderAccessTest.kt`,
`LegacyFolderCleanupTest.kt`, aturan skill penyimpanan dan dokumen bug/checkpoint.
Tidak ada file sumber yang dihapus atau migrasi database baru.

## Batas pengujian

Penghapusan media+LRC/direktori benar-benar dijalankan oleh regresi host dalam
folder sementara. Penghapusan seluruh koleksi HP **dijalankan pengelola sendiri** setelah update, bukan oleh otomasi agent; hasilnya terverifikasi di bawah.
Android11+ scoped storage, provider eksternal dan pencabutan izin ketika operasi
sedang berjalan tetap memerlukan pengujian perangkat tersendiri. Perbaikan ini
memakai izin sah yang tersedia; tidak meminta MANAGE_EXTERNAL_STORAGE.


## APK, pemasangan dan hasil HP

APK ketiga ABI lulus apksigner/certificate, zipalign16KB, audit native dan ZIP
reflektif. AAB lulus jarsigner, audit native dan bundletool validate. Sertifikat
produksi tetap `ebe3b40eb42c9b60fd044a011fc6a9e0cbfcde97c53accb2ea49934f4934dbd0`.
Tidak ada publikasi toko. ARM64 bertambah2.136byte dari1.1.10; tidak mengukur RAM/baterai.

| Artefak | Byte | SHA256 |
| --- | ---: | --- |
| arm64-v8a APK | 70566627 | `93c6318b682a3a4837b171f5ff264e55fc56e60859e0874f43635bee9004a662` |
| armeabi-v7a APK | 62257085 | `da1ac2e17ec3ddd99483a47433a7e736398b7396333668b37c0b0aa85319251e` |
| x86_64 APK | 75007436 | `41ee2c0ac2f0559a0905ef5b24190b7f0fb9e9645317d51a0b3b0d5e60a03e6e` |
| AAB | 255596700 | `943ded82348ddb993a03b41f95a95e93beff52420cffc748e18c23da29b38b9f` |

RealmeRMX1821 Android10/API29, ARM64/4GB/halaman4KB. Izin baca/tulis sudah granted,
LEGACY_STORAGE allow. `adb install --no-incremental -r` berhasil, versi1.1.11/code25
serta hash base.apk dari HP cocok ARM64 di atas. Tidak uninstall/clear-data.

| Tahap | Hasil aktual |
| --- | --- |
| Sebelum/sesudah pemasangan | 70/70file identik SHA256; belum ada media dihapus agent. |
| Percobaan pengelola | Hapus semua dan konfirmasi; jawaban **“Langsung bersih tanpa SAF”**. |
| Verifikasi folder sesudah aksi pengelola | `find /sdcard/Download/TubeFlow -mindepth 1` sukses dengan **0entri**; `test -d` sukses, root tetap ada. File dan subfolder habis. |
| Crash setelah pemasangan | Tidak ada package TubeFlow dalam buffer crash yang diperiksa. |

Perubahan70→0 berasal dari aksi hapus yang dipilih pengelola sesudah verifikasi
update, bukan kehilangan data saat instalasi. Log/hash/XML mentah disimpan lokal
di `build/reports/storage-1111/` dan tidak dipublikasikan. Status BUG-050 terbukti
untuk jalur Android10 ini; batas Android11+/provider lain tetap berlaku.
