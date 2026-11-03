# Sistem tampilan TubeFlow

## Arah

Perluas tampilan player musik yang sudah dipilih pengelola ke seluruh menu. Ini
aplikasi Android Compose untuk mengunduh dan mengelola media pribadi. Sampul,
judul lagu, progres, dan tindakan berikutnya menjadi fokus. Gunakan Material 3;
pertahankan navigasi dan fitur aktual. Tidak menyalin logo, aset, atau merek Spotify.

Mode Impeccable: Operate. Anti Slop: during untuk sesi perbaikan ini berdasarkan
arahan pengelola untuk melanjutkan. ENERGY 2, RHYTHM 1, MOTION 1: identitas sampul
cukup kuat, daftar mudah dipindai, gerak hanya untuk perubahan status.

### Penyelarasan permukaan 1.1.53

Charcoal kehijauan `#101411` menjadi latar malam; panel berjenjang `#141A15`,
`#1B221D`, `#242D26`, dengan outline tipis `#3B473F`. Aksen utama hijau
`#58D994`; champagne `#E0C681` hanya menandai status sekunder. Mode terang
memakai latar `#F4F7F3` dan aksen hijau tua `#0B7A42`. Sampul tidak mewarnai
latar setiap kartu lagi: warna album tetap hidup di artwork dan player, sementara
daftar yang memuat banyak sampul memakai panel netral. Ikon splash, launcher,
dan monokrom memakai satu tanda gelombang/play TubeFlow.

### Splash 1.1.54

Splash terang/gelap memakai simbol gelombang-play yang sama dengan identitas
launcher, di atas latar ikon hijau hutan yang sesuai tema. Resource `drawable-night`
lama yang hanya berisi segitiga ungu dihapus dari tampilan dengan menggantinya
dengan simbol yang sama; layar pembuka tetap system splash agar tidak menambah
waktu tunggu sebelum aplikasi siap.

## Tema Cyberpunk (1.1.57)

Cyberpunk adalah pilihan tema eksplisit di Pengaturan → Tampilan, tidak bergantung pada wallpaper
atau sampul. Gunakan latar navy hampir hitam dengan permukaan biru berlapis; turquoise untuk aksi,
cyan untuk outline/navigasi, dan magenta hanya sebagai aksen sekunder. Teks utama/sekunder tetap
berkontras tinggi. Aksen dinamis dan artwork tidak mengganti palet saat Cyberpunk dipilih; preferensi
pengguna tetap tersimpan dan berlaku kembali saat memilih tema lain.

Polesan khusus tema dibatasi pada outline kartu, header halaman, ikon kartu Alat, mini player, dan
pemisah navigasi. Konten, hierarki tindakan, bentuk kontrol, dan navigasi tetap komponen Material 3
yang sama; tidak memakai glow animasi atau hiasan yang mengganggu pemindaian daftar. Palet Cyberpunk
diuji kontrasnya bersama palet gelap/terang di `ThemeContrastTest`.

Penyelarasan susulan menutup dua celah. Lencana ikon (`IconBadge`) kini memakai isi turquoise–cyan
dengan outline cyan, sehingga ikon kartu Alat dan header halaman benar-benar berada di palet
Cyberpunk, bukan hanya memakai wadah lama. Aksen sampul juga tidak lagi mengganti warna pemutar:
mini player, pemutar penuh, antrean putar, dan progres unduhan memakai `primary` turquoise saat
Cyberpunk aktif. `AccentTheme` di akar aplikasi memang sudah mematikan aksen artwork untuk tema ini,
tetapi pemakaian `rememberCoverAccent` langsung di layar melewati gerbang tersebut.

## Tema Transformers (1.1.66)

