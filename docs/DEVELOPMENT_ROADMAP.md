# Roadmap Perbaikan dan Optimasi TubeFlow

Dicatat **9 Oktober 2026** sebagai panduan pengembang berikutnya. Delapan usulan berikut sudah disetujui untuk **didokumentasikan**. Status per **9 Oktober 2026** (pembaruan setelah perbaikan bug): OPT-001 **dikerjakan sebagian**, OPT-002 **dikerjakan sebagian**, sisanya **diusulkan**. Daftar bug dan bukti pemeriksaan tersedia di [BUG_FINDINGS.md](BUG_FINDINGS.md); keputusan desain dan bukti perbaikan di [PROGRESS.md](PROGRESS.md).

## Status aktual hasil rekonsiliasi — 13 Oktober 2026, 1.1.0/code14

Tabel ini menggantikan status historis9–13Oktober di bawah. Pemeriksaan mencocokkan laporan terbaru dengan kode, bukan implementasi baru atau tes runtime seluruh fitur. Checklist lama tetap menjadi kriteria cakupan lengkap, bukan jumlah pekerjaan yang belum ditulis. **Pembaruan sesi 1.1.1/code15** (dashboard Ringkasan media + penguatan cari ulang lirik): baris OPT-002 dan catatan dashboard di bawah tabel sudah diperbarui; baris lain masih status 1.1.0.

| ID | Status aktual | Yang masih perlu dikerjakan atau dibuktikan |
| --- | --- | --- |
| OPT-001 | Sebagian; pencocokan terstruktur/versi/durasi±2detik, status tidak cocok dan tie durasi tersedia | Record ID/atribusi, preview/pemilihan manual tersedia1.1.5; jalur pemilihan otomatis belum menyimpan record ID. Lamunan pernah dikonfirmasi sinkron, uji auditori terbaru menunggu |
| OPT-002 | Sebagian; pengamanan refetch diperkuat dan ikut rilis 1.1.1 (terpasang di HP) | Backup wajib/read-back/rollback+jurnal persisten melalui `JournaledTextReplacement`, mutex bersama edit/refetch/rename/delete dan stale snapshot guard; stem audio diverifikasi penuh. Uji fault injection Android/provider/process death serta refetch live tetap perlu; lihat [laporan](DASHBOARD_LYRICS_2026-10-13.md) |
| OPT-003 | Implementasi1.1.5, regresi host dan seleksi/simpan oleh pengguna di HP terbukti | Preview, maksimum50 kandidat, record ID/atribusi di LRC, pengakuan hasil berbeda gerbang otomatis; MediaStore SATRU2 terverifikasi. Cancel/rename→search penuh, SAF/provider/crash dan sinkron auditori belum diuji lengkap |
| OPT-004 | Implementasi tersedia dan teruji terbatas | `LrcTiming`/`LyricEditor`, ±30detik/langkah0,5detik dan input detik1.1.5 (presisi1ms), baseline/reset/jurnal/read-back/rollback; Ya Sudahlah−2detik dikonfirmasi pengguna. Fault injection Android/process death/provider berbeda dan interaksi refetch belum terbukti; tidak menjamin semua lagu sinkron |
| OPT-005 | Sebagian, bukan belum dimulai | Cache respons LRCLIB10MiB/200entry, positif24jam/4041jam, clear/refresh dan regresi TTL/error tersedia. `extractor-cache` yt-dlp bukan cache snapshot metadata app berdasarkan engine/TTL. Snapshot metadata, benchmark request/waktu, serta tes permintaan bersamaan/coalescing belum lengkap |
| OPT-006 | Diterapkan; progres per tahap telah diuji host/Realme | Cakupan callback terlambat/stop-resume cepat dan proses mati tetap terkait BUG-002/008; bukan kembali ke heartbeat lama |
| OPT-007 | Diterapkan1.1.15; host dan dialog/copy inspeksi nyata di HP lulus | Laporan perjob/generasi/status, redaksi dan data waktu/HTTP tidak ditebak; dialog lirik final/fault injection Android luas tetap perlu; lihat laporan1.1.15 |
| OPT-008 | Diterapkan1.1.15; ukuran cache/parsial dan cleanup eksplisit berlease | HP cache6,9→5,2MB/emptycleanup; nonzero/symlink/lease diuji host. PAUSED/FAILED tetap disimpan otomatis, hanya dihapus setelah konfirmasi pengguna; race native/kill luas belum seluruhnya terbukti |
| OPT-009 | Diterapkan; kompresi cover teruji | JPEG maksimum800px/rasio/paket audio; format/provider lain tetap bagian matriks runtime, bukan bukti seluruh codec |

