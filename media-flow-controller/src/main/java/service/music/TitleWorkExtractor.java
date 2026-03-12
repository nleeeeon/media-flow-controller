package service.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import com.google.gson.JsonObject;

import domain.music.MusicMatchJudge;
import domain.youtube.VideoGenre;
import dto.anime.Anime;
import dto.anime.AnimeWithVideoId;
import dto.music.MusicSource;
import dto.music.Track;
import util.common.TitlePatterns;
import util.string.Strings;
import util.youtube.YtJson;

public class TitleWorkExtractor {
	private final Map<String/*channelId*/, Long> channelRegistrantNumber;//チャンネルごとの登録者数
	private final Map<String, String> channel_title_ja_by_id;
	private Set<AnimeWithVideoId> onlyAnimes;
	private Map<String/*曲名*/, List<MusicSource>> songForArtist;
	public TitleWorkExtractor(Map<String, Long> channelRegistrantNumber, Map<String, String> channel_title_ja_by_id, 
			Set<AnimeWithVideoId> onlyAnimes, Map<String/*曲名*/, List<MusicSource>> songForArtist) {
		this.channelRegistrantNumber = channelRegistrantNumber;
		this.channel_title_ja_by_id = channel_title_ja_by_id;
		this.onlyAnimes = onlyAnimes;
		this.songForArtist = songForArtist;
		
	}
	public static boolean containsPianoWord(String s){
		  if (s == null || s.isBlank()) return false;
		  return TitlePatterns.PIANO_DETECT.matcher(s).find();
		}
	public record TitleExtractResult(
			Track works,
			String channelIds,
			VideoGenre category,
			boolean is_confident,
			boolean foundKeyParenthesis
			) {}
		
	private static final String FIRST_TAKE_CHANNEL_ID = "UC9zY_E8mcAo_Oq772LEZq8Q";

