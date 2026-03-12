package infrastructure.index;

import java.util.HashSet;
import java.util.Set;

import domain.music.MusicMatchJudge;
import domain.youtube.MusicKeyType;
import dto.music.MusicDetail;
import dto.response.UpdateKey;
import util.common.MusicKeyUtils;

public class PrefixMatcher {
	private final MusicIndexManager manager;
	public PrefixMatcher(MusicIndexManager manager) {
		this.manager = manager;
	}
	record SongArtistEntry(UpdateKey key, Set<MusicIndexEntry> array, Boolean newMusicFlag, Boolean existingMusicFlag/*既存の曲フラグ*/) {}
	public class Result{
		  public boolean newsong;
		  public boolean existingMusicFlag;
		  public MusicIndexEntry recordKey;
		  public UpdateKey updateKey;

		  public Result(boolean newsong, boolean existingMusicFlag, MusicIndexEntry recordKey, UpdateKey updateKey) {
			  this.newsong = newsong;
			  this.existingMusicFlag = existingMusicFlag;
			  this.recordKey = recordKey;
			  this.updateKey = updateKey;
		  }
	  }


	/** 入力（曲名候補）について、先頭から1文字ずつ強キーで絞り込み、残存候補の曲名キーを返す */
	  public SongArtistEntry narrowByPrefix(String input){
	    String k = MusicKeyUtils.strongKey(input);
	    if (k.isEmpty()) return new SongArtistEntry(null,Set.of(),false, false);
	    if (k != null && k.length() > 24) {
	        k = k.substring(0, 24);
	    }

	    int keep = 0;
	    Set<MusicIndexEntry> pre = null;
	    Set<MusicIndexEntry> cur = null;
	    for (int i=1;i<=k.length(); i++){
	      String p = k.substring(0, i);
	      Set<MusicIndexEntry> bucket = manager.prefixGet(p);
	      if (bucket == null || bucket.isEmpty()){
	        cur = null;
	        break;// 0件になったら早期終了
	      }
	      cur = (cur == null) ? new HashSet<MusicIndexEntry>(bucket) : intersect(cur, bucket);
	      pre = (pre == null) ? cur : intersect(pre, cur);
	      keep++;
	    }
	    
	    if(cur != null && cur.size() == 1) {
	    	//まだどちらが完全に一致したのかわからないのでメソッドを使って確認する処理
	    	 if(k.equals(manager.musicDetailGetKeyValue(cur.iterator().next()))) {
	    		 //完全一致
	    		 return new SongArtistEntry(new UpdateKey(k,k,false),cur,false,true);
	    	 }else {
	    		 //タイトル側の文字がすべて一致。7割り一致してたらいったん今のコードではその曲だと仮定して動く
	    		 if((double)keep/(double)manager.musicDetailGetKeyValue(cur.iterator().next()).length() > 0.6) {
	    			 return new SongArtistEntry(new UpdateKey(k,manager.musicDetailGetKeyValue(cur.iterator().next()),true),cur,false,true);
	    			 
	    		 }else {
	    			 //多分新しい曲だと思う
	    			 return new SongArtistEntry(null,Set.of(),true,false);
	    		 }
	    	 }
	    	
	    }else if(cur != null && cur.size() >= 2){
	    	//ここは複数ヒットしたとき
	    	if(k.length() > 4) {//いったん応急処置でこのコードにしてるけど後ですぐに絶対に変える
	    		Set<MusicIndexEntry> cur2 = new HashSet<>();
	    		for(MusicIndexEntry c : cur) {
	    			if(manager.musicDetailGetKeyValue(c).equals(k))cur2.add(c);
	    		}
	    		if(cur2.size() == 0) {
	    			return new SongArtistEntry(new UpdateKey(k,k,false),cur,false,false);
	    			
	    		}else if(cur2.size() == 1){
	    			//完全に一致したものが一つ
	    			return new SongArtistEntry(new UpdateKey(k,k,false),cur2,false,true);
	    		}else {
	    			return new SongArtistEntry(new UpdateKey(k,k,false),cur2,false,false);
	    		}
	    	}else {
	    		Set<MusicIndexEntry> cur2 = new HashSet<>();
	    		for(MusicIndexEntry c : cur) {
	    			if(manager.musicDetailGetKeyValue(c).equals(k))cur2.add(c);
	    		}
	    		if(cur2.size() == 0) {
	    			
		    		return new SongArtistEntry(new UpdateKey(k,k,false),Set.of(),true,false);
	    			
	    		}else if(cur2.size() == 1){
	    			//完全に一致したものが一つ
	    			return new SongArtistEntry(new UpdateKey(k,k,false),cur2,false,true);
	    		}else {
	    			return new SongArtistEntry(new UpdateKey(k,k,false),cur2,false,false);
	    		}
	    		
	    	}
	    	
	    }else if(cur == null && pre == null){
	    	//一文字目から何も一致しなかった
	    	return new SongArtistEntry(null,Set.of(),false,false);
	    }else if(pre != null && pre.size() == 1) {
	    	//途中で候補が消えた
	    	if((double)keep/(double)k.length() > 0.8) {//今の８割が同じなら同じ曲として扱うことにする
	    		//同じ曲として処理
	    		return new SongArtistEntry(new UpdateKey(manager.musicDetailGetKeyValue(pre.iterator().next()), manager.musicDetailGetKeyValue(pre.iterator().next()),false),pre,false,true);
	    	}else {
	    		return new SongArtistEntry(new UpdateKey(k,k,false),Set.of(),true,false);
	    	}
	    }else if(pre != null && pre.size() >= 2) {
	    	//二つ一気に消えた場合は多分新しい曲だと思う
	    	return new SongArtistEntry(null,Set.of(),false,false);
	    }else {
	    	//デバッグ用
	    	//本来はこの処理には行くことはない。行ったらバグ
	    	System.out.println("本来は行くはずない処理に行きました");
	    	return new SongArtistEntry(null,Set.of(),false,false);
	    }
	    
	    
	  }
	  

