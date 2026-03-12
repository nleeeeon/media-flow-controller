package infrastructure.http;

import java.util.concurrent.atomic.AtomicLong;

public final class SimpleRateLimiter {
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    private final long minIntervalMs;

    public SimpleRateLimiter(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    public void acquire() {
        synchronized (this) {
            long now = System.currentTimeMillis();
            long last = lastRequestTime.get();
            long elapsed = now - last;

            if (elapsed < minIntervalMs) {
                long sleepMs = minIntervalMs - elapsed;
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                now = System.currentTimeMillis();
            }
            lastRequestTime.set(now);
        }
    }
}