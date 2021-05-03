package fi.vm.sade.javautils.cas;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

public class ApplicationSession {
    private static final String CSRF_VALUE = "CSRF";

    private final AsyncHttpClient client;
    private final CookieManager cookieManager;
    private final String callerId;
    private final int authenticationTimeout;
    private final CasSession casSession;
    private final String service;
    private final String cookieName;
    private CompletableFuture<SessionToken> sessionToken;

    public ApplicationSession(AsyncHttpClient client,
                              CookieManager cookieManager,
                              String callerId,
                              int authenticationTimeout,
                              CasSession casSession,
                              String service,
                              String cookieName) {
        this.client = client;
        this.cookieManager = cookieManager;
        this.callerId = callerId;
        this.authenticationTimeout = authenticationTimeout;
        this.casSession = casSession;
        this.service = service;
        this.cookieName = cookieName;
        this.sessionToken = CompletableFuture.failedFuture(new IllegalStateException("uninitialized"));
    }

    public CompletableFuture<SessionToken> getSessionToken() {
        CompletableFuture<SessionToken> currentSessionToken = this.sessionToken;
        if (currentSessionToken.isCompletedExceptionally()) {
            synchronized (this) {
                if (this.sessionToken.isCompletedExceptionally()) {
                    this.sessionToken = this.casSession.getServiceTicket(this.service).thenComposeAsync(this::requestSession);
                }
                currentSessionToken = this.sessionToken;
            }
        }
        return currentSessionToken;
    }

    public synchronized void invalidateSession(SessionToken sessionToken) {
        if (this.sessionToken.isCompletedExceptionally() || sessionToken.equals(this.sessionToken.getNow(null))) {
            this.sessionToken = CompletableFuture.failedFuture(new IllegalStateException("invalidated"));
        }
    }

    private CompletableFuture<SessionToken> requestSession(ServiceTicket serviceTicket) {
        Request request = new RequestBuilder()
                .setUrl(serviceTicket.getLoginUrl().toString())
                .setMethod("GET")
                .addHeader("Caller-Id", this.callerId)
                .addHeader("CSRF", CSRF_VALUE)
                .addHeader("Cookie", String.format("CSRF=%s;", CSRF_VALUE))
                .setRequestTimeout(this.authenticationTimeout)
                .build();
        return this.client.executeRequest(request).toCompletableFuture()
                .handle((response, e) -> {
                    if (e != null) {
                        throw new IllegalStateException(String.format(
                                "%s: Failed to establish session",
                                request.getUrl()
                        ), e);
                    }
                    return new SessionToken(serviceTicket, this.getCookie(response, serviceTicket));
                });
    }

    private HttpCookie getCookie(Response response, ServiceTicket serviceTicket) {
        URI loginUrl = serviceTicket.getLoginUrl();
        return this.cookieManager.getCookieStore().get(loginUrl).stream()
                .filter(cookie -> loginUrl.getPath().startsWith(cookie.getPath()) && this.cookieName.equals(cookie.getName()))
                .findAny()
                .orElseThrow(() -> new IllegalStateException(String.format(
                        "%s %d: Failed to establish session. No cookie %s set. Headers: %s",
                        response.getUri().toString(),
                        response.getStatusCode(),
                        this.cookieName,
                        response.getHeaders()
                )));
    }
}
