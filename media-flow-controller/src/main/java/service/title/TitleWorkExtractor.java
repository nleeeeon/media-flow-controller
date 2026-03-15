package service.title;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;

import com.google.gson.JsonObject;

import domain.youtube.VideoGenre;
import dto.anime.Anime;
import dto.anime.AnimeWithVideoId;
import dto.music.MusicSource;
import dto.music.Track;
import util.common.TitleAnimePatterns;
import util.common.TitleParsePatterns;
import util.string.Strings;
import util.youtube.YtJson;

public class TitleWorkExtractor {

    private static final Logger LOG = Logger.getLogger(TitleWorkExtractor.class.getName());
    private static final String FIRST_TAKE_CHANNEL_ID = "UC9zY_E8mcAo_Oq772LEZq8Q";
    private static final long MAD_CHANNEL_REGISTRANT_THRESHOLD = 500_000L;

    private final Map<String, Long> channelRegistrantNumber;
    private final Map<String, String> channelTitleJaById;
    private final Set<AnimeWithVideoId> onlyAnimes;
    private final Map<String, List<MusicSource>> songForArtist;

    public TitleWorkExtractor(
            Map<String, Long> channelRegistrantNumber,
            Map<String, String> channel_title_ja_by_id,
            Set<AnimeWithVideoId> onlyAnimes,
            Map<String, List<MusicSource>> songForArtist) {
        this.channelRegistrantNumber = channelRegistrantNumber;
        this.channelTitleJaById = channel_title_ja_by_id;
        this.onlyAnimes = onlyAnimes;
        this.songForArtist = songForArtist;
    }

    public static boolean containsPianoWord(String s) {
        return TitleClassifier.containsPianoWord(s);
    }

    public record TitleExtractResult(
            Track works,
            String channelIds,
            VideoGenre category,
            boolean is_confident,
            boolean foundKeyParenthesis) {}
    


    public TitleExtractResult extractWorksFromTitle(
            JsonObject item,
            String rawTitle,
            String rawSubtitle,
            String videoId) {

    	
        ExtractionContext context = createContext(item, rawTitle, rawSubtitle, videoId);
        ExtractionState state = createInitialState(context);

        if (!context.title().contains("メドレー") && !context.pianoHit() && !state.madFlag) {
        	//通常の抽出を行う。条件によっては信頼度高くしたり低くしたり
            TitleExtractResult generalMusicResult = tryExtractGeneralMusic(context, state);
            if (generalMusicResult != null) {
                return generalMusicResult;
            }
        } else if (!context.title().contains("メドレー") && !state.madFlag && context.pianoHit()) {
        	//ピアノ動画なので、通常の抽出のアニメやボカロ限定の抽出はしない
            TitleExtractResult pianoResult = tryExtractPianoMusic(context, state);
            if (pianoResult != null) {
                return pianoResult;
            }
        }

        return buildFallbackResult(context, state);
    }

    private ExtractionContext createContext(
            JsonObject item,
            String rawTitle,
            String rawSubtitle,
            String videoId) {

        String title = Strings.norm(rawTitle);
        String subtitle = Strings.norm(rawSubtitle);
        String channelId = YtJson.getStr(item, "snippet", "channelId");
        String channelTitle = Strings.norm(channelTitleJaById.get(channelId));
        List<String> tags = YtJson.getArray(item, "snippet", "tags");
        String description = YtJson.getStr(item, "snippet", "description");
        String joined = String.join(" / ", title, subtitle, channelTitle);
        long registrantCount = channelRegistrantNumber.getOrDefault(channelId, 0L);
        boolean pianoHit = containsPianoWord(title) || containsPianoWord(subtitle);
        boolean plainFlag = TitleClassifier.plainCheck(joined);
        boolean hasOpEd = TitleAnimePatterns.P_OP_ED.matcher(title).find();
        boolean hasAnime = title.toLowerCase().matches(".*(anime|アニメ).*");

        return new ExtractionContext(
                item,
                videoId,
                title,
                subtitle,
                title,
                channelId,
                channelTitle,
                tags,
                description,
                joined,
                registrantCount,
                pianoHit,
                plainFlag,
                hasOpEd,
                hasAnime);
    }

