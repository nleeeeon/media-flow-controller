package service.music;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dto.music.MonthlySongStats;

public final class UserSongMonthlyRollup {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    private UserSongMonthlyRollup() {}

    private static final class Key {
        final String ym;
        final String videoId;

        Key(String ym, String songKey) {
            this.ym = ym;
            this.videoId = songKey;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(ym, k.ym)
                    && Objects.equals(videoId, k.videoId);
        }

        @Override public int hashCode() {
            return Objects.hash(ym, videoId);
        }
    }

    private static final class Agg {
        int plays = 0;
        Instant first = null;
        Instant last = null;
        final Set<LocalDate> activeDays = new HashSet<>();

        void see(Instant ts) {
            plays++;
            if (first == null || ts.isBefore(first)) first = ts;
            if (last  == null || ts.isAfter(last))  last  = ts;
            activeDays.add(LocalDateTime.ofInstant(ts, ZONE).toLocalDate());
        }
    }

    /** recs を「月×songKey×category」でロールアップして返す */
    public static List<MonthlySongStats> rollup(List<MusicClassificationService.Result> recs) {
        Map<Key, Agg> map = new HashMap<>();

        for (MusicClassificationService.Result r : recs) {
            if (r == null || r.videoId() == null || r.watchedAt() == null) continue;

            String ym = YM.format(LocalDateTime.ofInstant(r.watchedAt(), ZONE));
            String songKey = r.videoId();

            Key key = new Key(ym, songKey);
            Agg agg = map.computeIfAbsent(key, k -> new Agg());
            agg.see(r.watchedAt());
        }

        List<MonthlySongStats> out = new ArrayList<>(map.size());
        for (var e : map.entrySet()) {
            Key k = e.getKey();
            Agg a = e.getValue();
            out.add(new MonthlySongStats(
                    k.ym,
                    k.videoId,
                    a.plays,
                    a.activeDays.size(),
                    a.first,
                    a.last
            ));
        }
        return out;
    }
}