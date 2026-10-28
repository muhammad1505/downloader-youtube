# Ringkasan ringkas dan dialog "Apa yang baru"

Batch ini menjawab dua keluhan: layar **Ringkasan** terasa terlalu rame karena semua angka dan
penjelasan tampil sekaligus, dan pengguna tidak punya cara melihat perubahan setelah aplikasi
diperbarui.

## Perilaku

- **Ringkasan jadi empat blok**, masing-masing satu panel tenang:
  1. **Koleksi di perangkat** — jumlah audio/video, donut komposisi, bilah penyimpanan internal,
     dan satu baris status akses. Ukuran per jenis, artis/album, jumlah tidak dapat diakses, serta
     kapasitas/tersedia internal ada di balik tombol **Lihat detail**. Saat koleksi kosong, blok
     menampilkan ajakan "Mulai download" dan "Segarkan katalog" — bukan statistik nol yang ramai.
  2. **Kesehatan Smart Audio** — hanya angka yang perlu tindakan di depan (menunggu indeks,
     metadata belum diperiksa, artwork belum tersedia, lirik belum diperiksa) plus tanggal pindai
     terakhir; sisa metrik ada di balik **Lihat detail**.
  3. **Aktivitas unduhan** — satu grafik dengan chip periode 7/30/90 hari, mode Batang/Akumulasi,
     satu baris "selesai · gagal · dibatalkan · % berhasil", dan sumber tiga teratas. Ikhtisar
     penjelasan angka pindah ke ikon info (dialog), bukan paragraf di layar.
  4. **Aktivitas terbaru** — maksimum tiga entri + tombol menuju Riwayat.
- **Dialog "Apa yang baru"** muncul **sekali per versi** setelah update, berisi sorotan rilis
  berjalan. Dapat dibuka ulang dari **Pengaturan → Tentang → "Apa yang baru di versi ini"**.
- Versi tanpa entri sorotan tidak menampilkan dialog apa pun; tombol di Tentang menampilkan catatan
  terbaru yang tersedia. Ini mencegah catatan lama tampil dengan judul versi baru.

## Teknis

- `app/src/main/assets/changelog.json` — satu sumber data changelog, berisi
  `versionCode`, `versionName`, dan daftar `highlights`; terbaru di atas. Menambah rilis cukup
  mengedit JSON ini, tanpa menyentuh daftar Kotlin. `core/AppChangelog.kt` memuat dan mem-parse
  aset sekali per proses; `forVersion()` mencocokkan versi persis, `latestAtOrBefore()` menyalakan
  tombol di Tentang, dan `shouldOffer()` hanya membuka dialog bila versi berjalan lebih baru dari
  yang sudah ditutup **dan** versi itu punya catatan sendiri.
- `ChangelogStore` menyimpan `last_seen_version` di SharedPreferences berkas `changelog`; ditulis
  hanya saat dialog ditutup.
- `ui/ReleaseNotesDialog.kt` memakai `AlertDialog` dengan daftar sorotan yang bisa digulir; dipanggil
  dari `ui/TubeFlowApp.kt` (efek sekali per proses) dan dari `ui/SettingsScreen.kt` bagian Tentang.
- `ui/MediaDashboardScreen.kt` dirombak memakai `AnimatedVisibility` untuk dua panel rincian
  (`collectionDetail`, `healthDetail`) dan `Metric` yang otomatis menumpuk saat `fontScale > 1.3`.
- **Saat menaikkan versi, tambahkan entri JSON untuk versi itu** — `AppChangelogTest` membaca
  aset dan `runningVersionAlwaysHasItsOwnNotes` gagal bila `versionCode` berjalan tidak punya
  sorotan. Tes juga memeriksa urutan terbaru-di-atas, kode unik, dan panjang sorotan.

## Verifikasi host

- `./gradlew testDebugUnitTest lintDebug`: **578 tes, 0 gagal** (4 tes baru: `AppChangelogTest`),
  lint **0 error**, 3 warning lama.
- `bash scripts/build_release.sh --max-workers=2`: APK/AAB `code64`/`1.1.49` bertanda tangan kunci
  produksi.

## Uji perangkat — Realme RMX1821, Android 10 (1.1.49/code64)

- Ringkasan: panel Koleksi menampilkan Audio 8 / Video 0, donut "8 media tersedia", penyimpanan
  "38,0 GB dari 49,9 GB", dan "Semua media dapat diakses."; **Lihat detail** membuka rincian
  (Audio 124,9 MB, Video 0 B, Artis/album 6/0, tidak dapat diakses 0, internal 49,9 GB / 11,9 GB)
  beserta catatan cara hitungnya.
- Kesehatan Smart Audio menampilkan empat metrik teratas dan "Pindai terakhir: 27 Okt 2026 17:47"
  tanpa memenuhi layar; Aktivitas unduhan menampilkan chip 7/30/90 hari, info, Batang/Akumulasi,
  dan "Puncak: 2 selesai / hari".
- Dialog "Apa yang baru di 1.1.49" menampilkan empat sorotan dan tombol "Mengerti"; setelah ditutup,
  membuka ulang aplikasi tidak menampilkan lagi (sekali per versi).
- Pengaturan → Tentang menampilkan "Versi 1.1.49 (64)" beserta tombol "Apa yang baru di versi ini"
  yang membuka dialog yang sama.
- Catatan 28 Oktober 2026: aset 1.1.49 kini memuat enam sorotan (tambahan crop mode Penuh dan
  fokus kolom tautan dari shortcut Unduh). Uji perangkat di atas masih mencatat empat sorotan dari
  build code64 sebelumnya; pengguna yang sudah menutup dialog versi 64 baru melihat sorotan
  tambahan setelah `versionCode` berikutnya dinaikkan.
- Sesi 28 Oktober 2026 memindahkan sumber changelog dari daftar Kotlin ke
  `app/src/main/assets/changelog.json`. `AppChangelogTest` membaca aset itu dan memvalidasi format.
- Tindak lanjut 28 Oktober 2026: build **1.1.50/code65** berhasil dengan **584 tes Kotlin lulus**,
  lint 0 error/3 warning lama, APK ARM64 bertanda tangan dan tautan publik terverifikasi.
  [Rincian build dan tautan](PRODUCTION_SIGNING.md).
