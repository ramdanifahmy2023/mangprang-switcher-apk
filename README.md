# Mangprang Switcher APK

Android APK helper internal PT FahmyID untuk login Tracsh, menampilkan daftar toko milik user, lalu auto-login ke Akulaku Seller dengan cookie toko dari Tracsh.

## Status

Versi v0.3.7 (`versionCode 14`) memverifikasi sesi melalui endpoint daftar toko sebelum membuka layar merchant, membedakan sesi tidak valid, respons rusak, gangguan jaringan, dan akun valid tanpa assignment toko. Login mendukung pilihan akun Admin/Marketer atau Group. Cookie sesi Tracsh disimpan hanya pada jar HTTP in-memory yang terpisah dari CookieManager Akulaku. Cookie toko tidak diambil dari list; aplikasi selalu meminta cookie melalui endpoint session-scoped dengan identitas row toko.

Source v0.3.7 sudah lolos lint, unit-test task, dan build debug di CI. APK debug hanya boleh dipakai sebagai pilot internal; release distributable tetap harus dibangun dengan signing key release melalui workflow utama dan diverifikasi bersama checksum-nya.

## Cara Build

GitHub Actions menjalankan security contract check, `lintRelease`, unit test release, build APK release bertanda tangan, verifikasi sertifikat bukan Android Debug, dan pembuatan checksum SHA-256 pada setiap push ke `main` atau pemanggilan manual. APK dan `SHA256SUMS` tersedia sebagai artifact privat sesuai akses repository selama 3 hari. Workflow tidak melakukan commit atau push APK ke repository.

Manual lokal bila Android SDK tersedia:

```bash
./gradlew assembleDebug
```

APK debug berada di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Build release lokal memerlukan empat environment variable signing yang dijelaskan di bawah. Keystore, password, APK, dan AAB tidak boleh di-commit.

## GitHub Actions Secrets

Buat empat **repository Actions secrets** berikut melalui **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Isi yang diwajibkan |
| --- | --- |
| `ANDROID_SIGNING_KEY_BASE64` | Seluruh file keystore release **PKCS12** (`.p12`/`.keystore`) yang di-encode base64 satu baris. |
| `ANDROID_KEYSTORE_PASSWORD` | Password keystore release. |
| `ANDROID_KEY_ALIAS` | Alias private key release di dalam keystore. |
| `ANDROID_KEY_PASSWORD` | Password private key untuk alias tersebut. |

Jangan menaruh nilai secret di README, source, workflow, issue, log, artifact, atau history Git. Simpan backup keystore dan password di penyimpanan rahasia perusahaan. Kehilangan key mencegah update APK terpasang; mengganti key membuat Android menolak update di atas instalasi lama.

Untuk menghasilkan nilai base64 secara lokal tanpa membuat file output tambahan:

```bash
base64 -w 0 /path/to/mangprang-release.p12
```

Salin output langsung ke secret `ANDROID_SIGNING_KEY_BASE64`; jangan kirim output melalui chat atau commit.

## Prosedur Update

1. Ubah `versionName` di `app/build.gradle` ke versi baru dan naikkan `versionCode` secara monoton. Jangan memakai ulang atau menurunkan `versionCode`.
2. Jalankan `./gradlew lintRelease testReleaseUnitTest assembleRelease` dengan environment variable signing tersedia, atau push perubahan untuk menjalankan workflow.
3. Pastikan workflow hijau. Cocokkan SHA-256 APK dengan `SHA256SUMS` dan catat fingerprint sertifikat dari job summary sebelum distribusi internal.
4. Unduh artifact hanya melalui pengguna GitHub yang berhak, lalu distribusikan lewat kanal internal. Jangan commit APK ke repository.

## Prosedur Rollback

1. Hentikan distribusi artifact bermasalah dan simpan nomor versi, SHA-256, serta alasan rollback.
2. Revert commit sumber yang bermasalah. Pertahankan keystore/alias yang sama, lalu naikkan lagi `versionCode` dan buat release perbaikan baru. Ini jalur rollback yang dapat dipasang sebagai update tanpa menghapus aplikasi.
3. Jika harus kembali segera ke APK lama, verifikasi checksum dan sertifikatnya terlebih dahulu. Android umumnya menolak downgrade; uninstall versi baru lalu install versi lama akan menghapus data aplikasi. Gunakan opsi ini hanya setelah menyetujui dampak tersebut.
4. Jangan pernah memulihkan dengan debug APK, menurunkan `versionCode` untuk release berikutnya, atau mengganti signing key.

## Cara Pakai

1. Install APK release bertanda tangan dari GitHub Actions artifact. APK debug hanya untuk pilot internal terkontrol.
2. Buka aplikasi.
3. Pilih jenis akun **Admin / Marketer** atau **Group**, lalu login memakai username/password Tracsh.
4. Pilih toko dari daftar toko milik user.
5. Klik **Masuk Toko**.
6. App inject cookie toko ke WebView dan membuka Akulaku Seller.

## Guardrail

- Aplikasi tidak menampilkan raw cookie.
- Password Tracsh tidak disimpan.
- Cookie toko hanya dipakai untuk inject WebView Akulaku.
- Jangan share APK ke luar tim internal.
- Endpoint daftar toko: `GET https://tracsh.com/apiv/merchant`.
- Endpoint cookie session-scoped: `POST https://tracsh.com/api/getMerchantCookie`.
