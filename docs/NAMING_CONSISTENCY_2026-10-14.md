# Kredit judul/artis dan nama file1.1.4

`TitleSplit` memisahkan judul, artist dengan bukti, versi, creator dan remixer. `CollectionCredit` mempelajari identitas seluruh entri serta seed sebelum seleksi pengguna, tanpa menebak artis hanya dari posisi/frekuensi. DJ/remix tidak menerima artis koleksi. Channel ringkas cocok hanya bila namanya muncul sebagai credit lengkap; label yang berulang bukan artis.

Nama output: `Judul (Versi relevan) - Artis/Remixer.ext`; tanpa credit valid gunakan judul. Pipe tidak ditulis ke filesystem. Track/source asli tetap disimpan; tidak melakukan rename massal pada hasil lama. Auto Folder menggunakan snapshot koleksi yang sama untuk semua pilihan, termasuk refresh/resume.

Album OLAK memakai satu performer terverifikasi, bukan Various Artists atau playlist owner. Album dengan kredit bertentangan tetap per track. LRCLIB mencoba lead artist kolaborasi tetapi gerbang identitas/versi/durasi tetap berlaku.

Regresi meliputi alias channel, dua arah credit, kolaborasi ambigu, judul cover/label berulang, remix, album, metadata lebih kuat, retention dan verified seed. Hasil build/perangkat aktual: [laporan upgrade](UPGRADE_1_1_4_2026-10-14.md).
