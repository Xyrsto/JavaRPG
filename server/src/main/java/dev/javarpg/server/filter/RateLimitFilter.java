package dev.javarpg.server.filter;

import dev.javarpg.server.config.JavaRpgProperties;
import dev.javarpg.server.util.TokenBucketRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client-IP rate limits on POST /api/v1/players, and on the run and submit endpoints (F-14).
 * The client is identified by getRemoteAddr(); X-Forwarded-For is used only when
 * javarpg.rate.trust-forwarded=true (it is client controlled otherwise).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String PLAYERS_PATH = "/api/v1/players";
    private static final Pattern RUN_PATH = Pattern.compile("^/api/v1/players/[^/]+/quests/[^/]+/run$");
    private static final Pattern SUBMIT_PATH = Pattern.compile("^/api/v1/players/[^/]+/quests/[^/]+/submit$");

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
            String path = request.getRequestURI();
            String bucket = null;
            int limit = 0;
            if (PLAYERS_PATH.equals(path)) {
                bucket = "players";
                limit = props.getRate().getPlayersPerMinute();
            } else if (RUN_PATH.matcher(path).matches()) {
                bucket = "run";
                limit = props.getRate().getRunPerMinute();
            } else if (SUBMIT_PATH.matcher(path).matches()) {
                bucket = "submit";
                limit = props.getRate().getSubmitPerMinute();
            }
            if (bucket != null) {
                long wait = limiter.tryAcquire(bucket + "|" + clientIp(request), limit);
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