	  public TitleExtractResult extractWorksFromTitle(JsonObject item, String rawTitle, String rawSubtitle, String videoId){
		  Track track = new Track();
		  Track keeptrack = new Track();
		  String title = Strings.norm(rawTitle);
		  String subtitle = Strings.norm(rawSubtitle);
		  
		  final String chTitle = Strings.norm(channel_title_ja_by_id.get(YtJson.getStr(item, "snippet", "channelId")));
		  List<String> ytTags = YtJson.getArray(item,"snippet", "tags");
		  String description = YtJson.getStr(item, "snippet", "description");
		  String channelId = YtJson.getStr(item, "snippet", "channelId");
		  String joined = String.join(" / ", title, subtitle, chTitle);
		  boolean madIs_confident = false;
		  boolean madFlag = MadJudge(joined, ytTags) || strMadStrictCheck(description);
		  if(madFlag)madIs_confident = true;
		  boolean pianoHit =
			      containsPianoWord(title) ||
			      containsPianoWord(subtitle)/* ||
			      ytTags.stream().anyMatch(WorksRank::containsPianoWord)タグは余計なものが入ってることがあるのでいったんなし*/;
		  
		  boolean foundKeyParenthesis = false;
		  
		  
		  boolean plainFlag = plainCheck(joined);
		  if(!plainFlag && !madFlag && channelRegistrantNumber.get(channelId) < 500000 && (strMadCheck(description) || joinedStrMadCheck(joined))) {
			  madFlag = true;
			  if(title.length() <= 10) {
				  madIs_confident = true;
			  }else {
				  
				  madIs_confident = false;
			  }
			  
		  }
		  
		//いったん宣言だけする。タイトル等にmadがついてたらmad優先。madやピアノが無く音楽系の文字があったらtrueになる
		  
		//追加コード↓
			String baseTitle = title;
			
			//追加コード↑
		  
		  boolean hasOpEd  = TitlePatterns.P_OP_ED.matcher(title).find();
		  boolean hasAnime = title.toLowerCase().matches(".*(anime|アニメ).*");
		  boolean vocaloHit = false;
		  
		  //ifの条件はネガティブなんたら方式で行うからplainCheckは使わない
		  if(!title.contains("メドレー") && ((plainFlag && !pianoHit && !madFlag) || (!plainFlag && !pianoHit && !madFlag)) ) {
			  Anime anime = new Anime();
			  
			  anime.season = 1;
			  anime.cour = 1;
			  anime.isOp = true;
			  java.util.regex.MatchResult mrSong = null;
			  {
				  var brackets     = findAllBrackets(title);
				  if (hasAnime && !brackets.isEmpty()) {
					  // 1) 「アニメ」の近傍かっこがあればそれをアニメ名、もう一方を曲名
					  var near = TitlePatterns.P_ANIME_NEAR_BRACKET.matcher(title);
					  if (near.find()) {
					    anime.title = Strings.norm(near.group(2));
					    if (brackets.size() >= 2) {
						      int nearStart = near.start(1);
						      java.util.regex.MatchResult other = null;
						      for (var mr : brackets) {
						        if (mr.start() != nearStart) { other = mr; break; }
						      }
						      if (other != null) {
						        anime.song = Strings.norm(other.group(2));
						        mrSong = other; // ← 曲名側の括弧
						      }
					     }else if(!hasOpEd) {
					    	anime.title = null;
					     }
				   } else if(hasOpEd){
					    // 3) 1組だけ → いったん左端をアニメ名（曲名は別ルールに委ね）
					    anime.title = Strings.norm(brackets.get(0).group(2));
					    // mrSong は未確定
					  }


					}else if (hasOpEd && !brackets.isEmpty() && brackets.size() == 2) {

						  // ① 劇場版の直後括弧を優先してアニメタイトルに
						  var g = TitlePatterns.P_GEKIJO_NEAR.matcher(title);
						  if (g.find()) {
						    anime.title = Strings.norm(g.group(2));
						    // 残りの括弧を曲名にする
						    java.util.regex.MatchResult other = null;
						    for (var mr : brackets) {
						      if (mr.start() != g.start(1)) { other = mr; break; }
						    }
						    if (other != null) {
						      anime.song = Strings.norm(other.group(2));
						      mrSong = other;
						    }
						  } else {
						    // ② タイトルが括弧始まりなら先頭括弧をアニメ名に
						    int firstNonWs = 0;
						    while (firstNonWs < title.length() && Character.isWhitespace(title.charAt(firstNonWs))) firstNonWs++;
						    boolean startsWithBracket = firstNonWs < title.length() &&
						        (title.charAt(firstNonWs) == '「' || title.charAt(firstNonWs) == '『');

						    if (startsWithBracket) {
						      // 先頭括弧→アニメ、もう一方→曲名
						      java.util.regex.MatchResult first = (brackets.get(0).start() == firstNonWs) ? brackets.get(0) : null;
						      if (first == null && brackets.get(1).start() == firstNonWs) first = brackets.get(1);
						      java.util.regex.MatchResult other = (first == brackets.get(0)) ? brackets.get(1) : brackets.get(0);
						      anime.title = Strings.norm(first.group(2));
						      anime.song  = Strings.norm(other.group(2));
						      mrSong = other;
						    } else {
						      // ③ 従来：左=アニメ / 右=曲名
						      anime.title = Strings.norm(brackets.get(0).group(2));
						      anime.song  = Strings.norm(brackets.get(1).group(2));
						      mrSong = brackets.get(1);
						    }
						  }


						}
				  
				// OP/ED 種別
				  if(hasOpEd)anime.isOp = classifyOpEd(title);

				  // ★ アーティスト抽出：曲名の括弧を採用できたときだけ
				  //かつ、かっこが一番右側にあった場合に抽出
				  String artist = "";
				  if (mrSong != null && mrSong.end() >= baseTitle.length()) {
				    int songStart = mrSong.start(); // 曲名括弧の開始インデックス（[「 の位置）
				    artist = extractArtistLeftOf(title, songStart);
				    if (!Strings.isTrivial(artist)) {
				      // out にはアーティストのみ格納
				      track.artist = artist;
				    }
				  }

				  // 出力キー（表示用）は anime.title / anime.song を使用
				  if (!Strings.isTrivial(anime.title) || !Strings.isTrivial(anime.song) || !track.isNull()) {
				    // 期・クール
				    {
				      var m = TitlePatterns.P_COUR.matcher(title);
				      if (m.find()) anime.cour = Integer.parseInt(m.group(1));
				      var mm = TitlePatterns.P_SEASON_SIMPLE.matcher(title);
				      if (mm.find()) {
				        String numStr = mm.group(1) != null ? mm.group(1) : mm.group(2);
				        anime.season = parseNumber(numStr);
				      }
				    }
				    
				    track.song = !Strings.isTrivial(anime.song) ? anime.song : null;
		  	          if(anime.song == null) {
		  	        	  AnimeWithVideoId a = new AnimeWithVideoId(anime, videoId, channelId);
		  	        	  onlyAnimes.add(a);
		  	          }else {
		  	        	  MusicSource a = new MusicSource(track.song, artist,true,videoId,anime,channelId, false, baseTitle);
		  	        	  songForArtist.computeIfAbsent(track.song, k -> new ArrayList<>()).add(a);
		  	          }
				    	return new TitleExtractResult(track, channelId, VideoGenre.MUSIC, true, false);
				  }
				  
				  
				}
		  // A) FIRST TAKE タイトル用の抽出（「アーティスト - 曲名」+ 末尾に / THE FIRST TAKE or feat.）

		  		//ここにif文でfirsttakeのチャンネルIDの時だけやらせる
		  		if(FIRST_TAKE_CHANNEL_ID.equals(YtJson.getStr(item, "snippet", "channelId"))){
				  var m = TitlePatterns.P_FIRSTTAKE_TITLE.matcher(title);
				  if (m.find()) {
				    String artist = Strings.norm(stripHeadBracket(m.group(1)));
				    String song   = Strings.norm(m.group(2));
				    if (!Strings.isTrivial(artist)) track.artist = artist;
				    if (!Strings.isTrivial(song))   track.song = song;

				    if(!track.isNull()) {
				    	
				    	//THE FIRST TAKEはたまに「-」が入ってることがあって正しくないセットで入る可能性があるけどあまりないから、その時はその時で
				    	MusicSource a = new MusicSource(track.song, track.artist,true,videoId,new Anime(),channelId, false, baseTitle);
				    	songForArtist.computeIfAbsent(track.song, k -> new ArrayList<>()).add(a);
				    	
				    	
				    	return new TitleExtractResult(track, channelId, VideoGenre.MUSIC, true, false);
				    }
						  
				  }
				}
		  		title = Strings.norm(title);
		  		int pairCnt = countQuotePairs(baseTitle);
		  		foundKeyParenthesis = (pairCnt > 0);
				// ① （）【】 等のルール（先頭だけ消す→その後に再出現したら右側全部カット）
				title = cutByBracketsRule(title);
				boolean parenthesisNotFoundFlag = title.equals(baseTitle);
				// ② cover/歌ってみた 等のルール（先頭だけ消す→その後に再出現したら右側全部カット）
				title = cutByCoverLikeRule(title);
				title = Strings.norm(title);
		  		boolean fixed = true;
		  	//追加コード↓
		  		// ==== extractWorksFromTitle 内：ボカロ名が出たら手前を曲名とみなすブロック ====
		  		vocaloid_song_check(track, title, chTitle); 
		  		
		  		  
		  		  if(!track.isNull()) {
		  		    	
		  		    	MusicSource a = new MusicSource(track.song, track.artist,fixed,videoId,new Anime(),channelId, false, baseTitle);
		  		    	songForArtist.computeIfAbsent(track.song, k -> new ArrayList<>()).add(a);
				    	return new TitleExtractResult(track, channelId, VideoGenre.MUSIC, true, false);
		  		    }
		  		  
		  		  
		  	      // --- ここから新規ブロック：かっこ→cover/歌ってみた→抽出 ---
		  	      {
		  	        String work = title;
		  	        
		  	        //先に『』「」の組が2以上か判定して、falseなら中の処理を行う
		  	        if (!work.isEmpty()) {
		  		
		  		//アーティスト名と曲名を一旦抽出するフェーズ
		  		          // ③ 『』や「」が“ちょうど1組だけ”あれば：中身=曲名、左側=アーティスト（左が空ならアーティスト無し）
		  	        	artistSongTitleExtraction(track, work);
		  	        	
		  		        //抽出した曲名とアーティストの順序が正しいかどうかを判定するフェーズ
		  	        	if(track.isArtistMissing() && TitlePatterns.p.matcher(chTitle).find()) {
		  		        	Matcher m = TitlePatterns.p.matcher(chTitle);
		  		        	if (m.find()) {
		  			            String name = m.group(1).trim();
		  			            // （かっこと中身）を削除 → "Aimer (Official)" → "Aimer"
		  			            name = name.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
		  			            track.artist = name;
		  			        }
		  	        	}else if(track.isArtistMissing() && artistCheckAncChenge(track, chTitle)) {
		  	        		String artist = track.artist;

			  	      	    // 既存ロジック維持：引用符ペアから再抽出を試す
			  	      	    findLiftPairProcess(baseTitle, track);
		
			  	      	    // 曲名が取れたなら、artistCheckAncChenge で決めた artist を戻す
			  	      	    if (track.song != null) {
			  	      	        track.artist = artist;
			  	      	    }else {
			  	      	    	
			  	      	    	String cand = findSongCandidateAfterFeatOrBracket(baseTitle, work, chTitle);
			  	      	    	if ( cand == null) {
			  	      	    		System.out.println("対応してないコードに来ました.videoId=" + videoId);
			  	      	    		track.clear();
			  	      	    	}else {
			  	      	    		
			  	      	    		track.song = cand;
			  	      	    	}
			  	      	    	
			  	      	    }
		
		  		        
		  		    	  
		  		        }else if(track.isArtistMissing()) {
		  		        	if(!plainFlag && parenthesisNotFoundFlag) {
		  		        		if(!foundKeyParenthesis && baseTitle.length() <= 10)madIs_confident = true;
		  		        		keeptrack = track.copy();
		  		        		track.clear();
		  		        	}
		  		        }else if(track.hasAllValues()) {
		  		        	boolean flag = channelNameAndArtistNameComparison(track, chTitle);
		  		        	if(plainFlag) {
		  		        		//以下のメソッドはチャンネル名の文字があるかと同時に入れ替えもする
		  		        		fixed = flag;
		  		        	}else if(!flag && channelRegistrantNumber.get(channelId) < 500000 && parenthesisNotFoundFlag){
		  		        		if(!foundKeyParenthesis && baseTitle.length() <= 10)madIs_confident = true;
		  		        		//plainFlagがfalseでチャンネル名がタイトルのどこにもなかったため、抽出を中止
		  		        		keeptrack = track.copy();
		  		        		track.clear();
		  		        		
		  		        	}
		  		        	
		  		        	if(fixed && (coverCheck(baseTitle) || !flag) )fixed = false;
		  		        	
		  		        }
		  	        }else if(baseTitle.isEmpty()) {
		  	        	//タイトルを付けないで投稿された音楽があったのでそれ用に
		  	        	track.song = "";
		  	        	track.artist = chTitle;
		  	        }
		  	      

		  	        if (!track.isNull()) {
		  	        	track.swapNamesIfNeeded();
		  	        	track.song = trimDecorativeBrackets(track.song);
		  	        	if(track.artist != null)track.artist = trimDecorativeBrackets(track.artist);
		  	          MusicSource a = new MusicSource(track.song, track.artist,fixed,videoId,new Anime(),channelId, false, baseTitle);
			  	        songForArtist.computeIfAbsent(track.song, k -> new ArrayList<>()).add(a);
				    	
				    	return new TitleExtractResult(track, channelId, VideoGenre.MUSIC, true, foundKeyParenthesis);
		  	        }
		  	      }


		  		
			  
			  //OPED判定があっても音楽判定とする
			  if(!plainFlag && (hasOpEd || vocaloHit))plainFlag = true;

		  }else if(!title.contains("メドレー") && !madFlag && pianoHit) {
			  //ピアノ動画だからチャンネル名がアーティストということは無いと思う
			  titleJustForWithSongTitleAndArtistExtract(track, baseTitle);
			  if (!track.isNull()) {
				  track.swapNamesIfNeeded();
				  boolean fixed = false;
				  if(track.isArtistMissing())fixed = true;
				  track.song = trimDecorativeBrackets(track.song);
		        	if(track.hasAllValues())track.artist = trimDecorativeBrackets(track.artist);
		          MusicSource a = new MusicSource(track.song, track.artist,fixed,videoId,new Anime(),channelId, true, baseTitle);
		  	        songForArtist.computeIfAbsent(track.song, k -> new ArrayList<>()).add(a);
			    	
			    	return new TitleExtractResult(track, channelId, VideoGenre.PIANO, true, false);
		        }
		  }
			  
		  
		//  “ピアノ”補強（title/subtitle/tags のどれかに含まれていれば追加）
		//	このフェーズでoutに何も入らなかったらreturnにチャンネルＩＤも返す  
			  if (pianoHit) {
				  //out.add("ピアノ");
				  return new TitleExtractResult(new Track(), channelId, VideoGenre.PIANO, true, false);
			  }else if(!madFlag && !plainFlag && track.isNull()){
				  return new TitleExtractResult(keeptrack,YtJson.getStr(item, "snippet", "channelId"), VideoGenre.MAD, madIs_confident, foundKeyParenthesis);
			  }else if(!madFlag && plainFlag) {//このif文はデバッグ用
				  //このコードには多分来ることはない。音楽だと判定したら無理やりせめて曲名として何か抽出をしてると思う
				  System.out.println("音楽なんだろうけど抽出できなかったもの＝"+title);
				  return new TitleExtractResult(keeptrack,channelId,VideoGenre.MUSIC, true, false);
			  }else if(madFlag){
				  return new TitleExtractResult(keeptrack,channelId,VideoGenre.MAD, madIs_confident, false);
			  }else {
				  System.out.println("バグです。どの判定にも引っかからなかった");
				  return new TitleExtractResult(keeptrack,channelId,VideoGenre.UNKNOWN, false, false);
			  }
			  
			
		}
	  
