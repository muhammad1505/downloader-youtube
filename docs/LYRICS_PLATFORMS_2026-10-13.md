# Validasi Lirik dan Platform — 13 Oktober 2026

## Lingkup dan akar penyebab

Lanjutan sistem 1.0.9 atas permintaan pengelola: lirik dan platform lain, memakai HP fisik tanpa emulator. Release **1.0.10 / code 11**, dilanjutkan release **1.0.11 / code 12** setelah link SoundCloud penuh mengungkap versi ganda pada field track, lalu **1.0.12 / code 13** untuk alias SoundCloud dan tie skor kandidat lirik. Room tetap v8; tidak ada reset database atau penggantian keystore.

| Masalah | Akar penyebab | Perbaikan |
| --- | --- | --- |
| Lirik versi berbeda lolos pencarian alternatif | `/search` menerima selisih durasi `max(12 detik, 10%)`, lebih longgar daripada `/get` | Gerbang ±2 detik pada kedua jalur; durasi audio dan kandidat wajib diketahui sebelum penerimaan otomatis |
| Data LRC tidak dapat dipakai | Pembersihan menganggap setiap baris bertanda `[` valid, tanpa memeriksa timestamp | Periksa format detik dan timestamp maksimal durasi audio +2 detik; wajib ada teks bertimestamp |
| Koreksi waktu penyedia hilang | Baris `[offset:...]` dibuang karena tidak punya teks setelah `]` | Pertahankan metadata LRC dan offset numerik, serta beberapa timestamp per baris |
| Hasil ditolak terlihat seolah penyedia tidak punya lirik | Hasil tidak cocok dan tidak ditemukan memakai status sama | Status `tidak_cocok`, label “Lirik belum cocok dengan versi audio”, dapat dicari ulang; kegagalan jaringan tetap terpisah |
| Versi structured track tampil ganda | `metadataTrack` langsung dipakai tanpa normalisasi kelompok versi, berbeda dari raw title | Gunakan pembersihan grup/credit bersama; versi Live/Slowed hanya sekali, raw title dipertahankan |
| Artis Lamunan tidak teridentifikasi | Metadata artist/track kosong; channel Music Interactive merupakan pengunggah | Baca kredit eksplisit `Official Music Live from ARTIST - TRACK` pada deskripsi, hanya jika nama artis dan lagu lengkap juga ada pada judul dan pasangan kredit unik; metadata platform tetap didahulukan |

## File dan pengujian kode

- `core/Lyrics.kt`: gerbang durasi bersama, validasi LRC, retensi offset, klasifikasi hasil.
- `core/SourceMetadata.kt`: kredit sumber dengan verifikasi judul, tidak memakai songwriter/arranger sebagai vokalis.
- `core/Models.kt` / `SelectionTest`: identitas SoundCloud mengabaikan UTM dengan token akses tetap utuh, termasuk pembandingan riwayat lama dan seluruh opsi keluaran.
- `core/TitleSplit.kt`: hapus kata promosi pada keterangan versi, pertahankan Live; normalisasi raw title dan structured track memakai helper yang sama agar versi/credit tidak ganda.
- `LyricTest`, `LyricsFetchTest`, `SourceMetadataTest`: regresi selisih 2/3/10 detik, durasi kosong, exact/fallback, timestamp rusak/lewat akhir audio, metadata tanpa lirik, offset/multi-timestamp, kredit konflik/tidak cocok, metadata asli menang, identitas/folder konsisten.
- `app/build.gradle.kts`: versi release baru; data lama dan artefak 1.0.9 dipertahankan.

**Host:** 1.0.10 lulus 110 tes Kotlin; koreksi structured track 1.0.11 lulus 112 tes Kotlin. 61 tes Python lulus pada batch ini, kode engine/Python tidak berubah setelahnya. Lint debug/release 0 masalah; signed APK×3/AAB 1.0.11 sukses dalam 10m26s; signature/certificate, zipalign 16 KB, audit native dan ZIP reflection seluruh APK, serta signature/native/bundletool AAB lulus. Peringatan JAR sertifikat self-signed/timestamp dan deprecation internal Gradle tetap terpisah dari lint.

## Sumber nyata dan batas penyedia

Sumber pengguna:

