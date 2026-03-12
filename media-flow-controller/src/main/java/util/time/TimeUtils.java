package util.time;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {
	private static final Pattern ISO = Pattern.compile("(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");

	public static int parseIsoDurationToSeconds(String iso) {
        int hours = 0, minutes = 0, seconds = 0;
        String s = iso.startsWith("PT") ? iso.substring(2) : iso;
        Matcher m = ISO.matcher(s);
        if (m.matches()) {
            if (m.group(1) != null) hours = Integer.parseInt(m.group(1));
            if (m.group(2) != null) minutes = Integer.parseInt(m.group(2));
            if (m.group(3) !=  null) seconds = Integer.parseInt(m.group(3));
        }
        return hours * 3600 + minutes * 60 + seconds;
    }
	public static Instant parseInstant(String iso){
	    if (iso==null || iso.isBlank()) return null;
	    try { return OffsetDateTime.parse(iso).toInstant(); }
	    catch (DateTimeParseException e) { try { return Instant.parse(iso); } catch(Exception ignore){ return null; } }
	  }
}
