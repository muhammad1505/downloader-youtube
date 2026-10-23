# Privasi TubeFlow

TubeFlow tidak membutuhkan akun atau server aplikasi. URL dikirim dari perangkat langsung ke penyedia konten untuk memperoleh metadata dan media. Penyedia konten dapat melihat alamat IP serta permintaan jaringan seperti pada akses internet lainnya. Runtime yt-dlp dapat mengakses endpoint lain yang diperlukan oleh extractor. Saat pengguna menekan **Periksa pembaruan** di Pengaturan → Mesin media, aplikasi menghubungi API resmi GitHub dan mengunduh metadata/rilis yt-dlp; URL unduhan, riwayat, dan data media tidak dikirim. Pemeriksaan tidak berjalan otomatis.

URL, pilihan kualitas, status, dan riwayat disimpan secara lokal dalam database aplikasi. Hasil disimpan di folder Downloads/ yang namanya bisa diubah di Pengaturan (default Downloads/TubeFlow). Menghapus aplikasi menghapus database lokal, tetapi file publik Downloads dapat tetap tersimpan. Backup aplikasi dinonaktifkan.

Tidak ada analitik atau pelaporan crash jarak jauh yang ditambahkan dalam project ini. Thumbnail dimuat dari URL penyedia konten dan dapat dicache sebagai JPEG di penyimpanan internal aplikasi. Artwork riwayat lama dapat dibaca dari audio lokal. Identitas judul/artis/creator/versi/platform serta referensi thumbnail disimpan bersama job.

Pencarian lirik dan validasi identitas artis yang belum pasti dapat mengirim judul lagu, kandidat nama artis dan durasi ke LRCLIB (`lrclib.net`), termasuk saat identifikasi untuk penamaan/Auto Folder. Nama uploader tidak otomatis dipakai sebagai artis. Penyedia ini dapat melihat IP dan parameter permintaan; tidak ada akun TubeFlow yang dikirim. Izin notifikasi digunakan untuk progres unduhan; izin internet digunakan untuk metadata dan unduhan.

Buka/bagikan file memberikan izin baca sementara untuk URI file kepada aplikasi penerima yang dipilih pengguna.


## Smart Media dan koreksi lirik

Katalog media dan event aktivitas berada pada database Room yang sama di perangkat. Impor hanya menyimpan URI/grant dan metadata, tanpa menyalin file atau meminta akses semua penyimpanan. Event tidak menggandakan URL/token sumber. Pemindaian manual dibatasi folder yang diizinkan; statistik memakai indeks aktual dan bisa parsial ketika izin/provider berubah.

Cache respons LRCLIB dibatasi10MiB/200entry, cover32MiB dan frame/artwork katalog24MiB. Metadata hasil inspeksi (URL, judul, durasi, format/subtitle) disimpan privat maksimal30menit/200entri dan dihitung sebagai metadata; cache tidak diunggah dan dipangkas otomatis. Impor **LRC manual** menyimpan teks yang ditempel pengguna sebagai sidecar di samping audio, menggantikan pilihan lirik sebelumnya hanya setelah validasi berhasil. Koreksi LRC menyimpan baseline/jurnal privat (batas32MiB) untuk reset/rollback; audio dan timestamp rekaman lain tidak diubah. Playback audio dapat menggunakan pemutar lokal TubeFlow atau aplikasi pemutar perangkat. Video tetap memakai pemutar perangkat.

## Pemutaran dan interupsi audio

Pemutar memakai audio focus dan notifikasi perubahan aktivitas audio Android untuk menjeda musik ketika audio lain dimulai dan dapat melanjutkannya setelah interupsi selesai. Pemeriksaan hanya memakai status aktif/tidak aktif serta mode audio; tidak merekam suara atau membaca layar/isi aplikasi lain. Jeda manual, menutup pemutar atau melepas headset membatalkan niat lanjut. Niat ini hanya tersimpan di RAM untuk track aktif dan tidak dipulihkan setelah proses mati.

## Karaoke opsional

Saat pengguna meminta pemisahan vokal, model UVR sekitar67MB diunduh dari GitHub Releases (termasuk CDN pengiriman GitHub) dan diverifikasi SHA256. Penyedia unduhan melihat IP/request model; rekaman, judul lagu dan lirik tidak diunggah untuk proses karaoke. Model tersimpan privat dan tidak ada server TubeFlow. ONNX Runtime bekerja di HP, terpisah dari downloader biasa.

Pemrosesan menyalin sumber sementara ke penyimpanan privat, membaca PCM per potongan dan menyiapkan FLAC instrumental. Sumber asli tidak diubah. Input/PCM dibersihkan setelah berhasil atau batal; pratinjau terakhir dipertahankan sampai proses baru menggantikannya. Proses yang dihentikan sistem dapat memulai ulang dengan aman; karaoke belum melanjutkan inference dari potongan terakhir. Model dan pratinjau hilang saat uninstall/reset data; file publik yang sudah disimpan dapat tetap ada.

Simpan hasil hanya dilakukan setelah pengguna memilihnya. Hasil masuk katalog media sebagai file turunan, bukan unduhan jaringan baru. LRC disalin terpisah hanya untuk hasil lagu penuh; cuplikan uji tidak memakai lirik lagu penuh. Tidak ada izin akses seluruh penyimpanan baru.

### Auto-play headset dan Bluetooth

Auto-play hanya menanggapi sambungan perangkat audio. Pada Android12+, izin
`BLUETOOTH_CONNECT` diminta melalui tombol di Pengaturan untuk menerima peristiwa
sambungan TWS/Bluetooth ketika UI tertutup; headset kabel tidak membutuhkan izin
ini. Android10–11 memakai izin Bluetooth normal. Aplikasi tidak meminta izin
pemindaian Bluetooth/lokasi untuk fitur ini dan tidak mengunggah informasi
perangkat. Penanda sesi player disimpan lokal agar sesi aktif dapat dipulihkan
setelah proses dihentikan sistem; tombol X mengakhiri sesi tersebut.


## Snapshot player dan detail kegagalan

Antrean/track/URI lokal/posisi disimpan privat di noBackupFilesDir, maksimal3000
entri/4MiB ditambah checkpoint posisi kecil; tombol X menutup sesi. Pemulihan
proses dibuka jeda; sambungan headset baru tetap memiliki pemicu auto-play sendiri.
Preferensi lanjut setelah interupsi dapat dimatikan dan membatalkan niat tertunda.
Snapshot tidak diunggah; parameter pencarian lirik tetap mengikuti penjelasan LRCLIB.

Diagnostik kegagalan tersimpan lokal, dibatasi80entry/1MiB, berisi versi, tahap,
waktu yang tercatat, status/kategori dan HTTP bila tersedia. URL, header kredensial,
pola token dan path lokal disamarkan sebelum disimpan/disalin. Histori yang tidak
mencatat suatu nilai tidak direkonstruksi dengan tebakan. Pengguna meninjau dan
menyalin sendiri; tidak ada pengiriman laporan otomatis. Status lirik/cooldown
terpisah dari kegagalan media dan tidak berarti ada respons HTTP baru.

Ukuran cache yang ditampilkan mencakup respons lirik, metadata extractor, covers
dan library-covers; angka terpisah dari data sementara downloads dan media Koleksi.
Bersihkan cache lirik tidak mengosongkan cooldown penyedia. Hapus sementara hanya
melalui konfirmasi; worker aktif, model karaoke, baseline/jurnal LRC dan output
media tidak dihapus oleh aksi tersebut.
