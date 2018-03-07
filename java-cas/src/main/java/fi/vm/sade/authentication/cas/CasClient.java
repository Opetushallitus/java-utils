package fi.vm.sade.authentication.cas;

import java.io.IOException;
import java.util.List;

import fi.vm.sade.javautils.httpclient.*;
import fi.vm.sade.javautils.httpclient.apache.ApacheHttpClientBuilder;
import fi.vm.sade.javautils.httpclient.apache.ApacheOphHttpClient;
import org.apache.commons.lang.StringUtils;
import org.apache.http.cookie.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static fi.vm.sade.javautils.httpclient.OphHttpClient.FORM_URLENCODED;
import static fi.vm.sade.javautils.httpclient.OphHttpClient.UTF8;

/**
 * An example Java client to authenticate against CAS using REST services.
 * Please ensure you have followed the necessary setup found on the <a
 * href="http://www.ja-sig.org/wiki/display/CASUM/RESTful+API">wiki</a>.
 *
 * @author Antti Salonen
 * @author <a href="mailto:jieryn@gmail.com">jesse lauren farinacci</a>
 * @since 3.4.2
 */
public final class CasClient {
    public static final String CAS_URL_SUFFIX = "/v1/tickets";
    public static final String SERVICE_URL_SUFFIX = "/j_spring_cas_security_check";
    private static final Logger logger = LoggerFactory.getLogger(CasClient.class);

    private CasClient() {
        // static-only access
    }

    /**
     * get cas service ticket, throws runtime exception if fails
     */
    public static String getTicket(String server, final String username, final String password, String service) {
        return getTicket(server, username, password, service, true);
    }

    /**
     * get cas service ticket, throws runtime exception if fails
     */
    public static String getTicket(String server, final String username, final String password, String service, boolean addSuffix) {
        if (addSuffix) {
            return getTicket(server, username, password, service, SERVICE_URL_SUFFIX);
        } else {
            return getTicket(server, username, password, service, "");
        }
    }

    public static String getTicket(String server, final String username, final String password, String service, String serviceUrlSuffix) {
        logger.debug("getTicket for server:{}, username:{}, service::{} ", new Object[]{server, username, service});

        notNull(server, "server must not be null");
        notNull(username, "username must not be null");
        notNull(password, "password must not be null");
        notNull(service, "service must not be null");

        server = checkUrl(server, CAS_URL_SUFFIX);
        if (StringUtils.isNotEmpty(serviceUrlSuffix)) {
            service = checkUrl(service, serviceUrlSuffix);
        }

        try (OphHttpClient client = new OphHttpClient(ApacheOphHttpClient.createCustomBuilder().
                createClosableClient().
                setDefaultConfiguration(10000, 60).build(), "CasClient")) {
            return getServiceTicket(server, username, password, service, client);
        }
    }

    public static Cookie initServiceSession(String casServiceSessionInitUrl, String serviceTicket, String cookieName) {
        ApacheOphHttpClient apacheClient = ApacheOphHttpClient.createCustomBuilder().createClosableClient().setDefaultConfiguration(10000, 60).build();
        OphHttpClient client = new OphHttpClient(apacheClient, "CasClient");

        return client.get(casServiceSessionInitUrl + "?" + "ticket="+serviceTicket).skipResponseAssertions().execute(r -> {
            for(Cookie cookie : apacheClient.getCookieStore().getCookies()) {
                if(cookieName.equals(cookie.getName())) {
                    return cookie;
                }
            }
            throw new RuntimeException("failed to init session to target service, response code: " + r.getStatusCode() + ", casServiceSessionInitUrl: " + casServiceSessionInitUrl + ", serviceTicket: " + serviceTicket);
        });
    }


    private static String getServiceTicket(final String server, String username, String password, final String service, OphHttpClient client) {
        final String ticketGrantingTicket = getTicketGrantingTicket(server, username, password, client);

        logger.debug("getServiceTicket: server:'{}', ticketGrantingTicket:'{}', service:'{}'", new Object[]{server, ticketGrantingTicket, service});

        try {
            return client.post(server + "/" + ticketGrantingTicket).
                    dataWriter(FORM_URLENCODED, UTF8, out -> OphHttpClient.formUrlEncodedWriter(out).param("service", service)).
                    skipResponseAssertions().execute(r -> {
                final String response = r.asText();
                printTraceResponse(r, response);
                switch (r.getStatusCode()) {
                    case 200:
                        logger.debug("serviceTicket found: {}", response);
                        return response;
                    default:
                        logger.warn("Invalid response code ({}) from CAS server!", r.getStatusCode());
                        logger.info("Response (1k): " + response.substring(0, Math.min(1024, response.length())));
                        throw new RuntimeException("failed to get CAS service ticket, response code: " + r.getStatusCode() + ", server: " + server + ", tgt: " + ticketGrantingTicket + ", service: " + service);
                }
            });
        } catch (final Exception e) {
            throw new RuntimeException("failed to get CAS service ticket, server: " + server + ", tgt: " + ticketGrantingTicket + ", service: " + service + ", cause: " + e, e);
        }
    }

