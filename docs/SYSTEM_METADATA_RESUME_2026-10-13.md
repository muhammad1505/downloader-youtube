# Identitas lagu, resume, dan kartu download — 13 Oktober 2026

## Analisis implementasi awal

Checkout sudah memuat pekerjaan batch 12 Oktober (Room v7, TitleSplit, thumbnail dan penyimpanan parsial). Perubahan ini mempertahankannya dan mengoreksi celah berikut sebelum menyatakan sistem sesuai.

| Permintaan | Akar penyebab yang ditemukan | Perbaikan |
| --- | --- | --- |
| 1. Auto Folder | `playlistFolder` masih mempromosikan channel Mix dan fragmen judul tanpa bukti; seluruh playlist diberi satu folder | Artis terbukti per item; job menyimpan mode Auto Folder. Metadata lengkap worker menentukan subfolder; artis belum pasti tetap root |
| 2. Nama konsisten | Metadata audio dipotong lagi pada dash nama tampilan; judul asli/versi tidak tersimpan terpisah | Snapshot Room untuk rawTitle, trackTitle, artist/evidence, creator, remixer dan versionTag; nama dan tag audio dari snapshot yang sama; `MetadataTags` mengatasi fallback uploader/track di postprocessor asli |
| 3. DJ/remix | Channel dengan token terakhir hilang dianggap artis; `creator` extractor dianggap artist | Creator/remixer terpisah dari artis; penanda versi tetap ada. Pencocokan channel tidak memakai substring atau membuang token nama |
| 4. Pencarian lirik | Jalur pencarian memecah display title secara posisional dan tidak mencoba judul-saja jika artis ada | Query terstruktur; variasi lookup judul/artis, judul saja dan judul utama tetap divalidasi dengan identitas/versi asli. Orientasi nama dapat dikonfirmasi oleh satu identitas provider yang cocok judul/artis/durasi |
| 5. Platform | Label/icon dihitung terpisah dari URL pada layar, YouTube Music disamakan dengan YouTube | Registry SourcePlatforms + platformId persisten pada job; fallback URL untuk data lama; satu properti model untuk antrean/riwayat/filter |
| 6. Resume | `.part` dipertahankan, tetapi belum ada jurnal identitas; direktori yang berisi file apa pun dianggap resumable; retry menghapus angka progres | Jurnal per transfer sumber/format/ukuran/fragmen, validator HTTP dan pemeriksaan prefix. Native HTTP/DASH yt-dlp meneruskan byte/fragmen; partialBytes/notice tersimpan, resume/revive mempertahankan posisi |
| 7. Thumbnail | coverPath baru ditulis setelah seluruh download selesai | thumbnailUrl tersimpan sejak submit/flat entry; cache JPEG internal bounded dimulai sebelum transfer; kartu memakai file lokal/URL dengan fallback ikon. Riwayat lama mencoba artwork tertanam dari URI media, kemudian URL thumbnail tersimpan |
| 8. Konsistensi | Worker hanya menginspeksi judul placeholder yang tak sesuai nilai submit; artis lokal job tidak diperbarui bersama DB | Semua attempt melewati satu pemetaan metadata, update atomik bersyarat generation, model dan kartu bersama; migrasi 7→8 mempertahankan data lama |
| 9. Regresi/laporan | Sebagian tes lama mengharuskan fallback channel/tebakan posisi | Ubah ekspektasi kontrak tersebut, tambah fixture metadata/lirik/SQL dan downloader HTTP/fragmen nyata |

## Bukti sumber DJ pengguna

Engine yt-dlp yang dipin membaca `https://www.youtube.com/watch?v=3J1Opi7MVHw`: judul asli `DJ JAUH KO PERGI | SA CERITAKAN PADA BINTANG BINTANG SLOW VIRAL TIKTOK FULL SONG MAMAN FVNDY 2026`, uploader/channel `Maman Fvndy Rmx`, durasi 248 detik. Field artist/artists/track/creator tidak tersedia. Ini bukan bukti artis asli. Identitas tersebut tetap menyimpan uploader sebagai creator; credit remix yang diperkuat teks dapat tampil sebagai remixer tanpa masuk folder artis asli. Hasil JSON lokal: `build/reports/system-2026-10-13/dj-source.json`.

## Titik kode dan kompatibilitas

