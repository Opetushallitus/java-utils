package fi.vm.sade.javautils.httpclient;

import fi.vm.sade.properties.OphProperties;
import org.junit.*;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;

import java.io.IOException;
import java.io.Writer;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import static fi.vm.sade.javautils.httpclient.OphHttpClient.*;
import static fi.vm.sade.javautils.httpclient.OphHttpClient.Header.ACCEPT;
import static org.junit.Assert.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class OphHttpClientTest {

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);

    OphProperties properties = new OphProperties();
    private OphHttpClient client;
    private OphHttpResponseHandler<String> responseAsText = new OphHttpResponseHandler<String>() {
        @Override
        public String handleResponse(OphHttpResponse response) {
            return response.asText();
        }
    };

    @Before
    public void setUp() throws Exception {
        properties = new OphProperties();
        client = ApacheOphHttpClient.createDefaultOphHttpClient("TESTCLIENT", properties, 1000, 1000);
        Logger.getLogger("io.netty").setLevel(Level.OFF);
        properties.addDefault("local.test", "/test");
        properties.addDefault("baseUrl", "http://localhost:" + mockServerRule.getPort());
    }

    @Test
    public void unknownUrlThrowsException() {
        try {
            client.get("local.mirror");
            throw new RuntimeException("client.get should have thrown exception");
        } catch (RuntimeException e) {
            assertEquals("\"local.mirror\" not defined.", e.getMessage());
        }
    }

    @Test
    public void getSendsClientSubSystemCode() {

        new MockServerClient("localhost", mockServerRule.getPort()).when(
                request()
                        .withMethod("GET")
                        .withPath("/test")
                        .withQueryStringParameter("a","1","2")
                        .withHeader("clientSubSystemCode: TESTCLIENT")
        ).respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", TEXT)
                .withBody("OK!")
        );

        assertEquals("OK!", client.get("local.test")
                .param("a",1)
                .param("a",2)
                .accept(TEXT)
                .execute(responseAsText));
    }

    @Test
    public void postSendsClientSubSystemCodeAndCSRFAndContentTypeAndEncoding() {
        new MockServerClient("localhost", mockServerRule.getPort()).when(
                request()
                        .withMethod("POST")
                        .withPath("/test")
                        .withHeader("clientSubSystemCode", "TESTCLIENT")
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withHeader("CSRF", "CSRF")
                        .withCookie("CSRF", "CSRF")
                        .withBody("POW!!", StandardCharsets.UTF_8)
        ).respond(response().
                withStatusCode(200).
                withHeader("Content-Type", TEXT).
                withBody("OK!")
        );

        assertEquals("OK!", client.post("local.test")
                .data(JSON, UTF8, new OphRequestPostWriter() {
                    @Override
                    public void writeTo(Writer outstream) throws IOException {
                        outstream.write("POW!!");
                    }
                }).accept(TEXT).execute(responseAsText));
    }

    @Test
    public void acceptIsVerifiedFromResponseContentType() {
        MockServerClient mockServerClient = new MockServerClient("localhost", mockServerRule.getPort());

        mockServerClient.when(
                request()
                        .withMethod("GET")
                        .withPath("/test")
        ).respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", TEXT)
                .withBody("OK!")
        );

        assertEquals("OK!", client.get("local.test")
                .accept(TEXT)
                .execute(responseAsText));
        try {
            client.get("local.test")
                    .accept(JSON)
                    .execute(responseAsText);
            throw new RuntimeException("should not get here");
        } catch (RuntimeException e) {
            assertContains(e.getMessage(),
                    "Error with response Content-Type header. Url: http://localhost:",
                    "/test Error value: text/plain Expected: application/json");
        }

        // content type with charset
        properties.addDefault("local.test2", "/test2");
        mockServerClient.when(
                request()
                        .withMethod("GET")
                        .withPath("/test2")
        ).respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", JSON + "; charset=UTF-8")
                .withBody("OK!")
        );
        assertEquals("OK!", client.get("local.test2")
                .execute(responseAsText));
        assertEquals("OK!", client.get("local.test2")
                .accept(JSON)
                .execute(responseAsText));
        try {
            client.get("local.test2")
                    .accept(TEXT)
                    .execute(responseAsText);
            throw new RuntimeException("should not get here");
        } catch (RuntimeException e) {
            assertContains(e.getMessage(),
                    "Error with response Content-Type header. Url: http://localhost:",
                    "/test2 Error value: application/json Expected: text/plain");
        }
    }

    @Test
    public void responseStatusCodeIsVerified() {
        new MockServerClient("localhost", mockServerRule.getPort()).when(
                request()
                        .withMethod("GET")
                        .withPath("/test")
        ).respond(response()
                .withStatusCode(404)
                .withHeader("Content-Type", TEXT)
                .withBody("NOT OK!")
        );

        try {
            client.get("local.test").execute(responseAsText);
            throw new RuntimeException("should not get here");
        } catch (RuntimeException e) {
            assertContains(e.getMessage(),
                    "Unexpected response status: 404 Url: http://localhost:",
                    "/test Expected: any 2xx code");
        }
        try {
            client.get("local.test").expectStatus(200, 201).execute(responseAsText);
            throw new RuntimeException("should not get here");
        } catch (RuntimeException e) {
            assertContains(e.getMessage(),
                    "Unexpected response status: 404 Url: http://localhost:",
                    "/test Expected: any of 200, 201");
        }
    }

    @Test
    public void skipResponseAssertions() {
        new MockServerClient("localhost", mockServerRule.getPort()).when(
                request()
                        .withMethod("GET")
                        .withPath("/test")
                        .withHeader(ACCEPT, JSON)
        ).respond(response()
                .withStatusCode(404)
                .withHeader("Content-Type", TEXT)
                .withBody("NOT OK!")
        );
        assertEquals(new Integer(404), client.get("local.test").skipResponseAssertions().accept(JSON).execute(new OphHttpResponseHandler<Integer>() {
            @Override
            public Integer handleResponse(OphHttpResponse response) throws IOException {
                return response.getStatusCode();
            }
        }));
    }

    @Test
    public void retryOnError() {
        new MockServerClient("localhost", mockServerRule.getPort()).when(
                request()
                        .withMethod("GET")
                        .withPath("/test")
        ).respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", TEXT)
                .withBody("OK!")
        );

        // handler exception
        try {
            client.get("local.test").retryOnError(2,1).execute(new OphHttpResponseHandler<Void>() {
                @Override
                public Void handleResponse(OphHttpResponse response) throws IOException {
                    throw new RuntimeException("Thrown for testing");
                }
            });
            throw new RuntimeException("should not get here");
        } catch (RuntimeException retryException) {
            assertContains(retryException.getMessage(),
                    "Tried GET http://localhost:", "/test 2 times");

            Throwable handlerException = retryException.getCause();
            assertEquals("Thrown for testing", handlerException.getMessage());
        }

        // status code exception
        try {
            client.get("local.test").retryOnError(3,1).expectStatus(201).execute(responseAsText);
            throw new RuntimeException("should not get here");
        } catch (RuntimeException retryException) {
            assertContains(retryException.getMessage(),
                    "Tried GET http://localhost:", "/test 3 times");

            Throwable handlerException = retryException.getCause();
            assertContains(handlerException.getMessage(),
                    "Unexpected response status: 200 Url: http://localhost:",
                    "/test Expected: 201");
        }

        // cannot connect
        properties.addDefault("baseUrl", "http://weriuhweropowejmrcpokmpwock");
        try {
            client.get("local.test").retryOnError(4,1).execute(responseAsText);
            throw new RuntimeException("should not get here");
        } catch (RuntimeException retryException) {
            assertEquals("Tried GET http://weriuhweropowejmrcpokmpwock/test 4 times",
                    retryException.getMessage());

            Throwable clientException = retryException.getCause();
            assertEquals("Error handling url: http://weriuhweropowejmrcpokmpwock/test",
                    clientException.getMessage());

            UnknownHostException unknownHostException = (UnknownHostException) (clientException.getCause());
            assertEquals("weriuhweropowejmrcpokmpwock", unknownHostException.getMessage());
        }
    }

    private void wrappedGetWithVarArgs(String... args) {
        assertEquals("OK!", client.get("local.test", args)
                .accept(TEXT)
                .execute(responseAsText));
    }

    @Test
    public void wrappedGetWithVarArgs() {
        new MockServerClient("localhost", mockServerRule.getPort()).when(
                request()
                        .withMethod("GET")
                        .withPath("/test/a/b")
        ).respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", TEXT)
                .withBody("OK!")
        );

        properties.addDefault("local.test", "/test/$1/$2");
        wrappedGetWithVarArgs("a", "b");
    }

    private static void assertContains(String from, String... args) {
        for (String arg : args) {
            Assert.assertTrue("String " + arg + " not found from: " + from, from.contains(arg));
        }
    }
}