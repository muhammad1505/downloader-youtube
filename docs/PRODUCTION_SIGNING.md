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

## Release 1.0.9 — sistem metadata/resume (13 Oktober 2026)

Kunci produksi yang sama dipakai untuk APK×3/AAB lokal; tidak membuat keystore baru atau memublikasikan ke toko. Distribusi `app/build/outputs/distribution/1.0.9/` memuat sertifikat publik, release.json dan SHA256SUMS. ARM64 final 62.853.907 byte, SHA256 `e3c5b3f3587614f55f6d56a670d04a41e1f55a6325ddc02e3d1c9958a0cdf91f`; hash APK yang ditarik dari HP identik. Candidate awal yang menemukan fallback artist uploader diarsipkan privat dalam build/reports dan diganti pada distribusi lokal oleh hasil koreksi tag.

102 tes Kotlin/61 Python lulus; lint debug/release 0 masalah. Signature/cert, zipalign 16 KB, manifest non-debuggable, native 255/255/257 dan refleksi ZIP 13 kelas per APK lulus. AAB jarsigner/cert, bundletool validate dan native 1.024 ELF lulus; pin engine/bootstrap cocok. Deprecation internal Gradle dan peringatan sertifikat JAR self-signed/timestamp tetap dicatat terpisah. Update tanpa uninstall/clear data mempertahankan riwayat; cakupan fisik dan batasnya ada pada [laporan sistem](SYSTEM_METADATA_RESUME_2026-10-13.md).

## Release 1.0.10 — validasi lirik/kredit sumber (13 Oktober 2026)

Memakai keystore produksi yang sama, versionCode 11, Room v8. Build signed APK×3/AAB sukses dalam 8m30s; 110 tes Kotlin dan 61 tes Python lulus, lint debug/release 0 masalah. Signature/cert, zipalign 16 KB, manifest non-debuggable, audit native 255/255/257 ELF dan refleksi ZIP 13 kelas per APK lulus. AAB signature/certificate, native 1.024 ELF dan bundletool validate lulus. Pin engine dan bootstrap paket cocok, tidak ada pembaruan engine.

ARM64 `TubeFlow-1.0.10-arm64-v8a-release.apk`: 62.858.143 byte, SHA256 `6c80ff2a13f5eac65735534227ad51c9f1d8cc6315e6d145093932e9d676ccf5`. APK sudah di-update melalui ADB `install -r` pada Realme Android 10; hash yang ditarik dari HP cocok dan 27 riwayat lama tetap ada. Distribusi `app/build/outputs/distribution/1.0.10/` memuat APK per ABI, AAB, sertifikat publik, release.json dan SHA256SUMS; artefak versi lama dipertahankan. Deprecation Gradle dan peringatan JAR self-signed/timestamp terpisah dari lint.

[Laporan lirik dan platform](LYRICS_PLATFORMS_2026-10-13.md) mencatat pengujian fungsional serta batas sinkronisasi; signing tidak membuktikan kompatibilitas seluruh perangkat/platform. Tidak memublikasikan ke toko atau membagikan private key.


## Release 1.0.11 — normalisasi structured track (13 Oktober 2026)

VersionCode 12, Room v8, sertifikat produksi sama. Signed APK×3/AAB berhasil dalam 10m26s; 112 tes Kotlin, 61 tes Python pada batch (engine tidak berubah setelah tes), lint debug/release 0 masalah. Audit signature/certificate, zipalign 16 KB, manifest non-debuggable, native 255/255/257 ELF dan ZIP reflection 13 kelas per APK lulus. AAB signature/cert, native 1.024 ELF dan bundletool validate lulus. Peringatan JAR self-signed/timestamp dan deprecation Gradle terpisah dari lint.

