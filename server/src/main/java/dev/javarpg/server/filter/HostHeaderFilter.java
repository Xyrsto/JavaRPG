package dev.javarpg.server.filter;

import dev.javarpg.server.config.JavaRpgProperties;
import dev.javarpg.server.config.SandboxSafety;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * In unsafe-local mode only requests addressed to localhost, 127.0.0.1 or [::1] are served (F-12a), which
 * blocks DNS rebinding and requests that reached the machine by another name. Other modes pass through.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HostHeaderFilter extends OncePerRequestFilter {

    private final JavaRpgProperties props;

    public HostHeaderFilter(JavaRpgProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SandboxSafety.UNSAFE_LOCAL.equals(props.getSandbox().getMode())
                && !isLocalHost(request.getHeader("Host"))) {
            FilterErrors.write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN",
                    "This server only answers on localhost.");
            return;
        }
        chain.doFilter(request, response);
    }

    /** True for localhost, 127.0.0.1 or [::1], each with an optional numeric port. */
    static boolean isLocalHost(String hostHeader) {
        if (hostHeader == null) {
            return false;
        }
        String h = hostHeader.trim().toLowerCase(Locale.ROOT);
        String host;
        String port = null;
        if (h.startsWith("[")) {
            int end = h.indexOf(']');
            if (end < 0) {
                return false;
            }
            host = h.substring(0, end + 1);
            String rest = h.substring(end + 1);
            if (!rest.isEmpty()) {
                if (!rest.startsWith(":")) {
                    return false;
                }
                port = rest.substring(1);
            }
        } else {
            int colon = h.indexOf(':');
            if (colon >= 0) {
                host = h.substring(0, colon);
                port = h.substring(colon + 1);
            } else {
                host = h;
            }
        }
        if (port != null && !port.matches("[0-9]{1,5}")) {
            return false;
        }
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]");
    }
}
