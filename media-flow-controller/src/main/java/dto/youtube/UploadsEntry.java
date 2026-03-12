package dto.youtube;

/** uploads の (channelId, uploadsPlaylistId) */
public class UploadsEntry {
    public final String channelId;
    public final String uploadsId;

    public UploadsEntry(String channelId, String uploadsId) {
        this.channelId = channelId;
        this.uploadsId = uploadsId;
    }
}