    private ExtractionState createInitialState(ExtractionContext context) {
        ExtractionState state = new ExtractionState();
        state.fixed = true;//最初に抽出度をtrueにしといて、後で条件によって落とす
        state.plainFlag = context.plainFlag();
        state.madFlag = TitleClassifier.madJudge(context.joined(), context.tags())
                || TitleClassifier.strMadStrictCheck(context.description());
        state.madIsConfident = state.madFlag;

        if (!context.plainFlag() && !state.madFlag
                && context.registrantCount() < MAD_CHANNEL_REGISTRANT_THRESHOLD
                && (TitleClassifier.strMadCheck(context.description())
                        || TitleClassifier.joinedStrMadCheck(context.joined()))) {
            state.madFlag = true;
            //mad動画は数文字しかないタイトルってのが多かったような気がするので文字数次第でtrueにする
            //あとで良くなかったら修正します
            state.madIsConfident = context.title().length() <= 10;
        }
        return state;
    }


    private TitleExtractResult tryExtractGeneralMusic(ExtractionContext context, ExtractionState state) {
        TitleExtractResult animeResult = tryExtractAnimePattern(context, state.track);
        if (animeResult != null) {
            return animeResult;
        }

        TitleExtractResult firstTakeResult = tryExtractFirstTakePattern(context, state.track);
        if (firstTakeResult != null) {
            return firstTakeResult;
        }

        PreparedTitle prepared = prepareTitleForGeneralMusic(context);
        state.foundKeyParenthesis = prepared.foundKeyParenthesis();

        TitleExtractResult vocaloidResult = tryExtractVocaloidTrack(context, prepared, state);
        if (vocaloidResult != null) {
            return vocaloidResult;
        }

        TitleExtractResult normalMusicResult = tryExtractStandardMusicTrack(context, prepared, state);
        if (normalMusicResult != null) {
            return normalMusicResult;
        }

        if (!state.plainFlag && context.hasOpEd()) {
            state.plainFlag = true;
        }
        return null;
    }

    private PreparedTitle prepareTitleForGeneralMusic(ExtractionContext context) {
        int pairCnt = TitleQuoteUtil.countQuotePairs(context.baseTitle());
        //最終的に判定が次第でMADかどうか条件に使われる
        boolean foundKeyParenthesis = pairCnt > 0;

        String cleaned = TitleCleanupUtil.cutByBracketsRule(context.title());
        boolean parenthesisNotFoundFlag = cleaned.equals(context.baseTitle());
        cleaned = TitleCleanupUtil.cutByCoverLikeRule(cleaned);

        return new PreparedTitle(cleaned, foundKeyParenthesis, parenthesisNotFoundFlag);
    }

    private TitleExtractResult tryExtractVocaloidTrack(
            ExtractionContext context,
            PreparedTitle prepared,
            ExtractionState state) {

        TitleTrackExtractor.vocaloidSongCheck(state.track, prepared.cleanedTitle(), context.channelTitle());
        if (state.track.isNull()) {
            return null;
        }

        addMusicSource(state.track, state.fixed, context.videoId(), context.channelId(), false, context.baseTitle());
        return new TitleExtractResult(state.track, context.channelId(), VideoGenre.MUSIC, true, false);
    }

    private TitleExtractResult tryExtractStandardMusicTrack(
            ExtractionContext context,
            PreparedTitle prepared,
            ExtractionState state) {

        if (!prepared.cleanedTitle().isEmpty()) {
            TitleTrackExtractor.artistSongTitleExtraction(state.track, prepared.cleanedTitle());
            adjustExtractedTrack(context, prepared, state);
        }

        if (state.track.isNull()) {
            return null;
        }

        normalizeExtractedTrack(state.track);
        addMusicSource(state.track, state.fixed, context.videoId(), context.channelId(), false, context.baseTitle());
        return new TitleExtractResult(
                state.track,
                context.channelId(),
                VideoGenre.MUSIC,
                true,
                state.foundKeyParenthesis);
    }

