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
| Lirik dan pencocokan kandidat | `core/Lyrics.kt`, `core/LyricEditor.kt`, `core/LrcTiming.kt`, `ui/LyricsSearchDialog.kt` |
| Koleksi media, pemindaian dan penghapusan | `core/MediaRepository.kt`, `core/MediaLibraryViewModel.kt`, `core/MediaIndexWorker.kt`, `ui/MediaLibraryScreen.kt`, `ui/MediaDashboardScreen.kt` |
| Pemutar lokal, antrean, auto-play dan equalizer | `core/PlayerViewModel.kt`, `core/PlaybackService.kt`, `core/PlaybackQueue.kt`, `core/HeadsetAutoPlayReceiver.kt`, `core/PlayerEqualizer.kt`, `ui/PlayerUi.kt`, `ui/CoverColors.kt` |
| Karaoke vokal/instrumen | `core/KaraokeEngine.kt`, `core/KaraokeWorker.kt`, `core/KaraokeViewModel.kt`, `ui/KaraokeDialog.kt` |
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
- Aksi Hapus semua file membersihkan file dan subfolder bottom-up, mempertahankan root serta riwayat, dan hanya menyentuh root yang dipilih. MediaStore tidak mencatat direktori kosong. Android10 yang masih legacy dan sudah memiliki WRITE_EXTERNAL_STORAGE memakai akses filesystem terbatas ke root Download/<folder> tanpa pemilih SAF tambahan; cek kemampuan ini pada UI dan backend bersama. Android scoped/folder eksplisit tetap memerlukan grant SAF baca/tulis tepat pada root. Tolak symlink/path di luar root dan sinkronkan penghapusan berkas terindeks dengan MediaStore. Pembacaan gagal/batas traversal tidak membuktikan folder kosong; jangan menghapus induknya atau melaporkan bersih.
- Pisahkan ukuran folder penuh, file yang terlihat aplikasi, dan kegagalan baca. Jangan mengubah query gagal/batas traversal menjadi nol atau jumlah parsial yang tampak lengkap.
- Pemindaian Koleksi (`MediaRepository.scan()`) menggabungkan hasil MediaStore dengan dokumen pada grant SAF persisten `Download/TubeFlow` (tanpa mengubah tujuan output unduhan), melakukan deduplikasi berdasarkan folder+nama+ukuran, dan mengembalikan `ScanSummary` yang jujur. Pada Android 10 (API 29), file lama hasil SAF/instalasi sebelumnya dapat memiliki `owner_package_name = NULL` sehingga tersembunyi dari query MediaStore biasa setelah hapus data; pertahankan `requestLegacyExternalStorage="true"` di manifest, izin baca/tulis klasik (`maxSdkVersion 29`) serta `READ_MEDIA_*` (API 33+), dan auto-scan saat Koleksi kosong (`scanIfEmpty`). Tombol manual "Pindai folder"/"Impor file" di layar Koleksi telah dihapus karena pemindaian berjalan otomatis.
- Penghapusan satu item dari Koleksi (`MediaRepository.delete()`) menghapus file fisik/URI beserta baris database (`dao.remove` + event `DELETED`) sehingga item langsung lenyap dari daftar; jangan menyisakan baris berstatus "file tidak tersedia" (`UNAVAILABLE`) setelah pengguna memilih hapus.

## Identitas, playlist dan UI

- Gunakan `Selection.videoId` dan `DownloadJob.matchesOutput` untuk identitas sumber serta seluruh opsi keluaran. Alias watch/youtu.be/Shorts/Live/Music untuk video sama tidak menjadi hasil baru; bitrate/format/opsi berbeda tetap sah.
- Snapshot identitas disimpan pada job: rawTitle/trackTitle/artist/artistEvidence/creator/remixer/versionTag. `TitleSplit` + `SourceMetadata` dipakai submit/worker/tag audio/Auto Folder/lirik. Jangan mempromosikan creator atau uploader menjadi artis; channel hanya sinyal jika credit judul lengkap memperkuatnya. Tanpa bukti, artist kosong. Structured track melewati pembersihan grup versi/credit yang sama dengan raw title; jangan membuat `(slowed) | slowed` atau mengulang artist yang sudah terbukti. Resolusi provider hanya menerima identitas unik dengan judul/artis/versi dan durasi yang cocok. Auto Folder job playlist/Mix baru memakai policy `collection`: satu snapshot subfolder untuk seluruh pilihan. Mix memakai artis utama seed yang terbukti (mis. `mix-denny-caknan`), lalu remixer teridentifikasi; bila tidak pasti, gunakan ID seed sebagai nama koleksi, bukan channel. Playlist/album statis memakai judul playlist aktual. Artis/tag/nama file tetap per lagu; jangan mengganti folder saat metadata lagu lain diperbarui. Nama channel/creator saja bukan bukti. Policy `performer` lama dipertahankan untuk kompatibilitas. `withMetadata` dan `withNativeAudioMetadata` harus memakai helper folder yang sama. Policy lama `artist`/`mix` tetap kompatibel; file selesai tidak dipindahkan otomatis. Kredit berlabel Title/Voc/Judul/Penyanyi diterima hanya bila satu pasangan unik cocok dengan raw title; Cipt/composer dan produser bukan performer.
- CollectionCredit hanya membagikan artis yang terverifikasi lewat credit judul lengkap atau album OLAK terkoroborasi; jangan mempromosikan frekuensi teks/urutan uploader/playlist owner menjadi bukti. DJ/remix tetap per identitasnya. Filename memakai kurung untuk versi agar pipe tidak diganti provider menjadi underscore; raw title dan display tetap dipisah.
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

