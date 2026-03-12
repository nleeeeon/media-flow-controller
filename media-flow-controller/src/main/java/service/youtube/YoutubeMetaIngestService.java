package service.youtube;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import dto.youtube.VideoInput;
import infrastructure.youtube.YouTubePlaylistItemsApi;

public class YoutubeMetaIngestService {
    private final YouTubePlaylistItemsApi playlistApi = new YouTubePlaylistItemsApi();
    
    

    public void playlistVideoSave(String accessToken, String playlistId) throws Exception {
    	LinkedHashSet<String> ids = playlistApi.fetchVideoIdsFromPlaylist(accessToken, playlistId);
        videoSave(accessToken, ids);
    }

    public void videoSave(String accessToken, LinkedHashSet<String> videoIds) throws Exception {
        if (videoIds == null || videoIds.isEmpty()) return;
        
        List<VideoInput> rows = new ArrayList<>();
    	for(String videoId : videoIds) {
    		VideoInput row = new VideoInput();
            row.videoId = videoId;
            rows.add(row);
    	}

    	MusicVideoProcessor process = new MusicVideoProcessor();
    	process.process(accessToken, rows, videoIds);
    }


}