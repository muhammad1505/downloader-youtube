# Playbook stabilitas TubeFlow

Daftar isi: Cara memakai · Skenario gangguan · Kebiasaan kode yang menjaga stabilitas · Pengamatan dan diagnosis · Mencatat hasil

## Cara memakai

Aplikasi downloader gagal di tempat yang tidak terlihat saat pengembangan: proses dimatikan sistem di tengah download, jaringan berpindah, folder tujuan dicabut, atau yt-dlp berubah perilaku karena situs berubah. Playbook ini mengubah kekhawatiran itu menjadi skenario yang dijalankan satu per satu dan dicatat hasilnya. Pilih skenario yang berhubungan dengan perubahan; untuk audit kesiapan, jalankan semuanya.

Untuk setiap skenario catat: kondisi awal, langkah, hasil yang diharapkan, hasil aktual, perangkat/versi Android, versi aplikasi. Hasil yang tidak dijalankan ditulis "belum diuji", bukan dilewati.

## Skenario gangguan

Hasil yang diharapkan secara umum: tidak ada crash/ANR, tidak ada data hilang, status di UI sesuai kenyataan, pengguna tahu langkah berikutnya, dan tidak ada file setengah jadi yang tampak sebagai selesai.

**Siklus hidup proses**
- Status WorkManager CANCELLED muncul sementara eksekusi lama masih berjalan: eksekusi lama tidak menyentuh status atau file milik eksekusi baru (guard `generation`).
- Proses dimatikan sistem (mis. `adb shell am kill`) atau force-stop saat download berjalan: setelah dibuka lagi, status antrean benar (dilanjutkan, dapat dilanjutkan, atau gagal dengan alasan), tanpa item "berjalan" yang menggantung.
- Rotasi, perubahan tema/bahasa/ukuran font saat dialog atau layar progres terbuka: state bertahan.
- Aplikasi di latar belakang lama, mode hemat daya/Doze, optimasi baterai aktif: download tetap berjalan atau berhenti dengan status jujur, dan layanan foreground memakai tipe yang sesuai dengan aturan target SDK aktif (verifikasi batas waktu dan izin pada dokumentasi Android).
- Izin notifikasi ditolak: download tetap berjalan dan pengguna masih bisa melihat progres di aplikasi.

**Jaringan**
- Putus total di tengah download lalu pulih; berpindah Wi-Fi ke data seluler; jaringan sangat lambat; DNS gagal: item kembali QUEUED dengan berkas parsial utuh, atau gagal dengan galat yang jelas. Tidak ada loop tak berujung dan tidak ada spam notifikasi.
- Inspeksi URL gagal: pengguna dapat mencoba URL yang sama lagi secara manual, tanpa loop retry otomatis.
- Jeda atau gagal lalu "Coba lagi": berkas parsial dipakai hanya bila jurnal identitas sumber/format dan validator sumber cocok; bila tidak, unduhan dimulai bersih.

**Penyimpanan dan SAF/MediaStore**
- Penyimpanan hampir penuh sebelum dan saat download/merge: gagal dengan pesan jelas, file sementara dibersihkan.
- Izin folder SAF dicabut atau folder dihapus/dipindah saat antrean berisi item: galat yang menyarankan memilih folder lagi, tanpa kehilangan item antrean.
- Nama file bertabrakan, mengandung karakter ilegal, atau sangat panjang; judul beremoji atau bukan Latin: nama aman dan tidak menimpa file lain.
- File hasil dihapus pengguna dari luar aplikasi: riwayat tidak menampilkan status "selesai" palsu, dan penghapusan URI mengikuti helper bersama. Penghapusan dari dalam Koleksi (`MediaRepository.delete()`) menghapus file sekaligus baris database (`dao.remove`), tanpa menyisakan baris hantu berstatus `UNAVAILABLE`.
- Hapus data aplikasi / instalasi ulang di Android 10+ saat folder `Download/TubeFlow` sudah berisi file dari instalasi sebelumnya (`owner_package_name = NULL`): auto-scan saat Koleksi kosong (`scanIfEmpty`) dan union MediaStore + grant SAF memulihkan daftar Koleksi dan ukuran folder dengan jujur.
- Interupsi setelah download selesai tetapi sebelum publikasi/merge/penandaan metadata: pemulihan menghasilkan file utuh atau status gagal, bukan file rusak yang ditandai selesai. URI media/sidecar yang sedang dibuat tetap tercatat sampai publikasi berhasil atau penghapusan terbukti selesai.
- Ukuran folder dan "Hapus semua file" saat pembacaan folder gagal atau batas traversal tercapai: hasil tidak dilaporkan nol atau bersih, folder induk tidak dihapus, dan hanya root yang dipilih yang disentuh.
- `.lrc` mengikuti nama aktual media yang diterbitkan provider, termasuk suffix tabrakan nama.

**Engine yt-dlp dan Python**
- Video privat, dibatasi usia, diblokir wilayah, dihapus, live/premiere, playlist berisi item tak tersedia: kategori galat yang dikenal, hasil sebagian pada playlist, dan pesan yang menjelaskan.
- Format yang diminta tidak tersedia: fallback yang transparan atau pilihan ulang, bukan galat umum.
- Engine gagal karena situs berubah: pesan yang mengarahkan pada pembaruan engine bila ada jalurnya, dan aplikasi tetap dapat dipakai untuk fungsi lain.
- Pembatalan di tengah ekstraksi atau merge: proses anak dihentikan lewat identitas proses/session/token (bukan kill global Python atau FFmpeg), tidak ada proses yatim, dan file sementara dibersihkan setelah pemiliknya berhenti.

