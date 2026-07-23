#!/usr/bin/env python3
"""Dependency-free source contract for the v0.3.8 login runtime hotfix."""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
activity = (ROOT / "app/src/main/java/id/fahmy/mangprang/MainActivity.java").read_text()
runtime = (ROOT / "app/src/main/java/id/fahmy/mangprang/LoginRuntime.java").read_text()
cookie_jar = (ROOT / "app/src/main/java/id/fahmy/mangprang/TracshCookieJar.java").read_text()
build = (ROOT / "app/build.gradle").read_text()

checks = {
    "versionCode advanced": "versionCode 15" in build,
    "versionName advanced": "versionName '0.3.8'" in build,
    "app version comes from Gradle": "BuildConfig.VERSION_NAME" in activity and "APP_VERSION" not in activity,
    "account login endpoint": 'TRACSH_ACCOUNT_LOGIN_ENDPOINT = TRACSH_BASE_URL + "/procAuth/signIn"' in activity,
    "group login endpoint": 'TRACSH_GROUP_LOGIN_ENDPOINT = TRACSH_BASE_URL + "/procAuth/signInGroup"' in activity,
    "role selection exists": '"Jenis akun: Group"' in activity and '"Jenis akun: Admin / Marketer"' in activity,
    "merchant GET is sole auth authority": "isSuccessfulLoginRedirect" not in activity and "MerchantFetchResult verification = requestMerchants()" in activity,
    "merchant verification precedes merchant screen": re.search(
        r"MerchantFetchResult verification = requestMerchants\(\);[\s\S]+?verification\.isSuccess\(\)[\s\S]+?showMerchantScreen\(false\)",
        activity,
    ) is not None,
    "401 and 403 stay distinct": 'code == 401' in activity and 'code == 403' in activity and 'failure("forbidden", code)' in activity,
    "invalid JSON is distinct": 'MerchantFetchResult.failure("invalid", code)' in activity,
    "network failure is distinct": 'MerchantFetchResult.networkError()' in activity,
    "empty account is not treated as login failure": "MerchantFetchResult.success(parseMerchants(text))" in activity and "AUTH_EMPTY" in activity,
    "password is cleared after verified login": 'passwordInput.setText("")' in activity,
    "Tracsh session is in-memory": "TracshCookieJar" in activity and "setCookieSync(" not in activity,
    "empty JSON object is invalid": "merchantArrayFromWrapper" in activity and 'if (rows == null) throw new Exception' in activity,
    "Tracsh cookies are host restricted": '"tracsh.com".equalsIgnoreCase(target.getHost())' in activity,
    "Tracsh cookies preserve URI scope": "CookieManager" in cookie_jar and "CookiePolicy.ACCEPT_ORIGINAL_SERVER" in cookie_jar and "target.toURI()" in cookie_jar,
    "merchant response requests gzip": 'setRequestProperty("Accept-Encoding", "gzip")' in activity and "GZIPInputStream" in runtime,
    "login has total watchdog": "LOGIN_TOTAL_TIMEOUT_MS = 60_000" in activity and "scheduleLoginTimeout(generation)" in activity and "VERIFY_TIMEOUT" in activity,
    "large response is byte bounded": "MAX_RESPONSE_BYTES" in activity and "total > maxBytes" in runtime and "Response terlalu besar" in runtime,
    "merchant dedupe is linear": "LinkedHashMap<String, Merchant> unique" in activity and "unique.containsKey(key)" in activity,
    "login progress distinguishes merchant fetch": "Login diterima. Mengambil daftar toko..." in activity,
    "active connections cancel from snapshot": "synchronized (activeConnections)" in activity and "cancelActiveConnections()" in activity,
    "POST body has fixed length": "setFixedLengthStreamingMode(bodyBytes.length)" in activity,
    "login POST skips response body": re.search(r"conn\.getResponseCode\(\);\s+saveCookies\(conn\);", activity) is not None,
    "merchant headers precede body": re.search(r"int code = conn\.getResponseCode\(\);\s+saveCookies\(conn\);[\s\S]+?String text = readResponse\(conn\);", activity) is not None,
    "transport exceptions are not masked": "catch (Exception e) { is = conn.getErrorStream(); }" not in activity,
    "lifecycle guards async callbacks": "!isFinishing() && !isDestroyed()" in activity,
}

for name, ok in checks.items():
    if not ok:
        raise SystemExit(f"login contract failed: {name}")

print(f"login contract OK ({len(checks)} invariants)")
