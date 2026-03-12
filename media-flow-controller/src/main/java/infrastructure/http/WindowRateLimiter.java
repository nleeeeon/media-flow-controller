package infrastructure.http;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicLong;

public final class WindowRateLimiter {

    private final int maxRequestsInWindow;
    private final long windowMs;
    private final long cooldownMs;
    private final long minIntervalMs;

    private final Deque<Long> requestTimes = new ArrayDeque<>();
    private final AtomicLong lastRequestTime = new AtomicLong(0);

    public WindowRateLimiter(int maxRequestsInWindow, long windowMs, long cooldownMs, long minIntervalMs) {
        this.maxRequestsInWindow = maxRequestsInWindow;
        this.windowMs = windowMs;
        this.cooldownMs = cooldownMs;
        this.minIntervalMs = minIntervalMs;
    }

    public synchronized void acquire() {
        long now = System.currentTimeMillis();
        long last = lastRequestTime.get();
        long elapsed = now - last;

        // 1) 最小間隔
        if (elapsed < minIntervalMs) {
            sleepQuietly(minIntervalMs - elapsed);
            now = System.currentTimeMillis();
        }

        // 2) ウィンドウ更新
        pruneOld(now);
        requestTimes.addLast(now);

        // 3) 上限超過
        if (requestTimes.size() > maxRequestsInWindow) {
            sleepQuietly(cooldownMs);
            requestTimes.clear();
        }

        lastRequestTime.set(System.currentTimeMillis());
    }

    private void pruneOld(long now) {
        while (!requestTimes.isEmpty() && now - requestTimes.peekFirst() > windowMs) {
            requestTimes.removeFirst();
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}