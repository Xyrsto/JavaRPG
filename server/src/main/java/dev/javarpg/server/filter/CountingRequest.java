package dev.javarpg.server.filter;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Request wrapper whose body stream throws BodyTooLargeException past the limit (F-13). */
final class CountingRequest extends HttpServletRequestWrapper {

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
            stream = new CountingInputStream(super.getInputStream(), limit);
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
