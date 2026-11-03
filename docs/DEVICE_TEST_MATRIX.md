# Matriks uji perangkat TubeFlow

Dokumen ini adalah checklist bukti untuk kandidat rilis, bukan klaim bahwa
seluruh skenario sudah lulus. Status terakhir diperbarui **3 November 2026**
untuk kandidat **1.1.71/code 86**. HP fisik sempat tersambung dan build ini
sudah terpasang lewat ADB (bukti hash di [PROGRESS.md](PROGRESS.md)), tetapi
belum ada skenario di bawah yang dijalankan pada build ini; semua baris masih
**belum dijalankan**.

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

## Batas

- Uji host, lint, dan unit test tidak membuktikan perilaku WorkManager, SAF,
  MediaStore, dekoder, atau provider di perangkat.
- Emulator tidak menggantikan uji codec perangkat keras, kartu memori nyata,
  atau pencabutan izin oleh vendor.
- Status **Lulus** hanya boleh ditulis bila ada bukti yang dapat diperiksa
  ulang; kegagalan yang belum direproduksi dicatat sebagai **Belum terbukti**.
