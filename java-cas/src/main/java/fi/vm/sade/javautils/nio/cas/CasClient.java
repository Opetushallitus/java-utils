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
    private final long estimatedValidToken = TimeUnit.MINUTES.toMillis(15);

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

    private String tgtLocationFromResponse(Response casResponse) {
        if (201 == casResponse.getStatusCode()) {
            logger.info("got tgt response:" + casResponse.toString());
            return casResponse.getHeader("Location");
        } else {
            throw new RuntimeException("Couldn't get TGT ticket!");
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
        return new CasSession(config.getjSessionName(), token, new Date(System.currentTimeMillis() + estimatedValidToken));
    }

    private CasSession sessionFromResponse(Response casResponse) {
        logger.info("ticket response: " + casResponse.toString());
        logger.info("cas response cookies:" + casResponse.getCookies().toString());
        logger.info("config jsessionname: " + config.getjSessionName());
        for (Cookie cookie : casResponse.getCookies()) {
            if (config.getjSessionName().equals(cookie.name())) {
                CasSession session = newSessionFromToken(cookie.value());
                logger.info((String.format("Got CAS ticket!")));
                return session;
            }
        }
        logger.error("Cas Response: " + casResponse.toString());
        throw new RuntimeException(String.format("%s cookie not in CAS authentication response!", config.getjSessionName()));
    }

    private Request withCsrfAndCallerId(Request req) {
        return req.toBuilder()
                .setHeader("Caller-Id", config.getCallerId())
                .setHeader("CSRF", config.getCsrf())
                .addOrReplaceCookie(new DefaultCookie("CSRF", config.getCsrf()))
                .build();
    }

    private Request buildSTRequest(Response response) {
        logger.info("TGT RESPONSE CODE ->" + response.getStatusCode());
        final String serviceUrl = String.format("%s%s",
                config.getServiceUrl(),
                config.getServiceUrlSuffix()
        );
        return withCsrfAndCallerId(new RequestBuilder()
                .setUrl(tgtLocationFromResponse(response))
                .setMethod("POST")
                .addFormParam("service", serviceUrl)
                .build());
    }

    private Request buildTgtRequest() {
        return withCsrfAndCallerId(new RequestBuilder()
                .setUrl(String.format("%s/v1/tickets", config.getCasUrl()))
                .setMethod("POST")
                .addFormParam("username", config.getUsername())
                .addFormParam("password", config.getPassword())
                .build());
    }

    public CasSessionFetchProcess sessionRequest(CasSessionFetchProcess currentSession) {
        logger.info("STARTING TO FETCH SESSION FROM CAS.");
        CompletableFuture<CasSession> responsePromise = asyncHttpClient.executeRequest(buildTgtRequest())
                .toCompletableFuture().thenCompose(response -> asyncHttpClient.executeRequest(buildSTRequest(response)).toCompletableFuture()).thenCompose(response -> {
                    logger.info("st response: " + response.toString());
                    logger.info("ST RESPONSE CODE ->" + response.getStatusCode());
                    Request req = withCsrfAndCallerId(new RequestBuilder()
                            .setUrl(config.getSessionUrl())
                            .setMethod("GET")
                            .addQueryParam("ticket", ticketFromResponse(response))
                            .build());
                    logger.info((String.format("ticket request to url: %s", req.getUrl())));
                    return asyncHttpClient.executeRequest(req).toCompletableFuture();
                }).thenApply(this::sessionFromResponse);

        final CasSessionFetchProcess newFetchProcess = new CasSessionFetchProcess(responsePromise);

        if (sessionStore.compareAndSet(currentSession, newFetchProcess)) {
            return newFetchProcess;
        } else {
            responsePromise.cancel(true);
            return sessionStore.get();
        }
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

    private CompletableFuture<Response> executeRequestWithSession(CasSession session, Request request) {
        return asyncHttpClient.executeRequest(withCsrfAndCallerId(request.toBuilder()
                .addOrReplaceCookie(new DefaultCookie(config.getjSessionName(), session.getSessionCookie()))
                .build())).toCompletableFuture();
    }

    private CompletableFuture<Response> executeRequestWithReusedSession(CasSessionFetchProcess currentSessionProcess, CasSession session, Request request) {
        return executeRequestWithSession(session, request).thenApply(onSuccessIncreaseSessionTime(currentSessionProcess, session));
    }

    public CompletableFuture<CasSession> getSession() {
        logger.info("GETSESSSION CALLED!");
        final CasSessionFetchProcess currentSession = sessionStore.get();
        logger.info("CURRENTSESSION: " + currentSession.toString());
        return currentSession.getSessionProcess()
                .thenCompose(session ->
                        {
                            logger.info("is sesssion valid: " + session.isValid());
                            return session.isValid() ? currentSession.getSessionProcess() :
                                    sessionRequest(currentSession).getSessionProcess();
                        }
                );
    }

    public CompletableFuture<Response> execute(Request request) {
        final CasSessionFetchProcess currentSession = sessionStore.get();
        return currentSession.getSessionProcess()
                .thenCompose(session ->
                        session.isValid() ? executeRequestWithReusedSession(currentSession, session, request) :
                                sessionRequest(currentSession).getSessionProcess().thenCompose(newSession -> executeRequestWithSession(newSession, request)));
    }

    public Response executeBlocking(Request request) throws ExecutionException {
        try {
            return execute(request).get();
        } catch (Exception e) {
            throw new ExecutionException(String.format("Failed to execute blocking request: %s", request.getUrl()), e);
        }
    }

    public CompletableFuture<Response> executeWithServiceTicket(Request request) {
        return asyncHttpClient.executeRequest(buildTgtRequest())
                .toCompletableFuture().thenCompose(response -> asyncHttpClient.executeRequest(buildSTRequest(response)).toCompletableFuture())
                .thenCompose(response -> {
                    if (response.getStatusCode() == 302 || response.getStatusCode() == 401) {
                        return execute(request);
                    }
                    logger.info("st response: " + response.toString());
                    logger.info("ST RESPONSE CODE ->" + response.getStatusCode());
                    Request req = withCsrfAndCallerId(request.toBuilder()
                            .addQueryParam("ticket", ticketFromResponse(response))
                            .build());
                    logger.info((String.format("ticket request to url: %s", req.getUrl())));
                    return asyncHttpClient.executeRequest(req).toCompletableFuture();
                });
    }

    public Response executeWithServiceTicketBlocking(Request request) throws ExecutionException {
        try {
            return executeWithServiceTicket(request).get();
        } catch (Exception e) {
            throw new ExecutionException(String.format("Failed to execute blocking request with service ticket: %s", request.getUrl()), e);
        }
    }

    private CompletableFuture<Response> fetchValidationResponse(String service, String ticket) {
        logger.info("Fetching serviceValidate: " + ticket + " , service: " + service);
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
            logger.info("Validated!");
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
