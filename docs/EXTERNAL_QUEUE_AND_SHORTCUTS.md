# Antrean berkas luar, shortcut launcher, dan Kelola playlist

Batch ini menutup tiga keluhan: berkas yang dibuka dari aplikasi lain hanya masuk antrean sebagai
satu lagu, ikon launcher tidak punya pintasan, dan dialog Kelola playlist terasa berat.

## Perilaku

- **Antrean berkas luar.** Membuka audio/video lewat "Buka dengan" kini memutar berkas itu beserta
  **media sejenis di folder yang sama**, sehingga tombol berikut/sebelumnya berguna walau berkas
  bukan hasil unduhan TubeFlow. Berkas yang dibuka tetap menjadi trek aktif.
- **Koleksi tidak berubah.** Layar Koleksi tetap hanya menampilkan media di folder unduhan TubeFlow;
  berkas luar masuk lewat antrean pemutar, bukan ke katalog. Memindai seluruh perangkat ditolak
  karena membebani HP kelas menengah dan mengaburkan arti layar itu.
- **Shortcut tahan-ikon launcher.** Empat pintasan statis: **Unduh**, **Audio** (pemutar audio),
  **Video** (pemutar video), dan **Riwayat**. Audio/Video membuka Koleksi dengan filter Musik/Video
  (audio juga memulihkan sesi putar terakhir); Unduh membuka tab Download; Riwayat membuka Riwayat.
- **Kelola playlist.** Satu baris pembuat di atas (nama + tombol Buat), daftar playlist dengan
  **jumlah lagu per playlist**, pencarian baru muncul saat playlist lebih dari empat, dan baris
  memakai kartu tenang (ikon tonal, nama, jumlah lagu, aksi Ubah/Hapus 48dp) — bukan lagi satu
  formulir panjang bercampur daftar.

## Teknis

- `core/MediaFolders.kt` — `MediaFolderQueue.plan()` murni: menyatukan berkas yang dibuka, membuang
  duplikat, mengurutkan menurut judul, dan mengembalikan indeks trek aktif; null berarti pemanggil
  kembali ke antrean satu berkas. `MediaFolders.siblings()` membaca MediaStore: kolom folder dan
  `_ID` diambil dari URI yang dikirim, koleksi disusun dari segmen URI itu sendiri, dan baris yang
  dibuka dikecualikan dari hasil.
- **Jangan kembali memakai `MediaStore.Audio/Video.getContentUri(volume)` untuk koleksi antrean.**
  Volume `external` dan `external_primary` menunjuk baris yang sama tetapi menghasilkan string URI
  berbeda, sehingga berkas yang dibuka muncul dua kali di antrean (ditemukan saat uji perangkat).
- `PlayerViewModel.playTracks(tracks, startAt)` membuka antrean daftar trek; `ui/TubeFlowApp.kt`
  menyambungkan berkas masuk ke jalur ini (audio) atau `video.open()` (video).
- `core/AppNavigation.kt` menambah `AUDIO_PLAYER`/`VIDEO_PLAYER` (tab Koleksi) yang dibaca dari
  `AppDestination.fromAction`; `res/xml/shortcuts.xml` + `android.app.shortcuts` di manifest +
  empat ikon `ic_shortcut_*` menyediakan pintasan launcher tanpa Activity kedua.
- `PersonalLibraryDao.observeCounts()` (COUNT per playlist, tanpa perubahan skema) mengalir ke
  `MediaLibraryViewModel.playlistCounts`; `ui/PersonalPlaylistDialog.kt` dirombak memakai
  `AdaptivePair(minInlineWidth = 240.dp)` agar nama dan aksi tetap sebaris di layar 360dp.

## Verifikasi host

- `./gradlew testDebugUnitTest lintDebug assembleDebug`: **574 tes, 0 gagal** (6 tes baru:
  `MediaFoldersTest` 5, `AppNavigationTest` 1), lint **0 error**, 3 warning lama.
- `bash scripts/build_release.sh --max-workers=2`: **BUILD SUCCESSFUL**; APK/AAB code63 bertanda
  tangan kunci produksi; `lintRelease` 0 error.

## Uji perangkat — Realme RMX1821, Android 10 (1.1.48/code63)

- Dua berkas uji disalin ke `Download/UjiLuar/` lalu diindeks MediaStore. Membuka `Uji Satu`
  lewat `ACTION_VIEW` (content:// audio) memutar berkas itu dan **Antrean putar menampilkan
  "2 lagu": Uji Satu (Sedang diputar) dan Uji Dua**; pemutar menulis "Lagu 1 dari 2".
- Sebelum perbaikan koleksi URI, antrean menampilkan **3 lagu** (Uji Satu dua kali) — bukti bug
  ejaan volume di atas.
- `dumpsys shortcut` menampilkan empat pintasan dengan label "Unduh/Audio/Video/Riwayat", ikon
  `drawable/ic_shortcut_*`, dan intent `id.local.tubeflow.action.OPEN_*` ke `MainActivity`.
  Menjalankan `OPEN_VIDEO` membuka **"Koleksi video"** dengan filter Video aktif.
- Dialog Kelola playlist: pembuat playlist berfungsi (0 → 1 playlist), baris menampilkan "0 lagu"
  dari Room, aksi Ubah/Hapus 48dp, dan konfirmasi hapus mengembalikan hitungan ke 0.
- Tidak ada `FATAL EXCEPTION` milik `id.local.tubeflow` di logcat sesi uji; berkas uji, baris
  MediaStore, dan playlist uji dihapus kembali setelah pengujian.

## Batasan dan sisa

- Berkas dari provider non-MediaStore (mis. folder sementara aplikasi pesan) tidak punya kolom
  folder, jadi antreannya tetap satu berkas — perilaku lama, bukan regresi.
- TalkBack untuk dialog Kelola playlist yang baru, tampilan long-press launcher di layar (hanya
  didaftarkan lewat `dumpsys`), matriks font besar/tema terang, dan uji video luar folder belum
  dijalankan pada batch ini.
- Uji klip Status panjang dari rilis sebelumnya tetap terpisah dan tidak diulang di sini.