Distribusi lokal `app/build/outputs/distribution/1.0.11/` mempertahankan versi sebelumnya. ARM64 62.858.595 byte, SHA256 `800f3fd703a539c8996c9611ca1c50ae25ba88d661fbb6e55bba70585e55bd48`; sudah terpasang dengan `install -r` dan hash yang ditarik dari HP identik. Pengelola mengonfirmasi menghapus riwayat selama menunggu build; empat file publik sebelumnya tetap ada, agent tidak uninstall/clear data. Tidak mengklaim retensi 28 baris pada update ini. Hasil fungsional tercatat pada [laporan lirik/platform](LYRICS_PLATFORMS_2026-10-13.md); tidak ada publikasi toko.


## Release 1.0.12 — alias SoundCloud dan pemeringkatan lirik (13 Oktober 2026)

VersionCode13, Room v8, keystore produksi sama. Signed APK×3/AAB sukses dalam 5m24s; 116 Kotlin, lint debug/release 0 masalah; 61 Python pada batch tanpa perubahan engine setelah tes. Audit signature/cert, zipalign 16 KB, native 255/255/257 ELF dan ZIP reflection 13 kelas per APK lulus; AAB jarsigner/cert, 1.024 ELF dan bundletool validate lulus. Peringatan JAR self-signed/timestamp dan deprecation Gradle tetap tercatat terpisah.

ARM64 62.859.063 byte, SHA256 `e6fe701f96de98c83c0ab4cca598b0b71e02978d9df30c54bd44d504ba83d60a`; sudah terpasang di HP dengan `install -r`, hash pulled identik. Satu riwayat SoundCloud bertahan setelah update/restart, duplikat link bersih ditolak dan enam file tetap utuh. Distribusi `app/build/outputs/distribution/1.0.12/` menyertakan APK×3/AAB/cert/release.json/SHA256SUMS, versi sebelumnya dipertahankan, tidak upload toko. Delay lirik Ya Sudahlah 0,5 detik masih terbuka; [laporan fungsional](LYRICS_PLATFORMS_2026-10-13.md).


## Release 1.1.0 — Smart Media (13 Oktober 2026)

VersionCode14, Room9, keystore produksi yang sama. Build final teroptimasi APK×3/AAB sukses;136Kotlin/76Python lulus, lint debug/release0masalah. Signature/certificate, zipalign16KB, audit native/reflectiveZIP serta manifest nondebuggable lulus pada ketiga APK; AAB jarsigner/certificate/native dan bundletool validate lulus. Engine ZIP yang dipin tidak berubah.

Distribusi `app/build/outputs/distribution/1.1.0/` memuat APK, AAB, sertifikat publik, release.json dan SHA256SUMS. ARM64 63.008.739byte, SHA256 `06da7742a451ad27ae8dd88b15c31fad0b047e2bffcead4de1a6f9482e45eda6`, bertambah149.676byte (~0,24%) dibanding1.0.12. Update `install -r` berhasil pada RealmeAndroid10 dan hash base.apk cocok; data nonempty dan hash audio lama dipertahankan. Tidak uninstall/clear-data, tidak upload toko, tidak membuat ulang keystore. [Cakupan dan batas pengujian](SMART_MEDIA_UPGRADE_2026-10-13.md).


## Release 1.1.1 — dashboard Ringkasan media dan penguatan cari ulang lirik (13 Oktober 2026)

VersionCode15, Room9 (tanpa migrasi — schema tidak berubah), keystore produksi yang sama (signer SHA-256 `ebe3b40e…`, identik rilis sebelumnya). APK×3/AAB ditandatangani oleh `scripts/build_release.sh`; 145 tes Kotlin lulus, **76 tes Python dijalankan ulang ulang sesi dokumentasi → `OK` (23,173 detik)**, lint debug/release “No issues found”. Audit dijalankan ulang sesi dokumentasi pada artefak distribusi (source tidak berubah sesudah build): native 255/255/257 ELF per APK + AAB 1.024 ELF 0 gagal 16 KB, ZIP reflektif 13 kelas 0 gagal, `zipalign -c -P 16 4` OK, `apksigner` v2 verified, AAB `jarsigner` jar verified (peringatan self-signed/PKIX/timestamp/POSIX wajar). Bundletool tidak diulang pada rilis ini (tidak ada log sesi ini).