**Data dan upgrade**
- Upgrade dari versi sebelumnya yang berisi riwayat, antrean, dan pengaturan nyata: data utuh setelah migrasi Room. Jangan menguji upgrade hanya pada instalasi bersih.
- Pemasangan ulang di atas versi lama dengan tanda tangan yang sama; perilaku bila tanda tangan berbeda dicatat.
- Playlist sangat besar (ratusan item): memori stabil, daftar tetap mulus, tidak ada ANR saat menambah ke antrean.

**Player dan karaoke**
- Pemutaran di latar lewat `PlaybackService` dan `PlayerViewModel.obtain()` ber-scope proses: menutup Activity tidak mematikan musik; notifikasi (`ic_stat_playback`, sampul, progress bar dengan durasi/reset posisi tiap lagu) dan tombol headset (play/pause/prev/next/stop) mengendalikan player yang sama; tidak ada player kedua. Efek equalizer (termasuk preset bawaan `AutoEq` / Otomatis bass bulat) dan kecepatan putar tidak membuat crash di perangkat yang tidak mendukungnya.
- Auto-play saat perangkat audio tersambung: saat aplikasi terbuka/di latar (`AudioDeviceCallback`) maupun saat proses mati lalu TWS Bluetooth A2DP tersambung (`HeadsetAutoPlayReceiver`), pemutaran dilanjutkan dengan antrean berisi **seluruh koleksi audio yang tersedia** (`AutoPlayQueue.plan()`) mulai dari lagu terakhir tanpa me-reload lagu yang sedang aktif. Catat bahwa `ACTION_HEADSET_PLUG` kabel tidak membangunkan proses mati karena pembatasan broadcast sistem Android.
- Antrean Koleksi vs Riwayat: ketuk lagu di Koleksi memuat seluruh daftar Koleksi ke antrean, sedangkan ketuk lagu di Riwayat hanya memutar item terkait tanpa membanjiri antrean.
- Berpindah lagu atau menggeser pager Sampul↔Lirik dengan cepat: callback track lama tidak mengubah track baru (token track), dan geseran manual pengguna pada `HorizontalPager` tidak ditimpa auto-switch lirik.
- Penggantian lirik atau penyesuaian waktu lewat tombol **Cari lirik** / **Editor lirik** langsung dari pemutar penuh: kegagalan simpan mempertahankan lirik lama; cari ulang lirik memakai lokasi audio yang sudah terbit.
- Job karaoke dibatalkan, dijalankan ulang, atau pengguna berganti lagu saat dialog karaoke dibuka: judul dialog mengikuti lagu terpilih dan memberi label sumber pada proses lama; direktori job lama tidak dihapus atau ditimpa job baru sebelum pembersihan berhenti; LRC lagu penuh disalin tersendiri; batas RAM/durasi pada HP dicatat.

**Konkurensi**
- Batal dan selesai hampir bersamaan, dua item dengan URL yang sama, tambah banyak item cepat, jeda/lanjut berulang: status akhir konsisten dan tidak ada duplikasi atau file yatim.

## Kebiasaan kode yang menjaga stabilitas

- Main thread hanya untuk UI. Aktifkan StrictMode pada build debug saat menyelidiki jank atau ANR.
- Hindari `GlobalScope`; ikat coroutine pada scope dengan siklus hidup yang jelas, dan jangan menelan `CancellationException`.
- Pekerjaan lama yang harus selamat dari penutupan aplikasi berjalan lewat WorkManager/layanan foreground dengan idempotensi: menjalankan ulang pekerjaan yang sama tidak merusak hasil.
- Transisi status download bersifat eksplisit dan divalidasi (dari status apa ke status apa yang sah); tulis ke database secara atomik untuk perubahan yang saling terkait.
- Batas Python-Kotlin: semua pengecualian dari engine ditangkap di perbatasan dan dipetakan ke kategori galat; akses bersama ke state engine dilindungi.
- Sumber daya (stream, cursor, file descriptor, proses anak) ditutup pada semua jalur, termasuk jalur gagal dan batal.
- Gambar sampul di-downsample dan di-cache; daftar besar tidak memuat bitmap penuh.
- Retry memiliki batas, backoff, dan hanya untuk galat yang memang sementara.

## Pengamatan dan diagnosis

- Log bermakna dan terstruktur (kategori, ID tugas, tahap), tanpa data pribadi, token, atau URL bertanda sensitif; log dapat diekspor pengguna untuk laporan bug.
- Gunakan `adb logcat`, `dumpsys` (jobscheduler, activity, meminfo, gfxinfo) untuk bukti, dan simpan potongan yang relevan pada laporan.
- Untuk dugaan race, deskripsikan urutan kejadian yang mungkin dan buktikan dengan tes yang memaksa urutan itu; bila tidak bisa dibuktikan aman, temuan tetap terbuka.

## Aturan keselamatan saat menguji di HP

Ikuti langkah pada [validation-release.md](validation-release.md). Ringkasnya: uji pada HP fisik, gunakan konten yang diizinkan, jangan force-stop atau restart saat ada job aktif di luar skenario, jangan memindahkan atau menghapus file pengguna (bandingkan ukuran/jumlah file sebelum dan sesudah), dan fokuskan logcat pada PID aplikasi tanpa membagikan nama file pribadi atau serial perangkat. Pemasangan, uninstall dan penghapusan hanya dengan otorisasi dalam tugas.

## Mencatat hasil

Tambahkan ke laporan pengujian perangkat tabel ringkas: skenario, status (Lulus, Gagal, Belum diuji), perangkat dan versi Android, versi/hash aplikasi, dan catatan. Kegagalan baru masuk ke BUG_FINDINGS dengan ID, titik kode, dan checklist.
