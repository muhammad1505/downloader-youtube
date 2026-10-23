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

### Ukuran kontekstual dan ruang tersedia (1.1.25)

Tombol formulir memakai tinggi minimum40dp (utama48dp), padding16×8dp,
radius small tema dan labelLarge yang boleh membungkus. ActionChip tetap32dp
untuk kontrol ringkas di dekat filter. Keduanya mempertahankan target48dp;
teks tidak dikecilkan otomatis untuk menyembunyikan kekurangan ruang.

AdaptivePair memindahkan aksi ke bawah isi ketika lebar lokal kurang320dp
atau font minimal1,3×. Mini player memakai ambang360dp karena tiga kontrolnya
memerlukan144dp. Nama playlist mendapat baris sendiri pada dialog sempit.
Layar dengan area aplikasi minimal840dp memakai navigasi rail; isi dibatasi
840dp dan berada di tengah agar formulir tidak merentang tanpa batas.

```text
lapang:  [sampul / judul playlist ........] [ubah] [hapus]
sempit:  [sampul / judul playlist ....................]
                                      [ubah] [hapus]
form:    [       Simpan       ]    filter: [Semua] [Musik]
```

ENERGY2/RHYTHM1/MOTION1, palet dan ritme8/12/16dp tetap. Kritik terhadap aturan
1.1.24: bentuk seragam membuat tombol formulir terlalu menyerupai filter dan
tidak menyelesaikan baris yang berebut lebar. Aturan baru mempertahankan peran
kontrol, memakai batas minimum/intrinsik dan perubahan susunan sesuai ruang.
Ukuran tidak ditentukan dari merek HP atau dikalikan resolusi piksel mentah.

### Kontrol ringkas menyeluruh (1.1.24, historis)

Ukuran formulir bagian ini digantikan aturan kontekstual1.1.25 di atas.

ActionButton dan ActionChip menggunakan komponen chip Material yang sama:
tinggi visual minimum32dp, ikon18dp, labelLarge, bentuk chip tema dan target
sentuh minimum48dp. Aksi utama dibedakan lewat warna filled, sekunder outline,
dan aksi tenang tanpa border. Label panjang/font besar boleh membungkus.
OptionMenu menampilkan label dan nilai dalam satu chip, bukan tombol dua baris
dengan padding10dp. Dropdown tetap memakai menu Material yang sama.

```text
Pengaturan: [Tema: Gelap ▾] [Segarkan] [Bersihkan cache lirik]
Lirik:      [Cari lirik] [Muat hasil lain]
Form:       [Simpan] [Batal]
```

Palet, tipografi, jarak8/12/16dp, radius kartu dan ENERGY2/RHYTHM1/MOTION1
dipertahankan. Kritik: mengecilkan seluruh area sentuh akan mempersulit pemakaian;
yang disamakan adalah ukuran visual. Kontrol transport utama player, switch,
radio, field input dan target ikon tetap mengikuti kebutuhan interaksinya.
Aturan ini menggantikan tinggi tombol52/48dp pada catatan versi sebelumnya.

Polesan 1.1.23 mempertahankan ENERGY2/RHYTHM1/MOTION1, palet, tipografi,
radius kartu20dp dan ritme8/12/16dp. Antrean putar memakai ActionChip seperti
filter Acak/Ulangi. Aksi kartu audio dan video memakai IconAction48dp agar
baris tetap ringkas dan target sentuh tidak mengecil. Video mendapat aksi
yang sesuai berkas video, tanpa menu lirik atau antrean audio. Sejak 1.1.26
seluruh aksi kartu ditampilkan langsung; titik tiga di bawah adalah keadaan
1.1.23 yang sudah digantikan.

```text
Antrean: [Acak] [Ulangi] [Bersihkan]
Audio:   sampul / judul / artis
         [putar] [favorit] [bagikan] [antrean] [lirik] [berkas]
Video:   thumbnail / judul / info
         [buka] [bagikan] [detail] [berkas]
```

Kritik: menyamakan semua aksi dengan audio akan memberi aksi yang tidak berlaku
untuk video. Kesamaan diterapkan pada ukuran, posisi dan operasi berkas;
fitur audio tetap khusus audio. Tombol utama dan konfirmasi tetap memakai
ActionButton agar hierarki tindakan dan keterbacaan terjaga. Rincian pembagian
aksi ada pada bagian "Aksi kartu media" di bawah.

