# Perbaikan alur karaoke 1.1.16

## Audit dan rancangan

Keluhan: dialog terus membawa data sebelumnya dan cuplikan terlihat seperti hasil
lagu penuh. Audit dilakukan pada source 1.1.15 serta perubahan lokal BUG-058.
Karaoke masih lokal (UVR/ONNX), bukan layanan online. Tidak mengubah model/bobot,
format output, database Room 9, atau kemampuan downloader.

Akar yang ditemukan:

- Pilihan lagu/mode/waktu tidak dibandingkan dengan konteks hasil sebelum tombol
  dengarkan/simpan ditampilkan. Mode cuplikan juga tidak dipersistenkan pada jurnal.
- `info` mengalir asinkron; null atau nilai dari request lama bukan bukti selesai.
  Bersih/ganti lagu dapat membuang identitas sebelum status sebenarnya diketahui.
- Bersihkan hanya menghapus jurnal/status, bukan file FLAC pratinjau privat.
- Batal langsung menghapus identitas tanpa menunggu lease cleanup native.
- Start menutup player/antrean sebelum request diterima.
- Pratinjau/publikasi belum mencocokkan manifest dengan URI sumber dan mode;
  pembacaan saved.json lama dapat menandai request baru sudah disimpan.
- Adopsi work tak terlacak tidak memulihkan identitas sumber yang benar. Publikasi
  perlu pemulihan jurnal sebelum worker membuang preview lama.

Rencana UI terfokus: gunakan primary/surface/onSurface/error/outline dan bentuk
Material3 dari Theme.kt: primary #C6B7FF, surface #111113, onSurface #F0EDF4,
outline #858B9D dan error #FFB4AB (fallback gelap; aksen sampul/tema terang tetap
mengikuti tema). Gunakan tipografi titleSmall/bodySmall, jarak 8/12/16dp dan target
48dp. Tidak mengganti tema atau player.

```text
Karaoke
  Lagu yang dipilih
  Cuplikan [mode] / mulai 01:30
  [Proses cuplikan]

  Hasil cocok: Cuplikan 01:30–02:00 (30 detik)
  [Dengarkan cuplikan] [Buat lagu penuh]
  [Simpan cuplikan…] -> konfirmasi durasi dan format
  [Buang pratinjau…] -> hanya cache, hasil Koleksi tetap ada

  Hasil berbeda: judul + mode sumber lama, diberi label
  [Buka hasil sebelumnya] / [Buang pratinjau…]
  Proses baru hanya setelah tidak ada worker/cleanup berjalan.
```

Kritik rancangan: jangan menyamakan cuplikan dengan output final atau menampilkan
aksi simpan untuk formulir baru yang belum diproses. Hasil yang belum disimpan
bertahan saat berpindah lagu sampai pengguna memilih proses baru/buang. Menutup
dialog mempertahankan proses; membuang pratinjau benar-benar membersihkan file
privat, tanpa menghapus model, sumber, atau hasil publik.

## Verifikasi

Implementasi tersedia pada **1.1.16/code 30**, terpasang update-r pada Realme
RMX1821 Android 10/API 29, ARM64, RAM 4 GB, halaman 4 KB. Hash base.apk terpasang
cocok dengan APK final. Tidak uninstall/reset, menambah izin/dependensi, atau
mengubah Room 9, model/bobot, dan kunci produksi.

## Aturan cleanup yang diminta pengelola

Batal menunggu worker melepas lease native/FFmpeg, kemudian membuang seluruh
direktori privat job: salinan sumber, PCM input/output, log, manifest, dan FLAC
cuplikan yang belum dipublikasikan. Jalur ini juga menangani race saat hasil
cuplikan selesai tepat sebelum pembatalan. Status/jurnal baru dikosongkan setelah
pemeriksaan penghapusan berhasil; kegagalan cleanup tetap diberitahukan.

Jika pengguna berhenti sesudah mencoba cuplikan, **Buang pratinjau** menjalankan
cleanup yang sama untuk job selesai. **Tutup** hanya menyembunyikan dialog.
Model terverifikasi (sekitar 67 MB) tetap disimpan untuk pemakaian berikutnya.
Audio asli dan hasil yang telah disimpan secara eksplisit ke Koleksi tidak
termasuk data sementara. Cuplikan tidak otomatis disalin ke Koleksi.

## Checkpoint host dan reproduksi perangkat

Build debug awal berhasil (4m55s), 304 tes Kotlin lulus. Empat peringatan Kotlin
safe-call baru ditemukan lalu dihapus sebelum build berikutnya. Regresi helper
menguji sumber/mode/posisi hasil, hash/rentang, cleanup setelah preview selesai,
idempotensi, penolakan symlink/traversal, serta perlindungan model/asli/hasil publik.
Ini bukan pengujian CoroutineWorker/WorkManager Android sebelum perbaikan.

