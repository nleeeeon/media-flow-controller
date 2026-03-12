package service.music;

	import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dao.music.Music_details_dao;
import dao.music.Music_piano_video_dao;
import dao.youtube.Videos_dao;
import domain.music.MusicMatchJudge;
import dto.music.DeterminationMusic;
import dto.music.MusicDetail;
import dto.music.VideoRecord;
import dto.music.artistIdentitys;
import infrastructure.db.DbVendorChecker;
import infrastructure.db.DbVendorChecker.DbVendor;
import infrastructure.index.ArtistSearchIndex.Candidate;
import infrastructure.index.IdGenerator;
import infrastructure.index.MusicIndexEntry;
import infrastructure.index.MusicIndexManager;
import infrastructure.index.PrefixMatcher;
import service.identity.PairJudgeRegistry;
import util.common.MusicKeyUtils;
import util.string.Strings;


public class VideoTitleCorrespondingFinding {
	// 曲名とアーティストの組をキーにする専用クラス
	private final MusicIndexManager manager;
	private final Map<String, Long>Videos_dao_butch;
	private final Map<String, Long>Pianos_dao_butch;
	private final PrefixMatcher songIndex;
	private final IdGenerator idGen;
	private Map<String, Long> videoPlayTimes;
	public VideoTitleCorrespondingFinding(MusicIndexManager manager, IdGenerator idGen) {
		this(manager, idGen, new HashMap<>());
	}
	public VideoTitleCorrespondingFinding(MusicIndexManager manager, IdGenerator idGen, Map<String, Long> videoPlayTimes) {
		this.manager = manager;
		this.Videos_dao_butch = new HashMap<>();
		this.Pianos_dao_butch = new HashMap<>();
		this.songIndex = new PrefixMatcher(manager);
		this.idGen = idGen;
		this.videoPlayTimes = videoPlayTimes;
	}
	

	  public Long getPlayTimes(String videoId) throws SQLException {
		   if(videoPlayTimes.containsKey(videoId)) {
			   return videoPlayTimes.get(videoId);
		   }else {
			   Videos_dao dao = new Videos_dao();
			   VideoRecord v = dao.findByVideoId(videoId);
			   if(v != null) {
				   return v.playCount();
			   }else {
				   return 0L;
			   }
		   }
	  }

	  public void put(DeterminationMusic entry,
			  String searchKeywordArtist, String searchKeywordSong, 
			  boolean anotherMusicFlag, long id) throws SQLException{
		  
		  if(!anotherMusicFlag) {
		    if(entry.songKey == null || entry.songKey.isEmpty()) {
		    	entry.songKey = MusicKeyUtils.strongKey(entry.song);
		    }
		    if(entry.artistKey == null || entry.artistKey.isEmpty()) {
		    	entry.artistKey = MusicKeyUtils.strongKey(entry.artist);
		    }
		  }
	    
		  manager.musicInsert(new MusicDetail(entry, id, getPlayTimes(entry.videoId)), anotherMusicFlag);
	    
	    
	  }
	  
	  private String artistSameCheck(Candidate result1, String artist) {
		  Candidate result2 = null;
			int spaceCount = artist.length() - artist.replace(" ", "").length();
			if(spaceCount == 1){
				String[] parts = artist.split(" ", 2); // 最初の1個のスペースで分割
	            String swapped = parts[1] + " " + parts[0];
				result2 = manager.searchArtistByNgram(swapped);
			}
			
			Candidate ans = null;
			
			if(result1 != null && result2 != null) {
				ans = result2.score > result1.score ? result2 : result1;
			}else if(result1 != null){
				ans = result1;
			}else if(result2 != null){
				ans = result2;
			}
			
			if(ans == null)return null;
			
			if(MusicMatchJudge.sameCheck(ans.artistKey, artist)) {
				return ans.artistKey;
			}else {
				return null;
			}
			
	  }
	  
	  private record LoopAction(PrefixMatcher.Result cand, boolean shouldBreak, boolean shouldContinue, String sameArtistKey) {
		    static LoopAction breakLoop(PrefixMatcher.Result cand, String sameArtistKey) {
		        return new LoopAction(cand, true, false, sameArtistKey);
		    }

		    static LoopAction continueLoop(PrefixMatcher.Result cand, String sameArtistKey) {
		        return new LoopAction(cand, false, true, sameArtistKey);
		    }

		}

