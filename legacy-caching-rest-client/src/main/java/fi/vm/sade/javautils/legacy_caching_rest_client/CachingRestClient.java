package fi.vm.sade.javautils.legacy_caching_rest_client;

import static org.apache.commons.httpclient.HttpStatus.SC_BAD_REQUEST;
import static org.apache.commons.httpclient.HttpStatus.SC_FORBIDDEN;
import static org.apache.commons.httpclient.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.commons.httpclient.HttpStatus.SC_NOT_FOUND;
import static org.apache.commons.httpclient.HttpStatus.SC_UNAUTHORIZED;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

import fi.vm.sade.javautils.cas.CasClient;
import fi.vm.sade.javautils.legacy_cxf_cas.PERA;
import fi.vm.sade.javautils.legacy_cxf_cas.ui.portlet.security.ProxyAuthenticator;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.ProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.RedirectLocations;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Simple http client, that allows doing GETs to REST-resources so that http cache headers are respected.
 * Just a lightweight wrapper on top of apache commons-http and commons-http-cache.
 * Use get -method to do requests.
 *
 * Service-as-a-user authentication: set webCasUrl/casService/username/password
 *
 * Proxy authentication: set useProxyAuthentication=true + casService
 */
public class CachingRestClient implements HealthChecker {

    public static final String WAS_REDIRECTED_TO_CAS = "redirected_to_cas";
    public static final int DEFAULT_TIMEOUT_MS = 5 * 60 * 1000; // 5min
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final long DEFAULT_CONNECTION_TTL_SEC = 60; // infran palomuuri katkoo monta minuuttia makaavat connectionit
    public static final String CAS_SECURITY_TICKET = "CasSecurityTicket";
    private static final String CSRF = "CachingRestClient";
    private static final String CACHE_RESPONSE_STATUS = "http.cache.response.status"; //CachingHttpClient.CACHE_RESPONSE_STATUS
    protected static Logger logger = LoggerFactory.getLogger(CachingRestClient.class);
    private static ThreadLocal<DateFormat> df1 = new ThreadLocal<DateFormat>(){
        protected DateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm");
        }
    };
    private static ThreadLocal<DateFormat> df2 = new ThreadLocal<DateFormat>(){
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd");
        }
    };
    private boolean reuseConnections = true;

    private HttpClient cachingClient;
    private ThreadLocal<HttpContext> localContext = new ThreadLocal<HttpContext>(){
        @Override
        protected HttpContext initialValue() {
            return new BasicHttpContext();
        }
    };
    //private HttpResponse response;
    private Object cacheStatus;  //used in tests
    private Gson gson;

    private String webCasUrl;
    private String username;
    private String password;
    private String casService;
    protected String serviceAsAUserTicket;
    private ProxyAuthenticator proxyAuthenticator;
    private boolean useProxyAuthentication = false;
    @Value("${auth.mode:cas}")
    private String proxyAuthMode;
    private String requiredVersionRegex;
    private final int timeoutMs;
    private final String callerId;
    private boolean allowUrlLogging;
    private HashMap<String, Boolean> csrfCookiesCreateForHost = new HashMap<String, Boolean>();
    private final CookieStore cookieStore;

    public CachingRestClient(String callerId) {
        this(callerId, DEFAULT_TIMEOUT_MS, DEFAULT_CONNECTION_TTL_SEC);
    }

    public CachingRestClient(String callerId, int timeoutMs) {
        this(callerId, timeoutMs, DEFAULT_CONNECTION_TTL_SEC);
    }

    public CachingRestClient(String callerId, int timeoutMs, long connectionTimeToLiveSec) {
        this(callerId, timeoutMs, connectionTimeToLiveSec, true);
    }

    public CachingRestClient(String callerId, int timeoutMs, long connectionTimeToLiveSec, boolean allowUrlLogging) {
        this.callerId = callerId;
        this.timeoutMs = timeoutMs;
        this.allowUrlLogging = allowUrlLogging;
        final DefaultHttpClient actualClient = createDefaultHttpClient(timeoutMs, connectionTimeToLiveSec);

        actualClient.setRedirectStrategy(new DefaultRedirectStrategy(){
            // detect redirects to cas
            @Override
            public URI getLocationURI(HttpRequest request, HttpResponse response, HttpContext context) throws ProtocolException {
                URI locationURI = super.getLocationURI(request, response, context);
                String uri = locationURI.toString();
                if (isCasUrl(uri)) {
                    logger.debug("set redirected_to_cas=true, url: " + uri);
                    context.setAttribute(WAS_REDIRECTED_TO_CAS, "true");
                    clearRedirects();
                } else { // when redirecting back to service _from_ cas
                    logger.debug("set redirected_to_cas=false, url: " + uri);
                    context.removeAttribute(WAS_REDIRECTED_TO_CAS);
                }
                return locationURI;
            }
        });

        if (!reuseConnections) { // hidastaa?
            actualClient.setReuseStrategy(new NoConnectionReuseStrategy());
        }

        cookieStore = actualClient.getCookieStore();
        cachingClient = initCachingClient(actualClient);

        initGson();
    }

    public static DefaultHttpClient createDefaultHttpClient(int timeoutMs, long connectionTimeToLiveSec) {
        // multithread support + max connections
        PoolingClientConnectionManager connectionManager;
        connectionManager = new PoolingClientConnectionManager(SchemeRegistryFactory.createDefault(), connectionTimeToLiveSec, TimeUnit.MILLISECONDS);
        connectionManager.setDefaultMaxPerRoute(100); // default 2
        connectionManager.setMaxTotal(1000); // default 20

        // init stuff
        final DefaultHttpClient actualClient = new DefaultHttpClient(connectionManager);

        HttpParams httpParams = actualClient.getParams();
        HttpConnectionParams.setConnectionTimeout(httpParams, timeoutMs);
        HttpConnectionParams.setSoTimeout(httpParams, timeoutMs);
        HttpConnectionParams.setSoKeepalive(httpParams, true); // prevent firewall to reset idle connections?
        return actualClient;
    }

    public static HttpClient initCachingClient(DefaultHttpClient actualClient) {
        try {
            org.apache.http.impl.client.cache.CacheConfig cacheConfig = new org.apache.http.impl.client.cache.CacheConfig();
            cacheConfig.setMaxCacheEntries(50 * 1000);
            cacheConfig.setMaxObjectSize(10 * 1024 * 1024); // 10M, eg oppilaitosnumero -koodisto is 7,5M
            return new org.apache.http.impl.client.cache.CachingHttpClient(actualClient, cacheConfig);
        } catch (Throwable e) {
            logger.error("ERROR creating CachingRestClient, httpclient-cache jar missing? falling back to non-cached http client - "+e, e);
            return actualClient;
        }
    }

    private void initGson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(XMLGregorianCalendar.class, new JsonDeserializer<XMLGregorianCalendar>() {

            @Override
            public XMLGregorianCalendar deserialize(final JsonElement json, final Type typeOfT, final JsonDeserializationContext context)
                    throws JsonParseException {
                String string = json.getAsString();
                try {
                 return parseXmlGregorianCalendar(string);
                } catch (Throwable t){
                    return null;
                }
            }

        });
        gsonBuilder.registerTypeAdapter(Date.class, new JsonDeserializer<Date>() {
            @Override
            public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                return new Date(json.getAsJsonPrimitive().getAsLong());
            }
        });
        gson = gsonBuilder.create();
    }

    private boolean isCasUrl(String uri) {
        return uri != null && (uri.endsWith("/cas") || uri.contains("/cas/") || uri.contains("/cas?"));
    }

    /**
     * get REST Json resource as Java object of type resultType (deserialized with gson).
     * Returns null if error occurred while querying resource.
     */
    public <T> T get(String url, Class<? extends T> resultType) throws IOException {
        InputStream is = null;
        String response = null;
        try {
            is = get(url);
            response = IOUtils.toString(is);
            T t = fromJson(resultType, response);
            return t;
        } finally {
            if(is != null) {
                is.close();
            }
        }
    }

    public String getAsString(String url) throws IOException {
        return IOUtils.toString(get(url));
    }

    private <T> T fromJson(Class<? extends T> resultType, String response) throws IOException {
        try {
            return gson.fromJson(response, resultType);
        } catch (JsonSyntaxException e) {
            throw new IOException("failed to parse object from (json) response, type: "+resultType.getSimpleName()+", reason: "+e.getCause()+", response:\n"+response);
        }
    }

    /**
     * get REST JSON resource as string.
     */
    public InputStream get(String url) throws IOException {
        HttpGet req = new HttpGet(url);
        HttpResponse response = execute(req, null, null);
        HttpEntity responseEntity = response.getEntity();
        if (responseEntity == null) {
            logAndThrowHttpException(req, response, "request did not return any content");
        }
        return responseEntity.getContent();
    }

    private boolean wasRedirectedToCas() {
        return "true".equals(localContext.get().getAttribute(WAS_REDIRECTED_TO_CAS));
    }

    protected boolean authenticate(final HttpRequestBase req) throws IOException {
        synchronized (this) {
            if (useServiceAsAUserAuthentication()) {
                if (serviceAsAUserTicket == null) {
                    checkNotNull(username, "username");
                    checkNotNull(password, "password");
                    checkNotNull(webCasUrl, "webCasUrl");
                    checkNotNull(casService, "casService");
                    serviceAsAUserTicket = obtainNewCasServiceAsAUserTicket();
                    logger.info("got new serviceAsAUser ticket, service: " + casService + ", ticket: " + serviceAsAUserTicket);
                }
                req.setHeader(CAS_SECURITY_TICKET, serviceAsAUserTicket);
                PERA.setKayttajaHeaders(req, getCurrentUser(), username);
                logger.debug("set serviceAsAUser ticket to header, service: " + casService + ", ticket: " + serviceAsAUserTicket + ", currentUser: " + getCurrentUser() + ", callAsUser: " + username);
                return true;
            } else if (useProxyAuthentication) {
                checkNotNull(webCasUrl, "webCasUrl");
                checkNotNull(casService, "casService");
                if (proxyAuthenticator == null) {
                    proxyAuthenticator = new ProxyAuthenticator();
                }
                final boolean[] gotNewProxyTicket = {false};
                proxyAuthenticator.proxyAuthenticate(casService, proxyAuthMode, new ProxyAuthenticator.Callback() {
                    @Override
                    public void setRequestHeader(String key, String value) {
                        req.setHeader(key, value);
                        logger.debug("set http header: " + key + "=" + value);
                    }

                    @Override
                    public void gotNewTicket(Authentication authentication, String proxyTicket) {
                        logger.info("got new proxy ticket, service: " + casService + ", ticket: " + proxyTicket);
                        gotNewProxyTicket[0] = true;
                    }
                });
                return gotNewProxyTicket[0];
            }

            return false;
        }
    }

    private void checkNotNull(String value, String name) {
        if (value == null) throw new NullPointerException("CachingRestClient."+name+" is null, and guess what, it shouldn't!");
    }

    /*
    private void addRequestParameter(HttpRequestBase req, String key, String value) {
        URIBuilder builder = new URIBuilder(req.getURI()).setParameter(key, value);
        try {
            req.setURI(builder.build());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
    */

    private boolean useServiceAsAUserAuthentication() {
        return username != null;
    }

    protected String obtainNewCasServiceAsAUserTicket() throws IOException {
        return CasClient.getTicket(webCasUrl + "/v1/tickets", username, password, casService);
    }

    public String postForLocation(String url, String content) throws IOException {
        return postForLocation(url, "application/json", content);
    }

    public String postForLocation(String url, String contentType, String content) throws IOException {
        HttpRequestBase request = new HttpPost(url);
        HttpResponse response = execute(request, contentType, content);
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED) {
            return response.getFirstHeader("Location").getValue();
        } else {
            throw new RuntimeException("post didn't result in http 201 created: " + info(request, response));
        }
    }

    public HttpResponse post(String url, String contentType, String content) throws IOException {
        return execute(new HttpPost(url), contentType, content);
    }

    public HttpResponse put(String url, String contentType, String content) throws IOException {
        return execute(new HttpPut(url), contentType, content);
    }

    public HttpResponse delete(String url) throws IOException {
        return execute(new HttpDelete(url), null, null);
    }

    public HttpResponse execute(HttpRequestBase req, String contentType, String postOrPutContent) throws IOException {
        return execute(req, contentType, postOrPutContent, 0);
    }

    public HttpResponse execute(HttpRequestBase req, String contentType, String postOrPutContent, int retry) throws IOException {
        // prepare
        if (req.getURI().toString().startsWith("/") && casService != null) { // if relative url
            try {
                req.setURI(new URIBuilder(casService.replace("/j_spring_cas_security_check", "") + req.getURI().toString()).build());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        String url = req.getURI().toString();
        if (req.getURI().getHost() == null) throw new NullPointerException("CachingRestClient.execute ERROR! host is null, req.uri: "+url);
        if (contentType != null) {
            req.setHeader("Content-Type", contentType);
        }
        if(this.callerId != null) {
            req.setHeader("Caller-Id", this.callerId);
        }
        req.setHeader("CSRF",CSRF);
        ensureCSRFCookie(req);

        if (postOrPutContent != null && req instanceof HttpEntityEnclosingRequestBase) {
            ((HttpEntityEnclosingRequestBase)req).setEntity(new StringEntity(postOrPutContent, UTF8));
        }

        boolean wasJustAuthenticated = false;
        try {
            wasJustAuthenticated = authenticate(req);
        } catch (ProxyAuthenticator.CasProxyAuthenticationException e) {
            if (retry == 0) {
                logger.warn("Failed to CAS authenticate. Renewing proxy ticket.");
                logger.debug("Failed to CAS authenticate. Renewing proxy ticket.", e);
            } else {
                logger.warn("Failed second time to CAS authenticate");
                logger.debug("Failed second time to CAS authenticate", e);
                // CAS didn't likely recognise TGT (One can't be completely sure since Cas20ProxyRetriever just returns null)
                throw new HttpException(req, getEmptyHttpResponse(SC_UNAUTHORIZED), e.getMessage());
            }
        }

        // do actual request
        HttpResponse response = null;
        String responseString = null;
        try {
            response = cachingClient.execute(req, localContext.get());
        } catch (Exception e) {
            logger.error("error in CachingRestClient - " + info(req, response, wasJustAuthenticated, wasJustAuthenticated, wasJustAuthenticated, retry), e);
            throw new IOException("Internal error calling "+req.getMethod()+"/"+url+" (check logs): "+e.getMessage());
        } finally {
            // after request, wrap response entity so it can be accessed later, and release the connection
            if (response != null && response.getEntity() != null) {
                responseString = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
                response.setEntity(new StringEntity(responseString, "UTF-8"));
            }
            req.releaseConnection();
        }

        // logging
        boolean isRedirCas = isRedirectToCas(response); // this response is 302 with location header pointing to cas
        boolean wasRedirCas = wasRedirectedToCas(); // this response is from cas after 302 redirect
        boolean isHttp401 = response.getStatusLine().getStatusCode() == SC_UNAUTHORIZED;
        if (logger.isDebugEnabled()) {
            logger.debug(info(req, response, wasJustAuthenticated, isRedirCas, wasRedirCas, retry));
            logger.debug("    responseString: {}", responseString);
        }

        // just got new valid ticket, but still got cas login page.. something wrong with the system, target service didn't process the request/ticket correctly?
        if (retry > 0 && wasJustAuthenticated && (isRedirCas || wasRedirCas)) {
            throw new IOException("just got new valid ticket, but still got cas login page.. something wrong with the system, target service didn't process the request/ticket correctly?\n"
                    +info(req, response, wasJustAuthenticated, isRedirCas, wasRedirCas, retry));
        }

        // authentication: was redirected to cas OR http 401 -> get ticket and retry once (but do it only once, hence 'retry')
        if (isRedirCas || wasRedirCas || isHttp401) {
            if (retry == 0) {
                logger.warn("warn! got redirect to cas or 401 unauthorized, re-getting ticket and retrying request");
                clearTicket();
                logger.debug("set redirected_to_cas=false");
                localContext.get().removeAttribute(WAS_REDIRECTED_TO_CAS);
                return execute(req, contentType, postOrPutContent, 1);
            } else {
                clearTicket();
                logAndThrowHttpException(req, response, "Unauthorized error calling REST resource, got redirect to cas or 401 unauthorized");
            }
        }

        if(response.getStatusLine().getStatusCode() == SC_FORBIDDEN) {
            logAndThrowHttpException(req, response, "Access denied error calling REST resource");
        }

        if(response.getStatusLine().getStatusCode() >= SC_INTERNAL_SERVER_ERROR) {
            logAndThrowHttpException(req, response, "Internal error calling REST resource");
        }

        if(response.getStatusLine().getStatusCode() >= SC_NOT_FOUND) {
            logAndThrowHttpException(req, response, "Not found error calling REST resource");
        }

        if(response.getStatusLine().getStatusCode() == SC_BAD_REQUEST) {
            logAndThrowHttpException(req, response, "Bad request error calling REST resource");
        }

        cacheStatus = localContext.get().getAttribute(CACHE_RESPONSE_STATUS);

        logger.debug("{}, url: {}, contentType: {}, content: {}, status: {}, headers: {}", new Object[]{req.getMethod(), url, contentType, postOrPutContent, response.getStatusLine(), Arrays.asList(response.getAllHeaders())});
        return response;
    }

    private HttpResponse getEmptyHttpResponse(int statusCode) {
        return new DefaultHttpResponseFactory()
                .newHttpResponse(new BasicStatusLine(HttpVersion.HTTP_1_1, statusCode, null), null);
    }

    private void ensureCSRFCookie(HttpRequestBase req) {
        String host = req.getURI().getHost();
        if (!csrfCookiesCreateForHost.containsKey(host)) {
            synchronized (csrfCookiesCreateForHost) {
                if (!csrfCookiesCreateForHost.containsKey(host)) {
                    csrfCookiesCreateForHost.put(host, true);
                    BasicClientCookie cookie = new BasicClientCookie("CSRF", CSRF);
                    cookie.setDomain(host);
                    cookie.setPath("/");
                    cookieStore.addCookie(cookie);
                }
            }
        }
    }

    private void logAndThrowHttpException(HttpRequestBase req, HttpResponse response, final String msg) throws CachingRestClient.HttpException {
        String message = msg + ", " + info(req, response);
        logger.error(message);
        throw new CachingRestClient.HttpException(req, response, message);
    }

    private String getUserInfo(HttpUriRequest req) {
        return header(req, "current", PERA.X_KUTSUKETJU_ALOITTAJA_KAYTTAJA_TUNNUS)
                + header(req, "caller", PERA.X_PALVELUKUTSU_LAHETTAJA_KAYTTAJA_TUNNUS)
                + header(req, "proxy", PERA.X_PALVELUKUTSU_LAHETTAJA_PROXY_AUTH)
                + header(req, "ticket", CAS_SECURITY_TICKET);
    }

    private String header(HttpUriRequest req, String info, String name) {
        Header[] headers = req.getHeaders(name);
        StringBuilder res = new StringBuilder();
        if (headers != null && headers.length > 0) {
            res.append("|").append(info).append(":");
            for (Header header : headers) {
                res.append(header.getValue());
            }
        }
        return res.toString();
    }

    private String info(HttpUriRequest req, HttpResponse response) {
        return "url: " + (allowUrlLogging ? req.getURI() : "hidden")
                + ", method: " + req.getMethod()
                + ", status: " + (response != null && response.getStatusLine() != null ? response.getStatusLine().getStatusCode() : "?")
                + ", userInfo: " + getUserInfo(req)
                + ", timeoutMs: " + timeoutMs;
    }

    private String info(HttpUriRequest req, HttpResponse response, boolean wasJustAuthenticated, boolean isRedirCas, boolean wasRedirCas, int retry) {
        return info(req, response)
                + ", isredircas: " + isRedirCas
                + ", wasredircas: " + wasRedirCas
                + ", wasJustAuthenticated: " + wasJustAuthenticated
                + ", retry: " + retry;
    }

    private String getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext() != null ? SecurityContextHolder.getContext().getAuthentication() : null;
        return authentication != null ? authentication.getName() : null;
    }

    /** will force to get new ticket next time */
    public void clearTicket() {
        synchronized (this) {
            serviceAsAUserTicket = null;
            if (useProxyAuthentication && proxyAuthenticator != null) {
                proxyAuthenticator.clearTicket(casService);
            }
        }
    }

    private void clearRedirects() {
        // clear redirects, because cas auth could cause same auth redirections again after new login/ticket. this will prevent CircularRedirectException
        localContext.get().setAttribute(DefaultRedirectStrategy.REDIRECT_LOCATIONS, new RedirectLocations());
        logger.info("cleared redirects");
    }

    private boolean isRedirectToCas(HttpResponse response) {
        Header location = response.getFirstHeader("Location");
        return location != null && isCasUrl(location.getValue());
    }

    public Object getCacheStatus() {
        return cacheStatus;
    }

    private XMLGregorianCalendar parseXmlGregorianCalendar(String string) {
        // long t = System.currentTimeMillis();
        if (string == null || string.isEmpty()) {
            return null;
        }

        final boolean hasSemicolon = string.indexOf(":") != -1;
        final boolean hasDash = string.indexOf("-") != -1;

        try {
            if (hasSemicolon) {
                GregorianCalendar cal = new GregorianCalendar();
                cal.setTime(df1.get().parse(string));
                return DatatypeFactory.newInstance().newXMLGregorianCalendar(cal);
            } else if (hasDash) {
                GregorianCalendar cal = new GregorianCalendar();
                cal.setTime(df2.get().parse(string));
                return DatatypeFactory.newInstance().newXMLGregorianCalendar(cal);
            } else {
                GregorianCalendar cal = new GregorianCalendar();
                cal.setTime(new Date(Long.parseLong(string)));
                return DatatypeFactory.newInstance().newXMLGregorianCalendar(cal);

            }
        } catch (Throwable th) {
            logger.warn("error parsing json to xmlgregoriancal: " + string);
        }
        return null;
    }

    public String getWebCasUrl() {
        return webCasUrl;
    }

    public void setWebCasUrl(String webCasUrl) {
        clearTicket();
        this.webCasUrl = webCasUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        clearTicket();
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        clearTicket();
        this.password = password;
    }

    public String getCasService() {
        return casService;
    }

    public void setCasService(String casService) {
        clearTicket();
        this.casService = casService;
    }

    /** Check health of this rest client */
    @Override
    public Object checkHealth() throws Throwable {
        if (casService != null) {

            // call target service's buildversion url (if we have credentials try the secured url)
            String serviceUrl = casService.replace("/j_spring_cas_security_check", "");
            final String buildversionUrl = serviceUrl + "/buildversion.txt" + (useServiceAsAUserAuthentication() ? "?auth" : "");
            final HttpResponse result = execute(new HttpGet(buildversionUrl), null, null);

            LinkedHashMap<String,Object> map = new LinkedHashMap<String,Object>() {{
                put("url", buildversionUrl);
                put("user", useServiceAsAUserAuthentication() ? username : useProxyAuthentication ? "proxy" : "anonymous");
                put("status", result.getStatusLine().getStatusCode() == 200 ? "OK" : result.getStatusLine());
                // todo: kuormitusdata?
            }};

            // kohdepalvelun healthcheck
            try {
                Map hc = get(serviceUrl+"/healthcheck", Map.class);
                Object targetserviceStatus = hc.get("status");
                if ("OK".equals(targetserviceStatus)) {
                    map.put("targetserviceHealthcheck", "OK");
                } else {
                    throw new Exception("targetserviceHealthcheck error: "+targetserviceStatus);
                }
            } catch (HttpException e) {
                if (e.getStatusCode() == 404) {
                    map.put("targetserviceHealthcheck", "not found");
                } else {
                    throw new Exception("targetserviceHealthcheck exception: "+e.getMessage());
                }
            }

            // mikäli kohdepalvelu ok, mutta halutaan varmistaa vielä sen versio
            if (result.getStatusLine().getStatusCode() == 200 && requiredVersionRegex != null) {
                Properties buildversionProps = new Properties();
                buildversionProps.load(result.getEntity().getContent());
                String version = buildversionProps.getProperty("version");
                if (!version.matches(requiredVersionRegex)) {
                    throw new Exception("wrong version: "+version+", required: "+ requiredVersionRegex+", service: "+casService);
                }
                map.put("version", version);
            }

            return map;
        } else {
            return "nothing to check, casService not configured";
        }
    }

    public boolean isUseProxyAuthentication() {
        return useProxyAuthentication;
    }

    public void setUseProxyAuthentication(boolean useProxyAuthentication) {
        this.useProxyAuthentication = useProxyAuthentication;
    }

    public ProxyAuthenticator getProxyAuthenticator() {
        return proxyAuthenticator;
    }

    public void setProxyAuthenticator(ProxyAuthenticator proxyAuthenticator) {
        this.proxyAuthenticator = proxyAuthenticator;
    }

    public String getRequiredVersionRegex() {
        return requiredVersionRegex;
    }

    public void setRequiredVersionRegex(String requiredVersionRegex) {
        this.requiredVersionRegex = requiredVersionRegex;
    }

    public void setReuseConnections(boolean reuseConnections) {
        this.reuseConnections = reuseConnections;
    }

    public static class HttpException extends IOException {

        private int statusCode;
        private String statusMsg;
        private String errorContent;

        public HttpException(HttpRequestBase req, HttpResponse response, String message) {
            super(message);
            this.statusCode = response.getStatusLine().getStatusCode();
            this.statusMsg = response.getStatusLine().getReasonPhrase();
            try {
                if (response.getEntity() != null) {
                    this.errorContent = IOUtils.toString(response.getEntity().getContent());
                } else {
                    this.errorContent = "no content";
                }

            } catch (IOException e) {
                CachingRestClient.logger.error("error reading errorContent: "+e, e);
            }
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStatusMsg() {
            return statusMsg;
        }

        public String getErrorContent() {
            return errorContent;
        }
    }

    public CachingRestClient setAllowUrlLogging(boolean allowUrlLogging) {
        this.allowUrlLogging = allowUrlLogging;
        return this;
    }
}