Distribusi `app/build/outputs/distribution/1.1.1/` memuat APK per ABI, AAB, sertifikat publik, `release.json`, `SHA256SUMS` (semua entri `sha256sum -c` OK). ARM64 63.025.099 byte, SHA256 `437d42759c2b953587f885d70b0a5ffd44fee21dc47dd0774fa088ee847405c0` (+16.360 byte dibanding 1.1.0). Update `install -r` pada Realme Android 10 sukses (`lastUpdateTime 13 Okt 09:58:46`) dan hash `base.apk` dari HP identik artefak. Tidak uninstall/clear-data, tidak upload toko, tidak membuat ulang keystore. Retensi media `not established` (pengelola membersihkan media selama build); [laporan, hasil UI dashboard dan batas cakupan](DASHBOARD_LYRICS_2026-10-13.md), [uji perangkat](DEVICE_TEST.md).


## Release 1.1.2 — penamaan dan Auto Folder (13 Oktober 2026)

VersionCode16, Room9, kunci produksi sama. Build final11m11s;149 Kotlin/76 Python lulus, lint debug/release0masalah. Audit tiga APK:signature/certificate,16KBzipalign/native,reflectiveZIP danmanifest; AAB signing/native/bundletool validate lulus. Distribusi `app/build/outputs/distribution/1.1.2/` mempertahankan versi sebelumnya. ARM64 63.025.815byte,SHA256 `d7210fba3fe44419144629a7c41335296733be6da79fe31b73b2ad0dfd7a093a`.

Update-r berhasil di RealmeAndroid10, hash base.apk cocok;57file publik sebelum/sesudah update identik. Tidak membuat ulang keystore atau memublikasikan ke toko. Perbaikan mengambil kredit judul/penyanyi eksplisit dan folder perartis/remixer untukjobplaylistbaru. [Bukti, cakupan danbatas](SOURCE_REVIEW_2026-10-13.md).


## Release1.1.3 — folder koleksi dan cleanup

Kunci produksi lama dipertahankan; signed APK×3/AAB berhasil,160 Kotlin/76 Python/lint0, audit final dan bundletool lulus. ARM64 diupdate ke HP RMX1821Android10 tanpa uninstall/clear data; version1.1.3/code17 dan hash base.apk terverifikasi. Pengujian pemakaian diserahkan ke pengelola. [Laporan](COLLECTION_CLEANUP_2026-10-13.md).

| Artefak | Byte | SHA256 |
| --- | ---: | --- |
| `TubeFlow-1.1.3-arm64-v8a-release.apk` | 63031979 | `c767ac27c705a2adeba408c05ab7c51b7603885bb4085b20cbd80e2b86d35e7a` |
| `TubeFlow-1.1.3-armeabi-v7a-release.apk` | 55368673 | `4454b23460fa1726b81b5b097463470dc54769ca3143b5f75e81ae5a44a06cc2` |
| `TubeFlow-1.1.3-x86_64-release.apk` | 66164414 | `acccfec5245e48e5c3124940c1a5322d5fba86513af1de7cb488df6eb421644d` |
| `TubeFlow-1.1.3-release.aab` | 210244266 | `68a0bd23206cec1c6bee6b4893e6f7c5d60ef28b19cf6b52e1bf154af021579e` |

Distribusi lokal `app/build/outputs/distribution/1.1.3/`; source/tes/dokumentasi dipush ke main. Tidak ada publikasi toko atau backup baru keystore.


## Release 1.1.4 — kredit koleksi dan player

