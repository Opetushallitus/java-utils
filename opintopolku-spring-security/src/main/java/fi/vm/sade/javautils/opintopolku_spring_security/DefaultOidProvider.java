package fi.vm.sade.javautils.opintopolku_spring_security;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class DefaultOidProvider implements OidProvider {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final String organisaatioServiceUrl;

    private final String rootOrganisaatioOid;
    private final String callerId;

    public DefaultOidProvider(String organisaatioServiceUrl, String rootOrganisaatioOid, String callerId) {
        this.organisaatioServiceUrl = organisaatioServiceUrl;
        this.rootOrganisaatioOid = rootOrganisaatioOid;
        this.callerId = callerId;
    }

    @Override
    public List<String> getSelfAndParentOids(String organisaatioOid) {
        try {
            String url = organisaatioServiceUrl + "/rest/organisaatio/" + organisaatioOid + "/parentoids";
            String result = httpGet(url, 200);
            return Arrays.asList(result.split("/"));
        } catch (Exception e) {
            log.warn("failed to getSelfAndParentOids, exception: " + e + ", returning only rootOrganisaatioOid and organisaatioOid");
            return Arrays.asList(rootOrganisaatioOid, organisaatioOid);
        }
    }

    private String httpGet(String url, int expectedStatus) {
        HttpClient client = new HttpClient();
        GetMethod get = new GetMethod(url);
        get.addRequestHeader("Caller-Id", callerId);
        try {
            client.executeMethod(get);
            final String response = get.getResponseBodyAsString();
            if (get.getStatusCode() == expectedStatus) {
                return response;
            } else {
                throw new RuntimeException("failed to call '" + url + "', invalid status: " + get.getStatusCode() + "/" + get.getStatusText());
            }
        } catch (final Exception e) {
            throw new RuntimeException("failed to call '" + url + "': " + e, e);
        } finally {
            get.releaseConnection();
        }
    }
}