Pada HP 1.1.15, topbar membuka cuplikan DJ lama 0:58.756–1:28.756 sementara mini
player memuat Lamunan. Dialog lama memiliki dua tombol utama dengarkan/simpan
cuplikan serta tombol proses lagi tanpa pemisahan hasil dan formulir. Screenshot
privat `build/reports/karaoke-logic-1116/ui-before.png` diperiksa; tidak dipublikasikan.
Baseline sebelum pembaruan: 63 file publik, semua SHA256 dicatat. Player Lamunan
memiliki satu lagu di antrean dan dijeda pada 143381 ms untuk pengujian.

Build release pertama dihentikan secara terkontrol sebelum dipasang untuk
menambahkan cleanup fragmen model setelah process death. Build final memakai log
`build-release-final.log`; artefak dari build yang dihentikan tidak diserahkan.

Permintaan cleanup disimpan di jurnal sebelum pembatalan/penghapusan dan dilanjutkan
saat startup jika belum selesai. Jika cleanup gagal, proses baru diblokir sampai
pengguna berhasil membuang pratinjau. Selain direktori job, hanya `model.part`
dan `model-adapted.part` dibuang; `.onnx` terverifikasi tidak disentuh. Penghapusan
jurnal juga diperiksa sebelum status dinyatakan bersih. Toggle cuplikan memakai
satu target sentuh berlabel minimal 48dp. Penanda tersimpan tidak lagi dapat
ditimpa hasil pembacaan saved.json lama yang kosong.

Jalur notifikasi juga diselaraskan: worker yang dihentikan membuang seluruh job
meskipun flag pratinjau sempat selesai; WorkManager CANCELLED memicu cleanup di
ViewModel aktif atau saat startup berikutnya. Pemutar pratinjau privat dilepas
sebelum proses baru mengganti file tersebut, agar descriptor file tidak menahan
ruang setelah unlink. Pemutar audio asli cukup dijeda; antreannya dipertahankan.
Build sebelum guard ini juga dihentikan sebelum instalasi; hanya build final
yang selesai akan dipasang.

## Hasil final — 17 Oktober 2026

`bash scripts/build_release.sh assembleDebug --no-daemon --max-workers=2`
berhasil **10m8s**: debug, release/R8 APK tiga ABI, dan AAB bertanda tangan.
**306 Kotlin** (termasuk 10 regresi karaoke) dan **84 Python** lulus.
Lint debug/release masing-masing **0 error/2 warning lama**: SelectedPhotoAccess
dan GradleDependency ONNX yang dipin. Deprecation Gradle/API terpisah; log Python
memiliki ResourceWarning fixture lama. Tidak ada warning Kotlin baru di build final.

Audit APK ketiga ABI: metadata/non-debuggable, sertifikat produksi, signature,
ELF/alignment 16 KB, refleksi ZIP, hash engine dan kesamaan bootstrap lulus.
AAB jarsigner/native lulus; bundletool tidak dijalankan ulang. Runtime 16 KB
belum diuji, sehingga alignment paket tidak dianggap bukti kompatibilitas penuh.

| Pengujian HP final | Hasil dan batas |
|---|---|
| Preview lama setelah update | Cuplikan DJ dipulihkan; konfirmasi simpan menampilkan rentang 0:58.756–1:28.756. Mengubah ke mode penuh menyembunyikan aksi simpan cuplikan lama. |
| Ganti ke Lamunan | Hasil DJ berlabel Hasil sebelumnya; tidak dapat disimpan seolah milik Lamunan. |
| Batal dari dialog | Request penuh masuk Membaca audio; Batalkan dan bersihkan selesai, hasil lama tidak kembali. |
| Batal dari notifikasi | Mencapai Memisahkan vokal; kartu TubeFlow diperluas lalu tombol Batalkan dipilih, cleanup selesai. Notifikasi lain tidak disentuh/dipublikasikan. |
| Cuplikan selesai | Mulai 258,5 detik; hasil 0,877687 detik, FLAC stereo 44,1 kHz, 67.566 byte. Decode FFmpeg penuh lulus. Tidak menguji kualitas pengurangan vokal dari potongan akhir ini. |
| Simpan | Satu FLAC baru hanya muncul sesudah konfirmasi eksplisit. Tidak ada LRC lagu penuh disalin ke cuplikan. |
| Buang setelah dengarkan/simpan | Preview privat dibuang dan sesi player preview ditutup; file publik yang sudah disimpan tetap identik hash-nya. |
| Cleanup QA | Hanya FLAC yang dibuat tes, dengan path/hash tercatat, dihapus lewat konfirmasi Koleksi. Row hilang tanpa pesan unavailable. |
| Antrean/seek | Request lalu batal mempertahankan satu lagu publik dan posisi jeda tepat 143381 ms, sebelum/sesudah identik. Dengarkan preview memang memutar media preview; lagu asli dipilih kembali setelah QA. |
| Restart sesudah cleanup | Tanpa worker aktif, force-stop lalu buka manual: tidak ada status karaoke lama; lagu asli pulih jeda pada posisi sama. |
| Integritas media | Tepat 63 file sebelum/sesudah; seluruh SHA256 identik, tanpa file baru/hilang/berubah. Lagu asli dikembalikan ke keadaan putar sebelumnya setelah tes dan tidak ada audio aplikasi lain aktif. |
| Crash | Tidak ada FATAL EXCEPTION dalam log terbatas PID kandidat sebelum/sesudah restart yang diperiksa; bukan bukti semua sesi bebas crash. |

