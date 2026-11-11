# Matriks uji perangkat TubeFlow

Dokumen ini adalah checklist bukti untuk kandidat rilis, bukan klaim bahwa
seluruh skenario sudah lulus. Status terakhir diperbarui **11 November 2026**
untuk kandidat **1.1.79/code 94** di Realme RMX1821 (Android 10,
720×1520). **DTM-18/21/23/24** sudah dicoba sebagian pada iterasi build
1.1.79; baris lain dan pengukuran ulang panel pada APK final masih **belum
dijalankan**. Bukti rinci ada di [PROGRESS.md](PROGRESS.md).

Gunakan hanya konten milik sendiri atau yang diizinkan. Hasil lama pada
[DEVICE_TEST.md](DEVICE_TEST.md) tetap berlaku untuk versi yang disebut di sana
dan tidak otomatis menjadi bukti build terbaru.

## Perangkat dan cakupan minimum

| Perangkat | Android | ABI | Halaman memori | Fokus |
| --- | --- | --- | --- | --- |
| Realme RMX1821 (perangkat lama) | 10 / API 29 | arm64-v8a | 4 KB | Baseline low-end, SAF, unduhan, pemutar |
| HP/emulator API 33 | 13 | arm64-v8a | 4 KB | Izin media baru, notifikasi, pemilih dokumen |
| HP/emulator API 34–35 | 14–15 | arm64-v8a | 4 KB | Foreground service, PiP, background |
| Perangkat/emulator 16 KB | 15+ | arm64-v8a | 16 KB | Runtime native FFmpeg/WebP, ZIP native |
| Emulator | 10–15 | x86_64 | 4 KB | Regresi cepat non-native; bukan pengganti HP |

Cakupan ABI rilis adalah `arm64-v8a`, `armeabi-v7a`, dan `x86_64`. Minimal satu
perangkat ARM32 nyata diperlukan bila split itu ikut didistribusikan.

## Bukti yang wajib dicatat

Catat untuk setiap sesi: model, versi Android/API, ABI, `PAGE_SIZE`, versi
aplikasi, SHA-256 APK, skenario, hasil, dan path tangkapan layar/log. Perintah
awal:

```sh
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getconf PAGE_SIZE
adb shell dumpsys package id.local.tubeflow | grep -E "versionCode|versionName"
sha256sum app/build/outputs/apk/release/*.apk
adb logcat -c
adb logcat -v threadtime --pid=$(adb shell pidof -s id.local.tubeflow) > evidence/logcat.txt
```

Untuk bug buffer, rekam layar 60 fps agar durasi spinner dapat diukur. Log
pemutar memakai tag `TubeFlow-video` (`adb logcat -s TubeFlow-video`).

## Matriks skenario

