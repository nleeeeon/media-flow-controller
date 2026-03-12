package service.youtube;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import domain.youtube.ShortsDetector;
import dto.youtube.VidStat;
import dto.youtube.VideoInfo;
import infrastructure.youtube.YouTubeSearchApi;
import infrastructure.youtube.YouTubeVideosApi;

public class VideoSearchService {

    private final YouTubeSearchApi searchApi;
    private final YouTubeVideosApi videosApi;

    public VideoSearchService() {
        this(new YouTubeSearchApi(), new YouTubeVideosApi());
    }

    public VideoSearchService(YouTubeSearchApi searchApi, YouTubeVideosApi videosApi) {
        this.searchApi = searchApi;
        this.videosApi = videosApi;
    }

    public List<VideoInfo> searchOneKeyword(String query, String oauthTokenOrNull, boolean excludeShorts) {
        final int PER_PAGE = 25;

        var hits = searchApi.searchHits(query, PER_PAGE, oauthTokenOrNull);
        if (hits.isEmpty()) return List.of();

        Map<String, VidStat> statMap = videosApi.fetchStatsForIds(
                hits.stream().map(h -> h.videoId()).toList(),
                oauthTokenOrNull
        );

        List<VideoInfo> out = new ArrayList<>();
        for (var h : hits) {
            VidStat st = statMap.get(h.videoId());
            long view = (st == null ? 0L : st.viewCount());
            int sec   = (st == null ? Integer.MAX_VALUE : st.durationSec());
            double ratio = (st == null ? 0.0 : st.aspectRatio());

            if (excludeShorts && !ShortsDetector.isValidPianoVideo(h.title(), sec, ratio)) continue;

            VideoInfo vp = new VideoInfo(h.videoId(), h.title(), h.channelId(), h.thumbnailUrl());
            vp.playCount = view;
            out.add(vp);
        }
        return out;
    }

    
}