Transformers adalah tema eksplisit di Pengaturan → Tampilan. Paletnya memakai baja navy
`#04070F` sampai `#182A43`, biru elektrik `#4FD8FF` untuk aksi utama, merah `#FF6A5C` untuk aksen
sekunder, dan kuning `#FFC93C` hanya untuk status. Sudut terpotong menjadi bahasa bentuk; lambang
perisai geometris dipakai di header dan pemutar.

Penyelarasan 1.1.66 membawa bahasa itu ke header pemutar penuh, bingkai sampul, tombol aksi,
indikator halaman, sheet antrean/opsi video, banner antrean unduhan, dan judul dialog. Tema
gelap/terang/Cyberpunk tetap memakai kontrol dan radius lamanya; kontras keempat palet dikunci
`ThemeContrastTest`.

Penyelarasan susulan menutup bentuk dan warna pemutar video yang masih di-hardcode: overlay
lanjut-tonton, pengaturan cepat, lencana status, dan lembar opsi kini mengambil bentuk dari
`MaterialTheme.shapes` (bukan `RoundedCornerShape` tetap), latar lembar/overlay memakai
`surfaceContainer` palet Transformers, dan aksen status memakai `primary` palet. Tema lain tetap
memakai radius, latar, dan biru pemutarnya sendiri — pemutar video selalu gelap, sehingga warna
tema terang tidak dijamin berkontras di atas panel gelap. Kontrol ringkas (ikon bulat pengaturan
cepat, ChoicePill) sengaja tidak diubah bentuknya agar tetap komponen Material 3 yang sama.
Wadah lencana yang tersisa ikut diselaraskan: lingkaran ikon status kosong (`EmptyState`) dan badge
platform di layar Download memakai sudut terpotong pada tema Transformers, sedangkan chip status dan
`ChoicePill` tetap berbentuk pil agar sejalan dengan `FilterChip`. Dialog `AlertDialog` tidak diubah
karena sudutnya sudah berasal dari `MaterialTheme.shapes.extraLarge`, dan `ModalBottomSheet` pemutar
audio sudah memilih bentuk serta drag handle-nya sendiri sejak 1.1.66.

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

Sejak 3 November 2026 baris Koleksi (`MediaListRow`) memberi aksi cepat 36 dp
di samping ikon lirik: hati favorit dan bagikan untuk audio yang tersedia. Hati
terisi memakai warna galat; video tetap memakai penanda hati 14 dp karena
aksinya berada di menu. Di tab Download, kartu Antrean memakai `JobCard`
ringkas yang sama dengan Riwayat: aksi per status pindah ke menu ⋮, sedangkan
progres dan fase tetap di bawah kepala kartu.

Sejak 3 November 2026 tab Download memisahkan Antrean dan Riwayat menjadi dua tab
(`PrimaryTabRow`) dengan jumlah item di judul; masing-masing tab punya kata kunci
pencarian sendiri dan hanya isi tab aktif yang dirender. Folder Koleksi membuka
lembar aksi lewat tekan lama: Buka, Putar semua lagu, Detail, Ganti nama tampilan,
dan Hapus isi folder. Ganti nama hanya mengubah label TubeFlow (`FolderAliases`)
karena rename direktori akan memutus URI berkas, lirik, dan riwayat unduhan; hapus
hanya membersihkan isi folder, folder fisiknya tetap ada.

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
- Radius 10dp untuk kontrol kecil, 16dp panel, 22/28dp untuk sheet/dialog.
- Target sentuh minimal 48dp; isi memanjang dan font besar boleh membungkus.
- Navigasi mempertahankan Ringkasan, Download, Koleksi; Pengaturan lewat top
  bar, lalu daftar kategori dan subhalaman. Mini player tetap di atas navigasi.

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
Ringkasan  Download  Koleksi
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
- **Filter Koleksi (1.1.38).** Dua baris chip saja: jenis + Putar/Acak, lalu
  Favorit/Filter/Cari/Kelola playlist. Urutan, folder, playlist, artis, dan album
  pindah ke dialog **Filter**; chip menampilkan jumlah filter aktif dan dialog
  menyediakan **Reset filter**. Sebelumnya empat baris chip memenuhi bagian atas
  daftar sebelum kartu pertama.
