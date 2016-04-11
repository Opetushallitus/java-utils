package fi.vm.sade.generic.rest;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

/**
 * Simple cas http mock, works with CasClient, see also HttpTestResource.
 *
 * @author Antti Salonen
 */
@Path("/mock_cas")
public class MockCasResource {

    public static boolean isRequestAuthenticated(HttpServletRequest request) { // todo: tän voisi korvata casfiltterillä oikeastaan niin ois todellisempi
        TestParams.instance.isRequestAuthenticatedCount++;
        TestParams.prevRequestTicketHeaders = Collections.list(request.getHeaders("CasSecurityTicket"));

        String ticket = request.getParameter("ticket");
        if (ticket == null) ticket = request.getHeader("CasSecurityTicket"); // jos ticket headerissa
        System.out.print("isRequestAuthenticated, request: " + request.getRequestURL() + ", ticket: " + ticket + ", failNextBackendAuthentication: " + TestParams.instance.failNextBackendAuthentication);

        if (ticket.contains("illegaluser")) {
            System.out.println(" --> false (illegaluser)");
            return false;
        }

        if (TestParams.instance.failNextBackendAuthentication) {
            TestParams.instance.failNextBackendAuthentication = false;
            request.getSession().invalidate();
            System.out.println(" --> false (failNextBackendAuthentication)");
            return false;
        }

        // jos sessio on jo autentikoitu, ei autentikoida cassia vasten vaan luotetaan sessioon
        Object sessionTicket = request.getSession().getAttribute("authenticatedTicket");
        if (sessionTicket != null) {
            // ...paitsi ainoastaan näin mikäli sama tiketti.. jos uusi tiketti parametrina, casfilter autentikoi uusiksi
            if (sessionTicket.equals(ticket)) {
                System.out.println(" --> true (authenticatedTicket)");
                return true;
            }
        }

        boolean ok = ticket != null && !ticket.startsWith("invalid");
        if (ok) {
            TestParams.instance.authTicketValidatedSuccessfullyCount++;
            request.getSession().setAttribute("authenticatedTicket", ticket);
        } else {
            request.getSession().invalidate();
        }
        System.out.println(" --> "+ok);
        return ok;
    }

    @Path("/cas")
    @GET
    public Response casRedirectToServiceWithTicket(@Context HttpServletRequest request) throws URISyntaxException {
        String service = request.getParameter("service");

        if (TestParams.instance.userIsAlreadyAuthenticatedToCas != null && service != null) {
            // käyttäjällä on jo autentikoitu sessio cassiin -> redirect to target service with ticket
            TestParams.instance.authRedirects++;
            String url = service + "?ticket=REDIRECTED_FROM_CAS_" + TestParams.instance.userIsAlreadyAuthenticatedToCas + "_" + System.currentTimeMillis();
            System.out.println("MockCasResource.casRedirectToServiceWithTicket, service: "+service+" -> http 302 redir to: "+url);
            return Response.status(302).location(new URI(url)).build();
        }

        // mock cas auth+redirect toimii vain jos userIsAlreadyAuthenticatedToCas ja request.service annettu
        System.out.println("MockCasResource.casRedirectToServiceWithTicket, service: "+service+", user not logged in -> http 200 show login page");
        return Response.ok("this is cas login page").build();
    }

    @Path("/cas/v1/tickets")
    @POST
    public Response createCasTgt(@Context HttpServletRequest request, @FormParam("username") String username, @FormParam("password") String password) throws URISyntaxException {
        System.out.println("MockCasResource.cas tgt, username: "+ username);
        if (username == null) throw new NullPointerException("username param is null"); // tunnareiden "tarkastus"
        String tgt = "TEMP_TGTX_"+username+"_"+System.currentTimeMillis();
        TestParams.instance.authTgtCount++;
        return Response.created(new URI("/mock_cas/cas/v1/tickets/" + tgt + "?user=" + username)).build();
    }

    @Path("/cas/v1/tickets/{tgt}")
    @POST
    public Response getCasServiceTicket(@PathParam("tgt") String tgt, @FormParam("service") String service, @QueryParam("user") String user) throws URISyntaxException {
        System.out.println("MockCasResource.cas getCasServiceTicket, tgt: "+ tgt+", service: "+service+", user: "+user);
        if (tgt == null) throw new NullPointerException("tgt param is null");
        if (service == null) throw new NullPointerException("service param is null");
        String ticket = "TEMP_STX_"+(++TestParams.instance.ticketNr)+"_"+user+"_"+service+"_"+System.currentTimeMillis();
        TestParams.instance.authTicketCount++;
        return Response.ok(ticket).build();
    }

}
