# Navigasi notifikasi, cache lirik, dan playlist 1.1.20

Release final sudah terpasang dan pengelola mengonfirmasi notifikasi sesuai.
345 tes Kotlin/93 Python lulus; 62 file awal tetap identik setelah cleanup QA.

## Audit baseline dan arah

Baseline aktual: commit 1d7caf3, 1.1.19/code33, Room10. Perubahan komponen/panel/filter
83d4f97 dipertahankan. HP juga memakai 1.1.19; dumpsys menemukan dua MainActivity
dalam satu task. Ini berbeda dari checkpoint 1.1.18, jadi perbaikan mengikuti
source dan keadaan perangkat terbaru.

Akar: launcher masih standard; intent notifikasi player tidak membawa tujuan,
hasil download membuka file eksternal, worker aktif/karaoke tanpa content intent.
Cache hanya dibersihkan bila objek in-memory sudah dibuat; lock cache menahan
load jaringan dan tombol tidak menampilkan hasil. Tombol secondary secara eksplisit
memakai border=null, sehingga peran outlined pada sistem desain hilang.

Polesan mengikuti DESIGN.md 1.1.19: palet dan font tetap, spasi8/12/16dp,
ActionButton pill48/52dp, outline sekunder dan merah untuk aksi destruktif.
Kelola playlist memakai dialog Material yang sama, daftar lazy bergulir, pencarian,
form tambah/ubah jelas, batal edit, dan error yang mempertahankan nama pengguna.
Kritik: tidak membuat panel dekoratif/statistik baru; fokus pada nama daftar dan
operasi referensi, bukan file lagu. Notifikasi memakai MediaStyle native Android.

```text
Kelola playlist
  keterangan ruang lingkup
  [nama playlist] [Buat / Simpan nama] [Batal ubah]
  jumlah aktual + [Cari playlist]
  nama daftar
    [Ubah nama] [Hapus]  (outline, membungkus)
  [Tutup]

Notifikasi → satu MainActivity → tujuan terkait
  musik → full player; selesai → Riwayat
  unduhan aktif/gagal → Antrean; karaoke → status karaoke
```

## Hasil akhir build dan pemasangan

**1.1.20/code34** dibangun sebagai release non-debuggable dan dipasang update-r
pada Realme RMX1821 Android10/API29, ARM64, RAM4GB, halaman4KiB. Hash base.apk
cocok dengan ARM64 di bawah. Keystore produksi dipertahankan, tanpa uninstall/reset.
Room tetap10; tidak menambah dependensi, izin, engine, model atau migrasi.

- `bash scripts/build_release.sh assembleDebug --no-daemon --max-workers=2`:
  BUILD SUCCESSFUL **11m24s**, debug + APK release tiga ABI + AAB/R8.
- **345 tes Kotlin lulus**, termasuk enam regresi baru cache/navigasi.
- `python3 -m unittest discover -s scripts -p 'test_*.py'`: **93 lulus**,48,373s.
  Angka93 mengikuti source terbaru, bukan angka90 dari laporan lama.
- Lint debug/release:0error/2warning lama (`SelectedPhotoAccess`, `GradleDependency`
  ONNX1.23.2 yang dipin). Deprecation compiler/Gradle terpisah dari warning lint.
- Percobaan debug awal menemukan dua pemanggilan fixture cache lama, lalu dua
  akses resource Compose yang tidak configuration-aware; diperbaiki sebelum
  build final. Resource notifikasi custom yang tidak dipakai juga dibuang.
- Audit APK×3: signature/cert produksi, metadata/non-debuggable, zipalign16KB,
  native, refleksiZIP setelah minify, checksum engine dan bootstrap lulus.
  ARM64:257ELF/13kelas ZIP. AAB jarsigner/native1.038ELF lulus. Bundletool tidak
  diulang; audit alignment tidak membuktikan runtime16KB.

| Artefak | Byte | SHA256 |
|---|---:|---|

| APK arm64-v8a | 70,870,104 | `c82ff6934889a3ae67e693b3ac6e0143f9c378acf52506e635b3ffa023b6e583` |
| APK armeabi-v7a | 62,560,562 | `94ccad2f49d0e461df78f80b14a35ec050457d016f39668f2198879c59c462d0` |
| APK x86_64 | 75,310,913 | `d624d68194603093c9757c5bc6f60a7b4fc4b1bdf57f30c38f9a9d58e2752b8e` |
| AAB | 255,944,677 | `4a4c570b28c81c4b20f88379dab5fd4baf520bb1575c8ce35853fb8edaa2a461` |

