---
name: tubeflow-dev
description: "Kembangkan, perbaiki, review, poles UI/UX, uji, dan siapkan rilis aplikasi Android TubeFlow (repository downloader-youtube, package id.local.tubeflow). Gunakan SELALU untuk pekerjaan apa pun pada proyek ini: Kotlin/Jetpack Compose, desain dan tema aplikasi, engine Python/yt-dlp, Room/WorkManager, SAF/MediaStore, playlist, progres, sampul, lirik, perbaikan bug dari BUG_FINDINGS, audit stabilitas, uji perangkat, signing/APK/release, serta setiap permintaan 'siap produksi', 'rapikan', 'premium', 'elegan', 'profesional' atau 'stabil' untuk TubeFlow, walau pengguna tidak menyebut nama skill ini."
---

# Pengembangan TubeFlow

Tujuan skill ini: menghasilkan TubeFlow yang benar, aman bagi data pengguna, stabil, dan terasa premium. Proyek ini aplikasi Android on-device tanpa backend. Jalankan perintah dari root checkout yang memiliki package `id.local.tubeflow`; jangan menerapkan instruksi ini pada proyek lain.

"Siap produksi" adalah klaim yang harus dibuktikan, bukan label. Pengguna TubeFlow menyimpan file dan riwayat yang tidak bisa diunduh ulang dengan mudah, jadi satu migrasi yang salah atau satu file setengah jadi yang ditandai selesai lebih merusak kepercayaan daripada tampilan yang kurang rapi. Karena itu skill ini mengikat setiap klaim kesiapan pada bukti per gerbang (lihat "Klaim kesiapan produksi").

## Mulai dari kondisi yang sebenarnya

- Baca [AGENTS.md](../../../AGENTS.md) dan bagian yang relevan pada [README](../../../README.md). Jangan ubah AGENTS.md yang sudah ada tanpa permintaan pengelola.
- Untuk perbaikan, cari ID, status, titik kode dan checklist pada [BUG_FINDINGS](../../../docs/BUG_FINDINGS.md), lalu baca laporan audit/perangkat yang dirujuk. Untuk fitur, baca [DEVELOPMENT_ROADMAP](../../../docs/DEVELOPMENT_ROADMAP.md) hanya pada usulan terkait.
- Cocokkan dokumentasi dengan kode terkini. [PROGRESS](../../../docs/PROGRESS.md) memuat sejarah keputusan; bagian lama dapat sudah digantikan perbaikan berikutnya. Jangan menghidupkan kembali heartbeat progres lama atau menyimpulkan seluruh bug sudah selesai.
- Ambil versi aplikasi/SDK/dependensi dari Gradle, engine dari `app/src/main/assets/engine.json`, dan schema dari Room. AGENTS.md masih menyebut SDK 35, sementara konfigurasi yang diperiksa saat skill dibuat memakai compile/target 37. Gunakan konfigurasi build aktual; jangan menurunkan SDK agar sesuai catatan lama.
- Untuk pekerjaan visual, baca tema yang sudah ada (warna, tipografi, bentuk, komponen bersama) sebelum menambah apa pun. Identitas visual yang ada diperluas, bukan diganti, kecuali pengelola memintanya.

## Pilih panduan yang dibutuhkan

Untuk pekerjaan UI/UX atau teks, baca juga [supporting-skills.md](references/supporting-skills.md). Pengelola meminta tujuh sumber skill pendukung dipasang dan dipakai sesuai cakupannya; gunakan pemetaan itu agar panduan web, tulisan, dan Android tidak tercampur.

| Jenis pekerjaan | Baca sebelum mengedit |
|---|---|
| Download, database, penyimpanan, playlist, identitas/metadata, progres, sampul, lirik, player lokal, karaoke | [invariants.md](references/invariants.md), bagian terkait |
| Build, pemilihan tes, pemasangan APK, signing/distribusi | [validation-release.md](references/validation-release.md) |
| Tampilan, tema, komponen, teks antarmuka, aksesibilitas, animasi | [ui-design-system.md](references/ui-design-system.md) |
| Crash, ANR, race, kegagalan jaringan/penyimpanan/engine, ketahanan | [stability-playbook.md](references/stability-playbook.md) |
| Menyatakan "siap rilis/produksi", audit kesiapan, keputusan go/no-go | [production-readiness.md](references/production-readiness.md) |

