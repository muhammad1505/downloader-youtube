# Potong Status WhatsApp — 23 Oktober 2026

## Perilaku

- Aksi **Potong Status (maks 30 dtk)** ada di menu titik tiga kartu **video** pada Koleksi.
- Dialog memilih titik awal dan akhir; panjang klip 1–30 detik (video lebih pendek dipakai seluruhnya).
- Sumber H.264/AAC dipotong dengan **stream copy** (`-c copy`), tanpa encode ulang.
- Format lain (mis. WebM/VP9/Opus) dienkode ulang ke **H.264/AAC MP4** agar kompatibel WhatsApp.
- Setelah selesai, pemilih bagikan WhatsApp langsung dibuka; bila WhatsApp tidak terpasang,
  chooser Android dipakai. File asli tidak diubah.

## Teknis

- `core/StatusClip.kt` — penjepitan titik awal, durasi, dan argumen FFmpeg yang dapat diuji.
- `core/StatusClipper.kt` — menyalin `content://` ke cache, menjalankan `libffmpeg.so`,
  memeriksa hasil dengan `MediaMetadataRetriever`, dan berbagi lewat `FileProvider`.
- `ui/StatusClipDialog.kt` — slider awal dan akhir, progres, pembatalan, dan pemanggilan berbagi.
- `res/xml/status_clip_paths.xml` hanya membuka `cache/status-clips/`.
- Klip sementara disimpan maksimal 6 file/24 jam; tidak masuk galeri atau Koleksi.

## Batasan

- Stream copy memakai keyframe terdekat; awal bisa bergeser sedikit. Fallback encode dipakai
  bila durasi hasil di luar toleransi 1,75 detik.
- Belum ada crop 9:16, preset durasi 15/60 detik, atau aksi yang sama di Riwayat.
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

## Pembaruan 27 Oktober 2026 — titik awal dan akhir

Sebelumnya akhir klip selalu `awal + 30 detik`, sehingga pengguna hanya bisa menggeser
awal. Dialog kini memiliki dua slider pada timeline yang sama: **Awal** dan **Akhir**.
`StatusClip.range` menjepit keduanya ke dalam video, berurutan, dan berdurasi 1–30 detik.
Menggeser awal ke kiri saat klip sudah 30 detik ikut menggeser akhir agar batas tetap
dipenuhi; menggeser akhir melewati batas berhenti pada 30 detik dari awal.

`StatusClipper.clip` menerima kedua titik, menjepit ulang setelah durasi asli dibaca,
lalu meneruskan `-ss` dan `-t` (durasi = akhir − awal) ke stream-copy maupun fallback
encode. Pratinjau memakai rentang yang sama.

Verifikasi: `StatusClipTest` **9 tes** lulus dengan kotlinc/JUnit di luar Gradle
(rentang eksplisit, penjepitan, batas 1/30 detik, video pendek, argumen FFmpeg).
Build Gradle, lint, dan uji perangkat belum dijalankan pada sesi ini.

## Pembaruan lanjutan — satu bilah rentang (1.1.47, code 62)

Dua slider terpisah diganti satu bilah dengan dua pegangan (`ui/RangeScrubber.kt`). Menarik ujung
kiri/kanan hanya mengubah batas itu dan berhenti 1 detik sebelum ujung lainnya; menarik bagian
terpilih memindahkan seluruh klip tanpa mengubah panjangnya; ketukan di luar rentang menarik ujung
terdekat. Garis tipis menandai `awal + 30 detik` supaya jelas kenapa ujung kanan berhenti, dan
`StatusClip.range` tetap menjadi satu-satunya penjepit 1–30 detik yang dipakai dialog. Pratinjau
memakai rentang yang sama; tidak ada perubahan pada `StatusClipper` atau perintah FFmpeg.

Geometri geser dipisah menjadi fungsi murni (`rangeDragModeAt`, `rangeDragSelection`,
`scrubberXFor`) dan diuji tanpa perangkat di `RangeScrubberGeometryTest`.

Verifikasi: `./gradlew testDebugUnitTest` — **566 tes, 0 gagal** (10 tes baru di
`RangeScrubberGeometryTest`). `lintDebug` tidak selesai dalam batas waktu sesi ini, jadi status
lint untuk perubahan ini belum diklaim. Uji perangkat (memegang dua pegangan, geser bagian tengah,
TalkBack, video vertikal) belum dijalankan.
