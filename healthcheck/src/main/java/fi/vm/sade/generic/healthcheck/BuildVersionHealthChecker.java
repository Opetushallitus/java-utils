package fi.vm.sade.generic.healthcheck;

import javax.servlet.ServletContext;
import java.util.HashMap;
import java.util.Properties;

/**
 * @author Antti Salonen
 */
public class BuildVersionHealthChecker implements HealthChecker {
    private ServletContext servletContext;

    public BuildVersionHealthChecker(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    @Override
    public Object checkHealth() throws Throwable {
        Properties buildversionProps = new Properties();
        buildversionProps.load(servletContext.getResourceAsStream("buildversion.txt"));
        return new HashMap(buildversionProps);
    }
}
