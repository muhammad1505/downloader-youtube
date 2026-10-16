# Perbaikan bertahap TubeFlow 1.1.15

## Ringkasan verifikasi terkini

Seluruh lima kelompok implementasi tersedia pada **1.1.15/code 29**. APK final sudah
dipasang update-r dan hash base.apk cocok. Pengujian host/build selesai; beberapa
pemeriksaan layar akhir menunggu HP selesai dipakai untuk panggilan. Bagian
checkpoint di bawah adalah riwayat pengerjaan, bukan pengganti status pada tabel ini.

| Tahap | Implementasi dan bukti | Batas yang masih terbuka |
|---|---|---|
| Penamaan, metadata, lirik, koleksi | Audit 51 MP3; download M4A SATRU2Live benar nama/tag dan decode penuh;2 file QA otomatis hilang dari daftar setelah dihapus; lookup otomatis sekarang search-first dengan gerbang versi/durasi tetap |24 file lama tanpa artis tidak diisi sembarang; LRC baru sumber Live masih mendapat status pembatasan pada kandidat awal; sinkron auditori baru belum terbukti |
| Antrean dan posisi | Snapshot atomik ber-revisi,3000 entri/4 MiB; posisi kecil tiap5 detik/pause/seek;51 lagu/acak/ulangi/31589 ms pulih jeda setelah force-stop/manualopen pada kandidat awal; APK final kembali memulihkan51 lagu/jeda44817 ms |Kehilangan proses hanya pulih sampai checkpoint terakhir; bukan bukti semua OEM/low-memory/stickyrestart;1.1.14 tidak menyimpan antrean/seek lama |
| Preferensi auto-resume | Default aktif; disable langsung membatalkan niat tertunda;5 kasus fokus native pada kandidat awal lulus termasuk transient/permanent/disableketikamenunggu; kode kontrol sama di final |Persistensi OFF lintas restart/final kabel/TWS menunggu layar HP; nilai acak awal aktif telah dikembalikan sesuai baseline |
| Detail error | Dialog/copy inspeksi nyata unavailable-video lulus; versi/engine/tahap/waktu ada, HTTP tidak ditebak; laporan worker dan lirik dibatasi80 entry/1 MiB dan ID/generasi/status |Dialog lirik final perlu cek layar; fault injection provider/filewrite Android belum menyeluruh |
| Ruang sementara | Ukuran cache terpisah dari media/parsial; cache 6,9→5,2 MB setelah clear lirik; sementara 0B dan dialog quota/from-zero/noopcleanup diuji; lease/symlink/nonzero deletion diuji host |Tidak menghapus parsial nonzero milik pengguna demi tes; perlindungan worker dibuktikan di host, belum seluruh race native/kill/provider |

**Build final:** `bash scripts/build_release.sh assembleDebug --no-daemon --max-workers=2`
berhasil9m55s; **296 Kotlin lulus**,84 Python lulus (skrip tidak berubah setelah
pembaruan Kotlin). Lint debug/release masing-masing0 error/2 warning lama:
SelectedPhotoAccess dan GradleDependency ONNX yang sengaja di-pin. Deprecation
Gradle/API terpisah dari lint. APK×3 lulus signature/sertifikat produksi/native/
zipalign16 KB/refleksiZIP/checksum engine/bootstrap. AAB jarsigner/native lulus;
bundletool tidak dijalankan ulang. Runtime diuji pada RealmeRMX1821 Android10/API29,
ARM64, RAM4 GB, halaman4 KB; bukan perangkat16 KB atau Android15+.

### Berkas dan kompatibilitas

- Baru: `core/PlayerSession.kt`, `core/PrivateStorage.kt`, `core/FailureDetails.kt`,
  `ui/FailureDetailsDialog.kt`; tes PlayerSession/PrivateStorage/FailureDetails.
- Diubah: PlayerViewModel/PlaybackQueue/PlaybackInterruption/SettingsStore;
  DownloadViewModel/DownloadWorker/Models/Lyrics; MediaLibraryViewModel/
  MediaRepository/SmartMedia; SettingsScreen/DownloadScreen/TubeFlowApp/components,
  strings.xml dan versi Gradle. Tidak menghapus berkas sumber.
- Room tetap9, tanpa migrasi/tabel/izin/dependensi baru. Snapshot player serta
  diagnostik privat bukan salinan media atau database katalog baru. JSON diagnostik
  menerima field status opsional; histori yang tidak mencatat waktu/versi/HTTP
  tetap ditandai tidak diketahui. Default folder tetap Download/TubeFlow.
