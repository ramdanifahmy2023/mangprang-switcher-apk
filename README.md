# Mangprang Switcher APK

Android APK helper internal PT FahmyID untuk login Tracsh, menampilkan daftar toko milik user, lalu auto-login ke Akulaku Seller dengan cookie toko dari Tracsh.

## Status

Versi v0.3.2 native Android WebView dengan enterprise UI polish lanjutan. Ini bukan Chrome Extension; ini APK terpisah supaya tim bisa switch toko Akulaku dari HP Android lewat akun Tracsh.

## Cara Build

Build otomatis lewat GitHub Actions setiap push ke `main`.

Manual lokal bila Android SDK tersedia:

```bash
./gradlew assembleDebug
```

APK debug berada di:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Cara Pakai

1. Install APK debug dari GitHub Actions artifact.
2. Buka aplikasi.
3. Login memakai username/password Tracsh.
4. Pilih toko dari daftar toko milik user.
5. Klik **Masuk Toko**.
6. App inject cookie toko ke WebView dan membuka Akulaku Seller.

## Guardrail

- Aplikasi tidak menampilkan raw cookie.
- Password Tracsh tidak disimpan.
- Cookie toko hanya dipakai untuk inject WebView Akulaku.
- Jangan share APK ke luar tim internal.
- Endpoint aktif: `https://tracsh.com/api/updateCookie`.
