package util.common;

import java.util.regex.Pattern;

public class TitlePatterns {
	public static final java.util.regex.Pattern PLAIN_DETECT =
			java.util.regex.Pattern.compile("(?i)(music|topic|records|official|Lyric(?:\\s*Video)?|mv|feat\\.?|pv|song|cover|album|bgm|オフィシャル|オープニング|エンディング|アニメ|主題歌|ミュージック|アレンジ|音楽|曲|歌ってみた|歌う|アルバム)");
	
	public static final java.util.regex.Pattern PIANO_DETECT =
		    java.util.regex.Pattern.compile("(?iu)ピアノ|piano|弾いてみた");
	
	public static final java.util.regex.Pattern MAD_DETECT =
		    java.util.regex.Pattern.compile(
		        "(?iu)(?:音\\s*[mｍ][aａ][dｄ])" +           // 音mad / 音 MAD（大小/全角/空白ゆらぎ）
		        "|(?:^|[^\\p{L}\\p{N}])[mｍ][aａ][dｄ](?:$|[^\\p{L}\\p{N}])" // 単独の mad / MAD を単語境界で
		    );
	
	//THE FIRST TAKE特別用
	public static final java.util.regex.Pattern P_FIRSTTAKE_TITLE =
	java.util.regex.Pattern.compile(
			"^\\s*([^\\-–—]+?)\\s*[\\-–—]\\s*([^/\\(（\\[]*?)" +        // 1=artist, 2=song
					"(?:\\s*feat\\.[^/]+)?\\s*/\\s*THE\\s*FIRST\\s*TAK[EA]\\s*$", // 任意の feat. を挟みつつ / THE FIRST TAKE で終了
					java.util.regex.Pattern.CASE_INSENSITIVE
			);
	
