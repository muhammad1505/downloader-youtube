# Perbaikan bertahap TubeFlow 1.1.15

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
