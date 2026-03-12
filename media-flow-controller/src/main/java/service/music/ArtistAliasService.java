package service.music;

import java.util.Optional;

import domain.music.MusicMatchJudge;
import dto.itunes.ArtistHit;
import infrastructure.itunes.ItunesSearchClient;

public final class ArtistAliasService {

    private final ItunesSearchClient client;

    public ArtistAliasService(ItunesSearchClient client) {
        this.client = client;
    }

    /** 2表記が同一アーティストなら「採用する表記（name1 or name2）」を返す */
    public Optional<String> resolveCanonicalName(String name1, String name2, String country) {
        ArtistHit a = client.searchTopArtist(name1, country).orElse(null);
        ArtistHit b = client.searchTopArtist(name2, country).orElse(null);

        if (!isSameArtist(a, b, name1, name2)) return Optional.empty();

        // 元コードの「aが取れたならname1、そうでなければname2」
        return Optional.of(a != null ? name1 : name2);
    }

    private boolean isSameArtist(ArtistHit a, ArtistHit b, String baseA, String baseB) {
        if (a == null && b == null) return false;

        // 片方だけ取れた：fallbackとして sameCheck を使う
        if (a == null || b == null) {
            String check1 = (a == null) ? baseA : baseB;
            String check2 = (a == null) ? b.artistName() : a.artistName();
            return MusicMatchJudge.sameCheck(check1, check2);
        }

        // amgArtistId一致
        if (a.amgArtistId() != null && a.amgArtistId().equals(b.amgArtistId())) return true;

        // artistId一致
        if (a.artistId() != null && a.artistId().equals(b.artistId())) return true;

        return false;
    }
}