package fi.vm.sade.javautils.cas;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.Response;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class CasUtils {

    public static Cookie getCookie(Response response, ServiceTicket serviceTicket, String cookieName) {
        URI loginUri = URI.create(serviceTicket.getLoginUrl());
        List<Cookie> cookieList = new ArrayList<>();
        List<String> cookieStrings = response.headers("Set-cookie");
        for (String cookieString : cookieStrings) {
            cookieList.add(Cookie.parse(HttpUrl.parse(serviceTicket.getLoginUrl()), cookieString));
        }
        return cookieList.stream().filter(cookie -> loginUri.getPath().startsWith(cookie.path()) && cookieName.equals(cookie.name()))
                .findAny()
                .orElseThrow(() -> new IllegalStateException(String.format(
                        "%s %d: Failed to establish session. No cookie %s set. Headers: %s",
                        loginUri,
                        response.code(),
                        cookieName,
                        response.headers()
                )));
    }
}
