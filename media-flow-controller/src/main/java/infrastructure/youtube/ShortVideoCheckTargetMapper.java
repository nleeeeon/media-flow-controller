package infrastructure.youtube;

import java.util.List;

import com.google.gson.JsonObject;

import dto.youtube.ShortVideoCheckTarget;
import util.time.Iso8601Duration;
import util.youtube.YtJson;

public final class ShortVideoCheckTargetMapper {

    private ShortVideoCheckTargetMapper() {}

    public static ShortVideoCheckTarget toShortVideoCheckTarget(JsonObject item) {
        String iso = YtJson.getStr(item, "contentDetails", "duration");
        int seconds = Iso8601Duration.secondsOf(iso);

        String title = YtJson.getStr(item, "snippet", "title");
        String desc  = YtJson.getStr(item, "snippet", "description");
        List<String> tags = YtJson.getArray(item, "snippet", "tags");

        return new ShortVideoCheckTarget(seconds, title, desc, tags);
    }
}