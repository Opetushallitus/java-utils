package fi.vm.sade.javautils.cas;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

public class CasHttpClientTest {
    private MockWebServer mockWebServer;
    private OkHttpClient client;
    private CasHttpClient casHttpClient;
    private Duration authenticationTimeout;

    private static final String COOKIENAME = "JSESSIONID";
    private static final String VALID_TICKET = "it-ankan-tiketti";
    private static final String TEST_SERVICE = "test-service";

    @Before
    public void init() {
        this.mockWebServer = new MockWebServer();
        this.authenticationTimeout = Duration.ofSeconds(10);
        this.client = new OkHttpClient();
        this.casHttpClient = new CasHttpClient(this.client, "Caller-id", COOKIENAME, mockWebServer.url("/") + "test-service", mockWebServer.url("/") + "cas/tickets", "It-Ankka", "neverstopthemadness", this.authenticationTimeout);

    }

    @After
    public void shutDown() throws IOException {
        this.mockWebServer.shutdown();
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldReturnValidTicketResponse() throws ExecutionException, InterruptedException {
        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getPath().contains("/cas/")) {
                    System.out.println("1");
                    System.out.println(request.toString());
                    return new MockResponse()
                            .addHeader("Location", mockWebServer.url("/") + "tickets")
                            .setResponseCode(201);

                } else if (request.getPath().contains("tickets") && request.getMethod().equals("POST")) {
                    System.out.println("2");
                    System.out.println(request.toString());
                    return new MockResponse()
                            .setBody(VALID_TICKET)
                            .setResponseCode(200);
                } else {
                    System.out.println("3");
                    System.out.println(request.toString());
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
                .header("CSRF", CasEnums.CSRF_VALUE)
                .build();
        //Thread.sleep(10);
        //CompletableFuture<Response> response = this.casHttpClient.call(request);
        Response completedResponse = this.casHttpClient.call(request).get();
        System.out.println("DONE!");
    }
}
