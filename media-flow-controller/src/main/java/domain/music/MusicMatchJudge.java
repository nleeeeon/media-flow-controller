package domain.music;

import java.util.List;

import util.common.MusicKeyUtils;
import util.string.RomajiConverter;
import util.string.StringSimilarity;
import util.string.Strings;

public class MusicMatchJudge {
	private static final double ARTIST_MATCH_THRESHOLD = 0.6;
	private static final double SAME_MUSIC_MATCH_RATE_STRICT = 0.85;
	private static final double SAME_MUSIC_MATCH_RATE_LOOSE  = 0.70;
	private static final int NAME_LENGTH_REJECT_THRESHOLD = 5;
	private static final double PREFIX_MATCH_MIN_LENGTH_RATIO = 0.4;
	
	
	public static boolean sameCheck(String normArtist,String baseArtist) {
		if(normArtist == null || baseArtist == null)return false;
		  //normArtistは
		  String recArtist = MusicKeyUtils.normKey(normArtist);
			String entArtist = MusicKeyUtils.normKey(baseArtist);
		  
			if(recArtist.equals(entArtist)) {
				return true;
			}
			//英語で一定の文字数を下回ってる物は
			//ちょっと違うだけで違うことがよくあるのでこの時点で判定しないように返す
		  if( (!Strings.isJapanese(baseArtist) && baseArtist.length() < NAME_LENGTH_REJECT_THRESHOLD) ||
				  (!Strings.isJapanese(normArtist) && normArtist.length() < NAME_LENGTH_REJECT_THRESHOLD)) {
			  return false;
		  }
		  boolean match = fuzzyArtistCheck(entArtist, recArtist, SAME_MUSIC_MATCH_RATE_STRICT);
		  if(match) {
			  return true;
		  }
		  //どちらも同じ言語どうしだった場合で、一致判定じゃなかったら
		  //多分同じ曲ではないと思う
		  if( (Strings.isJapanese(baseArtist) && Strings.isJapanese(normArtist)) ||
				  (!Strings.isJapanese(baseArtist) && !Strings.isJapanese(normArtist)) ) {
			  return false;
		  }
		  
		  //以下の処理は、一つのデータの中に日本語表記と海外表記が一緒になってるものを考慮したもの
		  if(Strings.isJapanese(recArtist)) {
			  recArtist = RomajiConverter.toRomaji(recArtist);
		  }else {
			  //ここで英語をローマ字に変換するメソッド
			// 英語→ローマ字変換ロジックの検討にあたり
			  //recArtist = EnglishNameToRomaji.convertName(recArtist);
		  }
		  if(Strings.isJapanese(entArtist)) {
			  entArtist = RomajiConverter.toRomaji(entArtist);
		  }else {
			//ここで英語をローマ字に変換するメソッド
			// 英語→ローマ字変換ロジックの検討にあたり
			  //entArtist = EnglishNameToRomaji.convertName(entArtist);
		  }
		  
		  String comparison1 = entArtist.replaceAll("\\s+", "").toLowerCase();
		  String comparison2 = recArtist.replaceAll("\\s+", "").toLowerCase();
		  int eLen = comparison1.length();
		  int rLen = comparison2.length();
		  if(Math.abs(eLen-rLen) > Math.max(eLen, rLen)*PREFIX_MATCH_MIN_LENGTH_RATIO) {
			  return false;
		  }
		  if(eLen > rLen) {
			  comparison1 = comparison1.substring(0,rLen);
		  }else {
			  comparison2 = comparison2.substring(0,eLen);
		  }
		  //名前の中に空白が無い場合は、多分一つの単位としての名前になるはず
		  //先頭の部分から同じ文字数文だけの比較で一致してなかったら多分違うと思う
		  double result = StringSimilarity.levenshteinScore(comparison1, comparison2);
		  if(result > SAME_MUSIC_MATCH_RATE_LOOSE) {
				return true;
			}
		  
			return false;
		 
	  }
	  
	/** 与えた title に対して、規則に応じた比較候補（ローテーション）を作る。
	 * ・スペース1個（=2トークン）… [元, 逆順]
	 * ・スペース2個（=3トークン）… [元, 左回転, 右回転]
	 * ・それ以外（0個／3個以上）… [元のみ]
	 */
	private static List<String> rotationsForCompare(String title) {
	    // スペースは toLowerCase の後もそのまま扱う想定
	    String trimmed = title.trim();
	    // 連続スペースも区切れるように \s+ で分割
	    String[] parts = trimmed.split("\\s+");
	    int n = parts.length;

	    if (n == 2) {
	        // 例: ["Kenshi", "Yonezu"] -> ["Kenshi Yonezu", "Yonezu Kenshi"]
	        return List.of(
	            String.join("", parts),
	            parts[1] + "" + parts[0]
	        );
	    } else if (n == 3) {
	        // 例: ["米津玄師","Kenshi","Yonezu"]
	        // -> ["米津玄師 Kenshi Yonezu", "Kenshi Yonezu 米津玄師", "Yonezu 米津玄師 Kenshi"]
	        return List.of(
	            String.join("", parts),
	            parts[1] + "" + parts[2] + "" + parts[0], // 左回転
	            parts[2] + "" + parts[0] + "" + parts[1]  // 右回転
	        );
	    } else {
	        // 0個（=単語1つ）または 3個以上は回転せず
	        return List.of(trimmed);
	    }
	}

	/** 編集距離スコア（0.0〜1.0で大きいほど近い）を利用して一致判定。
	 *  title 側はローテーションを試し、その中の最大スコアと channel を比較。
	 *  ※ toLowerCase は消さない要件に合わせて、ここで必ず lower にしてから比較します。
	 */
	public static boolean fuzzyArtistCheck(String title, String channel, double threshold/*判定が動画のタイトルとチャンネル名での比較の場合は判定は緩くていいかも。多分*/) {
	    String t = title == null ? "" : title.toLowerCase();
	    String c = channel == null ? "" : channel.toLowerCase().replaceAll("\\s+", "");

	    double best = 0.0;
	    for (String cand : rotationsForCompare(t)) {
	        double s = StringSimilarity.levenshteinScore(cand, c); 
	        if (s > best) best = s;
	        if (best >= threshold) return true;
	    }
	    return false;
	}

	// 既存メソッドの置き換え版：containsではなくfuzzyArtistCheckで判定。
	// 判定が true のとき、従来と同様、短い方に寄せる処理は維持。
	public static boolean artistCheck(String title, String channel) {
	    if(fuzzyArtistCheck(title, channel, ARTIST_MATCH_THRESHOLD)) {
	    	return true;
	    }else {
	    	return artistCheckContains(title.replaceAll("\\s+", ""), channel);
	    }
	}
	private static boolean artistCheckContains(String title, String channel) {
		if(title.contains(channel)) {
			return true;
		}else if(channel.contains(title)) {
			return true;
		}else {
			return false;
		}
	}
}
