package fi.vm.sade.javautils.cas;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.*;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;

public class CasHttpClientTest {
    private MockWebServer mockWebServer;
    private OkHttpClient client;
    private CasHttpClient casHttpClient;
    private Duration authenticationTimeout;

    private static final String CSRF_VALUE = "CSRF";
    private static final String COOKIENAME = "JSESSIONID";
    private static final String VALID_TICKET = "it-ankan-tiketti";
    private static final String TEST_SERVICE = "test-service";
    private static final String SECURITY_URI_SUFFIX = "j_spring_cas_security_check";

    @Before
    public void init() {
        this.mockWebServer = new MockWebServer();
        this.authenticationTimeout = Duration.ofSeconds(60);
        this.client = new OkHttpClient();
        String serverUrl = mockWebServer.url("/").toString();
        String casServerUrl = serverUrl.replace(serverUrl.substring(serverUrl.length()-1), "");
        this.casHttpClient = new CasHttpClient(this.client, "Caller-id", COOKIENAME, mockWebServer.url("/") + TEST_SERVICE, casServerUrl , SECURITY_URI_SUFFIX, "It-Ankka", "neverstopthemadness", this.authenticationTimeout);
    }

    @After
    public void shutDown() throws IOException {
        this.mockWebServer.shutdown();
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldReturnValidTicketAndRetryFetchCasSessionOnMultipleFailures() throws ExecutionException, InterruptedException {
        final Dispatcher dispatcher = new Dispatcher() {
            int callCount = 0;

            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getPath().contains("/v1/")) {
                    callCount++;
                    if (callCount == 1) {
                        return new MockResponse()
                                .addHeader("Location", mockWebServer.url("/") + "tickets")
                                .setResponseCode(404);
                    }
                    return new MockResponse()
                            .addHeader("Location", mockWebServer.url("/") + "tickets")
                            .setResponseCode(200);

                } else if (request.getPath().contains("tickets") && request.getMethod().equals("POST")) {
                    callCount++;
                    if (callCount == 3) {
                        return new MockResponse()
                                .setBody(VALID_TICKET)
                                .setResponseCode(500);
                    }
                    return new MockResponse()
                            .setBody(VALID_TICKET)
                            .setResponseCode(200);
                } else if (request.getRequestUrl().toString().contains("?ticket")) {
                    callCount++;
                    if (callCount == 6) {
                        return new MockResponse()
                                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                                .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service", "WHUTEVAMAN"))
                                .setResponseCode(200);
                    }
                    return new MockResponse()
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .addHeader("Set-Cookie: " + String.format(COOKIENAME + "=%s; Path=/test-service", "123456789"))
                            .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service", "WHUTEVAMAN"))
                            .setResponseCode(200);
                } else {
                    callCount++;
                    return new MockResponse()
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .addHeader("Set-Cookie: " + String.format(COOKIENAME + "=%s; Path=/test-service", "123456789"))
                            .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service", "WHUTEVAMAN"))
                            .setResponseCode(200);
                }
            }
        };
        mockWebServer.setDispatcher(dispatcher);
        Request request = new Request.Builder()
                .url(this.mockWebServer.url("/"))
                .header("Caller-Id", "Caller-Id")
                .header("CSRF", CSRF_VALUE)
                .build();

        Response completedResponse = this.casHttpClient.call(request).get();
        List<String> cookielist = completedResponse.headers().values("Set-Cookie");
        assertEquals(true, cookielist.contains("JSESSIONID=123456789; Path=/test-service"));
    }

    @Test
    public void shouldReturnValidTicketResponse() throws ExecutionException, InterruptedException {
        final Dispatcher dispatcher = new Dispatcher() {

            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getPath().contains("/v1/")) {
                    return new MockResponse()
                            .addHeader("Location", mockWebServer.url("/") + "tickets")
                            .setResponseCode(201);

                } else if (request.getPath().contains("tickets") && request.getMethod().equals("POST")) {
                    return new MockResponse()
                            .setBody(VALID_TICKET)
                            .setResponseCode(200);
                } else if (request.getRequestUrl().toString().contains("?ticket")) {
                    return new MockResponse()
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .addHeader("Set-Cookie: " + String.format(COOKIENAME + "=%s; Path=/test-service", "123456789"))
                            .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service", "WHUTEVAMAN"))
                            .setResponseCode(200);
                } else {
                    return new MockResponse()
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .addHeader("Set-Cookie: " + String.format(COOKIENAME + "=%s; Path=/test-service", "123456789"))
                            .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service", "WHUTEVAMAN"))
                            .setResponseCode(200);
                }
            }
        };
        mockWebServer.setDispatcher(dispatcher);
        Request request = new Request.Builder()
                .url(this.mockWebServer.url("/"))
                .header("Caller-Id", "Caller-Id")
                .header("CSRF", CSRF_VALUE)
                .build();

        Response completedResponse = this.casHttpClient.call(request).get();
        List<String> cookielist = completedResponse.headers().values("Set-Cookie");
        assertEquals(true, cookielist.contains("JSESSIONID=123456789; Path=/test-service"));
    }
}
