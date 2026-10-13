# Aturan perilaku TubeFlow

Semua path di bawah relatif terhadap root repository. Ini panduan menjaga perilaku saat mengubah kode, bukan pernyataan bahwa semua jalur saat ini sudah aman. Status temuan tetap mengacu pada `docs/BUG_FINDINGS.md` dan laporan terbaru yang dirujuk.

## Peta kode

Root Kotlin: `app/src/main/java/id/local/tubeflow/`.

| Area | Titik awal |
| --- | --- |
| Identitas URL, opsi, Room dan migrasi | `core/Models.kt` (`Selection`, Room), `core/Format.kt` |
| Submit/retry/inspeksi/cleanup/ukuran folder | `core/DownloadViewModel.kt` |
| Download, publikasi dan worker | `core/DownloadWorker.kt`, `core/Engine.kt` |
| URI SAF dan cakupan folder default | `core/StorageUris.kt`, `core/StorageFolderAccess.kt` |
| Tahap dan angka progres | `core/DownloadProgress.kt`, `ui/components.kt` |
| Lirik dan pencocokan kandidat | `core/Lyrics.kt`, `refetchLyrics` pada ViewModel |
| UI dan pengaturan | `ui/`, `core/SettingsStore.kt` |
| Bootstrap engine | `scripts/engine_bootstrap.py`, `app/src/main/assets/engine-bootstrap.py` |

## Worker dan Room

- `generation` membedakan eksekusi sebelum/sesudah resume atau revive. Pertahankan pemeriksaan kepemilikan pada mutasi worker; audit juga pending URI, sidecar, status lirik dan cleanup. Beberapa mutasi belum memiliki guard: jangan menganggap `cancelOwned` membuat seluruh cleanup aman.
- Status WorkManager `CANCELLED` tidak membuktikan coroutine/proses native sudah berhenti. Pastikan eksekusi lama tidak menyentuh status atau file milik eksekusi baru.
- Pause/Gagal mempertahankan berkas parsial ("Coba lagi" memeriksa jurnal identitas sumber/format dan validator/prefix HTTP sebelum memakai Range/ukuran downloader; kemampuan sumber berbeda-beda); putus koneksi kembali QUEUED dengan parsial utuh. Cancel/remove/clear harus membersihkan berkas setelah pemiliknya berhenti. Recovery harus tetap memiliki jejak file pending bila penghapusan gagal.
- Perubahan entity/versi Room harus disertai migrasi dan `app/schemas/`. Periksa migrasi yang terpasang di `Models.kt`; jangan mengandalkan nomor schema pada dokumen historis.
- Pembatalan engine mengikuti identitas proses/session/token di `Engine.kt`, `ProcessIdentity.kt` dan bootstrap. Jangan mengganti dengan kill global Python/FFmpeg.

## Penyimpanan dan file pendukung

- Default `Download/TubeFlow`; folder eksplisit SAF dipilih pengguna. Subfolder tes lama bukan tujuan default. Membaca ukuran folder melalui grant persisten tidak boleh diam-diam mengubah tujuan output.
- SAF memakai URI `content://`: deteksi dokumen dengan `DocumentsContract.isDocumentUri`; buat dokumen melalui URI induk yang dibangun dari tree. Gunakan helper `StorageUris` untuk penghapusan dan tangani hasil gagal dengan jujur.
- URI media/sidecar yang sedang dibuat perlu tercatat sampai publikasi berhasil atau penghapusan terbukti selesai. Audit jendela proses mati antara create, journal, write, publish dan finish; baca temuan cleanup sebelum menutupnya.
- Nama `.lrc` mengikuti nama aktual media yang diterbitkan provider, termasuk suffix tabrakan. Periksa display name aktual: URI berakhiran `.lrc` saja tidak membuktikan nama benar pada provider.
- Ukuran SAF merekursi MIME direktori yang tepat. Query MediaStore harus memiliki batas folder dan wildcard yang di-escape. Grant default hanya cocok dengan root tepat, bukan subfolder/sibling.
- Pisahkan ukuran folder penuh, file yang terlihat aplikasi, dan kegagalan baca. Jangan mengubah query gagal/batas traversal menjadi nol atau jumlah parsial yang tampak lengkap.

## Identitas, playlist dan UI

