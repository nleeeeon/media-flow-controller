package infrastructure.youtube;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import domain.youtube.ShortsDetector;
import dto.youtube.VideoInput;
import util.time.TimeUtils;
import util.youtube.YtJson;

public class WatchHistoryJsonReader {
	public static record InputResult(List<VideoInput> rows,List<String> titleUrl) {}
	public InputResult readInputs(JsonReader r, Map<String, Integer> videoIds) {
	    try {
	      

	      List<VideoInput> out = new ArrayList<>();
	      List<String> urls = new ArrayList<>();
	      r.beginArray();
	      while (r.hasNext()) {
	    	  JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
	        // 期待形式: {videoId,title,subtitle,watchedAt?}
	    	  if(ShortsDetector.looksShortsByUrl(YtJson.getStr(o, "titleUrl")))continue;
	    	  
	        if (o.has("videoId")) {
	          String vid = YtJson.getStr(o, "videoId");
	          if (vid==null || vid.isBlank()) continue;
	          //デバッグ用↓
	          //if( !(vid.equals("ue4fZnZ7qkM") || vid.equals("TngUo1gDNOg") || vid.equals("_KFAhsZjfn8")) )continue;
	          //デバッグ用↑
	          VideoInput w = new VideoInput();
	          w.videoId = vid; w.title = YtJson.getStr(o, "title"); w.subtitle = YtJson.getStr(o, "subtitle");
	          w.watchedAt = TimeUtils.parseInstant(YtJson.getStr(o, "watchedAt"));
	          out.add(w); 
	          
	          urls.add(YtJson.getStr(o, "titleUrl")); 
	          videoIds.merge(vid, 1, Integer::sum);
	          continue;
	        }

	        // Takeout: titleUrl/header からID、title/subtitles/name、time を拾う
	        String url = YtJson.getStr(o, "titleUrl"); if (url==null) url = YtJson.getStr(o, "header");
	        String vid = extractVideoIdFromUrl(url);
	        if (vid==null) continue;
	        
	      //デバッグ用↓
          /*if( !(vid.equals("Mes1XCDZYTo") || 
        		  vid.equals("2pECnr5MNuU") || 
        		  vid.equals("JqUWua4MrIM") ||
        		  vid.equals("JqUWua4MrIM") ||
        		  vid.equals("ie7-k4dppwg")) )continue;*/
          //デバッグ用↑

	        String title = YtJson.getStr(o, "title");
	        String sub = ""; // subtitles[*].name を結合
	        if (o.has("subtitles") && o.get("subtitles").isJsonArray()) {
	          List<String> names = new ArrayList<>();
	          for (JsonElement se : o.getAsJsonArray("subtitles")) {
	            if (se.isJsonObject()) {
	              String nm = YtJson.getStr(se.getAsJsonObject(), "name");
	              if (nm!=null && !nm.isBlank()) names.add(nm);
	            }
	          }
	          if (!names.isEmpty()) sub = String.join(" / ", names);
	        }
	        Instant watched = TimeUtils.parseInstant(YtJson.getStr(o, "time")); // TakeoutはここにISO8601

	        VideoInput w = new VideoInput();
	        w.videoId = vid; w.title = cleanTitle(title); w.subtitle = sub; w.watchedAt = watched;
	        out.add(w);
	        urls.add(url);
	        videoIds.merge(vid, 1, Integer::sum);
	      }
	      // videoId必須
	      return new InputResult(out.stream().filter(x->x.videoId!=null && !x.videoId.isBlank()).toList(), urls);
	      //return out.stream().filter(x->x.videoId!=null && !x.videoId.isBlank()).toList();
	    } catch (Exception ex) {
	      System.out.println("ERROR readInputs: " + ex);
	      return new InputResult(List.of(), List.of());
	    }
	  }
	
	private static String cleanTitle(String s){
		  if (s == null) return null;
		  String t = s;
		  t = t.replaceFirst("^YouTube で視聴:\\s*", "");          // Takeout特有
		  t = t.replaceAll("[\\s　]*を視聴しました$", "");          // …を視聴しました
		  t = t.replaceAll("[\\s　]*をライブ配信しました$", "");
		  t = t.replaceAll("[\\s　]*をプレミア公開しました$", "");
		  t = t.replaceAll("】\\s*を視聴しました$", "】");          // 【…】 を視聴しました → 【…】
		  return t.trim();
		 }
	//YouTube URL → videoId（watch/shorts/youtu.be対応）
	 private static String extractVideoIdFromUrl(String url){
	   if (url==null) return null;
	   try {
	     int i = url.indexOf("/shorts/"); if (i>=0) return cut(url.substring(i+8));
	     i = url.indexOf("youtu.be/");    if (i>=0) return cut(url.substring(i+9));
	     i = url.indexOf("watch?v=");     if (i>=0) return cut(url.substring(i+8));
	   } catch (Exception ignore) {}
	   return null;
	 }
	 private static String cut(String s){ String id = s.split("[?&/#]")[0]; return (id==null||id.isBlank())? null : id; }
}
