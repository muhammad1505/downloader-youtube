# Pembatasan pencarian LRCLIB — 1.1.13 (27)

## Laporan dan akar masalah

Pengelola melaporkan pesan “LRCLIB membatasi permintaan; tunggu sebelum mencoba lagi” pada 1.1.12. Pesan ini mewakili HTTP429 atau penolakan lokal selama cooldown, bukan bukti koneksi internet terputus. Kelanjutan BUG-052 menemukan:

1. Discovery mendahulukan label tampilan seperti `SATRU 2 | Live` dan kredit kolaborasi lengkap. Pada contoh ini query mentah mendapat429 dan menghentikan batch sebelum variasi judul bersih/artis utama yang berhasil sempat dicoba. Ini menjelaskan kegagalan satu pencarian; penyebab penolakan internal provider belum terbukti.
2. Ketika sudah ada hasil, pencarian manual tetap mencoba seluruh variasi. Satu klik dapat menghasilkan empat pencarian.
3. `refetchLyrics` menghapus seluruh cache respons, termasuk lagu lain yang sudah ditemukan. Pencarian manual tanpa inisialisasi engine juga belum menjamin cache tersedia.
4. Pemeriksaan cooldown tidak mengunci penerimaan permintaan bersama; belum ada jeda antar-request. Pencarian berbeda dapat lolos bersamaan ketika cache belum aktif.
5. `Retry-After` hanya dibaca sebagai angka, dipotong maksimal300detik, dan tidak dipertahankan ketika proses mati. Tidak ada backoff bertahap setelah penolakan berulang.
6. Dialog tidak memberi hitung mundur; galat provider dapat disertai pesan “Tidak ada hasil”, seolah penyedia tidak memiliki lirik. Coroutine pencarian yang dibatalkan belum menginterupsi loop sinkron.

Satu GET host ke pencarian SATRU2/DennyCaknan pada 16Oktober mengembalikan200. Pada HP RealmeAndroid10 dengan1.1.12, membuka Cari lirik pada SATRU2Live mereproduksi pesan pembatasan sekaligus “Tidak ada hasil”. Pengelola mengonfirmasi baru mencoba satu lagu. Probe lanjutan dengan parameter mentah yang sama persis juga mengembalikan429/Retry-After1 di host. Parameter yang dinormalisasi menjadi SATRU2/DENNYCAKNAN mengembalikan200,17record/14dengan syncedLyrics. Query berbeda ini tidak membuktikan perbedaan IP. Pengelola memakai DNS; tidak ada perubahan jaringan/DNS/VPN untuk menguji atau menghindari pembatasan. Jangan menyalahkan koneksi pengguna atau menjamin429 hilang setelah perubahan aplikasi.

## Perubahan

- `LyricsRequestGate.kt`: satu gerbang jaringan, jarak awal permintaan minimal1detik (kebijakan aplikasi, bukan klaim kuota resmi LRCLIB), dan backoff5/10/20/40/80/160/300detik. `Retry-After` numerik atau HTTP-date yang lebih lama tetap dihormati. Tanggal respons server dipakai jika tersedia untuk mengurangi pengaruh jam HP berbeda.
- Cooldown dan jumlah penolakan disimpan ke `noBackupFilesDir/lyrics-cooldown.json`, tanpa judul, URL atau lirik. Cache tidak dapat menghapus cooldown. Respons200/404 jaringan mereset backoff; hit cache tidak membatalkan pembatasan server.
- `Lyrics.kt`: discovery mendahulukan judul bersih dan artis utama, sambil mempertahankan identitas rekaman asli untuk validasi. Manual berhenti ketika sudah ada pilihan LRC; variasi tambahan diminta melalui “Cari lebih banyak hasil”. Fallback identitas berhenti saat429. Gerbang identitas/versi/durasi±2detik tetap berlaku untuk penerimaan otomatis.
- `LyricsResponseCache.kt`: cari ulang menyegarkan404/arraykosong secara terarah, mempertahankan hasil positif. Hasil kosong kedaluwarsa1jam; hasil positif24jam. Tidak ada cache negatif untuk429/jaringan/5xx.
- `Engine.kt`, `MediaLibraryViewModel.kt`, `DownloadViewModel.kt`: inisialisasi cache dan cooldown pada jalur download maupun pencarian manual; hapus invalidasi cache global saat refetch.
- `DownloadWorker.kt` dan kedua ViewModel: pencarian sinkron melalui `runInterruptible` di IO. Thread terinterupsi tidak mengirim request berikutnya; koneksi yang sedang membaca tetap memiliki timeout8detik.
- `LyricsSearchDialog.kt`/`strings.xml`: hitung mundur, tombol pencarian dinonaktifkan selama cooldown, aksi perluasan hasil, serta pemisahan galat dari status tidak ditemukan. Setelah waktu lokal lewat, pengguna mencoba kembali; tidak ada loop request otomatis.
- Versi1.1.13/code27; Room9, database, format media dan dependensi tidak berubah.

## Verifikasi

