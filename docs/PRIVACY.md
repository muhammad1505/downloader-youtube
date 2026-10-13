# Privasi TubeFlow

TubeFlow tidak membutuhkan akun atau server aplikasi. URL dikirim dari perangkat langsung ke penyedia konten untuk memperoleh metadata dan media. Penyedia konten dapat melihat alamat IP serta permintaan jaringan seperti pada akses internet lainnya. Runtime yt-dlp dapat mengakses endpoint lain yang diperlukan oleh extractor.

URL, pilihan kualitas, status, dan riwayat disimpan secara lokal dalam database aplikasi. Hasil disimpan di folder Downloads/ yang namanya bisa diubah di Pengaturan (default Downloads/TubeFlow). Menghapus aplikasi menghapus database lokal, tetapi file publik Downloads dapat tetap tersimpan. Backup aplikasi dinonaktifkan.

Tidak ada analitik atau pelaporan crash jarak jauh yang ditambahkan dalam project ini. Thumbnail dimuat dari URL penyedia konten dan dapat dicache sebagai JPEG di penyimpanan internal aplikasi. Artwork riwayat lama dapat dibaca dari audio lokal. Identitas judul/artis/creator/versi/platform serta referensi thumbnail disimpan bersama job.

Pencarian lirik dan validasi identitas artis yang belum pasti dapat mengirim judul lagu, kandidat nama artis dan durasi ke LRCLIB (`lrclib.net`), termasuk saat identifikasi untuk penamaan/Auto Folder. Nama uploader tidak otomatis dipakai sebagai artis. Penyedia ini dapat melihat IP dan parameter permintaan; tidak ada akun TubeFlow yang dikirim. Izin notifikasi digunakan untuk progres unduhan; izin internet digunakan untuk metadata dan unduhan.

Buka/bagikan file memberikan izin baca sementara untuk URI file kepada aplikasi penerima yang dipilih pengguna.


## Smart Media dan koreksi lirik

Katalog media dan event aktivitas berada pada database Room yang sama di perangkat. Impor hanya menyimpan URI/grant dan metadata, tanpa menyalin file atau meminta akses semua penyimpanan. Event tidak menggandakan URL/token sumber. Pemindaian manual dibatasi folder yang diizinkan; statistik memakai indeks aktual dan bisa parsial ketika izin/provider berubah.

Cache respons LRCLIB dibatasi10MiB/200entry, cover32MiB dan frame/artwork katalog24MiB. Koreksi LRC menyimpan baseline/jurnal privat (batas32MiB) untuk reset/rollback; audio dan timestamp rekaman lain tidak diubah. Playback menggunakan aplikasi pemutar yang dipilih pengguna. Tidak ada model AI, server baru, atau layanan metadata baru pada upgrade ini.
