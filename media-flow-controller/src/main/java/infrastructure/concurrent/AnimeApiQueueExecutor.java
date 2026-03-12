package infrastructure.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class AnimeApiQueueExecutor {

    private static final ExecutorService EXEC = new ThreadPoolExecutor(
            1, 1,
            5, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread t = new Thread(r);
                t.setName("app-worker");
                t.setDaemon(false);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private AnimeApiQueueExecutor() {}

    /** 非同期実行 */
    public static void submit(Runnable task) {
        EXEC.submit(task);
    }

    /** アプリ終了時に呼ぶ */
    public static void shutdown() {
        EXEC.shutdown();
    }
}