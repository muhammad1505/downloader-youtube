# Sistem desain TubeFlow: elegan, premium, profesional

Daftar isi: Prinsip · Proses desain · Token · Status antarmuka · Teks antarmuka · Gerak · Platform Android · Aksesibilitas · Bukti visual · Hal yang dihindari

## Prinsip

Tampilan premium pada aplikasi utilitas seperti TubeFlow lahir dari ketenangan dan konsistensi. Pengguna datang untuk satu tugas (tempel tautan, pilih opsi, tunggu, temukan file), jadi desain yang baik membuat tugas itu terasa ringan dan terkendali.

- Pilih satu momen yang diingat (misalnya kartu progres download atau layar hasil), lalu buat sisanya tenang dan disiplin. Boros aksen di semua tempat membuat aplikasi terlihat ramai, bukan mewah.
- Hierarki harus terbaca dalam sekilas: satu aksi utama per layar, informasi sekunder lebih redup, aksi berisiko dipisahkan.
- Struktur visual (garis, kartu, pembatas, penomoran) hanya dipakai bila menyampaikan informasi. Penomoran 1/2/3 hanya untuk urutan yang memang berurutan.
- Perluas tema yang sudah ada. Baca file tema (warna, tipografi, bentuk) dan komponen bersama dulu; jangan memperkenalkan palet atau font baru tanpa alasan dan persetujuan.

## Proses desain

Untuk setiap pekerjaan visual yang bukan perbaikan kecil, kerjakan dua tahap.

1. Rencana singkat: palet 4 sampai 6 warna bernama (hex), peran tipografi, ritme spasi, aturan bentuk, dan sketsa tata letak ASCII untuk layar yang dikerjakan.
2. Kritik rencana sebelum kode: apakah bagian mana pun akan sama untuk aplikasi downloader apa pun? Bila ya, ganti dengan pilihan yang spesifik untuk TubeFlow (isinya: tautan, format, progres, file, lirik) dan sebutkan apa yang diubah.

Titik awal yang sering menjadi tanda desain generik: latar krem dengan aksen terakota; latar hitam dengan satu aksen hijau neon; semua konten dipotong menjadi kartu identik dengan radius dan bayangan sama; label huruf kapital berjarak di atas setiap judul; panah "→" di setiap tombol. Semua sah bila dipilih dengan sadar, tetapi jangan jadikan default.

## Token

Definisikan sekali di tema, pakai di mana-mana. Nilai literal di composable adalah tanda token belum cukup.

- **Warna.** Gunakan peran Material 3 (primary, secondary, tertiary, surface bertingkat, error, outline). `Theme.kt` kini memakai latar gelap netral `#121212`, panel `#1D1D1D`, aksen hijau statis, dan palet terang tersendiri. Pilihan aksen sampul dapat mengganti warna utama lintas aplikasi; jangan memaksanya kembali hijau ketika pengguna mengaktifkan pilihan itu. Warna dinamis Android 12+ tetap tersedia. `DESIGN.md` mencatat sistem terbaru; periksa source aktual sebelum mengubah token.
- **Tipografi.** Satu atau dua keluarga huruf; bila dua, bedanya jelas. Gunakan skala tipe tema (display, headline, title, body, label) dengan bobot dan jarak yang disengaja. Font yang dibundel perlu lisensi yang jelas dan fallback. Teks tetap terbaca pada skala font sistem 200%. Panjang baris bacaan di bawah sekitar 80 karakter.
- **Spasi.** Grid 4dp dengan ritme 8/12/16/24/32. Margin layar konsisten. Jarak antar kelompok lebih besar daripada jarak dalam kelompok, sehingga pengelompokan terlihat tanpa garis.
- **Bentuk.** Radius berjenjang menurut hierarki (komponen kecil, kartu, sheet), bukan satu radius untuk semuanya.
- **Elevasi.** Utamakan tonal elevation (perbedaan warna surface) dibanding bayangan abu seragam.
- **Ikon.** Satu keluarga ikon dengan ketebalan garis konsisten; ikon tanpa label hanya untuk makna yang universal.
- **Target sentuh.** Minimal 48dp, dengan jarak cukup antar aksi yang berdekatan (mis. jeda/batal pada item antrean).

Untuk deret kontrol di dekat tab/filter, gunakan `ActionChip` agar tinggi visual,
bentuk dan padding mengikuti chip Material. Putar/Acak/Cari di Koleksi dan
Bersihkan di Riwayat memakai pola ini; tombol formulir/dialog tetap memakai
`ActionButton`. Sejak1.1.24 komponen ActionButton sendiri memakai AssistChip
bersama ActionChip: visual32dp minimum dan target48dp, dengan warna/border
sesuai peran. Label panjang membungkus. OptionMenu menampilkan label:nilai
dalam chip yang sama, bukan tombol dua baris berpadding besar. Ini berlaku
untuk Pengaturan, Download, lirik, timer, karaoke, playlist dan empty state;
tinggi52/48dp pada catatan desain lama sudah digantikan.
Baris Kelola playlist mengikuti kartu koleksi dengan nama dan
ikon Ubah/Hapus48dp, beserta deskripsi aksesibilitas yang menyebut playlist.
Antrean putar dan aksi massal antrean download mengikuti chip yang sama.
Kartu video memakai baris ikon Putar/Bagikan/Detail/Ubah/Hapus; kartu audio
menambahkan Bagikan pada aksi musik yang ada. Jangan menyalin aksi khusus
audio ke video demi menyamakan jumlah tombol.

