package fi.vm.sade.generic.healthcheck;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.support.WebApplicationContextUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * Health check servlet, joka on tietoinen springistä, ja mm:
 * ==========================================================
 *
 * - ajaa healthcheckit kaikille spring beaneille jotka toteuttavat HealthChecker -rajapinnan
 * - ajaa healthcheckit tietokannalle (kaivaa spring datasourcen)
 * - kertoo kannan data_status -taulun sisällön
 * - ajaa itsensä myös sovelluksen startissa ja kirjoittaa logiin jos ongelmia
 *
 *
 * Käyttöönotto:
 * =============
 *
 * 1. Mäppää tämä (tai tästä peritty) luokka urliin /healthcheck
 *
 *      @WebServlet(urlPatterns = "/healthcheck", loadOnStartup = 9)
 *      public class HealthCheckServlet extends SpringAwareHealthCheckServlet { }
 *
 *      TAI
 *
 *      <servlet>
 *          <servlet-name>healthcheck</servlet-name>
 *          <servlet-class>SpringAwareHealthCheckServlet</servlet-class>
 *      </servlet>
 *      <servlet-mapping>
 *          <servlet-name>healthcheck</servlet-name>
 *          <url-pattern>/healthcheck</url-pattern>
 *      </servlet-mapping>
 *
 *      HUOM:
 *      - Vaatii gson -dependencyn toimiakseen (vaihtoehtoisesti voinee ylikirjoittaa toJson -metodin)
 *
 * 2. Lisää tarvittaessa springin app contexiin uusia beaneja, jotka toteuttaa HealthChecker -interfacen
 *
 *
 * Kustomointi jos tarvetta, esim:
 * ===============================
 *
 * - Ylikirjoita registerHealthCheckers -metodi jos haluat lisätä muita checkereitä
 * - Toteuta afterHealthCheck -metodi, jos haluat tehdä jotain spesifiä kaikkien tarkastuksien jälkeen
 * - Huom, pääset käsiksi spring app ctx:iin, ctx -muuttujan kautta mikäli tarvetta
 *
 *
 * Speksi:
 * =======
 *
 * - https://liitu.hard.ware.fi/confluence/display/PROG/Healthcheck+url
 *
 * @see HealthChecker
 * @author Antti Salonen
 */
public class SpringAwareHealthCheckServlet extends HttpServlet {

    public static final long CACHE_MS = 10 * 1000; // cache results 10 secs per session to prevent dos, or circular healthchecks between services
    public  static final int CACHE_MAX_SIZE = 100;
    public static final String OK = "OK";
    public static final String STATUS = "status";
    public static final String ERRORS = "errors";

    private static final Logger log = LoggerFactory.getLogger(SpringAwareHealthCheckServlet.class);
    public static final String RESULT_JSON = "resultJson";
    public static final String TIMESTAMP = "timestamp";
    protected ApplicationContext ctx;
    protected Map<String, Map<String,Object>> cache = Collections.synchronizedMap(
        new LinkedHashMap<String, Map<String,Object>>(CACHE_MAX_SIZE + 1, .75F, true) {
            // This method is called just after a new entry has been added
            public boolean removeEldestEntry(Map.Entry eldest) {
                return size() > CACHE_MAX_SIZE;
            }
        }
    );

    @Autowired(required = false)
    private DataSource dataSource;

    @Value("${web.url.cas}")
    String casUrl;

    @Value("${host.virkailija}")
    String hostVirkailija;

    @Override
    public void init() throws ServletException {
        log.info("init healthcheck servlet");

        // autowire
        ctx = WebApplicationContextUtils.getWebApplicationContext(getServletContext());
        if (ctx != null) {
            ctx.getAutowireCapableBeanFactory().autowireBean(this);
            log.info("initial health check:\n" + toJson(doHealthCheck(System.currentTimeMillis(), "init")));
        } else {
            log.warn("spring ctx null in healthcheck servlet!");
        }
    }

