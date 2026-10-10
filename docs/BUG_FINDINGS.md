# Daftar Temuan Bug TubeFlow

Dokumen serah-terima pengembangan berdasarkan hasil review dalam percakapan dan pembacaan kode pada **9 Oktober 2026**. Daftar komentar berisi **12 temuan** (1 P1, 9 P2, 2 P3), walaupun ringkasan review menyebut 11. Tidak tersedia riwayat Git untuk melacak commit perbaikan. Nomor baris di bawah sesuai snapshot saat dokumentasi dibuat; gunakan nama fungsi bila kode bergeser.

Pemeriksaan lanjutan logika download lirik menambahkan **BUG-013 dan BUG-014**, lalu catatan verifikasi perangkat 9 Oktober 2026 (malam) menambahkan **BUG-015** (provider SAF menyisipkan ekstensi mime). Audit ulang kemudian menemukan **BUG-016** (penggantian lirik dapat merusak file lama saat gagal). Total sekarang **16 temuan** (1 P1, 13 P2, 2 P3). BUG-013/014/016 berasal dari pemeriksaan kode/probe; bukti perangkat BUG-015 berasal dari sesi sebelumnya.

Usulan fitur dan optimasi lanjutan dicatat di [DEVELOPMENT_ROADMAP.md](DEVELOPMENT_ROADMAP.md), dengan ID OPT-001 sampai OPT-008, urutan kerja, dependensi bug, dan kriteria selesai.

## Status dan titik mulai

**Hasil audit ulang: belum semua tuntas.** Dari 15 temuan sebelumnya, **9 memiliki perbaikan kode sesuai masalah awal, 6 masih sebagian diperbaiki**; ditambah **1 temuan baru terbuka** (BUG-016). Perbaikan kode sesuai belum berarti verifikasi runtime lengkap. Bukti, lokasi kode terkini, dan langkah pengerjaan ada di [REVIEW_2026-10-09.md](REVIEW_2026-10-09.md). [PROGRESS.md](PROGRESS.md) dan "Catatan perbaikan" di bawah mencatat batch sebelumnya; kesimpulan audit terbaru menjadi acuan status.

| ID | Prioritas | Temuan | Status |
| --- | --- | --- | --- |
| BUG-001 | P1 | URI induk SAF untuk pembuatan dokumen | Diperbaiki; terverifikasi di perangkat (audio) — sidecar `.lrc` perlu uji |
| BUG-002 | P2 | Worker lama membatalkan unduhan yang dilanjutkan | Diperbaiki kode 10 Okt (R1: cleanup bergaris generasi + evaluasi ulang kepemilikan); menunggu verifikasi perangkat |
| BUG-003 | P2 | Penghapusan dokumen SAF salah jalur | Diperbaiki (`StorageUris`); penghapusan file SAF perlu uji perangkat |
| BUG-004 | P2 | Berkas parsial job yang dijeda tidak dibersihkan | Diperbaiki kode 10 Okt (R1: `remove`/`clearUnsuccessful` hanya menghapus direktori bila baris benar-benar terhapus); menunggu perangkat |
| BUG-005 | P2 | Deteksi duplikat mengabaikan opsi keluaran | Diperbaiki; teruji host |
| BUG-006 | P2 | Inspeksi gagal tidak bisa diulang untuk URL yang sama | Diperbaiki kode 10 Okt (R6: retry memakai jenis inspeksi terakhir); uji perangkat ditunda terakhir atas permintaan pengguna |
| BUG-007 | P2 | Tautan pendek/Shorts gagal dibuka sebagai playlist | Diperbaiki; teruji host + terverifikasi di perangkat (watch+list → kartu playlist 31 video) |
| BUG-008 | P2 | File pendukung parsial tertinggal saat publikasi gagal | Diperbaiki kode 10 Okt (R2: commit atomik per sidecar + jurnal jujur); simulasi proses mati menunggu runtime |
| BUG-009 | P2 | Pilihan bahasa lirik tidak diterapkan | Diperbaiki; terverifikasi di perangkat (toggle lirik audio, ringkasan tanpa bahasa) |
| BUG-010 | P2 | Nama lirik tidak mengikuti nama audio aktual | Diperbaiki; terverifikasi sebagian (Despacito `.lrc` ikut nama audio; kasus tabrakan `Judul (1)` belum diuji) |
| BUG-011 | P3 | Subfolder SAF tidak dihitung dalam ukuran penyimpanan | Diperbaiki; menunggu verifikasi runtime |
| BUG-012 | P3 | Ukuran penyimpanan mencakup folder lain | Diperbaiki; menunggu verifikasi runtime |
| BUG-013 | P2 | Pencocokan lirik terlalu longgar dan mengabaikan durasi/versi | Diperbaiki kode 10 Okt (R3: gerbang 12 dtk/10%, durasi ke API + worker, `tidy` bertahan penanda versi); sinkron menunggu perangkat |
| BUG-014 | P2 | Hasil pencarian/penyimpanan lirik tidak dilaporkan ke pengguna | Diperbaiki kode 10 Okt (R4: kegagalan HTTP → status jaringan); tes fixture host lulus |
| BUG-015 | P2 | Provider SAF menyisipkan ekstensi mime (`.lrc` → `.lrc.txt`) | Diperbaiki kode 10 Okt (R5: nama diverifikasi semua jalur, salah nama dibuang); simulasi provider menunggu runtime |
| BUG-016 | P2 | Penggantian lirik dapat merusak file lama ketika penulisan gagal | Diperbaiki kode 10 Okt (R7: cadangan isi lama + rollback sebelum timpa); simulasi kegagalan menunggu runtime |
| BUG-017 | P2 | Progres akhir dinaikkan timer dan mencampur transfer dengan pemrosesan | Diperbaiki 10 Oktober: byte per track + tahap terpisah; host dan Realme Android 10 lulus — [PROGRESS_COVER_2026-10-10.md](PROGRESS_COVER_2026-10-10.md) |
| BUG-018 | P2 | Pratinjau Mix memuat lanjutan antrean hingga 500 entri | Diperbaiki: panel awal tanpa continuation, seed dipertahankan; link contoh 25 entri di Realme Android 10 — [bukti](MIX_DUPLICATES_2026-10-10.md) |
| BUG-019 | P2 | Duplikat video lolos ketika bentuk URL berbeda | Diperbaiki: ID video + semua opsi, termasuk riwayat lama; dua alias ditolak di perangkat tanpa file baru — [bukti dan batas](MIX_DUPLICATES_2026-10-10.md) |
| BUG-020 | P2 | Ukuran nol pada folder berisi file lama dalam mode default | Kode dan signed 1.0.1 lulus host; root default, ukuran dan restart terverifikasi di HP — [rincian](STORAGE_SIZE_2026-10-10.md) |
| BUG-021 | P3 | Ringkasan "Sampul" tampil untuk preset FLAC/WAV/OGG yang tak menyematkan sampul | Diperbaiki 10 Okt (ringkasan ikut `coverSupported`) |
| BUG-022 | P3 | Tautan playlist dibagikan memunculkan error sesaat dan inspeksi ganda | Diperbaiki 10 Okt (inspeksi share diserahkan ke efek DownloadScreen) |
| BUG-023 | P3 | Label "Selesai" memakai waktu antre, bukan waktu selesai | Diperbaiki kode 10 Okt (kolom `completedAt`, Room v5 + migrasi 4→5 + snapshot schema); tampilan di HP menunggu |
| BUG-024 | P3 | Selector audio m4a/opus manual tanpa fallback; wadah sumber tampak tertukar | Diperbaiki 10 Okt (fallback ditambahkan; pemetaan lintas wadah dipertahankan — lihat koreksi di seksi) |
| BUG-025 | P3 | Putus koneksi di tengah unduh menghapus parsial sehingga mulai dari nol | Diperbaiki kode 10 Okt (parsial dipertahankan saat QUEUED/interrupted); lanjutan `.part` di HP menunggu |
| BUG-026 | P3 | "Unduh lagi" selalu error selama file hasil masih ada | Diperbaiki kode 10 Okt (ganti hasil: hapus lama + `requeueCompleted`, dialog konfirmasi Riwayat); uji HP menunggu |
| BUG-027 | P2 | Link Mix/RD tanpa seed (Tautan Bagikan autoplay) → "This playlist type is unviewable" | Diperbaiki kode + rilis 1.0.3 (seed dipulihkan dari ID Mix → jalur `watch+list+start_radio`); terbukti di HP dengan 1.0.2, rilis 1.0.3 terpasang di HP, verifikasi link menunggu buka kunci layar |

Urutan batch perbaikan sebelumnya (riwayat pengerjaan; masih ada celah menurut audit ulang):

