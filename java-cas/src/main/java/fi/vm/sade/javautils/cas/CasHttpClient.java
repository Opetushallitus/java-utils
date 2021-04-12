package fi.vm.sade.javautils.cas;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class CasHttpClient {
    private class SessionCookies {
        public final long created;
        public final String value;

        public SessionCookies(String token) {
            this.value = token;
            this.created = System.currentTimeMillis();
        }
    }

    private final String callerId;
    private final String service;
    private final String ticketsUrl;
    private final String username;
    private final String password;
    private final Duration sessionTimeout;
    private final AtomicReference<SessionCookies> TOKEN_STORE = new AtomicReference<>();
    private static final Logger logger = LoggerFactory.getLogger(CasHttpClient.class);

    private final OkHttpClient client;

    public CasHttpClient(OkHttpClient client, String callerId, String service, String ticketsUrl, String username, String password, Duration sessionTimeout) {
        this.callerId = callerId;
        this.client = client;
        this.service = service;
        this.ticketsUrl = ticketsUrl;
        this.username = username;
        this.password = password;
        this.sessionTimeout = sessionTimeout;
    }

    private boolean currentTokenCouldBeValid() {
        if (TOKEN_STORE.get().created > System.currentTimeMillis() - this.sessionTimeout.toMillis()) {
            return TOKEN_STORE.get() != null;
        }
        return false;
    }

    public CompletableFuture<Response> callToFuture(Call call) {
        final CompletableFuture<Response> r = new CompletableFuture<>();
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                r.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                r.complete(response);
            }
        });
        return r;
    }

    private CompletableFuture<Response> fetchCasSession() {
        RequestBody ticketGrantingTicketRequestBody = new FormBody.Builder()
                .add("username", URLEncoder.encode(this.username, StandardCharsets.UTF_8))
                .add("password", URLEncoder.encode(this.password, StandardCharsets.UTF_8))
                .build();

        Request requestTicketGrantingTicket = new Request.Builder()
                .url(this.ticketsUrl)
                .post(ticketGrantingTicketRequestBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Caller-Id", this.callerId)
                .addHeader("Cookie", String.format("CSRF=%s;", this.callerId))
                .header("Connection", "close")
                .build();

        return callToFuture(this.client.newCall(requestTicketGrantingTicket))
                .thenCompose((tgtResponse) -> {
                    if (tgtResponse.isSuccessful()) {
                        HttpUrl ticketGrantingTicketUrl = HttpUrl.get(tgtResponse.headers("Location").stream().findFirst()
                                .get());

                        RequestBody serviceTicketRequestBody = new FormBody.Builder()
                                .add("service", URLEncoder.encode(this.service, StandardCharsets.UTF_8))
                                .build();

                        Request requestServiceTicket = new Request.Builder()
                                .url(ticketGrantingTicketUrl)
                                .post(serviceTicketRequestBody)
                                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                                .addHeader("Caller-Id", this.callerId)
                                .addHeader("Cookie", String.format("CSRF=%s;", this.callerId))
                                .header("Connection", "close")
                                .build();
                        return callToFuture(this.client.newCall(requestServiceTicket)).thenCompose((stResponse) -> {
                            if (stResponse.isSuccessful()) {
                                return CompletableFuture.completedFuture(stResponse);
                            } else {
                                return CompletableFuture.failedFuture(new RuntimeException("Invalid service ticket from CAS"));
                            }
                        });
                    } else {
                        return CompletableFuture.failedFuture(new RuntimeException("Invalid ticket granting ticket from CAS"));
                    }
                });
    }


    public CompletableFuture<Response> call(Request request) {
        return call(request, 0);
    }

    private CompletableFuture<Response> call(final Request request, final int tryNumber) {
        if (currentTokenCouldBeValid()) {
            Request requestWithSessionCookie = new Request.Builder(request)
                    .addHeader("Set-Cookie", "TODO")
                    .build();
            return callToFuture(this.client.newCall(requestWithSessionCookie))
                    .thenCompose((response) -> {
                        boolean unauthorized = true;
                        boolean shouldGiveUp = tryNumber > 1;

                        if (response.code() == 302) {
                            return fetchCasSession().thenCompose((casResponse) -> {
                                String setSessionCookie = null;
                                try {
                                    setSessionCookie = CasUtils.getCookie(casResponse, new ServiceTicket(this.service, casResponse.body().string()), CasEnums.SESSIONCOOKIE_NAME).value();
                                    TOKEN_STORE.set(new SessionCookies(setSessionCookie));
                                    if (currentTokenCouldBeValid()) {
                                        return call(request, tryNumber);
                                    } else {
                                        return CompletableFuture.failedFuture(new RuntimeException("Invalid session token from CAS. Should check credentials!"));
                                    }
                                } catch (IOException e) {
                                    return CompletableFuture.failedFuture(new RuntimeException("Error getting cookie from response.", e));
                                }
                            });
                        }
                        if (response.code() == 401) {
                            return CompletableFuture.failedFuture(new RuntimeException("Error getting ticket from CAS."));
                        }


                        if (unauthorized && !shouldGiveUp) {
                            TOKEN_STORE.set(null);
                            return call(request, tryNumber + 1);
                        } else {
                            return CompletableFuture.completedFuture(response);
                        }
                    });
        } else {
            return fetchCasSession().thenCompose((response) -> {
                String setSessionCookie = null;
                try {
                    setSessionCookie = CasUtils.getCookie(response, new ServiceTicket(this.service, response.body().string()), CasEnums.SESSIONCOOKIE_NAME).value();
                    TOKEN_STORE.set(new SessionCookies(setSessionCookie));
                    if (currentTokenCouldBeValid()) {
                        return call(request, tryNumber);
                    } else {
                        return CompletableFuture.failedFuture(new RuntimeException("Invalid session token from CAS. Should check credentials!"));
                    }
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(new RuntimeException("Error getting cookie from response.", e));
                }
            });
        }
    }
}