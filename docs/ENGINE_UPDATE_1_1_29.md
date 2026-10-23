# Pembaruan engine di Pengaturan, 1.1.28–1.1.29

> 1.1.30/code44 tetap memakai engine bawaan **yt-dlp 2026.10.21** dan kode
> updater yang sama. Tidak ada uji pasang/rollback baru pada 1.1.30; bukti
> perangkat 1.1.30 ada di [PROGRESS.md](PROGRESS.md).

## Masalah dan keputusan

TubeFlow membawa yt-dlp di dalam APK. Sebelum perubahan ini, memperbarui engine
berarti menunggu rilis aplikasi baru; tidak ada jalur aman untuk mengambil
perbaikan extractor yang lebih cepat. Pengelola meminta pembaruan engine dari
dalam aplikasi tanpa mengorbankan integritas paket.

Keputusan:

- Pemeriksaan dan pemasangan dilakukan dari **Pengaturan → Mesin media**.
- Hanya rilis stabil resmi `yt-dlp/yt-dlp` di GitHub yang diterima.
- Engine baru tidak menggantikan aset APK. Ia disimpan sebagai override di
  `noBackupFilesDir` dan dapat dihapus lewat **Kembali ke engine bawaan**.
- Tidak ada pembaruan otomatis atau latar belakang.

## Kontrol keamanan

- API: `https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest`.
- Host yang diizinkan: `api.github.com`, `github.com`,
  `github-releases.githubusercontent.com`, `objects.githubusercontent.com`, dan
  `release-assets.githubusercontent.com`; HTTPS, port 443, tanpa userinfo,
  maksimal lima redirect.
- Ukuran engine dibatasi 20 MB; tag harus berbentuk `YYYY.MM.DD` yang stabil.
- Checksum diperiksa dua kali: `digest` dari respons API dan entri `yt-dlp` pada
  `SHA2-256SUMS` rilis. File zipapp juga divalidasi berisi `__main__.py` dan
  `yt_dlp/version.py` dengan versi yang sama, serta menolak entri traversal.
- Pemasangan memakai file sementara + `ATOMIC_MOVE`; jurnal override menyimpan
  versi, SHA-256, sumber, dan waktu pasang. Engine sebelumnya tidak disimpan
  sebagai salinan kedua.
- **Rollback** menghapus override dan manifest, lalu aplikasi kembali memakai
  engine dari APK. FFmpeg tidak ikut diperbarui.
- Pemeriksaan dan pemasangan ditolak saat inspeksi atau unduhan
  `QUEUED`/`RUNNING`/`PAUSED` aktif. Kontrol juga memeriksa eksekusi engine yang
  masih hidup.
- Command line `YoutubeDL.updateYoutubeDL` dari library tidak dipakai karena
  bytecode-nya tidak memverifikasi checksum.

## Perubahan perilaku

- Engine bawaan 1.1.29 adalah **yt-dlp 2026.10.21**, SHA-256
  `b7af77f686e95f7845ce8d09d21957718febb8b48b30555adb9dd2292ad7bf14`.
- 1.1.28 sengaja masih membawa 2026.08.19 agar jalur pembaruan diuji nyata;
  1.1.29 membawa 2026.10.21 sebagai fallback.
- Recovery juga diperkuat: jurnal `pendingUri`/`sidecarPending` hanya dilepas
  bila `StorageUris.delete` membuktikan berkas hilang. Entri yang ditolak
  provider tetap tersimpan untuk percobaan berikutnya, termasuk setelah job
  aktif ditandai gagal/dibatalkan. Perubahan ini memiliki enam regresi di
  `DownloadRecoveryTest`.

## Berkas utama

`core/EngineUpdater.kt`, `core/Engine.kt`, `core/DownloadRecovery.kt`,
`core/DownloadViewModel.kt`, `core/Models.kt`, `ui/EngineUpdatePanel.kt`,
`ui/SettingsScreen.kt`, `ui/TubeFlowApp.kt`, `res/values/strings.xml`,
`EngineUpdaterTest.kt`, `DownloadRecoveryTest.kt`, `app/build.gradle.kts`,
`scripts/update_bundled_engine.py`, dan aset `engine.json`/`yt-dlp`.

## Verifikasi host

```sh
python3 scripts/update_bundled_engine.py 2026.10.21
python3 -m unittest discover -s scripts -p 'test_*.py'
# 93 tes Python: OK (24,604 detik)

./gradlew --no-daemon -PuseGoogleMirror=true testDebugUnitTest lintDebug
# 384 tes Kotlin, 0 gagal/error/skip
# lint: 0 error, 3 warning yang diketahui
#   SelectedPhotoAccess, Gradle 9.8.2 tersedia, pin onnxruntime 1.23.2

bash scripts/build_release.sh
# BUILD SUCCESSFUL in 7m 25s
```

