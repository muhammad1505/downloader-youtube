# Validasi perangkat — alur Spotify (track, album, playlist)

Panduan ini dipakai untuk memvalidasi alur Spotify di HP fisik. **Status: sebagian
tervalidasi pada 24 Oktober 2026** (hasil di §9; kasus yang belum diuji tetap
dicantumkan). Bukti host: 427 tes Kotlin (0 gagal), 98 tes Python `OK`, lint
debug 0 error/3 warning lama, dan parser diuji memakai markup embed Spotify asli
yang diambil 23 Oktober 2026. Tes host tidak membuktikan perilaku Android,
jaringan nyata, pencarian yt-dlp di perangkat, maupun tampilan Compose.

Cakupan: tempel tautan `open.spotify.com/track|album|playlist/…`, pratinjau
metadata, pencocokan YouTube per lagu, pemilihan lagu, antrean batch, tag/
sampul, folder koleksi, pembatalan, kegagalan jaringan, dan batas halaman embed.

## 1. Prasyarat

- HP Android 10 (API 29) atau lebih baru; sebutkan model, versi Android, ABI,
  dan ukuran halaman memori di laporan.
- APK release/debug bertanda tangan dengan kunci yang sama seperti versi
  terpasang. Catat `versionName`, `versionCode`, dan SHA-256 APK **sebelum**
  instalasi.
- Jaringan yang cukup untuk Spotify dan YouTube. Siapkan mode pesawat untuk
  skenario gagal.
- Akun Spotify tidak diperlukan (metadata dibaca dari halaman embed publik),
  tetapi tautan album/playlist harus publik — bukan **Liked Songs** atau
  playlist privat.
- Konten uji milik sendiri atau yang diizinkan untuk diunduh. Gunakan satu
  album kecil (≤ 10 lagu), satu playlist kecil (≤ 20 lagu), satu playlist besar
  (> 100 lagu), dan satu track tunggal yang sudah dikenal.
- Catat keadaan awal: jumlah entri Riwayat, isi folder `Downloads/<folder>`,
  dan setelan **Hapus otomatis riwayat** agar penilaian akhir tidak salah
  membaca retensi otomatis.

## 2. Persiapan bukti

Simpan bukti di folder privat di luar Git (mis. `~/tubeflow-evidence/spotify/`)
karena screenshot/log memuat URL dan judul pribadi.

```sh
# Identitas build
adb shell dumpsys package id.local.tubeflow | grep -E "versionName|versionCode"
sha256sum app/build/outputs/apk/<varian>/<file>.apk

# Instalasi (atau lewati jika sudah terpasang versi yang sama)
adb install -r app/build/outputs/apk/<varian>/<file>.apk

# Log hanya untuk proses aplikasi; catat PID
adb logcat -c
adb shell pidof -s id.local.tubeflow
adb logcat -v threadtime --pid=$(adb shell pidof -s id.local.tubeflow) > evidence/logcat.txt

# Snapshot teks UI (lebih kuat daripada screenshot untuk klaim teks/angka)
adb shell uiautomator dump /sdcard/spotify.xml
adb pull /sdcard/spotify.xml evidence/spotify.xml

# Screenshot / rekaman
adb exec-out screencap -p > evidence/spotify-<kasus>-<langkah>.png
adb shell screenrecord /sdcard/spotify-<kasus>.mp4
```

Penamaan bukti: `spotify-<ID kasus>-<langkah>.<ext>` (contoh
`spotify-S6-01-matching-cancel.png`). Setiap klaim di laporan akhir harus
menunjuk satu file bukti.

## 3. Matriks kasus wajib

Tandai tiap baris **Lulus / Gagal / Tidak diuji** dan tulis hasil yang diamati,
bukan hanya "OK".

### S1 — Track tunggal

0. Dari aplikasi Spotify, **Bagikan → TubeFlow** untuk track yang sama. Link
   harus mengisi composer dan memicu pembacaan otomatis seperti tempel manual.
1. Tempel tautan track. Selama ± 0,4 detik composer menampilkan status membaca;
   setelah itu muncul kartu Spotify dengan sampul, judul, artis, durasi, dan
   catatan **Sumber audio: YouTube**.
2. Pastikan daftar kandidat muncul (maksimal 5), kandidat teratas terpilih, dan
   tiap baris memuat judul video, channel, durasi, serta selisih durasi.
