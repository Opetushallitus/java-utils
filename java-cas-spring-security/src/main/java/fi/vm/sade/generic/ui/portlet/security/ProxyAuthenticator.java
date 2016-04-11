package fi.vm.sade.generic.ui.portlet.security;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.cas.authentication.CasAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import fi.vm.sade.authentication.cas.DefaultTicketCachePolicy;
import fi.vm.sade.authentication.cas.TicketCachePolicy;
import fi.vm.sade.generic.PERA;

/**
 * @author Antti Salonen
 */
public class ProxyAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(ProxyAuthenticator.class);
    // private TicketCachePolicy ticketCachePolicy = new
    // SimpleTicketCachePolicy();
    private TicketCachePolicy ticketCachePolicy = new DefaultTicketCachePolicy();

    public void proxyAuthenticate(String casTargetService, String authMode, Callback callback) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {

            if (authentication != null && "dev".equals(authMode)) {
                proxyAuthenticateDev(callback, authentication);
            }

            else {
                proxyAuthenticateCas(casTargetService, callback, authentication);
            }

        } catch (Throwable e) {
            throw new RuntimeException("Could not attach security ticket to SOAP message, user: "
                    + (authentication != null ? authentication.getName() : "null") + ", authmode: " + authMode
                    + ", exception: " + e, e);
        }
    }

    protected void proxyAuthenticateCas(String casTargetService, Callback callback, Authentication authentication) {
        String proxyTicket = getCachedProxyTicket(casTargetService, authentication, callback);
        if (proxyTicket == null) {
            throw new BadCredentialsException("got null proxyticket, cannot attach to request, casTargetService: " + casTargetService
                    + ", authentication: " + authentication);
        } else {
            callback.setRequestHeader("CasSecurityTicket", proxyTicket);
            PERA.setProxyKayttajaHeaders(callback, authentication.getName());
            log.debug("attached proxyticket to request! user: " + authentication.getName() + ", ticket: " + proxyTicket);
        }
    }

    protected void proxyAuthenticateDev(Callback callback, Authentication authentication) {
        callback.setRequestHeader("CasSecurityTicket", "oldDeprecatedSecurity_REMOVE");
        String user = authentication.getName();
        String authorities = toString(authentication.getAuthorities());
        callback.setRequestHeader("oldDeprecatedSecurity_REMOVE_username", user);
        callback.setRequestHeader("oldDeprecatedSecurity_REMOVE_authorities", authorities);
        log.debug("DEV Proxy ticket! user: " + user + ", authorities: " + authorities);
    }

    public String getCachedProxyTicket(final String targetService, final Authentication authentication, final Callback callback) {
        return ticketCachePolicy.getCachedTicket(targetService, authentication, new TicketCachePolicy.TicketLoader() {
            @Override
            public String loadTicket() {
                String proxyTicket = obtainNewCasProxyTicket(targetService, authentication);
                if (callback != null) {
                    callback.gotNewTicket(authentication, proxyTicket);
                }
                return proxyTicket;
            }
        });
    }

    public void clearTicket(String casTargetService) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ticketCachePolicy.clearTicket(casTargetService, authentication);
    }

    protected String obtainNewCasProxyTicket(String casTargetService, Authentication authentication) {
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            throw new RuntimeException("current user is not authenticated");
        }
        String ticket = ((CasAuthenticationToken) authentication).getAssertion().getPrincipal()
                .getProxyTicketFor(casTargetService);
        if (ticket == null) {
            throw new NullPointerException(
                    "obtainNewCasProxyTicket got null proxyticket, there must be something wrong with cas proxy authentication -scenario! check proxy callback works etc, targetService: "
                            + casTargetService + ", user: " + authentication.getName());
        }
        return ticket;
    }

    private String toString(Collection<? extends GrantedAuthority> authorities) {
        StringBuilder sb = new StringBuilder();
        for (GrantedAuthority authority : authorities) {
            sb.append(authority.getAuthority()).append(",");
        }
        return sb.toString();
    }

    public static interface Callback {
        void setRequestHeader(String key, String value);

        void gotNewTicket(Authentication authentication, String proxyTicket);
    }

    public void setTicketCachePolicy(TicketCachePolicy ticketCachePolicy) {
        this.ticketCachePolicy = ticketCachePolicy;
    }
}
