# Kontrol adaptif TubeFlow 1.1.25

## Masalah dan solusi

Baseline1.1.24/dbeb6dd membuat seluruh ActionButton menyerupai chip. Bentuk
seragam belum memberi hierarki formulir dan belum menyelesaikan judul yang
berebut lebar dengan aksi. Ukuran yang diminta mengikuti ruang aplikasi,
isi dan font sistem, bukan resolusi piksel fisik.

- `components.kt`: ActionButton memakai Button utama minimum48dp,
  OutlinedButton/TextButton minimum40dp; tinggi intrinsik tumbuh bersama label.
  ActionChip32dp tetap untuk aksi dekat tab. Target Material minimum48dp.
  Teks membungkus; tidak memakai auto-shrink font.
- `AdaptivePair`: isi dan aksi dipisah vertikal pada lebar lokal<320dp atau
  font≥1,3. Mini player memakai ambang360dp agar tiga ikon48dp tidak mendesak
  judul. Diterapkan pada kelola playlist, ukuran penyimpanan, error inspeksi
  dan heading playlist Download.
- `PlayerUi.kt`: kontrol sekunder memakai TextButton intrinsik dengan ikon
  dan label; dua kolom ketika sempit/font besar. Jumlah antrean dan tombol
  berikutnya/tutup tetap tersedia. Aksi lirik memakai FlowRow.
- `TubeFlowApp.kt`: navigasi rail pada lebar area aplikasi≥840dp, dapat
  digulir ketika jendela pendek. Isi dan mini player dibatasi840dp. Jendela
  lebih kecil mempertahankan navigasi bawah.

Palet, EQ, download, penyimpanan, lirik, database Room10, dependensi dan izin
tetap. Tidak ada migrasi atau aset baru. `app/build.gradle.kts` menaikkan
versi1.1.25/code39. Aturan pemeliharaan ada di DESIGN.md dan referensi UI skill.

## Verifikasi

Perintah final:

```sh
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease --no-daemon --max-workers=2
```

Lulus9m2s:348tes Kotlin,0gagal/error/skip. Lint debug/release0error dan2warning
lama: SelectedPhotoAccess serta GradleDependency/ONNX yang dipin untuk Android10.
Deprecation Gradle terpisah dari hasil lint. Debug/release tiga ABI dibangun;
Python dan AAB tidak diulang karena engine tidak berubah. Tidak menambah tes
yang hanya meniru aturan ukuran komponen.

ARM64 signed release70.894.712byte, bertambah17.864byte dari1.1.24.
SHA256 `697a09c0f07070c0f31662d67b496f58b9440711ffd8e5ec3e60d4de55ef53b5`.
Sertifikat produksi, non-debuggable, zipalign16KB, audit257ELF dan13kelas
refleksi ZIP lulus. Uji runtime16KB tidak diwakili audit alignment.

## Pengujian HP fisik — 19 Oktober 2026

Update `adb install -r` berhasil pada Realme RMX1821/Android10/API29/ARM64,
halaman4KB. VersionCode39 dan hash APK yang ditarik dari perangkat cocok.
Tidak melakukan uninstall atau reset data.

| Kondisi | Hasil yang benar-benar diamati |
| --- | --- |
| Portrait720×1520, density272dpi, font0,9 | Form Pengaturan, playlist, mini player dan full player terbaca. Aksi dekat tab tetap chip; tombol form memiliki hierarki tersendiri. |
| Tema terang/gelap | Tombol Pengaturan, folder dan Segarkan diperiksa melalui screenshot; Ikut sistem dipulihkan. |
| Font1,6, maksimum pengaturan HP | Mini player menyusun metadata di atas kontrol. Empat aksi player menjadi2×2; Antrean membuka51lagu. Aksi lirik membungkus. Label ukuran279,2MB dan Segarkan berada pada baris terpisah. |
| Dialog playlist sempit | Nama panjang mendapat lebar lebih banyak dan ikon Ubah/Hapus berada di bawah. Ubah lalu batal bekerja. Nama sangat panjang tetap dibatasi2baris/ellipsis; form Ubah memuat nama lengkap. |
| Landscape1520×720 | Rail tampil dan dapat digulir hingga Riwayat. Riwayat2hasil dan Download terbuka. Mini player tetap satu baris pada font normal. |
| Area sentuh |10sampel form/dialog/kontrol player: minimum82px, setara≈48dp pada density272dpi. Grid player font besar dibuktikan lewat bounds dua baris. |
| Pemulihan keadaan | Playlist QA yang dibuat sebelum upgrade tetap ada setelah update, lalu dihapus dengan konfirmasi. Tema, font0,9 dan rotasi terkunci portrait dipulihkan. Daftar65berkas TubeFlow identik sebelum/sesudah. |

Playback diputar sebentar lalu dijeda untuk pengujian UI; pemutar uji ditutup
setelah selesai. Log AndroidRuntime PID yang diperiksa tidak memuat FATAL
EXCEPTION. Cache, model karaoke, offset lirik dan file media tidak dihapus.
Bukti XML/PNG, ledger cleanup, hash dan log berada pada direktori lokal
`build/reports/adaptive-ui-1125/`; screenshot memuat status bar/koleksi pribadi
sehingga tidak disalin ke Git.

## Batas verifikasi dan pemeriksaan desain

Ini bukan implementasi multipane/hinge atau bukti semua tablet/foldable.
Font200%, tablet fisik, Android12+ dan runtime16KB belum diuji. Matriks
gabungan semua tema/font/orientasi, TalkBack menyeluruh dan frame profiling
belum dijalankan. Cakupan ini tidak mengulang download baru, inspeksi playlist
berisi data, jalur error jaringan atau sinkronisasi LRCLIB; kompilasi dan tes
inti lulus, tetapi itu tidak membuktikan semua alur runtime tersebut.

Pemeriksaan desain dalam cakupan screenshot: kontrol bekerja, disabled jelas,
label form dapat membungkus, dan palet/ritme/sampul yang sudah ada dipertahankan.
Ukuran berbeda mempunyai fungsi (chip/filter, form, ikon, transport), tanpa
hiasan/aset baru. ENERGY2/RHYTHM1/MOTION1 tetap. Deret filter panjang tetap
menggunakan gulir horizontal. Tidak menyatakan seluruh gerbang produksi lulus.


## Pemeliharaan

Gunakan ActionChip untuk kelompok filter/aksi massal, ActionButton untuk form,
dan IconAction untuk operasi berikon. Tambahkan AdaptivePair ketika judul
berbobot bersaing dengan aksi tetap; ukur lebar lokal melalui BoxWithConstraints.
Hindari tinggi tetap yang memotong label besar dan jangan mengalikan seluruh
UI dengan resolusi piksel. Breakpoint dapat disesuaikan jika hasil uji konten
nyata membuktikan kebutuhan; bukan berdasarkan merek atau model perangkat.

Rujukan platform: [layout adaptif Compose](https://developer.android.com/develop/ui/compose/build-adaptive-apps).
