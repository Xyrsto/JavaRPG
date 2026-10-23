package dev.javarpg.server.filter;

import dev.javarpg.server.config.JavaRpgProperties;
import dev.javarpg.server.exception.BodyTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects oversized request bodies before they are deserialised (F-13): a declared Content-Length above
 * the limit gets 413 straight away; chunked or unknown-length bodies are counted byte by byte and fail
 * with 413 once the limit is passed.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class BodySizeLimitFilter extends OncePerRequestFilter {

    static final String CODE = "SOURCE_TOO_LARGE";
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
            FilterErrors.write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, CODE, MESSAGE);
            return;
        }
        try {
            chain.doFilter(new CountingRequest(request, limit), response);
        } catch (ServletException | IOException e) {
            if (BodyTooLargeException.isCausedBy(e) && !response.isCommitted()) {
                response.resetBuffer();
                FilterErrors.write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, CODE, MESSAGE);
                return;
            }
            throw e;
        }
    }

    /** Request wrapper whose body stream throws BodyTooLargeException past the limit. */
    static final class CountingRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream stream;
        private BufferedReader reader;

        CountingRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (reader != null) {
                throw new IllegalStateException("getReader() was already called");
            }
            if (stream == null) {
                stream = new CountingStream(super.getInputStream(), limit);
            }
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (reader == null) {
                String name = getCharacterEncoding();
                Charset charset = name == null ? StandardCharsets.UTF_8 : Charset.forName(name);
                reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
            }
            return reader;
        }
    }

    private static final class CountingStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long count;

        CountingStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        private void add(long n) throws IOException {
            count += n;
            if (count > limit) {
                throw new BodyTooLargeException(limit);
            }
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                add(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            int n = delegate.read(buffer, off, len);
            if (n > 0) {
                add(n);
            }
            return n;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
