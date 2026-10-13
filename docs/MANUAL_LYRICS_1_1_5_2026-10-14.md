# TubeFlow 1.1.5 — kontrol lirik dan kanal audio

Baseline `40e3d5e` (1.1.4/code18); upgrade 1.1.5/code19. Room tetap 9, tanpa migrasi, dependensi baru, perubahan engine/native atau transcoding. AGENTS.md tidak diubah.

## Analisis dan perubahan

- Koreksi LRC sebelumnya hanya menyediakan langkah ±0,5 detik. Kolom baru menerima detik bertanda, koma/titik dan tiga desimal: `-2` memajukan dua detik, `0,5` memundurkan setengah detik. Batas tetap ±30 detik, dihitung dari cadangan asli per lagu; reset dan penulisan terverifikasi dipertahankan. Tidak ada offset global.
- Pencarian otomatis LRCLIB menyembunyikan alternatif. Tombol **Cari lirik** pada koleksi/riwayat kini membuka parameter judul/artis yang dapat diedit, maksimum 50 hasil unik, judul/artis/album/durasi, status timestamp dan preview. Maksimum tiga request terbatas (fallback judul dasar/artis utama hanya bila belum ada LRC) memakai API search yang sudah tersedia; respons/cache dan batas ukuran tetap digunakan.
- Pilihan manual tidak melonggarkan pencocokan otomatis ±2 detik. Hasil yang gagal gerbang pencocokan identitas/versi/durasi memerlukan pengakuan pengguna; hasil tanpa timestamp tidak dapat disimpan sebagai LRC. Timestamp melewati akhir audio ditolak. Tidak mengarang waktu atau mengganti tag/nama audio dari hasil pencarian.
- Pilihan berdasarkan record ID dan payload hasil tersebut, tanpa mencari ulang kandidat ambigu saat simpan. Tag `[re:TubeFlow / LRCLIB #ID]` menyimpan atribusi/ID dalam LRC; metadata dan timestamp sumber dipertahankan. Lirik lama diganti setelah verifikasi melalui backup/jurnal `LyricEditor`.
- Setelah rename berhasil, opsi **Cari lirik dengan nama baru** membuka pencarian. Format dua segmen `Judul - Artis.ext` menjadi parameter pencarian yang dapat dikoreksi; nama ambigu tetap judul utuh. Ini bukan bukti untuk menulis artist tag. Rename gagal tidak memicu pencarian.
- `LyricPublisher` mengekstrak jalur refetch yang sudah ada, digunakan bersama pilihan manual. LRC mengikuti nama dan lokasi aktual audio. Snapshot nama/URI/isi LRC serta generation job diperiksa sebelum simpan. Audio tidak diunduh ulang. File impor yang writable juga dapat memakai alur yang sama; publikasi baru tanpa job memakai jurnal AtomicFile privat.
- Navigasi menjadi **Ringkasan → Download → Antrean → Koleksi**, termasuk intent share, pintasan dashboard, badge dan perpindahan setelah enqueue. Pengaturan tetap di top bar.
- Lirik full player rata tengah. Tombol mini/full player memilih **L/R (stereo), L, R**, memakai volume kanal MediaPlayer pada thread engine. Kanal kembali stereo saat trek baru. Tidak memisahkan vokal dari rekaman stereo campuran; karaoke hanya berguna bila sumber memisahkan vokal/musik per kanal. Kanal yang dimatikan tidak dirutekan ulang ke speaker lain. Pemutaran tetap foreground-only.

## File terdampak

Baru: `core/AudioChannel.kt`, `ManualLyricQuery.kt`, `LyricPublisher.kt`; `ui/LyricsSearchDialog.kt`; `ManualLyricsInputTest.kt`, laporan ini.

Diubah: `LrcTiming`, `Lyrics`, `DownloadViewModel`, `MediaLibraryViewModel`, `MediaRepository`, `PlayerViewModel`; `MediaLibraryScreen`, `HistoryScreen`, `components`, `PlayerUi`, `TubeFlowApp`, `MediaDashboardScreen`; Gradle/version; `LyricsFetchTest`. Dokumentasi release/roadmap/skill diperbarui saat verifikasi selesai. Tidak ada source dihapus.

