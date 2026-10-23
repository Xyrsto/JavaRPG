package dev.javarpg.server.filter;

import dev.javarpg.server.config.JavaRpgProperties;
import dev.javarpg.server.util.TokenBucketRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client-IP rate limits on POST /api/v1/players, and on the run and submit endpoints (F-14).
 * The client is identified by getRemoteAddr(); X-Forwarded-For is used only when
 * javarpg.rate.trust-forwarded=true (it is client controlled otherwise).
 * To limit another endpoint, add a Rule to RULES.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class RateLimitFilter extends OncePerRequestFilter {

    /** One limited endpoint: the bucket name, which paths it covers and where its per-minute limit lives. */
    private record Rule(String bucket, Predicate<String> covers, ToIntFunction<JavaRpgProperties.Rate> limit) {
        static Rule exact(String bucket, String path, ToIntFunction<JavaRpgProperties.Rate> limit) {
            return new Rule(bucket, path::equals, limit);
        }

        static Rule matching(String bucket, String regex, ToIntFunction<JavaRpgProperties.Rate> limit) {
            Pattern pattern = Pattern.compile(regex);
            return new Rule(bucket, path -> pattern.matcher(path).matches(), limit);
        }
    }

    private static final List<Rule> RULES = List.of(
            Rule.exact("players", "/api/v1/players", JavaRpgProperties.Rate::getPlayersPerMinute),
            Rule.matching("run", "^/api/v1/players/[^/]+/quests/[^/]+/run$", JavaRpgProperties.Rate::getRunPerMinute),
            Rule.matching("submit", "^/api/v1/players/[^/]+/quests/[^/]+/submit$",
                    JavaRpgProperties.Rate::getSubmitPerMinute));

    private final TokenBucketRateLimiter limiter;
    private final JavaRpgProperties props;

    public RateLimitFilter(TokenBucketRateLimiter limiter, JavaRpgProperties props) {
        this.limiter = limiter;
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod())) {
            Optional<Rule> rule = ruleFor(request.getRequestURI());
            if (rule.isPresent()) {
                long wait = limiter.tryAcquire(rule.get().bucket() + "|" + clientIp(request),
                        rule.get().limit().applyAsInt(props.getRate()));
                if (wait > 0) {
                    response.setHeader("Retry-After", Long.toString(wait));
                    FilterErrors.write(response, 429, "RATE_LIMITED",
                            "You are going a little fast. Wait a few seconds and try again.");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static Optional<Rule> ruleFor(String path) {
        return RULES.stream().filter(rule -> rule.covers().test(path)).findFirst();
    }

    private String clientIp(HttpServletRequest request) {
        if (props.getRate().isTrustForwarded()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isEmpty() && first.length() <= 64) {
                    return first;
                }
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}