Bedakan tidak ditemukan, tidak cocok, instrumental, HTTP429/rate limit, gangguan provider/5xx/akses ditolak, respons invalid, kegagalan koneksi dan gagal simpan. Lookup manual/otomatis memakai discovery dan validasi LRC bersama yang terbatas; hormati Retry-After dan jangan menembus cooldown lewat clear cache. Timestamp kosong harus tetap disimpan untuk mengosongkan sorotan saat hening. Download audio yang berhasil tetap sukses meskipun lirik gagal, dengan status lirik terpisah. Cari ulang lirik memakai lokasi audio yang sudah terbit, bukan folder pengaturan terbaru; penggantian gagal harus mempertahankan lirik lama. Periksa implementasi backup/rollback serta hasil audit terbaru; jangan mengganti jalur aman dengan penulisan langsung yang menghilangkan lirik lama saat gagal.

Pada pencarian fallback, skor identitas lebih kuat tetap prioritas; tie memakai selisih durasi terdekat setelah gerbang versi/durasi. Ini bukan bukti sinkronisasi auditori. Delay tetap perlu koreksi per rekaman dengan pratinjau/backup; jangan menggeser timestamp seluruh lagu karena satu contoh terlambat.

## Player lokal

Satu `MediaPlayer` on-device untuk seluruh proses aplikasi (`PlayerViewModel.obtain()` ber-scope proses, bukan Activity, sehingga menutup UI tidak mematikan pemutaran via `onCleared`), semua metode dijalankan pada HandlerThread/Looper engine yang sama. File/LRC/artwork dibaca di luar main thread, token track membatasi callback lama. Pemutaran berlanjut di latar lewat `PlaybackService` (foreground `mediaPlayback`, MediaSession dengan durasi dan reset posisi ke 0 setiap ganti lagu, notifikasi `DecoratedMediaCustomViewStyle` berikon monokrom `ic_stat_playback` dengan isi ringkas bersampul 40dp dan tombol X langsung, partial wake lock hanya saat putar); jeda otomatis saat app ke latar tidak lagi berlaku. Kontrol notifikasi play/pause/prev/next/stop dan tombol media headset mengarah ke engine yang sama; jangan menambahkan player kedua.

Antrean putar dikelola oleh `PlaybackQueue` (posisi, add-next/add, remove, shuffle dengan pemulihan urutan asli, repeat OFF/ALL/ONE) pada thread engine:
- Ketuk satu lagu di **Koleksi** memasukkan seluruh daftar yang sedang tampil ke antrean mulai dari lagu yang diketuk.
- Ketuk satu lagu di **Riwayat** (`HistoryScreen.onPlay`) hanya memutar lagu tersebut (atau menambahkannya sesuai aksi), **bukan** membanjiri antrean dengan seluruh daftar riwayat.
- Auto-play saat perangkat audio tersambung bekerja lewat dua jalur: `AudioDeviceCallback` saat proses hidup (kabel/Bluetooth/USB tanpa izin tambahan) dan `HeadsetAutoPlayReceiver` di manifest untuk `BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED` (dikecualikan dari pembatasan implicit broadcast sehingga dapat membangunkan proses mati saat TWS tersambung; `ACTION_HEADSET_PLUG` kabel tetap terbatas pada receiver runtime oleh Android). Saat auto-play terpicu, `AutoPlayQueue.plan()` mengisi antrean dengan **seluruh koleksi audio yang tersedia** (`MediaDao.availableAudio()`) dimulai dari lagu terakhir yang disimpan (atau awal koleksi bila lagu terakhir sudah terhapus); bila lagu aktif masih siap, lebarkan antreannya tanpa me-reload MediaPlayer agar posisi putar tidak terputus.

