# Signing produksi TubeFlow

Keystore pertama dibuat atas permintaan pengelola pada 10 Oktober 2026. Tidak ada keystore sebelumnya yang ditimpa. Kunci ini digunakan untuk artefak release lokal, tanpa publikasi ke toko atau server.

## Berkas privat

- Keystore PKCS12: `$HOME/.local/share/tubeflow/signing/TubeFlow-release.p12`.
- Password dan empat variabel `TUBEFLOW_KEYSTORE_*` / `TUBEFLOW_KEY_*`: `release-signing.env` dalam direktori yang sama.
- Alias: `tubeflow-release`; RSA 4096-bit, SHA256withRSA, validitas 10.000 hari.
- Direktori bermode `700`; keystore dan konfigurasi bermode `600`. Password tidak dimasukkan ke dokumentasi atau argumen command-line.
- Sertifikat publik: `TubeFlow-release-cert.pem` dalam direktori signing.

Simpan backup keystore beserta kredensial di tempat aman terpisah dari komputer ini. Backup belum dibuat ke lokasi eksternal. Gunakan kunci yang sama untuk setiap pembaruan; jangan membuat keystore baru saat menaikkan versi. Jangan memasukkan berkas privat ke Git atau paket distribusi.

## Membuat artefak

```sh
bash scripts/build_release.sh -PuseGoogleMirror=true --no-daemon
```

Script memakai konfigurasi lokal di atas bila empat variabel signing belum lengkap; CI dapat menyediakannya melalui environment. Override lokasi konfigurasi menggunakan `TUBEFLOW_SIGNING_ENV`. Hanya gunakan file konfigurasi dari sumber tepercaya karena script memuatnya melalui `source`.

Output Gradle: APK per ABI dalam `app/build/outputs/apk/release/` dan AAB `app/build/outputs/bundle/release/app-release.aab`. AAB digunakan untuk distribusi melalui layanan yang mendukung App Bundle; APK dipasang langsung sesuai ABI perangkat.

## Pembaruan dan validasi

APK testing yang sebelumnya diuji di HP memakai kunci testing lokal. APK produksi berkunci baru tidak dapat menimpanya dengan package ID yang sama. Pindah instalasi membutuhkan rencana pemindahan data; jangan uninstall/clear data otomatis. Produksi 1.0.0 kemudian terpasang di HP dengan hash terverifikasi (rincian di bawah). Release 1.0.1 memakai kunci produksi yang sama dan versionCode lebih tinggi; 1.0.1 kemudian terpasang dan root/ukuran folder terverifikasi pada HP (rincian di bawah).

Verifikasi setiap APK dengan `apksigner verify --verbose --print-certs`, `zipalign -c -P 16 4`, audit native dan audit refleksi ZIP. Verifikasi tanda tangan AAB memakai `jarsigner -verify`; cocokkan fingerprint sertifikat dengan APK/keystore. Naikkan `versionCode` untuk pembaruan berikutnya.

Signing produksi tidak menutup temuan fungsional pada [daftar bug](BUG_FINDINGS.md) atau menggantikan matriks perangkat pada [persiapan rilis](RELEASE.md).

## Hasil release pertama

Versi **1.0.0**, `versionCode = 1`, package `id.local.tubeflow`. Build signed APK/AAB selesai dalam 1m22s. Pemeriksaan unit Kotlin 55 tes dan lint debug/release lulus (0 masalah); kode aplikasi tidak berubah sejak 37 tes Python dan pengujian fisik Mix/duplikat terakhir.

Fingerprint SHA256 sertifikat produksi:

```text
EB:E3:B4:0E:B4:2C:9B:60:FD:04:4A:01:1F:C6:A9:E0:CB:FC:DE:97:C5:3A:CC:B2:EA:49:93:4F:49:34:DB:D0
```

Artefak salinan distribusi berada di `app/build/outputs/distribution/1.0.0/`, bersama `SHA256SUMS`, `release.json`, dan sertifikat publik. Tidak ada private key/password dalam folder distribusi.

| Artefak | Byte | SHA256 |
| --- | ---: | --- |
| `TubeFlow-1.0.0-arm64-v8a-release.apk` | 62.787.275 | `8e9ae75a91c6323ab74fa385cd722096df41c2a46a6bfb8e5354ed749db3e1c0` |
| `TubeFlow-1.0.0-armeabi-v7a-release.apk` | 55.123.969 | `b37ae8c2ba7a664278925165de9b89e4f8130e1cc4ecbd3a5bb3488ae7fb3d69` |
| `TubeFlow-1.0.0-x86_64-release.apk` | 65.919.710 | `a51ddb95fb0c1609d38d3c1eb79eecc3aba5dbc139c002233594c61753eee68d` |
| `TubeFlow-1.0.0-release.aab` | 209.949.827 | `fa7ef1103e0e82cb5ea53ec49cf7ea8a7ef9c843fc103106ecfaa0902dad9e5f` |