1. Verifikasi BUG-001; selesaikan BUG-003 dan BUG-008 bersama jalur publikasi/penghapusan SAF.
2. Tangani BUG-002 sebelum BUG-004 supaya cleanup tidak menghapus berkas milik worker baru.
3. Tangani BUG-005, BUG-006, dan BUG-007; selesaikan alur lirik BUG-009, BUG-010, BUG-013, dan BUG-014 bersama-sama.
4. Perbaiki BUG-011 dan BUG-012.

**Pembaruan 10 Oktober 2026 (batch R1–R7 + review):** audit R1–R7 dan temuan BUG-021/022/024 sudah ditutup di tingkat kode dengan regresi host — rincian per ID di bawah dan di [REVIEW_2026-10-10.md](REVIEW_2026-10-10.md). **Pembaruan lanjut 10 Okt (gelombang kedua):** BUG-023 (`completedAt`, Room v5), BUG-025 (parsial dipertahankan saat putus koneksi), dan BUG-026 (Unduh lagi = ganti hasil + konfirmasi) selesai di tingkat kode dengan regresi host. **Pembaruan lanjut 10 Okt (BUG-027, temuan perangkat):** tautan Mix/RD tanpa seed kini memulihkan video seed dari ID playlist-nya sendiri sehingga inspeksi memakai jalur `watch+list` yang diterima YouTube — rincian di seksi BUG-027 dan [REVIEW_2026-10-10.md](REVIEW_2026-10-10.md). **Pekerjaan berikutnya:** satu sesi verifikasi perangkat (urutan jeda/lanjut cepat, simulasi gagal publish, penghapusan SAF, sinkronisasi `.lrc`, lanjutan `.part` setelah putus, alur Unduh lagi, BUG-006 mode pesawat — dijadwalkan paling akhir sesuai permintaan pengguna), lalu fitur roadmap (OPT-003/005/007/008, "Tambah lirik" untuk audio tanpa lirik). Deskripsi awal tiap ID di bawah tetap sebagai masalah awal; gunakan tabel dan bullet perbaikan untuk kondisi kode terkini.

## BUG-001 — P1: URI induk SAF saat membuat dokumen

**Lokasi:** [DownloadWorker.kt](../app/src/main/java/id/local/tubeflow/core/DownloadWorker.kt), `executeDownload` (144–145), `publishSidecars` (233–234), `treeRootUri` (255–259).

- **Pemicu/dampak:** Pilih folder melalui `OpenDocumentTree`, lalu unduh media. Review menemukan tree URI mentah diteruskan ke `createDocument`, sehingga penyimpanan gagal setelah download selesai; file pendukung terkena masalah yang sama.
- **Kode saat ini:** Kedua pemanggilan sudah menggunakan `treeRootUri(tree)`, yang memanggil `buildDocumentUriUsingTree(treeUri, getTreeDocumentId(treeUri))`. Jangan mengulang perubahan tersebut tanpa memeriksa kode.
- **Tindakan berikutnya:** Verifikasi publikasi media dan file pendukung pada folder SAF. Pertahankan penggunaan URI dokumen induk di semua jalur pembuatan dokumen.
- [ ] **Kriteria selesai:** Audio/video dan `.lrc`/subtitle berhasil disimpan serta dibuka dari folder SAF pada perangkat; tidak ada error `Invalid URI`. Catat provider, versi Android, APK, dan hasil.

## BUG-002 — P2: Worker lama membatalkan job yang dilanjutkan

**Lokasi:** [DownloadWorker.kt](../app/src/main/java/id/local/tubeflow/core/DownloadWorker.kt), `handleStop` (48–54), `takenOverByOther` (202–205); [DownloadViewModel.kt](../app/src/main/java/id/local/tubeflow/core/DownloadViewModel.kt), `awaitIdle`/`resume` (182–226).

- **Reproduksi:** Mulai unduhan, jeda, lalu segera lanjutkan ketika worker lama masih melakukan cleanup. Status WorkManager dapat sudah `CANCELLED` sebelum coroutine lama selesai.
- **Penyebab/dampak:** `handleStop` membaca status baru `QUEUED`/`RUNNING`, lalu memanggil `dao.cancel(jobId)` berdasarkan pembatalan worker lama. Pemeriksaan kepemilikan saat cleanup belum melindungi mutasi status ini.
- **Arah perbaikan:** Lindungi mutasi stop dengan kepemilikan worker. Pertimbangkan token/generasi eksekusi dan update bersyarat agar pemeriksaan dan mutasi tidak menyisakan race. `awaitIdle` dengan delay tetap bukan bukti coroutine telah berhenti.
- [x] **Perbaikan kode 10 Oktober 2026 (R1, host):** blok `finally` worker mengevaluasi ulang kepemilikan di dalam `NonCancellable` tepat sebelum tiap mutasi; `dao.clearPending` diganti `clearPendingOwned(id, gen)`; penghapusan direktori mensyaratkan baris bergenerasi sama + `!takenOverByOther()` terbaru. Regresi host: `test_stale_generation_cannot_clear_new_pending`.
- [ ] **Kriteria selesai:** Tes dengan urutan stop/resume yang dikendalikan membuktikan worker lama tidak bisa mengubah status atau mematikan engine baru. Ulangi jeda/lanjut cepat pada perangkat, termasuk `resumeAll`.

## BUG-003 — P2: Penghapusan dokumen SAF dan recovery

**Lokasi:** [DownloadViewModel.kt](../app/src/main/java/id/local/tubeflow/core/DownloadViewModel.kt), `recovery` (66–75), `removeFile`/`deleteStoredUri` (241–260); [DownloadWorker.kt](../app/src/main/java/id/local/tubeflow/core/DownloadWorker.kt), `isSafDocument`/`deleteUri` (261–272).

- **Pemicu/dampak:** Hapus hasil dari folder SAF atau lakukan recovery atas `pendingUri` SAF. Pemeriksaan lama `scheme == "document"` tidak cocok dengan URI `content://`; penghapusan gagal sementara riwayat dan pesan UI menganggap berhasil.
- **Kode saat ini:** Helper worker dan `deleteStoredUri` sudah memakai pemeriksaan path dan `deleteDocument`. Namun recovery masih memanggil `contentResolver.delete` langsung; kegagalan helper masih ditelan oleh `runCatching`.
- **Arah perbaikan:** Satukan helper penghapusan untuk worker, riwayat, dan recovery; gunakan `DocumentsContract.isDocumentUri(context, uri)` untuk memilih `deleteDocument`. Bedakan file sudah hilang dari kegagalan izin/provider; jangan mengumumkan keberhasilan penghapusan file jika gagal.
- [ ] **Kriteria selesai:** Media dan sidecar SAF benar-benar terhapus; pending SAF dibersihkan saat recovery. Uji izin dicabut/provider menolak, serta jalur MediaStore. UI dan riwayat mencerminkan hasil sebenarnya.

## BUG-004 — P2: Berkas parsial tertinggal setelah pembatalan

**Lokasi:** [DownloadViewModel.kt](../app/src/main/java/id/local/tubeflow/core/DownloadViewModel.kt), `cancelOne` (175), `remove` (239), `clearUnsuccessful` (271–274), `recovery` (66–75); [DownloadWorker.kt](../app/src/main/java/id/local/tubeflow/core/DownloadWorker.kt), cleanup (187–198).

- **Reproduksi:** Unduh sebagian, jeda sampai worker berhenti, lalu batalkan dan hapus entri. Periksa `noBackupFilesDir/downloads/<jobId>`.
- **Penyebab/dampak:** Jeda mempertahankan direktori parsial, tetapi pembatalan tidak menjalankan cleanup mandiri. Penghapusan baris tidak membersihkan direktori; recovery melewati job cancelled tanpa `pendingUri`. Penyimpanan terus terpakai.
- **Arah perbaikan:** Bersihkan berkas sementara setelah engine dan coroutine pemilik benar-benar berhenti. Cakup pembatalan tunggal/semua, penghapusan entri, pembersihan gagal/dibatalkan, dan recovery. Terapkan perlindungan kepemilikan BUG-002 sebelum menghapus direktori.
- [x] **Perbaikan kode 10 Oktober 2026 (R1):** `remove` menghapus direktori hanya setelah `dao.remove` mengembalikan >0; `clearUnsuccessful` memeriksa keberadaan baris per id sebelum menghapus direktori — parsial job yang baru dilanjutkan tidak ikut terhapus.
- [ ] **Kriteria selesai:** Parsial tetap ada saat `PAUSED`, tetapi hilang setelah cancel/remove/clear dan setelah restart. Tidak ada berkas worker baru yang terhapus akibat cleanup terlambat.

## BUG-005 — P2: Duplikat harus membandingkan opsi keluaran

**Lokasi:** [DownloadViewModel.kt](../app/src/main/java/id/local/tubeflow/core/DownloadViewModel.kt), `submit` (139–149); [Models.kt](../app/src/main/java/id/local/tubeflow/core/Models.kt), `DownloadOptions`/`DownloadJob`.

