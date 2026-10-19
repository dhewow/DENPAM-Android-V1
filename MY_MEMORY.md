# MY_MEMORY — DENPAM V1 ANDROID

## Tujuan
Android klien dari https://andin.top/denpam/ dengan logo Paminal resmi. Login, rekam dan kelola di website; audio diteruskan secara sadar ke ChatGPT Project untuk pemrosesan BAI Word.

## Arsitektur
Single Activity Java WebView HTTPS, ChromeClient izin mikrofon, FileChooser, DownloadManager, bridge navigasi skema URL khusus ke native ACTION_SEND. PrivateAudioProvider read-only melayani file rekaman sementara dari cache privat. Tanpa SDK pihak ketiga. Semua autentikasi, pengaturan Project, dan audit utama tetap di server DENPAM.

## Dependensi
Android Studio, Android SDK 35, Android Gradle Plugin 8.7.3, Gradle 8.9, Java 17. Build cloud via GitHub Actions; minSDK 26, targetSDK 34.

## Hak akses
`INTERNET`, `RECORD_AUDIO`. Tidak ada izin penyimpanan luas, kontak, lokasi, atau kamera.

## Data dan privasi
Cookie login WebView disimpan oleh Android. Native mengambil rekaman dengan cookie login hanya dari `https://andin.top/denpam/recordings/{id}`. Audio dibagikan melalui content URI read-only dengan izin sementara, file cache dibersihkan jika usia >24h. Project pribadi tersimpan di backend sesuai user. Tidak ada hardcoded password atau token.

## Deployment
Build APK Debug dari Android Studio atau GitHub Actions lalu pasang di HP; tidak perlu update VPS. Website ChatGPT handoff V1 harus sudah terpasang.

## Status / known issues
WebView tidak menjamin rekaman tetap aktif saat Android mematikan aplikasi/HP dikunci. Tidak ada jaminan ChatGPT Android menerima share intent audio atau memasukkannya ke Project tertentu. Belum teruji pada ponsel real karena toolchain dan emulator Android tidak tersedia.

## Selanjutnya
Lakukan build & install nyata, uji izin mikrofon/record/upload/share. Bila diperlukan fitur rekam tahan layar mati/background, implementasi perekam native + foreground service dan antrean unggah terpisah setelah diskusi.
