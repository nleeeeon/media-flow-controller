package service.youtube;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import domain.youtube.ChannelDescriptionGenreJudge;
import infrastructure.youtube.YouTubeApiClient;

public final class ChannelGenreJudgeService {

    private final YouTubeApiClient api;

    public ChannelGenreJudgeService(YouTubeApiClient api) {
        this.api = api;
    }

    public Map<String, ChannelDescriptionGenreJudge.Result> judge(Set<String> channelIds, String token) throws Exception {
        Map<String, String> descriptions = api.fetchChannelDescriptions(channelIds, token);

        Map<String, ChannelDescriptionGenreJudge.Result> result = new HashMap<>();
        for (String channelId : channelIds) {
            String desc = descriptions.get(channelId);

            ChannelDescriptionGenreJudge.Result label = ChannelDescriptionGenreJudge.judge(desc);
            result.put(channelId, label);
        }
        return result;
    }
}