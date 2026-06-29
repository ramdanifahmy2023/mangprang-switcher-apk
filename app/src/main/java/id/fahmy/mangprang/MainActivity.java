package id.fahmy.mangprang;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String TRACSH_BASE_URL = "https://tracsh.com";
    private static final String TRACSH_LOGIN_PAGE = TRACSH_BASE_URL + "/auth/signIn";
    private static final String MERCHANT_ENDPOINT = TRACSH_BASE_URL + "/apiv/merchant";
    private static final String MERCHANT_COOKIE_ENDPOINT = TRACSH_BASE_URL + "/api/getMerchantCookie";
    private static final String AKULAKU_COOKIE_URL = "https://ec-vendor.akulaku.com";
    private static final String AKULAKU_VENDOR_URL = "https://ec-vendor.akulaku.com/ec-vendor/";

    private LinearLayout root;
    private TextView statusText;
    private ProgressBar progress;
    private EditText usernameInput;
    private EditText passwordInput;
    private EditText searchInput;
    private WebView webView;
    private int webScale = 72;
    private final List<Merchant> merchants = new ArrayList<>();
    private String currentScreen = "login";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CookieManager.getInstance().setAcceptCookie(true);
        showLoginScreen();
    }

    private void baseRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        setContentView(root);
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setTypeface(null, style);
        return tv;
    }

    private Button primaryButton(String value) {
        Button btn = new Button(this);
        btn.setText(value);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(Color.rgb(37, 99, 235));
        btn.setAllCaps(false);
        btn.setPadding(16, 12, 16, 12);
        return btn;
    }

    private Button secondaryButton(String value) {
        Button btn = new Button(this);
        btn.setText(value);
        btn.setTextColor(Color.rgb(15, 23, 42));
        btn.setBackgroundColor(Color.rgb(226, 232, 240));
        btn.setAllCaps(false);
        return btn;
    }

    private EditText input(String hint, boolean password) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setPadding(18, 12, 18, 12);
        et.setTextColor(Color.rgb(15, 23, 42));
        et.setHintTextColor(Color.rgb(100, 116, 139));
        et.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        return et;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(24, 22, 24, 22);
        c.setBackgroundColor(Color.WHITE);
        return c;
    }

    private void showLoginScreen() {
        currentScreen = "login";
        baseRoot();
        ScrollView scroll = new ScrollView(this);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(28, 56, 28, 28);
        scroll.addView(wrap);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        TextView badge = text("Mangprang Switcher", 28, Color.rgb(15, 23, 42), 1);
        wrap.addView(badge, new LinearLayout.LayoutParams(-1, -2));
        TextView sub = text("Login Tracsh untuk masuk toko Akulaku dari HP.", 14, Color.rgb(71, 85, 105), 0);
        sub.setPadding(0, 8, 0, 24);
        wrap.addView(sub, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout loginCard = card();
        wrap.addView(loginCard, new LinearLayout.LayoutParams(-1, -2));
        usernameInput = input("Username / email Tracsh", false);
        passwordInput = input("Password Tracsh", true);
        loginCard.addView(usernameInput, new LinearLayout.LayoutParams(-1, -2));
        loginCard.addView(passwordInput, new LinearLayout.LayoutParams(-1, -2));

        Button login = primaryButton("Masuk");
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
        btnLp.setMargins(0, 18, 0, 0);
        loginCard.addView(login, btnLp);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        loginCard.addView(progress, new LinearLayout.LayoutParams(-1, -2));

        statusText = text("Password tidak disimpan. Session mengikuti cookie Tracsh.", 13, Color.rgb(100, 116, 139), 0);
        statusText.setPadding(0, 14, 0, 0);
        loginCard.addView(statusText, new LinearLayout.LayoutParams(-1, -2));
        login.setOnClickListener(v -> doLogin());
    }

    private void showMerchantScreen() {
        currentScreen = "merchant";
        baseRoot();
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(24, 28, 24, 16);
        header.setBackgroundColor(Color.WHITE);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text("Pilih Toko", 24, Color.rgb(15, 23, 42), 1);
        header.addView(title);
        TextView subtitle = text("Klik toko untuk auto-login ke Akulaku Seller.", 13, Color.rgb(71, 85, 105), 0);
        header.addView(subtitle);

        searchInput = input("Cari nama toko / email / group", false);
        header.addView(searchInput, new LinearLayout.LayoutParams(-1, -2));
        Button refresh = secondaryButton("Refresh daftar toko");
        header.addView(refresh, new LinearLayout.LayoutParams(-1, -2));
        refresh.setOnClickListener(v -> fetchMerchants());
        searchInput.setOnEditorActionListener((v, actionId, event) -> { renderMerchantList(searchInput.getText().toString()); return false; });
        searchInput.setOnKeyListener((v, keyCode, event) -> { renderMerchantList(searchInput.getText().toString()); return false; });

        statusText = text("Memuat daftar toko...", 13, Color.rgb(100, 116, 139), 0);
        statusText.setPadding(24, 8, 24, 8);
        root.addView(statusText, new LinearLayout.LayoutParams(-1, -2));
        progress = new ProgressBar(this);
        root.addView(progress, new LinearLayout.LayoutParams(-1, -2));
        fetchMerchants();
    }

    private void renderMerchantList(String query) {
        while (root.getChildCount() > 3) root.removeViewAt(3);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(16, 8, 16, 24);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        String q = (query == null ? "" : query).toLowerCase().trim();
        int shown = 0;
        for (Merchant m : merchants) {
            String hay = (m.title + " " + m.email + " " + m.groupTitle + " " + m.employeeName).toLowerCase();
            if (!q.isEmpty() && !hay.contains(q)) continue;
            shown++;
            LinearLayout c = card();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, 14);
            list.addView(c, lp);
            c.addView(text(m.title.isEmpty() ? "Toko tanpa nama" : m.title, 18, Color.rgb(15, 23, 42), 1));
            c.addView(text(maskEmail(m.email), 13, Color.rgb(71, 85, 105), 0));
            String meta = joinNonEmpty(m.groupTitle, m.employeeName);
            if (!meta.isEmpty()) c.addView(text(meta, 13, Color.rgb(71, 85, 105), 0));
            c.addView(text(m.hasCookie() ? "Cookie: tersedia" : "Cookie: belum tersedia / perlu endpoint", 13, m.hasCookie() ? Color.rgb(22, 163, 74) : Color.rgb(220, 38, 38), 1));
            Button open = primaryButton("Masuk Toko");
            c.addView(open, new LinearLayout.LayoutParams(-1, -2));
            open.setOnClickListener(v -> openMerchant(m));
        }
        statusText.setText("Toko tampil: " + shown + " dari " + merchants.size());
        progress.setVisibility(View.GONE);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showAkulakuScreen() {
        currentScreen = "akulaku";
        baseRoot();

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(10, 10, 10, 8);
        top.setBackgroundColor(Color.WHITE);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button back = secondaryButton("Toko");
        Button home = primaryButton("Akulaku Home");
        Button reload = secondaryButton("Reload");
        row1.addView(back, new LinearLayout.LayoutParams(0, -2, 1));
        row1.addView(home, new LinearLayout.LayoutParams(0, -2, 2));
        row1.addView(reload, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        Button zoomOut = secondaryButton("Zoom -");
        Button fit = secondaryButton("Fit layar");
        Button zoomIn = secondaryButton("Zoom +");
        row2.addView(zoomOut, new LinearLayout.LayoutParams(0, -2, 1));
        row2.addView(fit, new LinearLayout.LayoutParams(0, -2, 1));
        row2.addView(zoomIn, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(row2, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = text("Tips: cubit layar untuk zoom, geser kiri-kanan untuk tabel/menu Akulaku.", 12, Color.rgb(71, 85, 105), 0);
        hint.setPadding(4, 6, 4, 0);
        top.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(92);
        settings.setDefaultZoom(WebSettings.ZoomDensity.FAR);
        settings.setUserAgentString(settings.getUserAgentString() + " MangprangSwitcherApk/0.2.2");
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setInitialScale(webScale);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectAkulakuDesktopFit(view);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));

        back.setOnClickListener(v -> showMerchantScreen());
        home.setOnClickListener(v -> webView.loadUrl(AKULAKU_VENDOR_URL));
        reload.setOnClickListener(v -> webView.reload());
        zoomOut.setOnClickListener(v -> setWebScale(webScale - 10));
        fit.setOnClickListener(v -> setWebScale(72));
        zoomIn.setOnClickListener(v -> setWebScale(webScale + 10));
    }

    private void setWebScale(int scale) {
        if (scale < 45) scale = 45;
        if (scale > 140) scale = 140;
        webScale = scale;
        if (webView != null) {
            webView.setInitialScale(webScale);
            webView.zoomBy(webScale / 100.0f);
            injectAkulakuDesktopFit(webView);
        }
    }

    private void injectAkulakuDesktopFit(WebView view) {
        String js = "(function(){" +
            "var head=document.head||document.getElementsByTagName('head')[0];" +
            "if(head&&!document.getElementById('mangprang-viewport')){var m=document.createElement('meta');m.id='mangprang-viewport';m.name='viewport';m.content='width=1200, initial-scale=0.35, minimum-scale=0.25, maximum-scale=2.5, user-scalable=yes';head.appendChild(m);}" +
            "if(head&&!document.getElementById('mangprang-style')){var s=document.createElement('style');s.id='mangprang-style';s.innerHTML='html,body{min-width:1100px!important;overflow:auto!important;-webkit-text-size-adjust:85%!important;} table,.ant-table,.el-table{font-size:12px!important;} input,button,select,textarea{font-size:13px!important;} .ant-layout,.el-container{min-width:1100px!important;}';head.appendChild(s);}" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    private void doLogin() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (username.isEmpty() || password.isEmpty()) { statusText.setText("Username dan password wajib diisi."); return; }
        progress.setVisibility(View.VISIBLE);
        statusText.setText("Login ke Tracsh...");
        new Thread(() -> {
            Exception last = null;
            String csrf = "";
            try {
                HttpURLConnection preflight = open(TRACSH_LOGIN_PAGE, "GET", null);
                String loginHtml = readResponse(preflight);
                saveCookies(preflight);
                csrf = extractCsrf(loginHtml);
            } catch (Exception e) { last = e; }

            String[] endpoints = {"/procAuth/adminSignIn", "/procAuth/signIn", "/auth/signIn"};
            String[][] fieldSets = {{"username", "password"}, {"email", "password"}, {"username", "pass"}};
            for (String endpoint : endpoints) {
                for (String[] fields : fieldSets) {
                    try {
                        String body = encode(fields[0], username) + "&" + encode(fields[1], password);
                        if (!csrf.isEmpty()) {
                            body += "&" + encode("csrfToken", csrf);
                            body += "&" + encode("csrf_token", csrf);
                            body += "&" + encode("_token", csrf);
                        }
                        HttpURLConnection conn = open(TRACSH_BASE_URL + endpoint, "POST", "application/x-www-form-urlencoded");
                        try (OutputStream os = conn.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
                        int code = conn.getResponseCode();
                        String text = readResponse(conn);
                        saveCookies(conn);
                        if (code >= 200 && code < 400 && !looksLikeLoginFailed(text, conn.getURL().toString())) {
                            runOnUiThread(() -> showMerchantScreen());
                            return;
                        }
                    } catch (Exception e) { last = e; }
                }
            }
            Exception finalLast = last;
            runOnUiThread(() -> { progress.setVisibility(View.GONE); statusText.setText("Login gagal. Cek username/password Tracsh." + (finalLast != null ? " " + finalLast.getClass().getSimpleName() : "")); });
        }).start();
    }

    private void fetchMerchants() {
        progress.setVisibility(View.VISIBLE);
        statusText.setText("Mengambil daftar toko dari Tracsh...");
        new Thread(() -> {
            try {
                HttpURLConnection conn = open(MERCHANT_ENDPOINT, "GET", null);
                int code = conn.getResponseCode();
                String text = readResponse(conn);
                saveCookies(conn);
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
                List<Merchant> parsed = parseMerchants(text);
                merchants.clear();
                merchants.addAll(parsed);
                runOnUiThread(() -> renderMerchantList(""));
            } catch (Exception e) {
                runOnUiThread(() -> { progress.setVisibility(View.GONE); statusText.setText("Gagal ambil toko: " + e.getMessage()); });
            }
        }).start();
    }

    private void openMerchant(Merchant merchant) {
        progress.setVisibility(View.VISIBLE);
        statusText.setText("Menyiapkan login Akulaku untuk " + merchant.title + "...");
        new Thread(() -> {
            try {
                String cookie = merchant.cookie;
                if (cookie == null || cookie.trim().isEmpty()) cookie = fetchMerchantCookie(merchant.merchantId);
                if (cookie == null || cookie.trim().isEmpty()) throw new Exception("Cookie toko belum tersedia dari Tracsh.");
                clearAkulakuCookies();
                applyAkulakuCookie(cookie);
                runOnUiThread(() -> {
                    showAkulakuScreen();
                    webView.loadUrl(AKULAKU_VENDOR_URL);
                });
            } catch (Exception e) {
                runOnUiThread(() -> { progress.setVisibility(View.GONE); statusText.setText("Belum bisa masuk toko: " + e.getMessage()); });
            }
        }).start();
    }

    private String fetchMerchantCookie(String merchantId) throws Exception {
        if (merchantId == null || merchantId.isEmpty()) return "";
        JSONObject body = new JSONObject();
        body.put("merchantId", merchantId);
        HttpURLConnection conn = open(MERCHANT_COOKIE_ENDPOINT, "POST", "application/json");
        try (OutputStream os = conn.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
        int code = conn.getResponseCode();
        saveCookies(conn);
        if (code < 200 || code >= 300) return "";
        JSONObject data = new JSONObject(readResponse(conn));
        return data.optString("cookie", data.optString("data", ""));
    }

    private HttpURLConnection open(String url, String method, String contentType) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept", "application/json, text/html, text/plain, */*");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 MangprangSwitcherApk/0.2.2");
        String tracshCookie = CookieManager.getInstance().getCookie(TRACSH_BASE_URL);
        if (tracshCookie != null && !tracshCookie.trim().isEmpty()) conn.setRequestProperty("Cookie", tracshCookie);
        if (contentType != null) { conn.setRequestProperty("Content-Type", contentType); conn.setDoOutput(true); }
        return conn;
    }

    private void saveCookies(HttpURLConnection conn) {
        Map<String, List<String>> headers = conn.getHeaderFields();
        if (headers == null) return;
        List<String> values = headers.get("Set-Cookie");
        if (values == null) values = headers.get("set-cookie");
        if (values == null) return;
        CookieManager cm = CookieManager.getInstance();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) cm.setCookie(TRACSH_BASE_URL, value);
        }
        cm.flush();
    }

    private String extractCsrf(String html) {
        if (html == null || html.isEmpty()) return "";
        String[] patterns = {
            "name=[\"']csrfToken[\"']\\s+value=[\"']([^\"']+)",
            "name=[\"']csrf_token[\"']\\s+value=[\"']([^\"']+)",
            "name=[\"']_token[\"']\\s+value=[\"']([^\"']+)"
        };
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(html);
            if (matcher.find()) return matcher.group(1);
        }
        return "";
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        InputStream is;
        try { is = conn.getInputStream(); } catch (Exception e) { is = conn.getErrorStream(); }
        if (is == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        return sb.toString();
    }

    private List<Merchant> parseMerchants(String text) throws Exception {
        String trimmed = text == null ? "" : text.trim();
        Object parsed;
        if (trimmed.startsWith("[")) parsed = new JSONArray(trimmed);
        else if (trimmed.startsWith("{")) parsed = new JSONObject(trimmed);
        else throw new Exception("Response toko bukan JSON");
        List<Merchant> out = new ArrayList<>();
        collectMerchants(parsed, out);
        return dedupeMerchants(out);
    }

    private List<Merchant> dedupeMerchants(List<Merchant> source) {
        List<Merchant> out = new ArrayList<>();
        for (Merchant m : source) {
            String key = !m.merchantId.isEmpty() ? m.merchantId : (!m.id.isEmpty() ? m.id : m.title + m.email);
            boolean exists = false;
            for (Merchant existing : out) {
                String existingKey = !existing.merchantId.isEmpty() ? existing.merchantId : (!existing.id.isEmpty() ? existing.id : existing.title + existing.email);
                if (existingKey.equals(key)) { exists = true; break; }
            }
            if (!exists && (!m.title.isEmpty() || !m.email.isEmpty() || !m.merchantId.isEmpty())) out.add(m);
        }
        return out;
    }

    private void collectMerchants(Object node, List<Merchant> out) throws Exception {
        if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) collectMerchants(arr.get(i), out);
        } else if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            if (looksLikeMerchant(obj)) out.add(Merchant.from(obj));
            JSONArray names = obj.names();
            if (names != null) for (int i = 0; i < names.length(); i++) collectMerchants(obj.opt(names.getString(i)), out);
        }
    }

    private boolean looksLikeMerchant(JSONObject obj) {
        return obj.has("merchantId") || obj.has("merchant_id") || obj.has("mid") || obj.has("title") || obj.has("storeName") || obj.has("accountUsername");
    }

    private void clearAkulakuCookies() {
        CookieManager cm = CookieManager.getInstance();
        cm.removeAllCookies(null);
        cm.flush();
    }

    private void applyAkulakuCookie(String cookieHeader) {
        CookieManager cm = CookieManager.getInstance();
        for (String part : cookieHeader.split(";")) {
            String cookie = part.trim();
            if (cookie.isEmpty() || !cookie.contains("=")) continue;
            cm.setCookie(AKULAKU_COOKIE_URL, cookie + "; Domain=.akulaku.com; Path=/; Secure");
            cm.setCookie(AKULAKU_COOKIE_URL, cookie + "; Domain=ec-vendor.akulaku.com; Path=/; Secure");
        }
        cm.flush();
    }

    private boolean looksLikeLoginFailed(String text, String url) {
        String hay = ((text == null ? "" : text) + "\n" + (url == null ? "" : url)).toLowerCase();
        return hay.contains("password salah") || hay.contains("login gagal") || hay.contains("invalid") || (url != null && url.toLowerCase().contains("/auth/signin"));
    }

    private String encode(String k, String v) throws Exception { return URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8"); }
    private String maskEmail(String email) {
        if (email == null || email.isEmpty() || !email.contains("@")) return email == null ? "" : email;
        String[] parts = email.split("@", 2);
        String first = parts[0].length() <= 2 ? "**" : parts[0].substring(0, 2) + "***";
        return first + "@" + parts[1];
    }
    private String joinNonEmpty(String a, String b) {
        String aa = a == null ? "" : a.trim();
        String bb = b == null ? "" : b.trim();
        if (aa.isEmpty()) return bb;
        if (bb.isEmpty()) return aa;
        return aa + " • " + bb;
    }

    @Override
    public void onBackPressed() {
        if ("akulaku".equals(currentScreen) && webView != null && webView.canGoBack()) { webView.goBack(); return; }
        if ("akulaku".equals(currentScreen)) { showMerchantScreen(); return; }
        if ("merchant".equals(currentScreen)) { showLoginScreen(); return; }
        super.onBackPressed();
    }

    static class Merchant {
        String id = "";
        String merchantId = "";
        String title = "";
        String email = "";
        String groupTitle = "";
        String employeeName = "";
        String cookie = "";
        static Merchant from(JSONObject o) {
            Merchant m = new Merchant();
            m.id = pick(o, "id", "tracsh_row_id", "row_id");
            m.merchantId = pick(o, "merchantId", "merchant_id", "mid", "id");
            m.title = pick(o, "title", "name", "storeName", "store_name", "accountUsername", "email");
            m.email = pick(o, "email", "accountUsername", "username");
            m.groupTitle = pick(o, "groupTitle", "group", "groupName", "leader");
            m.employeeName = pick(o, "employeName", "employeeName", "marketerName");
            m.cookie = pick(o, "cookie", "cookieHeader", "akulakuCookie");
            return m;
        }
        boolean hasCookie() { return cookie != null && !cookie.trim().isEmpty(); }
        static String pick(JSONObject o, String... keys) {
            for (String k : keys) {
                Object v = o.opt(k);
                if (v != null && !JSONObject.NULL.equals(v)) {
                    String s = String.valueOf(v).trim();
                    if (!s.isEmpty()) return s;
                }
            }
            return "";
        }
    }
}