Satu tugas sering menyentuh beberapa baris tabel (mis. layar progres baru menyentuh invariants, ui-design-system, dan stability-playbook). Baca semuanya yang relevan; jangan berhenti di yang pertama.

Untuk review: pisahkan reproduksi, risiko berdasarkan kode, perbaikan host, dan hasil perangkat. Temuan parsial tetap terbuka bila jalur gagal atau race belum dibuktikan aman.

## Empat sumbu kualitas

Setiap perubahan dinilai pada sumbu yang disentuhnya. Menyadari sumbu mana yang berisiko membantu memilih bukti yang tepat.

1. **Benar.** Perilaku sesuai niat pada jalur normal dan jalur gagal. Bukti: regresi yang memeriksa hasil (transisi status, opsi output, identitas URL, query folder, kegagalan publikasi), bukan sekadar "tidak crash".
2. **Aman bagi data.** File, riwayat dan pengaturan pengguna bertahan melewati upgrade, interupsi dan kegagalan. Bukti: migrasi Room dengan snapshot schema, uji upgrade dari versi sebelumnya yang berisi data, tidak ada penghapusan URI tanpa verifikasi kepemilikan.
3. **Stabil.** Aplikasi bertahan dari proses dimatikan, jaringan putus, penyimpanan penuh, izin dicabut, dan engine yang berubah perilaku. Bukti: skenario pada stability-playbook dijalankan, bukan diasumsikan.
4. **Terasa premium.** Konsisten, tenang, responsif, mudah dibaca, dan jujur saat gagal. Premium berasal dari disiplin: ritme spasi, hierarki tipografi, status yang lengkap, gerak yang menjawab aksi, dan teks yang jelas. Dekorasi tambahan bukan ukurannya. Panduan lengkap ada di ui-design-system.

## Alur kerja per jenis tugas

**Perbaikan bug.** Temukan ID di BUG_FINDINGS, reproduksi atau buktikan lewat kode, tulis regresi yang gagal pada kode lama, perbaiki, jalankan regresi plus tes sekitarnya, lalu perbarui status dan checklist bug. Bila jalur gagal atau race belum terbukti aman, status tetap terbuka dengan catatan apa yang masih kurang.

**Fitur baru.** Pastikan fitur ada di roadmap atau diminta pengguna, baca invariants yang disentuh, rancang status UI lengkap sejak awal (memuat, kosong, galat, offline, izin dicabut, penyimpanan penuh, selesai, dibatalkan), lalu implementasikan bersama tesnya. Fitur yang hanya punya jalur sukses belum selesai.

**Poles UI/UX.** Tulis rencana desain singkat dulu (token warna/tipografi/spasi/bentuk dan sketsa tata letak ASCII), kritik rencana itu terhadap tampilan generik, baru bangun. Batasi perubahan visual per tugas pada layar atau komponen yang diminta dan ekstrak pola yang berulang ke komponen bersama. Merombak seluruh aplikasi dalam satu perubahan membuat regresi sulit dilacak.

**Audit stabilitas atau kesiapan.** Jalankan skenario dari stability-playbook, catat hasil per skenario, lalu nilai per gerbang di production-readiness. Laporkan apa adanya, termasuk gerbang yang belum diuji.

**Rilis.** Ikuti validation-release dan gerbang rilis di production-readiness. Skill ini tidak memberi izin menerbitkan; gunakan otorisasi tugas yang sudah diberikan.

## Implementasi

Ikuti gaya kode di sekitarnya; kode Kotlin/Python baru menggunakan empat spasi. Ubah hanya bagian yang mendukung tugas. Letakkan perilaku bersama pada helper yang sudah ada, bukan menyalin deteksi duplikat, penghapusan URI atau interpretasi progres ke layar lain.

Pilih regresi yang memeriksa hasil perilaku: transisi status, opsi output, identitas URL, query folder atau kegagalan publikasi. Perubahan Room harus memiliki migrasi yang mempertahankan data beserta snapshot schema. Jangan memakai reset database sebagai pengganti migrasi.

Kode yang stabil memenuhi beberapa kebiasaan dasar:

