# Aksi kartu langsung dan EQ bass penuh 1.1.26

> Catatan: 1.1.27 mempersempit aksi yang tampil langsung menjadi enam aksi
> harian; sisanya kembali ke menu titik tiga. Lihat
> [CARD_ACTIONS_1_1_27.md](CARD_ACTIONS_1_1_27.md).

## Masalah dan keputusan

Baseline 1.1.25/code39. Dua permintaan pengelola:

1. Aksi di Koleksi dan Riwayat diseragamkan; **Unduh lagi** dan **Cari lirik**
   harus ada di keduanya, dan aksinya **ditampilkan langsung di kartu**, bukan
   disembunyikan di menu titik tiga.
2. EQ otomatis dibuat **full bass bulat** karena kurva seimbang +3 dB terasa
   kurang penuh.

Keputusan desain:

- Satu `MediaCardPlan.card` menyusun urutan aksi untuk kedua daftar. Semua aksi
  dirender sebagai ikon 48dp dalam `FlowRow` yang membungkus; menu titik tiga
  dihapus. Urutan: Putar/Buka → Favorit (audio) → Bagikan → antrean/playlist →
  lirik → lampiran → detail/berkas → hapus.
- Koleksi mendapat **Unduh lagi** hanya bila item masih tertaut ke job selesai
  (punya URL sumber). Konfirmasi wajib karena file lama diganti (BUG-026).
- Riwayat mendapat **Tambah ke antrean**, **Putar berikutnya**, dan **Atur waktu
  lirik** untuk LRC tersimpan. Aksi yang tidak dimiliki Riwayat (favorit,
  playlist, ubah nama, detail) tidak dipalsukan.
- Aksi hapus tetap berkonfirmasi walau kini tampil sebagai ikon.

## Kurva EQ

| Frekuensi | Level |
|---|---|
| 30–60 Hz | +6,0 dB |
| 120 Hz | +4,5 dB |
| 250 Hz | +2,0 dB |
| 500 Hz | +0,5 dB |
| 1–2 kHz | 0,0 dB |
| 4 kHz | +0,5 dB |
| 8 kHz | +1,5 dB |
| 16 kHz | +2,5 dB |

Interpolasi logaritmik dan clamp rentang band perangkat dipertahankan. Preset
perangkat, level manual, dan pilihan nonaktif pengguna tidak direset. Tidak ada
preamp; kurva hanya mengubah band equalizer perangkat.

## Perbaikan sampingan

`sub|` pada audio adalah berkas LRC, bukan subtitle video. Sebelumnya kartu
Riwayat audio yang punya lirik menampilkan aksi **Buka subtitle (CC)**. Kini
`hasSubtitleAttachment` hanya mengizinkan subtitle untuk video; regresi
`audioLyricSidecarIsNeverOfferedAsSubtitle` menguncinya.

## File utama

`ui/MediaCardActions.kt`, `ui/MediaLibraryScreen.kt`, `ui/components.kt`,
`ui/HistoryScreen.kt`, `ui/TubeFlowApp.kt`, `core/PlayerViewModel.kt`,
`core/AutoEq.kt`, `res/values/strings.xml`, `AutoEqTest.kt`,
`MediaCardPlanTest.kt`, Gradle, DESIGN.md dan referensi skill.

Room, engine download, izin, dan dependensi tidak berubah. Versi
1.1.26/code40.

## Verifikasi host

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon --max-workers=2
# BUILD SUCCESSFUL 6m17s
# 362 tes Kotlin, 0 gagal/error/skip
# lint: 0 error, 2 warning lama (SelectedPhotoAccess, GradleDependency/ONNX pin)

./gradlew assembleRelease --no-daemon --max-workers=2
# BUILD SUCCESSFUL 9m33s
```

APK ARM64 final: 70.893.564 byte, SHA256
`e4fbef60be742935ec90e823d870e8481a351ca71c605a51fb81f5f8f12c1374`.
Audit: 257 ELF, 0 gagal alignment 16 KB; 13 kelas refleksi ZIP, 0 gagal.

## Verifikasi perangkat

Realme RMX1821, Android 10/API29, ARM64, halaman 4 KB. Pemasangan
`adb install -r` APK final 1.1.26/code40 tanpa uninstall; data Koleksi,
antrean, dan preferensi tetap.

| Skenario | Hasil |
|---|---|
| Kartu Koleksi audio | 13 ikon tampil langsung, termasuk Unduh lagi dan Cari lirik; tidak ada titik tiga. |
| Unduh lagi dari Koleksi | Dialog "Unduh lagi?" menjelaskan file lama diganti; Batal menutup tanpa menghapus file. |
| Kartu Riwayat audio | Putar, Bagikan, antrean, putar berikutnya, Cari lirik, Atur waktu lirik, Buka dengan aplikasi lain, Unduh lagi, Hapus tampil langsung. Tidak ada lagi ikon CC yang keliru untuk LRC audio. |
| Atur waktu lirik dari Riwayat | Dialog memuat pratinjau LRC lagu aktual; Batal tidak mengubah berkas. |
| EQ | Preset Otomatis (bass bulat): 60 Hz +6,0 dB, 230 Hz +2,0 dB, 910 Hz/3,6 kHz 0 dB, 14 kHz +2,0 dB. |
| Crash | Tidak ada FATAL EXCEPTION pada logcat sesi. |

Tidak diuji: konfirmasi Unduh lagi yang benar-benar menghapus dan mengunduh
ulang (tidak ingin menghapus media pengguna saat sesi), penilaian auditori
EQ/clipping, tema terang, font besar, TalkBack, dan kartu video (riwayat saat
pengujian hanya berisi satu audio). Aksi **Atur waktu lirik** diuji sampai
dialog dan pratinjau; penyimpanan koreksi tidak dilakukan. Bukti screenshot
privat disimpan di `build/reports/cards-eq-1126/` dan tidak dipublikasikan
karena memuat judul media serta data perangkat pengguna.
