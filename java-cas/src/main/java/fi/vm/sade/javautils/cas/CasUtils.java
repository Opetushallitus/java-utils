package fi.vm.sade.javautils.cas;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.Response;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class CasUtils {
    public static Cookie getCookie(Response response, String loginUrl, String cookieName) {
        URI loginUri = URI.create(loginUrl);

        List<Cookie> cookieList = new ArrayList<>();
        List<String> cookieStrings = response.headers("set-cookie");
        for (String cookieString : cookieStrings) {
            cookieList.add(Cookie.parse(HttpUrl.parse(loginUrl), cookieString));
        }
        return cookieList.stream().filter(cookie -> loginUri.getPath().startsWith(cookie.path()) && cookieName.equals(cookie.name()))
                .findAny()
                .orElseThrow(() -> new IllegalStateException(String.format(
                        "%s %d: Failed to establish session. No cookie %s set. Headers: %s",
                        loginUrl,
                        response.code(),
                        cookieName,
                        response.headers()
                )));
    }

    public static String getHost(String url) throws MalformedURLException {
        String protocol = new URL(url).getProtocol();
        String host = new URL(url).getHost();
        int port = new URL(url).getPort();

        if (port == -1) {
            return String.format("%s://%s", protocol, host);
        } else {
            return String.format("%s://%s:%d", protocol, host, port);
        }
    }
}