1. [Lamunan — Sasya Arkhisna, Music Interactive](https://youtu.be/8uWb5QvR95s): 259 detik, judul asli `LAMUNAN - SASYA ARKHISNA (Official Music Live) Pindha samudra pasang`. Metadata artist/track kosong, tetapi deskripsi mencantumkan kredit performer/lagu yang cocok. Hasil normalisasi: `Lamunan | Live - Sasya Arkhisna`; creator tetap Music Interactive. LRCLIB `/api/search` memberi 14 rekaman, termasuk Live 259 detik; 12 hasil tidak memiliki `syncedLyrics`; dua hasil Live berdurasi 259–260 detik memiliki timestamp (awal 16,62; akhir 257,31 detik). Audio tidak boleh dilabeli gagal karena LRC tidak tersedia.
2. [Hysteria — Muse, SoundCloud](https://soundcloud.com/muse/hysteria): metadata platform menyebut artist Muse/track Hysteria. Format tersedia tanpa login adalah preview 30 detik (`http_mp3_1_0_preview`, `hls_mp3_1_0_preview`), bukan lagu penuh. Hasil LRCLIB umumnya sekitar 227 detik; LRC tersebut harus ditolak untuk preview. Tidak memakai bypass akses berbayar.

Pemeriksaan tambahan `/api/search` Negoro Angin menemukan rekaman berdurasi 7/219/305/312/313/415/416 detik dengan timestamp yang sama (awal 17,81; akhir 297,72 detik). Validasi batas timestamp menolak contoh yang melewati akhir rekaman, tetapi tidak membuktikan bahwa salinan timestamp pada rekaman lebih panjang tepat secara audio. Data respons mentah disimpan lokal pada `build/reports/lyrics-platform-2026-10-13/`, bukan dibagikan sebagai salinan penuh lirik.

Aturan ±2 detik dikonfirmasi pada [repository resmi LRCLIB](https://github.com/tranxuanthang/lrclib/blob/main/server/src/repositories/track_repository.rs) dan [dokumentasi API](https://lrclib.net/docs). LRCLIB bukan Musixmatch. Tidak mengubah timestamp, menebak offset, atau membuat LRC dari plain lyrics. Caption `id` tersedia pada video Lamunan, tetapi audio masih memakai LRCLIB; caption sumber bukan fallback audio yang sudah diimplementasikan.

Sumber tambahan pengguna: [Hysteria-MUSE (slowed)](https://soundcloud.com/user-428356631/hysteria-muse-slowed-reverb), ID 1056898207, durasi 280,183 detik. Format HTTP MP3/HLS MP3/AAC tersedia penuh. Metadata `artist` kosong, `track` sama dengan judul, uploader `sleep_shady`, deskripsi kosong. Tidak ada bukti untuk menjadikan uploader sebagai artis; kredit MUSE pada judul dipertahankan tanpa promosi otomatis menjadi artist. Normalisasi konservatif `Hysteria-MUSE | slowed`, creator tetap `sleep_shady`; versi dari metadata aktual tidak ditambah berdasarkan slug URL saja. Belum dilakukan download preview dari link SoundCloud pertama.

Sumber sebelumnya yang hanya diinspeksi: [Muse - Hysteria, Triaxis75](https://soundcloud.com/triaxis75/muse-hysteria), ID 38684141, durasi 227,309 detik, format HTTP/HLS MP3 dan AAC penuh. Artist/artists kosong, structured track `Muse - Hysteria`, uploader Triaxis75. Deskripsi pendek menyebut quick recording, tanpa kredit vocalist/artist terstruktur. Resolusi provider dapat menguatkan identitas lagu dari judul–artis–durasi unik, tetapi bukan audio fingerprint atau bukti siapa performer rekaman. Pertahankan uploader sebagai creator dan verifikasi audio/lirik pada pemutar; jangan menyatakan source ini rekaman resmi hanya dari judul. Link SoundCloud lain hanya diinspeksi, bukan diunduh pada HP.

## Pembandingan timeline sumber

Caption manual `id` pada video Lamunan berhasil dibaca sebagai pembanding independen. Dari 31 baris LRC berteks dan 25 cue caption, sembilan baris dengan pasangan teks tunggal memiliki selisih timestamp −0,495 sampai +0,389 detik (awal dan bagian tengah lagu). Baris berulang memiliki beberapa kandidat dan tidak dianggap pasangan pasti. Ini bukti kedekatan dengan timeline caption kreator, **bukan pengamatan auditori** atau jaminan seluruh lagu sinkron. Tidak mengubah timestamp hasil provider. Detail numerik privat: `build/reports/lyrics-platform-2026-10-13/lamunan-caption-comparison.json`.

## Pengujian perangkat 1.0.10

Update signed `install -r` berhasil, hash APK terpasang sama dengan artefak ARM64 `6c80ff2a13f5eac65735534227ad51c9f1d8cc6315e6d145093932e9d676ccf5`; 27 riwayat lama bertahan. Lamunan selesai menjadi entri ke-28, queue kosong. Di antrean dan riwayat judul/artis/cover/YouTube konsisten. Root TubeFlow berisi `Lamunan _ Live - Sasya Arkhisna.mp3` dan `.lrc` dengan nama dasar sama. MP3 4.209.854 byte, durasi 259,392 detik, audio 128 kbps, title `Lamunan | Live`, artist Sasya Arkhisna dan creator Music Interactive terpisah; artwork MJPEG 800×450. Full ffmpeg decode sukses. LRC 1.456 byte, 31 baris bertimestamp nonkosong, awal 16,62–akhir 253,30 detik (timestamp kosong akhir dari provider dibuang). Poweramp membuka audio, cover/artist benar dan local lyrics tampil; pengelola mengonfirmasi lagu dan lirik sinkron setelah pemutaran di HP. Tidak menyimpulkan sinkron hanya dari screenshot.

## Pengujian perangkat 1.0.11

APK ARM64 signed 1.0.11/code12 sudah di-update dengan `install -r`; hash APK terpasang identik dengan artefak `800f3fd703a539c8996c9611ca1c50ae25ba88d661fbb6e55bba70585e55bd48`, 62.858.595 byte. Room tetap v8 dan kunci produksi sama. Riwayat terlihat kosong setelah update; pengelola mengonfirmasi telah menghapus riwayat selama menunggu build. Empat file publik sebelumnya masih ada; agent tidak menjalankan uninstall/clear data. Karena tindakan tersebut, batch ini tidak membuktikan retensi 28 baris melalui update 1.0.11; retensi 27 baris pada update 1.0.10 telah teramati.

Sumber akhir pengelola: [Ya Sudahlah — bondanfade2black](https://soundcloud.com/bondanfade2black/ya-sudahlah), ID 46232966, durasi sumber 252,467 detik. Format HTTP/HLS MP3 dan AAC penuh tersedia. Artist/artists kosong, track `Ya Sudahlah`, uploader bondanfade2black; uploader tidak dipromosikan menjadi artis. LRCLIB memiliki beberapa variasi identitas artis pada durasi yang cocok, sehingga fallback konservatif diperlukan bila tidak ada pasangan identitas tunggal. MP3 128 kbps + lirik + cover diajukan pada HP; antrean menampilkan SoundCloud dan artwork aktual. Download selesai, riwayat menjadi satu entri audio SoundCloud, antrean kosong. Root menambah `Ya Sudahlah.mp3` (4.083.718 byte) dan `Ya Sudahlah.lrc` (2.975 byte), tanpa menghapus empat file sebelumnya. Audio 252,499592 detik, 128 kbps, title Ya Sudahlah, creator bondanfade2black; tag artist tetap kosong karena bukti identitas tidak unik. Artwork JPEG 797×800, 42.567 byte. Full ffmpeg decode berhasil. LRC memiliki 62 timestamp berteks (5,67–241,66 detik), nama dasar sama dengan audio. Poweramp membuka file, artwork dan LRC lokal terbaca; pengelola melaporkan lirik masih delay sekitar 0,5 detik. Audio/penyimpanan berhasil, tetapi sinkronisasi SoundCloud ini belum lulus. Pengelola kemudian mengonfirmasi sorotan muncul terlambat dan selisih sekitar 0,5 detik tetap sampai tengah/akhir; koreksi yang dibutuhkan adalah memajukan lirik per lagu, bukan perubahan global.

## Pekerjaan berikutnya dan kriteria

- Sinkronisasi auditori Lamunan dikonfirmasi pengelola. Rekaman SoundCloud perlu diperiksa terpisah; hasil preview Hysteria resmi tidak mewakili audio penuh dari link lain.
- OPT-003/004 tetap usulan: pratinjau/pilih ID rekaman dan koreksi offset manual, tanpa menimpa lirik lama ketika batal/gagal. Pergeseran tetap tidak memperbaiki drift sepanjang lagu.
- Parser mempertahankan offset provider; tidak mengoreksi atau membuktikan arti/arah offset pada setiap pemutar. Pemeriksaan batas memakai timestamp tertulis.
- Kredit deskripsi hanya menangani pola eksplisit terverifikasi; format tidak dikenali tetap fallback konservatif, tanpa mengarang artis. Pengujian platform baru tidak mengklaim seluruh platform didukung secara runtime.

**Konfirmasi pengelola:** “lagu dan lirik sync” untuk Lamunan di Poweramp. Ini hasil auditori pengguna, melengkapi pemeriksaan file/timestamp oleh agent; tidak digeneralisasi ke seluruh lagu atau versi lain. Pengelola mengganti sumber akhir SoundCloud menjadi `https://soundcloud.com/bondanfade2black/ya-sudahlah`; link Muse/Hysteria sebelumnya hanya dipakai untuk analisis metadata, tidak dibuat download audio pada HP.


## Temuan dari tes auditori SoundCloud

Pengelola mendengar delay sekitar 0,5 detik pada Ya Sudahlah. Kandidat yang tersimpan memiliki timeline identik dengan record LRCLIB ID 25044772 (254 detik), serta beberapa salinan lain. Kandidat ID 22922939 berdurasi 252,342857 detik lebih dekat ke audio, tetapi 29 baris teks unik yang dapat dipasangkan berbeda −0,31 sampai +0,21 detik dari LRC tersimpan; menggantinya belum terbukti menghilangkan delay 0,5 detik.

Seleksi fallback pada 1.0.11 hanya mengganti kandidat ketika skor judul/artis lebih tinggi. Kandidat dengan skor sama tetap mengikuti urutan respons provider; selisih durasi ±2 detik merupakan gerbang, belum pemeringkat. Ini celah pemilihan yang dapat dioptimalkan, **bukan bukti akar delay audio**. Status tersimpan menyatakan file LRC berhasil dibuat, bukan jaminan sinkron secara auditori. Tie selisih durasi terdekat telah diterapkan pada 1.0.12. Usulan berikutnya: simpan ID/atribusi record untuk diagnostik, dan berikan koreksi waktu per lagu dengan pratinjau serta rollback. Jangan mengubah offset global atau mengklaim seluruh lagu sinkron. Lihat BUG-033 dan OPT-003/004.


## Lanjutan 1.0.12 — temuan tes nyata

Uji duplikat SoundCloud release 1.0.11 gagal: link ber-UTM dan link bersih dengan opsi sama membuat job kedua. Job uji kedua dibatalkan sebelum publikasi, lalu hanya entri cancelled tersebut dihapus. Audio/LRC pertama tetap utuh, riwayat satu completed, root enam file; Settings membaca 92,5 MB.

Release 1.0.12/code13 memperbaiki `Selection.identity` untuk SoundCloud: buang hanya `utm_*`, normalkan host web/mobile, pertahankan parameter akses dan platform lain. `Lyrics.fetch` juga memakai selisih durasi terdekat untuk tie skor identitas, setelah gerbang versi/durasi; identitas lebih kuat tetap didahulukan. Ini menghilangkan ketergantungan urutan provider pada kandidat setara, tetapi **belum menyelesaikan delay auditori 0,5 detik**. Koreksi waktu per lagu masih usulan dan tidak menggeser semua file. Regresi baru menguji kedua urutan provider, versi berbeda, skor identitas lebih kuat dan alias/token akses SoundCloud.

**Host 1.0.12:** 116 tes Kotlin, 0 gagal/error/skipped; lint debug/release 0 masalah (5m51s). Engine/Python tidak berubah sejak 61 tes Python pada batch. Signed build 1.0.12 selesai dalam 5m24s; seluruh audit APK×3/AAB dan bundletool validate lulus.


## Hasil final 1.0.12 di HP

ARM64 62.859.063 byte, SHA256 `e6fe701f96de98c83c0ab4cca598b0b71e02978d9df30c54bd44d504ba83d60a`; versionCode13 sudah terpasang melalui `install -r`, hash APK pulled identik. Sertifikat produksi tetap sama, Room v8. Satu riwayat Ya Sudahlah bertahan melalui update dan force-stop/reopen; title, platform SoundCloud, thumbnail, status selesai dan LRC tetap terlihat. Empat file sebelumnya ditambah audio/LRC SoundCloud tetap enam file (97.031.658 byte); Settings default Downloads/TubeFlow membaca 92,5 MB sebelum dan setelah restart.

Uji alias link bersih dengan MP3 128 + lirik + cover yang sama **ditolak**: “File ini sudah pernah diunduh.” Antrean kosong, riwayat satu completed, tidak ada media/sidecar baru. BUG-034 selesai untuk kasus ini; parameter akses dan platform lain tetap diuji host, belum seluruh variasi situs diuji perangkat. Log PID aplikasi setelah restart tidak memuat fatal exception atau error schema/migration.

Pemeringkatan kandidat baru berlaku pada pencarian lirik berikutnya; LRC tersimpan dari tes 1.0.11 tidak ditulis ulang. Pengguna mengonfirmasi sorotan Ya Sudahlah terlambat sekitar 0,5 detik dengan selisih tetap hingga tengah/akhir. **BUG-033 tetap terbuka:** koreksi per lagu perlu memajukan lirik sekitar 0,5 detik dengan pratinjau, backup dan verifikasi auditori. Kandidat durasi terdekat belum diuji auditori pada HP dan bukan jaminan mengatasi delay. OPT-003/004 belum diimplementasikan. Lamunan tetap memiliki konfirmasi sinkron yang terpisah.

Artefak lokal `app/build/outputs/distribution/1.0.12/`: APK ARM64/ARMv7/x86_64, AAB, sertifikat publik, `release.json`, `SHA256SUMS`. Versi sebelumnya dipertahankan; tidak ada upload toko. Bukti mentah/screenshot/hash/file decode tersimpan privat di `build/reports/lyrics-platform-2026-10-13/`. Tidak memakai emulator atau menghapus file hasil download sebelumnya.
