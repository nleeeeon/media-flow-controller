package util.string;

/**
 * 文字列系ユーティリティ
 */
public final class Strings {

    private Strings() {}

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public static boolean notBlank(String s) {
        return !isBlank(s);
    }

    public static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String s : values) {
            if (notBlank(s)) return s.trim();
        }
        return null;
    }
    
    public static boolean isTrivial(String s) {
	    if (s == null) return true;
	    String t = norm(s);
	    // 英数字・ひらがな・カタカナ・漢字以外の記号しか残っていなければ trivial とみなす
	    // 「一文字でも文字らしい物」が含まれていればOK
	    return !t.matches(".*[\\p{L}\\p{N}].*");
	}
    
    public static String norm(String s){
   	 if (s == null) return "";
   	 String x = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);
   	 x = x.replaceAll("\\s+", " ").trim();
   	 return x;
   	}

    public static String opt(String s, String def) {
        return isBlank(s) ? def : s;
    }

    /**
     * 簡易JSON文字列エスケープ（手組みJSON用）
     * ※ GsonのJsonObjectを使うなら通常は不要
     */
    public static String jesc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\"': sb.append("\\\""); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
    
    public static boolean isJapanese(String text) {
    	if (text == null || text.isEmpty()) {
            return false;
        }
        for (char c : text.toCharArray()) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
                block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
                return true;
            }
        }
        return false;
    }
    
    public static boolean containsJapanese(String s) {
	      if (s == null || s.isEmpty()) return false;
	      // ひらがな、カタカナ、漢字のUnicode範囲をチェック
	      return s.codePoints().anyMatch(cp ->
	          (cp >= 0x3040 && cp <= 0x309F) || // ひらがな
	          (cp >= 0x30A0 && cp <= 0x30FF) || // カタカナ
	          (cp >= 0x4E00 && cp <= 0x9FFF) || // CJK統合漢字
	          (cp >= 0x3400 && cp <= 0x4DBF) || // CJK拡張A
	          (cp >= 0xF900 && cp <= 0xFAFF)    // CJK互換漢字
	      );
	  }

    /**
     * 既存互換用（必要なら）
     * " と \ だけを逃がす軽い版
     */
    public static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}