package util.common;

import java.util.regex.Pattern;

public final class TitleParsePatterns {

    private TitleParsePatterns() {}

    public static final Pattern P_FIRSTTAKE_TITLE = Pattern.compile(
            "^\\s*([^\\-–—]+?)\\s*[\\-–—]\\s*([^/\\(（\\[]*?)"
                    + "(?:\\s*feat\\.[^/]+)?\\s*/\\s*THE\\s*FIRST\\s*TAK[EA]\\s*$",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern P_FEAT = Pattern.compile(
            "\\b(feat\\.?|Lyric(?:\\s*Video)?|Official|MV|Music)\\b",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern DELIMS1 = Pattern.compile("[/／\\|｜]+");
    
    public static final Pattern DELIMS2 = Pattern.compile("[/／\\|｜-]+");

    public static final Pattern TOPIC_CHANNEL =
            Pattern.compile("^(.*?)\\s*-\\s*(?i:トピック|topic)\\s*$");

    public static final Pattern EDGE_DECOR = Pattern.compile(
            "^[\\p{Zs}「『\\(\\[（【】）\\]』》]+|[\\p{Zs}「『\\(\\[（【】）\\]』》]+$");

    public static final Pattern P_ONE = Pattern.compile(
            "『([^』]+)』"
                    + "|「([^」]+)」"
                    + "|“([^”]+)”"
                    + "|\"([^\"]+)\""
                    + "|‘([^’]+)’"
                    + "|'([^']+)'");

    public static final Pattern P_COVER = Pattern.compile(
            "(?i)(歌ってみた|歌ってみました|弾いてみた|踊ってみた|ピアノ|piano|演奏してみた|feat\\.?|Lyric(?:\\s*Video)?|Official|MV|(?-i)Music(?:\\s*Video)?|cover(?:ed)?(?:\\s*by)?|covered\\s*by)");

    public static final String LEAD =
            "^(?i)\\s*(?:歌ってみた|歌ってみました|弾いてみた|踊ってみた|演奏してみた|公式|cover(?:ed)?(?:\\s*by)?|covered\\s*by)\\s*(?:[|｜:/\\-–—]+\\s*)?";

    public static final Pattern P_AFTER_FEAT_SEPARATOR =
            Pattern.compile("[-–—/／|｜]");

    public static final Pattern P_CLOSING_BRACKET =
            Pattern.compile("[)）\\]］】]");

    public static final Pattern P_SONG_CANDIDATE_END =
            Pattern.compile("[\\(（\\[［【「『\\-–—/／|｜]");
}
