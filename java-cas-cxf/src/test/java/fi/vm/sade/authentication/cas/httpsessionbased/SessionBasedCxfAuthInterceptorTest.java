package fi.vm.sade.authentication.cas.httpsessionbased;

import fi.vm.sade.generic.rest.RestWithCasTestSupport;
import org.apache.commons.io.IOUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.MessageImpl;
import org.junit.Assert;
import org.junit.Test;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Antti Salonen
 */
public class SessionBasedCxfAuthInterceptorTest extends RestWithCasTestSupport {

    @Test
    public void test_toCookieString() {
        List<String> cookies = testcookies();
        Assert.assertEquals("key=val; foo=bar", SessionBasedCxfAuthInterceptor.toCookieString(cookies));
    }

    @Test
    public void test_writeCookiesToMessage() throws IOException {
        URL url = new URL(getUrl("/httptest/printcookies"));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        MessageImpl message = new MessageImpl();
        message.put("http.connection", conn);
        SessionBasedCxfAuthInterceptor.writeCookiesToMessage(message, testcookies());
        String response = IOUtils.toString(conn.getInputStream());
        Assert.assertEquals("" +
                "key=val(|domain:null|path:null|maxage:-1)\n" +
                "foo=bar(|domain:null|path:null|maxage:-1)\n", response);
    }

    @Test
    public void test_handleMessage() throws IOException {
        BlockingAuthCookieCache blockingAuthCookieCache = new BlockingAuthCookieCache(getUrl("/mock_cas/cas"), 1, 10);

        WebClient cxfClient = createClient("/httptest/printcookies", blockingAuthCookieCache);
        String response = IOUtils.toString((InputStream) cxfClient.get().getEntity());
        System.out.println(response);

        // get the cookies blockingAuthCookieCache got for us
        List<String> authCookies = blockingAuthCookieCache.getAuthenticatedCookiesForServiceUser("user1", "pass1", "user1", getUrl("/httptest/j_spring_cas_security_check"));

        // do jsessionid/session asserts
        assertResponseSession(blockingAuthCookieCache, response, authCookies);

        // do more calls with fresh cxf client/interceptor and assert same jsessionid/session was used
        for (int i = 0; i < 10; i++) {
            cxfClient = createClient("/httptest/printcookies", blockingAuthCookieCache);
            response = IOUtils.toString((InputStream) cxfClient.get().getEntity());
            assertResponseSession(blockingAuthCookieCache, response, authCookies);
        }
    }

    private void assertResponseSession(BlockingAuthCookieCache blockingAuthCookieCache, String response, List<String> authCookies) {
        // assert blockingAuthCookieCache size now 1
        Assert.assertEquals(1, blockingAuthCookieCache.size());

        // assert correct cookies exist
        Assert.assertTrue(response.contains("JSESSIONID="));
        Assert.assertTrue(response.contains("TIKETTICOOKIE="));
        Assert.assertTrue(response.contains("CLIENTCOOKIE=asdasd"));

        // assert jsessionid cookie equals to one originally got from BlockingAuthCookieCache
        String authJsessionId = getCookieValue(authCookies, "JSESSIONID");
        Assert.assertTrue("correct jsessionid (" + authJsessionId + ") not found in response:\n" + response, response.contains("JSESSIONID=" + authJsessionId));
    }

    private String getCookieValue(List<String> cookies, String what) {
        for (String cookie : cookies) {
            List<HttpCookie> httpCookies = HttpCookie.parse(cookie);
            for (HttpCookie httpCookie : httpCookies) {
                if (httpCookie.getName().equals(what)) {
                    return httpCookie.getValue();
                }
            }
        }
        return null;
    }

    private List<String> testcookies() {
        List<String> cookies = new ArrayList<String>();
        cookies.add("key=val");
        cookies.add("foo=bar");
        return cookies;
    }

    private WebClient createClient(String url, BlockingAuthCookieCache blockingAuthCookieCache) {
        WebClient c = WebClient.create(getUrl(url)).accept(MediaType.TEXT_PLAIN, MediaType.TEXT_HTML, MediaType.APPLICATION_JSON).cookie(new Cookie("CLIENTCOOKIE", "asdasd"));
        SessionBasedCxfAuthInterceptor authInterceptor = new SessionBasedCxfAuthInterceptor(blockingAuthCookieCache, "user1", "pass1");
        WebClient.getConfig(c).getOutInterceptors().add(authInterceptor);
        return c;
    }


}
