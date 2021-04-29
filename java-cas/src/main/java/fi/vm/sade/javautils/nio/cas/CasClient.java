package fi.vm.sade.javautils.nio.cas;

import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import org.asynchttpclient.*;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static fi.vm.sade.javautils.nio.cas.CasSessionFetchProcess.emptySessionProcess;
import static org.asynchttpclient.Dsl.*;

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
  private final CasConfig config;
  private final AsyncHttpClient asyncHttpClient;
  private final AtomicReference<CasSessionFetchProcess> sessionStore =
    new AtomicReference<>(emptySessionProcess());
  private final long estimatedValidToken = TimeUnit.MINUTES.toMillis(15);

  public CasClient(CasConfig config) {
    this.config = config;
    this.asyncHttpClient = asyncHttpClient();
  }

  private String tgtLocationFromResponse(Response casResponse) {
    if (201 == casResponse.getStatusCode()) {
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
    return new CasSession(token, new Date(System.currentTimeMillis() + estimatedValidToken));
  }

  private CasSession sessionFromResponse(Response casResponse) {
    for (Cookie cookie : casResponse.getCookies()) {
      if (config.getjSessionName().equals(cookie.name())) {
        CasSession session = newSessionFromToken(cookie.value());
        return session;
      }
    }
    throw new RuntimeException(String.format("%s cookie not in CAS authentication response!", config.getjSessionName()));
  }

  private Request withCsrfAndCallerId(Request req) {
    return req.toBuilder()
      .setHeader("Caller-Id", config.getCallerId())
      .addOrReplaceCookie(new DefaultCookie("CSRF", config.getCsrf()))
      .build();
  }

  public CasSessionFetchProcess sessionRequest(CasSessionFetchProcess currentSession) {
    Request tgtReq = withCsrfAndCallerId(new RequestBuilder()
      .setUrl(String.format("%s/v1/tickets", config.getCasUrl()))
      .setMethod("POST")
      .addFormParam("username", config.getUsername())
      .addFormParam("password", config.getPassword())
      .build());
    final String serviceUrl = String.format("%s%s",
      config.getServiceUrl(),
      config.getServiceUrlSuffix()
    );
    CompletableFuture<CasSession> responsePromise = asyncHttpClient.executeRequest(tgtReq)
      .toCompletableFuture().thenCompose(response -> {

        Request req = withCsrfAndCallerId(new RequestBuilder()
          .setUrl(tgtLocationFromResponse(response))
          .setMethod("POST")
          .addFormParam("service", serviceUrl)
          .build());
        return asyncHttpClient.executeRequest(req).toCompletableFuture();
      }).thenCompose(response -> {

        Request req = withCsrfAndCallerId(new RequestBuilder()
          .setUrl(serviceUrl)
          .setMethod("GET")
          .addQueryParam("ticket", ticketFromResponse(response))
          .build());
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
          //System.out.println("Updated current session with more time");
        } else {
          //System.out.println("Tried to update more time to current session but some other thread was faster");
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

  public CompletableFuture<Response> execute(Request request) {
    final CasSessionFetchProcess currentSession = sessionStore.get();
    return currentSession.getSessionProcess()
      .thenCompose(session ->
        session.isValid() ? executeRequestWithReusedSession(currentSession, session, request) :
          sessionRequest(currentSession).getSessionProcess().thenCompose(newSession -> executeRequestWithSession(newSession, request)));
  }

}