		private LoopAction handleNewSongCandidate(
		        DeterminationMusic entry,
		        PrefixMatcher.Result cand) {

			String sameArtistKey = null;
		    String artist = MusicKeyUtils.normKey(entry.artist);

		    Candidate artistMatch = manager.searchArtistByNgram(artist);
		    Candidate songMatch = manager.searchArtistByNgram(MusicKeyUtils.normKey(entry.song));

		    if (isPerfectArtistMatch(artistMatch)) {
		        sameArtistKey = artistMatch.artistKey;
		        entry.fixed = true;
		        return LoopAction.breakLoop(cand, sameArtistKey);
		    }

		    if (isPerfectArtistMatch(songMatch)) {
		        /*determinationMusicSwap(entry);
		        if (i == 1) {
		            cand = songCandidates(entry.song, 5, entry.artist);
		        }*/
		        return LoopAction.continueLoop(cand, sameArtistKey);
		    }

		    if (isJapaneseNearArtistMatch(artist, artistMatch)) {
		        sameArtistKey = artistMatch.artistKey;
		        entry.fixed = true;
		        return LoopAction.breakLoop(cand, sameArtistKey);
		    }

		    String sameKey = artistSameCheck(artistMatch, artist);
		    if (sameKey != null) {
		        sameArtistKey = sameKey;
		        manager.newArtistNameAdd(sameArtistKey, entry.artist);
		        entry.fixed = true;
		        return LoopAction.breakLoop(cand, sameArtistKey);
		    }

		    /*determinationMusicSwap(entry);
		    if (i == 1) {
		        cand = songCandidates(entry.song, 5, entry.artist);
		    }*/
		    return LoopAction.continueLoop(cand, sameArtistKey);
		}

		private String handleFixedCorrection(
		        DeterminationMusic entry,
		        PrefixMatcher.Result cand,
		        MusicDetail candM) {

			String sameArtistKey = null;
		    boolean swapFlag = candM.songKey().contains(cand.updateKey.oldKey());
		    //recorded側のsongとentry側の本来のartistが一致してたらtrue
		    // swapFlag == true:
		    //   本来のアーティスト名が曲名として登録されていた
		    // swapFlag == false:
		    //   信頼度falseだったが、正しい並びでアーティスト・曲名が一致した

		    determinationMusicSwap(entry);
		    cand.newsong = true;
		    cand.existingMusicFlag = false;

		    Set<Long> ids = manager.findIdsByArtist(candM.artistKey());

		    if (swapFlag) {
		    	//登録されてたアーティストは多分違うと思うので、いったん該当アーティスト名はnullにする
		    	manager.removeArtists(ids);

		        for (long id : ids) {
		            if (id == cand.recordKey.id()) {
		            	//今回ヒットしたものは正しいと思うので、アーティスト名も含めて正式に登録する
		            	manager.renameArtistAndSong(id, candM.song(), candM.artist());
		            }
		        }
		        sameArtistKey = candM.songKey();

		    } else {
		    	manager.changeFixedsTrue(ids, candM.artistKey(), candM.artist());
		        sameArtistKey = candM.artistKey();
		    }

		    cand.updateKey = null;
		    return sameArtistKey;
		}
		//artistでは一致してないけど、artistでヒットするのを探して、そのペア同士が一致するならそっちを返すメソッド
		private PrefixMatcher.Result handleExistingMismatch(
		        DeterminationMusic entry,
		        PrefixMatcher.Result cand,
		        MusicDetail candM) {

		    determinationMusicSwap(entry);
		    PrefixMatcher.Result keep = songIndex.songCandidates(entry.song, 5, entry.artist);

		    if (keep.existingMusicFlag
		            && MusicMatchJudge.sameCheck(candM.artist(), entry.artist)) {
		        return keep;
		    }

		    determinationMusicSwap(entry);
		    return cand;
		}

