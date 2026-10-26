# Dukungan episode dan season Bstation — 25 Oktober 2026

## Cakupan

TubeFlow menerima tautan Bstation/Bilibili TV berikut:

- **Season**: `https://www.bilibili.tv/<locale>/play/<season>` dan
  `/media/<season>` (contoh `https://www.bilibili.tv/id/play/2432940`).
  Halaman season membuka **pemilih episode**.
- **Episode**: `https://www.bilibili.tv/<locale>/play/<season>/<episode>`
  (contoh `https://www.bilibili.tv/id/play/2432940/31524599`).
- **Video UGC**: `https://www.bilibili.tv/<locale>/video/<id>`.
- Host yang dikenali extractor BiliIntl: `bilibili.tv`/`www.bilibili.tv` dan
  `biliintl.com`/`www.biliintl.com`. Subdomain lain (mis. `m.bilibili.tv`) hanya
  dikenali sebagai label platform, tidak diteruskan ke engine karena extractor
  upstream akan menolaknya.

Label platform di Riwayat adalah **Bstation** (`bilibili_tv`). Unduhan tetap
memakai extractor upstream `BiliIntl` per episode; tidak ada extractor video
kustom.

## Perilaku pemilih season

- Katalog diambil dari API publik yang sama dengan extractor BiliIntl:
  `season_info` untuk judul/cover dan `episodes` untuk daftar
  `sections[].episodes[]`.
- Setiap episode menjadi **satu job terpisah**. Satu season tidak dijalankan
  sebagai satu job `--playlist`, karena worker mengasumsikan satu hasil
  publikasi/progress per job.
- Episode gratis bernomor dipilih otomatis. PV/spesial tetap terlihat tetapi
  tidak dipilih otomatis.
- Episode dengan `limit > 0` atau `limit_text` Premium/VIP ditandai
  **Premium · masuk untuk memilih** dan checkbox-nya terkunci selama belum
  masuk. Setelah login, checkbox aktif dengan catatan akses mengikuti
  langganan akun; episode Premium yang dipilih diantrekan seperti episode lain.
  `queuePlaylist` memeriksa ulang status login agar pilihan lama tidak lolos
  saat sesi dihapus.
- Daftar mengikuti episode yang tersedia untuk wilayah akun; jumlahnya bisa
  berbeda dari katalog lengkap di wilayah lain.
- Nama episode dinormalisasi menjadi dua digit (`E01 - The Battle Begins`) agar
  urutan file benar. PV/special diletakkan setelah episode bernomor.
- Folder season berasal dari judul, contoh `black-clover-season-2`; fallback
  `bstation-<seasonId>` bila judul tidak aman. Episode masuk satu folder season.
- URL episode memakai bentuk kanonis
  `https://www.bilibili.tv/play/<season>/<episode>`, sehingga locale dan query
  pelacakan (`?bstar_from=…`) tidak dianggap episode berbeda.

## Login dan cookie Bstation

- Login dilakukan di **WebView halaman resmi Bstation** (Pengaturan → Akun
  Bstation, atau tombol langsung di pemilih season). TubeFlow tidak memiliki
  formulir kata sandi dan tidak melihat/menyimpan kata sandi.
- Setelah cookie `SESSDATA` muncul, `BstationAuth` membaca cookie HttpOnly
  lewat `CookieManager`, menyimpan salinan sesi dengan AES-GCM + kunci Android
  Keystore di `noBackupFilesDir`, lalu menutup jendela login. Cookie tidak ikut backup dan
  tidak pernah ditulis ke log.
- Katalog season dan engine memakai sesi hanya untuk host Bstation. Engine
  menerima file Netscape sementara berisi cookie `.bilibili.tv` dan
  `.biliintl.com`; file dihapus di `finally` setelah proses selesai.
- Tombol keluar menghapus file sesi, kunci Keystore, dan cookie WebView, sehingga
  login berikutnya harus dimulai dari awal. Tidak ada refresh token atau akun di
  server TubeFlow.
- Akses Premium tetap ditentukan Bstation. Login biasa tanpa langganan dapat
  membuat episode Premium terlihat dan terpilih, tetapi `playurl`/unduhan bisa
  ditolak. Pesan kegagalan premium mengarahkan pengguna ke Akun Bstation.
- Sesi yang kedaluwarsa dapat membuat unduhan Premium gagal meski status di
  Pengaturan masih "Masuk"; keluar lalu masuk lagi memperbarui cookie. Bila API
  katalog menolak cookie lama, katalog dibaca ulang tanpa cookie agar episode
  gratis tetap terlihat; status login di Pengaturan tidak diubah otomatis.

## Yang sengaja ditolak