- Sampul, data audio, LRC lama, keystore dan pengaturan DNS/keamanan dipertahankan.
 60 file baseline telah dibandingkan sesudah upgrade kandidat awal; pembandingan
 akhir setelah seluruh tes masih dijadwalkan. Retensi seluruh baris histori lama
 tidak diklaim tanpa baseline database.

### Artefak final yang terpasang

| Artefak | Byte | SHA256 |
|---|---:|---|
| arm64-v8a APK | 70679199 | `4e2ce13b649d93e072402f3bdf8688f44c78c46334cd8541bd619116ca2e58d4` |
| armeabi-v7a APK | 62369657 | `3a678bf13de291ddda76b2ffffffae566ee9f3e7bdb014bcd8062d548225c260` |
| x86_64 APK | 75120012 | `46c4313d004f1115236ae496fca7784982066cc19f5b257a14c317a11769d0d3` |
| AAB | 255746691 | `4dee008de65d908a601f5feeb5fd6c2e30ac228e45c6ebfe5e412d21ae42abef` |

APK aktif berada di app/build/outputs/apk/release/; AAB di
app/build/outputs/bundle/release/. Tidak menambah salinan distribusi atau publikasi
toko. ARM64 bertambah98.816 byte dari1.1.14; belum ada benchmark baterai/RAM
menyeluruh. Snapshot antrean hanya ditulis saat berubah, sedangkan checkpoint
posisi memakai berkas kecil agar tidak menulis ulang ribuan entri setiap5 detik.

### Langkah uji Android10 untuk pengembang berikutnya

1. Pasang update bertanda tangan sama; jangan uninstall/clear-data. Pertahankan
   media pengguna. Periksa jumlah/ukuran serta SHA256 file sebelum/sesudah.
2. Putar koleksi, acak/ulangi, seek dan pause. Hentikan proses hanya ketika semua
   download/karaoke idle; buka manual dan bandingkan track/antrean/posisi/jeda.
3. Uji auto-resume aktif/nonaktif dan disable saat interupsi; sambungkan headset
   fisik untuk regresi. Initial/sticky callback saat boot tidak berarti koneksi baru.
4. Buat file QA beridentitas unik, scan, lalu hapus hanya file/row milik QA. Daftar
   harus hilang sendiri; akses DENIED/UNKNOWN tidak boleh dianggap file terhapus.
5. Periksa cache/parsial. Konfirmasi cleanup hanya pada data QA, bukan parsial
   pengguna. PAUSED/FAILED boleh dihapus hanya setelah persetujuan eksplisit dan
   lease idle; retry kemudian mulai dari nol. Jangan hapus model/jurnal LRC/media.
6. Untuk lirik, tunggu cooldown normal; coba sumber dengan judul/artis/versi/durasi
   valid. Bedakan tidak cocok, tidak ditemukan dan pembatasan. Jangan memilih
   kandidat yang berbeda hanya agar tes lulus. Catat record/sumber/offset dan
   dengarkan awal/tengah/akhir sebelum menyebut sinkron.
7. Salin detail inspeksi/download/lirik, pastikan redaksi dan waktu/HTTP tidak
   ditebak. Tidak ada pengiriman otomatis. Bukti privat lokal:
   build/reports/improvements-1115/; jangan unggah screenshot berisi notifikasi pribadi.

## Riwayat checkpoint dan rencana awal


## Cakupan dan checkpoint

Permintaan pengelola: kerjakan kelima usulan setelah 1.1.14 secara bertahap.
Baseline: 1.1.14/code28, Room9, satu MediaPlayer dan PlaybackService; HP fisik
Android10 tersambung. Dua berkas untracked lama tidak termasuk perubahan ini.

1. Verifikasi penamaan/tag/lirik/koleksi; perbaiki kehilangan permintaan validasi
   yang datang ketika validasi sedang berjalan. Metadata/LRCLIB menggunakan jalur
   yang sudah tersedia; jangan menebak artis atau melonggarkan syarat sinkronisasi.
2. Simpan antrean (termasuk identitas urutan shuffle), indeks dan posisi lewat
   snapshot privat atomik/batas ukuran. Pulihkan jeda; koneksi headset eksplisit
   tetap mengikuti auto-play yang sudah ada. File tidak dapat dibaca dilewati.