	  public static void vocaloid_song_check(Track track, String title, String chTitle) {
		    java.util.regex.Matcher mv = TitlePatterns.P_VOCALO_PLAIN_OR_WITH.matcher(title);
		    if (mv.find()) {
		      // ボカロ名（or "with + ボカロ名"）が現れた位置より左側から曲名候補を切り出す
		      int namePos = mv.start();
		      String left = title.substring(0, Math.max(0, namePos)).trim();

		      // 直前の区切り（/ ／ - – — | × など）でタイトル部と残りを分け、左側を曲名とみなす
		      int cut = lastCutIndex(left);
		      String song = (cut >= 0 ? left.substring(0, cut) : "").trim();
		      song = stripHeadBracket(song);
		      // 末尾に余った始まり括弧類を掃除（「『( ［ などの直後で止まっていた場合の体裁調整）
		      song = trimDecorativeBrackets(song);
		      
		      final String checkSong = song;
	          if(Arrays.stream(TitlePatterns.normalMusicElements).anyMatch(s -> checkSong.equalsIgnoreCase(s)))song = "";

		      
	          if (!song.isEmpty() && !Strings.isTrivial(song)) {
	        	  String artist = chTitle.replaceAll("\\s*\\(.*?\\)\\s*", "").trim();
	        	  if(!artist.isEmpty())track.artist = artist;
	        	  track.song = song;
	          }
		      
		    }
		  }
		