- Pekerjaan berat dan I/O berjalan di luar main thread; coroutine terikat pada scope yang jelas, dan pembatalan merambat (jangan menelan `CancellationException`).
- Kegagalan engine Python dipetakan ke kategori galat yang dikenal aplikasi, lalu diterjemahkan ke pesan pengguna. Teks mentah stderr yt-dlp tidak tampil sebagai pesan utama.
- Status yang tampil di UI berasal dari sumber kebenaran tunggal (database/state holder), bukan dari salinan lokal yang bisa menyimpang.
- Untuk teks antarmuka baru atau komponen yang sedang disentuh, pindahkan ke `strings.xml` secara bertahap. Codebase saat ini masih memiliki string literal berbahasa Indonesia langsung di dalam Composable (`strings.xml` semula hanya memuat `app_name`); jangan merombak seluruh layar yang tidak terkait dalam satu perubahan hanya demi ekstraksi string, tetapi jaga konsistensi istilah dan register bahasa Indonesia di seluruh alur.
- Teks dan tampilan tidak mengklaim lebih dari yang terbukti: nama penyedia lirik sesuai implementasi, karaoke tidak dijanjikan bebas vokal di semua lagu, dan artis hanya ditampilkan bila terbukti (aturan identitas ada di invariants).

## Verifikasi dan bukti

Jalankan pemeriksaan yang sebanding dengan risiko: tes unit/instrumen terdekat untuk perubahan kecil; build release yang di-minify dan uji perangkat nyata untuk perubahan yang menyentuh download, penyimpanan, migrasi, atau pengemasan. Build debug yang lulus tidak membuktikan build release bekerja (shrinker dan pengemasan runtime Python bisa mengubah perilaku).

Pengujian perangkat memakai HP fisik, bukan emulator, sesuai pilihan pengelola (rincian dan batas otorisasi ada di validation-release). Bila HP belum tersambung, kerjakan yang bisa dikerjakan dan catat tes perangkat sebagai tertunda.

Setelah pemeriksaan yang diperlukan lulus, lanjutkan ke hasil yang diminta. Bila build lama masih berjalan setelah interupsi, periksa proses, log dan metadata output sebelum memulai ulang. Hindari mengulang build/tes yang lulus tanpa perubahan atau kegagalan baru.

Untuk perubahan visual, bukti berupa tangkapan layar dari HP fisik proporsional dengan cakupan perubahan: layar/status yang disentuh untuk polesan terfokus, dan matriks penuh (mode terang dan gelap, skala font besar, lebar layar sempit, serta status galat/kosong) untuk perombakan tata letak/tema atau audit kesiapan produksi (G5). Periksa isi tangkapan sebelum disimpan ke `docs/images/` agar tidak memuat notifikasi atau data pribadi. Rincian ada di ui-design-system.

## Klaim kesiapan produksi

Nilai delapan gerbang pada [production-readiness.md](references/production-readiness.md): kebenaran fungsional, integritas data, stabilitas, performa, UI/UX dan aksesibilitas, keamanan dan privasi, rekayasa rilis, serta ketahanan engine dan kepatuhan distribusi. Beri setiap gerbang salah satu status: **Terbukti** (ada bukti: perintah, hasil, perangkat, versi), **Sebagian**, **Belum diuji**, atau **Gagal**.

Kata "siap produksi" hanya dipakai bila semua gerbang Terbukti, atau pengelola secara eksplisit menerima risiko pada gerbang tertentu. Selain itu, laporkan tabel statusnya dan sebutkan langkah berikutnya yang paling bernilai. Laporan jujur yang menyebut gerbang belum diuji lebih berguna bagi pengelola daripada klaim yang kelak terbantah di perangkat pengguna.

## Dokumentasi dan serah-terima

Perbarui dokumen yang terdampak: ID/checklist bug, laporan pengujian perangkat, dan ringkasan kemajuan jika ada keputusan penting. Catat versi/hash artefak yang benar-benar diuji, perintah dan hasil aktual, serta cakupan yang belum dijalankan. Jangan membekukan jumlah tes atau versi release dalam skill ini.

Komunikasikan hasil dalam bahasa pengguna, dengan pembaruan singkat saat build/tes panjang. Jawaban akhir menyebut perubahan, hasil verifikasi, batas cakupan dan tautan artefak/laporan yang relevan. Untuk tugas kesiapan, sertakan tabel status gerbang. Skill ini tidak memberi izin tambahan untuk menghapus data, memasang APK, mengunggah berkas, atau menerbitkan release; gunakan otorisasi tugas yang sudah diberikan.
