package fi.vm.sade.generic.ui.portlet.security;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.interceptor.SoapInterceptor;
import org.apache.cxf.binding.soap.interceptor.SoapPreProtocolOutInterceptor;

import javax.xml.namespace.QName;
import java.net.URI;
import java.util.Collections;
import java.util.Set;

/**
 * Interceptor for adding a security ticket SOAP header into outbound SOAP message.
 * Should be used by ticket-aware clients of services.
 *
 * @author Eetu Blomqvist
 */
@Deprecated // korvattava httpsessio/cookie pohjaisella ratkaisulla, esim: SessionBasedCxfAuthInterceptor
public class SecurityTicketOutInterceptor extends AbstractSecurityTicketOutInterceptor<SoapMessage> implements SoapInterceptor {
    public SecurityTicketOutInterceptor() {
        super();
        getAfter().add(SoapPreProtocolOutInterceptor.class.getName());
    }

    @Override
    public Set<URI> getRoles() {
        return Collections.emptySet();
    }

    @Override
    public Set<QName> getUnderstoodHeaders() {
        return Collections.emptySet();
    }
}