		private String handleNormalArtistMatch(
		        DeterminationMusic entry,
		        PrefixMatcher.Result cand,
		        MusicDetail candM) {

			String sameArtistKey = null;
		    String artist = entry.artist;
		    Candidate artistMatch = manager.searchArtistByNgram(artist);

		    if (artistMatch == null) {
		        return null;
		    }

		    boolean matched = isPerfectArtistMatch(artistMatch)
		            || isJapaneseNearArtistMatch(MusicKeyUtils.normKey(artist), artistMatch);

		    if (!matched) {
		        return null;
		    }

		    boolean alreadyContained = candM.artistKeys().contains(artistMatch.artistKey);

		    // A,B というアーティストがいて、両方に M という曲がある場合に、
		    // 曲名一致だけで別アーティストへ誤登録されるのを防ぐ
		    if (!alreadyContained) {
		        sameArtistKey = artistMatch.artistKey;
		        entry.fixed = true;
		        cand.newsong = true;
		        cand.existingMusicFlag = false;
		        return sameArtistKey;
		    }

		    entry.artist = artistMatch.artistKey;
		    sameArtistKey = artistMatch.artistKey;
		    return sameArtistKey;
		}

		private boolean isPerfectArtistMatch(Candidate result) {
		    return result != null && result.score == 1.0;
		}

		private boolean isJapaneseNearArtistMatch(String artist, Candidate result) {
		    return Strings.containsJapanese(artist)
		            && result != null
		            && artist.length() >= 3
		            && (result.insertOnly || result.deleteOnly);
		}
	  
		private void registerNewMusic(DeterminationMusic entry, boolean fixed) throws SQLException {

		    long id = idGen.newId();

		    put(
		        entry,
		        MusicKeyUtils.normKey(entry.artist),
		        MusicKeyUtils.normKey(entry.song),
		        fixed,
		        id
		    );

		    Videos_dao_butch.put(entry.videoId, id);

		    if (entry.isPiano) {
		        Pianos_dao_butch.put(entry.videoId, id);
		    }
		}
	  
		private record MatchPhaseResult(
		        boolean anotherMusicFlag,
		        boolean existingMusicFlag,
		        String sameArtistKey,
		        boolean unknowFlag) {

		    static MatchPhaseResult keepExisting() {
		        return new MatchPhaseResult(false, true, null, false);
		    }

		    static MatchPhaseResult keepExisting(String sameArtistKey) {
		        return new MatchPhaseResult(false, true, sameArtistKey, false);
		    }

		    static MatchPhaseResult noExisting() {
		        return new MatchPhaseResult(false, false, null, false);
		    }

		    static MatchPhaseResult createAnother(String sameArtistKey) {
		        return new MatchPhaseResult(true, false, sameArtistKey, false);
		    }
		    
		    static MatchPhaseResult unknowExiting() {
		    	return new MatchPhaseResult(false, false, null, true);
		    }
		}
		
		private boolean isArtistMissing(DeterminationMusic entry, MusicDetail candM) {

		    return !(entry.artist != null
		            && !entry.artist.isEmpty()
		            && candM.artistKey() != null
		            && !candM.artistKey().isEmpty());
		}

		private void applyCandidateResult(
		        DeterminationMusic entry,
		        PrefixMatcher.Result cand,
		        MusicDetail candM,
		        boolean anotherMusicFlag) throws SQLException {

		    if (!cand.existingMusicFlag) {
		        long id = idGen.newId();
		        put(entry, MusicKeyUtils.normKey(entry.artist), MusicKeyUtils.normKey(entry.song), anotherMusicFlag, id);
		        Videos_dao_butch.put(entry.videoId, id);

		        if (entry.isPiano) {
		            Pianos_dao_butch.put(entry.videoId, id);
		        }
		        return;
		    }

		    Videos_dao_butch.put(entry.videoId, candM.id());
		    if (entry.isPiano) {
		        Pianos_dao_butch.put(entry.videoId, candM.id());
		    }
		}
		
		private MatchPhaseResult handleFixedRelatedCase(
		        DeterminationMusic entry,
		        PrefixMatcher.Result cand,
		        MusicDetail candM,
		        MusicDetail candMfixedSwap,
		        String entArtistKey,
		        boolean swapFlag,
		        boolean artistIsNull) {

		    if (!artistIsNull && !candMfixedSwap.artistKeys().contains(entArtistKey)) {
		    	//どちらもartist,songが両方登録されていて、artistは単純な一致ではない
		        return handleFixedArtistMismatch(
		                entry, cand, candM, candMfixedSwap, entArtistKey, swapFlag);
		    }

		    if (!artistIsNull && candMfixedSwap.artistKeys().contains(entArtistKey)) {
		        return handleFixedArtistMatch(candM, candMfixedSwap, swapFlag);
		    }

		    return handleFixedArtistMissing(entry, candM, entArtistKey, swapFlag);
		}
		
