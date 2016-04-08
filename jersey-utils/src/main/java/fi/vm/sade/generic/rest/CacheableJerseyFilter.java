package fi.vm.sade.generic.rest;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;

import javax.ws.rs.core.CacheControl;
import java.lang.annotation.Annotation;

/**
 * Filter that applies cache headers to Jersey REST responses defined by @Cacheable -annotations.
 *
 * @see Cacheable
 * @author Antti Salonen
 */
public class CacheableJerseyFilter implements ContainerResponseFilter {

    @Override
    public ContainerResponse filter(ContainerRequest containerRequest, ContainerResponse containerResponse) {

        Cacheable cacheableAnnotation = getAnnotation(containerResponse, Cacheable.class);
        if (cacheableAnnotation != null) {
            containerResponse.getHttpHeaders().putSingle("Cache-Control", "public, max-age="+cacheableAnnotation.maxAgeSeconds());
        }

        return containerResponse;
    }

    private <T> T getAnnotation(ContainerResponse containerResponse, Class<? extends T> aClass) {
        for (Annotation annotation : containerResponse.getAnnotations()) {
            if (aClass.isAssignableFrom(annotation.getClass())) {
                return (T) annotation;
            }
        }
        return null;
    }

    /*
    private String getServerTime(int dSeconds) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, dSeconds); // 24h
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormat.format(calendar.getTime());
    }
    */

}