APK di `app/build/outputs/apk/release/`, AAB di `app/build/outputs/bundle/release/`.
Tidak menambah salinan distribusi atau menerbitkan ke toko.

## Implementasi dan file

| Temuan | Solusi dan file |
|---|---|
| BUG-067: cache diam/tidak terhapus setelah startup | `Lyrics.clearCache` menerima direktori eksplisit, walau Engine belum diinisialisasi. `LyricsResponseCache` memisahkan lock jaringan/disk; generasi menolak penulisan respons lama sesudah clear. Hasil sukses/kosong/parsial diteruskan ke SettingsScreen dengan status busy dan live region. LRC serta cooldown tidak dihapus. |
| BUG-068: Activity menumpuk | MainActivity memakai singleTask; onNewIntent menangani share/notifikasi pada instance yang sama. Event hanya dikonsumsi sekali, yang pending disimpan saat rotasi. |
| BUG-069: ketukan notifikasi tidak punya tujuan konsisten | `AppNavigation.kt`/`AppIntents.kt` menjadi satu pemetaan action→tujuan. MainActivity/TubeFlowApp mengarahkan player/history/queue/karaoke. DownloadWorker dan KaraokeWorker memakai content intent Activity langsung; tidak melalui trampoline layanan. Retry autoplay tetap aksi layanan terpisah. |
| BUG-070: tombol secondary tampak seperti text button | `ui/components.kt` memulihkan outline yang sebelumnya null. Bersihkan di HistoryScreen/PlayerUi dan Ubah/Hapus playlist memakai secondary/destructive yang sama. |
| BUG-071: pengelolaan playlist sulit dipindai dan form terlalu cepat hilang | PersonalPlaylistDialog memakai LazyColumn, pencarian/empty state, form tambah/ubah, batal, error lokal dan aksi membungkus. MediaLibraryViewModel mengirim hasil operasi ke form; nama baru dibersihkan setelah sukses. Tidak menyalin/menghapus media. |

PlaybackService mengganti RemoteViews dengan **MediaStyle native**, artwork
terbatas256px, MediaSession metadata/durasi dan SessionActivity; kontrol sebelumnya,
putar/jeda, berikutnya dan Stop/X menuju engine yang sama. Layout
`res/layout/notification_playback.xml` dihapus karena tidak dipakai.
KaraokeViewModel menunggu pemulihan status sebelum openCurrent dan membatalkan
pembukaan UI tertunda jika pengguna berpindah; tidak membatalkan worker karaoke.
String baru di resources; status cache menyimpan ID resource agar ikut konfigurasi.

| Notifikasi/entry | Ketuk isi | Aksi terpisah |
|---|---|---|
| Musik / sesi media | Full player; pulihkan sesi paused bila diperlukan | Prev/play-pause/next/stop |
| Selesai download | Riwayat | File dibuka lewat aksi di Riwayat |
| Aktif / gagal download | Antrean | Batalkan milik WorkManager |
| Karaoke | Status/hasil karaoke | Batalkan milik WorkManager |
| Autoplay ditolak | Player | Coba putar menjalankan service |
| Share URL | Download | Launcher biasa mempertahankan layar |

