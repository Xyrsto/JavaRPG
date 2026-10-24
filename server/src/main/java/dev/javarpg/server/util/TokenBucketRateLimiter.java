package dev.javarpg.server.util;

import java.time.Clock;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory token bucket per key (client and endpoint). Capacity equals the per-minute limit and
 * refills continuously. The key map is bounded: when it is full the least recently used entry is
 * evicted, so the limiter cannot be turned into a memory DoS (F-14).
 */
public final class TokenBucketRateLimiter {

    private static final class Bucket {
        double tokens;
        long lastMillis;
    }

    private final Clock clock;
    private final int maxKeys;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>(256, 0.75f, true);

    public TokenBucketRateLimiter(Clock clock, int maxKeys) {
        this.clock = clock;
        this.maxKeys = Math.max(16, maxKeys);
    }

    /**
     * Takes one token. Returns 0 when the request is allowed, otherwise the number of seconds
     * (at least 1) after which a token will be available.
     */
    public synchronized long tryAcquire(String key, int perMinute) {
        if (perMinute <= 0) {
            return 0L;
        }
        long now = clock.millis();
        Bucket b = buckets.get(key);
        if (b == null) {
            evictIfFull(now);
            b = new Bucket();
            b.tokens = perMinute;
            b.lastMillis = now;
            buckets.put(key, b);
        } else {
            double refill = Math.max(0L, now - b.lastMillis) * (perMinute / 60000.0);
            b.tokens = Math.min(perMinute, b.tokens + refill);
            b.lastMillis = now;
        }
        if (b.tokens >= 1.0) {
            b.tokens -= 1.0;
            return 0L;
        }
        double missing = 1.0 - b.tokens;
        long millis = (long) Math.ceil(missing / (perMinute / 60000.0));
        return Math.max(1L, (millis + 999L) / 1000L);
    }

    /** Number of tracked keys (for tests and monitoring). */
    public synchronized int size() {
        return buckets.size();
    }

    private void evictIfFull(long now) {
        if (buckets.size() < maxKeys) {
            return;
        }
        // Entries idle for more than a minute are full again, so dropping them loses nothing.
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue().lastMillis > 60000L) {
                it.remove();
            }
        }
        while (buckets.size() >= maxKeys) {
            Iterator<String> oldest = buckets.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }
}
