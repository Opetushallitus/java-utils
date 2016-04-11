package fi.vm.sade.authentication.cas.httpsessionbased;

import com.google.common.cache.*;
import fi.vm.sade.authentication.cas.CasClient;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.cas.authentication.CasAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Is responsible for getting authenticated http session cookies for targetService.
 * Is used to make authenticated service http calls to targetService.
 * Obtaining new cookies is blocked/synchronized per user.
 *
 * <pre>
 *
 * Sequence:
 * =========
 *
 * (- client asks cookies for targetService for userX)
 * - BlockingAuthCookieCache gets CAS ticket
 * - BlockingAuthCookieCache opens and authenticates http session to targetService using the ticket
 * - BlockingAuthCookieCache obtaining cookies from the session, and caches them using userX_targetService as cache key
 * - BlockingAuthCookieCache gives cookies to client
 * (-clients, attaches cookies to http request so it can do authenticated call to targetService)
 * - BlockingAuthCookieCache expires tickets after some time
 *
 * Usage (pseudo code):
 * ====================
 *
 * authenticatedCookies = BlockingAuthCookieCache.getAuthenticatedCookiesForProxyAuth(securityContext.getAuthentication(), "http://virkailija.opintopolku.fi/koodisto-service");
 * OR
 * authenticatedCookies = BlockingAuthCookieCache.getAuthenticatedCookiesForServiceUser("testuser", currentSessionId, "http://virkailija.opintopolku.fi/koodisto-service");
 * httpRequest.setCookies(authenticatedCookies);
 * String someSecretData = httpRequest.execute();
 *
 * Configuring:
 * ============
 *
 * maxAgeSeconds - default 10 minutes
 * maxCacheSize - default 100 000
 * casUrl
 *
 * NOTE!
 * =====
 *
 * - not replicated (and shouldn't be a must)
 *
 * </pre>
 *
 * @author Antti Salonen
 */
public class BlockingAuthCookieCache {

    private static final Logger log = LoggerFactory.getLogger(BlockingAuthCookieCache.class);
    private static LoadingCache<CacheKey, List<String>> cookiesCache; // todo: pitääkö cachen olla singleton/staattinen?

    private String casUrl;
    private int maxAgeSeconds = 10 * 60;
    private int maxCacheSize = 100000;
    private long removed = 0;

    public BlockingAuthCookieCache(String casUrl, int maxAgeSeconds, int maxCacheSize) {
        this.casUrl = casUrl;
        this.maxAgeSeconds = maxAgeSeconds;
        this.maxCacheSize = maxCacheSize;
        initCache();
    }

    /**
     * @param serviceUser service user name
     * @param callerContext Context of the caller, example current user's session id, can be also null
     * @param targetService target service url (with /j_security.. stuff)
     */
    public List<String> getAuthenticatedCookiesForServiceUser(String serviceUser, String servicePass, String callerContext, String targetService) {
        CacheKey cachekey = new CacheKey(serviceUser, servicePass, callerContext, targetService);
        log.debug("get authenticated session for service user... cachekey: "+cachekey);
        List<String> result = cookiesCache.getUnchecked(cachekey);
        log.debug("got authenticated session for service user, cachekey: " + cachekey + ", cookies: " + result);
        return result;
    }

    /**
     * @param currentUser current user's spring authentication object
     * @param callerContext Context of the caller, example current user's session id, can be also null
     * @param targetService target service url (with /j_security.. stuff)
     */
    public List<String> getAuthenticatedCookiesForProxyAuth(Authentication currentUser, String callerContext, String targetService) {
        CacheKey cachekey = new CacheKey(currentUser, null, callerContext, targetService);
        log.debug("got authenticated session for proxy auth, cachekey: "+cachekey);
        List<String> result = cookiesCache.getUnchecked(cachekey);
        log.debug("got authenticated session for proxy auth, cachekey: "+cachekey+", cookies: "+result);
        return result;
    }

    private void initCache() {
        log.info("init cookiesCache, maxAgeSeconds: " + maxAgeSeconds + ", maxCacheSize: " + maxCacheSize);
        cookiesCache = CacheBuilder.newBuilder()
                .maximumSize(maxCacheSize)
                .expireAfterWrite(maxAgeSeconds, TimeUnit.SECONDS)
                .removalListener(new RemovalListener<CacheKey, List<String>>() {
                    @Override
                    public void onRemoval(RemovalNotification<CacheKey, List<String>> notif) {
                        log.info("removed authenticated session cookies from cache, cachekey: "+notif.getKey()+", cookies: "+notif.getValue());
                        removed++;
                    }
                })
                .build(
                        new CacheLoader<CacheKey, List<String>>() {
                            public List<String> load(CacheKey cachekey) throws Exception {
                                log.info("blocking loading authenticated session... cachekey: "+cachekey);
                                List<String> result = getCasTicketAndOpenTargetServiceSessionWithIt(cachekey);
                                log.info("blocking loaded authenticated session, cachekey: "+cachekey+", cookies: "+result);
                                return result;
                            }
                        });
    }

    private List<String> getCasTicketAndOpenTargetServiceSessionWithIt(CacheKey cachekey) {

        // obtain new ticket
        String ticket = obtainTicket(cachekey);
        log.info("obtained new ticket: "+ticket+", cachekey: "+cachekey);

        // open session
        List<String> authenticatedCookies = openSessionAndGetCookies(cachekey, ticket);
        log.info("opened new authenticated session, ticket: "+ticket+", cachekey: "+cachekey+", cookies: "+authenticatedCookies);

        // now we got 'em
        return authenticatedCookies;
    }

    protected List<String> openSessionAndGetCookies(CacheKey cachekey, String ticket) {
        String url = cachekey.targetService.replaceAll("/j_spring_cas_security_check", "") + "/buildversion.txt?auth&ticket="+ticket; // todo: auth init url konffattavaksi
//        String url = cachekey.targetService+"?ticket="+ticket; // oikeastaan juuri */j_spring_cas_security_check -urlin pitäisi osata ticket -pyyntö käsitellä - eipä anna cookieita

        DefaultHttpClient httpClient = new DefaultHttpClient();
        try {
            HttpGet request = new HttpGet(url);
            HttpResponse response = httpClient.execute(request);
            Header[] cookieHeaders = response.getHeaders("Set-Cookie");
            List<String> cookies = new ArrayList<String>();
            for (Header cookieHeader : cookieHeaders) {
                //System.out.println("    HEADER: "+cookieHeader.getName()+" ===> "+cookieHeader.getValue()); name aina Set-Cookie, valuessa itse cookie
                cookies.add(cookieHeader.getValue());
            }
            request.releaseConnection();

            if (cookies.isEmpty()) {
                throw new RuntimeException("error opening authenticated session, got no cookies! url: "+url+", cachekey: "+cachekey);
            }

            return cookies;
        } catch (IOException e) {
            throw new RuntimeException("error opening authenticated session, url: "+url+", cachekey: "+cachekey, e);
        }
    }

    public long size() {
        return cookiesCache.size();
    }

    public long removed() {
        return removed;
    }

    //

    public static class CacheKey {
        public final Object user;
        private transient final String servicePass; // note! no password in key/equals/toString/cache, only used when getting new ticket
        public final String callerContext;
        public final String targetService;
        public final String key;
        public CacheKey(Object user, String servicePass, String callerContext, String targetService) {
            if (user == null) throw new NullPointerException("cannot get authenticated session for null user, callerContext: "+callerContext+", targetService: "+targetService);
            this.user = user;
            this.servicePass = servicePass;
            this.callerContext = callerContext;
            this.targetService = targetService;
            key = String.format("u:%s_cc:%s_ts:%s", user instanceof Authentication ? ((Authentication)user).getName() : user, callerContext, targetService);
        }
        public boolean isProxyAuth() {
            return user instanceof Authentication;
        }
        @Override
        public boolean equals(Object o) {
            return o instanceof CacheKey && key.equals(((CacheKey) o).key);
        }
        @Override
        public int hashCode() {
            return key.hashCode();
        }
        @Override
        public String toString() {
            return key;
        }
    }

    protected String obtainTicket(CacheKey cachekey) {
        try {
            if (cachekey.isProxyAuth()) {
                return obtainNewCasProxyTicket(cachekey.targetService, (Authentication)cachekey.user);
            } else {
                return CasClient.getTicket(casUrl, (String)cachekey.user, cachekey.servicePass, cachekey.targetService);
            }
        } catch (Throwable e) {
            throw new RuntimeException("authentication to CAS failed, cachekey: "+cachekey+", error: "+e, e);
        }
    }

    // copy paste from old ProxyAuthenticator
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

}
