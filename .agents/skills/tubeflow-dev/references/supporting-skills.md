# Skill pendukung TubeFlow

Pengelola meminta pemasangan tujuh sumber berikut pada 18 Oktober 2026. Skill utama tetap `tubeflow-dev`; panduan pendukung dipilih menurut pekerjaan, bukan dijalankan bersamaan pada setiap perubahan.

## Lokasi dan pemakaian

Skill dipasang secara global di `$CODEX_HOME/skills`, dengan fallback `~/.codex/skills`. Baca `SKILL.md` lokal sebelum menerapkannya. Jika belum tersedia pada komputer lain, gunakan `skill-installer` dari sumber dan revisi berikut; jangan menganggap instalasi mesin ini ikut tersimpan dalam Git.

| Sumber | Folder skill | Cakupan TubeFlow | Revisi terpasang |
|---|---|---|---|
| [Taste](https://github.com/leonxlnx/taste-skill) | `design-taste-frontend` | Desain halaman web proyek bila diperlukan. Versi ini secara eksplisit mengecualikan layar aplikasi; jangan memaksakan workflow landing page ke Compose. | `f624f37a8c650b9cc550253104925efe4c8de246` |
| [Anti Slop](https://github.com/miqdadbadjuber/anti-slop) | `antislop`, `antislop-ui`, `antislop-copywriting`, `antislop-human`, `antislop-layoutmobile`, `antislop-code` | Kritik visual, aksesibilitas, tata letak mobile, teks, dan komentar sesuai kebutuhan. | `439a2ef5b9dd0ebe74e8113ff43f5928d0fe2a6a` |
| [Impeccable](https://github.com/pbakaus/impeccable) | `impeccable` | Panduan UI/UX Android, konsistensi komponen, hierarki, layout, dan audit. Gunakan mode Operate untuk menu aplikasi. | `08d5b470495d242ca0470f4aeae1956c3a074f83` |
| [Humanizer](https://github.com/blader/humanizer) | `humanizer` | Meninjau teks yang kaku; pertahankan fakta dan istilah produk. | `8c307f367c91bd974002f10f2be2965d642c13df` |
| [Stop Slop](https://github.com/hardikpandya/stop-slop) | `stop-slop` | Mengurangi pengisi dan pola tulisan berulang. | `8da1f030185bdfe8471220585162991eaeb970e9` |
| [No AI Slop](https://github.com/petergyang/no-ai-slop) | `no-ai-slop` | Pemeriksaan tambahan untuk teks dan dokumentasi yang diminta. | `000650b156983f5159695b441477f4e63b25dc85` |
| [Avoid AI Writing](https://github.com/conorbronsdon/avoid-ai-writing) | `avoid-ai-writing` | Audit/edit prosa dengan perlindungan fakta, kutipan, kode, dan pengenal. Aturan/detektornya dikalibrasi untuk bahasa Inggris. | `0a4f1ad101683fd8734241ed46430a0b4a9af729` |

## Penerapan pada proyek

- Arah pengelola: perluas nuansa pemutar musik yang sudah dibuat ke Ringkasan, Download, Antrean, Koleksi, dan Pengaturan. Ambil token dan komponen bersama dari tema aktual; jangan mengganti identitas atau perilaku hanya untuk mengikuti contoh skill.
- Gunakan panduan native Android Impeccable dan `ui-design-system.md`; verifikasi hasil pada HP fisik, termasuk tema terang/gelap, font besar, serta status kosong dan galat.
- Anti Slop memiliki pilihan `during`/`after`. Baca preferensi global yang sah atau tanyakan pilihan saat pertama diaktifkan jika belum ditentukan. Instalasi ini belum memilih atau menyimpan mode pengguna. Instalasi dan penggunaan adalah langkah berbeda.
- Instruksi pemasangan langsung dari pengelola mengizinkan pengunduhan skill. Wizard upstream bukan alasan untuk meminta izin pemasangan lagi atau menambahkan pointer ke `AGENTS.md` yang sudah diminta tetap utuh. Referensi ini menjadi penghubung proyek; skill pendukung Anti Slop tersedia sebagai folder saudara di direktori skill global.
- Gunakan skill tulisan ketika mengerjakan teks. Jangan menjalankan seluruh detektor Inggris pada teks Indonesia lalu menganggap skornya sebagai bukti kualitas. Pertahankan bahasa Indonesia, ketidakpastian teknis, atribusi, angka, dan pesan galat yang akurat.
- Jangan menerapkan penulisan ulang prosa ke Kotlin, XML, JSON, URL, nama file, tag media, atau timestamp lirik. Perbaikan fungsional tetap melalui analisis kode dan pengujian `tubeflow-dev`.
- Instalasi tidak menambahkan dependensi APK, hook otomatis, layanan eksternal, atau izin perangkat. Tinjau helper yang akan dijalankan dan ikuti batas otorisasi tugas saat itu.

## Checkpoint permintaan 18 Oktober 2026

Status implementasi dan pengujian terbaru ada pada `docs/REPAIR_2026-10-18.md`. Daftar berikut merekam masukan audit awal; jangan mengulang perbaikan yang telah diverifikasi dalam laporan tersebut. Sesi pengerjaan 18 Oktober melanjutkan Anti Slop during; preferensi global tidak diubah.

Instalasi skill selesai. Upgrade visual dan perbaikan berikut belum dinyatakan selesai oleh pemasangan ini:

1. Audit konsistensi semua menu terhadap perubahan lokal terbaru di `Theme.kt`, `components.kt`, dan `PlayerUi.kt`; pertahankan perubahan pengelola yang belum di-commit.
2. Perbaiki pesan palsu saat video dari Riwayat berhasil dibuka. Bukti kode: `launchFile` di `ui/components.kt` memakai `job.outputUri?.let { launchMedia(...) } ?: "File tidak tersedia."`; `launchMedia` mengembalikan `null` pada keberhasilan. Pisahkan URI kosong dari hasil sukses, lalu uji buka dan bagikan audio/video serta URI kosong.
3. Periksa riwayat galat LRCLIB dan jalur otomatis/manual: bedakan HTTP 429, kegagalan jaringan, respons invalid, pencocokan identitas/durasi, serta kegagalan publikasi LRC. Jangan menyimpulkan penyebab dari keluhan saja atau memicu pencarian massal.
4. Lanjutkan verifikasi fitur 1.1.17 dari `docs/MEDIA_FEATURES_1_1_17.md` dan laporan perangkat terbaru. Cocokkan hash APK terpasang dengan artefak terbaru; jangan mengandalkan checkpoint build lama.
