package fi.vm.sade.javautils.cas;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class CasSession {
    private static final Logger logger = LoggerFactory.getLogger(CasSession.class);

    private static final String CSRF_VALUE = "CSRF";

    private final OkHttpClient client;
    private final Duration requestTimeout;
    private final String callerId;
    private final HttpUrl ticketsUrl;
    private final String username;
    private final String password;
    private URI ticketGrantingTicket;

    public CasSession(OkHttpClient client,
                      Duration requestTimeout,
                      String callerId,
                      HttpUrl ticketsUrl,
                      String username,
                      String password) {

        this.requestTimeout = requestTimeout;
        this.client = client;
        this.callerId = callerId;
        this.ticketsUrl = ticketsUrl;
        this.username = username;
        this.password = password;
        this.ticketGrantingTicket = null;
    }

    public ServiceTicket getServiceTicket(String service) {
        try {
            this.ticketGrantingTicket = this.getTicketGrantingTicket();
            ServiceTicket serviceTicket = this.requestServiceTicket(this.ticketGrantingTicket, service);
            if (serviceTicket != null) {
                return serviceTicket;
            }
            this.invalidateTicketGrantingTicket(this.ticketGrantingTicket);
            this.ticketGrantingTicket = this.getTicketGrantingTicket();
            serviceTicket = this.requestServiceTicket(this.ticketGrantingTicket, service);
            return serviceTicket;
        } catch (Exception e) {
            throw new IllegalStateException("Error getting service ticket for service: " + service, e);
        }
    }

    private URI getTicketGrantingTicket() {
        URI currentTicketGrantingTicket = this.ticketGrantingTicket;

        if (currentTicketGrantingTicket == null) {
            synchronized (this) {
                if (this.ticketGrantingTicket == null) {
                    RequestBody body = new FormBody.Builder()
                            .add("username", URLEncoder.encode(this.username, StandardCharsets.UTF_8))
                            .add("password", URLEncoder.encode(this.password, StandardCharsets.UTF_8))
                            .build();
                    //TODO timeouts!

                    Request request = new Request.Builder()
                            .url(this.ticketsUrl)
                            .post(body)
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .addHeader("Caller-Id", this.callerId)
                            .addHeader("Cookie", String.format("CSRF=%s;", CSRF_VALUE))
                            .header("Connection", "close")
                            .build();

                    try (Response response = this.client.newCall(request).execute()) {
                        if (response.code() != 201) {
                            throw new IllegalStateException(String.format("%s %d: %s", request.url().toString(), response.code(), response.body()));
                        }

                        try {
                            this.ticketGrantingTicket = response.headers("Location").stream().findFirst()
                                    .map(URI::create).get();
                        } catch (Exception e) {
                            throw new IllegalStateException(String.format("%s %d: %s", request.url().toString(), response.code(), "Could not parse TGT, no Location header found"));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return this.ticketGrantingTicket;
            }
        }
        return currentTicketGrantingTicket;
    }

    private synchronized void invalidateTicketGrantingTicket(URI invalidTicketGrantingTicket) {
        if (this.ticketGrantingTicket == null || (invalidTicketGrantingTicket != null && invalidTicketGrantingTicket.equals(this.ticketGrantingTicket))) {
            this.ticketGrantingTicket = null;
        }
    }

    public ServiceTicket requestServiceTicket(URI tgt, String service) {
        RequestBody body = new FormBody.Builder()
                .add("service", URLEncoder.encode(service, StandardCharsets.UTF_8))
                .build();

        Request request = new Request.Builder()
                .url(HttpUrl.get(tgt.toString()))
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Caller-Id", this.callerId)
                .addHeader("Cookie", String.format("CSRF=%s;", CSRF_VALUE))
                .header("Connection", "close")
                .build();
        //TODO timeouts!

        try (Response response = this.client.newCall(request).execute()) {
            if (response.code() != 200 || response.body().contentLength() == 0) {
                throw new IllegalStateException(String.format("%s %d: %s", request.url(), response.code(), response.body()));
            }
            ServiceTicket ticket = new ServiceTicket(service, response.body().string());
            return ticket;
        } catch (Exception e) {
            throw new IllegalStateException(request.url().toString(), e);
        }
    }
}