Ketiga APK memakai sertifikat yang sama, bukan sertifikat testing; `apksigner` memverifikasi signature v2, zipalign lulus, manifest tidak debuggable. Audit native: 255/255/257 ELF, tanpa kegagalan alignment 64-bit; audit refleksi ZIP: masing-masing 13 kelas, 0 kegagalan. Hash engine resmi di APK/AAB sesuai manifest pin.

AAB: `jarsigner` memverifikasi tanda tangan; fingerprint sama dengan APK/keystore. **Bundletool validate** melalui dependency AGP lokal lulus; audit 1.024 ELF bundle tidak menemukan kegagalan alignment 64-bit. Log lokal: `/tmp/tubeflow-production-release-build.log`, `/tmp/tubeflow-production-bundle-validation.log`, `/tmp/tubeflow-production-aab-verification.txt`.

`jarsigner` mengeluarkan peringatan sertifikat self-signed/tidak ada trust chain, tanpa timestamp, atribut POSIX, dan perbedaan pembacaan `JarInputStream` karena posisi manifest ZIP. Verifikasi memakai `JarFile` serta validasi struktur bundletool lulus; laporan ini tidak menyatakan pemeriksaan JAR tanpa warning.

Pada build awal, APK produksi belum dipasang. Dalam sesi pemasangan berikutnya atas permintaan pengguna, APK 1.0.0 berhasil terpasang melalui installer bawaan dan Play Protect; hash yang ditarik dari HP cocok (`8e9ae75a91c6323ab74fa385cd722096df41c2a46a6bfb8e5354ed749db3e1c0`), sertifikat produksi cocok. Agent tidak menjalankan uninstall/clear data; proses penggantian instalasi tidak teramati penuh karena ada interaksi pengguna. Riwayat yang kemudian diperiksa kosong, sedangkan file publik masih ada. Tidak ada klaim retensi data testing pada perpindahan kunci ini.

Belum ada unggahan toko atau backup signing ke media eksternal. Perbaikan penghitung folder berikutnya dicatat pada [STORAGE_SIZE_2026-10-10.md](STORAGE_SIZE_2026-10-10.md).

## Release 1.0.1 — penghitung folder

Versi **1.0.1**, `versionCode = 2`, menggunakan sertifikat produksi yang sama dengan 1.0.0. Build signed APK/AAB berhasil; 60 tes Kotlin, 37 tes Python, lint debug/release 0 masalah. APK signature v2, zipalign, manifest non-debuggable, audit native 255/255/257 ELF dan refleksi ZIP 13 kelas per APK lulus. AAB signature/certificate, bundletool validate dan audit 1.024 ELF lulus. Engine/bootstrap paket cocok dengan pin/sumber. Peringatan JAR dan deprecation internal Gradle tetap dicatat seperti release sebelumnya.

Distribusi lokal: `app/build/outputs/distribution/1.0.1/`, bersama sertifikat publik, `release.json` dan `SHA256SUMS`. Salinan 1.0.0 dipertahankan. Tidak ada keystore/password dalam distribusi.

| Artefak | Byte | SHA256 |
| --- | ---: | --- |
| `TubeFlow-1.0.1-arm64-v8a-release.apk` | 62,791,091 | `3436ec61465a5a6e7a710158d21529c75a5feb50c9a3dbf35587aa004acf23ac` |
| `TubeFlow-1.0.1-armeabi-v7a-release.apk` | 55,127,785 | `bb14aa019090cb16691da99c4344935cfaa54d7725b77ee1d4e1c1d344d4377f` |
| `TubeFlow-1.0.1-x86_64-release.apk` | 65,923,526 | `e4f8720bdf63acd6bc049a80c8a6f3bad9fdfe739dc058ab227359f99319891e` |
| `TubeFlow-1.0.1-release.aab` | 209,954,847 | `1ceee34932ccd86229218b6f10a03ddd2053174762168e85191a1cf26f29538a` |

APK 1.0.1 **sudah terpasang** di Realme Android 10 melalui installer bawaan. APK yang ditarik dari HP cocok hash ARM64 di tabel dan sertifikat produksi. Root default, angka 246,4 MB dan persistensi setelah restart terverifikasi. Hasil serta batas cakupan ada di [STORAGE_SIZE_2026-10-10.md](STORAGE_SIZE_2026-10-10.md). Log build/validasi final tersimpan di `build/reports/storage-*.log` dan `storage-final-verification.json`. Signing tidak menyatakan temuan runtime lainnya selesai.
