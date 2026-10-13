# Upgrade Smart Media — implementasi dan verifikasi 1.1.0

## Fase 0: arsitektur terverifikasi

Baseline signed 1.0.12/code13, Kotlin/Compose Material3, AGP9.1.1/Gradle9.8.1/compile-target37/min29/JDK17. Room v8 pada downloads.db berisi downloads; tidak ada DI framework/backend/player internal. Android intent membuka audio/video pada pemutar pengguna (Poweramp teruji audio). WorkManager menjalankan engine yt-dlp2026.08.19 + FFmpeg/Python lokal. Tidak ada proses build aktif saat audit.

Sumber metadata dinormalisasi oleh SourceMetadata/TitleSplit dan snapshot DownloadJob; platform tidak disamakan dengan creator. Auto Folder memakai bukti artist. MetadataTags/FFmpeg memberi tag dan cover saat download; artwork cache JPEG maksimum800px. LRCLIB memiliki gerbang versi/durasi±2s, fallback terbatas dan status terpisah; belum cache hasil/koreksi waktu. SAF/MediaStore menghasilkan URI; scoped storage tanpa izin akses semua file. Pada tes sebelum upgrade pengguna menghapus riwayat saat enam file masih ada; pada checkpoint upgrade pengguna mengonfirmasi semua file lama sudah dihapus, sehingga baseline folder/riwayat0. Jumlah aktivitas seumur hidup tidak bisa direkonstruksi.

## Modul reuse dan kekurangan

Reuse: Room/migrasi, status/generation worker, URI storage, metadata, penamaan, cover, Compose dan intent playback. Kekurangan: katalog independen dari riwayat, snapshot ukuran/ketersediaan, operasi rename yang memperbarui indeks, pengaturan proses otomatis, cache LRCLIB, dashboard agregat/event minimal, koreksi LRC asli/rollback. Engine menerapkan meta_* tanpa membedakan audio/video: harus dibatasi audio. Tidak menambah engine pemutar kedua; playback/seek/focus/background/notifikasi diserahkan pada pemutar yang dipilih, error intent ditampilkan. Player internal bukan klaim upgrade ini.

## Urutan dan risiko

1. SmartMedia classification/naming, katalog pada Room yang sama, migrasi9 tanpa mengubah baris download lama; event tercatat hanya sejak upgrade.
2. Scan terbatas folder TubeFlow/SAF berizin dan impor file pilihan; metadata/cache/indexing IO berurutan, file tidak disalin. File tidak terbaca diberi unavailable/inaccessible, bukan ukuran0 atau metadata gagal.
3. SmartAudio switches snapshot pada job, tag audio-only, cache LRCLIB; koreksi waktu per lagu dari baseline asli, backup/write verification/rollback, tanpa request atau unduh ulang audio.
4. Library Musik/Video/Semua memakai satu katalog, cari/sort/folder/artist/album, detail/playback/rename/delete terkonfirmasi.
5. Dashboard aktual, event/history parsial, storage/health/recent; tidak men-trigger pencarian/tag massal.
6. Tes migrasi/regresi, lint/build/signing, update HP Android10 dan validasi hash/persistensi/audio/video/dashboard/lirik.

Risiko: provider SAF dapat mengubah URI setelah rename; konflik nama dan sidecar harus ditangani bersama. Perubahan izin bukan file hilang pasti. Format tagging dibatasi kemampuan engine/container yang sudah tersedia; tidak transcoding otomatis pada library. Pemutar/codec bergantung perangkat. Media tambahan hanya dipilih melalui picker, tidak meminta MANAGE_EXTERNAL_STORAGE. Statistik sebelum pencatatan event tidak dipalsukan. Cache/baseline diberi batas; operasi berat tidak dijalankan di main thread. Perkiraan delay awal berubah selama tes; total koreksi−2s akhirnya dikonfirmasi sinkron oleh pengguna. Kalibrasi tetap per rekaman.

## Status checkpoint