Kunci produksi lama dipertahankan. 175 Kotlin/78 Python/lint debug+release 0; signed APK×3/AAB, signature/native/alignment/refleksi ZIP/manifest/bundletool lulus. ARM64 final 63.135.471 byte (+103.492 byte dari 1.1.3), SHA256 `61e39a4849db7410982a92840d32b76d94bf94893b0086c0bbfc785953bf56ed`. Terpasang pada RMX1821 Android 10 tanpa uninstall/clear data; hash APK terpasang cocok. Distribusi lokal `app/build/outputs/distribution/1.1.4/` memuat manifest/checksum dan sertifikat publik. Tidak ada publikasi toko atau pengukuran benchmark performa. [Laporan](UPGRADE_1_1_4_2026-10-14.md).


## Release1.1.5 — pilihan lirik manual

Kunci produksi/Room9 dipertahankan, tanpa dependensi baru. 184 Kotlin/78 Python/lint0, signed APK×3/AAB, audit signature/cert/native/alignment16KB/refleksiZIP/manifest dan bundletool validate lulus. ARM64 63.172.747byte, SHA256 `c4aea0927af8bac992036805986fc1e59982e324760ed37a99d800b544154828`; terpasang dan hash base.apk cocok. Distribusi lokal `app/build/outputs/distribution/1.1.5/`; tidak ada publikasi toko. [Laporan](MANUAL_LYRICS_1_1_5_2026-10-14.md).

## Kandidat 1.1.9 / code23 — 15 Oktober 2026

APK akhir memakai kunci produksi yang sama, non-debuggable; sertifikat SHA256 `ebe3b40eb42c9b60fd044a011fc6a9e0cbfcde97c53accb2ea49934f4934dbd0`. ARM64 **70.546.231 byte**, SHA256 `042d64db675617826441da0c0e76e7aab39ecad849d73732aa8279a0d9696af0`, dipasang sebagai update di Realme Android10; hash base.apk cocok. Artefak aktif berada di `app/build/outputs/apk/release/` dan `app/build/outputs/bundle/release/`; tidak menambahkan salinan distribution lama.

SHA256 APK armeabi-v7a `f72fccb063ffe0563c6205416e155fdd32d2d4ce944a5578317f700de45a50b9`, x86_64 `2e3fcdd8a69393b0ad4c3ad6f6a6fa4cd6b5700a508fc10c23b39d8ca3f43d88`; AAB `e8971585bce57e667e476141a3621a344a608903f8c691b847fd6e8df68472f6` (255.574.674 byte). APK×3: signature/zipalign16KB/native/refleksi ZIP lulus. AAB: jarsigner/bundletool/native lulus. 228 unit Kotlin dan79 Python lulus; lint0error/2warning yang diketahui. Ini bukan klaim pengujian runtime semua ABI/Android16KB atau publikasi toko. [Cakupan perangkat](PLAYER_POLISH_2026-10-15.md).


## Kandidat 1.1.10 / code24 — 15 Oktober 2026

Kunci produksi sama (SHA256 `ebe3b40eb42c9b60fd044a011fc6a9e0cbfcde97c53accb2ea49934f4934dbd0`),
Room9/dependensi tetap. Signed APK×3/AAB, audit signature/alignment/native/refleksi ZIP,
AAB jarsigner/bundletool lulus;233 Kotlin/83 Python lulus, lint0error/2warning.
ARM64 **70.564.491byte**, SHA256 `3ff8b5a250a82b3e2aef65a1cf06f9ca2d7200e262b328418ea628e3b3f70d3a`,
terpasang update-r di RealmeAndroid10 dan base.apk cocok. Ketiga ABI, AAB dan batas
verifikasi dicatat pada [laporan1.1.10](PLAYER_AUTOPLAY_1_1_10.md).
Artefak di outputs/apk/release dan outputs/bundle/release; tidak menambah salinan
distribution, mengganti keystore, atau menerbitkan toko. Uji kabel sesudah Recent
berhasil; TWS/Android12+/runtime16KB tetap belum dibuktikan.