	  public static void artistSongTitleExtraction(Track track, String work) {
		  int pairCnt = countQuotePairs(work);

	        if (pairCnt >= 2) {
	            // 一番左のペア + その左隣を使う
	        	findLiftPairProcess(work, track);
	            

	        } else {
	            // 従来ロジック：1組なら [左=アーティスト, 中身=曲名]、0組なら ④ へ
	            var q = findSingleQuotePair(work);
	            if (q != null) {
	                int qs = q.start();
	                String artistMaybe = work.substring(0, Math.max(0, qs));
	                String song = Strings.norm(q.group(1));

	              String[] pair = splitFirstTwoByDelims(song, TitlePatterns.DELIMS1);
	            if(pair.length >= 2) {
	            	artistMaybe = Strings.norm(pair[0]);
	            	song = Strings.norm(pair[1]);
	            }
	                
	                artistMaybe = artistMaybe
	                    .replaceAll("^[\\p{Zs}/／\\-–—|｜]+", "")
	                    .trim();

	                if (!Strings.isTrivial(artistMaybe)) track.artist = artistMaybe;
	                if (!Strings.isTrivial(song))        track.song = song;

	            } else {
	                // ④ 区切り（左から最初の2箇所）で [artist, song]
	                String[] pair = splitFirstTwoByDelims(work, TitlePatterns.DELIMS2);
	                if (pair.length >= 2) {
	                    String a = Strings.norm(pair[0]);
	                    String b = Strings.norm(pair[1]);
	                    if (!Strings.isTrivial(a)) track.artist = a;
	                    if (!Strings.isTrivial(b)) track.song = b;
	                    if(track.isSongMissing()) {
	                    	track.song = track.artist;
	                    	track.artist = null;
	                    }
	                } else {
	                    if (!Strings.isTrivial(work)) track.song = work;
	                }
	            }
	        }
	  }
	  
