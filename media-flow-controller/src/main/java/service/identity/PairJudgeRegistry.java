package service.identity;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PairJudgeRegistry {
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static PairJudgeWorker worker;
    private static PairJudgeRunner runner;

    private PairJudgeRegistry() {}

    public static void init() {
        if (!initialized.compareAndSet(false, true)) return;

        ArtistPairJudge judge = new ArtistPairJudge();
        ArtistIdentityUpdater updater = new ArtistIdentityUpdater();
        worker = new PairJudgeWorker(judge, updater);
        runner = new PairJudgeRunner(worker);
    }

    public static PairJudgeWorker worker() {
        if (!initialized.get()) init();
        return worker;
    }

    public static PairJudgeRunner runner() {
        if (!initialized.get()) init();
        return runner;
    }
}