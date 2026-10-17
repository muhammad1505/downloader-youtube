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

## Kritik dan verifikasi

Gelap dan hijau saja mudah terasa generik. Pertahankan identitas melalui sampul
media nyata, hierarki judul/artis, status unduhan yang tepat, dan akses langsung
ke lirik. Tidak menambah kartu promosi, slogan, atau statistik buatan.

Periksa HP fisik: setiap menu, panel lirik, font 1,3x/2x, terang/gelap, layar sempit,
status kosong dan galat. Catat cakupan yang benar-benar diuji pada laporan
perbaikan; jangan menyimpulkan aksesibilitas atau kesiapan rilis dari build saja.