Implementasi, build produksi, tes host dan pemasangan APK final selesai. Pengujian perangkat dilakukan bertahap; batas dan pemeriksaan auditori yang masih menunggu disebutkan di bawah.

## Implementasi dan akar penyebab

| Area | Akar penyebab | Perubahan |
| --- | --- | --- |
| Katalog | Daftar media dahulu hanya memakai baris download; menghapus riwayat menghilangkan daftar walaupun file masih ada | `media_items` pada database yang sama, satu kunci URI kanonis, URI akses SAF tersendiri; tidak menyalin audio/video |
| Smart Audio | Penulisan metadata juga berjalan pada video dan opsi otomatis belum terpisah | Snapshot `autoRename/autoTag/autoLibrary` pada job; tag khusus audio, default artwork/lirik dapat diubah per download; video selalu masuk katalog |
| Tag asli dan tahun | Default metadata yt-dlp memakai upload_date untuk tahun, uploader untuk artis, categories untuk genre dan series untuk album; override artis kosong dapat menghapus tag asli | Probe format/stream audio sebelum tagging; gunakan field musik eksplisit atau tag native valid, tanpa tanggal unggah/URL/deskripsi. Setelah download, tag file membantu identitas, Auto Folder dan query lirik |
| Pemrosesan gagal | Kegagalan postprocessor metadata/artwork dapat menggagalkan hasil download yang sudah lengkap | Pembungkus terbatas postprocessor FFmpeg; hanya meneruskan hasil jika berkas asli tetap utuh berdasarkan ukuran/mtime. Ekstraksi/merge/transcoding/download tetap gagal normal; `processingNotice` dan event mencatat masalah |
| Gambar | Preview dibatasi per gambar, belum total dan dapat diminta ulang | Reuse preview valid; cache privat cover32MiB, cover library24MiB; JPEG800px untuk cover, frame video320×180; `CacheBudget` bersama, tidak menyentuh media/backup lirik |
| LRCLIB | Respons identik diambil berulang | Cache respons privat10MiB/200entry, positif24jam/4041jam; invalid/offline/429/503 tidak dijadikan cache negatif; aksi pencarian ulang membersihkan cache |
| Delay lirik | Kecocokan judul/durasi bukan bukti keselarasan vokal; rekaman LRCLIB dapat punya offset berbeda | Koreksi per LRC, langkah0,5detik, batas±30detik, translate dari baseline asli, preview, reset asli, AtomicFile jurnal, write/read-back dan rollback; tidak mengubah audio/rekaman lain |
| Rename | Perubahan nama audio saja membuat nama LRC tidak cocok dan konflik berisiko menimpa file | Validasi UTF-8/ekstensi/nama saudara; pasangan MediaStore media–LRC memakai rollback dan jurnal pemulihan; URI tetap sama dan indeks diperbarui |
| Dashboard | Riwayat bukan katalog fisik, dan sejarah yang dihapus tidak tersedia | Agregat Room atas file yang terakhir terbukti dapat diakses; ukuran yang tidak diketahui tetap nullable. Event terminal mulai upgrade, retry berupa aktivitas baru, tidak dihitung sebagai file baru |
| Performa | Pengindeksan sinkron dapat menahan akhir worker download ketika pemindaian aktif | `MediaIndexWorker` persisten terpisah; operasi IO berurutan. Refresh membuka maksimal100URI terindeks; scan folder hanya eksplisit, maksimal4000file/depth8, daftar bertahap100 |

### Definisi data dan statistik

`AVAILABLE` berarti provider berhasil queried dan FD bisa dibuka pada pemeriksaan terakhir. `ACCESS_DENIED` berarti izin ditolak; `UNAVAILABLE` berarti file saat ini tidak dapat diakses, bukan bukti penghapusan permanen. Pemindaian manual memeriksa katalog lebih lengkap; statistik dapat parsial jika grant/provider/batas scan menghalangi akses. Ukuran koleksi hanya media audio/video tersedia, tidak termasuk LRC, gambar cache, file pending atau seluruh isi perangkat. Kapasitas/free internal memakai `StatFs` volume aplikasi.

