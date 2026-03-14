package service.title;

import java.util.Arrays;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;

import domain.music.MusicMatchJudge;
import dto.music.Track;
import util.common.TitleDetectionPatterns;
import util.common.TitleParsePatterns;
import util.string.Strings;

public class TitleTrackExtractor {

    private TitleTrackExtractor() {}

    static void vocaloidSongCheck(Track track, String title, String chTitle) {
        Matcher mv = TitleDetectionPatterns.P_VOCALO_PLAIN_OR_WITH.matcher(title);
        if (!mv.find()) return;

        int namePos = mv.start();
        String left = title.substring(0, Math.max(0, namePos)).trim();

        int cut = TitleCleanupUtil.lastCutIndex(left);
        String song = (cut >= 0 ? left.substring(0, cut) : "").trim();
        song = TitleCleanupUtil.stripHeadBracket(song);
        song = TitleCleanupUtil.trimDecorativeBrackets(song);

        final String checkSong = song;
        if (Arrays.stream(TitleDetectionPatterns.NORMAL_MUSIC_ELEMENTS).anyMatch(s -> checkSong.equalsIgnoreCase(s))) {
            song = "";
        }

        if (!song.isEmpty() && !Strings.isTrivial(song)) {
            String artist = chTitle.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
            if (!artist.isEmpty()) track.artist = artist;
            track.song = song;
        }
    }

    static void artistSongTitleExtraction(Track track, String work) {

        MatchResult q = TitleQuoteUtil.findLeftmostQuotePair(work);
        if (q != null) {
        	
            int qs = q.start();
            //「」があるなら左側がアーティストだと思うので、いったん候補として入れる
            String artistMaybe = TitleQuoteUtil.extractArtistLeftOf(work, qs);
            String song = q.group(1);

            //「」の中身でスラッシュなどがあったら多分「」の中身が曲とアーティスト両方あると思うので
            //それに対応する処理をする
            //DELIMS1の中身はDELIMS2から"-"が無い事しか変わらないけど、曲の中に"-"がつくものがあるのでそれも含めるように
            String[] pair = TitleQuoteUtil.splitFirstTwoByDelims(song, TitleParsePatterns.DELIMS1);
            if (pair.length >= 2) {
                artistMaybe = pair[0];
                song = pair[1];
            }

            if (!Strings.isTrivial(artistMaybe)) track.artist = artistMaybe;
            if (!Strings.isTrivial(song)) track.song = song;
            if (track.isSongMissing()) {
                track.song = track.artist;
                track.artist = null;
            }
            return;
        }
        //"-"を付けてアーティストと曲名を分けて書くことがあるので、DELIMS2には"-"が追加されてる
        String[] pair = TitleQuoteUtil.splitFirstTwoByDelims(work, TitleParsePatterns.DELIMS2);
        if (pair.length >= 2) {
            String a = pair[0];
            String b = pair[1];
            if (!Strings.isTrivial(a)) track.artist = a;
            if (!Strings.isTrivial(b)) track.song = b;
            if (track.isSongMissing()) {
                track.song = track.artist;
                track.artist = null;
            }
        } else {
            if (!Strings.isTrivial(work)) track.song = work;
            track.artist = null;
        }
    }

    static boolean coverCheck(String str) {
        return java.util.regex.Pattern.compile("(?i)(歌ってみた|cover)")
                .matcher(str)
                .find();
    }

    public static void titleJustForWithSongTitleAndArtistExtract(Track track, String title) {
        title = TitleCleanupUtil.cutByBracketsRule(title);
        title = TitleCleanupUtil.cutByCoverLikeRule(title);
        artistSongTitleExtraction(track, title);
    }

    static boolean artistCheckAncChenge(Track track, String baseChannel) {
        String title = track.song;
        String tLower = title.toLowerCase();
        String cLower = baseChannel.toLowerCase();

        boolean ok = MusicMatchJudge.artistCheck(tLower, cLower);
        if (!ok) return false;

        if (baseChannel.length() < title.length()) {
            track.artist = baseChannel;
            track.song = null;
        }
        return true;
    }

    static boolean channelNameAndArtistNameComparison(Track track, String chTitle) {
        String a0 = track.artist;
        String a1 = track.song;

        if (MusicMatchJudge.artistCheck(a0, chTitle)) {
            if (a0.length() > chTitle.length()) track.artist = chTitle;
            return true;
        }
        if (MusicMatchJudge.artistCheck(a1, chTitle)) {
            track.song = track.artist;
            if (a1.length() > chTitle.length()) track.artist = chTitle;
            return true;
        }
        return false;
    }

    static String findSongCandidateAfterFeatOrBracket(String baseTitle, String work, String chTitle) {
        final int n = baseTitle.length();

        int next = baseTitle.indexOf(work);
        if (next < 0) return null;
        next += work.length();

        while (next < n && Character.isWhitespace(baseTitle.charAt(next))) {
            next++;
        }

        boolean startsWithOpenBracket = false;
        if (next < n) {
            char c = baseTitle.charAt(next);
            if (TitleCleanupUtil.isOpenBracket(c)) {
                startsWithOpenBracket = true;
                next++;
            }
        }

        Matcher mf = TitleParsePatterns.P_FEAT.matcher(baseTitle);
        int sepIdx;
        if (!startsWithOpenBracket && mf.find() && mf.start() > 1) {
        	//「Aimer feat. Vaundy - 残響散歌」のようなタイトルの時のfeat以降から区切りの以降を拾うため
            sepIdx = findSeparatorIndexAfterFeat(baseTitle, mf.start());
        } else {
        	//「YOASOBI (Official Video) 夜に駆ける」のようなタイトルの時に（）以降の名前を拾うため
            sepIdx = findSeparatorIndexAfterClosingBracket(baseTitle, next);
        }

        if (sepIdx < 0) return null;

        String cand = extractSongCandidate(baseTitle, sepIdx);
        if (cand == null || cand.isEmpty()) return null;
        //間違ってアーティスト名を拾わないように
        if (MusicMatchJudge.artistCheck(cand, chTitle)) return null;
        return cand;
    }

    private static int findSeparatorIndexAfterFeat(String baseTitle, int featPos) {
        Matcher m = TitleParsePatterns.P_AFTER_FEAT_SEPARATOR.matcher(baseTitle);
        return m.find(Math.max(0, featPos)) ? m.start() : -1;
    }

    private static int findSeparatorIndexAfterClosingBracket(String baseTitle, int startPos) {
        Matcher m = TitleParsePatterns.P_CLOSING_BRACKET.matcher(baseTitle);
        return m.find(Math.max(0, startPos)) ? m.start() : -1;
    }

    private static String extractSongCandidate(String baseTitle, int sepIdx) {
        final int n = baseTitle.length();
        int start = sepIdx + 1;

        while (start < n && Character.isWhitespace(baseTitle.charAt(start))) {
            start++;
        }

        Matcher endMatcher = TitleParsePatterns.P_SONG_CANDIDATE_END.matcher(baseTitle);
        int end = endMatcher.find(start) ? endMatcher.start() : n;

        String cand = (start < end) ? baseTitle.substring(start, end) : "";
        cand = TitleCleanupUtil.stripHeadBracket(cand);
        cand = TitleCleanupUtil.trimDecorativeBrackets(cand);
        return cand;
    }
}
