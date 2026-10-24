package dev.javarpg.server.config;

import dev.javarpg.server.util.TokenBucketRateLimiter;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cross-cutting beans: the javarpg.* settings, the clock (injected so time stays testable) and the rate limiter. */
@Configuration
@EnableConfigurationProperties(JavaRpgProperties.class)
public class InfrastructureConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    public TokenBucketRateLimiter rateLimiter(Clock clock, JavaRpgProperties props) {
        return new TokenBucketRateLimiter(clock, props.getRate().getMaxTrackedClients());
    }
}
