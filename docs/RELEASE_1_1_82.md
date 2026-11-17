# Hapus folder, akses Jangan Ganggu, dan auto-play headset — 1.1.82/code97

Rilis **1.1.82/code97** menggabungkan tiga perbaikan yang belum masuk APK 1.1.81 yang sudah
terbit. Versi dan changelog dinaikkan pada 18 November 2026; tag `v1.1.82` diarahkan ulang ke
commit rilis ini sehingga CI membangun APK bertanda tangan dari sumber terbaru.

## Sorotan pengguna

1. **Hapus folder di Koleksi benar-benar menghapus direktorinya.** Sebelumnya berkas dihapus tetapi
   direktori kosong tertinggal di penyimpanan. Kini berkas di folder beserta subfoldernya dihapus,
   subfolder kosong dibersihkan bottom-up, lalu direktorinya dihapus lewat grant SAF bila TubeFlow
   punya izin tulis. Folder baca-saja, folder di luar sumber yang dapat ditulis, akar sumber
   koleksi, dan folder dengan unduhan berjalan tetap dipertahankan dengan alasan yang jelas.
   (BUG-106)
2. **TubeFlow muncul lagi di daftar akses Jangan Ganggu.** `ACCESS_NOTIFICATION_POLICY` sebelumnya
   tidak dideklarasikan, sehingga opsi menahan notifikasi melayang saat menonton tidak pernah bisa
   aktif. Status izin di Pengaturan juga dibaca ulang saat layar kembali aktif. (BUG-104)
3. **Auto-play headset kabel tidak lagi hilang saat sesi baru dipulihkan.** Perencanaan antrean
   diulang tiap 100 ms sampai sesi selesai dipulihkan (maksimal 3 detik), dan setiap broadcast
   sambungan dicatat dengan `action`/`state`/`sticky` di Pengaturan → Log aplikasi. (BUG-105)

Perubahan non-pengguna: AGP 9.5.1 dengan Gradle 9.9.1, `shrinkResources` pada build rilis,
nextlib-media3ext 0.18.0, perapian lint di beberapa layar, dan adaptive icon dipindah dari
`mipmap-anydpi-v26` ke `mipmap-anydpi`. Perubahan ini tidak menambah fitur baru.

## Verifikasi

- **Host (18 November):** `testDebugUnitTest` 705 tes Kotlin lulus sebelum bump versi; regresi baru
  `FolderDeleteTest` (pemetaan folder ke direktori sumber, akar sumber, folder saudara) lulus.
- **Emulator Pixel 4, Android 10/API 29 (permintaan pengguna):** folder SAF bertulis dan folder
  bertingkat benar-benar terhapus dari penyimpanan setelah konfirmasi; folder tanpa grant tulis
  mempertahankan direktori kosong dengan pesan yang jujur. Rincian di [DEVICE_TEST](DEVICE_TEST.md).
- **Build rilis lokal tidak dijalankan pada sesi ini** atas permintaan pengguna. Gate rilis
  (unit test, lint debug/release, `assembleRelease`, `bundleRelease`, signing, audit native/ZIP,
  zipalign 16 KB, `apksigner`) diserahkan ke workflow GitHub Actions pada tag `v1.1.82`.

## Batas yang belum terbukti

- APK/AAB bertanda tangan untuk 1.1.82 belum diperiksa lokal; hash artefak baru bisa dicatat
  setelah workflow tag selesai.
- Folder Koleksi pada provider selain external storage (kartu memori, USB), folder baca-saja
  Android 11+, folder yang masih berisi berkas non-media, dan folder dengan unduhan berjalan belum
  diuji untuk BUG-106.
- Uji emulator tidak menggantikan validasi HP fisik pada [RELEASE](RELEASE.md); Jangan Ganggu dan
  headset kabel/TWS fisik juga belum diuji pada versi ini.
- `shrinkResources` baru aktif pada build rilis; pengaruhnya terhadap paket rilis belum diverifikasi
  tanpa build rilis lokal.