    @Override
    protected final void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // prepare
        resp.setContentType("application/json");
        final long timestamp = System.currentTimeMillis();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String user = auth != null ? auth.getName() : "anonymous";

        // ?userinfo --> näytetäänkin vain userinfoa eikä healthcheckiä
        if (req.getParameter("userinfo") != null) {
            resp.getWriter().print(toJson(SecurityContextHolder.getContext().getAuthentication()));
            return;
        }

        // jos cachessa
        Map<String,Object> cachedResult = cache.get(user);
        if (cachedResult != null && timestamp - (Long)cachedResult.get(TIMESTAMP) < CACHE_MS) {
            resp.getWriter().print(cachedResult.get(RESULT_JSON));
            return;
        }

        // actual health check
        try {
            Map<String, Object> result = doHealthCheck(timestamp, user);
            final String resultJson = toJson(result);
            if (result == null || !OK.equals(result.get(STATUS))) { // log status != ok
                log.warn("healthcheck failed:\n" + resultJson);
            }

            // cache the result
            cache.put(user, new HashMap<String,Object>(){{ put(TIMESTAMP, timestamp); put(RESULT_JSON, resultJson); }});

            // write result
            resp.getWriter().print(resultJson);
        } catch (Throwable e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            e.printStackTrace(resp.getWriter());
        }
    }

    protected String toJson(Object o) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(o);
    }

    protected Map<String, Object> doHealthCheck(long timestamp, String user) {
        Map<String, String> errors = new HashMap<String, String>();

        // register healthcheckers
        Map<String, HealthChecker> checkers = registerHealthCheckers();

        // invoke all healthcheckers
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("timestamp", timestamp);
        result.put("user", user);
        result.put("contextPath", getServletContext().getContextPath());
        result.put("checks", new LinkedHashMap());
        for (String checkerName : checkers.keySet()) {
            HealthChecker healthChecker = checkers.get(checkerName);
            log.debug("healthcheck calling checker: " + checkerName);
            doHealthChecker(result, errors, checkerName, healthChecker);
        }

        // set app's health check status
        if (errors.size() == 0) {
            result.put(STATUS, OK);
        } else {
            result.put(ERRORS, errors);
            result.put(STATUS, "ERRORS --- " + errors.keySet());
        }

        //
        afterHealthCheck(result, checkers);

        return result;
    }

    protected Map<String, HealthChecker> registerHealthCheckers() {
        Map<String, HealthChecker> checkers = ctx.getBeansOfType(HealthChecker.class);

        // register some default checkers
        checkers.put("database", new DatabaseHealthChecker(dataSource));
        checkers.put("buildversion", new BuildVersionHealthChecker(getServletContext()));
        checkers.put("proxyauth", new ProxyAuthenticationChecker(getServletContext(), ctx));

        return checkers;
    }

    protected void doHealthChecker(Map<String, Object> result, Map<String, String> erros, String checkerName, HealthChecker healthChecker) {
        Object res = null;
        try {
            res = healthChecker.checkHealth();

            // if check ok, put the response into healtcheck results check
            log.debug("healthcheck called healthchecker ok: " + checkerName + ", result: " + res);
            if (res == null || (res instanceof Collection && ((Collection) res).isEmpty())) res = OK;
            if (res instanceof Map) res = new LinkedHashMap((Map)res); // gson ei tykkää sisäkkäisistä normimäpeistä :-o

        } catch (Throwable e) {
            log.warn("error in healthchecker '" + checkerName + "': " + e, e);

            // if check failed, put the error into healtcheck results
            if (e instanceof InvocationTargetException) {
                e = ((InvocationTargetException) e).getTargetException();
            }
            res = "ERROR: " + e.getMessage();
            erros.put(checkerName, e.getMessage());
        }

        // put checker result in healthcheck result
        ((Map<String,Object>)result.get("checks")).put(checkerName, res);
    }

    protected void afterHealthCheck(Map<String, Object> result, Map<String, HealthChecker> checkers) {
    }

}
