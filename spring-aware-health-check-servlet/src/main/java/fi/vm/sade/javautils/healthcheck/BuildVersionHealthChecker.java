package fi.vm.sade.javautils.healthcheck;

import fi.vm.sade.javautils.healthcheck.HealthChecker;

import javax.servlet.ServletContext;
import java.util.HashMap;
import java.util.Properties;

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