	  private static <T> Set<T> intersect(Set<T> a, Set<T> b){
	    if (a.size() > b.size()) { Set<T> t = a; a=b; b=t; }
	    Set<T> out = new HashSet<>();
	    for (T x : a) if (b.contains(x)) out.add(x);
	    return out;
	  }

	  // ========= 5) 類似候補を取る（先頭一致で絞ってからLCS上位） =========

	  public Result songCandidates(String input, int limit, String titleOrArtist){
	    // 先頭一致で粗く絞る
		SongArtistEntry narrowed = narrowByPrefix(MusicKeyUtils.strongKey(input));
	    if(narrowed.newMusicFlag() || narrowed.key() == null) {
	    	//何もヒットしなかった、もしくは名前違うけど多分別の曲？なのを新しく曲追加しろって言うフラグ
	    	return new Result(true,false,null,null);
	    }else if(narrowed.existingMusicFlag()) {
	    	return new Result(false, true, narrowed.array().iterator().next(),narrowed.key());
	    }
	    
	    titleOrArtist = MusicKeyUtils.normKey(titleOrArtist);
	    Set<MusicIndexEntry> cur = narrowed.array();//songKeyPrefix.get(narrowed.key.nextKey)/*ここの処理では多分nextもoldも変わらない*/;
	    MusicIndexEntry t = null;
	    MusicIndexEntry f = null;
	    String resultFixedF = null;
	    String resultFixedT = null;
	    
	    
	    for (MusicIndexEntry k : cur){
	    	MusicDetail m = manager.musicDetailGet(k);
	    	
	    	if(m.fixed()) {
	    		for(String artistKey : m.artistKeys()) {
	    			if(MusicMatchJudge.sameCheck(artistKey, titleOrArtist) && ( resultFixedT == null || resultFixedT.length() > artistKey.length()) ){
	    				resultFixedT = artistKey;
	    				t = k;
	    				break;
	    			}
	    			
	    		}
	    		
	    	}else {
	    		
	    		String checkKey = null;
	    		if(k.type() == MusicKeyType.SONG) {
	    			checkKey = m.artistKey();;
	    		}else {
	    			checkKey = m.songKey();
	    		}
	    		
	    		if(MusicMatchJudge.sameCheck(checkKey, titleOrArtist) && ( resultFixedF == null || resultFixedF.length() < checkKey.length()) ){
	    			resultFixedF = checkKey;
	    			f = k;
	    		}
	    	}
	    	
	    	
	    	
	    }
	    
	    if(f == null && t == null) {
	    	int keep = -1;
	    	for(MusicIndexEntry k : cur) {
	    		MusicDetail m = manager.musicDetailGet(k);
	    		String checkKey = null;
	    		if(k.type() == MusicKeyType.SONG) {
	    			checkKey = m.artistKey();
	    		}else {
	    			checkKey = m.songKey();
	    		}
	    		if(keep == -1) {
	    			f = k;
	    			keep = checkKey.length();
	    			continue;
	    		}
	    		
	    		if(checkKey.length() < keep) {
	    			f = k;
	    			keep = checkKey.length();
	    		}
	    		
	    	}
	    }
	    
	    
	    //ここでresultFixedの中身から普通の上の方のreturnの形で返すようにする
	    if(t != null && f != null) {//条件のこれしかヒットしないかも
	    	return new Result(false, true, t,new UpdateKey(manager.musicDetailGetKeyValue(t), manager.musicDetailGetKeyValue(t), false));
	    }else if(t != null) {
	    	return new Result(false, true, t,new UpdateKey(manager.musicDetailGetKeyValue(t), manager.musicDetailGetKeyValue(t), false));
	    }else if(f != null) {
	    	return new Result(false, true, f,new UpdateKey(manager.musicDetailGetKeyValue(f), manager.musicDetailGetKeyValue(f), false));
	    }else if(titleOrArtist.isEmpty()) {
	    	return new Result(false, true, f,new UpdateKey(manager.musicDetailGetKeyValue(f), manager.musicDetailGetKeyValue(f), false));
	    }else {
	    	//新しい曲と判定
	    	return new Result(true,false,null,null);
	    }
	    
	  }
}
