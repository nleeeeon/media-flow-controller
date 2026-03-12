package util.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class MusicKeyUtils {
	private static final Pattern SMALL_KANA = Pattern.compile("[ぁぃぅぇぉっゃゅょゎゕゖ]");
	private final static int lengthLimit = 24;//もし２５文字以上先頭の文字が一緒な音楽が現れたら増やす
	  // ========= 1) 正規化 =========

	  // 記号/絵文字/バリアントなどを落として検索用キーに
	  public static String normKey(String s){
	    if (s == null) return "";
	    // NFKC
	    s = Normalizer.normalize(s, Normalizer.Form.NFKC);

	    // 絵文字/記号/装飾などをおおまかに除去
	    // ※ 「文字」は以下を残す：漢字・ひらがな・カタカナ・英数字・空白
	    s = s.replaceAll("\\p{InVariation_Selectors}+", ""); // 異体字セレクタ
	    s = s.replaceAll("[\\p{So}\\p{Sk}]+", ""); // 記号（絵文字含む）
	    s = s.replaceAll("[\\p{Punct}・．。！!？?「」『』（）()\\[\\]【】、，\\./／‐—–\\-:：;；…•]+", " ");
	    // 英字は小文字化
	    s = s.toLowerCase(Locale.ROOT);
	    // 小書きかな→通常かな（ざっくり。要件次第で拡張可）
	    s = SMALL_KANA.matcher(s).replaceAll(mr -> switch (mr.group()) {
	    case "ぁ" -> "あ"; case "ぃ" -> "い"; case "ぅ" -> "う"; case "ぇ" -> "え"; case "ぉ" -> "お";
	    case "っ" -> "つ"; case "ゃ" -> "や"; case "ゅ" -> "ゆ"; case "ょ" -> "よ";
	    case "ゎ" -> "わ"; case "ゕ" -> "か"; case "ゖ" -> "け";
	    default -> mr.group();
	  });

	    // 空白を1つに
	    s = s.replaceAll("\\s+", " ").trim();

	    return s;
	  }
	  // “強めのキー”（空白も除去）: 先頭一致の段階絞りに使用
	  public static String strongKey(String s){
	    String k = normKey(s);
	    k = k.replace(" ", "");
	    if (k != null && k.length() > lengthLimit) {
	    	k = k.substring(0, lengthLimit);
	    }
	    return k;
	  }
}
