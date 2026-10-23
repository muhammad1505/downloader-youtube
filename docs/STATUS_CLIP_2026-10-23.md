# Potong Status WhatsApp — 23 Oktober 2026

## Perilaku

- Aksi **Potong Status (30 dtk)** ada di menu titik tiga kartu **video** pada Koleksi.
- Dialog memilih titik awal; panjang klip 30 detik atau sisa video bila lebih pendek.
- Sumber H.264/AAC dipotong dengan **stream copy** (`-c copy`), tanpa encode ulang.
- Format lain (mis. WebM/VP9/Opus) dienkode ulang ke **H.264/AAC MP4** agar kompatibel WhatsApp.
- Setelah selesai, pemilih bagikan WhatsApp langsung dibuka; bila WhatsApp tidak terpasang,
  chooser Android dipakai. File asli tidak diubah.

## Teknis

- `core/StatusClip.kt` — penjepitan titik awal, durasi, dan argumen FFmpeg yang dapat diuji.
- `core/StatusClipper.kt` — menyalin `content://` ke cache, menjalankan `libffmpeg.so`,
  memeriksa hasil dengan `MediaMetadataRetriever`, dan berbagi lewat `FileProvider`.
- `ui/StatusClipDialog.kt` — slider awal, progres, pembatalan, dan pemanggilan berbagi.
- `res/xml/status_clip_paths.xml` hanya membuka `cache/status-clips/`.
- Klip sementara disimpan maksimal 6 file/24 jam; tidak masuk galeri atau Koleksi.

## Batasan

- Stream copy memakai keyframe terdekat; awal bisa bergeser sedikit. Fallback encode dipakai
  bila durasi hasil di luar toleransi 1,75 detik.
- Belum ada crop 9:16, pilihan durasi 15/60 detik, atau aksi yang sama di Riwayat.
- Klip tidak disimpan permanen; fitur ini berfokus pada bagikan cepat ke Status.

## Bukti

- `./gradlew testDebugUnitTest`: **411 tes, 0 gagal**.
- `bash scripts/build_release.sh --max-workers=2`: **BUILD SUCCESSFUL 9m 49s**
  untuk **1.1.32 (code 46)**; lint debug/release **0 error**, 3 warning lama
  (akses media parsial, Gradle 9.8.2, pin ONNX 1.23.2).
- Audit native: 257 ELF, 0 gagal alignment 16 KB; audit ZIP reflektif: 13 kelas, 0 gagal.
- Perangkat Realme Android 10:
  - MP4 H.264/AAC 45 detik, mulai 0:05 → pemilih bagikan WhatsApp terbuka.
  - WebM VP9/Opus 40 detik → fallback encode selesai dan pemilih WhatsApp terbuka.
  - File uji dihapus kembali dari Downloads dan katalog.
