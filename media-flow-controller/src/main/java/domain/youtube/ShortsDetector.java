package domain.youtube;

import java.util.List;
import java.util.Locale;

import dto.youtube.ShortVideoCheckTarget;
import dto.youtube.VideoInfo;
import service.music.TitleWorkExtractor;

public final class ShortsDetector {
	private static final int SHORTS_MIN_SEC  = 0;
	private static final int SHORTS_MAX_SEC  = 60;
	private static final int PIANO_MIN_SEC  = 60;
	private static final int PIANO_MAX_SEC  = 420;
	private static final double MAX_RATIO   = 1.2;
    private ShortsDetector() {}
    
    public static boolean looksShortsByUrl(String url) {
  	  if (url == null) return false;
  	  String u = url.toLowerCase(java.util.Locale.ROOT);
  	  // 代表的なショート専用パス
  	  // 例) https://www.youtube.com/shorts/XXXX, https://youtube.com/shorts/XXXX
  	  return u.contains("/shorts/");
  	}

    public static boolean looksShorts(ShortVideoCheckTarget v) {
        
        int seconds = v.durationSec();
        if (seconds > SHORTS_MIN_SEC && seconds <= SHORTS_MAX_SEC) return true;

        if (containsHashShorts(v.title()) || containsHashShorts(v.description())) return true;

        List<String> tags = v.tags();
        if (tags != null) {
            for (String t : tags) {
                if (containsShortsKeywordInTag(t)) return true;
            }
        }
        return false;
    }

    private static boolean containsHashShorts(String s){
        if (s == null) return false;
        String L = s.toLowerCase(Locale.ROOT);
        return L.contains("#shorts");
    }
    
    private static boolean containsShortsKeywordInTag(String s){
        if (s == null) return false;
        String L = s.toLowerCase(Locale.ROOT);
        return L.contains("#shorts")
                || L.contains(" shorts ")
                || L.endsWith(" shorts")
                || L.startsWith("shorts ")
                || L.contains("ショート");
    }
    
    public static boolean isValidPianoVideo(VideoInfo v) {
        if (v == null) return false;
        return isValidPianoVideo(v.title, v.durationSec, v.ratio);
       
    }
    
    public static boolean isValidPianoVideo(String title, int sec, double ratio) {
        String t = (title == null) ? "" : title;
        if(containsHashShorts(t))return false;
        if (t.contains("メドレー")) return false;
        
        if (sec < PIANO_MIN_SEC) return false;
        if (sec > PIANO_MAX_SEC) return false;
        if (ratio > MAX_RATIO) return false;

        if (!TitleWorkExtractor.containsPianoWord(t)) return false;

        return true;
    }
}