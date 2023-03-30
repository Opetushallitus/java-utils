package fi.vm.sade.javautils.nio.cas.impl;

import fi.vm.sade.javautils.nio.cas.CasConfig;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.Charset;

public class CasUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(CasUtils.class);
    private final CasConfig config;
    private final String serviceLoginRedirectLocation;

    public CasUtils(CasConfig config) {
        this.config = config;
        this.serviceLoginRedirectLocation = config.getRefreshTicketsOnLoginRedirect() ?
                String.format("%s/login?service=%s", config.getCasUrl(), URLEncoder.encode(config.getSessionUrl(), Charset.defaultCharset())) : null;
    }

    public RequestBuilder withCallerIdAndCsrfHeader(RequestBuilder requestBuilder) {
        return requestBuilder.setHeader("Caller-Id", config.getCallerId())
                .setHeader("CSRF", config.getCsrf())
                .addOrReplaceCookie(new DefaultCookie("CSRF", config.getCsrf()));
    }

    public RequestBuilder withCallerIdAndCsrfHeader() {
        return withCallerIdAndCsrfHeader(new RequestBuilder());
    }

    public RequestBuilder withTicket(RequestBuilder requestBuilder, String ticket) {
        if (this.config.getServiceTicketHeaderName() == null) {
            return requestBuilder.addQueryParam("ticket", ticket);
        } else {
            return requestBuilder.addHeader(this.config.getServiceTicketHeaderName(), ticket);
        }
    }

    public boolean shouldReauthenticate(Response response) {
        if (this.config.getRefreshTicketsOnLoginRedirect() && response.getStatusCode() == 302) {
            // Only consider status 302 for now.
            // Eg. permanent redirects should probably not be taken into account!
            String location = response.getHeader("Location");
            if (location != null && location.equals(serviceLoginRedirectLocation)) {
                return true;
            } else {
                LOGGER.warn("Response status was 302, but the Location header didn't match expectation. Location: " + location);
                return false;
            }
        } else {
            return false;
        }

    }
}
