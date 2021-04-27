package fi.vm.sade.javautils.cas;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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
    private final String casUrl;
    private final String securityUriSuffix;
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
                         String casUrl,
                         String securityUriSuffix,
                         String username,
                         String password,
                         Duration sessionTimeout) {
        this.client = client;
        this.callerId = callerId;
        this.cookieName = cookieName;
        this.service = service;
        this.securityUriSuffix = securityUriSuffix;
        this.casUrl = casUrl;
        this.username = username;
        this.password = password;
        this.sessionTimeout = sessionTimeout;
    }

    private boolean currentTokenCouldBeValid() {
        if (TOKEN_STORE.get() == null) {
            logger.info("Tokenstore is null, ticket not valid.");
            return false;
        } else if (TOKEN_STORE.get().created > System.currentTimeMillis() - this.sessionTimeout.toMillis()) {
            logger.info("Token valid.");
            return TOKEN_STORE.get() != null;
        }
        logger.info("Token not valid.");
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


    private CompletableFuture<Response> fetchCasSession(final int tryNumber) {
        boolean shouldGiveUp = tryNumber > 1;
        RequestBody ticketGrantingTicketRequestBody = new FormBody.Builder()
                .add("username", this.username)
                .add("password", this.password)
                .build();

        Request requestTicketGrantingTicket = new Request.Builder()
                .url(this.casUrl + "/v1/tickets")
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
                        RequestBody serviceTicketRequestBody = null;
                        try {
                            serviceTicketRequestBody = new FormBody.Builder()
                                    .add("service", CasUtils.getHost(this.casUrl) + this.service + "/" + this.securityUriSuffix)
                                    .build();
                        } catch (MalformedURLException e) {
                            throw new RuntimeException("Failed to parse host: " + e);
                        }
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
                                        try {
                                            final String sessionRequestUrl = CasUtils.getHost(this.casUrl) + this.service + "/?ticket=" + stResponse.body().string();
                                            Request sessionRequest = new Request.Builder()
                                                    .url(sessionRequestUrl)
                                                    .header("Caller-Id", this.callerId)
                                                    .header("CSRF", this.callerId)
                                                    .header("Cookie", String.format("CSRF=%s;", this.callerId))
                                                    .header("Connection", "close")
                                                    .build();
                                            return callToFuture(this.client.newCall(sessionRequest))
                                                    .thenCompose((sessionResponse) -> {
                                                        if (sessionResponse.isSuccessful()) {
                                                            String setSessionCookie = null;
                                                            setSessionCookie = CasUtils.getCookie(sessionResponse, sessionRequestUrl, this.cookieName).value();
                                                            TOKEN_STORE.set(new SessionCookies(setSessionCookie));
                                                            logger.info("Session Cookie set: " + setSessionCookie);
                                                            return CompletableFuture.completedFuture(sessionResponse);
                                                        } else {
                                                            if (!shouldGiveUp) {
                                                                logger.info("Failed to fetch Session from CAS, retrying...");
                                                                return fetchCasSession(tryNumber + 1);
                                                            } else {
                                                                return CompletableFuture.failedFuture(new RuntimeException("Failed to fetch Session from CAS."));
                                                            }
                                                        }
                                                    });

                                        } catch (Exception e) {
                                            if (!shouldGiveUp) {
                                                logger.error("Failed to create Service ticket from CAS response, retrying..." + e);
                                                return fetchCasSession(tryNumber + 1);
                                            } else {
                                                return CompletableFuture.failedFuture(new RuntimeException("Failed to create Service ticket from CAS response, " + e));
                                            }
                                        }
                                    } else {
                                        if (!shouldGiveUp) {
                                            logger.error("Invalid service ticket from CAS, retrying...");
                                            return fetchCasSession(tryNumber + 1);
                                        } else {
                                            return CompletableFuture.failedFuture(new RuntimeException("Invalid service ticket from CAS"));
                                        }
                                    }
                                });
                    } else {
                        if (!shouldGiveUp) {
                            logger.error("Invalid ticket granting ticket from CAS, retrying...");
                            return fetchCasSession(tryNumber + 1);
                        } else {
                            return CompletableFuture.failedFuture(new RuntimeException("Invalid ticket granting ticket from CAS"));
                        }
                    }
                });
    }

    public Response callBlocking(Request request) throws ExecutionException {
        try {
            return call(request, 0).get();
        } catch (Exception e) {
            e.printStackTrace();
            throw new ExecutionException("Blocking call to " + request.url() + " failed.", e);
        }
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
                            logger.info("RESPONSE CODE 200, ALL GOOD");
                            unauthorized.set(false);
                        } else if (response.code() == 302) {
                            logger.info("RESPONSE CODE 302, FETCHING...");
                            return fetchCasSession().thenCompose((casResponse) -> {
                                logger.info("got session, storing session cookie......");
                                //String setSessionCookie = CasUtils.getCookie(casResponse, new ServiceTicket(this.service, casResponse.body().string()), this.cookieName).value();
                                //TOKEN_STORE.set(new SessionCookies(setSessionCookie));
                                if (currentTokenCouldBeValid()) {
                                    unauthorized.set(false);
                                    return call(request, tryNumber);
                                } else {
                                    return CompletableFuture.failedFuture(new RuntimeException("Invalid session token from CAS. Should check credentials!"));
                                }
                            });
                        } else if (response.code() == 401) {
                            logger.info("response code 401, fetching cas session");
                            return fetchCasSession().thenCompose((casResponse) -> {
                                try {
                                    //String setSessionCookie = CasUtils.getCookie(casResponse, new ServiceTicket(this.service, casResponse.body().string()), this.cookieName).value();
                                    //TOKEN_STORE.set(new SessionCookies(setSessionCookie));
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
                        } else {
                            logger.info("Returning response: " + response.toString());
                            return CompletableFuture.completedFuture(response);
                        }
                    });
        } else {
            return fetchCasSession().thenCompose((response) -> {
                logger.info("fetched session, creating serviceticket.... headers: " + response.headers());

                try {
                    //ServiceTicket serviceTicket = new ServiceTicket(this.service, response.body().string());
                    //String setSessionCookie = CasUtils.getCookie(response, serviceTicket, this.cookieName).value();
                    //logger.info("Setting new sesson cookie....");
                    //TOKEN_STORE.set(new SessionCookies(setSessionCookie));
                    if (currentTokenCouldBeValid()) {
                        unauthorized.set(false);
                        return call(request, tryNumber + 1);
                    } else {
                        return CompletableFuture.failedFuture(new RuntimeException("Invalid session token from CAS. Should check credentials!"));
                    }
                } catch (Exception e) {
                    if (!shouldGiveUp) {
                        logger.info("error fetching CAS session, retrying..." + e);
                        return fetchCasSession(tryNumber + 1);
                    }
                    return CompletableFuture.failedFuture(new RuntimeException("Error getting cookie from response.", e));
                }
            });
        }
    }
}