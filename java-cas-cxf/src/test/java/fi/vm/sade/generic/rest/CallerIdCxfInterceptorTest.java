package fi.vm.sade.security;

import java.io.InputStream;

import javax.ws.rs.core.MediaType;

import fi.vm.sade.jetty.JettyJersey;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.Message;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import fi.vm.sade.generic.rest.CallerIdCxfInterceptor;

/**
 * Tests for Caller-Id header insertion interceptor for cxf.
 * @author Jouni Stam
 *
 */
public class CallerIdCxfInterceptorTest {

    String unprotectedTargetUrl = "/mirror/headers";

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() throws Exception {
        JettyJersey.startServer("fi.vm.sade.generic.rest", null);
        SecurityContextHolder.clearContext();
    }

    @After
    public void tearDown() {
    }

    /**
     * Caller-Id:n tulisi tulla pyynnön headeriin.
     */
    @Test
    public void testCallerIdInsertion() {
        try {
            CallerIdCxfInterceptor<Message> interceptor = this.createInterceptor();
            WebClient cxfClient = createClient(this.unprotectedTargetUrl, interceptor);
            String response = IOUtils.toString((InputStream) cxfClient.get().getEntity());
            String headerAndValue = interceptor.getHeaderName() + ": " + interceptor.getHeaderValue();
            Assert.assertTrue("Response must include header with value: " + headerAndValue, response.contains(headerAndValue));
        } catch(Exception ex) {
            ex.printStackTrace();
            Assert.assertTrue(false);
        }
    }

    private WebClient createClient(String url, CallerIdCxfInterceptor<Message> interceptor) {
        WebClient c = WebClient.create(getUrl(url)).accept(MediaType.TEXT_PLAIN, MediaType.TEXT_HTML, MediaType.APPLICATION_JSON);
        // Add only as OUT interceptor
        WebClient.getConfig(c).getOutInterceptors().add(interceptor);
        return c;
    }
    
    private CallerIdCxfInterceptor<Message> createInterceptor() {
        CallerIdCxfInterceptor<Message> interceptor = new CallerIdCxfInterceptor<Message>();
        interceptor.setHeaderValue("TEST");

        return interceptor;
    }
    
    public static String getUrl(String url) {
        return JettyJersey.getUrl(url);
    }

}