- **Reproduksi:** Selesaikan MP3 128 kbps, lalu antrekan URL sama sebagai MP3 320 kbps atau tambahkan lirik/sampul. Permintaan berbeda ditolak sebagai hasil yang sudah ada.
- **Penyebab:** Pembandingan hasil selesai hanya mencakup URL, mode, format, dan quality. Pembandingan antrean juga belum mencakup `embedSubs`/`embedThumb`.
- **Arah perbaikan:** Gunakan satu pembanding opsi hasil untuk antrean dan hasil selesai: URL, mode, format, quality, bitrate, subtitles, embedSubs, thumbnail, embedThumb. Pertahankan pengecualian `keepId` dan pemeriksaan keberadaan hasil.
- [ ] **Kriteria selesai:** Tes membedakan setiap opsi keluaran; opsi identik ditolak jika masih antre/hasilnya ada, variasi diterima, dan hasil yang dihapus manual tidak menghalangi download ulang.

## BUG-006 — P2: Inspeksi gagal perlu bisa dicoba lagi

**Lokasi:** [DownloadScreen.kt](../app/src/main/java/id/local/tubeflow/ui/DownloadScreen.kt), `inspectedUrl`/`LaunchedEffect(trimmed)` (86, 100–107); [DownloadViewModel.kt](../app/src/main/java/id/local/tubeflow/core/DownloadViewModel.kt), `inspect`/`inspectPlaylist` (80–100).

- **Reproduksi:** Masukkan URL valid saat jaringan gagal, pulihkan jaringan, lalu tempel URL yang sama atau buka kembali tab. Metadata tidak muncul dan opsi download tidak tersedia.
- **Penyebab:** `inspectedUrl` ditandai sebelum sukses. Efek hanya bergantung pada URL dan guard mencegah percobaan berikutnya.
- **Arah perbaikan:** Sediakan aksi coba lagi yang memanggil inspeksi walau URL sama; pisahkan status sedang diperiksa dari sukses. Jangan membuat loop retry otomatis tanpa batas. Cakup video dan playlist.
- [x] **Perbaikan kode 10 Oktober 2026 (R6):** ViewModel menyimpan jenis inspeksi terakhir (`lastInspectPlaylist`); tombol "Coba lagi" memanggil `retryInspect` yang mengulang operasi yang sama — watch+list yang gagal sebagai playlist kini mengulang playlist, bukan video.
- [ ] **Kriteria selesai:** Setelah satu kegagalan, retry URL sama berhasil menampilkan metadata/pilihan download; klik ganda saat busy tidak memulai inspeksi bertumpuk.

## BUG-007 — P2: Normalisasi tautan playlist pendek/Shorts

**Lokasi:** [DownloadScreen.kt](../app/src/main/java/id/local/tubeflow/ui/DownloadScreen.kt), tombol buka playlist (192–194); [TubeFlowApp.kt](../app/src/main/java/id/local/tubeflow/ui/TubeFlowApp.kt), callback `inspectPlaylist` (128); [Models.kt](../app/src/main/java/id/local/tubeflow/core/Models.kt), `validPlaylistUrl`/`listParam`; [Engine.kt](../app/src/main/java/id/local/tubeflow/core/Engine.kt), `inspectPlaylist`.

- **Reproduksi:** Buka `https://youtu.be/abc123?list=PLabc123` atau tautan Shorts dengan parameter `list`, lalu tekan buka sebagai playlist. Validator menolak path tersebut sebelum inspeksi.
- **Arah perbaikan:** Ambil parameter `list` dari tautan YouTube yang valid, lalu bentuk URL kanonis `https://www.youtube.com/playlist?list=<id>` dengan encoding query yang benar. Selaraskan identitas URL metadata dengan `shownPlaylist`, yang sekarang membandingkan URL secara persis, agar hasil normalisasi tetap tampil.
- [ ] **Kriteria selesai:** Tes normalisasi watch/shorts/youtu.be/playlist, parameter kosong/tidak valid, dan host asing. Playlist dari tautan pendek tampil serta bisa diantrekan; tautan tanpa list tetap membuka video.

## BUG-008 — P2: Cleanup file pendukung ketika copy gagal

**Lokasi:** [DownloadWorker.kt](../app/src/main/java/id/local/tubeflow/core/DownloadWorker.kt), `publishSidecars` (227–250), pemanggilan `dao.sidecars` (169).

- **Reproduksi:** Simulasikan stream null, write gagal/storage penuh, atau publikasi MediaStore gagal setelah URI sidecar dibuat.
- **Penyebab/dampak:** `runCatching` menelan error dan `return@runCatching` keluar tanpa menghapus URI. URI belum tercatat di database, sehingga dokumen parsial/pending row tertinggal meskipun job selesai.
- **Arah perbaikan:** Simpan URI yang sedang dibuat; hapus pada setiap jalur kegagalan. Wajibkan keberhasilan publikasi sebelum menambahkan ke `recorded`. Pastikan URI yang sudah dibuat bisa ditemukan recovery jika proses mati, misalnya lewat pencatatan pending sidecar. Gunakan helper penghapusan BUG-003.
- [x] **Perbaikan kode 10 Oktober 2026 (R2, host):** DAO `commitSidecar` mencatat baris terbit + melepas jurnal dalam SATU UPDATE atomik (per sidecar); jalur gagal hanya melepas jurnal bila `StorageUris.delete` terbukti berhasil; `writeLyricBesideAudio` (cari ulang) menjurnal URI sebelum menulis, memperbarui jurnal setelah rename (ID dokumen bisa berubah), dan melepasnya setelah tercatat; baris sidecar yatim (`outputUri` null) dibersihkan di awal run worker dan di recovery. Regresi host: `test_sidecar_commit_is_atomic_with_journal_release`.
- [ ] **Kriteria selesai:** Tes kegagalan open/write/publish tidak menyisakan dokumen atau pending row pada SAF maupun MediaStore. Hanya sidecar berhasil yang dicatat; uji proses mati saat copy untuk memeriksa recovery.

## BUG-009 — P2: Pilihan bahasa lirik tidak berpengaruh

**Lokasi:** [DownloadScreen.kt](../app/src/main/java/id/local/tubeflow/ui/DownloadScreen.kt), pilihan bahasa (353–369); [DownloadWorker.kt](../app/src/main/java/id/local/tubeflow/core/DownloadWorker.kt), pemilihan lirik (109–112), `fetchSyncedLyrics` (310 dan seterusnya).

- **Reproduksi:** Dalam mode audio, pilih Indonesia, Inggris, atau Semua bahasa. Worker hanya membaca apakah `subtitles` kosong; pencarian LRCLIB tidak memakai bahasa tersebut.
- **Arah perbaikan:** Pilihan paling langsung: batasi menu bahasa ke mode video dan tampilkan toggle lirik penyedia pada mode audio, dengan opsi internal yang konsisten. Alternatifnya implementasikan pemilihan bahasa yang benar-benar didukung penyedia. Selaraskan ringkasan pilihan dan opsi tersimpan ketika mode berganti.
- [ ] **Kriteria selesai:** UI audio tidak menjanjikan bahasa yang tidak diterapkan; UI video tetap memilih subtitle sesuai bahasa. Uji pergantian mode dan kasus lirik tidak ditemukan.

## BUG-010 — P2: Nama lirik mengikuti nama audio yang diterbitkan

**Lokasi:** [DownloadWorker.kt](../app/src/main/java/id/local/tubeflow/core/DownloadWorker.kt), publikasi media (142–169), `publishSidecars`/`safeName` (209, 225–237).

- **Reproduksi:** Folder sudah berisi `Judul.mp3` tanpa lirik. Terbitkan audio baru dengan judul sama dan lirik; provider dapat menamainya `Judul (1).mp3`, tetapi sidecar masih memakai `safeName` dari judul.
- **Dampak:** `.lrc` cocok dengan audio lama, sehingga pemutar tidak otomatis mengaitkannya dengan audio baru.
- **Arah perbaikan:** Baca nama tampilan aktual media yang diterbitkan (MediaStore `DISPLAY_NAME` atau SAF `COLUMN_DISPLAY_NAME`) dan teruskan stem-nya ke publikasi sidecar, atau alokasikan nama unik bersama. Periksa pula suffix lokal `media.lrc` agar nama akhirnya benar-benar mengikuti stem audio, serta collision sidecar.
- [ ] **Kriteria selesai:** Audio `Judul (1).mp3` mendapat `Judul (1).lrc` pada SAF dan MediaStore; audio/lirik lama tetap benar. Uji dua download berjudul sama dan asosiasi lirik di pemutar.

## BUG-011 — P3: Rekursi ukuran folder SAF

**Lokasi:** [DownloadViewModel.kt](../app/src/main/java/id/local/tubeflow/core/DownloadViewModel.kt), `measureTree` (44–62), khususnya 54–57.