| ID | Area | Langkah singkat | Kriteria lulus |
| --- | --- | --- | --- |
| DTM-01 | Buffer sekejap | Putar video lokal 10+ menit, seek berulang, biarkan prefetch latar berjalan. Ulangi pada video 1080p di perangkat lambat. | Spinner dan tombol jeda tidak berkedip untuk buffer <300 ms; video tidak benar-benar berhenti; tidak ada galat dekoder di log. |
| DTM-02 | Unduhan hanya WiFi | Aktifkan **Hanya lewat WiFi**, matikan WiFi, antrekan unduhan, lalu sambungkan WiFi. | Job berstatus menunggu tanpa file setengah jadi saat hanya data seluler; berjalan setelah WiFi tersedia; tidak ada output ganda. |
| DTM-03 | Unduhan hanya saat mengisi daya | Aktifkan **Hanya saat mengisi daya**, antrekan saat baterai, lalu sambungkan charger. | Job menunggu tanpa error saat tidak diisi daya; berjalan setelah charger tersambung; kombinasi dengan WiFi tetap dihormati. |
| DTM-04 | Izin folder dicabut | Tambahkan folder SAF (idealnya kartu memori), cabut izin lewat sistem atau ejek kartu, lalu buka Koleksi dan Pengaturan → Penyimpanan. | Tidak crash; folder dengan grant hilang memakai ikon/status error; hasil pindai menyebut sumber yang tidak dapat diakses; **Periksa ulang indeks koleksi** selesai tanpa menghapus file. |
| DTM-05 | Folder dilepas dan ditambah ulang | Lepas folder tambahan, periksa file lewat aplikasi Files, tambahkan folder yang sama, pindai ulang. | File dan subfolder tetap utuh; baris indeks lama hilang; hasil pindai ulang tidak duplikat; jumlah item kembali benar. |
| DTM-06 | Proses mati saat unduhan | Mulai unduhan, `adb shell am kill id.local.tubeflow` (bukan force-stop), buka aplikasi. | Status dan progres konsisten; worker melanjutkan atau dapat dicoba lagi; tidak ada file selesai yang isinya terpotong. |
| DTM-07 | Proses mati saat memutar video | Putar video >10 detik, force-stop, buka kembali dan pilih video yang sama. | Tawaran lanjut tonton muncul pada posisi ±2 detik; spinner hanya muncul bila buffer benar-benar lama; audio/video tetap sinkron. |
| DTM-08 | Antrean paralel dan pembatalan | Setel 1 lalu 4 unduhan bersamaan; batalkan saat menunggu, mengunduh, dan konversi; coba lagi yang gagal. | Tidak ada proses Python/FFmpeg tertinggal; file setengah jadi tidak ditandai selesai; notifikasi progres tetap per job; job lain tidak ikut berhenti. |
| DTM-09 | Subtitle BUG-082/083 | Inspeksi episode Bstation yang punya subtitle `id`/`en`, unduh dengan subtitle Indonesia, buka di pemutar. | Daftar bahasa tampil saat inspeksi; sidecar berada di folder media yang sama; chip subtitle menampilkan track; teks muncul; tetap terbaca setelah proses mati. |
| DTM-10 | Metadata dan lirik BUG-051/052 | Unduh audio dengan artis eksplisit; periksa tag lewat `ffprobe`; cari lirik otomatis dan manual; paksa respons 429 bila memungkinkan. | Artis asli tidak diganti nama uploader; kredit tidak tercampur; lirik manual/otomatis memakai identitas dan durasi yang benar; status 429/provider/jaringan dibedakan; sinkronisasi diaudit dengan telinga. |
| DTM-11 | Runtime 16 KB | Pasang di perangkat/emulator `PAGE_SIZE=16384`, buka video dengan dekoder software, unduh audio, dan muat sampul WebP. | Aplikasi terpasang dan berjalan; tidak ada galat linker `dlopen`; `python3 scripts/audit_native.py <APK>` lulus; pemutaran dan sampul berfungsi. |
| DTM-12 | PiP dan latar | Masuk/keluar PiP, kunci layar, pindah aplikasi, sambungkan headset, kembali ke TubeFlow. | Tidak ada galat dekoder; posisi dan status putar benar; musik/video tidak memulai pemutar kedua; notifikasi memuat aksi yang sesuai. |
| DTM-13 | Aksesibilitas dan tata letak | Font 1,6×, TalkBack, portrait/lanskap, layar sempit; periksa toggle antrean, status folder, dan dialog hapus. | Semua kontrol terjangkau dan terbaca; status tidak hanya dibedakan warna; tidak ada teks terpotong permanen; target sentuh minimal 48 dp. |
| DTM-14 | Penyimpanan hampir penuh | Isi penyimpanan sampai tersisa sedikit, mulai unduhan dan publikasi, lalu batalkan. | Pesan jelas; tidak ada file selesai yang rusak; ruang kembali setelah pembatalan; antrean dapat dicoba lagi setelah ruang tersedia. |
| DTM-15 | Baris ringkas Koleksi & Riwayat | Buka Koleksi: cari file yang judulnya berbeda dari nama file dan file berlirik/subtitle. Lalu buka tab Download → Riwayat. | Nama file tampil di baris keterangan bila tidak mengulang judul; ikon lirik/CC muncul di ujung baris tanpa baris tambahan; kartu Riwayat memakai baris ringkas tapi tanggal selesai, `AttachmentInfo`, dan status lirik tetap tampil; semua aksi tersedia di menu ⋮; tap baris tetap memutar. |
| DTM-16 | Favorit, teks bergulir, antrean seragam | Tandai beberapa item sebagai favorit di Koleksi; buka item berjudul panjang/nama file panjang; buka antrean putar audio (pemutar lagu) dan antrean video (pemutar video). | Hati favorit tampil hanya pada item di playlist Favorit; judul/baris keterangan yang terpotong bergulir kanan ke kiri (diam bila muat); kedua antrean memakai kartu sama: sampul 48 dp, judul, `AttachmentInfo`, label "Sedang diputar" + aksen pada item aktif; menu ⋮ tetap ada di audio; tap baris melompat ke item tersebut. |
| DTM-17 | Ganti nama folder fisik | Ganti nama folder hasil unduhan (MediaStore) dan folder SAF bertingkat; periksa Files, Koleksi, playlist, lirik, dan Riwayat. Coba juga folder baca-saja, folder akar, dan nama yang sudah dipakai. | Direktori benar-benar berubah nama; berkas tetap dapat diputar; lirik, playlist, dan riwayat menunjuk lokasi baru; job yang belum selesai memakai subfolder baru; kasus yang ditolak memberi pesan jelas dan tidak meninggalkan folder setengah pindah. |
| DTM-18 | Empat mode rasio | Putar video 16:9 ber-hardsub di layar 20:9, lanskap dan portrait; ganti mode **100%**, **Pas ke layar**, **Rentangkan**, **Potong** lewat ikon cepat dan sheet; ulangi saat video dijeda; periksa notice, hint, dan TalkBack. | **100%** memetakan satu piksel sumber ke satu piksel layar dan boleh keluar layar; **Pas ke layar** menampilkan seluruh frame tanpa potongan; **Rentangkan** memenuhi layar tanpa membuang isi (rasio berubah); **Potong** memenuhi layar dengan tepi terpangkas merata di tengah. Ganti mode saat dijeda tidak membuka ulang pemutar; nilai lama Isi layar/Zoom tampil sebagai Pas ke layar. |
| DTM-19 | RAM katalog, tema awal, menu audio, genre | Buka aplikasi dengan katalog 1.000+ item dan ukur memori/waktu buka; pasang baru tanpa data untuk memeriksa tema; buka baris audio Koleksi; unduh audio tanpa genre eksplisit lalu periksa tag. | Pemeriksaan awal tidak memuat ribuan baris dan RAM tidak terus naik; instalasi baru memakai tema Sistem tanpa menimpa pilihan lama; menu ⋮ tidak mengulang Favorit/Bagikan; genre kosong tidak menjadi tag kosong dan fallback kategori/tag tetap bekerja. |
| DTM-20 | Antrean tertahan "Menunggu giliran" | Antrekan satu video dengan **Hanya lewat WiFi** aktif saat hanya data seluler, lalu sambungkan WiFi; ulangi dengan **Hanya saat mengisi daya** lalu sambungkan charger; matikan salah satu toggle saat job masih QUEUED; biarkan satu job QUEUED lebih dari 45 detik tanpa unduhan lain berjalan. | Kartu menyebut alasan tunggu yang benar (internet/WiFi tidak berbayar/pengisian daya/kombinasi); setelah syarat terpenuhi dan tidak ada job lain berjalan, job mulai dalam ±45 detik tanpa perlu jeda→lanjutkan; mengubah toggle langsung menjadwal ulang job QUEUED; tidak ada output ganda atau job lain ikut berhenti. |
| DTM-21 | Posisi lirik saat ganti lagu | Buka Koleksi → cari artis berlirik (mis. Skillet) → putar lagu pertama → buka pemutar penuh → gulir lirik ke bawah → tekan lagu berikutnya. Ulangi dari lagu tanpa lirik tersinkron. | Daftar lirik langsung kembali ke baris awal untuk lagu baru, tidak mewarisi posisi gulir lagu sebelumnya; pager tetap membuka halaman lirik bila tersedia. |
| DTM-22 | Panel antrean video di lanskap | Putar video berjudul panjang, masuk lanskap lewat kontrol orientasi pemutar, buka panel antrean, ukur lebar panel dan amati judul. | Panel tidak menutupi separuh layar (maksimum 320 dp / ±544 px pada density 1,7); judul panjang bergulir satu baris; item aktif tetap ditandai dan dapat diketuk. |
| DTM-23 | Watchdog buffer | Putar video lokal >12 detik dan seek beberapa kali; periksa log `TubeFlow-video` untuk memastikan tidak ada `macet` palsu. Bila stall nyata dapat direproduksi, biarkan >12 detik. | Pemutaran normal tidak memicu pemulihan; stall nyata membuka ulang di posisi yang sama, percobaan kedua memakai software (kecuali mode Perangkat manual), dan setelah dua pemulihan beruntun spinner berhenti dengan pesan galat yang dapat ditindaklanjuti. |
| DTM-24 | Unduhan video vertikal Instagram | Inspeksi reel Instagram portrait, pilih kualitas yang tersedia, antrekan, lalu periksa file keluaran dengan `ffprobe`. Ulangi saat inspeksi/antrean gagal. | Tinggi piksel nyata (mis. 1280) diterima; file 720×1280 diputar; kartu dan isian baru dibersihkan setelah antrean terkonfirmasi, sedangkan kegagalan mempertahankan hasil inspeksi dan menampilkan pesan jelas. |

