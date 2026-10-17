# TubeFlow 1.1.17 — enam peningkatan media

Checkpoint lama di bawah bersifat historis. Enam implementasi ini dibawa ke
1.1.18/code32 bersama konsistensi UI dan perbaikan lirik. Laporan terkini:
[REPAIR_2026-10-18.md](REPAIR_2026-10-18.md). Pengujian 18 Oktober membuktikan
countdown satu menit di latar dan mode selesai lagu saat shuffle/repeat: pemutar
berhenti otomatis; APK release final menggantikan paket QA debug. Detail A/B dan
fault injection yang belum dituntaskan tetap mengikuti batas pada laporan ini.

## Audit dan rencana
Baseline: 1.1.16/code30, Room9, satu MediaPlayer dengan layanan latar dan antrean persisten. Karaoke menghasilkan FLAC privat dengan manifest/hash dan publikasi berjurnal; model opsional dipertahankan setelah cleanup. Belum ada playlist pribadi, timer tidur, atau backup portabel.

Urutan: (1) perbandingan karaoke pada engine yang sama; (2) ukuran dan penghapusan model/preview dengan lease yang sama; (3) format turunan FLAC/MP3/AAC; (4) playlist/favorit di Room dengan migrasi non-destruktif; (5) timer pada thread player; (6) ekspor/impor terpilih dengan pencocokan file aktual.

## Rencana tampilan awal (historis)
Token awal berikut sudah digantikan sistem tampilan pada [DESIGN.md](../DESIGN.md). Saat checkpoint awal memakai: background OLED #000000, surface #111113, primary #C6B7FF, secondary #ACDCCE, onSurface #F0EDF4; mode terang tetap mengikuti peran Material. Judul titleMedium, penjelasan bodySmall, aksi labelLarge; spasi 8/12/16/24dp, radius medium20dp, target sentuh minimal48dp.

```text
Karaoke: sumber → rentang cuplikan → [Asli] [Instrumental]
         format + perkiraan ukuran → [Simpan] / [Buang]
Koleksi: Semua / Musik / Video → Favorit / Playlist → daftar lagu
Player: kontrol tetap → [Timer tidur] → durasi / selesai lagu
Pengaturan: Penyimpanan karaoke → ukuran / hapus dengan konfirmasi
            Cadangan → ekspor / pilih berkas → ringkasan → pulihkan
```
Kritik: tidak menambah kartu statistik dekoratif atau navigasi baru. Kontrol terkait ditempatkan dekat aksi asal; data asli, hasil karaoke dan cadangan dibedakan lewat label, bukan hanya warna. Cadangan bukan salinan media dan tidak memulihkan izin Android.

## Status verifikasi
Implementasi dan pengujian berlangsung; dokumen ini bukan klaim kelulusan. ADB belum mendeteksi HP pada awal tugas.

## Implementasi dan keputusan

1. **Perbandingan Asli/Instrumental.** `KaraokeComparison` mengubah posisi sumber absolut menjadi posisi lokal cuplikan. `PlayerViewModel` memakai engine yang sama, membatasi pengulangan ke rentang cuplikan dan mempertahankan snapshot antrean sebelumnya. Tombol selesai mengembalikan antrean; sesi perbandingan privat tidak mengganti snapshot pemulihan player. Pergantian membutuhkan prepare/seek, bukan crossfade tanpa jeda.
2. **Penyimpanan karaoke.** `KaraokeStorageViewModel` mengukur model, pratinjau dan sisa proses secara terpisah di IO. Model diverifikasi SHA256. Penghapusan eksplisit memakai mutex permintaan dan lease worker, menolak pekerjaan yang masih queued/running, tidak mengikuti symlink, dan tidak menyentuh audio asli atau hasil publik. Model diunduh kembali hanya melalui proses karaoke yang diminta pengguna.
3. **Format turunan.** `KaraokeOutput` menawarkan FLAC asli hasil pemisahan atau MP3/AAC192kb/s atas pilihan pengguna. AAC disimpan sebagai M4A dengan MIME audio/mp4. `KaraokePublication` memeriksa durasi konversi (toleransi150ms) dan hash hasil penyalinan, memakai jurnal publikasi lama. Ukuran kompresi adalah perkiraan bitrate, bukan janji ukuran pasti; cuplikan tidak diberi LRC lagu penuh.
4. **Favorit/playlist.** Dua tabel referensi dalam database Room yang sama (`personal_playlists`, `playlist_members`). Playlist Favorit memakai ID khusus. Maksimum200 playlist pribadi + Favorit, maksimum3000 referensi per daftar. Query filter bekerja sebelum limit halaman dan mempertahankan urutan penambahan. Rename SAF merelink URI; penghapusan media membuang referensinya. REPLACE saat pemindaian tidak menghapus keanggotaan karena tidak ada FK ke media_items. Menghapus playlist hanya menghapus referensi.
5. **Timer tidur.** Deadline elapsedRealtime 1–180menit atau selesai lagu sebelum aturan repeat/next. Fade volume engine selama10detik terakhir, tanpa mengubah volume sistem; pembatalan mengembalikan gain. Proses mati menghapus timer dan memulihkan sesi paused, bukan menjadwalkan autoplay baru.
6. **Cadangan.** Dokumen JSON versi1 dengan checksum, maksimal12MiB terbungkus/5MiB isi item, maksimal3000 media dan kedalaman16. Tidak ada salinan audio/video, model, URL sumber, path privat, token, riwayat atau grant. Pengaturan portabel mencakup tema, aksen, resume interupsi, konkurensi dan Smart Media; preset equalizer/kecepatan/sesi player dan lokasi output tidak dicadangkan. Pencocokan memakai nama/ukuran/durasi/jenis serta hash64KiB awal+akhir; ini fingerprint terbatas, bukan checksum seluruh media. Tidak memakai judul saja; media ambigu/tidak terbaca dilewati. Playlist digabung, metadata katalog opsional (bukan tag file), lirik lama hanya diganti dengan izin eksplisit. Publisher/jurnal read-back/rollback lama dipakai kembali. Jurnal tambahan memulihkan baseline+offset untuk LRC yang publikasinya sudah terverifikasi saat proses terputus; tidak menulis ulang file asing saat recovery.

