package service.identity;

import service.music.MusicSearchAtApi;

public class ArtistPairJudge{

    public String resolveCanonical(String a, String b) throws Exception{
        String[] check = { a, b };
        return MusicSearchAtApi.search(check); // null or canonical
    }
}