- **Pengaturan berkategori (1.1.38).** Daftar utama hanya berisi tujuh kartu
  kategori dengan ikon, ringkasan nilai aktif, dan chevron. Setiap kategori
  membuka subhalaman berjudul tunggal; Tentang (privasi, versi, disclaimer)
  terpisah. Kembali dari subhalaman kembali ke daftar kategori, bukan langsung
  keluar ke tab sebelumnya.
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

- **Label resolusi (1.1.42).** Label `format_note` sumber diprioritaskan.
  Tanpa label tersebut, tampilkan sisi pendek aktual; 960×540 tetap 540p.
  Video 1920×818 dengan label sumber 1080p tetap memakai 1080p di UI dan 818
  pada filter download. State formulir menyimpan tier; konversi ke tinggi
  dilakukan saat membentuk DownloadOptions. Menu, ringkasan dan sorotan
  preset memakai tier yang sama. Cache inspeksi v3 menyegarkan label lama
  saat URL dibaca kembali, tanpa mengubah riwayat atau file media.

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


## Menu Alat (1.1.56, 29 Oktober 2026)

Navigasi bawah menjadi empat tab: Ringkasan, Download, Koleksi, Alat. Alat adalah hub operasi
pada file yang sudah ada, bukan pengganti aksi kartu: putar/bagikan/ubah nama/cari lirik tetap
milik `MediaCardPlan` di Koleksi dan Riwayat. Pengaturan tetap di luar bilah bawah dan berisi
Alat media (model karaoke, cadangan, pembaruan engine) yang berbeda peran.

Setiap alat memakai rangka yang sama supaya tidak menjadi enam dialog yang berbeda:

```text
Panel alat        judul · deskripsi satu baris · satu aksi
Dialog alat       [ cari …………… ]  [ Pilih semua ] [ Kosongkan ]
                  [ ] lagu 1        ← daftar 240dp, satu baris per lagu
                  [x] lagu 2
                  pola / pratinjau / keterangan
                  [ Batal ]            [ Terapkan ]
Pekerjaan         progres n/total · berkas aktif · [ Hentikan ]
```

- Aksi destruktif atau massal (terapkan nama, simpan tag) tetap memakai `ConfirmDialog` dan
  warna galat; aksi tenang memakai `ActionEmphasis.Quiet`.
- Daftar pilihan memakai `Checkbox`/`RadioButton` dengan tinggi minimum 48dp dan satu baris judul
  (elipsis) diikuti artis `bodySmall`; pencarian memakai `SearchField` bersama.
- Pratinjau selalu terlihat sebelum tombol aktif: jumlah yang berubah, jumlah yang tetap, dan
  maksimal empat contoh `sebelum → sesudah`. Tidak ada penerapan tanpa pratinjau.
- Hasil dilaporkan per berkas (berhasil/gagal), bukan klaim "semua berhasil".
- Keterangan status memakai `bodySmall` redup; angka memakai string plural agar lint bersih.
- Panel **Karaoke** tidak membuat dialog baru: satu lagu membuka `KaraokeDialog` yang sudah
  dipakai pemutar, banyak lagu masuk antrean `ToolsViewModel` di belakang `KaraokeWorker` yang
  sama. Alat tidak pernah menimpa audio asli.

## Pemutar dua kolom (1.1.31)

Layar landscape lebar (lebar > tinggi dan minimal 560dp) membagi pemutar penuh
menjadi dua kolom: kolom kiri hanya lirik, tanpa pager sampul atau pil
indikator; kolom kanan berisi judul, artis/posisi antrean, sampul, slider
dengan waktu di kiri/kanan, dan transport. Sampul 104dp pada font normal dan
48dp pada font minimal 1,3× (judul lalu dibatasi satu baris).
Acak/Ulangi/Karaoke/Antrean menetap di dasar kolom kanan dalam satu baris empat
slot agar tidak perlu digulir. Portrait tetap satu kolom bergulir dengan pager
sampul/lirik dan indikator mengambang.

