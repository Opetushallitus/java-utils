package fi.vm.sade.javautils.nio.cas.impl;

import fi.vm.sade.javautils.nio.cas.CasClient;
import fi.vm.sade.javautils.nio.cas.CasConfig;
import fi.vm.sade.javautils.nio.cas.exceptions.ServiceTicketException;
import fi.vm.sade.javautils.nio.cas.exceptions.TicketGrantingTicketException;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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

    private CompletableFuture<Response> executeWithSession(Request request, boolean retry) {
        return this.casSessionFetcher.fetchSessionToken()
                .handle(Either<String>::new)
                .thenCompose(session -> {
                    final Throwable t = session.throwable;
                    if (retry
                            && t instanceof ExecutionException
                            && t.getCause() != null
                            && (t.getCause() instanceof ServiceTicketException || t.getCause() instanceof TicketGrantingTicketException)) {
                        LOGGER.warn("Clearing stores and retrying executeWithSession, retry {}", retry, session.throwable);
                        this.casSessionFetcher.clearTgtStore();
                        this.casSessionFetcher.clearSessionStore();
                        return executeWithSession(request, false);
                    }
                    this.asyncHttpClient.getConfig().getCookieStore().clear();
                    Request requestWithSession =
                            utils.withCallerIdAndCsrfHeader(request.toBuilder())
                                    .addOrReplaceCookie(new DefaultCookie(config.getjSessionName(), session.value))
                                    .build();
                    return this.asyncHttpClient.executeRequest(requestWithSession).toCompletableFuture();
                });
    }

    private CompletableFuture<Response> retryConditionally(Request request, Response response, int numberOfRetries, Set<Integer> statusCodesToRetry) {
        if(statusCodesToRetry.contains(response.getStatusCode())) {
            LOGGER.warn(String.format("Retrying request %s (response status code = %s)", request.getUrl(), response.getStatusCode()));
            this.casSessionFetcher.clearSessionStore();
            this.casSessionFetcher.clearTgtStore();
            return executeWithRetries(request, numberOfRetries - 1, statusCodesToRetry);
        } else {
            return CompletableFuture.completedFuture(response);
        }
    }
    private CompletableFuture<Response> retryConditionally(Request request, Throwable exception, int numberOfRetries, Set<Integer> statusCodesToRetry) {
        LOGGER.warn(String.format("Retrying request %s on exception!", request.getUrl()), exception);
        return executeWithRetries(request, numberOfRetries - 1, statusCodesToRetry);
    }
    private static class Either<T> {
        public final T value;
        public final Throwable throwable;
        public Either(T v, Throwable t) {
            this.value = v;
            this.throwable = t;
        }
    }

    private CompletableFuture<Response> executeWithRetries(Request request, int numberOfRetries, Set<Integer> statusCodesToRetry) {
        CompletableFuture<Response> execution = executeWithSession(request, true);
        if(numberOfRetries < 1) {
            return execution;
        } else {
            return execution.handle(Either<Response>::new).thenCompose(entry -> {
                    if(entry.throwable != null) {
                        return retryConditionally(request, entry.throwable, numberOfRetries, statusCodesToRetry);
                    } else {
                        return retryConditionally(request, entry.value, numberOfRetries, statusCodesToRetry);
                    }
            });
        }
    }

    @Override
    public CompletableFuture<Response> execute(Request request) {
        return executeWithRetries(request, config.getNumberOfRetries(), Set.of(401));
    }

    @Override
    public CompletableFuture<Response> executeAndRetryWithCleanSessionOnStatusCodes(Request request, Set<Integer> statusCodesToRetry) {
        return executeWithRetries(request, config.getNumberOfRetries(), statusCodesToRetry);
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
                .setUrl(config.getCasUrl() + "/serviceValidate")
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
