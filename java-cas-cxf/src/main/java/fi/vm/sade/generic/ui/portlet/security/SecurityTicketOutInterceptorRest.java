package fi.vm.sade.generic.ui.portlet.security;

import org.apache.cxf.message.Message;

/**
 * Security ticket interceptor for REST clients
 *
 * User: wuoti
 * Date: 3.9.2013
 * Time: 9.58
 */
@Deprecated // korvattava httpsessio/cookie pohjaisella ratkaisulla, esim: SessionBasedCxfAuthInterceptor
public class SecurityTicketOutInterceptorRest extends AbstractSecurityTicketOutInterceptor<Message> {
    public SecurityTicketOutInterceptorRest() {
    }
}
