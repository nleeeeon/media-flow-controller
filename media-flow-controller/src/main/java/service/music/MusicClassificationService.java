package service.music;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;

import dao.youtube.Videos_dao;
import domain.youtube.ChannelDescriptionGenreJudge;
import domain.youtube.VideoGenre;
import dto.anime.AnimeWithVideoId;
import dto.music.DeterminationMusic;
import dto.music.MusicSource;
import dto.music.Track;
import dto.music.VideoRecord;
import dto.youtube.VideoInput;
import infrastructure.youtube.YouTubeApiClient;
import listener.AppStartupListener;
import service.anime.AnimationOpedSongTitleApiSearch;
import service.music.TitleWorkExtractor.TitleExtractResult;
import service.youtube.ChannelGenreJudgeService;

public class MusicClassificationService{
	private final Map<String/*videoId*/, Long> videoPlayTimes;//動画ごとの再生回数
	private final Map<String/*channelId*/, Long> channelRegistrantNumber;//チャンネルごとの登録者数
	private final Map<String, String> channel_title_ja_by_id;
	private Set<AnimeWithVideoId> onlyAnimes;
	private Map<String/*曲名*/, List<MusicSource>> songForArtist;
	public MusicClassificationService(Map<String, Long> videoPlayTimes, Map<String, Long> channelRegistrantNumber, Map<String, String> channel_title_ja_by_id) {
		this.videoPlayTimes = videoPlayTimes;
		this.channelRegistrantNumber = channelRegistrantNumber;
		this.channel_title_ja_by_id = channel_title_ja_by_id;
		
	}
	
