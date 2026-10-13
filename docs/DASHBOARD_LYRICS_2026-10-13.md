# Dashboard dan keamanan cari ulang lirik — 13 Oktober 2026

## Lingkup dan kontrak

Permintaan pengelola: lanjutkan perbaikan Cari ulang lirik dan optimasi dashboard dengan grafik donat/batang/garis. Tidak menambah dependency/layanan/pemutar atau mengubah schema Room9; kandidat 1.1.1/code15 yang diajukan memakai kunci produksi yang sama — kini telah dirilis dan terpasang (hasil di bagian bawah). Tidak menyatakan seluruh backlog selesai.

- Donat: jumlah audio/video **AVAILABLE**, bukan seluruh riwayat atau byte storage; unavailable dikecualikan dan disebutkan.
- Batang: transisi COMPLETED per tanggal lokal pada periode7/30/90hari kalender. Retry dapat menghasilkan transisi baru; bukan jumlah file unik.
- Garis: akumulasi COMPLETED dalam periode terpilih. Database tidak menyimpan snapshot ukuran historis, sehingga tidak membuat tren byte palsu.
- Hari tercatat tanpa aktivitas diisi0; hari sebelum analyticsSince ditandai belum tercatat, tidak dianggap0. Rentang maksimum90titik, teks legenda/ringkasan dan semantics untuk pembaca layar. Periode disimpan lewat rememberSaveable; query periode dipicu sekali, tidak ganda oleh klik dan effect.
- Pengelompokan status job dan titik grafik dimemoisasi. Masuk dashboard memakai validasi katalog dengan batas100URI dan TTL1jam yang sudah ada, bukan force probe setiap kunjungan. Pindai manual tetap jalur segarkan eksplisit. Ketersediaan adalah hasil pemeriksaan terakhir, bukan janji realtime. Tidak mengklaim speedup/FPS/daya tanpa benchmark perangkat.

## Keamanan refetch

`MediaFileOperations.mutex` dipakai koreksi waktu, refetch, katalog/index/rename/delete dan penghapusan media pada riwayat/penggantian hasil/clear-folder. Lock tidak ditahan selama request jaringan; refetch menangkap nama audio + bytes LRC sebelum request, lalu memeriksa ulang state/generation/output/sidecars/nama dan isi sebelum mengganti. Perubahan ketika request berlangsung membatalkan penggantian. Snapshot dialog koreksi yang usang ditolak oleh hash yang sudah ada.

`JournaledTextReplacement` dipakai oleh `LyricEditor` pada jalur refetch lama: backup/read wajib sukses sebelum writer dibuka, jurnal AtomicFile privat berisi bytes before/next Base64 disimpan sebelum truncate, write/close dan read-back wajib cocok. Kegagalan mencoba rollback dan read-back; pemulihan gagal mempertahankan jurnal untuk pemeriksaan berikutnya. Recovery hanya menerima bytes target, sebelumnya atau prefix keduanya; perubahan eksternal yang tidak cocok tidak ditimpa. Target berhasil mereset baseline timing lama; no-op byte-identik mempertahankan baseline. Jurnal koreksi waktu yang belum pulih memblokir refetch hingga dialog waktu dibuka untuk recovery.

Penggantian LRC lama mewajibkan satu sidecar dengan nama penuh sesuai stem audio, bukan sekadar akhiran.lrc. File pendamping yang tidak dapat diperiksa membatalkan pencarian. Provider SAF opaque tanpa induk terverifikasi tidak diberi fallback root sembarang. File baru diverifikasi read-back/nama dan sidecar committed atomik; penghapusan stale yang gagal tetap tercatat. Audio tidak diubah oleh jalur refetch.

Batas: lock proses aplikasi bukan transaksi dengan aplikasi eksternal. Masih perlu fault injection Android nyata dan audit jalur worker/recovery yang belum generation-guarded; create→journal/proses mati tetap merupakan cakupan BUG-008. Tidak mengklaim menutup seluruh BUG-002/003/004/008/010/015/016 atau semua provider. Backup privat bounded32MiB, pembacaanLRC512KiB; backup penuh/provider gagal menyebabkan operasi dibatalkan.

## Regresi dan bukti

Tes `JournaledTextReplacementTest`: unreadable backup/journal full tidak membuka writer, partial-write/close failure rollback, rollback gagal mempertahankan backup dan recovery baru, external bytes ditolak, committed target, stale snapshot, no-op/baseline dan shared mutex/cancellation. Tes `DashboardChartsTest`: zero-fill/gap, malformed/future/out-of-period/negative rows,90point bound, kalender/DST dan pengukuran helper90hari.

76 tes Python lulus pada sesi implementasi (52,619 detik). Build debug pertama lulus dengan 144 Kotlin/0 gagal, tetapi beberapa guard final ditambahkan sesudahnya; angka itu bukan verifikasi source final. Hasil build final signed, pemeriksaan yang dijalankan ulang, dan uji UI perangkat dicatat pada bagian berikut.

## Hasil build final dan uji perangkat — 13 Oktober 2026

