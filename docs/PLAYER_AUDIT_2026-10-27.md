# Audit pemutar audio & video — 27 Oktober 2026

Audit UX, performa, dan kestabilan dua pemutar (lagu dan video) beserta antrean video.
Semua temuan di bawah berasal dari pengujian di perangkat nyata (Realme RMX1821, Android 10),
bukan hanya pembacaan kode.

## Temuan

### 1. Pemutar lagu mati setelah memutar video (kestabilan, berat)

**Gejala.** Putar satu lagu, lalu buka video apa pun dari Koleksi dan tutup lagi. Mini player
menampilkan "Audio tidak dapat diputar. Periksa file atau codec perangkat." dan lagu itu tidak
bisa dilanjutkan tanpa dipilih ulang dari Koleksi.

**Bukti (logcat).**

```
pause called in state 8, mPlayer(0x7602f609c0)
MediaPlayerNative: error (-38, 0)
TubeFlow-audio: MediaPlayer what=-38 extra=0; berhenti
```

**Sebab.** Membuka video memanggil `PlayerViewModel.pause()`. Saat itu pemutar lagu sudah berhenti
dalam keadaan *prepared* (bukan *started*), dan `MediaPlayer.pause()` hanya sah pada keadaan
started. Platform menjawab `error (-38, 0)` lewat `onErrorListener`, lalu aplikasi memperlakukannya
sebagai kegagalan berkas: engine dilepas dan pesan error ditampilkan.

**Perbaikan.** `core/AudioPausePolicy.kt` baru memutuskan kapan `pause()` boleh dikirim
(`prepared && isPlaying == true`), dan `AudioErrorPolicy.benignWhilePaused()` membuat error
(-38) saat pemutar berhenti tidak lagi menghentikan sesi — hanya dicatat sebagai perintah yang
diabaikan.

### 2. Label pengaturan cepat pemutar video saling menabrak (UX)

Tujuh–delapan tombol pengaturan cepat memakai lebar mengikuti isi, sehingga label seperti "Bass",
"Ruang", atau "Penuh" menimpa ikon tombol sebelahnya dan teksnya tidak lagi terbaca utuh.

**Perbaikan.** Deretan memakai `Modifier.weight(1f)` (lebar dibagi rata, semua tombol muat tanpa
digeser) dengan label satu baris yang dipangkas elipsis. Tombol dekoder dilepas dari deretan cepat
karena sudah ada di lembar "Opsi pemutar" — pengaturan lain tetap satu ketuk.

**Tindak lanjut 28 Oktober 2026 (1.1.51).** Dekoder dikembalikan sebagai ikon di panel pengaturan;
ketukannya membuka pilihan Otomatis/Perangkat/Software. Panel kini dapat dibuka-tutup dari top bar
dan membungkus ikon ke beberapa baris, sehingga semua aksi tetap terjangkau tanpa memadatkan satu
baris.

### 3. Keterbacaan kontrol di atas video terang (UX)

Gradien gelap bilah atas berhenti tepat di tinggi bilah itu, sehingga judul video dan "2 dari 3"
berada di bagian gradien yang hampir transparan; pada adegan terang teks nyaris tak terbaca.

**Perbaikan.** Gradien dipindah ke kolom pembungkus yang menutupi bilah atas **dan** deretan
pengaturan cepat (0,80 → 0,42 → transparan), sehingga judul, "x dari y", dan label nilai tetap
terbaca.

### 4. Antrean video kurang informatif (UX)

Lembar "Antrean video" hanya menampilkan judul dan tanda centang, tanpa posisi sekarang, dan bilah
atas adalah satu-satunya tempat melihat "3 dari 3".

**Perbaikan.** Judul lembar menjadi "Antrean video · 3 dari 3" (bila lebih dari satu video) dan
video yang sedang diputar diberi label "Sedang diputar", sama seperti antrean lagu.

### 5. Frame video tertinggal di sisi layar setelah rotasi saat dijeda (UX)

Memutar layar ketika video sedang **dijeda atau sudah berakhir** menyisakan frame lama dengan
geometri lama: video menempel di kiri layar dan separuh layar hitam, sampai pemutaran dilanjutkan.
Sebabnya permukaan `SurfaceView` berukuran baru hanya akan terisi ketika pemutar menggambar frame
berikutnya — dan pemutar yang dijeda tidak menggambar apa pun.