    private void adjustExtractedTrack(
            ExtractionContext context,
            PreparedTitle prepared,
            ExtractionState state) {

        if (state.track.isArtistMissing() && TitleParsePatterns.TOPIC_CHANNEL.matcher(context.channelTitle()).find()) {
        	//チャンネルにtopic等があれば、そのままチャンネル名がアーティストでタイトルが曲名になるはず
            applyTopicChannelArtist(state.track, context.channelTitle());
            return;
        }

        if (state.track.isArtistMissing() && TitleTrackExtractor.artistCheckAncChenge(state.track, context.channelTitle())) {
        	//取得した名前がチャンネル名と一致するなら多分、それがアーティスト名で曲名は他にあると思うので見つける処理をする
            recoverSongFromBaseTitle(context, prepared, state);
            return;
        }

        if (state.track.isArtistMissing()) {
            moveTrackToKeepTrackIfMadLike(context, prepared, state);
            return;
        }

        if (state.track.hasAllValues()) {
            adjustTrackByChannelName(context, prepared, state);
        }
    }

    private void applyTopicChannelArtist(Track track, String channelTitle) {
        Matcher m = TitleParsePatterns.TOPIC_CHANNEL.matcher(channelTitle);
        if (!m.find()) {
            return;
        }

        String name = m.group(1).trim();
        name = name.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
        track.artist = name;
    }

    private void recoverSongFromBaseTitle(
            ExtractionContext context,
            PreparedTitle prepared,
            ExtractionState state) {

    	//チャンネル名と一致したので、このアーティスト名が正しい名前なはず
        String artist = state.track.artist;
        TitleTrackExtractor.artistSongTitleExtraction(state.track, context.baseTitle());

        if (state.track.artist != null) {
        	//アーティストが取れたら、曲名はちゃんとしたのが取れてるはず
            state.track.artist = artist;
            return;
        }

        String candidate = TitleTrackExtractor.findSongCandidateAfterFeatOrBracket(
                context.baseTitle(),
                prepared.cleanedTitle(),
                context.channelTitle());

        if (candidate == null) {
            LOG.warning("未対応パターンに到達しました. videoId=" + context.videoId());
            state.track.clear();
            return;
        }
        state.track.artist = artist;
        state.track.song = candidate;
    }

    private void moveTrackToKeepTrackIfMadLike(
            ExtractionContext context,
            PreparedTitle prepared,
            ExtractionState state) {

        if (state.plainFlag || !prepared.parenthesisNotFoundFlag()) {
            return;
        }

        //微妙な判定で多分MADになると思うので、falseで登録する
        state.madIsConfident = false;
        state.track.clear();
    }

    private void adjustTrackByChannelName(
            ExtractionContext context,
            PreparedTitle prepared,
            ExtractionState state) {

        boolean channelMatched = TitleTrackExtractor.channelNameAndArtistNameComparison(
                state.track,
                context.channelTitle());

        if (state.plainFlag) {
            state.fixed = channelMatched;
        } else if (!channelMatched
                && context.registrantCount() < MAD_CHANNEL_REGISTRANT_THRESHOLD
                && prepared.parenthesisNotFoundFlag()) {
            //チャンネル登録者数がある程度あったら多分タグとか説明をちゃんとしてると思う
        	//あとカッコがないんだったら、MAD特有のタイトルだけとかじゃないかなって
            state.madIsConfident = false;
            state.track.clear();
        }

        if (state.fixed && (TitleTrackExtractor.coverCheck(context.baseTitle()) || !channelMatched)) {
            state.fixed = false;
        }
    }

    private void normalizeExtractedTrack(Track track) {
        track.swapNamesIfNeeded();
        track.song = TitleCleanupUtil.trimDecorativeBrackets(track.song);
        if (track.artist != null) {
            track.artist = TitleCleanupUtil.trimDecorativeBrackets(track.artist);
        }
    }