Urutan lanjut (diperbarui 13 Okt setelah 1.1.1): penguatan BUG-016/OPT-002 sudah masuk rilis 1.1.1 di tingkat host — sisa langkahnya fault injection Android/provider + refetch live → jurnal recovery BUG-008/003 (create→journal, proses mati) → ownership/konkurensi BUG-002/004 → uji fixture SAF/publish/collision → BUG-006 retry jaringan terakhir sesuai permintaan lama → OPT-003/007, sisa OPT-005/008 dan benchmark. Usulan fitur tetap memerlukan penugasan implementasi; audit status ini tidak mengubah media.

Bukti: [laporan Smart Media](SMART_MEDIA_UPGRADE_2026-10-13.md), [uji perangkat](DEVICE_TEST.md), [rekonsiliasi backlog](PROGRESS.md#rekonsiliasi-backlog-bug-roadmap-dan-release--13-oktober-2026).

**Dashboard rilis 1.1.1:** donat komposisi koleksi (media AVAILABLE saja), batang harian dan garis akumulasi COMPLETED memakai periode kalender 7/30/90 hari. Hari sebelum pencatatan bukan 0; tren byte historis tidak dibuat. Memoisasi/TTL validasi katalog tidak merupakan bukti speedup perangkat. Signed 1.1.1/code15 terpasang di HP; donat kondisi kosong, batang/garis 7 hari diperiksa via UIAutomator + screenshot — retensi media dan periode 30/90 hanya tercatat, bukan terbukti penuh. [Kontrak, hasil dan batas](DASHBOARD_LYRICS_2026-10-13.md).

## Prioritas dan urutan pengerjaan (historis)

Prioritas roadmap menunjukkan urutan pengembangan, bukan tingkat keparahan bug.

| ID | Prioritas | Usulan | Ketergantungan utama |
| --- | --- | --- | --- |
| OPT-001 | 1 | Pencocokan lirik berdasarkan identitas dan versi rekaman | BUG-013 — **sebagian**: `LyricMatch` (syarat judul/artis terpisah, gerbang versi/durasi) sudah diterapkan & teruji host; simpan ID rekaman/sumber lirik dan pratinjau sinkronisasi perangkat belum |
| OPT-002 | 1 | Cari ulang lirik tanpa download ulang audio | BUG-008, BUG-009, BUG-010, BUG-014; OPT-001 — **sebagian**: aksi "Cari ulang" di Riwayat + status terstruktur + penulisan di folder audio sudah ada; penggantian bertahap dengan pratinjau kandidat & anti-konkurensi lintas-jenis belum |
| OPT-003 | 2 | Pilih kandidat lirik secara manual | OPT-001, OPT-002 |
| OPT-004 | 2 | Atur waktu lirik dan ekspor timestamp yang disesuaikan | BUG-003, BUG-010; OPT-002 |
| OPT-005 | 2 | Cache metadata dan lirik dengan masa berlaku | OPT-001; BUG-006 |
| OPT-006 | 1 | Progres sesuai tahap pekerjaan yang sebenarnya | BUG-002 |
| OPT-007 | 1 | Detail kegagalan yang bisa disalin pengguna | BUG-014 |
| OPT-008 | 2 | Ukuran dan pembersihan cache/berkas parsial | BUG-002, BUG-003, BUG-004, BUG-011, BUG-012 |

Selesaikan dulu jalur penyimpanan/penghapusan SAF, cleanup sidecar, dan race pause/resume pada daftar bug. OPT-001 dan OPT-007 dapat dikembangkan bersama perbaikan bug terkait. Kerjakan OPT-002 setelah pencocokan dan publikasi lirik aman; lanjutkan pemilihan kandidat, penyesuaian waktu, dan cache.

## OPT-001 — Pencocokan lirik lebih akurat

**Tujuan:** Mengurangi lirik salah lagu dan timestamp dari versi rekaman yang berbeda.

- **Titik kode:** [DownloadWorker.kt](../app/src/main/java/id/local/tubeflow/core/DownloadWorker.kt), `fetchSyncedLyrics`/`lyricScore`; [Engine.kt](../app/src/main/java/id/local/tubeflow/core/Engine.kt), metadata; [Models.kt](../app/src/main/java/id/local/tubeflow/core/Models.kt), model data.
- **Lingkup awal:** Pisahkan pencarian/pencocokan lirik dari worker agar bisa diuji. Gunakan judul track, artis, durasi, album bila tersedia, serta penanda studio/live/remix/cover. Simpan ID rekaman dan sumber lirik yang dipilih. Terapkan syarat kecocokan judul dan artis secara terpisah pada get maupun search.
- **Catatan:** LRCLIB mendukung pencocokan durasi ±2 detik pada endpoint get; lihat [dokumentasi API](https://lrclib.net/docs). Video dengan intro/outro tambahan tetap dapat memiliki timeline berbeda. Jangan menerima kandidat hanya karena durasinya dekat atau menganggap uploader selalu artis lagu.
- [ ] **Kriteria selesai:** Regresi judul sama/artis berbeda, versi berbeda, durasi berbeda, dan metadata kosong lulus; kandidat cocok diterima dan kandidat meragukan dilaporkan. Sinkronisasi diperiksa di beberapa titik audio pada perangkat. Perbarui BUG-013 dengan bukti.

## OPT-002 — Cari ulang lirik dari riwayat

**Tujuan:** Memperbaiki atau melengkapi lirik tanpa mengunduh audio lagi.

- **Titik kode:** [HistoryScreen.kt](../app/src/main/java/id/local/tubeflow/ui/HistoryScreen.kt), aksi riwayat; [DownloadViewModel.kt](../app/src/main/java/id/local/tubeflow/core/DownloadViewModel.kt), orkestrasi; model status lirik dan helper publikasi yang dipisahkan dari worker.
- **Lingkup awal:** Tambahkan aksi “Cari ulang lirik” pada audio selesai. Tampilkan status mencari, tersimpan, tidak ditemukan, instrumental, gagal jaringan, atau gagal simpan. Pastikan audio masih ada dan tulis sidecar di folder audio tersebut, walaupun pengaturan folder sekarang telah berubah.
- **Perilaku penggantian:** Pertahankan lirik lama sampai pengganti berhasil disimpan. Perbarui URI sidecar setelah sukses; kegagalan tidak boleh meninggalkan dokumen parsial. Cegah dua permintaan penggantian berjalan bersamaan untuk audio yang sama.
- [ ] **Kriteria selesai:** Tidak ada pemanggilan download audio saat retry lirik; audio tidak berubah. Nama `.lrc` mengikuti nama audio aktual, status tersedia setelah aplikasi dibuka ulang, dan penggantian gagal tidak merusak lirik lama. Uji SAF dan MediaStore.

## OPT-003 — Pilihan kandidat lirik

**Tujuan:** Memberi pengguna kontrol ketika hasil pencarian ambigu.

- **Titik kode:** Lapisan pencarian OPT-001, [HistoryScreen.kt](../app/src/main/java/id/local/tubeflow/ui/HistoryScreen.kt), dan dialog/komponen UI baru.
- **Lingkup awal:** Tampilkan judul, artis, album, durasi, sumber, serta ada/tidaknya timestamp. Sediakan pratinjau singkat dan tandai hasil yang durasi/versinya berbeda. Pilihan manual memakai ID rekaman agar tidak menjalankan pencarian ambigu lagi.
- [ ] **Kriteria selesai:** Pengguna bisa memilih kandidat dan membatalkan tanpa perubahan file. Kandidat tanpa lirik tersinkron tidak ditampilkan seolah memiliki timestamp. Kandidat terpilih dipublikasikan melalui alur OPT-002 dan identitasnya tersimpan.

## OPT-004 — Pengaturan waktu lirik

**Tujuan:** Mengoreksi lirik yang seluruhnya maju atau mundur dengan selisih tetap.

- **Titik kode:** Helper parser/penulis `.lrc` baru di `core/`, aksi riwayat, dan helper publikasi OPT-002. [Format.kt](../app/src/main/java/id/local/tubeflow/core/Format.kt) sudah memiliki utilitas caption, tetapi parser LRC perlu menangani format LRC secara khusus.
- **Lingkup awal:** Kontrol “Majukan 0,5 detik”, “Mundurkan 0,5 detik”, dan reset. Tulis ulang timestamp `.lrc` agar koreksi tidak hanya berlaku di tampilan aplikasi. Simpan versi asli dan nilai koreksi untuk mencegah pergeseran berulang saat disimpan kembali.
- **Batas fitur:** Pergeseran tetap tidak memperbaiki lirik yang makin meleset sepanjang lagu; arahkan pengguna ke pemilihan versi rekaman. Definisikan penanganan timestamp negatif, beberapa timestamp pada satu baris, metadata LRC, dan offset yang sudah ada.
- [ ] **Kriteria selesai:** Tes arah pergeseran, reset, format pecahan detik, timestamp negatif, dan penyimpanan berulang lulus. Ekspor dapat dibaca pemutar target dan perubahan waktu terlihat pada audio yang diuji. File asli tetap tersedia jika penyimpanan gagal.

## OPT-005 — Cache metadata dan lirik

**Tujuan:** Mengurangi permintaan jaringan berulang untuk video atau lagu yang sama.

- **Titik kode:** [Engine.kt](../app/src/main/java/id/local/tubeflow/core/Engine.kt), inspeksi; lapisan lirik OPT-001; penyimpanan lokal cache baru.
- **Lingkup awal:** Cache metadata berdasarkan identitas video/playlist dan versi engine; cache kandidat lirik berdasarkan identitas track, artis, versi, serta durasi. Simpan waktu pengambilan dan TTL yang eksplisit. Hasil tidak ditemukan menggunakan TTL lebih singkat; timeout/error jaringan jangan disamakan dengan tidak ditemukan.
- **Perilaku pengguna:** Tombol segarkan melewati cache. Tetapkan batas ukuran/jumlah entri dan penghapusan entri kedaluwarsa. Gabungkan permintaan identik yang sedang berjalan agar beberapa job tidak memanggil API yang sama bersamaan.
- [ ] **Kriteria selesai:** Tes hit, kedaluwarsa, refresh, key berbeda untuk versi lagu, dan permintaan bersamaan lulus. Ukur jumlah request serta waktu inspeksi sebelum/sesudah dengan input yang sama; retry setelah jaringan pulih tetap bekerja.

## OPT-006 — Progres berdasarkan tahap nyata

**Tujuan:** Menjelaskan pekerjaan yang sedang berlangsung tanpa persentase konversi yang dibuat-buat.

- **Titik kode:** [DownloadWorker.kt](../app/src/main/java/id/local/tubeflow/core/DownloadWorker.kt), `publish`/`heartbeat`; [Engine.kt](../app/src/main/java/id/local/tubeflow/core/Engine.kt), callback proses; [Models.kt](../app/src/main/java/id/local/tubeflow/core/Models.kt); [components.kt](../app/src/main/java/id/local/tubeflow/ui/components.kt), `JobCard` dan notifikasi worker.
- **Lingkup awal:** Tambahkan tahap mempersiapkan, mencari lirik, mengunduh, mengonversi/menggabungkan, dan menyimpan. Persentase/ETA ditampilkan hanya untuk tahap dengan ukuran/progres terukur. Gunakan indikator tak tentu ketika progres konversi tidak diketahui; hilangkan heartbeat yang menaikkan persen tanpa bukti proses.
- [ ] **Kriteria selesai:** UI dan notifikasi menampilkan tahap konsisten; tidak ada kecepatan/ETA lama saat konversi. Status jeda/batal tidak ditimpa callback terlambat. “Selesai” berarti media telah diterbitkan, dengan status lirik terpisah sesuai BUG-014.

## OPT-007 — Detail kegagalan yang bisa disalin

**Tujuan:** Membantu pengguna dan pengembang memahami kegagalan tanpa mengulang investigasi dari awal.

- **Titik kode:** [DownloadViewModel.kt](../app/src/main/java/id/local/tubeflow/core/DownloadViewModel.kt), error inspeksi/antrean; worker dan engine; kartu hasil/riwayat.
- **Lingkup awal:** Aksi “Salin detail error” dengan versi aplikasi/engine, tahap gagal, waktu, kategori error, kode HTTP bila tersedia, dan pesan singkat. Bedakan gagal media dari gagal lirik. Tampilkan penjelasan mudah dipahami dan tindakan berikutnya, misalnya coba lagi atau pilih ulang folder.
- **Isi laporan:** Hapus token, kredensial, dan data pribadi yang tidak dibutuhkan dari detail yang disalin. Pengguna menyalin sendiri; jangan mengirim laporan otomatis. Jangan memasukkan seluruh log mentah secara default.
- [ ] **Kriteria selesai:** Simulasi timeout, storage penuh, izin SAF dicabut, dan lirik tidak ditemukan menghasilkan kategori/pesan yang benar. Laporan dapat disalin, tetap tersedia setelah restart jika relevan, dan tidak mengandung rahasia. Perbarui BUG-014 dengan hasil uji.

## OPT-008 — Pembersihan cache dan berkas parsial

**Tujuan:** Memberi pengguna cara melihat dan mengurangi penggunaan ruang sementara dengan aman.

- **Titik kode:** [SettingsScreen.kt](../app/src/main/java/id/local/tubeflow/ui/SettingsScreen.kt), penyimpanan; recovery/cancel di [DownloadViewModel.kt](../app/src/main/java/id/local/tubeflow/core/DownloadViewModel.kt); direktori `noBackupFilesDir/downloads/<jobId>` dan cache OPT-005.
- **Lingkup awal:** Pisahkan angka cache metadata/lirik, berkas parsial yang dipertahankan, dan berkas sementara yang bisa dihapus. Bersihkan hanya entri cache/berkas job yang tidak aktif dan sudah boleh dihapus. Job `PAUSED` tetap dilindungi pada cleanup otomatis. Pada1.1.15 pengelola mengotorisasi cleanup manual berkonfirmasi untuk membuang parsial PAUSED/FAILED idle dan memulai retry dari nol.
- **Koordinasi:** Gunakan perlindungan kepemilikan dan penghentian coroutine/engine dari BUG-002/BUG-004. Status WorkManager finished saja tidak cukup untuk menjamin tidak ada cleanup lama. Jangan menghapus file media hasil download melalui aksi bersihkan cache.
- **Catatan 11 Okt 2026:** aksi EKSPLISIT "Hapus semua file" (kartu Penyimpanan, dialog konfirmasi, `clearStorageFolder`) dan pembersihan parsial `noBackupFilesDir` job non-aktif sudah dihadirkan terpisah dari aksi bersihkan cache — aksi eksplisit menghapus media hasil download atas konfirmasi pengguna dan diblokir saat ada job QUEUED/RUNNING/PAUSED. Yang tersisa untuk OPT-008: pemisahan/angka ukuran cache metadata & lirik (OPT-005) dari berkas parsial.
- [ ] **Kriteria selesai:** Tes bersihkan saat download aktif, jeda, resume, batal, dan restart tidak menghapus berkas yang masih diperlukan. Job gagal/dibatalkan tidak meninggalkan parsial permanen; angka penggunaan ruang diperbarui sesuai byte yang benar-benar dihapus.

## Panduan implementasi dan serah-terima

1. Kerjakan usulan dalam perubahan kecil sesuai dependensi. Perbarui status menjadi dikerjakan, menunggu verifikasi, atau selesai; catat blocker dan langkah berikutnya.
2. Penambahan status lirik, tahap proses, atau metadata persisten mungkin memerlukan migrasi Room. Sertakan schema snapshot dan regresi migrasi; data unduhan lama harus tetap terbaca.
3. Ikuti perintah tes/build/audit di [BUG_FINDINGS.md](BUG_FINDINGS.md#validasi-dan-bukti-yang-perlu-ditinggalkan). Tambahkan tes perilaku yang relevan, lalu uji provider SAF dan pemutar lirik di perangkat untuk kasus runtime.
4. Catat file/fungsi yang berubah, bukti tes, perangkat/provider/pemutar, versi/hash APK, serta hasil ukur untuk optimasi. Build lulus saja belum membuktikan sinkronisasi lirik atau keamanan cleanup.
5. Ketika usulan menyelesaikan bug, perbarui kedua dokumen menggunakan ID terkait. Penyedia saat ini tetap LRCLIB; penambahan Musixmatch atau penyedia lain memerlukan rancangan integrasi tersendiri.

## Pembaruan 10 Oktober 2026 — OPT-006 dan kompresi sampul

OPT-006 diterapkan dalam kode: tahap persisten di Room v4, template byte per track, indikator tak tentu untuk proses yang tidak terukur, notifikasi dengan nama tahap, dan status selesai sesudah publikasi sidecar. Heartbeat persen dihapus. Persen video dan audio ditampilkan terpisah karena bobot ukuran kedua track berbeda. Hasil pemeriksaan dan cakupan uji ada di [PROGRESS_COVER_2026-10-10.md](PROGRESS_COVER_2026-10-10.md).

**OPT-009 — Kompresi sampul (disetujui pengguna):** JPEG dengan sisi terpanjang maksimal 800 piksel, tanpa memperbesar gambar kecil atau mengubah rasio. Bootstrap memperluas postprocessor konversi thumbnail dari yt-dlp yang dipin agar JPEG sumber juga diperkecil; gambar diolah sebelum disematkan atau disimpan terpisah. Audio tidak dikompresi ulang oleh tahap sampul. Tidak ada batas byte tetap: ukuran bergantung detail gambar. File lama tidak dimodifikasi; kebijakan berlaku pada unduhan baru. Tes meliputi dimensi, pengurangan ukuran PNG besar, JPEG sumber, gambar kecil, kegagalan konversi, progres pipeline, dan kesamaan paket audio sebelum/sesudah embedding.

## Pembaruan validasi lirik — 13 Oktober 2026

Lanjutan OPT-001/BUG-013 pada 1.0.10 memakai gerbang durasi ±2 detik pada exact/search dan menolak durasi kosong, memvalidasi timestamp, mempertahankan offset LRC, serta membedakan kandidat tidak cocok dari penyedia tidak memiliki lirik. Kredit sumber eksplisit kini mengatasi link Lamunan live dari channel Music Interactive tanpa menjadikan channel sebagai artis. [Laporan lirik/platform](LYRICS_PLATFORMS_2026-10-13.md) memuat pemeriksaan provider, pembandingan caption, tes dan batas cakupan.

**Status pada checkpoint1.0.10 (historis, digantikan tabel aktual di atas):** OPT-003/004 saat itu belum diimplementasikan; OPT-004 sekarang tersedia pada1.1.0, OPT-003 masih belum. Prioritaskan pemilihan ID rekaman dengan pratinjau dan koreksi offset yang tidak merusak file lama, karena beberapa record LRCLIB menyimpan timestamp identik pada durasi berbeda. Pertimbangkan caption manual dari sumber yang sama sebagai kandidat tambahan dengan atribusi/language dan timeline yang divalidasi; jangan menganggap caption narasi/terjemahan otomatis sebagai lirik asli. Tautan pertama Muse/Hysteria resmi di SoundCloud hanya memberi preview 30 detik tanpa login; sumber SoundCloud lain yang dikirim kemudian menyediakan format penuh, sehingga batas ini tidak berlaku untuk seluruh tautan Hysteria; tampilkan keterbatasan sumber tersebut jika menambah penanda preview, tanpa menawarkan bypass akses.


Tes akhir SoundCloud Ya Sudahlah 1.0.11 mengungkap delay auditori ±0,5 detik (BUG-033). Prioritaskan tie-break selisih durasi untuk kandidat dengan skor identitas sama, penyimpanan record ID/atribusi, dan OPT-003/004 dengan pratinjau serta backup. Kandidat berdurasi lebih dekat bukan bukti timing lebih tepat; pengguna mengonfirmasi sorotan terlambat 0,5 detik dengan selisih tetap, sehingga koreksi per lagu perlu memajukan lirik. Audio jangan diunduh ulang untuk mengganti lirik.


## Pembaruan 14 Oktober 2026 — 1.1.5

OPT-003 diterapkan; OPT-004 menerima input signed decimal seconds. 184 Kotlin/78 Python/lint0 dan build/audit lulus; seleksi/simpan lirik oleh pengelola serta artefak fisik diperiksa. [Laporan dan cakupan](MANUAL_LYRICS_1_1_5_2026-10-14.md). Status historis belum-diimplementasikan di bagian lama digantikan pembaruan ini; kriteria fault injection/provider tetap terbuka.

**Karaoke opsional — status diperbarui 16 Oktober 2026:** engine lokal UVR/ONNX, cuplikan dengan waktu mulai, lagu penuh, pratinjau dan publikasi instrumental terpisah sudah tersedia; kontrol L/R telah dihapus. Lihat [laporan implementasi](KARAOKE_1_1_6_2026-10-14.md). Atas permintaan pengelola, opsi gratis untuk memindahkan inference keluar HP sedang dikaji: [temuan layanan dan rancangan integrasi](KARAOKE_REMOTE_FREE_2026-10-16.md). Demucs/ZeroGPU merupakan kandidat percobaan berkuota, belum terintegrasi atau diuji dengan unggahan audio. Kualitas pengurangan vokal tidak dijamin untuk semua rekaman.


## Penugasan bertahap 1.1.15 — 16 Oktober 2026

Pengelola mengotorisasi kelima usulan: verifikasi metadata/lirik/koleksi, snapshot
antrean/seek, preferensi auto-resume, OPT-007 dan sisa ukuran/cleanup OPT-008.
Implementasi tersedia;296Kotlin/84Python/build/audit lulus. Bukti HP dan batas
setiap tahap ada pada [laporan1.1.15](IMPROVEMENTS_1_1_15.md). Catatan lama
melindungi PAUSED tetap berlaku pada cleanup otomatis; konfirmasi cleanup manual
baru secara eksplisit mengizinkan membuang parsial idle dan memulai retry dari nol.

## Enam fitur media — 17 Oktober 2026, kandidat1.1.17

Diminta pengelola: A/B karaoke pada posisi selaras, pengelola model/temporary,
format hasil FLAC/MP3/AAC, Favorit/playlist pribadi, sleep timer, dan cadangan
pengaturan/playlist/metadata/lirik. Implementasi dan keputusan di
[MEDIA_FEATURES_1_1_17.md](MEDIA_FEATURES_1_1_17.md); status build/perangkat harus
mengikuti hasil aktual di laporan tersebut, bukan checklist rencana lama.
Room10 menambah dua tabel referensi tanpa reset data; tidak menambah engine
pemutar, model atau server. Cloud karaoke gratis tetap riset terpisah.


### Checkpoint UI dan diagnostik 1.1.18 — 18 Oktober 2026

Komponen visual bersama, peluncuran video, kepemilikan inspeksi, cache/identitas
lirik, HTTP aktual, font besar dan dialog timer diperbaiki. Release dipasang dan
retest terfokus lulus;339Kotlin/90Python. Backlog tetap: keselarasan lirik per rekaman,
verifikasi A/B akhir/fault storage, TalkBack/font2×/tablet dan perangkat16KB.
[Laporan dan batas cakupan](REPAIR_2026-10-18.md).
