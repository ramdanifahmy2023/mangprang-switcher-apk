package id.fahmy.mangprang;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Insets;
import android.view.WindowInsets;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Window;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.http.SslError;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final String TRACSH_BASE_URL = "https://tracsh.com";
    private static final String TRACSH_LOGIN_PAGE = TRACSH_BASE_URL + "/auth/signIn";
    private static final String MERCHANT_ENDPOINT = TRACSH_BASE_URL + "/apiv/merchant";
    private static final String MERCHANT_COOKIE_ENDPOINT = TRACSH_BASE_URL + "/api/getMerchantCookie";
    private static final String AKULAKU_COOKIE_URL = "https://ec-vendor.akulaku.com";
    private static final String AKULAKU_VENDOR_URL = "https://ec-vendor.akulaku.com/ec-vendor/";
    private static final int NETWORK_TIMEOUT_MS = 15_000;
    private static final int WEBVIEW_TIMEOUT_MS = 30_000;
    private static final Pattern COOKIE_NAME = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$");
    private static final Set<String> COOKIE_ATTRIBUTES = Collections.unmodifiableSet(new HashSet<String>() {{
        add("domain"); add("path"); add("expires"); add("max-age"); add("secure");
        add("httponly"); add("samesite"); add("priority"); add("partitioned");
    }});

    private static final int BG = Color.rgb(241, 245, 249);
    private static final int CARD = Color.WHITE;
    private static final int PRIMARY = Color.rgb(29, 78, 216);
    private static final int PRIMARY_DARK = Color.rgb(30, 64, 175);
    private static final int TEXT = Color.rgb(15, 23, 42);
    private static final int MUTED = Color.rgb(100, 116, 139);
    private static final int LINE = Color.rgb(226, 232, 240);
    private static final int SUCCESS = Color.rgb(22, 163, 74);
    private static final int DANGER = Color.rgb(220, 38, 38);

    private LinearLayout root;
    private TextView statusText;
    private ProgressBar progress;
    private EditText usernameInput;
    private EditText passwordInput;
    private EditText searchInput;
    private LinearLayout merchantListView;
    private Button refreshButton;
    private Button clearSearchButton;
    private WebView webView;
    private TextView webStatusText;
    private ProgressBar webProgress;
    private int webScale = 100;
    private int screenMode = 1;
    private int safeLeftInset = 0;
    private int safeTopInset = 0;
    private int safeRightInset = 0;
    private int safeBottomInset = 0;
    private boolean loggedInToTracsh = false;
    private boolean enteringStore = false;
    private final List<Merchant> merchants = new ArrayList<>();
    private final Map<String, String> tracshCookies = Collections.synchronizedMap(new LinkedHashMap<>());
    private final Set<HttpURLConnection> activeConnections = Collections.synchronizedSet(new HashSet<>());
    private final AtomicInteger requestGeneration = new AtomicInteger();
    private volatile boolean destroyed = false;
    private String activeMerchantTitle = "";
    private String currentScreen = "login";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int webNavigationToken = 0;
    private final Runnable webLoadTimeout = () -> {
        if (webView != null && "akulaku".equals(currentScreen) && webProgress != null && webProgress.getVisibility() == View.VISIBLE) {
            webView.stopLoading();
            showWebError("Akulaku terlalu lama merespons. Tekan Reload untuk mencoba lagi.");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        CookieManager.getInstance().setAcceptCookie(true);
        WebView.setWebContentsDebuggingEnabled(false);
        configureSystemBars();
        computeScreenMode();
        showLoginScreen();
        clearAllCookies(null);
    }

    private void computeScreenMode() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int h = Math.round(dm.heightPixels / dm.density);
        int w = Math.round(dm.widthPixels / dm.density);
        if (h < 700 || w < 360) screenMode = 0;
        else if (h > 840) screenMode = 2;
        else screenMode = 1;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int adaptive(int compact, int normal, int tall) { return screenMode == 0 ? compact : (screenMode == 2 ? tall : normal); }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(Color.WHITE);
        int flags = 0;
        if (Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private GradientDrawable bg(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable borderedBg(int color, int radiusDp, int strokeColor) {
        GradientDrawable g = bg(color, radiusDp);
        g.setStroke(dp(1), strokeColor);
        return g;
    }

    private void applyInsetsAwarePadding(View view, int left, int top, int right, int bottom) {
        applyInsetsAwarePadding(view, left, top, right, bottom, false, true, true);
    }

    private void applyInsetsAwarePadding(View view, int left, int top, int right, int bottom, boolean includeTop, boolean includeBottom) {
        applyInsetsAwarePadding(view, left, top, right, bottom, includeTop, includeBottom, true);
    }

    private void applyInsetsAwarePadding(View view, int left, int top, int right, int bottom, boolean includeTop, boolean includeBottom, boolean includeHorizontal) {
        setResolvedPadding(view, left, top, right, bottom, includeTop, includeBottom, includeHorizontal);
        if (Build.VERSION.SDK_INT >= 20) {
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                updateSafeInsets(insets);
                setResolvedPadding(v, left, top, right, bottom, includeTop, includeBottom, includeHorizontal);
                return insets;
            });
            view.requestApplyInsets();
        }
    }

    private void updateSafeInsets(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
            safeLeftInset = Math.max(bars.left, cutout.left);
            safeTopInset = Math.max(bars.top, cutout.top);
            safeRightInset = Math.max(bars.right, cutout.right);
            safeBottomInset = Math.max(bars.bottom, cutout.bottom);
        } else {
            safeLeftInset = insets.getSystemWindowInsetLeft();
            safeTopInset = insets.getSystemWindowInsetTop();
            safeRightInset = insets.getSystemWindowInsetRight();
            safeBottomInset = insets.getSystemWindowInsetBottom();
        }
    }

    private void setResolvedPadding(View view, int left, int top, int right, int bottom, boolean includeTop, boolean includeBottom, boolean includeHorizontal) {
        int l = dp(left) + (includeHorizontal ? safeLeftInset : 0);
        int t = dp(top) + (includeTop ? safeTopInset : 0);
        int r = dp(right) + (includeHorizontal ? safeRightInset : 0);
        int b = dp(bottom) + (includeBottom ? safeBottomInset : 0);
        view.setPadding(l, t, r, b);
    }

    private void baseRoot() {
        requestGeneration.incrementAndGet();
        destroyWebView();
        computeScreenMode();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 20) root.requestApplyInsets();
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setTypeface(Typeface.DEFAULT, style);
        return tv;
    }

    private TextView centerText(String value, int sp, int color, int style) {
        TextView tv = text(value, sp, color, style);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private Button primaryButton(String value) {
        Button btn = new Button(this);
        btn.setText(value);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(14);
        btn.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        btn.setBackground(bg(PRIMARY, 10));
        btn.setAllCaps(false);
        btn.setMinHeight(dp(48));
        btn.setPadding(dp(16), dp(10), dp(16), dp(10));
        return btn;
    }

    private Button secondaryButton(String value) {
        Button btn = new Button(this);
        btn.setText(value);
        btn.setTextColor(TEXT);
        btn.setTextSize(13);
        btn.setBackground(borderedBg(Color.WHITE, 10, LINE));
        btn.setAllCaps(false);
        btn.setMinHeight(dp(48));
        btn.setPadding(dp(10), dp(8), dp(10), dp(8));
        return btn;
    }

    private Button compactButton(String value) {
        Button btn = secondaryButton(value);
        btn.setTextSize(12);
        btn.setMinHeight(dp(40));
        btn.setMinimumHeight(dp(40));
        btn.setPadding(dp(8), dp(4), dp(8), dp(4));
        return btn;
    }

    private EditText input(String hint, boolean password) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setTextSize(14);
        et.setMinHeight(dp(48));
        et.setPadding(dp(14), dp(10), dp(14), dp(10));
        et.setTextColor(TEXT);
        et.setHintTextColor(MUTED);
        et.setBackground(borderedBg(Color.rgb(248, 250, 252), 12, LINE));
        et.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        return et;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(adaptive(16, 20, 22)), dp(adaptive(14, 18, 20)), dp(adaptive(16, 20, 22)), dp(adaptive(14, 18, 20)));
        c.setBackground(borderedBg(CARD, 16, LINE));
        if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(1));
        return c;
    }

    private LinearLayout storeCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(borderedBg(CARD, 14, LINE));
        if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(1));
        return c;
    }

    private View spacer(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    private View flexSpacer(float weight) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, 0, weight));
        return view;
    }

    private LinearLayout bottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        applyInsetsAwarePadding(bar, 16, 6, 16, 8, false, true);
        bar.setBackgroundColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 21) bar.setElevation(dp(8));
        return bar;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private void showLoginScreen() {
        currentScreen = "login";
        baseRoot();

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(Gravity.CENTER_HORIZONTAL);
        applyInsetsAwarePadding(wrap, adaptive(18, 24, 30), adaptive(12, 18, 24), adaptive(18, 24, 30), adaptive(12, 16, 20), true, true);
        root.addView(wrap, new LinearLayout.LayoutParams(-1, -1));

        wrap.addView(flexSpacer(screenMode == 0 ? 0.35f : 0.8f));

        TextView logo = centerText("M", 28, Color.WHITE, Typeface.BOLD);
        logo.setBackgroundColor(PRIMARY_DARK);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        logoLp.gravity = Gravity.CENTER_HORIZONTAL;
        wrap.addView(logo, logoLp);

        TextView title = centerText("Mangprang", adaptive(24, 27, 30), TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(10), 0, 0);
        wrap.addView(title, titleLp);

        TextView sub = centerText("Enterprise switcher toko Akulaku", 13, MUTED, Typeface.NORMAL);
        wrap.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        wrap.addView(spacer(adaptive(12, 18, 26)));

        LinearLayout loginCard = card();
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.gravity = Gravity.CENTER_HORIZONTAL;
        wrap.addView(loginCard, cardLp);

        usernameInput = input("Username / email Tracsh", false);
        passwordInput = input("Password Tracsh", true);
        loginCard.addView(usernameInput, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams passLp = new LinearLayout.LayoutParams(-1, -2);
        passLp.setMargins(0, dp(8), 0, 0);
        loginCard.addView(passwordInput, passLp);

        Button login = primaryButton("Masuk Tracsh");
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(dp(adaptive(190, 220, 240)), -2);
        btnLp.gravity = Gravity.CENTER_HORIZONTAL;
        btnLp.setMargins(0, dp(14), 0, 0);
        loginCard.addView(login, btnLp);

        progress = new ProgressBar(this);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, -2);
        progressLp.setMargins(0, dp(6), 0, 0);
        loginCard.addView(progress, progressLp);

        statusText = centerText("Password tidak disimpan di aplikasi", 12, MUTED, Typeface.NORMAL);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.setMargins(0, dp(8), 0, 0);
        loginCard.addView(statusText, statusLp);
        login.setOnClickListener(v -> doLogin());

        wrap.addView(flexSpacer(screenMode == 0 ? 0.45f : 1.0f));
        TextView footer = centerText("PT FahmyID Digital Group", 11, MUTED, Typeface.NORMAL);
        wrap.addView(footer, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showMerchantScreen() {
        currentScreen = "merchant";
        baseRoot();

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        applyInsetsAwarePadding(header, 16, adaptive(12, 14, 16), 16, 12, true, false);
        header.setBackgroundColor(Color.WHITE);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text("Pilih Toko", adaptive(24, 26, 28), TEXT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(-1, -2));
        statusText = text("Session Tracsh aktif. Memuat toko...", adaptive(14, 14, 15), MUTED, Typeface.NORMAL);
        statusText.setPadding(0, dp(2), 0, 0);
        header.addView(statusText, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        merchantListView = new LinearLayout(this);
        merchantListView.setOrientation(LinearLayout.VERTICAL);
        merchantListView.setPadding(dp(16), dp(12), dp(16), dp(8));
        scroll.addView(merchantListView);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bar = bottomBar();
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout searchRow = horizontalRow();
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView searchIcon = centerText("⌕", 20, MUTED, Typeface.BOLD);
        searchIcon.setBackground(borderedBg(Color.rgb(248, 250, 252), 12, LINE));
        searchRow.addView(searchIcon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        searchInput = input("Cari toko, email, atau grup", false);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        searchLp.setMargins(dp(8), 0, 0, 0);
        searchRow.addView(searchInput, searchLp);
        bar.addView(searchRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout bottomRow = horizontalRow();
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(0, dp(6), 0, 0);
        bar.addView(bottomRow, rowLp);
        refreshButton = secondaryButton("Refresh");
        clearSearchButton = secondaryButton("Clear");
        Button logoutButton = secondaryButton("Logout");
        bottomRow.addView(refreshButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        clearLp.setMargins(dp(8), 0, 0, 0);
        bottomRow.addView(clearSearchButton, clearLp);
        LinearLayout.LayoutParams logoutLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        logoutLp.setMargins(dp(8), 0, 0, 0);
        bottomRow.addView(logoutButton, logoutLp);

        refreshButton.setOnClickListener(v -> fetchMerchants());
        clearSearchButton.setOnClickListener(v -> searchInput.setText(""));
        logoutButton.setOnClickListener(v -> logoutSession());
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence value, int start, int before, int count) { renderMerchantList(value.toString()); }
            public void afterTextChanged(Editable editable) {}
        });

        progress = new ProgressBar(this);
        showListLoading("Memuat daftar toko...");
        fetchMerchants();
    }

    private void showListLoading(String message) {
        if (merchantListView == null) return;
        merchantListView.removeAllViews();
        if (statusText != null) statusText.setText(message);
        progress = new ProgressBar(this);
        merchantListView.addView(progress, new LinearLayout.LayoutParams(-1, -2));
    }

    private void setControlsEnabled(boolean enabled) {
        if (refreshButton != null) refreshButton.setEnabled(enabled);
        if (clearSearchButton != null) clearSearchButton.setEnabled(enabled);
        if (searchInput != null) searchInput.setEnabled(enabled);
    }

    private void renderMerchantList(String query) {
        if (merchantListView == null) return;
        merchantListView.removeAllViews();
        String q = (query == null ? "" : query).toLowerCase().trim();
        int shown = 0;
        for (Merchant m : merchants) {
            String hay = (m.title + " " + m.email + " " + m.groupTitle + " " + m.employeeName).toLowerCase();
            if (!q.isEmpty() && !hay.contains(q)) continue;
            shown++;
            LinearLayout c = storeCard();
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, 0, 0, dp(12));
            merchantListView.addView(c, lp);

            LinearLayout row = horizontalRow();
            row.setGravity(Gravity.CENTER_VERTICAL);
            c.addView(row, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            TextView storeTitle = text(m.title.isEmpty() ? "Toko tanpa nama" : m.title, 16, TEXT, Typeface.BOLD);
            storeTitle.setMaxLines(2);
            storeTitle.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(storeTitle);
            if (!m.email.isEmpty()) {
                TextView email = text(maskEmail(m.email), 12, MUTED, Typeface.NORMAL);
                email.setPadding(0, dp(2), 0, 0);
                info.addView(email);
            }
            String meta = joinNonEmpty(m.groupTitle, m.employeeName);
            if (!meta.isEmpty()) {
                TextView metaText = text(meta, 12, MUTED, Typeface.NORMAL);
                metaText.setPadding(0, dp(1), 0, 0);
                info.addView(metaText);
            }
            TextView state = text(m.hasCookie() ? "Siap masuk Akulaku" : "Cookie diambil saat masuk", 12, m.hasCookie() ? SUCCESS : DANGER, Typeface.BOLD);
            state.setPadding(0, dp(4), 0, 0);
            info.addView(state);

            Button open = primaryButton("Masuk");
            LinearLayout.LayoutParams openLp = new LinearLayout.LayoutParams(dp(adaptive(96, 104, 112)), dp(48));
            openLp.setMargins(dp(10), 0, 0, 0);
            row.addView(open, openLp);
            open.setOnClickListener(v -> openMerchant(m, open));
        }
        statusText.setText(shown + " toko tampil dari " + merchants.size() + " toko");
        if (shown == 0) {
            TextView empty = centerText(q.isEmpty() ? "Belum ada toko tampil" : "Toko tidak ditemukan", 14, MUTED, Typeface.NORMAL);
            empty.setPadding(0, dp(24), 0, dp(24));
            merchantListView.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }
        if (progress != null) progress.setVisibility(View.GONE);
        setControlsEnabled(true);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showAkulakuScreen() {
        currentScreen = "akulaku";
        baseRoot();

        FrameLayout webWrap = new FrameLayout(this);
        applyInsetsAwarePadding(webWrap, 0, 0, 0, 0, true, false, true);
        root.addView(webWrap, new LinearLayout.LayoutParams(-1, 0, 1));

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);
        settings.setDefaultZoom(WebSettings.ZoomDensity.MEDIUM);
        settings.setUserAgentString(settings.getUserAgentString() + " MangprangSwitcherApk/0.3.6");
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);
        webView.setInitialScale(webScale);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                boolean blocked = !isAllowedWebUrl(request.getUrl().toString());
                if (blocked) showWebError("Blokir URL di luar Akulaku.");
                return blocked;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                boolean blocked = !isAllowedWebUrl(url);
                if (blocked) showWebError("Blokir URL di luar Akulaku.");
                return blocked;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (isAllowedWebUrl(request.getUrl().toString())) return super.shouldInterceptRequest(view, request);
                return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                if (!isAllowedWebUrl(url)) { view.stopLoading(); showWebError("Blokir URL di luar Akulaku."); return; }
                webNavigationToken++;
                mainHandler.removeCallbacks(webLoadTimeout);
                mainHandler.postDelayed(webLoadTimeout, WEBVIEW_TIMEOUT_MS);
                setWebLoading(true, "Memuat Akulaku...");
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!isAllowedWebUrl(url)) return;
                mainHandler.removeCallbacks(webLoadTimeout);
                setWebLoading(false, "Toko aktif: " + activeMerchantTitle);
                injectAkulakuDesktopFit(view);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    mainHandler.removeCallbacks(webLoadTimeout);
                    showWebError("Gagal memuat Akulaku. Periksa koneksi lalu tekan Reload.");
                }
                super.onReceivedError(view, request, error);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showWebError("Koneksi aman gagal. Reload setelah jaringan tepercaya tersedia.");
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webWrap.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bar = bottomBar();
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        webStatusText = text("Menyiapkan Akulaku...", 12, MUTED, Typeface.NORMAL);
        webStatusText.setPadding(0, 0, 0, dp(4));
        bar.addView(webStatusText, new LinearLayout.LayoutParams(-1, -2));
        webProgress = new ProgressBar(this);
        webProgress.setVisibility(View.GONE);
        bar.addView(webProgress, new LinearLayout.LayoutParams(-1, dp(3)));
        LinearLayout row1 = horizontalRow();
        Button back = secondaryButton("< Toko");
        Button home = primaryButton("Home");
        Button reload = secondaryButton("Reload");
        row1.addView(back, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams homeLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        homeLp.setMargins(dp(8), 0, dp(8), 0);
        row1.addView(home, homeLp);
        row1.addView(reload, new LinearLayout.LayoutParams(0, dp(48), 1));
        bar.addView(row1, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row2 = horizontalRow();
        LinearLayout.LayoutParams row2Lp = new LinearLayout.LayoutParams(-1, -2);
        row2Lp.setMargins(0, dp(6), 0, 0);
        bar.addView(row2, row2Lp);
        Button zoomOut = compactButton("−");
        Button fit = compactButton("Fit");
        Button zoomIn = compactButton("+");
        row2.addView(zoomOut, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams fitLp = new LinearLayout.LayoutParams(0, dp(40), 1);
        fitLp.setMargins(dp(8), 0, dp(8), 0);
        row2.addView(fit, fitLp);
        row2.addView(zoomIn, new LinearLayout.LayoutParams(0, dp(40), 1));

        back.setOnClickListener(v -> showMerchantScreen());
        home.setOnClickListener(v -> loadAkulakuUrl(AKULAKU_VENDOR_URL));
        reload.setOnClickListener(v -> { if (webView != null) loadAkulakuUrl(webView.getUrl()); });
        zoomOut.setOnClickListener(v -> setWebScale(webScale - 10));
        fit.setOnClickListener(v -> setWebScale(100));
        zoomIn.setOnClickListener(v -> setWebScale(webScale + 10));
    }

    private boolean isAllowedWebUrl(String rawUrl) {
        if (rawUrl == null) return false;
        try {
            Uri uri = Uri.parse(rawUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                && "ec-vendor.akulaku.com".equalsIgnoreCase(uri.getHost());
        } catch (Exception e) { return false; }
    }

    private void setWebLoading(boolean loading, String message) {
        if (webProgress != null) webProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (webStatusText != null) webStatusText.setText(message);
    }

    private void showWebError(String message) {
        mainHandler.removeCallbacks(webLoadTimeout);
        setWebLoading(false, message);
    }

    private void loadAkulakuUrl(String url) {
        if (webView == null) return;
        String target = isAllowedWebUrl(url) ? url : AKULAKU_VENDOR_URL;
        webNavigationToken++;
        mainHandler.removeCallbacks(webLoadTimeout);
        setWebLoading(true, "Memuat Akulaku...");
        webView.loadUrl(target);
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
            "if(head){var m=document.querySelector('meta[name=viewport]')||document.createElement('meta');m.id='mangprang-viewport';m.name='viewport';m.content='width=device-width, initial-scale=1.0, minimum-scale=0.5, maximum-scale=3.0, user-scalable=yes, viewport-fit=cover';if(!m.parentNode)head.appendChild(m);}" +
            "if(head&&!document.getElementById('mangprang-style')){var s=document.createElement('style');s.id='mangprang-style';s.innerHTML='html,body{width:100%!important;min-height:100%!important;height:auto!important;overflow:auto!important;-webkit-text-size-adjust:100%!important;} #app,.app,.ant-layout,.el-container{max-width:100vw!important;} table,.ant-table,.el-table{font-size:12px!important;max-width:100%!important;} input,button,select,textarea{font-size:13px!important;} img,video{max-width:100%!important;}';head.appendChild(s);}" +
            "})();";
        view.evaluateJavascript(js, null);
    }

    private void doLogin() {
        String username = usernameInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (username.isEmpty() || password.isEmpty()) { statusText.setText("Username dan password wajib diisi."); return; }
        progress.setVisibility(View.VISIBLE);
        statusText.setText("Login ke Tracsh...");
        final int generation = requestGeneration.get();
        new Thread(() -> {
            Exception last = null;
            String csrf = "";
            try {
                HttpURLConnection preflight = open(TRACSH_LOGIN_PAGE, "GET", null);
                try {
                    String loginHtml = readResponse(preflight);
                    saveCookies(preflight);
                    csrf = extractCsrf(loginHtml);
                } finally { closeConnection(preflight); }
            } catch (Exception e) { last = e; }

            if (!isRequestCurrent(generation)) return;
            try {
                String body = encode("username", username) + "&" + encode("password", password);
                if (!csrf.isEmpty()) body += "&" + encode("csrfToken", csrf);
                HttpURLConnection conn = open(TRACSH_BASE_URL + "/procAuth/signIn", "POST", "application/x-www-form-urlencoded");
                try {
                    try (OutputStream os = conn.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
                    int code = conn.getResponseCode();
                    String text = readResponse(conn);
                    saveCookies(conn);
                    String location = conn.getHeaderField("Location");
                    boolean success = code >= 300 && code < 400 && location != null && !location.contains("/auth/signIn");
                    if (!success) success = code >= 200 && code < 300 && !looksLikeLoginFailed(text, conn.getURL().toString());
                    if (success) {
                        loggedInToTracsh = true;
                        if (isRequestCurrent(generation)) runOnUiThread(() -> {
                            if (!isRequestCurrent(generation)) return;
                            if (passwordInput != null) passwordInput.setText("");
                            showMerchantScreen();
                        });
                        return;
                    }
                    last = new Exception("Login ditolak");
                } finally { closeConnection(conn); }
            } catch (Exception e) { last = e; }
            Exception finalLast = last;
            if (isRequestCurrent(generation)) runOnUiThread(() -> {
                if (!isRequestCurrent(generation)) return;
                if (progress != null) progress.setVisibility(View.GONE);
                if (statusText != null) statusText.setText("Login gagal. Cek username/password Tracsh.");
            });
        }).start();
    }

    private void fetchMerchants() {
        setControlsEnabled(false);
        showListLoading("Mengambil daftar toko dari Tracsh...");
        final int generation = requestGeneration.get();
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = open(MERCHANT_ENDPOINT, "GET", null);
                int code = conn.getResponseCode();
                String text = readResponse(conn);
                saveCookies(conn);
                if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
                List<Merchant> parsed = parseMerchants(text);
                if (!isRequestCurrent(generation)) return;
                merchants.clear();
                merchants.addAll(parsed);
                runOnUiThread(() -> { if (isRequestCurrent(generation)) renderMerchantList(searchInput != null ? searchInput.getText().toString() : ""); });
            } catch (Exception e) {
                if (!isRequestCurrent(generation)) return;
                runOnUiThread(() -> {
                    if (!isRequestCurrent(generation)) return;
                    if (progress != null) progress.setVisibility(View.GONE);
                    setControlsEnabled(true);
                    if (statusText != null) statusText.setText("Gagal memuat toko. Periksa koneksi lalu tekan Refresh.");
                    if (merchantListView != null) {
                        merchantListView.removeAllViews();
                        TextView err = centerText("Gagal memuat toko. Tekan Refresh untuk mencoba lagi.", 14, MUTED, Typeface.NORMAL);
                        err.setPadding(0, dp(24), 0, dp(24));
                        merchantListView.addView(err, new LinearLayout.LayoutParams(-1, -2));
                    }
                });
            } finally { closeConnection(conn); }
        }).start();
    }

    private void openMerchant(Merchant merchant, Button sourceButton) {
        if (merchant == null) return;
        new AlertDialog.Builder(this)
            .setTitle("Konfirmasi toko")
            .setMessage("Masuk ke " + (merchant.title.isEmpty() ? "toko ini" : merchant.title) + "? Sesi Akulaku saat ini akan dibersihkan.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Masuk", (dialog, which) -> switchMerchant(merchant, sourceButton))
            .show();
    }

    private void switchMerchant(Merchant merchant, Button sourceButton) {
        if (enteringStore) return;
        enteringStore = true;
        if (sourceButton != null) { sourceButton.setEnabled(false); sourceButton.setText("Masuk..."); }
        if (progress != null) progress.setVisibility(View.VISIBLE);
        if (statusText != null) statusText.setText("Menyiapkan login Akulaku untuk " + merchant.title + "...");
        final int generation = requestGeneration.get();
        new Thread(() -> {
            try {
                String cookie = fetchMerchantCookie(merchant);
                if (cookie == null || cookie.trim().isEmpty()) throw new Exception("Cookie toko belum tersedia dari Tracsh.");
                if (!isRequestCurrent(generation)) return;
                String finalCookie = cookie;
                runOnUiThread(() -> prepareIsolatedCookies(finalCookie, () -> {
                    if (!isRequestCurrent(generation)) return;
                    enteringStore = false;
                    activeMerchantTitle = merchant.title;
                    if (sourceButton != null) { sourceButton.setEnabled(true); sourceButton.setText("Masuk"); }
                    showAkulakuScreen();
                    loadAkulakuUrl(AKULAKU_VENDOR_URL);
                }, () -> {
                    enteringStore = false;
                    if (sourceButton != null) { sourceButton.setEnabled(true); sourceButton.setText("Masuk"); }
                    if (progress != null) progress.setVisibility(View.GONE);
                    if (statusText != null) statusText.setText("Cookie toko gagal dipasang. Coba masuk lagi.");
                }));
            } catch (Exception e) {
                if (!isRequestCurrent(generation)) return;
                runOnUiThread(() -> {
                    if (!isRequestCurrent(generation)) return;
                    enteringStore = false;
                    if (sourceButton != null) { sourceButton.setEnabled(true); sourceButton.setText("Masuk"); }
                    if (progress != null) progress.setVisibility(View.GONE);
                    if (statusText != null) statusText.setText("Belum bisa masuk toko. Cek koneksi atau status cookie, lalu coba lagi.");
                });
            }
        }).start();
    }

    private String fetchMerchantCookie(Merchant merchant) throws Exception {
        if (merchant == null) return "";
        JSONObject body = new JSONObject();
        if (!merchant.merchantId.isEmpty()) body.put("merchantId", merchant.merchantId);
        if (!merchant.id.isEmpty()) {
            body.put("id", merchant.id);
            body.put("tracsh_row_id", merchant.id);
        }
        if (body.length() == 0) return "";
        HttpURLConnection conn = open(MERCHANT_COOKIE_ENDPOINT, "POST", "application/json");
        try {
            try (OutputStream os = conn.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = conn.getResponseCode();
            saveCookies(conn);
            if (code < 200 || code >= 300) return "";
            JSONObject data = new JSONObject(readResponse(conn));
            String responseMerchantId = data.optString("merchantId", "").trim();
            String responseRowId = data.optString("tracshRowId", "").trim();
            boolean merchantMatches = responseMerchantId.isEmpty() || merchant.merchantId.isEmpty()
                || responseMerchantId.equals(merchant.merchantId);
            boolean rowMatches = responseRowId.isEmpty() || merchant.id.isEmpty()
                || responseRowId.equals(merchant.id);
            if (!merchantMatches || !rowMatches || !"success".equalsIgnoreCase(data.optString("status", "success"))) return "";
            return data.optString("cookie", "").trim();
        } finally { closeConnection(conn); }
    }

    private HttpURLConnection open(String url, String method, String contentType) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(NETWORK_TIMEOUT_MS);
        conn.setReadTimeout(NETWORK_TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json, text/html, text/plain, */*");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 MangprangSwitcherApk/0.3.6");
        String tracshCookie = tracshCookieHeader();
        if (tracshCookie != null && !tracshCookie.trim().isEmpty()) conn.setRequestProperty("Cookie", tracshCookie);
        if (contentType != null) { conn.setRequestProperty("Content-Type", contentType); conn.setDoOutput(true); }
        activeConnections.add(conn);
        return conn;
    }

    private void closeConnection(HttpURLConnection conn) {
        if (conn != null) { activeConnections.remove(conn); conn.disconnect(); }
    }

    private boolean isRequestCurrent(int generation) {
        return !destroyed && generation == requestGeneration.get();
    }

    private void saveCookies(HttpURLConnection conn) {
        Map<String, List<String>> headers = conn.getHeaderFields();
        if (headers == null) return;
        List<String> values = headers.get("Set-Cookie");
        if (values == null) values = headers.get("set-cookie");
        if (values == null) return;
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                String[] pair = value.trim().split("=", 2);
                if (pair.length == 2 && COOKIE_NAME.matcher(pair[0].trim()).matches()
                    && !COOKIE_ATTRIBUTES.contains(pair[0].trim().toLowerCase(Locale.US))) {
                    String cookieValue = pair[1].split(";", 2)[0].trim();
                    if (!cookieValue.isEmpty()) tracshCookies.put(pair[0].trim(), pair[0].trim() + "=" + cookieValue);
                }
                String cookie = value;
                String domain = cookieAttribute(cookie, "domain");
                String url = domain != null && domain.toLowerCase(Locale.US).contains("tracsh.com")
                    ? "https://" + domain.replaceFirst("^\\.", "") : TRACSH_BASE_URL;
                setCookieSync(url, cookie);
            }
        }
    }

    private String cookieAttribute(String header, String name) {
        if (header == null) return null;
        for (String part : header.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && name.equalsIgnoreCase(pair[0].trim())) return pair[1].trim();
        }
        return null;
    }

    private String tracshCookieHeader() {
        synchronized (tracshCookies) {
            return TextUtils.join("; ", tracshCookies.values());
        }
    }

    private void setCookieSync(String url, String header) {
        if (header == null || header.trim().isEmpty()) return;
        Runnable set = () -> {
            CookieManager.getInstance().setCookie(url, header, null);
            CookieManager.getInstance().flush();
        };
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) set.run();
        else runOnUiThread(set);
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

    private void setCookieHeaders(String url, List<String> headers, int index, Runnable completion, Runnable failure) {
        if (index >= headers.size()) {
            CookieManager.getInstance().flush();
            if (completion != null) completion.run();
            return;
        }
        CookieManager.getInstance().setCookie(url, headers.get(index), ok -> {
            if (!ok) {
                if (failure != null) failure.run();
                return;
            }
            setCookieHeaders(url, headers, index + 1, completion, failure);
        });
    }

    private void prepareIsolatedCookies(String cookieHeader, Runnable completion, Runnable failure) {
        clearAllCookies(() -> applyAkulakuCookie(cookieHeader, completion, failure));
    }

    private void applyAkulakuCookie(String cookieHeader, Runnable completion, Runnable failure) {
        List<String> safeCookies = new ArrayList<>();
        for (String part : cookieHeader.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2 || !COOKIE_NAME.matcher(pair[0].trim()).matches() || COOKIE_ATTRIBUTES.contains(pair[0].trim().toLowerCase(Locale.US))) continue;
            String value = pair[1].trim();
            if (value.isEmpty()) continue;
            safeCookies.add(pair[0].trim() + '=' + value + "; Path=/; Secure");
        }
        if (safeCookies.isEmpty()) { if (failure != null) failure.run(); return; }
        setCookieHeaders(AKULAKU_COOKIE_URL, safeCookies, 0, completion, failure);
    }

    private void clearAllCookies(Runnable completion) {
        Runnable clear = () -> CookieManager.getInstance().removeAllCookies(ignored -> {
            CookieManager.getInstance().flush();
            if (completion != null) completion.run();
        });
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) clear.run();
        else runOnUiThread(clear);
    }

    private void logoutSession() {
        requestGeneration.incrementAndGet();
        for (HttpURLConnection conn : new ArrayList<>(activeConnections)) closeConnection(conn);
        clearAllCookies(() -> {
            loggedInToTracsh = false;
            tracshCookies.clear();
            merchants.clear();
            activeMerchantTitle = "";
            if (usernameInput != null) usernameInput.setText("");
            if (passwordInput != null) passwordInput.setText("");
            showLoginScreen();
        });
    }

    private void destroyWebView() {
        if (webView == null) return;
        webView.stopLoading();
        webView.setWebViewClient(null);
        webView.setWebChromeClient(null);
        webView.destroy();
        webView = null;
        webStatusText = null;
        webProgress = null;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        requestGeneration.incrementAndGet();
        mainHandler.removeCallbacks(webLoadTimeout);
        for (HttpURLConnection conn : new ArrayList<>(activeConnections)) closeConnection(conn);
        destroyWebView();
        super.onDestroy();
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
        if ("akulaku".equals(currentScreen)) { requestGeneration.incrementAndGet(); mainHandler.removeCallbacks(webLoadTimeout); destroyWebView(); showMerchantScreen(); return; }
        if ("merchant".equals(currentScreen)) {
            if (loggedInToTracsh) {
                new AlertDialog.Builder(this)
                    .setTitle("Keluar dari Tracsh?")
                    .setMessage("Keluar akan menghapus sesi Tracsh dan Akulaku dari perangkat ini.")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Keluar", (dialog, which) -> logoutSession())
                    .show();
                return;
            }
            showLoginScreen();
            return;
        }
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
            // Cookie must come only from the session-scoped endpoint, never the store list.
            m.cookie = "";
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