	  private static boolean coverCheck(String str){
	      return java.util.regex.Pattern
					    .compile("(?i)(歌ってみた|cover)")
					    .matcher(str)
					    .find();
	  }
	  public static void titleJustForWithSongTitleAndArtistExtract(Track track, String title) {
		  title = cutByBracketsRule(title);
			// ② cover/歌ってみた 等のルール（先頭だけ消す→その後に再出現したら右側全部カット）
			title = cutByCoverLikeRule(title);
			title = Strings.norm(title);
			vocaloid_song_check(track, title, "");
			if(track.isNull())artistSongTitleExtraction(track, title);
			track.swapNamesIfNeeded();
			
	  }
	  
	  private static void findLiftPairProcess(String work, Track track) {
			var qleft = findLeftmostQuotePair(work);  // ← new
	      if (qleft != null) {
	          int qs = qleft.start();               // 左ペアの開始位置
	          String song = Strings.norm(qleft.group(1));   // 中身＝曲名

	          // ペア直前からアーティスト候補を抽出（既存ヘルパー）
	          String artistMaybe = extractArtistLeftOf(work, qs);
	          
	          String[] pair = splitFirstTwoByDelims(song, TitlePatterns.DELIMS1);
	          if(pair.length >= 2) {
	          	artistMaybe = Strings.norm(pair[0]);
	          	song = Strings.norm(pair[1]);
	          }

	          // 整形と trivial 排除
	          artistMaybe = artistMaybe
	              .replaceAll("^[\\p{Zs}/／\\-–—|｜]+", "")
	              .trim();

	          if (!Strings.isTrivial(artistMaybe)) track.artist = artistMaybe;
	          if (!Strings.isTrivial(song))        track.song = song;
	      }
		}
		// 「『…』/「…」/ "…" / '…'」の“左から最初の1組”を返す。
		// 戻り値の group(1) で必ず中身を取得できるようラップする。
	  private static java.util.regex.MatchResult findLeftmostQuotePair(String s) {
		    if (s == null || s.isBlank()) return null;
		    // 『…』 / 「…」 / “…”
		    // "…" / '…' / ‘…’
		    
		    java.util.regex.Matcher m = TitlePatterns.P_ONE.matcher(s);
		    if (!m.find()) return null;
		    java.util.regex.MatchResult mr = m.toMatchResult();

		    return new java.util.regex.MatchResult() {
		        @Override public int start() { return mr.start(); }
		        @Override public int start(int g) { return mr.start(g); }
		        @Override public int end() { return mr.end(); }
		        @Override public int end(int g) { return mr.end(g); }
		        @Override public String group() { return mr.group(); }
		        @Override public String group(int g) {
		            // g==1 のときは、どの分岐でマッチしても “中身” を返す
		            if (g == 1) {
		                for (int i = 1; i <= mr.groupCount(); i++) {
		                    String v = mr.group(i);
		                    if (v != null) return v;
		                }
		                return null;
		            }
		            return mr.group(g);
		        }
		        @Override public int groupCount() { return mr.groupCount(); } // ← 実数に合わせる
		    };
		}

		// 一致とみなす下限。要調整
		

		// 「out[0] を baseChannel に寄せる」既存ロジックを維持しつつ fuzzy 判定へ
		private static boolean artistCheckAncChenge(Track track, String baseChannel) {
		    String title = track.song;
		    String tLower = title.toLowerCase();
		    String cLower = baseChannel.toLowerCase();

		    // まず fuzzy 判定（title vs channel）※ title 側はローテーションを内部で実施
		    boolean ok = MusicMatchJudge.artistCheck(tLower, cLower);
		    if (!ok) return false;

		    // true の場合のみ、長さ比較で out[0] を置換（元ロジック踏襲）
		    if (baseChannel.length() < title.length()) {
		    	track.artist = baseChannel;
		    	track.song = null;
		        //out.set(0, baseChannel);
		    }
		    return true;
		}

