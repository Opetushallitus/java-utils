package fi.vm.sade.generic.rest;

import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.xml.datatype.XMLGregorianCalendar;

import fi.vm.sade.jetty.JettyJersey;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.cache.CacheResponseStatus;
import org.apache.http.impl.client.cache.CachingHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import junit.framework.Assert;

import static fi.vm.sade.generic.rest.CachingRestClientTest.assertContains;

/**
 * @author Antti Salonen
 * @author Juha Paananen
 */
public class CachingHttpGetClientTest {

    @Test
    public void testXmlGregorianCalendarParsing() throws Exception {
        Calendar now = new GregorianCalendar();
        assertDay(now, client.get(getUrl("/httptest/xmlgregoriancalendar1"), XMLGregorianCalendar.class));
        assertDay(now, client.get(getUrl("/httptest/xmlgregoriancalendar2"), XMLGregorianCalendar.class));
    }

    private void assertDay(Calendar now, XMLGregorianCalendar xmlGregorianCalendar) {
        System.out.println("CachingRestClientTest.assertDay, now: "+now+", xmlGregCal: "+xmlGregorianCalendar);
        Assert.assertEquals(now.get(Calendar.YEAR), xmlGregorianCalendar.toGregorianCalendar().get(Calendar.YEAR));
        Assert.assertEquals(now.get(Calendar.MONTH), xmlGregorianCalendar.toGregorianCalendar().get(Calendar.MONTH));
        Assert.assertEquals(now.get(Calendar.DAY_OF_MONTH), xmlGregorianCalendar.toGregorianCalendar().get(Calendar.DAY_OF_MONTH));
    }

    @Test
    public void testCachingWithCommonsHttpClientAndJersey() throws Exception {
        // lue resurssi, jossa cache 1 sek
        Assert.assertEquals("pong 1", get("/httptest/pingCached1sec"));

        // lue resurssi uudestaan, assertoi että tuli cachesta, eikä serveriltä asti
        Assert.assertEquals("pong 1", get("/httptest/pingCached1sec"));

        // odota 1 sek
        Thread.sleep(2000);

        // lue resurssi uudestaan, assertoi että haettiin serveriltä koska cache vanheni
        Assert.assertEquals("pong 2", get("/httptest/pingCached1sec"));
    }

    @Test
    public void testResourceMirroringUsingEtag() throws Exception {
        // luetaan resurssi
        Assert.assertEquals("original value 1", get("/httptest/someResource"));
        Assert.assertEquals(getCacheStatus(), CacheResponseStatus.CACHE_MISS);

        // tehdään muutos serverin resurssiin
        HttpTestResource.someResource = "changed value";

        // luetaan resurssi, assertoi että tulee cachesta vielä (koska expires)
        Assert.assertEquals("original value 1", get("/httptest/someResource"));
        Assert.assertEquals(getCacheStatus(), CacheResponseStatus.CACHE_HIT);

        // odotetaan että expires menee ohi
        Thread.sleep(2000);

        // luetaan resurssi, assertoi että tulee serveriltä, koska muuttunut etag JA expires aika mennyt
        Assert.assertEquals("changed value 2", get("/httptest/someResource"));
        Assert.assertEquals(getCacheStatus(), CacheResponseStatus.VALIDATED);

        // odotetaan että expires menee ohi
        Thread.sleep(2000);

        // luetaan resurssi, assertoi että tulee cachesta vaikka käy serverillä (serveri palauttaa unmodified, eikä nosta counteria, koska etag sama)
        Assert.assertEquals("changed value 2", get("/httptest/someResource"));
        Assert.assertEquals(getCacheStatus(), CacheResponseStatus.VALIDATED);

        // vielä assertoidaan että unmodified -responsen jälkeen expires toimii kuten pitää eli ei käydä serverillä vaan tulee cache_hit
        Assert.assertEquals("changed value 2", get("/httptest/someResource"));
        Assert.assertEquals(getCacheStatus(), CacheResponseStatus.CACHE_HIT);
    }

    private Object getCacheStatus() {
        return context.getAttribute(CachingHttpClient.CACHE_RESPONSE_STATUS);
    }

    @Test(expected = IOException.class)
    public void testErrorStatus() throws IOException {
        get("/httptest/status500");
    }

    @Test
    public void testClientSubSystemCode() throws Exception {
        // lue resurssi, jossa cache 1 sek
        assertContains(get("/mirror/headers"), "clientSubSystemCode: CachingHttpGetClientTest");
    }


    private String get(String url) throws IOException {
        return IOUtils.toString(client.get(getUrl(url), context));
    }

    CachingHttpGetClient client;
    final HttpContext context = new BasicHttpContext();

    @Before
    public void start() throws Exception {
        JettyJersey.startServer("fi.vm.sade.generic.rest", null);
        TestParams.instance = new TestParams();
        HttpTestResource.counter = 1;
        HttpTestResource.someResource = "original value";
        SecurityContextHolder.clearContext();
//        DefaultTicketCachePolicy.ticketThreadLocal.remove();
        client = new CachingHttpGetClient().setClientSubSystemCode("CachingHttpGetClientTest");
    }

    @After
    public void stop() throws Exception {
        JettyJersey.stopServer();
    }

    protected String getUrl(String url) {
        return JettyJersey.getUrl(url);
    }

}
