package id.fahmy.mangprang;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
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
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String AKULAKU_URL = "https://ec-vendor.akulaku.com/ec-vendor/";
    private static final String TRACSH_UPDATE_URL = "https://tracsh.com/api/updateCookie";

    private WebView webView;
    private TextView statusText;
    private EditText merchantInput;
    private String lastCookie = "";

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Mangprang Switcher APK");
        title.setTextSize(20);
        title.setTextColor(Color.rgb(15, 23, 42));
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(24, 20, 24, 12);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(24, 0, 24, 16);

        merchantInput = new EditText(this);
        merchantInput.setHint("Merchant ID Tracsh / Akulaku");
        merchantInput.setSingleLine(true);
        controls.addView(merchantInput, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);

        Button openAkulaku = new Button(this);
        openAkulaku.setText("Buka Akulaku");
        openAkulaku.setOnClickListener(v -> webView.loadUrl(AKULAKU_URL));
        row.addView(openAkulaku, new LinearLayout.LayoutParams(0, -2, 1));

        Button readCookie = new Button(this);
        readCookie.setText("Baca Cookie");
        readCookie.setOnClickListener(v -> readCookie());
        row.addView(readCookie, new LinearLayout.LayoutParams(0, -2, 1));

        Button syncCookie = new Button(this);
        syncCookie.setText("Sync");
        syncCookie.setOnClickListener(v -> syncCookie());
        row.addView(syncCookie, new LinearLayout.LayoutParams(0, -2, 1));

        controls.addView(row, new LinearLayout.LayoutParams(-1, -2));

        statusText = new TextView(this);
        statusText.setText("Login Akulaku di WebView ini, isi Merchant ID, lalu Baca Cookie dan Sync.");
        statusText.setTextColor(Color.rgb(51, 65, 85));
        statusText.setPadding(0, 8, 0, 0);
        controls.addView(statusText, new LinearLayout.LayoutParams(-1, -2));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUserAgentString(settings.getUserAgentString() + " MangprangSwitcherApk/0.1.0");
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        root.addView(webView, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
        webView.loadUrl(AKULAKU_URL);
    }

    private void readCookie() {
        String cookie = CookieManager.getInstance().getCookie("https://ec-vendor.akulaku.com");
        if (cookie == null || cookie.trim().isEmpty()) {
            lastCookie = "";
            setStatus("Cookie Akulaku belum terbaca. Login dulu di halaman Akulaku.");
            return;
        }
        lastCookie = cookie;
        setStatus("Cookie terbaca: " + maskCookie(cookie) + "\nPanjang: " + cookie.length());
    }

    private void syncCookie() {
        String merchantId = merchantInput.getText().toString().trim();
        if (merchantId.isEmpty()) {
            setStatus("Merchant ID wajib diisi dulu.");
            return;
        }
        if (lastCookie.isEmpty()) readCookie();
        if (lastCookie.isEmpty()) return;

        setStatus("Mengirim cookie ke Tracsh...");
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("merchantId", merchantId);
                body.put("cookie", lastCookie);
                body.put("source", "mangprang-switcher-apk");
                body.put("syncedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(new Date()));

                HttpURLConnection conn = (HttpURLConnection) new URL(TRACSH_UPDATE_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept", "application/json, text/plain, */*");
                conn.setDoOutput(true);
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) { os.write(payload); }
                int code = conn.getResponseCode();
                runOnUiThread(() -> setStatus(code >= 200 && code < 300
                    ? "Sync berhasil. HTTP " + code
                    : "Sync belum berhasil. HTTP " + code + ". Cek session/akses Tracsh."));
            } catch (Exception e) {
                runOnUiThread(() -> setStatus("Sync error: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }).start();
    }

    private void setStatus(String text) {
        statusText.setText(text);
    }

    private String maskCookie(String cookie) {
        String[] parts = cookie.split(";");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            String name = part.trim().split("=", 2)[0];
            if (name.isEmpty()) continue;
            if (out.length() > 0) out.append(", ");
            out.append(name).append("=***");
            if (out.length() > 140) return out.substring(0, 140) + "...";
        }
        return out.toString();
    }
}