- DRM (Crunchyroll, Netflix, Hidive premium, dan sejenisnya) tidak didukung.
  Login tidak membuka konten ber-DRM; TubeFlow tidak menambahkan penanganan DRM.
- Campur tangan OAuth/SSO yang memblokir WebView. Login email/telepon di halaman
  resmi Bstation tetap tersedia.
- Satu halaman season tidak bisa diunduh sebagai satu video. Jalur video
  tunggal tetap menolaknya dan mengarahkan ke pemilih episode.
- Tidak ada auto-follow episode baru; daftar dibaca saat pengguna membuka season.

## Perubahan

- `core/BstationUrls.kt` — aturan host/path, bentuk kanonis episode, bentuk
  kanonis season (`canonicalSeason`, `seasonId`), URL episode, dan
  `isSeriesEpisode`.
- `core/BstationSeason.kt` — klien katalog API Bstation dengan cookie sesi bila
  ada, parser section/episode, normalisasi judul, deteksi Premium, aturan
  `premiumLocked`, folder season, serta error geo/premium/katalog kosong.
- `core/BstationCookies.kt` — parser header cookie yang menolak nama/nilai tidak
  aman, penggabungan header WebView, dan ekspor Netscape untuk dua domain
  Bstation. Konstanta `SESSDATA` menjadi syarat sesi valid.
- `core/BstationAuth.kt` — penangkapan cookie dari WebView, penyimpanan
  terenkripsi AES-GCM di `noBackupFilesDir`, file cookie sementara untuk engine,
  status akun, dan logout yang ikut membersihkan cookie WebView.
- `ui/BstationLoginDialog.kt` — WebView login penuh layar, deteksi cookie
  otomatis, status pemuatan/error, dan pesan penyimpanan yang gagal.
- `ui/SettingsScreen.kt` — kategori **Akun Bstation**, status login/UID, tombol
  masuk/keluar dengan konfirmasi, dan penjelasan privasi.
- `ui/TubeFlowApp.kt` + `MainActivity.kt` — memuat sesi saat aplikasi dibuka,
  menyediakan dialog login dari Pengaturan/pemilih, dan memuat ulang katalog
  season setelah login.
- `core/Models.kt` — `PlaylistEntry.episodeNumber`/`premium`,
  `Selection.seriesFolder`, dan jalur metadata episode season yang tidak
  memecah judul sebagai “artis - lagu”.
- `core/Engine.kt` — `inspectPlaylist` mengarahkan season ke katalog Bstation;
  jalur video tunggal tetap menolak season dengan pesan yang menjelaskan;
  inspeksi/unduhan episode menerima cookie sesi; `--cookies` hanya untuk URL
  Bstation dan file cookie sementara dihapus setelah eksekusi, termasuk saat
  dibatalkan.
- `core/DownloadViewModel.kt` — inspeksi season memakai URL kanonis, folder
  season tidak ditimpa policy artis/musik, dan `queuePlaylist` membuat job per
  episode dengan `autoFolder=""`, subfolder season, serta hanya melewati
  episode Premium bila belum masuk.
- `core/DownloadWorker.kt` — episode season tidak menjalankan pencarian lirik/
  identitas musik dan tidak memakai metadata audio native untuk mengubah judul.
- `core/FailureDetails.kt` — kategori `empty` untuk katalog season kosong,
  selain geo/premium yang sudah ada; pesan premium kini mengarahkan ke login
  Bstation, bukan menyatakan login belum didukung.
- `ui/DownloadScreen.kt` — deteksi season sebagai koleksi, kartu pemilih episode,
  pencarian, pilihan otomatis episode gratis, kunci/buka Premium sesuai status
  login, tombol masuk, dan teks antrean yang menyebut episode.
- Regresi: `BstationUrlsTest`, `BstationSeasonTest`, `BstationCookiesTest`,
  `SelectionTest`, dan `FailureDetailsTest`.

## Verifikasi

Pemeriksaan API/engine pada URL yang dilaporkan:

- `https://www.bilibili.tv/id/play/2432940` dikenali `biliIntl:series`
  (“Black Clover Season 2”).
- Dari VM ini (IP **US**), API katalog mengembalikan 4 episode + 1 PV;
  E4 memiliki `limit: 2`, `limit_text: "Premium"`. Di HP Indonesia daftar bisa
  lebih panjang karena ketersediaan wilayah.
- `https://www.bilibili.tv/id/play/2432940/31524599` dikenali `BiliIntl`;
  metadata episode, subtitle English/Thai, dan intro/outro terbaca.
- Dari VM ini (IP **US**), `playurl` mengembalikan `10004001`
  (geo restriction), jadi format/resolusi/unduhan **belum terverifikasi**.
  Uji perangkat di Indonesia masih diperlukan.

Verifikasi unit di workspace:

