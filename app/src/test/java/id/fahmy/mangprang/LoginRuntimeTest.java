package id.fahmy.mangprang;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

public class LoginRuntimeTest {
    @Test
    public void readsUtf8BodyWithinLimit() throws Exception {
        String expected = "daftar toko aman";
        String actual = LoginRuntime.readBody(
            new ByteArrayInputStream(expected.getBytes(StandardCharsets.UTF_8)),
            null,
            1024
        );
        assertEquals(expected, actual);
    }

    @Test
    public void decodesGzipBeforeApplyingLimit() throws Exception {
        String expected = "toko-" + repeat("aman", 100);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(expected.getBytes(StandardCharsets.UTF_8));
        }
        String actual = LoginRuntime.readBody(
            new ByteArrayInputStream(compressed.toByteArray()),
            "gzip",
            expected.getBytes(StandardCharsets.UTF_8).length
        );
        assertEquals(expected, actual);
    }

    @Test
    public void rejectsBodyAboveDecompressedLimit() {
        assertThrows(
            LoginRuntime.ResponseTooLargeException.class,
            () -> LoginRuntime.readBody(new ByteArrayInputStream(new byte[33]), null, 32)
        );
    }

    @Test
    public void endlessBodyStillTerminatesAtLimit() {
        InputStream endless = new InputStream() {
            @Override
            public int read() {
                return 'x';
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                int count = Math.min(length, 8);
                for (int i = 0; i < count; i++) buffer[offset + i] = 'x';
                return count;
            }
        };
        assertThrows(
            LoginRuntime.ResponseTooLargeException.class,
            () -> LoginRuntime.readBody(endless, null, 32)
        );
    }

    private static String repeat(String value, int count) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }
}
