# Validasi, perangkat dan release

Jalankan dari root repository. Rujukan proyek: `docs/RELEASE.md`, `docs/PRODUCTION_SIGNING.md`, `docs/DEVICE_TEST.md` dan laporan fitur terkait. Baca laporan terbaru bila catatan release lama bertentangan dengan instalasi yang sudah tercatat.

## Pilih pemeriksaan sesuai perubahan

Gunakan JDK 17; ambil SDK/Build Tools dari konfigurasi aktual dan `.github/workflows/android.yml`. `local.properties` bukan berkas untuk dibagikan. Fresh checkout membutuhkan FFmpeg AAR lokal; bangun dengan `bash scripts/rebuild_ffmpeg_webp.sh` ketika artefak yang diperlukan belum ada atau payload native diubah.

Untuk perubahan Kotlin, jalankan tes yang relevan dan lint; untuk engine/bootstrap atau transisi yang dicakup host, jalankan Python. Sebelum menyerahkan kandidat APK, ikuti pemeriksaan release proyek:

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Contoh tes Kotlin terarah:

```sh
./gradlew testDebugUnitTest --tests 'id.local.tubeflow.core.StorageFolderAccessTest'
```

Hasil release Gradle dapat **unsigned** tanpa konfigurasi signing. Build sukses bukan bukti APK bisa diinstal. Untuk kandidat produksi yang memang diminta:

```sh
bash scripts/build_release.sh --no-daemon
```

Script menjalankan unit Kotlin, lint debug/release, assembleRelease dan bundleRelease dengan konfigurasi signing tepercaya. `-PuseGoogleMirror=true` tersedia bila resolusi dependency memerlukannya; jangan menjadikannya syarat universal.

APK per ABI berada di `app/build/outputs/apk/{debug,release}/`; AAB di `app/build/outputs/bundle/release/`. Audit APK final yang benar-benar akan dibagikan:

```sh
python3 scripts/audit_native.py path/to/final.apk
python3 scripts/audit_release_zip_fields.py path/to/final-release.apk
apksigner verify --verbose --print-certs path/to/final-release.apk
zipalign -c -P 16 4 path/to/final-release.apk
```

Gunakan executable dari Build Tools aktif jika belum ada pada PATH. Periksa metadata versionName/versionCode/package, non-debuggable, engine checksum dan kesamaan bootstrap paket. Untuk AAB, verifikasi `jarsigner`, fingerprint sertifikat, struktur melalui bundletool validate dan audit native. Simpan output besar ke log; laporkan ringkasannya. Lint bersih berbeda dari deprecation Gradle atau warning JAR.

## Pengujian pada HP fisik

Pilihan pengelola untuk proyek ini adalah **HP fisik, tanpa emulator**. Skill tidak mengotorisasi pemasangan, upload Play Protect, uninstall atau penghapusan file. Lanjutkan aksi yang sudah diizinkan dalam tugas; bila izin memang diperlukan dan belum ada, minta hanya untuk aksi tersebut setelah artefak siap ditinjau.

1. Cek `adb devices`, ABI/Android/page size dan versi/signing aplikasi yang terpasang. Jika HP belum tersambung, selesaikan build/dokumentasi yang dapat dikerjakan; catat tes perangkat sebagai tertunda.
2. Pilih APK sesuai ABI. Update memakai kunci yang sama dan versionCode lebih tinggi; jangan uninstall/clear data untuk mengatasi perbedaan sertifikat. Tentukan strategi perpindahan data bersama pengelola bila kunci berbeda.
3. Coba pemasangan yang diizinkan. Pada HP lokal, ADB pernah ditolak `INSTALL_FAILED_VERIFICATION_FAILURE` sementara installer Files berhasil. Gunakan installer normal bila diizinkan; jangan mematikan verifier atau mengulang ADB tanpa perubahan kondisi. Play Protect scan/upload memerlukan otorisasi tersendiri yang belum tentu ada di sesi berikutnya.
4. Baca layar terbaru sebelum menekan tombol karena pengguna dapat memajukan installer/picker sendiri. Jangan membuat loop tap buta. Kutip URL utuh untuk shell remote agar `&` tidak memecah intent share.
5. Verifikasi APK terpasang dengan versi, hash APK yang ditarik dari HP, dan sertifikat; layar “terpasang” saja belum cukup. Catat model/Android/ABI/page size serta hash pada laporan.
6. Uji pemicu asli dan jalur gagal yang relevan. Gunakan konten diizinkan. Periksa artefak audio/video dengan ffprobe/playback bila format berubah; lirik perlu pengamatan waktu aktual.

Sebelum force-stop/restart, pastikan tidak mengganggu job aktif yang bukan bagian skenario uji. Untuk storage, bandingkan ukuran/jumlah file sebelum-sesudah tanpa memindahkan atau menghapus file pengguna. Default harus tetap root TubeFlow kecuali pengguna memilih tujuan lain.

Logcat difokuskan pada PID aplikasi; jangan membagikan isi chat/nama file pribadi atau serial perangkat. Inspeksi screenshot sebelum menyimpannya ke `docs/images/`; notifikasi pribadi yang muncul tidak boleh ikut menjadi bukti publik. Catat interaksi pengguna yang membuat suatu langkah tidak teramati penuh; jangan mengklaim retensi data hanya karena instalasi berhasil.

## Signing dan serah-terima artefak

Pakai keystore produksi yang sudah ada. Lokasi default privat: `$HOME/.local/share/tubeflow/signing/`; script mendukung `TUBEFLOW_SIGNING_ENV` atau empat variabel `TUBEFLOW_KEYSTORE_PATH`, `TUBEFLOW_KEYSTORE_PASSWORD`, `TUBEFLOW_KEY_ALIAS`, `TUBEFLOW_KEY_PASSWORD`. Jangan cetak password, memasukkannya ke argumen command-line, atau menyalin konfigurasi/private key ke repo/distribusi. Jangan regenerate kunci untuk update biasa.

Untuk release yang diminta, naikkan versi yang tepat dan simpan artefak terverifikasi per versi di `app/build/outputs/distribution/<version>/`. Pertahankan release lama. Sertakan `release.json`, SHA256SUMS dan sertifikat publik bila mengikuti format distribusi yang sudah ada. Manifest harus mencerminkan hasil tes aktual; sesudah metadata berubah, hitung ulang checksum.

Keystore lokal dan artefak signed tidak membuktikan backup eksternal, publikasi toko, pemenuhan lisensi, atau kompatibilitas Android/16 KB yang belum diuji. Update `docs/PRODUCTION_SIGNING.md` dan laporan perangkat dengan cakupan nyata; publikasi/upload hanya dalam tugas yang mengotorisasinya.
