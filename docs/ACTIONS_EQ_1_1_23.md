# Aksi koleksi, kontrol ringkas dan EQ 1.1.23

## Cakupan dan akar masalah

Baseline 1.1.22/code36 (`bae427b`). Versi baru 1.1.23/code37.
Room10, izin, engine download dan dependensi tetap sama.

- Antrean putar masih memakai `ActionButton` untuk Bersihkan di sebelah
  `FilterChip`. Kini memakai `ActionChip` yang sama tinggi visualnya, warna
  galat dan konfirmasi tetap berlaku. Aksi massal antrean download serta
  pilihan preset EQ juga memakai chip. Target sentuh tetap minimal48dp;
  tombol utama/formulir/konfirmasi mempertahankan hierarki semula.
- Aksi kartu Koleksi dibatasi cabang audio. Video kini mempunyai Putar,
  Bagikan, Detail, Ubah nama dan Hapus sebagai ikon langsung. Hapus tetap
  berkonfirmasi; rename memakai operasi berkas yang sudah ada. Tidak
  memasukkan video ke antrean audio, mencari lirik audio untuk video, atau
  mengubah formatnya. Filter playlist/favorit audio disembunyikan pada Video.
- Audio dan video mendapat Bagikan langsung serta melalui menu. Keduanya
  memakai `launchMedia`, MIME aktual, `EXTRA_STREAM`, ClipData dan grant baca
  sementara yang sama dengan Riwayat. Kegagalan bagikan dibedakan dari galat
  membuka pemutar. Tidak mengirim file sebelum pengguna memilih tujuan.
- EQ otomatis lama menaikkan bass hingga5dB, menurunkan mid hingga−1dB,
  treble hingga2,5dB. Kurva baru memakai puncak bass/treble3dB, low-bass
  120Hz2,5dB,250Hz1dB, mid500Hz–2kHz netral,4kHz0,5dB,8kHz2dB.
  Interpolasi logaritmik dan clamp kemampuan band dipertahankan. Default
  otomatis berubah; preset perangkat, level manual dan pilihan nonaktif
  pengguna tidak direset. Dialog EQ dapat digulir.

## Audio lanjutan: kemampuan dan batas

FLAC/WAV sudah tersedia di Download; Auto mempertahankan format sumber bila
memungkinkan. Konversi sumber lossy ke FLAC tidak mengembalikan detail yang
telah hilang. Tidak ada upsampling atau transcoding otomatis baru.

Hi-Res perlu verifikasi metadata sumber dan jalur decoder/output, termasuk
perangkat, DAC dan headset. Daftar sample rate perangkat bukan bukti sample
rate yang sedang keluar atau playback bit-perfect. Pemutar MediaPlayer saat
ini tidak memberi bukti itu; jangan menampilkan status Hi-Res aktif hanya
berdasarkan ekstensi FLAC. Hi-Fi merupakan sasaran fidelitas, bukan preset EQ.

ANC memerlukan mikrofon/sirkuit dan algoritme headset yang sesuai. Android
NoiseSuppressor bekerja pada jalur AudioRecord untuk rekaman mikrofon; itu
bukan ANC pemutaran. ENC panggilan/rekaman juga di luar fungsi player saat ini.
Tidak menambahkan izin mikrofon, engine audio kedua, atau toggle semu.

