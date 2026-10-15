# Nama, metadata, lirik dan pemulihan Koleksi — 1.1.12

## Audit dan akar masalah

Baseline:1.1.11/code25/Room9. Pengelola sudah menghapus media dan mengizinkan
pengujian dengan sumber contoh. Dua artefak lokal lama yang tidak terkait tetap
utuh. Audit menemukan:

1. Pemisah judul yang memuat label `DC MUSIK` tidak dibersihkan sebelum identifikasi;
   artis asli dan remixer yang sama-sama diketahui hanya salah satunya dihapus dari
   batas judul. Deskripsi dikalahkan bila `track` sudah terisi meskipun `artist`
   kosong; label Artist/Artis/Song dan kredit setelah50baris belum ditangani.
2. Tag artist kosong pada snapshot menimpa artist sungguhan yang baru tersedia saat
   ekstraksi download. Judul tag memakai pipe sedangkan nama file memakai kurung.
3. Pencarian otomatis mempunyai variasi berbeda dari manual dan mengirim banyak
   query berulang. HTTP429,5xx/401/403 dan respons tidak valid disamakan dengan
   koneksi pengguna gagal. Probe LRCLIB nyata mengembalikan429 pada permintaan
   field, tetapi pencarian lain200. Internet baik tidak menjamin provider menerima
   semua permintaan.
4. Validasi otomatis membuang timestamp kosong; lirik sebelumnya dapat tetap aktif
   selama jeda. Validasi manual/otomatis berbeda dalam penanganan LRC/offset.
5. Koleksi sengaja menyimpan UNAVAILABLE, validasi ditunda1jam/100entri, perubahan
   MediaStore tidak diamati, dan scan folder kosong dilempar sebagai galat sebelum
   rekonsiliasi. HP baseline membuktikan beberapa kartu peringatan masih muncul
   saat file fisiknya sudah dihapus.

## Perbaikan yang diterapkan

- Identitas: bersihkan segmen promosi; hapus credit artis/remixer hanya pada batas;
  terima pasangan kredit deskripsi yang eksplisit, unik dan cocok dengan judul,
  termasuk ketika track terstruktur sudah ada. Placeholder artis dibuang. Metadata
  sumber terstruktur yang bertentangan tetap dipertahankan, bukan ditimpa tebakan.
- Tag: judul memakai label versi dalam kurung; artist yang terlambat tersedia tetap
  memakai field artist sumber, dengan fallback kosong (bukan uploader). Ekstensi
  asli dan format audio tidak berubah. Durasi aktual audio selesai dipakai sebelum
  pencarian lirik. Album/genre/tahun yang tidak diberikan sumber tidak dikarang.
- Lirik: satu rencana discovery otomatis/manual; maksimal4search + sampai2exact
  untuk kolaborasi; resolusi identitas ambigu merupakan fase terpisah yang juga
  terbatas. Exact dilewati bila artist/durasi tidak diketahui. HTTP429
  menghentikan batch dan menahan request jaringan sesuai Retry-After (1–300detik,
  default10detik). Gangguan provider, jaringan, respons invalid dan ketidakcocokan
  mempunyai status berbeda. Cache tidak menyimpan kegagalan sebagai tidak ditemukan.
- Seluruh hasil otomatis tetap melewati identitas/versi/durasi±2detik. Memperluas
  discovery tidak berarti menerima lirik dari rekaman berbeda. Parser LRC bersama
  mempertahankan timestamp kosong dan memeriksa offset efektif terhadap akhir audio.
- Koleksi: probe kehadiran membedakan MISSING/DENIED/UNKNOWN/PRESENT. Hanya bukti
  hilang dengan akses sah yang menghapus row; izin dicabut/null provider tidak
  menghapus katalog. Observer MediaStore didebounce750ms, kembali ke foreground
  memeriksa ulang, dan file tersedia tidak diproses metadata/LRCLIB ulang hanya
  untuk validasi. Folder kosong dapat menghasilkan scan sukses0media.

