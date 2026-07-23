package id.fahmy.mangprang;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

final class LoginRuntime {
    private LoginRuntime() {}

    static String readBody(InputStream raw, String contentEncoding, int maxBytes) throws Exception {
        if (raw == null) return "";
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be positive");
        InputStream decoded = "gzip".equalsIgnoreCase(contentEncoding) ? new GZIPInputStream(raw) : raw;
        try (InputStream is = decoded; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = is.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new ResponseTooLargeException();
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    static final class ResponseTooLargeException extends Exception {
        ResponseTooLargeException() {
            super("Response terlalu besar");
        }
    }
}
