package service.title;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.common.TitleAnimePatterns;
import util.common.TitleParsePatterns;
import util.string.Strings;

public class TitleQuoteUtil {

    private TitleQuoteUtil() {}

    static MatchResult findLeftmostQuotePair(String s) {
        if (s == null || s.isBlank()) return null;

        Matcher m = TitleParsePatterns.P_ONE.matcher(s);
        if (!m.find()) return null;
        MatchResult mr = m.toMatchResult();

        return new MatchResult() {
            @Override public int start() { return mr.start(); }
            @Override public int start(int g) { return mr.start(g); }
            @Override public int end() { return mr.end(); }
            @Override public int end(int g) { return mr.end(g); }
            @Override public String group() { return mr.group(); }
            @Override public String group(int g) {
                if (g == 1) {
                    for (int i = 1; i <= mr.groupCount(); i++) {
                        String v = mr.group(i);
                        if (v != null) return v;
                    }
                    return null;
                }
                return mr.group(g);
            }
            @Override public int groupCount() { return mr.groupCount(); }
        };
    }

    static int countQuotePairs(String s) {
        if (s == null || s.isBlank()) return 0;
        Matcher m = TitleAnimePatterns.P_ANY_BRACKETS.matcher(s);
        int cnt = 0;
        while (m.find()) cnt++;
        return cnt;
    }

    static List<MatchResult> findAllBrackets(String s) {
        Matcher m = TitleAnimePatterns.P_ANY_BRACKETS.matcher(s);
        ArrayList<MatchResult> out = new ArrayList<>();
        //アニメ関係のタイトルはアニメ,タイトルとアーティスト,曲名って書かれる事があるため
        while (m.find()) out.add(m.toMatchResult());
        return out;
    }

    //このメソッドは"/"や"|"などで区切られた文字列から左をアーティスト、右を曲名として抽出する
    static String[] splitFirstTwoByDelims(String s, Pattern delims) {
        if (s == null) return new String[0];
        Matcher m = delims.matcher(s);
        int n = s.length();

        if (!m.find()) {
            return new String[]{s.trim()};
        }
        int firstStart = m.start();
        int firstEnd = m.end();

        String a = s.substring(0, firstStart).trim();

        int afterFirst = firstEnd;
        while (afterFirst < n && Character.isWhitespace(s.charAt(afterFirst))) afterFirst++;

        if (m.find()) {
        	//もし文字列が「artist/song/other」みたいな感じだったら、otherの部分を含めないように抽出するため
            int secondStart = m.start();
            String b = s.substring(afterFirst, Math.max(afterFirst, secondStart)).trim();
            if (b.equals("")) return new String[]{a};
            return new String[]{a, b};
        }

        String b = s.substring(afterFirst).trim();
        if (b.equals("")) return new String[]{a};
        return new String[]{a, b};
    }

    private static int lastIndexOfAnyBefore(String s, int songStart, char... delims) {
        if (s == null || s.isEmpty() || songStart <= 0) return -1;
        int searchEnd = Math.min(songStart - 1, s.length() - 1);

        for (int i = searchEnd; i >= 0; i--) {
            char c = s.charAt(i);
            for (char d : delims) {
                if (c == d) {
                    return i;
                }
            }
        }
        return -1;
    }

    static String extractArtistLeftOf(String title, int songBracketStart) {
        if (title == null || songBracketStart <= 0) return "";
        int cut = lastIndexOfAnyBefore(title, songBracketStart,
                '｜', '│', '|', '×', '/', '／', '-', '–', '—', '：', ':');
        String chunk = (cut >= 0)
                ? title.substring(cut + 1, songBracketStart)
                : title.substring(0, songBracketStart);

        chunk = TitleCleanupUtil.stripHeadBracket(chunk);
        chunk = chunk.replaceAll("(?:ノンクレジット|ノンテロップ|OP|ED|オープニング|エンディング)\\s*$", "");
        chunk = chunk.replaceFirst("^(?:TV\\s*アニメ|アニメ)\\s*", "");
        chunk = chunk.replaceAll("^[\\p{Zs}\\-–—|｜/／×:：]+", "")
                     .replaceAll("[\\p{Zs}\\-–—|｜/／×:：]+$", "")
                     .trim();

        if (Strings.isTrivial(chunk)) return "";
        return chunk;
    }
}