Rujukan primer yang diperiksa19Oktober2026:
[format Android](https://developer.android.com/media/platform/supported-formats),
[kemampuan perangkat audio](https://developer.android.com/reference/android/media/AudioDeviceInfo#getSampleRates()),
[NoiseSuppressor](https://developer.android.com/reference/android/media/audiofx/NoiseSuppressor),
[cara kerja ANC headset Sony](https://helpguide.sony.net/mdr/2966/v1/en/contents/TP1000777042.html).

Tindak lanjut yang layak: detail sample rate/bit depth sumber jika benar-benar
terbaca, informasi jalur output dengan status tidak diketahui bila perlu,
serta pengujian sumber lossless dan DAC fisik. Ini usulan, belum implementasi
atau klaim dukungan Hi-Res/bit-perfect.

## File utama

`ui/PlayerUi.kt`, `MediaLibraryScreen.kt`, `QueueScreen.kt`, `components.kt`,
`core/AutoEq.kt`, `res/values/strings.xml`, `AutoEqTest.kt`, Gradle dan DESIGN.md.

## Verifikasi

Dengan konfigurasi signing produksi yang sudah ada:

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon --max-workers=2
```

Build final lulus dalam 9m21s. **348 tes Kotlin** lulus tanpa gagal/error/skip,
termasuk regresi puncak bass/treble seimbang, mid netral, interpolasi, clamp dan
preferensi manual. Lint debug/release: 0 error/2 warning lama, SelectedPhotoAccess
dan GradleDependency ONNX yang sengaja di-pin. Debug dan release tiga ABI
terbentuk; AAB dan tes Python tidak diulang karena engine tidak berubah.

ARM64: signature produksi, non-debuggable, zipalign16KB, 257 ELF dan 13 kelas
refleksi ZIP lulus. Ukuran **70.879.348 byte** (+5.808 byte dari 1.1.22).
SHA256 `f0f8439d57bca54b9ffd65305a55308717245de31bebccc8a282468ba85b1b64`.

### HP fisik

Update `adb install -r` pada Realme RMX1821/Android10/API29/ARM64/halaman4KB
berhasil. Versi1.1.23/code37 dan hash base.apk cocok; tidak uninstall/reset.
Tema/density272dpi/font0,9 pengguna dipertahankan.

| Skenario | Hasil aktual |
| --- | --- |
| Antrean putar | Acak/Ulangi/Bersihkan masing-masing82px (48dp). Konfirmasi terbuka, Batal mempertahankan51lagu. |
| Kartu video | Lima ikon aksi masing-masing82px. Screenshot diperiksa; filter khusus audio tidak muncul di Video. |
| Bagikan | Ikon langsung audio dan video membuka lembar berbagi Android; dibatalkan tanpa memilih penerima. Pengiriman ke aplikasi tujuan belum diuji. |
| Detail video | Nama, MIMEvideo/mp4, ukuran, durasi dan resolusi terbaca. |
| Rename/delete video | Dialog yang sesuai terbuka; Batal menutupnya. Tidak mengganti nama atau menghapus file pengguna. |
| Putar video | Ikon membuka video aktual di MX Player; kembali ke TubeFlow berhasil. Tidak terlihat pesan file hilang pada layar Koleksi akhir. |
| EQ | Otomatis tetap terpilih; band perangkat60Hz+3dB,230Hz+1dB,910Hz0dB,3,6kHz0dB,14kHz+3dB. Menu preset terbuka tanpa mengganti pilihan. |
| Sesi pemutar | Lagu15/51, acak/ulangi dan posisi249.210ms bertahan; status akhir paused seperti sebelum update. |
| Crash | Log AndroidRuntime PID aplikasi yang diperiksa:0FATAL EXCEPTION. |

Awalnya PiP video pengguna menutupi hierarki aksesibilitas. Tes ditahan sampai
pengelola menutupnya. Dua pencarian kontrol terhenti ketika layar berubah saat
interaksi manual; layar dibaca ulang, lalu tes dilanjutkan dari keadaan aktual.
Tidak menganggap kegagalan alat menemukan tombol sebagai crash aplikasi.

### Pemeriksaan desain yang dicakup

- Hard gate, PASS pada layar yang diperiksa: build dan klik aksi baru tercatat;
  kontrol tidak meluap, nama aksesibilitas ada, operasi berisiko berkonfirmasi.
- Purpose gate, PASS: memakai komponen/palet/ikon yang sudah ada; tidak
  menambahkan aset, hiasan, statistik atau klaim kualitas audio rekaan.
- Liveliness, PASS: ENERGY2/RHYTHM1/MOTION1 dari DESIGN.md dipertahankan,
  sampul/judul menjadi fokus dan baris ikon/chip menjaga ritme daftar.
- Craftsmanship, PASS untuk polesan terfokus: tampilan antrean, audio, video,
  EQ dan share diperiksa dari screenshot HP; state dan tujuan aksi cocok.

Ini bukan audit aksesibilitas atau kesiapan produksi penuh. Mode terang, font
besar, TalkBack, state massal antrean download aktif/gagal, URI dicabut, fault
rename/delete, seluruh codec, runtime16KB dan perangkat lain belum diuji ulang.
Tidak ada tes download jaringan baru, benchmark performa, atau klaim subjektif
bass/treble, Hi-Res/bit-perfect/ANC dari hasil tes ini. Hi-Res dan ANC tetap
penilaian kemampuan serta usulan tindak lanjut, bukan fitur baru yang selesai.

Bukti privat: `build/reports/actions-eq-1123/`; screenshot tidak dipublikasi
karena memuat konten/perangkat pengguna. Tidak ada media QA yang perlu dibersihkan.