Equalizer (`PlayerEqualizer`) dan kecepatan putar (0,25–3×) memakai efek audiofx dan `PlaybackParams` pada session yang sama, dibungkus `runCatching` karena ketersediaan tergantung perangkat. Sediakan preset bawaan **Otomatis (bass bulat)** (`AutoEq`: kurva frekuensi tengah band +5 dB @60 Hz, −1 dB mid, +2,5 dB treble yang di-clamp ke rentang level perangkat) sebagai default yang tetap menghormati pilihan preset/level manual pengguna.

Layar pemutar penuh memakai `HorizontalPager` antara halaman Sampul dan Lirik (tanpa chip Sampul/Lirik; swipe manual pengguna menang atas auto-switch saat lirik selesai dimuat), latar bergradien dari aksen sampul (`rememberCoverAccent`), serta panel bawah Antrean dengan sampul per lagu dan penanda "Sedang diputar". Saat lirik belum ada atau ingin diubah, pemutar menyediakan langsung tombol **Cari lirik** (`LyricsSearchDialog`) dan **Editor lirik** (`LyricTimingDialog`) via `MediaLibraryViewModel.openLyricsForTrack`/`itemFor` tanpa harus kembali ke layar Koleksi. LRC memakai timestamp asli+offset, bukan interpolasi/offset global. Source informasi platform tidak berasal dari warna artwork. Rujuk laporan upgrade terbaru untuk cakupan runtime dan minify.

Pilihan lirik manual memerlukan preview, ID/atribusi, pemeriksaan snapshot sebelum simpan dan pengakuan ketika berbeda dari identitas audio aktual. Parameter pencarian/filename edit bukan metadata artis terverifikasi. Gunakan LyricPublisher bersama refetch, jangan menyalin publikasi URI. Input waktu adalah offset total dari cadangan asli per lagu. Kontrol kanal L/R sudah dihapus atas permintaan pengelola (termasuk `AudioChannel.kt`); pemisahan vokal hanya lewat jalur karaoke — jangan mengklaim karaoke universal.

## Karaoke opsional

Pemisahan vokal merupakan job terpisah yang diminta pengguna, bukan mode L/R atau tahap wajib downloader. Judul dialog karaoke selalu mengikuti lagu yang sedang dipilih dan menampilkan label sumber yang jelas bila ada proses lama yang masih berjalan. Pertahankan sourceaudio, publish instrumental sebagai media turunan setelah pengguna memilih Simpan, dan jangan mencatatnya sebagai download jaringan. Model tidak dibundel; ukuran/hash asli serta model siap pakai wajib diverifikasi. Jangan mengganti weights atau deklarasi input tanpa verifikasi tensor/FFT/iSTFT dan runtime HP. Dua deklarasi waktu model saat ini diperpendek untuk RAM; hasil bukan jaminan tanpa vokal semua lagu. Pin `com.microsoft.onnxruntime:onnxruntime-android:1.23.2` dipertahankan dengan sengaja karena versi lebih baru (mis. 1.31.0) gagal inisialisasi JNI pada perangkat target Android 10.

Jaga singlejob/mutex cleanup, handleONNX dan FFmpegchild milikjob. UI CANCELLED belum membuktikan native sudah selesai; jangan menghapus/menimpa direktori job lama dari job baru sebelum cleanup berhenti. Cuplikan harus berlabel cuplikan; jangan memasangkan LRC seluruh lagu pada audio yang telah dicrop. Untuk lagu penuh, salin LRC tersendiri agar delete/rename instrumental tidak menyentuh lirik sumber. Baca laporan karaoke terbaru untuk batas RAM/durasi/arsitektur dan cakupan tes nyata.

Aksi dengarkan/simpan karaoke hanya berlaku bagi request dengan URI sumber, mode cuplikan/penuh dan posisi yang sesuai. Hasil lama yang berbeda harus berlabel; simpan cuplikan memerlukan konfirmasi rentang, tanpa LRC lagu penuh. Start yang ditolak tidak menutup player; request diterima cukup menjeda dan mempertahankan antrean/seek. Cancel/Buang pratinjau mencatat intent persisten, menunggu lease, lalu memverifikasi direktori job, fragmen model dan jurnal bersih sebelum menghapus status. Cleanup gagal menahan proses baru. Model terverifikasi dan hasil Koleksi bukan file sementara; Tutup dialog mempertahankan proses/preview. Preview dan publish memverifikasi manifest sumber/mode/hash terlebih dahulu.

Ukuran katalog di Pengaturan mengikuti agregat `MediaDao.totals()` termasuk perubahan scan/impor/hapus/karaoke; jangan hanya mengamati riwayat download. Ukuran null ditandai parsial. Ukuran Koleksi tidak boleh diklaim sebagai seluruh isi folder atau penyimpanan perangkat.

