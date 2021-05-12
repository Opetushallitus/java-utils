package fi.vm.sade.javautils.nio.cas;

//import okhttp3.mockwebserver.MockResponse;
//import okhttp3.mockwebserver.MockWebServer;
//import org.asynchttpclient.Response;
//import org.junit.After;
//import org.junit.Before;
//import org.junit.Ignore;
//import org.junit.Test;
//
//import java.io.IOException;
//import java.util.concurrent.ExecutionException;

public class CasClientTest {
//    private MockWebServer mockWebServer;
//    private CasClient casClient;
//
//    @Before
//    public void init() {
//        this.mockWebServer = new MockWebServer();
//        this.casClient = new CasClient(CasConfig.CasConfig("it-ankka",
//                "neverstopthemadness",
//                "/cas",
//                "test-service",
//                "CSRF",
//                "Caller-Id",
//                "JSESSIONID",
//                "/j_spring_cas_security_check"));
//    }
//
//    @After
//    public void shutDown() throws IOException {
//        this.mockWebServer.shutdown();
//    }
//
//
//    String casOppijaAttributes = "<cas:authenticationSuccess>\n" +
//            "    <cas:user>suomi.fi#010170-999R</cas:user>\n" +
//            "    <cas:attributes>\n" +
//            "        <cas:isFromNewLogin>true</cas:isFromNewLogin>\n" +
//            "        <cas:mail>tero.ayramo@kouluk.com</cas:mail>\n" +
//            "        <cas:authenticationDate>2021-03-02T17:28:00.823527Z[UTC]</cas:authenticationDate>\n" +
//            "        <cas:clientName>suomi.fi</cas:clientName>\n" +
//            "        <cas:displayName>Testi Äyrämö</cas:displayName>\n" +
//            "        <cas:givenName>Testi</cas:givenName>\n" +
//            "        <cas:VakinainenKotimainenLahiosoiteS>Kauppa Puistikko 6 B 15</cas:VakinainenKotimainenLahiosoiteS>\n" +
//            "        <cas:VakinainenKotimainenLahiosoitePostitoimipaikkaS>VAASA</cas:VakinainenKotimainenLahiosoitePostitoimipaikkaS>\n" +
//            "        <cas:cn>Äyrämö Tero Testi</cas:cn>\n" +
//            "        <cas:notBefore>2021-03-02T17:28:00.390Z</cas:notBefore>\n" +
//            "        <cas:personOid>1.2.246.562.24.78873180244</cas:personOid>\n" +
//            "        <cas:personName>Äyrämö Tero Testi</cas:personName>\n" +
//            "        <cas:firstName>Tero Testi</cas:firstName>\n" +
//            "        <cas:VakinainenKotimainenLahiosoitePostinumero>65100</cas:VakinainenKotimainenLahiosoitePostinumero>\n" +
//            "        <cas:KotikuntaKuntanumero>905</cas:KotikuntaKuntanumero>\n" +
//            "        <cas:KotikuntaKuntaS>Vaasa</cas:KotikuntaKuntaS>\n" +
//            "        <cas:notOnOrAfter>2021-03-02T17:33:00.390Z</cas:notOnOrAfter>\n" +
//            "        <cas:longTermAuthenticationRequestTokenUsed>false</cas:longTermAuthenticationRequestTokenUsed>\n" +
//            "        <cas:sn>Äyrämö</cas:sn>\n" +
//            "        <cas:nationalIdentificationNumber>010170-999R</cas:nationalIdentificationNumber>\n" +
//            "        </cas:attributes>\n" +
//            "</cas:authenticationSuccess>";
//
//    @Test
//    public void shouldParseOppijaAttributesFromXMLSuccessfully() throws ExecutionException {
//mockWebServer.enqueue(new MockResponse().setBody("hello, world!"));
//        String oppijaAttributes = casClient.validateServiceTicketWithVirkailijaUsernameBlocking("test-service", "it-ankan-tiketti");
//        //System.out.println(oppijaAttributes);
//    }
//
//    @Ignore
//    @Test
//    public void shouldReturnEmptyIfMalformedXML() {
//        String logoutXML = "<samlp:t><saml:NameID>It-ankka</saml:NameID><samlp:SessionIndex>123456789</samlp:SessionIndex></samlp:LogoutRequest>";
////        Optional<String> logoutName = casLogout.parseTicketFromLogoutRequest(logoutXML);
////        assertEquals(Optional.empty(), logoutName);
//    }
//

}