- `BstationUrlsTest` (7 kasus): **OK**.
- `BstationSeasonTest` (7 kasus): **OK**.
- `BstationCookiesTest` (6 kasus): **OK** — parser header, penolakan injeksi,
  prioritas header, syarat `SESSDATA`, ekspor Netscape dua domain, dan UID.
- `SelectionTest` (47 kasus): **OK**.
- `FailureDetailsTest` (8 kasus): **OK**.
- Total **75 tes** inti (kelima kelas di atas) lulus.
- Format file Netscape yang dihasilkan diuji dengan `http.cookiejar.MozillaCookieJar`
  (parser yang dipakai yt-dlp); `SESSDATA` terbaca untuk `.bilibili.tv` dan
  `.biliintl.com`.

Kelima kelas regresi di atas dijalankan melalui Gradle `:app:testDebugUnitTest`
(termasuk `BstationCookiesTest` yang baru); `BstationSeason` kini memakai
`Context` untuk cookie sehingga tidak lagi dijalankan di harness kotlinc tanpa
Android.

Verifikasi Gradle (SDK 37 sementara di `/tmp`):

- `:app:compileDebugKotlin` — **BUILD SUCCESSFUL**.
- `:app:testDebugUnitTest` — **511 tes, 0 gagal**.
- `:app:lintDebug` — **BUILD SUCCESSFUL**; hanya 2 peringatan lama
  (Selected Photo Access dan versi Gradle); tidak ada peringatan login/WebView
  baru.
- `:app:assembleDebug` — **BUILD SUCCESSFUL** memakai AAR FFmpeg stok sementara
  karena `app/libs/ffmpeg-0.18.1-webp16k.aar` tidak ada di workspace. APK itu
  bukan build rilis yang setara dengan AAR WebP proyek.

Uji perangkat Indonesia dan build dengan AAR FFmpeg proyek tetap diperlukan.

**Belum diverifikasi:** login WebView dengan akun nyata, penyimpanan/decrypt
Keystore di perangkat, dan `playurl` episode Premium dari jaringan Indonesia.
Tidak ada akun Bstation di VM ini; alur cookie hanya diuji pada level parser dan
file Netscape. Uji ini wajib sebelum mengklaim Premium berhasil diunduh.

## Subtitle dan detail episode (26 Oktober 2026)

Subtitle episode sempat tidak terdeteksi karena inspeksi memakai
`--dump-single-json` tanpa `--write-subs`; BiliIntl membaca daftar subtitle lewat
`extract_subtitles()` yang hanya aktif bila subtitle diminta. Setelah flag
ditambahkan, episode `31524599` melaporkan `en`, `th`, dan `id`.

Pemilih subtitle kini mengikuti sumber. YouTube mempertahankan pilihan "Bahasa
asli" karena auto-caption `-orig` menandai bahasa aslinya; Bstation hanya
menampilkan bahasa yang benar-benar tersedia (mis. Inggris/Thai/Indonesia) dan
memilih bahasa perangkat sebagai default bila ada. `Selection` menerima tag
bahasa ISO, bukan lagi daftar tetap, sehingga `th` tidak ditolak sebelum engine
berjalan.

Dua detail pratinjau juga diperbaiki. BiliIntl tidak melaporkan `uploader`
maupun `duration`, jadi baris penulis memakai judul seri dari API season
(`BstationSeason.seriesTitle`) dan durasi yang tidak diketahui tidak lagi
ditampilkan sebagai `0:00` — baik di kartu maupun di badge thumbnail.

## Batas berikutnya

- Uji di HP Indonesia: daftar season muncul, episode gratis terpilih otomatis,
  Premium terkunci sebelum login, lalu bisa dipilih setelah login dan
  diantrekan ke satu folder.
- Uji login email/telepon di WebView, tutup otomatis setelah `SESSDATA` muncul,
  status UID di Pengaturan, logout, lalu login ulang.
- Episode gratis harus menampilkan resolusi dan bisa diunduh; episode Premium
  harus memberi pesan langganan/geo yang jelas bila akun tidak berhak, bukan
  crash atau sukses palsu.
- Uji sesi kedaluwarsa: hapus cookie dari sisi Bstation atau tunggu kedaluwarsa,
  lalu pastikan pesan login muncul dan login ulang memperbaikinya.
- Durasi episode tidak tersedia dari API katalog; durasi asli diisi saat worker
  menginspeksi masing-masing episode.
- API katalog Bstation dapat berubah; parser dan pesan error perlu diuji ulang
  saat extractor upstream berubah.
- WebView login membuka halaman resmi Bstation. OAuth pihak ketiga yang menolak
  embedded WebView berada di luar kendali aplikasi; gunakan login email/telepon.
