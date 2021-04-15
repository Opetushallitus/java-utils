package fi.vm.sade.javautils.cas;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final String cookieName;
    private final String service;
    private final String ticketsUrl;
    private final String username;
    private final String password;
    private final Duration sessionTimeout;
    private final AtomicReference<SessionCookies> TOKEN_STORE = new AtomicReference<>();
    private static final Logger logger = LoggerFactory.getLogger(CasHttpClient.class);

    private final OkHttpClient client;

    public CasHttpClient(OkHttpClient client,
                         String callerId,
                         String cookieName,
                         String service,
                         String ticketsUrl,
                         String username,
                         String password,
                         Duration sessionTimeout) {
        this.client = client;
        this.callerId = callerId;
        this.cookieName = cookieName;
        this.service = service;
        this.ticketsUrl = ticketsUrl;
        this.username = username;
        this.password = password;
        this.sessionTimeout = sessionTimeout;
    }

    private boolean currentTokenCouldBeValid() {
        if (TOKEN_STORE.get() == null) {
            return false;
        } else if (TOKEN_STORE.get().created > System.currentTimeMillis() - this.sessionTimeout.toMillis()) {
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
        return fetchCasSession(0);
    }

//    TODO private Request buildRequest()

    private CompletableFuture<Response> fetchCasSession(final int tryNumber) {
        boolean shouldGiveUp = tryNumber > 1;
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

                        return callToFuture(this.client.newCall(requestServiceTicket))
                                .thenCompose((stResponse) -> {
                                    if (stResponse.isSuccessful()) {
                                        ServiceTicket serviceTicket = null;
                                        try {
                                            serviceTicket = new ServiceTicket(this.service, stResponse.body().string());
                                            Request sessionRequest = new Request.Builder()
                                                    .url(serviceTicket.getLoginUrl())
                                                    .header("Caller-Id", this.callerId)
                                                    .header("CSRF", this.callerId)
                                                    .header("Cookie", String.format("CSRF=%s;", this.callerId))
                                                    .header("Connection", "close")
                                                    .build();

                                            return callToFuture(this.client.newCall(sessionRequest))
                                                    .thenCompose((sessionResponse) -> {
                                                        if (sessionResponse.isSuccessful()) {
                                                            return CompletableFuture.completedFuture(sessionResponse);
                                                        } else {
                                                            if (!shouldGiveUp) {
                                                                logger.debug("Failed to fetch Session from CAS, retrying...");
                                                                return fetchCasSession(tryNumber + 1);
                                                            } else {
                                                                return CompletableFuture.failedFuture(new RuntimeException("Failed to fetch Session from CAS."));
                                                            }
                                                        }
                                                    });

                                        } catch (Exception e) {
                                            if (!shouldGiveUp) {
                                                return fetchCasSession(tryNumber + 1);
                                            } else {
                                                logger.debug("Failed to create Service ticket from CAS response, retrying...");
                                                return CompletableFuture.failedFuture(new RuntimeException("Failed to create Service ticket from CAS response, " + e));
                                            }
                                        }

                                    } else {
                                        if (!shouldGiveUp) {
                                            logger.debug("Invalid service ticket from CAS, retrying...");
                                            return fetchCasSession(tryNumber + 1);
                                        } else {
                                            return CompletableFuture.failedFuture(new RuntimeException("Invalid service ticket from CAS"));
                                        }
                                    }
                                });
                    } else {
                        if (!shouldGiveUp) {
                            logger.debug("Invalid ticket granting ticket from CAS, retrying...");
                            return fetchCasSession(tryNumber + 1);
                        } else {
                            return CompletableFuture.failedFuture(new RuntimeException("Invalid ticket granting ticket from CAS"));
                        }
                    }
                });
    }

    public CompletableFuture<Response> call(Request request) {
        return call(request, 0);
    }

    private CompletableFuture<Response> call(final Request request, final int tryNumber) {
        AtomicBoolean unauthorized = new AtomicBoolean(true);
        boolean shouldGiveUp = tryNumber > 1;
        if (currentTokenCouldBeValid()) {
            Request requestWithSessionCookie = new Request.Builder(request)
                    .addHeader("Caller-Id", this.callerId)
                    .addHeader("Cookie", String.format("CSRF=%s;", this.callerId))
                    .addHeader("Cookie", this.cookieName + "=" + TOKEN_STORE.get().value)
                    .build();

            return callToFuture(this.client.newCall(requestWithSessionCookie))
                    .thenCompose((response) -> {
                        if (response.code() == 200) {
                            unauthorized.set(false);
                        } else if (response.code() == 302) {
                            return fetchCasSession().thenCompose((casResponse) -> {
                                try {
                                    String setSessionCookie = CasUtils.getCookie(casResponse, new ServiceTicket(this.service, casResponse.body().string()), this.cookieName).value();
                                    TOKEN_STORE.set(new SessionCookies(setSessionCookie));
                                    if (currentTokenCouldBeValid()) {
                                        unauthorized.set(false);
                                        return call(request, tryNumber);
                                    } else {
                                        return CompletableFuture.failedFuture(new RuntimeException("Invalid session token from CAS. Should check credentials!"));
                                    }
                                } catch (IOException e) {
                                    return CompletableFuture.failedFuture(new RuntimeException("Error getting cookie from response.", e));
                                }
                            });
                        } else if (response.code() == 401) {
                            logger.debug("response code 401, fetching cas session");
                            return fetchCasSession().thenCompose((casResponse) -> {
                                try {
                                    String setSessionCookie = CasUtils.getCookie(casResponse, new ServiceTicket(this.service, casResponse.body().string()), this.cookieName).value();
                                    TOKEN_STORE.set(new SessionCookies(setSessionCookie));
                                    if (currentTokenCouldBeValid()) {
                                        unauthorized.set(false);
                                        return call(request, tryNumber);
                                    } else {
                                        return CompletableFuture.failedFuture(new RuntimeException("Invalid session token from CAS. Should check credentials!"));
                                    }
                                } catch (Exception e) {
                                    return CompletableFuture.failedFuture(new RuntimeException("Error getting cookie from response.", e));
                                }
                            });
                        }

                        if (unauthorized.get() && !shouldGiveUp) {
                            TOKEN_STORE.set(null);
                            return call(request, tryNumber + 1);
                        }
                        else {
                            return CompletableFuture.completedFuture(response);
                        }
                    });
        } else {
            logger.debug("current token not valid");
            return fetchCasSession().thenCompose((response) -> {
                try {
                    ServiceTicket serviceTicket = new ServiceTicket(this.service, response.body().string());
                    String setSessionCookie = CasUtils.getCookie(response, serviceTicket, this.cookieName).value();
                    TOKEN_STORE.set(new SessionCookies(setSessionCookie));
                    if (currentTokenCouldBeValid()) {
                        unauthorized.set(false);
                        return call(request, tryNumber + 1);
                    } else {
                        return CompletableFuture.failedFuture(new RuntimeException("Invalid session token from CAS. Should check credentials!"));
                    }
                } catch (Exception e) {
                    if (!shouldGiveUp) {
                        logger.debug("error fetching CAS session, retrying...");
                        return fetchCasSession(tryNumber + 1);
                    }
                    return CompletableFuture.failedFuture(new RuntimeException("Error getting cookie from response.", e));
                }
            });
        }
    }
}