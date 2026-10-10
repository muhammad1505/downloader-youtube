# Ukuran folder dan lokasi default — 10 Oktober 2026

## Temuan BUG-020

Pada HP Realme Android 10, APK produksi 1.0.0 menampilkan `Terpakai 0 B` dalam mode default meskipun `Download/TubeFlow` berisi **53 file, 309.199.528 byte**. Setelah memilih root TubeFlow melalui pemilih folder, angka menjadi **294,9 MB**, sesuai hasil penjumlahan ukuran file melalui ADB. Saat mode default kembali aktif, hitungan kembali nol.

Default di kode tetap `SettingsStore.DEFAULT_FOLDER = "TubeFlow"`; worker menyimpan pada `Download/${storageFolder}`. `Denny-Caknan` adalah subfolder pengujian lama, bukan default dalam kode. Tidak ada file dipindah/dihapus oleh perbaikan ini.

Penyebab: tanpa `storageTree`, penghitung hanya menggunakan koleksi Downloads yang terlihat aplikasi. Akses folder persisten yang sudah diberikan tidak dipakai lagi saat kembali ke tujuan default. Menurut [dokumentasi Android](https://developer.android.com/training/data-storage/shared/media), hasil download yang tidak dibuat oleh instalasi aplikasi saat ini memerlukan Storage Access Framework. Riwayat kosong juga tidak membuktikan folder fisik kosong.

## Perubahan release 1.0.1

Titik pengerjaan: `core/DownloadViewModel.kt` (`refreshStorage`, `measureStorage`, `measureTree`), `core/StorageFolderAccess.kt` (`matchesDefault`), `ui/SettingsScreen.kt`, dan penghubung state `ui/TubeFlowApp.kt`. Semua relatif terhadap `app/src/main/java/id/local/tubeflow/`. Regresi berada di `app/src/test/java/id/local/tubeflow/core/StorageFolderAccessTest.kt`.

- `StorageFolderAccess.matchesDefault` menyamakan ID root provider native dengan folder default **secara tepat**. Grant untuk `TubeFlow/Denny-Caknan`, `TubeFlowBackup`, folder lain, kartu SD atau provider tidak dikenal tidak digunakan untuk menghitung root default.
- `DownloadViewModel.measureStorage` memakai read grant persisten root TubeFlow ketika output masih mode default. Penghitungan lewat SAF tidak mengubah tujuan output menjadi subfolder.
- Label membedakan **Ukuran folder** dari **File yang dapat diakses**. Query gagal/null, ukuran file tidak diketahui, atau batas 8 tingkat/4.000 dokumen terlampaui menjadi **Ukuran belum dapat dibaca**, bukan nol palsu atau loading selamanya.
- Refresh yang lebih lama dibatalkan sebelum refresh baru; hasil query lama tidak diterapkan setelah pembatalan.
- Pemilih folder dimulai pada tujuan aktif/root default TubeFlow. Tombol pilih ulang tersedia. URI baru disimpan hanya setelah izin persisten berhasil diperoleh.
- Versi dinaikkan ke **1.0.1 (2)**; keystore produksi sebelumnya tetap digunakan. Tidak ada perubahan schema/database atau penambahan izin akses seluruh penyimpanan.

Lima regresi `StorageFolderAccessTest` mencakup root primary, raw Downloads lintas profil, penolakan subfolder/sibling/provider lain, serta nama folder literal dan tervalidasi.

Kasus provider opaque/cloud tidak ditebak sebagai folder default. Jika tidak dapat disamakan, pilih folder tersebut secara eksplisit; ukuran tanpa grant lengkap tetap diberi label cakupan terbatas. Kegagalan provider/izin dicabut dan batas folder besar perlu pengujian runtime tersendiri.

## Validasi host dan artefak final

Build signed release **1.0.1 (2)** berhasil. **60 tes Kotlin** (0 gagal/error/skipped), **37 tes Python**, dan lint debug/release **0 masalah**. Ketiga APK lolos signature produksi, zipalign 16 KB, manifest non-debuggable, audit native (255/255/257 ELF) dan refleksi ZIP (13 kelas per APK), tanpa kegagalan. AAB lolos verifikasi tanda tangan/sertifikat, bundletool validate dan audit **1.024 ELF**. Hash engine dan bootstrap paket cocok dengan sumber yang dipin. Peringatan internal Gradle dan verifikasi JAR tetap seperti catatan signing; lint bersih tidak berarti semua alat tanpa warning.

Salinan distribusi: `app/build/outputs/distribution/1.0.1/`, berisi APK tiga ABI, AAB, sertifikat publik, `release.json`, dan `SHA256SUMS`. APK ARM64 untuk HP uji: `TubeFlow-1.0.1-arm64-v8a-release.apk`, SHA256 `3436ec61465a5a6e7a710158d21529c75a5feb50c9a3dbf35587aa004acf23ac`. Fingerprint produksi sama dengan 1.0.0; tidak ada perubahan kunci.

Log lokal berada di `build/reports/storage-release-build.log`, `storage-python-tests.log`, `storage-bundle-validation.log`, `storage-final-verification.json`, serta `storage-*-signature.log` dan log audit terkait. Metadata lengkap ada pada [panduan signing](PRODUCTION_SIGNING.md).

## Verifikasi HP — berhasil pada root default

HP kemudian tersambung. APK produksi **1.0.1 (2)** terpasang melalui installer bawaan; hash APK yang ditarik dari HP dan sertifikat produksi cocok. ADB install sebelumnya ditolak verifikasi bawaan; tidak ada bypass verifier atau uninstall/clear data. Pengguna turut berinteraksi dengan installer/pemilih folder.

Pada Realme RMX1821, Android 10/API 29, ARM64: jumlah aktual **50 file / 258.380.600 byte**, setara **246,4 MB** pada UI. Ini berbeda dari baseline 1.0.0 karena isi folder berubah sebelum tes terbaru. Mode default langsung menunjukkan ukuran yang benar dengan grant persisten. Picker membuka root TubeFlow; mode folder pilihan juga menunjukkan 246,4 MB. Setelah **Kembali ke Downloads/TubeFlow**, Segarkan, dan force-stop/buka ulang saat antrean kosong, lokasi tetap root dan ukuran tetap benar. Jumlah file serta daftar ukuran per file identik sebelum/sesudah tes. Riwayat yang diperiksa sudah kosong; tidak ada klaim pemulihan riwayat lama. Log PID aplikasi setelah restart tidak menemukan fatal/Room error.

Bukti bersih: [Pengaturan sesudah restart](images/storage-default-restart-2026-10-10.png). XML dan ringkasan ukuran lokal berada di `build/reports/storage-default-*.xml` dan `storage-device-size-{before,after}.json`. Kasus grant subfolder/sibling hanya tercakup tes host; pencabutan izin/provider gagal/batas folder besar masih perlu tes runtime terpisah. Langkah reproduksi:

1. Sambungkan Realme Android 10, pasang APK ARM64 1.0.1 sebagai update produksi; jangan uninstall atau clear data. Cocokkan hash APK terpasang.
2. Jumlahkan ukuran file root `Download/TubeFlow` dan subfolder melalui ADB tanpa mengubah file. Bandingkan dengan angka Pengaturan; ukuran mungkin berubah jika pengguna menambah file.
3. Pastikan lokasi default `Downloads/TubeFlow`. Dengan grant root yang masih berlaku, mode default harus menampilkan **Ukuran folder** sesuai jumlah fisik.
4. Bila grant belum ada, pilih root TubeFlow melalui **Pilih folder sendiri**, lalu **Kembali ke Downloads/TubeFlow**. Ukuran lengkap harus tetap terbaca, tujuan output tetap root.
5. Segarkan dan restart aplikasi saat antrean kosong. Pastikan lokasi/angka tetap benar dan tidak ada file atau riwayat terhapus.
6. Uji grant subfolder/sibling, izin dicabut dan provider gagal secara terpisah; catat hasil dan screenshot di `DEVICE_TEST.md`; cakupan ini belum dijalankan pada HP.
