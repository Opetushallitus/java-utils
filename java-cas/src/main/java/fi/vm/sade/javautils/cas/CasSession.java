package fi.vm.sade.javautils.cas;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class CasSession {

    private static final String CSRF_VALUE = "CSRF";

    private final HttpClient client;
    private final Duration requestTimeout;
    private final String callerId;
    private final URI ticketsUrl;
    private final String username;
    private final String password;
    private CompletableFuture<URI> ticketGrantingTicket;

    public CasSession(HttpClient client,
                      Duration requestTimeout,
                      String callerId,
                      URI ticketsUrl,
                      String username,
                      String password) {
        this.client = client;
        this.requestTimeout = requestTimeout;
        this.callerId = callerId;
        this.ticketsUrl = ticketsUrl;
        this.username = username;
        this.password = password;
        this.ticketGrantingTicket = CompletableFuture.failedFuture(new IllegalStateException("uninitialized"));
    }

    public CompletableFuture<ServiceTicket> getServiceTicket(String service) {
        return this.getTicketGrantingTicket()
                .thenCompose(currentTicketGrantingTicket -> this.requestServiceTicket(currentTicketGrantingTicket, service)
                        .handle((serviceTicket, e) -> {
                            if (serviceTicket != null) {
                                return CompletableFuture.completedFuture(serviceTicket);
                            }
                            this.invalidateTicketGrantingTicket(currentTicketGrantingTicket);
                            return getTicketGrantingTicket()
                                    .thenCompose(newTicketGrantingTicket -> this.requestServiceTicket(newTicketGrantingTicket, service));
                        }))
                .thenCompose(f -> f);
    }

    private CompletableFuture<URI> getTicketGrantingTicket() {
        CompletableFuture<URI> currentTicketGrantingTicket = this.ticketGrantingTicket;
        if (currentTicketGrantingTicket.isCompletedExceptionally()) {
            synchronized (this) {
                if (this.ticketGrantingTicket.isCompletedExceptionally()) {
                    HttpRequest request = HttpRequest.newBuilder(this.ticketsUrl)
                            .POST(HttpRequest.BodyPublishers.ofString(String.format(
                                    "username=%s&password=%s",
                                    URLEncoder.encode(this.username, Charset.forName("UTF-8")),
                                    URLEncoder.encode(this.password, Charset.forName("UTF-8"))
                            )))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .header("Caller-Id", this.callerId)
                            .header("CSRF", CSRF_VALUE)
                            .header("Cookie", String.format("CSRF=%s;", CSRF_VALUE))
                            .timeout(this.requestTimeout)
                            .build();
                    this.ticketGrantingTicket = this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString(Charset.forName("UTF-8")))
                            .handle((response, e) -> {
                                if (e != null) {
                                    throw new IllegalStateException(request.uri().toString(), e);
                                }
                                if (response.statusCode() != 201) {
                                    throw new IllegalStateException(String.format("%s %d: %s", request.uri().toString(), response.statusCode(), response.body()));
                                }
                                return response.headers().firstValue("Location")
                                        .map(URI::create)
                                        .orElseThrow(() -> new IllegalStateException(String.format("%s %d: %s", request.uri().toString(), response.statusCode(), "Could not parse TGT, no Location header found")));
                            });
                }
                return this.ticketGrantingTicket;
            }
        }
        return currentTicketGrantingTicket;
    }

    private synchronized void invalidateTicketGrantingTicket(URI invalidTicketGrantingTicket) {
        if (this.ticketGrantingTicket.isCompletedExceptionally() || (invalidTicketGrantingTicket != null && invalidTicketGrantingTicket.equals(this.ticketGrantingTicket.getNow(null)))) {
            this.ticketGrantingTicket = CompletableFuture.failedFuture(new IllegalStateException("invalidated"));
        }
    }

    private CompletableFuture<ServiceTicket> requestServiceTicket(URI tgt, String service) {
        HttpRequest request = HttpRequest.newBuilder(tgt)
                .POST(HttpRequest.BodyPublishers.ofString(String.format(
                        "service=%s",
                        URLEncoder.encode(service, Charset.forName("UTF-8"))
                )))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Caller-Id", this.callerId)
                .header("CSRF", CSRF_VALUE)
                .header("Cookie", String.format("CSRF=%s;", CSRF_VALUE))
                .timeout(this.requestTimeout)
                .build();
        return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString(Charset.forName("UTF-8")))
                .handle((response, e) -> {
                    if (e != null) {
                        throw new IllegalStateException(request.uri().toString(), e);
                    }
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException(String.format("%s %d: %s", response.uri().toString(), response.statusCode(), response.body()));
                    }
                    return new ServiceTicket(service, response.body());
                });
    }

}