Checkpoint awal: **46tes lirik lulus** (`LyricsFetchTest`37, `LyricsResponseCacheTest`3, `LyricsRequestGateTest`6). Regresi tambahan memastikan query judul bersih/artis utama menemukan pilihan tanpa mencoba query mentah yang ditolak; kandidat studio tetap tidak diterima otomatis untuk audio live. Suite final **271tes Kotlin** lulus, termasuk47tes lirik. **84tes Python lulus** (33,934detik). Fixture menguji pembatasan bersama, Retry-After600/900detik dan HTTP-date, persistensi/expiry, backoff berulang, pembatalan saat menunggu, retry yang kemudian menemukan lirik, cache saat dibatasi, serta perluasan hasil sesuai tindakan pengguna.

Build signed lengkap berhasil dalam9menit18detik: `bash scripts/build_release.sh --no-daemon --max-workers=2` menjalankan271tes Kotlin, lint debug/release, APK release tigaABI dan AAB. Lint kedua varian0error/2warning lama (`SelectedPhotoAccess`, pin ONNXRuntime); tidak ada warning baru. Audit native, konstruktor ZIP reflektif, tanda tangan/certificate, alignment16KB, serta checksum engine/bootstrap APK×3 lulus. AAB lulus jarsigner/native audit; bundletool tidak dijalankan ulang untuk kandidat ini. Tidak ada pengujian yang membanjiri LRCLIB untuk sengaja memicu429. Tidak ada bypass IP, endpoint tersembunyi, atau penyedia lirik baru.

## Hasil HP fisik

RealmeRMX1821, Android10/API29, ARM64, RAM4GB, halaman memori4096byte. Update `adb install -r` sukses, tanpa uninstall/reset data. Hash `base.apk` yang ditarik dari HP sama dengan ARM64 pada tabel artefak. Satu audio di TubeFlow tetap memiliki SHA256 yang sama sebelum update dan setelah seluruh tes pencarian.

| Skenario | Hasil aktual |
| --- | --- |
| Baseline1.1.12, Cari lirik SATRU2Live | Pembatasan LRCLIB disertai “Tidak ada hasil” |
|1.1.13, buka Cari lirik untuk audio yang sama |17kandidat tampil tanpa galat; input tetap memuat judul versi/kredit asli |
| Pilih “Cari lebih banyak hasil” sekali |24kandidat tampil; provider tidak membatasi percobaan ini |
| Keutuhan file sumber |1file audio, hash tetap sama; tidak memilih/menyimpan LRC atau mengunduh ulang audio |
| Galat fatal proses aplikasi | Tidak ada `FATAL EXCEPTION` pada buffer PID yang diperiksa setelah tes |
| Countdown429 dan persistensi restart | Lulus fixture host; tidak teramati lagi pada HP setelah update, tidak memaksa provider memberi429 |

Kandidat tersedia **bukan** bukti timestamp cocok dengan rekaman live6:34. Tidak ada verifikasi auditori, penggantian LRC, download audio/video baru, gangguan jaringan nyata, atau pengujian perangkat16KB dalam sesi ini. Pengujian UI di HP kini dapat dijalankan; hambatan Norton pada sesi1.1.12 sebelumnya tidak muncul, dan pengaturan keamanan tidak diubah. Bukti screenshot/XML/log privat disimpan di `build/reports/lyrics-1113/`; screenshot berstatus bar notifikasi tidak disalin ke dokumentasi publik.

## Batas dan langkah uji HP

1. Cari lirik sebuah audio di Koleksi; periksa pilihan dan perluasan hasil. Cari lagi lagu yang sama untuk memeriksa respons cache.
2. Ketika provider benar-benar membatasi, periksa countdown/tombol, tutup-buka dialog/aplikasi dan coba kembali setelah jeda. Kegagalan berulang harus memperpanjang backoff tanpa menghapus audio/LRC lama.
3. Uji pencarian otomatis, cari ulang tanpa download audio, jaringan putus, dan pembatalan dialog/worker. Berhasil mengambil lirik belum membuktikan sinkronisasi auditori rekaman.
4. Jika429 tetap terjadi pada pencarian pertama setelah masa tunggu, catat keadaan jaringan serta respons aktual. Jeda aplikasi tidak menjamin kapasitas provider atau kuota IP bersama sudah pulih.

## Artefak terverifikasi

APK memakai keystore produksi yang sama, non-debuggable, code27/Room9. Tidak ada dependensi baru. ARM64 bertambah5.096byte terhadap1.1.12; belum ada benchmark performa runtime. Tidak ada publikasi toko atau salinan distribusi tambahan.

| Artefak | Byte | SHA256 |
| --- | ---: | --- |
| APK arm64-v8a | 70578919 | `9ed1d31e2abde344e30f1613da238bc7fc008a50424762e22cfbdbc68c9a6326` |
| APK armeabi-v7a | 62269377 | `8deda16d8ca1b4a94a8588f5a50ae6410d2ea80a311d7bf7bc6ee8a1a795a379` |
| APK x86_64 | 75019728 | `37fcf145813dd437700a9927bc5c2ce743fdbbffce820135e27e577dc90b1497` |
| AAB | 255610155 | `4ea1100812ebae8bacb7632747b2af0263c5e07bf3f95d3742f28cb1c2de81ea` |
