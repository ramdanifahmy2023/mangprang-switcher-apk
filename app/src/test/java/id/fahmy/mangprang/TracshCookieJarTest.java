package id.fahmy.mangprang;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class TracshCookieJarTest {
    @Test
    public void sendsCookieOnlyToMatchingPath() throws Exception {
        TracshCookieJar jar = new TracshCookieJar();
        URL login = new URL("https://tracsh.com/auth/signIn");
        jar.store(login, headers("Set-Cookie", "session=abc; Path=/apiv; Secure; HttpOnly"));

        assertTrue(jar.header(new URL("https://tracsh.com/apiv/merchant")).contains("session=abc"));
        assertFalse(jar.header(new URL("https://tracsh.com/api/getMerchantCookie")).contains("session=abc"));
    }

    @Test
    public void clearRemovesSession() throws Exception {
        TracshCookieJar jar = new TracshCookieJar();
        URL login = new URL("https://tracsh.com/auth/signIn");
        jar.store(login, headers("Set-Cookie", "session=abc; Path=/; Secure; HttpOnly"));
        assertTrue(jar.header(new URL("https://tracsh.com/apiv/merchant")).contains("session=abc"));

        jar.clear();
        assertFalse(jar.header(new URL("https://tracsh.com/apiv/merchant")).contains("session=abc"));
    }

    @Test
    public void rejectsCookieForForeignDomain() throws Exception {
        TracshCookieJar jar = new TracshCookieJar();
        URL login = new URL("https://tracsh.com/auth/signIn");
        jar.store(login, headers("Set-Cookie", "session=abc; Domain=evil.example; Path=/; Secure"));

        assertFalse(jar.header(new URL("https://tracsh.com/apiv/merchant")).contains("session=abc"));
    }

    private static Map<String, List<String>> headers(String name, String value) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put(name, Collections.singletonList(value));
        return headers;
    }
}