3. Pilih kandidat, atur **MP3 128** dan lirik, lalu **Download audio**.
4. Setelah selesai, periksa kartu Riwayat: label platform **Spotify**, judul dan
   artis Spotify (bukan judul video YouTube), sampul Spotify.
5. Verifikasi file (lihat §5): tag judul/artis, nama file, folder, tidak ada
   thumbnail video YouTube yang tersemat.

### S2 — Album kecil

1. Tempel tautan album. Kartu menampilkan **Album**, jumlah lagu, dan total
   durasi.
2. Semua lagu yang tersedia terpilih otomatis; lagu `isPlayable=false`
   (jika ada) tidak terpilih dan diberi keterangan **Tidak tersedia di Spotify**.
3. Kosongkan beberapa lagu, tekan **Cocokkan N lagu**. Progres berjalan
   `x/y`, lalu baris berubah menjadi judul video YouTube + selisih durasi.
4. Tekan **Antrekan N lagu**. Notifikasi menyebut jumlah yang diantrekan dan
   folder; lagu yang belum cocok dilewati dengan hitungan.
5. Di HP, cek Riwayat: satu entri per lagu, semuanya platform Spotify, dan file
   masuk subfolder slug nama artis album (atau folder utama bila slug tidak
   valid).

### S3 — Playlist kecil

Ulangi S2 untuk playlist. Folder harus memakai judul playlist, bukan nama
kurator (`Spotify`). Uji pencarian di dalam daftar, **Pilih semua/Kosongkan**,
dan pastikan lagu yang difilter tidak mengubah pilihan di luar hasil filter.

### S4 — Playlist besar (> 100 lagu)

1. Tempel playlist dengan lebih dari 100 lagu.
2. Kartu harus menampilkan pesan **Halaman Spotify hanya memuat 100 lagu
   pertama.** dan daftar berhenti di 100.
3. Pastikan antrean hanya memakai lagu yang terlihat di daftar; tidak ada
   permintaan halaman lanjutan.

### S5 — Batalkan pembacaan metadata

1. Tempel tautan album pada jaringan lambat.
2. Tekan **Batalkan pemeriksaan** sebelum kartu muncul.
3. Tidak ada kartu setengah jadi; muncul tombol **Baca ulang link Spotify**.
4. Tekan tombol itu: pembacaan ulang berhasil tanpa mengganti tautan.

### S6 — Batalkan pencocokan

1. Pilih seluruh playlist kecil dan mulai **Cocokkan**.
2. Setelah 2–3 lagu, tekan **Batalkan** di kartu.
3. Progres berhenti; lagu yang sudah cocok tetap menampilkan kandidat; lagu
   yang belum diproses tetap tanpa tanda cocok (tidak ada teks "menunggu").
4. **Antrekan** hanya mengantrekan lagu yang sudah cocok; sisanya dihitung
   sebagai dilewati.
5. Pastikan tidak ada proses yt-dlp tersisa (lihat §4, pemeriksaan proses).

### S7 — Gagal jaringan / pencarian

Skenario yang diuji terpisah:

- **Mode pesawat sebelum tempel**: metadata gagal dengan pesan kegagalan
  koneksi (catat teks persisnya), tombol **Coba lagi** muncul, aplikasi tidak
  crash.
- **Mode pesawat saat mencocokkan**: baris yang sedang diproses menampilkan
  **Gagal dicocokkan: …**, lagu berikutnya tetap dicoba, ringkasan merah
  **Sebagian lagu gagal dicocokkan dan akan dilewati saat antre.**
- **Aktifkan jaringan lalu Cocokkan lagi**: hanya lagu yang belum punya
  kandidat yang dicari ulang.
- **Bot-check YouTube** (bila muncul): pesan mesin tampil di baris, bukan crash;
  catat pesan lengkapnya dan waktu kejadian.

### S8 — Kecocokan lemah

Gunakan track yang sengaja sulit (judul umum, banyak versi live/cover/cover
akustik). Cari baris dengan **kecocokan lemah, periksa dulu**:

- Baris dengan skor < 70 mendapat teks merah **kecocokan lemah, periksa dulu**
  (skor angka tidak ditampilkan di daftar koleksi); lagu masih boleh
  diantrekan (keputusan pengguna).
