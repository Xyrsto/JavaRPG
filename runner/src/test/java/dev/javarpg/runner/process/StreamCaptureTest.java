package dev.javarpg.runner.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StreamCaptureTest {

    private static StreamCapture capture(String text, int cap, Runnable onOverflow) throws InterruptedException {
        StreamCapture c = new StreamCapture("test-reader",
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), cap, onOverflow);
        c.start();
        c.join(2000);
        return c;
    }

    @Test
    void keepsEverythingUnderTheCap() throws InterruptedException {
        StreamCapture c = capture("hello", 100, null);
        assertEquals("hello", c.text());
        assertFalse(c.limitHit());
    }

    @Test
    void truncatesAtTheCapAndFiresOverflowOnce() throws InterruptedException {
        AtomicInteger fired = new AtomicInteger();
        StreamCapture c = capture("x".repeat(10_000), 5000, fired::incrementAndGet);
        assertEquals(5000, c.text().length());
        assertTrue(c.limitHit());
        assertEquals(1, fired.get());
    }
}