    private TitleExtractResult tryExtractPianoMusic(ExtractionContext context, ExtractionState state) {
        TitleTrackExtractor.titleJustForWithSongTitleAndArtistExtract(state.track, context.baseTitle());
        if (state.track.isNull()) {
            return null;
        }

        state.track.swapNamesIfNeeded();
        state.fixed = state.track.isArtistMissing();
        state.track.song = TitleCleanupUtil.trimDecorativeBrackets(state.track.song);
        if (state.track.hasAllValues()) {
            state.track.artist = TitleCleanupUtil.trimDecorativeBrackets(state.track.artist);
        }

        addMusicSource(state.track, state.fixed, context.videoId(), context.channelId(), true, context.baseTitle());
        return new TitleExtractResult(state.track, context.channelId(), VideoGenre.PIANO, true, false);
    }

    private TitleExtractResult buildFallbackResult(ExtractionContext context, ExtractionState state) {
        if (context.pianoHit()) {
            return new TitleExtractResult(new Track(), context.channelId(), VideoGenre.PIANO, true, false);
        }
        if (!state.madFlag && !state.plainFlag && state.track.isNull()) {
            return new TitleExtractResult(
                    new Track(),
                    context.channelId(),
                    VideoGenre.MAD,
                    state.madIsConfident,
                    state.foundKeyParenthesis);
        }
        if (!state.madFlag && state.plainFlag) {
            LOG.warning("音楽と判定したが抽出できませんでした. title=" + context.title());
            return new TitleExtractResult(new Track(), context.channelId(), VideoGenre.MUSIC, true, false);
        }
        if (state.madFlag) {
            return new TitleExtractResult(new Track(), context.channelId(), VideoGenre.MAD, state.madIsConfident, false);
        }

        LOG.warning("どの判定にも該当しませんでした。");
        return new TitleExtractResult(new Track(), context.channelId(), VideoGenre.UNKNOWN, false, false);
    }

    private TitleExtractResult tryExtractAnimePattern(ExtractionContext context, Track track) {
        Anime anime = new Anime();
        //タイトルにちゃんと表記されてなかったら大体１だと思う
        anime.season = 1;
        anime.cour = 1;
        anime.isOp = true;
        java.util.regex.MatchResult mrSong = null;

        List<MatchResult> brackets = TitleQuoteUtil.findAllBrackets(context.title());
        if (context.hasAnime() && !brackets.isEmpty()) {
            var near = TitleAnimePatterns.P_ANIME_NEAR_BRACKET.matcher(context.title());
            if (near.find()) {
            	//大体"アニメ"ってついた後の隣の「」は作品名が多いため
                anime.title = near.group(1);
                if (brackets.size() >= 2) {
                    int nearStart = near.start(1);
                    java.util.regex.MatchResult other = null;
                    for (var mr : brackets) {
                        if (mr.start(1) != nearStart) {
                            other = mr;
                            break;
                        }
                    }
                    //もう一つの「」はだいたい曲名になるはず
                    if (other != null) {
                        anime.song = other.group(1);
                        mrSong = other;
                    }
                } else if (!context.hasOpEd()) {
                    anime.title = null;
                }
            } else if (context.hasOpEd()) {
                anime.title = brackets.get(0).group(1);
            }
        } else if (context.hasOpEd() && !brackets.isEmpty() && brackets.size() == 2) {
            var g = TitleAnimePatterns.P_GEKIJO_NEAR.matcher(context.title());
            if (g.find()) {
            	//劇場版ってなった場合の処理
                anime.title = g.group(1);
                java.util.regex.MatchResult other = null;
                for (var mr : brackets) {
                    if (mr.start(1) != g.start(1)) {
                        other = mr;
                        break;
                    }
                }
                if (other != null) {
                    anime.song = other.group(1);
                    mrSong = other;
                }
            } else {
                //だいたい左のほうにアニメタイトルで右側が曲名を書くような形のはず
                anime.title = brackets.get(0).group(1);
                anime.song = brackets.get(1).group(1);
                mrSong = brackets.get(1);
                
            }
        }

        if (context.hasOpEd()) {
            anime.isOp = !TitleAnimePatterns.P_ED.matcher(context.title()).find();
        }

        String artist = "";
        //だいたい曲名が右側にあって、その近くにはアーティスト名がだいたい書いてある
        //だから最も右側にあれば多分その左にある物がアーティストなはず
        if (mrSong != null && mrSong.end() >= context.baseTitle().length()) {
            int songStart = mrSong.start();
            artist = TitleQuoteUtil.extractArtistLeftOf(context.title(), songStart);
            if (!Strings.isTrivial(artist)) {
                track.artist = artist;
            }
        }

        if (Strings.isTrivial(anime.title) && Strings.isTrivial(anime.song) && track.isNull()) {
            return null;
        }

        var courMatcher = TitleAnimePatterns.P_COUR.matcher(context.title());
        if (courMatcher.find()) {
            anime.cour = Integer.parseInt(courMatcher.group(1));
        }
        var seasonMatcher = TitleAnimePatterns.P_SEASON_SIMPLE.matcher(context.title());
        if (seasonMatcher.find()) {
            String number = seasonMatcher.group(1) != null ? seasonMatcher.group(1) : seasonMatcher.group(2);
            anime.season = TitleClassifier.parseNumber(number);
        }

        track.song = !Strings.isTrivial(anime.song) ? anime.song : null;
        if (anime.song == null) {
            onlyAnimes.add(new AnimeWithVideoId(anime, context.videoId(), context.channelId()));
        } else {
            MusicSource source = new MusicSource(
                    track.song,
                    artist,
                    true,
                    context.videoId(),
                    anime,
                    context.channelId(),
                    false,
                    context.baseTitle());
            songForArtist.computeIfAbsent(track.song, k -> new ArrayList<>()).add(source);
        }
        return new TitleExtractResult(track, context.channelId(), VideoGenre.MUSIC, true, false);
    }