- Kandidat live/remix dengan durasi jauh harus kalah dari rekaman durasi sama.
- Versi karaoke/instrumental/cover tidak boleh mendapat label cocok kuat.

### S9 — Duplikat

1. Unduh satu lagu dari album sampai **Selesai**.
2. Ulangi album yang sama dan antrekan lagi.
3. Job untuk lagu itu harus ditolak sebagai duplikat dan dihitung **dilewati**;
   lagu lain tetap diantrekan. Riwayat tidak boleh berisi dua baris identik.
4. Hapus file lewat Riwayat (hapus file), cocokkan ulang bila perlu, lalu
   antrekan ulang: harus berhasil.

### S10 — Proses mati dan restart

- **Saat pencocokan**: force-stop aplikasi. Buka lagi; bila tautan dipulihkan,
  aplikasi membaca ulang metadata tetapi **hasil pencocokan sebelumnya hilang**
  (pencocokan disimpan di memori, bukan database). Catat sebagai batas, bukan
  bug. Jika tautan tidak dipulihkan, tempel ulang.
- **Saat antrean berjalan**: force-stop lalu buka lagi. Job yang sudah masuk
  antrean harus pulih mengikuti mekanisme WorkManager yang ada (status
  **Coba lagi**/**Selesai** konsisten), bukan hilang.
- Setelah restart, pastikan tidak ada folder sementara baru untuk job yang
  belum pernah dibuat.

### S11 — Folder dan nama file

- Album → subfolder slug artis di bawah folder penyimpanan
  (`Downloads/<folder>/<artis>/`). Jika nama artis tidak menghasilkan slug
  aman, file langsung di folder utama.
- Playlist → subfolder slug judul playlist.
- Nama file memakai judul Spotify + artis (bukan judul video YouTube), dan
  berlaku penamaan otomatis yang sama seperti unduhan lain.
- Ganti setelan folder penyimpanan lalu ulangi satu album: hasil harus mengikuti
  folder baru.

### S12 — Tampilan, aksesibilitas, dan teks

- Font 1,3× dan 1,6×; landscape; layar sempit: tombol **Cocokkan/Antrekan**
  tidak terpotong dan daftar tetap bisa digulir.
- TalkBack: checkbox, status cocok/gagal, dan progres `x/y` terbaca.
- Buka-tutup keyboard pencarian tidak menghapus pilihan.

### S13 — Integrasi katalog dan pemutar

- Sampul Spotify muncul di Riwayat, Koleksi, dan mini player.
- Audio hasil dapat diputar; lirik (bila aktif) memakai judul/artis/durasi
  Spotify dan status lirik tercatat seperti biasa.
- Filter platform **Spotify** di Riwayat/Ringkasan menghitung entri dengan benar.
- **Unduh lagi** dari Riwayat memakai URL YouTube yang tersimpan dan tetap
  mempertahankan identitas Spotify.

## 4. Skenario kegagalan dan pesan yang diharapkan

Cocokkan pesan yang tampil dengan daftar ini; pesan berbeda harus dicatat
sebagai temuan.

| Pemicu | Perilaku yang diharapkan |
| --- | --- |
| Tautan artist/episode/`spotify.link` pendek | Composer menolak: **Link belum didukung — gunakan https dari platform yang tersedia.** |
| Album/playlist dihapus (HTTP 404) | **Halaman Spotify tidak ditemukan — periksa tautannya.** |
| Konten privat / halaman embed tanpa entity | **Daftar lagu Spotify tidak bisa dibaca. Halaman Spotify mungkin berubah; coba lagi nanti.** |
| Spotify membatasi permintaan (HTTP 429) | **Spotify membatasi permintaan sementara. Coba lagi beberapa saat lagi.** |
| HTTP 4xx lain (mis. 403) | **Spotify menolak permintaan (HTTP 403). Pastikan kontennya publik.** |
| HTTP 5xx | **Spotify sedang bermasalah (HTTP 5xx). Coba lagi nanti.** |
| Halaman berubah bentuk / entity bukan album/playlist | **Daftar lagu Spotify tidak bisa dibaca. Halaman Spotify mungkin berubah; coba lagi nanti.** |
| Playlist kosong | **Tidak ada lagu yang bisa dibaca dari halaman ini.** |
| Tidak ada kandidat untuk satu lagu | Baris: **Tidak ada kandidat YouTube.**; lagu dilewati saat antre. |
| Pencarian satu lagu gagal | Baris: **Gagal dicocokkan: …**; lagu lain lanjut. |
| Semua lagu gagal diantrekan | Snackbar: **Tidak ada lagu yang bisa diantrekan dari &lt;judul&gt;.** |
| Batalkan saat metadata | Kartu hilang; tombol **Baca ulang link Spotify** tersedia. |

Selain pesan, periksa:

- `adb logcat` tidak berisi `FATAL EXCEPTION` atau `AndroidRuntime` untuk PID
  aplikasi selama setiap skenario.
- Bila pencarian dibatalkan, tidak ada proses `yt-dlp`/`python` yatim:
  `adb shell ps -A | grep -i -E "yt-dlp|python"` tidak menampilkan proses baru
  milik aplikasi.
- Setelah pembatalan antrean, tidak ada file `.part` baru yang tertinggal untuk
  job yang dibatalkan.

## 5. Verifikasi file hasil

Tarik satu file audio per kasus, lalu periksa di host:

```sh
adb shell content query --uri content://media/external/audio/media \
  --projection _display_name:relative_path:title:artist:album \
  --where "_display_name LIKE '%.mp3'"

# Sesuaikan folder penyimpanan (default Download/TubeFlow, atau folder SAF)
adb pull "/sdcard/Download/<folder>/<subfolder>/<file>.mp3" evidence/
ffprobe -v error -show_entries format_tags=title,artist,album \
  -show_entries stream=codec_name,bit_rate -of json evidence/<file>.mp3
```

Yang harus benar:

- `title` = judul Spotify; `artist` = artis Spotify (bukan uploader YouTube).
- Folder sesuai S11.
- Bitrate/format sesuai pilihan.
- **Tidak ada** gambar tertanam pada alur Spotify: sampul Spotify hanya dipakai
  di aplikasi. Jika ada `attached_pic`, catat sebagai temuan (berarti thumbnail
  video ikut tersemat).
- Lirik `.lrc` (bila diminta) berada di folder yang sama dengan nama dasar sama.

## 6. Format laporan hasil

Tambahkan bagian baru di [VALIDATION.md](VALIDATION.md) dan ringkasan di
[DEVICE_TEST.md](DEVICE_TEST.md) dengan tabel:

| ID | Kasus | Hasil diamati | Status | Bukti |
| --- | --- | --- | --- | --- |
| S1 | Track tunggal → MP3 | … | Lulus | `spotify-S1-03-history.png` |
| S6 | Batalkan pencocokan | … | Lulus | `spotify-S6-02-cancel.png`, `logcat.txt` |
| S7 | Mode pesawat saat cocokkan | … | Gagal (pesan X) | `spotify-S7-01-error.png` |

Sertakan juga:

- model/Android/ABI/halaman memori, `versionName`/`versionCode`, SHA-256 APK;
- jumlah tes host/lint yang dijalankan ulang pada source yang sama;
- daftar kasus **Tidak diuji** beserta alasannya;
- temuan baru dengan langkah reproduksi dan (bila ada) ID bug.

Jangan menyalin URL akun, token, path pribadi, atau isi log mentah ke Git;
simpan bukti asli di luar repo dan rujuk nama filenya saja.

## 7. Kriteria selesai

Alur Spotify album/playlist boleh disebut tervalidasi di perangkat hanya jika:

1. S1–S4, S6, S7, dan S11 lulus pada minimal satu perangkat Android 10+ ARM64.
2. Setiap skenario gagal di §4 menampilkan pesan yang sesuai tanpa crash.
3. Tidak ada proses yt-dlp yatim atau file parsial baru setelah pembatalan.
4. Tag judul/artis dan folder diverifikasi langsung dari file, bukan hanya UI.
5. Batas yang tetap berlaku dicatat eksplisit: audio dari YouTube (bukan DRM
   Spotify), playlist maksimal 100 lagu dari halaman embed, pencocokan hilang
   setelah proses mati, dan sampul Spotify tidak disematkan ke file.

## 8. Batas yang diketahui sebelum pengujian

- Pencocokan memilih kandidat terbaik secara otomatis; tidak ada editor kueri
  per lagu. Kandidat lemah hanya diberi tanda.
- Hasil pencarian YouTube dapat berubah antar waktu/akun/jaringan; angka
  kandidat dan skor bukan kontrak tetap.
- Tautan pendek `spotify.link` belum didukung; gunakan tautan
  `open.spotify.com/...` dari menu Bagikan Spotify.
- Tidak ada pencarian halaman lanjutan playlist di luar 100 lagu pertama.

## 9. Hasil validasi perangkat — 24 Oktober 2026

Release **1.1.33/code47**, Realme RMX1821 (Realme 3 Pro), Android 10/API 29,
arm64-v8a, halaman 4 KB. APK rilis 71.042.372 byte, SHA256
`2f7e2fc7f321b65d66761db9b30de195fa2ec7f80e7247b76ed09d1684cbb317`;
`base.apk` di HP identik. Update-r tanpa uninstall/clear data. Build host:
427 tes Kotlin, 98 tes Python, lint 0 error/3 warning; audit 257 ELF/13 kelas
ZIP, zipalign 16 KB, signature v2 `ebe3b40e…`.

| ID | Kasus | Hasil diamati | Status |
| --- | --- | --- | --- |
| S1 | Track tunggal → MP3 | Share intent mengisi composer; kartu **Red/Scott Buckley/3:33**, 5 kandidat dengan channel, durasi, selisih. MP3 128 terunduh, `title=Red`, `artist=Scott Buckley`, tanpa `attached_pic`; riwayat berlabel Spotify. | Lulus |
| S2 | Album kecil | **Library Songs 5**, 10 lagu/51:16, semua terpilih. Dipilih 2, keduanya cocok kuat (selisih 0 dan 1 dtk), diantrekan ke `scott-buckley/`. `Borealis` MP3 128 dengan tag Spotify. | Lulus |
| S3 | Playlist kecil | **Alternative Music**, 20 lagu/1:08:56. Pencarian “Rush” menyaring 2 baris tanpa mengubah pilihan 20/20. Antrean menghasilkan `alternative-music-free-stock-music/` dengan tag Spotify. | Lulus |
| S4 | Playlist > 100 lagu | **All Out 80s** menampilkan 100 lagu dan pesan **Halaman Spotify hanya memuat 100 lagu pertama.**; tidak ada permintaan halaman lanjutan. | Lulus |
| S6 | Batalkan pencocokan | Mulai 20 lagu, dibatalkan pada `2/20`. Indikator berhenti, kandidat yang sudah ada tetap, tombol menjadi **Antrekan 3 lagu**, tidak ada teks “Menunggu”, dan tidak ada proses python/ffmpeg tersisa. | Lulus |
| S7 | Gagal jaringan | Mode pesawat tidak dapat memutus VPN aktif di perangkat, jadi skenario offline tidak dijalankan. Sebagai gantinya album dengan ID tak dikenal menghasilkan pesan **Daftar lagu Spotify tidak bisa dibaca…** + **Coba lagi** (Spotify membalas HTTP 200 tanpa entity), tanpa crash. | Sebagian |
| S9 | Duplikat | `Power` yang sudah selesai ikut tercocokkan lagi tetapi tidak menambah entri/file kedua; lagu lain tetap diantrekan. Hitungan “dilewati” pada snackbar tidak sempat dibaca. | Sebagian |
| S13 | Riwayat/pemutar | Riwayat menampilkan sampul, judul, artis, dan label Spotify; memutar `Here I Come` memberi MediaSession `state=3` dengan metadata Spotify. Filter platform dan **Unduh lagi** belum diuji. | Sebagian |

Bukti disimpan di luar repo (`spotify-S1-01-preview.png`,
`spotify-S13-history.png`, `spotify-S1-Red.mp3`, `spotify-S2-Borealis.mp3`,
`spotify-S3-Power.mp3`). Tidak ada `FATAL EXCEPTION` pada PID aplikasi selama
pengujian.

**Tidak diuji:** S5 (batalkan metadata), S7 offline penuh, S8 (kecocokan lemah),
S10 (force-stop saat mencocokkan/antrean), S11 pergantian folder, dan S12
(font/landscape/TalkBack). Sesuai §7, alur ini **belum** boleh disebut
tervalidasi penuh sampai kasus tersebut dijalankan.
