# Enam aksi di kartu, menu ⋮, dan caption berbagi 1.1.27

## Masalah dan keputusan

Baseline 1.1.26/code40. 1.1.26 memindahkan **semua** aksi kartu ke luar menu,
sehingga kartu audio Koleksi menampilkan 13 ikon dalam dua baris. Pengelola
meminta pembagian yang lebih tegas:

- Di kartu: **Putar, Favorit, Bagikan, Ubah nama, Cari lirik, Hapus**.
- Di menu titik tiga: seluruh aksi lain (antrean, playlist, atur waktu lirik,
  lampiran, detail/berkas, unduh lagi).

Keputusan desain:

- `MediaCardPlan.card` menandai slot tiap aksi (`Inline`/`Menu`); `MAX_INLINE`
  menjadi 6. Urutan ikon langsung mengikuti permintaan tersebut, dan aksi yang
  tidak dimiliki layar tidak dipalsukan — Riwayat tetap tanpa Favorit/Ubah nama.
- `MediaActionBar` merender ikon langsung dalam `FlowRow` 48dp, lalu satu tombol
  ⋮. Menu mengelompokkan aksi: *Antrean & playlist*, *Lirik*, *Lampiran*,
  *Berkas*. Grup hanya muncul bila ada isinya.
- **Hapus file** kini ikon langsung berwarna galat dan tetap berkonfirmasi.
- **Unduh lagi** kembali ke menu *Berkas* bersama Detail dan Buka dengan
  aplikasi lain; konfirmasi penggantian file lama (BUG-026) tidak berubah.
- Aksi yang dinonaktifkan (mis. file hilang) ikut nonaktif di menu, bukan
  disembunyikan, supaya pengguna tahu aksi itu ada.

## Caption saat berbagi

`launchMedia` mengisi `EXTRA_TEXT` pada `ACTION_SEND` dengan nama file tanpa
ekstensi, sehingga aplikasi tujuan yang mendukung caption membuka pesan dengan
teks terisi — misalnya "Ipank - Mutiara" untuk `Ipank - Mutiara.mp3`. Nama
diambil dari `OpenableColumns.DISPLAY_NAME` lewat helper `displayName()` yang
juga dipakai `concreteMime`; bila provider tidak memberi nama, judul media dipakai
sebagai cadangan (`shareCaption`). Aksi **Buka dengan aplikasi lain** tidak
diberi caption.

**Batas WhatsApp.** WhatsApp Android tidak menyediakan kolom caption untuk berkas
audio, jadi `EXTRA_TEXT` tidak tampil di sana walau sudah dikirim. Karena itu,
setiap kali audio dibagikan aplikasi juga menyalin caption ke clipboard dan
menampilkan toast "Caption disalin: …" supaya teksnya bisa ditempel manual.
Untuk gambar/video atau aplikasi yang mendukung caption (Telegram, email), teks
tetap terisi otomatis. Jangan mengklaim WhatsApp akan menampilkan caption audio.

## Perbaikan share dari Koleksi

Toast "Caption disalin" sempat dibuat langsung di `Dispatchers.IO` (pemanggil
Koleksi), padahal `Toast` membutuhkan Looper main thread. Exception-nya tertangkap
`catch` di `launchMedia`, sehingga share dari Koleksi gagal dengan pesan
"File tidak dapat dibagikan…" walau berkasnya sehat; Riwayat berhasil karena
dipanggil dari main thread. Toast sekarang diposting lewat
`Handler(Looper.getMainLooper())`, dan clipboard tetap diisi. Jalur share kedua
layar kembali sama.

## Retensi riwayat otomatis

Pengaturan baru **Riwayat unduhan → Hapus otomatis**: Nonaktif (default), 7, 30,
atau 90 hari. Saat aplikasi dibuka atau pilihan diubah, `DownloadViewModel.pruneHistory`
menghapus baris `downloads` berstatus `COMPLETED` yang lebih lama dari batas,
memakai `completedAt` dan jatuh ke `createdAt` untuk baris lama. File media,
sampul, LRC, dan playlist tidak disentuh. Notifikasi hasil unduhan yang dihapus
ikut dibatalkan. 0 berarti nonaktif; nilai di atas 365 hari dijepit ke 365.
Pilihannya ikut diekspor/dipulihkan oleh Cadangan privat (`history_auto_days`),
dan cadangan lama tanpa kunci itu tetap aman (dianggap nonaktif).

