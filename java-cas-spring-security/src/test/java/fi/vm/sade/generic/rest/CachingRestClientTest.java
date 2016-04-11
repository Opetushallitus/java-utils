package fi.vm.sade.generic.rest;

import fi.vm.sade.generic.ui.portlet.security.ProxyAuthenticator;
import junit.framework.Assert;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.cache.CacheResponseStatus;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.ws.rs.core.MediaType;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.IOException;
import java.util.*;

/**
 * @author Antti Salonen
 */
public class CachingRestClientTest extends RestWithCasTestSupport {

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

    public static void assertContains(String source, String... args) {
        for(String arg: args) {
            Assert.assertTrue("could not find string '" + arg + "' from: " + source, source.indexOf(arg) > -1);
        }
    }

    @Test
    public void testCSRFHeaders() throws Exception {
        // lue resurssi, jossa cache 1 sek
        assertContains(get("/mirror/headers"), "CSRF: CachingRestClient", "Cookie: CSRF=CachingRestClient", "clientSubSystemCode: RestWithCasTestSupport");
    }

    @Test
    public void testResourceMirroringUsingEtag() throws Exception {
        // luetaan resurssi
        Assert.assertEquals("original value 1", get("/httptest/someResource"));
        Assert.assertEquals(client.getCacheStatus(), CacheResponseStatus.CACHE_MISS);

        // tehdään muutos serverin resurssiin
        HttpTestResource.someResource = "changed value";

        // luetaan resurssi, assertoi että tulee cachesta vielä (koska expires)
        Assert.assertEquals("original value 1", get("/httptest/someResource"));
        Assert.assertEquals(client.getCacheStatus(), CacheResponseStatus.CACHE_HIT);

        // odotetaan että expires menee ohi
        Thread.sleep(2000);

        // luetaan resurssi, assertoi että tulee serveriltä, koska muuttunut etag JA expires aika mennyt
        Assert.assertEquals("changed value 2", get("/httptest/someResource"));
        Assert.assertEquals(client.getCacheStatus(), CacheResponseStatus.VALIDATED);

        // odotetaan että expires menee ohi
        Thread.sleep(2000);

        // luetaan resurssi, assertoi että tulee cachesta vaikka käy serverillä (serveri palauttaa unmodified, eikä nosta counteria, koska etag sama)
        Assert.assertEquals("changed value 2", get("/httptest/someResource"));
        Assert.assertEquals(client.getCacheStatus(), CacheResponseStatus.VALIDATED);

        // vielä assertoidaan että unmodified -responsen jälkeen expires toimii kuten pitää eli ei käydä serverillä vaan tulee cache_hit
        Assert.assertEquals("changed value 2", get("/httptest/someResource"));
        Assert.assertEquals(client.getCacheStatus(), CacheResponseStatus.CACHE_HIT);
    }

    @Test(expected = IOException.class)
    public void testErrorStatus() throws IOException {
        get("/httptest/status500");
    }

    @Test
    public void testAuthenticationWithGetRedirect() throws Exception {
        initClientAuthentication("test");

        // alustava pyyntö -> CachingRestClient hankkii tiketin kutsua ennen, kutsu menee ok:sti
        Assert.assertEquals("pong 1", get("/httptest/pingSecuredRedirect/asd1"));
        assertCas(0, 1, 1, 1, 1);

        // simuloidaan että ollaan autentikoiduttu casiin, mutta ei kohdepalveluun vielä, joten kutsun suojattuun resurssiin pitäisi redirectoitua casiin
        TestParams.instance.userIsAlreadyAuthenticatedToCas = "asdsad";
        TestParams.instance.failNextBackendAuthentication = true;

        // lue suojattu resurssi -> välillä käydään cassilla, joka ohjaa takaisin ticketin kanssa (koska ollaan jo casissa sisällä)
        Assert.assertEquals("pong 2", get("/httptest/pingSecuredRedirect/asd1")); // asd? tarvitaan koska muuten apache http saattaa tulkita circular redirectiksi..
        assertCas(1, 1, 1, 3, 2);

        // kutsu uudestaan -> ei redirectiä koska nyt serviceenkin ollaan autentikoiduttu, ainoastaan request autentikoidaan backendissä
        Assert.assertEquals("pong 3", get("/httptest/pingSecuredRedirect/asd1"));
        assertCas(1, 1, 1, 4, 3);

        // invalidoi tiketti serverillä, cas sessio edelleen ok (simuloi ticket cachen tyhjäytymistä serverillä) -> redirectit resource->cas->resource tapahtuu uusiksi
        TestParams.instance.failNextBackendAuthentication = true;
        Assert.assertEquals("pong 4", get("/httptest/pingSecuredRedirect/asd1"));
        assertCas(2, 1, 1, 6, 4);

        // tehdään ensin onnistunut kutsu..
        Assert.assertEquals("pong 5", get("/httptest/pingSecuredRedirect/asd1"));
        assertCas(2, 1, 1, 7, 5);
        // ..sitten invalidoi tiketti ja cas sessio (simuloi cas/backend restarttia)
        // -> resurssi redirectoi cassille, mutta cas ei ohjaa takaisin koska ei olla sisällä casissa
        // -> CachingRestClient havaitsee puuttuvan authin, ja osaa hakea uuden tiketin, ja tehdä pyynnön uusiksi
        // -> redirectejä ei tämän jälkeen tapahdu, mutta tgt+ticket luodaan casiin, ja validoidaan backend resurssilla
        TestParams.instance.failNextBackendAuthentication = true;
        TestParams.instance.userIsAlreadyAuthenticatedToCas = null;
        Assert.assertEquals("pong 6", get("/httptest/pingSecuredRedirect/asd1"));
        assertCas(2, 2, 2, 9, 6);
    }