Notifikasi hasil bertahan maksimal30menit atau sampai diketuk/dihapus, sehingga
sukses tidak menghilang sesudah10detik. Detail kontrol native dapat berbeda antar-OEM.
Rujukan: [task dan launch mode Android](https://developer.android.com/guide/components/activities/tasks-and-back-stack),
[MediaStyle](https://developer.android.com/reference/android/app/Notification.MediaStyle),
[navigasi notifikasi](https://developer.android.com/develop/ui/views/notifications/navigation).

## Pengujian yang diamati

| Skenario | Hasil dan batas |
|---|---|
| Cache sebelum Engine dipakai | Paket QA signed:128berkas→0, pesan berhasil tampil; ketukan kedua menyatakan kosong. Hash cooldown identik. Pada release final, pesan cache kosong juga teramati. |
| Cache ketika network tertahan | Unit latch membuktikan clear tidak menunggu load; hasil lama tidak menulis cache kembali, permintaan berikut tetap bisa mengisi cache. |
| Cache gagal/parsial | Unit tidak melaporkan direktori tak terduga sebagai bersih; cooldown dan hasil positif/offline/TTL tetap lulus. Tidak menyuntik fault storage ke HP pengguna. |
| Instance aplikasi | Sebelum perbaikan2MainActivity dalam satu task; release final hanya1. Share dan action membuka instance yang sama. |
| Notifikasi musik | Paket QA: tampilan native dengan artwork/progres/empat kontrol teramati; ketuk isi membuka full player. Pada release final, pengelola mengonfirmasi notifikasi sudah sesuai. |
| Hasil download | Release mengunduh video QA `jNQXAC9IVRw`, MP4 H264/AAC19,063583s/744412byte; full decode lulus. Pengelola mengonfirmasi perilaku notifikasi. Rute Queue/Player juga diuji dengan action Activity langsung. |
| Kelola playlist | Buat/cari/ubah/simpan/batal edit lulus pada paket QA. Satu playlist uji bertahan pada release final. Search kosong/reset serta konfirmasi hapus→Batal lulus. |
| Aksi Bersihkan | Dialog Riwayat dan antrean putar terbuka; Batal mempertahankan data. Tidak menghapus seluruh riwayat atau antrean pengguna untuk tes gaya tombol. |
| Tampilan | Dialog final gelap dan terang diperiksa, tombol outline/destruktif konsisten. Skala font HP0,9/density272 tetap; retest font besar khusus modal baru belum dilakukan. |

Paket QA sementara SHA256
`354644ac7af67e35bb7e6fba3b0984311fd17203f082fa818614eef89410112a`
memakai kunci produksi dan code34; kemudian diganti APK release di tabel.
Bukti kandidat tidak disamakan dengan final. Konfirmasi pengguna dibedakan dari
ketukan yang dilihat alat. UIAutomator drawer beberapa kali gagal mencapai idle;
satu ketukan yang mengenai notifikasi lain tidak dihitung sebagai hasil pengujian.
Tidak mengubah DNS/VPN, keamanan atau izin notifikasi untuk mengatasi alat uji.
Log/XML/screenshot privat ada di `build/reports/navigation-1120/`, tidak diikutkan Git.

## Batas dan perawatan

- Tidak mengklaim seluruh tombol lama, TalkBack, keyboard eksternal, font besar
  modal baru, Android12+/OEM lain, tablet atau runtime16KB telah diuji.
- Pemulihan player/playlist normal teramati; kill tepat saat consume intent,
  DB write gagal, storage penuh dan grant dicabut belum diinjeksi pada HP.
- Gerbang desain terfokus: build/interaksi tercatat, dua tema, peran tombol,
  state kosong/loading/error dan isi aktual diperiksa. Bukan sertifikasi seluruh
  aplikasi terhadap semua gerbang produksi atau semua aturan skill desain.
- Jangan menggabungkan kembali lock network dengan clear cache, menghapus cooldown
  untuk menghilangkan429, atau memakai hasil launcher null sebagai URI hilang.
- Tambahkan tujuan notifikasi melalui AppDestination/AppIntents dan handler yang
  sama. Hindari intent generik launcher, ACTION_VIEW file dan Activity baru per tap.


## Penutupan data uji

- Tepat62 path+SHA256 publik awal identik setelah pengujian. MP4 QA milik sesi ini
  diverifikasi hash sebelum dihapus lewat aplikasi beserta entri riwayatnya.
- Baseline playlist pribadi0; satu playlist QA dibuat/diubah, bertahan saat upgrade,
  lalu dihapus lewat konfirmasi UI. Favorit dan media pengguna tidak dihapus.
- Pengelola menggunakan pemutar selama tes dan memilih SATRU. Setelah diminta jeda,
  cleanup mempertahankan STATE_PAUSED, posisi72960ms/durasi332352ms dan antreannya.
  Tidak memaksakan kembali sesi Lamunan yang dipakai pengujian sebelumnya.
- Tema Ikut sistem dipulihkan; font0,9/density272 tidak diubah. Dialog final gelap
  dan terang diperiksa pada skala ini. Helper remote QA dihapus tepat menurut ledger.
- Log PID aplikasi final tidak memuat FATAL/ANR pada rentang yang diperiksa. Ini
  bukan bukti seluruh jalur stabilitas. Tidak memakai emulator.