    private static String getTicketGrantingTicket(final String server, final String username, final String password, OphHttpClient client) {
        logger.debug("getTicketGrantingTicket: server:'{}', user:'{}'", new Object[]{server, username});

        //username=battags&password=password&additionalParam1=paramvalue

        /*
         Response example:

		Status : 201
		URI: http://centosx/cas/v1/tickets
		Request Headers: 4
		  User-Agent = Jakarta Commons-HttpClient/3.1
		  Host = centosx
		  Content-Length = 40
		  Content-Type = application/x-www-form-urlencoded
		Response Path: /cas/v1/tickets
		Response Headers: 9
		  Date = Fri, 13 Dec 2013 00:12:37 GMT
		  Server = Noelios-Restlet-Engine/1.1..1
		  Location = http://centosx/cas/v1/tickets/TGT-14-VW7KiAZdkqqO27ysCvd9rArUfnk0SLkXdifMzywUtlI4A7mdgg-cas.centosx
		  Accept-Ranges = bytes
		  Content-Type = text/html;charset=ISO-8859-1
		  Content-Length = 430
		  Cache-Control = max-age=0, public
		  Expires = Fri, 13 Dec 2013 00:12:37 GMT
		  Connection = close
		Cookies: 0
		Response Text:
		<!DOCTYPE HTML PUBLIC "-//IETF//DTD HTML 2.0//EN"><html>
		<head><title>201 The request has been fulfilled and resulted in a new resource being created</title></head>
		<body><h1>TGT Created</h1>
		<form action="http://centosx/cas/v1/tickets/TGT-14-VW7KiAZdkqqO27ysCvd9rArUfnk0SLkXdifMzywUtlI4A7mdgg-cas.centosx" method="POST">
		Service:<input type="text" name="service" value=""><br>
		<input type="submit" value="Submit"></form>
		</body></html>
         */

        try {
            return client.post(server)
                    .dataWriter(FORM_URLENCODED, UTF8, out -> OphHttpClient.formUrlEncodedWriter(out)
                            .param("username", username)
                            .param("password", password))
                    .skipResponseAssertions()
                    .execute(r -> {
                        switch (r.getStatusCode()) {
                            case 201: {
                                List<String> locationHeaders = r.getHeaderValues("Location");
                                logger.debug("locationHeader: " + locationHeaders);
                                final String response = r.asText();
                                printTraceResponse(r, response);
                                if (locationHeaders != null && locationHeaders.size() == 1) {
                                    String responseLocation = locationHeaders.get(0);
                                    String ticket = StringUtils.substringAfterLast(responseLocation, "/");
                                    logger.debug("-> ticket: " + ticket);
                                    return ticket;
                                }
                                throw new RuntimeException("Successful ticket granting request, but no ticket found! server: " + server + ", user: " + username);
                            }
                            default: {
                                throw new RuntimeException("Invalid response code from CAS server: " + r.getStatusCode() + ", server: " + server + ", user: " + username);
                            }
                        }
                    });
        } catch (final Exception e) {
            throw new RuntimeException("error getting TGT, server: " + server + ", user: " + username + ", exception: " + e, e);
        }
    }

    private static void notNull(final Object object, final String message) {
        if (object == null) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String checkUrl(String url, final String suffix) {
        logger.debug("url: " + url);
        url = url.trim();
        url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        if (!url.endsWith(suffix)) {
            url += suffix;
        }
        logger.debug("-> fixed url: " + url);
        return url;
    }

    private static void printTraceResponse(final OphHttpResponse response, final String responseTxt) throws IOException {

        if (!logger.isTraceEnabled()) return;

        OphRequestParameters requestParameters = response.getRequestParameters();

        logger.debug("\n<cas-http-response>");
        logger.debug("Status : " + response.getStatusCode());
        logger.debug("URI: " + requestParameters.url);
        logger.debug("Request Headers: " + requestParameters.headers.size());

        for (String headerName : requestParameters.headers.keySet()) {
            for (String headerValue : requestParameters.headers.get(headerName)) {
                logger.debug("  " + headerName + " = " + headerValue);
            }
        }

        logger.debug("Response Path: " + requestParameters.url);
        logger.debug("Response Headers: " + response.getHeaderKeys().size());

        for (String headerName : response.getHeaderKeys()) {
            for (String headerValue : response.getHeaderValues(headerName)) {
                logger.debug("  " + headerName + " = " + headerValue);
            }
        }

        logger.debug("Response Text: ");
        logger.debug(responseTxt);
        logger.debug("</cas-http-response>\n");
    }


}