	public record Result(
			  String videoId,
		    String title,
		    String subtitle,
		    VideoGenre category,
		    boolean is_confident,
		    Instant watchedAt,
		    boolean foundKeyParenthesis,
		    Track tags){
			  public Result withWatchedAt(Instant newWatchedAt) {
				  return new Result(
						  videoId,
						  title,
						  subtitle,
						  category,
						  is_confident,
						  newWatchedAt,
						  foundKeyParenthesis,
						  tags
						  );
			  }
			  
			  public Result withCategory(VideoGenre newCategory) {
				  return new Result(
						  videoId,
						  title,
						  subtitle,
						  newCategory,
						  is_confident,
						  watchedAt,
						  foundKeyParenthesis,
						  tags
						  );
			  }
			  
			  public Result withIs_confident(boolean newIs_confident) {
				  return new Result(
						  videoId,
						  title,
						  subtitle,
						  category,
						  newIs_confident,
						  watchedAt,
						  foundKeyParenthesis,
						  tags
						  );
			  }
			  
		  }
	// ====== 設定 ======

public List<Result> classify(List<VideoInput> musicOnly, Map<String, JsonObject> meta, String token, Map<String, Boolean> existingVideoIds) throws Exception {
	songForArtist = new HashMap<>();
	onlyAnimes = new HashSet<>();
  
  //時間を計測。デバッグ用
	long start = System.nanoTime();
	// 1) 一次抽出 & キャッシュヒットを先に反映
	List<Result> allMusic = new ArrayList<>();
	Map<String, Result> keepMusic = new HashMap<>();
	Map<String, List<Instant>> toNextPhaseMusic = new HashMap<>();
	Map<String, Result> history = new HashMap<>();
	Map<String/*videoId*/, String> channelIds = new HashMap<>();
	Set<String> singleChannelIds =  new HashSet<>();
	//ピアノかどうかをデータベースに登録するため
	Set<String> pianos = new HashSet<>();
	Videos_dao dao = new Videos_dao();
	for (VideoInput r : musicOnly) {
    

    	if(history.containsKey(r.videoId)) {
    		Result w = history.get(r.videoId);
    		allMusic.add(w.withWatchedAt(r.watchedAt));
    		continue;
    	}else if(toNextPhaseMusic.containsKey(r.videoId)){
    		toNextPhaseMusic.get(r.videoId).add(r.watchedAt);
    		continue;
    	}
    	
		boolean existingFlag = existingVideoIds.containsKey(r.videoId);
		if(existingFlag && existingVideoIds.containsKey(r.videoId)) {
			VideoRecord m =  dao.findByVideoId(r.videoId);
			if(m != null) {
				
				Result w = new Result(r.videoId, m.videoTitle(), r.subtitle,VideoGenre.MUSIC, true, r.watchedAt, false, null);
				history.put(r.videoId, w);
				continue;
			}
			
		}
    	
    	
    	
      JsonObject item = meta.get(r.videoId);
      TitleWorkExtractor extractor = new TitleWorkExtractor(channelRegistrantNumber, channel_title_ja_by_id, onlyAnimes, songForArtist);
      TitleExtractResult pre    = extractor.extractWorksFromTitle(item, r.title, r.subtitle, r.videoId);
      
      if(pre.category() == VideoGenre.PIANO) {
    	  pianos.add(r.videoId);
      }
      

      Result w = new Result(r.videoId, r.title, r.subtitle, pre.category(), pre.is_confident(), r.watchedAt, pre.foundKeyParenthesis(), pre.works());
      
      
	   if(pre != null && !pre.is_confident()){
		   channelIds.put(r.videoId, pre.channelIds()); 
		   singleChannelIds.add(pre.channelIds());
		   keepMusic.put(r.videoId, w);
		   
		   toNextPhaseMusic.computeIfAbsent(r.videoId, k-> new ArrayList<>()).add(w.watchedAt());
	   }else {
		   allMusic.add(w);
		   
		   history.put(r.videoId, w);
		   
	   }
    }
    ChannelGenreJudgeService service = new ChannelGenreJudgeService(new YouTubeApiClient());
    Map<String, ChannelDescriptionGenreJudge.Result> judgeList = service.judge(singleChannelIds, token);
    
    //最後まで判定されなかったものは、以下でチャンネルの詳細情報を見てそこで判断する
    for(Map.Entry<String/*ここはvideoIdになります*/, String> entry : channelIds.entrySet()) {
    	
    	Result w = keepMusic.get(entry.getKey());
    	if(w.category() == VideoGenre.MAD || !w.foundKeyParenthesis() && (judgeList.get(entry.getValue()).genre() == VideoGenre.MAD || w.tags().isArtistMissing())/*一つだけ抽出されてるのなら問答無用で*/) {
    		
    		boolean newIs_confident = judgeList.get(entry.getValue()).is_confident();
    		w = w.withIs_confident(newIs_confident);
    		for(Instant i : toNextPhaseMusic.get(entry.getKey())) {
    			Result newW = w.withWatchedAt(i);
    			allMusic.add(newW);
    		}
    	}else {
    		w = w.withCategory(VideoGenre.MUSIC);
    		for(Instant i : toNextPhaseMusic.get(entry.getKey())) {
    			Result newW = w.withWatchedAt(i);
    			allMusic.add(newW);
    		}
    	}
    }
    
    
    
    
    VideoTitleCorrespondingFinding fx = new VideoTitleCorrespondingFinding(AppStartupListener.musicIndexManager, AppStartupListener.idGenerator, videoPlayTimes);
    //fx.init();
    //printMapContents();
    List<DeterminationMusic> list = new ArrayList<>();
    for(Map.Entry<String, List<MusicSource>> ent : songForArtist.entrySet()) {
    	for(MusicSource m : ent.getValue()) {
    		DeterminationMusic d = new DeterminationMusic(m);
    		list.add(d);
    		
    	}
    }
    VideoTitleCorrespondingFinding.withExclusive(() ->{
    	try {
			fx.start(fx, list);
		} catch (Exception e) {
			// TODO 自動生成された catch ブロック
			VideoTitleCorrespondingFinding.ps.flush(); 
	    	System.out.println(VideoTitleCorrespondingFinding.buffer.toString());
	    	System.out.println("kokomadega");
			e.printStackTrace();
		}
    	
    });
    
    List<DeterminationMusic> animeList = new ArrayList<>();
    for(AnimeWithVideoId a : onlyAnimes) {
    	animeList.add(new DeterminationMusic(null, null, true, a.videoId(), a.channelId(), a.anime(), null, null, false, null));
    }
    List<String> list2 = new ArrayList<>();
	for(DeterminationMusic m : animeList) {
		if(m.anime.title != null) {
			list2.add(m.anime.title);
			}
	}
    AnimationOpedSongTitleApiSearch.start(animeList);
    
    //ピアノ動画判定として更新
    dao.updateGenreBatch(allMusic);
    
  //時間を計測。デバッグ用↓
	long elapsedNanos = System.nanoTime() - start;
	double millis = elapsedNanos / 1_000_000.0;
	System.out.printf("elapsed: %.3f ms%n", millis);
	//時間を計測。デバッグ用↑
	
    //以下はデバッグ用です
    return allMusic;
    
  }
  

  

}
