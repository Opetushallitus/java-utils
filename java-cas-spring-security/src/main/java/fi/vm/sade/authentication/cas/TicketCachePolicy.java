package fi.vm.sade.authentication.cas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Blocking cache for CAS tickets.
 * Blocks cache gets/loads per ticketKey.
 *
 * @author Antti Salonen
 */
public abstract class TicketCachePolicy {
    private static final Logger log = LoggerFactory.getLogger(TicketCachePolicy.class);

    protected abstract String getTicketFromCache(String cacheKey);
    protected abstract void putTicketToCache(String cacheKey, String ticket);
    public final String getCachedTicket(String targetService, Object authenticationOrUsername, TicketLoader ticketLoader) {
        Authentication auth = authenticationOrUsername instanceof Authentication ? (Authentication) authenticationOrUsername : new UsernamePasswordAuthenticationToken("" + authenticationOrUsername, null);
        String cacheKey = getCacheKey(targetService, auth.getName());
        log.info("blocking get ticket from cache... user: " + auth.getName() + ", cacheKey: "+cacheKey+", targetService: "+targetService+", thread: "+Thread.currentThread().getName());
        synchronized (cacheKey.intern()) {
            // get from cache
            String cachedTicket = this.getTicketFromCache(cacheKey);

            if (cachedTicket == null) {
                // get ticket
                cachedTicket = ticketLoader.loadTicket();
                log.warn("blocking loaded new ticket, user: " + auth.getName() + ", cacheKey: "+cacheKey+", ticket: " + cachedTicket+", targetService: "+targetService+", thread: "+Thread.currentThread().getName());
                if (cachedTicket == null) throw new NullPointerException("blocking loaded NULL ticket, user: " + auth.getName() + ", targetService: "+targetService);

                // put to cache
                this.putTicketToCache(cacheKey, cachedTicket);
            }

            else {
                log.warn("blocking got ticket from cache, user: " + auth.getName() + ", ticket: " + cachedTicket+", targetService: "+targetService+", thread: "+Thread.currentThread().getName());
            }

            return cachedTicket;
        }
    }

    public void clearTicket(String targetService, Object authenticationOrUsername) {
        Authentication auth = authenticationOrUsername instanceof Authentication ? (Authentication) authenticationOrUsername : new UsernamePasswordAuthenticationToken("" + authenticationOrUsername, null);
        String cacheKey = getCacheKey(targetService, auth.getName());
        synchronized (cacheKey.intern()) {
            this.putTicketToCache(cacheKey, null);
            log.info("clearTicket done, user: " + auth.getName() + ", targetService: "+targetService);
        }
    }

    protected String getCacheKey(String targetService, String user) {
        return "cachedTicket_" + targetService + "_"+user;
    }

    public static interface TicketLoader {
        String loadTicket();
    }

}

