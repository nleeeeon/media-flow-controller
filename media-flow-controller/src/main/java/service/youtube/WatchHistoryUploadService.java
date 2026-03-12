package service.youtube;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.stream.JsonReader;

import dao.user.MusicArchiveDao;
import dto.youtube.VideoInput;
import infrastructure.youtube.WatchHistoryJsonReader;
import service.music.MusicArchiveService;
import service.music.MusicClassificationService;

public class WatchHistoryUploadService {
	public boolean processWatchHistoryUpload(
            InputStream in,
            String token,
            long userId
    ) throws Exception {

	    // JSON をストリーミングで読み込む
	         InputStreamReader isr = new InputStreamReader(in, StandardCharsets.UTF_8);
	         JsonReader re = new JsonReader(isr);
	    	WatchHistoryJsonReader watchistoryRe = new WatchHistoryJsonReader();
	    	Map<String, Integer> videoIds = new HashMap<>();
	    	WatchHistoryJsonReader.InputResult input = watchistoryRe.readInputs(re, videoIds);
	    	Set<String> inputVideoIds = new HashSet<>(videoIds.keySet());
	    	
	    	
	    	List<VideoInput> rows = new ArrayList<>(input.rows());
	 
	    	
	    	MusicVideoProcessor musicVideoProcessor = new MusicVideoProcessor();
	    	List<MusicClassificationService.Result> result = musicVideoProcessor.process(token, rows, inputVideoIds);
	    	
	    	MusicArchiveService service = new MusicArchiveService(new MusicArchiveDao());
	    	service.rollupAndUpsert(userId, result);
	    	return true;
	    	
	    	
	  
	}
}