	public static final java.util.regex.Pattern P_FEAT =
			java.util.regex.Pattern.compile("\\b(feat\\.?|Lyric(?:\\s*Video)?|Official|MV|Music)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);//追加コード（MV追加修正）
	
	// 「劇場版」直後の『…』/「…」を拾う
		public static final java.util.regex.Pattern P_GEKIJO_NEAR =
		    java.util.regex.Pattern.compile("劇場版\\s*([「『])([^」』]+)[」』]");
		
		
		
		//区切り集合（連続区切りは1つに圧縮して扱う）
		public static final java.util.regex.Pattern DELIMS1 =
		   java.util.regex.Pattern.compile("[/／\\|｜]+");

		public static final java.util.regex.Pattern DELIMS2 =
		java.util.regex.Pattern.compile("[/／\\|｜-]+");
		
		public static Pattern p = Pattern.compile("^(.*?)\\s*-\\s*(?i:トピック|topic)\\s*$");
		
		 public static final java.util.regex.Pattern P_ANIME_NEAR_BRACKET =
				    java.util.regex.Pattern.compile("(?i)(?:アニメ|anime)\\s*([「『])([^」』]+)[」』]");


			public static final java.util.regex.Pattern P_ANY_BRACKETS =
			  java.util.regex.Pattern.compile("([「『])([^」』]+)[」』]");
			
			public static final java.util.regex.Pattern QUOTE_PAIRS =
					  java.util.regex.Pattern.compile("([「『])([^」』]+)[」』]");
			
			public static final Pattern EDGE_DECOR =
				    Pattern.compile("^[\\p{Zs}「『\\(\\[（【】）\\]』》]+|[\\p{Zs}「『\\(\\[（【】）\\]』》]+$");
			

			// OP/ED 判定（英: op/ed, 日: オープニング/エンディング）
			public static final java.util.regex.Pattern P_OP_ED =
			  java.util.regex.Pattern.compile("(?i)\\bop\\b|\\bed\\b|オープニング|エンディング");
			
			// === 期・クール 判定用 ===
			public static final java.util.regex.Pattern P_SEASON_SIMPLE =
				    java.util.regex.Pattern.compile(
				        "(?:第)?([0-9０-９一二三四五六七八九十]+)期"   // 〜期 のパターン（アラビア数字/全角数字/漢数字）
				        + "|season\\s*([0-9０-９]+)",                  // season のパターン（スペース有無OK）
				        java.util.regex.Pattern.CASE_INSENSITIVE
				    );

			public static final java.util.regex.Pattern P_COUR =
				    java.util.regex.Pattern.compile("(?:第)?(\\d+)クール");	
			
			public static String[] normalMusicElements = {"music","topic","records","official","mv","feat","feat\\.?","pv","song","cover","album","bgm","オフィシャル","ミュージック","音楽","曲","歌ってみた","アルバム"};	
			// ボカロ名の正規表現（必要に応じて増やしてOK）
			private static final String VOCALO_REGEX =
					"(?:初音ミク|重音テトSV|重音テト|flower|可不|GUMI|結月ゆかり|巡音ルカ|鏡音リン|鏡音レン|音街ウナ|MEIKO|KAITO|雨衣"
							+ "|神威がくぽ|がくっぽいど|氷山キヨテル|歌愛ユキ|SF-A2\\s*miki|猫村いろは|兎眠りおん|Lily|VY1|VY2|蒼姫ラピス|ZOLA\\s*PROJECT|WIL|YUU|KYO\n"
							+ "|さとうささら|すずきつづみ|タカハシ|小春六花|夏色花梨|花隈千冬|弦巻マキ|桜乃そら|東北ずん子|東北きりたん|東北イタコ|亞北ネル\n"
							+ "|琴葉茜|琴葉葵|紲星あかり|四国めたん|ずんだもん|つくよみちゃん|春日部つむぎ|白上虎太郎|No.7\n"
							+ "|波音リツ|欲音ルコ|桃音モモ|健音テイ|重音テッド|雪歌ユフ|健音テイ|朝音ボウ|健音ルイ\n"
							+ "|MEGPOID|Gackpoid|CUL|心華|楽正綾|洛天依|言和|星塵|楽正龍牙|墨清弦|徵羽摩柯\n"
							+ "|初音ミクNT|巡音ルカV4X|鏡音リンV4X|鏡音レンV4X\n"
							+ "|Hatsune\\s*Miku|Kagamine\\s*Rin|Kagamine\\s*Len|Megurine\\s*Luka|GUMI|Yuzuki\\s*Yukari|Otomachi\\s*Una|KAITO|MEIKO|Kasane\\s*Teto|v\\s*flower\n"
							+ ")";
			
			// 「with の後」「いきなりボカロ名」両対応（全角半角ゆらぎは nk で吸収）
			public static final java.util.regex.Pattern P_VOCALO_PLAIN_OR_WITH =
			  java.util.regex.Pattern.compile("(?i)(?:\\bwith\\b\\s*)?" + VOCALO_REGEX);
			
			
			public static final java.util.regex.Pattern P_ONE = java.util.regex.Pattern.compile(
			          "『([^』]+)』"       // 1
			        + "|「([^」]+)」"      // 2
			        + "|“([^”]+)”"        // 3  ← 追加（U+201C/U+201D）
			        + "|\"([^\"]+)\""     // 4
			        + "|‘([^’]+)’"        // 5  ← 追加（U+2018/U+2019）
			        + "|'([^']+)'"        // 6
			    );
			
			public static final java.util.regex.Pattern P_COVER =
			        java.util.regex.Pattern.compile("(?i)(歌ってみた|歌ってみました|弾いてみた|踊ってみた|ピアノ|piano|演奏してみた|feat\\.?|Lyric(?:\\s*Video)?|Official|MV|(?-i)Music(?:\\s*Video)?|cover(?:ed)?(?:\\s*by)?|covered\\s*by)");
			
			public static final String lead = "^(?i)\\s*(?:歌ってみた|歌ってみました|弾いてみた|踊ってみた|演奏してみた|公式|cover(?:ed)?(?:\\s*by)?|covered\\s*by)\\s*(?:[|｜:/\\-–—]+\\s*)?";
			
			public static final Pattern P_AFTER_FEAT_SEPARATOR =
			        Pattern.compile("[-–—/／|｜]");

			public static final Pattern P_CLOSING_BRACKET =
			        Pattern.compile("[)）\\]］】]");

			public static final Pattern P_SONG_CANDIDATE_END =
			        Pattern.compile("[\\(（\\[［【「『\\-–—/／|｜]");


		    
}
