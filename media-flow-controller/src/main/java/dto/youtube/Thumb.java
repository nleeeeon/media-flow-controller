package dto.youtube;

public class Thumb {
    public final String videoId;
    public final String thumbnailUrl;
    public final String videoTitle;

    public Thumb(String videoId, String thumbnailUrl, String videoTitle) {
        this.videoId = videoId;
        this.thumbnailUrl = thumbnailUrl;
        this.videoTitle = videoTitle;
    }
}