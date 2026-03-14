package util.common;

import java.util.regex.Pattern;

public final class TitleAnimePatterns {

    private TitleAnimePatterns() {}

    public static final Pattern P_GEKIJO_NEAR =
            Pattern.compile("劇場版\\s*[「『]([^」』]+)[」』]");

    public static final Pattern P_ANIME_NEAR_BRACKET =
            Pattern.compile("(?i)(?:アニメ|anime)\\s*[「『]([^」』]+)[」』]");

    public static final Pattern P_ANY_BRACKETS =
            Pattern.compile("[「『]([^」』]+)[」』]");

    public static final Pattern P_OP_ED =
            Pattern.compile("(?i)\\bop\\b|\\bed\\b|オープニング|エンディング");
            
    public static final Pattern P_ED =
            Pattern.compile("(?i)\\bed\\b|エンディング");

    public static final Pattern P_SEASON_SIMPLE = Pattern.compile(
            "(?:第)?([0-9０-９一二三四五六七八九十]+)期|season\\s*([0-9０-９]+)",
            Pattern.CASE_INSENSITIVE);

    public static final Pattern P_COUR =
            Pattern.compile("(?:第)?(\\d+)クール");
}
