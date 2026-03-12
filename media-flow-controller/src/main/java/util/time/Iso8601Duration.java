package util.time;

public final class Iso8601Duration {
    private Iso8601Duration() {}

    public static int secondsOf(String iso){
        if (iso == null || iso.isBlank()) return 0;
        try {
            int h=0,m=0,s=0;
            String x = iso.startsWith("PT") ? iso.substring(2) : iso;
            StringBuilder num = new StringBuilder();

            for (char c : x.toCharArray()){
                if (Character.isDigit(c)) num.append(c);
                else {
                    int v = (num.length()==0) ? 0 : Integer.parseInt(num.toString());
                    switch (c){
                        case 'H' -> h = v;
                        case 'M' -> m = v;
                        case 'S' -> s = v;
                        default -> { /* ignore */ }
                    }
                    num.setLength(0);
                }
            }
            return h*3600 + m*60 + s;
        } catch (Exception ignore) {
            return 0;
        }
    }
}