- **Reproduksi:** Pilih folder SAF yang file-filenya berada di subfolder. Ukuran tampil terlalu kecil atau nol.
- **Penyebab:** MIME direktori normal `vnd.android.document/directory` tidak memenuhi `endsWith("/")`.
- **Arah perbaikan:** Bandingkan dengan `DocumentsContract.Document.MIME_TYPE_DIR`, lalu lakukan rekursi. Pertahankan batas depth/jumlah dokumen; jelaskan jika angka dibatasi, dan tangani ukuran null dari provider.
- [ ] **Kriteria selesai:** Folder dengan file langsung serta subfolder dihitung tepat dalam batas traversal; direktori tidak diperlakukan sebagai file, dan provider dengan ukuran null tidak menyebabkan crash.

## BUG-012 — P3: Batas folder dalam query ukuran MediaStore

**Lokasi:** [DownloadViewModel.kt](../app/src/main/java/id/local/tubeflow/core/DownloadViewModel.kt), `measureStorage` (35–41).

- **Reproduksi:** Isi `Download/TubeFlow/` dan `Download/TubeFlowBackup/`, atau `Download/my_folder/` dan `Download/myXfolder/`. Pilih folder pertama lalu lihat ukuran.
- **Penyebab/dampak:** Prefix `Download/<folder>%` mencakup folder saudara; `_` dan `%` bermakna wildcard SQL. Ukuran mencakup file di luar folder pilihan.
- **Arah perbaikan:** Tetapkan apakah ukuran mencakup subfolder. Untuk folder langsung, gunakan kesetaraan `RELATIVE_PATH` dengan trailing `/`; jika mencakup subfolder, gunakan prefix berbatas `/` dan escape wildcard secara eksplisit sesuai dukungan provider.
- [ ] **Kriteria selesai:** Tes path target, folder saudara, nama dengan underscore, dan subfolder sesuai cakupan yang dipilih; ukurannya tidak mencakup folder lain.

## BUG-013 — P2: Pencocokan lirik mengabaikan artis/durasi/versi

**Lokasi:** [DownloadWorker.kt](../app/src/main/java/id/local/tubeflow/core/DownloadWorker.kt), `lyricScore` (298–302), `fetchSyncedLyrics` (310–362); [Models.kt](../app/src/main/java/id/local/tubeflow/core/Models.kt), metadata dan `DownloadJob`.