		private MatchPhaseResult handleFixedArtistMismatch(
		        DeterminationMusic entry,
		        PrefixMatcher.Result cand,
		        MusicDetail candM,
		        MusicDetail candMfixedSwap,
		        String entArtistKey,
		        boolean swapFlag) {

			boolean sameArtistFlag = MusicMatchJudge.sameCheck(candMfixedSwap.artist(),entry.artist);
			
		    String sameArtistKey = sameArtistFlag ? candMfixedSwap.artistKey() : null;

		    if (sameArtistKey != null) {
		    	//アーティスト名の別名だと判断して追加をする
		    	manager.newArtistNameAdd(sameArtistKey, entry.artist);

		        Set<Long> ids = manager.findIdsByArtist(candM.artistKey());

		        if (!candM.fixed() && !swapFlag) {
		        	
		        	manager.changeFixedsTrue(ids, candM.artistKey(), candM.artist());

		        } else if (!candM.fixed() && swapFlag) {
		        	
		        	manager.removeArtists(ids);

		            for (long id : ids) {
		                if (id == cand.recordKey.id()) {
		                	manager.swapNameAndFixedTrue(id);
		                }
		            }
		        }

		        return MatchPhaseResult.keepExisting(sameArtistKey);
		    }

		    long entryPlayCount = videoPlayTimes.getOrDefault(entry.videoId, 0L);

		    //詳しく調べてもアーティスト名は一致しなかったので、再生回数多かったら正しいんじゃないかなって
		    if (candM.playCount() < entryPlayCount) {
		        //recorded側がfixedがtrueなら迷うので、APIで検索をかけて判断する
		    	if (candM.fixed()) {
		            PairJudgeRegistry.worker().submit(candM.artistKey(), entArtistKey);
		            return MatchPhaseResult.noExisting();
		        }

		        Set<Long> ids = manager.findIdsByArtist(candM.artistKey());
		        manager.removeArtists(ids);

		        if (swapFlag) {
		        	manager.renameArtistAndSongAndChangeFixeds(
		                    candM.id(),
		                    entry.artist,
		                    candM.songKey(),
		                    true);
		        } else {
		        	manager.renameArtistAndChangeFixeds(
		                    candM.id(),
		                    entry.artist,
		                    true);
		        }

		        return MatchPhaseResult.keepExisting();
		    }

		    if (entry.fixed && !swapFlag && candM.fixed()) {
		        PairJudgeRegistry.worker().submit(candM.artistKey(), entArtistKey);
		        return MatchPhaseResult.noExisting();
		    }

		    return MatchPhaseResult.keepExisting();
		}
		
		private MatchPhaseResult handleFixedArtistMatch(
		        MusicDetail candM,
		        MusicDetail candMfixedSwap,
		        boolean swapFlag) {

		    if (!candM.fixed() && swapFlag) {
		        Set<Long> ids = manager.findIdsByArtist(candM.artistKey());
		        manager.removeArtists(ids);
		        manager.renameArtistAndSongAndChangeFixeds(
		                candM.id(),
		                candMfixedSwap.artist(),
		                candMfixedSwap.song(),
		                true);

		    } else if (!candM.fixed() && !swapFlag) {
		    	manager.renameArtistAndSongAndChangeFixeds(
		                candM.id(),
		                candM.artist(),
		                candM.song(),
		                true);
		    }

		    return MatchPhaseResult.keepExisting();
		}
		
