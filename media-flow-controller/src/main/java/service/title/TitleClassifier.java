package service.title;

import java.text.Normalizer;
import java.util.List;
import java.util.logging.Logger;

import util.common.TitleDetectionPatterns;


public class TitleClassifier {

    private static final Logger LOG = Logger.getLogger(TitleClassifier.class.getName());

    private TitleClassifier() {}

    static boolean containsPianoWord(String s) {
        if (s == null || s.isBlank()) return false;
        return TitleDetectionPatterns.PIANO_DETECT.matcher(s).find();
    }

    static boolean plainCheck(String str) {
        return TitleDetectionPatterns.PLAIN_DETECT.matcher(str).find()
                || TitleDetectionPatterns.P_VOCALO_PLAIN_OR_WITH.matcher(str).find();
    }

    static boolean strMadCheck(String str) {
        if (str == null) return false;
        return str.toLowerCase()
                .matches("(?s).*(#\\s*mad|音\\s*mad|#\\s*oto\\s*mad|ニコニコ|コメ付き|コメント付き|合作|転載|nicovideo\\.?jp/watch).*");
    }

    static boolean joinedStrMadCheck(String str) {
        if (str == null) return false;
        return str.toLowerCase()
                .matches("(?s).*(#\\s*mad|音\\s*mad|#\\s*oto\\s*mad|コメ付き|コメント付き|合作).*");
    }

    private static boolean containsMadWord(String s) {
        if (s == null || s.isBlank()) return false;
        return TitleDetectionPatterns.MAD_DETECT.matcher(s).find();
    }

    static boolean madJudge(String title, List<String> ytTags) {
        return title.toLowerCase().contains("mad")
                || ytTags.stream().anyMatch(TitleClassifier::containsMadWord);
    }

    static boolean strMadStrictCheck(String str) {
        if (str == null) return false;
        return str.toLowerCase().matches("(?s).*(#\\s*mad|音\\s*mad|#\\s*oto\\s*mad).*");
    }

    static boolean classifyOpEd(String title) {
        if (title == null || title.isBlank()) return true;
        int iOp1 = indexOfRegex(title, "(?i)\\bop\\b");
        int iOp2 = indexOfRegex(title, "オープニング");
        int iEd1 = indexOfRegex(title, "(?i)\\bed\\b");
        int iEd2 = indexOfRegex(title, "エンディング");

        int iOp = minPos(iOp1, iOp2);
        int iEd = minPos(iEd1, iEd2);
        if (iOp < 0 && iEd < 0) {
            LOG.warning(() -> "OP/ED 判定不可のタイトルです: " + title);
            return true;
        }

        if (iEd < 0) return true;
        if (iOp < 0) return false;
        return iOp <= iEd;
    }

    static int parseNumber(String s) {
        s = Normalizer.normalize(s, Normalizer.Form.NFKC);
        switch (s) {
            case "一": case "１": return 1;
            case "二": case "２": return 2;
            case "三": case "３": return 3;
            case "四": case "４": return 4;
            case "五": case "５": return 5;
            case "六": case "６": return 6;
            case "七": case "７": return 7;
            case "八": case "８": return 8;
            case "九": case "９": return 9;
            case "十": return 10;
            default: return Integer.parseInt(s.replaceAll("\\D", ""));
        }
    }

    private static int indexOfRegex(String s, String re) {
        var m = java.util.regex.Pattern.compile(re).matcher(s);
        return m.find() ? m.start() : -1;
    }

    private static int minPos(int... xs) {
        int out = -1;
        for (int x : xs) {
            if (x >= 0 && (out < 0 || x < out)) out = x;
        }
        return out;
    }
}
