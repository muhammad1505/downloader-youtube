# Pemeriksaan source, penamaan, dan Auto Folder — 13 Oktober 2026

## Baseline dan perubahan pengguna

Diperiksa commit source `2415049` dan dokumentasi `02ce4ed`, rilis **1.1.1/code15/Room9**. Source baseline sesuai artefak yang sudah dibangun. APK terpasang di Realme Android10 ber-SHA256 `437d42759c2b953587f885d70b0a5ffd44fee21dc47dd0774fa088ee847405c0`, cocok distribusi. Checksum semua artefak diperiksa ulang dan lulus; hasil baseline145 tes Kotlin serta lint debug/release bersih diverifikasi dari laporan build. Perubahan pengguna pada dashboard, jurnal refetch, dan koordinasi operasi file dipertahankan.

## Hasil HP dengan koleksi nyata

Antrean kosong. Folder TubeFlow dan subfolder memiliki **50 audio/259.937.411 byte, 0 video, 7 LRC**. Dashboard cocok:50 audio/247,9MiB, donat50/0, batang50 selesai hari ini, garis akumulasi50. Aktivitas50 selesai/2 gagal menghasilkan rasio96%, sedangkan50 tugas sekarang completed. LRC dan retry tidak menggandakan jumlah file.

Metadata lengkap7/parsial43, artwork50, LRC tersimpan7 dan tidak ditemukan/tidak cocok26. Ini membuktikan grafik dengan koleksi terisi, sekaligus menunjukkan identifikasi artis belum lengkap pada banyak sumber. Audit tidak menghapus, memindahkan, atau mengganti nama file pengguna. Ya Sudahlah lama tidak ada pada koleksi saat ini; konfirmasi auditori−2s merupakan bukti historis.

## BUG-038: kredit penyanyi tersedia tetapi parser melewatkan