		// チャンネル名とアーティスト名（out[0]/out[1]）の比較ロジックを fuzzy へ差し替え
		private static boolean channelNameAndArtistNameComparison(Track track, String chTitle) {
		    String a0 = track.artist;
		    String a1 = track.song;

		    // まず out[0] と比較
		    if (MusicMatchJudge.artistCheck(a0, chTitle)) {
		        if (a0.length() > chTitle.length()) track.artist = chTitle;
		        return true;
		    }
		    // ダメなら out[1] を試し、ヒットしたら swap
		    if (MusicMatchJudge.artistCheck(a1, chTitle)) {
		    	track.song = track.artist;
		        if (a1.length() > chTitle.length()) track.artist = chTitle;
		        return true;
		    }
		    return false;
		}
		
		
		// 直前の区切り（｜|×/／-–—：:）の位置を songStart より左で探す
		// s 内で songStart より左にある区切り(delims)のうち、最も右の位置（= songStart に最も近いもの）を返す
		private static int lastIndexOfAnyBefore(String s, int songStart, char... delims) {
			if (s == null || s.isEmpty() || songStart <= 0) return -1;
		    int nearest = -1;

		    // songStart より左側の部分だけを対象に走査
		    int searchEnd = Math.min(songStart - 1, s.length() - 1);

		    for (int i = searchEnd; i >= 0; i--) {
		        char c = s.charAt(i);
		        for (char d : delims) {
		        	
		            if (c == d) {
		                // 最初に見つけた時点でそれが「最も右の区切り」
		                return i;
		            }
		        }
		    }
		    return nearest; // 見つからなければ -1
		}


		// アーティスト候補を「曲名(括弧)の直前の区切り～括弧直前」から抜く
		private static String extractArtistLeftOf(String title, int songBracketStart){
		  if (title == null || songBracketStart <= 0) return "";
		  // 区切り候補
		  int cut = lastIndexOfAnyBefore(title, songBracketStart, '｜','│','|','×','x','X','/','／','-','–','—','：',':');
		  String chunk = (cut >= 0) ? title.substring(cut+1, songBracketStart)
		                            : title.substring(0, songBracketStart);
		  // 整形
		  chunk = Strings.norm(stripHeadBracket(chunk));
		  // ありがちなノイズを右端から掃除（OP/ED語、ノンクレ等は落とす）
		  chunk = chunk.replaceAll("(?:ノンクレジット|ノンテロップ|OP|ED|オープニング|エンディング)\\s*$", "");
		  // 先頭側の「TVアニメ」「アニメ」等は削る
		  chunk = chunk.replaceFirst("^(?:TV\\s*アニメ|アニメ)\\s*", "");
		  // さらに余計な記号の端を削る
		  chunk = chunk.replaceAll("^[\\p{Zs}\\-–—|｜/／×:：]+", "").replaceAll("[\\p{Zs}\\-–—|｜/／×:：]+$", "").trim();
		  // trivial 判定
		  if (Strings.isTrivial(chunk)) return "";
		  return chunk;
		}

		




	//左から最初の2つの区切りで切って、先頭2トークン（= artist, song）を返す
	//例: "A / B / C - D" -> ["A", "B"] を返す
	private static String[] splitFirstTwoByDelims(String s, java.util.regex.Pattern DELIMS) {
	 if (s == null) return new String[0];
	 var m = DELIMS.matcher(s);
	 int n = s.length();

	 if (!m.find()) {
	   return new String[]{ s.trim() };                  // 区切りなし
	 }
	 int firstStart = m.start();
	 int firstEnd   = m.end();                           // 連続区切りはまとめてここまで

	 String a = s.substring(0, firstStart).trim();       // 先頭〜最初の区切りの直前

	 // 1個目区切りの直後の空白をスキップ
	 int afterFirst = firstEnd;
	 while (afterFirst < n && Character.isWhitespace(s.charAt(afterFirst))) afterFirst++;

	 if (m.find()) {
	   // 2個目の区切りがある → 2個目区切りの直前までを song
	   int secondStart = m.start();
	   String b = s.substring(afterFirst, Math.max(afterFirst, secondStart)).trim();
	   if(b.equals(""))return new String[]{ a };
	   return new String[]{ a, b };
	 } else {
	   // 区切りが1個だけ → 末尾までを song
	   String b = s.substring(afterFirst).trim();
	   if(b.equals(""))return new String[]{ a };
	   return new String[]{ a, b };
	 }
	}

		
		
		  // ===== かっこ削除ルール =====
		  // 仕様：
		  // - 先頭（空白を除く最初の文字）が「（」「【」「[」「［」「(」などの開きかっこなら、
		  //   その“最初の対となる閉じかっこ”までを削除。その後に出てくる“次のかっこ以降”は全削除。
		  // - 先頭がかっこ以外なら、“最初に出てくるかっこ”の位置以降を全削除。
		public static String cutByBracketsRule(String s) {
		    if (s == null || s.isBlank()) return "";

		    final Map<Character, Character> pair = Map.of(
		        '(', ')', '（', '）',
		        '[', ']', '［', '］',
		        '【', '】'
		    );

		    // 1) 先頭の“かっこブロック”を連続して剥がす
		    s = s.stripLeading();
		    while (!s.isEmpty() && pair.containsKey(s.charAt(0))) {
		        char open = s.charAt(0);
		        char close = pair.get(open);
		        int j = s.indexOf(close, 1);
		        s = (j >= 0 ? s.substring(j + 1) : s.substring(1)).stripLeading();
		    }

		    // 2) 先頭がかっこ以外になったら、以降で最初に現れる開きかっこ位置で右側を全削除
		    if (s.isEmpty()) return "";
		    int k = indexOfAnyOpenBracket(s);
		    return (k >= 0 ? s.substring(0, k) : s).trim();
		}
		
