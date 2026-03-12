package util.string;

import util.common.MusicKeyUtils;

public class StringSimilarity {
	/** Levenshtein 距離を返す。 */
    public static int levenshteinDistance(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                cur[j] = Math.min(
                    Math.min(cur[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                );
            }
            int[] tmp = prev;
            prev = cur;
            cur = tmp;
        }

        return prev[m];
    }
    
    public static double levenshteinScore(String a, String b) {
        String A = MusicKeyUtils.strongKey(a);
        String B = MusicKeyUtils.strongKey(b);

        if (A.isEmpty() || B.isEmpty()) return 0.0;

        int dist = levenshteinDistance(A, B);
        int maxLen = Math.max(A.length(), B.length());

        return 1.0 - ((double) dist / maxLen);
    }
}
