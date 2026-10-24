package dev.javarpg.server.config;

import java.util.Locale;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

/**
 * Decides whether the API description and the browser documentation page are served (decision 1 of spec 004).
 * Explicit javarpg.docs.enabled wins. Otherwise they are on only when the server listens on loopback, so a
 * server reachable from other machines does not offer them unless the owner asks for it.
 */
public class DocsSwitch implements EnvironmentPostProcessor {

    static final String PROPERTY = "javarpg.docs.enabled";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String enabled = Boolean.toString(docsEnabled(environment));
        // Lowest precedence, so an explicit springdoc.* setting on the command line still wins.
        environment.getPropertySources().addLast(new MapPropertySource("javarpgDocsSwitch", Map.of(
                "springdoc.api-docs.enabled", enabled,
                "springdoc.swagger-ui.enabled", enabled)));
    }

    public static boolean docsEnabled(Environment environment) {
        String explicit = environment.getProperty(PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            return Boolean.parseBoolean(explicit.trim());
        }
        return isLoopback(environment.getProperty("server.address"));
    }

    static boolean isLoopback(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        String a = address.trim().toLowerCase(Locale.ROOT);
        if (a.startsWith("[") && a.endsWith("]")) {
            a = a.substring(1, a.length() - 1);
        }
        return a.equals("localhost") || a.startsWith("127.") || a.equals("::1") || a.equals("0:0:0:0:0:0:0:1");
    }
}