		private MatchPhaseResult handleFixedArtistMissing(
		        DeterminationMusic entry,
		        MusicDetail candM,
		        String entArtistKey,
		        boolean swapFlag) {

		    if (!entry.fixed) {
		    	
		        Candidate result1 = manager.searchArtistByNgram(MusicKeyUtils.normKey(entry.artist));
		        
		        boolean matched = isPerfectArtistMatch(result1)
		                || isJapaneseNearArtistMatch(MusicKeyUtils.normKey(entry.artist), result1);

		        String sameArtistKey = null;
		        if (!matched) {
		            sameArtistKey = artistSameCheck(result1, MusicKeyUtils.normKey(entry.artist));
		            if (sameArtistKey != null) {
		            	manager.newArtistNameAdd(sameArtistKey, entry.artist);
		            	//アーティストの検索でヒットしたから多分trueじゃないかなって
		            	entry.fixed = true;
		            }
		        }else {
		        	//アーティストの検索でヒットしたから多分trueじゃないかなって
		        	entry.fixed = true;
		        }

		        manager.renameArtistAndChangeFixeds(candM.id(), entry.artist, entry.fixed);
		        return MatchPhaseResult.keepExisting(sameArtistKey);
		    }

		    if (!candM.fixed() && swapFlag) {
		        if (manager.falsePrefixRecordArtistNum(candM.artistKey()) != 1) {
		        	//同じartistと対応する曲がほかにもあった場合、処理しづらいのでいったんdeleteをする
		        	manager.deleteMusicsByArtist(candM.artistKey());
		            return MatchPhaseResult.unknowExiting();
		        }

		        //swapFlagがtrueなので多分recorded側が逆だと思う
		        manager.swapName(candM.id());
		        return MatchPhaseResult.keepExisting();
		    }

		    //artistIsNullがtrueでお互いがfixedなので両方artistないのでそのまま登録
		    if (entry.fixed && candM.fixed()) {
		        if (!entArtistKey.isEmpty() && !candM.artistKey().isEmpty()) {
		        	manager.renameArtistAndChangeFixeds(candM.id(), entry.artist, true);
		        }
		    }

		    return MatchPhaseResult.keepExisting();
		}
		
		private MatchPhaseResult handleNonFixedCase(
		        DeterminationMusic entry,
		        PrefixMatcher.Result cand,
		        MusicDetail candM,
		        MusicDetail candMfixedSwap,
		        String entArtistKey,
		        boolean swapFlag) {

		    if (candMfixedSwap.artistKeys().contains(entArtistKey)) {
		        return MatchPhaseResult.keepExisting();
		    }
		    boolean sameArtistFlag = MusicMatchJudge.sameCheck(candMfixedSwap.artist(), entry.artist);
		    String sameArtistKey = sameArtistFlag ? (candMfixedSwap.artistKey()) : null;

		    if (sameArtistKey != null) {
		        if (swapFlag) {
		        	manager.addNewSongKey(candM.id(), entArtistKey);
		        } else {
		        	manager.addNewArtistKey(candM.id(), entArtistKey);
		        }
		        return MatchPhaseResult.keepExisting(sameArtistKey);
		    }

		    if (swapFlag) {
		    	//entry側のほうがartist,songの順が違うと判断して、artistの新しいsongとして登録する
		        String newArtistKey = candM.artistKey();

		        determinationMusicSwap(entry);
		        entry.artistKey = newArtistKey;
		        entry.songKey = MusicKeyUtils.strongKey(entry.song);

		        return MatchPhaseResult.createAnother(newArtistKey);
		    }

		    if (manager.falsePrefixRecordArtistNum(candM.artistKey()) == 1) {
		    	
		        String newArtistKey = candM.songKey();

		        manager.swapName(candM.id());
		        determinationMusicSwap(entry);
		        entry.artistKey = newArtistKey;
		        entry.songKey = MusicKeyUtils.strongKey(entry.song);

		        return MatchPhaseResult.createAnother(newArtistKey);
		    }

		    //同じartistで複数のsongと対応されてて、処理しづらいのでいったん消す
		    Set<Long> ids = manager.findIdsByArtist(candM.artistKey());
		    manager.deleteMusics(ids);
		    
		    return MatchPhaseResult.unknowExiting();
		}
		
		private boolean hasText(String text) {
			return text != null && !text.isEmpty();
		}
		
		private record CandidateLoopResult(PrefixMatcher.Result cand, String sameArtistKey) {};
		
		private LoopAction handleNewSongOriginal(DeterminationMusic entry) {
			PrefixMatcher.Result cand = songIndex.songCandidates(entry.song, 5, entry.artist);
			MusicDetail candM = manager.musicDetailGet(cand.recordKey);
			if(cand.newsong && hasText(entry.artist)) {
				//artist側でヒットするかもしれないので、逆にしてもう一度検索をかける
				return handleNewSongCandidate(entry, cand);
			}else if(cand.existingMusicFlag && !MusicMatchJudge.sameCheck(candM.artist(), entry.artist)) {
				//念のためにartistとsongを入れ替えて、さらに一致してるのが見つかったら
				//そっちの方に切り替えるため
				cand = handleExistingMismatch(entry, cand, candM);
				return LoopAction.breakLoop(cand, null);
				
			}else {
				
				//アーティスト名をngramのほうで検索をかけて、
				//ちゃんとヒットした曲と同じものかどうかをさらに判定する
				String sameArtistKey = handleNormalArtistMatch(entry, cand, candM);
				return LoopAction.breakLoop(cand,sameArtistKey);
			}
		}
		