## Kandidat1.1.11/code25 — 15 Oktober 2026

Keystore produksi/Room9/dependensi tetap; signed APK×3/AAB, signature/cert,
alignment16KB/native/refleksiZIP dan AAB jarsigner/bundletool lulus. 240Kotlin/
83Python lulus, lint0error/2warning diketahui. ARM64 **70.566.627byte**, SHA256
`93c6318b682a3a4837b171f5ff264e55fc56e60859e0874f43635bee9004a662`, terpasang update-r
pada RealmeAndroid10, hashbase.apk cocok;70file utuh saat update. Sesudah itu,
pengelola menguji Hapus semua tanpa SAF dan root tersisa kosong. Artefak aktif
outputs/apk/release dan outputs/bundle/release, tanpa salinan distribusi tambahan
atau publikasi toko. [Checksum semuaABI dan batas tes](STORAGE_CLEANUP_1_1_11.md).


## Kandidat1.1.12/code26 — 16 Oktober 2026

Keystore produksi/Room9/dependensi tetap. 260Kotlin/84Python lulus; debug/signed
APK×3/AAB/R8, lint0error/2warning diketahui, audit certificate/signature/native/
alignment16KB/refleksiZIP dan AAB jarsigner/bundletool lulus. ARM6470.573.823byte,
SHA256 `497716d5938b8ef8d8147fe2078a76bab21cf890e95af707828ba9abde617a44`,
terpasang update-r RMX1821Android10; hashbase.apk cocok. Pengujian UI baru masih
tertahan halaman hasil Norton yang memerlukan tindakan pengelola. Tidak uninstall/
clear-data, mematikan keamanan, menambah salinan distribution atau publikasi toko.
[Checksum semuaABI, hasil dan keterbatasan](METADATA_LYRICS_LIBRARY_1_1_12.md).


## Kandidat1.1.13/code27 — 16 Oktober 2026

Keystore produksi, Room9 dan dependensi dipertahankan.271Kotlin/84Python lulus;
APK×3/AAB signed/R8, lint0error/2warning lama, audit native/certificate/signature/
alignment16KB/refleksiZIP serta engine/bootstrap lulus. AAB jarsigner/native
lulus; bundletool tidak dijalankan ulang. ARM64 70.578.919byte, SHA256
`9ed1d31e2abde344e30f1613da238bc7fc008a50424762e22cfbdbc68c9a6326`.
Update-r RealmeAndroid10 sukses dan base.apk cocok. Satu audio asli tetap utuh;
pencarian manual17hasil, perluasan24hasil berhasil. Tidak uninstall/reset,
mengubah keamanan, menerbitkan ke toko, atau menggandakan distribusi.
[Checksum semuaABI dan batas pengujian](LYRICS_RATE_LIMIT_1_1_13.md).


## Kandidat 1.1.14/code28 — 16 Oktober 2026

Keystore produksi, Room9, manifest dan dependensi tetap. Build release 8m55s,
debug 58s; 279 Kotlin/84 Python lulus, lint 0 error/2 warning lama. Signed APK×3
lulus signature/certificate/native/alignment16KB/refleksiZIP/engine/bootstrap;
AAB jarsigner/native lulus, bundletool tidak dijalankan ulang. ARM64 70.580.383 byte,
SHA256 `0aef3db354514119812ea4e0119299f708d1a71f7aee3b06d0a113d24751e99d`.
Update-r dan hash base.apk Realme Android 10 cocok; enam skenario fokus terkontrol
lulus, 58 file media identik. Tidak menggandakan distribusi atau publikasi toko.
Konfirmasi Facebook asli serta cakupan perangkat lain tetap terpisah dari build.
[Checksum semua ABI/AAB dan batas pengujian](AUDIO_FOCUS_1_1_14.md).
