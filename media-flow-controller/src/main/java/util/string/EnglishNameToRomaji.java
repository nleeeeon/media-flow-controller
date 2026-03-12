package util.string;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import jakarta.servlet.ServletContext;
//英語→ローマ字変換ロジックの検討にあたり
//Japanese Name Converter (Nolan Lawson, WTFPL) を参考にしました。
/**
 * 英語名をローマ字表記へ変換するユーティリティ。
 * - /assets/all_names.txt を読み込み、"english romaji" のマップを作る
 * - マッチはケース非依存（小文字正規化）
 * - 複数語の名前は語ごとに変換（例: "Ellen Joe" -> "eren joe" みたいに）
 * - 見つからない語はそのまま返す
 *
 * ファイルの想定形式: 1行につき "english<space(s)>romaji"
 * 例:
 *   chanelle shaneru
 *   channah chana
 *   chantal shantaru
 *   chantay shante
 */
public final class EnglishNameToRomaji {

    // 語 -> ローマ字
    private static final Map<String, String> DICT = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    // 英単語の正規化: 記号除去用
    private static final Pattern NON_WORD = Pattern.compile("[^a-zA-Z'-]+"); // ハイフン/アポストロフィは許容

    private EnglishNameToRomaji() {}

    /** 
     * Servlet 環境（動的Webプロジェクト）で使う場合はこちら。
     * 例: loadFromWebInfAssets(getServletContext(), "/WEB-INF/assets/all_names.txt");
     */
    public static synchronized void loadFromWebInfAssets(ServletContext ctx, String path) throws IOException {
        if (initialized) return;
        try (InputStream in = ctx.getResourceAsStream(path)) {
            if (in == null) {
                throw new FileNotFoundException("Resource not found: " + path);
            }
            loadFromStream(in);
        }
    }

    /**
     * クラスパスから読む場合（resources に置いた場合など）。
     * 例: loadFromClasspath("/assets/all_names.txt");
     */
    public static synchronized void loadFromClasspath(String classpathResource) throws IOException {
        if (initialized) return;
        try (InputStream in = EnglishNameToRomaji.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new FileNotFoundException("Classpath resource not found: " + classpathResource);
            }
            loadFromStream(in);
        }
    }

    private static void loadFromStream(InputStream in) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int ln = 0;
            while ((line = br.readLine()) != null) {
                ln++;
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;

                // 半角正規化
                line = Normalizer.normalize(line, Normalizer.Form.NFKC);

                // "english romaji" 形式。英語側に空白が入らない前提
                int sp = line.indexOf(' ');
                if (sp < 0) {
                    // スペースで区切れていない行はスキップ
                    continue;
                }
                String key = line.substring(0, sp).trim();
                String val = line.substring(sp + 1).trim();
                if (key.isEmpty() || val.isEmpty()) continue;

                String normKey = normalizeToken(key);
                // 重複キーは後勝ちにして上書き
                DICT.put(normKey, val);
            }
        }
        initialized = true;
    }

    /** 1語を辞書で引く（見つからなければ元の語を返す） */
 // 英語→ローマ字変換ロジックの検討にあたり
 // Japanese Name Converter (Nolan Lawson, WTFPL) を参考にしました。
    public static String convertToken(String token) {
        ensureInitialized();
        String norm = normalizeToken(token);
        String hit = DICT.get(norm);
        return (hit != null) ? hit : token;
    }

    /** フルネームを語ごとに変換。空白やハイフンを分割しつつ、語の原形は normalize して照合。 */
    public static String convertName(String englishName) {
        ensureInitialized();
        if (englishName == null || englishName.isBlank()) return englishName;

        // スペースで大まかに分割し、各語から不要記号を落として変換
        String[] rough = englishName.trim().split("\\s+");
        List<String> out = new ArrayList<>(rough.length);
        for (String r : rough) {
            // ハイフンでさらに分割し、語ごとに引いて戻す
            String[] hy = r.split("(?<=-)|(?=-)"); // ハイフン自体をトークンとして保持
            StringBuilder sb = new StringBuilder();
            for (String h : hy) {
                if ("-".equals(h)) {
                    sb.append("-");
                } else {
                    sb.append(convertToken(h));
                }
            }
            out.add(sb.toString());
        }
        return String.join(" ", out);
    }

    private static String normalizeToken(String s) {
        // 大文字小文字を畳み、不要記号を除去
        String nf = Normalizer.normalize(s, Normalizer.Form.NFKC);
        nf = nf.toLowerCase(Locale.ROOT);
        nf = NON_WORD.matcher(nf).replaceAll(""); // 文字列中の記号（ハイフン/アポストロフィ以外）除去
        return nf;
    }

    private static void ensureInitialized() {
        if (!initialized) {
            throw new IllegalStateException("Dictionary not loaded yet. Call loadFromWebInfAssets(...) or loadFromClasspath(...) first.");
        }
    }

    /*// --- 簡単な動作確認用 ---
    public static void main(String[] args) throws Exception {
        // 1) クラスパスから読む場合
        // EnglishNameToRomaji.loadFromClasspath("/assets/all_names.txt");

        // 2) Webアプリで ServletContext がある場合は以下のように（サーブレット init 等で）
        // EnglishNameToRomaji.loadFromWebInfAssets(getServletContext(), "/WEB-INF/assets/all_names.txt");

        // デモ（ここではクラスパス読み込みを仮定）
        EnglishNameToRomaji.loadFromClasspath("/assets/all_names.txt");

        System.out.println(convertName("Chanelle"));       // -> shaneru（辞書にあれば）
        System.out.println(convertName("Channah Marie"));  // -> chana Marie（前者だけ辞書ヒット例）
        System.out.println(convertName("Chantal O'Neil")); // -> shantaru O'Neil
        System.out.println(convertName("Chantay-Lee"));    // -> shante-Lee
    }*/
}
