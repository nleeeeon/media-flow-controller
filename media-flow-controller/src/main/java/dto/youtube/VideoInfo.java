package dto.youtube;

public class VideoInfo {
    public final String videoId;
    public String channelId;
    public String title;
    public long playCount;
    public int durationSec;
    public double ratio = 16.0 / 9.0;
    public String thumbnailUrl;

    public VideoInfo(String videoId, String title, String channelId, String thumbnailUrl) {
        this.videoId = videoId;
        this.title = title;
        this.channelId = channelId;
        this.thumbnailUrl = thumbnailUrl;
    }

    @Override
    public String toString() {
        return String.format("%s (%ds) %s", videoId, durationSec, title);
    }
}