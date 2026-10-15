# Privasi TubeFlow

TubeFlow tidak membutuhkan akun atau server aplikasi. URL dikirim dari perangkat langsung ke penyedia konten untuk memperoleh metadata dan media. Penyedia konten dapat melihat alamat IP serta permintaan jaringan seperti pada akses internet lainnya. Runtime yt-dlp dapat mengakses endpoint lain yang diperlukan oleh extractor.

URL, pilihan kualitas, status, dan riwayat disimpan secara lokal dalam database aplikasi. Hasil disimpan di folder Downloads/ yang namanya bisa diubah di Pengaturan (default Downloads/TubeFlow). Menghapus aplikasi menghapus database lokal, tetapi file publik Downloads dapat tetap tersimpan. Backup aplikasi dinonaktifkan.

Tidak ada analitik atau pelaporan crash jarak jauh yang ditambahkan dalam project ini. Thumbnail dimuat dari URL penyedia konten dan dapat dicache sebagai JPEG di penyimpanan internal aplikasi. Artwork riwayat lama dapat dibaca dari audio lokal. Identitas judul/artis/creator/versi/platform serta referensi thumbnail disimpan bersama job.

Pencarian lirik dan validasi identitas artis yang belum pasti dapat mengirim judul lagu, kandidat nama artis dan durasi ke LRCLIB (`lrclib.net`), termasuk saat identifikasi untuk penamaan/Auto Folder. Nama uploader tidak otomatis dipakai sebagai artis. Penyedia ini dapat melihat IP dan parameter permintaan; tidak ada akun TubeFlow yang dikirim. Izin notifikasi digunakan untuk progres unduhan; izin internet digunakan untuk metadata dan unduhan.

Buka/bagikan file memberikan izin baca sementara untuk URI file kepada aplikasi penerima yang dipilih pengguna.


## Smart Media dan koreksi lirik

Katalog media dan event aktivitas berada pada database Room yang sama di perangkat. Impor hanya menyimpan URI/grant dan metadata, tanpa menyalin file atau meminta akses semua penyimpanan. Event tidak menggandakan URL/token sumber. Pemindaian manual dibatasi folder yang diizinkan; statistik memakai indeks aktual dan bisa parsial ketika izin/provider berubah.

Cache respons LRCLIB dibatasi10MiB/200entry, cover32MiB dan frame/artwork katalog24MiB. Koreksi LRC menyimpan baseline/jurnal privat (batas32MiB) untuk reset/rollback; audio dan timestamp rekaman lain tidak diubah. Playback audio dapat menggunakan pemutar lokal TubeFlow atau aplikasi pemutar perangkat. Video tetap memakai pemutar perangkat.

## Karaoke opsional

Saat pengguna meminta pemisahan vokal, model UVR sekitar67MB diunduh dari GitHub Releases (termasuk CDN pengiriman GitHub) dan diverifikasi SHA256. Penyedia unduhan melihat IP/request model; rekaman, judul lagu dan lirik tidak diunggah untuk proses karaoke. Model tersimpan privat dan tidak ada server TubeFlow. ONNX Runtime bekerja di HP, terpisah dari downloader biasa.

Pemrosesan menyalin sumber sementara ke penyimpanan privat, membaca PCM per potongan dan menyiapkan FLAC instrumental. Sumber asli tidak diubah. Input/PCM dibersihkan setelah berhasil atau batal; pratinjau terakhir dipertahankan sampai proses baru menggantikannya. Proses yang dihentikan sistem dapat memulai ulang dengan aman; karaoke belum melanjutkan inference dari potongan terakhir. Model dan pratinjau hilang saat uninstall/reset data; file publik yang sudah disimpan dapat tetap ada.

Simpan hasil hanya dilakukan setelah pengguna memilihnya. Hasil masuk katalog media sebagai file turunan, bukan unduhan jaringan baru. LRC disalin terpisah hanya untuk hasil lagu penuh; cuplikan uji tidak memakai lirik lagu penuh. Tidak ada izin akses seluruh penyimpanan baru.