		private LoopAction handleNewSongSwappedOrder(DeterminationMusic entry) {
			PrefixMatcher.Result cand = songIndex.songCandidates(entry.song, 5, entry.artist);
			MusicDetail candM = manager.musicDetailGet(cand.recordKey);
			
			if(cand.newsong && hasText(entry.artist)) {
				return handleNewSongCandidate(entry, cand);
			}else if(entry.fixed && !candM.fixed()) {
				//entry側の信頼度がtrueなら条件次第でrecorded側の信頼度を昇格する
				String sameArtistKey = null;
				sameArtistKey = handleFixedCorrection(entry, cand, candM);
				
				return  LoopAction.breakLoop(cand,sameArtistKey);
			}else {
				String sameArtistKey = handleNormalArtistMatch(entry, cand, candM);
				return LoopAction.breakLoop(cand,sameArtistKey);
			}
		}
		
		private CandidateLoopResult resolveCandidate(DeterminationMusic entry) {
			LoopAction action = handleNewSongOriginal(entry);
			if(action.shouldBreak) {
				return new CandidateLoopResult(action.cand, action.sameArtistKey);
			}
			//アーティスト名として取得したものデータも検索して、
			//結果を確認するためにもう一度検索する
			determinationMusicSwap(entry);
		    
			action = handleNewSongSwappedOrder(entry);
			if(action.shouldContinue) {
				//入れ替えても特に結果が変わらなかったのでもとに戻す
				determinationMusicSwap(entry);
			}
			
			return new CandidateLoopResult(action.cand, action.sameArtistKey);
		}
		
		private void handleNewMusic(PrefixMatcher.Result cand, DeterminationMusic entry, String sameArtistKey) throws SQLException {
			boolean createFixed = cand.newsong && sameArtistKey != null;

		    if (createFixed && !entry.fixed && MusicMatchJudge.sameCheck(sameArtistKey, entry.song)) {
		    	//信頼度がfalseでsongとartistが同じ場合この処理に来る
		    	//同じものを登録するとバグが発生しやすくなるので何もしない
		        return ;
		    }

		    if (createFixed) {
		        entry.artistKey = sameArtistKey;
		        entry.songKey = MusicKeyUtils.strongKey(entry.song);
		    }

		    registerNewMusic(entry, createFixed);


		    return;
		}
		
		/* 多分同じ曲だと判定された際のフェーズ */
		private void handleExistingMusic(PrefixMatcher.Result cand, DeterminationMusic entry) throws SQLException {
			MusicDetail candM = manager.musicDetailGet(cand.recordKey);
		    String entArtistKey = MusicKeyUtils.strongKey(entry.artist);

		    boolean recordMatchIsSongKey = candM.songKeys().contains(cand.updateKey.oldKey());
		    //このswapFlagは前方一致でヒットした部分がfalseどうしだったらtrueでartistならtrue
		    boolean swapFlag = !recordMatchIsSongKey;

		    MusicDetail candMfixedSwap = swapFlag
		            ? prefixRecordParticularSwap(cand.recordKey)
		            : candM;

		    boolean artistIsNull = isArtistMissing(entry, candM);
		    boolean fixedRelated = entry.fixed || candM.fixed();

		    MatchPhaseResult result;
		    if (fixedRelated) {
		        result = handleFixedRelatedCase(
		                entry, cand, candM, candMfixedSwap, entArtistKey, swapFlag, artistIsNull);
		    } else {
		        result = handleNonFixedCase(
		                entry, cand, candM, candMfixedSwap, entArtistKey, swapFlag);
		    }
		    
		    if(result.unknowFlag) {
		    	return;
		    }

		    cand.existingMusicFlag = result.existingMusicFlag();

		    applyCandidateResult(entry, cand, candM, result.anotherMusicFlag());

		    boolean keyChangeFlag = cand.updateKey != null && cand.updateKey.changeFlag();
		    if (keyChangeFlag && (result.anotherMusicFlag() || result.existingMusicFlag())) {
		    	manager.renameKey(candM.id(), cand.updateKey);
		    }

		    return;
		}
	  

		
		
		
		
