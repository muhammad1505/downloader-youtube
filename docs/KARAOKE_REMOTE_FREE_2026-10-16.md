# Kajian karaoke gratis di luar HP — 16 Oktober 2026

## Keputusan dan status

Pengelola memilih **mencari opsi gratis terlebih dahulu**. Kandidat utama adalah Demucs pada Hugging Face ZeroGPU, untuk percobaan opsional. Belum ada integrasi remote, unggahan audio, akun baru, deployment, biaya, atau pengujian kualitas hasil. APK tetap memakai karaoke lokal. Layanan demo publik belum layak dijadikan dependensi wajib seluruh pengguna.

## Kondisi implementasi TubeFlow

- `core/KaraokeWorker.kt` menjalankan pemisahan ONNX/UVR di CPU HP, melalui WorkManager. Model diunduh terpisah; audio asli dipertahankan. Cuplikan dengan waktu mulai dan lagu penuh tersedia.
- `core/KaraokeEngine.kt` menangani inference dan FFmpeg; bagian inference inilah yang perlu dipindahkan untuk mengurangi beban HP.
- `core/KaraokeViewModel.kt` menyimpan identitas pekerjaan dan menyediakan pratinjau; `ui/KaraokeDialog.kt` menampilkan proses lokal.
- `core/KaraokePublication.kt` sudah menyediakan publikasi media turunan dengan jurnal pemulihan dan pemeriksaan hash. Gunakan kembali jalur ini jika hasil remote kelak diadaptasi ke kontrak hasil yang sama.
- [Kebijakan privasi](PRIVACY.md) saat ini menyatakan audio tidak diunggah. Integrasi remote memerlukan penjelasan penyedia dan persetujuan unggah per tindakan, sebelum transfer dimulai.

## Opsi yang diperiksa

| Opsi | Kelayakan | Batas yang relevan |
| --- | --- | --- |
| [Demucs di Hugging Face](https://huggingface.co/spaces/abidlabs/music-separation) | Kandidat percobaan pertama; inference berada di server | Kuota ZeroGPU, antrean, perubahan API/availability milik pengelola demo |
| [Google Colab](https://research.google.com/colaboratory/faq.html) | Alternatif notebook interaktif yang dijalankan pengguna | Resource gratis tidak dijamin; jangan dijadikan backend aplikasi melalui tunnel atau pengganti antarmuka notebook |

Menurut [dokumentasi ZeroGPU](https://huggingface.co/docs/hub/spaces-zerogpu), kuota harian saat pemeriksaan adalah 2 menit GPU tanpa login dan 5 menit GPU untuk akun gratis. **Waktu GPU berbeda dari durasi lagu**; jumlah lagu yang dapat diproses belum diukur. Akun gratis tertentu dapat meng-host maksimal dua ZeroGPU Spaces, dengan email terverifikasi, umur akun lebih dari 30 hari, dan status baik. Ini bukan jaminan kapasitas layanan publik. Aturan penyedia dapat berubah.

## Bukti pemeriksaan kandidat

Permintaan GET tanpa autentikasi, tanpa mengirim audio:

- [Metadata Space](https://huggingface.co/api/spaces/abidlabs/music-separation): `RUNNING`, domain `READY`; commit `f2a8314d0642119b4a107c076838229474dbe73b`.
- [Konfigurasi runtime](https://abidlabs-music-separation.hf.space/config): Gradio 6.8.0, antrean aktif, API publik `inference`.
- [Kontrak API](https://abidlabs-music-separation.hf.space/gradio_api/info): satu input audio; dua hasil berkas, vokal dan instrumental. Metadata yang terbaca tidak membuktikan inference berhasil atau quota tersedia.
- [Source aplikasi](https://huggingface.co/spaces/abidlabs/music-separation/blob/f2a8314d0642119b4a107c076838229474dbe73b/app.py): model `htdemucs`, hasil instrumental merupakan gabungan stem selain vokal, disimpan sebagai WAV. Fungsi GPU tidak menetapkan durasi khusus; dokumentasi menyebut batas default 60 detik per pemanggilan. Lagu panjang belum diuji.
- Source demo belum memberi jaminan retensi/penghapusan berkas pengguna. API publik yang diperiksa hanya mengekspos inference; jangan menjanjikan penghapusan remote atau pembatalan GPU sudah selesai.
- [Demucs upstream](https://github.com/facebookresearch/demucs) berlisensi MIT dan repositori Meta telah diarsipkan. README Space tidak mendeklarasikan lisensi aplikasinya. Sebelum menyalin/deploy source, periksa hak source demo dan bobot model secara terpisah; lisensi engine bukan lisensi seluruh layanan.

## Rancangan integrasi jika kandidat dipilih

1. Sediakan pilihan lokal dan online opsional; tampilkan penyedia, penggunaan internet, serta bahwa audio dikirim keluar perangkat. Jangan beralih otomatis ke lokal ketika kuota remote habis.
2. Uji cuplikan terlebih dahulu, lalu lagu penuh. Verifikasi autentikasi dan atribusi kuota API; jangan menyisipkan token bersama ke APK atau menghindari batas dengan rotasi akun.
3. Status persisten: menyiapkan → mengunggah → mengantre → memproses → mengunduh hasil → memverifikasi → pratinjau. Persen hanya dari byte/progres yang benar-benar tersedia.
4. Batasi ukuran, waktu, dan retry; tangani offline, 429/kuota habis, antrean, layanan tidur, respons berubah, hasil rusak, dan pembatalan. Batasi URL hasil/redirect ke penyedia yang disetujui; jangan menerima path server sebagai path lokal.
5. Verifikasi tipe media, durasi, hash lokal, dan kecocokan request sebelum menawarkan Simpan. Hash menjamin integritas berkas lokal, bukan keaslian hasil dari penyedia. Sumber tidak berubah; hasil memakai publikasi yang sudah ada. Cuplikan tidak menerima LRC lagu penuh.
6. Simpan identitas penyedia/model dan status pekerjaan seperlunya, tanpa token/URL sensitif di log. Perbarui kebijakan privasi sesuai perilaku yang benar-benar diterapkan.

## Verifikasi yang masih diperlukan

Belum dijalankan: inference, benchmark waktu/kuota, penilaian vokal, retensi/penghapusan remote, autentikasi akun gratis, pembatalan, pemulihan job, serta alur online di HP Android 10. Pemeriksaan kali ini hanya source, dokumentasi resmi, metadata runtime, dan kontrak API. Tidak ada perubahan kode aplikasi, database, dependensi, maupun build APK.
