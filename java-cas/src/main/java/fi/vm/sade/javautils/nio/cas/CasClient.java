package fi.vm.sade.javautils.nio.cas;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.asynchttpclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static fi.vm.sade.javautils.nio.cas.CasSessionFetchProcess.emptySessionProcess;
import static fi.vm.sade.javautils.nio.cas.CasTicketGrantingTicketFetchProcess.emptyTicketGrantingTicketProcess;
import static org.asynchttpclient.Dsl.asyncHttpClient;

/*
 Usage example:
    String username = ...;
    String password = ...;
    String host = "https://virkailija.testiopintopolku.fi";
    String casUrl = String.format("%s/cas", host);
    String serviceUrl = String.format("%s/suoritusrekisteri", host);
    CasClient casClient = new CasClient(SpringSessionCasConfig(username, password, casUrl, serviceUrl, "suoritusrekisteri", "suoritusrekisteri.backend"));
    Request req = new RequestBuilder()
      .setUrl(String.format("%s/suoritusrekisteri/rest/v1/valpas/", host))
      .setMethod("POST")
      .setBody("[]")
      .build();
    casClient.execute(req).thenApply(response -> System.out.println(response.getStatusCode()));
 */

public class CasClient {
    private static final Logger logger = LoggerFactory.getLogger(CasClient.class);
    private final CasConfig config;
    private final AsyncHttpClient asyncHttpClient;
    private final AtomicReference<CasSessionFetchProcess> sessionStore =
            new AtomicReference<>(emptySessionProcess());
    private final AtomicReference<CasTicketGrantingTicketFetchProcess> tgtStore =
            new AtomicReference<>(emptyTicketGrantingTicketProcess());
    private final long estimatedValidToken = TimeUnit.MINUTES.toMillis(15);
    private final long estimatedValidTgt = TimeUnit.HOURS.toMillis(7);



