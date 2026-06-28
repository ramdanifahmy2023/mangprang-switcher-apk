# Mangprang Switcher APK

Android APK helper internal PT FahmyID untuk membuka Akulaku Seller di WebView, membaca cookie Akulaku dari WebView, lalu sync cookie ke Tracsh.

## Status

Versi awal native Android WebView. Ini bukan Chrome Extension; ini APK terpisah supaya tim bisa pakai dari HP Android.

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
3. Login ke Akulaku Seller di WebView.
4. Isi Merchant ID.
5. Klik **Baca Cookie**.
6. Klik **Sync**.

## Guardrail

- Aplikasi tidak menampilkan raw cookie; status hanya menampilkan nama cookie yang dimask.
- Jangan share APK ke luar tim internal.
- Endpoint aktif: `https://tracsh.com/api/updateCookie`.
