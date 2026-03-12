package service.youtube;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonObject;

import domain.youtube.ShortsDetector;
import dto.youtube.ShortVideoCheckTarget;
import infrastructure.youtube.ShortVideoCheckTargetMapper;
import util.time.Iso8601Duration;
import util.youtube.YtJson;

public final class MusicJudge {

    public record Result(
            boolean isMusic,
            double score,
            double threshold,
            Map<String, Double> firedSignals
    ) {}

    private final double MUSIC_THRESHOLD = 1.0;

    /** YouTube videos.list の 1 item を判定 */
    public Result judge(JsonObject item) {
        double score = 0.0;
        Map<String, Double> sig = new LinkedHashMap<>();
        ShortVideoCheckTarget v = ShortVideoCheckTargetMapper.toShortVideoCheckTarget(item);
        
        if (ShortsDetector.looksShorts(v)) {
            sig.put("shorts:detected", -1.0);
            return new Result(false, 0.0, MUSIC_THRESHOLD, sig);
        }

        // 1) categoryId == 10
        String cat = YtJson.getStr(item, "snippet", "categoryId");
        if ("10".equals(cat)) { score += 1.0; sig.put("categoryId==10", 1.0); }

        // 2) topicCategories に Music 系
        for (String t : YtJson.getArray(item, "topicDetails", "topicCategories")) {
            String s = t.toLowerCase(Locale.ROOT);
            if (s.contains("music")) { score += 0.7; sig.merge("topic:music", 0.7, Double::sum); }
            if (s.contains("song"))  { score += 0.3; sig.merge("topic:song", 0.3, Double::sum); }
            if (s.contains("album")) { score += 0.3; sig.merge("topic:album", 0.3, Double::sum); }
        }

        // 3) channelTitle の "- Topic"
        String ch = YtJson.getStr(item, "snippet", "channelTitle");
        if (ch.endsWith(" - Topic")) { score += 0.6; sig.put("channel:-Topic", 0.6); }

        // 4) tags
        for (String tag : YtJson.getArray(item, "snippet", "tags")) {
            String s = tag.toLowerCase(Locale.ROOT);
            if (s.contains("music")) { score += 0.4; sig.merge("tag:music", 0.4, Double::sum); }
            if (s.contains("cover") || s.contains("カバー")) { score += 0.3; sig.merge("tag:cover", 0.3, Double::sum); }
            if (s.contains("歌ってみた")) { score += 0.4; sig.merge("tag:歌ってみた", 0.4, Double::sum); }
            if (s.contains("official")) { score += 0.2; sig.merge("tag:official", 0.2, Double::sum); }
            if (s.contains("mv")) { score += 0.2; sig.merge("tag:mv", 0.2, Double::sum); }
        }

        // title/tags 両方に効かせるキーワード群
        {
            String title = YtJson.getStr(item, "snippet", "title");
            List<String> hay = new ArrayList<>();
            hay.add(title);
            hay.addAll(YtJson.getArray(item, "snippet", "tags"));

            for (String raw : hay) {
                String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT);

                if (s.contains("piano") || s.contains("ピアノ")) { score += 0.5; sig.merge("kw:piano", 0.5, Double::sum); }
                if (s.contains("弾いてみた") || s.contains("演奏してみた")) { score += 0.6; sig.merge("kw:play_cover", 0.6, Double::sum); }
                if (s.contains("arrange") || s.contains("アレンジ")) { score += 0.2; sig.merge("kw:arrange", 0.2, Double::sum); }
                if (s.contains("bgm") || s.contains("lofi") || s.contains("chill")) { score += 0.4; sig.merge("kw:bgm", 0.4, Double::sum); }

                if (s.contains("音mad") || s.contains("remix") || s.contains("リミックス")) { score += 0.6; sig.merge("kw:mad_remix", 0.6, Double::sum); }
            }
        }

        // 5) duration
        String iso = YtJson.getStr(item, "contentDetails", "duration");
        int seconds = Iso8601Duration.secondsOf(iso);
        if (seconds > 0 && seconds < 60) { score -= 0.2; sig.put("duration<60s:-0.2", -0.2); }

        // 6) description
        String description = YtJson.getStr(item, "snippet","description");
        String d = description.toLowerCase(Locale.ROOT);
        if (d.matches("(?s).*(#\\s*mad|音\\s*mad|#\\s*oto\\s*mad|ニコニコ|nicovideo\\.?jp/watch).*")) {
            score += 2.0;
            sig.merge("description:#mad", 2.0, Double::sum);
        }

        boolean isMusic = score >= MUSIC_THRESHOLD;
        return new Result(isMusic, score, MUSIC_THRESHOLD, sig);
    }

}