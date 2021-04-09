package fi.vm.sade.javautils.cas;

import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class CasHttpClient {
    private class SessionCookies {
        public final long created;
        public final String value;

        public SessionCookies(String token) {
            this.value = token;
            this.created = System.currentTimeMillis();
        }
    }

    private final AtomicReference<SessionCookies> TOKEN_STORE = new AtomicReference<>();

    private final OkHttpClient client;

    public CasHttpClient(OkHttpClient client) {
        this.client = client;
    }

    private boolean currentTokenCouldBeValid() {
        return TOKEN_STORE.get() != null;
    }

    public CompletableFuture<Response> callToFuture(Call call) {
        final CompletableFuture<Response> r = new CompletableFuture<>();
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                r.completeExceptionally(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                r.complete(response);
            }
        });
        return r;
    }

    private CompletableFuture<Response> fetchCasSession() {
        return null; // TODO
    }

    public CompletableFuture<Response> call(Request request) {
        return call(request, 0);
    }

    private CompletableFuture<Response> call(final Request request, final int tryNumber) {
        if (currentTokenCouldBeValid()) {
            Request requestWithSessionCookie = new Request.Builder(request).addHeader("Cookies", "TODO").build();
            return callToFuture(this.client.newCall(requestWithSessionCookie))
                    .thenCompose((response) -> {
                        boolean unauthorized = true; // TODO 302 redirect to CAS or 401
                        boolean shouldGiveUp = tryNumber > 1;
                        if (unauthorized && !shouldGiveUp) {
                            TOKEN_STORE.set(null);
                            return call(request, tryNumber + 1);
                        } else {
                            return CompletableFuture.completedFuture(response);
                        }
                    });
        } else {
            return fetchCasSession().thenCompose((response) -> {
                String setSessionCookie = null; // TODO
                TOKEN_STORE.set(new SessionCookies(setSessionCookie));
                if(currentTokenCouldBeValid()) {
                    return call(request, tryNumber);
                } else {
                    return CompletableFuture.failedFuture(new RuntimeException("Invalid session token from CAS. Should check credentials!"));
                }
            });
        }
    }
}