## Status antarmuka

Aplikasi terasa profesional ketika semua status dirancang, bukan hanya jalur sukses. Setiap layar atau komponen data menyediakan:

- **Memuat.** Progres determinate bila nilainya diketahui; kerangka (skeleton) atau indikator ringan bila tidak. Hindari kedipan untuk pemuatan singkat.
- **Kosong.** Ajak bertindak: apa yang bisa dilakukan sekarang, dengan satu aksi jelas.
- **Galat.** Jelaskan apa yang terjadi dan cara memperbaikinya, dengan aksi (coba lagi, pilih folder, buka pengaturan). Detail teknis (kode galat, keluaran engine) ada di bagian "Detail" yang dapat disalin, bukan sebagai pesan utama.
- **Offline / jaringan buruk, penyimpanan penuh, izin atau folder dicabut, engine perlu diperbarui.** Masing-masing punya pesan dan aksi sendiri.
- **Berjalan, jeda, selesai, dibatalkan, gagal sebagian (playlist).** Warna, ikon dan teks berbeda, dan arti status tidak hanya bergantung pada warna.
- **Item antrean dan daftar besar.** Kunci stabil pada `LazyColumn`, tidak ada animasi ulang saat progres berubah, dan pembaruan progres tidak memicu recomposition seluruh daftar.

## Teks antarmuka

Aplikasi ini berbahasa Indonesia; pertahankan konsistensi satu bahasa dan satu register (netral dan sopan, tidak terlalu santai).

- Huruf kapital gaya kalimat, kata kerja aktif, tanpa basa-basi. Tombol menyebut hasilnya: "Simpan perubahan", bukan "Kirim".
- Satu aksi, satu nama di seluruh alur. Tombol "Unduh" menghasilkan notifikasi "Mengunduh…" lalu "Unduhan selesai", bukan campuran "Download/Unduh/Simpan".
- Galat tidak meminta maaf dan tidak samar: katakan apa yang gagal dan langkah berikutnya. Contoh: "Folder tujuan tidak lagi dapat diakses. Pilih folder lagi di Pengaturan."
- Pindahkan string baru atau teks pada layar yang sedang diubah ke `strings.xml` (dengan plurals untuk jumlah) secara bertahap. Codebase saat ini masih menyimpan banyak string Bahasa Indonesia langsung di Composable (`strings.xml` awalnya hanya berisi `app_name`); hindari PR raksasa yang mengganti seluruh literal sekaligus di luar cakupan tugas agar diff tetap fokus dan bebas regresi.
- Angka, durasi dan ukuran file diformat dengan formatter lokal, bukan manual.
- Teks tidak mengklaim lebih dari yang terbukti. Sebut penyedia lirik sesuai implementasi (bukan penyedia berlisensi lain), beri label "Cuplikan" pada hasil yang dipotong, dan jangan menjanjikan karaoke bebas vokal di semua lagu. Artis hanya ditampilkan bila terbukti; kolom kosong lebih baik daripada tebakan dari nama channel.

## Gerak

- Gerak yang menjawab aksi pengguna (membuka, memperluas, mengonfirmasi) disambut baik; gerak otomatis dipakai hemat dan hanya untuk menarik perhatian pada hal penting.
- Durasi sekitar 150 sampai 300 ms dengan easing yang konsisten. Hormati pengaturan skala animasi sistem.
- Progres bergerak halus tanpa melompat mundur. Jangan menghidupkan kembali pola heartbeat progres lama.
- Tampilan progres mengikuti semantik di [invariants.md](invariants.md): persen transfer per track; pemrosesan, lirik dan finalisasi memakai indikator tak tentu; kecepatan/ETA hanya bila datanya valid. Jangan membuat animasi yang seolah-olah transfer masih berjalan.
- Hindari fade-and-slide pada setiap kartu dan efek hover/ripple berlebihan; itu tanda desain generik.

## Platform Android

Verifikasi detail terhadap dokumentasi Android untuk target SDK aktif di Gradle.

- **Edge-to-edge** dengan penanganan insets yang benar (status bar, navigation bar, keyboard, display cutout); tidak ada konten terpotong atau tertutup.
- **Back.** Dukung predictive back dan perilaku back yang dapat diprediksi di layar bersarang, dialog dan sheet.
- **Adaptif.** Layar sempit (sekitar 360dp), ponsel besar, tablet/foldable, dan rotasi. Gunakan kelas ukuran jendela bila tata letak berubah secara bermakna; jangan hanya meregangkan satu kolom.
- **Notifikasi.** Channel dengan nama jelas, progres yang tidak spam, aksi (jeda/batal) yang berfungsi, dan perilaku wajar bila izin notifikasi ditolak.
- **Ikon peluncur** adaptif dan monokrom (themed icon), splash yang singkat dan konsisten dengan tema.