- `core/TitleSplit.kt`, `SourceMetadata.kt`, `SourcePlatforms.kt`, `MetadataTags.kt`, `Format.kt`: identitas dan format; path Kotlin relatif `app/src/main/java/id/local/tubeflow/`.
- `core/Models.kt`: snapshot, metadata update, generation, platform/resume state dan migrasi Room 7→8; schema `app/schemas/id.local.tubeflow.core.DownloadDatabase/8.json`.
- `core/DownloadViewModel.kt`, `DownloadWorker.kt`, `Engine.kt`: submit/inspeksi/retry/publikasi memakai snapshot yang sama; destination default tetap `Download/TubeFlow`.
- `core/Lyrics.kt`: resolusi orientasi hanya dengan hasil provider unik, durasi selisih maksimum dua detik dan score ketat; lookup alternatif tidak menurunkan validasi versi/artist.
- `core/CoverCache.kt`, `ui/components.kt`, `QueueScreen.kt`, `HistoryScreen.kt`, `DownloadScreen.kt`: cache/tampilan/folder sesuai sumber data.
- `scripts/engine_bootstrap.py` dan asset identik: validasi resume sebelum downloader yang dipin dipanggil; zip engine resmi tidak diubah.

Data lama tetap terbaca; field baru berisi default aman. Judul/URI hasil lama tidak diganti dan file pengguna tidak dipindah. `artist` lama tanpa bukti baru tidak dipakai untuk menebak identitas pencarian; metadata direfresh saat attempt berikutnya. ID job/opsi duplikat tetap menjadi identitas tugas, bukan nama tampilan.

## Kontrak fallback resume

HTTP memakai Range downloader asli; signed URL diekstrak ulang setiap attempt. Jurnal mencocokkan item/extractor/format/codec/ukuran/protokol; HTTP parsial diperiksa validator ETag/Last-Modified yang tersedia dan prefix hingga 64 KiB. Sumber/format/ukuran/checkpoint/validator/prefix berubah atau Range tidak tersedia → file sementara milik job diulang aman dengan notice. Preflight gagal jaringan → parsial dipertahankan, tugas gagal dan dapat dicoba lagi. Fragmen menggunakan checkpoint `.ytdl` downloader; fragmen selesai tidak diminta ulang pada fixture DASH.

Ini tidak menjamin setiap extractor/CDN mendukung Range, validator, atau signed URL baru yang valid. Identitas/provider ambigu tetap memakai fallback konservatif. Metadata cocok tidak membuktikan timestamp lirik sinkron; live/remix tetap membutuhkan pemeriksaan audio nyata.

## Hasil pengujian

- Kotlin: **102 tes, 0 gagal/error/skipped** pada perubahan snapshot/identitas, lookup lirik, URL/folder, format dan regresi sebelumnya.
- Python: **61 tes lulus**, termasuk migrasi schema 7→8 yang mempertahankan kolom lama, ownership update snapshot/cover, dan downloader yt-dlp yang dipin (HTTP/DASH nyata terhadap server lokal).
- Resume host: koneksi terputus → Range; signed query berubah tetapi sumber sama; tanpa Range → restart aman; ETag/prefix/format/total Content-Range berubah → restart; jurnal hilang → restart; preflight 503 → parsial tetap; file lengkap valid → tidak GET ulang; checkpoint DASH → fragmen selesai tidak diunduh ulang.
- Pencarian lirik memakai server fixture: pencarian pertama gagal diikuti title-only, artis/judul kedua orientasi divalidasi provider, kandidat ambigu/durasi berbeda/versi berbeda ditolak. Ini bukan pengujian sinkronisasi timestamp dengan audio asli.
- Kandidat awal sempat mengeluarkan ResourceWarning pada teardown Python; rangkaian final 61 tes tidak mengeluarkannya. Lint debug/release final: **0 masalah**; signed APK/AAB build berhasil (8m1s), audit ketiga APK/AAB lulus. Deprecation internal Gradle dan warning JAR signing self-signed/timestamp tetap terpisah dari lint aplikasi.
- Baseline HP sebelum update: Realme RMX1821 Android 10/API 29 ARM64, antrean kosong, 25 entri riwayat, tujuan default Downloads/TubeFlow. Direktori tujuan kosong dan angka 0 B **sebelum** update; retensi file lama dan pemulihan embedded artwork tidak dapat dibuktikan dari baseline ini.