**Perbaikan.** `VideoPlayerViewModel.setViewport()` sekarang membuka ulang pemutar yang sedang
berhenti saat ukuran permukaan berubah: video yang sudah berakhir dibuka ulang 1 ms sebelum akhir
tanpa mulai memutar (seek saja tidak menggambar apa pun pada state berakhir), sedangkan sesi
dekoder software yang dijeda dibuka ulang di posisi yang sama. Pemutar yang sedang berjalan tidak
diganggu karena ia menggambar frame berikutnya sendiri.

**Catatan percobaan (agar tidak diulang).** Tiga percobaan sebelum ini gagal di perangkat dan
sudah dibuang: (1) membiarkan permukaan menyesuaikan sendiri — frame menempel di separuh layar;
(2) seek langsung dari `onSizeChanged` — terlalu dini, permukaan belum berukuran baru;
(3) seek yang ditunda 300 ms — pemutar yang dijeda/berakhir tidak menggambar ulang saat di-seek,
sehingga hasilnya kadang benar kadang tidak (terbukti: satu frame terakhir tetap tampil hanya
sebagian di layar lanskap).

### 6. Pemulihan decoder dan posisi setelah error (tindak lanjut 28 Oktober 2026)

Beberapa kegagalan putar berasal dari decoder yang direbut sistem dan dari tombol
**Coba lagi** yang mengulang renderer yang baru saja gagal. Perubahannya:

- `ERROR_CODE_DECODING_RESOURCES_RECLAIMED` sekarang dianggap kegagalan codec, jadi video yang
dekodernya direbut aplikasi lain otomatis dibuka ulang dengan FFmpeg/dav1d.
- Fallback otomatis mempertahankan niat pengguna: video yang sedang diputar tetap diputar,
sedangkan video yang dijeda tetap dijeda setelah pemutar dibangun ulang.
- Setelah fallback software gagal, **Coba lagi** membuat pemutar baru dengan jalur Otomatis
(hardware lebih dulu), bukan mengulang renderer software yang sama. Mode sesi software juga tidak
lagi direset saat pindah item karena ExoPlayer belum mengganti renderer.
- Posisi UI yang terakhir diketahui tidak lagi hilang ketika ExoPlayer melaporkan posisi 0 setelah
error; fallback, ganti decoder, dan simpan lanjut-tonton memakai posisi yang aman.
- Dari layar error mode **Software** tersedia tombol **Coba dekoder otomatis**, sehingga pengguna
tidak terkunci pada software yang tidak mendukung berkas tersebut.
- Pemilih subtitle tidak lagi memicu PiP di Android 10–11 saat `onUserLeaveHint` ikut terpanggil.

Verifikasi host: `:app:testDebugUnitTest` **587 tes, 0 gagal/error/skip**. Ini belum diuji pada
perangkat; keberhasilan decoder tetap bergantung pada codec dan dukungan GPU/MediaCodec tiap HP.

## Yang sudah bagus dan tidak diubah

- **Performa.** Pemutaran video 720p: 68 frame UI/12 detik, **1 frame janky (1,47 %)**, p50 7 ms,
  p95 13 ms; MediaCodec melaporkan `mRenderFrameCnt = 30 fps` tanpa frame jatuh. Pemutar lagu
  saat memutar lagu + lirik: 169 frame/14 detik, **1 frame janky (0,59 %)**, p50 9 ms, p95 12 ms.
  Hanya fling sintetis cepat di halaman lirik yang menaikkan jank (11,4 %, p95 17 ms — masih di
  batas 16,7 ms untuk mayoritas frame); gesekan tangan biasa tidak sesering itu.
- **Kestabilan.** Tidak ada `FATAL EXCEPTION`/ANR selama seluruh sesi audit (±1 jam, puluhan kali
  buka/tutup pemutar, rotasi, ganti video).
- **Antrean video berfungsi.** Dibuka dari Koleksi berisi semua video yang dapat diputar pada urutan
  daftar, mulai dari yang diketuk ("2 dari 3"); tombol berikut/sebelumnya bekerja, video berakhir
  otomatis lanjut ke video berikutnya, dan "sebelumnya" memulai ulang bila sudah lewat 5 detik.
- Antrean lagu (cari, acak/ulangi, bersihkan, ubah urutan), mini player, lirik, sleep timer, dan
  equalizer berjalan seperti sebelumnya.

## Verifikasi

- `./gradlew testDebugUnitTest`: **582 tes, 0 gagal** (4 tes baru: `AudioPausePolicyTest` 3,
  `AudioErrorPolicyTest` 1).
- `bash scripts/build_release.sh --max-workers=2`: **BUILD SUCCESSFUL** (10–11 menit), lint 0 error,
  APK + AAB bertanda tangan kunci produksi, dipasang ulang di perangkat tiap iterasi.