- Gunakan `Selection.videoId` dan `DownloadJob.matchesOutput` untuk identitas sumber serta seluruh opsi keluaran. Alias watch/youtu.be/Shorts/Live/Music untuk video sama tidak menjadi hasil baru; bitrate/format/opsi berbeda tetap sah.
- Snapshot identitas disimpan pada job: rawTitle/trackTitle/artist/artistEvidence/creator/remixer/versionTag. `TitleSplit` + `SourceMetadata` dipakai submit/worker/tag audio/Auto Folder/lirik. Jangan mempromosikan creator atau uploader menjadi artis; channel hanya sinyal jika credit judul lengkap memperkuatnya. Tanpa bukti, artist kosong. Structured track melewati pembersihan grup versi/credit yang sama dengan raw title; jangan membuat `(slowed) | slowed` atau mengulang artist yang sudah terbukti. Resolusi provider hanya menerima identitas unik dengan judul/artis/versi dan durasi yang cocok. Auto Folder memakai artis per item; artis tidak diketahui tetap root, tanpa fallback channel Mix atau tebakan segmen.
- Tag output memakai `MetadataTags`/override `meta_*`, termasuk artist kosong bila tidak terbukti. Postprocessor yt-dlp mendahulukan track dan memakai uploader sebagai fallback artist; mengganti field title/artist saja tidak cukup. Uji parser resmi, generator metadata dan tag file nyata; custom creator/remixer dapat dibuang container MP4.
- Platform persisten `platformId` berasal dari registry `SourcePlatforms` berdasarkan URL sumber. `DownloadJob.sourcePlatform` digunakan bersama antrean/riwayat/filter; fallback URL hanya untuk data lama. Thumbnail disimpan terpisah (`thumbnailUrl`/`coverPath`), dengan pemulihan artwork tertanam untuk riwayat lama. Jangan menurunkan platform dari nama file/creator.

- Terapkan pemeriksaan konsisten pada submit dan retry. Hasil selesai mencegah duplikat jika file masih ada; riwayat URL lama ikut dibandingkan.
- Identitas SoundCloud mengabaikan parameter pelacakan `utm_*`, tetapi mempertahankan parameter akses seperti `secret_token`. Jangan membuang seluruh query atau menormalkan URL platform lain tanpa bukti bahwa kontennya sama.
- Mix `RD...` mempertahankan seed dan membaca panel awal tanpa continuation otomatis. Playlist statis tetap mengikuti pagination sesuai batas engine. Jangan memaksakan 25 untuk semua Mix; hasil server dapat berubah/personal.
- Video dengan parameter list tetap dapat diinspeksi sebagai video, lalu playlist lewat aksi khusus. Retry inspeksi harus memungkinkan URL sama setelah kegagalan tanpa loop retry otomatis.
- Persen transfer adalah per track; pemrosesan/lirik/finalisasi memakai indikator tak tentu, penyalinan memakai byte tersimpan. Speed/ETA hanya ketika datanya valid. Jangan membuat timer 90→98% seolah-olah transfer masih berjalan.
- Kompresi sampul saat ini JPEG dengan sisi maksimum 800 px dan rasio aspek tetap. Jaga payload audio; uji perubahan sampul dengan ukuran/dimensi dan metadata/paket audio, bukan screenshot saja.
- Dua salinan bootstrap harus sama; perubahan pada `scripts/engine_bootstrap.py` ikut diperbarui pada asset Android dan diuji host.

## Lirik

Audio memakai LRCLIB dalam implementasi saat ini; caption/subtitle YouTube untuk mode video. Jangan menyebut LRCLIB sebagai Musixmatch atau penyedia berlisensi tanpa integrasi/bukti. Jika sumber diganti, tinjau kontrak API, atribusi dan privasi yang berlaku.

Nilai kecocokan judul/artis/versi/durasi pada seluruh endpoint dan fallback. Penerimaan timed lyrics memakai gerbang ±2 detik dan durasi kedua pihak wajib diketahui; jangan melonggarkan search untuk mengakomodasi intro/outro tanpa bukti offset. Validasi timestamp, pertahankan metadata/offset LRC, dan bedakan hasil tidak cocok dari penyedia tidak punya lirik. Kredit deskripsi performer/track hanya dipakai bila eksplisit, unik dan dicocokkan dengan judul lengkap; songwriter/arranger bukan bukti vokalis. Metadata cocok belum membuktikan timestamp sinkron dengan audio live/cover/remix; dengarkan beberapa posisi lagu pada HP. Lihat temuan terbuka pencocokan dan toleransi durasi sebelum mengklaim sinkronisasi beres.

Bedakan tidak ditemukan, instrumental, kegagalan jaringan dan gagal simpan. Download audio yang berhasil tetap sukses meskipun lirik gagal, dengan status lirik terpisah. Cari ulang lirik memakai lokasi audio yang sudah terbit, bukan folder pengaturan terbaru; penggantian gagal harus mempertahankan lirik lama. Periksa implementasi backup/rollback serta hasil audit terbaru; jangan mengganti jalur aman dengan penulisan langsung yang menghilangkan lirik lama saat gagal.

Pada pencarian fallback, skor identitas lebih kuat tetap prioritas; tie memakai selisih durasi terdekat setelah gerbang versi/durasi. Ini bukan bukti sinkronisasi auditori. Delay tetap perlu koreksi per rekaman dengan pratinjau/backup; jangan menggeser timestamp seluruh lagu karena satu contoh terlambat.
