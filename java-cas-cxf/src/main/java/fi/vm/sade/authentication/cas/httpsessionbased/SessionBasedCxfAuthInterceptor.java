package fi.vm.sade.authentication.cas.httpsessionbased;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.util.List;

/**
 * Cxf client interceptor that uses BlockingAuthCookieCache to authenticate the requests.
 * BlockingAuthCookieCache handles CAS tickets, session opening & authenticating, this interceptor only attaches cookies to http requests.
 *
 * USAGE:
 *
 * Set serviceUser/servicePass to use in application-as-a-user mode,
 * otherwise will use proxy authentication.
 *
 * @author Antti Salonen
 * @see BlockingAuthCookieCache
 */
@Deprecated //use instead CasFriendlyCxfInterceptor from java-utils/java-cas-cxf
public class SessionBasedCxfAuthInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger log = LoggerFactory.getLogger(SessionBasedCxfAuthInterceptor.class);
    public static final String COOKIE = "Cookie";
    public static final String COOKIE_SEPARATOR = "; ";
    private BlockingAuthCookieCache blockingAuthCookieCache;
    private String serviceUser;
    private String servicePass;

    public SessionBasedCxfAuthInterceptor(BlockingAuthCookieCache blockingAuthCookieCache, String serviceUser, String servicePass) {
        super(Phase.POST_PROTOCOL);
        this.blockingAuthCookieCache = blockingAuthCookieCache;
        this.serviceUser = serviceUser;
        this.servicePass = servicePass;
    }

    @Override
    public void handleMessage(Message message) throws Fault {
        boolean proxyAuthMode = serviceUser == null || serviceUser.trim().length() == 0;
        String targetService = getCasTargetService(getUrl(message));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // tällä ei välttämättä ole väliä, ja nullkin on käypä arvo, mutta jos callerContext ei vastaa kutsuvan käyttäjän clientServicen session id:tä tms
        // ..käytetään samaa cxf clientService->targetService -sessiota, vaikka käyttäjä kävisi välillä kirjautumassa ulos frontServicestä
        String callerContext = authentication != null ? authentication.getName() : serviceUser;

        List<String> authenticatedSessionCookies;
        if (proxyAuthMode) {
            authenticatedSessionCookies = blockingAuthCookieCache.getAuthenticatedCookiesForProxyAuth(authentication, callerContext, targetService);
        } else {
            authenticatedSessionCookies = blockingAuthCookieCache.getAuthenticatedCookiesForServiceUser(serviceUser, servicePass, callerContext, targetService);
        }

        writeCookiesToMessage(message, authenticatedSessionCookies);
    }

    private static String getUrl(Message message) {
        return (String) message.get(Message.ENDPOINT_ADDRESS);
    }

    public static void writeCookiesToMessage(Message message, List<String> authenticatedSessionCookies) {
        //message.setContextualProperty(Message.MAINTAIN_SESSION, true); // forces Cookies.writeToMessageHeaders to actually set cookies if using that

        HttpURLConnection httpURLConnection = (HttpURLConnection) message.get("http.connection");
        String oldCookieHeader = httpURLConnection.getRequestProperty(COOKIE);
        String newCookieHeader = toCookieString(authenticatedSessionCookies);
        httpURLConnection.setRequestProperty(COOKIE, newCookieHeader);

        log.info("wrote cookies to message, url: "+getUrl(message)+", oldCookieHeader: "+oldCookieHeader+", newCookieHeader: "+newCookieHeader);
    }

    public static String toCookieString(List<String> cookies) {
        StringBuilder cookieString = new StringBuilder();
        for (String cookie : cookies) {
            List<HttpCookie> httpCookies = HttpCookie.parse(cookie);
            for (HttpCookie httpCookie : httpCookies) {
                //System.out.println("        "+cookie+" ===> "+httpCookie.getName()+"="+httpCookie.getValue());
                cookieString.append(cookieString.length() > 0 ? COOKIE_SEPARATOR : "").append(httpCookie.getName()).append("=").append(httpCookie.getValue());
            }
        }
        return cookieString.toString();
    }

    /**
     * Get cas service from url string, get string before 4th '/' char. For
     * example:
     * <p/>
     * https://asd.asd.asd:8080/backend-service/asd/qwe/qwe2.foo?bar=asd --->
     * https://asd.asd.asd:8080/backend-service
     *
     * (copypaste from deprecated AbstractSecurityTicketOutInterceptor)
     */
    private static String getCasTargetService(String url) {
        return url.replaceAll("(.*?//.*?/.*?)/.*", "$1") + "/j_spring_cas_security_check";
    }

    public BlockingAuthCookieCache getBlockingAuthCookieCache() {
        return blockingAuthCookieCache;
    }
}
