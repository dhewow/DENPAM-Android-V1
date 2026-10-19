# DENPAM V1 — APK Android

**Nama:** DENPAM — Digitalisasi Elektronik Pemeriksaan dan Analisis Pengamanan Internal.

**Website utama:** https://andin.top/denpam/

## Yang tersedia pada aplikasi

- Tampil seperti website DENPAM yang sudah disetujui (merah maroon dan logo PAMINAL asli), bukan UI baru yang berbeda.
- Login DENPAM dengan akun yang sudah ada. Password tidak disimpan di source APK.
- Mengaktifkan izin mikrofon untuk tombol Mulai Rekam pada website menggunakan WebView Android.
- Menangani pemilih audio dari file manager, serta unduhan Word/audio.
- **Start Otomatis:** mencoba mengirim file rekaman terautentikasi melalui intent Android `ACTION_SEND` ke aplikasi ChatGPT; bila tidak ada penerima, menampilkan pilihan berbagi Android. Tetap harus memilih percakapan/Project dan menekan Kirim sesuai dukungan ChatGPT.
- **Start Manual:** download rekaman ke folder Downloads lalu membuka link Project ChatGPT pribadi yang tersimpan di DENPAM.
- Mencatat niat berbagi ke audit DENPAM, tetapi **tidak mengklaim file telah diterima ChatGPT**.
- Audio dibagikan dari cache privat aplikasi via `ContentProvider` read-only dengan URI izin sementara. Cache rekaman yang berusia lebih dari 24 jam dibersihkan saat aplikasi dibuka.

## Pembatasan yang perlu dipahami

1. Ini adalah wrapper Android untuk DENPAM Web, **bukan** perekam native yang menjamin berjalan saat HP dikunci / aplikasi ditutup. Saat merekam, pertahankan aplikasi di layar depan; halaman sesi menjaga layar menyala untuk mengurangi risiko terputus. Tidak ada jaminan audio selamat saat Android membunuh proses.
2. **Tidak ada API ChatGPT otomatis.** Website maupun APK tidak bisa memaksakan file masuk ke Project ChatGPT tertentu atau memicu pengolahan tanpa persetujuan pengguna. Aplikasi ChatGPT Android mungkin tidak menerima `ACTION_SEND` untuk audio; bila begitu gunakan Start Manual.
3. Instruksi BAI dan template Word perlu dipasang **sekali per Project ChatGPT** oleh pengguna, sesuai `PROJECT_INSTRUCTIONS.txt` pada paket website DENPAM.
4. Gunakan rekaman simulasi dahulu. Pemeriksaan Paminal dapat mengandung informasi rahasia dan tidak boleh dikirim keluar instansi tanpa izin formal.
5. APK ini tidak menyertakan AI lokal, tidak mengubah backend, PostgreSQL, ATURAP, atau ISPAT. Internet HTTPS ke `andin.top` diperlukan.
6. APK Debug dipakai untuk uji coba internal; untuk distribusi institusi perlu build Release ditandatangani menggunakan keystore privat yang dikelola instansi (jangan kirim kunci privat lewat chat).

## Cara build APK (tanpa menulis kode lagi)

### Cara A: Android Studio
1. Install Android Studio resmi dan Android SDK Platform 35.
2. Buka folder `DENPAM_Android_V1` sebagai Project.
3. Tunggu Gradle Sync (butuh internet saat pertama kali untuk plugin dan SDK).
4. Pilih **Build > Build APK(s)**.
5. APK uji berada di `app/build/outputs/apk/debug/app-debug.apk`.
6. Pindahkan file APK ke HP, aktifkan izin instalasi dari sumber tersebut hanya selama proses pemasangan, dan instal.

### Cara B: GitHub Actions (kompilasi cloud)
1. Upload isi folder proyek ke repository **private** GitHub (jangan upload data pemeriksaan).
2. Di repository pilih **Actions > Build DENPAM APK > Run workflow**.
3. Setelah selesai, buka hasil workflow dan download artifact **DENPAM-V1-Android-APK**; ekstrak ZIP-nya untuk memperoleh `app-debug.apk`.

Tidak perlu menjalankan `install.sh` di VPS karena APK hanya klien; website DENPAM Anda tetap seperti semula.

## Checklist pengujian di Android

- [ ] Website login dan dashboard muncul dengan CSS/logo lengkap.
- [ ] Tombol Mulai Rekam memunculkan dialog izin mikrofon, perekaman berjalan.
- [ ] Jeda, Lanjut, Selesai & Upload menghasilkan record yang dapat diputar.
- [ ] Start Otomatis menawarkan ChatGPT bila mampu menerima audio; jika tidak, fallback ke menu berbagi.
- [ ] Start Manual menyimpan file dalam Download dan membuka ChatGPT Project.
- [ ] Logout/Login tidak membawa rekaman milik pengguna lain.
- [ ] Pengujian saat koneksi putus: rekaman yang belum berhasil upload tidak diklaim terkirim.
- [ ] Tidak ada akses atau perubahan pada ISPAT/ATURAP.

## Pengujian yang telah dilakukan dalam lingkungan pembuatan

Source Java diperiksa sintaks, manifest XML diparse, logo asli diverifikasi dan struktur proyek diaudit. **Tidak ada Android SDK/Gradle yang terpasang di lingkungan penyusun**, sehingga tidak ada klaim berhasil compile APK atau berhasil menguji di HP sampai build sungguhan dilakukan.