## Sumber uji

- `https://youtu.be/3J1Opi7MVHw`: DJ JAUH KO PERGI, uploaderMamanFvndyRmx,
  durasi248detik. Sumber tidak memiliki field artist/track. Remixer dipisahkan;
  tidak menjanjikan artis asli atau lirik studio sinkron dengan versi DJ ini.
- `https://www.youtube.com/watch?v=rEjSkBl47OY`: sumber aktual adalah SATRU2live,
  bukan PINGAL; kredit Title/Voc pada deskripsi, durasi394detik.
- `https://youtu.be/4HDAGmRPbpo`: sumber original yang ditautkan deskripsi DJ,
  JauhKoPergi/AnggiValentia&KkzDBlg, durasi199detik. Selisih durasi49detik
  tidak membuktikan offset tetap atau transformasi timestamp tertentu.
- `https://soundcloud.com/bondanfade2black/ya-sudahlah`: trackYaSudahlah,
  genrepop, durasi252,467detik; source extractor tidak mengisi artist/album.
- LRCLIB nyata mengembalikan kandidat SATRU2 berdurasi296/336/289/400detik,
  sehingga nama yang sama saja tidak membuktikan rekaman394detik cocok.

## Checkpoint verifikasi awal

Checkpoint awal255Kotlin/84Python lulus; penguatan cari ulang/ambiguitas menambah5tes sehingga suite akhir260Kotlin lulus. Python84tes dijalankan ulang setelah perubahan terakhir: OK41,820detik. Hasil build signed final dan batas pengujian perangkat dicatat di bawah. Pada1.1.11, download linkDJ di HP
mengalami HTTP403 dari server media YouTube sebelum publikasi; ini dibedakan dari
rate limit429LRCLIB dan tidak diklaim diperbaiki oleh perubahan lirik.

