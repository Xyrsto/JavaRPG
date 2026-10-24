package dev.javarpg.server.config;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the API, limited to the origins listed in javarpg.cors.allowed-origins. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JavaRpgProperties props;

    public WebConfig(JavaRpgProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = props.getCors().getAllowedOrigins();
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins.toArray(new String[0]))
                .allowedMethods("GET", "POST");
    }
}