Helper UI awal perlu diperbaiki untuk memperluas kartu notifikasi yang terlipat
dan menekan nav melalui parent semantics. Kegagalan helper tersebut tidak dianggap
bug aplikasi atau tes lulus; hasil tabel berasal dari pemeriksaan lanjutan yang
benar-benar dijalankan. Sesudah instalasi, layar awal sempat dialihkan oleh Norton;
satu pembukaan ulang TubeFlow berhasil. Pengaturan keamanan tidak diubah dan
keputusan/deteksi antivirus tidak diaudit dalam tugas ini.

Pemeriksaan direktori privat memakai guard penghapusan di aplikasi dan fixture
filesystem host; sandbox release tidak dibaca langsung lewat ADB. Tidak
mengklaim pengukuran total data aplikasi menjadi nol. Model terverifikasi dan
database/cache lain tetap ada. Jurnal pending cleanup ditinjau pada kode, tetapi
process death tepat di tengah cleanup, fault filesystem Android, storage/RAM
rendah, background tanpa ViewModel, lagu penuh selesai, perangkat 16 KB, tema
terang dan font 200% belum seluruhnya diuji. Tidak mengklaim vokal 100% hilang
atau performa lagu penuh meningkat; inference/model tetap lokal dan sama.

### Artefak final

| Artefak | Byte | SHA256 |
|---|---:|---|
| arm64-v8a APK | 70729359 | `c0e472d74978739b6588d10a53ecf9f351ab1f8f8e499eed91352e5b35144b7c` |
| armeabi-v7a APK | 62419813 | `7a87e103406caa630cf7990432e77caca00f9778109ce4ae412747196e01b1bb` |
| x86_64 APK | 75170168 | `74b92a528115a77f00d584f467ad72497366ea4aa255a12ed91ae770f0b12a27` |
| AAB | 255781402 | `6346d645dad08eac7b4cdad0b539b1b50ca9793d1df4a33e830cf33200067d74` |

ARM64 bertambah 50.160 byte dari 1.1.15. Tidak ada benchmark baterai/RAM
menyeluruh. Artefak aktif berada di `app/build/outputs/apk/release/` dan
`app/build/outputs/bundle/release/`; tidak mempublikasikan toko/tag release.
Bukti privat lokal: `build/reports/karaoke-logic-1116/`, termasuk checksum, hasil
per skenario, ffprobe/decode dan screenshot dialog yang diperiksa.

### File dan pemeliharaan

- Baru: KaraokeResult, KaraokeProcessPolicy, kedua tesnya, ikon notifikasi
  `ic_stat_karaoke`, serta laporan ini. Kebijakan awal lokal BUG-058 telah
  diintegrasikan dan diganti pada bagian yang masih memiliki race.
- Diubah: KaraokeViewModel/Worker/Publication, KaraokeDialog, TubeFlowApp,
  strings.xml, versi Gradle, README, BUG_FINDINGS, PROGRESS, DEVICE_TEST,
  PRODUCTION_SIGNING dan referensi invariants skill. Tidak menghapus source.
- Mulai/batal/buang/simpan berbagi mutex request; worker/publikasi/cleanup berbagi
  lease worker. Jangan membalik urutan lock atau memperlakukan WorkManager
  CANCELLED sebagai bukti native selesai. Jangan menulis ulang jurnal dari worker
  lama tanpa kepemilikan request.
- Pertahankan perbedaan Tutup, Batalkan, Buang pratinjau dan Simpan ke Koleksi.
  Jangan mengembalikan auto-clear saat berpindah lagu atau auto-save cuplikan.
- Jaga binding ID/URI/mode/waktu serta verifikasi manifest/hash. Mode/waktu hasil
  disimpan bersama request; hasil yang berbeda harus berlabel, bukan diganti tag-nya
  agar terlihat cocok. Metadata/artwork/LRC sumber tidak ditulis ulang oleh cleanup.
- Jalankan tes helper dan build minify, lalu uji tiga jalur utama di Android 10:
  batal saat aktif, buang setelah cuplikan siap, serta buang sesudah simpan publik.
  Gunakan file QA tercatat; jangan menghapus koleksi pengguna untuk menguji cleanup.
