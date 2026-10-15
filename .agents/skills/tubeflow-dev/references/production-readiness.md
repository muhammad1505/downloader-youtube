# Gerbang kesiapan produksi TubeFlow

Daftar isi: Cara menilai · G1 Kebenaran · G2 Integritas data · G3 Stabilitas · G4 Performa · G5 UI/UX dan aksesibilitas · G6 Keamanan dan privasi · G7 Rekayasa rilis · G8 Engine dan distribusi · Format laporan

## Cara menilai

Setiap gerbang berisi pemeriksaan yang dapat dibuktikan. Beri status **Terbukti** (ada bukti: perintah, hasil, perangkat/versi), **Sebagian**, **Belum diuji**, atau **Gagal**. Skala bukti mengikuti risiko: gerbang yang menyentuh data pengguna (G2) dan build yang benar-benar dirilis (G7) memerlukan bukti pada perangkat nyata dan artefak rilis, bukan hanya tes host.

Gerbang tidak menggantikan penilaian. Jika sebuah pemeriksaan tidak relevan pada rilis tertentu, tulis alasannya.

## G1. Kebenaran fungsional

- Alur utama berjalan ujung ke ujung pada perangkat: tempel tautan, pilih format/kualitas, unduh, hasil muncul di folder dan riwayat; playlist; lirik; sampul.
- Tes otomatis yang relevan lulus (Kotlin: `testDebugUnitTest`; Python: `unittest` pada `scripts`), termasuk regresi untuk bug yang diperbaiki. Regresi memeriksa hasil perilaku, bukan sekadar pemanggilan fungsi.
- Dua salinan bootstrap engine (`scripts/engine_bootstrap.py` dan asset Android) identik dan diuji host.
- Lirik: kecocokan metadata belum membuktikan sinkron; dengarkan beberapa posisi lagu di HP sebelum mengklaim sinkronisasi beres.
- Bug pada BUG_FINDINGS yang menjadi target rilis berstatus jelas; yang masih terbuka dicantumkan, bukan disembunyikan.

## G2. Integritas data

- Setiap perubahan schema Room punya migrasi dan snapshot schema; tidak ada reset database sebagai jalan pintas.
- Upgrade dari versi sebelumnya dengan data nyata diuji dan data utuh sesudahnya.
- Tidak ada penghapusan URI/file tanpa verifikasi bahwa file itu milik aplikasi atau pilihan eksplisit pengguna; penghapusan memakai helper bersama.
- Tidak ada file setengah jadi atau sementara yang tampak sebagai unduhan selesai; sisa sementara dibersihkan.
- Cadangan/ekspor pengaturan dan riwayat (bila ada) dapat dipulihkan.

## G3. Stabilitas

- Skenario pada [stability-playbook.md](stability-playbook.md) dijalankan; kegagalan masuk BUG_FINDINGS.
- Tidak ada crash atau ANR pada skenario tersebut; lint tidak menyisakan error (catat secara jujur warning yang memang diketahui seperti `SelectedPhotoAccess` dan pin `GradleDependency` untuk `onnxruntime-android:1.23.2` tanpa mengklaim "0 warning"); temuan StrictMode debug pada jalur utama ditangani.
- Pembatalan, jeda, dan race utama punya tes atau argumen keamanan yang jelas.

## G4. Performa

- Ukur di HP fisik dengan angka, bukan kesan: waktu start dingin (mis. `adb shell am start -W`), kemulusan scroll daftar besar (mis. `dumpsys gfxinfo` atau Macrobenchmark bila proyek memilikinya), memori saat playlist besar.
- Bandingkan terhadap hasil sebelumnya pada perangkat yang sama; catat perangkat. Tidak ada klaim "lebih cepat" tanpa pembanding.
- Pertimbangkan Baseline Profile bila start atau scroll terasa berat; keputusan memasangnya mengikuti hasil ukur.
- Ukuran APK diperiksa karena runtime Python dan engine cenderung besar; pertimbangkan pemisahan ABI atau format distribusi bila relevan, sebagai keputusan pengelola.

## G5. UI/UX dan aksesibilitas

- Layar yang disentuh mengikuti [ui-design-system.md](ui-design-system.md): token tema, status lengkap, teks konsisten (string baru atau komponen yang diubah diekstrak bertahap ke `strings.xml`).
- Tangkapan layar dari HP fisik sesuai cakupan (untuk audit G5 penuh: terang dan gelap, skala font 1,3x dan 2,0x, lebar sempit, status kosong dan galat; untuk perubahan UI terfokus: layar dan status yang disentuh). Periksa isinya agar tidak memuat data pribadi.
- Smoke test TalkBack pada alur utama; kontras dan target sentuh memenuhi batas.
- Edge-to-edge, back, rotasi, dan notifikasi berperilaku benar.

## G6. Keamanan dan privasi