## Pemutar video (1.1.39)

Pemutar video memakai tiga lapis kontrol yang tidak saling menumpuk:

- **Top bar:** back, judul/posisi antrean, tombol buka/tutup panel pengaturan,
  ikon antrean, dan ⋮ Opsi pemutar.
- **Pengaturan cepat:** panel ikon bulat tanpa caption di bawah top bar
  (kecepatan, subtitle, EQ, efek audio, Smart Enhance, dekoder, screenshot,
  rasio). Panel dapat disembunyikan dari top bar; di HP ikon tersusun empat per
  baris (dua baris), sedangkan di layar lebar delapan ikon muat satu baris.
  Status aktif ditandai warna aksen, bukan teks.
- **Bar bawah:** waktu kiri/kanan dengan lebar sama, track progres tipis dan
  thumb titik; kunci di kiri, previous/play/next di tengah, PiP dan orientasi di
  kanan. Pengaturan detail (termasuk Ulangi dan antrean) tetap di lembar Opsi.

```text
←  player-h264                 ☰   ⋮
(⏱) (CC) (EQ) (FX) (✦) (▣) (📷) (⤢)

          video / subtitle

0:11 ──────────●──────── 0:20
🔒        ⏮   ▶   ⏭        ⧉   ⤢
```

Tombol ±10 detik tidak ditampilkan agar transport tetap lapang; seek memakai
track, double-tap, dan gestur. Semua ikon tetap memiliki nama TalkBack yang
menyebut nilai aktifnya.

### Kebijakan dekoder (1.1.40)

Mode **Otomatis** membuka dengan dekoder perangkat lalu, bila gagal, membuka
ulang sekali dengan FFmpeg/dav1d. Kegagalan yang memicu fallback: decoder init,
query decoder, format melebihi kapasitas, decoding gagal, dan **timeout** —
timeout inilah yang muncul pada AV1 10-bit/AC3 setelah rotasi atau resume, saat
MediaCodec berhenti melapor. Mode **Perangkat** adalah pilihan manual dan tidak
diganti diam-diam; mode **Software** selalu memakai FFmpeg/dav1d. Aturan ini ada
di `VideoPlayback.shouldUseSoftwareFallback` supaya tidak bergantung pada state
Compose, dan tombol **Coba lagi** tetap membuat ulang pemutar bila player sudah
dilepas setelah kegagalan.

### Bilah progres bersama (1.1.41)

Video dan audio memakai satu `Scrubber`, bukan `Slider` Material. Bentuknya:
track tipis membulat, bagian termuat (buffer video) lebih terang dari sisa
track, bagian terputar memakai warna konten, dan titik bulat 10–12dp yang
membesar 1,9× saat digeser. Titik dan track dipisah celah 2dp supaya keduanya
terbaca sebagai satu kontrol, bukan garis yang menempel. Area sentuh setinggi
34–44dp dengan geser dan ketuk-untuk-lompat; TalkBack membaca nilai serta waktu
lewat `progressBarRangeInfo`.

Karena bar sistem disembunyikan saat memutar, safe drawing bawah bernilai nol;
bar bawah pemutar video karena itu menyisakan margin tetap 14dp (portret) /
16dp (lanskap) agar transport tidak menempel ke tepi layar dan tetap terbaca
saat bar sistem muncul sementara.

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


## Player dan lampiran — 1.1.45

Riwayat, Koleksi dan antrean putar memakai `AttachmentInfo` bersama, labelSmall
serta ikon Lyrics/ClosedCaption 14dp, spasi 4dp. Status tersedia/absen/tidak
terbaca dibedakan lewat teks, berdasarkan file/track nyata. Tidak memakai
pilihan subtitle/lirik saat download sebagai bukti ketersediaan.

