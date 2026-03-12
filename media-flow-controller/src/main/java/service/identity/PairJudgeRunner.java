package service.identity;

import java.util.concurrent.atomic.AtomicBoolean;

import infrastructure.concurrent.SongApiQueueExecutor;

/**
 * PairJudgeWorker を「単一スレッドで順次」処理させる実行器。
 * start() を何回呼ばれても同時実行しない。
 */
public final class PairJudgeRunner {

    private final PairJudgeWorker worker;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public PairJudgeRunner(PairJudgeWorker worker) {
        this.worker = worker;
    }

    /** 非同期でキューを空になるまで処理（多重起動防止） */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return; // 既に実行中
        }

        SongApiQueueExecutor.submit(() -> {
            try {
                worker.processAll(); // キューが空になるまで
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.set(false);
            }
        });
    }

    /** 同期で処理したい場合（テスト等） */
    public void runNow() throws InterruptedException {
        if (!running.compareAndSet(false, true)) return;
        try {
            worker.processAll();
        } finally {
            running.set(false);
        }
    }
}