3. Tambahkan pilihan lanjut otomatis setelah interupsi, default aktif mengikuti
   1.1.14. Mematikan pilihan membatalkan niat lanjut, termasuk ketika sedang menunggu.
4. Detail kegagalan download/inspeksi yang dapat disalin: kategori, tahap, versi,
   waktu aktual bila tercatat, HTTP hanya bila terbukti; redaksi URL/token/path.
5. Ukuran cache dan parsial terpisah dari media. Penghapusan parsial memerlukan
   konfirmasi dan lease yang sama dengan worker, melewati job aktif. Media, model
   karaoke, baseline/jurnal LRC dan kredensial tidak dibersihkan oleh aksi ini.

## Audit awal

- Player hanya menyimpan track terakhir; tidak ada snapshot antrean/seek persisten.
- Loss audio sudah terverifikasi 1.1.14 tetapi belum mempunyai preferensi pengguna.
- Validasi koleksi mengabaikan event ketika job validasi masih aktif; file PRESENT
  yang tetap AVAILABLE tidak memperbarui checkedAt sehingga urutan cek tidak maju.
- Kartu gagal hanya menampilkan pesan; belum ada laporan tersanitasi yang persisten.
- Pengaturan memiliki ukuran katalog/folder dan clear lirik, belum angka cache dan
  parsial terpisah. WorkManager finished bukan bukti engine/finally sudah berhenti.

## Rencana UI terfokus

Gunakan peran Material3/tema yang ada: primary ungu #C6B7FF, surface #111113,
onSurface #F0EDF4, outline #858B9D (tema terang tetap mengikuti token yang ada).
Judul titleMedium, isi bodyMedium/bodySmall, jarak 8/12/16 dp, kartu radius tema,
target sentuh minimum48 dp. Tidak mengubah navigasi atau tata letak player.

```text
Pengaturan
  Pemutaran
    Lanjut otomatis setelah interupsi                [switch]
  Penyimpanan
    Ukuran media: ...  (cakupan katalog/folder tetap jelas)
    Cache yang diukur: ...       [Bersihkan cache lirik]
    Parsial download: ...        [Hapus parsial…]
      dialog: resume dari awal; job aktif dilewati
Kartu gagal
  Pesan dan tindakan utama
  [Detail error] -> ringkasan tersanitasi -> [Salin detail error]
```

Kritik: jangan menambah kartu metrik generik atau dua angka seolah menghitung
ruang perangkat yang sama; cache/parsial diberi cakupan dan status parsial bila
pengukuran terbatas. Detail teknis tetap di dialog, bukan pesan utama.

## Hasil

Checkpoint implementasi kelima tahap tersedia. 291 tes Kotlin dan 84 tes Python
lulus pada checkpoint awal. Pemeriksaan pertama sempat gagal karena resource baru
berubah saat build berjalan; build berikutnya menghasilkan resource dan lulus.
Optimasi checkpoint posisi terpisah bertoken revisi serta fallback semua file
hilang ditambahkan sesudahnya; suite final dan APK release masih harus diverifikasi.

Snapshot antrean dibatasi 3000 entri/4 MiB; posisi kecil disimpan setiap 5 detik
serta saat pause/seek/perubahan antrean. Kedua berkas atomik memakai revisi sama
agar posisi lama tidak diterapkan pada antrean baru. Daftar asli shuffle dan
kemunculan URI berulang tetap dipertahankan. Pemulihan proses selalu jeda;
initial/sticky headset saat registrasi tidak dianggap sebagai sambungan baru.

Clear parsial memakai lease berstrip 64 mutex yang juga dipegang worker sampai
finally selesai. Job aktif/QUEUED/RUNNING/COMPLETED dan traversal tidak lengkap/
symlink dilewati. Hanya data sementara di direktori privat downloads/<jobId>
yang dihapus setelah konfirmasi, bukan output media. Karena izin eksplisit ini,
PAUSED/FAILED yang telah idle boleh dibersihkan dan dilanjutkan dari nol; ini
menggantikan usulan lama yang melindungi PAUSED dari semua cleanup otomatis.

Laporan error disimpan maksimal 80 entri/1 MiB, per ID/generasi, tanpa URL/token/
path lokal; waktu kegagalan histori lama tidak ditebak. Inspeksi memiliki detail
tersendiri. Tidak ada dependensi, izin, tabel atau migrasi Room baru.

