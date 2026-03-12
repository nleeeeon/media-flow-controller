package infrastructure.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import util.string.StringSimilarity;

/**
 * アーティスト名を高速に検索するためのファジーマッチャ。
 *
 * <p>事前構築:
 * <ul>
 *   <li>正式名と別名を n-gram 倒置インデックスに登録する</li>
 * </ul>
 *
 * <p>検索処理:
 * <ol>
 *   <li>クエリを n-gram に分解する</li>
 *   <li>n-gram 倒置インデックスから候補集合を取得する</li>
 *   <li>Jaccard 係数で cheap ranking を行う</li>
 *   <li>上位候補に対して Jaro-Winkler / Levenshtein / Jaccard を合成して最終スコアを算出する</li>
 *   <li>スコア順に Top-N を返す</li>
 * </ol>
 *
 * <p>特徴:
 * <ul>
 *   <li>n-gram による候補絞り込みで高速に検索できる</li>
 *   <li>表記揺れや軽微なタイプミスに対応できる</li>
 * </ul>
 */
public class ArtistSearchIndex {

    public static class Artist {
        public final String id;
        public final String name;
        public final List<String> aliases;

        public Artist(String id, String name, List<String> aliases) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
            this.aliases = aliases == null ? new ArrayList<>() : new ArrayList<>(List.copyOf(aliases));
        }
    }

    public static class Candidate {
        public final String artistKey;
        public final String displayName;
        public final double score;

        // 最短編集が挿入のみ / 削除のみで達成できるか
        public final boolean insertOnly;
        public final boolean deleteOnly;

        Candidate(String id, String displayName, double score,
                  boolean insertOnly, boolean deleteOnly) {
            this.artistKey = id;
            this.displayName = displayName;
            this.score = score;
            this.insertOnly = insertOnly;
            this.deleteOnly = deleteOnly;
        }

        @Override
        public String toString() {
            return "Candidate{id=%s, name=%s, score=%.4f, insOnly=%s, delOnly=%s}"
                .formatted(artistKey, displayName, score, insertOnly, deleteOnly);
        }
    }

    public static class Builder {

        private int ngramN = 3;
        private int cheapMaxPerGram = 300;
        private int cheapTopK = 200;
        private int resultTopN = 10;
        private double threshold = 0.72;

        private static final List<Artist> source = new ArrayList<>();

        private final Map<String, Set<Integer>> ngramIndex = new HashMap<>();
        private final List<Row> rows = new ArrayList<>();

        private final Map<String, List<Candidate>> queryCache = new ConcurrentHashMap<>();

        private double wJaro = 0.55;
        private double wEd   = 0.20;
        private double wJac  = 0.25;

        public Builder ngramN(int n) {
            this.ngramN = Math.max(2, n);
            return this;
        }

        public Builder threshold(double t) {
            this.threshold = t;
            return this;
        }

        public Builder cheapMaxPerGram(int m) {
            this.cheapMaxPerGram = m;
            return this;
        }

        public Builder cheapTopK(int k) {
            this.cheapTopK = k;
            return this;
        }

        public Builder resultTopN(int n) {
            this.resultTopN = n;
            return this;
        }

        public Builder scoreWeights(double wJaro, double wEd, double wJac) {
            double s = wJaro + wEd + wJac;
            this.wJaro = wJaro / s;
            this.wEd = wEd / s;
            this.wJac = wJac / s;
            return this;
        }

        public Builder add(Artist a) {
            source.add(a);
            return this;
        }

        public ArtistSearchIndex build() {
            for (Artist a : source) {
                rows.add(Row.of(a.id, a.name));
                for (var alias : a.aliases) {
                    rows.add(Row.of(a.id, alias));
                }
            }

            for (int i = 0; i < rows.size(); i++) {
                Row r = rows.get(i);
                for (var g : ngrams(r.norm, ngramN)) {
                    ngramIndex.computeIfAbsent(g, k -> new HashSet<>()).add(i);
                }
            }

            return new ArtistSearchIndex(
                ngramN,
                cheapMaxPerGram,
                cheapTopK,
                resultTopN,
                threshold,
                wJaro,
                wEd,
                wJac,
                rows,
                toImmutable(ngramIndex),
                queryCache
            );
        }

        private static Map<String, Set<Integer>> toImmutable(Map<String, Set<Integer>> src) {
            return src.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                    Map.Entry::getKey,
                    e -> Set.copyOf(e.getValue())
                ));
        }
    }

    private static class Row {
        final String id;
        final String display;
        final String norm;

        private Row(String id, String display, String norm) {
            this.id = id;
            this.display = display;
            this.norm = norm;
        }

        static Row of(String id, String display) {
            return new Row(id, display, display);
        }
    }

    private final int ngramN;
    private final int cheapMaxPerGram;
    private final int cheapTopK;
    private final int resultTopN;
    private final double threshold;
    private final double wJaro;
    private final double wEd;
    private final double wJac;
    private final List<Row> rows;
    private final Map<String, Set<Integer>> ngramIndex;
    private final Map<String, List<Candidate>> queryCache;
    private final Map<String, Set<Integer>> idToRowIdx;
    private final java.util.BitSet alive;

    private ArtistSearchIndex(
            int ngramN,
            int cheapMaxPerGram,
            int cheapTopK,
            int resultTopN,
            double threshold,
            double wJaro,
            double wEd,
            double wJac,
            List<Row> rows,
            Map<String, Set<Integer>> ngramIndex,
            Map<String, List<Candidate>> queryCache) {

        this.ngramN = ngramN;
        this.cheapMaxPerGram = cheapMaxPerGram;
        this.cheapTopK = cheapTopK;
        this.resultTopN = resultTopN;
        this.threshold = threshold;
        this.wJaro = wJaro;
        this.wEd = wEd;
        this.wJac = wJac;
        this.rows = new ArrayList<>(rows);

        this.ngramIndex = new HashMap<>();
        for (var e : ngramIndex.entrySet()) {
            this.ngramIndex.put(e.getKey(), new HashSet<>(e.getValue()));
        }

        this.queryCache = queryCache;

        this.alive = new java.util.BitSet(this.rows.size());
        this.alive.set(0, this.rows.size(), true);

        this.idToRowIdx = new HashMap<>();
        for (int i = 0; i < this.rows.size(); i++) {
            Row r = this.rows.get(i);
            this.idToRowIdx.computeIfAbsent(r.id, k -> new HashSet<>()).add(i);
        }
    }

    /** Top-1 を返す。 */
    public Candidate matchOne(String query, int maxCompute) {
        var list = match(query, Math.max(1, maxCompute));
        if (list.isEmpty()) {
            return null;
        }
        var top = list.get(0);
        return (top.score >= threshold) ? top : top;
    }

    /** Top-N 候補をスコア降順で返す。 */
    public List<Candidate> match(String query, int topN) {
        final String key = query;
        if (key.isEmpty()) {
            return List.of();
        }

        var cached = queryCache.get(key);
        if (cached != null) {
            return trim(cached, topN);
        }

        Set<Integer> pool = new HashSet<>();
        var grams = ngrams(key, ngramN);
        for (var g : grams) {
            var set = ngramIndex.get(g);
            if (set != null) {
                mergeLimited(pool, set, cheapMaxPerGram);
            }
        }

        if (pool.isEmpty()) {
            return List.of();
        }

        var qset = new HashSet<>(grams);
        List<Scored> cheapScored = new ArrayList<>(pool.size());
        for (int i : pool) {
            var r = rows.get(i);
            var rset = new HashSet<>(ngrams(r.norm, ngramN));
            double jac = jaccard(qset, rset);
            cheapScored.add(new Scored(i, jac));
        }

        cheapScored.sort((a, b) -> Double.compare(b.score, a.score));
        if (cheapScored.size() > cheapTopK) {
            cheapScored = cheapScored.subList(0, cheapTopK);
        }

        List<Candidate> out = new ArrayList<>(Math.min(resultTopN, cheapScored.size()));
        for (var s : cheapScored) {
            if (!alive.get(s.idx)) {
                continue;
            }

            var r = rows.get(s.idx);

            double jw = jaroWinkler(key, r.norm);
            double edn = StringSimilarity.levenshteinScore(key, r.norm);
            double jac = s.score;
            double score = wJaro * jw + wEd * edn + wJac * jac;

            boolean[] flags = detectInsertDeleteOnly(key, r.norm);
            out.add(new Candidate(r.id, r.display, score, flags[0], flags[1]));
        }

        out.sort((a, b) -> Double.compare(b.score, a.score));
        if (out.size() > resultTopN) {
            out = out.subList(0, resultTopN);
        }

        queryCache.put(key, out);
        return trim(out, topN);
    }

    private static List<Candidate> trim(List<Candidate> list, int n) {
        return list.size() <= n ? list : list.subList(0, n);
    }

    private static class Scored {
        final int idx;
        final double score;

        Scored(int i, double s) {
            idx = i;
            score = s;
        }
    }

    private static void mergeLimited(Set<Integer> acc, Set<Integer> add, int limit) {
        int c = 0;
        for (int i : add) {
            acc.add(i);
            if (++c >= limit) {
                break;
            }
        }
    }

    private static List<String> ngrams(String s, int n) {
        ArrayList<String> out = new ArrayList<>(Math.max(0, s.length() - n + 1));
        if (s.length() < n) {
            out.add(s);
            return out;
        }
        for (int i = 0; i <= s.length() - n; i++) {
            out.add(s.substring(i, i + n));
        }
        return out;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        int inter = 0;
        if (a.size() < b.size()) {
            for (var x : a) {
                if (b.contains(x)) {
                    inter++;
                }
            }
        } else {
            for (var x : b) {
                if (a.contains(x)) {
                    inter++;
                }
            }
        }
        int uni = a.size() + b.size() - inter;
        return uni == 0 ? 0 : (inter / (double) uni);
    }

    /** Jaro-Winkler similarity を返す。 */
    public static double jaroWinkler(String s1, String s2) {
        if (s1.equals(s2)) {
            return 1.0;
        }

        int s1Len = s1.length();
        int s2Len = s2.length();
        if (s1Len == 0 || s2Len == 0) {
            return 0.0;
        }

        int matchDistance = Integer.max(s1Len, s2Len) / 2 - 1;
        boolean[] s1Matches = new boolean[s1Len];
        boolean[] s2Matches = new boolean[s2Len];

        int matches = 0;
        for (int i = 0; i < s1Len; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, s2Len);
            for (int j = start; j < end; j++) {
                if (s2Matches[j]) {
                    continue;
                }
                if (s1.charAt(i) != s2.charAt(j)) {
                    continue;
                }
                s1Matches[i] = true;
                s2Matches[j] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) {
            return 0.0;
        }

        int t = 0;
        int k = 0;
        for (int i = 0; i < s1Len; i++) {
            if (!s1Matches[i]) {
                continue;
            }
            while (!s2Matches[k]) {
                k++;
            }
            if (s1.charAt(i) != s2.charAt(k)) {
                t++;
            }
            k++;
        }

        double m = matches;
        double jaro = (m / s1Len + m / s2Len + (m - t / 2.0) / m) / 3.0;

        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(s1Len, s2Len)); i++) {
            if (s1.charAt(i) == s2.charAt(i)) {
                prefix++;
            } else {
                break;
            }
        }

        return jaro + 0.1 * prefix * (1 - jaro);
    }

    public void add(Artist a) {
        addOneRow(a.id, a.name);

        if (a.aliases != null) {
            for (String alias : a.aliases) {
                if (alias == null || alias.isBlank()) {
                    continue;
                }
                addOneRow(a.id, alias);
            }
        }
        queryCache.clear();
    }

    private void addOneRow(String id, String display) {
        Row r = Row.of(id, display);
        int idx = rows.size();
        rows.add(r);

        for (var g : ngrams(r.norm, ngramN)) {
            ngramIndex.computeIfAbsent(g, k -> new HashSet<>()).add(idx);
        }

        idToRowIdx.computeIfAbsent(id, k -> new HashSet<>()).add(idx);
        alive.set(idx, true);
    }

    /** 指定アーティストIDに紐づく全バリアントを削除する。 */
    public synchronized int deleteByArtistId(String artistId) {
        Set<Integer> idxs = idToRowIdx.get(artistId);
        if (idxs == null || idxs.isEmpty()) {
            return 0;
        }

        int removed = 0;
        for (int idx : new ArrayList<>(idxs)) {
            if (deleteRowIndex(idx)) {
                removed++;
            }
        }

        idToRowIdx.remove(artistId);
        queryCache.clear();
        return removed;
    }

    /** 指定ID＋表示名に一致する行のみ削除する。 */
    public synchronized boolean deleteOne(String artistId, String display) {
        Set<Integer> idxs = idToRowIdx.get(artistId);
        if (idxs == null || idxs.isEmpty()) {
            return false;
        }

        String norm = display;
        boolean ok = false;
        for (int idx : new ArrayList<>(idxs)) {
            Row r = rows.get(idx);
            if (r.norm.equals(norm) && alive.get(idx)) {
                if (deleteRowIndex(idx)) {
                    ok = true;
                }
            }
        }

        if (ok) {
            queryCache.clear();
        }
        return ok;
    }

    /** 行インデックスを論理削除し、倒置インデックスからも除去する。 */
    private boolean deleteRowIndex(int idx) {
        if (idx < 0 || idx >= rows.size()) {
            return false;
        }
        if (!alive.get(idx)) {
            return false;
        }

        Row r = rows.get(idx);
        for (var g : ngrams(r.norm, ngramN)) {
            var set = ngramIndex.get(g);
            if (set != null) {
                set.remove(idx);
                if (set.isEmpty()) {
                    ngramIndex.remove(g);
                }
            }
        }

        var s = idToRowIdx.get(r.id);
        if (s != null) {
            s.remove(idx);
            if (s.isEmpty()) {
                idToRowIdx.remove(r.id);
            }
        }

        alive.clear(idx);
        return true;
    }

    /** 指定アーティストIDに別名を1件追加する。 */
    public synchronized boolean addAlias(String artistId, String aliasDisplay) {
        if (artistId == null || artistId.isBlank() || aliasDisplay == null || aliasDisplay.isBlank()) {
            return false;
        }

        Set<Integer> idxs = idToRowIdx.get(artistId);
        if (idxs == null || idxs.isEmpty()) {
            return false;
        }

        String norm = aliasDisplay;
        for (int idx : idxs) {
            if (!alive.get(idx)) {
                continue;
            }
            Row r = rows.get(idx);
            if (r.norm.equals(norm)) {
                return false;
            }
        }

        addOneRow(artistId, aliasDisplay);
        queryCache.clear();
        return true;
    }

    /** 指定アーティストIDに複数の別名を追加する。 */
    public synchronized int addAliases(String artistId, java.util.Collection<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return 0;
        }

        int added = 0;
        for (String a : aliases) {
            if (a == null || a.isBlank()) {
                continue;
            }
            if (addAlias(artistId, a)) {
                added++;
            }
        }
        return added;
    }

    /** 指定アーティストIDに現在登録されている名前一覧を返す。 */
    public synchronized java.util.List<String> listNames(String artistId) {
        Set<Integer> idxs = idToRowIdx.get(artistId);
        if (idxs == null || idxs.isEmpty()) {
            return java.util.List.of();
        }

        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        for (int idx : idxs) {
            if (!alive.get(idx)) {
                continue;
            }
            out.add(rows.get(idx).display);
        }
        return java.util.List.copyOf(out);
    }

    private static boolean isSubsequence(String small, String large) {
        int i = 0;
        int j = 0;
        while (i < small.length() && j < large.length()) {
            if (small.charAt(i) == large.charAt(j)) {
                i++;
            }
            j++;
        }
        return i == small.length();
    }

    /** [insertOnly, deleteOnly] を返す。 */
    //日本語表記と海外表記が両方含まれてた時ように
    //削除だけもしくは追加だけで一致したか確認するため
    private static boolean[] detectInsertDeleteOnly(String a, String b) {
        if (a.equals(b)) {
            return new boolean[]{false, false};
        }

        int ed = StringSimilarity.levenshteinDistance(a, b);
        int diff = Math.abs(a.length() - b.length());

        if (ed != diff) {
            return new boolean[]{false, false};
        }

        if (a.length() < b.length()) {
            return new boolean[]{isSubsequence(a, b), false};
        } else if (a.length() > b.length()) {
            return new boolean[]{false, isSubsequence(b, a)};
        } else {
            return new boolean[]{false, false};
        }
    }

    /** 現在の登録データを Map<artistId, List<displayName>> で返す。 */
    public synchronized Map<String, List<String>> dumpAll() {
        Map<String, List<String>> out = new HashMap<>();
        for (var e : idToRowIdx.entrySet()) {
            String artistId = e.getKey();
            Set<Integer> idxs = e.getValue();
            if (idxs == null || idxs.isEmpty()) {
                continue;
            }

            Set<String> names = new java.util.TreeSet<>();
            for (int idx : idxs) {
                if (!alive.get(idx)) {
                    continue;
                }
                Row r = rows.get(idx);
                names.add(r.display);
            }

            if (!names.isEmpty()) {
                out.put(artistId, List.copyOf(names));
            }
        }
        return java.util.Collections.unmodifiableMap(out);
    }

    /** ログ出力やデバッグ用に全登録データを整形して返す。 */
    public synchronized String debugDumpAll() {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("# artists=").append(idToRowIdx.size())
          .append(" rowsAlive=").append(alive.cardinality())
          .append('\n');

        Map<String, List<String>> all = dumpAll();
        var keys = new java.util.TreeSet<>(all.keySet());
        for (String id : keys) {
            List<String> names = all.get(id);
            sb.append(id).append('\t')
              .append(String.join(" | ", names))
              .append('\n');
        }
        return sb.toString();
    }

    public void printAll(java.io.PrintStream out) {
        out.println(debugDumpAll());
    }

    public void printDumpAll() {
        Map<String, List<String>> all = dumpAll();
        if (all.isEmpty()) {
            System.out.println("[登録データなし]");
            return;
        }

        System.out.println("=== 登録アーティスト一覧 ===");
        for (var entry : all.entrySet()) {
            String artistId = entry.getKey();
            List<String> names = entry.getValue();

            System.out.println("ID: " + artistId);
            for (int i = 0; i < names.size(); i++) {
                String prefix = (i == 0) ? "  正式名/別名: " : "　　　　　└ ";
                System.out.println(prefix + names.get(i));
            }
            System.out.println();
        }
    }

    public void printArtistDetails(List<Artist> artistList) {
        if (artistList == null || artistList.isEmpty()) {
            System.out.println("Artistのリストは空です。");
            return;
        }

        System.out.println("=========================================================");
        System.out.println("Artist 詳細出力 (全 " + artistList.size() + " 件)");
        System.out.println("=========================================================");

        for (int i = 0; i < artistList.size(); i++) {
            Artist artist = artistList.get(i);
            String aliasesStr = artist.aliases.stream()
                .collect(Collectors.joining(", "));

            System.out.println("\n--- [Record " + (i + 1) + "] -----------------------------");
            System.out.println("ID: " + artist.id);
            System.out.println("Name: " + artist.name);
            System.out.println("Aliases: [" + aliasesStr + "]");
        }

        System.out.println("\n=========================================================");
    }

    public static void main(String[] args) {
        var matcher = new ArtistSearchIndex.Builder()
            .add(new Artist("bz", "B'z", List.of("Bz", "ビーズ", "びーず")))
            .add(new Artist("yoasobi", "YOASOBI", List.of("よあそび", "yo asobi")))
            .add(new Artist("aimyon", "あいみょん", List.of("AIMYON", "アイミョン")))
            .add(new Artist("kinggnu", "King Gnu", List.of("キングヌー", "kinggnu", "king gnu")))
            .add(new Artist("mrs", "Mr.Children", List.of("ミスチル", "mrchildren", "mr. children")))
            .add(new Artist("kessoku band", "結束バンド", List.of("kessoku band", "結束バンド")))
            .build();

        matcher.add(new Artist("kessoku band", "米津玄氏", List.of("yonetsugenshi")));
        matcher.add(new Artist("こっちのけんと", "kocchinokento", List.of()));
        matcher.add(new Artist("odajin", "aaaa", List.of()));

        var q = "嵐aa";
        var top = matcher.matchOne(q, 1);
        System.out.println("Top for \"" + q + "\" -> " + top);
        System.out.println(top != null ? top.artistKey : null);
        System.out.println();

        matcher.add(new Artist("arashi", "嵐aaaaa", List.of("ARASHI", "あらし")));
        q = "嵐aa";
        top = matcher.matchOne(q, 1);
        System.out.println("Top for \"" + q + "\" -> " + top);
        System.out.println(top != null ? top.artistKey : null);
        System.out.println();

        var list = matcher.match("結束バンド嵐", 5);
        System.out.println("Candidates:");
        for (var c : list) {
            System.out.println("  " + c);
        }
        System.out.println();

        matcher.deleteByArtistId("arashi");
        q = "嵐aa";
        top = matcher.matchOne(q, 1);
        System.out.println("Top for \"" + q + "\" -> " + top);
        System.out.println(top != null ? top.artistKey : null);
        System.out.println();

        q = "結束 バンド";
        top = matcher.matchOne(q, 1);
        System.out.println("Top for \"" + q + "\" -> " + top);
        System.out.println(top != null ? top.artistKey : null);
        System.out.println();

        q = "別名追加";
        top = matcher.matchOne(q, 1);
        System.out.println("Top for \"" + q + "\" -> " + top);
        System.out.println(top != null ? top.artistKey : null);
        System.out.println();

        matcher.addAlias("yoasobi", "別名追加");

        q = "別名追加";
        top = matcher.matchOne(q, 1);
        System.out.println("Top for \"" + q + "\" -> " + top);
        System.out.println(top != null ? top.artistKey : null);
        System.out.println();

        q = "genshiyonetsu";
        top = matcher.matchOne(q, 1);
        System.out.println("Top for \"" + q + "\" -> " + top);
        System.out.println(top != null ? top.artistKey : null);
        System.out.println();

        q = "結束バンド2";
        top = matcher.matchOne(q, 1);
        System.out.println("Top for \"" + q + "\" -> " + top);
        System.out.println(top != null ? top.artistKey : null);
        System.out.println();

        q = "kessokuband";
        top = matcher.matchOne(q, 1);
        System.out.println("Top for \"" + q + "\" -> " + top);
        System.out.println(top != null ? top.artistKey : null);
        System.out.println();

        q = "kotsuchinokento";
        top = matcher.matchOne(q, 1);
        System.out.println("Top for \"" + q + "\" -> " + top);
        System.out.println(top != null ? top.artistKey : null);
        System.out.println();

        q = "odajin";
        top = matcher.matchOne(q, 1);
        System.out.println("Top for \"" + q + "\" -> " + top);
        System.out.println(top != null ? top.artistKey : null);
        System.out.println();
    }
}