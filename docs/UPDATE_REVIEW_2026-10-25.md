# Pemeriksaan update TubeFlow — 25 Oktober 2026

## Snapshot dan cakupan

Git main/origin/main berada pada `41ce217` (1.1.40). Working tree sudah
memuat1.1.41/code55:13berkas tracked diubah dan `ui/Scrubber.kt` baru belum
tracked. File skill dan screenshot lama yang sebelumnya untracked dipertahankan.
Database masih Room10. Pemeriksaan berfokus pada delta lokal1.1.41; fitur dari
commit terdahulu dipetakan, bukan seluruhnya diuji ulang.

Sejak checkpoint1.1.25, kode mencakup Antrean/Riwayat terpadu di Download,
Pengaturan berkategori, perbaikan landscape audio, klip status WhatsApp,
Spotify album/playlist, video player Media3 beserta antrean/PiP/subtitle,
dan dekoder software dengan fallback. Delta terbaru menambahkan Scrubber audio/
video bersama, margin transport video dan pasangan tier/tinggi resolusi.

## Temuan yang perlu diperbaiki

### R1 — P2: preset tier dapat tertukar dengan tinggi nyata

**Lokasi:** `core/Models.kt`, `Selection.heightForTier`, baris335–340;
pemanggil `ui/DownloadScreen.kt`, `applyPreset`.

Parameter `requested` dipakai untuk dua arti. Pemeriksaan tinggi yang sama
berjalan sebelum pencocokan tier. Pada video vertikal2:3 dengan format720×1080
(tier720) dan1080×1620 (tier1080), menekan preset1080p menghasilkan tinggi1080,
padahal format1080p-nya setinggi1620. Selector unduhan kemudian mengecualikan
format yang diminta, sementara preset dapat tetap menyorot1080p.

**Bukti host baru:** parser menghasilkan
`[VideoResolution(1620,1080), VideoResolution(1080,720)]`;
`heightForTier(1080, choices)` mengembalikan1080, seharusnya1620 untuk preset.
Probe berjalan terhadap class aplikasi yang sudah dikompilasi.

**Arah perbaikan:** bedakan konversi tier preset dari validasi tinggi pilihan
menu. State pilihan harus mempunyai arti yang jelas; jangan mendeteksi arti
angka lewat kecocokan dengan daftar. Tambahkan regresi format vertikal2:3.

### R2 — P2: resolusi tanpa label sumber dibulatkan naik

**Lokasi:** `core/SourceMetadata.kt`, `tierOf`, baris45–54; fallback root juga
memakai aturan serupa pada baris75–77.

`firstOrNull { it >= height/width }` membuat video960×540 tanpa `format_note`
dilaporkan720p, walaupun sumber hanya menyediakan540piksel pada sisi pendek.
Format nonstandar lain dapat mengalami kenaikan label yang sama. Filter tetap
memakai tinggi asli; masalahnya klaim resolusi di UI dan pilihan preset.

**Bukti host baru:** parsing fixture960×540 menghasilkan
`VideoResolution(height=540, tier=720)`.

**Arah perbaikan:** prioritaskan label resmi; ketika tidak tersedia, tampilkan
ukuran asli atau label yang mempunyai bukti. Jangan menaikkan angka ke tangga
berikutnya hanya untuk menyeragamkan tampilan. Uji540p dan dimensi nonstandar,
selain format_note YouTube dan video vertikal.

### R3 — P3: preset yang berhasil dipilih kehilangan sorotan

**Lokasi:** `ui/DownloadScreen.kt`, `applyPreset`, baris207–212;
pemakaian `activePreset` pada baris232 dan517.

Untuk video1920×818 bertier1080p, konversi1080→818 berhasil tetapi
`target != p.quality` langsung mengosongkan `preset`. Chip pilihan tidak lagi
aktif meskipun kualitas yang diminta tersedia. Ini juga perlu direkonsiliasi
dengan catatan DEVICE_TEST yang menyebut chip1080p MP4 ditandai.

**Bukti:** alur state pada kode; klik preset belum direproduksi ulang di HP
pada pemeriksaan ini.

**Arah perbaikan:** pertahankan preset jika tier yang dipilih sesuai. Bandingkan
tier, bukan tinggi piksel aktual. Tambahkan uji state preset untuk rasio lebar.

## Risiko tambahan yang memerlukan uji runtime

`ui/Scrubber.kt:77–105` menghitung `span` sekali sebelum loop detektor;
`pointerInput` hanya memakai key enabled/duration. Lebar widget dapat berubah
sedangkan keduanya tetap, sehingga pemetaan koordinat berpotensi memakai lebar
lama. MainActivity menangani rotasi lewat configChanges, dan Scrubber video
berada pada callsite yang sama. Ini risiko berdasarkan kode, bukan hasil uji
rotasi baru. Reset dari konfigurasi/platform dapat memengaruhi reproduksinya.

Uji: mulai menggeser saat portrait, putar layar dengan durasi/enabled tetap,
lalu ketuk/geser ke50%. Periksa posisi terhadap durasi, bukan hanya gerakan
visual titik. Ulangi saat ukuran jendela berubah. Baca ukuran saat event
berlangsung atau perbarui geometri handler ketika ukurannya berubah.
[Kontrak pointerInput Android](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures)
menjelaskan eksekusi ulang berdasarkan perubahan key.

## Verifikasi dan batas

- Laporan lokal yang tersedia berisi473tes Kotlin,0gagal/error/skip,
  timestamp25Oktober19:36; lint debug/release0error/3warning:
  SelectedPhotoAccess, AndroidGradlePluginVersion, GradleDependency.
  Suite Gradle dan Python tidak dijalankan ulang pada pemeriksaan ini.
- Dua probe logika di atas dijalankan baru. Source probe dan output berada di
  `build/reports/update-review-2026-10-25/AuditProbe.java` dan `probe.log`.
- APK ARM6476.276.302byte mempunyai SHA256
  `73da1d6b0649239144b349b72da9bde3e121f6928adf9d8177e3ebc43ef58665`.
  Versi terpasang1.1.41/code55 dan hash base.apk yang ditarik dari HP cocok
  dengan APK lokal serta dokumentasi. Tidak memasang pembaruan pada sesi ini.
- Audit ulang APK:262ELF,0kegagalan alignment16KB;13kelas refleksi ZIP,
 0kegagalan. `git diff --check` lulus.
- Pengamatan HP dalam DEVICE_TEST berasal dari sesi sebelumnya. Pada sesi ini
  tidak menjalankan playback, rotasi, unduhan, penghapusan data atau perubahan
  pengaturan pengguna. Runtime16KB, TalkBack dan matriks UI belum diuji ulang.

Scrubber.kt harus ikut disimpan dalam commit perubahan player; commit tracked
files saja akan meninggalkan referensi Scrubber tanpa definisinya pada checkout
lain. Temuan ini laporan review, belum berstatus diperbaiki.
