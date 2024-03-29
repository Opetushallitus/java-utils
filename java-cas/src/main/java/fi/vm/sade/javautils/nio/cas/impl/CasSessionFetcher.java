package fi.vm.sade.javautils.nio.cas.impl;

import fi.vm.sade.javautils.nio.cas.CasConfig;
import fi.vm.sade.javautils.nio.cas.exceptions.MissingSessionCookieException;
import fi.vm.sade.javautils.nio.cas.exceptions.ServiceTicketException;
import fi.vm.sade.javautils.nio.cas.exceptions.TicketGrantingTicketException;
import io.netty.handler.codec.http.cookie.Cookie;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.CompletableFuture;
import static java.util.concurrent.CompletableFuture.*;

public class CasSessionFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(CasSessionFetcher.class);
    private final AsyncHttpClient asyncHttpClient;
    private final CasConfig config;
    private final CasUtils utils;
    private final CompletableFutureStore<String> sessionStore;
    private final CompletableFutureStore<String> tgtStore;

    public CasSessionFetcher(CasConfig config,
                             AsyncHttpClient asyncHttpClient,
                             CompletableFutureStore<String> sessionStore,
                             CompletableFutureStore<String> tgtStore) {
        this.config = config;
        this.utils = new CasUtils(this.config);
        this.asyncHttpClient = asyncHttpClient;
        this.sessionStore = sessionStore;
        this.tgtStore = tgtStore;
    }

    private CompletableFuture<String> tgtFromResponse(Response tgtResponse) {
	if (201 == tgtResponse.getStatusCode()) {
	    try {
		String location = tgtResponse.getHeader("Location");
		String path = new URI(location).getPath();
		String tgt = path.substring(path.lastIndexOf('/') + 1);
		return completedFuture(tgt);
	    } catch (URISyntaxException e) {
		return failedFuture(
		    new TicketGrantingTicketException(
			new RuntimeException(
			    String.format("Could not parse CasTicketGrantingTicket from CAS tgt response. URL = %s, location = %s, body = %s",
				tgtResponse.getUri(), tgtResponse.getHeader("Location"), tgtResponse.getResponseBody()),
			    e)));
	    }
	} else {
	    return failedFuture(
		new TicketGrantingTicketException(
		    new RuntimeException(
			String.format("Couldn't get TGT ticket from CAS! URL = %s, status = %s, body = %s",
			    tgtResponse.getUri(), tgtResponse.getStatusCode(), tgtResponse.getResponseBody()))));
	}
    }

    private CompletableFuture<String> fetchTicketGrantingTicketForReal() {
        LOGGER.info(String.format("Fetching CAS ticket granting ticket (service = %s, session name = %s)", config.getSessionUrl(), config.getjSessionName()));
        Request tgtRequest = utils.withCallerIdAndCsrfHeader()
                .setUrl(String.format("%s/v1/tickets", config.getCasUrl()))
                .setMethod("POST")
                .addFormParam("username", config.getUsername())
                .addFormParam("password", config.getPassword())
                .build();
        this.asyncHttpClient.getConfig().getCookieStore().clear();
        return this.asyncHttpClient.executeRequest(tgtRequest).toCompletableFuture()
                .thenCompose(this::tgtFromResponse);
    }

    private CompletableFuture<String> fetchTicketGrantingTicket() {
        return tgtStore.getOrSet(this::fetchTicketGrantingTicketForReal);
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
		    new RuntimeException(
			String.format("Couldn't get service ticket from CAS! URL = %s, status = %s, body = %s",
			    response.getUri(), response.getStatusCode(), response.getResponseBody()))));
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

    private CompletableFuture<String> fetchSessionForReal() {
        LOGGER.info(String.format("Fetching CAS session (service = %s, session name = %s)", config.getSessionUrl(), config.getjSessionName()));
        return fetchTicketGrantingTicket()
                .thenCompose(this::fetchServiceTicketWithTgt)
                .thenCompose(this::sessionFromSTResponse)
                .thenCompose(this::responseAsToken);
    }

    public CompletableFuture<String> fetchSessionToken() {
        return sessionStore.getOrSet(this::fetchSessionForReal);
    }

    public void clearSessionStore() {
        this.sessionStore.clear();
        this.asyncHttpClient.getConfig().getCookieStore().clear();
    }
    public void clearTgtStore() {
        this.tgtStore.clear();
        this.asyncHttpClient.getConfig().getCookieStore().clear();
    }
}
