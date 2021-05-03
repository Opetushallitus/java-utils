package fi.vm.sade.javautils.cas;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;

public class CasSession {

    private static final String CSRF_VALUE = "CSRF";

    private final AsyncHttpClient client;
    private final int requestTimeout;
    private final String callerId;
    private final String ticketsUrl;
    private final String username;
    private final String password;
    private CompletableFuture<URI> ticketGrantingTicket;

    public CasSession(AsyncHttpClient client,
                      int requestTimeout,
                      String callerId,
                      String ticketsUrl,
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
                    Request request = new RequestBuilder(this.ticketsUrl)
                            .setMethod("POST")
                            .setBody(String.format(
                                    "username=%s&password=%s",
                                    URLEncoder.encode(this.username, Charset.forName("UTF-8")),
                                    URLEncoder.encode(this.password, Charset.forName("UTF-8"))
                            ))
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .addHeader("Caller-Id", this.callerId)
                            .addHeader("CSRF", CSRF_VALUE)
                            .addHeader("Cookie", String.format("CSRF=%s;", CSRF_VALUE))
                            .setRequestTimeout(this.requestTimeout)
                            .build();
                    this.ticketGrantingTicket = this.client.executeRequest(request).toCompletableFuture()
                            .handle((response, e) -> {
                                if (e != null) {
                                    throw new IllegalStateException(request.getUrl(), e);
                                }
                                if (response.getStatusCode() != 201) {
                                    throw new IllegalStateException(String.format("%s %d: %s", request.getUrl(), response.getStatusCode(), response.getResponseBody()));
                                }
                                if (response.getHeader("Location") == null) {
                                    throw new IllegalStateException(String.format("%s %d: %s", request.getUrl().toString(), response.getStatusCode(), "Could not parse TGT, no Location header found"));
                                }
                                return URI.create(response.getHeader("Location"));
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
        Request request = new RequestBuilder().setUrl(tgt.toString())
                .setMethod("POST")
                .setBody(String.format(
                        "service=%s",
                        URLEncoder.encode(service, Charset.forName("UTF-8"))
                ))
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Caller-Id", this.callerId)
                .addHeader("CSRF", CSRF_VALUE)
                .addHeader("Cookie", String.format("CSRF=%s;", CSRF_VALUE))
                .setRequestTimeout(this.requestTimeout)
                .build();
        return this.client.executeRequest(request).toCompletableFuture()
                .handle((response, e) -> {
                    if (e != null) {
                        throw new IllegalStateException(request.getUrl(), e);
                    }
                    if (response.getStatusCode() != 200) {
                        throw new IllegalStateException(String.format("%s %d: %s", response.getUri().toString(), response.getStatusCode(), response.getResponseBody()));
                    }
                    return new ServiceTicket(service, response.getResponseBody());
                });
    }

}