Artefak final **1.0.9/code 10**, sertifikat produksi sama dengan release sebelumnya. ARM64 SHA256 `e3c5b3f3587614f55f6d56a670d04a41e1f55a6325ddc02e3d1c9958a0cdf91f` (62.853.907 byte). Signature, zipalign 16 KB, manifest non-debuggable, audit native 255/255/257 ELF dan ZIP refleksi 13 kelas per APK lulus. AAB signature/cert, bundletool validate dan audit 1.024 ELF lulus. Engine resmi sesuai SHA pin dan bootstrap asset identik sumber. Distribusi lokal `app/build/outputs/distribution/1.0.9/`.

Instalasi `adb install -r`: Success. APK ditarik dari HP dan hash identik artefak final; UI setelah migrasi menunjukkan **25 entri selesai/25 audio** seperti baseline. Tidak menjalankan uninstall, clear data atau penghapusan file. Update kandidat koreksi tag mempertahankan hasil baru serta job PAUSED; hash APK yang ditarik cocok hash final di atas.

## Cakupan yang tetap memerlukan uji lanjutan

- NEGORO ANGIN dan PINGAL diuji sebagai pola/fixture; link sumber asli kedua lagu belum tersedia dalam sesi ini. Pola ambigu tanpa metadata atau hasil provider yang meyakinkan tetap disimpan tanpa menebak artis.
- Pencocokan lirik umum memakai toleransi durasi dan skor yang sudah ada; resolusi artis baru lebih ketat. Timestamp LRCLIB tidak dikoreksi otomatis terhadap intro, edit, cover atau remix; sinkronisasi auditori belum terbukti.
- Registry platform tidak menjamin setiap extractor berhasil mengunduh konten privat, login atau tautan kedaluwarsa. Ikon menggunakan representasi Material, bukan aset logo merek resmi.
- Host membuktikan HTTP tanpa Range/URL bertoken/sumber berubah/DASH; tidak membuktikan seluruh CDN, HLS/live, external downloader atau platform dengan format yang berubah setiap ekstraksi.
- Schema baru tidak mengubah nama file/URI hasil lama. Jika file sumber history sudah hilang dan URL/artwork lama tidak tersedia, kartu tetap memakai fallback.

## Celah yang ditemukan pada pengujian file pertama

APK kandidat awal 1.0.9 menghasilkan kartu/filename yang benar, tetapi ffprobe menemukan `artist=Maman Fvndy Rmx` meskipun snapshot tidak menetapkannya sebagai artis asli. Source `FFmpegMetadataPP._get_metadata_opts` pada yt-dlp yang dipin memakai fallback artist → artists → creator → uploader, dan title mendahulukan track. `--replace-in-metadata artist` tidak bekerja bila field sumber belum ada.

Perbaikan lanjutan: `MetadataTags.kt` mengirim override `meta_title`, `meta_artist` (termasuk kosong secara eksplisit), `meta_creator` dan `meta_remixer` melalui parser resmi. Override mempertahankan provenance tanpa mempromosikan uploader. Tes memakai parser CLI dan generator tag FFmpeg dari engine yang dipin; judul/artist yang lama, nilai kosong, `%` dan `:` diverifikasi. Kandidat awal berhash `ceab6fa686eff2b4577d0192f7253ea187192f1241efa6978cd708fde96c03ad` diarsipkan lokal di `build/reports/system-2026-10-13/initial-artifacts/`; distribusi 1.0.9 kini memakai APK koreksi tag dengan hash final pada bagian pengujian.

## Pengujian HP kandidat sebelum koreksi tag

