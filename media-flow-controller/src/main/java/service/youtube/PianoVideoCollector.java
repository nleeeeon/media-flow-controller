package service.youtube;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import dao.youtube.Checked_video_ids_dao;
import domain.youtube.ShortsDetector;
import dto.youtube.UploadsEntry;
import dto.youtube.VideoInfo;
import infrastructure.youtube.YouTubeApiClient;

public class PianoVideoCollector {

    private final YouTubeApiClient api;

    public PianoVideoCollector(YouTubeApiClient api) {
        this.api = api;
    }

    /** 複数チャンネルから通常動画（Shorts等除外）を取得 
     * @throws SQLException */
    public Set<VideoInfo> fetchNonShorts(String accessToken, Set<String> channelIds) throws IOException, SQLException {
    	Set<UploadsEntry> uploads = api.getUploadsPlaylists(accessToken, channelIds);
        if (uploads.isEmpty()) return Set.of();

        Set<VideoInfo> allVideos = new HashSet<>();
        for (UploadsEntry ue : uploads) {
            allVideos.addAll(api.getPlaylistVideos(accessToken, ue.uploadsId, ue.channelId));
        }
        if (allVideos.isEmpty()) return allVideos;
        
        Set<String> videoIds = new HashSet<>();
        for (VideoInfo v : allVideos) {
            videoIds.add(v.videoId);
        }
        Checked_video_ids_dao dao = new Checked_video_ids_dao();
        Map<String, Boolean>existingVideoIds = dao.findExistingVideoIds(videoIds);
        allVideos.removeIf(v -> existingVideoIds.containsKey(v.videoId));

        api.enrichVideos(accessToken, allVideos);

        Map<String, VideoInfo> uniq = new LinkedHashMap<>();
        for (VideoInfo v : allVideos) {
            if (ShortsDetector.isValidPianoVideo(v)) {
                uniq.putIfAbsent(v.videoId, v);
            }
        }
        return new HashSet<>(uniq.values());
    }

    
}