package fi.vm.sade.javautils.legacy_caching_rest_client;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.StringWriter;
import java.util.Enumeration;

/**
 * Mock resource for mirroring request for testing purposes.
 */
@Path("/mirror")
public class MirrorMockResource {

    /**
     * Returns request headers in the response body.
     * @param request
     * @return
     */
    @Path("/headers")
    @GET
    @Produces("text/plain")
    public Response mirrorHeaders(@Context HttpServletRequest request) {
        StringWriter out = new StringWriter();
        @SuppressWarnings("unchecked")
        Enumeration<String> headerNames = request.getHeaderNames();
        while(headerNames.hasMoreElements()) {
            String one = headerNames.nextElement();
            out.write(one + ": " + request.getHeader(one) + "\n");
        }

        return Response.ok(out.toString()).build();
    }
}
