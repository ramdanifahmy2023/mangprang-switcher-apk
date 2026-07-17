#!/usr/bin/env python3
"""Small source-level gate for the APK security contract.

This is intentionally dependency-free and fails closed when a P0 invariant
is accidentally weakened during a future patch.
"""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
activity = (ROOT / "app/src/main/java/id/fahmy/mangprang/MainActivity.java").read_text()
manifest = (ROOT / "app/src/main/AndroidManifest.xml").read_text()

checks = {
    "cleartext traffic disabled": 'android:usesCleartextTraffic="false"' in manifest,
    "application backup disabled": 'android:allowBackup="false"' in manifest,
    "third-party cookies disabled": "setAcceptThirdPartyCookies(webView, false)" in activity,
    "host-only Akulaku allowlist": '"ec-vendor.akulaku.com".equalsIgnoreCase(uri.getHost())' in activity,
    "cookie endpoint receives row identity": 'body.put("tracsh_row_id", merchant.id)' in activity,
    "merchant list does not copy cookie": 'm.cookie = ""' in activity,
    "network timeout bounded": "NETWORK_TIMEOUT_MS = 15_000" in activity,
    "WebView timeout bounded": "WEBVIEW_TIMEOUT_MS = 30_000" in activity,
    "SSL errors are cancelled": "handler.cancel()" in activity,
    "WebView debugging disabled": "setWebContentsDebuggingEnabled(false)" in activity,
    "response ownership checked": "responseMerchantId.equals(merchant.merchantId)" in activity,
    "cookie writes fail closed": "if (!ok)" in activity,
}

for name, ok in checks.items():
    if not ok:
        raise SystemExit(f"security contract failed: {name}")

if "setAcceptThirdPartyCookies(webView, true)" in activity:
    raise SystemExit("security contract failed: third-party cookies enabled")

print(f"security contract OK ({len(checks)} invariants)")
