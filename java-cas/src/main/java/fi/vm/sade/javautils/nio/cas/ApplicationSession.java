package fi.vm.sade.javautils.nio.cas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class ApplicationSession {
    private static final Logger logger = LoggerFactory.getLogger(fi.vm.sade.javautils.nio.cas.ApplicationSession.class);

    private final CasClient casClient;
    private final CasConfig casConfig;

    public ApplicationSession(CasConfig casConfig) {
        this.casConfig = casConfig;
        this.casClient = new CasClient(this.casConfig);
    }

    public CompletableFuture<CasSession> getSession() {
        return this.casClient.getSession();
    }

    public CasSession getSessionBlocking() {
        try {
            return getSession().get();
        } catch (Exception e) {
            logger.error("Failed to fetch CAS session" , e);
            throw new RuntimeException("Failed to fetch CAS session.", e);
        }
    }
}

