package dev.javarpg.server.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protective headers for the web client's page and files: nothing may load from another site, no inline script,
 * no content-type guessing. The game API, health, documentation and the API description are left exactly as they
 * were (the documentation page needs its own inline styles and must not be restricted by this policy).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    static final String CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data:; font-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; "
            + "frame-ancestors 'none'";

    private static final List<String> UNTOUCHED = List.of("/api", "/health", "/v3", "/docs", "/swagger-ui");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isUntouched(request.getRequestURI())) {
            response.setHeader("Content-Security-Policy", CONTENT_SECURITY_POLICY);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Referrer-Policy", "no-referrer");
        }
        chain.doFilter(request, response);
    }

    private static boolean isUntouched(String path) {
        for (String prefix : UNTOUCHED) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