`PlaybackNotifications` membandingkan state terakhir yang diterbitkan: progres berjalan per detik, seek saat jeda dan perubahan kecepatan langsung. Mengamati hanya track/playing/duration membuat posisi MediaSession tertinggal. Jangan memakai atribut tema Activity untuk tinta X notifikasi karena SystemUI dapat berbeda tema; uji kontras serta aksi stop pada drawer HP.

Auto-play 1.1.10: receiver koneksi Bluetooth manifest/runtime memakai exported karena sender dapat berada pada UID Bluetooth tersendiri; action dibatasi pada broadcast sistem terproteksi. Service pemutar tetap tidak diekspor. Izin BLUETOOTH berlaku sampai API30, BLUETOOTH_CONNECT diminta lewat Pengaturan pada API31+; jangan menambahkan discovery/lokasi untuk menerima sambungan. Receiver menyerahkan pekerjaan ke foreground service sebelum Room/fokus audio, dengan bootstrap terbatas dan fallback ketuk-untuk-putar bila start ditolak.

Sesi media aktif memakai START_STICKY serta marker `player-last/session_active`; X membersihkan marker. Pemulihan tanpa headset harus tetap jeda. Jangan menerapkan PlaybackParams pada player jeda karena dapat memulai audio; simpan pilihan kecepatan dan terapkan sesudah fokus audio saat play. Pemulihan antrean/seek memakai PlayerSession privat (3000entri/4MiB), ID urutan asli shuffle, indeks/repeat dan checkpoint posisi kecil ber-revisi tiap5detik/pause/seek. Snapshot antrean hanya ditulis ketika berubah. Pemulihan proses dibuka jeda; initial/sticky headset ketika registrasi bukan sambungan baru. Format1.1.14 tidak menyimpan antrean/seek sehingga data tersebut tidak dapat dipulihkan retroaktif. Kabel HEADSET_PLUG tidak membangunkan proses mati; force-stop dan kebijakan OEM tetap membatasi restart, sehingga klaim auto-play harus menyebut skenario HP yang diuji.

Pencarian antrean memakai posisi asli dari `PlaybackQueueSearch`, bukan indeks hasil filter. Semua aksi jump/move/remove tetap mengacu pada antrean penuh. Full player memakai seek track4dp/thumb12dp dengan target sentuh48dp; jumlah antrean berada di dalam tombol, tanpa badge yang keluar kontainer.


Validasi Koleksi1.1.12: hanya MISSING yang terbukti dengan akses sah menghapus row;
query null/gagal dan izin dicabut adalah UNKNOWN/DENIED, bukan bukti hilang.
Observer MediaStore didebounce; ON_RESUME memeriksa kehadiran tanpa mengekstrak tag
atau mencari lirik lagi. Folder kosong bukan galat scan. Nama tag audio memakai
label versi kurung yang sama dengan filename; artist sumber yang baru muncul saat
download tidak boleh dihapus oleh snapshot kosong atau diganti uploader. Cari ulang
memakai nama publikasi/durasi aktual; parameter filename baru bukan artist terverifikasi.


Preferensi lanjut setelah interupsi (SettingsStore.resumeAfterInterruption) default
aktif; mematikannya membatalkan niat/fokus tertunda saat itu juga. Pengaturan ini
terpisah dari pemicu sambungan headset. PlaybackInterruption/permanent recovery
hanya meminta fokus sekali setelah audio lain teramati aktif→sunyi; mode komunikasi,
status tidak diketahui, jeda manual/stop/release/ganti track membatalkan atau menahan.

PrivateStorage memisahkan cache lirik/metadata/sampul dan sementara downloads dari
media. Cleanup eksplisit berkonfirmasi boleh membuang PAUSED/FAILED idle, dengan
peringatan quota/retry dari nol; cleanup otomatis tetap mempertahankannya. Pakai
DownloadFileAccess lease yang dipegang worker melalui finally, periksa engine/WM/
generasi/status, tolak symlink/traversal tidak lengkap dan jangan menghapus output
media/model/jurnal LRC. WorkManager finished saja bukan izin menghapus file.

FailureDetails/Store adalah satu jalur diagnostik tersanitasi, perjob/generasi/status
lirik,80entry/1MiB. Tampilkan detail teknis di dialog copy yang dipicu pengguna;
waktu/versi/HTTP yang tidak tercatat tetap unknown. RATE_LIMIT bisa berasal dari
cooldown lokal: jangan mengarang HTTP429 baru. Laporan lirik tidak mengubah sukses
media menjadi FAILED. Fetch otomatis mendahulukan searchPaths yang sama dengan
manual; exact get hanya fallback. Pertahankan gerbang identitas/versi/durasi±2detik,
cache positif dan kepatuhan Retry-After; jangan menembus cooldown untuk mengisi statistik.
