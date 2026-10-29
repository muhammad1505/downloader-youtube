# Pemutar video 1.1.50 / code 65

## Perubahan

- Ikon dekoder kembali tersedia di deretan kontrol cepat dan membuka pilihan
  Otomatis/Perangkat/Software. Screenshot tetap berada di menu Opsi pemutar,
  jadi kontrol cepat tidak bertambah jumlah atau tinggi.
- Kontrol cepat memakai ikon saja dengan deskripsi aksesibilitas yang memuat
  pilihan dekoder aktif.
- Mode Pas menampilkan seluruh frame; Penuh mengisi layar dengan crop; Zoom
  memperbesar dari tengah. Gambar tidak diregangkan.
- Pemutar mempertahankan sesi saat video berganti dengan konfigurasi renderer
  dan penguat yang sama. Antrean dimulai pada item yang sedang diputar.

## Verifikasi

- `bash scripts/build_release.sh --no-daemon --max-workers=2`: berhasil; 583 tes
  Kotlin lulus, lint debug/release 0 error dan 2 warning (`SelectedPhotoAccess`,
  `AndroidGradlePluginVersion`), APK dan AAB release dibuat.
- 98 tes Python lulus. Satu pesan unduhan fragmen muncul selama tes tetapi retry
  berhasil; seluruh suite berakhir `OK`.
- ARM64 APK bertanda tangan dan lolos zipalign 16 KB; audit menemukan 262 ELF
  tanpa kegagalan alignment dan 13 kelas ZIP tanpa kegagalan.
- AAB lolos verifikasi arsip penandatanganan.
- SHA-256 ARM64 APK:
  `05f178b562f96e5f508f18a098123c96de9994c9c4e82cc1eb6fbfe1007a7e1e`.
- Pemasangan update pada Realme RMX1821 / Android 10 ditolak karena sertifikat
  APK lokal berbeda dari sertifikat TubeFlow yang terpasang. Sertifikat APK
  kandidat berakhir `ebe3b40e…`; sertifikat instalasi berakhir `94e49e79…`.
  Atas persetujuan pengguna, instalasi lama dicopot lalu 1.1.50 dipasang bersih;
  data lokal aplikasi ter-reset. Berkas media bersama tidak dihapus.
- Pada layar 720 × 1520, TubeFlow meminta akses ke `1DMP/Videos`, mengindeks
  7 video MKV, lalu memutar `Kuramanime-BLCH_S2P4-10_END-480p-BGlobal.mkv`
  (23:10). Baris 7 ikon kontrol muat dalam satu baris dan label aksesibilitas
  dekoder terbaca. Pilihan Perangkat membuka ulang video dan pemutaran berlanjut
  sekitar 1:25; pilihan dikembalikan ke Otomatis.
- Tujuan simpan dipulihkan ke `Downloads/TubeFlow` setelah pengujian. Video dan
  audio yang sudah ada tidak dihapus atau dipindahkan. Tidak ditemukan crash
  TubeFlow selama smoke test.

## Batas verifikasi

Uji perangkat hanya mencakup satu MKV, satu perangkat Android 10, dan kontrol
dekoder Auto/Perangkat. Rotasi terkontrol, mode Software, TalkBack, format/codec
lain, dan beragam ukuran layar masih perlu pengujian. Pemasangan bersih
menghapus database dan setelan lokal; media bersama tetap ada.

## Tindak lanjut kontrol dan pintasan 1.1.51–1.1.52

Ketukan dekoder langsung berputar Auto → Perangkat → Software; pilihan rinci
tetap di Opsi pemutar. Kontrol Kunci, Sebelumnya, Putar/Jeda, Berikutnya, PiP,
dan Orientasi dikelompokkan di tengah. Pintasan launcher memakai ikon yang
lebih kontras; Musik/Video membuka koleksi, sedangkan Riwayat/Antrean menggulir
ke bagian yang diminta. Uji perangkat mendapati empty state Riwayat/Antrean
sempat hilang setelah posisi pintasan ditangani; 1.1.52 mempertahankan state
kosong yang sesuai. Build, tes, instalasi, smoke test, dan batas verifikasi
1.1.52 dicatat di `PROGRESS.md`.