Cadangan berisi data koleksi dan teks lirik dalam bentuk terbaca. Pemilih dokumen Android dipakai untuk ekspor/impor yang diminta pengguna; tidak ada unggahan otomatis. Media harus sudah tersedia dan terindeks sebelum dipulihkan. Format tidak kompatibel/provider non-seekable atau identitas tidak pasti dilaporkan dilewati.

## Database, dependensi dan sumber teknis

- Room9→10 menambah dua tabel dan indeks, tanpa menghapus tabel/kolom atau mengubah data lama. Snapshot10.json diikutkan. Regresi SQLite memakai SQL migrasi dan query aktual, termasuk REPLACE pemindaian, cascade playlist dan konflik relink.
- Tidak ada dependensi, izin, engine pemutar, model atau layanan eksternal baru.
- Rujukan API: [MediaPlayer/seek/volume](https://developer.android.com/reference/android/media/MediaPlayer), [migrasi Room](https://developer.android.com/training/data-storage/room/migrating-db-versions). Detail pengujian nyata ada di bagian hasil setelah build/perangkat selesai.

## Checklist perangkat

- Upgrade di atas1.1.16, pertahankan63 file baseline dan cocokkan hash.
- Favorit/tambah playlist/putar/ubah nama/hapus daftar, uji persistensi setelah proses ditutup.
- A/B cuplikan dengan awal bukan nol, selesai perbandingan mengembalikan antrean; buang preview aman saat sisi asli/instrumental aktif.
- Simpan hasil MP3/AAC, periksa nama/MIME/durasi/decoding, source hash tetap.
- Model/pratinjau/temporary terpisah, batalkan hapus, penolakan saat job aktif, cleanup.
- Timer custom/selesai lagu, repeat tidak melewati timer, jeda/resume/cancel.
- Ekspor/read-back, impor terpilih dan ulangi, restore metadata/lirik, cadangan rusak, hilang/ambigu, URI provider berubah, interupsi recovery.
- Tampilan terang/gelap/fontbesar; Android10 fisik, audit paket release; Android16KB runtime tetap perlu perangkat lain.

## Hasil yang sudah diamati (checkpoint perangkat)

Perangkat: Realme RMX1821, Android10/API29, ARM64, halaman4KiB. Baseline63 file publik. Bukti rinci, dokumen cadangan yang memuat data pribadi, XML dan screenshot berada lokal di `build/reports/media-features-1117/` dan tidak diikutkan Git.

| Skenario | Hasil aktual |
| --- | --- |
| Upgrade Room9→10 pada kandidat release | Aplikasi dan koleksi terbuka; Favorit/playlist dapat ditulis tanpa reset |
| Favorit, buat/tambah/putar/rename playlist | Lulus di kandidat release; pemutaran teramati STATE_PLAYING, source duration259388ms |
| Tutup paksa ketika semua job selesai, buka ulang | Favorit dan nama playlist bertahan; tidak menghapus data |
| Ekspor dokumen lokal | 31419byte; checksum diverifikasi; 52media,11lirik,2daftar (Favorit+playlist QA) |
| Pratinjau pemulihan | 52cocok,0hilang,0ambigu |
| Pemulihan default | 11lirik dipulihkan,0gagal/0dilewati; 63hash publik awal tetap identik; metadata katalog dan overwrite lirik tidak dipilih |
| MP3 turunan | CodecMP3,0,914286s,tag artis sesuai, full decode FFmpeg lulus |
| AAC/M4A turunan | CodecAAC/containerM4A,0,977007s, full decode FFmpeg lulus |
| Publikasi cuplikan | Tepat2audio QA baru, tanpa LRC lagu penuh; 63file awal tetap identik |
| Buang pratinjau | Ukuran pratinjau dan temporary kembali0B; hasil publik tetap ada |
| Hapus model saat idle | Dikonfirmasi lewat UI; model hilang, tidak menyentuh hasil publik |
| Unduh ulang model | Proses berikut mengunduh, memverifikasi, lalu menghasilkan preview; SHA256 prepared cocok `bf09aee9b867a102c786dd8205d7f5176938b78329d906773764e05918d21c4f` |
| Jeda cuplikan sangat pendek | Bug kandidat release direproduksi; kode diperbaiki. Paket QA debug: STATE_PAUSED dan posisi144ms tetap sama pada dua pembacaan berjarak2detik |
| Posisi A/B | Pergantian source duration259388ms dan preview878ms teramati; pembacaan MediaSession dapat tertinggal saat prepare. Assertion alignment end-to-end belum sah; perlu menunggu metadata yang cocok, lalu ulangi |
| Timer tidur | Unit deadline/fade/boundary lulus; pengujian timer nyata di HP belum selesai |

Sambungan ADB terputus setelah uji jeda. Paket terakhir yang benar-benar terpasang saat itu adalah **QA debug** dengan sertifikat produksi, SHA256 `3c009c69f555f4c687da99d590a3940587444d47729c6d915cb0649033a794cf`, bukan release akhir. Kandidat release sebelumnya SHA256 `5370e39b93878949f225044063b7f6420d3770063ce5ca60920334b8ca810f5f`. Keduanya memakai version1.1.17/code31; hash membedakan biner yang diuji. APK release final wajib diverifikasi/diinstal terpisah setelah build selesai.

## Perbaikan selama pengujian

- Callback completion cuplikan dapat tiba setelah Pause. Guard autoplay mencegah repeat/next mengalahkan jeda; hasil QA debug menunjukkan posisi tetap.
- Teks konfirmasi lama menyebut FLAC untuk semua format. Resource dan parameter diperbaiki agar mengikuti format pilihan; tanda “Karaoke cuplikan” dipertahankan di player.
- Setelah jeda/timer, finish A/B tidak melanjutkan musik tanpa niat putar yang masih aktif. Koneksi headset keluar dari preview untuk memakai antrean publik.
- Pengambilan seluruh playlist dipisahkan dari halaman tampilan (maksimum3000referensi), kapasitas menghitung anggota saat ini, relink/penghapusan referensi tetap atomik. Tombol Simpan nama dibedakan dari aksi Ubah nama tiap daftar.
- Kontrol Putar/Jeda ditambahkan langsung pada panel A/B; tidak membuat engine kedua.

## Batas yang masih perlu diverifikasi

- Timer1menit di latar dan mode selesai lagu ketika repeat aktif; gain fade actual belum diukur di perangkat. Fade countdown tidak mengubah volume sistem.
- Alignment A/B setelah prepare selesai, tombol perbandingan dan label format pada APK release akhir; headset tersambung saat preview dijeda.
- Impor berulang/cadangan rusak lewat UI; codec negatif dan referensi duplikat sudah diuji otomatis. Restore metadata/overwrite LRC berbeda belum diuji di HP. Batch bersifat perfile; setelah proses mati, ulangi impor untuk melanjutkan seluruh pilihan. Recovery perfile tidak berarti seluruh batch otomatis selesai.
- Fault injection storage penuh, grant SAF dicabut/provider berbeda, kill di tengah publication/restore, model delete bersamaan native cleanup.
- Lagu karaoke penuh setelah perubahan ini belum dituntaskan; native separator dan model tidak diubah. Kualitas vokal belum dinilai ulang secara auditori.
- Android16KiB runtime, Android versi lain, tema terang, fontbesar/TalkBack dan benchmark koleksi3000 belum diuji. Tidak ada emulator.
- ADB perlu tersambung untuk mengganti paket QA debug dengan release dan membersihkan hanya2audio/playlist/dokumen QA yang dibuat pengujian. Jangan menghapus63file baseline.
