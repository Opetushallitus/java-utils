package fi.vm.sade.javautils.cas;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.CookieManager;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ApplicationSession {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationSession.class);

    private static final String CSRF_VALUE = "CSRF";

    private final OkHttpClient client;
    private final CookieManager cookieManager;
    private final String callerId;
    private final Duration authenticationTimeout;
    private final CasSession casSession;
    private final String service;
    private final String cookieName;
    private SessionToken sessionToken;

    public ApplicationSession(OkHttpClient client,
                              CookieManager cookieManager,
                              String callerId,
                              Duration authenticationTimeout,
                              CasSession casSession,
                              String service,
                              String cookieName) {
        this.cookieManager = cookieManager;
        this.client = client;
        this.callerId = callerId;
        this.authenticationTimeout = authenticationTimeout;
        this.casSession = casSession;
        this.service = service;
        this.cookieName = cookieName;
        this.sessionToken = null;
    }

    public CompletableFuture<SessionToken> getSessionToken() {
        SessionToken currentSessionToken = this.sessionToken;
        if (currentSessionToken == null) {
            synchronized (this) {
                ServiceTicket serviceTicket = this.casSession.getServiceTicket(this.service);
                this.sessionToken = this.requestSession(serviceTicket);
                return CompletableFuture.completedFuture(this.sessionToken);
            }
        }
        return CompletableFuture.completedFuture(currentSessionToken);
    }

    private SessionToken requestSession(ServiceTicket serviceTicket) {
        Request request = new Request.Builder()
                .url(serviceTicket.getLoginUrl())
                .header("Caller-Id", this.callerId)
                .header("CSRF", CSRF_VALUE)
                .header("Cookie", String.format("CSRF=%s;", CSRF_VALUE))
                .header("Connection", "close")
                //TODO.timeout(this.authenticationTimeout)
                .build();

        try {
            Response response = this.client.newCall(request).execute();
            return new SessionToken(serviceTicket, getCookie(response, serviceTicket));
        } catch (Exception e) {
            throw new IllegalStateException(String.format(
                    "%s: Failed to establish session",
                    request.url()
            ), e);
        }
    }

    private List<Cookie> resolveCookies(Response response, ServiceTicket serviceTicket) {
        List<String> cookieStrings = response.headers("Set-cookie");
        List<Cookie> cookieList = new ArrayList<>();
        for (String cookieString : cookieStrings) {
            cookieList.add(Cookie.parse(HttpUrl.parse(serviceTicket.getLoginUrl()), cookieString));
        }
        return cookieList;
    }

    private Cookie getCookie(Response response, ServiceTicket serviceTicket) {
        URI loginUrl = URI.create(serviceTicket.getLoginUrl());
        List<Cookie> cookieList = resolveCookies(response, serviceTicket);
        return cookieList.stream().filter(cookie -> loginUrl.getPath().startsWith(cookie.path()) && this.cookieName.equals(cookie.name()))
                .findAny()
                .orElseThrow(() -> new IllegalStateException(String.format(
                        "%s %d: Failed to establish session. No cookie %s set. Headers: %s",
                        loginUrl,
                        response.code(),
                        this.cookieName,
                        response.headers()
                )));
    }
}