Kartu Riwayat selesai dapat diketuk untuk memutar seperti Koleksi. Tombol
sekunder tetap memakai aksi yang sama, tanpa meneruskan klik ke induk.
Dialog potong Status menambah preview 16:9 sumber lokal pada rentang pilihan,
dijeda saat awal potongan digeser dan dilepas saat dialog ditutup/diproses.

Rasio Pas memakai seluruh gambar tanpa distorsi; Penuh mengisi layar dengan
memotong tepi (tanpa regangan); Zoom mempertahankan rasio, memotong tepi, lalu
memperbesar 1,25×. Ukuran permukaan mengikuti ruang aktual serta rasio piksel,
dihitung ulang setelah rotasi.
Kecerahan video mengikuti adaptif Android atau sensor cahaya selama layar
video aktif, tanpa menulis pengaturan brightness global.

Penghemat baterai berada di Pengaturan → Pemutaran (Ikuti sistem/Aktif/Nonaktif).
Mode ini mengurangi frekuensi pembaruan posisi dan sementara melepas efek
Smart Enhance, mempertahankan preferensi efek pengguna untuk dipakai kembali.

## Panel aksi baris unduhan dan dialog folder — 1.1.75

Antrean dan Riwayat memakai satu panel aksi: menu titik tiga dengan ikon, urutan, dan pengelompokan yang
sama (`Antrean & playlist`, `Lirik`, `Lampiran`, `Berkas`). Tinggi menu dibatasi 420 dp dan aksi
destruktif selalu memakai warna galat, sehingga perbedaan hanya berasal dari status job, bukan dari
komponen yang berbeda. Baris ringkas tidak menambah deret ikon; kartu besar tetap memakai deret ikon.

Aksi folder Koleksi (tekan lama) tampil sebagai dialog tengah layar, bukan lembar bawah: lebar mengikuti
isi dengan batas 420 dp (nyaman di ponsel 360 dp maupun tablet), tinggi maksimum 560 dp dan dapat
digulir, judul folder dipotong maksimal dua baris, dan setiap aksi adalah baris ikon 20 dp + label
dengan seluruh lebar baris sebagai target sentuh. Catatan bahwa ganti nama hanya label TubeFlow tetap
tampil di bawah daftar aksi.

## Kecerahan player — 1.1.46

Penyempurnaan Operate, ENERGY2/RHYTHM1/MOTION1. Pilihan Otomatis/Manual berada
di lembar Tampilan yang sudah ada; tidak menambah dialog atau halaman. Gunakan
ChoicePill, Scrubber tipis dengan target48dp, labelSmall dan spasi8/12/20dp,
warna SheetBackground/PlayerContent serta aksen tema yang sama. Mode aktif
ditandai selected dan teks. Persentase hanya tampil saat manual.

```text
Tampilan
Kecerahan
[Otomatis] [Manual]
   50% [bilah kecerahan jika Manual]
Rasio layar … / Rotasi … / Ulangi …
```

Geser vertikal sisi kiri beralih ke manual dengan titik awal kecerahan yang
sedang digunakan. Otomatis mengikuti adaptif Android/sensor; manual hanya
mengubah jendela player dan dipulihkan saat keluar/PiP/Stop. Kritik: mengecilkan
target sentuh atau membuat slider baru akan merusak konsistensi; pakai kontrol
bersama dan jangan mengubah volume atau brightness sistem untuk mereset mode.

Penguat video berada di awal lembar Suara & gambar, memakai ChoicePill yang
sama: Normal/+3/+6/+9dB. Normal tidak mengaktifkan pemrosesan tambahan; status
menunggu/aktif/format tidak didukung berasal dari pipeline audio. Pembatas
puncak tidak ditampilkan sebagai klaim normalisasi otomatis atau kualitas baru.
