# Sistem tampilan TubeFlow

## Arah

Perluas tampilan player musik yang sudah dipilih pengelola ke seluruh menu. Ini
aplikasi Android Compose untuk mengunduh dan mengelola media pribadi. Sampul,
judul lagu, progres, dan tindakan berikutnya menjadi fokus. Gunakan Material 3;
pertahankan navigasi dan fitur aktual. Tidak menyalin logo, aset, atau merek Spotify.

Mode Impeccable: Operate. Anti Slop: during untuk sesi perbaikan ini berdasarkan
arahan pengelola untuk melanjutkan. ENERGY 2, RHYTHM 1, MOTION 1: identitas sampul
cukup kuat, daftar mudah dipindai, gerak hanya untuk perubahan status.

## Token

Kontrol Koleksi Putar/Acak/Cari dan Bersihkan di Riwayat memakai `ActionChip`
yang sama dengan deret filter: tinggi visual mengikuti chip Material, target
sentuh tetap minimal48dp. Tombol formulir/dialog tetap memakai `ActionButton`.
Kelola playlist mengikuti kartu koleksi: ikon playlist bawaan, nama dua baris,
dan ikon Ubah/Hapus48dp. Tidak memakai sampul atau jumlah lagu rekaan.

```text
[Semua] [Musik] [Video]
[▷ Putar] [⤨ Acak] [⌕ Cari]       ← ukuran chip yang sama

Kelola playlist
  [ikon] Nama playlist          [ubah] [hapus]
```

Pengaturan memisahkan total cache menjadi lirik, metadata, dan sampul. Hasil
bersihkan lirik tidak menyiratkan cache sampul atau file LRC juga dihapus.

| Peran | Gelap | Terang |
|---|---|---|
| Latar | `#121212` | `#F4F6F4` |
| Panel | `#1D1D1D` | `#EDF1ED` |
| Teks utama | `#F1F1F1` | `#161A17` |
| Teks sekunder | `#B3B3B3` | `#4A524D` |
| Aksi utama | `#33D07C` | `#0B7A42` |
| Galat | `#FFB4AB` | `#BA1A1A` |

Sumber kebenaran warna adalah `ui/theme/Theme.kt`, termasuk warna dinamis yang
dipilih pengguna. Warna sampul dipakai tipis pada media dan player; warna galat
tetap semantik. Tidak menambahkan hiasan gradien pada panel pengaturan atau empty state.

- Tipografi: skala Material, judul halaman `headlineSmall`, judul bagian
  `titleMedium`, isi `bodyMedium`, keterangan `bodySmall`. Font mengikuti sistem.
- Margin halaman/panel 16dp; antar elemen 8/12dp; antar kelompok 24dp.
- Radius 12dp untuk elemen kecil, 20dp panel media, 24/28dp untuk sheet/dialog.
- Target sentuh minimal 48dp; isi memanjang dan font besar boleh membungkus.
- Navigasi mempertahankan Ringkasan, Download, Antrean, Koleksi, Riwayat;
  Pengaturan lewat top bar. Mini player tetap di atas navigasi.

## Pola halaman

```text
TubeFlow                                      Pengaturan
Judul halaman
Keterangan singkat tentang tugas/data aktual

[Pencarian / filter yang relevan]

Bagian atau panel
  judul dan informasi utama
  keterangan sekunder
  satu aksi utama, tindakan tambahan lebih tenang

[Mini player: sampul, judul/artis, putar/jeda]
Ringkasan  Download  Antrean  Koleksi  Riwayat
```

Panel mengelompokkan kontrol yang terkait. Lagu menggunakan sampul dan identitas
aktual; metrik menggunakan data Room, bukan angka contoh. Empty state memberi
langkah berikutnya tanpa meniru kartu lagu. Dialog memakai tema dan istilah yang
sama, dengan tindakan berisiko terpisah.

## Sistem aksi dan panel (1.1.19)

Polesan ini tidak menambah warna atau font baru; identitas tetap sampul media +
satu aksen hijau. Yang disatukan adalah lapisan interaksi yang sebelumnya berbeda
per layar.

- **Panel.** `Panel` di `ui/components.kt`: padding 16dp, judul `titleMedium`,
  deskripsi `bodySmall` redup, jarak isi 12dp. Menggantikan padding 14/16/18dp
  dan judul dengan bobot berbeda di Ringkasan, Download, Antrean, Riwayat,
  Pengaturan, dan alat media.
- **Aksi.** Satu `ActionButton` dengan empat peran: utama (filled, tinggi 52dp,
  satu per layar), sekunder (outlined, 48dp), tenang (text, target 48dp), dan
  destruktif (warna galat, bukan hijau). Ikon 18dp, jarak 8dp, label
  `labelLarge`. CTA gradien di Download dihapus.
