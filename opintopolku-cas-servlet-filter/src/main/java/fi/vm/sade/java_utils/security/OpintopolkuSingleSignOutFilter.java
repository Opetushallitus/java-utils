package fi.vm.sade.java_utils.security;

import fi.vm.sade.properties.OphProperties;
import org.jasig.cas.client.Protocol;
import org.jasig.cas.client.configuration.ConfigurationKeys;
import org.jasig.cas.client.session.SingleSignOutFilter;

import javax.servlet.*;
import java.io.IOException;
import java.nio.file.Paths;

public class OpintopolkuSingleSignOutFilter implements Filter {
    private final static String WEB_URL_CAS = "web.url.cas";
    private SingleSignOutFilter singleSignOutFilter;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        final String userHome = System.getProperty("user.home");
        final OphProperties ophProperties = new OphProperties()
                .addFiles(Paths.get(userHome, "/oph-configuration/common.properties").toString());
        final String webUrlCas = ophProperties.require(WEB_URL_CAS);
        this.singleSignOutFilter = new SingleSignOutFilter();
        this.singleSignOutFilter.setIgnoreInitConfiguration(true);
        this.singleSignOutFilter.setArtifactParameterName(Protocol.CAS2.getArtifactParameterName());
        this.singleSignOutFilter.setLogoutParameterName("logoutRequest");
        this.singleSignOutFilter.setRelayStateParameterName("RelayState");
        this.singleSignOutFilter.init(filterConfig);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        this.singleSignOutFilter.doFilter(request,response,chain);
    }

    @Override
    public void destroy() {
        this.singleSignOutFilter.destroy();
    }
}