## Player dan karaoke

- Satu player untuk seluruh aplikasi (`PlayerViewModel.obtain()` yang hidup di level proses aplikasi, bukan terikat siklus hidup Activity). Kontrol di layar, notifikasi media native (`PlaybackService`/MediaStyle dengan ikon monokrom `ic_stat_playback`, artwork dan durasi/posisi dari MediaSession, serta tujuan ketuk full player), tombol headset, serta auto-play saat perangkat audio tersambung mengarah ke engine yang sama; jangan membuat tampilan player kedua dengan state sendiri.
- Pemutar penuh memakai `HorizontalPager` dua halaman (Sampul ↔ Lirik) tanpa chip pemindah terpisah; geseran manual (swipe) pengguna menang atas auto-switch saat lirik baru tiba.
- Saat lirik kosong atau belum pas, pemutar penuh menyediakan langsung tombol **Cari lirik** (`LyricsSearchDialog`) dan **Editor lirik** (`LyricTimingDialog`) tanpa memaksa pengguna kembali ke layar Koleksi.
- Tampilan lirik mengikuti timestamp asli ditambah offset per lagu. Pilihan lirik manual menampilkan pratinjau, atribusi, dan pengakuan bila berbeda dari identitas audio aktual.
- Status lirik (tidak ditemukan, instrumental, gagal jaringan, gagal simpan) punya pesan yang berbeda, dan lirik yang gagal tidak membuat unduhan tampak gagal.
- Warna turunan sampul (`rememberCoverAccent`) boleh mempercantik gradien latar pemutar, panel bawah antrean (kartu lagu aktif berlabel "Sedang diputar" beserta sampul per item), dan aksen notifikasi, tetapi tidak dipakai sebagai informasi platform sumber; ikon/warna platform berasal dari registry platform.
- Equalizer menyediakan preset bawaan **Otomatis (bass bulat)** (`AutoEq`: kurva frekuensi tengah band +5 dB @60 Hz, −1 dB mid, +2,5 dB treble yang di-clamp ke rentang perangkat) di samping preset perangkat dan pengaturan manual pengguna.
- Job karaoke berjalan terpisah dengan progres sendiri; judul dialog mengikuti lagu yang sedang dipilih dan memberi label sumber bila ada proses lama yang masih berjalan. Status batal di UI belum berarti proses native selesai, jadi tombol ulang tidak boleh mengaktifkan job baru sebelum pembersihan job lama berhenti.

## Aksesibilitas

- Setiap elemen interaktif punya nama yang terbaca TalkBack (`contentDescription` atau teks), dan elemen dekoratif disembunyikan dari semantik.
- Urutan baca logis; item daftar yang berisi beberapa bagian digabung menjadi satu fokus dengan aksi kustom yang jelas.
- Progres mengekspos nilai (rentang progres) agar TalkBack dapat membacakannya, tanpa mengumumkan setiap tick.
- Kontras teks minimal 4,5:1 (3:1 untuk teks besar dan komponen UI). Status tidak hanya dibedakan oleh warna.
- Seluruh aksi dapat dijangkau dengan keyboard/switch access dan fokus terlihat.

## Bukti visual

Sesuaikan cakupan tangkapan layar dari HP fisik (screenshot perangkat lewat adb; proyek ini menguji di HP fisik, tanpa emulator) dengan skala perubahan:

- **Perubahan terfokus (satu dialog, kartu, atau interaksi layar tertentu):** tangkapan layar sebelum/sesudah pada layar dan status yang disentuh sudah cukup, selama tata letak utama dan tema tidak dirombak.
- **Perombakan layar/tema atau audit kesiapan produksi (Gerbang G5):** lengkapi matriks penuh berikut:
  1. Mode terang dan gelap.
  2. Skala font 1,3x dan 2,0x.
  3. Lebar sempit (sekitar 360dp) dan, bila tata letak adaptif disentuh, landscape. Bila tidak ada perangkat berlayar besar, tandai tablet/foldable sebagai belum diuji.
  4. Status kosong dan galat, bukan hanya status berisi data.

Simpan atau rujuk tangkapan itu pada laporan pengujian perangkat. Periksa isi tangkapan sebelum menyimpannya ke `docs/images/`; notifikasi atau nama file pribadi tidak boleh ikut menjadi bukti publik. Bila HP belum tersambung, katakan begitu dan tandai sebagai belum diuji.

## Hal yang dihindari

- Warna atau ukuran literal di composable di luar tema, serta menambah string literal baru tanpa rencana ekstraksi ke `strings.xml`.
- Satu radius, satu bayangan, dan satu jenis kartu untuk seluruh aplikasi.
- Menyorot satu kata dalam judul dengan warna atau miring sebagai hiasan.
- Label huruf kapital berjarak di atas setiap judul dan penomoran pada konten yang bukan urutan.
- Menampilkan stack trace atau stderr mentah sebagai pesan galat utama.
- Merombak identitas visual yang sudah ada tanpa diminta.