- **Kontrol.** `ToggleRow` (baris penuh, `toggleable` + `Role.Switch`), `CheckRow`
  (`Role.Checkbox`), `ScrollRow` (jarak 8dp), `StatRow` (metrik menumpuk saat
  font >1,3x), `SearchField`, dan `ConfirmDialog` (aksi berisiko berwarna galat).
- **Aksi di daftar.** `IconAction` (ikon 48dp, nama dibaca TalkBack) menggantikan
  tombol berlabel di baris kartu Riwayat/Antrean: label panjang memakan ruang
  daftar dan membuat kartu tinggi. Teks tetap dipakai untuk aksi di header,
  panel, dialog, dan tautan dalam pesan galat.
- **Filter Koleksi.** Satu bentuk chip untuk seluruh deret: `FilterChip` untuk
  Favorit, `MenuChip` untuk dropdown playlist/urutan/folder/artis/album, dan
  `ActionChip` untuk "Kelola playlist". Sebelumnya ukuran dan bentuknya campur
  (chip, tombol outlined, tombol teks).
- **Aksi lagu di Koleksi.** Favorit, tambah ke playlist, dan tambah ke antrean
  tampil sebagai ikon di kartu (bukan di menu tiga titik). Menu menyisakan aksi
  informasi dan berkas: putar sekarang/berikutnya, keluarkan dari playlist,
  detail, cari lirik, atur waktu lirik, ubah nama, hapus.
- **Lirik di Riwayat.** Lampiran lirik (.lrc) tidak lagi ditawarkan dari kartu
  riwayat: satu-satunya aksi lirik di sana adalah pencarian lirik. Lampiran yang
  tersisa hanya thumbnail dan subtitle video.
- **Kartu.** Kartu media tetap `mediaCardColors` dengan radius 20dp; chip status
  memakai bentuk pil agar sejalan dengan `FilterChip`. Baris lagu hanya
  menampilkan nama file bila tidak mengulang judul.

```text
Judul halaman
Keterangan singkat
[ Tempel link ..........................  📋  ✕ ]
[ pratinjau / galat + Detail error ]
Panel: Pilih download
  ( Video | Audio )        segmented
  Pilihan cepat: (chip) (chip) (chip)
  ▸ Pengaturan lanjutan
[ ringkasan opsi ]
[  ⬇  Download audio  ]     satu aksi utama, filled
```

## Kritik

Yang mudah terasa generik dan karena itu diubah: CTA gradien hijau (diganti satu
tombol filled), tiga tombol outlined sederajat di puncak Ringkasan (diganti chip
tujuan), setiap aksi tampil sebagai `TextButton` (kini empat peran berbeda), panel
identik dengan padding acak (kini satu `Panel`), dan dialog hapus yang tombolnya
sama dengan dialog biasa (kini berwarna galat). Identitas TubeFlow tetap dipegang
sampul nyata, hierarki judul/artis, status unduhan, dan akses lirik.

## Kritik dan verifikasi

Gelap dan hijau saja mudah terasa generik. Pertahankan identitas melalui sampul
media nyata, hierarki judul/artis, status unduhan yang tepat, dan akses langsung
ke lirik. Tidak menambah kartu promosi, slogan, atau statistik buatan.

Periksa HP fisik: setiap menu, panel lirik, font 1,3x/2x, terang/gelap, layar sempit,
status kosong dan galat. Catat cakupan yang benar-benar diuji pada laporan
perbaikan; jangan menyimpulkan aksesibilitas atau kesiapan rilis dari build saja.


## Navigasi dan playlist (1.1.20)

- Notifikasi musik memakai MediaStyle native dan MediaSession, bukan salinan mini
  player dalam RemoteViews. Ketukan isi membuka full player pada Activity yang sama.
- Selesai download membuka Riwayat; unduhan aktif/gagal membuka Antrean; karaoke
  membuka status proses. Aksi stop/cancel/retry tetap terpisah dari tujuan ketuk.
- Secondary ActionButton selalu memiliki outline, termasuk kondisi disabled.
  Bersihkan di Riwayat/Antrean putar dan Ubah/Hapus playlist mengikuti komponen ini.
- Kelola playlist: dialog Material, form tambah/ubah, batal edit, error lokal,
  pencarian serta daftar lazy. Tombol membungkus sesuai lebar; tidak menggandakan
  file musik. Nama form dikosongkan setelah operasi database berhasil.
- Cache lirik menampilkan status berjalan dan hasil sukses/kosong/parsial; LRC dan
  cooldown penyedia tetap dipertahankan. Bukan tombol untuk melewati batas LRCLIB.