Bukti sementara privat: build/reports/improvements-1115/. File APK final,
checksum, hasil perangkat, dan batas aktual ditambahkan setelah pengujian.

Build kandidat sesudah checkpoint dihentikan lebih awal untuk memperluas redaksi
access_token/client_secret/po_token/refresh_token/client_id beserta tesnya;
artefak dari build yang dihentikan tidak dipasang atau dibagikan.

Suite berikutnya lulus 292 Kotlin; lint menemukan tiga LocalContextGetResourceValueCall
pada label aksesibilitas/ukuran di Pengaturan. Label dipindahkan ke stringResource
pada scope Composable (tanpa suppress), lalu pemeriksaan lengkap dijalankan ulang.


## Checkpoint HP — kandidat awal 1.1.15

APK ARM64 kandidat awal terpasang dan base.apk cocok SHA256
6405f64efdce20e905367196539c32f39e49d6ba900387a9166c34aca3b7dd45.
60 file baseline (51 MP3/9 LRC) identik setelah upgrade. Audit seluruh MP3:
51 judul, 27 artis; 25 nama cocok literal dan dua lainnya hanya pembersihan
karakter terlarang. 24 artis kosong, 23 dengan penanda DJ/remix/versi; tidak
mengarang artis. Dua LRC pasangan valid struktur/durasi, bukan bukti auditori.

Pemulihan force-stop/manual open lulus: PID berubah, antrean 51/shuffle/repeatALL
serta posisi31589 ms sama dan tetap jeda. Lima kasus sakelar auto-resume lulus,
termasuk mematikan ketika pemulihan sudah menunggu; akhir aktif/jeda. Dua tone
QA berlabel uji diindeks, kemudian hanya file/row miliknya dihapus. Daftar turun
2→1→0 otomatis tanpa scan ulang atau baris unavailable. Semua file QA tersebut
sudah hilang; tidak menghapus media pengguna.

Pengaturan membaca cache 6,9 MB dan sementara 0B; clear cache lirik turun menjadi
5,2 MB, cooldown dipertahankan. Dialog cleanup quota/from-zero diuji pada baseline
sementara 0B; cleanup parsial nonzero milik pengguna tidak dilakukan. Host menguji
file parsial nonzero/symlink/lease worker secara terpisah.

Download sumber rEjSkBl47OY ternyata SATRU2Live (bukan asumsi PINGAL dari pesan
lama): M4A baru berjudul/artis benar, durasi393,98 detik, seluruh audio lulus decode.
Nama SATRU 2 (Live) - DENNY CAKNAN FT HAPPY ASMARA.m4a sesuai tag. Audio sukses,
lirik berstatus pembatasan LRCLIB, tidak disebut galat jaringan.

Analisis menemukan fetch otomatis masih mendahulukan /api/get, sedangkan manual
memakai /api/search. [Dokumentasi resmi LRCLIB](https://www.lrclib.net/docs)
menjelaskan get dapat mengambil dari sumber eksternal ketika database tidak punya
signature tersebut; cooldown/Retry-After tetap wajib dihormati. Regresi dibuat
untuk memakai hasil search valid sebelum exact fallback yang dapat membatasi.
Kandidat akhir setelah perbaikan discovery belum dibangun/diuji pada checkpoint ini.


Regresi HTTP lokal pada kode lama membuktikan expected tersimpan / actual
 dibatasi_penyedia: exact lookup429 menutup akses ke search200 yang valid. Fetch
sekarang mendahulukan rencana search yang sama dengan manual, memakai seluruh
validasi identitas/versi/durasi lama dan memilih kandidat sebelum berhenti. Exact
lookup tetap fallback bila search belum cocok, dengan album_name kosong eksplisit
untuk album tidak diketahui. Tidak menambah endpoint yang belum diverifikasi,
mengosongkan cooldown, memutar IP, atau meneruskan request setelah429.

Tes terarah lirik/diagnostik sesudah perbaikan lulus; tambahan tes memastikan cache
hasil manual bisa dipakai otomatis saat lagu lain dibatasi tanpa request jaringan.
Detail error lirik juga tersedia terpisah, per ID/generasi/status; pada histori
lama waktu/versi/HTTP yang tidak tercatat tetap tidak diketahui. RATE_LIMIT dapat
berasal dari cooldown lokal sehingga laporan tidak mengarang HTTP429 baru.
