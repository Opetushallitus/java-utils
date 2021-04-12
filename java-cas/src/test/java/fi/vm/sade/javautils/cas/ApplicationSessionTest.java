package fi.vm.sade.javautils.cas;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.hamcrest.core.IsInstanceOf;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.net.CookieManager;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;

public class ApplicationSessionTest {

    private MockWebServer mockWebServer;
    private ApplicationSession applicationSession;
    private CookieManager cookieManager;
    private Duration authenticationTimeout;
    private CasSession casSession;
    private OkHttpClient client;

    private static final String VALID_TICKET = "it-ankan-tiketti";
    private static final String TEST_SERVICE = "test-service";

    @Before
    public void init() {
        this.mockWebServer = new MockWebServer();
        this.cookieManager = new CookieManager();

        this.client = new OkHttpClient();
        this.authenticationTimeout = Duration.ofSeconds(10);
        this.casSession = new CasSession(client, Duration.ofMillis(1000), "Caller-id", mockWebServer.url("/cas/"), "it-ankka", "neverstopthemadness");
        this.applicationSession = new ApplicationSession(client, cookieManager, "Caller-Id", authenticationTimeout, casSession, mockWebServer.url("/") + TEST_SERVICE, CasEnums.SESSIONCOOKIE_NAME);
    }

    @After
    public void shutDown() throws IOException {
        this.mockWebServer.shutdown();
    }

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldReturnExceptionAndMessageOnTicketGrantingTicketFailure() throws ExecutionException, InterruptedException {
        exception.expectCause(IsInstanceOf.instanceOf(IllegalStateException.class));
        exception.expectMessage(startsWith("Error getting service ticket for service: "));

        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getPath().equals("/cas/")) {
                    return new MockResponse()
                            .addHeader("Location", mockWebServer.url("/") + "tickets")
                            .setResponseCode(404);
                } else if (request.getPath().contains("tickets") && request.getMethod().equals("POST")) {
                    return new MockResponse()
                            .setBody(VALID_TICKET)
                            .setResponseCode(200);
                } else {
                    return new MockResponse()
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .addHeader("Set-Cookie: " + String.format(CasEnums.SESSIONCOOKIE_NAME + "=%s; Path=/test-service", "123456789"))
                            .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service", "WHUTEVAMAN"))
                            .setResponseCode(200);
                }
            }
        };
        mockWebServer.setDispatcher(dispatcher);
        applicationSession.getSessionToken();
    }

    @Test
    public void shouldReturnExceptionAndMessageOnServiceTicketFailure() throws ExecutionException, InterruptedException {
        exception.expectCause(IsInstanceOf.instanceOf(IllegalStateException.class));
        exception.expectMessage(startsWith("Error getting service ticket for service: "));

        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getPath().equals("/cas/")) {
                    return new MockResponse()
                            .addHeader("Location", mockWebServer.url("/") + "tickets")
                            .setResponseCode(201);
                } else if (request.getPath().contains("tickets") && request.getMethod().equals("POST")) {
                    return new MockResponse()
                            .setBody(VALID_TICKET)
                            .setResponseCode(500);
                } else {
                    return new MockResponse()
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .addHeader("Set-Cookie: " + String.format(CasEnums.SESSIONCOOKIE_NAME + "=%s; Path=/test-service", "123456789"))
                            .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service", "WHUTEVAMAN"))
                            .setResponseCode(200);
                }
            }
        };
        mockWebServer.setDispatcher(dispatcher);
        applicationSession.getSessionToken();
    }

    @Test
    public void shouldReturnExceptionAndMessageOnMissingTicketFailure() throws ExecutionException, InterruptedException {
        exception.expectCause(IsInstanceOf.instanceOf(IllegalStateException.class));
        exception.expectMessage(startsWith("Error getting service ticket for service: "));

        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getPath().equals("/cas/")) {
                    return new MockResponse()
                            .addHeader("Location", mockWebServer.url("/") + "tickets")
                            .setResponseCode(201);
                } else if (request.getPath().contains("tickets") && request.getMethod().equals("POST")) {
                    return new MockResponse()
                            .setResponseCode(200);
                } else {
                    return new MockResponse()
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .addHeader("Set-Cookie: " + String.format(CasEnums.SESSIONCOOKIE_NAME + "=%s; Path=/test-service", "123456789"))
                            .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service", "WHUTEVAMAN"))
                            .setResponseCode(200);
                }
            }
        };
        mockWebServer.setDispatcher(dispatcher);
        applicationSession.getSessionToken();
    }

    @Test
    public void shouldReturnExceptionAndMessageOnMissingCookieFailure() throws ExecutionException, InterruptedException {
        exception.expectCause(IsInstanceOf.instanceOf(IllegalStateException.class));
        exception.expectMessage(endsWith("Failed to establish session"));

        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getPath().equals("/cas/")) {
                    return new MockResponse()
                            .addHeader("Location", mockWebServer.url("/") + "tickets")
                            .setResponseCode(201);
                } else if (request.getPath().contains("tickets") && request.getMethod().equals("POST")) {
                    return new MockResponse()
                            .setBody(VALID_TICKET)
                            .setResponseCode(200);
                } else {
                    return new MockResponse()
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service", "WHUTEVAMAN"))
                            .setResponseCode(200);
                }
            }
        };
        mockWebServer.setDispatcher(dispatcher);
        applicationSession.getSessionToken();
    }


    @Test
    public void shouldReturnValidSessionTokenOnSuccessfulResponse() throws ExecutionException, InterruptedException, IOException {
        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                if (request.getPath().equals("/cas/")) {
                    return new MockResponse()
                            .addHeader("Location", mockWebServer.url("/") + "tickets")
                            .setResponseCode(201);

                } else if (request.getPath().contains("tickets") && request.getMethod().equals("POST")) {
                    return new MockResponse()
                            .setBody(VALID_TICKET)
                            .setResponseCode(200);
                } else {
                    return new MockResponse()
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .addHeader("Set-Cookie: " + String.format(CasEnums.SESSIONCOOKIE_NAME + "=%s; Path=/test-service", "123456789"))
                            .addHeader("Set-Cookie: " + String.format("TEST-COOKIE=%s; Path=/test-service", "WHUTEVAMAN"))
                            .setResponseCode(200);
                }
            }
        };
        mockWebServer.setDispatcher(dispatcher);
        CompletableFuture<SessionToken> sessionToken = applicationSession.getSessionToken();
        SessionToken result = sessionToken.get();

        assertEquals(VALID_TICKET, result.serviceTicket.serviceTicket);
        assertEquals(mockWebServer.url("/") + TEST_SERVICE, result.serviceTicket.service);
        assertEquals("/" + TEST_SERVICE, result.cookie.path());
        assertEquals(CasEnums.SESSIONCOOKIE_NAME, result.cookie.name());
        assertEquals("123456789", result.cookie.value());
        System.out.println("done");
    }
}
