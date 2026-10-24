package dev.javarpg.server.filter;

import dev.javarpg.core.contract.ErrorCode;
import dev.javarpg.server.config.JavaRpgProperties;
import dev.javarpg.server.exception.BodyTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects oversized request bodies before they are deserialised (F-13): a declared Content-Length above
 * the limit gets 413 straight away; chunked or unknown-length bodies are counted byte by byte (see
 * CountingRequest) and fail with 413 once the limit is passed.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class BodySizeLimitFilter extends OncePerRequestFilter {

    static final String MESSAGE = "That request is too large. Send less data.";

    private final JavaRpgProperties props;

    public BodySizeLimitFilter(JavaRpgProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = props.getHttp().getMaxBodyBytes();
        if (request.getContentLengthLong() > limit) {
            tooLarge(response);
            return;
        }
        try {
            chain.doFilter(new CountingRequest(request, limit), response);
        } catch (ServletException | IOException e) {
            if (BodyTooLargeException.isCausedBy(e) && !response.isCommitted()) {
                response.resetBuffer();
                tooLarge(response);
                return;
            }
            throw e;
        }
    }

    private static void tooLarge(HttpServletResponse response) throws IOException {
        FilterErrors.write(response, ErrorCode.SOURCE_TOO_LARGE, MESSAGE);
    }
}
