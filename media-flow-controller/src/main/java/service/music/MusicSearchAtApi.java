package service.music;

import java.time.Duration;
import java.util.Optional;

import infrastructure.http.WindowRateLimiter;
import infrastructure.itunes.ItunesSearchClient;

public final class MusicSearchAtApi {

    private static final int MAX_REQUESTS_IN_WINDOW = 338;
    private static final long WINDOW_MS = 30 * 60 * 1000;
    private static final long COOLDOWN_MS = 30 * 60 * 1000;
    private static final long MIN_INTERVAL_MS = 5000;

    private static final String USER_AGENT = "ITunesAliasCheck/1.0 (+no-redistribution)";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private MusicSearchAtApi() {}

    public static String search(String[] checkArtist) throws Exception {
        if (checkArtist.length < 2 || checkArtist.length > 3) return null;

        String name1 = checkArtist[0];
        String name2 = checkArtist[1];
        String country = (checkArtist.length >= 3 && !checkArtist[2].isBlank())
                ? checkArtist[2].trim()
                : "JP";



        WindowRateLimiter limiter = new WindowRateLimiter(
                MAX_REQUESTS_IN_WINDOW, WINDOW_MS, COOLDOWN_MS, MIN_INTERVAL_MS
        );

        ItunesSearchClient client = new ItunesSearchClient(limiter, USER_AGENT, HTTP_TIMEOUT);
        ArtistAliasService service = new ArtistAliasService(client);

        Optional<String> resolved = service.resolveCanonicalName(name1, name2, country);
        return resolved.orElse(null);
    }
}