package id.fahmy.mangprang;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class TracshCookieJar {
    private final CookieManager manager = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);

    synchronized void clear() {
        manager.getCookieStore().removeAll();
    }

    synchronized void store(URL source, Map<String, List<String>> responseHeaders) throws Exception {
        if (source == null || responseHeaders == null) return;
        manager.put(source.toURI(), responseHeaders);
    }

    synchronized String header(URL target) throws Exception {
        Map<String, List<String>> headers = manager.get(target.toURI(), Collections.emptyMap());
        List<String> values = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && "cookie".equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
                values.addAll(entry.getValue());
            }
        }
        return join(values);
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (out.length() > 0) out.append("; ");
            out.append(value.trim());
        }
        return out.toString();
    }
}