- Uji perangkat Realme RMX1821, Android 10 (1.1.49/code64):

| Uji | Hasil |
| --- | --- |
| Lagu diputar, video dibuka 9 detik, video ditutup | Mini player **tanpa pesan error**, lagu lanjut dengan satu ketukan; logcat bersih (tidak ada lagi `pause called in state 8` / `error (-38, 0)`) |
| Deretan pengaturan cepat video | 7 tombol sejajar, label `1× · — · Bass · Ruang · Mati · Foto · Penuh` tidak menabrak ikon tetangga |
| Scrim bilah atas | Latar judul 40 % lebih gelap (terukur: bar SMPTE 191 → 114); sebelumnya tidak tergelapkan sama sekali |
| Lembar antrean video | Judul "Antrean video · 3 dari 3" dan label "Sedang diputar" pada entri aktif |
| Rotasi sambil **berakhir** (potret → lanskap, lanskap → potret) | Video penuh layar di kedua arah (terukur 1519/1520 px dan baris 44–1472/1520), tanpa chip "Dilanjutkan dari" palsu |
| Rotasi sambil **dijeda** | Video penuh layar di kedua arah |
| Berkas uji yang dibuat untuk audit | Dihapus kembali dari `Download/TubeFlow`; Koleksi video kembali kosong |

## Build rilis 1.1.49/code64 — 28 Oktober 2026

`bash scripts/build_release.sh --no-daemon --max-workers=2` atas revisi `48ba14d`: **BUILD SUCCESSFUL**
(3m 31s; 9 task dijalankan, sisanya sudah mutakhir), APK×3 + AAB bertanda tangan kunci produksi
yang sama (signer SHA-256 `ebe3b40e…`, non-debuggable, `id.local.tubeflow` 1.1.49/code64, targetSdk 37).

| Artefak | Byte | SHA-256 |
| --- | --- | --- |
| app-arm64-v8a-release.apk | 76.419.310 | `16ef745d566f1d65e2d2696fb94f15f467e9e27236dbe879e67b553c80ab9f9a` |
| app-armeabi-v7a-release.apk | 67.948.902 | `d69a541a322451572db4fc2b01b1031200580965b3ba7407ded00a1edce338bb` |
| app-x86_64-release.apk | 81.690.904 | `90759249fb929846cd737064d7b0be327905f137b74482397a161b389711173b` |
| app-release.aab | 273.369.701 | `9c086374e7b5a4890d34265a621606586cb8ac7d05c9c1a0fa95dab665e3ca37` |

- Kotlin **582 tes lulus** (0 gagal/terlewat), Python **98 tes `OK`**, lint debug/release **0 error**, 3 warning
  (SelectedPhotoAccess dan GradleDependency onnxruntime yang di-pin — keduanya lama — plus
  AndroidGradlePluginVersion: Gradle 9.8.2 tersedia).
- `audit_native.py`: 262/262/264 ELF per APK dan 1.058 ELF di AAB, **0 gagal** 16 KB PT_LOAD.
- `audit_release_zip_fields.py`: **13 kelas ZIP reflektif, 0 gagal** pada tiap APK.
- `apksigner verify --verbose --print-certs`: v2 verified, 1 penanda tangan, sertifikat produksi;
  `zipalign -c -P 16 -v 4`: OK pada tiap APK. AAB: `jarsigner -verify` → **"jar verified."**
  dengan sertifikat yang sama.
- Dipasang sebagai update di HP uji (Realme RMX1821, Android 10, arm64, page 4 KB) dengan
  `adb install -r` tanpa uninstall/clear data; versi terpasang 1.1.49/code64 dan `base.apk` yang
  ditarik dari HP **identik byte-per-byte** dengan artefak ARM64 (76.419.310 byte, SHA-256
  `16ef745d…`, sertifikat `ebe3b40e…`); aplikasi terbuka tanpa `FATAL EXCEPTION`.
- Versi tidak dinaikkan: 1.1.49/code64 memang target rilis batch ini.
- Bundletool tidak tersedia di host ini, jadi `bundletool validate` tidak dijalankan — sama seperti
  beberapa rilis sebelumnya.

## Catatan

- Perintah pengaturan rotasi sistem ditolak untuk shell pada perangkat uji, sehingga tata letak
  pemutar lagu dalam mode lanskap tidak bisa diverifikasi di perangkat; jalur itu tidak berubah
  pada audit ini.
- Berkas uji `Uji Video Satu/Dua/Tiga.mp4` dibuat untuk audit dan dihapus kembali setelahnya.
