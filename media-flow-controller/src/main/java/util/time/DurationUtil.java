package util.time;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationUtil {
    private static final Pattern ISO = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?");

    private DurationUtil() {}

    /** ISO 8601 duration (例: PT1M2S) を秒へ変換 */
    public static int parseIsoDurationToSeconds(String iso) {
        if (iso == null || iso.isBlank()) return 0;

        int h = 0, m = 0, s = 0;
        Matcher ma = ISO.matcher(iso);
        if (ma.find()) {
            if (ma.group(1) != null) h = Integer.parseInt(ma.group(1));
            if (ma.group(2) != null) m = Integer.parseInt(ma.group(2));
            if (ma.group(3) != null) s = Integer.parseInt(ma.group(3));
        }
        return h * 3600 + m * 60 + s;
    }
}