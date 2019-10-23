package fi.vm.sade.javautils.httpclient;

import static fi.vm.sade.javautils.httpclient.OphHttpClient.Header.ACCEPT;
import static fi.vm.sade.javautils.httpclient.OphHttpClient.JSON;
import static fi.vm.sade.javautils.httpclient.OphHttpClient.TEXT;
import static fi.vm.sade.javautils.httpclient.OphHttpClient.UTF8;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import fi.vm.sade.javautils.httpclient.apache.ApacheOphHttpClient;
import fi.vm.sade.properties.OphProperties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OphHttpClientTest {

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this);

    OphProperties properties = new OphProperties();
    private OphHttpClient client;
    private OphHttpResponseHandler<String> responseAsText = OphHttpResponse::asText;
    OphHttpClient clientPlainUrls;

    @Before
    public void setUp() throws Exception {
        properties = new OphProperties();
        client = ApacheOphHttpClient.createDefaultOphClient("TESTCLIENT", properties, 1000, 1000);
        Logger.getLogger("io.netty").setLevel(Level.OFF);
        properties.addDefault("local.test", "/test");
        properties.addDefault("baseUrl", "http://localhost:" + mockServerRule.getPort());
        clientPlainUrls = ApacheOphHttpClient.createDefaultOphClient("TESTCLIENT", null, 1000, 1000);
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
    public void getSendsCallerId() {

        new MockServerClient("localhost", mockServerRule.getPort()).when(
                request()
                        .withMethod("GET")
                        .withPath("/test")
                        .withQueryStringParameter("a","1","2")
                        .withHeader("Caller-Id: TESTCLIENT")
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
    public void postSendsCallerIdAndCSRFAndContentTypeAndEncoding() {
        new MockServerClient("localhost", mockServerRule.getPort()).when(
                request()
                        .withMethod("POST")
                        .withPath("/test")
                        .withHeader("Caller-Id", "TESTCLIENT")
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
                .dataWriter(JSON, UTF8, outstream -> outstream.write("POW!!")).accept(TEXT)
                .execute(responseAsText));
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
                    "Unexpected response status: 404 Expected: any 2xx code Url: http://localhost:",
                    "/test");
        }
        try {
            client.get("local.test").expectStatus(200, 201).execute(responseAsText);
            throw new RuntimeException("should not get here");
        } catch (RuntimeException e) {
            assertContains(e.getMessage(),
                    "Unexpected response status: 404 Expected: any of 200, 201 Url: http://localhost:",
                    "/test");
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
        assertEquals(Integer.valueOf(404), client.get("local.test")
                .skipResponseAssertions().accept(JSON)
                .execute(OphHttpResponse::getStatusCode));
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
            client.get("local.test").retryOnError(2,1).execute((OphHttpResponseHandler<Void>) response -> {
                throw new RuntimeException("Thrown for testing");
            });
            throw new RuntimeException("should not get here");
        } catch (RuntimeException retryException) {
            assertContains(retryException.getMessage(),
                    "Tried 2 times GET http://localhost:", "/test");

            Throwable handlerException = retryException.getCause();
            assertEquals("Thrown for testing", handlerException.getMessage());
        }

        // status code exception
        try {
            client.get("local.test").retryOnError(3,1).expectStatus(201).execute(responseAsText);
            throw new RuntimeException("should not get here");
        } catch (RuntimeException retryException) {
            assertContains(retryException.getMessage(),
                    "Tried 3 times GET http://localhost:", "/test");

            Throwable handlerException = retryException.getCause();
            assertContains(handlerException.getMessage(),
                    "Unexpected response status: 200 Expected: 201 Url: http://localhost:",
                    "/test");
        }

        // cannot connect
        properties.addDefault("baseUrl", "http://weriuhweropowejmrcpokmpwock");
        try {
            client.get("local.test").retryOnError(4,1).execute(responseAsText);
            throw new RuntimeException("should not get here");
        } catch (RuntimeException retryException) {
            assertEquals("Tried 4 times GET http://weriuhweropowejmrcpokmpwock/test",
                    retryException.getMessage());

            Throwable clientException = retryException.getCause();
            assertEquals("Error handling url: http://weriuhweropowejmrcpokmpwock/test",
                    clientException.getMessage());

            UnknownHostException unknownHostException = (UnknownHostException) (clientException.getCause());
            assertEquals("weriuhweropowejmrcpokmpwock", unknownHostException.getMessage());
        }
    }

    private void wrappedGetWithVarArgs(String... args) {
        assertEquals("OK!", client.get("local.test", (Object[])args)
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

    @Test
    public void plainUrlsWorkIfNoUrlProperties() {
        new MockServerClient("localhost", mockServerRule.getPort()).when(
                request()
                        .withMethod("GET")
                        .withPath("/test")
        ).respond(response()
                .withStatusCode(200)
                .withHeader("Content-Type", TEXT)
                .withBody("OK!")
        );
        assertEquals("OK!", clientPlainUrls.get("http://localhost:"+mockServerRule.getPort()+"/test")
                .execute(responseAsText));
    }

    @Test
    public void onErrorHandlesErrors() {
        new MockServerClient("localhost", mockServerRule.getPort()).when(
                request()
                        .withMethod("GET")
                        .withPath("/test")
        ).respond(response()
                .withStatusCode(404)
                .withHeader("Content-Type", TEXT)
                .withBody("NOT OK!")
        );

        // return correct Object
        assertEquals("Exception: Unexpected response status: 404 Expected: any 2xx code Url: http://localhost:"+mockServerRule.getPort()+"/test",
                client.get("local.test")
                .throwOnlyOnErrorExceptions()
                .onError((requestParameters, response, e) -> "Exception: " + e.getMessage())
                .execute(responseAsText));

        // return incorrect object type -> class cast exception
        try {
            String result = client.get("local.test")
                    .throwOnlyOnErrorExceptions()
                    .onError((requestParameters, response, e) -> 123)
                    .execute(responseAsText);
            throw new RuntimeException("For some reason class cast exception was not thrown!");
        } catch (Exception e) {
            assertThat(e, is(instanceOf(ClassCastException.class)));
        }

        // onError can throw exception
        final String[] arr = {""};
        try {
            String result = client.get("local.test")
                    .onError((requestParameters, response, e) -> {arr[0]="POW!"; throw e;})
                    .execute(responseAsText);
            throw new RuntimeException("For some reason there was no exception");
        } catch (Exception e) {
            assertEquals("POW!", arr[0]);
            assertThat(e, is(instanceOf(RuntimeException.class)));
            assertEquals("Unexpected response status: 404 Expected: any 2xx code Url: http://localhost:"+mockServerRule.getPort()+"/test", e.getMessage());
        }

        // handleManually supports onError
        arr[0]="";
        try {
            assertEquals("Exception: Unexpected response status: 404 Expected: any 2xx code Url: http://localhost:"+mockServerRule.getPort()+"/test",
                    client.get("local.test")
                            .onError((requestParameters, response, e) -> arr[0]="POW!")
                            .handleManually()
                            .asText());
            throw new RuntimeException("For some reason there was no exception");
        } catch (IOException e) {
            throw new RuntimeException("There should have not been an IOException");
        } catch (RuntimeException e) {
            assertEquals("POW!", arr[0]);
            assertEquals(e.getMessage(), "Unexpected response status: 404 Expected: any 2xx code Url: http://localhost:"+mockServerRule.getPort()+"/test", e.getMessage());
        }

    }

    private static void assertContains(String from, String... args) {
        for (String arg : args) {
            Assert.assertTrue("String " + arg + " not found from: " + from, from.contains(arg));
        }
    }
}
