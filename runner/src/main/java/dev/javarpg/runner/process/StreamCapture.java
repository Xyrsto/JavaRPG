package dev.javarpg.runner.process;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads a child process stream into a bounded buffer. Past the cap it keeps draining (so the child never
 * blocks on a full pipe) but discards the bytes, and fires the optional overflow callback once.
 */
public final class StreamCapture extends Thread {

    private static final Logger LOG = Logger.getLogger(StreamCapture.class.getName());

    private final InputStream in;
    private final int cap;
    private final Runnable onOverflow;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final Object lock = new Object();
    private boolean limitHit;

    /** onOverflow may be null. */
    public StreamCapture(String threadName, InputStream in, int cap, Runnable onOverflow) {
        super(threadName);
        this.in = in;
        this.cap = cap;
        this.onOverflow = onOverflow;
        setDaemon(true);
    }

    @Override
    public void run() {
        byte[] chunk = new byte[4096];
        try (InputStream source = in) {
            int n;
            while ((n = source.read(chunk)) != -1) {
                boolean fire = false;
                synchronized (lock) {
                    int room = cap - buffer.size();
                    if (n <= room) {
                        buffer.write(chunk, 0, n);
                    } else {
                        if (room > 0) {
                            buffer.write(chunk, 0, room);
                        }
                        fire = !limitHit;
                        limitHit = true;
                    }
                }
                if (fire && onOverflow != null) {
                    onOverflow.run();
                }
            }
        } catch (IOException e) {
            // The pipe closes when the process ends or is killed; there is nothing more to read.
            LOG.log(Level.FINE, "Sandbox stream closed", e);
        }
    }

    public String text() {
        synchronized (lock) {
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    /** True when more bytes arrived than the cap allows. */
    public boolean limitHit() {
        synchronized (lock) {
            return limitHit;
        }
    }
}
