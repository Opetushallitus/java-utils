package fi.vm.sade.javautils.nio.cas;

import fi.vm.sade.javautils.nio.cas.impl.CasClientImpl;
import fi.vm.sade.javautils.nio.cas.impl.CasSessionFetcher;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;

import java.util.concurrent.ThreadFactory;

import static org.asynchttpclient.Dsl.asyncHttpClient;

public class CasClientBuilder {

    public static CasClient buildFromConfigAndHttpClient(CasConfig config, AsyncHttpClient asyncHttpClient) {
        CasSessionFetcher casSessionFetcher =
        new CasSessionFetcher(
                config,
                asyncHttpClient,
                config.getSessionTicketValidMs(),
                config.getTicketGrantingTicketValidMs());
        return new CasClientImpl(config, asyncHttpClient, casSessionFetcher);
    }

    public static CasClient build(CasConfig config) {
        ThreadFactory factory = new BasicThreadFactory.Builder()
                .namingPattern("async-cas-client-thread-%d")
                .daemon(true)
                .priority(Thread.NORM_PRIORITY)
                .build();

        return buildFromConfigAndHttpClient(config, asyncHttpClient(new DefaultAsyncHttpClientConfig.Builder()
                .setThreadFactory(factory)
                .build()));
    }

}