## Format hasil

Salin tabel ini ke laporan sesi dan isi satu baris per percobaan:

| ID | Perangkat/API | APK | Hasil | Bukti | Catatan |
| --- | --- | --- | --- | --- | --- |
| DTM-01 |  |  | Belum dijalankan |  |  |
| DTM-02 |  |  | Belum dijalankan |  |  |
| DTM-03 |  |  | Belum dijalankan |  |  |
| DTM-04 |  |  | Belum dijalankan |  |  |
| DTM-05 |  |  | Belum dijalankan |  |  |
| DTM-06 |  |  | Belum dijalankan |  |  |
| DTM-07 |  |  | Belum dijalankan |  |  |
| DTM-08 |  |  | Belum dijalankan |  |  |
| DTM-09 |  |  | Belum dijalankan |  |  |
| DTM-10 |  |  | Belum dijalankan |  |  |
| DTM-11 |  |  | Belum dijalankan |  |  |
| DTM-12 |  |  | Belum dijalankan |  |  |
| DTM-13 |  |  | Belum dijalankan |  |  |
| DTM-14 |  |  | Belum dijalankan |  |  |
| DTM-15 |  |  | Belum dijalankan |  |  |
| DTM-16 |  |  | Belum dijalankan |  |  |
| DTM-17 |  |  | Belum dijalankan |  |  |
| DTM-18 | Realme RMX1821 / API 29 | 1.1.79/94 iterasi 18:58 | Sebagian | log `TubeFlow-video`, tangkapan layar | **Rentangkan** 894×423 dp skala 1,195×1,000 dan **Potong** 894×506 dp skala 1,195×1,195; ganti mode saat dijeda tidak memicu `sesi=buka`. **100%** dan **Pas ke layar** sudah diukur pada iterasi build sebelumnya (848×480 terpusat; 1272×720), belum diulang pada APK final. |
| DTM-19 |  |  | Belum dijalankan |  |  |
| DTM-20 |  |  | Belum dijalankan |  |  |
| DTM-21 | Realme RMX1821 / API 29 | 1.1.79/94 iterasi 18:58 | Lulus (konfirmasi pengguna) | tangkapan pemutar lirik | Pengguna memutar lagu dari Koleksi dan mengonfirmasi lirik "aman"; pengujian lanjutan dihentikan atas permintaan pengguna. |
| DTM-22 | Realme RMX1821 / API 29 | 1.1.79/94 iterasi 18:58 | Gagal pada iterasi, diperbaiki | tangkapan panel + log | Panel masih 76% layar karena `fillMaxWidth().widthIn()` mengunci minimum; implementasi diganti `Modifier.width(queuePanelWidthDp(...))` dan regresi host ditambahkan. Lebar pada APK final belum diukur ulang di HP. |
| DTM-23 | Realme RMX1821 / API 29 | 1.1.79/94 iterasi 18:58 | Sebagian | log `TubeFlow-video` | Pemutaran normal 848×480 selama 15 detik tidak menghasilkan log `macet`; stall nyata belum berhasil direproduksi sehingga jalur pemulihan belum terbukti di perangkat. |
| DTM-24 | Realme RMX1821 / API 29 | sebelum build final | Sebagian | `ffprobe` dua file | Dua file `Video by instagram*.mp4` masing-masing 6.830.931 byte, H.264 720×1280. Pengantrean ulang pada APK final belum dijalankan. |

## Batas

- Uji host, lint, dan unit test tidak membuktikan perilaku WorkManager, SAF,
  MediaStore, dekoder, atau provider di perangkat.
- Emulator tidak menggantikan uji codec perangkat keras, kartu memori nyata,
  atau pencabutan izin oleh vendor.
- Status **Lulus** hanya boleh ditulis bila ada bukti yang dapat diperiksa
  ulang; kegagalan yang belum direproduksi dicatat sebagai **Belum terbukti**.