		public static String trimBrackets(String s) {
		    if (s == null || s.isEmpty()) return s;

		    // 先頭の括弧
		    if ("([{「『".indexOf(s.charAt(0)) >= 0) {
		        s = s.substring(1);
		    }
		    // 末尾の括弧
		    if (s.length() > 0 && ")]}」』".indexOf(s.charAt(s.length() - 1)) >= 0) {
		        s = s.substring(0, s.length() - 1);
		    }
		    return s;
		}


		  // 最初に出現する“開きかっこ”のインデックス（無ければ -1）
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

		  // ===== cover/歌ってみた ルール =====
		  // 仕様：
		  // 1) 先頭に「歌ってみた / covered by / cover / 弾いてみた / 踊ってみた …」等があればその前置きを除去（区切り記号も軽く掃除）
		  // 2) その後、同キーワードが再び現れたら、その位置“以降”を右側全部カット
		  public static String cutByCoverLikeRule(String s) {
		    if (s == null || s.isBlank()) return "";
		    String x = s;

		    // 先頭の前置き（パイプ等の区切り込みで）を除去
		    
		    x = x.replaceFirst(TitlePatterns.lead, "");

		    // 本体に対象語が再登場したら、その右側を全部カット
		    
		    java.util.regex.Matcher m = TitlePatterns.P_COVER.matcher(x);
		    if (m.find() && (m.start() > 1 || m.find())) {
		      x = x.substring(0, Math.max(0, m.start()));
		    }
		    return x.trim();
		  }

		  private static java.util.regex.MatchResult findSingleQuotePair(String s) {
			    if (s == null || s.isBlank()) return null;


			    java.util.regex.Matcher m = TitlePatterns.P_ONE.matcher(s);
			    java.util.List<java.util.regex.MatchResult> hits = new java.util.ArrayList<>();
			    while (m.find()) hits.add(m.toMatchResult());
			    if (hits.size() != 1) return null; // 1組のみを対象にする

			    java.util.regex.MatchResult mr = hits.get(0);

			    // 種類に関わらず “中身” を group(1) で取得できるようラップ
			    return new java.util.regex.MatchResult() {
			        @Override public int start() { return mr.start(); }
			        @Override public int start(int group) { return mr.start(group); }
			        @Override public int end() { return mr.end(); }
			        @Override public int end(int group) { return mr.end(group); }
			        @Override public String group() { return mr.group(); }

			        @Override public String group(int group) {
			            if (group == 1) {
			                for (int i = 1; i <= mr.groupCount(); i++) {
			                    String v = mr.group(i);
			                    if (v != null) return v;
			                }
			                return null;
			            }
			            return mr.group(group);
			        }

			        @Override public int groupCount() { return mr.groupCount(); }
			    };
			}



		  
		// 『…』/「…」の“完全なペア”の出現数を数える
		  private static int countQuotePairs(String s) {
		    if (s == null || s.isBlank()) return 0;
		    java.util.regex.Matcher m = TitlePatterns.QUOTE_PAIRS
		        .matcher(s);
		    int cnt = 0;
		    while (m.find()) cnt++;
		    return cnt;
		  }

		  


		private static boolean plainCheck(String str){
	        return TitlePatterns.PLAIN_DETECT
	        			.matcher(str)
					    .find() ||
					    TitlePatterns.P_VOCALO_PLAIN_OR_WITH.matcher(str).find();
	        
	        
		    
	    }
		
		public static boolean strMadCheck(String str) {
			if(str == null)return false;
			return str.toLowerCase()
		    .matches("(?s).*(#\\s*mad|音\\s*mad|#\\s*oto\\s*mad|ニコニコ|コメ付き|コメント付き|合作|転載|nicovideo\\.?jp/watch).*");
		}
		
		public static boolean joinedStrMadCheck(String str) {
			if(str == null)return false;
			return str.toLowerCase()
		    .matches("(?s).*(#\\s*mad|音\\s*mad|#\\s*oto\\s*mad|コメ付き|コメント付き|合作).*");
		}
		
		
		
		
		private static String trimDecorativeBrackets(String s) {
		    if (s == null) return null;
		    return TitlePatterns.EDGE_DECOR.matcher(s).replaceAll("").trim();
		}
		// === アニメ + OP/ED 用 追加パターン ===
	 
		
		private static int parseNumber(String s) {
		    // 全角 → 半角
		    s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKC);

