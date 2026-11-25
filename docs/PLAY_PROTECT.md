# Play Protect dan APK sideload TubeFlow

Dokumen ini mencatat investigasi peringatan Play Protect yang dilaporkan pengguna
pada 25 November 2026 setelah memasang APK **1.1.83/code98**. Teks peringatan
persis belum diterima, jadi statusnya belum boleh disebut "false positive" atau
"malware" tanpa bukti.

## Fakta APK 1.1.83

- Paket `id.local.tubeflow`, versionCode 98, versionName 1.1.83.
- Target SDK 37, min SDK 29, hanya ABI arm64-v8a yang dipublikasikan.
- Ditandatangani kunci produksi (bukan sertifikat debug); signature v2 dan
  zipalign 16 KB lulus di CI.
- Tidak mendeklarasikan `READ_SMS`, `RECEIVE_SMS`, layanan aksesibilitas,
  notification listener, `SYSTEM_ALERT_WINDOW`, atau `REQUEST_INSTALL_PACKAGES`.
- SHA-256 APK 1.1.83:
  `9a86ced6a78fbd9dce41a159bc7198bc6ba3d38498cddae5a97d4969c6a2a868`.

Izin yang paling menonjol adalah `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (masuk
sejak 1.1.82/code97, bukan 1.1.83) dan `ACCESS_NOTIFICATION_POLICY` (dipakai
fitur Jangan Ganggu opsional). Google membatasi izin pengecualian baterai
langsung; pola itu sering masuk kategori riskware pada scanner. Karena itu
1.1.84 menghapus izin tersebut dan tidak lagi membuka dialog pengecualian
langsung. Bila pengguna melompat dari 1.1.80 ke 1.1.83, kedua izin baru itu
memang pertama kali terlihat oleh Play Protect pada pemindaian 1.1.83.

## Perubahan 1.1.84

- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` dihapus dari manifest.
- Panduan baterai membuka Info aplikasi; pengguna mengatur Baterai/Aktivitas
  latar belakang dan Autostart sendiri. Tidak ada permintaan izin langsung.
- Tidak ada tombol optimasi baterai ganda di menu Pemutaran.
- Tidak ada perubahan pada izin SMS, aksesibilitas, atau notification listener
  karena memang tidak pernah ada.

## Cara memverifikasi APK yang diunduh

```bash
sha256sum TubeFlow-1.1.84-arm64-v8a-release.apk
unzip -p TubeFlow-1.1.84-arm64-v8a-release.apk AndroidManifest.xml >/dev/null
```

Gunakan `apksigner verify --verbose --print-certs` dari build-tools Android
untuk memastikan sertifikat bukan `CN=Android Debug` dan fingerprint-nya cocok
dengan sertifikat produksi yang tercatat di
[PRODUCTION_SIGNING](PRODUCTION_SIGNING.md). Jangan memasang APK dari tautan
selain GitHub Release resmi proyek.

## Bila peringatan masih muncul

Kirim teks atau screenshot persis dari dialog, versi APK, dan waktu pemindaian.
Teks menentukan tindakan:

| Teks/perilaku | Arti | Tindakan |
| --- | --- | --- |
| "Play Protect hasn't seen an app from this developer before" | Aplikasi jarang/uncommon, bukan klasifikasi malware | Pilih opsi pemindaian/kirim untuk evaluasi; tidak ada izin yang perlu diubah |
| "This app is harmful, it can put your device and data at risk" | Klasifikasi riskware/PHA | Cocokkan dengan perubahan 1.1.84; bila tetap muncul, appeal dengan APK dan hash |
| "App blocked to protect your device" | Pemblokiran izin sensitif saat sideload | Periksa manifest gabungan APK; TubeFlow tidak memakai SMS/aksesibilitas/notification listener |
| "This app was built for an older version of Android" | targetSdk terlalu rendah | Tidak berlaku: target SDK 37 |

Jangan menonaktifkan Play Protect untuk memasang TubeFlow. Bila yakin APK bersih,
gunakan kanal appeal resmi yang ditautkan pada dialog atau panduan Google:
<https://developers.google.com/android/play-protect/warning-dev-guidance>.

## Batas yang belum terbukti

Peringatan Play Protect dapat dipicu oleh reputasi aplikasi, sertifikat yang
belum dikenal, atau analisis dinamis, bukan hanya izin. TubeFlow juga memuat
engine yt-dlp dan memperbaruinya dari rilis resmi GitHub dengan verifikasi
SHA-256; pembaruan itu bukan pemasangan APK, tetapi tetap merupakan pemuatan kode
saat runtime yang dapat menaikkan skor heuristik. Perubahan 1.1.84 menghilangkan
satu pemicu yang paling mungkin; belum ada bukti bahwa peringatan hilang sampai
APK 1.1.84 dipindai ulang di perangkat yang sama.