Bootstrap hooks diuji pada host dengan engine 2026.10.21:
`--tubeflow-compress-cover --tubeflow-mix-snapshot --tubeflow-validate-resume
--tubeflow-optional-audio --version` berhasil dan melaporkan versi 2026.10.21.

## Artefak 1.1.29/code43

| Artefak | Ukuran | SHA-256 |
|---|---:|---|
| ARM64 APK | 70.935.808 | `e3893e1183ef28539ecb5f09343e2abd18407cb24eac4dd92d00274d3dc390c2` |
| armeabi-v7a APK | 62.626.262 | `e509cd224460e8a1e45135b819bb8c27135715af768c30bd15fd1c691fae06a6` |
| x86_64 APK | 75.376.617 | `d50f1e9d90e621ea75ca0c23e082338fcea61b571d89ab8a21adb49b09e83b26` |
| AAB | 256.024.880 | `810269d6b516b5a33aa336d4fe7e94c395cc9dd6e3b0fc804e5353391cc04730` |

Metadata: `id.local.tubeflow`, versionCode 43, versionName 1.1.29,
compile/target SDK 37, non-debuggable. Sertifikat produksi SHA-256
`ebe3b40eb42c9b60fd044a011fc6a9e0cbfcde97c53accb2ea49934f4934dbd0`.

Audit ARM64: 257 ELF, 0 gagal alignment 16 KB; 13 kelas refleksi ZIP, 0 gagal;
`zipalign -c -P 16 4` OK; `apksigner` v2 verified. Engine dan bootstrap di APK
cocok dengan aset (`b7af77f6…`, `3bc177f7…`). AAB `jarsigner` **jar verified**;
audit native AAB 1.038 ELF, 0 gagal 16 KB.

## Verifikasi perangkat

Realme RMX1821, Android 10/API29, ARM64, halaman 4 KB. Pengujian memakai HP
fisik, tanpa uninstall atau reset data.

| Versi/skenario | Hasil |
|---|---|
| 1.1.28/code42 dipasang | APK signed SHA-256 `8d00a3f9…`; data lama tetap. |
| Panel Mesin media | Menampilkan `yt-dlp 2026.08.19 · bawaan aplikasi`. |
| Periksa pembaruan | Menemukan 2026.10.21 (2,9 MB), dialog konfirmasi tampil. |
| Pasang pembaruan | Selesai cepat; status `yt-dlp 2026.10.21 · pembaruan terverifikasi`; riwayat tetap. |
| Kembali ke bawaan | Dialog konfirmasi, lalu engine kembali ke `2026.08.19 · bawaan aplikasi`. |
| 1.1.29/code43 dipasang | `adb install -r` sukses; base.apk SHA-256 `e3893e11…` identik dengan artefak. |
| Data setelah update | Riwayat tetap 212 entri; file media lama utuh. |
| Engine bawaan 1.1.29 | `yt-dlp 2026.10.21 · bawaan aplikasi`; tidak ada override aktif. |
| Periksa pembaruan | `yt-dlp 2026.10.21 adalah versi terbaru yang tersedia.` |
| Blokir saat unduhan aktif | Panel menampilkan `Selesaikan atau hentikan unduhan aktif dulu.` |
| Unduhan 320 kbps pada 1.1.29 | Selesai; file 9.971.412 byte, durasi 247,824 dtk, 321.886 bps, sampul mJPG. Entri uji dihapus, riwayat kembali 212. |
| BUG-054 pada 2026.10.21 / 1.1.29 | Unduhan MP3 128 selesai: 4.023.060 byte, 247,824 dtk sama dengan metadata, decode penuh, sampul tertanam. Lirik tidak tersedia karena LRCLIB, bukan HTTP403. |

## Batas

- Rollback diuji pada 1.1.28; kode rollback tidak berubah di 1.1.29, tetapi
  tombol rollback pada build final belum ditekan ulang.
- Pemeriksaan jaringan gagal, checksum salah, traversal, dan rollback tanpa
  override diuji sebagai unit test, bukan dengan gangguan jaringan nyata.
- Runtime halaman 16 KB, ABI lain, dan Android selain 10 belum diuji.
- AAB dibangun dan diverifikasi, tetapi bundletool dan publikasi toko tidak
  dijalankan pada sesi ini.
- BUG-054 hanya terbukti untuk URL laporan; akar perbedaan 403 lama
  (engine/jaringan/IP) belum diisolasi, dan sumber DJ lain belum diuji.
- Lirik BUG-054 dan unduhan uji tetap gagal karena LRCLIB sedang bermasalah;
  kegagalan tersebut tidak boleh disamakan dengan kegagalan transfer media.
- Fault injection provider SAF/izin dicabut untuk perbaikan jurnal recovery
  belum dijalankan di perangkat.