- Izin seminimal mungkin dan masing-masing dapat dijelaskan; izin runtime diminta pada konteks yang masuk akal dan penolakan ditangani.
- Tidak ada rahasia, kunci signing, atau data pribadi di repository atau log. Komponen yang diekspor dan FileProvider dibatasi seperlunya.
- Lalu lintas jaringan memakai HTTPS; pengecualian cleartext (bila ada) dibenarkan dan dibatasi. Aturan cadangan/ekstraksi data diperiksa agar tidak membocorkan data yang tidak semestinya.
- Aplikasi tanpa backend: pastikan tidak ada telemetri atau pengiriman data yang tidak diumumkan; bila ada, tercantum pada kebijakan privasi.
- Shrinker/R8 tidak merusak refleksi, serialisasi, Room, atau jembatan Python (diuji pada build release).

## G7. Rekayasa rilis

- Kandidat dibangun dengan `bash scripts/build_release.sh --no-daemon` (unit Kotlin, lint debug/release, assembleRelease, bundleRelease). Hasil Gradle tanpa signing berstatus unsigned, dan build sukses bukan bukti APK dapat diinstal.
- APK final diaudit: `scripts/audit_native.py`, `scripts/audit_release_zip_fields.py`, `apksigner verify --print-certs`, dan `zipalign -c -P 16 4`; metadata versionName/versionCode/package, non-debuggable, checksum engine dan kesamaan bootstrap diperiksa. AAB: `jarsigner`, fingerprint sertifikat, `bundletool validate`, dan audit native.
- Build release yang di-minify dan ditandatangani diuji pada HP fisik, bukan hanya build debug; APK dipilih sesuai ABI, dan HP yang diuji dicatat (model, Android, ABI, page size).
- versionCode naik dan konsisten dengan versionName dan catatan rilis; update memakai kunci yang sama tanpa uninstall atau clear data.
- Keystore produksi yang ada dipakai dan tidak dibuat ulang; password tidak dicetak atau dimasukkan ke argumen command-line, dan konfigurasi atau private key tidak masuk repo.
- Verifikasi APK terpasang dari hash APK yang ditarik dari HP beserta sertifikatnya; layar "terpasang" saja belum cukup. Bila ADB menolak dengan `INSTALL_FAILED_VERIFICATION_FAILURE`, gunakan installer normal bila diizinkan; jangan mematikan verifier.
- Artefak rilis terkini disimpan di `app/build/outputs/apk/release/` (atau `app/build/outputs/distribution/<version>/` bersama `release.json`, `SHA256SUMS` dan sertifikat publik bila paket distribusi diminta); manifest mencerminkan hasil tes aktual dan checksum dihitung ulang setelah metadata berubah. Untuk menjaga ruang disk, biner rilis lama di `distribution/` tidak ditumpuk tanpa batas — catat ukuran dan SHA256-nya di `docs/DISTRIBUTION_ARCHIVED_SHA256.txt` sebelum membersihkan biner historis. Berkas mapping R8 disimpan untuk membaca crash.
- Catatan rilis menyebut perubahan, masalah yang diketahui, dan cakupan uji. Keystore dan artefak signed belum membuktikan cadangan eksternal, publikasi toko, pemenuhan lisensi, atau kompatibilitas 16 KB yang belum diuji. Ikuti [validation-release.md](validation-release.md) untuk perintah dan artefak.

## G8. Engine dan distribusi

- Perilaku saat yt-dlp rusak karena situs berubah: pesan jelas, aplikasi tetap berguna, dan ada jalur pembaruan engine yang terdokumentasi (versi diambil dari `engine.json`, bukan ditulis ulang di dokumen).
- Dokumentasikan siapa yang memperbarui engine, seberapa sering, dan bagaimana hasil pembaruan diuji sebelum dirilis.
- Kebijakan distribusi: toko aplikasi dan ketentuan layanan platform sumber dapat membatasi aplikasi pengunduh. Pilihan kanal distribusi dan penerimaan risikonya adalah keputusan pengelola; catat risikonya pada laporan, jangan diputuskan sepihak.

## Format laporan

Gunakan tabel ini pada jawaban akhir untuk tugas audit atau rilis:

| Gerbang | Status | Bukti (perintah/hasil/perangkat/versi) | Celah dan langkah berikutnya |
|---|---|---|---|
| G1 Kebenaran fungsional | | | |
| G2 Integritas data | | | |
| G3 Stabilitas | | | |
| G4 Performa | | | |
| G5 UI/UX dan aksesibilitas | | | |
| G6 Keamanan dan privasi | | | |
| G7 Rekayasa rilis | | | |
| G8 Engine dan distribusi | | | |

Di bawah tabel, tulis satu kesimpulan: siap, siap dengan risiko yang diterima (sebutkan), atau belum siap (sebutkan tiga langkah dengan nilai terbesar).
