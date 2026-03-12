package util.string;

import java.util.Locale;
import java.util.regex.Pattern;

import com.atilika.kuromoji.TokenizerBase;
import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import com.ibm.icu.text.Transliterator;

/**
 * 日本語（漢字/ひらがな/カタカナ混在）をローマ字（ASCII）へ変換するユーティリティ。
 *
 * <p>方針:
 * <ul>
 *   <li>Kuromojiで形態素解析し、読みが取れる場合は読み（カタカナ）を採用</li>
 *   <li>読みが取れない場合は表層をひらがな→カタカナにして連結</li>
 *   <li>連結したカタカナを ICU4J Transliterator で Latin に変換</li>
 * </ul>
 *
 * <p>注意:
 * <ul>
 *   <li>固有名詞の読みは辞書に依存します（例: 人名）</li>
 *   <li>出力は「検索用キー」用途を想定し、完全な表記揺れ統一を保証しません</li>
 * </ul>
 */
public final class RomajiConverter {

    private RomajiConverter() {}

    // 1回だけ生成して使い回し
    private static final Tokenizer TOKENIZER = new Tokenizer.Builder()
            .mode(TokenizerBase.Mode.SEARCH)
            // .userDictionary("WEB-INF/classes/userdict.csv") // 使うなら有効化
            .build();

    private static final Transliterator HIRA2KATA =
            Transliterator.getInstance("Hiragana-Katakana");
    private static final Transliterator KATA2LATIN =
            Transliterator.getInstance("Katakana-Latin; Latin-ASCII");

    private static final Pattern WHITESPACES = Pattern.compile("\\s+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^0-9a-z]+");

    /**
     * 日本語（漢字かな混在OK）をローマ字（ASCII）へ変換します。
     * 空白は1つに正規化し、前後は trim します。
     */
    public static String toRomaji(String text) {
        if (text == null || text.isBlank()) return "";

        StringBuilder katakana = new StringBuilder(text.length() * 2);

        for (Token t : TOKENIZER.tokenize(text)) {
            String reading = t.getReading();   // 読み（カタカナ） or "*" or null
            String surface = t.getSurface();   // 表層

            if (reading != null && !"*".equals(reading)) {
                katakana.append(reading);
            } else if (surface != null && !surface.isEmpty()) {
                // ひらがなはカタカナへ上げて連結（漢字等はそのまま来るが、後段で変換されない場合がある）
                katakana.append(HIRA2KATA.transliterate(surface));
            }
        }

        String romaji = KATA2LATIN.transliterate(katakana.toString());
        romaji = WHITESPACES.matcher(romaji).replaceAll(" ").trim();
        return romaji;
    }

    /**
     * 検索キー向けの変換（小文字化 + 英数字以外除去）。
     * 例: "宇多田ヒカル" → "utadahikaru"
     */
    public static String toRomajiKey(String text) {
        String r = toRomaji(text).toLowerCase(Locale.ROOT);
        return NON_ALNUM.matcher(r).replaceAll("");
    }
    
}