package dev.javarpg.server.filter;

import dev.javarpg.server.exception.BodyTooLargeException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.IOException;

/** Counts the bytes read from the delegate and throws BodyTooLargeException once the limit is passed. */
final class CountingInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long limit;
    private long count;

    CountingInputStream(ServletInputStream delegate, long limit) {
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