	  // ========= 8) 重複吸収（近いなら同じ曲として保存） =========
	  /** 新規（artist, song）が既存のどの曲キーに近いかを見て、近ければそこにぶら下げ、遠ければ新規キー作成 
	 * @throws SQLException */
	  public void upsertFuzzy(DeterminationMusic entry,boolean pianoFlag) throws SQLException{
	    
	    
		CandidateLoopResult loopResult = resolveCandidate(entry);
		
		PrefixMatcher.Result cand = loopResult.cand;
		String sameArtistKey = loopResult.sameArtistKey;
		
		if (!cand.existingMusicFlag) {
			handleNewMusic(cand, entry, sameArtistKey);
			return;
		}
		
		handleExistingMusic(cand, entry);
		
		
	  }
	  
	  private MusicDetail prefixRecordParticularSwap(MusicIndexEntry entry) {
		  MusicDetail m = manager.musicDetailGet(entry);
		  //アーティスト名と曲名を入れ替えたものを返す
		  return m.swapName();
	  }
	  
	  private void determinationMusicSwap(DeterminationMusic entry) {
		  String str = entry.artist;
		  entry.artist =entry.song;
		  entry.song = str;
	  }
	  
	  public static ByteArrayOutputStream buffer = new ByteArrayOutputStream();
	  public static PrintStream ps = new PrintStream(buffer);
	  public static synchronized void withExclusive(Runnable r) throws InterruptedException {
			  try {
				  r.run();
				  
			  }catch(Exception e) {
				  VideoTitleCorrespondingFinding.ps.flush(); 
			    	System.out.println(VideoTitleCorrespondingFinding.buffer.toString());
			    	  e.printStackTrace();
			    	  Thread.sleep(10000000);
			  }
		  }
	  
	  public void artistSameIdentity(artistIdentitys a) {
	    
		List<String> addArtists = manager.artistSearchAddArtist(a.newArtistKey(), a.oldArtistKey());
	    
		if(addArtists != null && !addArtists.isEmpty()) {
			manager.addNewArtistNames(a.newArtistKey(), addArtists);
		}
		
		
	  }
	  public void start(VideoTitleCorrespondingFinding fx, List<DeterminationMusic> a) throws Exception {
		  for(DeterminationMusic m : a) {
			  //信頼度が低くて、両方同じはバグりやすいと思うのでいったんスキップ
			  if(!m.fixed && m.artist != null && m.artist.equals(m.song)) {
				  continue;
			  }
			  fx.upsertFuzzy(m,m.isPiano);
		  }
		  
		  if(DbVendorChecker.get() == DbVendor.MARIADB) {
			  PairJudgeRegistry.runner().start();
		  }
		  if(DbVendorChecker.get() == DbVendor.MARIADB) {
			  registerMusicDetailsMappings();
		  }
		  if(DbVendorChecker.get() == DbVendor.MARIADB) {
			  registerPianoMusicMappings();
		  }
		  //この処理は最後にする。music_idの外部キーの制約上
		  if(DbVendorChecker.get() == DbVendor.MARIADB) {
			  registerVideoMusicMappings();
		  }
		  
			  
	  }
	  private void registerMusicDetailsMappings() throws SQLException {
		  Music_details_dao dao = new Music_details_dao();
		  dao.upsert(manager.getAllMusicDetail());
	  }
	  private void registerVideoMusicMappings() throws SQLException {
		  Videos_dao dao = new Videos_dao();
		  Videos_dao_butch.entrySet().removeIf(e -> manager.getTableDeleteIds().contains(e.getValue()));
		  dao.updateMusicIdBatch(Videos_dao_butch);
	  }
	  private void registerPianoMusicMappings() throws SQLException {
		  Music_piano_video_dao dao = new Music_piano_video_dao();
		  Pianos_dao_butch.entrySet().removeIf(e -> manager.getTableDeleteIds().contains(e.getValue()));
		  dao.upsertBatch(Pianos_dao_butch);
	  }
	  
	  
}
