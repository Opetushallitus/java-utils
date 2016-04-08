package fi.vm.sade.generic.rest;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation for Jersey REST annotated methods, that tells that the resource is cacheable (Cache-control: max-age: X), must be used with CacheableJerseyFilter.
 *
 * Usage:
 *
 *     @Path("/cacheableAnnotatedResource")
 *     @GET
 *     @Produces("text/plain")
 *     @Cacheable(maxAgeSeconds = 2)
 *     public Response cacheableAnnotatedResource() { ... }
 *
 * web.xml:
 *
 *     <servlet>
 *     <servlet-name>Jersey REST Service</servlet-name>
 *     <servlet-class>com.sun.jersey.spi.spring.container.servlet.SpringServlet</servlet-class>
 *     <init-param>
 *     <param-name>com.sun.jersey.api.json.POJOMappingFeature</param-name>
 *     <param-value>true</param-value>
 *     </init-param>
 *     <init-param>
 *     <param-name>com.sun.jersey.spi.container.ContainerResponseFilters</param-name>
 *     <param-value>fi.vm.sade.generic.rest.CacheableJerseyFilter</param-value>
 *     </init-param>
 *     <load-on-startup>1</load-on-startup>
 *     </servlet>
 *
 * @see CacheableJerseyFilter
 * @author Antti Salonen
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface Cacheable {
    int maxAgeSeconds () default 60*60; // default one hour
}
