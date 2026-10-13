# Satu folder koleksi dan pembersihan root TubeFlow

Checkpoint implementasi 13 Oktober 2026, release1.1.3/code17, Room9. Aturan ini menggantikan pilihan folder per artis pada laporan1.1.2; hasil historis sebelumnya tetap berlaku untuk build sebelumnya.

## Akar masalah dan perubahan

- Playlist: policy per performer memperbarui folder dari identitas setiap lagu. `PlaylistMetadata.folderName` kini menyimpan folder koleksi selama inspeksi. `Selection.collectionFolder` memakai identitas seed Mix terverifikasi (artis utama sebelum kolaborator, atau remixer); channel pemilik bukan artis. Tanpa bukti gunakan ID seed. Playlist/album statis memakai judulnya, tanpa mengarang album tag.
- `queuePlaylist` menyalin satu folder ke seluruh pilihan sebagai policy `collection`; pembaruan metadata sumber/native tidak menggantinya. Nama file tetap `judul | versi relevan - artis/remixer` per lagu. Job lama kompatibel; file selesai tidak dipindahkan.
- Cleanup: MediaStore menghapus baris file tetapi tidak direktori kosong. Rekursi SAF lama membaca root berulang. `FolderCleanup` membaca ID anak, menghapus bottom-up, memeriksa kembali kosong, menjaga root dan membatasi4000 entri/depth8. Akses ditolak, siklus, batas dan penambahan bersamaan menghasilkan laporan belum lengkap.
- Pengaturan meminta grant SAF baca/tulis tepat pada root ketika Hapus semua dikonfirmasi. Batal/wrong root tidak menghapus apa pun; output tetap default MediaStore. Cleanup hanya menyentuh isi root saat ini, menjaga riwayat serta menolak job aktif/dijeda. Tidak ada izin seluruh penyimpanan.
- Pembuatan folder SAF diserialisasi agar worker bersamaan memakai direktori yang sama; query gagal tidak dianggap folder belum ada.

## File dan kompatibilitas

Diubah: Models, DownloadViewModel, DownloadWorker, DownloadScreen, SettingsScreen, TubeFlowApp dan versi Gradle. Ditambah: FolderCleanup, FolderCleanupTest, CollectionFolderTest. Skill invariants dan dokumentasi diperbarui. Tidak ada migrasi Room, dependensi baru, perubahan engine/bootstrap/native atau transcoding otomatis.

## Verifikasi

- 160 tes Kotlin lulus, termasuk11 regresi baru untuk folder koleksi dan cleanup. 76 tes Python lulus.
- Lint debug/release:0 masalah. Build signed1.1.3 berhasil dalam10m8s; APK ketiga ABI dan AAB lolos signature/certificate/native, APK lolos alignment16KB/refleksiZIP/manifest, dan AAB lolos bundletool validate. Pin engine/bootstrap paket sesuai sumber. Deprecation Gradle dan warning JAR tetap terpisah dari lint.
- APK ARM64 terpasang melalui `adb install -r` pada Realme RMX1821 Android10/API29. versionName1.1.3/code17 dan hash base.apk yang ditarik dari HP cocok dengan release. Tidak menjalankan uninstall atau clear data. Root sebelum/sesudah pemasangan tetap Download/TubeFlow:0file/5subfolder lama. Pemasangan belum menguji operasi cleanup/playlist.
- Pengguna meminta pemasangan segera lalu akan melaporkan kendala sendiri karena harus bekerja. Pengujian runtime tombol cleanup dan batch playlist pada1.1.3 ditunda; tes host tidak membuktikan hasil fisik.
- Fixture sementara untuk rencana tes nested/outside-root dibersihkan oleh agent sebelum serah-terima; lima folder lama pengguna tidak dihapus lewat shell. Ini bukan bukti cleanup aplikasi.

## Langkah uji pengguna

1. Mix SATRU2 → buka playlist → periksa preview `mix-denny-caknan`, pilih lagu dari beberapa artis dan antrekan. Semua hasil harus masuk satu folder itu; nama/tag masing-masing lagu tetap mengikuti artisnya.
2. Mix DJ yang diberikan → satu folder `mix-maman-fvndy-rmx`. Playlist/album statis → satu folder dengan judul playlist aktual.
3. Pengaturan → Hapus semua file → konfirmasi. Jika pemilih Android muncul, pilih tepat `Download/TubeFlow` dan izinkan akses. Folder lain ditolak, batal tidak menghapus.
4. Pastikan semua file dan subfolder hilang, root TubeFlow tetap ada, ukuran0; riwayat tetap tercatat dengan file tidak tersedia.
5. Ulangi setelah restart; unduhan berjalan/dijeda harus memblokir cleanup. Gangguan akses tidak boleh dilaporkan sebagai bersih.


## Artefak dan pemeliharaan

Distribusi lokal `app/build/outputs/distribution/1.1.3/` memuat APK×3/AAB, sertifikat publik, release.json dan SHA256SUMS. ARM64 63.031.979byte, SHA256 `c767ac27c705a2adeba408c05ab7c51b7603885bb4085b20cbd80e2b86d35e7a`; naik6164byte dari1.1.2. Performa runtime belum diukur ulang. Log privat `build/reports/collection-cleanup-2026-10-13/` menyimpan tes/build/audit dan bukti pemasangan; tidak mengandung keystore dalam distribusi.

Source, tes, skill dan laporan disimpan ke Git. Tidak ada migrasi/format media baru. Android membatasi direktori lewat SAF; bila grant dibatalkan/ditolak, cleanup tidak boleh mengklaim root bersih. Traversal yang melampaui4000 entri/depth8 dilaporkan belum lengkap. Provider SD card/opaque, worker SAF paralel, batch25 download fisik, dan kegagalan izin runtime belum diverifikasi pada release ini.