## Verifikasi dan batas

Checkpoint awal: 180 Kotlin/78 Python lulus; source final menambahkan regresi agar query yang diedit tidak mengubah identitas audio untuk peringatan versi. Build final, audit artefak dan hasil HP dicatat di bawah setelah dijalankan.

Baseline HP: 59 berkas pengguna, hash dicatat privat. Tes penulisan hanya memakai media tes baru bila tersedia; tidak mereset koleksi/riwayat. Pengujian fisik tetap tanpa emulator.

Belum mengklaim karaoke menghilangkan vokal, sinkronisasi auditori, provider SAF read-only/opaque, proses mati saat publikasi, atau seluruh race storage. Opaque parent dan izin berubah harus gagal dengan mempertahankan audio/lirik lama. Jendela create→journal tetap memerlukan fault injection. Penilaian TalkBack dan seluruh ukuran layar belum dilakukan.

Rujukan API: [LRCLIB](https://lrclib.net/docs), [MediaPlayer.setVolume](https://developer.android.com/reference/android/media/MediaPlayer#setVolume(float,%20float)).


## Hasil pemeriksaan final

- Signed build `scripts/build_release.sh assembleDebug …` berhasil dalam **8m4s**. **184 Kotlin/78 Python lulus**, lint debug/release 0 masalah. Regresi meliputi query pencarian vs identitas audio, pilihan manual berbeda versi, fallback lead artist, metadata nullable, LRC melewati akhir audio, input detik/rentang dan koreksi 5 ms yang mempertahankan presisi timestamp.
- APK×3/AAB lolos signature/certificate, native, APK alignment 16KB/refleksi ZIP/manifest; engine dan bootstrap cocok dengan pin. AAB bundletool validate lulus. Keystore produksi dipertahankan; tidak ada publikasi toko.
- Candidate sebelum perbaikan metadata nullable/presisi dipasang update-r pada Realme RMX1821 Android10/API29/ARM64/4KB. Hash terpasang cocok dengan candidate `981cd8b8ea170ad265d99f957baf9dd8de76cd0041f0a1771ce21281f750b75a`; berbeda dari paket final.
- HP menampilkan urutan empat tab yang diminta dan **17 hasil** pencarian SATRU2 melalui fallback. Pengelola mengonfirmasi memilih dan menyimpan sendiri. LRC fisik mengikuti stem audio dalam `mix-denny-caknan`, menyimpan ID **10455242**; payload sesuai `/api/get/10455242` byte-for-byte setelah header atribusi. Audio tidak diunduh ulang. Hash **59 berkas baseline sama** pada saat pemeriksaan setelah seleksi, ditambah satu LRC.
- Interaksi HP berlangsung bersama pengguna. Otomatisasi membuka preview Teteg Ati ketika daftar bergeser, tetapi tidak menekan Simpan/menulis koreksi; tindakan tombol dihentikan agar tidak berbenturan dengan pengguna. Tidak mengklaim otomatisasi menyimpan offset manual, menguji rename→search, atau memverifikasi L/R secara auditori. Parser/penulis waktu diuji host; UI input muncul di HP. Sinkronisasi vokal rekaman terpilih belum dikonfirmasi.
- Permintaan lanjutan karaoke tanpa vokal dipisahkan dari kontrol kanal; [kajian](KARAOKE_FEASIBILITY_2026-10-14.md). Belum memasang model atau membuat tombol AI yang tidak berfungsi.

Final ARM64: 63,172,747 byte (delta +37,276 dari1.1.4), SHA256 `c4aea0927af8bac992036805986fc1e59982e324760ed37a99d800b544154828`. Tidak ada benchmark performa/RAM.

APK final dipasang update-r pada HP yang sama; SHA256 base.apk cocok dengan ARM64 final. **61 berkas yang ada tepat sebelum update tetap sama sesudah update**, termasuk pilihan LRC pengguna. Tidak uninstall/clear data. Pengujian waktu simpan custom, rename→search dan kualitas kanal secara auditori tetap belum diklaim.
