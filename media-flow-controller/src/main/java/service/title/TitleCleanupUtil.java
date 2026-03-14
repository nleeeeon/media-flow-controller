package service.title;

import java.util.Map;

import util.common.TitleParsePatterns;


public class TitleCleanupUtil {

    private TitleCleanupUtil() {}

    static String cutByBracketsRule(String s) {
        if (s == null || s.isBlank()) return "";

        final Map<Character, Character> pair = Map.of(
                '(', ')', '（', '）',
                '[', ']', '［', '］',
                '【', '】'
        );

        s = s.stripLeading();
        //先頭にあるかっこを全て消す。最初に出るかっこの中身は補足情報が多いと思うから
        while (!s.isEmpty() && pair.containsKey(s.charAt(0))) {
            char open = s.charAt(0);
            char close = pair.get(open);
            int j = s.indexOf(close, 1);
            s = (j >= 0 ? s.substring(j + 1) : s.substring(1)).stripLeading();
        }

        if (s.isEmpty()) return "";
        //かっこ以降も多分補足情報だと思うから消す
        int k = indexOfAnyOpenBracket(s);
        return (k >= 0 ? s.substring(0, k) : s).trim();
    }

    static String trimBrackets(String s) {
        if (s == null || s.isEmpty()) return s;
        if ("([{「『".indexOf(s.charAt(0)) >= 0) {
            s = s.substring(1);
        }
        if (s.length() > 0 && ")] }」』".replace(" ", "").indexOf(s.charAt(s.length() - 1)) >= 0) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static int indexOfAnyOpenBracket(String s) {
        if (s == null) return -1;
        int pos = -1;
        char[] opens = new char[]{'(', '（', '[', '［', '【'};
        for (char o : opens) {
            int i = s.indexOf(o);
            if (i >= 0 && (pos < 0 || i < pos)) pos = i;
        }
        return pos;
    }

    static String cutByCoverLikeRule(String s) {
        if (s == null || s.isBlank()) return "";
        String x = s;
        x = x.replaceFirst(TitleParsePatterns.LEAD, "");

        java.util.regex.Matcher m = TitleParsePatterns.P_COVER.matcher(x);
        //一致したとしても左端で一致してるところは多分意味のあるものだと思うのでそれ以降で一致したところ以降を消す
        if (m.find() && (m.start() > 1 || m.find())) {
            x = x.substring(0, Math.max(0, m.start()));
        }
        return x.trim();
    }

    static String trimDecorativeBrackets(String s) {
        if (s == null) return null;
        return TitleParsePatterns.EDGE_DECOR.matcher(s).replaceAll("").trim();
    }

    static String stripHeadBracket(String s) {
        if (s == null) return "";
        return s.replaceFirst("^(【[^】]*】|\\[[^\\]]*\\]|\\([^)]*\\)|（[^）]*）)\\s*", "");
    }

    static int lastCutIndex(String s) {
        int pos = -1;
        char[] cuts = new char[]{'/', '／', '-', '–', '—', '|'};
        for (char c : cuts) {
            int i = s.lastIndexOf(c);
            if (i > pos) pos = i;
        }
        return pos;
    }

    static boolean isOpenBracket(char c) {
        return c == '(' || c == '（' || c == '[' || c == '［' || c == '【';
    }
}