    public CasClient(CasConfig config) {
        this.config = config;
        ThreadFactory factory = new BasicThreadFactory.Builder()
                .namingPattern("async-cas-client-thread-%d")
                .daemon(true)
                //.priority(Thread.MAX_PRIORITY)
                .priority(Thread.NORM_PRIORITY)
                .build();

        this.asyncHttpClient = asyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
                .setThreadFactory(factory)
                .build());
    }

    private String locationFromResponse(Response tgtResponse) {
        if (201 == tgtResponse.getStatusCode()) {
            return tgtResponse.getHeader("Location");
        } else {
            throw new RuntimeException("Couldn't get TGT ticket from CAS!");
        }
    }

    private String ticketFromResponse(Response casResponse) {
        if (200 == casResponse.getStatusCode()) {
            return casResponse.getResponseBody().trim();
        } else {
            throw new RuntimeException("Couldn't get session ticket from CAS!");
        }
    }

    private CasSession newSessionFromToken(String token) {
        return new CasSession(token, new Date(System.currentTimeMillis() + estimatedValidToken));
    }

    private CasTicketGrantingTicket newTicketGrantingTicketFromToken(String token) {
        return new CasTicketGrantingTicket(token, new Date(System.currentTimeMillis() + estimatedValidTgt));
    }

    private String tgtFromLocation(String location) throws URISyntaxException {
        String path = new URI(location).getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private CasTicketGrantingTicket tgtFromResponse(Response tgtResponse) {
        try {
            CasTicketGrantingTicket ticket = newTicketGrantingTicketFromToken(tgtFromLocation(locationFromResponse(tgtResponse)));
            logger.debug(String.format("got ticket granting ticket: %s", ticket));
            return ticket;
        } catch (URISyntaxException e) {
            throw new RuntimeException("Could not create CasTicketGrantingTicket from CAS tgt response.", e);
        }
    }

    private Request withCsrfAndCallerId(Request req) {
        return req.toBuilder()
                .setHeader("Caller-Id", config.getCallerId())
                .setHeader("CSRF", config.getCsrf())
                .addOrReplaceCookie(new DefaultCookie("CSRF", config.getCsrf()))
                .build();
    }

    private Request buildTgtRequest() {
        return withCsrfAndCallerId(new RequestBuilder()
                .setUrl(String.format("%s/v1/tickets", config.getCasUrl()))
                .setMethod("POST")
                .addFormParam("username", config.getUsername())
                .addFormParam("password", config.getPassword())
                .build());
    }

    private Request buildSTRequest(String ticketGrantingTicket) {
        final String serviceUrl = String.format("%s%s",
                config.getServiceUrl(),
                config.getServiceUrlSuffix()
        );
        return withCsrfAndCallerId(new RequestBuilder()
                .setUrl(String.format("%s/v1/tickets/%s", config.getCasUrl(), ticketGrantingTicket))
                .setMethod("POST")
                .addFormParam("service", serviceUrl)
                .build());
    }

    private Request buildSessionRequest(Response response) {
        logger.debug(String.format("buildSessionRequest response: %s", response.toString()));
        return withCsrfAndCallerId(new RequestBuilder()
                .setUrl(config.getSessionUrl())
                .setMethod("GET")
                .addQueryParam("ticket", ticketFromResponse(response))
                .build());
    }

    private Function<Response, Response> onSuccessIncreaseSessionTime(CasSessionFetchProcess currentSessionProcess, CasSession session) {
        return response -> {
            if (Set.of(200, 201).contains(response.getStatusCode())) {
                if (sessionStore.compareAndSet(currentSessionProcess, new CasSessionFetchProcess(
                        CompletableFuture.completedFuture(
                                newSessionFromToken(session.getSessionCookie()))))) {
                    logger.info("Updated current session with more time");
                } else {
                    logger.info("Tried to update more time to current session but some other thread was faster");
                }
            }
            return response;
        };
    }

    private CompletableFuture<Response> executeRequestWithSession(CasSession session, Request request, boolean forceUpdate) {
        return asyncHttpClient.executeRequest(withCsrfAndCallerId(request.toBuilder()
                .addOrReplaceCookie(new DefaultCookie(config.getjSessionName(), session.getSessionCookie()))
                .build())).toCompletableFuture().thenCompose(response -> {
            if (Set.of(302, 401).contains(response.getStatusCode())) {
                if (forceUpdate) {
                    throw new RuntimeException(String.format("Request with %s failed with status %s after retry.", request.getUrl(), response.getStatusCode()));
                }
                logger.warn(String.format("Got statuscode %s, Retrying once...", response.getStatusCode()));
                return execute(request, true);
            }
            return CompletableFuture.completedFuture(response);

        });
    }

    private CompletableFuture<Response> executeRequestWithReusedSession(CasSessionFetchProcess currentSessionProcess, CasSession session, Request request, boolean forceUpdate) {
        return executeRequestWithSession(session, request, forceUpdate).thenApply(onSuccessIncreaseSessionTime(currentSessionProcess, session));
    }

    public CompletableFuture<Response> execute(Request request) {
        try {
            return execute(request, false);
        } catch (Exception e) {
            // Just make sure we do not fail instantly...
            logger.debug(String.format("execute failed, retrying with forceupdate...", e));
            return execute(request, true);
        }
    }

    private CompletableFuture<Response> execute(Request request, boolean forceUpdate) {
        final CasSessionFetchProcess currentSession = sessionStore.get();
        return currentSession.getSessionProcess()
                .thenCompose(session -> {
                    logger.debug(String.format("execute - forceupdate: %s session valid: %s", forceUpdate, session.isValid()));
                            if (forceUpdate || !session.isValid()) {
                                return sessionRequest(currentSession, true).getSessionProcess()
                                        .thenCompose(newSession -> executeRequestWithSession(newSession, request, forceUpdate));
                            } else {
                                    return executeRequestWithReusedSession(currentSession, session, request, forceUpdate);
                            }
                        }
                );
    }

    private CompletableFuture<Response> serviceTicketRequestWithTicketGrantingTicket(String ticketGrantingTicket) {
        logger.debug("Getting service ticket.");
        return asyncHttpClient.executeRequest(buildSTRequest(ticketGrantingTicket)).toCompletableFuture();
    }

    private CasSession sessionFromResponse(Response casResponse) {
        logger.debug(String.format("sessionFromResponse response: %s", casResponse));
        for (Cookie cookie : casResponse.getCookies()) {
            if (config.getjSessionName().equals(cookie.name())) {
                CasSession session = newSessionFromToken(cookie.value());
                logger.debug(String.format("sessionFromResponse, got session %s", session));
                return session;
            }
        }
        logger.error(String.format("Cas session Response failed in URI=%s: %s", casResponse.getUri(), casResponse));
        throw new RuntimeException(String.format("%s cookie not found in CAS authentication response using CAS URL %s and service %s", config.getjSessionName(), config.getCasUrl(), config.getServiceUrl()));
    }

    private CompletableFuture<Response> createSessionResponsePromise(CasTicketGrantingTicketFetchProcess currentTicketGrantingTicket, boolean forceUpdate) {
        return currentTicketGrantingTicket.getTicketGrantingTicketProcess()
                .thenCompose(ticket -> {
                    logger.debug(String.format("createSessionResponsePromise - forceupdate: %s ticket valid: %s", forceUpdate, ticket.isValid()));
                            if (forceUpdate || !ticket.isValid()) {
                                return tgtRequest(currentTicketGrantingTicket).getTicketGrantingTicketProcess().thenCompose(
                                        newTicketGrantingTicket -> serviceTicketRequestWithTicketGrantingTicket(newTicketGrantingTicket.getTicketGrantingTicket()))
                                        .thenCompose(response -> asyncHttpClient.executeRequest(buildSessionRequest(response))
                                                .toCompletableFuture());
                            } else {
                                return serviceTicketRequestWithTicketGrantingTicket(ticket.getTicketGrantingTicket())
                                        .thenCompose(response -> asyncHttpClient.executeRequest(buildSessionRequest(response))
                                                .toCompletableFuture());
                            }
                        }
                );
    }

    private CasSessionFetchProcess sessionRequest(CasSessionFetchProcess currentSession, boolean forceUpdate) {
        final CasTicketGrantingTicketFetchProcess currentTicketGrantingTicket = tgtStore.get();
        CompletableFuture<Response> sessionResponse = createSessionResponsePromise(currentTicketGrantingTicket, forceUpdate);

        CompletableFuture<CasSession> responsePromise = sessionResponse.thenCompose(response -> {
                try {
                    logger.debug(String.format("sessionRequest response: %s", response.toString()));
                    return CompletableFuture.completedFuture(sessionFromResponse(response));
                } catch (RuntimeException cookieException) {
                    logger.warn(String.format("No %s cookie found from response, retrying once...", config.getjSessionName()));
                    CompletableFuture<CasSession> retrySessionResponse =
                      createSessionResponsePromise(currentTicketGrantingTicket, true).thenApply(r -> sessionFromResponse(r));
                    try {
                        logger.debug("sessionRequest retry.");
                        return retrySessionResponse;
                    } catch (Exception e) {
                        return CompletableFuture.failedFuture(new RuntimeException("Failed to get session response after retry", e));
                    }
                }
        });

        final CasSessionFetchProcess newFetchProcess = new CasSessionFetchProcess(responsePromise);
        if (sessionStore.compareAndSet(currentSession, newFetchProcess)) {
            return newFetchProcess;
        } else {
            responsePromise.cancel(true);
            return sessionStore.get();
        }
    }

    private CasTicketGrantingTicketFetchProcess tgtRequest(CasTicketGrantingTicketFetchProcess currentTicketGrantingTicket) {
        CompletableFuture<CasTicketGrantingTicket> responsePromise = asyncHttpClient.executeRequest(buildTgtRequest())
                .toCompletableFuture().thenApply(this::tgtFromResponse);
        final CasTicketGrantingTicketFetchProcess newFetchProcess = new CasTicketGrantingTicketFetchProcess(responsePromise);

        if (tgtStore.compareAndSet(currentTicketGrantingTicket, newFetchProcess)) {
            logger.debug("Set new tgt ticket");
            return newFetchProcess;
        } else {
            logger.debug("Set new tgt ticket cancelled");
            responsePromise.cancel(true);
            return tgtStore.get();
        }
    }

    public CompletableFuture<Response> executeWithServiceTicket(Request request) {
        try{
            return executeWithServiceTicket(request, false);
        } catch (Exception e) {
            // Just make sure we do not fail instantly...
            logger.debug(String.format("executeWithServiceTicket failed, retrying with forceupdate...", e));
            return executeWithServiceTicket(request, true);
        }
    }

    private CompletableFuture<Response> executeWithServiceTicket(Request request, boolean forceUpdate) {
        final CasTicketGrantingTicketFetchProcess currentTicketGrantingTicket = tgtStore.get();
        CompletableFuture<Response> stResponse = currentTicketGrantingTicket.getTicketGrantingTicketProcess().thenCompose(ticket -> {
            logger.debug(String.format("executeWithServiceTicket - forceupdate: %s ticket valid: %s", forceUpdate, ticket.isValid()));
            if (forceUpdate || !ticket.isValid()) {
                return tgtRequest(currentTicketGrantingTicket).getTicketGrantingTicketProcess().thenCompose(
                        newTicketGrantingTicket -> serviceTicketRequestWithTicketGrantingTicket(newTicketGrantingTicket.getTicketGrantingTicket()));
            } else {
                return serviceTicketRequestWithTicketGrantingTicket(ticket.getTicketGrantingTicket());
            }
        });

        return stResponse.thenCompose(response -> {
            try {
                Request req;
                if (this.config.getServiceTicketHeaderName() == null) {
                    req = withCsrfAndCallerId(request.toBuilder()
                            .addQueryParam("ticket", ticketFromResponse(response))
                            .build());
                } else {
                    req = withCsrfAndCallerId(request.toBuilder()
                            .addHeader(this.config.getServiceTicketHeaderName(), ticketFromResponse(response))
                            .build());
                }

                logger.debug(String.format("Request with service ticket to url: %s", req.getUrl()));
                return asyncHttpClient.executeRequest(req).toCompletableFuture().thenCompose(res -> {
                    logger.info(res.toString());
                    if (Set.of(302, 401).contains(res.getStatusCode())) {
                        if (forceUpdate) {
                            return CompletableFuture.failedFuture(new RuntimeException(String.format("Request %s failed with status %s after retry.", request.getUrl(), res.getStatusCode())));
                        }
                        logger.warn(String.format("Got statuscode %s from request with service ticket, Retrying once...", res.getStatusCode()));
                        return executeWithServiceTicket(request, true);
                    }
                    return CompletableFuture.completedFuture(res);
                });
            } catch (RuntimeException e) {
                logger.warn("Get service ticket from response failed, Retrying once...");
                return executeWithServiceTicket(request, true);
            }
        });
    }

    public Response executeBlocking(Request request) throws ExecutionException {
        try {
            return execute(request).get();
        } catch (Exception e) {
            throw new ExecutionException(String.format("Failed to execute blocking request: %s", request.getUrl()), e);
        }
    }

    public Response executeWithServiceTicketBlocking(Request request) throws ExecutionException {
        try {
            return executeWithServiceTicket(request).get();
        } catch (Exception e) {
            throw new ExecutionException(String.format("Failed to execute blocking request with service ticket: %s", request.getUrl()), e);
        }
    }

    private CompletableFuture<Response> fetchValidationResponse(String service, String ticket) {
        Request req = withCsrfAndCallerId(new RequestBuilder()
                .setUrl(config.getCasUrl() + "/serviceValidate?ticket=" + ticket + "&service=" + service)
                .addQueryParam("ticket", ticket)
                .addQueryParam("service", service)
                .setMethod("GET")
                .build());
        return asyncHttpClient.executeRequest(req).toCompletableFuture();
    }

    public CompletableFuture<String> validateServiceTicketWithVirkailijaUsername(String service, String ticket) {
        return fetchValidationResponse(service, ticket).thenApply(this::getUsernameFromResponse);
    }

    public CompletableFuture<HashMap<String, String>> validateServiceTicketWithOppijaAttributes(String service, String ticket) throws ExecutionException {
        return fetchValidationResponse(service, ticket).thenApply(this::getOppijaAttributesFromResponse);
    }

    public String validateServiceTicketWithVirkailijaUsernameBlocking(String service, String ticket) throws ExecutionException {
        try {
            return validateServiceTicketWithVirkailijaUsername(service, ticket).get();
        } catch (Exception e) {
            throw new ExecutionException(String.format("Failed to validate service ticket with virkailija username, service: %s , ticket: &s", service, ticket), e);
        }
    }

    private String getUsernameFromResponse(Response response) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(response.getResponseBody())));
            return document.getElementsByTagName("cas:user").item(0).getTextContent();
        } catch (Exception e) {
            throw new RuntimeException("CAS service ticket validation failed: ", e);
        }
    }

    private HashMap<String, String> getOppijaAttributesFromResponse(Response response) {
        HashMap<String, String> oppijaAttributes = new HashMap<String, String>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(response.getResponseBody())));

            oppijaAttributes.put("clientName", document.getElementsByTagName("cas:clientName").item(0).getTextContent());
            oppijaAttributes.put("displayName", document.getElementsByTagName("cas:displayName").item(0).getTextContent());
            oppijaAttributes.put("givenName", document.getElementsByTagName("cas:givenName").item(0).getTextContent());
            oppijaAttributes.put("personOid", document.getElementsByTagName("cas:personOid").item(0).getTextContent());
            oppijaAttributes.put("personName", document.getElementsByTagName("cas:personName").item(0).getTextContent());
            oppijaAttributes.put("firstName", document.getElementsByTagName("cas:firstName").item(0).getTextContent());
            oppijaAttributes.put("nationalIdentificationNumber", document.getElementsByTagName("cas:nationalIdentificationNumber").item(0).getTextContent());

            if (document.getElementsByTagName("cas:impersonatorNationalIdentificationNumber").getLength() > 0) {
                oppijaAttributes.put("impersonatorNationalIdentificationNumber", document.getElementsByTagName("cas:impersonatorNationalIdentificationNumber").item(0).getTextContent());
                oppijaAttributes.put("impersonatorDisplayName", document.getElementsByTagName("cas:impersonatorDisplayName").item(0).getTextContent());
            }
            return oppijaAttributes;
        } catch (Exception e) {
            throw new RuntimeException("CAS service ticket validation failed for oppija attributes: ", e);
        }
    }

    public HashMap<String, String> validateServiceTicketWithOppijaAttributesBlocking(String service, String ticket) throws ExecutionException {
        try {
            return validateServiceTicketWithOppijaAttributes(service, ticket).get();
        } catch (Exception e) {
            throw new ExecutionException(String.format("Failed to validate service ticket with oppija attributes, service: %s , ticket: &s", service, ticket), e);
        }
    }
}
