package fi.vm.sade.javautils.nio.cas.impl;

import fi.vm.sade.javautils.nio.cas.CasClient;
import fi.vm.sade.javautils.nio.cas.CasConfig;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Request;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

public class CasClientImpl implements CasClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(CasClientImpl.class);
    private final CasConfig config;
    private final AsyncHttpClient asyncHttpClient;
    private final CasSessionFetcher casSessionFetcher;
    private final CasUtils utils;

    public CasClientImpl(CasConfig config,
                         AsyncHttpClient asyncHttpClient,
                         CasSessionFetcher casSessionFetcher) {
        this.config = config;
        this.utils = new CasUtils(this.config);
        this.asyncHttpClient = asyncHttpClient;
        this.casSessionFetcher = casSessionFetcher;
    }

    private CompletableFuture<Response> executeWithSession(Request request) {
        return this.casSessionFetcher.fetchSessionToken()
                .thenCompose(session -> {
                    this.asyncHttpClient.getConfig().getCookieStore().clear();
                    Request requestWithSession =
                            utils.withCallerIdAndCsrfHeader(request.toBuilder())
                                    .addOrReplaceCookie(new DefaultCookie(config.getjSessionName(), session))
                                    .build();
                    return this.asyncHttpClient.executeRequest(requestWithSession).toCompletableFuture();
                });
    }

    private CompletableFuture<Response> retryConditionally(Request request, Response response, int numberOfRetries) {
        if (401 == response.getStatusCode()) {
            LOGGER.warn(String.format("Retrying request %s (response status code = %s)", request.getUrl(), response.getStatusCode()));
            this.casSessionFetcher.clearSessionStore();
            this.casSessionFetcher.clearTgtStore();
            return executeWithRetries(request, numberOfRetries - 1);
        } else if (utils.shouldReauthenticate(response)) {
            LOGGER.info(String.format("Response indicates that we need to reauthenticate with service. Clearing ticket stores and retrying request %s (response status code = %s)", request.getUrl(), response.getStatusCode()));
            this.casSessionFetcher.clearSessionStore();
            this.casSessionFetcher.clearTgtStore();
            return executeWithRetries(request, numberOfRetries - 1);
        } else {
            return CompletableFuture.completedFuture(response);
        }
    }

    private CompletableFuture<Response> retryConditionally(Request request, Throwable exception, int numberOfRetries) {
        LOGGER.warn(String.format("Retrying request %s on exception!", request.getUrl()), exception);
        return executeWithRetries(request, numberOfRetries - 1);
    }

    private static class Either {
        public final Response response;
        public final Throwable throwable;

        public Either(Response r, Throwable t) {
            this.response = r;
            this.throwable = t;
        }
    }

    private CompletableFuture<Response> executeWithRetries(Request request, int numberOfRetries) {
        CompletableFuture<Response> execution = executeWithSession(request);
        if (numberOfRetries < 1) {
            return execution;
        } else {
            return execution.handle(Either::new).thenCompose(entry -> {
                if (entry.throwable != null) {
                    return retryConditionally(request, entry.throwable, numberOfRetries);
                } else if (utils.shouldReauthenticate(entry.response)) {
                    // Reauthentication with service is forced by
                    // clearing the underlying ticket stores of the CasSessionFetcher.
                    this.casSessionFetcher.clearTgtStore();
                    this.casSessionFetcher.clearSessionStore();
                    // Once ticket stores are cleared, try response again.
                    // Don't decrement numberOfRetries, as the redirect response
                    // should probably not be considered an error.
                    return this.execute
                } else {
                    return retryConditionally(request, entry.response, numberOfRetries);
                }
            });
        }
    }

    @Override
    public CompletableFuture<Response> execute(Request request) {
        return executeWithRetries(request, config.getNumberOfRetries());
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
        HashMap<String, String> oppijaAttributes = new HashMap<>();
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

    private CompletableFuture<Response> fetchValidationResponse(String service, String ticket) {
        Request req = utils.withCallerIdAndCsrfHeader()
                .setUrl(config.getCasUrl() + "/serviceValidate?ticket=" + ticket + "&service=" + service)
                .addQueryParam("ticket", ticket)
                .addQueryParam("service", service)
                .setMethod("GET").build();
        return asyncHttpClient.executeRequest(req).toCompletableFuture();
    }

    @Override
    public CompletableFuture<String> validateServiceTicketWithVirkailijaUsername(String service, String ticket) {
        return fetchValidationResponse(service, ticket).toCompletableFuture()
                .thenApply(this::getUsernameFromResponse);
    }

    @Override
    public CompletableFuture<HashMap<String, String>> validateServiceTicketWithOppijaAttributes(String service, String ticket) {
        return fetchValidationResponse(service, ticket).toCompletableFuture().thenApply(this::getOppijaAttributesFromResponse);
    }

}
