package domain.youtube;

import java.util.Locale;

import service.title.TitleWorkExtractor;


public final class ChannelDescriptionGenreJudge {


    private ChannelDescriptionGenreJudge() {}
    public record Result (
    		VideoGenre genre,
    		boolean is_confident
    		){}

    public static Result judge(String description) {
        if (description == null || description.isBlank()) {
            
            return new Result(VideoGenre.MUSIC, false);
        }

        String s = description.toLowerCase(Locale.ROOT);

        if (TitleWorkExtractor.strMadCheck(s)) {
            return new Result(VideoGenre.MAD, true);
        }

        if (s.contains("mad")) {
        	//この単語があるだけの判定なので、is_confidentはfalse
            return new Result(VideoGenre.MAD, false);
        }

        return new Result(VideoGenre.MUSIC, true);
    }
}