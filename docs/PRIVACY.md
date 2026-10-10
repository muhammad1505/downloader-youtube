# Privasi TubeFlow

TubeFlow tidak membutuhkan akun atau server aplikasi. URL dikirim dari perangkat langsung ke penyedia konten untuk memperoleh metadata dan media. Penyedia konten dapat melihat alamat IP serta permintaan jaringan seperti pada akses internet lainnya. Runtime yt-dlp dapat mengakses endpoint lain yang diperlukan oleh extractor.

URL, pilihan kualitas, status, dan riwayat disimpan secara lokal dalam database aplikasi. Hasil disimpan di folder Downloads/ yang namanya bisa diubah di Pengaturan (default Downloads/TubeFlow). Menghapus aplikasi menghapus database lokal, tetapi file publik Downloads dapat tetap tersimpan. Backup aplikasi dinonaktifkan.

Tidak ada analitik atau pelaporan crash jarak jauh yang ditambahkan dalam project ini. Thumbnail dimuat dari URL penyedia konten. Izin notifikasi digunakan untuk progres unduhan; izin internet digunakan untuk metadata dan unduhan.

Buka/bagikan file memberikan izin baca sementara untuk URI file kepada aplikasi penerima yang dipilih pengguna.
