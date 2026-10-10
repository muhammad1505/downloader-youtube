# Pembersihan lint — 9 Oktober 2026

Permintaan: perbaiki 17 warning dari audit sebelumnya, lalu uji langsung di perangkat fisik. Tidak menggunakan emulator.

## Perubahan

| Temuan sebelumnya | Jumlah | Perbaikan |
| --- | ---: | --- |
| `UseKtx` | 8 warning | Gunakan `String.toUri()` dan `SharedPreferences.edit { ... }`. |
| `GradleDependency` | 8 warning | Perbarui Compose BOM, Activity, Lifecycle, WorkManager, dan Room. |
| `OldTargetApi` | 1 warning | Naikkan compile/target SDK dari 35 ke 37; minimum Android tetap 10 (API 29). |
| `AutoboxingStateCreation` | 2 informasi | Gunakan `mutableIntStateOf` untuk filter riwayat dan batas konkurensi. |

Compose BOM `2026.09.00`, Activity `1.13.0`, Lifecycle `2.11.0`, WorkManager `2.12.0`, Room `2.8.5`. Metadata AAR Compose UI `1.12.1` meminta compile SDK 37 dan AGP minimal 9.1.0, sehingga toolchain ikut diperbarui: AGP `9.1.1`, Gradle `9.8.1`, Compose compiler `2.4.21`, KSP `2.3.12`, JDK 17. Kotlin menggunakan dukungan bawaan AGP, tanpa plugin Android Kotlin lama. Checksum distribusi Gradle dicocokkan dengan sumber resmi.

`extractNativeLibs` dipindahkan dari deklarasi manifest ke pengaturan yang sudah ada, `packaging.jniLibs.useLegacyPackaging = true`. CI memasang platform `android-37.0`, Build Tools `36.0.0`, dan Command-line Tools 23.0. Persyaratan lokal diperbarui di README. Tidak menambah baseline atau menonaktifkan pemeriksaan lint. Warning baru `EmptySuperCall` diperbaiki dengan menghapus pemanggilan superclass kosong; warning versi Gradle diperbaiki melalui wrapper terbaru. Warning compiler tentang ikon playlist, konstruktor Locale, dan pemeriksaan null berlebih ikut dirapikan.

Kompatibilitas toolchain mengikuti [catatan resmi AGP 9.1.1](https://developer.android.com/build/releases/agp-9-1-0-release-notes) dan [panduan Kotlin bawaan AGP](https://developer.android.com/build/migrate-to-built-in-kotlin). R8 bawaan `9.1.31` memenuhi [minimum Kotlin 2.4](https://developer.android.com/build/kotlin-support), yaitu `9.1.29`.

## Perintah verifikasi

```sh
./gradlew -PuseGoogleMirror=true -Pkotlin.compiler.execution.strategy=in-process -Dorg.gradle.jvmargs="-Xmx2g -Dfile.encoding=UTF-8" --max-workers=2 testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon
python3 -m unittest discover -s scripts -p "test_*.py"
for apk in app/build/outputs/apk/debug/*.apk; do python3 scripts/audit_native.py "$apk"; done
for apk in app/build/outputs/apk/release/*.apk; do python3 scripts/audit_release_zip_fields.py "$apk"; done
bash scripts/make_test_apk.sh arm64-v8a
adb install -r app/build/outputs/apk/rc/TubeFlow-arm64-v8a-testing.apk
```

Untuk komputer dengan RAM terbatas, validasi di atas membatasi worker dan menjalankan compiler dalam proses Gradle. Opsi mirror hanya diperlukan bila repositori Google sulit diakses.

## Hasil pemeriksaan host

- Lint debug dan release: **0 error, 0 warning, 0 informasi**; laporan `app/build/reports/lint-results-{debug,release}.{txt,xml,html}`.
- **43 tes Kotlin lulus**, tanpa kegagalan; laporan `app/build/test-results/testDebugUnitTest/`.
- **24 tes Python lulus**, termasuk migrasi database dan generasi worker.
- Kompilasi Kotlin debug/release tidak menghasilkan warning compiler.
- Build debug/release ketiga ABI berhasil; audit native: 255/255/257 ELF, tanpa kegagalan alignment 64-bit. Audit ZIP release: 13 kelas per ABI, tanpa kegagalan.
- APK testing ARM64 lolos `apksigner` dan `zipalign`; SHA256 `10f230542a6eb6953c3f46c1437c56e1c4c88c5c6d3c8957552f34d484b4ece1`.

Gradle mencatat dua jenis deprecation internal plugin AGP: `Configuration.setVisible(boolean)` dan notasi dependency `Project`. Rincian berada di `build/reports/problems/problems-report.html`. Peringatan toolchain ini masih ada; lint aplikasi dan kompilasi Kotlin sudah bersih.

## Cakupan

Pembersihan warning tidak menyelesaikan celah fungsional di [audit bug](REVIEW_2026-10-09.md). Pengujian perangkat dilakukan pada Realme RMX1821, Android 10, ARM64; hasil tidak membuktikan perilaku runtime Android 17 atau perangkat halaman memori 16 KB.

## Pengujian perangkat — 10 Oktober 2026

APK terpasang memiliki hash yang sama dengan artefak ARM64 di atas. Inspeksi link, antrean/background, konversi MP3 128 kbps dengan sampul, penyimpanan SAF, buka/pemutaran Poweramp, filter riwayat, dan persistensi pengaturan setelah restart berhasil. `ffprobe` membuktikan bitrate audio 128.000 bit/detik dan sampul PNG tertanam. Batas paralel dikembalikan ke 3; hasil uji tetap ada di riwayat dan folder pilihan.

Pengguna mengonfirmasi telah membersihkan riwayat/media lama sendiri sebelum tes dilanjutkan; retensi entri lama saat update belum diuji. Bukti dan batas cakupan lengkap ada di [DEVICE_TEST.md](DEVICE_TEST.md).
