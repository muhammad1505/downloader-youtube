# Perbaikan resolusi dan preset 1.1.42

Checkpoint 1.1.41: `0b2665f`, dipush sebelum perbaikan. R1/R2/R3
pada [review 25 Oktober](UPDATE_REVIEW_2026-10-25.md) ditangani pada versi
1.1.42/code56. Risiko tambahan Scrubber dalam review tetap terpisah.

## Akar dan penerapan

- **BUG-078 / R1:** angka UI sebelumnya bercampur antara tier dan tinggi.
  `Selection.resolutionForTier` memilih pasangan tier/tinggi tanpa menebak arti
  input. `heightForTier` menerima tier saja. DownloadComposer menyimpan
  `qualityTier`; menu dan ringkasan juga memakai tier. Konversi ke piksel hanya
  pada pembuatan DownloadOptions. Format 720×1080 dan 1080×1620 kini menghasilkan
  tinggi 1620 ketika preset 1080p dipilih.
- **BUG-079 / R2:** pembulatan ke tangga lebih tinggi dihapus. `format_note`
  tetap prioritas; tanpa label sumber dipakai sisi pendek aktual. 960×540
  tampil 540p, bukan 720p. Fallback metadata root memakai aturan yang sama.
- **BUG-080 / R3:** applyPreset membandingkan tier dengan tier. Tinggi 818
  pada video ultrawide tidak lagi mengosongkan pilihan preset 1080p.
  Pemutakhiran metadata mempertahankan tier dan pemetaan filter.

Cache inspeksi privat menjadi schema 3 agar nilai yang dahulu dibulatkan tidak
terus muncul. Cache lama diabaikan saat dibaca dan inspeksi mengambil metadata
baru; tidak ada pengunduhan massal. Room tetap 10, data job lama tetap memakai
cap tinggi piksel, dan tidak ada migrasi database/dependensi/izin baru. Grup
saveable kualitas dan penanda preset UI diganti agar state piksel sementara versi lama tidak
terbaca sebagai tier setelah upgrade.

Kode: `core/Models.kt`, `SourceMetadata.kt`, `InspectionCache.kt`,
`ui/DownloadScreen.kt`, `app/build.gradle.kts`. Regresi pada SelectionTest,
SourceMetadataTest dan InspectionCacheTest. DESIGN.md dan catatan bug diperbarui.

## Verifikasi

Tiga regresi baru dijalankan sebelum mengubah implementasi: kasus bentrok
preset vertikal, 540p per-format dan 540p metadata root semuanya gagal pada
kode 1.1.41. Suite perbaikan juga memeriksa round-trip cache ultrawide,
penolakan cache schema 2, tier preset yang tetap 1080 pada refresh, dan filter
nyata `[height<=?818]`.

Pada 25 Oktober 2026, perintah berikut berhasil (13m 1s):

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon --max-workers=2
```

- **479 tes Kotlin**, 0 gagal/error/skip; enam regresi baru.
- Lint debug/release: **0 error, 3 warning** lama (`SelectedPhotoAccess`,
  `AndroidGradlePluginVersion`, `GradleDependency`). Peringatan deprecation
  Gradle dilaporkan terpisah oleh build.
- APK debug dan release berhasil dibangun. APK ARM64 release non-debuggable,
  signature produksi dan zipalign 16 KB lulus; **262 ELF** dan **13 kelas ZIP**
  diaudit tanpa kegagalan.
- ARM64: **76.274.746 byte**, 1.556 byte lebih kecil dari checkpoint 1.1.41.
  SHA256: `8cbf6c07f4821f1d11f8a0b8d7c74cde2494ee9112818c4bf91b65e359e66daa`.
  Lokasi: `app/build/outputs/apk/release/app-arm64-v8a-release.apk`.
- AAB tidak dibangun ulang; AAB lama 1.1.41 bukan artefak rilis ini.

Log lokal berada di `build/reports/resolution-fixes-1142/` (diabaikan Git).
Suite Python juga dijalankan ulang: **98 tes lulus** (24,689 detik). Ada
`ResourceWarning` dari tes host, tanpa kegagalan. Uji HP dicatat terpisah di bawah.

## Pengujian HP fisik

Release dipasang sebagai update tanpa uninstall/reset pada Realme RMX1821,
Android 10/API29, ARM64, halaman memori 4 KB. Versi terpasang 1.1.42/code56;
SHA256 `base.apk` yang ditarik dari HP identik dengan artefak di atas.

Sumber inspeksi: [IPANK – MUTIARA (Official Music Video)](https://www.youtube.com/watch?v=0SbnqsQInqE),
durasi 6:14. Metadata memerlukan lebih dari satu menit pada sesi ini. Pengujian hanya
membaca metadata dan memilih opsi; tidak menekan tombol download.

- Menekan **1080p MP4** menghasilkan node chip `checkable=true, checked=true`
  dan ringkasan **1080p · MP4**.
- Membuka pengaturan lanjutan menampilkan **Resolusi: 1080p**; menu memuat
  pilihan 1080p. Menu ditutup tanpa mengganti pilihan.
- Setelah rotasi lanskap → potret, chip tetap `checked=true` dan ringkasan
  tetap **1080p · MP4**. Rotasi otomatis/pilihan orientasi awal dipulihkan.
- Link uji dibersihkan. Enam path berkas publik sebelum/sesudah tes tetap
  sama; tidak ada `FATAL EXCEPTION` pada log PID aplikasi selama sesi ini.
- Beberapa format sumber tanpa `format_note` kini menambah label sisi pendek
  aktual (contohnya 818p) di samping label eksplisit 1080p. Itu fallback
  dimensi yang terukur, bukan pembulatan ke tier lebih tinggi.

Bukti XML/PNG disimpan privat di direktori laporan build, tanpa menyalin
status bar HP ke dokumentasi publik.

## Batas dan pemeliharaan

- Kasus bentrok video vertikal dan fallback 540p dibuktikan melalui tes fixture,
  belum melalui download sumber vertikal/540p di HP.
- Unduhan video penuh, playback, LRCLIB, TalkBack, font besar, runtime 16 KB dan
  ABI/Android lain tidak diuji ulang untuk perbaikan terfokus ini.
- Risiko geometri Scrubber setelah perubahan ukuran dari review sebelumnya
  tidak termasuk tiga perbaikan ini dan tetap memerlukan verifikasi tersendiri.
- Semua input UI ke resolver harus berupa tier. Tinggi piksel hanya untuk
  DownloadOptions/selector; jangan memasukkan kembali cap piksel job lama ke
  resolver sebagai tier.
