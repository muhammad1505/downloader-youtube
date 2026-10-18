# Kontrol ringkas seluruh menu 1.1.24

## Akar dan penerapan

Baseline1.1.23/fdd46c5. Sebagian kontrol sudah memakai chip, tetapi
`ActionButton` masih menghasilkan tombol48/52dp dan OptionMenu berupa tombol
dua baris dengan padding besar. Mengganti pemanggil satu per satu menyisakan
ketidakkonsistenan pada Pengaturan, Download, lirik, karaoke, timer dan playlist.

`ui/components.kt` sekarang menggunakan AssistChip untuk ActionButton dan
ActionChip bersama. Tinggi visual minimum32dp, ikon18dp, tipografi labelLarge,
bentuk mengikuti chip tema, dan area sentuh Material tetap minimum48dp.
Aksi utama tetap filled, sekunder outline, aksi tenang tanpa border dan
destruktif berwarna galat. Enabled, callback dan konfirmasi tidak diganti.
Label panjang boleh membungkus, menggantikan pemotongan ellipsis satu baris.

OptionMenu memuat label dan nilai bersama dalam chip; pilihan dropdown dan
callback tetap sama. Tombol penuh lebar yang memang dipakai formulir tetap
mengikuti lebar formulir, dengan tinggi visual ringkas. Transport sekunder
full player berubah dari tinggi tetap64dp menjadi minimum48dp. Tombol putar
utama, input, switch, radio serta navigasi tetap mengikuti fungsi masing-masing.

Cakupan pemanggil komponen: Ringkasan/empty state, Download/opsi lanjutan,
Koleksi/playlist/rename/lirik, Antrean/Riwayat, Pengaturan/alat penyimpanan/
cadangan, timer, karaoke serta diagnostik galat. Tidak menambah sistem tombol
paralel atau mengubah data/download/audio engine.

Versi1.1.24/code38. File kode: `components.kt`, `PlayerUi.kt`, Gradle; panduan
di DESIGN.md dan referensi skill diselaraskan. Room10, dependensi dan izin
tetap sama.

## EQ dan headset

Pengelola melaporkan EQ1.1.23 terdengar jelas dengan bass bulat dan menyebut
headset QKZ AK6 Pro. Kurva EQ tidak diubah pada1.1.24. Ini umpan balik dengar
pengguna, bukan pengukuran respons frekuensi, ANC atau bukti playback Hi-Res.

## Verifikasi

Perintah final memakai konfigurasi signing produksi yang sudah ada:

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon --max-workers=2
```

Lulus dalam9m12s: 348 tes Kotlin tanpa gagal/error/skip; lint debug/release
0error/2warning lama (SelectedPhotoAccess dan pin ONNX/GradleDependency).
Tidak menambah tes yang hanya meniru komponen visual. Build debug/release
tiga ABI selesai; Python/AAB tidak diulang karena engine tidak berubah.
Deprecation compiler/Gradle terpisah dari hasil lint.

ARM64 release **70.876.848byte**, berkurang2.500byte dari1.1.23.
SHA256 `b9639c8be303bad3e0217d7e0ea5c34df158352cce392437fbcde43e7483adf5`.
Signature produksi, non-debuggable, zipalign16KB,257ELF dan13kelas refleksi ZIP
lulus. Update `adb install -r` berhasil pada Realme RMX1821/Android10/API29/
ARM64/halaman4KB. Versi/code38 dan hash APK terpasang cocok; tanpa reset data.

### Cakupan HP

- **24 kontrol:** tinggi target82px, setara48dp pada density272dpi. Sampel
  mencakup tab Musik, aksi Koleksi, tema/folder/cache, Buat playlist, pilihan
  timer dan Mulai timer disabled, Proses cuplikan, koreksi waktu lirik,
  kontrol sekunder full player serta opsi format/resolusi Download. Tinggi
  visual chip diperiksa melalui screenshot, bukan disamakan dengan bounds
  target sentuh. Font0,9 pengguna dipertahankan.
- **Pengaturan:** tampilan gelap dan terang, label folder panjang, dropdown
  tema dan Segarkan ukuran279,2MB diperiksa. Pilihan Ikut sistem dipulihkan
  setelah tes; ledger mencatat restored=true. Cache tidak dihapus.
- **Playlist:** form kosong/disabled, input dan Buat playlist bekerja. Satu
  playlist QA unik dibuat lalu dihapus melalui konfirmasi; ledger deleted=true.
  Tidak mengubah file media atau playlist pengguna.
- **Player/timer/karaoke:** kontrol bawah dan jumlah51lagu terbaca, timer
  beserta input custom dan aksi disabled terlihat, dialog karaoke idle dengan
  tombol Proses cuplikan ringkas. Tidak mengaktifkan timer atau menjalankan
  pemisahan vokal/model baru.
- **Lirik:** dialog pencarian dan koreksi waktu Lamunan dibuka. Cari lirik,
  Majukan/Mundurkan/Reset serta tombol simpan/batal terbaca. Tidak menyimpan
  pilihan atau offset. Pencarian menampilkan16hasil serta **HTTP503** LRCLIB
  pada salah satu jalur; pesan galat tercatat sebagaimana respons aktual.
  Ini bukan bukti sinkronisasi atau kelulusan layanan lirik. Tidak mengubah
  DNS/VPN, menghapus cooldown, atau mengulang request massal untuk menutupinya.
- **Navigasi/Download:** Ringkasan, Antrean kosong, Riwayat, Koleksi dan
  Download diperiksa. Inspeksi satu link YouTube yang diberikan pengelola
  berhasil, opsi video/format/resolusi dan CTA ringkas terlihat. Tidak menekan
  download atau menambah media. Link tes dibersihkan dan filter Semua dipulihkan.
- **Log:** tidak ada FATAL EXCEPTION pada AndroidRuntime PID yang diperiksa.

Layar sempat berubah ketika pengelola mencoba UI bersamaan; pengelola
mengonfirmasi ketukan manual lalu membiarkan tes berjalan. Target dibaca ulang;
kejadian ini tidak dicatat sebagai bug navigasi otomatis. Tidak mengembalikan
sesi/seek lama secara paksa ketika pengguna melanjutkan pemakaian HP.

### Pemeriksaan desain

- Hard gate, PASS untuk cakupan yang dilihat: build, klik dan ukuran tercatat;
  label tidak terpotong pada screenshot yang diperiksa, disabled jelas.
- Purpose gate, PASS: satu komponen/material/palet yang sudah ada, tanpa
  hiasan atau aset baru; perbedaan warna menyatakan peran tindakan.
- Liveliness, PASS: ENERGY2/RHYTHM1/MOTION1 dipertahankan, sampul dan judul
  tetap fokus; kontrol yang diringkas mengikuti ritme chip yang sudah ada.
- Craftsmanship, PASS pada viewport yang diuji: screenshot Pengaturan
  terang/gelap, Download, timer, karaoke, lirik, playlist dan player diperiksa.

Belum diuji: font besar, tablet/layar sempit tambahan, TalkBack, seluruh state
download aktif/gagal, pemrosesan karaoke, pengiriman share ke penerima, fault
operasi berkas, runtime16KB dan perangkat lain. Tidak ada tes download media
penuh atau benchmark performa baru. Ini polesan komponen, bukan audit kesiapan
produksi penuh atau penyelesaian gangguan HTTP503 LRCLIB.

Bukti privat: `build/reports/compact-controls-1124/`, termasuk ukuran kontrol,
ledger tema/playlist, hash terpasang dan screenshot. Screenshot tidak masuk Git
karena memuat konten/perangkat pengguna. AGENTS.md tidak diubah.
