package service.anime;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dao.youtube.Anime_records_dao;
import dto.anime.Anime;
import dto.anime.AnimeOpEd;
import dto.anime.Theme;
import dto.music.MusicIdentityService;
import infrastructure.anime.AnimeThemesApiClient;
import listener.AppStartupListener;
import service.music.VideoTitleCorrespondingFinding;

public final class AnimeOpEdEnrichmentService {

    private final AnimeThemesApiClient client;

    public AnimeOpEdEnrichmentService(AnimeThemesApiClient client) {
        this.client = client;
    }

    /** DeterminationMusic一覧をAnimeThemes APIで補完し、必要なら upsert/prefixCheck まで実行 */
    public void enrich(List<String> queries, List<MusicIdentityService> musics) throws Exception {
        Map<String, Anime> batch = new HashMap<>();
    	for (int i = 0; i < queries.size(); i++) {
            String q = queries.get(i);
            MusicIdentityService m = musics.get(i);

            AnimeOpEd a = client.fetchOneAnime(q).orElse(null);
            if (a == null) continue;

            Theme picked = pickTheme(a.themes(), m);
            if (picked == null) continue;

            // artistsが空の可能性もあるので防御
            String artist = picked.artists().isEmpty() ? "" : picked.artists().get(0);

            m.artist = artist;
            m.song = picked.songTitle();
            m.anime.apiAnimeArtist = artist;
            m.anime.apiAnimeSong = picked.songTitle();
            m.anime.animeFixed = true;
            m.fixed = true;
            
            batch.put(m.videoId, m.anime);

            // ここは既存設計に合わせてそのまま
            VideoTitleCorrespondingFinding v = new VideoTitleCorrespondingFinding(AppStartupListener.musicIndexManager, AppStartupListener.idGenerator);
            int idx = i;

            VideoTitleCorrespondingFinding.withExclusive(() -> {
                try {
                    v.upsertFuzzy(musics.get(idx), false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    	Anime_records_dao dao = new Anime_records_dao();
    	dao.upsertAnimeBatch(batch);
    }

    private static Theme pickTheme(List<Theme> themes, MusicIdentityService m) {
        if (themes == null || themes.isEmpty()) return null;

        // 元コードは if/else で結局 break していたため、実質「最初の1件」になっていました。
        // ここでは「希望(OP/ED)があればそれを優先、無ければ最初」を明確化します。
        String wanted = (m != null && m.anime != null && m.anime.isOp) ? "OP" : "ED";

        for (Theme t : themes) {
            if (wanted.equalsIgnoreCase(t.type())) return t;
        }
        return themes.get(0);
    }
}