package service.anime;

import java.util.ArrayList;
import java.util.List;

import dto.music.DeterminationMusic;
import infrastructure.anime.AnimeThemesApiClient;
import infrastructure.concurrent.AnimeApiQueueExecutor;
import infrastructure.http.SimpleRateLimiter;
import service.identity.PairJudgeRegistry;

public final class AnimationOpedSongTitleApiSearch {

    private AnimationOpedSongTitleApiSearch() {}

    private static final String CONTACT_URL = "https://github.com/nleeeeon";
    private static final String USER_AGENT = "MyApp/1.0 (" + CONTACT_URL + ")";

    private static final long SAFE_INTERVAL_MS = 1000L;
    private static final int MAX_RETRIES = 6;

    public static void start(List<DeterminationMusic> ms) {
        List<String> queries = new ArrayList<>();
        for (DeterminationMusic m : ms) {
            queries.add(m.anime.title);
        }

        AnimeApiQueueExecutor.submit(() -> {
            try {

                SimpleRateLimiter limiter = new SimpleRateLimiter(1_000L);

                AnimeThemesApiClient client = new AnimeThemesApiClient(
                        limiter, USER_AGENT, SAFE_INTERVAL_MS, MAX_RETRIES
                );

                AnimeOpEdEnrichmentService service = new AnimeOpEdEnrichmentService(client);

                service.enrich(queries, ms);

                PairJudgeRegistry.runner().start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}