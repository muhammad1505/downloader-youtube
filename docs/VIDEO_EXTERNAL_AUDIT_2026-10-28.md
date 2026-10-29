# Audit pemutar video — berkas luar folder 1DM (28 Oktober 2026)

Uji pemutar video 1.1.49/code64 dengan enam MKV nyata yang **berada di luar folder unduhan
TubeFlow** (`/sdcard/1dmp/Videos/`, diindeks MediaStore sebagai `1DMP/Videos/`). Berkas dibuka
lewat "Buka dengan" (`ACTION_VIEW`), persis seperti dari pengelola berkas atau unduhan 1DM.

## Lingkungan dan metode

- Realme RMX1821, Android 10, arm64-v8a, halaman memori 4 KB; video 858×482 (480p) AVC dalam MKV,
  23:10–24:30 per episode.
- Perangkat dikendalikan dari host: intent `am start`, `uiautomator dump` (hanya andal saat video
  dijeda karena jendela jarang idle saat memutar), `screencap`, `dumpsys audio`, `logcat`,
  `dumpsys window`, dan `/proc/<pid>/status`.
- Ketuk-dua kali diinjeksi dengan dua `input tap` berjeda 120 ms (satu `input` butuh ±410 ms,
  terlalu lambat untuk jendela 300 ms); `sendevent` ditolak SELinux untuk shell.

## Hasil yang terverifikasi

| Berkas | Antrean | Durasi | Buka + putar | Resume | Dekoder |
| --- | --- | --- | --- | --- | --- |
| Kuramanime-AOASHI_S2-04-480p | 1 dari 6 | 24:30 | ya | ya | HW MTK AVC |
| Kuramanime-BLCH_S2P4-10_END-480p-BGlobal | 2 dari 6 | 23:10 | ya | ya | HW MTK AVC |
| Kuramanime-OVERGRD-05-480p | 3 dari 6 | 23:40 | ya | ya | HW MTK AVC |
| Kuramanime-PSYREN-04-480p | 4 dari 6 | 23:40 | ya | ya | HW MTK AVC |
| Kuramanime-TENKAT_S3-05-480p-BGlobal | 5 dari 6 | 23:42 | ya | ya | HW MTK AVC |
| Kuramanime-YOZASKS_S2P2-03-480p | 6 dari 6 | 24:20 | ya | ya | HW MTK AVC |

Antrean folder terbentuk dari enam berkas (urut alfabetis) dengan judul, "n dari 6", dan penanda
"Sedang diputar" di lembar Antrean. Tidak ada `FATAL EXCEPTION`, `PlaybackException` yang tak
tertangani, atau kegagalan dekoder selama ±25 menit pengujian; tidak ada berkas yang jatuh ke
dekoder software.

Perilaku lain yang lulus:

- **Seek.** Geser horizontal 400 px di area video = +63 detik (rumus 120 detik per lebar layar);
  ketuk-dua kali kanan = **tepat +10 detik** (8:56 → 9:06); scrubber saat kontrol tampil melompat
  ke posisi tarikan (23:40/23:40 untuk tarikan ke ujung kanan).
- **Jeda/lanjut, pindah video.** Tombol next memindah BLCH → OVERGRD (3 dari 6, lanjut 0:45);
  ketuk item di lembar Antrean memindah ke AOASHI (1 dari 6, lanjut 0:58).
- **Akhir berkas.** Memutar dari 23:40/23:40 → berkas **tamat** dan **lompat otomatis** ke video
  berikutnya (PSYREN 4 dari 6, lanjut 0:53); posisi berkas yang sudah tamat dibersihkan, dibuka
  ulang mulai 0 (bukti: OVERGRD dibuka ulang pada 0:11, bukan 23:40).
- **Picture-in-Picture.** HOME saat memutar masuk PiP otomatis (bukan jeda), audio tetap jalan,
  `am start` mengembalikan layar penuh; keluar PiP benar (`mLastReportedPictureInPictureMode=false`).
- **Rotasi saat jeda.** Layar berputar tanpa frame setengah/ hitam; posisi tidak berubah; bilah
  kontrol dan chip tetap dapat dijangkau.
- **Subtitle.** Track fansub bernama "Kuramanime" dipilih otomatis; siklus chip bekerja
  ("Subtitle Kuramanime" → "Subtitle nonaktif" → kembali) dan teks subtitle tampil di layar.
