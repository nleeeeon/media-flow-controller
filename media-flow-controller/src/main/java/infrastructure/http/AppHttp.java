package infrastructure.http;

import java.net.http.HttpClient;
import java.time.Duration;

public final class AppHttp {

    public static final HttpClient CLIENT =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    private AppHttp() {}
}