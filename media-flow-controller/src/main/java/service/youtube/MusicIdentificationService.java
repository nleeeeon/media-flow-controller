
package service.youtube;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dao.youtube.Channels_dao;
import dao.youtube.Videos_dao;
import domain.music.MusicMatchJudge;
import domain.youtube.VideoGenre;
import dto.anime.Anime;
import dto.music.MusicIdentityService;
import dto.music.Track;
import dto.music.VideoUpsertParam;
import dto.youtube.ThumbnailAndTitle;
import dto.youtube.VideoInfo;
import dto.youtube.VideoidWithThumbnail;
import infrastructure.concurrent.BackgroundExecutorPiano;
import infrastructure.db.DbVendorChecker;
import infrastructure.db.DbVendorChecker.DbVendor;
import infrastructure.youtube.YouTubeApiClient;
import listener.AppStartupListener;
import service.identity.PairJudgeRegistry;
import service.music.VideoTitleCorrespondingFinding;
import service.title.TitleTrackExtractor;
import util.common.MusicKeyUtils;
public class MusicIdentificationService {

	
	static Set<String>history = new HashSet<>(); 
	public static void applyChannelVideo(String token, List<String> channelIds) throws IOException, InterruptedException, SQLException {
		Set<String> missingChannelIds = new HashSet<>();
		for(String str : channelIds) {
			if(history.add(str)) {
				missingChannelIds.add(str);
			}
			if(missingChannelIds.size() >= 3)break;
		}
		
		YouTubeApiClient api = new YouTubeApiClient();
		PianoVideoCollector service = new PianoVideoCollector(api);
		Set<VideoInfo> videos = service.fetchNonShorts(token, missingChannelIds);
		
		Map<String, Integer> count = new HashMap<>();
		VideoTitleCorrespondingFinding vt = new VideoTitleCorrespondingFinding(AppStartupListener.musicIndexManager, AppStartupListener.idGenerator);
		
		
		Videos_dao mDao = new Videos_dao();
		List<VideoUpsertParam> batchMusic = new ArrayList<>();
		List<MusicIdentityService> list = new ArrayList<>();
		Map<String, Long> videoPlayTimes = new HashMap<>();;
		for(VideoInfo entry : videos) {
			batchMusic.add(new VideoUpsertParam(entry.videoId, entry.channelId, VideoGenre.PIANO, true, entry.title, entry.playCount, entry.thumbnailUrl));
			
			String baseTitle = entry.title;
			count.merge(entry.channelId, 1, Integer::sum);
			videoPlayTimes.put(entry.videoId, entry.playCount);
			Track out = new Track();
			TitleTrackExtractor.titleJustForWithSongTitleAndArtistExtract(out, entry.title);
			if(out.isNull())continue;
			String song = out.song;
			String artist = out.hasArtist() ? out.artist : "";
			boolean fixed = out.hasArtist() ? false : true;
			
			MusicIdentityService m = new MusicIdentityService(artist, song, fixed, entry.videoId, entry.channelId, new Anime(), null, null, true, baseTitle);
			//デバッグ用↓
			if(m.artist.equals(m.song)) {
	    		System.out.println(m.artist+" スキップしました");
	    		continue;
	    	}
			//デバッグ用↑
			
			list.add(m);
			
		}
		
		mDao.upsertInitial(batchMusic);
		
		VideoTitleCorrespondingFinding.withExclusive(() ->{
	    	try {
				vt.start(vt, list);
			} catch (Exception e) {
				// TODO 自動生成された catch ブロック
				VideoTitleCorrespondingFinding.ps.flush(); 
		    	System.out.println(VideoTitleCorrespondingFinding.buffer.toString());
		    	System.out.println("kokomadega");
				e.printStackTrace();
			}
	    	
	    });
		for(Map.Entry<String, Integer> a : count.entrySet())System.out.println("channelId="+a.getKey()+"数＝"+a.getValue());
		if(DbVendorChecker.get() == DbVendor.MARIADB) {
			PairJudgeRegistry.runner().start();
		}
	}
	
	public static void main(String args[]) throws IOException, InterruptedException {
		String title = "【天気の子】「グランドエスケープ（Movie edit）feat.三浦透子」を弾いてみた【ピアノ】";
		List<String> out = new ArrayList<>();
		//WorksRank.vocaloid_song_check(out, title, "");
		//if(out.isEmpty())WorksRank.artistSongTitleExtraction(out, title);
		System.out.println(out);
		//System.out.println(searchVideo("","いますぐ輪廻","","",true));
	}
	
	public static VideoidWithThumbnail searchVideo(String artist, String song, String query, String token, boolean fixed) throws IOException, InterruptedException, SQLException {
		VideoSearchService service = new VideoSearchService();
		List<VideoInfo> results = 
				service.searchOneKeyword(query, token, true);
		
		long max = -1;
		VideoidWithThumbnail resultVideo = new VideoidWithThumbnail();
		List<String> targetChannelIds = new ArrayList<>();
		Set<String> allChannelIds = new HashSet<>();
		Videos_dao mDao = new Videos_dao();
		List<VideoUpsertParam> batchMusic = new ArrayList<>();
		int maxSimilarity = 0;
		System.out.println("検索から得られたタイトル等↓");
		for(VideoInfo entry : results) {
			allChannelIds.add(entry.channelId);
			int similarity = 0;
			batchMusic.add(new VideoUpsertParam(entry.videoId, entry.channelId, VideoGenre.PIANO, true, entry.title, entry.playCount, entry.thumbnailUrl));
			
			Track out = new Track();
			TitleTrackExtractor.titleJustForWithSongTitleAndArtistExtract(out, entry.title);
			
			if(out.isNull())continue;
			
			out.song = MusicKeyUtils.normKey(out.song);
			out.artist = MusicKeyUtils.normKey(out.artist);
			
			if(MusicMatchJudge.sameCheck(song, out.song)) {
				similarity++;
				if(out.hasArtist() && MusicMatchJudge.sameCheck(artist, out.artist))similarity++;
			}else if(MusicMatchJudge.sameCheck(artist, out.song)) {
				similarity++;
				if(out.hasSong() && MusicMatchJudge.sameCheck(artist, out.song))similarity++;
			}
			
			if(similarity == 0)continue;
			
			targetChannelIds.add(entry.channelId);
			resultVideo.thumbnails.put(entry.videoId, new ThumbnailAndTitle(entry.thumbnailUrl, entry.title));
			if( (max < entry.playCount && maxSimilarity == similarity) || maxSimilarity < similarity) {
				resultVideo.mainVideoId = entry.videoId;
				max = entry.playCount;
				maxSimilarity = similarity;
			}
			
		}
		
		Channels_dao cDao = new Channels_dao();
		cDao.upsertChannelIds(allChannelIds);
		
		
		mDao.upsertInitial(batchMusic);
		
			
		
		BackgroundExecutorPiano.submit(()->{
			try {
				applyChannelVideo(token, targetChannelIds.subList(0, Math.min(targetChannelIds.size(), 2)));
			} catch (IOException | InterruptedException | SQLException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}
			
		});
		return resultVideo;
		
		
	}
	
	

}