## File utama

`ui/MediaCardActions.kt`, `ui/components.kt`, `ui/SettingsScreen.kt`,
`ui/TubeFlowApp.kt`, `core/SettingsStore.kt`, `core/Models.kt`,
`core/DownloadViewModel.kt`, `MediaCardPlanTest.kt`, `MediaLaunchTest.kt`,
`HistoryRetentionTest.kt`, DESIGN.md, PROGRESS.md, README.md, dan dokumen ini.
Tidak ada perubahan skema Room (hanya query baru), engine, izin, dependensi,
maupun kurva EQ 1.1.26.

## Verifikasi host

```sh
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ./gradlew testDebugUnitTest lintDebug assembleRelease --no-daemon --max-workers=2
# BUILD SUCCESSFUL 13m11s
# 370 tes Kotlin, 0 gagal/error/skip
# lint: 0 error, 2 warning lama (SelectedPhotoAccess, GradleDependency/ONNX pin)
```

APK ARM64 bertanda tangan: 70.898.404 byte, SHA256
`f70b34ef4900d650de4110b6193929f0c8952bbed2362fc3cd6143874ce17c70`. Audit:
257 ELF, 0 gagal alignment 16 KB; 13 kelas refleksi ZIP, 0 gagal.

## Verifikasi perangkat

Realme RMX1821, Android 10/API29, ARM64, halaman 4 KB. Pemasangan
`adb install -r` tanpa uninstall; data Koleksi, riwayat, antrean, dan preferensi
tetap.

| Skenario | Hasil |
|---|---|
| Kartu audio Koleksi | Enam ikon dalam satu baris: Putar, Favorit, Bagikan, Ubah nama, Cari lirik, Hapus (merah), lalu ⋮. |
| Menu ⋮ Koleksi | Grup *Antrean & playlist* (Tambah ke antrean, Putar berikutnya, Tambah ke playlist), *Lirik* (Atur waktu lirik), *Berkas* (Detail, Buka dengan aplikasi lain). |
| Atur waktu lirik dari menu | Dialog terbuka dengan pratinjau LRC lagu; Batal tidak mengubah berkas. |
| Kartu audio Riwayat | Empat ikon: Putar, Bagikan, Cari lirik, Hapus, lalu ⋮; tidak ada ikon CC untuk LRC. |
| Menu ⋮ Riwayat | Grup yang sama ditambah *Berkas*: Buka dengan aplikasi lain dan Unduh lagi. |
| Bagikan dari Koleksi (perbaikan toast) | Lembar bagikan terbuka dan toast "Caption disalin: MUTIARA - Ipank" muncul; tidak ada lagi pesan "File tidak dapat dibagikan". |
| Pengaturan → Riwayat unduhan | Panel **Hapus otomatis** tampil dengan pilihan Nonaktif/7/30/90 hari dan keterangan bahwa file media tetap tersimpan. |
| Crash | Tidak ada FATAL EXCEPTION TubeFlow; entri crash log hanya proses OPPO `EnCodeFileHandlerThread`. |

Tidak diuji: retensi riwayat dengan data yang benar-benar melewati batas waktu
(hanya pemilihan pengaturan yang diperiksa), konfirmasi Hapus yang
benar-benar menghapus, penilaian auditori EQ, kartu video, tema terang, font
besar, dan TalkBack. Pada item Koleksi yang diuji, **Unduh lagi** tidak muncul
karena item itu tidak tertaut ke job selesai yang dimuat — aturan kemampuan
lama, bukan efek pemindahan menu.

Bukti screenshot privat disimpan di `build/reports/card-actions-1127/` dan tidak
dipublikasikan karena memuat judul media serta data perangkat pengguna.
