---
name: tubeflow-dev
description: "Kembangkan, perbaiki, review, uji, dan siapkan release aplikasi Android TubeFlow dalam repository downloader-youtube. Gunakan untuk perubahan Kotlin/Compose, engine Python/yt-dlp, Room/WorkManager, SAF/MediaStore, lirik, dan artefak release proyek ini."
---

# Pengembangan TubeFlow

Bantu menyelesaikan perubahan TubeFlow dengan menjaga hasil download, data pengguna, dan bukti pengujian. Proyek ini aplikasi Android on-device, tanpa backend. Jalankan perintah dari root checkout yang memiliki package `id.local.tubeflow`; jangan menerapkan instruksi ini pada proyek lain.

## Mulai dari kondisi yang sebenarnya

- Baca [AGENTS.md](../../../AGENTS.md) dan bagian yang relevan pada [README](../../../README.md). Jangan ubah AGENTS.md yang sudah ada tanpa permintaan pengelola.
- Untuk perbaikan, cari ID, status, titik kode dan checklist pada [BUG_FINDINGS](../../../docs/BUG_FINDINGS.md), lalu baca laporan audit/perangkat yang dirujuk. Untuk fitur, baca [DEVELOPMENT_ROADMAP](../../../docs/DEVELOPMENT_ROADMAP.md) hanya pada usulan terkait.
- Cocokkan dokumentasi dengan kode terkini. [PROGRESS](../../../docs/PROGRESS.md) memuat sejarah keputusan; bagian lama dapat sudah digantikan perbaikan berikutnya. Jangan menghidupkan kembali heartbeat progres lama atau menyimpulkan seluruh bug sudah selesai.
- Ambil versi aplikasi/SDK/dependensi dari Gradle, engine dari `app/src/main/assets/engine.json`, dan schema dari Room. AGENTS.md masih menyebut SDK 35, sementara konfigurasi yang diperiksa saat skill dibuat memakai compile/target 37. Gunakan konfigurasi build aktual; jangan menurunkan SDK agar sesuai catatan lama.

## Pilih panduan yang dibutuhkan

- Perubahan download, database, penyimpanan, playlist, progres, sampul atau lirik: baca [invariants.md](references/invariants.md) pada bagian terkait sebelum mengedit.
- Build, pemilihan tes, pemasangan APK, atau signing/distribusi: baca [validation-release.md](references/validation-release.md).
- Review: pisahkan reproduksi, risiko berdasarkan kode, perbaikan host, dan hasil perangkat. Temuan parsial tetap terbuka bila jalur gagal atau race belum dibuktikan aman.

## Implementasi dan serah-terima

Ikuti gaya kode di sekitarnya; kode Kotlin/Python baru menggunakan empat spasi. Ubah hanya bagian yang mendukung tugas. Letakkan perilaku bersama pada helper yang sudah ada, bukan menyalin deteksi duplikat, penghapusan URI atau interpretasi progres ke layar lain.

Pilih regresi yang memeriksa hasil perilaku: transisi status, opsi output, identitas URL, query folder atau kegagalan publikasi. Perubahan Room harus memiliki migrasi yang mempertahankan data beserta snapshot schema. Jangan memakai reset database sebagai pengganti migrasi.

Setelah pemeriksaan yang diperlukan lulus, lanjutkan ke hasil yang diminta. Bila build lama masih berjalan setelah interupsi, periksa proses, log dan metadata output sebelum memulai ulang. Hindari mengulang build/tes yang lulus tanpa perubahan atau kegagalan baru.

Perbarui dokumen yang terdampak: ID/checklist bug, laporan pengujian perangkat, dan ringkasan kemajuan jika ada keputusan penting. Catat versi/hash artefak yang benar-benar diuji, perintah dan hasil aktual, serta cakupan yang belum dijalankan. Jangan membekukan jumlah tes atau versi release dalam skill ini.

Komunikasikan hasil dalam bahasa pengguna, dengan pembaruan singkat saat build/tes panjang. Jawaban akhir menyebut perubahan, hasil verifikasi, batas cakupan dan tautan artefak/laporan yang relevan. Skill ini tidak memberi izin tambahan untuk menghapus data, memasang APK, mengunggah berkas, atau menerbitkan release; gunakan otorisasi tugas yang sudah diberikan.
