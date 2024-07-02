package fi.vm.sade.javautils.nio.cas.impl;

import fi.vm.sade.javautils.nio.cas.CasConfig;
import fi.vm.sade.javautils.nio.cas.exceptions.MissingSessionCookieException;
import fi.vm.sade.javautils.nio.cas.exceptions.ServiceTicketException;
import fi.vm.sade.javautils.nio.cas.exceptions.TicketGrantingTicketException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.netty.handler.codec.http.cookie.Cookie;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.*;

public class CasSessionFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CasSessionFetcher.class);
    private final AsyncHttpClient asyncHttpClient;
    private final CasConfig config;
    private final CasUtils utils;
    private final CachedSupplier<String> sessionTicketSupplier;
    private final CachedSupplier<String> tgtSupplier;

    public CasSessionFetcher(CasConfig config,
                             AsyncHttpClient asyncHttpClient,
                             long sessionTicketTTL,
                             long tgtTTL) {
        this(config, asyncHttpClient, sessionTicketTTL, tgtTTL, CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .slowCallRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMillis(30000))
            .slowCallDurationThreshold(Duration.ofSeconds(10))
            .permittedNumberOfCallsInHalfOpenState(4)
            .minimumNumberOfCalls(6)
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
            .slidingWindowSize(10)
            .build());
    }

    public CasSessionFetcher(CasConfig config,
                             AsyncHttpClient asyncHttpClient,
                             long sessionTicketTTL,
                             long tgtTTL,
                             CircuitBreakerConfig circuitBreakerConfig) {
        this.config = config;
        this.utils = new CasUtils(this.config);
        this.asyncHttpClient = asyncHttpClient;
        this.sessionTicketSupplier = new CachedSupplier<>(sessionTicketTTL, new CircuitBreakerSupplier<>("sessionTicket", circuitBreakerConfig, this::fetchSessionForReal));
        this.tgtSupplier = new CachedSupplier<>(tgtTTL, new CircuitBreakerSupplier<>("tgt", circuitBreakerConfig, this::fetchTicketGrantingTicketForReal));
    }

    private String tgtFromResponse(Response tgtResponse) {
        if (201 == tgtResponse.getStatusCode()) {
          try {
            String location = tgtResponse.getHeader("Location");
            String path = new URI(location).getPath();
            String tgt = path.substring(path.lastIndexOf('/') + 1);
            return tgt;
          } catch (URISyntaxException e) {
            throw new TicketGrantingTicketException(
                String.format("Could not parse CasTicketGrantingTicket from CAS tgt response. URL = %s, location = %s, body = %s",
                    tgtResponse.getUri(), tgtResponse.getHeader("Location"), tgtResponse.getResponseBody()), e);
          }
        } else {
          throw new TicketGrantingTicketException(
              String.format("Couldn't get TGT ticket from CAS! URL = %s, status = %s, body = %s",
                  tgtResponse.getUri(), tgtResponse.getStatusCode(), tgtResponse.getResponseBody()));
          }
    }

    private String fetchTicketGrantingTicketForReal() {
        LOGGER.info(String.format("Fetching CAS ticket granting ticket (service = %s, session name = %s)", config.getSessionUrl(), config.getjSessionName()));
        Request tgtRequest = utils.withCallerIdAndCsrfHeader()
                .setUrl(String.format("%s/v1/tickets", config.getCasUrl()))
                .setMethod("POST")
                .addFormParam("username", config.getUsername())
                .addFormParam("password", config.getPassword())
                .build();
        this.asyncHttpClient.getConfig().getCookieStore().clear();
        Response response;
        try {
          response = this.asyncHttpClient.executeRequest(tgtRequest).get();
        } catch(Exception e) {
          throw new TicketGrantingTicketException("Unable to retrieve TGT", e);
        }
        return this.tgtFromResponse(response);
    }

    private CompletableFuture<String> fetchTicketGrantingTicket() {
        try {
            String tgt = this.tgtSupplier.get();
            return CompletableFuture.completedFuture(tgt);
        } catch (Exception e) {
            return failedFuture(e);
        }
    }

    private CompletableFuture<Response> fetchServiceTicketWithTgt(String ticketGrantingTicket) {
        final String serviceUrl = String.format("%s%s",
                config.getServiceUrl(),
                config.getServiceUrlSuffix()
        );
        Request serviceTicketRequest = utils.withCallerIdAndCsrfHeader()
                .setUrl(String.format("%s/v1/tickets/%s", config.getCasUrl(), ticketGrantingTicket))
                .setMethod("POST")
                .addFormParam("service", serviceUrl)
                .build();
        this.asyncHttpClient.getConfig().getCookieStore().clear();
        return this.asyncHttpClient.executeRequest(serviceTicketRequest).toCompletableFuture();
    }

    private CompletableFuture<Response> sessionFromSTResponse(Response response) {
        if (200 == response.getStatusCode()) {
            String ticket = response.getResponseBody().trim();
            Request sessionRequest = utils.withCallerIdAndCsrfHeader()
                    .setUrl(config.getSessionUrl())
                    .setMethod("GET")
                    .addQueryParam("ticket", ticket)
                    .build();
            return this.asyncHttpClient.executeRequest(sessionRequest).toCompletableFuture();
        } else {
            return failedFuture(
                new ServiceTicketException(
                    String.format("Couldn't get service ticket from CAS! URL = %s, status = %s, body = %s",
                        response.getUri(), response.getStatusCode(), response.getResponseBody())));
        }
    }

    private CompletableFuture<String> responseAsToken(Response response) {
        for (Cookie cookie : response.getCookies()) {
            if (config.getjSessionName().equals(cookie.name())) {
                return completedFuture(cookie.value());
            }
        }
        return failedFuture(new MissingSessionCookieException(config.getjSessionName(), response));
    }

    private static class WrappedException extends RuntimeException {
        public WrappedException(Exception cause) {
            super(cause);
        }
    }

    private String fetchSessionForReal() throws WrappedException {
        LOGGER.info(String.format("Fetching CAS session (service = %s, session name = %s)", config.getSessionUrl(), config.getjSessionName()));
        try {
            return this.fetchTicketGrantingTicket()
                .thenCompose(this::fetchServiceTicketWithTgt)
                .thenCompose(this::sessionFromSTResponse)
                .thenCompose(this::responseAsToken)
                .get();
        } catch (Exception e) {
            throw new WrappedException(e);
        }
    }

    public CompletableFuture<String> fetchSessionToken() {
        try {
            String sessionToken = sessionTicketSupplier.get();
            return CompletableFuture.completedFuture(sessionToken);
        } catch (WrappedException e) {
            return failedFuture(e.getCause());
        }
    }

    public void clearSessionStore() {
        this.sessionTicketSupplier.clear();
        this.asyncHttpClient.getConfig().getCookieStore().clear();
    }
    public void clearTgtStore() {
        this.tgtSupplier.clear();
        this.asyncHttpClient.getConfig().getCookieStore().clear();
    }
}