Rujukan: [API LRCLIB](https://lrclib.net/docs),
[MediaStore dan izin media](https://developer.android.com/training/data-storage/shared/media).


## Penguatan setelah audit jalur cari ulang

Cari ulang kini membaca nama publikasi aktual dan durasi katalog (yang dibaca dari
file), bukan selalu memakai judul awal job. Provider suffix tabrakan tetap memakai
identitas lama yang terbukti; rename ke judul baru dengan artis lama yang masih
tercantum utuh memakai query baru. Credit filename yang berubah tidak langsung
menjadi artist: resolusi provider tetap memerlukan bukti unik dan durasi/versi yang
sesuai. Ketika artist tidak diketahui dan satu hasil search memuat beberapa
performer yang sama-sama lolos, otomatis menolak memilih sembarangan dan meminta
pemilihan manual lewat status tidak cocok.


## Catatan pengujian host

Regresi baru memeriksa kredit deskripsi dengan track terstruktur, kredit setelah
baris50, placeholder/konflik metadata, credit artis+remixer, labelDCMUSIK, parser
yt-dlp asli untuk artist terlambat, status429/5xx/JSONinvalid/koneksi, rencana lookup
terbatas, LRC hening/offset, ambiguous performer, nama publikasi/collision/rename,
serta kebijakan hilang vs izin. Tidak ada dependensi atau migrasi Room baru.

Probe metadata asli dan respons lirik utuh hanya berada di direktori lokal yang
diabaikan Git `build/reports/metadata-1112/`; lirik penuh/URL media bertoken tidak
masuk dokumentasi publik. HTTP429 nyata terakhir dariCloudflare membawaRetry-After1;
host dan jaringan HP dapat mendapat respons berbeda, jadi status host tidak
langsung dianggap hasil perangkat.


## Build final, paket dan pemasangan — 16 Oktober 2026

`JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 bash scripts/build_release.sh assembleDebug --offline --max-workers=2`
→ **BUILD SUCCESSFUL7m7s**,260Kotlin lulus;84Python lulus41,820detik.
Lint debug/release masing-masing0error/2warning yang diketahui:
SelectedPhotoAccess dan pinONNX1.23.2. Peringatan compiler MediaSession/
Notification.Action dan deprecation Gradle terpisah dari lint. Build signed
APK×3/AAB/R8 dan debug lulus. APK:apksigner/certificate, zipalign16KB, audit native
257/257/259ELF dan ZIP reflektif13kelas lulus; AAB:jarsigner, bundletool validate,
audit1.038ELF lulus. Ini tidak membuktikan runtime16KB atau semuaABI di HP.

Keystore produksi tetap `ebe3b40eb42c9b60fd044a011fc6a9e0cbfcde97c53accb2ea49934f4934dbd0`.
Room9/schema/dependensi tetap; tidak uninstall, clear-data atau publikasi toko.

| Artefak | Byte | SHA256 |
| --- | ---: | --- |
| arm64-v8a APK | 70573823 | `497716d5938b8ef8d8147fe2078a76bab21cf890e95af707828ba9abde617a44` |
| armeabi-v7a APK | 62264277 | `040ed0be8cb33aa80e8f94f12f73bebcd28e03296cf3537a2e349543c2be95c9` |
| x86_64 APK | 75014632 | `c6c34233496d711d0930e6cfaae1e32f8b3dc35529a753a271418510318fe223` |
| AAB | 255604411 | `9114e17eb56f5c04b933453a151d8ac893a0fa93050062fda8d8ed0370460d55` |

ARM64 bertambah7.196byte dari1.1.11. Artefak aktif di outputs/apk/release dan
outputs/bundle/release; tidak menumpuk salinan distribution. Tidak mengukur
benchmark RAM, baterai atau performa4000media.

RealmeRMX1821 Android10/API29 ARM64/4GB/halaman4KB: update `install --no-incremental -r`
berhasil dan hashbase.apk dari HP cocok ARM64 di atas, versi1.1.12/code26. Sebelum
pemasangan, folderTubeFlow0file sesuai penghapusan oleh pengelola. Tidak ditemukan
packageTubeFlow dalam buffer crash setelah launch. Saat inspeksi awal, halaman
hasil Norton menutupi UI; agent tidak menekan pilihan keamanan/menonaktifkannya.
Pengujian UI/download baru menunggu pengelola membuka TubeFlow kembali.

## Modul yang berubah

Identitas/tag:TitleSplit,SourceMetadata,MetadataTags,DownloadWorker.
Lookup/refetch:Lyrics,PublishedLyricIdentity,DownloadViewModel,MediaLibraryViewModel.
Katalog:MediaAvailability,MediaRepository,MediaLibraryViewModel,TubeFlowApp.
VersiGradle,tesKotlin/Python,aturan skill dan dokumentasi turut diperbarui.
Tidak ada file sumber yang dihapus atau database baru.

## Batas hasil

- Perbaikan parser, query dan klasifikasi galat terbukti di host. Respons live429
  provider telah teramati; sukses fixture bukan bukti provider selalu tersedia.
- Sinkronisasi auditori awal/tengah/akhir lagu baru belum dikonfirmasi pengelola;
  tidak menambahkan offsetglobal atau mengarang timestamp.
- Metadata yang sumbernya memang tidak menyediakan artist/album/genre/tahun dapat
  tetap parsial. Satu uploader/remixer tidak otomatis dianggap artis asli.
- Android11+ scoped/SAFcloud, izin dicabut ketika pemeriksaan aktif, runtime16KB,
  library4000media dan downloadvideo baru belum diuji fisik pada checkpoint ini.
- 403YouTube yang teramati di HP baseline tetap dibedakan dari429LRCLIB; akar
  perbedaan host/HP belum dipastikan. Tidak mengganti engine/platform tanpa bukti.
