# Perbaikan Mix dan duplikat — 10 Oktober 2026

Menangani laporan pengguna pada Mix `https://www.youtube.com/watch?v=3J1Opi7MVHw&list=RD3J1Opi7MVHw&start_radio=1` serta unduhan video yang sama melalui bentuk URL berbeda. Temuan terkait: BUG-018/019 di [daftar bug](BUG_FINDINGS.md).

## Perubahan

- `Selection.playlistInspectionUrl`: Mix mempertahankan ID video awal, ID playlist dan konteks radio; playlist biasa tetap menggunakan URL playlist kanonis. Identitas metadata di UI tetap mengikuti input pengguna.
- `Engine.inspectPlaylist`: flag bootstrap `--tubeflow-mix-snapshot` hanya diberikan untuk playlist `RD...`. `install_mix_snapshot` menggunakan panel awal extractor yt-dlp tanpa meminta continuation. Playlist biasa mempertahankan pagination dan batas 500 entri.
- Kartu Mix menyebut **antrean awal**, dengan penjelasan bahwa daftar dinamis dapat berbeda dari akun YouTube pengguna. Tidak ada angka 25 yang dipaksakan.
- `Selection.videoId` dan `DownloadJob.matchesOutput`: bentuk watch, youtu.be, Shorts, live, YouTube Music/mobile dibandingkan berdasarkan ID video. Parameter konteks diabaikan; semua opsi keluaran tetap dibandingkan. URL lama di riwayat ikut diperiksa tanpa perubahan schema atau penghapusan data.
- `DownloadViewModel.submit`: antrean aktif/dijeda dan hasil selesai yang masih tersedia melindungi dari duplikat. Retry job gagal juga memeriksa hasil selesai milik job lain. File yang sudah tidak tersedia dapat diunduh kembali.

ZIP yt-dlp resmi tetap versi `2026.08.19`, SHA256 `1fa6733c37ea6fb51c99ad8fe785e7b7e5f3246c9b980230329d4fb72ed8d4d6`. Bootstrap script dan asset identik. Hook bergantung pada API internal extractor yang dipin; jalankan regresi saat memperbarui engine.

## Regresi host

- Python: **37 tes lulus**, termasuk extractor sebenarnya untuk panel Mix 25/8/50 entri tanpa continuation, panel kosong, pagination non-radio, dan konsumsi flag bootstrap sendiri maupun bersama kompresi sampul.
- Kotlin: **55 tes lulus**, termasuk enam regresi tambahan yang mencakup alias URL, host/path tidak aman, riwayat lama pada status antre/berjalan/dijeda/selesai, opsi berbeda, ID case-sensitive, seed Mix dan playlist biasa.
- Build debug/release dan lint debug/release **lulus, 0 error/0 warning lint**. Audit native debug/release: ARM64 dan ARMv7 masing-masing 255 ELF, x86_64 257 ELF, tanpa kegagalan alignment 64-bit. Audit refleksi ZIP release: 13 kelas per ABI, 0 kegagalan.

Perintah pemeriksaan dari root repository:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew -PuseGoogleMirror=true \
  -Pkotlin.compiler.execution.strategy=in-process \
  -Dorg.gradle.jvmargs='-Xmx2g -Dfile.encoding=UTF-8' --max-workers=2 \
  testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/audit_native.py <debug-atau-release-APK>
python3 scripts/audit_release_zip_fields.py <release-APK>
bash scripts/make_test_apk.sh arm64-v8a
```

Gradle selesai dalam 12m29s. Deprecation internal AGP yang tercatat pada laporan toolchain sebelumnya tetap terpisah dari hasil lint; tidak ada perubahan dependensi pada batch ini.

## APK dan verifikasi perangkat — berhasil

APK `app/build/outputs/apk/rc/TubeFlow-arm64-v8a-testing.apk`, 62.819.030 byte, kunci testing lokal; SHA256 **`75b62581d62da04588f16fb3ed15aa9b26ade278843f5f59e20bf1f0c0ff2b40`**. Tanda tangan v3 dan zipalign lulus. Terpasang melalui installer bawaan + pemindaian Play Protect; APK terpasang ditarik kembali dan hash-nya cocok. Tidak ada uninstall/clear data.

Perangkat **Realme RMX1821, Android 10/API 29, ARM64**, tanpa emulator. Riwayat sebelum dan setelah pembaruan tetap 3 entri (2 MP3 128 kbps dengan sampul + 1 MP4 360p).

| Skenario | Langkah dan hasil |
| --- | --- |
| Mix contoh | Bagikan URL pengguna ke TubeFlow, tunggu metadata, tekan **buka sebagai playlist**. Kartu menampilkan **25 video dalam antrean awal**; entri pertama ID `3J1Opi7MVHw`, judul sesuai metadata video awal. Tidak ada aksi download playlist. |
| Alias link pendek | Input `https://youtu.be/aqz-KE-bpKQ?si=tubeflow-duplicate-check`, pilih MP3 128 + sampul, tekan Download audio. Muncul **File ini sudah pernah diunduh.** Antrean kosong. |
| Alias YouTube Music | Input `https://music.youtube.com/watch?v=aqz-KE-bpKQ&t=30`, pilih opsi sama. Pesan penolakan identik; tidak membuat job/output baru. |
| Retensi | Riwayat akhir tetap **3 entri**; daftar file di `Download/TubeFlow/Denny-Caknan/` identik dengan sebelum pengujian (3 file). File MP3 pembanding lama berakhiran `(1)` tidak dihapus otomatis. |
| Log aplikasi | Tidak ditemukan `FATAL EXCEPTION`, `SQLiteException`, atau kegagalan verifikasi Room pada logcat PID aplikasi yang diperiksa. |

Bukti: [Mix 25 entri](images/mix-initial-panel-2026-10-10.png), [penolakan link pendek](images/mix-duplicate-rejected-2026-10-10.png), [penolakan YouTube Music](images/mix-music-rejected-2026-10-10.png). Snapshot XML dan log host berada di `/tmp/tubeflow-mix-*`; screenshot permanen tersedia dalam repository.

## Batas perilaku

Jumlah awal mengikuti respons YouTube untuk sesi mesin aplikasi, yang tidak diberi cookies akun YouTube pada pengujian ini. **25** telah diamati pada link contoh di HP; angka itu bukan batas yang dipaksakan atau jaminan untuk setiap Mix. Antrean pada aplikasi YouTube dengan akun pengguna tidak dibandingkan satu per satu.

Uji perangkat menggunakan Realme Android 10 fisik, tanpa emulator. Riwayat dan tiga file uji sebelumnya dipertahankan. Penghapusan file, duplikat aktif/dijeda pada perangkat, dan download ulang setelah file hilang memerlukan skenario runtime tersendiri; regresi identitas host tidak membuktikan seluruh alur Android tersebut.