- **Berkas rusak.** 3 MB data acak bernama `.mkv` → pesan "Berkas video rusak atau kontainernya
  tidak dikenali." + saran membuka di pemutar lain, tombol **Coba lagi** (aman, mengulang galat)
  dan **Tutup** (kembali ke aplikasi, pemutar dilepas: tidak ada AudioTrack baru setelahnya).
- **Aksesibilitas statis.** Saat kontrol tampil, pohon aksesibilitas berisi label lengkap
  ("Tutup pemutar", "Antrean video", "Opsi pemutar", "Kecepatan 1×…", "Subtitle …", "Kunci kontrol
  pemutar", "Video sebelumnya/berikutnya", "Putar di jendela kecil", "Orientasi …").

## Temuan

### T1 — "Rasio Penuh" meregangkan gambar, bukan memotong (UX)

`VideoViewport.size()` mengembalikan ukuran viewport apa adanya untuk `VideoAspect.FILL`, dan
`PlayerView` selalu memakai `RESIZE_MODE_FILL` (`ui/VideoPlayerScreen.kt`); jalur efek juga memakai
`Presentation.LAYOUT_STRETCH_TO_FIT`. Akibatnya "Penuh" **melar** (distorsi geometri) sementara
"Zoom" justru mode potong (`LAYOUT_SCALE_TO_FIT_WITH_CROP`). Pada layar 2,11:1 dengan video 16:9,
distorsi ±19%. Bukti: pada frame sama, mode "Penuh" mengisi seluruh lebar (kotak isi 1476 px, tanpa
bilah) sementara "Pas" menyisakan bilah sisi.
Saran: ganti label "Penuh" menjadi "Regang", atau jadikan "Penuh" = potong
(`LAYOUT_SCALE_TO_FIT_WITH_CROP`) dan sisakan "Zoom" untuk perbesaran potong.

### T2 — Kebocoran SurfaceTexture pada setiap pembukaan berkas (performa)

Setiap `ACTION_VIEW` menjalankan `open()` → `closeInternal()` → `openInternal()`, yang **selalu
membuat ExoPlayer baru** (`playerVersion++`) sehingga layar membuat `PlayerView`/permukaan baru.
Permukaan lama dibersihkan hanya oleh GC: log berisi 2–3 × `W/System: A resource failed to call
dispose.` per pembukaan, berpasangan dengan `~BufferQueueCore` (SurfaceTexture), dan VmRSS naik
173 MB → 213 MB setelah tujuh perpindahan (VmHWM tetap 241 MB, jadi bukan kebocoran tanpa batas,
tetapi beban GC dan memori naik).
Saran: pakai ulang ExoPlayer yang hidup untuk permintaan "ganti antrean" (`setMediaItems`) dan
hanya buat ulang saat mode dekoder/software fallback berubah; atau lepas permukaan lama eksplisit
saat `onRelease`.

### T3 — Baris `Style:` ASS dengan 24 nilai dilewati Media3 (batasan pustaka)

Dua contoh dari berkas uji: `Style: flashback,…` dan `Style: Watermark360,…` →
`W/SsaStyle: Skipping malformed 'Style:' line (expected 23 values, found 24)`. Gaya baris itu
(font/ukuran/posisi kustom) hilang sehingga dirender memakai gaya bawaan pemutar. Ini perilaku
parser Media3, bukan kode TubeFlow; catat agar tidak diburu sebagai bug aplikasi.

### T4 — Kontrol tersembunyi menyisakan nol node aksesibilitas

Saat video diputar dan kontrol tersembunyi, dump aksesibilitas kosong: tidak ada node fokus,
tidak ada petunjuk bahwa satu ketukan akan menampilkan kontrol. Pengguna TalkBack perlu
"menebak" dulu. Uji dengan TalkBack aktif dan penambahan node berisi petunjuk (mis. "Ketuk untuk
menampilkan kontrol") belum dilakukan pada batch ini.

### T5 — Lembar Antrean tidak menggulir ke video yang sedang diputar

Dibuka saat posisi 5 dari 6, daftar tetap mulai dari nomor 1; di lanskap hanya dua baris terlihat
sehingga pengguna harus menggulir sendiri untuk menemukan penanda "Sedang diputar". Saran:
posisikan daftar pada item aktif saat lembar dibuka.

## Perbaikan sesudah audit (belum dirilis)

Semua temuan di atas yang dapat diperbaiki di kode sudah dikerjakan dan diuji ulang di perangkat
yang sama. Build debug dipasang ulang; 583 tes Kotlin, lint (0 error/2 peringatan lama), dan
`assembleDebug` lulus.

| Temuan | Perubahan | Bukti sesudah perbaikan |
| --- | --- | --- |
| "Penuh" meregangkan gambar (T1) | `VideoAspect` menyimpan faktor zoom; `VideoViewport.size()` menskalakan seragam (`max`) untuk Penuh/Zoom dan `Presentation` memakai `LAYOUT_SCALE_TO_FIT_WITH_CROP`. Surface hanya diperbesar untuk Zoom. | Potret 720×1520: bingkai penuh tanpa bilah hitam, proporsi wajah normal. Lanskap 1520×720: penuh, tepi terpotong. `VideoViewportTest` mengunci rasio 16:9 setelah crop. |
| SurfaceTexture menumpuk tiap berkas (T2) | ExoPlayer dipakai ulang selama mode renderer dan penguat volume sama (`setMediaItems`), hanya permukaan yang dipertahankan; `onRelease` melepas pemutar dari permukaan lama. | VmRSS awal 196,6 MB; 7 perpindahan 222,4 MB; dua putaran tambahan (21 perpindahan) berakhir 225,7 → 223,5 MB — dataran, bukan kenaikan per berkas (audit lama: 173→213 MB setelah 7). |
| Antrean tidak menunjuk video aktif (T5) | Lembar Antrean membuka penuh (`skipPartiallyExpanded`), daftar memakai `initialFirstVisibleItemIndex = index` dan mengisi tinggi lembar (`weight(1f)`) sehingga menggulir sendiri. | Potret dan lanskap: baris "7. … ✓ Sedang diputar" langsung terlihat tanpa menggulir. |
| Deretan aksi memakan ruang dengan caption | `QuickIcon` kembali bulat tanpa label tetap; nilai aktif hanya lewat `content-desc` (TalkBack) dan pemberitahuan setelah diketuk, warna aksen menandai nilai non-bawaan. | Dump aksesibilitas: tujuh node tanpa teks, hanya `content-desc`; bilah lebih pendek dan ikon 36–19 dp. |
| Rotasi/ganti rasio saat jeda menyisakan frame separuh/sudut | `PlayerView` dibuat ulang setiap geometri permukaan berubah (`key(playerVersion, surfaceWidth, surfaceHeight)`), lalu frame diminta setelah jeda singkat (`onSurfaceResized` → `seekTo(pos−1)`), bukan membuka ulang pemutar segera. | Potret → lanskap → potret saat dijeda (Zoom): frame penuh di tengah. Ganti Zoom → Penuh saat dijeda: bingkai penuh tanpa bilah. |

Catatan yang **belum** dikerjakan pada batch ini: posisi subtitle masih mengikuti jendela
`PlayerView` (layar penuh saat efek/Presentation aktif), jadi pada rasio "Pas" teks bisa berada di
bilah hitam, bukan di tepi gambar; dan lembar bawah di lanskap masih tampil menyempit (jendela
dialog tidak ikut melebar setelah rotasi) meski daftar dan gulirannya sudah benar.

## Batasan audit ini

- Enam berkas semuanya AVC 480p dalam MKV; codec lain (HEVC/AV1/10-bit), berkas >1 jam, dan audio
  multitreng belum diuji. Hanya satu trek audio dan satu trek subtitle per berkas.
- TalkBack aktif, matriks font besar/tema terang, dan uji ketahanan baterai tidak dijalankan.
- Semua pengujian memakai biner rilis 1.1.49 yang sudah terpasang; tidak ada perubahan kode pada
  batch ini, jadi tidak ada build ulang.
- Perangkat uji dalam keadaan **lanskap dengan auto-rotate mati** (`user_rotation=1`,
  `accelerometer_rotation=0`); bukan akibat aplikasi, tetapi memengaruhi koordinat uji.
- Volume media perangkat dikembalikan ke nilai awal (2) dan berkas uji (folder
  `Download/TubeFlowUji`) dihapus setelah pengujian.