Link yang diberikan sebagai PINGAL sebenarnya [SATRU2 live](https://www.youtube.com/watch?v=rEjSkBl47OY),394detik, channel DC.PRODUCTION. JSON engine memiliki artist/track kosong, sedangkan deskripsi memberi pasangan `Title` dan `Voc`. Parser hanya membaca kalimat `Official Music from`, sehingga artis, nama file, Auto Folder dan query lirik kehilangan kredit yang tersedia.

`SourceMetadata` kini membaca satu pasangan label judul dan performer, memvalidasi nama lengkap/judul terhadap raw title, serta menyetarakan connector X/FT/feat/& untuk pembandingan. Kredit ganda, judul mirip, nama sebagian, partner duet salah, Cipt/composer dan produser ditolak. `TitleSplit` membersihkan typo promosi Offical, mempertahankan Live. Format target: `SATRU 2 | Live - DENNY CAKNAN FT HAPPY ASMARA`. Raw title dan creator tetap terpisah; gerbang versi/durasi LRCLIB tidak dilonggarkan. Metadata benar belum membuktikan tersedia lirik sinkron untuk rekaman394detik.

## BUG-039: remixer teridentifikasi diabaikan Auto Folder

Pengelola memilih **folder per artis/remixer di TubeFlow**. Engine dipin membaca25 entri panel awal pada respons Mix RD3J1Opi7MVHw saat audit; bukan batas tetap seluruh Mix. Seed memiliki artist kosong tetapi kredit remixer Maman Fvndy terverifikasi pada judul. Auto Folder lama hanya memakai artist sehingga file tetap masuk root; mode Mix bergantung prefix judul.

Job playlist baru memakai policy `performer`: artis terbukti → remixer teridentifikasi → fallback root. Tidak memakai uploader/channel sebagai artis. Contoh seed: `TubeFlow/maman-fvndy-rmx/`. Dua artis dalam playlist mendapat folder masing-masing. Helper yang sama dipakai setelah metadata sumber dan probe audio; folder remixer tidak hilang ketika artist native kosong. Policy tersimpan pada field `autoFolder` yang sudah ada. Job lama dengan mode artist/mix kompatibel; default tetap root TubeFlow dan file lama tidak dipindahkan otomatis.

## File dan kompatibilitas

Diubah: `SourceMetadata.kt`, `TitleSplit.kt`, `Models.kt`, `DownloadViewModel.kt`, `TubeFlowApp.kt`, `DownloadScreen.kt`, `SourceMetadataTest.kt`, versi Gradle. Tidak ada migrasi Room, dependency, perubahan ZIP engine atau keystore baru. UI menjelaskan pengelompokan artis/remixer dan fallback.

## Pengujian dan checkpoint

149 tes Kotlin tanpa kegagalan/error;76 tes Python lulus setelah helper tes proses diperbaiki. Suite Python pertama menemukan ProcessLookupError ketika proses hilang saat pembacaan /proc/stat; helper kini memperlakukan ESRCH dan FileNotFoundError sebagai proses mati. Log kegagalan awal dipertahankan privat, tanpa perubahan engine APK.

Regresi mencakup snapshot sumber → judul/artis/versi/evidence/folder; label ambigu/nonperformer; seed Mix tanpa artist → folder remixer; channel tanpa kredit → root; playlist multiartis; pause/native probe dan prioritas artis asli. Build signed1.1.2/code16, lint/paket dan dua download sampel pada HP lulus; rincian di bagian final di bawah. Source PINGAL sebenarnya belum diberikan; rekaman dan provider lain tetap memerlukan pengujian tersendiri.


## Build final dan pemasangan

Build produksi1.1.2/code16 berhasil dalam11m11s:149 Kotlin,76 Python, lint debug/release0masalah. Audit signature/certificate produksi, zipalign16KB, native/ELF, reflective ZIP dan manifest nondebuggable lulus pada ketiga ABI. AAB signing/native serta bundletool validate lulus. ARM64 63.025.815byte, SHA256 `d7210fba3fe44419144629a7c41335296733be6da79fe31b73b2ad0dfd7a093a`, bertambah716byte dibanding1.1.1.

Update-r pada RealmeAndroid10 berhasil; hash base.apk cocok. Seluruh57 file sebelum update (50audio+7LRC) memiliki hash/nama sama sesudah update. Normalisasi nama, tag dan artwork di pengaturan memang aktif; masalah awal bukan karena opsi dimatikan. Distribusi lokal berada di `app/build/outputs/distribution/1.1.2/`; dua pengujian job playlist final lulus (lihat bagian berikut).


## Uji download Mix pada APK final

Di HP, Mix menampilkan25 entri. Penguji memilih hanya seed, opsi AudioAuto+lirik+sampul (varian berbeda dari MP3 lama), lalu mengantrekan lewat picker playlist. File nyata terbit di `Download/TubeFlow/maman-fvndy-rmx/JAUH KO PERGI _ DJ · SA CERITAKAN PADA BINTANG BINTANG SLOW - Maman Fvndy Rmx.opus`. Ukuran4.114.238byte,durasi247,800s, Opus48kHz; cover JPEG800×450. Tag title terpisah dari creator/remixer; artist asli tetap kosong. Decode penuh exit0. SHA256 `6638aa153f1f418e57593e2a046ce7e8ab0f8914b3daa704178af65a244af226`.

Antrean kembali kosong.57file sebelum update masih sama hash/nama; hanya output varian uji yang ditambahkan. Ini membuktikan policy performer tetap berlaku setelah worker mengambil metadata dan probe native, sampai lokasi publikasi akhir. Bukan klaim semua25sumber diunduh atau semua artis teridentifikasi.


## Uji kredit duet pada APK final

Dari MixRDrEjSkBl47OY yang juga membaca25entri, hanya seeddipilih dengan AudioAuto+lirik+sampul. Hasil publikasi: `Download/TubeFlow/denny-caknan-ft-happy-asmara/SATRU 2 _ Live - DENNY CAKNAN FT HAPPY ASMARA.opus`,6.693.911byte/393,980s/Opus48kHz, JPEG800×450. Tag title `SATRU 2 | Live`, artist `DENNY CAKNAN FT HAPPY ASMARA`, creator `DC. PRODUCTION`, date2022 sesuai release_year sumber. SHA256 `54b74dec4bfd439b58f3e8ee1a669b05507b6e1ab19abd3c5a9acf8941dbb30a`; decode penuh exit0. File lama serta outputMix pertama utuh.

Ini membuktikan pembacaan Title/Voc pada engine Android sampai nama, tag dan folder publikasi, bukan tes helper saja. MediaProvider mengganti karakter pipe menjadi underscore pada nama fisik; versiLive dan format asli tetap dipertahankan. Kedua sampel tidak menerbitkan LRC baru; audio tetap berhasil. Sinkronisasi lirik baru tidak diklaim, dan gerbang versi/durasi LRCLIB tetap berlaku.


## Hasil akhir dan batas cakupan

Koleksi final52audio/270.745.560byte/258,2MiB,0video,4identitas artist, sesuai dua output baru dan50audio lama;7LRC lama tetap utuh. Detail SATRU2 menunjukkan folder/artist/creator/artwork yang benar dan statuslirik belum cocok dengan versi audio. Artist asli DJ tetap belum diketahui, terpisah dari remixer yang tervalidasi.

Selesai pada kasus uji: identifikasi kredit Title/Voc, penamaan judul–artis/version, folder perartis/remixer, konsistensi source/nativeprobe/publikasi/katalog dan statistik. Versi/lirik yang tidak cocok ditolak. File lama belum dirapikan secara massal; banyak sumber tanpa kredit terverifikasi tetap memerlukan metadata yang lebih baik atau aksi manual dengan preview. Ini bukan klaim semua43metadata lama sudah lengkap atau seluruh25sumber/37bug historis selesai. Tidak ada benchmark, uji provider SAF/prosesmati baru, sinkronisasi auditori baru, publikasi toko, atau emulator.

Pemeliharaan: gunakan satu jalur SourceMetadata/TitleSplit dan automaticSubfolder; jangan menentukan artist dari uploader atau mengembalikan kebijakan folder berbasis prefix judul Mix. Panduan tubeflow-dev invariants ikut diperbarui (instalasi skill merupakan symlink ke repository). source belum berubah sesudah APK final; checksum distribusi diperbarui dan semua entri lulus.