**Rilis 1.1.1 (versionCode 15, Room 9)** dibangun dengan `bash scripts/build_release.sh --no-daemon` memakai keystore produksi yang sama (`ebe3b40e…`), tanpa perubahan source sesudahnya (`find app/src scripts -newer <APK rilis>` kosong). Pemeriksaan berikut diverifikasi ulang pada sesi dokumentasi ini, bukan sekadar disalin dari sesi build:

- **145 tes Kotlin, 0 gagal/error** — dibaca dari `app/build/test-results/testDebugUnitTest/*.xml` (timestamp 09:41).
- **76 tes Python lulus** — `python3 -m unittest discover -s scripts -p "test_*.py"` dijalankan ulang sesi dokumentasi ini (23,173 detik, `OK`).
- **Lint debug dan release: "No issues found"** — `app/build/reports/lint-results-{debug,release}.txt` (09:45).
- **`sha256sum -c SHA256SUMS`** pada `distribution/1.1.1/` → seluruh entri `OK`.
- **APK×3:** audit native 255/255/257 ELF 0 gagal 16 KB, audit ZIP reflektif 13 kelas 0 gagal, `zipalign -c -P 16 4` OK (arm64), `apksigner verify` v2 **verified** dengan sertifikat SHA-256 `ebe3b40eb42c9b60fd044a011fc6a9e0cbfcde97c53accb2ea49934f4934dbd0` (identik rilis sebelumnya).
- **AAB:** audit native 1.024 ELF 0 gagal; `jarsigner -verify` → `jar verified` dengan peringatan self-signed/PKIX/timestamp/POSIX yang sama seperti rilis sebelumnya (wajar, tercatat di [PRODUCTION_SIGNING](PRODUCTION_SIGNING.md)). Klaim bundletool pada rilis lama tidak diulang untuk 1.1.1 karena tidak ada log sesi ini.

ARM64 `TubeFlow-1.1.1-arm64-v8a-release.apk`: **63.025.099 byte, SHA256 `437d42759c2b953587f885d70b0a5ffd44fee21dc47dd0774fa088ee847405c0`**. Artefak: `app/build/outputs/distribution/1.1.1/` (APK×3, AAB, cert, `release.json`, `SHA256SUMS`).

**Uji perangkat (Realme RMX1821, Android 10, arm64, tanpa emulator):** update `adb install -r` sukses (`lastUpdateTime 13 Okt 2026 09:58:46`, tanpa uninstall/clear data); `base.apk` ditarik dari HP ber-SHA256 identik ARM64 di atas. Pemeriksaan UI dashboard via UIAutomator + screenshot (09:59–10:03):

- **Donat** — [dashboard-1.1.1-donut.png](images/dashboard-1.1.1-donut.png): kondisi koleksi kosong; "0 media tersedia", legenda Audio 0 / Video 0, "Belum ada media tersedia", dan teks "Hanya media tersedia. 5 file tidak dapat diakses tidak masuk grafik." — angka tidak-terakses dilaporkan terpisah, tidak dipaksa masuk grafik, sesuai kontrak di atas.
- **Batang 7 hari** — [dashboard-1.1.1-bars.png](images/dashboard-1.1.1-bars.png): puncak 2 selesai/hari pada rentang 2026-10-07…2026-10-13; ringkasan "Selesai / gagal / dibatalkan 2 / 1 / 0, rasio 66%" dengan catatan "Pencatatan dimulai 13 Okt 2026 08:21 … termasuk retry; tidak merekonstruksi aktivitas sebelum upgrade"; hari tanpa pencatatan bertanda "Titik abu-abu: belum ada pencatatan", bukan 0.
- **Garis** — [dashboard-1.1.1-line.png](images/dashboard-1.1.1-line.png): "Tren akumulasi selesai, total periode 2" dengan legenda "Garis = akumulasi aktivitas selesai dalam 7 hari, bukan jumlah file atau riwayat ukuran penyimpanan"; kartu "Sumber download selesai — YouTube · 2".
- Periode **30/90 hari** tercatat diperiksa via UIAutomator menurut `release.json`; tangkapan layar yang disimpan ke `docs/images/` hanya periode 7 hari.

**Batas cakupan batch ini:**

- **Retensi media tidak terbukti:** pengelola membersihkan media selama build (donat menunjukkan 0 tersedia + 5 file tidak terakses), sesuai catatan `release.json` `mediaRetention: not established`.
- **Refetch lirik:** fault injection helper (`JournaledTextReplacementTest`) lulus di host; kegagalan provider Android, refetch live, dan proses mati **belum** diuji; **Lamunan tidak di-refetch** dan file audio/liriknya tidak diubah dalam batch ini.
- Tidak ada unduhan live, tidak ada pengujian sinkronisasi lirik baru, dan tidak ada benchmark kecepatan/FPS/daya. Angka dashboard adalah transisi COMPLETED yang tercatat sejak upgrade (termasuk retry), bukan jumlah file unik.
- Mutex lock proses aplikasi bukan transaksi dengan aplikasi eksternal; risiko BUG-008/003 (create→journal, proses mati) dan generation guard worker tetap terbuka seperti pada [rekonsiliasi backlog](PROGRESS.md#rekonsiliasi-backlog-bug-roadmap-dan-release--13-oktober-2026). Angka tes di atas berlaku untuk source sesi ini; jalankan ulang suite ketika source berubah.
