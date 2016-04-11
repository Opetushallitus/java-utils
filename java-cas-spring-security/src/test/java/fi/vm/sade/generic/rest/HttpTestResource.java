package fi.vm.sade.generic.rest;

import org.apache.commons.codec.binary.Hex;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * @author Antti Salonen
 */
@Path("/httptest")
public class HttpTestResource {

    public static int counter = 1;
    public static String someResource = "original value";

    @Path("/pingCached1sec")
    @GET
    @Produces("text/plain")
    public Response pingCached1sec() {
        System.out.println("HttpTest.pingCached1sec, counter: " + counter + ", now: " + new Date(System.currentTimeMillis()));
        return Response
                .ok("pong " + (counter++))
                .expires(date(2))
                .build();
    }

    @Path("/someResource")
    @GET
    @Produces("text/plain")
    public Response someResource(@Context javax.ws.rs.core.Request request) {
        System.out.println("HttpTest.someResource: "+someResource+", counter: "+counter+", now: " + new Date(System.currentTimeMillis()));

        EntityTag etag = new EntityTag(Hex.encodeHexString(someResource.getBytes()));
        Response.ResponseBuilder responseBuilder = request.evaluatePreconditions(etag);

        // Etag match = if resource not changed -> do nothing and return "unmodified" -http response (note also maxage-tag)
        if (responseBuilder != null) {
            System.out.println("resource has not changed..returning unmodified response code");
            return responseBuilder
                    .expires(date(2))
                    .build();
        }

        // otherwise do actual logic and tag response with etag and maxage -headers
        return Response
                .ok(someResource+" "+(counter++))
                .tag(etag)
                .expires(date(2))
                .build();
    }

    @Path("/cacheableAnnotatedResource")
    @GET
    @Produces("text/plain")
    @Cacheable(maxAgeSeconds = 2)
    public Response cacheableAnnotatedResource() {
        System.out.println("HttpTest.cacheableAnnotatedResource, counter: " + counter + ", now: " + new Date(System.currentTimeMillis()));

        return Response
                .ok("cacheable " + (counter++))
                .build();
    }

    @Path("/oneSecondResource")
    @GET
    @Produces("text/plain")
    public Response oneSecondResource() throws InterruptedException {
        Thread.sleep(1000);
        return Response.ok("OK").build();
    }

    @Path("/xmlgregoriancalendar1")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String xmlgregoriancalendar1() throws InterruptedException {
        return ""+new Date().getTime();
    }

    @Path("/xmlgregoriancalendar2")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String xmlgregoriancalendar2() throws InterruptedException {
        return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
    }

    private Date date(int dSeconds) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, dSeconds); // 24h
        return calendar.getTime();
    }

    @Path("/status500")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response status500() {
        return Response.status(500).build();
    }

    @Path("/pingSecuredRedirect/{sthing}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response pingSecuredRedirect(@Context HttpServletRequest request) throws URISyntaxException {
        System.out.println("HttpTestResource.pingSecuredRedirect, params: "+request.getParameterMap());
        if (MockCasResource.isRequestAuthenticated(request) && request.getParameter("SKIP_CAS_FILTER")==null) {
            String s = "pong " + (counter++);
            System.out.println("HttpTestResource.pingSecuredRedirect, ok: "+s);
            return Response.ok(s).build();
        } else {
            String url = "/mock_cas/cas?service=" + request.getRequestURL();
            System.out.println("HttpTestResource.pingSecuredRedirect, redirect: "+url);
            return Response.status(302).location(new URI(url)).build();
        }
    }

    @Path("/pingSecuredRedirect/{sthing}")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response pingSecuredRedirectPost(@Context HttpServletRequest request) throws URISyntaxException {
        return pingSecuredRedirect(request);
    }

    @Path("/pingSecured401Unauthorized")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response pingSecured401Unauthorized(@Context HttpServletRequest request) throws URISyntaxException {
        if (MockCasResource.isRequestAuthenticated(request)) {
            return Response.ok("pong " + (counter++)).build();
        }
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    @Path("/testResourceNoContent")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response testResourceNoContent(@Context HttpServletRequest request) throws URISyntaxException {
        return Response.status(Response.Status.NOT_MODIFIED).build();
    }

    @Path("/testMethod")
    @GET
    public Response testMethod(@Context HttpServletRequest request) {
        if (!MockCasResource.isRequestAuthenticated(request)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        return Response.ok("testResult").build();
    }

    @Path("/special-character-resource")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response post(String json) {
        return pingBackJson(json);
    }

    @Path("/special-character-resource")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response put(String json) {
        return pingBackJson(json);
    }

    private Response pingBackJson(String json) {
        System.out.println("got json: " + json);
        return Response.ok(json).build();
    }

    @Path("/printcookies")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response printcookies(@Context HttpServletRequest request) throws URISyntaxException {
        Cookie[] cookies = request.getCookies();
        String result = "";
        for (Cookie cookie : cookies) {
            result += ""+cookie.getName()+"="+cookie.getValue()+"(" +
                    "|domain:"+cookie.getDomain()+"" +
                    "|path:"+cookie.getPath() +
                    "|maxage:"+cookie.getMaxAge() +
                    ")\n";
        }
        return Response.ok(result)
                .header("sessionid", request.getSession(true).getId())
                .build();
    }

    @Path("/buildversion.txt")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response j_spring_cas_security_check(@Context HttpServletRequest request) throws URISyntaxException {
        String ticket = request.getParameter("ticket");
        //System.out.println("HttpTestResource.j_spring_cas_security_check, ticket: "+ ticket);
        HttpSession sess = request.getSession(true); // synnyttää JSESSIONID:n
        String ticketCookie = ticket.replaceAll(":|/", "_");
//        String ticketCookie = "asdasd";
        return Response.ok("sessionid: "+sess.getId())
                .header("sessionid", sess.getId())
                .cookie(new NewCookie("TIKETTICOOKIE", ticketCookie))
                .build();
    }


}