    @Test
    @Ignore // ei oikeastaan halutakaan tukea postien cas redirectointia, aina ennen postia pitää tehdä get!
    public void testAuthenticationWithPostRedirect() throws Exception {
        initClientAuthentication("test");

        // alustava pyyntö -> CachingRestClient hankkii tiketin kutsua ennen, kutsu menee ok:sti
        Assert.assertEquals("pong 1", post("/httptest/pingSecuredRedirect/asd1", "post content")); // asd? tarvitaan koska muuten apache http saattaa tulkita circular redirectiksi..
        assertCas(0, 1, 1, 1, 1);

        // autentikoiduttu casiin, mutta ei kohdepalveluun vielä, joten kutsun suojattuun resurssiin pitäisi redirectoitua casiin
        TestParams.instance.userIsAlreadyAuthenticatedToCas = "asdsad";
        TestParams.instance.failNextBackendAuthentication = true;

        // lue suojattu resurssi -> välillä käydään cassilla, joka ohjaa takaisin ticketin kanssa (koska ollaan jo casissa sisällä)
        Assert.assertEquals("pong 2", post("/httptest/pingSecuredRedirect/asd2", "post content")); // asd? tarvitaan koska muuten apache http saattaa tulkita circular redirectiksi..
        //assertCas(1, 1, 1, 3, 2); - note! ei tapahdu redirectiä, ei oikeastaan halutakaan tukea postien cas redirectointia, aina ennen postia pitää tehdä get!
    }

    @Test
    public void testAuthenticationWith401Unauthorized() throws Exception {
        initClientAuthentication("test");

        // lue suojattu resurssi joka palauttaisi 401 unauthorized, mikäli ei oltaisi autentikoiduttu -> client kuitenkin on yllä konffattu käyttämään palvelutunnuksia
        Assert.assertEquals("pong 1", get("/httptest/pingSecured401Unauthorized"));
        assertCas(0,1,1,1,1);

        // invalidoi serveripään tiketti -> seur kutsussa resurssi palauttaa 401, jonka jälkeen restclient osaa hakea uuden tiketin ja koittaa pyyntöä uusiksi
        TestParams.instance.failNextBackendAuthentication = true;
        Assert.assertEquals("pong 2", get("/httptest/pingSecured401Unauthorized"));
        assertCas(0,2,2,3,2);
    }

    @Test
    public void testIllegalUserWontGetStuckInRedirectLoopOrSthing() throws Exception {
        initClientAuthentication("illegaluser");
        try {
            get("/httptest/pingSecured401Unauthorized");
            Assert.fail("should fail");
        } catch (CachingRestClient.HttpException e) {
            Assert.assertEquals(401, e.getStatusCode());
        }
    }