    private TitleExtractResult tryExtractFirstTakePattern(ExtractionContext context, Track track) {
        if (!FIRST_TAKE_CHANNEL_ID.equals(context.channelId())) {
            return null;
        }

        var matcher = TitleParsePatterns.P_FIRSTTAKE_TITLE.matcher(context.title());
        if (!matcher.find()) {
            return null;
        }

        String artist = TitleCleanupUtil.stripHeadBracket(matcher.group(1));
        String song = matcher.group(2);
        if (!Strings.isTrivial(artist)) {
            track.artist = artist;
        }
        if (!Strings.isTrivial(song)) {
            track.song = song;
        }
        if (track.isNull()) {
            return null;
        }

        addMusicSource(track, true, context.videoId(), context.channelId(), false, context.baseTitle());
        return new TitleExtractResult(track, context.channelId(), VideoGenre.MUSIC, true, false);
    }

    private void addMusicSource(
            Track track,
            boolean fixed,
            String videoId,
            String channelId,
            boolean piano,
            String baseTitle) {
        MusicSource source = new MusicSource(track.song, track.artist, fixed, videoId, new Anime(), channelId, piano, baseTitle);
        songForArtist.computeIfAbsent(track.song, k -> new ArrayList<>()).add(source);
    }

    public static boolean strMadCheck(String str) {
        return TitleClassifier.strMadCheck(str);
    }

    public static boolean joinedStrMadCheck(String str) {
        return TitleClassifier.joinedStrMadCheck(str);
    }

    public static boolean MadJudge(String title, List<String> ytTags) {
        return TitleClassifier.madJudge(title, ytTags);
    }

    static boolean strMadStrictCheck(String str) {
        return TitleClassifier.strMadStrictCheck(str);
    }

    private record ExtractionContext(
            JsonObject item,
            String videoId,
            String title,
            String subtitle,
            String baseTitle,
            String channelId,
            String channelTitle,
            List<String> tags,
            String description,
            String joined,
            long registrantCount,
            boolean pianoHit,
            boolean plainFlag,
            boolean hasOpEd,
            boolean hasAnime) {}

    private record PreparedTitle(
            String cleanedTitle,
            boolean foundKeyParenthesis,
            boolean parenthesisNotFoundFlag) {}

    private static final class ExtractionState {
        private Track track = new Track();
        private boolean madFlag;
        private boolean madIsConfident;
        private boolean foundKeyParenthesis;
        private boolean plainFlag;
        private boolean fixed = true;
    }
}