`READY` untuk audio berarti judul dan artis tersedia; album, genre, tahun dan track tetap opsional. `PARTIAL` berarti identitas belum lengkap. `UNCHECKED` dipisahkan dari `FAILED`; tidak ada statistik yang memicu tagging/download gambar/query LRCLIB. Lirik `tersimpan` membuktikan file LRC diterbitkan, tidak membuktikan sinkronisasi suara. Musik dan video memakai satu katalog dan filter berbeda; file hilang tetap tampak dengan statusnya. Grafik/periode7/30/90hari menghitung transisi aktual setelah pencatatan mulai. “Tugas masih tercatat” adalah jumlah row download, bukan klaim aktivitas seumur hidup.

### Migrasi dan akses

Room8→9 menambah snapshot opsi/`processingNotice` pada download dengan default aman, serta dua tabel indeks dan event. Tidak ada reset/destructive migration/foreign-key cascade dari penghapusan history ke katalog. `uri` merupakan kunci identitas kanonis, `accessUri` merupakan URI untuk menggunakan grant sebenarnya; konversi URI tidak dianggap memberikan izin baru. Alias MediaStore/SAF yang berhasil dipetakan tidak menggandakan katalog. Grant picker dipersist bila provider mengizinkan; penolakan/perubahan grant ditampilkan, tidak meminta izin semua file. Rujukan: [Android shared media](https://developer.android.com/training/data-storage/shared/media?hl=en) dan [SAF documents](https://developer.android.com/training/data-storage/shared/documents-files).

### Cakupan fitur

Diterapkan: Semua/Musik/Video/Riwayat, cari, sortir nama/tanggal/ukuran/durasi, folder/artis/album nyata, detail metadata/format/resolusi/ukuran/artwork, impor dokumen tanpa copy, playback melalui intent, rename, konfirmasi delete, koreksi LRC, dashboard koleksi/storage/aktivitas/health, pengaturan Smart Media dan cache. Riwayat lama dan antrean digunakan ulang.

Batas yang disengaja: player internal/Media3 tidak ditambahkan. Seek, audio focus, background, notifikasi dan codec ditangani pemutar pengguna; intent error ditampilkan. Rename pasangan SAF ditolak bila provider/grant tidak memungkinkan pemeriksaan konflik/pemulihan aman; pasangan MediaStore didukung. Tagging otomatis berlaku pada download baru, bukan penulisan tag massal pada file impor. Data hilang sebelum upgrade tidak direkonstruksi. Offset konstan tidak memperbaiki drift/perbedaan aransemen; jika koreksi tidak cocok perlu LRC untuk rekaman yang benar. UTF-8 invalid, LRC terlalu besar, perubahan eksternal atau backup penuh menyebabkan koreksi dibatalkan tanpa menimpa file.

### File upgrade

Baru: `core/SmartMedia.kt`, `MediaRepository.kt`, `MediaIndexWorker.kt`, `MediaLibraryViewModel.kt`, `LrcTiming.kt`, `LyricEditor.kt`, `LyricsResponseCache.kt`, `CacheBudget.kt`; `ui/MediaLibraryScreen.kt`, `MediaDashboardScreen.kt`; schema9; lima kelas tes Kotlin dan dua modul tes Python; laporan ini. Diubah: `MainActivity`, `Models`, `SettingsStore`, `Engine`, `DownloadWorker`, `DownloadViewModel`, `Lyrics`, `DownloadScreen`, `SettingsScreen`, `TubeFlowApp`, `components`, Gradle versi, bootstrap sumber/aset. Tidak ada file source yang dihapus dan `AGENTS.md` tidak diubah. Perubahan sebelum upgrade disimpan di checkpoint patch dan dipertahankan.

### Dependensi dan keamanan

Tidak menambah dependency, model AI, server, player engine, atau izin storage luas. Memakai Room, WorkManager, Compose, Android framework, yt-dlp dan FFmpeg yang sudah ada; lisensi dan notice pihak ketiga dipertahankan. ZIP yt-dlp yang diverifikasi tetap sama; pembungkus bootstrap hanya menyediakan opsi tambahan proyek. Event tidak menyimpan URL, client ID, token atau kredensial. Signing memakai keystore produksi lama; informasi privat tidak masuk dokumen/repository.

## Verifikasi host dan baseline

Kandidat signed1.1.0 nonminified berhasil dibangun dan dipasang update tanpa reset untuk pengujian awal; bukan artefak distribusi final. 136 tes Kotlin dan76 tes Python lulus setelah perubahan final metadata. Build produksi teroptimasi kemudian lulus; hasil final ada pada bagian build produksi. Host Python76tes lulus, termasuk migrasi aktual/DAO SQLite, event retry, resume Range/fragmen, audit native/ZIP, metadata sumber, kompresi cover dan fault injection postprocessor opsional. Log `Got error` pada fixture fragmen adalah gangguan yang sengaja disuntikkan dalam tes, bukan kegagalan suite.

HP RealmeRMX1821/API29 tetap tersedia, tanpa emulator. Pengguna mengonfirmasi berkas lama dihapus sebelum upgrade; baseline awal folder/riwayat0. Download pembanding pada1.0.12 sempat gagal karena respons SoundCloud terputus/client ID extraction ditolak; retry berhasil dan menghasilkan satu row COMPLETED serta MP3+LRC. MP3 4.083.718byte,252,499592s, album `For All`, title `Ya Sudahlah`, artist tidak diisi secara tebakan. SHA256 `b3185bb2196965296da6ffe4bf8550e5d3492af4ce30219bb56b8d54cfae6dec`. LRC2961byte/62timestamp, SHA256 `c509d5e8bf7c905c4e9641a8d76ff7e4020bdf033cba180ad2e68d21d42ce464`. Kedua berkas disimpan privat sebagai pembanding migrasi/koreksi; tidak disajikan sebagai download sukses kandidat upgrade.

Hasil release, instalasi dan pemeriksaan tiap fitur ditambahkan setelah benar-benar dilakukan.

### Checkpoint perangkat kandidat (13 Oktober)

Migrasi Room8→9 dengan satu riwayat selesai berhasil; audio/LRC hash identik sebelum/sesudah update. Katalog otomatis menampilkan SoundCloud/artwork/album yang tersedia, artis tetap belum diketahui tanpa bukti. Scan ulang menghasilkan1media tanpa menggandakan entri. Koreksi LRC−1000ms mengubah seluruh62timestamp tepat1detik; simpan ulang tidak menambah offset, reset menghasilkan hash asli, MP3 tidak berubah. Playback via Poweramp dan tampilan synced lyrics dibuka; pada checkpoint ini auditori masih menunggu; hasil final−2s dicatat di bawah.

Kandidat ini dipakai untuk tes migrasi dan operasi katalog sementara R8 membangun release final. Hasil kandidat tidak menggantikan verifikasi finaloptimized APK.

## Matriks verifikasi dan batas bukti

| Kelompok | Bukti otomatis | Bukti perangkat / batas |
| --- | --- | --- |
| Smart Media | MIME/ekstensi, nama audio/video, unknown artist, versi, UTF-8/ext manual, konflik/rollback rename | Migrasi nonempty/scan tanpa duplikat lulus kandidat; operasi final release diperiksa tersendiri |
| Smart Audio | Tag native/source, kegagalan postprocessor/probe, hash stream Opus, cache TTL/offline/404/429/503, LRCLIB versi/durasi | Koreksi LRC/read-back/reset/hash audio lulus kandidat; total−2s dikonfirmasi sinkron pengguna pada APK final |
| Migrasi/dashboard | SQL migrasi8→9 dari schema nyata, semua kolom/default/index; ukuran NULL, tersedia/hilang, event retry/history clear, filter dan escape wildcard | Room9 dibuka dariRoom8 dengan riwayat1 dan file utuh; statistik historis lengkap sebelum upgrade tidak tersedia |
| Resume/regresi | HTTP Range sumber sama, berubah, tidak mendukung, fragmen/checkpoint, owner generation/cancel, cleanup/duplikat | Suite host bukan bukti gangguan jaringan semua platform pada HP; runtime final audio/video diuji dan hasilnya dicatat |
| Library/cache | Katalog URI unik; grant akses tersendiri; cache entry/byte LRU; rename journal | Player eksternal digunakan ulang; SAF provider berbeda dan pencabutan izin belum diuji menyeluruh |

Belum diukur: benchmark ribuan file, FPS/pemakaian daya, seluruh codec/platform/provider, pemutusan listrik pada setiap fase. Tidak ada klaim kompatibilitas semua perangkat hanya dari satu HP. Pemindaian folder dibatasi dan manual; file library di luar grant tidak diakses secara paksa.

### Cara uji Android10 berikutnya

1. Update APK ARM64 dengan `adb install -r`, jangan uninstall/clear-data. Simpan hash beberapa audio/LRC dan jumlah riwayat sebelum/sesudah untuk migrasi.
2. Unduh sumber audio dan video yang diizinkan; cek penamaan, platform, cover, opsi otomatis serta keterbacaan di pemutar. Audio tanpa artis pasti menggunakan judul saja.
3. Di Koleksi: scan, impor file yang sama, cari/sort/filter, detail, rename yang juga memperbarui LRC; batalkan dialog hapus lalu gunakan hanya fixture milik penguji untuk tes hapus permanen.
4. Koreksi lirik sesuai selisih yang benar-benar didengar jika sorotan terlambat konstan; bandingkan timestamp, reset, buka ulang Poweramp dan dengarkan awal/tengah/akhir. Offset tidak memperbaiki drift.
5. Bandingkan dashboard dengan ukuran file media aktual, bukan seluruh folder/riwayat; LRC tidak dihitung sebagai lagu. Force-stop/reopen ketika antrean kosong lalu periksa katalog/offset/grant.
6. Untuk gangguan jaringan, jeda/lanjut/retry sumber yang diizinkan; periksa retained partial/notice fallback dan file akhir valid. Jangan menilai dukungan Range hanya dari platform.


### Temuan runtime Android10 saat rename

Kandidat memakai URI kanonis Files untuk operasi LRC, sehingga MediaProvider API29 menolak movement sebelum audio diubah. Rollback bekerja; kedua file tetap utuh. Perbaikan memakai URI akses LRC asli pada operasi/jurnal, sementara kunci katalog tetap kanonis. Batas koleksi API29 juga terlihat pada [source resmi MediaProvider Android10](https://android.googlesource.com/platform/packages/providers/MediaProvider/+/android-10.0.0_r11/src/com/android/providers/media/MediaProvider.java). Input nama file menonaktifkan auto-correction agar keyboard tidak menyisipkan spasi pada ekstensi. Perbaikan sudah masuk build final dan rename pasangan berhasil diuji ulang; hasil kandidat sebelum perubahan tidak dianggap final.


### Katalog dan dashboard kandidat

Impor ulang MP3 melalui picker berhasil (1file/0gagal) dan katalog tetap satu entri. Force-stop/reopen mempertahankan item, lokasi, SoundCloud/cover dan offset−1s. Dashboard tersedia1audio/0video,3,9MB/0B,0artis/1album,1metadata parsial/1artwork/1LRC; riwayat1completed tetapi event completion sejakupgrade0, sesuai asal download sebelum upgrade. Delete dialog pada lagu asli dibatalkan.

Dua fixture milik penguji berada di `Download/TubeFlow-QA`, terpisah dari folder pengguna. Impor2file/0gagal membaca artist/album asli fixture audio; video320×180/2s/21031byte menjadiVIDEO dengan frame biru lokal, tidak diberi field tag audio. Fixture adalah berkas fisik pengujian, bukan data dummy pada fitur/statistik.


### Temuan entrypoint download

Regresi perangkat pada kandidat optimized menemukan `TypeError` karena flag resume dihapus dengan dua argumen pada `list.remove`. Belum ada transfer/file video publik saat gagal. Bootstrap diperbaiki tanpa mengubah ZIP engine; tes integration entrypoint baru menjalankan pinned ZIP dengan flag resume, resume+optionalAudio, serta kombinasi seluruh ekstensi dan memeriksa cleanup jurnal proses. Build sebelum koreksi ini tidak merupakan release final. Fixture helper saja tidak cukup membuktikan alur CLI aplikasi.

Delete permanen melalui dialog aplikasi berhasil pada fixture VIDEO dan AUDIO milik penguji; hash lagu pengguna tidak berubah. Katalog mencatat file test sebagai unavailable, ukuran/jumlah tersedia mengecualikannya. Dua entri QA yang sudah dihapus dapat tetap terlihat sebagai bukti penanganan file tidak tersedia; bukan media pengguna yang hilang.


Pengaturan Tag metadata audio dinonaktifkan lewat Switch, tetap nonaktif setelah force-stop/reopen, lalu dikembalikan aktif. Pencarian `Ya` menghasilkan hanya lagu terkait; opsi sortir Nama/Ukuran/Durasi tersedia dan Ukuran dapat dipilih. Folder bawaan tetap Downloads/TubeFlow dan pengaturan menghitung3,9MB.


## Build produksi dan audit paket

Build final `scripts/build_release.sh` dengan JDK17, mirror eksplisit, Kotlin in-process/Xmx2g/max-workers2 berhasil (3m37s incremental,99task). Satu proses sebelumnya terhenti dengan exit143 saat packaging AAB; dilanjutkan menggunakan hasil kompilasi/optimasi yang sudah selesai. Build terakhir memuat bootstrap yang diperbaiki; verifikasi source/aset identik dan checksum ZIP engine lulus.

136JUnit/76Python lulus; Lint debug/release0issue. Ketiga APK ABI lulus apksigner dengan sertifikat produksi lama, zipalign16KB, audit ELF/native dan reflectiveZIP; AAB lulus jarsigner/certificate/native dan bundletool validate. Ketiga APK nondebuggable1.1.0/code14. Tidak menambah dependensi; lisensi/notice bundle lama dipertahankan.

ARM64 final63.008.739byte, dibanding1.0.12 sebesar62.859.063byte: bertambah149.676byte (sekitar0,24%). SHA256 `06da7742a451ad27ae8dd88b15c31fad0b047e2bffcead4de1a6f9482e45eda6`. APK final dipasang update-r di RealmeRMX1821/API29; hash base.apk perangkat cocok. Distribusi3APK+AAB/publiccertificate/release.json/SHA256SUMS tersedia di `app/build/outputs/distribution/1.1.0/`.

Rename pasangan MediaStore audio/LRC setelah perbaikan Android10 lulus pada APK final, lalu dikembalikan ke nama asli. Hash MP3 tetap b3185bb2… dan LRC terkoreksi fd607241…; editor tetap menunjukkan offset−1s setelah rename. Pengguna melaporkan sorotan masih terlambat; Poweramp dihentikan/dibuka ulang tanpa clear-data, LRC dibuka ulang untuk mengecek cache dan sisa offset. BUG-033 tidak dinyatakan selesai berdasarkan struktur timestamp saja.


### Verifikasi final video dan koreksi tambahan

Retry YouTube Lamunan pada APK final berhasil memublikasikan `Lamunan _ Live - Sasya Arkhisna.mp4`, 28.569.594 byte. Probe: H.264 1280×720 + AAC, durasi259,413333s; decode penuh seluruh audio/video menggunakan FFmpeg selesai exit0 tanpa error. MediaProvider mengubah separator `|` menjadi `_` pada nama fisik; versi Live dan artis berbukti tetap dipertahankan, channel tidak ditambahkan. Ini membuktikan file video hasil download, belum menggantikan tes playback Android.

Setelah Poweramp dimuat ulang dengan koreksi−1000ms, pengguna melaporkan masih terlambat tetap sekitar0,5s. Koreksi kemudian diubah menjadi total−1500ms dari baseline asli: seluruh62timestamp tepat bergeser−1500ms, SHA256 LRC `e1425351e03e972e200915f26971c618837fafd9cfe24d1530366ade82fcf71b`. Audio tidak diubah. Poweramp dibuka ulang dengan LRC baru; pada checkpoint ini auditori masih menunggu; total−2s kemudian dikonfirmasi sinkron (lihat hasil final berikut).


### Konfirmasi final sinkronisasi pengguna

Pengguna memajukan tambahan0,5s melalui fitur koreksi, menjadi total−2s, dan menyatakan “sekarang sudah fix sync”. Pemeriksaan file aktual memastikan seluruh62timestamp−2000ms terhadap baseline asli; SHA256 LRC `82c16cf6a6e3d45e8637fa4203829146816720b321e3a2118b0b689a5b5a0a4f`. Koreksi rekaman Ya Sudahlah ini terverifikasi secara struktural dan dikonfirmasi auditori pengguna; tidak diterapkan global pada sumber/lagu lain.


### Download audio final dan dashboard aktual

MP3 Lamunan pada APK final selesai dengan opsi128kbps+lirik+sampul: 4.199.522byte,259,392s, stream MP3 nominal128000bps, JPEG800×450; title `Lamunan | Live`, artist `Sasya Arkhisna`, date2024. Source inspeksi yang tersimpan memang mempunyai `release_year=2024`/`release_date=20240210`; tahun bukan hasil menebak dari upload_date. SHA256 audio `f784d0bdc337be8b3e93147b0e83fe7ab1dbfe8e7992923493a3ec009ae4c7aa`. Decode penuh exit0, LRC1456byte berdampingan dengan stem file audio yang sama. Audio/video dengan judul sama diterima sebagai jenis/keluaran berbeda. Antrean kembali kosong.

Dashboard final: tersedia2audio/1video; audio8.283.240byte (7,9MiB), video28.569.594byte (27,2MiB);1artis/1album. Dua fixture yang sudah dihapus berstatus tidak tersedia dan dikecualikan dari jumlah/ukuran tersedia. Metadata lengkap1/parsial1, artwork2, pending indeks0. Row history3completed/0failed/0cancelled, sedangkan event sejak upgrade2completed/1failed/0cancelled dan rasio66%: video sempat gagal lalu retry pada row yang sama, SoundCloud dibuat sebelum upgrade. Periode30hari dan daftar aktivitas aktual diuji; tidak menggandakan file untuk retry atau LRC.


Regresi duplikat — permintaan ulang Lamunan MP3 dengan opsi128+lirik+sampul yang sama ditolak di HP oleh pesan “File ini sudah pernah diunduh.” Antrean tetap kosong; tidak ada job/file baru. Detail video menunjukkan MIME video/mp4,1280×720,259s dan kreator hanya pada detail, bukan tambahan nama file.


Playback video APK final melalui intent Koleksi → Putar membuka MX Player Pro; gambar bergerak teramati pada awal dan seek ke2:09 dari4:19. Decoder HW+ aktif; ini bukti playback codec H.264/AAC pada HP penguji, bukan semua codec/perangkat. LRC Lamunan final byte-identik dengan file sebelumnya yang dikonfirmasi sinkron pengguna.

## Pengecekan ulang sebelum uji Lamunan — 13 Oktober 2026

Permintaan pengelola dilanjutkan dari kondisi terbaru, bukan build historis1.0.8. Gradle dan APK terpasang menunjukkan1.1.0/code14, Room9; SHA256 base.apk perangkat `06da7742a451ad27ae8dd88b15c31fad0b047e2bffcead4de1a6f9482e45eda6`, cocok distribusi final. Tidak memasang ulang, menurunkan versi, mengubah sumber Kotlin/Python, menghentikan pemutar, mengunduh ulang, atau menghapus media.

Review terarah mencakup `SourceMetadata` → snapshot identitas → `Lyrics.fetch` pada worker/cari ulang, serta `LrcTiming`/`LyricEditor` dan dialog waktu. Kredit Lamunan memisahkan Sasya Arkhisna dari creator Music Interactive; validasi pencarian tetap memerlukan versi yang sama dan durasi diketahui dengan gerbang±2detik. Koreksi memakai baseline per URI, bukan offset global. Bukti JUnit aktual:136tes/0gagal/0error; LyricsFetchTest20, LyricTest11, LrcTimingTest5, SourceMetadataTest8. Laporan lint debug/release aktual0issue. Tidak ada sumber runtime/tes/schema yang lebih baru daripada APK final, dan kode lirik terkait mendahului laporan tes tersebut; tidak mengulang tes/build yang sama. Hasil76Python/audit paket tetap berasal dari batch build final di atas, bukan eksekusi baru sesi ini.

File Lamunan aktual ditarik hanya sebagai salinan privat dan diprobe:

- MP3:4.199.522byte,259,392detik, stream128000bps, title `Lamunan | Live`, artist `Sasya Arkhisna`, creator `Music Interactive`, year2024; artwork JPEG800×450. SHA256 `f784d0bdc337be8b3e93147b0e83fe7ab1dbfe8e7992923493a3ec009ae4c7aa`, cocok audio final sebelumnya. Decode penuh sebelumnya tidak diulang karena hash identik.
- LRC:1.456byte, SHA256 `e642dfb16226e4263b25a367023050381a3312c79ee306eae530e70c92f62b4a`.31timestamp berteks, urut,16,62–253,30detik, tidak ada baris rusak/lewat akhir atau tag offset. Nama dasar sama dengan MP3. Byte-identik dengan LRC final1.1.0 dan LRC1.0.10 yang sebelumnya dikonfirmasi sinkron pengelola.
- UI Koleksi → Musik menampilkan Lamunan/Sasya/YouTube dan penanda sampul. Dialog **Atur waktu lirik** Lamunan menunjukkan **Waktu asli** dan preview awal `[00:16.62]`; ditutup lewat **Batal**, tanpa menyimpan koreksi. Satu percobaan inspeksi berhenti karena layar berpindah ke Ringkasan; tidak ada tap pada target yang tidak cocok. Pembacaan ulang berhasil.
- LRC Ya Sudahlah tetap ber-SHA256 `82c16cf6a6e3d45e8637fa4203829146816720b321e3a2118b0b689a5b5a0a4f`, sesuai koreksi−2detik sebelumnya. Koreksi itu tidak diterapkan ke Lamunan.

Bukti lokal privat berada di `build/reports/lamunan-recheck-2026-10-13/` (probe, `verification.json`, salinan pembanding dan dump UI); tidak menyalin isi lirik penuh ke dokumentasi publik. Pengecekan ini membuktikan konsistensi file/metadata/setting, **bukan hasil auditori baru**. Tidak mengulang pemutaran atau simulasi putus jaringan pada sesi ini.

### Tahap berikutnya

Pengelola membuka **Koleksi → Musik → Lamunan → Putar** di Poweramp dan menampilkan lirik lokal, lalu mendengarkan peralihan baris dekat00:16–00:40, sekitar02:00–02:30 dan menjelang04:00. Catat apakah sinkron, sorotan terlambat atau terlalu cepat, besar selisih dan apakah tetap sepanjang lagu. Bila selisih tetap, gunakan koreksi hanya Lamunan dalam langkah0,5detik, buka ulang lirik di pemutar setelah simpan, lalu verifikasi awal/tengah/akhir serta hash audio. Bila selisih berubah, jangan memakai offset global untuk menutup drift; tinjau rekaman kandidat. Jangan Cari ulang lirik sebelum pengamatan baseline ini selesai karena aksi itu dapat mengganti file yang sedang diuji. Konfirmasi auditori terbaru masih menunggu pengelola.