| Skenario | Hasil yang teramati |
| --- | --- |
| Update Room 7→8 | 25 riwayat audio dipertahankan; hash APK terpasang cocok kandidat |
| Link pengguna sebagai Mix | 25 video panel awal, satu item dipilih untuk tes (bukan seluruh playlist) |
| Thumbnail/platform sebelum selesai | Artwork asli terlihat saat PREPARING; label/ikon YouTube sesuai sumber |
| Auto Folder artis tidak pasti | Audio tersimpan langsung dalam root TubeFlow; tidak ada subfolder Maman/channel |
| Antrean→riwayat | Nama normalisasi, versi DJ/slow, remixer, cover, platform dan opsi 128 kbps tetap; 26 riwayat selesai |
| Artefak audio | MP3 128000 bps, 248 detik; cover MJPEG 800×450, 57.082 byte. Tag artist fallback salah ditemukan dan dikoreksi seperti bagian sebelumnya |
| Duplikat URL alias | Submit opsi sama via youtu.be ditolak “File ini sudah pernah diunduh.”; antrean kosong, file tetap satu |
| Gagal sebenarnya | Video MP4 1080p gagal HTTP 403 setelah parsial 9,6 MB; partialBytes ditampilkan, bukan sukses palsu |
| Aplikasi ditutup/dibuka | FAILED + 9,6 MB tetap terlihat setelah force-stop; retry→pause→force-stop→reopen tetap PAUSED beserta nama/cover/platform |

HTTP 403 teratasi pada attempt baru setelah sumber diekstrak ulang. Kandidat final menampilkan “Identitas parsial cocok” dan progres video 42%; job kemudian selesai menjadi MP4 H.264 1920×1080 + AAC, durasi 247,85 detik. Decode penuh dengan `ffmpeg -xerror` berhasil tanpa error. Tag artist video kosong, bukan uploader. Status PAUSED dan parsial tetap tersedia melewati update APK; kondisi FAILED/Pause tersebut diuji pada kandidat awal dengan model/resume yang sama. Bukti UI/log/ffprobe kandidat ada pada `build/reports/system-2026-10-13/` dan `build/reports/system-*.xml/png`. Hasil host resume berhasil tetap dibedakan dari hasil aplikasi ini.

## Verifikasi APK final sesudah koreksi tag

- APK terpasang: `e3c5b3f3587614f55f6d56a670d04a41e1f55a6325ddc02e3d1c9958a0cdf91f`, sama dengan ARM64 distribusi. 102 tes Kotlin, 61 tes Python, lint debug/release 0 masalah. Seluruh audit APK/AAB diulang terhadap byte final dan lulus.
- Job video yang gagal kemudian dijeda tetap tersedia setelah update. Resume memvalidasi parsial, menampilkan 42% dan selesai; decode penuh MP4 hasilnya tanpa error. Perubahan URL server tidak menghapus parsial yang cocok.
- File audio **yang dibuat sesi pengujian ini** diulang lewat dialog penggantian hasil untuk memvalidasi tag final; row job dipakai ulang. HTTP 403 pada attempt pertama diselesaikan lewat Coba lagi/ekstraksi baru. ffprobe MP3 final menunjukkan 128000 bps, title normalisasi, `creator=Maman Fvndy Rmx`, `remixer=Maman Fvndy Rmx`, **tanpa artist uploader**; decode audio penuh tanpa error.
- Default tetap root Downloads/TubeFlow: dua file akhir (MP3 + MP4), **88.733.655 byte**, tanpa subfolder. Pengaturan menampilkan **84,6 MB**, sesuai ukuran binary tersebut. Riwayat lama tidak dihapus; file pengguna lama sudah tidak tersedia pada baseline.
- Cover audio MJPEG 800×450, sekitar 57 KB. MP4 mempertahankan title/artist override; custom creator/remixer tags tidak muncul pada ffprobe container MP4 ini. Peran creator/remixer tetap tersimpan di Room dan filename, tanpa mengarang dukungan tag container.

Sinkronisasi timestamp lirik, Auto Folder artis asli pada link NEGORO/PINGAL, platform selain YouTube, signed thumbnail yang benar-benar kedaluwarsa, storage SAF dan perangkat 16 KB belum diuji live dalam batch ini. Fixture dan audit masing-masing bukan pengganti pengujian perangkat tersebut. Tidak memakai emulator.

Verifikasi akhir setelah decode: submit ulang MP3 128 kbps via youtu.be pada APK final kembali ditolak sebagai duplikat; file tetap dua. Force-stop/reopen akhir mempertahankan **27 selesai (26 audio + 1 video)**, kedua artwork dan platform, antrean kosong; logcat PID aplikasi tidak memuat FATAL EXCEPTION. 25 riwayat lama tetap ada, tambahan hanya dua hasil pengujian. Manifest/checksum distribusi telah diperbarui sesuai cakupan ini.
