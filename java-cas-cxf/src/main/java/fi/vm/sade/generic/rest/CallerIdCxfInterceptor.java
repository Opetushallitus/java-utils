package fi.vm.sade.generic.rest;

import java.net.HttpURLConnection;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.HTTPConduit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interceptor for adding Caller-Id header to all requests. Interceptor must be registered for all 
 * services, in xml like following:
 * <bean id="callerIdInterceptor" class="fi.vm.sade.generic.rest.CallerIdCxfInterceptor">
 *   <property name="headerValue" value="${caller.id}"/>
 * </bean>
 *  <cxf:bus>
 *      <cxf:outInterceptors>
 *          <ref bean="callerIdInterceptor"/>
 *     </cxf:outInterceptors>
 *  </cxf:bus>
 */
public class CallerIdCxfInterceptor<T extends Message> extends AbstractPhaseInterceptor<T> {

    private static final Logger log = LoggerFactory.getLogger(CallerIdCxfInterceptor.class);

    private String headerName = "Caller-Id";
    private String headerValue = null;

    public CallerIdCxfInterceptor() {
        // Intercept in receive phase
        super(Phase.PRE_PROTOCOL);
    }

    /**
     * Invoked on in- and outbound (if interceptor is registered for both, which makes no sense). 
     */
    @Override
    public void handleMessage(Message message) throws Fault {
        this.handleOutbound(message.getExchange().getOutMessage());
    }

    /**
     * Invoked on outbound (request).
     * @param message
     * @throws Fault
     */
    public void handleOutbound(Message message) throws Fault {
        log.debug("Inbound message intercepted for Caller-Id insertion.");

        HttpURLConnection conn = resolveConnection(message);
        
        if(this.getHeaderValue() != null)
            conn.setRequestProperty(this.getHeaderName(), this.getHeaderValue());
        else
            log.warn("Missing Caller-Id headerValue. Set headerValue for CallerIdCxfInterceptor.");
    }

    /**
     * Resolve connection from message.
     */
    private static HttpURLConnection resolveConnection(Message message) {
        HttpURLConnection conn = (HttpURLConnection)message.getExchange().getOutMessage().get(HTTPConduit.KEY_HTTP_CONNECTION);
        return conn;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getHeaderValue() {
        return headerValue;
    }

    public void setHeaderValue(String headerValue) {
        this.headerValue = headerValue;
    }

}
