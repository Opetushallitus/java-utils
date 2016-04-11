package fi.vm.sade.generic.healthcheck;

import fi.vm.sade.generic.rest.CachingRestClient;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.servlet.ServletContext;

/**
 * @author Antti Salonen
 */
public class ProxyAuthenticationChecker implements HealthChecker {

    private ServletContext servletContext;
    private ApplicationContext ctx;

    public ProxyAuthenticationChecker(ServletContext servletContext, ApplicationContext ctx) {
        this.servletContext = servletContext;
        this.ctx = ctx;
    }

    @Override
    public Object checkHealth() throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) return "(must be logged in for proxyauth health check to work)";
        String currentUser = auth.getName();

        String currentAppId = servletContext.getContextPath().replaceAll("/", "");
        CachingRestClient restClient = new CachingRestClient().setClientSubSystemCode(currentAppId + ".ProxyAuthenticationChecker");
        restClient.setUseProxyAuthentication(true);
        restClient.setWebCasUrl(getProperty("web.url.cas"));
        String appurl = getProperty("cas.service." + currentAppId);
        restClient.setCasService(appurl);
        try {
            String res = restClient.getAsString(appurl+"/healthcheck?userinfo");
            if (!res.contains("\"name\": \""+currentUser+"\",")) {
                throw new Exception("proxied response should have contained current user's info (" + currentUser + "):\n" + res);
            }
            return "proxyauth ok for: "+currentUser;
        } catch (Exception e) {
            if (appurl.contains("localhost")) { // don't break whole healthcheck in localhost because this
                return "NOTE! proxyauth cannot work with localhost-urls, error: "+e.getMessage();
            } else {
                throw e;
            }
        }
    }

    private String getProperty(final String name) {
        return ((AbstractBeanFactory)ctx.getAutowireCapableBeanFactory()).resolveEmbeddedValue("${" + name + "}");
    }
}
