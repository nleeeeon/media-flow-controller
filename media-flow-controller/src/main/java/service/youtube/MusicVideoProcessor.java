package service.youtube;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;

import dao.youtube.Channels_dao;
import dao.youtube.Checked_video_ids_dao;
import dao.youtube.Videos_dao;
import dto.youtube.VideoInput;
import infrastructure.youtube.YouTubeChannelsApi;
import infrastructure.youtube.YouTubeVideoMetaApi;
import infrastructure.youtube.YouTubeVideosApi;
import infrastructure.youtube.YoutubeMetaProcessor;
import service.music.MusicClassificationService;

public class MusicVideoProcessor {
	private final YouTubeVideoMetaApi vmApi = new YouTubeVideoMetaApi();
	private final YouTubeChannelsApi cApi = new YouTubeChannelsApi();
	private final YouTubeVideosApi yvApi = new YouTubeVideosApi();
	
	public List<MusicClassificationService.Result> process(String token, List<VideoInput> rows, Set<String> inputVideoIds) throws Exception {
		Checked_video_ids_dao vCheckDao = new Checked_video_ids_dao();
		//一度チェックしたことのあるvideoIdの情報をAPI使って取得しないように
    	Map<String, Boolean> existingVideoIds = vCheckDao.findExistingVideoIds(inputVideoIds);
		Map<String, JsonObject> meta = vmApi.fetchVideoMeta(token, inputVideoIds);
		MusicJudge judge = new MusicJudge();
		Map<String, Boolean> videoMusicResults = new HashMap<>();
    	for (var it = rows.listIterator(); it.hasNext();) {
    		  VideoInput r = it.next();
    		  boolean existingFlag = existingVideoIds.containsKey(r.videoId);
    		  if(existingFlag && !existingVideoIds.get(r.videoId)) {
    			  it.remove(); 
    			  continue;
    		  }else if(existingFlag && existingVideoIds.get(r.videoId)) {
    			  continue;
    		  }
    		  
    		  boolean checkedFlag = videoMusicResults.containsKey(r.videoId);
    		  if(checkedFlag && !videoMusicResults.get(r.videoId)) {
    			  it.remove();
    			  continue;
    		  }else if(checkedFlag && videoMusicResults.get(r.videoId)) {
    			  continue;
    		  }
    		  
    		  
    		  JsonObject item = meta.get(r.videoId);
    		  if (item == null) { it.remove(); continue; } // 判定できないなら落とす方針
    		  MusicJudge.Result result = judge.judge(item);

    		  if (!result.isMusic()) {
    			  videoMusicResults.put(r.videoId, false);
    		    it.remove(); 
    		    continue;
    		  }else {
    			  videoMusicResults.put(r.videoId, true);
    			  continue;
    		  }
    	}

    	if (rows.isEmpty()) {
    		return List.of();
    	}
    	
    	vCheckDao.upsertCheckedVideoIds(videoMusicResults);
    	//音楽の動画のmetaだけ取得
    	Set<String> aliveIds = rows.stream()
    		    .map(r -> r.videoId)
    		    .collect(java.util.stream.Collectors.toSet());

		Map<String, JsonObject> prunedMeta = new java.util.HashMap<>();
		for (String id : aliveIds) {
		  JsonObject it = meta.get(id);
		  if (it != null) prunedMeta.put(id, it);
		}
		Map<String, Long> channelRegistrantNumber = new HashMap<>();
		Map<String, String> channel_title_ja_by_id = new HashMap<>();
    	cApi.fetchChannelStatisticsFromVideoMeta(token,prunedMeta, channelRegistrantNumber, channel_title_ja_by_id);//チャンネル登録者数をグローバル変数に格納
    	Channels_dao cDao = new Channels_dao();
    	cDao.upsertChannels(channelRegistrantNumber, channel_title_ja_by_id);
    	
    	Map<String, Long> videoPlayTimes = new HashMap<>();
    	vmApi.fetchVideoViewCountFromVideoMeta(token,prunedMeta, videoPlayTimes);
    	Map<String, String>thumbnailMap = new HashMap<>();
    	yvApi.fetchThumbnails(new ArrayList<>(prunedMeta.keySet()), token, thumbnailMap);
    	
    	YoutubeMetaProcessor.replaceTitlesWithLocalized(meta,rows);
    	
    	Videos_dao vDao = new Videos_dao();
    	vDao.upsertInitial(rows, thumbnailMap, videoPlayTimes, prunedMeta);
    	MusicClassificationService works = new MusicClassificationService(videoPlayTimes, channelRegistrantNumber, channel_title_ja_by_id);
    	List<MusicClassificationService.Result> result = works.classify(rows, meta, token, existingVideoIds);
    	return result;
	}
}
