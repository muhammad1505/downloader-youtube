# Karaoke tanpa vokal — kajian awal TubeFlow

Permintaan pengelola: tombol Karaoke menghasilkan instrumental bersih. Fitur L/R pada 1.1.5 hanya mengatur volume kanal sumber dan tidak memenuhi pemisahan vokal dari campuran stereo biasa.

## Pilihan teknis

| Pendekatan | Hasil dan batas |
| --- | --- |
| Sumber instrumental asli | Tidak perlu memisahkan vokal jika rekaman memang tanpa vokal. Identitas lagu, versi dan hak akses sumber harus diverifikasi; tidak menebak URL atau menganggap semua lagu punya versi instrumental. |
| L/R / penghilangan kanal tengah | Hanya berguna pada pemisahan kanal tertentu. Bukan jaminan tanpa vokal, dan komponen musik juga bisa hilang. Tidak diberi label karaoke bersih. |
| Pemisahan sumber berbasis model | Menghasilkan stem vokal dan pengiring, tetapi memerlukan proses, model dan evaluasi kualitas. Kebocoran vokal/artefak masih mungkin; belum ada benchmark Android 10/RAM4GB di proyek ini. |

[Demucs](https://github.com/facebookresearch/demucs/blob/main/README.md) menyediakan dua keluaran vokal/non-vokal. Mode dua stem tetap memproses pemisahan lengkap, sehingga bukan otomatis lebih ringan. Implementasi referensinya menggunakan Python/PyTorch; lisensi kode MIT tidak menggantikan pemeriksaan lisensi tiap model. [Spleeter](https://github.com/deezer/spleeter) adalah alternatif Python/TensorFlow dengan model dua stem; integrasi Android tetap perlu dibuktikan.

## Rancangan yang perlu divalidasi

Karaoke AI harus opsional dan tidak menjadi dependensi downloader, player atau upgrade lirik. Jangan memasukkan model besar ke APK atau menambahkan server wajib. Evaluasi model mobile yang didownload terpisah hanya setelah format, operator runtime, lisensi, checksum dan lokasi unduh terverifikasi. Pembuktian host tidak membuktikan dukungan Android.

Alur: pengguna memilih audio selesai → proses instrumental pada antrean terpisah → preview → simpan hasil turunan setelah dipilih. Audio asli dipertahankan. Gunakan ukuran/durasi/MIME aktual dan asal media untuk indeks; jangan menghitung proses ini sebagai download jaringan baru. Jangan mengunggah audio ke layanan luar tanpa instruksi pengguna.

Checkpoint sebelum tombol tersedia: prototipe satu model pada HP target; ukur RAM puncak, waktu, panas/baterai dan penyimpanan sementara; uji stereo/mono, duet/live/reverb, cancel, restart dan storage rendah. Dengarkan intro, vokal utama, backing vocal, chorus dan outro. Tetapkan batas kualitas dari hasil nyata; tidak menjanjikan vokal hilang 100% pada semua rekaman.

Status: **kajian, belum diimplementasikan**. Tidak ada model/runtime baru, server, upload audio, atau perubahan file media untuk eksperimen AI pada sesi ini. L/R 1.1.5 tetap kontrol kanal sumber. Jangan menambahkan tombol placeholder sebelum engine dan hasilnya siap diuji.
