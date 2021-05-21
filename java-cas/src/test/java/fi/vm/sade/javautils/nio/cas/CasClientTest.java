package fi.vm.sade.javautils.nio.cas;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.hamcrest.core.IsInstanceOf;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;

public class CasClientTest {
    private MockWebServer mockWebServer;
    private CasClient casClient;
    private static final String CSRF_VALUE = "CSRF";
    private static final String COOKIENAME = "JSESSIONID";
    private static final String VALID_TICKET = "it-ankan-tiketti";

    @Before
    public void init() {
        this.mockWebServer = new MockWebServer();
        this.casClient = new CasClient(CasConfig.CasConfig("it-ankka",
                "neverstopthemadness",
                mockWebServer.url("/cas").toString(),
                mockWebServer.url("/cas/") + "test-service",
                "CSRF",
                "Caller-Id",
                "JSESSIONID",
                "/j_spring_cas_security_check"));
    }

    @After
    public void shutDown() throws IOException {
        this.mockWebServer.shutdown();
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();

    String casVirkailijaAttributes = "<cas:serviceResponse><cas:authenticationSuccess><cas:user>it-ankka</cas:user></cas:authenticationSuccess></cas:serviceResponse>";
    String malformedCasVirkailijaAttributes = "<cas:serviceResponse><cas:authenticationSuccess><cas:ur>it-ankka</cas:user></cas:authenticationSuccess></cas:serviceResponse>";
    String casOppijaAttributes = "<cas:authenticationSuccess>\n" +
            "    <cas:user>suomi.fi#010170-999R</cas:user>\n" +
            "    <cas:attributes>\n" +
            "        <cas:isFromNewLogin>true</cas:isFromNewLogin>\n" +
            "        <cas:mail>tero.ayramo@kouluk.com</cas:mail>\n" +
            "        <cas:authenticationDate>2021-03-02T17:28:00.823527Z[UTC]</cas:authenticationDate>\n" +
            "        <cas:clientName>suomi.fi</cas:clientName>\n" +
            "        <cas:displayName>Testi Äyrämö</cas:displayName>\n" +
            "        <cas:givenName>Testi</cas:givenName>\n" +
            "        <cas:VakinainenKotimainenLahiosoiteS>Kauppa Puistikko 6 B 15</cas:VakinainenKotimainenLahiosoiteS>\n" +
            "        <cas:VakinainenKotimainenLahiosoitePostitoimipaikkaS>VAASA</cas:VakinainenKotimainenLahiosoitePostitoimipaikkaS>\n" +
            "        <cas:cn>Äyrämö Tero Testi</cas:cn>\n" +
            "        <cas:notBefore>2021-03-02T17:28:00.390Z</cas:notBefore>\n" +
            "        <cas:personOid>1.2.246.562.24.78873180244</cas:personOid>\n" +
            "        <cas:personName>Äyrämö Tero Testi</cas:personName>\n" +
            "        <cas:firstName>Tero Testi</cas:firstName>\n" +
            "        <cas:VakinainenKotimainenLahiosoitePostinumero>65100</cas:VakinainenKotimainenLahiosoitePostinumero>\n" +
            "        <cas:KotikuntaKuntanumero>905</cas:KotikuntaKuntanumero>\n" +
            "        <cas:KotikuntaKuntaS>Vaasa</cas:KotikuntaKuntaS>\n" +
            "        <cas:notOnOrAfter>2021-03-02T17:33:00.390Z</cas:notOnOrAfter>\n" +
            "        <cas:longTermAuthenticationRequestTokenUsed>false</cas:longTermAuthenticationRequestTokenUsed>\n" +
            "        <cas:sn>Äyrämö</cas:sn>\n" +
            "        <cas:nationalIdentificationNumber>010170-999R</cas:nationalIdentificationNumber>\n" +
            "        </cas:attributes>\n" +
            "</cas:authenticationSuccess>";

    String casOppijaAttributesWithImpersonatorData = "<cas:authenticationSuccess>\n" +
            "    <cas:user>suomi.fi#010170-999R</cas:user>\n" +
            "    <cas:attributes>\n" +
            "        <cas:isFromNewLogin>true</cas:isFromNewLogin>\n" +
            "        <cas:mail>tero.ayramo@kouluk.com</cas:mail>\n" +
            "        <cas:authenticationDate>2021-03-02T17:28:00.823527Z[UTC]</cas:authenticationDate>\n" +
            "        <cas:clientName>suomi.fi</cas:clientName>\n" +
            "        <cas:displayName>Testi Äyrämö</cas:displayName>\n" +
            "        <cas:givenName>Testi</cas:givenName>\n" +
            "        <cas:VakinainenKotimainenLahiosoiteS>Kauppa Puistikko 6 B 15</cas:VakinainenKotimainenLahiosoiteS>\n" +
            "        <cas:VakinainenKotimainenLahiosoitePostitoimipaikkaS>VAASA</cas:VakinainenKotimainenLahiosoitePostitoimipaikkaS>\n" +
            "        <cas:cn>Äyrämö Tero Testi</cas:cn>\n" +
            "        <cas:notBefore>2021-03-02T17:28:00.390Z</cas:notBefore>\n" +
            "        <cas:personOid>1.2.246.562.24.78873180244</cas:personOid>\n" +
            "        <cas:personName>Äyrämö Tero Testi</cas:personName>\n" +
            "        <cas:firstName>Tero Testi</cas:firstName>\n" +
            "        <cas:VakinainenKotimainenLahiosoitePostinumero>65100</cas:VakinainenKotimainenLahiosoitePostinumero>\n" +
            "        <cas:KotikuntaKuntanumero>905</cas:KotikuntaKuntanumero>\n" +
            "        <cas:KotikuntaKuntaS>Vaasa</cas:KotikuntaKuntaS>\n" +
            "        <cas:notOnOrAfter>2021-03-02T17:33:00.390Z</cas:notOnOrAfter>\n" +
            "        <cas:longTermAuthenticationRequestTokenUsed>false</cas:longTermAuthenticationRequestTokenUsed>\n" +
            "        <cas:sn>Äyrämö</cas:sn>\n" +
            "        <cas:nationalIdentificationNumber>010170-999R</cas:nationalIdentificationNumber>\n" +
            "        <cas:impersonatorNationalIdentificationNumber>010170-998R</cas:impersonatorNationalIdentificationNumber>\n" +
            "        <cas:impersonatorDisplayName>Faija Roger Äyrämö</cas:impersonatorDisplayName>\n" +
            "        </cas:attributes>\n" +
            "</cas:authenticationSuccess>";

    String malformedCasOppijaAttributes = "<cas:authentication>\n" +
            "    <cas:user>suomi.fi#010170-999R</cas:user>\n" +
            "    <cas:attributes>\n" +
            "        <cas:isFromNewLogin>true</cas:isFromNewLogin>\n" +
            "        <cas:mail>tero.ayramo@kouluk.com</cas:mail>\n" +
            "        <cas:authenticationDate>2021-03-02T17:28:00.823527Z[UTC]</cas:authenticationDate>\n" +
            "        <cas:clientName>suomi.fi</cas:clientName>\n" +
            "        <cas:displayName>Testi Äyrämö</cas:displayName>\n" +
            "        <cas:givenName>Testi</cas:givenName>\n" +
            "        <cas:VakinainenKotimainenLahiosoiteS>Kauppa Puistikko 6 B 15</cas:VakinainenKotimainenLahiosoiteS>\n" +
            "        <cas:VakinainenKotimainenLahiosoitePostitoimipaikkaS>VAASA</cas:VakinainenKotimainenLahiosoitePostitoimipaikkaS>\n" +
            "        <cas:cn>Äyrämö Tero Testi</cas:cn>\n" +
            "        <cas:notBefore>2021-03-02T17:28:00.390Z</cas:notBefore>\n" +
            "        <cas:personOid>1.2.246.562.24.78873180244</cas:personOid>\n" +
            "        <cas:personName>Äyrämö Tero Testi</cas:personName>\n" +
            "        <cas:firstName>Tero Testi</cas:firstName>\n" +
            "        <cas:VakinainenKotimainenLahiosoitePostinumero>65100</cas:VakinainenKotimainenLahiosoitePostinumero>\n" +
            "        <cas:KotikuntaKuntanumero>905</cas:KotikuntaKuntanumero>\n" +
            "        <cas:KotikuntaKuntaS>Vaasa</cas:KotikuntaKuntaS>\n" +
            "        <cas:notOnOrAfter>2021-03-02T17:33:00.390Z</cas:notOnOrAfter>\n" +
            "        <cas:longTermAuthenticationRequestTokenUsed>false</cas:longTermAuthenticationRequestTokenUsed>\n" +
            "        <cas:sn>Äyrämö</cas:sn>\n" +
            "        <cas:nationalIdentificationNumber>010170-999R</cas:nationalIdentificationNumber>\n" +
            "        </cas:attributes>\n" +
            "</cas:authenticationSuccess>";

    @Test
    public void shouldParseOppijaAttributesFromXMLSuccessfully() throws ExecutionException {
        mockWebServer.enqueue(new MockResponse().setBody(casOppijaAttributes));
        HashMap<String, String> oppijaAttributes = casClient.validateServiceTicketWithOppijaAttributesBlocking(mockWebServer.url("/test-service").toString(), VALID_TICKET);

        assertEquals(oppijaAttributes.get("personName"), "Äyrämö Tero Testi");
        assertEquals(oppijaAttributes.get("firstName"), "Tero Testi");
        assertEquals(oppijaAttributes.get("clientName"), "suomi.fi");
        assertEquals(oppijaAttributes.get("displayName"), "Testi Äyrämö");
        assertEquals(oppijaAttributes.get("givenName"), "Testi");
        assertEquals(oppijaAttributes.get("personOid"), "1.2.246.562.24.78873180244");
        assertEquals(oppijaAttributes.get("nationalIdentificationNumber"), "010170-999R");
    }

    @Test
    public void shouldParseOppijaAttributesWithImpersonatorDataFromXMLSuccessfully() throws ExecutionException {
        mockWebServer.enqueue(new MockResponse().setBody(casOppijaAttributesWithImpersonatorData));
        HashMap<String, String> oppijaAttributes = casClient.validateServiceTicketWithOppijaAttributesBlocking(mockWebServer.url("/test-service").toString(), VALID_TICKET);

        assertEquals(oppijaAttributes.get("personName"), "Äyrämö Tero Testi");
        assertEquals(oppijaAttributes.get("firstName"), "Tero Testi");
        assertEquals(oppijaAttributes.get("clientName"), "suomi.fi");
        assertEquals(oppijaAttributes.get("displayName"), "Testi Äyrämö");
        assertEquals(oppijaAttributes.get("givenName"), "Testi");
        assertEquals(oppijaAttributes.get("personOid"), "1.2.246.562.24.78873180244");
        assertEquals(oppijaAttributes.get("nationalIdentificationNumber"), "010170-999R");
        assertEquals(oppijaAttributes.get("impersonatorNationalIdentificationNumber"), "010170-998R");
        assertEquals(oppijaAttributes.get("impersonatorDisplayName"), "Faija Roger Äyrämö");
    }

    @Test
    public void shouldThrowExceptionOnParseOppijaAttributesIfMalformedXML() throws ExecutionException {
        exception.expectCause(IsInstanceOf.instanceOf(ExecutionException.class));
        exception.expectMessage(startsWith("Failed to validate service ticket with oppija attributes, service:"));
        mockWebServer.enqueue(new MockResponse().setBody(malformedCasOppijaAttributes));
        casClient.validateServiceTicketWithOppijaAttributesBlocking(mockWebServer.url("/test-service").toString(), VALID_TICKET);
    }

    @Test
    public void shouldParseVirkailijaUsernameFromXMLSuccessfully() throws ExecutionException {
        mockWebServer.enqueue(new MockResponse().setBody(casVirkailijaAttributes));
        String virkailijaUsername = casClient.validateServiceTicketWithVirkailijaUsernameBlocking(mockWebServer.url("/test-service").toString(), VALID_TICKET);
        assertEquals("it-ankka", virkailijaUsername);
    }

    @Test
    public void shouldThrowExceptionOnParseVirkailijaUsernameIfMalformedXML() throws ExecutionException {
        exception.expectCause(IsInstanceOf.instanceOf(ExecutionException.class));
        exception.expectMessage(startsWith("Failed to validate service ticket with virkailija username, service:"));
        mockWebServer.enqueue(new MockResponse().setBody(malformedCasVirkailijaAttributes));
        casClient.validateServiceTicketWithVirkailijaUsernameBlocking(mockWebServer.url("/test-service").toString(), VALID_TICKET);
    }

    @Test
    public void shouldSendSessionCookieWithRequest() throws ExecutionException, InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Location", mockWebServer.url("/") + "cas/tickets")
                .setResponseCode(201));
        mockWebServer.enqueue(new MockResponse()
                .setBody(VALID_TICKET)
                .setResponseCode(200));
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Set-Cookie: " + String.format(COOKIENAME + "=%s; Path=/test-service/", "123456789"))
                .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service/", "WHUTEVAMAN"))
                .setResponseCode(200));
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .setResponseCode(200));

        Request request = new RequestBuilder()
                .setUrl(this.mockWebServer.url("/test").toString())
                .addHeader("Caller-Id", "Caller-Id")
                .addHeader("CSRF", CSRF_VALUE)
                .build();

        this.casClient.execute(request).get();

        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        RecordedRequest actualRequest = mockWebServer.takeRequest();
        assertEquals("/test", actualRequest.getPath());
        assertEquals(true, actualRequest.getHeader("cookie").contains("JSESSIONID=123456789"));
    }

    @Test
    public void shouldSendServiceTicketWithRequest() throws ExecutionException, InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Location", mockWebServer.url("/") + "cas/tickets")
                .setResponseCode(201));
        mockWebServer.enqueue(new MockResponse()
                .setBody(VALID_TICKET)
                .setResponseCode(200));
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Set-Cookie: " + String.format(COOKIENAME + "=%s; Path=/test-service/", "123456789"))
                .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service/", "WHUTEVAMAN"))
                .setResponseCode(200));
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .setResponseCode(200));
        Request request = new RequestBuilder()
                .setUrl(this.mockWebServer.url("/test").toString())
                .setMethod("GET")
                .addHeader("Caller-Id", "Caller-Id")
                .addHeader("CSRF", CSRF_VALUE)
                .build();

        this.casClient.executeWithServiceTicket(request).get();
        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        RecordedRequest actualRequest = mockWebServer.takeRequest();
        assertEquals("/test?ticket=it-ankan-tiketti", actualRequest.getPath());
    }

    @Test
    public void shouldSendServiceTicketWithRequestWithParameters() throws ExecutionException, InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Location", mockWebServer.url("/") + "cas/tickets")
                .setResponseCode(201));
        mockWebServer.enqueue(new MockResponse()
                .setBody(VALID_TICKET)
                .setResponseCode(200));
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Set-Cookie: " + String.format(COOKIENAME + "=%s; Path=/test-service/", "123456789"))
                .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service/", "WHUTEVAMAN"))
                .setResponseCode(200));
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .setResponseCode(200));

        Request request = new RequestBuilder()
                .setUrl(this.mockWebServer.url("/test").toString())
                .setMethod("GET")
                .addQueryParam("param", "1234")
                .addHeader("Caller-Id", "Caller-Id")
                .addHeader("CSRF", CSRF_VALUE)
                .build();

        this.casClient.executeWithServiceTicketBlocking(request);
        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        RecordedRequest actualRequest = mockWebServer.takeRequest();
        assertEquals("/test?param=1234&ticket=it-ankan-tiketti", actualRequest.getPath());
    }
