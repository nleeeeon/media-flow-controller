package util.youtube;

import java.util.LinkedHashSet;
import java.util.regex.Pattern;
public class YouTubeIdParser {
	private static final Pattern PL_FROM_URL = Pattern.compile("[?&]list=([A-Za-z0-9_-]+)");
	  private static final Pattern PL_ID_ONLY  = Pattern.compile("^(PL|OL|RD)[A-Za-z0-9_-]+$");
	  private static final Pattern VID_IN_URL1 = Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})");
	  private static final Pattern VID_IN_URL2 = Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})");
	  private static final Pattern VID_PLAIN   = Pattern.compile("(^|\\s|,)([A-Za-z0-9_-]{11})(?=\\s|,|$)");
	public static String detectPlaylistId(String input) {
	    var m1 = PL_FROM_URL.matcher(input); if (m1.find()) {
	    	return m1.group(1);
	    }
	    if (PL_ID_ONLY.matcher(input).find()) {
	    	return input.trim();
	    }
	    return null;
	  }
	public static LinkedHashSet<String> extractVideoIds(String text) {
	    LinkedHashSet<String> out = new LinkedHashSet<>();
	    var m1 = VID_IN_URL1.matcher(text); while (m1.find()) out.add(m1.group(1));
	    var m2 = VID_IN_URL2.matcher(text); while (m2.find()) out.add(m2.group(1));
	    var m3 = VID_PLAIN.matcher(text);   while (m3.find()) out.add(m3.group(2));
	    return out;
	  }
}