## Aksi kartu media (20 Oktober 2026)

Koleksi dan Riwayat menampilkan file yang sama, jadi aksinya tidak boleh berbeda
pola. `ui/MediaCardActions.kt` menjadi satu sumber kebenaran: `MediaCardPlan.card`
menentukan aksi mana yang ada dan slotnya; `MediaActionBar` menggambar **enam aksi
harian langsung di kartu** sebagai ikon 48dp dalam `FlowRow` yang membungkus, lalu
satu tombol ⋮ untuk sisanya. Kedua layar hanya mengisi `enabled` dan `onClick`,
sehingga satu aksi memiliki satu nama dan satu ikon di mana pun.

- **Ikon langsung (maksimal enam):** Putar/Buka → Favorit (audio) → Bagikan →
  Ubah nama (Koleksi) → Cari lirik (audio) → Hapus file.
- **Menu ⋮ (berkelompok):** *Antrean & playlist* (Tambah ke antrean, Putar
  berikutnya, Tambah/Hapus dari playlist), *Lirik* (Atur waktu lirik bila LRC
  tersimpan), *Lampiran* (thumbnail, subtitle video), *Berkas* (Detail, Buka
  dengan aplikasi lain, Unduh lagi bila item masih tertaut ke unduhan selesai).
- **Video:** Buka, Bagikan, Ubah nama, Hapus file langsung; Detail dan lampiran
  di menu. Tidak ada aksi lirik atau antrean audio.
- **Perbedaan yang sah:** Koleksi memiliki aksi pustaka (favorit, playlist,
  ubah nama) karena katalog adalah sumbernya; Riwayat memiliki lampiran unduhan.
  Pola, penamaan, dan urutan tetap sama.
- **Aksi berisiko:** Hapus file dan Unduh lagi tetap meminta konfirmasi; ikon
  Hapus di kartu dan item Hapus di menu memakai warna galat.
- **Berbagi:** `ACTION_SEND` menyertakan caption `EXTRA_TEXT` berisi nama file
  tanpa ekstensi, agar aplikasi tujuan membuka pesan yang sudah terisi. Buka
  dengan aplikasi lain tidak menambahkan caption.

```text
Kartu audio (Koleksi):  [▷] [♡] [↗] [✎] [♪] [🗑] [⋮]
Kartu audio (Riwayat):  [▷] [↗] [♪] [🗑] [⋮]
Menu ⋮:                 Antrean & playlist / Lirik / Lampiran / Berkas
```

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
Panel **Riwayat unduhan** menyediakan satu pilihan **Hapus otomatis**
(Nonaktif/7/30/90 hari) dengan keterangan bahwa hanya catatan riwayat yang
dihapus — file media tetap ada.

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
- **Aksi kartu media.** Koleksi dan Riwayat memakai satu `MediaCardPlan.card`
  dan `MediaActionBar`: **enam aksi harian tampil langsung di kartu** (Putar/Buka,
  Favorit, Bagikan, Ubah nama, Cari lirik, Hapus) dan sisanya di menu ⋮ yang
  berkelompok. Urutan dan pembagian slot sama di kedua daftar. Sejak 1.1.27
  **Unduh lagi** (Koleksi, bila item masih tertaut ke unduhan selesai, dengan
  konfirmasi karena file lama diganti), **Atur waktu lirik**, antrean, detail,
  dan lampiran berada di menu agar baris kartu tetap ringkas. Hapus file tetap
  berkonfirmasi walau tampil sebagai ikon.
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


## Pemutar dua kolom (1.1.31)

Layar landscape lebar (lebar > tinggi dan minimal 560dp) membagi pemutar penuh
menjadi dua kolom: kolom kiri hanya lirik, tanpa pager sampul atau pil
indikator; kolom kanan berisi judul, artis/posisi antrean, sampul, slider
dengan waktu di kiri/kanan, dan transport. Sampul 104dp pada font normal dan
48dp pada font minimal 1,3× (judul lalu dibatasi satu baris).
Acak/Ulangi/Karaoke/Antrean menetap di dasar kolom kanan dalam satu baris empat
slot agar tidak perlu digulir. Portrait tetap satu kolom bergulir dengan pager
sampul/lirik dan indikator mengambang.

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