@Ignore
    @Test
    public void shouldSendJSessionIdWithRequestWithParametersWhenResponse302() throws ExecutionException, InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Location", mockWebServer.url("/") + "cas/tickets")
                .setResponseCode(201));
        mockWebServer.enqueue(new MockResponse()
                .setBody(VALID_TICKET)
                .setResponseCode(200));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(302));
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Location", mockWebServer.url("/") + "cas/tickets")
                .setResponseCode(201));
        mockWebServer.enqueue(new MockResponse()
                .setBody(VALID_TICKET)
                .setResponseCode(200));
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Set-Cookie: " + String.format(COOKIENAME + "=%s; Path=/test-service/", "123456789"))
                .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service/", "WHUTEVAMAN"))
                .setResponseCode(200));
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .setResponseCode(200));

        Request request = new RequestBuilder()
                .setUrl(this.mockWebServer.url("/test").toString())
                .setMethod("GET")
                .addQueryParam("param", "1234")
                .addHeader("Caller-Id", "Caller-Id")
                .addHeader("CSRF", CSRF_VALUE)
                .build();

        this.casClient.executeWithServiceTicketBlocking(request);
        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        RecordedRequest actualRequest = mockWebServer.takeRequest();
        assertEquals("/test?param=1234", actualRequest.getPath());
        assertEquals(true, actualRequest.getHeader("cookie").contains("JSESSIONID=123456789"));
    }
    @Ignore
    @Test
    public void shouldSendJSessionIdWithRequestWithParametersWhenResponse401() throws ExecutionException, InterruptedException {
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Location", mockWebServer.url("/") + "cas/tickets")
                .setResponseCode(201));
        mockWebServer.enqueue(new MockResponse()
                .setBody(VALID_TICKET)
                .setResponseCode(200));
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401));
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Location", mockWebServer.url("/") + "cas/tickets")
                .setResponseCode(201));
        mockWebServer.enqueue(new MockResponse()
                .setBody(VALID_TICKET)
                .setResponseCode(200));
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Set-Cookie: " + String.format(COOKIENAME + "=%s; Path=/test-service/", "123456789"))
                .setResponseCode(200));
        mockWebServer.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .setResponseCode(200));

        Request request = new RequestBuilder()
                .setUrl(this.mockWebServer.url("/test").toString())
                .setMethod("GET")
                .addQueryParam("param", "1234")
                .addHeader("Caller-Id", "Caller-Id")
                .addHeader("CSRF", CSRF_VALUE)
                .build();

        this.casClient.executeWithServiceTicketBlocking(request);
        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        mockWebServer.takeRequest().toString();
        RecordedRequest actualRequest = mockWebServer.takeRequest();
        assertEquals("/test?param=1234", actualRequest.getPath());
        assertEquals(true, actualRequest.getHeader("cookie").contains("JSESSIONID=123456789"));
    }
}
