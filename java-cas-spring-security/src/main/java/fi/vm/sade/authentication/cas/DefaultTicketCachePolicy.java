package fi.vm.sade.authentication.cas;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.HashMap;
import java.util.Map;

/**
 * Ticket cache policy that keeps cached ticket in user's http session context
 * (if using from spring webapp), otherwise in global (not static though) context
 * (with configurable expiration time).
 *
 * @author Antti Salonen
 */
public class DefaultTicketCachePolicy extends TicketCachePolicy {

    private static final Logger log = LoggerFactory.getLogger(DefaultTicketCachePolicy.class);
    private int globalTicketsTimeToLiveSeconds = 10*60; // 10 min default
    private Map<String, String> globalTickets = new HashMap<String, String>();
    private Map<String, Long> globalTicketsLoaded = new HashMap<String, Long>();

    @Override
    protected String getTicketFromCache(String cacheKey) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        String cachedTicket;
        if (requestAttributes != null) {
            cachedTicket = (String) requestAttributes.getAttribute(cacheKey, RequestAttributes.SCOPE_SESSION);
        } else {
            // expire?
            Long ticketLoaded = globalTicketsLoaded.get(cacheKey);
            if (ticketLoaded != null && ticketLoaded + (globalTicketsTimeToLiveSeconds *1000) < System.currentTimeMillis()) {
                globalTickets.remove(cacheKey);
                globalTicketsLoaded.remove(cacheKey);
                log.info("expired ticket from global expiring cache, cacheKey: "+cacheKey);
            }

            cachedTicket = globalTickets.get(cacheKey);
        }
        return cachedTicket;
    }


    @Override
    protected void putTicketToCache(String cacheKey, String ticket) {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            requestAttributes.setAttribute(cacheKey, ticket, RequestAttributes.SCOPE_SESSION);
            log.info("cached ticket to httpsession, cacheKey: "+cacheKey+", ticket: "+ticket);
        } else {
            globalTickets.put(cacheKey, ticket);
            globalTicketsLoaded.put(cacheKey, ticket != null ? System.currentTimeMillis() : null);
            log.info("cached ticket to global expiring cache, cacheKey: "+cacheKey+", ticket: "+ticket);
        }
    }

    public void setGlobalTicketsTimeToLiveSeconds(int globalTicketsTimeToLiveSeconds) {
        this.globalTicketsTimeToLiveSeconds = globalTicketsTimeToLiveSeconds;
    }
}