		    // 漢数字を対応（必要最低限）
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
		    }

		    return Integer.parseInt(s.replaceAll("\\D", "")); // 数字を取り出してint化
		}
			
		// かっこ出現の開始インデックスから最も近い OP/ED を拾う必要までは無い想定。
		// 文字列全体で OP/ED を判定（両方あったら先に登場したほうを採用）
		private static boolean classifyOpEd(String title) {
		  if (title == null || title.isBlank()) return true;
		  // 位置で優先決定
		  int iOp1 = indexOfRegex(title, "(?i)\\bop\\b");
		  int iOp2 = indexOfRegex(title, "オープニング");
		  int iEd1 = indexOfRegex(title, "(?i)\\bed\\b");
		  int iEd2 = indexOfRegex(title, "エンディング");

		  int iOp = minPos(iOp1, iOp2);
		  int iEd = minPos(iEd1, iEd2);
		  if (iOp < 0 && iEd < 0) {
			  System.out.println("来るはずないコードに行きました。バグです"+" タイトル＝"+title);
			  return true;
		  }

		  if (iEd < 0) return true;
		  if (iOp < 0) return false;
		  return (iOp <= iEd) ? true : false;
		}
		private static int indexOfRegex(String s, String re) {
		  var m = java.util.regex.Pattern.compile(re).matcher(s);
		  return m.find() ? m.start() : -1;
		}
		private static int minPos(int... xs){
		  int out = -1;
		  for (int x : xs) if (x >= 0 && (out < 0 || x < out)) out = x;
		  return out;
		}

		// 「」/『』 をすべて拾う（左→右順）
		private static java.util.List<java.util.regex.MatchResult> findAllBrackets(String s){
		  var m = TitlePatterns.P_ANY_BRACKETS.matcher(s);
		  java.util.ArrayList<java.util.regex.MatchResult> out = new java.util.ArrayList<>();
		  while (m.find()) out.add(m.toMatchResult());
		  return out;
		}
		
		
		private static String stripHeadBracket(String s){
		  if (s == null) return "";
		  return s.replaceFirst("^(【[^】]*】|\\[[^\\]]*\\]|\\([^)]*\\)|（[^）]*）)\\s*", "");
		}
		// ==== 先頭に置く補助（既存の norm と被らないなら省略可）====
		
		
		// 区切りの最後の出現位置（/ ／ - – — | × など）
		private static int lastCutIndex(String s){
		int pos = -1;
		char[] cuts = new char[]{'/', '／', '-', '–', '—', '|'};
		for (char c : cuts) {
		  int i = s.lastIndexOf(c);
		  if (i > pos) pos = i;
		}
		return pos;
		}
		
		
		
		private String findSongCandidateAfterFeatOrBracket(String baseTitle, String work, String chTitle) {
		    final int n = baseTitle.length();

		    int next = baseTitle.indexOf(work);
		    if (next < 0) return null;
		    next += work.length();

		    while (next < n && Character.isWhitespace(baseTitle.charAt(next))) {
		        next++;
		    }

		    boolean startsWithOpenBracket = false;
		    if (next < n) {
		        char c = baseTitle.charAt(next);
		        if (isOpenBracket(c)) {
		            startsWithOpenBracket = true;
		            next++;
		        }
		    }

		    Matcher mf = TitlePatterns.P_FEAT.matcher(baseTitle);

		    int sepIdx;
		    if (!startsWithOpenBracket && mf.find() && mf.start() > 1) {
		        sepIdx = findSeparatorIndexAfterFeat(baseTitle, mf.start());
		    } else {
		        sepIdx = findSeparatorIndexAfterClosingBracket(baseTitle, next);
		    }

		    if (sepIdx < 0) return null;

		    String cand = extractSongCandidate(baseTitle, sepIdx);
		    if (cand == null || cand.isEmpty()) return null;

		    // アーティスト名そのものを誤って曲名として拾わないための保険
		    if (MusicMatchJudge.artistCheck(cand, chTitle)) {
		        return null;
		    }

		    return cand;
		}
		
		private int findSeparatorIndexAfterFeat(String baseTitle, int featPos) {
		    Matcher m = TitlePatterns.P_AFTER_FEAT_SEPARATOR.matcher(baseTitle);
		    return m.find(Math.max(0, featPos)) ? m.start() : -1;
		}
		
		private int findSeparatorIndexAfterClosingBracket(String baseTitle, int startPos) {
		    Matcher m = TitlePatterns.P_CLOSING_BRACKET.matcher(baseTitle);
		    return m.find(Math.max(0, startPos)) ? m.start() : -1;
		}
		
		private String extractSongCandidate(String baseTitle, int sepIdx) {
		    final int n = baseTitle.length();
		    int start = sepIdx + 1;

		    while (start < n && Character.isWhitespace(baseTitle.charAt(start))) {
		        start++;
		    }

		    Matcher endMatcher = TitlePatterns.P_SONG_CANDIDATE_END.matcher(baseTitle);
		    int end = endMatcher.find(start) ? endMatcher.start() : n;

		    String cand = (start < end) ? baseTitle.substring(start, end) : "";
		    cand = stripHeadBracket(cand);
		    cand = trimDecorativeBrackets(cand);

		    return cand;
		}
		private static boolean isOpenBracket(char c) {
		    return c == '(' || c == '（' || c == '[' || c == '［' || c == '【';
		}

	  
	  // ====== 小物 ======
	  
	  
	  
	 

	//===== 追加：音MAD 検出/付与 =====
	private static boolean containsMadWord(String s){
		  if (s == null || s.isBlank()) return false;
		  return TitlePatterns.MAD_DETECT.matcher(s).find();
		}

	public static boolean MadJudge(String title, List<String>ytTags){
	  boolean hit = /*containsMadWord(title)もしあまりよくなかったらこれに戻して*/title.toLowerCase().contains("mad") || ytTags.stream().anyMatch(TitleWorkExtractor::containsMadWord);
	  return hit;
	}
	static boolean strMadStrictCheck(String str) {
		if(str == null)return false;
		return str.toLowerCase()
	    .matches("(?s).*(#\\s*mad|音\\s*mad|#\\s*oto\\s*mad).*");
	}
}