    @Test
    public void testProxyAuthentication() throws Exception {
        // prepare & mock stuff
        final String user = "uiasdhjsadhu";
        final int[] proxyTicketCounter = {0};
        List<GrantedAuthority> roles = Arrays.asList((GrantedAuthority)new SimpleGrantedAuthority("testrole"));
        TestingAuthenticationToken clientAuth = new TestingAuthenticationToken(user, user, roles);
        SecurityContextHolder.getContext().setAuthentication(clientAuth);
        client.setCasService(getUrl("/httptest"));
        client.setUseProxyAuthentication(true);
        client.setProxyAuthenticator(new ProxyAuthenticator() {
            @Override
            protected String obtainNewCasProxyTicket(String casTargetService, Authentication casAuthenticationToken) {
                return "mockticket_" + user + "_" + (++proxyTicketCounter[0]);
            }
        });

        // lue suojattu resurssi joka palauttaisi muuten 401 unauthorized, mutta client hoitaa autentikoinnin sisäisesti ja kutsuu clientuserina
        Assert.assertEquals("pong 1", get("/httptest/pingSecured401Unauthorized"));
        Assert.assertEquals(1, proxyTicketCounter[0]);
        assertCas(0,0,0,1,1); // redir ei tehtä, tikettejä ei luoda koska client laittaa mukaan proxytiketin, tiketin validointi tehty serverillä kerran ok

        // invalidoi tiketti serveripäässä (esim restarttaa cas tai kohdepalvelu välissä), ja yritä uudestaan -> client pitäisi hankkia uuusi proxy ticket
        TestParams.instance.failNextBackendAuthentication = true;
        Assert.assertEquals("pong 2", get("/httptest/pingSecured401Unauthorized"));
        Assert.assertEquals(2, proxyTicketCounter[0]);
        assertCas(0,0,0,3,2);

        // invalidoi tiketti clientilla -> client pitäisi hankkia uuusi proxy ticket
        client.getProxyAuthenticator().clearTicket(getUrl("/httptest"));
        Assert.assertEquals("pong 3", get("/httptest/pingSecured401Unauthorized"));
        Assert.assertEquals(3, proxyTicketCounter[0]);
        assertCas(0,0,0,4,3);
    }

    private void initClientAuthentication(String username) {
        client.setCasService(getUrl("/httptest"));
        client.setWebCasUrl(getUrl("/mock_cas/cas"));
        client.setUsername(username);
        client.setPassword(username);
    }

    private String get(String url) throws IOException {
        return IOUtils.toString(client.get(getUrl(url)));
    }

    private String post(String url, String postContent) throws IOException {
        return IOUtils.toString(client.post(getUrl(url), "application/json", postContent).getEntity().getContent());
    }

    @Test
    public void testPostUTF8Encoding() throws IOException {
        final String json = "{\"test\":\"Möttönen\"}";
        final HttpResponse response = client.post(getUrl("/httptest/special-character-resource"), MediaType.APPLICATION_JSON, json);
        final String responseJson = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
        System.out.println("got response entity: " + responseJson);
        Assert.assertTrue("response should contain \"Möttönen\": "+responseJson, StringUtils.contains(responseJson, "Möttönen"));
    }

    @Test
    public void testPutUTF8Encoding() throws IOException {
        final String json = "{\"test\":\"Möttönen\"}";
        final HttpResponse response = client.put(getUrl("/httptest/special-character-resource"), MediaType.APPLICATION_JSON, json);
        final String responseJson = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
        System.out.println("got response entity: " + responseJson);
        Assert.assertTrue("response should contain \"Möttönen\": "+responseJson, StringUtils.contains(responseJson, "Möttönen"));
    }

    @Test
    public void testGotRedirectToCasBecauseSystemBroken() {
        /*
         -systeemi/konffit rikki
         -eka kutsu, juuri hankittu validi tiketti
         -silti tulee redirect cas:lle
         -clientin pitäisi osata heittää poikkeus tällöin
        */
        initClientAuthentication("test");
        try {
            String resp = get("/httptest/pingSecuredRedirect/asd1?SKIP_CAS_FILTER");
            Assert.fail("should fail, but got response: "+resp);
        } catch (Exception e) {
            Assert.assertTrue(e.toString().contains("something wrong with the system"));
        }
    }

    @Test
    public void testOnlyOneTicketHeader() throws IOException {
        // fix bug: fix bug: cachingrestclient 401 virheen korjaus.. cas redirect tapauksissa CasSecurityTicket-header tuli kahteen kertaan, joka aiheutti ticketin validoinnin failaamisen -> 401 unauthorized

        // tehdään rest kutsu
        initClientAuthentication("test");
        Assert.assertEquals("pong 1", get("/httptest/pingSecuredRedirect/asd1"));
        Assert.assertEquals(1, TestParams.prevRequestTicketHeaders.size());
        Object orgTicket = TestParams.prevRequestTicketHeaders.get(0);

        // invalidoidaan ticket serverillä, jotta joudutaan käymään cassilla hakemassa redirecteillä uusi
        TestParams.instance.failNextBackendAuthentication = true;

        // tehdään toinen kutsu
        Assert.assertEquals("pong 2", get("/httptest/pingSecuredRedirect/asd1"));

        // assertoidaan että kutsussa oli edelleen vain yksi ticket-header, ja se on eri kuin edellinen ticket eli ticket oikeasti haettiin uusiksi
        Assert.assertEquals(1, TestParams.prevRequestTicketHeaders.size());
        Assert.assertNotSame(orgTicket, TestParams.prevRequestTicketHeaders.get(0));
    }

    @Test(expected = CachingRestClient.HttpException.class)
    public void testResourceWithoutContentWillNotFail() throws IOException {
        initClientAuthentication("test");
        Assert.assertNull(get("/httptest/testResourceNoContent"));
    }

}