- **Pemicu/dampak:** Lagu berjudul sama oleh artis berbeda, atau versi studio/live/cover/remix dengan durasi berbeda, dapat dipasangkan dengan lirik yang salah atau timestamp yang tidak sinkron.
- **Bukti kode:** Skor terdiri dari 65% judul dan 35% artis; ambang penerimaan hanya `0.45`. Judul persis dengan artis sama sekali tidak cocok tetap mendapat `0.65`. Saat artis kosong, skor artis memakai skor judul. Respons `/api/get` langsung diberi skor `1.0` tanpa mengevaluasi metadata hasil. Query dan penilaian kandidat tidak memakai durasi. Kandidat alternatif juga membuang semua teks dalam tanda kurung, termasuk penanda versi lagu.
- **Arah perbaikan:** Gunakan metadata track/artis yang tersedia, bukan hanya pemisahan judul berdasarkan ` - `. Terapkan syarat minimum kecocokan judul dan artis secara terpisah; verifikasi respons `/api/get` juga. Cocokkan durasi rekaman dan pertahankan penanda versi yang bermakna ketika menormalkan judul. Jika identitas/versi meragukan, laporkan tidak ada kecocokan yang aman. LRCLIB mendokumentasikan pencocokan durasi ±2 detik pada endpoint get; lihat [dokumentasi API](https://lrclib.net/docs). Untuk video dengan intro/outro atau versi berbeda, durasi mirip saja belum membuktikan timestamp sinkron.
- [x] **Perbaikan kode 10 Oktober 2026 (R3, host):** gerbang durasi ketat `max(12 dtk, 10%)` (200 vs 229 ditolak; 200 vs 210 tetap diterima); durasi valid ikut dikirim ke `/api/get` (gerbang ±2 dtk penyedia); `tidy` membuang hanya embel-embel promosi dan MEMPERTAHANKAN penanda versi — pasangan query kedua untuk "(Live)" tidak lagi terbentuk; query tanpa artis DAN durasi hanya menerima judul persis (kompromi sampai OPT-003 pemilihan kandidat); worker menyimpan `durationSeconds` hasil inspect placeholder (DAO `duration`, bergaris generasi). Regresi host: `LyricTest` (gerbang + identitas kosong) dan `LyricsFetchTest` (7 tes fixture HTTP lokal termasuk uji bypass end-to-end).
- [ ] **Kriteria selesai:** Tes kandidat dengan judul sama/artis salah ditolak; versi/durasi salah ditolak; metadata cocok diterima; artis kosong tidak otomatis dianggap cocok penuh. Uji `/api/get` dan fallback search, lalu dengarkan beberapa titik lagu untuk memverifikasi sinkronisasi `.lrc` pada perangkat.

## BUG-014 — P2: Kegagalan lirik tidak punya hasil yang jelas

**Lokasi:** [DownloadWorker.kt](../app/src/main/java/id/local/tubeflow/core/DownloadWorker.kt), `lrclibBody` (275–282), `fetchSyncedLyrics`, penulisan `media.lrc` (168), `publishSidecars` (227–250), `notifyResult`; [DownloadScreen.kt](../app/src/main/java/id/local/tubeflow/ui/DownloadScreen.kt), keterangan lirik (369).

- **Reproduksi yang perlu diuji:** Aktifkan lirik lalu simulasikan timeout/non-200 LRCLIB, respons tanpa synced lyrics, lagu instrumental, dan kegagalan penulisan/publikasi `.lrc`. Audio dapat berhasil, tetapi pengguna tidak mendapat penjelasan mengapa lirik tidak tersedia.
- **Penyebab:** HTTP error dikembalikan sebagai `null`, hasil pencarian juga nullable, penulisan lokal menggunakan `runCatching`, dan publikasi sidecar menelan kegagalan. Notifikasi sukses hanya menunjukkan hasil media, tanpa status lirik. Pengguna tidak bisa membedakan tidak ditemukan, instrumental, gagal jaringan, dan gagal menyimpan.
- **Arah perbaikan:** Kembalikan hasil terstruktur untuk pencarian dan penyimpanan lirik, lalu tampilkan/simpan statusnya pada hasil unduhan. Pertahankan keberhasilan audio jika audio memang tersimpan; beri keterangan terpisah saat lirik tidak tersedia atau gagal. Sediakan retry untuk kegagalan sementara tanpa wajib mengunduh ulang audio. Cleanup URI parsial tetap ditangani BUG-008.
- [x] **Perbaikan kode 10 Oktober 2026 (R4, host):** klasifikasi akhir memakai satu aturan — ada kegagalan HTTP di jalur mana pun tanpa kandidat diterima → `jaringan` (bisa dicoba lagi); semua respons sukses tanpa kandidat → `tidak_ditemukan`. Penanda `sawResponse` dihapus. Regresi host: `LyricsFetchTest.partialSearchFailureReportsNetworkNotNotFound`, `completeEmptySearchReportsNotFound`, `allFailuresReportNetwork`.
- [ ] **Kriteria selesai:** Tes membedakan lirik tersimpan, tidak ditemukan, instrumental, gagal jaringan, dan gagal simpan. Riwayat/pesan hasil menampilkan status yang tepat, retry bekerja untuk kegagalan sementara, dan audio berhasil tidak dilabeli gagal hanya karena lirik tidak tersedia.

## BUG-015 — P2: Nama `.lrc` berubah karena provider SAF

**Lokasi terkini:** [DownloadWorker.kt](../app/src/main/java/id/local/tubeflow/core/DownloadWorker.kt), 267–272 dan `hasPublishedLyric`; [DownloadViewModel.kt](../app/src/main/java/id/local/tubeflow/core/DownloadViewModel.kt), 370–395.

- **Masalah awal:** Catatan perangkat menunjukkan provider membuat `.lrc.txt` ketika MIME `text/plain` digunakan. Batch sebelumnya menambahkan rename dan pengecekan nama tampilan pada hasil download awal.
- **Sisa audit:** Cari ulang tetap memakai URI hasil create ketika rename gagal/null dan mengembalikan sukses setelah write, tanpa memastikan nama aktual `.lrc` mengikuti audio. Lihat audit R5 di [REVIEW_2026-10-09.md](REVIEW_2026-10-09.md).
- [x] **Perbaikan kode 10 Oktober 2026 (R5):** jalur cari ulang memverifikasi nama tampilan akhir berakhiran `.lrc` sebelum melapor sukses; rename ditolak provider → URI dibuang (bila delete terbukti berhasil, jejak tetap di jurnal bila gagal) dan status jatuh ke `gagal_simpan`. Jalur unduh awal: sidecar salah nama dibersihkan dari tujuan dan daftar saat status jatuh ke `gagal_simpan`.
- [ ] **Kriteria selesai:** Jalur download awal dan cari ulang memverifikasi nama akhir; provider yang menambahkan ekstensi lalu menolak rename menghasilkan status gagal simpan dan cleanup terjurnal, bukan sukses palsu.

## BUG-016 — P2: Penimpaan lirik lama tidak aman terhadap kegagalan

**Lokasi:** [DownloadViewModel.kt](../app/src/main/java/id/local/tubeflow/core/DownloadViewModel.kt), `writeLyricBesideAudio`/`writeTo` (345–365).

- **Pemicu/dampak:** Cari ulang lirik ketika sudah ada `.lrc`; stream menimpa file lama dengan mode `wt`. Bila write/close gagal setelah file terbuka, lirik lama dapat kosong atau parsial meskipun fungsi mengembalikan false.
- **Arah perbaikan:** Siapkan dan verifikasi pengganti lebih dulu; gunakan mekanisme replace provider atau backup/rollback. Pertahankan file lama sampai penggantian berhasil, serta jurnal URI dan pembaruan database sesuai BUG-008. Lihat audit R7 di [REVIEW_2026-10-09.md](REVIEW_2026-10-09.md).
- [x] **Perbaikan kode 10 Oktober 2026 (R7):** penimpaan `.lrc` lama membaca isi lama sebagai cadangan lebih dulu; write/close gagal → rollback dari cadangan + melapor `gagal_simpan` (bukan diam-diam mengosongkan). Cadangan gagal dibaca → tetap tanpa rollback (perilaku lama, dikomentari di kode). Pencatatan URI pengganti tetap lewat jurnal R2.
- [ ] **Kriteria selesai:** Uji kegagalan setelah open, write sebagian, dan close; lirik lama tetap utuh/dapat dipulihkan, tidak ada sidecar baru yatim, dan UI melaporkan gagal simpan.

## Catatan perbaikan batch sebelumnya (9 Oktober 2026)

Perubahan, tes, dan bukti yang dilaporkan batch sebelumnya. Klaim penyelesaian di bagian historis ini dikoreksi oleh status audit ulang di awal dokumen.

- **BUG-001:** `treeRootUri()` (`buildDocumentUriUsingTree`) dipakai di semua jalur `createDocument`. Terverifikasi di perangkat: 11 MP3 masuk folder SAF pilihan pengguna tanpa `Invalid URI`. Sidecar `.lrc` dari folder SAF belum diuji.
- **BUG-002:** Token `generation` pada `downloads` (migrasi 2→3). `resume`/`revive` menaikkan generasi; semua update worker (`progress/title/pending/finish/failOwned/interrupted/cancelOwned`) bergaris syarat `generation = :gen`; `handleStop` membaca generasi SEBELUM antre slot dan menolak bila job sudah `PAUSED`/`CANCELLED`/digantikan (`takenOverByOther`). Regresi host: `test_stale_generation_cannot_touch_resumed_job`, `test_resume_bumps_generation`.
- **BUG-003:** Helper tunggal `StorageUris.delete` (`DocumentsContract.isDocumentUri`, membedakan "sudah hilang" dari "ditolak") dipakai worker, riwayat (`removeFile` melapor jujur bila file gagal dihapus), dan recovery. Regresi host: jalur `pendingUri`/`sidecarPending` dibersihkan recovery.
- **BUG-004:** `deleteJobDir()` dijalankan pada batal tunggal/semua (setelah pekerjaan berhenti), hapus entri, bersihkan gagal/dibatalkan, dan recovery; job `PAUSED` dikecualikan agar parsial tetap bisa dilanjutkan. Batal tunggal terverifikasi di perangkat.
- **BUG-005:** `DownloadJob.matchesOutput(url, options)` membandingkan SELURUH opsi (url, mode, format, quality, bitrate, subtitles, embedSubs, thumbnail, embedThumb) untuk duplikat antrean maupun hasil selesai, dengan pengecualian `keepId` dan cek keberadaan file. Regresi host: `matchesOutputComparesEveryOutputOption`.
- **BUG-006:** `inspectError` StateFlow terpisah dari `busy`; DownloadScreen menampilkan pesan gagal + tombol "Coba lagi" yang memanggil inspeksi walau URL sama; inspeksi baru membatalkan yang lama (tanpa tumpang tindih). **Menunggu verifikasi runtime.**
- **BUG-007:** `validPlaylistUrl` menerima watch/shorts/live/youtu.be yang membawa `list`; `canonicalPlaylistUrl` membentuk URL kanonis hanya untuk host YouTube; `inspectPlaylist` menginspeksi URL kanonis tetapi menyimpan URL asli sebagai identitas metadata sehingga kartu playlist tampil. Tautan yang juga video tetap membuka video lebih dulu — buka playlist lewat tombol. Regresi host: `acceptsShortAndEmbeddedPlaylistLinks`, `canonicalizesPlaylistLinksFromAnyYoutubeHost`.
- **BUG-008:** `publishSidecars` mencatat URI yang sedang dibuat ke `sidecarPending` SEBELUM menulis; setiap jalur kegagalan menghapus URI buatan; publikasi wajib sukses sebelum masuk `recorded`; recovery + awal `executeDownload` membersihkan `sidecarPending`. **Menunggu verifikasi runtime (simulasi gagal open/write/publish).**
- **BUG-009:** Menu bahasa subtitle hanya tampil di mode video; mode audio memakai toggle "Download lirik" terpisah (state `lyrics`) dengan nilai internal `"asli"`; ringkasan pilihan menampilkan "Lirik" tanpa bahasa di mode audio. **Menunggu verifikasi runtime (uji ganti mode).**
- **BUG-010:** Nama sidecar mengikuti `displayNameOf()` hasil publikasi (`publishedStem`), bukan judul. **Menunggu verifikasi runtime (dua unduhan berjudul sama → `Judul (1).mp3` + `Judul (1).lrc`).**
- **BUG-011:** `measureTree` membandingkan `MIME_TYPE_DIR`, rekursi dengan batas depth 8 / 4000 dokumen, dan mengabaikan ukuran `null`. **Menunggu verifikasi runtime.**
- **BUG-012:** `measureStorage` memakai kesetaraan `RELATIVE_PATH = folder/` ATAU prefix `folder/%` dengan `ESCAPE '\'` — folder saudara dan wildcard `_`/`%` tak lagi ikut terhitung. **Menunggu verifikasi runtime.**
- **BUG-013:** Modul baru `core/Lyrics.kt`: `LyricMatch` menilai kandidat dengan syarat judul DAN artis terpisah, gerbang versi (live/cover/remix/dst. wajib sama), gerbang durasi (toleransi 20 dtk atau 15%), respons `/api/get` ikut dinilai (bukan skor 1.0 buta); `Lyrics.fetch` menggantikan `fetchSyncedLyrics`/`lyricScore` di worker. 8 tes host di `LyricTest` lulus. **Sinkronisasi perlu didengar di perangkat.**
- **BUG-014:** `LyricOutcome` terstruktur (`tersimpan`/`tidak_ditemukan`/`instrumental`/`jaringan`/`gagal_simpan`) disimpan ke kolom `lyricStatus` (migrasi 2→3), tampil di kartu Riwayat, dan disebutkan di notifikasi hasil; gagal jaringan dibedakan dari "tidak ada"; tombol **"Cari ulang"** (`refetchLyrics`) mencari ulang lirik TANPA mengunduh audio — menimpa `.lrc` lama bila ada atau membuat baru di folder yang sama dengan audio (identitas folder diambil dari `outputUri`, bukan pengaturan sekarang), dengan guard anti-konkurensi. Regresi host: `test_lyric_status_recorded_and_reset_on_revive`. Terverifikasi di perangkat: unduhan Despacito menampilkan "Lirik tersimpan" + file `.lrc` 3373 byte di folder SAF.
- **BUG-015 (temuan perangkat 9 Okt 2026):** `DocumentsContract.createDocument` pada provider SAF Android 10 menyisipkan ekstensi mime bila nama yang diminta tak cocok: membuat `X.lrc` dengan mime `text/plain` menghasilkan `X.lrc.txt` (pemain tak mengenalinya), dan percobaan berikutnya menjadi `X.lrc (1).txt`. Diperbaiki di dua jalur (`publishSidecars` worker dan `writeLyricBesideAudio`): **`renameDocument` dipaksa kembali ke nama yang diminta** setelah create bila nama tampilan berbeda; status lirik dinilai dari **nama tampilan aktual** (`displayNameOf`), bukan akhiran URI; `refetchLyrics` juga membuang sisa sidecar berekstensi salah setelah pengganti terbit. Terverifikasi di perangkat: `Luis Fonsi - Despacito....lrc` (tepat satu file, nama benar, isi lirik sinkron asli).

Pemeriksaan yang dijalankan dari root repo:

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew -PuseGoogleMirror=true testDebugUnitTest lintDebug assembleDebug  # 43 tes lulus, lint 0 error/16 warning
python3 -m unittest discover -s scripts -p "test_*.py"  # 24 tes lulus
```

## Validasi dan bukti yang perlu ditinggalkan

Audit ulang 9 Oktober 2026: Gradle unit/lint/debug/release berhasil, laporan **43 tes Kotlin** tanpa failure/error, **24 tes Python** dijalankan dan lulus, lint **0 error / 17 warning**, audit native debug dan refleksi ZIP release ketiga ABI tanpa kegagalan. Probe tambahan tetap menemukan bug yang belum tercakup tes rutin; rincian ada di [REVIEW_2026-10-09.md](REVIEW_2026-10-09.md). Tidak ada tes perangkat baru dalam audit ulang. Bukti perangkat sebelumnya tetap tercatat di [PROGRESS.md](PROGRESS.md); kelulusan host tidak membuktikan alur provider SAF, race worker, atau asosiasi lirik berjalan benar.

Jalankan dari root repository setelah perbaikan (prasyarat native/build ada di [README](../README.md)):

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --no-daemon
python3 -m unittest discover -s scripts -p "test_*.py"
for apk in app/build/outputs/apk/debug/*.apk; do
  python3 scripts/audit_native.py "$apk"
done
for apk in app/build/outputs/apk/release/*.apk; do
  python3 scripts/audit_release_zip_fields.py "$apk"
done
```

Letakkan regresi Kotlin di `app/src/test/java/id/local/tubeflow/core/` dan regresi host di `scripts/test_*.py`. Gunakan tes dengan hasil perilaku yang dapat diperiksa; kasus Android provider/UI memerlukan tes Android atau verifikasi perangkat. Ikuti [RELEASE.md](RELEASE.md) dan catat hasil perangkat di [DEVICE_TEST.md](DEVICE_TEST.md).

Saat menyelesaikan setiap ID:

1. Perbarui status tabel dan checklist temuan; jangan menandai selesai hanya karena build lulus.
2. Catat file/fungsi yang diubah, tes regresi, perintah dan hasil pemeriksaan, serta commit bila Git sudah tersedia.
3. Untuk kasus runtime, catat model perangkat, Android, provider SAF, ABI, versi/hash APK, langkah uji, dan hasil. Bila belum diuji, tulis **menunggu verifikasi runtime**.
4. Jika masih terhambat, tulis alasan dan langkah berikutnya agar pengembang berikutnya tidak mengulang investigasi.

## BUG-017 — P2: Progres 90–98% tidak mencerminkan pekerjaan sebenarnya

Ditambahkan **10 Oktober 2026** setelah pengguna melaporkan bar melambat mendekati 90%; jumlah temuan menjadi **17**. Status audit BUG-001–016 tetap seperti sebelumnya.

- **Pemicu/dampak:** MP3 dengan konversi/sampul atau video dengan dua track. Unduh dipetakan ke 0–90%, lalu heartbeat menambah 0,35% per 500 ms sampai 98%. Kecepatan/ETA hilang ketika heartbeat menulis ulang row. Library juga mengirim persen/ETA terakhir pada log pemrosesan, sehingga angka dapat tampak berjalan tanpa bukti transfer.
- **Perbaikan:** `DownloadProgress.kt` membaca template yt-dlp yang berisi byte, ukuran aktual/estimasi, kecepatan, ETA, ID format dan tahap postprocessor. Setiap track memakai 0–100% sendiri; konversi/sampul/pencarian lirik memakai indikator tak tentu. Penyalinan menghitung byte tersimpan. Kecepatan internet hanya tampil saat transfer. `dao.finish` dipanggil sesudah publikasi sidecar dan pencatatan hasilnya. Migrasi Room 3→4 menambah `phase` tanpa menghapus riwayat.
- **Titik kode:** `Engine.download`, `DownloadWorker.executeDownload`, `DownloadProgress.parse`, `JobCard` di `ui/components.kt`, `DownloadDatabase.MIGRATION_3_4`.
- **Verifikasi dan batas:** [PROGRESS_COVER_2026-10-10.md](PROGRESS_COVER_2026-10-10.md). Ukuran estimasi dapat berubah; persen track dapat terkoreksi turun. Ini bukan perkiraan persentase seluruh durasi pekerjaan.

## BUG-018 — P2: Jumlah Mix/radio berbeda dari antrean YouTube yang terlihat

Ditambahkan **10 Oktober 2026** dari laporan pengguna pada link `https://www.youtube.com/watch?v=3J1Opi7MVHw&list=RD3J1Opi7MVHw&start_radio=1`. Bersama BUG-019, jumlah temuan menjadi **19**; status temuan sebelumnya tetap berlaku.

- **Bukti kode sebelum perbaikan:** `Engine.inspectPlaylist` memakai `--playlist-end 500`. `DownloadViewModel.inspectPlaylist` mengubah input watch+list menjadi `/playlist?list=...`, sehingga konteks video awal/seed tidak dipertahankan. Extractor yt-dlp yang dipin, `YoutubeTabIE._extract_inline_playlist`, mengikuti respons `next` berulang setelah halaman awal. UI hanya menambahkan penjelasan Mix, belum membatasi pratinjau.
- **Dampak:** TubeFlow dapat menampilkan jauh lebih banyak entri daripada antrean awal yang terlihat di YouTube. Jumlah hasil diperoleh dari halaman lanjutan, bukan jumlah lagu yang sedang ditampilkan pengguna.
- **Batas fakta:** [YouTube Help](https://support.google.com/youtube/answer/9011078?hl=en) menjelaskan Mix sebagai daftar berkelanjutan yang dipersonalisasi. Tidak ada jaminan selalu 25 atau identik antar sesi/akun. Link contoh tidak berhasil dibuka oleh alat web sesi ini; jumlah 25 berasal dari laporan pengguna, bukan verifikasi independen.
- **Rancangan yang diterapkan:** Bedakan Mix `RD...` dari playlist statis. Pertahankan konteks seed watch+list; baca halaman awal yang dikembalikan YouTube tanpa mengambil continuation otomatis untuk pratinjau. Jika tersedia aksi memuat lagi, jalankan hanya saat pengguna memilihnya. Tampilkan bahwa snapshot dapat berbeda dari akun YouTube pengguna; jangan memotong semua playlist menjadi 25 untuk memaksakan kesamaan angka.
- [x] **Perbaikan dan verifikasi 10 Oktober:** `playlistInspectionUrl` mempertahankan seed Mix; flag bootstrap membatasi `RD...` ke panel awal. Fixture 25/8/50 entri tidak meminta continuation; non-radio tetap meneruskan pagination. Link contoh menghasilkan **25** entri di HP fisik, tanpa cookies akun pada engine. Label menjelaskan antrean awal dan personalisasi. [Rincian dan screenshot](MIX_DUPLICATES_2026-10-10.md).

## BUG-019 — P2: Identitas duplikat memakai URL mentah

- **Reproduksi yang sudah terbukti:** Pengujian kompresi menyimpan MP3 128 kbps dengan sampul dari `https://youtu.be/aqz-KE-bpKQ`, padahal riwayat sudah memiliki output identik dari `https://www.youtube.com/watch?v=aqz-KE-bpKQ`. File pembanding berakhiran ` (1).mp3`. Pengujian tersebut sengaja memakai bentuk link berbeda untuk membuat pembanding; kelolosannya bukan bukti deteksi duplikat yang benar.
- **Penyebab sebelum perbaikan:** `DownloadJob.matchesOutput` di `Models.kt` membandingkan `url == this.url`; `DownloadViewModel.submit` hanya melakukan `.trim()`. Perbaikan BUG-005 membandingkan seluruh opsi keluaran, tetapi belum menyamakan identitas sumber.
- **Rancangan yang diterapkan:** Gunakan ID video untuk identitas sumber, dari `watch?v=`, `youtu.be/`, `/shorts/`, `/live/` dan domain YouTube Music/mobile yang divalidasi. Abaikan parameter konteks seperti `list`, `start_radio`, `t`, `si` saat membandingkan sumber video. Pertahankan pembandingan opsi keluaran agar MP3 128/320 atau video/audio tetap boleh berbeda. Bandingkan juga URL lama yang sudah tersimpan sehingga migrasi identitas tidak meniadakan perlindungan riwayat.
- [x] **Perbaikan dan verifikasi 10 Oktober:** `videoId` membandingkan identitas dari semua bentuk URL yang didukung, termasuk riwayat lama; seluruh opsi tetap diperiksa. Regresi host mencakup identitas pada status antre/berjalan/dijeda/selesai, ID lain dan opsi lain. Dua alias baru (youtu.be + `si`, YouTube Music + `t`) untuk MP3 128 bersampul ditolak di HP dengan **File ini sudah pernah diunduh.** Riwayat/file tetap 3 dan antrean kosong. Retry gagal juga memeriksa hasil selesai milik job lain. [Rincian](MIX_DUPLICATES_2026-10-10.md).
- [ ] **Cakupan runtime lanjutan:** Uji duplikat pada job aktif/dijeda, retry gagal dengan sibling selesai, dan download ulang sesudah file benar-benar dihapus. Guard `outputStillExists` tetap dipakai; skenario tersebut belum dijalankan pada perangkat dalam batch ini.

## BUG-020 — P2: Mode default menampilkan nol meskipun folder berisi file lama

Ditambahkan 10 Oktober 2026; total temuan menjadi **20**. Detail akar masalah, lokasi kode, batasan dan hasil uji ada di [STORAGE_SIZE_2026-10-10.md](STORAGE_SIZE_2026-10-10.md).

- **Bukti:** Folder root TubeFlow berisi 53 file/309.199.528 byte. Mode default menghitung 0; pemilihan root SAF menghitung 294,9 MB. Default kode tidak menunjuk `Denny-Caknan`.
- **Perbaikan:** Pakai grant persisten yang cocok tepat dengan root folder default, pisahkan label cakupan penuh/terbatas, jangan menelan kegagalan query sebagai nol, dan beri opsi pilih ulang folder.
- **Validasi host:** Release 1.0.1 (2), 60 tes Kotlin/37 Python, lint 0 masalah, signature produksi dan audit APK/AAB lulus. APK 1.0.1 kemudian terpasang dengan hash/certificate cocok pada Realme Android 10.
- [x] **Verifikasi APK final pada root:** Mode default tetap `Downloads/TubeFlow`, menunjukkan 246,4 MB untuk 50 file/258.380.600 byte. Setelah pilih root, kembali ke default, Segarkan dan restart, angka tetap benar. Jumlah file/daftar ukuran identik sebelum-sesudah; tidak ada uninstall/clear data.
- [ ] **Cakupan runtime lanjutan:** Grant subfolder/sibling, izin dicabut, provider gagal, dan batas folder besar. Pembandingan root/subfolder/sibling sudah diuji host, tetapi bukan bukti seluruh provider runtime.

## BUG-021 — P3: Ringkasan "Sampul" tidak mengikuti format audio tanpa dukungan sampul

Ditambahkan **10 Oktober 2026** dari review kode ([REVIEW_2026-10-10.md](REVIEW_2026-10-10.md)); total temuan menjadi **26**.

- **Lokasi:** [DownloadScreen.kt](../app/src/main/java/id/local/tubeflow/ui/DownloadScreen.kt), default `embedThumb = true` (94), `applyPreset` (115–119), `buildOptions` (132), pemanggil `buildSummary` (433).
- **Bukti kode:** `buildOptions` memakai `embedThumb && mode == AUDIO && Selection.coverSupported(mode, audioFormat)`; pemanggil `buildSummary` hanya `embedThumb && mode == MediaMode.AUDIO`. Preset "FLAC lossless" (dan format WAV/OGG) mengesankan `audioFormat` tanpa mereset `embedThumb`; menu format manual meresetnya (350) sementara toggle disembunyikan (406).
- **Dampak:** pilih preset FLAC pada keadaan awal → kartu ringkasan menampilkan "· Sampul" padahal unduhan tidak menyematkan sampul. Hanya tampilan; keluaran mengikuti `buildOptions`.
- **Arah perbaikan:** teruskan `Selection.coverSupported(...)` ke pemanggilan `buildSummary`, atau jadikan satu sumber kebenaran bersama `buildOptions` dan toggle.
- [x] **Diperbaiki 10 Oktober 2026:** pemanggilan `buildSummary` menyertakan `Selection.coverSupported(mode, audioFormat)` — "Sampul" hanya tampil bila benar-benar disematkan, identik dengan `buildOptions`.
- [ ] **Kriteria selesai:** label ringkasan dan `embedThumb` hasil `buildOptions` selalu konsisten untuk semua preset/format; regresi host membandingkan keduanya.

## BUG-022 — P3: Tautan playlist dibagikan memunculkan error sesaat dan inspeksi ganda

- **Lokasi:** [TubeFlowApp.kt](../app/src/main/java/id/local/tubeflow/ui/TubeFlowApp.kt), `LaunchedEffect(incomingUrl)` (48–52); [DownloadScreen.kt](../app/src/main/java/id/local/tubeflow/ui/DownloadScreen.kt), `LaunchedEffect(trimmed)` (100–107).
- **Bukti kode:** `model.inspect(incomingUrl)` dipanggil tanpa membedakan playlist; `Engine.inspect` mewajibkan `validUrl`, sehingga `https://www.youtube.com/playlist?list=…` (tanpa `v=`) gagal dan `_error` menampilkan "Gunakan tautan video YouTube yang valid." Efek DownloadScreen baru berjalan setelah delay 700 ms dan memanggil `inspectPlaylist` yang membersihkan error — pengguna melihat snackbar salah selama kartu playlist dimuat. Untuk tautan video valid, inspeksi dijalankan dua kali; percobaan pertama dibatalkan pada 700 ms.
- **Dampak:** salah informasi sesaat pada bagikan playlist; pemborosan proses yt-dlp pada tautan video (inspeksi pertama dibatalkan di tengah jalan).
- **Arah perbaikan:** biarkan DownloadScreen satu-satunya penginspeksi (hapus panggilan `inspect` di TubeFlowApp), atau samakan rute playlist/video di TubeFlowApp sebelum memanggil.
- [x] **Diperbaiki 10 Oktober 2026:** panggilan `model.inspect(incomingUrl)` dihapus dari `TubeFlowApp` — efek `DownloadScreen` yang memilih rute video vs playlist setelah delay700 ms; tidak ada snackbar "tautan tidak valid" sesaat dan tautan video tak diperiksa dua kali.
- [ ] **Kriteria selesai:** bagikan tautan playlist murni tidak memunculkan snackbar error; tautan video terinspeksi tepat satu kali; metadata tetap muncul dari tab lain.

## BUG-023 — P3: Label "Selesai" memakai waktu antre, bukan waktu selesai

- **Lokasi:** [components.kt](../app/src/main/java/id/local/tubeflow/ui/components.kt) (174–175); `DownloadJob.createdAt` di [Models.kt](../app/src/main/java/id/local/tubeflow/core/Models.kt), diisi saat submit dan diperbarui saat revive.
- **Bukti kode:** kartu Riwayat menampilkan `"Selesai · <createdAt>"`; tidak ada kolom waktu selesai pada skema Room (v4).
- **Dampak:** job yang menunggu lama di antrean tampak selesai pada waktu pengantrean; riwayat tidak menyimpan kapan publikasi benar-benar terjadi.
- **Arah perbaikan:** tambah kolom `completedAt` (migrasi + snapshot schema), isi pada `finish`, tampilkan untuk status Selesai; pertahankan `createdAt` untuk antrean. Baris lama (`null`) turun ke `createdAt`.
- [x] **Perbaikan kode 10 Oktober 2026:** kolom `completedAt INTEGER NOT NULL DEFAULT 0` (Room **v5**, `MIGRATION_4_5` + snapshot `app/schemas/id.local.tubeflow.core.DownloadDatabase/5.json`); `finish` kini membawa `:now` (`System.currentTimeMillis`) dan mencatatnya bersama transisi COMPLETED; label kartu Riwayat memakai `completedAt` dengan fallback `createdAt` untuk baris hasil migrasi (nilai 0). Regresi host: `test_migration_4_5_adds_completed_at_like_schema_v5` (migrasi == schema v5, riwayat lama terbaca dengan `completedAt=0`), assert `completedAt` pada `test_completed_job_survives_late_cancel_failure` dan `test_phase_transition_clears_transfer_metrics_and_completion`, default 0 pada `test_new_option_columns_have_defaults`, dan `test_migration_3_4` kini membandingkan ke schema v4 (bukan skema terkini).
- [ ] **Kriteria selesai:** kartu Riwayat menampilkan waktu publikasi; migrasi mempertahankan seluruh data lama; regresi host untuk fallback `null`.

## BUG-024 — P3: Selector audio m4a/opus manual tanpa fallback dan wadah sumber tertukar

- **Lokasi:** [Models.kt](../app/src/main/java/id/local/tubeflow/core/Models.kt), `Selection.selector` (203–207).
- **Bukti kode:** `bitrate > 0` + format `m4a` → `bestaudio[ext=webm]`; `opus` → `bestaudio[ext=m4a]`; tanpa alternatif `/bestaudio` sedangkan lengan video memakai rantai fallback panjang. Pemetaan tampak tertukar: target m4a memilih sumber webm (opus) → transcode wajib, padahal sumber m4a bisa di-remux tanpa transcode; berlaku pula untuk sebaliknya.
- **Dampak:** (a) video/langsung tanpa track ext terpilih → yt-dlp `requested format not available` dan unduhan gagal total; (b) transcode tak perlu menurunkan kualitas dan menambah waktu konversi.
- **Arah perbaikan:** samakan wadah sumber dengan target (m4a → `[ext=m4a]`, opus → `[ext=webm]`) dan sertakan fallback `/bestaudio/best`; verifikasi dulu bila pemetaan sekarang ternyata disengaja.
- [x] **Koreksi + perbaikan 10 Oktober 2026:** source yt-dlp 2026.08.19 (`FFmpegExtractAudioPP.run` di bundel `assets/yt-dlp`) memilih `acodec = copy` (tanpa re-encode, `--audio-quality` DIABAIKAN) bila codec sumber sudah cocok dengan target — pemetaan lintas wadah justru DISENGAJ agar bitrate manual berlaku; klaim "wadah tertukar" di atas keliru. Yang benar-benar kurang adalah rantai fallback: kini `m4a` → `bestaudio[ext=webm]/bestaudio/best` dan `opus` → `bestaudio[ext=m4a]/bestaudio/best` (ext absen → tetap terunduh, bitrate jadi best-effort). Regresi host: `SelectionTest.audioManualBitrateKeepsCrossContainerWithFallback`.
- [ ] **Kriteria selesai:** format target tersedia → mengunduh tanpa transcode ulang bila memungkinkan; ext tak tersedia → fallback tetap berhasil; regresi host untuk lengan audio.

## BUG-025 — P3: Putus koneksi di tengah unduh menghapus parsial sehingga mulai dari nol

- **Lokasi:** [DownloadWorker.kt](../app/src/main/java/id/local/tubeflow/core/DownloadWorker.kt), `handleStop` (→ `interrupted` → `QUEUED`) dan blok `finally` (`state != PAUSED` → `directory.deleteRecursively()`).
- **Bukti kode:** pemotongan jaringan membuat WorkManager menghentikan worker (atau engine melempar IOException → FAILED); kedua jalur berakhir dengan direktori parsial dihapus, sementara WorkManager menjalankan ulang otomatis ketika jaringan pulih. File `.part`/`--continue` yt-dlp terbuang.
- **Dampak:** unduhan besar yang sudah 80% ulang dari nol — boros kuota dan waktu; bukan kehilangan data pengguna.
- **Arah perbaikan:** perlakukan "terputus" seperti `PAUSED` untuk retensi parsial dengan kepemilikan generasi BUG-002, atau jeda otomatis saat koneksi hilang; koordinasi dengan OPT-008. Cleanup saat cancel tetap wajib.
- [x] **Perbaikan kode 10 Oktober 2026:** blok `finally` worker menyimpan direktori parsial bila status `PAUSED` **atau `QUEUED`** — putus koneksi memicu `interrupted` → QUEUED → WorkManager menjalankan ulang otomatis ketika jaringan pulih dan `executeDownload` berjalan lagi dengan generasi sama, sehingga yt-dlp lanjut dari file `.part`. Gagal/batal tetap dibersihkan; recovery tetap membersihkan QUEUED yang pekerjaan WorkManager-nya sudah hilang (menjadi Gagal). Regresi host: `test_interruption_keeps_same_owner_for_partial_resume` (interrupted tanpa bump generasi → pemilik masih boleh menulis progres).
- [ ] **Kriteria selesai:** putus lalu pulih → unduhan lanjut dari `.part`; cancel tetap membersihkan parsial; tidak ada parsial milik worker lain yang terhapus.

## BUG-026 — P3: "Unduh lagi" selalu error selama file hasil masih ada

- **Lokasi:** [DownloadViewModel.kt](../app/src/main/java/id/local/tubeflow/core/DownloadViewModel.kt), `retry` (302) → `submit` tanpa `keepId`; cek duplikat (212); tombol [components.kt](../app/src/main/java/id/local/tubeflow/ui/components.kt) (241).
- **Bukti kode:** untuk job SELESAI, retry mengirim opsi sama; `matchesOutput` menemukan barisnya sendiri (`keepId` null → `it.id != keepId` selalu benar) dan `outputStillExists` true → `require(done == null)` melempar "File ini sudah pernah diunduh."
- **Dampak:** tombol Riwayat mengundang aksi yang pasti gagal; pengguna yang ingin unduh ulang (file rusak) harus menghapus file lebih dulu tanpa petunjuk. Guard duplikat BUG-019 memang disengaja — masalahnya pada UI.
- **Arah perbaikan:** tampilkan "Unduh lagi" hanya bila hasil sudah hilang, atau ubah aksi menjadi "Hapus hasil lama & unduh ulang" yang menghapus lalu mengantre.
- [x] **Perbaikan kode 10 Oktober 2026:** perilaku diubah menjadi **"ganti hasil lama"** sesuai salah satu opsi di atas. `retry` untuk COMPLETED kini: (1) hapus media + sidecar lama memakai `StorageUris` yang jujur — satu file gagal = seluruh aksi batal dan baris tidak tersentuh; (2) `requeueCompleted` (DAO baru) memakai ulang baris yang sama: outputUri/sidecarUris/lyricStatus/completedAt dibersihkan, generasi naik, status QUEUED — tanpa baris duplikat; (3) `enqueueWork`. Tombol Riwayat kini membuka dialog konfirmasi "Unduh lagi?" yang menjelaskan penggantian file lama (pola dialog yang sudah ada). Guard duplikat BUG-019 untuk submit pasif tidak berubah. Regresi host: `test_requeue_completed_reuses_row_without_duplicate` (baris dipakai ulang, tap ganda/ job berjalan ditolak SQL).
- [ ] **Kriteria selesai:** tidak ada jalur yang menawarkan aksi pasti gagal; unduh ulang sesudah file dihapus manual tetap lolos (BUG-019); pilihan perilaku tercatat di dokumen.

## BUG-027 — P2: Playlist Mix/RD tanpa seed ditolak YouTube ("This playlist type is unviewable")

- **Lokasi:** [Models.kt](../app/src/main/java/id/local/tubeflow/core/Models.kt), `Selection.playlistInspectionUrl` / `isMix` / `canonicalPlaylistUrl`.
- **Bukti perangkat (10 Okt 2026, Realme Android 10, TubeFlow 1.0.2):** pengguna menempelkan tautan Bagikan dari autoplay — `https://youtube.com/playlist?list=RDrEjSkBl47OY&playnext=1&si=mvfrZkxYG_CqldGI` — dan inspeksi gagal penuh: `WARNING: [youtube:tab] YouTube said: This playlist type is unviewable.` + `ERROR: [youtube:tab] RDrEjSkBl47OY: …`.
- **Bukti host (engine 2026.08.19):** ID `RD…` adalah Mix; endpoint `/playlist?list=RD…` (tanpa video seed) bersifat tidak stabil dan menolak dengan galat unviewable, sementara bentuk `watch?v=<seed>&list=RD…&start_radio=1` konsisten sukses — untuk link di atas Mix "SATRU 2" terbaca 541 entri lewat `--flat-playlist`. Pemulihan seed dari ID-nya sendiri valid karena YouTube membentuk Mix sebagai `RD`/`RDMM` + videoId (11 karakter).
- **Dampak:** bentuk tautan paling umum dari autoplay/mix (menu Bagikan YouTube) gagal total; pengguna mengira aplikasi rusak. Bukan kehilangan data.
- **Risiko pilihan awal (salah):** menormalisasi ke `playlist?list=RD…` kanonis sama saja dengan meniru endpoint yang ditolak — seed wajib dipulihkan.
- [x] **Perbaikan kode 10 Oktober 2026:** helper baru `mixSeed(id)` memulihkan seed dari ID Mix (`RD`+11 karakter, atau `RDMM`+11 karakter; lainnya → null). `playlistInspectionUrl` kini mengambil seed dari URL (watch/shorts/youtu.be + `list`, pola BUG-018) **atau** hasil pemulihan ID Mix, lalu memakai `watch?v=$seed&list=$id&start_radio=1` — URL yang terbukti diterima YouTube. ID yang tidak cocok pola tetap jatuh ke kanonis (perilaku lama untuk playlist biasa). Regresi host: `standaloneMixLinkRecoversSeedFromPlaylistId` (link persis laporan + bentuk RDMM); tes lama `mixInspectionPreservesSeedAcrossLinkForms` memastikan bentuk watch/shorts/youtu.be tidak berubah. Kotlin 67 tes, 0 gagal.
- [ ] **Kriteria selesai:** tautan persis laporan terbuka di HP — rilis 1.0.3 (versionCode 4) sudah `adb install -r` ke Realme dengan hash terverifikasi, tetapi sesi berakhir saat layar terkunci keyguard PIN; playlist biasa dan link campuran watch+list tidak terpengaruh (tertutup lewat tes).
